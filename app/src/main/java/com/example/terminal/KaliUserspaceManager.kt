package com.example.terminal

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Professional Kali Linux ARM64 Userspace Environment Manager.
 * Handles detection, setup, and verification of real ARM64 Linux binaries (CPython, Nmap, APT, Bash).
 * When rootfs binaries are absent, honestly informs the user instead of pretending.
 */
class KaliUserspaceManager(private val context: Context) {

    val kaliRootDir: File = File(context.filesDir, "kali")
    val usrBinDir: File = File(kaliRootDir, "usr/bin")
    val binDir: File = File(kaliRootDir, "bin")

    val isRootfsInstalled: Boolean
        get() = (File(usrBinDir, "bash").exists() || File(binDir, "sh").exists() || File(usrBinDir, "sh").exists())

    /**
     * Looks up an authentic executable binary in Kali rootfs or system paths.
     */
    fun findBinary(binaryName: String): String? {
        val rootfsCandidates = listOf(
            File(usrBinDir, binaryName),
            File(binDir, binaryName),
            File(kaliRootDir, "usr/sbin/$binaryName"),
            File(kaliRootDir, "sbin/$binaryName")
        )
        for (f in rootfsCandidates) {
            if (f.exists() && f.canExecute()) {
                return f.absolutePath
            }
        }

        val systemCandidates = listOf(
            File(context.filesDir, "usr/bin/$binaryName").absolutePath,
            File(context.filesDir, "bin/$binaryName").absolutePath,
            "/system/bin/$binaryName",
            "/system/xbin/$binaryName"
        )
        for (p in systemCandidates) {
            val f = File(p)
            if (f.exists() && f.canExecute()) {
                return p
            }
        }
        return null
    }

    /**
     * Status diagnostic for Kali Userspace.
     */
    fun getStatusDiagnostic(): String {
        val sb = StringBuilder()
        sb.append("============================================================\n")
        sb.append("         KALI LINUX ARM64 USERSPACE ENVIRONMENT\n")
        sb.append("============================================================\n")
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        sb.append("• Host Architecture : $primaryAbi (Android ${Build.VERSION.RELEASE})\n")
        sb.append("• Rootfs Location   : ${kaliRootDir.absolutePath}\n")
        sb.append("• Rootfs Status     : ${if (isRootfsInstalled) "INSTALLED (Active)" else "NOT DEPLOYED"}\n")

        val pythonBin = findBinary("python3") ?: findBinary("python")
        val nmapBin = findBinary("nmap")
        val aptBin = findBinary("apt") ?: findBinary("apt-get")
        val bashBin = findBinary("bash")

        sb.append("• Python 3 (CPython): ${pythonBin ?: "Not installed in OS"}\n")
        sb.append("• Nmap Network Tool : ${nmapBin ?: "Not installed in OS"}\n")
        sb.append("• APT Package Mgr   : ${aptBin ?: "Not installed in OS"}\n")
        sb.append("• Bash Shell        : ${bashBin ?: "Not installed in OS"}\n")

        if (!isRootfsInstalled) {
            sb.append("\n[*] Userspace Initialization Guide:\n")
            sb.append("  kali deploy <fayl.tar.xz> - Deploy Kali ARM64 rootfs archive automatically\n")
            sb.append("  unxz <fayl.tar.xz>         - Decompress .xz archive to .tar\n")
            sb.append("  tar -xvf <fayl.tar>        - Extract tar archive contents\n")
            sb.append("  kali init                  - Create directory hierarchy for Kali ARM64 rootfs\n")
            sb.append("  kali status                - Check status of installed packages\n")
        }
        sb.append("============================================================")
        return sb.toString()
    }

