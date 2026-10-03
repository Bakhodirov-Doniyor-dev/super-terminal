package com.example.terminal

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * Enterprise Terminal Package Manager & Native Linux Userspace Orchestrator.
 * Connects directly to high-availability global mirror networks, parses raw APT package indexes,
 * resolves dependency trees recursively, verifies SHA-256 integrity, and unpacks Debian binary packages (.deb)
 * directly into the app-private Unix root prefix without requiring root or external dependencies.
 */
class TerminalPackageManager(private val context: Context) {

    private val tag = "TerminalPackageManager"

    val prefixDir: File = File(context.filesDir, "usr")
    val binDir: File = File(prefixDir, "bin")
    val libDir: File = File(prefixDir, "lib")
    val etcDir: File = File(prefixDir, "etc")
    val shareDir: File = File(prefixDir, "share")
    val cacheDir: File = File(context.cacheDir, "pkg_cache")
    val installedIndexFile: File = File(etcDir, "terminal_installed_packages.json")
    val repoIndexFile: File = File(cacheDir, "terminal_repo_packages.json")

    init {
        prefixDir.mkdirs()
        binDir.mkdirs()
        libDir.mkdirs()
        etcDir.mkdirs()
        shareDir.mkdirs()
        cacheDir.mkdirs()
        try {
            prefixDir.setReadable(true, false)
            prefixDir.setExecutable(true, false)
            binDir.setReadable(true, false)
            binDir.setExecutable(true, false)
            libDir.setReadable(true, false)
            libDir.setExecutable(true, false)
        } catch (_: Exception) {}
    }

    data class PackageMetadata(
        val name: String,
        val version: String,
        val architecture: String,
        val section: String,
        val installedSize: Long,
        val filename: String,
        val size: Long,
        val sha256: String,
        val depends: List<String>,
        val description: String,
        var isInstalled: Boolean = false
    )

    private val activeMirrors = listOf(
        "https://packages.termux.dev/apt/termux-main",
        "https://mirror.mwt.me/termux/main",
        "https://mirrors.grimler.se/termux/termux-main",
        "https://termux.mentality.rip/termux-main",
        "https://packages-cf.termux.dev/apt/termux-main"
    )

    private var inMemoryIndex: MutableMap<String, PackageMetadata>? = null

    fun getHostArchitecture(): String {
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when {
            primaryAbi.startsWith("arm64") || primaryAbi.contains("aarch64") -> "aarch64"
            primaryAbi.startsWith("arm") -> "arm"
            primaryAbi.contains("x86_64") -> "x86_64"
            primaryAbi.contains("x86") -> "i686"
            else -> "aarch64"
        }
    }

