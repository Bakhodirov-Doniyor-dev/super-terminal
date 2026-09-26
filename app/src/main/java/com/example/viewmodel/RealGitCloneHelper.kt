package com.example.viewmodel

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ProgressMonitor
import java.io.File
import java.util.Locale

/**
 * Professional-grade native Git clone implementation using JGit.
 * Directly interacts with the Git protocol (Smart HTTP/Git) without 
 * relying on fake zip downloads. Full `.git` metadata is preserved.
 */
object RealGitCloneHelper {

    suspend fun cloneGitRepo(
        context: Context,
        rawUrl: String,
        onProgress: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val cleanUrl = rawUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext "git clone: repository URL must not be empty. Usage: git clone <url>"
        }

        var repoName = cleanUrl.removeSuffix(".git").substringAfterLast("/")
        if (repoName.isBlank()) repoName = "git-repository"
        
        onProgress("Native JGit Engine: Cloning repository '$repoName' from $cleanUrl ...")

        // Determine target download directory
        val baseDir = try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null && (downloadDir.exists() || downloadDir.mkdirs()) && downloadDir.canWrite()) {
                File(downloadDir, repoName)
            } else {
                File(context.filesDir, "repositories/$repoName")
            }
        } catch (e: Exception) {
            File(context.filesDir, "repositories/$repoName")
        }

        if (baseDir.exists() && (baseDir.listFiles()?.isNotEmpty() == true)) {
            return@withContext "fatal: destination path '${baseDir.name}' already exists and is not an empty directory."
        }
        baseDir.mkdirs()

        try {
            val git = Git.cloneRepository()
                .setURI(cleanUrl)
                .setDirectory(baseDir)
                .setCloneAllBranches(true)
                .setProgressMonitor(object : ProgressMonitor {
                    private var currentTask: String = "Starting"
                    private var totalWork: Int = 0
                    private var completedWork: Int = 0
                    private var lastUpdate = System.currentTimeMillis()

                    override fun start(totalTasks: Int) {
                        onProgress("Initializing Git protocol...")
                    }

                    override fun beginTask(title: String?, work: Int) {
                        currentTask = title ?: "Working"
                        totalWork = work
                        completedWork = 0
                        
                        val msg = if (work > 0) {
                            "$currentTask: 0% (0/$work)"
                        } else {
                            "$currentTask..."
                        }
                        onProgress(msg)
                    }

                    override fun update(completed: Int) {
                        completedWork += completed
                        val now = System.currentTimeMillis()
                        // Throttle updates to avoid flooding UI thread
                        if (now - lastUpdate > 250) {
                            lastUpdate = now
                            if (totalWork > 0) {
                                val percent = (completedWork * 100) / totalWork
                                onProgress("$currentTask: $percent% ($completedWork/$totalWork)")
                            } else {
                                onProgress("$currentTask: $completedWork objects")
                            }
                        }
                    }

                    override fun endTask() {
                        if (totalWork > 0) {
                            onProgress("$currentTask: 100% ($totalWork/$totalWork) [DONE]")
                        } else {
                            onProgress("$currentTask: $completedWork objects [DONE]")
                        }
                    }

                    override fun isCancelled(): Boolean = false
                })
                .call()
            
            // Clean up resources properly
            git.close()

            // Analyze cloned structure to provide professional stats
            val (totalExtractedFiles, totalSize) = analyzeDirectory(baseDir)
            val totalMb = "%.2f MB".format(Locale.US, totalSize / (1024.0 * 1024.0))
            
            """
            [✔️] Haqiqiy (Native) Git klonlash muvaffaqiyatli yakunlandi!
            • Manzil: ${baseDir.absolutePath}
            • Jami fayllar: $totalExtractedFiles ta
            • Yuklangan hajm: $totalMb
            • Git ombori holati: Barcha metama'lumotlar (.git) va tarix (history) to'liq saqlab qolindi.
            """.trimIndent()
        } catch (e: Exception) {
            "git clone failed: Tizim xatoligi yuz berdi. Internet ulanishini yoki ombor manzilining ochiqligini (public) tekshiring. \n\n[JGit Diagnostic]: ${e.localizedMessage ?: e.javaClass.name}"
        }
    }
    
    private fun analyzeDirectory(dir: File): Pair<Int, Long> {
        var count = 0
        var size = 0L
        val list = dir.listFiles() ?: return Pair(0, 0L)
        for (file in list) {
            count++
            if (file.isDirectory) {
                val (subCount, subSize) = analyzeDirectory(file)
                count += subCount
                size += subSize
            } else {
                size += file.length()
            }
        }
        return Pair(count, size)
    }
}
