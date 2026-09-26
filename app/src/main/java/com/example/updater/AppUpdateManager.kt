package com.example.updater

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.adb.AdbClientHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Professional In-App OTA Update Engine (Self-Updater).
 * Allows the application to check, download, and install new versions directly on the device
 * using high-performance OkHttp streaming with zero-hang redirects, progress tracking,
 * and seamless PackageInstaller integration.
 */
class AppUpdateManager(private val context: Context) {

    private val tag = "AppUpdateManager"
    private val prefs = context.getSharedPreferences("app_update_prefs", Context.MODE_PRIVATE)

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        const val PREF_CUSTOM_UPDATE_URL = "custom_update_url"
        const val PREF_AUTO_CHECK = "auto_check_updates"
        const val PREF_LAST_CHECK_TIME = "last_check_time"

        // GitHub metadata update endpoints for Bakhodirov-Doniyor-dev/super-terminal
        const val DEFAULT_UPDATE_URL = "https://raw.githubusercontent.com/Bakhodirov-Doniyor-dev/super-terminal/main/version.json"
        const val GITHUB_RELEASES_API = "https://api.github.com/repos/Bakhodirov-Doniyor-dev/super-terminal/releases/latest"
        const val GITHUB_ALL_RELEASES_API = "https://api.github.com/repos/Bakhodirov-Doniyor-dev/super-terminal/releases"

        @Volatile
        private var instance: AppUpdateManager? = null

