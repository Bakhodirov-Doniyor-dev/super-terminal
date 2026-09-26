package com.example.terminal

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import org.tukaani.xz.XZOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * High-performance Archive & Decompression Engine for Super Terminal Manager.
 * Supports .xz, .tar.xz, .tar.gz, .tar, .gz natively with real-time progress callbacks,
 * speed calculations, and permission preservation.
 */
object ArchiveEngine {

    data class ProgressState(
        val bytesProcessed: Long,
        val totalBytes: Long,
        val percent: Int,
        val speedMbPerSec: Double,
        val currentEntryName: String = "",
        val entriesProcessed: Int = 0
    )

    /**
     * Decompresses an .xz file (e.g. kali-rootfs-arm64.tar.xz -> kali-rootfs-arm64.tar)
     */
    suspend fun decompressXz(
        sourceFile: File,
        destFile: File? = null,
        keepOriginal: Boolean = true,
        onProgress: (suspend (ProgressState) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!sourceFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Fayl topilmadi: ${sourceFile.absolutePath}"))
            }

            val target = destFile ?: run {
                val name = sourceFile.name
                val targetName = if (name.endsWith(".xz", ignoreCase = true)) {
                    name.substring(0, name.length - 3)
                } else {
                    "$name.decompressed"
                }
                File(sourceFile.parentFile ?: sourceFile.absoluteFile.parentFile, targetName)
            }

            val totalSize = sourceFile.length()
            val startTime = System.currentTimeMillis()
            var lastUpdateTime = startTime

            val tempTarget = File(target.parentFile, "${target.name}.tmp_${System.currentTimeMillis()}")

            FileInputStream(sourceFile).use { fileIn ->
                BufferedInputStream(fileIn, 64 * 1024).use { bufferedIn ->
                    XZInputStream(bufferedIn).use { xzIn ->
                        FileOutputStream(tempTarget).use { fileOut ->
                            BufferedOutputStream(fileOut, 64 * 1024).use { bufferedOut ->
                                val buffer = ByteArray(64 * 1024)
                                var bytesRead: Int
                                var totalBytesRead = 0L

                                while (xzIn.read(buffer).also { bytesRead = it } != -1) {
                                    bufferedOut.write(buffer, 0, bytesRead)
                                    totalBytesRead += bytesRead

                                    val now = System.currentTimeMillis()
                                    if (now - lastUpdateTime >= 250 && onProgress != null) {
                                        val elapsedSec = (now - startTime) / 1000.0
                                        val speed = if (elapsedSec > 0) (totalBytesRead / (1024.0 * 1024.0)) / elapsedSec else 0.0
                                        val percent = if (totalSize > 0) {
                                            // XZ uncompressed size is typically 2x - 5x the compressed size
                                            val estimatedPercent = ((totalBytesRead.toDouble() / (totalSize * 3.5)) * 100).toInt().coerceIn(1, 99)
                                            estimatedPercent
                                        } else 0
                                        onProgress(ProgressState(totalBytesRead, totalSize, percent, speed))
                                        lastUpdateTime = now
                                    }
                                }
                                bufferedOut.flush()
                            }
                        }
                    }
                }
            }

            if (target.exists()) {
                target.delete()
            }
            if (!tempTarget.renameTo(target)) {
                tempTarget.copyTo(target, overwrite = true)
                tempTarget.delete()
            }

            if (!keepOriginal) {
                sourceFile.delete()
            }

            val finalElapsed = (System.currentTimeMillis() - startTime) / 1000.0
            val finalSpeed = if (finalElapsed > 0) (target.length() / (1024.0 * 1024.0)) / finalElapsed else 0.0
            onProgress?.invoke(ProgressState(target.length(), totalSize, 100, finalSpeed))

            Result.success(target)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extracts TAR / TAR.XZ / TAR.GZ archive entries to destination folder.
     */
    suspend fun extractTarArchive(
        archiveFile: File,
        targetDir: File,
        onProgress: (suspend (ProgressState) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!archiveFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Arxiv topilmadi: ${archiveFile.absolutePath}"))
            }

            targetDir.mkdirs()

            val rawIn = BufferedInputStream(FileInputStream(archiveFile), 64 * 1024)
            val decompressedStream: InputStream = when {
                archiveFile.name.endsWith(".xz", ignoreCase = true) -> XZInputStream(rawIn)
                archiveFile.name.endsWith(".gz", ignoreCase = true) || archiveFile.name.endsWith(".tgz", ignoreCase = true) -> GZIPInputStream(rawIn)
                else -> rawIn
            }

            var entryCount = 0
            var totalBytesExtracted = 0L
            val startTime = System.currentTimeMillis()
            var lastUpdateTime = startTime

            TarArchiveInputStream(decompressedStream).use { tarIn ->
                var entry: TarArchiveEntry? = tarIn.nextTarEntry
                val buffer = ByteArray(64 * 1024)

                while (entry != null) {
                    val outputFile = File(targetDir, entry.name)

                    // Security check against directory traversal (Zip Slip vulnerability)
                    if (!outputFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                        entry = tarIn.nextTarEntry
                        continue
                    }

                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        if (entry.isSymbolicLink) {
                            try {
                                java.nio.file.Files.deleteIfExists(outputFile.toPath())
                                java.nio.file.Files.createSymbolicLink(
                                    outputFile.toPath(),
                                    java.nio.file.Paths.get(entry.linkName)
                                )
                            } catch (_: Exception) {
                                // Fallback for filesystems or kernels where symlinks are restricted
                                try {
                                    val linkTarget = if (entry.linkName.startsWith("/")) {
                                        File(targetDir, entry.linkName.removePrefix("/"))
                                    } else {
                                        File(outputFile.parentFile, entry.linkName)
                                    }
                                    if (linkTarget.exists() && linkTarget.isFile) {
                                        linkTarget.copyTo(outputFile, overwrite = true)
                                        outputFile.setExecutable(true, false)
                                    } else {
                                        // If in bin or sbin, create an executable shell trampoline
                                        val parentName = outputFile.parentFile?.name ?: ""
                                        if (parentName == "bin" || parentName == "sbin") {
                                            outputFile.writeText("#!/bin/sh\nexec \"${entry.linkName}\" \"$@\"\n")
                                            outputFile.setExecutable(true, false)
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        } else {
                            FileOutputStream(outputFile).use { out ->
                                var len: Int
                                while (tarIn.read(buffer).also { len = it } != -1) {
                                    out.write(buffer, 0, len)
                                    totalBytesExtracted += len
                                }
                            }
                            // Restore executable permissions if applicable
                            if ((entry.mode and 0b001_001_001) != 0) {
                                outputFile.setExecutable(true, false)
                            }
                        }
                    }

                    entryCount++
                    val now = System.currentTimeMillis()
                    if (now - lastUpdateTime >= 300 && onProgress != null) {
                        val elapsedSec = (now - startTime) / 1000.0
                        val speed = if (elapsedSec > 0) (totalBytesExtracted / (1024.0 * 1024.0)) / elapsedSec else 0.0
                        onProgress(
                            ProgressState(
                                bytesProcessed = totalBytesExtracted,
                                totalBytes = archiveFile.length(),
                                percent = 0,
                                speedMbPerSec = speed,
                                currentEntryName = entry.name,
                                entriesProcessed = entryCount
                            )
                        )
                        lastUpdateTime = now
                    }

                    entry = tarIn.nextTarEntry
                }
            }

            Result.success(entryCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolves user-friendly paths (e.g. ~/Download, /sdcard/Download, or relative paths).
     */
    fun resolveFile(rawPath: String, currentDir: File): File {
        val cleanPath = rawPath.trim('\'', '"', ' ')
        return when {
            cleanPath.startsWith("/") -> File(cleanPath)
            cleanPath.startsWith("~/") -> File("/sdcard/Home", cleanPath.removePrefix("~/"))
            cleanPath == "~" -> File("/sdcard/Home")
            else -> File(currentDir, cleanPath)
        }
    }

    fun formatSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            "%.2f GB".format(Locale.US, mb / 1024.0)
        } else {
            "%.2f MB".format(Locale.US, mb)
        }
    }
}