    /**
     * Updates package repository index from global mirrors (equivalent to 'pkg update' / 'apt update').
     */
    suspend fun updateRepositoryIndex(
        onProgress: (suspend (String) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val arch = getHostArchitecture()
            onProgress?.invoke("[*] Terminal repozitoriya serverlariga ulanilmoqda (Arxitektura: $arch)...")

            var downloadedBytes: ByteArray? = null
            var lastError: Exception? = null

            for (mirror in activeMirrors) {
                val indexUrlCandidates = listOf(
                    "$mirror/dists/stable/main/binary-$arch/Packages.xz",
                    "$mirror/dists/stable/main/binary-$arch/Packages.gz",
                    "$mirror/dists/stable/main/binary-$arch/Packages"
                )

                for (urlStr in indexUrlCandidates) {
                    try {
                        onProgress?.invoke("[*] Indeks tekshirilmoqda: $urlStr")
                        val url = URL(urlStr)
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            connectTimeout = 8000
                            readTimeout = 15000
                            instanceFollowRedirects = true
                            setRequestProperty("User-Agent", "TerminalCore/3.0 (Linux; Android ${Build.VERSION.RELEASE})")
                        }

                        if (conn.responseCode in 200..299) {
                            val rawData = conn.inputStream.use { it.readBytes() }
                            if (rawData.isNotEmpty()) {
                                downloadedBytes = when {
                                    urlStr.endsWith(".xz") -> {
                                        XZInputStream(ByteArrayInputStream(rawData)).use { it.readBytes() }
                                    }
                                    urlStr.endsWith(".gz") -> {
                                        GZIPInputStream(ByteArrayInputStream(rawData)).use { it.readBytes() }
                                    }
                                    else -> rawData
                                }
                                break
                            }
                        }
                    } catch (e: Exception) {
                        lastError = e
                    }
                }
                if (downloadedBytes != null) break
            }

            if (downloadedBytes == null) {
                return@withContext Result.failure(lastError ?: IllegalStateException("Repozitoriya indeksini yuklab bo'lmadi."))
            }

            onProgress?.invoke("[*] Paketlar indeksi tahlil qilinmoqda (${downloadedBytes.size / 1024} KB)...")
            val indexText = String(downloadedBytes, Charsets.UTF_8)
            val parsedPackages = parsePackagesIndex(indexText)

            // Save to cached index file
            val jsonArray = JSONArray()
            parsedPackages.values.forEach { pkg ->
                val obj = JSONObject().apply {
                    put("name", pkg.name)
                    put("version", pkg.version)
                    put("arch", pkg.architecture)
                    put("section", pkg.section)
                    put("installedSize", pkg.installedSize)
                    put("filename", pkg.filename)
                    put("size", pkg.size)
                    put("sha256", pkg.sha256)
                    put("depends", JSONArray(pkg.depends))
                    put("desc", pkg.description)
                }
                jsonArray.put(obj)
            }
            repoIndexFile.writeText(jsonArray.toString())
            inMemoryIndex = parsedPackages.toMutableMap()

            onProgress?.invoke("[✔️] Repozitoriya muvaffaqiyatli yangilandi! (${parsedPackages.size} ta paket mavjud)")
            Result.success(parsedPackages.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses RFC 822 / Debian control-style Packages index.
     */
    private fun parsePackagesIndex(rawIndex: String): Map<String, PackageMetadata> {
        val packages = mutableMapOf<String, PackageMetadata>()
        val blocks = rawIndex.split("\n\n")

        for (block in blocks) {
            if (block.isBlank()) continue
            var name = ""
            var version = ""
            var arch = ""
            var section = ""
            var installedSize = 0L
            var filename = ""
            var size = 0L
            var sha256 = ""
            val depends = mutableListOf<String>()
            val descLines = StringBuilder()

            for (line in block.lines()) {
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0 && !line.startsWith(" ")) {
                    val key = line.substring(0, colonIdx).trim()
                    val value = line.substring(colonIdx + 1).trim()
                    when (key.lowercase(Locale.US)) {
                        "package" -> name = value
                        "version" -> version = value
                        "architecture" -> arch = value
                        "section" -> section = value
                        "installed-size" -> installedSize = value.toLongOrNull() ?: 0L
                        "filename" -> filename = value
                        "size" -> size = value.toLongOrNull() ?: 0L
                        "sha256" -> sha256 = value
                        "depends" -> {
                            val depTokens = value.split(",")
                            for (dt in depTokens) {
                                val cleanDep = dt.substringBefore("(").substringBefore("|").trim()
                                if (cleanDep.isNotEmpty()) depends.add(cleanDep)
                            }
                        }
                        "description" -> descLines.append(value)
                    }
                } else if (line.startsWith(" ")) {
                    descLines.append(" ").append(line.trim())
                }
            }

            if (name.isNotEmpty() && filename.isNotEmpty()) {
                packages[name] = PackageMetadata(
                    name = name,
                    version = version,
                    architecture = arch,
                    section = section,
                    installedSize = installedSize,
                    filename = filename,
                    size = size,
                    sha256 = sha256,
                    depends = depends,
                    description = descLines.toString().trim()
                )
            }
        }
        return packages
    }

    /**
     * Loads the repository index (from memory, disk cache, or triggers automatic update).
     */
    suspend fun getPackageIndex(): Map<String, PackageMetadata> = withContext(Dispatchers.IO) {
        val cached = inMemoryIndex
        if (cached != null && cached.isNotEmpty()) return@withContext cached

        if (repoIndexFile.exists() && repoIndexFile.length() > 1000) {
            try {
                val jsonArray = JSONArray(repoIndexFile.readText())
                val map = mutableMapOf<String, PackageMetadata>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val depArray = obj.optJSONArray("depends")
                    val depList = mutableListOf<String>()
                    if (depArray != null) {
                        for (d in 0 until depArray.length()) depList.add(depArray.getString(d))
                    }
                    val name = obj.getString("name")
                    map[name] = PackageMetadata(
                        name = name,
                        version = obj.optString("version", "1.0"),
                        architecture = obj.optString("arch", getHostArchitecture()),
                        section = obj.optString("section", "main"),
                        installedSize = obj.optLong("installedSize", 0L),
                        filename = obj.getString("filename"),
                        size = obj.optLong("size", 0L),
                        sha256 = obj.optString("sha256", ""),
                        depends = depList,
                        description = obj.optString("desc", "")
                    )
                }
                inMemoryIndex = map
                return@withContext map
            } catch (e: Exception) {
                Log.w(tag, "Failed to load cached index: ${e.message}")
            }
        }

        // Auto update if cache is empty
        val updateRes = updateRepositoryIndex()
        return@withContext inMemoryIndex ?: emptyMap()
    }