        fun getInstance(context: Context): AppUpdateManager {
            return instance ?: synchronized(this) {
                instance ?: AppUpdateManager(context.applicationContext).also { instance = it }
            }
        }
    }

    data class UpdateInfo(
        val hasUpdate: Boolean,
        val currentVersionCode: Int,
        val currentVersionName: String,
        val latestVersionCode: Int,
        val latestVersionName: String,
        val downloadUrl: String,
        val changelog: String,
        val releaseDate: String,
        val fileSizeFormatted: String = "",
        val isMandatory: Boolean = false
    )

    sealed class DownloadProgress {
        object Idle : DownloadProgress()
        data class Downloading(val percent: Int, val bytesDownloaded: Long, val totalBytes: Long, val speedMbPerSec: Double) : DownloadProgress()
        data class Completed(val file: File) : DownloadProgress()
        data class Error(val message: String) : DownloadProgress()
    }

    private val _downloadState = MutableStateFlow<DownloadProgress>(DownloadProgress.Idle)
    val downloadState: StateFlow<DownloadProgress> = _downloadState.asStateFlow()

    private val _availableUpdate = MutableStateFlow<UpdateInfo?>(null)
    val availableUpdate: StateFlow<UpdateInfo?> = _availableUpdate.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    fun getCurrentVersionCode(): Int {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            BuildConfig.VERSION_CODE
        }
    }

    fun getCurrentVersionName(): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: BuildConfig.VERSION_NAME
        } catch (e: Exception) {
            BuildConfig.VERSION_NAME
        }
    }

    fun getUpdateUrl(): String {
        val saved = prefs.getString(PREF_CUSTOM_UPDATE_URL, null)
        return if (saved.isNullOrBlank()) DEFAULT_UPDATE_URL else saved
    }

    fun setUpdateUrl(url: String) {
        prefs.edit().putString(PREF_CUSTOM_UPDATE_URL, url.trim()).apply()
    }

    fun isAutoCheckEnabled(): Boolean {
        return prefs.getBoolean(PREF_AUTO_CHECK, true)
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_CHECK, enabled).apply()
    }

    /**
     * Checks if a new version is available using both raw version.json and GitHub Releases API.
     */
    suspend fun checkForUpdates(
        customEndpoint: String? = null,
        onStatus: ((String) -> Unit)? = null
    ): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        _isChecking.value = true
        onStatus?.invoke("Yangilanishlar tekshirilmoqda...")
        try {
            val currentCode = getCurrentVersionCode()
            val currentName = getCurrentVersionName()

            var latestCode = currentCode
            var latestName = currentName
            var downloadUrl = ""
            var changelog = ""
            var releaseDate = ""
            var fileSizeFormatted = ""
            var isMandatory = false
            var checkSuccess = false

            // 1. Fetch version.json directly from GitHub raw (Fast, no rate-limits)
            try {
                val targetUrl = customEndpoint?.ifBlank { null } ?: getUpdateUrl()
                val noCacheUrl = if (targetUrl.contains("?")) "$targetUrl&_t=${System.currentTimeMillis()}" else "$targetUrl?_t=${System.currentTimeMillis()}"
                val request = Request.Builder()
                    .url(noCacheUrl)
                    .header("User-Agent", "SuperTerminalUpdater/1.0 (Android ${Build.VERSION.RELEASE})")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonStr = response.body?.string() ?: ""
                        if (jsonStr.isNotBlank()) {
                            val json = JSONObject(jsonStr)
                            val jsonCode = if (json.has("latestVersionCode")) {
                                json.getInt("latestVersionCode")
                            } else {
                                json.optInt("versionCode", currentCode)
                            }
                            if (jsonCode > latestCode) {
                                latestCode = jsonCode
                            }

                            val jsonName = if (json.has("latestVersionName")) {
                                json.getString("latestVersionName").removePrefix("v").removePrefix("V").trim()
                            } else {
                                json.optString("versionName", "").removePrefix("v").removePrefix("V").trim()
                            }
                            if (jsonName.isNotBlank() && isVersionNewer(jsonName, latestName, jsonCode, latestCode)) {
                                latestName = jsonName
                            }

                            val url = json.optString("downloadUrl", "")
                            if (url.isNotBlank()) downloadUrl = url

                            val cl = json.optString("changelog", "")
                            if (cl.isNotBlank()) changelog = cl

                            val rd = json.optString("releaseDate", "")
                            if (rd.isNotBlank()) releaseDate = rd

                            isMandatory = json.optBoolean("mandatory", false)
                            checkSuccess = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "version.json fetch error: ${e.message}")
            }

            // 2. Query GitHub Releases API for the latest release assets
            try {
                val apiRequest = Request.Builder()
                    .url(GITHUB_RELEASES_API)
                    .header("User-Agent", "SuperTerminalUpdater/1.0")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

                okHttpClient.newCall(apiRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank()) {
                            val json = JSONObject(body)
                            val tagName = json.optString("tag_name", "").removePrefix("v").removePrefix("V").trim()
                            if (tagName.isNotEmpty()) {
                                if (isVersionNewer(tagName, latestName, latestCode, latestCode)) {
                                    latestName = tagName
                                }
                                if (changelog.isBlank()) {
                                    changelog = json.optString("body", "Super Terminal yangilanishi.")
                                }
                                if (releaseDate.isBlank()) {
                                    releaseDate = json.optString("published_at", "").take(10)
                                }

                                val assets = json.optJSONArray("assets")
                                if (assets != null) {
                                    for (i in 0 until assets.length()) {
                                        val asset = assets.getJSONObject(i)
                                        val name = asset.optString("name", "")
                                        if (name.endsWith(".apk", ignoreCase = true)) {
                                            downloadUrl = asset.optString("browser_download_url", downloadUrl)
                                            val sizeBytes = asset.optLong("size", 0L)
                                            if (sizeBytes > 0) {
                                                fileSizeFormatted = "%.1f MB".format(Locale.US, sizeBytes / (1024.0 * 1024.0))
                                            }
                                            break
                                        }
                                    }
                                }
                                checkSuccess = true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "GitHub Releases API error: ${e.message}")
            }

            // Fallback download URL if needed
            if (downloadUrl.isBlank()) {
                downloadUrl = "https://github.com/Bakhodirov-Doniyor-dev/super-terminal/releases/download/v$latestName/SuperTerminal-v$latestName.apk"
            }

            prefs.edit().putLong(PREF_LAST_CHECK_TIME, System.currentTimeMillis()).apply()

            val hasUpdate = isVersionNewer(latestName, currentName, latestCode, currentCode)
            val info = UpdateInfo(
                hasUpdate = hasUpdate,
                currentVersionCode = currentCode,
                currentVersionName = currentName,
                latestVersionCode = latestCode,
                latestVersionName = latestName,
                downloadUrl = downloadUrl,
                changelog = changelog.ifBlank { "Super Terminal Manager uchun yangi reliz va tizim yangilanishi." },
                releaseDate = releaseDate.ifBlank { "2026-09-25" },
                fileSizeFormatted = fileSizeFormatted,
                isMandatory = isMandatory
            )

            _availableUpdate.value = info
            _isChecking.value = false

            if (hasUpdate) {
                onStatus?.invoke("Yangi versiya mavjud: v$latestName (Build $latestCode)")
            } else {
                onStatus?.invoke("Siz eng so'nggi versiyadasiz (v$currentName). Qayta yuklab o'rnatish imkoniyati mavjud.")
            }

            Result.success(info)
        } catch (e: Exception) {
            _isChecking.value = false
            Result.failure(e)
        }
    }

    /**
     * Compares two semantic version strings (e.g. 1.1.3 vs 1.1.2, 1.2 vs 1.1)
     * as well as integer versionCodes.
     */
    fun isVersionNewer(latestName: String, currentName: String, latestCode: Int, currentCode: Int): Boolean {
        if (latestCode > currentCode) return true
        return try {
            val lClean = latestName.removePrefix("v").removePrefix("V").trim()
            val cClean = currentName.removePrefix("v").removePrefix("V").trim()
            val lParts = lClean.split(".").map { it.trim().toIntOrNull() ?: 0 }
            val cParts = cClean.split(".").map { it.trim().toIntOrNull() ?: 0 }
            val maxLen = maxOf(lParts.size, cParts.size)
            for (i in 0 until maxLen) {
                val l = lParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Downloads APK file using OkHttp streaming with real-time speed calculation
     * and automatic redirect following.
     */
    suspend fun downloadApk(
        apkUrl: String,
        targetVersion: String = "latest",
        onProgress: ((Int, Long, Long, Double) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        _downloadState.value = DownloadProgress.Downloading(0, 0, 0, 0.0)
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val targetFile = File(updatesDir, "SuperTerminal_v${targetVersion}.apk")
            if (targetFile.exists()) targetFile.delete()

            val request = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", "SuperTerminalUpdater/1.0 (Android ${Build.VERSION.RELEASE})")
                .header("Accept", "*/*")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Yuklab olishda xatolik: HTTP ${response.code} ${response.message}")
            }

            val body = response.body ?: throw IllegalStateException("Serverdan bo'sh javob keldi")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            val startTime = System.currentTimeMillis()
            var lastUpdateTime = startTime

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime >= 150) {
                            val elapsedSec = (now - startTime) / 1000.0
                            val speed = if (elapsedSec > 0) (downloadedBytes / (1024.0 * 1024.0)) / elapsedSec else 0.0
                            val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

                            _downloadState.value = DownloadProgress.Downloading(percent, downloadedBytes, totalBytes, speed)
                            onProgress?.invoke(percent, downloadedBytes, totalBytes, speed)
                            lastUpdateTime = now
                        }
                    }
                    output.flush()
                }
            }

            if (!targetFile.exists() || targetFile.length() < 50000) {
                throw IllegalStateException("APK to'liq yuklab olinmadi (${targetFile.length()} bayt).")
            }

            // Set readable permissions for PackageInstaller
            targetFile.setReadable(true, false)

            _downloadState.value = DownloadProgress.Completed(targetFile)
            Result.success(targetFile)
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Yuklab olishda xatolik yuz berdi"
            _downloadState.value = DownloadProgress.Error(errorMsg)
            Result.failure(e)
        }
    }

    /**
     * Installs the downloaded APK.
     * Strategy 1: Silent instant install if ADB client or Root is connected.
     * Strategy 2: Official Android Intent-based PackageInstaller (via FileProvider).
     */
    suspend fun installApk(
        apkFile: File,
        onStatus: ((String) -> Unit)? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!apkFile.exists()) {
            return@withContext Result.failure(IllegalArgumentException("APK fayli topilmadi: ${apkFile.absolutePath}"))
        }

        val adbClient = AdbClientHolder.getClient(context)
        val hasAdb = adbClient.isConnected()
        val hasRoot = isSuAvailable()

        // 1. Silent Install via ADB or Root
        if (hasAdb || hasRoot) {
            onStatus?.invoke("[*] ADB / Root orqali o'rnatilmoqda (Silent Upgrade)...")
            try {
                if (hasAdb) {
                    val installCmd = "pm install -r -d \"${apkFile.absolutePath}\""
                    val res = adbClient.executeCommand(installCmd)
                    if (res.contains("Success", ignoreCase = true)) {
                        onStatus?.invoke("[✔️] Ilova ADB orqali muvaffaqiyatli yangilandi!")
                        return@withContext Result.success(true)
                    }
                }

                if (hasRoot) {
                    val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm install -r -d \"${apkFile.absolutePath}\""))
                    val out = p.inputStream.bufferedReader().readText()
                    if (out.contains("Success", ignoreCase = true)) {
                        onStatus?.invoke("[✔️] Ilova Root orqali muvaffaqiyatli yangilandi!")
                        return@withContext Result.success(true)
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Silent install failed: ${e.message}")
            }
        }

        // 2. Standard Android OS Package Installer (via FileProvider)
        onStatus?.invoke("[*] Android tizim o'rnatuvchisi ochilmoqda...")
        withContext(Dispatchers.Main) {
            try {
                val apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }

                // Check unknown sources permission on Android 8.0+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        val permIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(permIntent)
                    }
                }

                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(tag, "PackageInstaller launch failed: ${e.message}", e)
                onStatus?.invoke("[-] O'rnatuvchini ochishda xatolik: ${e.localizedMessage}")
            }
        }

        Result.success(false)
    }

    private fun isSuAvailable(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("which", "su"))
            p.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun resetDownloadState() {
        _downloadState.value = DownloadProgress.Idle
    }
}
