package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object SafStorageHelper {

    // Get volume ID from path (e.g. "/storage/A2B4-9F81/Folder" -> "A2B4-9F81")
    fun getVolumeId(path: String): String? {
        val parts = path.split("/")
        if (parts.size >= 3 && parts[1] == "storage") {
            val candidate = parts[2]
            if (candidate != "emulated" && candidate != "self") {
                return candidate
            }
        }
        return null
    }

    // Check if path is on a physical secondary storage (like SD Card or USB)
    fun isSecondaryStoragePath(path: String): Boolean {
        return getVolumeId(path) != null
    }

    // Find persisted Uri for a specific volume ID
    fun getPersistedUriForVolume(context: Context, volumeId: String): Uri? {
        try {
            val list = context.contentResolver.persistedUriPermissions
            for (perm in list) {
                val uriStr = perm.uri.toString()
                val decodedUri = Uri.decode(uriStr)
                if (decodedUri.contains(volumeId, ignoreCase = true) || uriStr.contains(volumeId, ignoreCase = true)) {
                    return perm.uri
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // Check if volume is already authorized via SAF
    fun isVolumeAuthorized(context: Context, path: String): Boolean {
        val volId = getVolumeId(path) ?: return true // Internal storage doesn't need SAF
        return getPersistedUriForVolume(context, volId) != null
    }

    // Convert file path to DocumentFile using a persisted tree URI
    fun getDocumentFileForPath(context: Context, path: String, createIfMissing: Boolean = false, isDirectory: Boolean = false): DocumentFile? {
        val volId = getVolumeId(path) ?: return null
        val treeUri = getPersistedUriForVolume(context, volId) ?: return null
        
        val storagePrefix = "/storage/$volId"
        if (!path.startsWith(storagePrefix, ignoreCase = true)) return null
        
        val relativePath = path.substring(storagePrefix.length).trim('/')
        var doc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        if (relativePath.isEmpty()) {
            return doc
        }
        
        val segments = relativePath.split("/")
        for (i in segments.indices) {
            val segment = segments[i]
            if (segment.isEmpty()) continue
            var nextDoc = doc.findFile(segment)
            if (nextDoc == null) {
                if (createIfMissing) {
                    val isLast = (i == segments.size - 1)
                    nextDoc = if (isLast && !isDirectory) {
                        doc.createFile("*/*", segment)
                    } else {
                        doc.createDirectory(segment)
                    }
                } else {
                    return null
                }
            }
            doc = nextDoc ?: return null
        }
        return doc
    }
    
    // Check if a file or directory exists
    fun exists(context: Context, path: String): Boolean {
        if (!isSecondaryStoragePath(path)) {
            return File(path).exists()
        }
        return getDocumentFileForPath(context, path) != null
    }
    
    // Check if path is a directory
    fun isDirectory(context: Context, path: String): Boolean {
        if (!isSecondaryStoragePath(path)) {
            return File(path).isDirectory
        }
        return getDocumentFileForPath(context, path)?.isDirectory ?: false
    }
    
    // Get file length
    fun length(context: Context, path: String): Long {
        if (!isSecondaryStoragePath(path)) {
            return File(path).length()
        }
        return getDocumentFileForPath(context, path)?.length() ?: 0L
    }
    
    // Get list of files in directory
    fun listFiles(context: Context, path: String): List<String>? {
        if (!isSecondaryStoragePath(path)) {
            return File(path).listFiles()?.map { it.absolutePath }
        }
        val doc = getDocumentFileForPath(context, path) ?: return null
        if (!doc.isDirectory) return null
        return doc.listFiles().map { file ->
            if (path.endsWith("/")) "$path${file.name}" else "$path/${file.name}"
        }
    }

    // Create a directory using SAF
    fun mkdir(context: Context, path: String): Boolean {
        if (!isSecondaryStoragePath(path)) {
            return File(path).mkdirs()
        }
        return getDocumentFileForPath(context, path, createIfMissing = true, isDirectory = true) != null
    }

    // Create a new file using SAF
    fun createNewFile(context: Context, path: String): Boolean {
        if (!isSecondaryStoragePath(path)) {
            try {
                return File(path).createNewFile()
            } catch (e: Exception) {
                return false
            }
        }
        return getDocumentFileForPath(context, path, createIfMissing = true, isDirectory = false) != null
    }

    // Delete file or directory recursively using SAF
    fun delete(context: Context, path: String): Boolean {
        if (!isSecondaryStoragePath(path)) {
            return File(path).deleteRecursively()
        }
        val doc = getDocumentFileForPath(context, path) ?: return false
        return doc.delete()
    }

    // Rename file or directory using SAF
    fun rename(context: Context, oldPath: String, newName: String): Boolean {
        if (!isSecondaryStoragePath(oldPath)) {
            val oldFile = File(oldPath)
            val newFile = File(oldFile.parent, newName)
            return oldFile.renameTo(newFile)
        }
        val doc = getDocumentFileForPath(context, oldPath) ?: return false
        return doc.renameTo(newName)
    }

    // Get input stream for reading
    fun getInputStream(context: Context, path: String): InputStream? {
        if (!isSecondaryStoragePath(path)) {
            return try { FileInputStream(path) } catch (e: Exception) { null }
        }
        val doc = getDocumentFileForPath(context, path) ?: return null
        return try {
            context.contentResolver.openInputStream(doc.uri)
        } catch (e: Exception) {
            null
        }
    }

    // Get output stream for writing/appending
    fun getOutputStream(context: Context, path: String, append: Boolean = false): OutputStream? {
        if (!isSecondaryStoragePath(path)) {
            return try { FileOutputStream(path, append) } catch (e: Exception) { null }
        }
        val doc = getDocumentFileForPath(context, path, createIfMissing = true, isDirectory = false) ?: return null
        return try {
            val mode = if (append) "wa" else "w"
            context.contentResolver.openOutputStream(doc.uri, mode)
        } catch (e: Exception) {
            null
        }
    }

    // Copy file
    fun copyFile(context: Context, srcPath: String, destPath: String): Boolean {
        var input: java.io.InputStream? = null
        var output: java.io.OutputStream? = null
        return try {
            input = getInputStream(context, srcPath)
            if (input == null) return false
            
            output = getOutputStream(context, destPath)
            if (output == null) return false
            
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            try { input?.close() } catch (e: Exception) {}
            try { output?.close() } catch (e: Exception) {}
        }
    }

    // Copy directory recursively
    fun copyDirectory(context: Context, srcPath: String, destPath: String): Boolean {
        if (!exists(context, srcPath)) return false
        
        if (!mkdir(context, destPath)) return false
        
        val files = listFiles(context, srcPath) ?: return true
        for (nextSrc in files) {
            val fileName = nextSrc.substringAfterLast('/')
            val nextDest = if (destPath.endsWith("/")) "$destPath$fileName" else "$destPath/$fileName"
            
            val success = if (isDirectory(context, nextSrc)) {
                copyDirectory(context, nextSrc, nextDest)
            } else {
                copyFile(context, nextSrc, nextDest)
            }
            if (!success) return false
        }
        return true
    }

    // Generic copy (handles both files and directories)
    fun copy(context: Context, srcPath: String, destPath: String): Boolean {
        return if (isDirectory(context, srcPath)) {
            copyDirectory(context, srcPath, destPath)
        } else {
            copyFile(context, srcPath, destPath)
        }
    }
}