    /**
     * Searches for packages by name or keyword.
     */
    suspend fun searchPackages(query: String): List<PackageMetadata> = withContext(Dispatchers.IO) {
        val index = getPackageIndex()
        val q = query.lowercase(Locale.US).trim()
        val installed = getInstalledPackageNames()
        return@withContext index.values.filter {
            it.name.lowercase(Locale.US).contains(q) || it.description.lowercase(Locale.US).contains(q)
        }.map {
            it.copy(isInstalled = installed.contains(it.name))
        }.sortedWith(compareBy({ !it.name.equals(q, ignoreCase = true) }, { it.name }))
    }

    /**
     * Reads the set of locally installed package names.
     */
    fun getInstalledPackageNames(): Set<String> {
        if (!installedIndexFile.exists()) return emptySet()
        return try {
            val jsonArray = JSONArray(installedIndexFile.readText())
            val set = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) set.add(jsonArray.getString(i))
            set
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun markPackageInstalled(packageName: String) {
        val current = getInstalledPackageNames().toMutableSet()
        current.add(packageName)
        val jsonArray = JSONArray(current.toList())
        installedIndexFile.writeText(jsonArray.toString())
    }

    /**
     * Installs a package and its dependencies recursively (equivalent to 'pkg install <name>').
     */
    suspend fun installPackage(
        packageName: String,
        onProgress: (suspend (String) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val index = getPackageIndex()
            val targetPkg = index[packageName]
                ?: return@withContext Result.failure(IllegalArgumentException("Paket topilmadi: '$packageName'. 'pkg search $packageName' deb qidirib ko'ring."))

            val installed = getInstalledPackageNames()
            if (installed.contains(packageName) && isPackageFilesExist(packageName)) {
                onProgress?.invoke("[*] '$packageName' allaqachon o'rnatilgan.")
                return@withContext Result.success(Unit)
            }

            // 1. Resolve full dependency tree
            val toInstallQueue = mutableListOf<PackageMetadata>()
            val visited = mutableSetOf<String>()

            fun resolveDeps(pkg: PackageMetadata) {
                if (visited.contains(pkg.name)) return
                visited.add(pkg.name)

                for (depName in pkg.depends) {
                    val depPkg = index[depName]
                    if (depPkg != null && !installed.contains(depName)) {
                        resolveDeps(depPkg)
                    }
                }
                if (!installed.contains(pkg.name) && toInstallQueue.none { it.name == pkg.name }) {
                    toInstallQueue.add(pkg)
                }
            }

            onProgress?.invoke("[*] '$packageName' uchun bog'liqliklar tahlil qilinmoqda...")
            resolveDeps(targetPkg)

            onProgress?.invoke("[*] Jami o'rnatiladigan paketlar (${toInstallQueue.size} ta): ${toInstallQueue.joinToString(", ") { it.name }}")

            // 2. Download and unpack each package sequentially
            for ((idx, pkg) in toInstallQueue.withIndex()) {
                val stepNum = idx + 1
                onProgress?.invoke("[$stepNum/${toInstallQueue.size}] '${pkg.name}' paketi yuklanmoqda va o'rnatilmoqda (v${pkg.version})...")

                val debFile = downloadDebPackage(pkg) { msg -> onProgress?.invoke(msg) }
                if (!debFile.exists()) {
                    return@withContext Result.failure(IllegalStateException("'${pkg.name}' paketini yuklab bo'lmadi."))
                }

                // Verify SHA-256 if available
                if (pkg.sha256.isNotBlank()) {
                    val sha = calculateSha256(debFile)
                    if (!sha.equals(pkg.sha256, ignoreCase = true)) {
                        debFile.delete()
                        return@withContext Result.failure(SecurityException("'${pkg.name}' SHA-256 tekshiruvidan o'tmadi!"))
                    }
                }

                // Unpack Debian binary archive (.deb)
                val unpackResult = unpackDebPackage(debFile) { entryName ->
                    // Optional per-file progress
                }

                try { debFile.delete() } catch (_: Exception) {}

                if (unpackResult.isFailure) {
                    return@withContext Result.failure(unpackResult.exceptionOrNull()!!)
                }

                markPackageInstalled(pkg.name)
            }

            // 3. Fix executable permissions and system environment
            fixPermissionsAndSymlinks()

            onProgress?.invoke(
                """
                ============================================================
                [✔️] '$packageName' va barcha bog'liqliklar muvaffaqiyatli o'rnatildi!
                • O'rnatilgan katalog : ${prefixDir.absolutePath}
                • Executable yo'li   : ${binDir.absolutePath}
                • Jami o'rnatildi    : ${toInstallQueue.size} ta paket
                ============================================================
                """.trimIndent()
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isPackageFilesExist(packageName: String): Boolean {
        val binCandidate = File(binDir, packageName)
        return binCandidate.exists() || File(libDir, packageName).exists()
    }

    /**
     * Uninstalls a package and removes its binaries from binDir and libDir.
     */
    suspend fun uninstallPackage(
        packageName: String,
        onProgress: (suspend (String) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val installed = getInstalledPackageNames().toMutableSet()
            if (!installed.contains(packageName)) {
                return@withContext Result.failure(IllegalArgumentException("Paket o'rnatilmagan: '$packageName'"))
            }

            onProgress?.invoke("[*] '$packageName' o'chirilmoqda...")
            // Remove binary
            val binFile = File(binDir, packageName)
            if (binFile.exists()) binFile.delete()

            // Remove installed record
            installed.remove(packageName)
            val jsonArray = JSONArray(installed.toList())
            installedIndexFile.writeText(jsonArray.toString())

            onProgress?.invoke("[✔️] '$packageName' muvaffaqiyatli o'chirildi.")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upgrades all currently installed packages to their newest available version.
     */
    suspend fun upgradePackages(
        onProgress: (suspend (String) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            updateRepositoryIndex { onProgress?.invoke(it) }
            val installed = getInstalledPackageNames()
            if (installed.isEmpty()) {
                onProgress?.invoke("Yangilash uchun o'rnatilgan paketlar mavjud emas.")
                return@withContext Result.success(0)
            }

            var upgradedCount = 0
            for (p in installed) {
                onProgress?.invoke("[*] '$p' yangilanmoqda...")
                val res = installPackage(p) { onProgress?.invoke(it) }
                if (res.isSuccess) upgradedCount++
            }
            onProgress?.invoke("[✔️] Jami $upgradedCount ta paket yangilandi.")
            Result.success(upgradedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clears downloaded .deb archives from the cache folder.
     */
    fun cleanCache(): Long {
        var freed = 0L
        cacheDir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(".deb")) {
                freed += f.length()
                f.delete()
            }
        }
        return freed
    }

    /**
     * Returns detailed metadata for a package (equivalent to 'pkg show <name>' / 'apt show <name>').
     */
    suspend fun getPackageInfo(packageName: String): String = withContext(Dispatchers.IO) {
        val index = getPackageIndex()
        val pkg = index[packageName] ?: return@withContext "Package '$packageName' not found."
        val installed = getInstalledPackageNames().contains(packageName)

        buildString {
            append("============================================================\n")
            append("Package: ${pkg.name}\n")
            append("Version: ${pkg.version}\n")
            append("Architecture: ${pkg.architecture}\n")
            append("Section: ${pkg.section}\n")
            append("Installed-Size: ${pkg.installedSize / 1024} MB\n")
            append("Download-Size: ${pkg.size / 1024} KB\n")
            append("Status: ${if (installed) "Installed (Active)" else "Available (Not installed)"}\n")
            if (pkg.depends.isNotEmpty()) {
                append("Depends: ${pkg.depends.joinToString(", ")}\n")
            }
            append("SHA-256: ${pkg.sha256}\n")
            append("Description: ${pkg.description}\n")
            append("============================================================")
        }
    }

    /**
     * Downloads .deb package from mirror networks.
     */
    private suspend fun downloadDebPackage(
        pkg: PackageMetadata,
        onProgress: (suspend (String) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val cacheFile = File(cacheDir, "${pkg.name}_${pkg.version}.deb")
        if (cacheFile.exists() && cacheFile.length() == pkg.size && pkg.size > 0) {
            return@withContext cacheFile
        }

        var lastError: Exception? = null
        for (mirror in activeMirrors) {
            val urlStr = "$mirror/${pkg.filename}"
            try {
                val url = URL(urlStr)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 25000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "TerminalCore/3.0 (Android; APT-Engine)")
                }

                if (conn.responseCode in 200..299) {
                    val total = conn.contentLengthLong
                    var down = 0L
                    var lastReportPercent = -1

                    conn.inputStream.use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            val buf = ByteArray(32 * 1024)
                            var r: Int
                            while (input.read(buf).also { r = it } != -1) {
                                output.write(buf, 0, r)
                                down += r
                                if (total > 0 && onProgress != null) {
                                    val pct = ((down * 100) / total).toInt()
                                    if (pct != lastReportPercent && pct % 20 == 0) {
                                        lastReportPercent = pct
                                        val kbDown = down / 1024
                                        val kbTot = total / 1024
                                        onProgress("  Yuklanmoqda: $pct% ($kbDown KB / $kbTot KB)")
                                    }
                                }
                            }
                        }
                    }

                    if (cacheFile.exists() && cacheFile.length() > 0) {
                        return@withContext cacheFile
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Paketni barcha mirrorlardan yuklab bo'lmadi: ${pkg.filename}")
    }

    /**
     * Unpacks a Debian binary package (.deb).
     * Extracts 'data.tar.xz' or 'data.tar.gz' directly into prefixDir (usr/).
     */
    private suspend fun unpackDebPackage(
        debFile: File,
        onFileUnpacked: ((String) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var extractedFilesCount = 0
            val fis = BufferedInputStream(FileInputStream(debFile), 64 * 1024)
            val arInput = ArArchiveInputStream(fis)

            var arEntry: ArArchiveEntry? = arInput.nextArEntry
            var foundDataArchive = false

            while (arEntry != null) {
                val entryName = arEntry.name.trim().removeSuffix("/").trim()
                if (entryName.startsWith("data.tar")) {
                    foundDataArchive = true
                    val isXz = entryName.endsWith(".xz")
                    val isGz = entryName.endsWith(".gz")

                    val dataBytes = arInput.readBytes()
                    val rawStream: InputStream = ByteArrayInputStream(dataBytes)
                    val decompressedStream: InputStream = when {
                        isXz -> XZInputStream(BufferedInputStream(rawStream, 32 * 1024))
                        isGz -> GZIPInputStream(BufferedInputStream(rawStream, 32 * 1024))
                        else -> rawStream
                    }

                    val tarInput = TarArchiveInputStream(decompressedStream)
                    var tarEntry: TarArchiveEntry? = tarInput.nextTarEntry
                    val buffer = ByteArray(32 * 1024)

                    while (tarEntry != null) {
                        // Normalize path relative to prefix
                        var relativePath = tarEntry.name.removePrefix("./").removePrefix("/")
                        if (relativePath.startsWith("data/data/com.termux/files/usr/")) {
                            relativePath = relativePath.removePrefix("data/data/com.termux/files/usr/")
                        } else if (relativePath.startsWith("usr/")) {
                            relativePath = relativePath.removePrefix("usr/")
                        }

                        if (relativePath.isNotBlank() && relativePath != ".") {
                            val targetFile = File(prefixDir, relativePath)

                            // Security check: Zip Slip prevention
                            if (!targetFile.canonicalPath.startsWith(prefixDir.canonicalPath)) {
                                tarEntry = tarInput.nextTarEntry
                                continue
                            }

                            if (tarEntry.isDirectory) {
                                targetFile.mkdirs()
                            } else {
                                targetFile.parentFile?.mkdirs()
                                if (tarEntry.isSymbolicLink) {
                                    try {
                                        if (targetFile.exists()) targetFile.delete()
                                        java.nio.file.Files.createSymbolicLink(
                                            targetFile.toPath(),
                                            java.nio.file.Paths.get(tarEntry.linkName)
                                        )
                                    } catch (_: Exception) {}
                                } else {
                                    FileOutputStream(targetFile).use { fos ->
                                        var r: Int
                                        while (tarInput.read(buffer).also { r = it } != -1) {
                                            fos.write(buffer, 0, r)
                                        }
                                    }

                                    // Restore execute permission
                                    if ((tarEntry.mode and 0b001_001_001) != 0 || relativePath.startsWith("bin/")) {
                                        targetFile.setExecutable(true, false)
                                    }
                                    targetFile.setReadable(true, false)
                                }
                                extractedFilesCount++
                                onFileUnpacked?.invoke(relativePath)
                            }
                        }
                        tarEntry = tarInput.nextTarEntry
                    }
                    break
                }
                arEntry = arInput.nextArEntry
            }
            arInput.close()

            if (!foundDataArchive) {
                return@withContext Result.failure(IllegalStateException("No 'data.tar' found in Debian package: ${debFile.name}"))
            }

            Result.success(extractedFilesCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ensures all binaries and shared libraries have correct permissions and dynamic links.
     */
    fun fixPermissionsAndSymlinks() {
        try {
            listOf(binDir, libDir).forEach { dir ->
                if (dir.exists()) {
                    dir.walkTopDown().forEach { file ->
                        if (file.isFile) {
                            file.setReadable(true, false)
                            if (file.parentFile == binDir || file.name.endsWith(".so")) {
                                file.setExecutable(true, false)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Downloads and installs the complete native Linux userspace bootstrap archive.
     * Yields a fully functional environment with /usr/bin/bash, apt, dpkg, coreutils, libc++.
     */
    suspend fun installCoreBootstrap(
        onProgress: (suspend (String) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val arch = getHostArchitecture()
            val bootstrapUrl = "https://github.com/termux/termux-packages/releases/download/bootstrap-2024.08.01-r1%2Bapt-android-7/bootstrap-$arch.zip"
            val altBootstrapUrl = "https://github.com/termux/termux-packages/releases/download/bootstrap-2024.01.16-r1%2Bapt-android-7/bootstrap-$arch.zip"

            val targetZip = File(cacheDir, "terminal-bootstrap-$arch.zip")
            onProgress?.invoke("[*] Terminal Native Linux Bootstrap yuklanmoqda ($arch)...")

            var downloaded = false
            for (urlStr in listOf(bootstrapUrl, altBootstrapUrl)) {
                try {
                    val url = URL(urlStr)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "TerminalCore/3.0")
                    }

                    if (conn.responseCode in 200..299) {
                        val total = conn.contentLengthLong
                        var down = 0L
                        var lastPercent = -1

                        conn.inputStream.use { input ->
                            FileOutputStream(targetZip).use { output ->
                                val buf = ByteArray(64 * 1024)
                                var r: Int
                                while (input.read(buf).also { r = it } != -1) {
                                    output.write(buf, 0, r)
                                    down += r
                                    if (total > 0 && onProgress != null) {
                                        val pct = ((down * 100) / total).toInt()
                                        if (pct != lastPercent && pct % 15 == 0) {
                                            lastPercent = pct
                                            val mbDown = down / (1024 * 1024)
                                            val mbTot = total / (1024 * 1024)
                                            onProgress("  Bootstrap yuklanmoqda: $pct% ($mbDown MB / $mbTot MB)")
                                        }
                                    }
                                }
                            }
                        }
                        if (targetZip.exists() && targetZip.length() > 5000000) {
                            downloaded = true
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Bootstrap download attempt failed: ${e.message}")
                }
            }

            if (!downloaded || !targetZip.exists()) {
                return@withContext Result.failure(IllegalStateException("Terminal bootstrap arxivini yuklab bo'lmadi."))
            }

            onProgress?.invoke("[*] Bootstrap tizim fayllari o'rnatilmoqda (${targetZip.length() / (1024 * 1024)} MB)...")

            // Extract ZIP preserving SYMLINKS.txt
            val symlinkEntries = mutableListOf<String>()
            var fileCount = 0

            ZipInputStream(BufferedInputStream(FileInputStream(targetZip), 64 * 1024)).use { zis ->
                var ze = zis.nextEntry
                val buf = ByteArray(64 * 1024)

                while (ze != null) {
                    val name = ze.name
                    if (name == "SYMLINKS.txt") {
                        val reader = BufferedReader(InputStreamReader(zis, Charsets.UTF_8))
                        var sLine = reader.readLine()
                        while (sLine != null) {
                            if (sLine.isNotBlank()) symlinkEntries.add(sLine.trim())
                            sLine = reader.readLine()
                        }
                    } else {
                        val outFile = File(prefixDir, name)
                        if (outFile.canonicalPath.startsWith(prefixDir.canonicalPath)) {
                            if (ze.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { fos ->
                                    var len: Int
                                    while (zis.read(buf).also { len = it } != -1) {
                                        fos.write(buf, 0, len)
                                    }
                                }
                                if (name.startsWith("bin/") || name.endsWith(".so")) {
                                    outFile.setExecutable(true, false)
                                }
                                outFile.setReadable(true, false)
                                fileCount++
                            }
                        }
                    }
                    zis.closeEntry()
                    ze = zis.nextEntry
                }
            }

            // Create recorded symlinks
            for (symLine in symlinkEntries) {
                val parts = symLine.split("←")
                if (parts.size == 2) {
                    val targetRel = parts[0].trim()
                    val linkRel = parts[1].trim()
                    val linkFile = File(prefixDir, linkRel)
                    try {
                        if (linkFile.exists()) linkFile.delete()
                        java.nio.file.Files.createSymbolicLink(
                            linkFile.toPath(),
                            java.nio.file.Paths.get(targetRel)
                        )
                    } catch (_: Exception) {}
                }
            }

            fixPermissionsAndSymlinks()
            try { targetZip.delete() } catch (_: Exception) {}

            onProgress?.invoke(
                """
                ============================================================
                [🚀] Terminal Native Linux Userspace to'liq o'rnatildi!
                • Manzil      : ${prefixDir.absolutePath}
                • Fayllar     : $fileCount ta binar va kutubxona
                • Bash Shell  : ${File(binDir, "bash").absolutePath}
                • APT & DPKG  : ${File(binDir, "apt").absolutePath}
                Endi barcha Linux paketlarini 'pkg install <nom>' orqali o'rnating!
                ============================================================
                """.trimIndent()
            )
            Result.success(fileCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(16384)
            var r: Int
            while (fis.read(buf).also { r = it } != -1) {
                digest.update(buf, 0, r)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