    suspend fun deployRootfs(
        archiveFile: File,
        onProgress: (suspend (ArchiveEngine.ProgressState) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!archiveFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Arxiv topilmadi: ${archiveFile.absolutePath}"))
            }

            initializeHierarchy()
            val result = ArchiveEngine.extractTarArchive(archiveFile, kaliRootDir, onProgress)
            
            if (result.isSuccess) {
                // Ensure executable permissions on essential binary folders
                listOf(usrBinDir, binDir, File(kaliRootDir, "usr/sbin"), File(kaliRootDir, "sbin")).forEach { dir ->
                    dir.listFiles()?.forEach { file ->
                        file.setExecutable(true, false)
                        file.setReadable(true, false)
                    }
                }
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Follows HTTP/HTTPS redirects across different hosts.
     */
    private fun openStreamWithRedirects(initialUrl: String, maxRedirects: Int = 6): Pair<java.net.HttpURLConnection, java.io.InputStream> {
        var currentUrl = initialUrl
        for (i in 0 until maxRedirects) {
            val url = java.net.URL(currentUrl)
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 60000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "SuperTerminalManager/1.0 (Android ${Build.VERSION.RELEASE}; ${Build.SUPPORTED_ABIS.firstOrNull()})")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: throw IllegalStateException("Redirect without Location header")
                currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                    location
                } else {
                    java.net.URL(url, location).toString()
                }
                conn.disconnect()
                continue
            }
            if (code in 200..299) {
                return Pair(conn, conn.inputStream)
            }
            throw IllegalStateException("HTTP server javobi: $code ${conn.responseMessage}")
        }
        throw IllegalStateException("Juda ko'p redirectlar ($maxRedirects)")
    }

    /**
     * Downloads and installs the authentic ARM64 Kali Linux minimal root filesystem automatically.
     */
    suspend fun downloadAndInstallKaliRootfs(
        onProgress: (suspend (String) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            val archSuffix = when {
                primaryAbi.startsWith("arm64") || primaryAbi.contains("aarch64") -> "arm64"
                primaryAbi.startsWith("arm") -> "armhf"
                primaryAbi.contains("x86_64") -> "x86_64"
                primaryAbi.contains("x86") -> "i386"
                else -> "arm64"
            }

            val urls = listOf(
                "https://raw.githubusercontent.com/EXALAB/AnLinux-Resources/master/Rootfs/Kali/$archSuffix/kali-rootfs-$archSuffix.tar.xz",
                "https://github.com/EXALAB/AnLinux-Resources/raw/master/Rootfs/Kali/$archSuffix/kali-rootfs-$archSuffix.tar.xz"
            )

            val downloadDir = File(context.cacheDir, "rootfs_downloads").apply { mkdirs() }
            val archiveFile = File(downloadDir, "kali-rootfs-$archSuffix.tar.xz")

            var downloaded = false
            var lastError: Exception? = null

            for (urlStr in urls) {
                try {
                    onProgress?.invoke("[*] Kali Linux rootfs arxivi yuklanmoqda ($archSuffix)...")
                    val (conn, stream) = openStreamWithRedirects(urlStr)
                    val totalBytes = conn.contentLengthLong
                    var downloadedBytes = 0L
                    var lastReportPercent = -1

                    stream.use { input ->
                        java.io.FileOutputStream(archiveFile).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloadedBytes += read
                                if (totalBytes > 0) {
                                    val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                                    if (percent != lastReportPercent && percent % 10 == 0) {
                                        lastReportPercent = percent
                                        val mbDown = downloadedBytes / (1024 * 1024)
                                        val mbTot = totalBytes / (1024 * 1024)
                                        onProgress?.invoke("[*] Yuklanmoqda: $percent% ($mbDown MB / $mbTot MB)")
                                    }
                                }
                            }
                        }
                    }

                    if (archiveFile.exists() && archiveFile.length() > 500000) {
                        downloaded = true
                        break
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }

            if (!downloaded || !archiveFile.exists()) {
                return@withContext Result.failure(lastError ?: IllegalStateException("Kali Rootfs arxivini yuklab bo'lmadi."))
            }

            onProgress?.invoke("[*] Arxiv muvaffaqiyatli yuklandi! Tizim fayllari ochilmoqda...")
            var lastEntryCount = 0
            val extractRes = deployRootfs(archiveFile) { prog ->
                if (prog.entriesProcessed - lastEntryCount >= 400) {
                    lastEntryCount = prog.entriesProcessed
                    onProgress?.invoke("[...] Fayllar o'rnatilmoqda (${prog.entriesProcessed} ta): ${prog.currentEntryName.takeLast(30)}")
                }
            }

            try { archiveFile.delete() } catch (_: Exception) {}

            extractRes
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads and installs an ultra-fast lightweight Alpine Linux rootfs (only 3.2 MB, installs in 3 seconds!).
     */
    suspend fun downloadAndInstallAlpineRootfs(
        targetDir: File = File(context.filesDir, "distros/alpine"),
        onProgress: (suspend (String) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            val alpineArch = when {
                primaryAbi.startsWith("arm64") || primaryAbi.contains("aarch64") -> "aarch64"
                primaryAbi.startsWith("arm") -> "armhf"
                primaryAbi.contains("x86_64") -> "x86_64"
                primaryAbi.contains("x86") -> "x86"
                else -> "aarch64"
            }
            val urlStr = "https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/$alpineArch/alpine-minirootfs-3.19.1-$alpineArch.tar.gz"
            onProgress?.invoke("[*] Tezkor Alpine Linux Mini-Rootfs (3.2 MB) yuklanmoqda...")
            val (conn, stream) = openStreamWithRedirects(urlStr)
            val downloadDir = File(context.cacheDir, "rootfs_downloads").apply { mkdirs() }
            val archiveFile = File(downloadDir, "alpine-minirootfs.tar.gz")

            stream.use { input ->
                java.io.FileOutputStream(archiveFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                }
            }

            targetDir.mkdirs()
            onProgress?.invoke("[*] Alpine Linux tizim fayllari ochilmoqda...")
            val res = ArchiveEngine.extractTarArchive(archiveFile, targetDir)
            try { archiveFile.delete() } catch (_: Exception) {}
            res
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun initializeHierarchy(): String = withContext(Dispatchers.IO) {
        kaliRootDir.mkdirs()
        File(kaliRootDir, "bin").mkdirs()
        File(kaliRootDir, "sbin").mkdirs()
        File(kaliRootDir, "usr/bin").mkdirs()
        File(kaliRootDir, "usr/sbin").mkdirs()
        File(kaliRootDir, "usr/lib").mkdirs()
        File(kaliRootDir, "etc/apt").mkdirs()
        File(kaliRootDir, "home").mkdirs()
        File(kaliRootDir, "tmp").mkdirs()
        "[Kali Manager] Rootfs directory hierarchy created at:\n${kaliRootDir.absolutePath}\nDeploy Debian/Kali ARM64 rootfs tarball to populate binaries."
    }
}
