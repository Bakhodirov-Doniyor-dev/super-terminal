package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.widget.ImageView
import android.widget.Toast

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import android.content.pm.PackageInfo
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.Translations
import com.example.viewmodel.AdbViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

data class ThemeColors(
    val textColor: Color,
    val bgColor: Color,
    val borderColor: Color,
    val accentColor: Color,
    val cardBgColor: Color,
    val secText: Color
)

// Data structure for Visual App Manager
data class AppItem(
    val name: String,
    val packageName: String,
    val isSystem: Boolean,
    val icon: android.graphics.drawable.Drawable?,
    val versionName: String,
    val installTime: String,
    val updateTime: String = "-",
    val apkSize: Long = 0L,
    val targetSdk: Int = 0,
    val minSdk: Int = 0,
    val uid: Int = 0,
    val permissionsCount: Int = 0,
    val isEnabled: Boolean = true
)

object CpuUsageHelper {
    fun getRealCpuUsage(): Float {
        return com.example.viewmodel.RealProcessInspector.calculateRealCpuUsage().first
    }
}

// Data structure for File Browser
data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val permissions: String,
    val lastModified: String,
    val nativeFile: File? = null
)

fun getFileMimeType(url: String): String {
    val ext = url.substringAfterLast('.', "").lowercase()
    val mimeTypeMap = android.webkit.MimeTypeMap.getSingleton()
    var mime = mimeTypeMap.getMimeTypeFromExtension(ext)
    if (mime == null) {
        mime = when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "tiff", "raw", "ico" -> "image/*"
            "mp4", "mkv", "avi", "3gp", "mov", "webm", "flv", "wmv", "m4v", "ts", "m2ts" -> "video/*"
            "mp3", "wav", "ogg", "m4a", "flac", "aac", "wma", "amr", "opus", "m4b" -> "audio/*"
            "pdf" -> "application/pdf"
            "zip", "rar", "7z", "tar", "gz" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            "txt", "csv", "xml", "json", "md", "prop", "conf", "log", "sh", "bat" -> "text/plain"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            else -> "*/*"
        }
    }
    return mime
}

object GlobalMediaPlayer {
    var sharedMediaPlayer: android.media.MediaPlayer? = null
    
    val currentPlayingAudioPathState = mutableStateOf("")
    var currentPlayingAudioPath: String
        get() = currentPlayingAudioPathState.value
        set(value) { currentPlayingAudioPathState.value = value }

    val isAudioPlayingState = mutableStateOf(false)
    var isAudioPlaying: Boolean
        get() = isAudioPlayingState.value
        set(value) { isAudioPlayingState.value = value }

    val isAudioMinimizedState = mutableStateOf(false)
    var isAudioMinimized: Boolean
        get() = isAudioMinimizedState.value
        set(value) { isAudioMinimizedState.value = value }

    val audioPlaybackPositionState = mutableIntStateOf(0)
    var audioPlaybackPosition: Int
        get() = audioPlaybackPositionState.intValue
        set(value) { audioPlaybackPositionState.intValue = value }

    val audioDurationState = mutableIntStateOf(1)
    var audioDuration: Int
        get() = audioDurationState.intValue
        set(value) { audioDurationState.intValue = value }

    val audioLoopingEnabledState = mutableStateOf(false)
    var audioLoopingEnabled: Boolean
        get() = audioLoopingEnabledState.value
        set(value) { audioLoopingEnabledState.value = value }

    val audioPlaybackSpeedState = mutableFloatStateOf(1.0f)
    var audioPlaybackSpeed: Float
        get() = audioPlaybackSpeedState.floatValue
        set(value) { audioPlaybackSpeedState.floatValue = value }

    val audioPlaylistState = mutableStateOf<List<String>>(emptyList())
    var audioPlaylist: List<String>
        get() = audioPlaylistState.value
        set(value) { audioPlaylistState.value = value }

    val audioPlaylistIndexState = mutableIntStateOf(-1)
    var audioPlaylistIndex: Int
        get() = audioPlaylistIndexState.intValue
        set(value) { audioPlaylistIndexState.intValue = value }

    val videoPathToPlayState = mutableStateOf("")
    var videoPathToPlay: String
        get() = videoPathToPlayState.value
        set(value) { videoPathToPlayState.value = value }

    val showVideoPlayerDialogState = mutableStateOf(false)
    var showVideoPlayerDialog: Boolean
        get() = showVideoPlayerDialogState.value
        set(value) { showVideoPlayerDialogState.value = value }

    val videoLoopingEnabledState = mutableStateOf(false)
    var videoLoopingEnabled: Boolean
        get() = videoLoopingEnabledState.value
        set(value) { videoLoopingEnabledState.value = value }

    val videoPlaybackSpeedState = mutableFloatStateOf(1.0f)
    var videoPlaybackSpeed: Float
        get() = videoPlaybackSpeedState.floatValue
        set(value) { videoPlaybackSpeedState.floatValue = value }

    val videoPlaylistState = mutableStateOf<List<String>>(emptyList())
    var videoPlaylist: List<String>
        get() = videoPlaylistState.value
        set(value) { videoPlaylistState.value = value }

    val videoPlaylistIndexState = mutableIntStateOf(-1)
    var videoPlaylistIndex: Int
        get() = videoPlaylistIndexState.intValue
        set(value) { videoPlaylistIndexState.intValue = value }

    val isVideoPlayingStateState = mutableStateOf(false)
    var isVideoPlayingState: Boolean
        get() = isVideoPlayingStateState.value
        set(value) { isVideoPlayingStateState.value = value }

    val showAudioPlayerDialogState = mutableStateOf(false)
    var showAudioPlayerDialog: Boolean
        get() = showAudioPlayerDialogState.value
        set(value) { showAudioPlayerDialogState.value = value }

    fun getOrCreatePlayer(context: android.content.Context): android.media.MediaPlayer {
        if (sharedMediaPlayer == null) {
            sharedMediaPlayer = android.media.MediaPlayer()
        }
        return sharedMediaPlayer!!
    }
}

class GlobalStateDelegate<T>(
    private val getter: () -> T,
    private val setter: (T) -> Unit
) : MutableState<T> {
    override var value: T
        get() = getter()
        set(v) = setter(v)
    override fun component1(): T = value
    override fun component2(): (T) -> Unit = { value = it }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    viewModel: AdbViewModel,
    terminalTheme: String,
    selectedLanguage: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(0) } // 0 = User, 1 = System, 2 = All
    var appList by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var selectedApp by remember { mutableStateOf<AppItem?>(null) }
    var showAppDetails by remember { mutableStateOf(false) }
    var appToUninstall by remember { mutableStateOf<AppItem?>(null) }
    var appToFreeze by remember { mutableStateOf<AppItem?>(null) }
    var appForProtectionManager by remember { mutableStateOf<AppItem?>(null) }
    var showConnectionDialogInAppManager by remember { mutableStateOf(false) }
    var showAntiOverlayGuardDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Theme Colors
    val (textColor, bgColor, borderColor, accentColor, cardBgColor, secText) = getThemeColors(terminalTheme)

    // Load Apps in Background with 100% Comprehensive Discovery (PackageManager + Intent Query + Shell pm fallback)
    LaunchedEffect(refreshTrigger) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val allPackagesMap = linkedMapOf<String, PackageInfo>()

            // 1. Primary discovery: getInstalledPackages
            try {
                val flags = PackageManager.GET_META_DATA
                val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
                } else {
                    pm.getInstalledPackages(flags)
                }
                list.forEach { pkg -> allPackagesMap[pkg.packageName] = pkg }
            } catch (e: Exception) {
                try {
                    val list = pm.getInstalledPackages(0)
                    list.forEach { pkg -> allPackagesMap[pkg.packageName] = pkg }
                } catch (_: Exception) {}
            }

            // 2. Secondary discovery: getInstalledApplications
            try {
                val appList = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                appList.forEach { app ->
                    if (!allPackagesMap.containsKey(app.packageName)) {
                        try {
                            val info = pm.getPackageInfo(app.packageName, PackageManager.GET_META_DATA)
                            allPackagesMap[app.packageName] = info
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}

            // 3. Tertiary discovery: launcher activities (guarantees Play Store, Galaxy Store, downloaded apps, games, APKs)
            try {
                val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)
                resolveInfos.forEach { ri ->
                    val pkgName = ri.activityInfo?.packageName
                    if (pkgName != null && !allPackagesMap.containsKey(pkgName)) {
                        try {
                            val info = pm.getPackageInfo(pkgName, PackageManager.GET_META_DATA)
                            allPackagesMap[pkgName] = info
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}

            // 4. Shell discovery fallback: pm list packages (guarantees 100% full package visibility on device)
            try {
                val process = Runtime.getRuntime().exec(arrayOf("pm", "list", "packages"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val pkgName = line?.removePrefix("package:")?.trim()
                    if (!pkgName.isNullOrEmpty() && !allPackagesMap.containsKey(pkgName)) {
                        try {
                            val info = pm.getPackageInfo(pkgName, 0)
                            allPackagesMap[pkgName] = info
                        } catch (_: Exception) {}
                    }
                }
                reader.close()
                process.waitFor()
            } catch (_: Exception) {}

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            val items = allPackagesMap.values.mapNotNull { pkg ->
                val appInfo = pkg.applicationInfo ?: try {
                    pm.getApplicationInfo(pkg.packageName, 0)
                } catch (e: Exception) { null }

                val name = if (appInfo != null) {
                    try { appInfo.loadLabel(pm).toString() } catch (e: Exception) { pkg.packageName }
                } else {
                    pkg.packageName
                }

                val pkgName = pkg.packageName
                val flags = appInfo?.flags ?: 0
                val isThirdParty = (flags and ApplicationInfo.FLAG_SYSTEM) == 0
                val isUpdatedSystem = (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val isGooglePlayStore = pkgName == "com.android.vending" || pkgName.contains(".vending")
                val isGalaxyStore = pkgName.contains("samsungapps")
                val isKnownAppStore = pkgName == "com.google.android.play.games" || pkgName.contains("appmarket") || pkgName.contains("store")

                // Classified as User App: All .apk installed apps, Play Store, Galaxy Store, updated user apps
                val isUserApp = isThirdParty || isUpdatedSystem || isGooglePlayStore || isGalaxyStore || isKnownAppStore
                val isSystem = !isUserApp

                val icon = if (appInfo != null) {
                    try { appInfo.loadIcon(pm) } catch (e: Exception) { null }
                } else null

                val installTime = try { sdf.format(Date(pkg.firstInstallTime)) } catch (e: Exception) { "-" }
                val updateTime = try { sdf.format(Date(pkg.lastUpdateTime)) } catch (e: Exception) { "-" }
                val verName = pkg.versionName ?: "1.0.0"
                val apkSize = if (appInfo?.sourceDir != null) {
                    try {
                        val file = File(appInfo.sourceDir)
                        if (file.exists()) file.length() else 0L
                    } catch (e: Exception) { 0L }
                } else 0L
                val targetSdk = appInfo?.targetSdkVersion ?: 0
                val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) (appInfo?.minSdkVersion ?: 0) else 0
                val uid = appInfo?.uid ?: 0
                val permsCount = pkg.requestedPermissions?.size ?: 0

                val enabledSetting = try {
                    pm.getApplicationEnabledSetting(pkg.packageName)
                } catch (_: Exception) {
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                }

                val isEnabled = when (enabledSetting) {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
                    else -> true
                }

                AppItem(
                    name = name,
                    packageName = pkg.packageName,
                    isSystem = isSystem,
                    icon = icon,
                    versionName = verName,
                    installTime = installTime,
                    updateTime = updateTime,
                    apkSize = apkSize,
                    targetSdk = targetSdk,
                    minSdk = minSdk,
                    uid = uid,
                    permissionsCount = permsCount,
                    isEnabled = isEnabled
                )
            }.sortedWith(
                compareBy<AppItem> { it.isSystem }
                    .thenBy { it.name.lowercase() }
            )

            withContext(Dispatchers.Main) {
                appList = items
                isLoading = false
            }
        }
    }

    val filteredApps = remember(searchQuery, selectedFilter, appList) {
        appList.filter { app ->
            val matchesSearch = app.name.contains(searchQuery, ignoreCase = true) || 
                                 app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                0 -> !app.isSystem
                1 -> app.isSystem
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor.copy(alpha = 0.50f))) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textColor)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (selectedLanguage) {
                        "en" -> "Visual App Manager"
                        "ru" -> "Визуальный менеджер приложений"
                        else -> "Vizual Ilovalar Menejeri"
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { showAntiOverlayGuardDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "24/7 Anti-Overlay Sentry",
                        tint = accentColor
                    )
                }
                IconButton(
                    onClick = { refreshTrigger++ },
                    enabled = !isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = if (isLoading) secText else accentColor
                    )
                }
            }

            // 24/7 Anti-Overlay Sentry Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBgColor.copy(alpha = 0.65f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable { showAntiOverlayGuardDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = Translations.get("anti_overlay_title", selectedLanguage),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = textColor
                            )
                            Text(
                                text = Translations.get("anti_overlay_desc", selectedLanguage),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = secText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = when (selectedLanguage) {
                            "en" -> "Search application..."
                            "ru" -> "Поиск приложений..."
                            else -> "Ilovani qidirish..."
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = secText
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = textColor) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("app_search_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = borderColor.copy(alpha = 0.35f),
                    focusedContainerColor = cardBgColor.copy(alpha = 0.50f),
                    unfocusedContainerColor = cardBgColor.copy(alpha = 0.35f)
                ),
                shape = CircleShape
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    when (selectedLanguage) { "en" -> "User" "ru" -> "Пользовательские" else -> "Foydalanuvchi" },
                    when (selectedLanguage) { "en" -> "System" "ru" -> "Системные" else -> "Tizim" },
                    when (selectedLanguage) { "en" -> "All" "ru" -> "Все" else -> "Barchasi" }
                )
                
                filters.forEachIndexed { index, label ->
                    val isSelected = selectedFilter == index
                    val count = if (index == 0) appList.count { !it.isSystem } else if (index == 1) appList.count { it.isSystem } else appList.size
                    
                    Box(
                        modifier = Modifier
                            .oneUiGlassCapsule(
                                backgroundColor = if (isSelected) accentColor.copy(alpha = 0.30f) else cardBgColor.copy(alpha = 0.45f),
                                accentColor = if (isSelected) accentColor else borderColor.copy(alpha = 0.25f)
                            )
                            .clickable { selectedFilter = index }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$label ($count)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) (if (terminalTheme == "light") Color(0xFF0F172A) else Color.White) else textColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Apps List
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ProfessionalSpinningLoader(color = accentColor)
                }
            } else if (filteredApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = when (selectedLanguage) {
                            "en" -> "No applications found"
                            "ru" -> "Приложения не найдены"
                            else -> "Ilovalar topilmadi"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = secText
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalFadingEdge(topEdgeSize = 32.dp, bottomEdgeSize = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (app.isEnabled) 1f else 0.6f)
                                .oneUiGlassCard(
                                    shape = RoundedCornerShape(16.dp),
                                    backgroundColor = cardBgColor.copy(alpha = 0.55f),
                                    borderColor = borderColor.copy(alpha = 0.25f)
                                )
                                .clickable {
                                    selectedApp = app
                                    showAppDetails = true
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (app.icon != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        ImageView(ctx).apply {
                                            scaleType = ImageView.ScaleType.FIT_CENTER
                                        }
                                    },
                                    update = { imageView ->
                                        imageView.setImageDrawable(app.icon)
                                    },
                                    modifier = Modifier.size(40.dp)
                                )
                            } else {
                                Icon(Icons.Default.Android, contentDescription = null, tint = textColor, modifier = Modifier.size(40.dp))
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = app.name,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = textColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (!app.isEnabled) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(Color.Red.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (selectedLanguage == "uz") "Muzlatilgan" else if (selectedLanguage == "ru") "Заморожен" else "Frozen",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Red,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = app.packageName,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = secText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                Spacer(modifier = Modifier.height(2.dp))

                                val sizeStr = if (app.apkSize > 0) String.format(Locale.US, "%.1f MB", app.apkSize / (1024f * 1024f)) else "-"
                                Text(
                                    text = "v${app.versionName} • $sizeStr",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = accentColor.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = secText, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // App Options Dialog
        if (showAppDetails && selectedApp != null) {
            val app = selectedApp!!
            Dialog(
                onDismissRequest = { showAppDetails = false }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(bgColor)
                        .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header info
                        if (app.icon != null) {
                            AndroidView(
                                factory = { ctx ->
                                    ImageView(ctx).apply {
                                        scaleType = ImageView.ScaleType.FIT_CENTER
                                    }
                                },
                                update = { imageView ->
                                    imageView.setImageDrawable(app.icon)
                                },
                                modifier = Modifier.size(64.dp)
                            )
                        } else {
                            Icon(Icons.Default.Android, contentDescription = null, tint = textColor, modifier = Modifier.size(64.dp))
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = app.name,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = textColor,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = app.packageName,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = secText,
                            textAlign = TextAlign.Center
                        )
                        
                        if (!app.isEnabled) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (selectedLanguage == "uz") "Holati: Muzlatilgan" else if (selectedLanguage == "ru") "Статус: Заморожен" else "Status: Frozen (Disabled)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Red,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val apkSizeMb = if (app.apkSize > 0) String.format(Locale.US, "%.2f MB", app.apkSize / (1024f * 1024f)) else "N/A"
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(cardBgColor)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "• Version: ${app.versionName} (Target SDK: ${app.targetSdk}, Min: ${app.minSdk})",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = textColor
                            )
                            Text(
                                text = "• APK Size: $apkSizeMb | UID: ${app.uid}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = textColor
                            )
                            Text(
                                text = "• Installed: ${app.installTime}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = secText
                            )
                            Text(
                                text = "• Updated: ${app.updateTime} | Perms: ${app.permissionsCount}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = secText
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = borderColor.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Actions Grid
                        val actionColumns = listOf(
                            Triple(
                                when (selectedLanguage) { "en" -> "Launch App" "ru" -> "Запустить" else -> "Ishga tushirish" },
                                Icons.Default.PlayArrow
                            ) {
                                try {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (launchIntent != null) {
                                        
                                        
                                                                (context as? com.example.MainActivity)?.isBypassingLock = true; context.startActivity(launchIntent)
                                    } else {
                                        Toast.makeText(context, if (selectedLanguage == "uz") "Ishga tushirib bo'lmadi" else "Cannot launch app", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                showAppDetails = false
                            },
                            Triple(
                                when (selectedLanguage) { "en" -> "System App Info" "ru" -> "О системе" else -> "Tizim sozlamalari" },
                                Icons.Default.Info
                            ) {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${app.packageName}")
                                    }
                                    
                                    
                                                                (context as? com.example.MainActivity)?.isBypassingLock = true; context.startActivity(intent)
                                } catch (e: Exception) {}
                                showAppDetails = false
                            },
                            Triple(
                                Translations.get("app_action_shield", selectedLanguage),
                                Icons.Default.Security
                            ) {
                                appForProtectionManager = app
                                showAppDetails = false
                            },
                            Triple(
                                Translations.get("app_action_uninstall", selectedLanguage),
                                Icons.Default.Delete
                            ) {
                                appToUninstall = app
                            },
                            Triple(
                                Translations.get("app_action_copy_pkg", selectedLanguage),
                                Icons.Default.ContentCopy
                            ) {
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("package", app.packageName)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, if (selectedLanguage == "uz") "Paket nomi nusxalandi!" else if (selectedLanguage == "ru") "Имя пакета скопировано!" else "Package name copied!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {}
                                showAppDetails = false
                            },
                            Triple(
                                if (app.isEnabled) Translations.get("app_action_freeze", selectedLanguage)
                                else Translations.get("app_action_unfreeze", selectedLanguage),
                                if (app.isEnabled) Icons.Default.AcUnit else Icons.Default.PlayCircle
                            ) {
                                appToFreeze = app
                            }
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            actionColumns.forEach { (label, icon, onClick) ->
                                Button(
                                    onClick = onClick,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = cardBgColor,
                                        contentColor = textColor
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(0.5.dp, borderColor.copy(alpha = 0.4f)),
                                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Close Button
                        TextButton(
                            onClick = { showAppDetails = false },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                text = Translations.get("btn_close", selectedLanguage),
                                color = accentColor,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (appToUninstall != null) {
        val app = appToUninstall!!
        LockScreenOverlay(
            terminalTheme = terminalTheme,
            selectedLanguage = selectedLanguage,
            isCancellable = true,
            onCancel = { appToUninstall = null },
            onSuccess = {
                appToUninstall = null
                showAppDetails = false
                scope.launch(Dispatchers.IO) {
                    val isAdbOrRoot = viewModel.isConnected.value || viewModel.isSuAvailable()
                    val isAdmin = isAppActiveAdmin(context, app.packageName)
                    val hasOverlay = hasOverlayPermission(context, app.packageName, app.uid)
                    val hasAccess = hasAccessibilityService(context, app.packageName)

                    if (isAdbOrRoot) {
                        val receivers = getAdminReceivers(context, app.packageName)
                        val result = viewModel.forceNukeApp(app.packageName, receivers)
                        withContext(Dispatchers.Main) {
                            if (result.contains("Success", ignoreCase = true) || !isAppInstalled(context, app.packageName)) {
                                Toast.makeText(context, if (selectedLanguage == "uz") "Ilova butunlay yo'q qilindi!" else "App successfully eliminated!", Toast.LENGTH_SHORT).show()
                                refreshTrigger++
                            } else {
                                if (isAdmin || hasOverlay || hasAccess) {
                                    appForProtectionManager = app
                                } else {
                                    try {
                                        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                                        (context as? com.example.MainActivity)?.isBypassingLock = true
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot uninstall: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            if (isAdmin || hasOverlay || hasAccess) {
                                appForProtectionManager = app
                            } else {
                                try {
                                    val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                                    (context as? com.example.MainActivity)?.isBypassingLock = true
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot uninstall: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    if (appToFreeze != null) {
        val app = appToFreeze!!
        LockScreenOverlay(
            terminalTheme = terminalTheme,
            selectedLanguage = selectedLanguage,
            isCancellable = true,
            onCancel = { appToFreeze = null },
            onSuccess = {
                appToFreeze = null
                showAppDetails = false
                scope.launch(Dispatchers.IO) {
                    val isAdbOrRoot = viewModel.isConnected.value || viewModel.isSuAvailable()
                    if (isAdbOrRoot) {
                        val result = viewModel.forceFreezeApp(app.packageName, app.isEnabled)
                        withContext(Dispatchers.Main) {
                            val msg = if (app.isEnabled) {
                                if (selectedLanguage == "uz") "Ilova majburan muzlatildi!" else "App frozen!"
                            } else {
                                if (selectedLanguage == "uz") "Ilova qayta faollashtirildi!" else "App enabled!"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            refreshTrigger++
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            appForProtectionManager = app
                        }
                    }
                }
            }
        )
    }

    if (appForProtectionManager != null) {
        ProtectedAppNeutralizerDialog(
            app = appForProtectionManager!!,
            viewModel = viewModel,
            terminalTheme = terminalTheme,
            selectedLanguage = selectedLanguage,
            onOpenAdbConnection = {
                showConnectionDialogInAppManager = true
            },
            onAppActionDone = {
                appForProtectionManager = null
                refreshTrigger++
            },
            onDismiss = {
                appForProtectionManager = null
            }
        )
    }

    if (showConnectionDialogInAppManager) {
        ConnectionManagerDialog(
            viewModel = viewModel,
            terminalTheme = terminalTheme,
            selectedLanguage = selectedLanguage,
            onDismiss = { showConnectionDialogInAppManager = false }
        )
    }

    if (showAntiOverlayGuardDialog) {
        AntiOverlayGuardDialog(
            terminalTheme = terminalTheme,
            selectedLanguage = selectedLanguage,
            viewModel = viewModel,
            onDismiss = { showAntiOverlayGuardDialog = false }
        )
    }
}

private fun isAppInstalled(context: Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: Exception) {
        false
    }
}

private fun getAdminReceivers(context: Context, packageName: String): List<String> {
    return try {
        val intent = Intent("android.app.action.DEVICE_ADMIN_ENABLED").setPackage(packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.GET_META_DATA
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_DISABLED_COMPONENTS or PackageManager.GET_META_DATA
        }
        context.packageManager.queryBroadcastReceivers(intent, flags).map {
            it.activityInfo.name
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun isAppActiveAdmin(context: Context, packageName: String): Boolean {
    return try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        dpm?.activeAdmins?.any { it.packageName == packageName } ?: false
    } catch (e: Exception) {
        false
    }
}

private fun hasOverlayPermission(context: Context, packageName: String, uid: Int): Boolean {
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps?.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps?.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }
}

private fun hasAccessibilityService(context: Context, packageName: String): Boolean {
    return try {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        enabled.contains(packageName)
    } catch (e: Exception) {
        false
    }
}

@Composable
fun ProtectedAppNeutralizerDialog(
    app: AppItem,
    viewModel: AdbViewModel,
    terminalTheme: String,
    selectedLanguage: String,
    onOpenAdbConnection: () -> Unit,
    onAppActionDone: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (textColor, bgColor, borderColor, accentColor, cardBgColor, secText) = getThemeColors(terminalTheme)

    val isAdbActive = viewModel.isConnected.collectAsState().value || viewModel.isSuAvailable()
    val isDeviceAdmin = remember(app.packageName) { isAppActiveAdmin(context, app.packageName) }
    val hasOverlay = remember(app.packageName) { hasOverlayPermission(context, app.packageName, app.uid) }
    val hasAccessibility = remember(app.packageName) { hasAccessibilityService(context, app.packageName) }

    var isExecutingAction by remember { mutableStateOf(false) }
    var actionLog by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Translations.get("app_neutralizer_title", selectedLanguage),
                    color = textColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // App Info Box
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, borderColor.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = app.name,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            fontSize = 14.sp
                        )
                        Text(
                            text = app.packageName,
                            fontFamily = FontFamily.Monospace,
                            color = secText,
                            fontSize = 11.sp
                        )
                    }
                }

                // Diagnostic Status Card
                Text(
                    text = Translations.get("app_neutralizer_analysis", selectedLanguage),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = accentColor,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                ) {
                    StatusRow(
                        label = Translations.get("device_admin_label", selectedLanguage),
                        isActive = isDeviceAdmin,
                        activeLabel = Translations.get("device_admin_active", selectedLanguage),
                        inactiveLabel = Translations.get("device_admin_inactive", selectedLanguage)
                    )
                    StatusRow(
                        label = Translations.get("overlay_label", selectedLanguage),
                        isActive = hasOverlay,
                        activeLabel = Translations.get("overlay_active", selectedLanguage),
                        inactiveLabel = Translations.get("overlay_inactive", selectedLanguage)
                    )
                    StatusRow(
                        label = Translations.get("accessibility_label", selectedLanguage),
                        isActive = hasAccessibility,
                        activeLabel = Translations.get("accessibility_active", selectedLanguage),
                        inactiveLabel = Translations.get("accessibility_inactive", selectedLanguage)
                    )
                    StatusRow(
                        label = Translations.get("adb_conn_label", selectedLanguage),
                        isActive = isAdbActive,
                        activeLabel = Translations.get("adb_conn_active", selectedLanguage),
                        inactiveLabel = Translations.get("adb_conn_inactive", selectedLanguage),
                        positiveIfActive = true
                    )
                }

                if (actionLog.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = actionLog,
                            color = Color(0xFF10B981),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // METHOD 1: 1-Click ADB Nuke / Freeze
                Text(
                    text = Translations.get("app_neutralizer_method1", selectedLanguage),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color(0xFF10B981),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                if (isAdbActive) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                    ) {
                        Button(
                            onClick = {
                                isExecutingAction = true
                                actionLog = if (selectedLanguage == "uz") "Zararsizlantirish va o'chirish boshlandi..." else if (selectedLanguage == "ru") "Запуск нейтрализации и удаления..." else "Neutralizing & uninstallation started..."
                                scope.launch(Dispatchers.IO) {
                                    val receivers = getAdminReceivers(context, app.packageName)
                                    val res = viewModel.forceNukeApp(app.packageName, receivers)
                                    withContext(Dispatchers.Main) {
                                        isExecutingAction = false
                                        actionLog = res
                                        Toast.makeText(context, if (selectedLanguage == "uz") "Ilova yo'q qilindi!" else if (selectedLanguage == "ru") "Приложение удалено!" else "App eliminated!", Toast.LENGTH_SHORT).show()
                                        onAppActionDone()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            enabled = !isExecutingAction
                        ) {
                            Text(
                                text = Translations.get("app_force_nuke_btn", selectedLanguage),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }

                        Button(
                            onClick = {
                                isExecutingAction = true
                                actionLog = if (selectedLanguage == "uz") "Muzlatish/Eritish boshlandi..." else if (selectedLanguage == "ru") "Заморозка/Разморозка..." else "Toggling freeze state..."
                                scope.launch(Dispatchers.IO) {
                                    val receivers = getAdminReceivers(context, app.packageName)
                                    val res = viewModel.forceFreezeApp(app.packageName, app.isEnabled, receivers)
                                    withContext(Dispatchers.Main) {
                                        isExecutingAction = false
                                        actionLog = res
                                        Toast.makeText(context, if (selectedLanguage == "uz") "Ilova eritildi va cheklovlar olib tashlandi!" else if (selectedLanguage == "ru") "Состояние изменено!" else "App state toggled!", Toast.LENGTH_SHORT).show()
                                        onAppActionDone()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            enabled = !isExecutingAction
                        ) {
                            Text(
                                text = if (app.isEnabled) Translations.get("app_force_freeze_btn", selectedLanguage) else Translations.get("app_force_unfreeze_btn", selectedLanguage),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }

                    // Direct ADB Admin & Restricted Settings Unlock Controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                    ) {
                        Button(
                            onClick = {
                                isExecutingAction = true
                                actionLog = if (selectedLanguage == "uz") "Qurilma ma'muri yoqilmoqda va cheklov yechilmoqda..." else "Enabling admin & unlocking restricted settings..."
                                scope.launch(Dispatchers.IO) {
                                    val receivers = getAdminReceivers(context, app.packageName)
                                    val res = viewModel.toggleDeviceAdminViaAdb(app.packageName, enable = true, receivers = receivers)
                                    withContext(Dispatchers.Main) {
                                        isExecutingAction = false
                                        actionLog = res
                                        Toast.makeText(context, if (selectedLanguage == "uz") "Device Admin yoqildi & Cheklov yechildi!" else "Device Admin activated & unlocked!", Toast.LENGTH_SHORT).show()
                                        onAppActionDone()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            enabled = !isExecutingAction
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (selectedLanguage == "uz") "⚡ Admin Yoqish" else if (selectedLanguage == "ru") "⚡ Вкл Админ" else "⚡ Enable Admin",
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = Color.White
                            )
                        }

                        Button(
                            onClick = {
                                isExecutingAction = true
                                actionLog = if (selectedLanguage == "uz") "Qurilma ma'muri o'chirilmoqda..." else "Deactivating device admin..."
                                scope.launch(Dispatchers.IO) {
                                    val receivers = getAdminReceivers(context, app.packageName)
                                    val res = viewModel.toggleDeviceAdminViaAdb(app.packageName, enable = false, receivers = receivers)
                                    withContext(Dispatchers.Main) {
                                        isExecutingAction = false
                                        actionLog = res
                                        Toast.makeText(context, if (selectedLanguage == "uz") "Device Admin o'chirildi!" else "Device Admin deactivated!", Toast.LENGTH_SHORT).show()
                                        onAppActionDone()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            enabled = !isExecutingAction
                        ) {
                            Icon(Icons.Default.RemoveModerator, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (selectedLanguage == "uz") "🛑 Admin O'chirish" else if (selectedLanguage == "ru") "🛑 Выкл Админ" else "🛑 Disable Admin",
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onOpenAdbConnection,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Translations.get("app_connect_adb_wireless_btn", selectedLanguage),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.Black
                        )
                    }
                }

                // METHOD 2: Step-by-Step Manual Shield Removal
                Text(
                    text = Translations.get("app_neutralizer_method2", selectedLanguage),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = accentColor,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ManualStepButton(
                        step = "1",
                        label = Translations.get("app_step1_overlay", selectedLanguage),
                        icon = Icons.Default.LayersClear,
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${app.packageName}"))
                                (context as? com.example.MainActivity)?.isBypassingLock = true
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                (context as? com.example.MainActivity)?.isBypassingLock = true
                                context.startActivity(intent)
                            }
                        },
                        cardBgColor = cardBgColor,
                        textColor = textColor,
                        borderColor = borderColor
                    )

                    ManualStepButton(
                        step = "2",
                        label = Translations.get("app_step2_accessibility", selectedLanguage),
                        icon = Icons.Default.AccessibilityNew,
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                (context as? com.example.MainActivity)?.isBypassingLock = true
                                context.startActivity(intent)
                            } catch (e: Exception) {}
                        },
                        cardBgColor = cardBgColor,
                        textColor = textColor,
                        borderColor = borderColor
                    )

                    ManualStepButton(
                        step = "3",
                        label = Translations.get("app_step3_device_admin", selectedLanguage),
                        icon = Icons.Default.AdminPanelSettings,
                        onClick = {
                            if (isAdbActive) {
                                scope.launch(Dispatchers.IO) {
                                    viewModel.unlockRestrictedSettingsViaAdb(app.packageName)
                                }
                            }
                            try {
                                val intent = Intent().apply {
                                    component = ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings")
                                }
                                (context as? com.example.MainActivity)?.isBypassingLock = true
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                                (context as? com.example.MainActivity)?.isBypassingLock = true
                                context.startActivity(intent)
                            }
                        },
                        cardBgColor = cardBgColor,
                        textColor = textColor,
                        borderColor = borderColor
                    )

                    ManualStepButton(
                        step = "4",
                        label = Translations.get("app_step4_uninstall", selectedLanguage),
                        icon = Icons.Default.DeleteForever,
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                                (context as? com.example.MainActivity)?.isBypassingLock = true
                                context.startActivity(intent)
                            } catch (e: Exception) {}
                        },
                        cardBgColor = cardBgColor,
                        textColor = Color(0xFFEF4444),
                        borderColor = borderColor
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = Translations.get("btn_close", selectedLanguage),
                    color = accentColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        containerColor = bgColor,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun StatusRow(
    label: String,
    isActive: Boolean,
    activeLabel: String,
    inactiveLabel: String,
    positiveIfActive: Boolean = false
) {
    val color = if (positiveIfActive) {
        if (isActive) Color(0xFF10B981) else Color(0xFFEF4444)
    } else {
        if (isActive) Color(0xFFEF4444) else Color(0xFF10B981)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (isActive) activeLabel else inactiveLabel,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun ManualStepButton(
    step: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    cardBgColor: Color,
    textColor: Color,
    borderColor: Color
) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = cardBgColor,
            contentColor = textColor
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, borderColor.copy(alpha = 0.4f)),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF3B82F6).copy(alpha = 0.2f),
                modifier = Modifier.size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(step, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60A5FA))
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = textColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ResourceMonitorScreen(
    viewModel: AdbViewModel,
    terminalTheme: String,
    selectedLanguage: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var cpuLoad by remember { mutableStateOf(0f) }
    var ramUsedByApp by remember { mutableStateOf(0f) } // MB
    var ramTotal by remember { mutableStateOf(0f) } // MB
    var ramUsed by remember { mutableStateOf(0f) } // MB
    var ramAvail by remember { mutableStateOf(0f) } // MB
    var isLowMemory by remember { mutableStateOf(false) }
    var diskTotal by remember { mutableStateOf(0f) } // GB
    var diskUsed by remember { mutableStateOf(0f) } // GB
    var diskFree by remember { mutableStateOf(0f) } // GB
    var cpuCores by remember { mutableStateOf(1) }
    val cpuHistory = remember { mutableStateListOf<Float>() }
    var batteryLevel by remember { mutableStateOf(100f) }
    var batteryTemp by remember { mutableStateOf(0f) }
    var batteryVoltage by remember { mutableStateOf(0f) }
    var batteryStatusText by remember { mutableStateOf("Normal") }
    var batteryHealthText by remember { mutableStateOf("Good") }
    var batteryTech by remember { mutableStateOf("Li-ion") }
    var networkType by remember { mutableStateOf("Checking...") }
    var activeThreadsCount by remember { mutableStateOf(1) }
    var systemUptimeText by remember { mutableStateOf("-") }

    // Theme Colors
    val (textColor, bgColor, borderColor, accentColor, cardBgColor, secText) = getThemeColors(terminalTheme)

    // Gather 100% Live, Real Hardware & System Stats without any mock or random data
    LaunchedEffect(Unit) {
        cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        while (true) {
            withContext(Dispatchers.IO) {
                // 1. Get Real RAM Stats Natively via ActivityManager
                val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                if (actManager != null) {
                    actManager.getMemoryInfo(memInfo)
                    val totalMb = (memInfo.totalMem.toDouble() / (1024.0 * 1024.0)).toFloat()
                    val availMb = (memInfo.availMem.toDouble() / (1024.0 * 1024.0)).toFloat()
                    val usedMb = (totalMb - availMb).coerceAtLeast(0f)
                    
                    ramTotal = totalMb
                    ramAvail = availMb
                    ramUsed = usedMb
                    isLowMemory = memInfo.lowMemory
                }

                // Precise JVM runtime memory
                val runtime = Runtime.getRuntime()
                ramUsedByApp = ((runtime.totalMemory() - runtime.freeMemory()).toDouble() / (1024.0 * 1024.0)).toFloat()

                // 2. Get Real Disk Storage Natively via StatFs
                try {
                    val dataDir = Environment.getDataDirectory()
                    val statFs = StatFs(dataDir.path)
                    val totalBytes = statFs.blockCountLong * statFs.blockSizeLong
                    val freeBytes = statFs.availableBlocksLong * statFs.blockSizeLong
                    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

                    diskTotal = (totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).toFloat()
                    diskFree = (freeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).toFloat()
                    diskUsed = (usedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).toFloat()
                } catch (e: Exception) {
                    val dataDir = Environment.getDataDirectory()
                    val total = dataDir.totalSpace
                    val free = dataDir.freeSpace
                    diskTotal = (total.toDouble() / (1024.0 * 1024.0 * 1024.0)).toFloat()
                    diskFree = (free.toDouble() / (1024.0 * 1024.0 * 1024.0)).toFloat()
                    diskUsed = ((total - free).toDouble() / (1024.0 * 1024.0 * 1024.0)).toFloat()
                }

                // 3. Get Real CPU load calculated from /proc/stat
                val currentCpu = CpuUsageHelper.getRealCpuUsage()
                cpuLoad = currentCpu
                activeThreadsCount = Thread.activeCount()

                // 4. Get Real Battery Stats
                try {
                    val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                    val batteryStatus = context.registerReceiver(null, intentFilter)
                    if (batteryStatus != null) {
                        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        if (level != -1 && scale != -1 && scale > 0) {
                            batteryLevel = (level * 100f / scale)
                        }
                        val temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                        batteryTemp = temp / 10f
                        val voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                        batteryVoltage = voltage / 1000f

                        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                        batteryStatusText = when {
                            status == BatteryManager.BATTERY_STATUS_FULL -> "Full"
                            status == BatteryManager.BATTERY_STATUS_CHARGING -> when (plugged) {
                                BatteryManager.BATTERY_PLUGGED_AC -> "AC Charging"
                                BatteryManager.BATTERY_PLUGGED_USB -> "USB Charging"
                                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Charging"
                                else -> "Charging"
                            }
                            else -> "Discharging"
                        }

                        val health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
                        batteryHealthText = when (health) {
                            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                            else -> "Normal"
                        }
                        batteryTech = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"
                    }
                } catch (e: Exception) {}

                // 5. Get Real Network Connection Type
                try {
                    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val activeNetwork = connectivityManager?.activeNetwork
                    val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
                    networkType = when {
                        capabilities == null -> "Disconnected"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular Network"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN Active"
                        else -> "Connected"
                    }
                } catch (e: Exception) {
                    networkType = "Unknown"
                }

                // 6. Real Uptime
                val uptimeMs = SystemClock.elapsedRealtime()
                val hrs = uptimeMs / (1000 * 60 * 60)
                val mins = (uptimeMs / (1000 * 60)) % 60
                val secs = (uptimeMs / 1000) % 60
                systemUptimeText = "${hrs}h ${mins}m ${secs}s"
            }

            if (cpuHistory.size >= 15) {
                cpuHistory.removeAt(0)
            }
            cpuHistory.add(cpuLoad)

            delay(1200)
        }
    }

    val monitorScrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize().background(bgColor.copy(alpha = 0.50f))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .verticalFadingEdge(scrollState = monitorScrollState, edgeSize = 36.dp)
                .verticalScroll(monitorScrollState)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textColor)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (selectedLanguage) {
                        "en" -> "System Monitor"
                        "ru" -> "Мониторинг системы"
                        else -> "Resurslar"
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }

            // CPU Gauge Section with Canvas Line Graph
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                border = BorderStroke(0.5.dp, borderColor.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (selectedLanguage) {
                                "uz" -> "PROTSESSOR (CPU)"
                                "ru" -> "ПРОЦЕССОР (CPU)"
                                else -> "CPU MONITOR"
                            },
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = accentColor
                        )
                        Text(
                            text = "${cpuCores} ${if (selectedLanguage == "uz") "Yadro" else if (selectedLanguage == "ru") "Ядер" else "Cores"} | ${String.format(Locale.US, "%.1f", cpuLoad)}%",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = textColor
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Real-time Canvas Graph
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(75.dp)
                            .background(bgColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .border(0.5.dp, borderColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (cpuHistory.size > 1) {
                                val stepX = size.width / (cpuHistory.size - 1).coerceAtLeast(1)
                                val maxVal = 100f
                                val path = Path()

                                for (i in 0 until cpuHistory.size) {
                                    val x = i * stepX
                                    val y = size.height - (cpuHistory[i] / maxVal * size.height).coerceIn(0f, size.height)
                                    if (i == 0) {
                                        path.moveTo(x, y)
                                    } else {
                                        path.lineTo(x, y)
                                    }
                                }

                                drawPath(
                                    path = path,
                                    color = accentColor,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Threads: $activeThreadsCount | Arch: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "-"}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = secText
                    )
                }
            }

            // RAM Memory Usage Gauge
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                border = BorderStroke(0.5.dp, borderColor.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = when (selectedLanguage) {
                            "uz" -> "RAM (OPERATIV XOTIRA)"
                            "ru" -> "ОПЕРАТИВНАЯ ПАМЯТЬ (RAM)"
                            else -> "RAM (RANDOM ACCESS MEMORY)"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = accentColor
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val ramRatio = if (ramTotal > 0f) (ramUsed / ramTotal).coerceIn(0f, 1f) else 0f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${if (selectedLanguage == "uz") "Ishlatilgan" else if (selectedLanguage == "ru") "Использовано" else "Used"}: ${String.format(Locale.US, "%.2f", ramUsed / 1024f)} GB",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = textColor
                            )
                            Text(
                                text = "${if (selectedLanguage == "uz") "Bo'sh" else if (selectedLanguage == "ru") "Свободно" else "Free"}: ${String.format(Locale.US, "%.2f", ramAvail / 1024f)} GB / ${String.format(Locale.US, "%.2f", ramTotal / 1024f)} GB",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = secText
                            )
                            Text(
                                text = "App Heap: ${String.format(Locale.US, "%.1f", ramUsedByApp)} MB",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = secText
                            )
                        }

                        Text(
                            text = "${String.format(Locale.US, "%.0f", ramRatio * 100f)}%",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = if (isLowMemory) Color.Red else accentColor
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress indicator
                    LinearProgressIndicator(
                        progress = { ramRatio },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (isLowMemory) Color.Red else accentColor,
                        trackColor = textColor.copy(alpha = 0.1f),
                        drawStopIndicator = {}
                    )
                }
            }

            // Disk Storage Usage Gauge
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                border = BorderStroke(0.5.dp, borderColor.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = when (selectedLanguage) {
                            "uz" -> "DISK (ICHKI XOTIRA)"
                            "ru" -> "ДИСК (ВНУТРЕННЯЯ ПАМЯТЬ)"
                            else -> "DISK (INTERNAL STORAGE)"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = accentColor
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val diskRatio = if (diskTotal > 0f) (diskUsed / diskTotal).coerceIn(0f, 1f) else 0f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${if (selectedLanguage == "uz") "Ishlatilgan" else if (selectedLanguage == "ru") "Использовано" else "Used"}: ${String.format(Locale.US, "%.2f", diskUsed)} GB",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = textColor
                            )
                            Text(
                                text = "${if (selectedLanguage == "uz") "Bo'sh joy" else if (selectedLanguage == "ru") "Свободно" else "Free"}: ${String.format(Locale.US, "%.2f", diskFree)} GB / ${String.format(Locale.US, "%.2f", diskTotal)} GB",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = secText
                            )
                        }

                        Text(
                            text = "${String.format(Locale.US, "%.0f", diskRatio * 100f)}%",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = accentColor
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { diskRatio },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = accentColor,
                        trackColor = textColor.copy(alpha = 0.1f),
                        drawStopIndicator = {}
                    )
                }
            }

            // Battery & Connection Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                border = BorderStroke(0.5.dp, borderColor.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = when (selectedLanguage) {
                            "uz" -> "AKKUMULYATOR VA TARMOQ"
                            "ru" -> "АККУМУЛЯТОР И СЕТЬ"
                            else -> "BATTERY & NETWORK"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = accentColor
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${if (selectedLanguage == "uz") "Zaryad" else if (selectedLanguage == "ru") "Уровень заряда" else "Battery"}: ${batteryLevel.toInt()}% ($batteryStatusText)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = textColor
                            )
                            Text(
                                text = "${if (selectedLanguage == "uz") "Harorat" else if (selectedLanguage == "ru") "Температура" else "Temperature"}: ${String.format(Locale.US, "%.1f", batteryTemp)}°C | ${String.format(Locale.US, "%.2f", batteryVoltage)}V",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = secText
                            )
                            Text(
                                text = "Health: $batteryHealthText | Tech: $batteryTech",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = secText
                            )
                            Text(
                                text = "${if (selectedLanguage == "uz") "Tarmoq" else if (selectedLanguage == "ru") "Тип сети" else "Network"}: $networkType",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = secText
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Battery",
                            tint = if (batteryLevel > 20f) accentColor else Color.Red,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { (batteryLevel / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (batteryLevel > 20f) accentColor else Color.Red,
                        trackColor = textColor.copy(alpha = 0.1f),
                        drawStopIndicator = {}
                    )
                }
            }

            // Device Specifications Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                border = BorderStroke(0.5.dp, borderColor.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = when (selectedLanguage) {
                            "uz" -> "QURILMA VA APPARAT TAVSIFI"
                            "ru" -> "ХАРАКТЕРИСТИКИ УСТРОЙСТВА"
                            else -> "DEVICE SPECIFICATIONS"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = accentColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "• Model: ${Build.MANUFACTURER.uppercase()} ${Build.MODEL} (${Build.DEVICE})",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = textColor
                    )
                    Text(
                        text = "• OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = textColor
                    )
                    Text(
                        text = "• Chipset / Board: ${Build.BOARD} / ${Build.HARDWARE}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = secText
                    )
                    Text(
                        text = "• Uptime: $systemUptimeText",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = secText
                    )
                }
            }
        }
    }
}


@Composable
fun FileSystemBrowserScreen(
    viewModel: AdbViewModel,
    terminalTheme: String,
    selectedLanguage: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableStateOf(0) }

    val safLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val takeFlags: Int = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                refreshTrigger++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var isSearchFocused by remember { mutableStateOf(false) }

    val safRefreshState by viewModel.safPermissionRefreshTrigger.collectAsState()
    LaunchedEffect(safRefreshState) {
        if (safRefreshState > 0) {
            refreshTrigger++
        }
    }


    // Dynamic BroadcastReceiver to trigger immediate refresh on USB plug/unplug or media mount/unmount
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                refreshTrigger++
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(android.content.Intent.ACTION_MEDIA_MOUNTED)
            addAction(android.content.Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(android.content.Intent.ACTION_MEDIA_EJECT)
            addDataScheme("file")
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            try {
                context.registerReceiver(receiver, filter)
            } catch (ex: Exception) {}
        }
        
        // Register a separate filter without data scheme for USB attachment/detachment actions
        val usbFilter = android.content.IntentFilter().apply {
            addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, usbFilter, android.content.Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, usbFilter)
            }
        } catch (e: Exception) {
            try {
                context.registerReceiver(receiver, usbFilter)
            } catch (ex: Exception) {}
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    // SD Card and USB storage devices (Real probe using standard Android API)
    val secondaryStorages = remember(refreshTrigger) {
        val list = mutableListOf<File>()
        try {
            val dirs = context.getExternalFilesDirs(null)
            if (dirs != null && dirs.size > 1) {
                for (i in 1 until dirs.size) {
                    val dir = dirs[i]
                    if (dir != null) {
                        var root = dir
                        // Traverse up to find the root folder of the secondary storage (above /Android)
                        while (root.parentFile != null && root.name != "Android" && !root.absolutePath.endsWith("/Android")) {
                            root = root.parentFile
                        }
                        if (root.name == "Android") {
                            root = root.parentFile ?: root
                        }
                        if (root != null && root.totalSpace > 0) {
                            if (!list.contains(root)) {
                                list.add(root)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        list
    }

    val classifiedStorages = remember(secondaryStorages) {
        val sdList = mutableListOf<File>()
        val usbList = mutableListOf<File>()
        
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? android.hardware.usb.UsbManager
        val hasUsbConnected = usbManager?.deviceList?.isNotEmpty() == true
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? android.os.storage.StorageManager

        for (root in secondaryStorages) {
            val path = root.absolutePath.lowercase()
            val volume = try {
                storageManager?.getStorageVolume(root)
            } catch (e: Exception) { null }
            val description = volume?.getDescription(context)?.lowercase() ?: ""
            
            val volumeId = try {
                val method = volume?.javaClass?.getMethod("getId")
                method?.invoke(volume) as? String
            } catch (e: Exception) {
                try {
                    volume?.let { v ->
                        val idField = v.javaClass.getDeclaredField("mId")
                        idField.isAccessible = true
                        idField.get(v) as? String
                    }
                } catch (ex: Exception) { null }
            }?.lowercase() ?: ""

            val blockDevice = ""

            // Check direct path or description indicators
            var isUsb = path.contains("usb") || 
                        path.contains("otg") || 
                        path.contains("udisk") || 
                        path.contains("usbshare") ||
                        path.contains("sda") ||
                        path.contains("sdb") ||
                        path.contains("sdc")

            if (!isUsb) {
                isUsb = description.contains("usb") || 
                        description.contains("накопитель") || 
                        description.contains("otg") || 
                        description.contains("flash") || 
                        description.contains("drive") ||
                        description.contains("pen") ||
                        description.contains("thumb")
            }

            if (volumeId.isNotEmpty()) {
                if (volumeId.contains("public:179") || volumeId.contains("mmc")) {
                    isUsb = false
                } else if (volumeId.contains("public:8") || volumeId.contains("usb") || volumeId.contains("sdc") || volumeId.contains("sdb") || volumeId.contains("sda")) {
                    isUsb = true
                }
            }

            if (blockDevice.isNotEmpty()) {
                if (blockDevice.contains("public:179") || blockDevice.contains("mmcblk")) {
                    isUsb = false
                } else if (blockDevice.contains("public:8") || blockDevice.contains("sda") || blockDevice.contains("sdb") || blockDevice.contains("sdc")) {
                    isUsb = true
                }
            }

            // Heuristic fallback: if USB is physically connected, and this volume path/description
            // does NOT look like an SD card, then classify it as a USB drive!
            if (!isUsb && hasUsbConnected) {
                val looksLikeSd = path.contains("sd") || 
                                  path.contains("card") || 
                                  description.contains("sd") || 
                                  description.contains("card")
                if (!looksLikeSd) {
                    isUsb = true
                }
            }

            if (isUsb) {
                usbList.add(root)
            } else {
                sdList.add(root)
            }
        }

        // Smart safety fallback: if exactly 2 secondary storages are found but both got
        // classified to the same bucket, split them so first is SD card and second is USB.
        if (secondaryStorages.size == 2 && sdList.size == 2) {
            sdList.clear()
            usbList.clear()
            sdList.add(secondaryStorages[0])
            usbList.add(secondaryStorages[1])
        } else if (secondaryStorages.size == 2 && usbList.size == 2) {
            sdList.clear()
            usbList.clear()
            sdList.add(secondaryStorages[0])
            usbList.add(secondaryStorages[1])
        }

        Pair(sdList, usbList)
    }

    // Core Navigation States
    var isDashboardMode by remember { mutableStateOf(true) }
    var activeCategory by remember { mutableStateOf<String?>(null) } // "Images", "Videos", "Audio", "Documents", "APKs"
    var currentDirectory by remember { mutableStateOf(File("/sdcard")) }
    
    // File list states
    var fileList by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var rawFileList by remember { mutableStateOf<List<FileItem>>(emptyList()) } // for category caching
    var isLoading by remember { mutableStateOf(false) }
    var showHiddenFiles by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Clipboard / File Operations (Amaze style)
    var clipboardFile by remember { mutableStateOf<File?>(null) }
    var isCutOperation by remember { mutableStateOf(false) }
    
    // File Action Dialogs
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var selectedItemForAction by remember { mutableStateOf<FileItem?>(null) }
    var dialogInputText by remember { mutableStateOf("") }
    
    // Native Storage States
    var diskTotal by remember { mutableStateOf(128f) }
    var diskUsed by remember { mutableStateOf(64f) }
    
    // File Viewer / Editor
    var selectedFileContent by remember { mutableStateOf<String?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var showViewer by remember { mutableStateOf(false) }
    var isEditingFile by remember { mutableStateOf(false) }
    
    // Image Preview Dialog
    var showImagePreviewDialog by remember { mutableStateOf(false) }
    var imagePathToPreview by remember { mutableStateOf("") }
    
    // Multi-Select & Action States
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedItemPaths by remember { mutableStateOf(emptySet<String>()) }
    var multiClipboardPaths by remember { mutableStateOf(emptySet<String>()) }
    var isMultiCutOperation by remember { mutableStateOf(false) }
    
    var showMultiDeleteConfirm by remember { mutableStateOf(false) }
    var showMultiZipConfirm by remember { mutableStateOf(false) }
    var showMultiDetailsDialog by remember { mutableStateOf(false) }
    
    // Specialized In-App Viewers
    var showAudioPlayerDialog by remember { GlobalStateDelegate({ GlobalMediaPlayer.showAudioPlayerDialog }, { GlobalMediaPlayer.showAudioPlayerDialog = it }) }
    var audioPathToPlay by remember { GlobalStateDelegate({ GlobalMediaPlayer.currentPlayingAudioPath }, { GlobalMediaPlayer.currentPlayingAudioPath = it }) }
    var showVideoPlayerDialog by remember { GlobalStateDelegate({ GlobalMediaPlayer.showVideoPlayerDialog }, { GlobalMediaPlayer.showVideoPlayerDialog = it }) }
    var videoPathToPlay by remember { GlobalStateDelegate({ GlobalMediaPlayer.videoPathToPlay }, { GlobalMediaPlayer.videoPathToPlay = it }) }
    var showPdfViewerDialog by remember { mutableStateOf(false) }
    var pdfPathToView by remember { mutableStateOf("") }
    var showZipViewerDialog by remember { mutableStateOf(false) }
    var zipPathToView by remember { mutableStateOf("") }
    
    // Global Dashboard Search Results
    var searchResultsList by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isSearchingGlobally by remember { mutableStateOf(false) }

    // Manual Path Editing States
    var isEditingPath by remember { mutableStateOf(false) }
    var pathInputText by remember { mutableStateOf("") }

    // Shared Media Player States
    val sharedMediaPlayer = GlobalMediaPlayer.getOrCreatePlayer(context)
    var currentPlayingAudioPath by remember { GlobalStateDelegate({ GlobalMediaPlayer.currentPlayingAudioPath }, { GlobalMediaPlayer.currentPlayingAudioPath = it }) }
    var isAudioPlaying by remember { GlobalStateDelegate({ GlobalMediaPlayer.isAudioPlaying }, { GlobalMediaPlayer.isAudioPlaying = it }) }
    var isAudioMinimized by remember { GlobalStateDelegate({ GlobalMediaPlayer.isAudioMinimized }, { GlobalMediaPlayer.isAudioMinimized = it }) }
    var audioPlaybackPosition by remember { GlobalStateDelegate({ GlobalMediaPlayer.audioPlaybackPosition }, { GlobalMediaPlayer.audioPlaybackPosition = it }) }
    var audioDuration by remember { GlobalStateDelegate({ GlobalMediaPlayer.audioDuration }, { GlobalMediaPlayer.audioDuration = it }) }
    var audioLoopingEnabled by remember { GlobalStateDelegate({ GlobalMediaPlayer.audioLoopingEnabled }, { GlobalMediaPlayer.audioLoopingEnabled = it }) }
    var audioPlaybackSpeed by remember { GlobalStateDelegate({ GlobalMediaPlayer.audioPlaybackSpeed }, { GlobalMediaPlayer.audioPlaybackSpeed = it }) }
    var audioPlaylist by remember { GlobalStateDelegate({ GlobalMediaPlayer.audioPlaylist }, { GlobalMediaPlayer.audioPlaylist = it }) }
    var audioPlaylistIndex by remember { GlobalStateDelegate({ GlobalMediaPlayer.audioPlaylistIndex }, { GlobalMediaPlayer.audioPlaylistIndex = it }) }

    // Video Playlist / Player States
    var videoLoopingEnabled by remember { GlobalStateDelegate({ GlobalMediaPlayer.videoLoopingEnabled }, { GlobalMediaPlayer.videoLoopingEnabled = it }) }
    var videoPlaybackSpeed by remember { GlobalStateDelegate({ GlobalMediaPlayer.videoPlaybackSpeed }, { GlobalMediaPlayer.videoPlaybackSpeed = it }) }
    var videoPlaylist by remember { GlobalStateDelegate({ GlobalMediaPlayer.videoPlaylist }, { GlobalMediaPlayer.videoPlaylist = it }) }
    var videoPlaylistIndex by remember { GlobalStateDelegate({ GlobalMediaPlayer.videoPlaylistIndex }, { GlobalMediaPlayer.videoPlaylistIndex = it }) }

    // Helper to find playable playlists
    val getPlayablePlaylist = { currentPath: String, isVideo: Boolean ->
        val typePrefix = if (isVideo) "video/" else "audio/"
        if (activeCategory != null) {
            rawFileList.filter { file ->
                getFileMimeType(file.path).startsWith(typePrefix)
            }.map { it.path }
        } else {
            fileList.filter { file ->
                getFileMimeType(file.path).startsWith(typePrefix)
            }.map { it.path }
        }
    }

    // Shared audio player play function
    val playAudioFile = { path: String ->
        try {
            sharedMediaPlayer.reset()
            val isSecondary = SafStorageHelper.isSecondaryStoragePath(path)
            if (isSecondary) {
                val doc = SafStorageHelper.getDocumentFileForPath(context, path)
                if (doc != null) {
                    sharedMediaPlayer.setDataSource(context, doc.uri)
                } else {
                    sharedMediaPlayer.setDataSource(path)
                }
            } else {
                sharedMediaPlayer.setDataSource(path)
            }
            sharedMediaPlayer.prepare()
            audioDuration = sharedMediaPlayer.duration
            sharedMediaPlayer.isLooping = audioLoopingEnabled
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    val params = sharedMediaPlayer.playbackParams
                    params.speed = audioPlaybackSpeed
                    sharedMediaPlayer.playbackParams = params
                } catch (e: Exception) {}
            }
            sharedMediaPlayer.start()
            currentPlayingAudioPath = path
            isAudioPlaying = true
            isAudioMinimized = false
            showAudioPlayerDialog = true
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Error playing audio: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Keep media player running in background when switching tabs
        }
    }

    LaunchedEffect(sharedMediaPlayer) {
        sharedMediaPlayer.setOnCompletionListener {
            if (!audioLoopingEnabled) {
                if (audioPlaylist.isNotEmpty() && audioPlaylistIndex >= 0) {
                    val nextIndex = (audioPlaylistIndex + 1) % audioPlaylist.size
                    audioPlaylistIndex = nextIndex
                    playAudioFile(audioPlaylist[nextIndex])
                } else {
                    isAudioPlaying = false
                }
            }
        }
    }

    LaunchedEffect(isAudioPlaying, currentPlayingAudioPath) {
        while (isAudioPlaying && currentPlayingAudioPath.isNotEmpty()) {
            try {
                if (sharedMediaPlayer.isPlaying) {
                    audioPlaybackPosition = sharedMediaPlayer.currentPosition
                }
            } catch (e: Exception) {}
            delay(500)
        }
    }

    // Comprehensive Back Button routing Handler
    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            showViewer -> showViewer = false
            showImagePreviewDialog -> showImagePreviewDialog = false
            showAudioPlayerDialog -> {
                showAudioPlayerDialog = false
                isAudioMinimized = true
            }
            showVideoPlayerDialog -> showVideoPlayerDialog = false
            showPdfViewerDialog -> showPdfViewerDialog = false
            showZipViewerDialog -> showZipViewerDialog = false
            showMultiDetailsDialog -> showMultiDetailsDialog = false
            showMultiDeleteConfirm -> showMultiDeleteConfirm = false
            showMultiZipConfirm -> showMultiZipConfirm = false
            showRenameDialog -> showRenameDialog = false
            showDeleteConfirmDialog -> showDeleteConfirmDialog = false
            showNewFolderDialog -> showNewFolderDialog = false
            showNewFileDialog -> showNewFileDialog = false
            isEditingPath -> isEditingPath = false
            isMultiSelectMode -> {
                isMultiSelectMode = false
                selectedItemPaths = emptySet()
            }
            isSearchFocused -> {
                focusManager.clearFocus()
            }
            searchQuery.isNotEmpty() -> {
                searchQuery = ""
            }
            activeCategory != null -> {
                activeCategory = null
                isDashboardMode = true
            }
            !isDashboardMode -> {
                val sdCardPaths = classifiedStorages.first.map { it.canonicalPath.lowercase() }
                val usbPaths = classifiedStorages.second.map { it.canonicalPath.lowercase() }
                val currentPath = currentDirectory.canonicalPath.lowercase()
                
                val isAtRoot = currentPath == "/storage/emulated/0" || 
                               currentPath == "/sdcard" || 
                               sdCardPaths.contains(currentPath) || 
                               usbPaths.contains(currentPath)
                
                val parent = currentDirectory.parentFile
                if (parent != null && !isAtRoot) {
                    currentDirectory = parent
                } else {
                    isDashboardMode = true
                }
            }
            else -> {
                onBack()
            }
        }
    }
    
    var hasPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }

    // Auto check permissions loop when user returns from settings (stops once granted)
    LaunchedEffect(Unit) {
        while (!hasPermission) {
            hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (hasPermission) break
            delay(3000)
        }
    }

    // Dynamic background search runner for Dashboard Mode
    LaunchedEffect(searchQuery, isDashboardMode) {
        if (isDashboardMode && searchQuery.isNotBlank()) {
            isSearchingGlobally = true
            scope.launch(Dispatchers.IO) {
                val results = mutableListOf<FileItem>()
                try {
                    File("/sdcard").walkTopDown().onEnter { it.name != "Android" || it.parentFile?.name != "sdcard" }
                        .maxDepth(3)
                        .filter { it.name.contains(searchQuery, ignoreCase = true) }
                        .take(100)
                        .forEach { file ->
                            val isDir = file.isDirectory
                            val size = if (isDir) 0L else file.length()
                            val perms = if (isDir) "drwxrwxrwx" else "-rwxrwxrwx"
                            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            val modified = try { sdf.format(Date(file.lastModified())) } catch (e: Exception) { "-" }
                            results.add(FileItem(file.name, file.absolutePath, isDir, size, perms, modified, file))
                        }
                } catch (e: Exception) {}
                withContext(Dispatchers.Main) {
                    searchResultsList = results.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    isSearchingGlobally = false
                }
            }
        } else {
            searchResultsList = emptyList()
        }
    }

                    // Update Disk Stats
    val updateDiskStats = {
        try {
            val dataDir = Environment.getDataDirectory()
            val statFs = StatFs(dataDir.path)
            val totalBytes = statFs.blockCountLong * statFs.blockSizeLong
            val freeBytes = statFs.availableBlocksLong * statFs.blockSizeLong
            val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
            diskTotal = (totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).toFloat()
            diskUsed = (usedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).toFloat()
        } catch (e: Exception) {
            val dataDir = Environment.getDataDirectory()
            val total = dataDir.totalSpace
            val free = dataDir.freeSpace
            diskTotal = (total.toDouble() / (1024.0 * 1024.0 * 1024.0)).toFloat().coerceAtLeast(1f)
            diskUsed = ((total - free).toDouble() / (1024.0 * 1024.0 * 1024.0)).toFloat().coerceAtLeast(0f)
        }
    }

    // Load Files function (native listing + high quality fallback shell tools)
    val loadFiles = {
        isLoading = true
        val list = mutableListOf<FileItem>()
        
        try {
            val isSecondaryStorage = SafStorageHelper.isSecondaryStoragePath(currentDirectory.absolutePath)
            val isAuthorized = SafStorageHelper.isVolumeAuthorized(context, currentDirectory.absolutePath)

            if (isSecondaryStorage && isAuthorized) {
                val docFile = SafStorageHelper.getDocumentFileForPath(context, currentDirectory.absolutePath)
                val files = docFile?.listFiles()
                if (files != null) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    files.forEach { doc ->
                        val name = doc.name ?: ""
                        if (!showHiddenFiles && name.startsWith(".")) {
                            return@forEach
                        }
                        val isDir = doc.isDirectory
                        val size = if (isDir) 0L else doc.length()
                        val modified = try { sdf.format(Date(doc.lastModified())) } catch (e: Exception) { "-" }
                        val perms = "rwx"
                        val childPath = if (currentDirectory.absolutePath.endsWith("/")) {
                            "${currentDirectory.absolutePath}$name"
                        } else {
                            "${currentDirectory.absolutePath}/$name"
                        }
                        list.add(FileItem(name, childPath, isDir, size, perms, modified, File(childPath)))
                    }
                }
            } else {
                val rawFiles = try {
                    currentDirectory.listFiles()
                } catch (e: Exception) {
                    null
                }
                
                if (rawFiles != null) {
                    rawFiles.forEach { file ->
                        if (!showHiddenFiles && file.name.startsWith(".")) {
                            return@forEach
                        }
                        val isDir = file.isDirectory
                        val size = if (isDir) 0L else file.length()
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val modified = try { sdf.format(Date(file.lastModified())) } catch (e: Exception) { "-" }
                        val read = if (file.canRead()) "r" else "-"
                        val write = if (file.canWrite()) "w" else "-"
                        val exec = if (file.canExecute()) "x" else "-"
                        val perms = "$read$write$exec"
                        
                        list.add(FileItem(file.name, file.absolutePath, isDir, size, perms, modified, file))
                    }
                }
            }
        } catch (e: Exception) {
            // Gracefully ignore error
        }

        // Sort: directories first, then alphabetically
        fileList = list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        isLoading = false
    }

                    // Load category files in background using fast find shell indexer + directory walk
    val loadCategoryFiles = { category: String ->
        isLoading = true
        activeCategory = category
        isDashboardMode = false
        
        val extensions = when (category) {
            "Images" -> listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp")
            "Videos" -> listOf(".mp4", ".mkv", ".avi", ".3gp", ".mov", ".webm")
            "Audio" -> listOf(".mp3", ".wav", ".ogg", ".m4a", ".flac", ".aac")
            "Documents" -> listOf(".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".epub", ".rtf", ".csv")
            "APKs" -> listOf(".apk")
            else -> emptyList()
        }
        
        scope.launch(Dispatchers.IO) {
            val list = mutableListOf<FileItem>()
            val extLower = extensions.map { it.lowercase() }
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            // 1. Try querying MediaStore for instant real results
            try {
                val uri = when (category) {
                    "Images" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "Videos" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "Audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> MediaStore.Files.getContentUri("external")
                }
                val projection = arrayOf(
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED
                )
                val cursor = context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )
                cursor?.use { c ->
                    val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val dateIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)

                    while (c.moveToNext()) {
                        val path = if (dataIdx >= 0) c.getString(dataIdx) else null
                        val name = if (nameIdx >= 0) c.getString(nameIdx) else (if (path != null) File(path).name else null)
                        val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                        val dateSec = if (dateIdx >= 0) c.getLong(dateIdx) else 0L

                        if (path != null && name != null) {
                            if (extLower.any { name.lowercase().endsWith(it) }) {
                                val modStr = if (dateSec > 0) sdf.format(Date(dateSec * 1000L)) else "-"
                                val file = File(path)
                                list.add(FileItem(name, path, false, size, "-rw-r--r--", modStr, file))
                            }
                        }
                    }
                }
            } catch (e: Exception) {}

            // 2. Fallback to storage traversal if MediaStore is empty or for APKs
            if (list.isEmpty()) {
                val roots = mutableListOf<String>("/sdcard")
                try {
                    classifiedStorages.first.forEach { roots.add(it.canonicalPath) }
                    classifiedStorages.second.forEach { roots.add(it.canonicalPath) }
                } catch (e: Exception) {}

                // Check via fast shell find command
                val extPatterns = extensions.joinToString(" -o ") { ext -> "-name '*$ext'" }
                val rootsStr = roots.joinToString(" ") { r -> "'$r'" }
                val findCmd = "find $rootsStr -maxdepth 4 ( $extPatterns )"
                val res = viewModel.executeLocalCommand(findCmd)
                if (res.isNotBlank() && !res.contains("Permission") && !res.contains("No such")) {
                    val lines = res.split("\n")
                    lines.forEach { path ->
                        val trimmed = path.trim()
                        if (trimmed.isNotEmpty()) {
                            val file = File(trimmed)
                            val size = file.length()
                            val modified = try { sdf.format(Date(file.lastModified())) } catch (e: Exception) { "-" }
                            list.add(FileItem(file.name, file.absolutePath, false, size, "-rw-r--r--", modified, file))
                        }
                    }
                }

                // Fallback walk if shell yielded nothing
                if (list.isEmpty()) {
                    roots.forEach { rootPath ->
                        try {
                            File(rootPath).walkTopDown().onEnter { it.name != "Android" || it.parentFile?.name != "sdcard" }
                                .maxDepth(4)
                                .filter { it.isFile && extLower.any { ext -> it.name.lowercase().endsWith(ext) } }
                                .take(200)
                                .forEach { file ->
                                    val size = file.length()
                                    val modified = try { sdf.format(Date(file.lastModified())) } catch (e: Exception) { "-" }
                                    list.add(FileItem(file.name, file.absolutePath, false, size, "-rw-r--r--", modified, file))
                                }
                        } catch (e: Exception) {}
                    }
                }
            }

            withContext(Dispatchers.Main) {
                rawFileList = list
                fileList = list.sortedBy { it.name.lowercase() }
                isLoading = false
            }
        }
    }







    // Trigger state loads
    LaunchedEffect(currentDirectory, showHiddenFiles, hasPermission, isDashboardMode, activeCategory) {
        if (hasPermission) {
            updateDiskStats()
            if (!isDashboardMode && activeCategory == null) {
                loadFiles()
            }
        } else {
            fileList = emptyList()
        }
    }

    // Read File content natively / fallback shell
    val viewFileContent = { fileItem: FileItem ->
        isLoading = true
        selectedFileName = fileItem.name
        isEditingFile = false
        
        scope.launch(Dispatchers.IO) {
            try {
                var content = ""
                var readSuccess = false
                
                val safStream = SafStorageHelper.getInputStream(context, fileItem.path)
                if (safStream != null) {
                    try {
                        content = safStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.take(25000)
                        readSuccess = true
                    } catch (e: Exception) {
                        try { safStream.close() } catch (ex: Exception) {}
                    }
                }
                
                if (!readSuccess) {
                    val f = fileItem.nativeFile ?: File(fileItem.path)
                    if (f.canRead()) {
                        try {
                            content = f.readText(Charsets.UTF_8).take(25000)
                            readSuccess = true
                        } catch (e: Exception) {
                            // try cat
                        }
                    }
                    if (!readSuccess) {
                        val res = viewModel.executeRootOrAdbOrLocalCommand("cat \"${fileItem.path}\"")
                        if (res.isNotBlank() && !res.contains("Permission denied") && !res.contains("permission denied")) {
                            content = res.take(25000)
                        } else {
                            content = when (selectedLanguage) {
                                "en" -> "Error: Permission Denied to read this file."
                                "ru" -> "Ошибка: Нет прав доступа для чтения этого файла."
                                else -> "Xatolik: Ushbu faylni o'qish uchun ruxsat yo'q."
                            }
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    selectedFileContent = content
                    showViewer = true
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    selectedFileContent = "Exception: ${e.localizedMessage}"
                    showViewer = true
                    isLoading = false
                }
            }
        }
    }

    // Save modified file text natively / fallback su base64
    val saveFileText = { path: String, newText: String ->
        isLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(path)
                var success = false
                
                val safOut = SafStorageHelper.getOutputStream(context, path)
                if (safOut != null) {
                    try {
                        safOut.write(newText.toByteArray(Charsets.UTF_8))
                        safOut.flush()
                        safOut.close()
                        success = true
                    } catch (e: Exception) {
                        try { safOut.close() } catch (ex: Exception) {}
                    }
                } else {
                    try {
                        file.writeText(newText)
                        success = true
                    } catch (e: Exception) {
                        success = false
                    }
                }
                
                if (!success) {
                    val base64Bytes = android.util.Base64.encodeToString(newText.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                    val cmd = "echo '$base64Bytes' | base64 -d > \"${file.absolutePath}\""
                    val res = viewModel.executeRootOrAdbOrLocalCommand(cmd)
                    success = file.exists() && !res.contains("Permission denied") && !res.contains("permission denied")
                }
                
                withContext(Dispatchers.Main) {
                    isLoading = false
                    if (success) {
                        android.widget.Toast.makeText(context, 
                            if (selectedLanguage == "uz") "Fayl muvaffaqiyatli saqlandi!" else if (selectedLanguage == "ru") "Файл успешно сохранен!" else "File saved successfully!", 
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        showViewer = false
                        if (activeCategory == null) loadFiles()
                    } else {
                        android.widget.Toast.makeText(context, 
                            if (selectedLanguage == "uz") "Saqlab bo'lmadi: Ruxsat berilmagan" else if (selectedLanguage == "ru") "Ошибка записи: Нет прав" else "Failed to save: Permission Denied", 
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    android.widget.Toast.makeText(context, "Error: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Helper to decode image local bitmap natively
    val loadLocalBitmap: (String) -> android.graphics.Bitmap? = { path: String ->
        try {
            val stream = SafStorageHelper.getInputStream(context, path)
            if (stream != null) {
                stream.use { android.graphics.BitmapFactory.decodeStream(it) }
            } else {
                android.graphics.BitmapFactory.decodeFile(path)
            }
        } catch (e: Exception) {
            null
        }
    }

    // Theme Colors
    val (textColor, bgColor, borderColor, accentColor, cardBgColor, secText) = getThemeColors(terminalTheme)

    Box(modifier = Modifier.fillMaxSize().background(bgColor.copy(alpha = 0.50f))) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (!isDashboardMode) {
                        isDashboardMode = true
                        activeCategory = null
                        searchQuery = ""
                    } else {
                        onBack()
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textColor)
                }
                Spacer(modifier = Modifier.width(8.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (selectedLanguage) {
                            "en" -> "Files"
                            "ru" -> "Файлы"
                            else -> "Fayllar"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    if (!isDashboardMode) {
                        Text(
                            text = if (activeCategory != null) {
                                when (activeCategory) {
                                    "Images" -> if (selectedLanguage == "uz") "Tasvirlar" else if (selectedLanguage == "ru") "Изображения" else "Images"
                                    "Videos" -> if (selectedLanguage == "uz") "Videolar" else if (selectedLanguage == "ru") "Видео" else "Videos"
                                    "Audio" -> "Audio"
                                    "Documents" -> if (selectedLanguage == "uz") "Hujjatlar" else if (selectedLanguage == "ru") "Документы" else "Documents"
                                    "APKs" -> if (selectedLanguage == "uz") "Ilovalar (APK)" else if (selectedLanguage == "ru") "Установочные APK" else "Installation files"
                                    else -> ""
                                }
                            } else {
                                val currentPath = currentDirectory.canonicalPath.lowercase()
                                val sdCardPaths = classifiedStorages.first.map { it.canonicalPath.lowercase() }
                                val usbPaths = classifiedStorages.second.map { it.canonicalPath.lowercase() }
                                when {
                                    currentPath == "/storage/emulated/0" || currentPath == "/sdcard" -> {
                                        if (selectedLanguage == "uz") "Ichki xotira" else if (selectedLanguage == "ru") "Внутреннее хранилище" else "Internal storage"
                                    }
                                    sdCardPaths.contains(currentPath) -> {
                                        if (selectedLanguage == "uz") "SD karta" else if (selectedLanguage == "ru") "SD-карта" else "SD card"
                                    }
                                    usbPaths.contains(currentPath) -> {
                                        if (selectedLanguage == "uz") "USB-ichki xotira" else if (selectedLanguage == "ru") "USB-накопитель" else "USB storage"
                                    }
                                    else -> currentDirectory.name
                                }
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Add Quick Reload Button
                IconButton(onClick = {
                    updateDiskStats()
                    if (!isDashboardMode) {
                        if (activeCategory != null) {
                            loadCategoryFiles(activeCategory!!)
                        } else {
                            loadFiles()
                        }
                    }
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = textColor)
                }
            }

            // Realtime Search Bar (Samsung My Files Style)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .onFocusChanged { isSearchFocused = it.isFocused },
                placeholder = {
                    Text(
                        text = when (selectedLanguage) {
                            "uz" -> "Fayllarni qidirish..."
                            "ru" -> "Поиск файлов..."
                            else -> "Search files..."
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = secText
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = secText, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = secText, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = borderColor.copy(alpha = 0.5f),
                    focusedContainerColor = cardBgColor,
                    unfocusedContainerColor = cardBgColor
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            )

            // Permissions Check
            if (!hasPermission) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(64.dp)
                        )
                        
                        Text(
                            text = when (selectedLanguage) {
                                "uz" -> "Xotiraga kirish ruxsati kerak"
                                "ru" -> "Требуется доступ к хранилищу"
                                else -> "Storage Access Required"
                            },
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = textColor
                        )
                        
                        Text(
                            text = when (selectedLanguage) {
                                "uz" -> "Samsung va boshqa yangi Android telefonlarida barcha fayllarni ko'rish va boshqarish uchun tizim tomonidan 'Barcha fayllarga ruxsat berish' huquqi yoqilishi shart."
                                "ru" -> "Для просмотра и управления файлами во внутренней памяти на устройствах Android необходимо предоставить разрешение 'Доступ ко всем файлам'."
                                else -> "To browse and manage files inside internal storage on modern Android devices, you must grant the 'All Files Access' system permission."
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = secText,
                            textAlign = TextAlign.Center
                        )
                        
                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    try {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = android.net.Uri.parse("package:${context.packageName}")
                                        }
                                        
                                                                (context as? com.example.MainActivity)?.isBypassingLock = true; context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                            
                                                                (context as? com.example.MainActivity)?.isBypassingLock = true; context.startActivity(intent)
                                        } catch (ex: Exception) {}
                                    }
                                } else {
                                    // Handle legacy permissions
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = bgColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = when (selectedLanguage) {
                                    "uz" -> "Ruxsat Berish"
                                    "ru" -> "Предоставить"
                                    else -> "Grant Access"
                                },
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ProfessionalSpinningLoader(color = accentColor)
                }
            } else if (isDashboardMode) {
                if (searchQuery.isNotBlank()) {
                    if (isSearchingGlobally) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            ProfessionalSpinningLoader(color = accentColor)
                        }
                    } else if (searchResultsList.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (selectedLanguage == "uz") "Hech qanday fayl topilmadi" else if (selectedLanguage == "ru") "Файлы не найдены" else "No matching files found",
                                fontFamily = FontFamily.Monospace,
                                color = secText,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Text(
                                text = (if (selectedLanguage == "uz") "Qidiruv natijalari" else if (selectedLanguage == "ru") "Результаты поиска" else "Search results") + " (${searchResultsList.size})",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            
                            LazyColumn(modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalFadingEdge(), 
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(searchResultsList, key = { it.path }) { item ->
                                    val itemExt = item.name.substringAfterLast('.', "").lowercase()
                                    val itemMime = getFileMimeType(item.path)
                                    val (itemIcon, iconTint) = when {
                                        item.isDirectory -> Icons.Default.Folder to Color(0xFFFFB300)
                                        itemMime.startsWith("image/") -> Icons.Default.Image to Color(0xFFE91E63)
                                        itemMime.startsWith("video/") -> Icons.Default.PlayArrow to Color(0xFF9C27B0)
                                        itemMime.startsWith("audio/") -> Icons.Default.MusicNote to Color(0xFF2196F3)
                                        itemMime == "application/pdf" || itemMime.startsWith("text/") -> Icons.Default.Description to Color(0xFF4CAF50)
                                        itemExt == "apk" -> Icons.Default.Android to Color(0xFF3DDC84)
                                        else -> Icons.Default.InsertDriveFile to Color(0xFF9E9E9E)
                                    }
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (item.isDirectory) {
                                                    currentDirectory = File(item.path)
                                                    isDashboardMode = false
                                                    searchQuery = ""
                                                } else {
                                                    val ext = itemExt
                                                    val mime = itemMime
                                                    when {
                                                        mime.startsWith("image/") -> {
                                                            imagePathToPreview = item.path
                                                            showImagePreviewDialog = true
                                                        }
                                                        mime.startsWith("audio/") -> {
                                                            val plist = getPlayablePlaylist(item.path, false)
                                                            audioPlaylist = plist
                                                            audioPlaylistIndex = plist.indexOf(item.path)
                                                            playAudioFile(item.path)
                                                        }
                                                        mime.startsWith("video/") -> {
                                                            videoPathToPlay = item.path
                                                            val plist = getPlayablePlaylist(item.path, true)
                                                            videoPlaylist = plist
                                                            videoPlaylistIndex = plist.indexOf(item.path)
                                                            showVideoPlayerDialog = true
                                                        }
                                                        ext == "pdf" -> {
                                                            pdfPathToView = item.path
                                                            showPdfViewerDialog = true
                                                        }
                                                        ext == "zip" -> {
                                                            zipPathToView = item.path
                                                            showZipViewerDialog = true
                                                        }
                                                        mime.startsWith("text/") || listOf("json", "xml", "prop", "conf", "log", "sh").contains(ext) -> {
                                                            viewFileContent(item)
                                                        }
                                                        else -> {
                                                            try {
                                                                val file = File(item.path)
                                                                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                                    setDataAndType(uri, mime)
                                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                                }
                                                                
                                                                (context as? com.example.MainActivity)?.isBypassingLock = true; context.startActivity(intent)
                                                            } catch (e: Exception) {
                                                                viewFileContent(item)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(imageVector = itemIcon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = item.name, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(text = "${item.path} | ${if (item.isDirectory) "Dir" else "${String.format("%.1f", item.size / 1024f)} KB"}", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = secText)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val dashScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalFadingEdge(scrollState = dashScrollState, edgeSize = 36.dp)
                            .verticalScroll(dashScrollState)
                    ) {
                    // Category Header
                    Text(
                        text = when (selectedLanguage) {
                            "uz" -> "Kategoriyalar"
                            "ru" -> "Категории"
                            else -> "Categories"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // Categories Grid (2 Columns, 3 Rows)
                    val categories = listOf(
                        Triple("Images", if (selectedLanguage == "uz") "Tasvirlar" else if (selectedLanguage == "ru") "Изображения" else "Images", Icons.Default.Image to Color(0xFFE91E63)),
                        Triple("Videos", if (selectedLanguage == "uz") "Videolar" else if (selectedLanguage == "ru") "Видео" else "Videos", Icons.Default.PlayArrow to Color(0xFF9C27B0)),
                        Triple("Audio", "Audio", Icons.Default.MusicNote to Color(0xFF2196F3)),
                        Triple("Documents", if (selectedLanguage == "uz") "Hujjatlar" else if (selectedLanguage == "ru") "Документы" else "Documents", Icons.Default.Description to Color(0xFF4CAF50)),
                        Triple("Downloads", if (selectedLanguage == "uz") "Yuklanmalar" else if (selectedLanguage == "ru") "Загрузки" else "Downloads", Icons.Default.Download to Color(0xFFFF9800)),
                        Triple("APKs", if (selectedLanguage == "uz") "Ilovalar (APK)" else if (selectedLanguage == "ru") "Файлы APK" else "Installation files", Icons.Default.Android to Color(0xFF3F51B5))
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (i in categories.indices step 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val item1 = categories[i]
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(cardBgColor)
                                        .border(1.dp, borderColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            if (item1.first == "Downloads") {
                                                currentDirectory = File("/sdcard/Download")
                                                activeCategory = null
                                                isDashboardMode = false
                                            } else {
                                                loadCategoryFiles(item1.first)
                                            }
                                        }
                                        .padding(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(item1.third.second.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(item1.third.first, contentDescription = item1.second, tint = item1.third.second, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = item1.second,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (i + 1 < categories.size) {
                                    val item2 = categories[i + 1]
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(cardBgColor)
                                            .border(1.dp, borderColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                            .clickable {
                                                if (item2.first == "Downloads") {
                                                    currentDirectory = File("/sdcard/Download")
                                                    activeCategory = null
                                                    isDashboardMode = false
                                                } else {
                                                    loadCategoryFiles(item2.first)
                                                }
                                            }
                                            .padding(12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(item2.third.second.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(item2.third.first, contentDescription = item2.second, tint = item2.third.second, modifier = Modifier.size(20.dp))
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = item2.second,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Storages Header
                    Text(
                        text = when (selectedLanguage) {
                            "uz" -> "Xotiralar"
                            "ru" -> "Хранилища"
                            else -> "Storages"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // Storage Devices (Samsung My Files List Layout)
                    // 1. Internal Storage (/sdcard) with gorgeous progress bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardBgColor)
                            .border(1.dp, borderColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .clickable {
                                currentDirectory = File("/sdcard")
                                activeCategory = null
                                isDashboardMode = false
                            }
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = accentColor, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (selectedLanguage == "uz") "Ichki xotira" else if (selectedLanguage == "ru") "Внутреннее хранилище" else "Internal storage",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Text(
                                    text = "${if (selectedLanguage == "uz") "Ishlatilgan" else if (selectedLanguage == "ru") "Использовано" else "Used"}: ${String.format("%.1f", diskUsed)} GB / ${String.format("%.1f", diskTotal)} GB",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = secText
                                )
                            }
                            Text(
                                text = "${String.format("%.0f", (diskUsed / diskTotal) * 100f)}%",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { (diskUsed / diskTotal).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = accentColor,
                            trackColor = textColor.copy(alpha = 0.1f),
                            drawStopIndicator = {}
                        )
                    }

                    val sdCardDevices = classifiedStorages.first
                    val usbDevices = classifiedStorages.second
                    
                    sdCardDevices.forEach { sdCardFile ->
                        val sdTotalGb = sdCardFile.totalSpace / (1024f * 1024f * 1024f)
                        val sdFreeGb = sdCardFile.freeSpace / (1024f * 1024f * 1024f)
                        val sdUsedGb = sdTotalGb - sdFreeGb
                        val sdRatio = (sdUsedGb / sdTotalGb).coerceIn(0f, 1f)
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(cardBgColor)
                                .border(1.dp, borderColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .clickable {
                                    currentDirectory = sdCardFile
                                    activeCategory = null
                                    isDashboardMode = false
                                }
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SdCard, contentDescription = null, tint = Color(0xFF00B0FF), modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (selectedLanguage == "uz") "SD karta" else if (selectedLanguage == "ru") "SD-карта" else "SD card",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                    Text(
                                        text = "${if (selectedLanguage == "uz") "Ishlatilgan" else if (selectedLanguage == "ru") "Использовано" else "Used"}: ${String.format("%.1f", sdUsedGb)} GB / ${String.format("%.1f", sdTotalGb)} GB",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = secText
                                    )
                                }
                                Text(
                                    text = "${String.format("%.0f", sdRatio * 100f)}%",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00B0FF)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { sdRatio },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF00B0FF),
                                trackColor = textColor.copy(alpha = 0.1f),
                                drawStopIndicator = {}
                            )
                        }
                    }

                    usbDevices.forEach { usbFile ->
                        val usbTotalGb = usbFile.totalSpace / (1024f * 1024f * 1024f)
                        val usbFreeGb = usbFile.freeSpace / (1024f * 1024f * 1024f)
                        val usbUsedGb = usbTotalGb - usbFreeGb
                        val usbRatio = (usbUsedGb / usbTotalGb).coerceIn(0f, 1f)
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(cardBgColor)
                                .border(1.dp, borderColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .clickable {
                                    currentDirectory = usbFile
                                    activeCategory = null
                                    isDashboardMode = false
                                }
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Usb, contentDescription = null, tint = Color(0xFFFF9100), modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (selectedLanguage == "uz") "USB-ichki xotira" else if (selectedLanguage == "ru") "USB-накопитель" else "USB storage",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                    Text(
                                        text = "${if (selectedLanguage == "uz") "Ishlatilgan" else if (selectedLanguage == "ru") "Использовано" else "Used"}: ${String.format("%.1f", usbUsedGb)} GB / ${String.format("%.1f", usbTotalGb)} GB",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = secText
                                    )
                                }
                                Text(
                                    text = "${String.format("%.0f", usbRatio * 100f)}%",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF9100)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { usbRatio },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFFFF9100),
                                trackColor = textColor.copy(alpha = 0.1f),
                                drawStopIndicator = {}
                            )
                        }
                    }
                }
                }
            } else {
                // DIRECTORY BROWSER MODE (Breadcrumbs & Detailed List view with full Actions)
                val filteredItems = fileList.filter { it.name.contains(searchQuery, ignoreCase = true) }
                
                // Breadcrumbs bar (Samsung Style relative storage path components)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedLanguage == "uz") "Bosh sahifa" else if (selectedLanguage == "ru") "Домой" else "Home",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.clickable {
                                isDashboardMode = true
                                activeCategory = null
                                searchQuery = ""
                            }
                        )
                        
                        if (activeCategory != null) {
                            Text(" > ", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = secText)
                            Text(
                                text = activeCategory!!,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        } else {
                            val sdCardDevices = classifiedStorages.first
                            val usbDevices = classifiedStorages.second
                            val activeRootFile = when {
                                currentDirectory.canonicalPath.lowercase().startsWith("/storage/emulated/0") ||
                                currentDirectory.canonicalPath.lowercase().startsWith("/sdcard") -> File("/sdcard")
                                else -> {
                                    sdCardDevices.firstOrNull { currentDirectory.canonicalPath.lowercase().startsWith(it.canonicalPath.lowercase()) }
                                    ?: usbDevices.firstOrNull { currentDirectory.canonicalPath.lowercase().startsWith(it.canonicalPath.lowercase()) }
                                }
                            }

                            if (activeRootFile != null) {
                                val rootName = when {
                                    activeRootFile.absolutePath.contains("sdcard") || activeRootFile.absolutePath.contains("emulated/0") -> {
                                        if (selectedLanguage == "uz") "Ichki xotira" else if (selectedLanguage == "ru") "Внутреннее хранилище" else "Internal storage"
                                    }
                                    sdCardDevices.any { it.canonicalPath == activeRootFile.canonicalPath } -> {
                                        if (selectedLanguage == "uz") "SD karta" else if (selectedLanguage == "ru") "SD-карта" else "SD card"
                                    }
                                    else -> {
                                        if (selectedLanguage == "uz") "USB-ichki xotira" else if (selectedLanguage == "ru") "USB-накопитель" else "USB storage"
                                    }
                                }

                                Text(" > ", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = secText)
                                Text(
                                    text = rootName,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeRootFile.canonicalPath == currentDirectory.canonicalPath) textColor else accentColor,
                                    modifier = Modifier.clickable {
                                        if (activeRootFile.canonicalPath != currentDirectory.canonicalPath) {
                                            currentDirectory = activeRootFile
                                        }
                                    }
                                )

                                // Append relative subdirectories
                                val rootPath = activeRootFile.canonicalPath
                                val currentPath = currentDirectory.canonicalPath
                                if (currentPath.length > rootPath.length) {
                                    val relativeSubPath = currentPath.substring(rootPath.length).trim('/')
                                    val subParts = relativeSubPath.split("/").filter { it.isNotEmpty() }
                                    var accumulatedRelative = rootPath
                                    subParts.forEach { part ->
                                        accumulatedRelative += "/$part"
                                        val targetPath = accumulatedRelative
                                        Text(" > ", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = secText)
                                        Text(
                                            text = part,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (targetPath == currentPath) textColor else accentColor,
                                            modifier = Modifier.clickable {
                                                if (targetPath != currentPath) {
                                                    currentDirectory = File(targetPath)
                                                }
                                            }
                                        )
                                    }
                                }
                            } else {
                                // Fallback if not inside any standard root (which shouldn't happen under navigation limits)
                                val parts = currentDirectory.absolutePath.split("/").filter { it.isNotEmpty() }
                                var accumulatedPath = ""
                                parts.forEach { part ->
                                    accumulatedPath += "/$part"
                                    val targetPath = accumulatedPath
                                    Text(" > ", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = secText)
                                    Text(
                                        text = part,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (targetPath == currentDirectory.absolutePath) textColor else accentColor,
                                        modifier = Modifier.clickable {
                                            if (targetPath != currentDirectory.absolutePath) {
                                                currentDirectory = File(targetPath)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = borderColor.copy(alpha = 0.15f))

                // Actions / Multi-Select Tool Row
                if (isMultiSelectMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.12f))
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel Select
                        IconButton(
                            onClick = {
                                isMultiSelectMode = false
                                selectedItemPaths = emptySet()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = textColor, modifier = Modifier.size(18.dp))
                        }
                        
                        Text(
                            text = "${selectedItemPaths.size}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )

                        // Select All
                        IconButton(
                            onClick = { selectedItemPaths = filteredItems.map { it.path }.toSet() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = accentColor, modifier = Modifier.size(18.dp))
                        }

                        // Copy Multi
                        IconButton(
                            onClick = {
                                if (selectedItemPaths.isEmpty()) {
                                    android.widget.Toast.makeText(context, if (selectedLanguage == "uz") "Hech narsa tanlanmagan" else "Nothing selected", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    multiClipboardPaths = selectedItemPaths
                                    isMultiCutOperation = false
                                    isMultiSelectMode = false
                                    selectedItemPaths = emptySet()
                                    android.widget.Toast.makeText(context, if (selectedLanguage == "uz") "Nusxalandi! Joylash uchun kerakli jildga o'ting." else "Copied!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Selected", tint = accentColor, modifier = Modifier.size(18.dp))
                        }

                        // Move Multi (Cut)
                        IconButton(
                            onClick = {
                                if (selectedItemPaths.isEmpty()) {
                                    android.widget.Toast.makeText(context, if (selectedLanguage == "uz") "Hech narsa tanlanmagan" else "Nothing selected", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    multiClipboardPaths = selectedItemPaths
                                    isMultiCutOperation = true
                                    isMultiSelectMode = false
                                    selectedItemPaths = emptySet()
                                    android.widget.Toast.makeText(context, if (selectedLanguage == "uz") "Kesib olindi! Joylash uchun kerakli jildga o'ting." else "Cut!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ContentCut, contentDescription = "Move Selected", tint = accentColor, modifier = Modifier.size(18.dp))
                        }

                        // Share Multi
                        IconButton(
                            onClick = {
                                if (selectedItemPaths.isEmpty()) {
                                    android.widget.Toast.makeText(context, if (selectedLanguage == "uz") "Hech narsa tanlanmagan" else "Nothing selected", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    try {
                                        val uris = ArrayList<Uri>()
                                        selectedItemPaths.forEach { path ->
                                            val isSecondary = SafStorageHelper.isSecondaryStoragePath(path)
                                            if (isSecondary) {
                                                val docFile = SafStorageHelper.getDocumentFileForPath(context, path)
                                                if (docFile != null && !docFile.isDirectory) {
                                                    uris.add(docFile.uri)
                                                }
                                            } else {
                                                val f = File(path)
                                                if (!f.isDirectory) {
                                                    val u = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                                    uris.add(u)
                                                }
                                            }
                                        }
                                        if (uris.isNotEmpty()) {
                                            val intent = Intent().apply {
                                                action = if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
                                                if (uris.size == 1) {
                                                    putExtra(Intent.EXTRA_STREAM, uris[0])
                                                    type = "*/*"
                                                } else {
                                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                                    type = "*/*"
                                                }
                                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            }
                                            
                                                                (context as? com.example.MainActivity)?.isBypassingLock = true; context.startActivity(Intent.createChooser(intent, "Share Files"))
                                        } else {
                                            android.widget.Toast.makeText(context, if (selectedLanguage == "uz") "Faqat fayllarni ulashish mumkin" else "Only files can be shared", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Share error: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = accentColor, modifier = Modifier.size(18.dp))
                        }

                        // Details Multi
                        IconButton(
                            onClick = {
                                if (selectedItemPaths.isEmpty()) {
                                    android.widget.Toast.makeText(context, if (selectedLanguage == "uz") "Hech narsa tanlanmagan" else "Nothing selected", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    showMultiDetailsDialog = true
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Details", tint = accentColor, modifier = Modifier.size(18.dp))
                        }

                        // Delete Multi
                        IconButton(
                            onClick = {
                                if (selectedItemPaths.isEmpty()) {
                                    android.widget.Toast.makeText(context, if (selectedLanguage == "uz") "Hech narsa tanlanmagan" else "Nothing selected", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    showMultiDeleteConfirm = true
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color.Red, modifier = Modifier.size(18.dp))
                        }

                        // Compress Multi (Zip)
                        IconButton(
                            onClick = {
                                if (selectedItemPaths.isEmpty()) {
                                    android.widget.Toast.makeText(context, if (selectedLanguage == "uz") "Hech narsa tanlanmagan" else "Nothing selected", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    showMultiZipConfirm = true
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = "Compress Selected", tint = accentColor, modifier = Modifier.size(18.dp))
                        }
                    }
                } else if (activeCategory == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // New Folder
                        Button(
                            onClick = {
                                dialogInputText = ""
                                showNewFolderDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = cardBgColor, contentColor = textColor),
                            border = BorderStroke(0.5.dp, borderColor.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (selectedLanguage == "uz") "+Jild" else if (selectedLanguage == "ru") "+Папка" else "+Folder",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                        }

                        // New File
                        Button(
                            onClick = {
                                dialogInputText = ""
                                showNewFileDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = cardBgColor, contentColor = textColor),
                            border = BorderStroke(0.5.dp, borderColor.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (selectedLanguage == "uz") "+Fayl" else if (selectedLanguage == "ru") "+Файл" else "+File",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                        }

                        // Clipboard Paste (Single)
                        if (clipboardFile != null) {
                            Button(
                                onClick = {
                                    val src = clipboardFile
                                    if (src != null) {
                                        val dest = File(currentDirectory, src.name)
                                        isLoading = true
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                var success = false
                                                if (isCutOperation) {
                                                    success = SafStorageHelper.copy(context, src.absolutePath, dest.absolutePath)
                                                    if (success) {
                                                        SafStorageHelper.delete(context, src.absolutePath)
                                                    }
                                                } else {
                                                    success = SafStorageHelper.copy(context, src.absolutePath, dest.absolutePath)
                                                }
                                                
                                                if (!success) {
                                                    val srcPath = src.absolutePath
                                                    val destPath = dest.absolutePath
                                                    val cmd = if (isCutOperation) {
                                                        "mv \"$srcPath\" \"$destPath\""
                                                    } else {
                                                        "cp -r \"$srcPath\" \"$destPath\""
                                                    }
                                                    viewModel.executeRootOrAdbOrLocalCommand(cmd)
                                                    success = dest.exists()
                                                    if (isCutOperation && success) {
                                                        viewModel.executeRootOrAdbOrLocalCommand("rm -rf \"$srcPath\"")
                                                    }
                                                }
                                                
                                                if (success) {
                                                    try {
                                                        android.media.MediaScannerConnection.scanFile(
                                                            context,
                                                            arrayOf(src.absolutePath, dest.absolutePath),
                                                            null,
                                                            null
                                                        )
                                                    } catch (e: Exception) {}
                                                }
                                                
                                                withContext(Dispatchers.Main) {
                                                    isLoading = false
                                                    if (success) {
                                                        if (isCutOperation) clipboardFile = null
                                                        android.widget.Toast.makeText(context, 
                                                            if (selectedLanguage == "uz") "Muvaffaqiyatli joylandi!" else if (selectedLanguage == "ru") "Вставлено!" else "Pasted successfully!", 
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                        loadFiles()
                                                    } else {
                                                        android.widget.Toast.makeText(context, "Paste failed (Permission denied)", android.widget.Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    isLoading = false
                                                    android.widget.Toast.makeText(context, "Error: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = bgColor),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1.2f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = bgColor, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (selectedLanguage == "uz") "Paste (${if (isCutOperation) "Cut" else "Copy"})" else "Paste",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Clipboard Paste (Multi)
                        if (multiClipboardPaths.isNotEmpty()) {
                            Button(
                                onClick = {
                                    val srcPaths = multiClipboardPaths
                                    val isCut = isMultiCutOperation
                                    isLoading = true
                                    scope.launch(Dispatchers.IO) {
                                        var count = 0
                                        var failCount = 0
                                        srcPaths.forEach { path ->
                                            val src = File(path)
                                            val dest = File(currentDirectory, src.name)
                                            try {
                                                var success = false
                                                if (isCut) {
                                                    success = SafStorageHelper.copy(context, src.absolutePath, dest.absolutePath)
                                                    if (success) {
                                                        SafStorageHelper.delete(context, src.absolutePath)
                                                    }
                                                } else {
                                                    success = SafStorageHelper.copy(context, src.absolutePath, dest.absolutePath)
                                                }
                                                
                                                if (!success) {
                                                    val srcPath = src.absolutePath
                                                    val destPath = dest.absolutePath
                                                    val cmd = if (isCut) {
                                                        "mv \"$srcPath\" \"$destPath\""
                                                    } else {
                                                        "cp -r \"$srcPath\" \"$destPath\""
                                                    }
                                                    viewModel.executeRootOrAdbOrLocalCommand(cmd)
                                                    success = dest.exists()
                                                    if (isCut && success) {
                                                        viewModel.executeRootOrAdbOrLocalCommand("rm -rf \"$srcPath\"")
                                                    }
                                                }
                                                
                                                if (success) {
                                                    count++
                                                    try {
                                                        android.media.MediaScannerConnection.scanFile(
                                                            context,
                                                            arrayOf(src.absolutePath, dest.absolutePath),
                                                            null,
                                                            null
                                                        )
                                                    } catch (e: Exception) {}
                                                } else {
                                                    failCount++
                                                }
                                            } catch (e: Exception) {
                                                failCount++
                                            }
                                        }
                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            if (isCut) multiClipboardPaths = emptySet()
                                            android.widget.Toast.makeText(context, 
                                                if (selectedLanguage == "uz") "Joylandi: $count, Xatolik: $failCount" else "Pasted: $count, Errors: $failCount", 
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                            loadFiles()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = bgColor),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1.5f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = bgColor, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (selectedLanguage == "uz") "Joylash" else "Paste Multi",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Parent folder shortcut row
                val sdCardPaths = classifiedStorages.first.map { it.canonicalPath.lowercase() }
                val usbPaths = classifiedStorages.second.map { it.canonicalPath.lowercase() }
                val currentPath = currentDirectory.canonicalPath.lowercase()
                val isAtRoot = currentPath == "/storage/emulated/0" || 
                               currentPath == "/sdcard" || 
                               sdCardPaths.contains(currentPath) || 
                               usbPaths.contains(currentPath)

                if (activeCategory == null && currentDirectory.parentFile != null && !isAtRoot) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { currentDirectory = currentDirectory.parentFile!! }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = ".. [Parent Directory]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                    HorizontalDivider(color = borderColor.copy(alpha = 0.08f))
                }

                val isSecondaryStorage = SafStorageHelper.isSecondaryStoragePath(currentDirectory.absolutePath)
                val isAuthorized = SafStorageHelper.isVolumeAuthorized(context, currentDirectory.absolutePath)

                if (isSecondaryStorage && !isAuthorized) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = accentColor.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Usb,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = when (selectedLanguage) {
                                    "uz" -> "Tashqi xotira (SD-karta / USB) ruxsati kerak"
                                    "ru" -> "Требуется доступ к внешнему накопителю"
                                    else -> "External Storage Access Required"
                                },
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = textColor,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    try {
                                        val activity = context.findActivity()
                                        if (activity != null) {
                                            (activity as? com.example.MainActivity)?.isSelectingFileForAi = true
                                            var intent: android.content.Intent? = null
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? android.os.storage.StorageManager
                                                val volume = storageManager?.getStorageVolume(currentDirectory)
                                                intent = volume?.createOpenDocumentTreeIntent()
                                            }
                                            if (intent == null) {
                                                intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE)
                                            }
                                            activity.startActivityForResult(intent, 1003)
                                        } else {
                                            safLauncher.launch(null)
                                        }
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Error: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = bgColor),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = when (selectedLanguage) {
                                        "uz" -> "Ruxsat berish"
                                        "ru" -> "Предоставить доступ"
                                        else -> "Grant Access"
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Main Browser Item List
                if (filteredItems.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (selectedLanguage == "uz") "Bo'sh jild yoki mos fayl topilmadi" else if (selectedLanguage == "ru") "Пустая папка или файлы не найдены" else "Empty folder or no files found",
                            fontFamily = FontFamily.Monospace,
                            color = secText,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalFadingEdge()
                    ) {
                        items(filteredItems, key = { it.path }) { item ->
                            // Custom item design matching Samsung files category icon accents
                            val itemExt = item.name.substringAfterLast('.', "").lowercase()
                            val itemMime = getFileMimeType(item.path)
                            val (itemIcon, iconTint) = when {
                                item.isDirectory -> Icons.Default.Folder to Color(0xFFFFB300) // Folder Orange
                                itemMime.startsWith("image/") -> Icons.Default.Image to Color(0xFFE91E63) // Images Pink
                                itemMime.startsWith("video/") -> Icons.Default.PlayArrow to Color(0xFF9C27B0) // Videos Purple
                                itemMime.startsWith("audio/") -> Icons.Default.MusicNote to Color(0xFF2196F3) // Audio Blue
                                itemMime == "application/pdf" || itemMime.startsWith("text/") -> Icons.Default.Description to Color(0xFF4CAF50) // Docs Green
                                itemExt == "apk" -> Icons.Default.Android to Color(0xFF3DDC84) // APK Android Green
                                else -> Icons.Default.InsertDriveFile to Color(0xFF9E9E9E) // Generic Gray
                            }
                            
                            val isSelected = selectedItemPaths.contains(item.path)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(item.path) {
                                        detectTapGestures(
                                            onLongPress = {
                                                if (!isMultiSelectMode) {
                                                    isMultiSelectMode = true
                                                    selectedItemPaths = setOf(item.path)
                                                }
                                            },
                                            onTap = {
                                                if (isMultiSelectMode) {
                                                    selectedItemPaths = if (selectedItemPaths.contains(item.path)) {
                                                        selectedItemPaths - item.path
                                                    } else {
                                                        selectedItemPaths + item.path
                                                    }
                                                } else {
                                                    if (item.isDirectory) {
                                                        currentDirectory = File(item.path)
                                                    } else {
                                                        val ext = itemExt
                                                        val mime = itemMime
                                                        when {
                                                            mime.startsWith("image/") -> {
                                                                imagePathToPreview = item.path
                                                                showImagePreviewDialog = true
                                                            }
                                                            mime.startsWith("audio/") -> {
                                                                val plist = getPlayablePlaylist(item.path, false)
                                                                audioPlaylist = plist
                                                                audioPlaylistIndex = plist.indexOf(item.path)
                                                                playAudioFile(item.path)
                                                            }
                                                            mime.startsWith("video/") -> {
                                                                videoPathToPlay = item.path
                                                                val plist = getPlayablePlaylist(item.path, true)
                                                                videoPlaylist = plist
                                                                videoPlaylistIndex = plist.indexOf(item.path)
                                                                showVideoPlayerDialog = true
                                                            }
                                                            ext == "pdf" -> {
                                                                pdfPathToView = item.path
                                                                showPdfViewerDialog = true
                                                            }
                                                            ext == "zip" -> {
                                                                zipPathToView = item.path
                                                                showZipViewerDialog = true
                                                            }
                                                            mime.startsWith("text/") || listOf("json", "xml", "prop", "conf", "log", "sh").contains(ext) -> {
                                                                viewFileContent(item)
                                                            }
                                                            else -> {
                                                                try {
                                                                    val file = File(item.path)
                                                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                                        setDataAndType(uri, mime)
                                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                                    }
                                                                    
                                                                (context as? com.example.MainActivity)?.isBypassingLock = true; context.startActivity(intent)
                                                                } catch (e: Exception) {
                                                                    viewFileContent(item)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    .background(if (isSelected) accentColor.copy(alpha = 0.15f) else Color.Transparent)
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isMultiSelectMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedItemPaths = if (checked == true) {
                                                selectedItemPaths + item.path
                                            } else {
                                                selectedItemPaths - item.path
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = accentColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Icon(
                                    imageVector = itemIcon,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = textColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${item.permissions} | ${if (item.isDirectory) "Dir" else "${String.format("%.1f", item.size / 1024f)} KB"} | ${item.lastModified}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = secText
                                    )
                                }
                                
                                // Action Menu Trigger Button (Three Dots)
                                var showLocalMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(
                                        onClick = { showLocalMenu = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = secText, modifier = Modifier.size(16.dp))
                                    }
                                    
                                    DropdownMenu(
                                        expanded = showLocalMenu,
                                        onDismissRequest = { showLocalMenu = false },
                                        modifier = Modifier.background(cardBgColor).border(0.5.dp, borderColor.copy(alpha = 0.2f))
                                    ) {
                                        val itemMime = getFileMimeType(item.path)

                                        // Edit / View Text
                                        if (!item.isDirectory && (itemMime.startsWith("text/") || listOf("json", "xml", "prop", "conf", "log", "sh").contains(itemExt))) {
                                            DropdownMenuItem(
                                                text = { Text(if (selectedLanguage == "uz") "Ko'rish / Tahrirlash" else if (selectedLanguage == "ru") "Посмотреть / Изменить" else "View / Edit", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = textColor) },
                                                onClick = {
                                                    showLocalMenu = false
                                                    viewFileContent(item)
                                                },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp)) }
                                            )
                                        }

                                        // Image Preview
                                        if (itemMime.startsWith("image/")) {
                                            DropdownMenuItem(
                                                text = { Text(if (selectedLanguage == "uz") "Rasm ko'rish" else if (selectedLanguage == "ru") "Просмотр фото" else "Preview Image", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = textColor) },
                                                onClick = {
                                                    showLocalMenu = false
                                                    imagePathToPreview = item.path
                                                    showImagePreviewDialog = true
                                                },
                                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp)) }
                                            )
                                        }

                                        // Audio Play
                                        if (itemMime.startsWith("audio/")) {
                                            DropdownMenuItem(
                                                text = { Text(if (selectedLanguage == "uz") "Audioni tinglash" else "Play Audio", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = textColor) },
                                                onClick = {
                                                    showLocalMenu = false
                                                    val plist = getPlayablePlaylist(item.path, false)
                                                    audioPlaylist = plist
                                                    audioPlaylistIndex = plist.indexOf(item.path)
                                                    playAudioFile(item.path)
                                                },
                                                leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp)) }
                                            )
                                        }

                                        // Video Play
                                        if (itemMime.startsWith("video/")) {
                                            DropdownMenuItem(
                                                text = { Text(if (selectedLanguage == "uz") "Videoni ko'rish" else "Play Video", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = textColor) },
                                                onClick = {
                                                    showLocalMenu = false
                                                    videoPathToPlay = item.path
                                                    val plist = getPlayablePlaylist(item.path, true)
                                                    videoPlaylist = plist
                                                    videoPlaylistIndex = plist.indexOf(item.path)
                                                    showVideoPlayerDialog = true
                                                },
                                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp)) }
                                            )
                                        }

                                        // Run Shell Script
                                        if (itemExt == "sh") {
                                            DropdownMenuItem(
                                                text = { Text(if (selectedLanguage == "uz") "Terminalda ishga tushirish" else "Run Script in Terminal", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = textColor) },
                                                onClick = {
                                                    showLocalMenu = false
                                                    // Quick command loading
                                                    viewModel.onCommandChange("sh \"${item.path}\"")
                                                    onBack() // return to terminal
                                                },
                                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF3DDC84), modifier = Modifier.size(14.dp)) }
                                            )
                                        }

                                        // Copy
                                        DropdownMenuItem(
                                            text = { Text(if (selectedLanguage == "uz") "Nusxalash" else if (selectedLanguage == "ru") "Копировать" else "Copy", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = textColor) },
                                            onClick = {
                                                showLocalMenu = false
                                                clipboardFile = File(item.path)
                                                isCutOperation = false
                                                android.widget.Toast.makeText(context, if (selectedLanguage == "uz") "Clipboardga nusxalandi!" else "Copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp)) }
                                        )

                                        // Cut / Move
                                        DropdownMenuItem(
                                            text = { Text(if (selectedLanguage == "uz") "Ko'chirish" else if (selectedLanguage == "ru") "Вырезать" else "Cut / Move", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = textColor) },
                                            onClick = {
                                                showLocalMenu = false
                                                clipboardFile = File(item.path)
                                                isCutOperation = true
                                                android.widget.Toast.makeText(context, if (selectedLanguage == "uz") "Ko'chirishga tayyor!" else "Ready to move!", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp)) }
                                        )

                                        // Rename
                                        DropdownMenuItem(
                                            text = { Text(if (selectedLanguage == "uz") "Nomini o'zgartirish" else if (selectedLanguage == "ru") "Переименовать" else "Rename", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = textColor) },
                                            onClick = {
                                                showLocalMenu = false
                                                selectedItemForAction = item
                                                dialogInputText = item.name
                                                showRenameDialog = true
                                            },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp)) }
                                        )

                                        // Delete
                                        DropdownMenuItem(
                                            text = { Text(if (selectedLanguage == "uz") "O'chirish" else if (selectedLanguage == "ru") "Удалить" else "Delete", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Red) },
                                            onClick = {
                                                showLocalMenu = false
                                                selectedItemForAction = item
                                                showDeleteConfirmDialog = true
                                            },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(14.dp)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- FILE DIALOGS SECTION ---
        
        // 1. Rename Dialog
        if (showRenameDialog && selectedItemForAction != null) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text(if (selectedLanguage == "uz") "Nomini o'zgartirish" else "Rename", fontFamily = FontFamily.Monospace, fontSize = 14.sp) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = dialogInputText,
                            onValueChange = { dialogInputText = it },
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, focusedBorderColor = accentColor)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val originalFile = File(selectedItemForAction!!.path)
                        val newFile = File(originalFile.parentFile, dialogInputText)
                        if (dialogInputText.isNotEmpty()) {
                            isLoading = true
                            scope.launch(Dispatchers.IO) {
                                var done = SafStorageHelper.rename(context, originalFile.absolutePath, dialogInputText)
                                if (!done) {
                                    viewModel.executeRootOrAdbOrLocalCommand("mv \"${originalFile.absolutePath}\" \"${newFile.absolutePath}\"")
                                    done = newFile.exists() && !originalFile.exists()
                                }
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    showRenameDialog = false
                                    if (done) {
                                        if (activeCategory != null) {
                                            loadCategoryFiles(activeCategory!!)
                                        } else {
                                            loadFiles()
                                        }
                                    } else {
                                        android.widget.Toast.makeText(context, "Rename failed (Permission Denied)", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }) {
                        Text("Rename", fontFamily = FontFamily.Monospace, color = accentColor)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel", fontFamily = FontFamily.Monospace, color = textColor)
                    }
                },
                containerColor = cardBgColor
            )
        }

        // 2. Delete Confirmation Dialog
        if (showDeleteConfirmDialog && selectedItemForAction != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text(if (selectedLanguage == "uz") "O'chirishni tasdiqlang" else "Confirm Delete", fontFamily = FontFamily.Monospace, fontSize = 14.sp) },
                text = {
                    Text(
                        text = if (selectedLanguage == "uz") "Haqiqatan ham '${selectedItemForAction!!.name}' faylini butunlay o'chirib tashlamoqchimisiz?" else "Are you sure you want to permanently delete '${selectedItemForAction!!.name}'?",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = textColor
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val file = File(selectedItemForAction!!.path)
                        isLoading = true
                        scope.launch(Dispatchers.IO) {
                            var done = SafStorageHelper.delete(context, file.absolutePath)
                            if (!done) {
                                viewModel.executeRootOrAdbOrLocalCommand("rm -rf \"${file.absolutePath}\"")
                                done = !file.exists()
                            }
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                showDeleteConfirmDialog = false
                                if (done) {
                                    if (activeCategory != null) {
                                        loadCategoryFiles(activeCategory!!)
                                    } else {
                                        loadFiles()
                                    }
                                    android.widget.Toast.makeText(context, if (selectedLanguage == "uz") "Muvaffaqiyatli o'chirildi!" else "Deleted successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context, "Delete failed (Permission Denied)", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }) {
                        Text("Delete", fontFamily = FontFamily.Monospace, color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Cancel", fontFamily = FontFamily.Monospace, color = textColor)
                    }
                },
                containerColor = cardBgColor
            )
        }

        // 3. New Folder Dialog
        if (showNewFolderDialog) {
            AlertDialog(
                onDismissRequest = { showNewFolderDialog = false },
                title = { Text(if (selectedLanguage == "uz") "Yangi jild yaratish" else "New Folder", fontFamily = FontFamily.Monospace, fontSize = 14.sp) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = dialogInputText,
                            onValueChange = { dialogInputText = it },
                            placeholder = { Text("Jild nomi", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, focusedBorderColor = accentColor)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (dialogInputText.isNotEmpty()) {
                            val newDir = File(currentDirectory, dialogInputText)
                            isLoading = true
                            scope.launch(Dispatchers.IO) {
                                var done = SafStorageHelper.mkdir(context, newDir.absolutePath)
                                if (!done) {
                                    viewModel.executeRootOrAdbOrLocalCommand("mkdir -p \"${newDir.absolutePath}\"")
                                    done = newDir.exists()
                                }
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    showNewFolderDialog = false
                                    if (done) {
                                        loadFiles()
                                    } else {
                                        android.widget.Toast.makeText(context, "Folder creation failed", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }) {
                        Text("Create", fontFamily = FontFamily.Monospace, color = accentColor)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewFolderDialog = false }) {
                        Text("Cancel", fontFamily = FontFamily.Monospace, color = textColor)
                    }
                },
                containerColor = cardBgColor
            )
        }

        // 4. New File Dialog
        if (showNewFileDialog) {
            AlertDialog(
                onDismissRequest = { showNewFileDialog = false },
                title = { Text(if (selectedLanguage == "uz") "Yangi fayl yaratish" else "New File", fontFamily = FontFamily.Monospace, fontSize = 14.sp) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = dialogInputText,
                            onValueChange = { dialogInputText = it },
                            placeholder = { Text("Fayl nomi (masalan text.txt)", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, focusedBorderColor = accentColor)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (dialogInputText.isNotEmpty()) {
                            val newFile = File(currentDirectory, dialogInputText)
                            isLoading = true
                            scope.launch(Dispatchers.IO) {
                                var done = false
                                try {
                                    done = SafStorageHelper.createNewFile(context, newFile.absolutePath)
                                } catch (e: Exception) {}
                                if (!done) {
                                    viewModel.executeRootOrAdbOrLocalCommand("touch \"${newFile.absolutePath}\"")
                                    done = newFile.exists()
                                }
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    showNewFileDialog = false
                                    if (done) {
                                        loadFiles()
                                    } else {
                                        android.widget.Toast.makeText(context, "File creation failed", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }) {
                        Text("Create", fontFamily = FontFamily.Monospace, color = accentColor)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewFileDialog = false }) {
                        Text("Cancel", fontFamily = FontFamily.Monospace, color = textColor)
                    }
                },
                containerColor = cardBgColor
            )
        }

        // 5. Native Image Preview Dialog
        if (showImagePreviewDialog && imagePathToPreview.isNotEmpty()) {
            Dialog(onDismissRequest = { showImagePreviewDialog = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = imagePathToPreview.substringAfterLast('/'),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showImagePreviewDialog = false }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }

                        // Local Bitmap rendering safely with premium zoom & pan controls
                        val localBitmap = remember(imagePathToPreview) { loadLocalBitmap(imagePathToPreview) }
                        if (localBitmap != null) {
                            var scale by remember { mutableFloatStateOf(1f) }
                            var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                            
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(350.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                val boxWidth = constraints.maxWidth.toFloat()
                                val boxHeight = constraints.maxHeight.toFloat()
                                
                                val imgWidth = localBitmap.width.toFloat()
                                val imgHeight = localBitmap.height.toFloat()
                                val imgRatio = if (imgHeight > 0) imgWidth / imgHeight else 1f
                                val boxRatio = if (boxHeight > 0) boxWidth / boxHeight else 1f
                                
                                val visibleWidth = if (imgRatio > boxRatio) boxWidth else boxHeight * imgRatio
                                val visibleHeight = if (imgRatio > boxRatio) boxWidth / imgRatio else boxHeight

                                Image(
                                    bitmap = localBitmap.asImageBitmap(),
                                    contentDescription = "Image Preview",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onDoubleTap = {
                                                    scale = 1f
                                                    offset = androidx.compose.ui.geometry.Offset.Zero
                                                }
                                            )
                                        }
                                        .pointerInput(Unit) {
                                            detectTransformGestures { centroid, pan, zoom, _ ->
                                                val prevScale = scale
                                                scale = (scale * zoom).coerceIn(1f, 10f)
                                                
                                                val center = androidx.compose.ui.geometry.Offset(boxWidth / 2f, boxHeight / 2f)
                                                val d = centroid - center
                                                val offsetDelta = d - d * (scale / prevScale)
                                                
                                                val newOffsetX = offset.x + pan.x + offsetDelta.x
                                                val newOffsetY = offset.y + pan.y + offsetDelta.y
                                                
                                                val maxX = maxOf(0f, (visibleWidth * scale - boxWidth) / 2f)
                                                val maxY = maxOf(0f, (visibleHeight * scale - boxHeight) / 2f)
                                                
                                                offset = androidx.compose.ui.geometry.Offset(
                                                    newOffsetX.coerceIn(-maxX, maxX),
                                                    newOffsetY.coerceIn(-maxY, maxY)
                                                )
                                            }
                                        }
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            translationX = offset.x
                                            translationY = offset.y
                                        },
                                    contentScale = ContentScale.Fit
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (selectedLanguage == "uz") "Rasm formati murakkab yoki o'qib bo'lmadi" else "Complex or unsupported image format",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color.Red
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = {
                                        try {
                                            val file = File(imagePathToPreview)
                                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, getFileMimeType(imagePathToPreview))
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            }
                                            
                                                                (context as? com.example.MainActivity)?.isBypassingLock = true; context.startActivity(intent)
                                            showImagePreviewDialog = false
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "No suitable app found", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Text(if (selectedLanguage == "uz") "Boshqa dasturda ochish (Tashqi)" else "Open Externally", fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Professional Full Screen Text Viewer + Native Text Editor
        if (showViewer && selectedFileContent != null) {
            Dialog(onDismissRequest = { showViewer = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                ) {
                    var localEditTextState by remember { mutableStateOf(selectedFileContent ?: "") }
                    
                    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedFileName,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            
                            // Editor Mode Toggle
                            TextButton(onClick = { isEditingFile = !isEditingFile }) {
                                Text(
                                    text = if (isEditingFile) {
                                        if (selectedLanguage == "uz") "Ko'rish" else "View"
                                    } else {
                                        if (selectedLanguage == "uz") "Tahrirlash" else "Edit"
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Save Button (Active when in editor mode)
                            if (isEditingFile) {
                                TextButton(onClick = {
                                    val currentPath = if (activeCategory != null) {
                                        // Category path finding
                                        fileList.firstOrNull { it.name == selectedFileName }?.path ?: ""
                                    } else {
                                        "${currentDirectory.absolutePath}/$selectedFileName"
                                    }
                                    if (currentPath.isNotEmpty()) {
                                        saveFileText(currentPath, localEditTextState)
                                    }
                                }) {
                                    Text(
                                        text = if (selectedLanguage == "uz") "Saqlash" else "Save",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFF3DDC84),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            IconButton(onClick = { showViewer = false }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color.Black)
                                .padding(8.dp)
                        ) {
                            if (isEditingFile) {
                                BasicTextField(
                                    value = localEditTextState,
                                    onValueChange = { newValue -> localEditTextState = newValue },
                                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFF33FF33)
                                    ),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White)
                                )
                            } else {
                                Text(
                                    text = localEditTextState,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFF33FF33),
                                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                }
            }
        }

        // 7. PDF Viewer Dialog
        if (showPdfViewerDialog && pdfPathToView.isNotEmpty()) {
            Dialog(onDismissRequest = { showPdfViewerDialog = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .heightIn(max = 620.dp)
                        .wrapContentHeight()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    var pdfPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
                    var pdfError by remember { mutableStateOf<String?>(null) }
                    var scale by remember { mutableStateOf(1f) }
                    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
                        scale = (scale * zoomChange).coerceIn(1f, 5f)
                        offset += offsetChange
                    }
                    
                    LaunchedEffect(pdfPathToView) {
                        try {
                            val isSecondary = SafStorageHelper.isSecondaryStoragePath(pdfPathToView)
                            val pfd = if (isSecondary) {
                                val doc = SafStorageHelper.getDocumentFileForPath(context, pdfPathToView)
                                if (doc != null) {
                                    context.contentResolver.openFileDescriptor(doc.uri, "r")
                                } else {
                                    ParcelFileDescriptor.open(File(pdfPathToView), ParcelFileDescriptor.MODE_READ_ONLY)
                                }
                            } else {
                                ParcelFileDescriptor.open(File(pdfPathToView), ParcelFileDescriptor.MODE_READ_ONLY)
                            }
                            
                            if (pfd != null) {
                                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                                val bitmaps = mutableListOf<Bitmap>()
                                val pageCount = renderer.pageCount.coerceAtMost(10) // Limit to first 10 pages for speed/memory efficiency
                                for (i in 0 until pageCount) {
                                    val page = renderer.openPage(i)
                                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                    bitmap.eraseColor(android.graphics.Color.WHITE)
                                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bitmaps.add(bitmap)
                                    page.close()
                                }
                                renderer.close()
                                pfd.close()
                                pdfPages = bitmaps
                            } else {
                                pdfError = "Failed to open file descriptor"
                            }
                        } catch (e: Exception) {
                            pdfError = e.localizedMessage
                        }
                    }
                    
                    Column(modifier = Modifier.wrapContentHeight().fillMaxWidth().padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = pdfPathToView.substringAfterLast('/'),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showPdfViewerDialog = false }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }
                        
                        if (pdfError != null) {
                            Box(modifier = Modifier.height(300.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("Error loading PDF: $pdfError", color = Color.Red, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        } else if (pdfPages.isEmpty()) {
                            Box(modifier = Modifier.height(300.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = accentColor)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .heightIn(max = 500.dp)
                                    .fillMaxWidth()
                                    .verticalFadingEdge()
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    )
                                    .transformable(state = transformState),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(pdfPages) { pageBitmap ->
                                    Image(
                                        bitmap = pageBitmap.asImageBitmap(),
                                        contentDescription = "PDF Page",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .wrapContentHeight()
                                            .background(Color.White)
                                            .clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.FillWidth
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 8. Audio Player Dialog (Premium offline media player with speed, looping, playlists, minimize)
        if (showAudioPlayerDialog && currentPlayingAudioPath.isNotEmpty()) {
            Dialog(onDismissRequest = {
                showAudioPlayerDialog = false
                isAudioMinimized = true
            }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        // Title Bar
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentPlayingAudioPath.substringAfterLast('/'),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Row {
                                // Minimize Button
                                IconButton(
                                    onClick = {
                                        showAudioPlayerDialog = false
                                        isAudioMinimized = true
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Minimize, contentDescription = "Minimize", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                // Close Button
                                IconButton(
                                    onClick = {
                                        showAudioPlayerDialog = false
                                        isAudioPlaying = false
                                        try {
                                            sharedMediaPlayer.stop()
                                        } catch (e: Exception) {}
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // Audio Art Icon
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(60.dp))
                                .background(accentColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Music Art",
                                tint = accentColor,
                                modifier = Modifier.size(56.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Progress Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentMin = (audioPlaybackPosition / 1000) / 60
                            val currentSec = (audioPlaybackPosition / 1000) % 60
                            val durationMin = (audioDuration / 1000) / 60
                            val durationSec = (audioDuration / 1000) % 60

                            Text(String.format("%02d:%02d", currentMin, currentSec), color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)

                            Slider(
                                value = audioPlaybackPosition.toFloat(),
                                onValueChange = {
                                    try {
                                        sharedMediaPlayer.seekTo(it.toInt())
                                        audioPlaybackPosition = it.toInt()
                                    } catch (e: Exception) {}
                                },
                                valueRange = 0f..audioDuration.toFloat().coerceAtLeast(1f),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                            )

                            Text(String.format("%02d:%02d", durationMin, durationSec), color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }

                        // Playback speed Selector Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedLanguage == "uz") "Tezlik:" else if (selectedLanguage == "ru") "Скорость:" else "Speed:",
                                color = secText,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { speed ->
                                Text(
                                    text = "${speed}x",
                                    color = if (audioPlaybackSpeed == speed) accentColor else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (audioPlaybackSpeed == speed) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (audioPlaybackSpeed == speed) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                                        .clickable {
                                            audioPlaybackSpeed = speed
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                                try {
                                                    val params = sharedMediaPlayer.playbackParams
                                                    params.speed = speed
                                                    sharedMediaPlayer.playbackParams = params
                                                } catch (e: Exception) {}
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Control Buttons Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Repeat / Loop button
                            IconButton(
                                onClick = {
                                    audioLoopingEnabled = !audioLoopingEnabled
                                    try {
                                        sharedMediaPlayer.isLooping = audioLoopingEnabled
                                    } catch (e: Exception) {}
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = "Repeat",
                                    tint = if (audioLoopingEnabled) accentColor else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Skip Previous button
                            IconButton(
                                onClick = {
                                    if (audioPlaylist.isNotEmpty() && audioPlaylistIndex >= 0) {
                                        val prevIndex = if (audioPlaylistIndex - 1 < 0) audioPlaylist.size - 1 else audioPlaylistIndex - 1
                                        audioPlaylistIndex = prevIndex
                                        playAudioFile(audioPlaylist[prevIndex])
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Play / Pause button
                            IconButton(
                                onClick = {
                                    try {
                                        if (sharedMediaPlayer.isPlaying) {
                                            sharedMediaPlayer.pause()
                                            isAudioPlaying = false
                                        } else {
                                            sharedMediaPlayer.start()
                                            isAudioPlaying = true
                                        }
                                    } catch (e: Exception) {}
                                },
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(27.dp))
                                    .background(accentColor)
                            ) {
                                Icon(
                                    imageVector = if (isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.Black,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Skip Next button
                            IconButton(
                                onClick = {
                                    if (audioPlaylist.isNotEmpty() && audioPlaylistIndex >= 0) {
                                        val nextIndex = (audioPlaylistIndex + 1) % audioPlaylist.size
                                        audioPlaylistIndex = nextIndex
                                        playAudioFile(audioPlaylist[nextIndex])
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Playlist Indicator/Counter
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (audioPlaylist.isNotEmpty()) "${audioPlaylistIndex + 1}/${audioPlaylist.size}" else "1/1",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // 9. Video Player Dialog (Premium video player with speed, looping, and playlists)
        if (showVideoPlayerDialog && videoPathToPlay.isNotEmpty()) {
            Dialog(
                onDismissRequest = { 
                    showVideoPlayerDialog = false
                },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                var activeVideoMediaPlayer: android.media.MediaPlayer? by remember { mutableStateOf(null) }
                var videoViewInstance: android.widget.VideoView? by remember { mutableStateOf(null) }
                var isVideoPlayingState by remember { mutableStateOf(true) }
                var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }

                LaunchedEffect(videoViewInstance) {
                    while (showVideoPlayerDialog && videoViewInstance != null) {
                        videoViewInstance?.let { view ->
                            isVideoPlayingState = view.isPlaying
                        }
                        delay(500)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .wrapContentHeight()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().background(Color.Black)) {
                        // Title Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = videoPathToPlay.substringAfterLast('/'),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { 
                                    val currentPos = videoViewInstance?.currentPosition ?: 0
                                    showVideoPlayerDialog = false
                                    // Seamlessly continue video audio track in the background!
                                    audioPathToPlay = videoPathToPlay
                                    isAudioPlaying = true
                                    showAudioPlayerDialog = false
                                    isAudioMinimized = true
                                    try {
                                        sharedMediaPlayer.reset()
                                        val isSecondary = SafStorageHelper.isSecondaryStoragePath(videoPathToPlay)
                                        if (isSecondary) {
                                            val doc = SafStorageHelper.getDocumentFileForPath(context, videoPathToPlay)
                                            if (doc != null) {
                                                sharedMediaPlayer.setDataSource(context, doc.uri)
                                            } else {
                                                sharedMediaPlayer.setDataSource(videoPathToPlay)
                                            }
                                        } else {
                                            sharedMediaPlayer.setDataSource(videoPathToPlay)
                                        }
                                        sharedMediaPlayer.prepare()
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                            val params = sharedMediaPlayer.playbackParams
                                            params.speed = videoPlaybackSpeed
                                            sharedMediaPlayer.playbackParams = params
                                        }
                                        sharedMediaPlayer.seekTo(currentPos)
                                        sharedMediaPlayer.start()
                                    } catch (e: Exception) {}
                                }, 
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }

                        // Video Rendering Canvas Area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(videoAspectRatio)
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    android.widget.VideoView(ctx).apply {
                                        val isSecondary = SafStorageHelper.isSecondaryStoragePath(videoPathToPlay)
                                        if (isSecondary) {
                                            val doc = SafStorageHelper.getDocumentFileForPath(ctx, videoPathToPlay)
                                            if (doc != null) {
                                                setVideoURI(doc.uri)
                                            } else {
                                                setVideoPath(videoPathToPlay)
                                            }
                                        } else {
                                            setVideoPath(videoPathToPlay)
                                        }
                                        
                                        // Set Media Controller
                                        val mediaController = android.widget.MediaController(ctx)
                                        mediaController.setAnchorView(this)
                                        setMediaController(mediaController)
                                        
                                        setOnPreparedListener { mp ->
                                            activeVideoMediaPlayer = mp
                                            mp.isLooping = videoLoopingEnabled
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                                try {
                                                    val params = mp.playbackParams
                                                    params.speed = videoPlaybackSpeed
                                                    mp.playbackParams = params
                                                } catch (e: Exception) {}
                                            }
                                            mp.setOnVideoSizeChangedListener { _, width, height ->
                                                if (width > 0 && height > 0) {
                                                    videoAspectRatio = width.toFloat() / height.toFloat()
                                                }
                                            }
                                        }

                                        setOnCompletionListener {
                                            if (!videoLoopingEnabled) {
                                                if (videoPlaylist.isNotEmpty() && videoPlaylistIndex >= 0) {
                                                    val nextIndex = (videoPlaylistIndex + 1) % videoPlaylist.size
                                                    videoPlaylistIndex = nextIndex
                                                    videoPathToPlay = videoPlaylist[nextIndex]
                                                } else {
                                                    isVideoPlayingState = false
                                                }
                                            }
                                        }

                                        videoViewInstance = this
                                        start()
                                    }
                                },
                                update = { view ->
                                    // Handle path update if it changes
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Playback Speed Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black)
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedLanguage == "uz") "Tezlik:" else if (selectedLanguage == "ru") "Скорость:" else "Speed:",
                                color = secText,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { speed ->
                                Text(
                                    text = "${speed}x",
                                    color = if (videoPlaybackSpeed == speed) accentColor else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (videoPlaybackSpeed == speed) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (videoPlaybackSpeed == speed) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                                        .clickable {
                                            videoPlaybackSpeed = speed
                                            activeVideoMediaPlayer?.let { mp ->
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                                    try {
                                                        val params = mp.playbackParams
                                                        params.speed = speed
                                                        mp.playbackParams = params
                                                    } catch (e: Exception) {}
                                                }
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Video controls (Repeat, Prev, Play/Pause, Next)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black)
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Repeat Button
                            IconButton(
                                onClick = {
                                    videoLoopingEnabled = !videoLoopingEnabled
                                    activeVideoMediaPlayer?.isLooping = videoLoopingEnabled
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = "Loop Video",
                                    tint = if (videoLoopingEnabled) accentColor else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Prev Track Button
                            IconButton(
                                onClick = {
                                    if (videoPlaylist.isNotEmpty() && videoPlaylistIndex >= 0) {
                                        val prevIndex = if (videoPlaylistIndex - 1 < 0) videoPlaylist.size - 1 else videoPlaylistIndex - 1
                                        videoPlaylistIndex = prevIndex
                                        videoPathToPlay = videoPlaylist[prevIndex]
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Prev Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Play/Pause Button
                            IconButton(
                                onClick = {
                                    videoViewInstance?.let { view ->
                                        if (view.isPlaying) {
                                            view.pause()
                                            isVideoPlayingState = false
                                        } else {
                                            view.start()
                                            isVideoPlayingState = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(accentColor)
                            ) {
                                Icon(
                                    imageVector = if (isVideoPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause Video",
                                    tint = Color.Black,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Next Track Button
                            IconButton(
                                onClick = {
                                    if (videoPlaylist.isNotEmpty() && videoPlaylistIndex >= 0) {
                                        val nextIndex = (videoPlaylistIndex + 1) % videoPlaylist.size
                                        videoPlaylistIndex = nextIndex
                                        videoPathToPlay = videoPlaylist[nextIndex]
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Count Indicator
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (videoPlaylist.isNotEmpty()) "${videoPlaylistIndex + 1}/${videoPlaylist.size}" else "1/1",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // 10. Zip Viewer / Extractor Dialog
        if (showZipViewerDialog && zipPathToView.isNotEmpty()) {
            Dialog(onDismissRequest = { showZipViewerDialog = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    var zipEntries by remember { mutableStateOf<List<String>>(emptyList()) }
                    var zipError by remember { mutableStateOf<String?>(null) }
                    
                    LaunchedEffect(zipPathToView) {
                        try {
                            val entries = mutableListOf<String>()
                            val inputStream = SafStorageHelper.getInputStream(context, zipPathToView) ?: java.io.FileInputStream(zipPathToView)
                            val zipInputStream = java.util.zip.ZipInputStream(inputStream)
                            var entry = zipInputStream.nextEntry
                            var count = 0
                            while (entry != null && count < 50) {
                                entries.add(entry.name)
                                zipInputStream.closeEntry()
                                entry = zipInputStream.nextEntry
                                count++
                            }
                            zipInputStream.close()
                            zipEntries = entries
                        } catch (e: Exception) {
                            zipError = e.localizedMessage
                        }
                    }
                    
                    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = zipPathToView.substringAfterLast('/'),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showZipViewerDialog = false }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }
                        
                        if (zipError != null) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("Error loading zip: $zipError", color = Color.Red, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        } else if (zipEntries.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = accentColor)
                            }
                        } else {
                            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                Text(
                                    text = if (selectedLanguage == "uz") "Fayllar tarkibi (maksimal 50 ta):" else "Files inside (max 50):",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = secText,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                LazyColumn(modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalFadingEdge()
                                ) {
                                    items(zipEntries) { entryPath ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = entryPath,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                
                                Button(
                                    onClick = {
                                        isLoading = true
                                        scope.launch(Dispatchers.IO) {
                                            var success = false
                                            try {
                                                val targetDir = currentDirectory
                                                val canonicalTargetDir = targetDir.canonicalPath
                                                val inputStream = SafStorageHelper.getInputStream(context, zipPathToView) ?: java.io.FileInputStream(zipPathToView)
                                                val zipInputStream = java.util.zip.ZipInputStream(inputStream)
                                                var entry = zipInputStream.nextEntry
                                                while (entry != null) {
                                                    val outFile = File(targetDir, entry.name)
                                                    val canonicalDest = outFile.canonicalPath
                                                    if (!canonicalDest.startsWith(canonicalTargetDir + File.separator) && canonicalDest != canonicalTargetDir) {
                                                        throw SecurityException("Zip Slip path traversal vulnerability detected in entry: ${entry.name}")
                                                    }
                                                    if (entry.isDirectory) {
                                                        SafStorageHelper.mkdir(context, outFile.absolutePath)
                                                    } else {
                                                        outFile.parentFile?.let { SafStorageHelper.mkdir(context, it.absolutePath) }
                                                        SafStorageHelper.getOutputStream(context, outFile.absolutePath)?.use { fos ->
                                                            zipInputStream.copyTo(fos)
                                                        }
                                                    }
                                                    zipInputStream.closeEntry()
                                                    entry = zipInputStream.nextEntry
                                                }
                                                zipInputStream.close()
                                                success = true
                                            } catch (e: Exception) {
                                                success = false
                                            }
                                            if (!success) {
                                                try {
                                                    val targetDir = currentDirectory
                                                    val cmd = "unzip -o \"$zipPathToView\" -d \"${targetDir.absolutePath}\""
                                                    val res = viewModel.executeRootOrAdbOrLocalCommand(cmd)
                                                    success = !res.contains("failed") && !res.contains("Permission denied")
                                                } catch (ex: Exception) {
                                                    success = false
                                                }
                                            }
                                            withContext(Dispatchers.Main) {
                                                isLoading = false
                                                showZipViewerDialog = false
                                                if (success) {
                                                    android.widget.Toast.makeText(context, if (selectedLanguage == "uz") "Muvaffaqiyatli ochildi!" else "Extracted successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                                    loadFiles()
                                                } else {
                                                    android.widget.Toast.makeText(context, "Extraction failed", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                                ) {
                                    Text(
                                        text = if (selectedLanguage == "uz") "Joriy jildga arxivdan chiqarish" else "Extract to here",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = bgColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 11. Multi Details Dialog
        if (showMultiDetailsDialog) {
            AlertDialog(
                onDismissRequest = { showMultiDetailsDialog = false },
                title = { Text(if (selectedLanguage == "uz") "Tafsilotlar" else "Selected Items Details", fontFamily = FontFamily.Monospace, fontSize = 14.sp) },
                text = {
                    var totalSize by remember { mutableStateOf(0L) }
                    var fileCount by remember { mutableStateOf(0) }
                    var dirCount by remember { mutableStateOf(0) }
                    
                    LaunchedEffect(selectedItemPaths) {
                        var sizeSum = 0L
                        var fc = 0
                        var dc = 0
                        selectedItemPaths.forEach { path ->
                            val f = File(path)
                            if (f.isDirectory) {
                                dc++
                                f.walkBottomUp().forEach {
                                    if (it.isFile) sizeSum += it.length()
                                }
                            } else {
                                fc++
                                sizeSum += f.length()
                            }
                        }
                        totalSize = sizeSum
                        fileCount = fc
                        dirCount = dc
                    }
                    
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (selectedLanguage == "uz") "Tanlangan elementlar: ${selectedItemPaths.size} ta" else "Selected Items: ${selectedItemPaths.size}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = textColor
                        )
                        Text(
                            text = if (selectedLanguage == "uz") "Fayllar: $fileCount ta" else "Files: $fileCount",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = textColor
                        )
                        Text(
                            text = if (selectedLanguage == "uz") "Jildlar: $dirCount ta" else "Folders: $dirCount",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = textColor
                        )
                        Text(
                            text = if (selectedLanguage == "uz") "Umumiy hajmi: ${String.format("%.2f", totalSize / (1024f * 1024f))} MB" else "Total Size: ${String.format("%.2f", totalSize / (1024f * 1024f))} MB",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = textColor
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showMultiDetailsDialog = false }) {
                        Text("OK", fontFamily = FontFamily.Monospace, color = accentColor)
                    }
                },
                containerColor = cardBgColor
            )
        }

        // 12. Multi Delete Dialog
        if (showMultiDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showMultiDeleteConfirm = false },
                title = { Text(if (selectedLanguage == "uz") "O'chirishni tasdiqlang" else "Confirm Bulk Delete", fontFamily = FontFamily.Monospace, fontSize = 14.sp) },
                text = {
                    Text(
                        text = if (selectedLanguage == "uz") "Haqiqatan ham barcha tanlangan ${selectedItemPaths.size} ta elementni butunlay o'chirib tashlamoqchimisiz?" else "Are you sure you want to permanently delete all ${selectedItemPaths.size} selected items?",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = textColor
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val targets = selectedItemPaths
                        isLoading = true
                        scope.launch(Dispatchers.IO) {
                            var doneCount = 0
                            var failCount = 0
                            targets.forEach { path ->
                                val f = File(path)
                                var isDeleted = SafStorageHelper.delete(context, f.absolutePath)
                                if (!isDeleted) {
                                    viewModel.executeRootOrAdbOrLocalCommand("rm -rf \"${f.absolutePath}\"")
                                    isDeleted = !f.exists()
                                }
                                if (isDeleted) doneCount++ else failCount++
                            }
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                showMultiDeleteConfirm = false
                                isMultiSelectMode = false
                                selectedItemPaths = emptySet()
                                android.widget.Toast.makeText(context, 
                                    if (selectedLanguage == "uz") "O'chirildi: $doneCount, Xatolik: $failCount" else "Deleted: $doneCount, Failed: $failCount", 
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                loadFiles()
                            }
                        }
                    }) {
                        Text("Delete", fontFamily = FontFamily.Monospace, color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMultiDeleteConfirm = false }) {
                        Text("Cancel", fontFamily = FontFamily.Monospace, color = textColor)
                    }
                },
                containerColor = cardBgColor
            )
        }

        // 13. Multi Zip Compress Dialog
        if (showMultiZipConfirm) {
            var zipFileNameInput by remember { mutableStateOf("archive.zip") }
            AlertDialog(
                onDismissRequest = { showMultiZipConfirm = false },
                title = { Text(if (selectedLanguage == "uz") "Arxiv yaratish" else "Create ZIP Archive", fontFamily = FontFamily.Monospace, fontSize = 14.sp) },
                text = {
                    Column {
                        Text(
                            text = if (selectedLanguage == "uz") "Arxiv fayl nomi:" else "Archive name:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = textColor,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = zipFileNameInput,
                            onValueChange = { zipFileNameInput = it },
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, focusedBorderColor = accentColor)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val targets = selectedItemPaths
                        val archiveName = zipFileNameInput
                        if (archiveName.isNotEmpty()) {
                            isLoading = true
                            scope.launch(Dispatchers.IO) {
                                val destZip = File(currentDirectory, archiveName)
                                var zipSuccess = false
                                try {
                                    val outStream = SafStorageHelper.getOutputStream(context, destZip.absolutePath) ?: throw Exception("Failed to open output stream")
                                    val zos = java.util.zip.ZipOutputStream(outStream)
                                    targets.forEach { path ->
                                        val fileToZip = File(path)
                                        zipFileHelper(context, fileToZip, fileToZip.name, zos)
                                    }
                                    zos.close()
                                    zipSuccess = true
                                } catch (e: Exception) {
                                    zipSuccess = false
                                }
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    showMultiZipConfirm = false
                                    isMultiSelectMode = false
                                    selectedItemPaths = emptySet()
                                    if (zipSuccess) {
                                        android.widget.Toast.makeText(context, if (selectedLanguage == "uz") "Arxiv muvaffaqiyatli yaratildi!" else "ZIP created successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                        loadFiles()
                                    } else {
                                        android.widget.Toast.makeText(context, "Zipping failed", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }) {
                        Text("ZIP", fontFamily = FontFamily.Monospace, color = accentColor)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMultiZipConfirm = false }) {
                        Text("Cancel", fontFamily = FontFamily.Monospace, color = textColor)
                    }
                },
                containerColor = cardBgColor
            )
        }

        // 11. Bottom Floating Mini-Player (shows up when audio is minimized and playing)
        if (isAudioMinimized && currentPlayingAudioPath.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .clickable {
                        showAudioPlayerDialog = true
                        isAudioMinimized = false
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Playing Mini",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currentPlayingAudioPath.substringAfterLast('/'),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Play/Pause
                        IconButton(
                            onClick = {
                                try {
                                    if (sharedMediaPlayer.isPlaying) {
                                        sharedMediaPlayer.pause()
                                        isAudioPlaying = false
                                    } else {
                                        sharedMediaPlayer.start()
                                        isAudioPlaying = true
                                    }
                                } catch (e: Exception) {}
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause Mini",
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        // Next track
                        IconButton(
                            onClick = {
                                if (audioPlaylist.isNotEmpty() && audioPlaylistIndex >= 0) {
                                    val nextIndex = (audioPlaylistIndex + 1) % audioPlaylist.size
                                    audioPlaylistIndex = nextIndex
                                    playAudioFile(audioPlaylist[nextIndex])
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Track Mini",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        // Stop / Close
                        IconButton(
                            onClick = {
                                try {
                                    sharedMediaPlayer.stop()
                                } catch (e: Exception) {}
                                isAudioPlaying = false
                                isAudioMinimized = false
                                currentPlayingAudioPath = ""
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Stop Mini",
                                tint = Color.Red,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Helper extension to unwrap FragmentActivity from Context wrappers
private fun android.content.Context.findFragmentActivity(): androidx.fragment.app.FragmentActivity? {
    var ctx: android.content.Context? = this
    while (ctx != null) {
        if (ctx is androidx.fragment.app.FragmentActivity) return ctx
        if (ctx is android.content.ContextWrapper) {
            ctx = ctx.baseContext
        } else {
            break
        }
    }
    return null
}

// Helper to trigger native Android BiometricPrompt securely
private fun triggerBiometricPrompt(
    context: android.content.Context,
    selectedLanguage: String,
    onSuccess: () -> Unit,
    onFailed: () -> Unit,
    isManual: Boolean = false
) {
    val activity = context.findFragmentActivity()
    if (activity == null) {
        if (isManual) {
            android.widget.Toast.makeText(
                context,
                if (selectedLanguage == "uz") "Biometriya mavjud emas" else if (selectedLanguage == "ru") "Биометрия недоступна" else "Biometrics not available",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        return
    }

    try {
        val biometricManager = androidx.biometric.BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or 
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        if (canAuth != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
            if (isManual) {
                val msg = when (canAuth) {
                    androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> 
                        if (selectedLanguage == "uz") "Qurilmada barmoq izi skaneri yo'q" else if (selectedLanguage == "ru") "Нет сканера отпечатков" else "No biometric sensor found"
                    androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> 
                        if (selectedLanguage == "uz") "Biometriya skaneri band" else if (selectedLanguage == "ru") "Биометрия недоступна" else "Biometric sensor busy"
                    androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> 
                        if (selectedLanguage == "uz") "Barmoq izi sozlamalarda ro'yxatdan o'tkazilmagan" else if (selectedLanguage == "ru") "Отпечаток не зарегистрирован" else "No biometrics enrolled"
                    else -> 
                        if (selectedLanguage == "uz") "Biometriya mavjud emas" else if (selectedLanguage == "ru") "Биометрия недоступна" else "Biometrics unavailable"
                }
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }

        val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
        val biometricPrompt = androidx.biometric.BiometricPrompt(
            activity,
            executor,
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED && 
                        errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != androidx.biometric.BiometricPrompt.ERROR_CANCELED) {
                        android.widget.Toast.makeText(context, errString, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    android.widget.Toast.makeText(
                        context, 
                        if (selectedLanguage == "uz") "Muvaffaqiyatli o'tildi!" else if (selectedLanguage == "ru") "Успешно!" else "Biometric Verified!", 
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            }
        )

        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (selectedLanguage == "uz") "Biometrik tasdiqlash" else if (selectedLanguage == "ru") "Биометрическая аутентификация" else "Biometric Authentication")
            .setSubtitle(if (selectedLanguage == "uz") "Tizimga kirish" else if (selectedLanguage == "ru") "Вход в систему" else "Log in to the system")
            .setNegativeButtonText(if (selectedLanguage == "uz") "PIN / Parol" else if (selectedLanguage == "ru") "PIN / Пароль" else "PIN / Password")
            .build()

        biometricPrompt.authenticate(promptInfo)
    } catch (e: Exception) {
        if (isManual) {
            android.widget.Toast.makeText(context, "Biometric error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

// Gorgeous High-Security Lock Overlay Supporting: Pin pad, password field, pattern drawing, and native fingerprint fallback
@Composable
fun LockScreenOverlay(
    terminalTheme: String,
    selectedLanguage: String,
    isCancellable: Boolean = false,
    onCancel: () -> Unit = {},
    onSuccess: () -> Unit
) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = { if (isCancellable) onCancel() else activity?.finish() },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = isCancellable,
            dismissOnClickOutside = false,
            securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn
        )
    ) {
        val context = LocalContext.current
        val sharedPrefs = remember { context.getSharedPreferences("super_adb_settings", Context.MODE_PRIVATE) }
        
        androidx.activity.compose.BackHandler {
            if (isCancellable) onCancel() else activity?.finish()
        }
        
        // Auto fallback from biometric-only mode if configured
        val lockType = remember { 
            val raw = sharedPrefs.getString("lock_type", "pin") ?: "pin" 
            if (raw == "biometric") "pin" else raw
        }
        val savedLock = remember { 
            val raw = sharedPrefs.getString("saved_lock_key", "") ?: ""
            if (raw.isEmpty()) {
                if (lockType == "pin") "1234" else ""
            } else raw
        }
        
        var inputPin by remember { mutableStateOf("") }
        var inputPassword by remember { mutableStateOf("") }
        var errorState by remember { mutableStateOf(false) }

        // Theme Colors
        val (textColor, bgColor, borderColor, accentColor, cardBgColor, secText) = getThemeColors(terminalTheme)

        // Drawing variables for pattern
        val patternTouchedDots = remember { mutableStateListOf<Int>() }
        var patternDragX by remember { mutableStateOf(-1f) }
        var patternDragY by remember { mutableStateOf(-1f) }

        LaunchedEffect(errorState) {
            if (errorState) {
                kotlinx.coroutines.delay(3000L)
                errorState = false
                patternTouchedDots.clear()
            }
        }

        // Automatically prompt biometric scanning on startup (only if available)
        LaunchedEffect(Unit) {
            triggerBiometricPrompt(context, selectedLanguage, onSuccess, { errorState = true }, isManual = false)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { } // swallow clicks
        ) {
            if (isCancellable) {
                androidx.compose.material3.IconButton(
                    onClick = onCancel,
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = textColor)
                }
            }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Visual lock icon
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = if (errorState) Color.Red else accentColor,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = if (errorState) {
                    when (selectedLanguage) {
                        "en" -> "Incorrect Try Again!"
                        "ru" -> "Неверный код, повторите!"
                        else -> "Xato qaytadan urining!"
                    }
                } else {
                    when (selectedLanguage) {
                        "en" -> "System Security"
                        "ru" -> "Система безопасности"
                        else -> "Xavfsizlik tizimi"
                    }
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (errorState) Color.Red else textColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            when (lockType) {
                "password" -> {
                    // Column wrapper to hold the row and a single long bottom underline
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(0.95f)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Left: Biometric Fingerprint Button (shifted 0.5 cm / ~20dp left)
                            IconButton(
                                onClick = {
                                    triggerBiometricPrompt(context, selectedLanguage, onSuccess, { errorState = true }, isManual = true)
                                },
                                modifier = Modifier
                                    .offset(x = (-20).dp)
                                    .size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Biometric Unlock",
                                    tint = accentColor,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            // Center: Password input field (borders/placeholder removed, center aligned text)
                            TextField(
                                value = inputPassword,
                                onValueChange = { inputPassword = it },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = textColor,
                                    unfocusedTextColor = textColor,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                textStyle = LocalTextStyle.current.copy(
                                    textAlign = TextAlign.Center,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 18.sp,
                                    color = textColor
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("password_lock_input")
                            )

                            // Right: Checkmark (ptichka) Submit Button (shifted 0.5 cm / ~20dp right)
                            IconButton(
                                onClick = {
                                    if (inputPassword == savedLock) {
                                        onSuccess()
                                        inputPassword = ""
                                    } else {
                                        errorState = true
                                        inputPassword = ""
                                    }
                                },
                                modifier = Modifier
                                    .offset(x = 20.dp)
                                    .size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Unlock",
                                    tint = accentColor,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Long underline underneath the entire fingerprint, input and checkmark row
                        HorizontalDivider(
                            color = accentColor.copy(alpha = 0.8f),
                            thickness = 1.5.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                "pattern" -> {
                    // 3x3 pattern drawing pad
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .aspectRatio(1f)
                            .pointerInput(Unit) {
                                val density = this
                                val thresholdPx = with(density) { 30.dp.toPx() } // precise touch radius (using 30.dp)
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        patternTouchedDots.clear()
                                        errorState = false
                                        val hit = detectHitDot(offset, size.width.toFloat(), size.height.toFloat(), thresholdPx)
                                        if (hit != null) patternTouchedDots.add(hit)
                                    },
                                    onDrag = { change, dragAmount ->
                                        val position = change.position
                                        patternDragX = position.x
                                        patternDragY = position.y
                                        val hit = detectHitDot(position, size.width.toFloat(), size.height.toFloat(), thresholdPx)
                                        if (hit != null && !patternTouchedDots.contains(hit)) {
                                            patternTouchedDots.add(hit)
                                        }
                                    },
                                    onDragEnd = {
                                        val drawnString = patternTouchedDots.joinToString(",")
                                        if (drawnString == savedLock) {
                                            onSuccess()
                                        } else {
                                            errorState = true
                                        }
                                        patternDragX = -1f
                                        patternDragY = -1f
                                    }
                                )
                            }
                            .drawBehind {
                                // Draw lines connecting touched dots
                                val stepX = size.width / 6f
                                val stepY = size.height / 6f
                                
                                val path = Path()
                                patternTouchedDots.forEachIndexed { index, dot ->
                                    val row = dot / 3
                                    val col = dot % 3
                                    val cx = (col * 2 + 1) * stepX
                                    val cy = (row * 2 + 1) * stepY
                                    
                                    if (index == 0) {
                                        path.moveTo(cx, cy)
                                    } else {
                                        path.lineTo(cx, cy)
                                    }
                                }
                                
                                // Drag line to current pointer
                                if (patternTouchedDots.isNotEmpty() && patternDragX != -1f) {
                                    path.lineTo(patternDragX, patternDragY)
                                }
                                
                                if (patternTouchedDots.isNotEmpty()) {
                                    drawPath(
                                        path = path,
                                        color = if (errorState) Color.Red else accentColor,
                                        style = Stroke(width = 4.dp.toPx())
                                    )
                                }
                            }
                    ) {
                        val gridSize = maxWidth
                        val dotSize = 72.dp
                        for (row in 0..2) {
                            for (col in 0..2) {
                                val index = row * 3 + col
                                val isSelected = patternTouchedDots.contains(index)
                                
                                val cxDp = gridSize * (col * 2 + 1) / 6
                                val cyDp = gridSize * (row * 2 + 1) / 6
                                val xOffset = cxDp - dotSize / 2
                                val yOffset = cyDp - dotSize / 2
                                
                                Box(
                                    modifier = Modifier
                                        .offset(x = xOffset, y = yOffset)
                                        .size(dotSize)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) {
                                                (if (errorState) Color.Red else accentColor).copy(alpha = 0.25f)
                                            } else {
                                                cardBgColor.copy(alpha = 0.40f)
                                            }
                                        )
                                        .border(
                                            width = 2.dp,
                                            color = if (isSelected) (if (errorState) Color.Red else accentColor) else borderColor.copy(alpha = 0.45f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Inner Dot
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) {
                                                    if (errorState) Color.Red else accentColor
                                                } else {
                                                    textColor.copy(alpha = 0.5f)
                                                }
                                            )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                    IconButton(
                        onClick = {
                            triggerBiometricPrompt(context, selectedLanguage, onSuccess, { errorState = true }, isManual = true)
                        },
                        modifier = Modifier.size(80.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Biometric Unlock",
                            tint = accentColor,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                else -> {
                    // PIN Pad Layout (glowing numeric buttons)
                    val totalDots = maxOf(4, inputPin.length)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 36.dp)
                    ) {
                        repeat(totalDots) { idx ->
                            val filled = inputPin.length > idx
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (filled) {
                                            if (errorState) Color.Red else accentColor
                                        } else {
                                            textColor.copy(alpha = 0.15f)
                                        }
                                    )
                                    .border(2.dp, borderColor.copy(alpha = 0.5f), CircleShape)
                            )
                        }
                    }

                    // Keypad Grid
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("BIO", "0", "DEL")
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        keys.forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                row.forEach { key ->
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(CircleShape)
                                            .background(cardBgColor)
                                            .border(2.dp, borderColor.copy(alpha = 0.5f), CircleShape)
                                            .clickable {
                                                errorState = false
                                                when (key) {
                                                    "DEL" -> {
                                                        if (inputPin.isNotEmpty()) inputPin = inputPin.dropLast(1)
                                                    }
                                                    "BIO" -> {
                                                        triggerBiometricPrompt(context, selectedLanguage, onSuccess, { errorState = true }, isManual = true)
                                                    }
                                                    else -> {
                                                        val targetLen = if (savedLock.isNotEmpty()) savedLock.length else 4
                                                        if (inputPin.length < targetLen) {
                                                            inputPin += key
                                                            if (inputPin.length == targetLen) {
                                                                if (inputPin == savedLock) {
                                                                    onSuccess()
                                                                } else {
                                                                    errorState = true
                                                                    inputPin = ""
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (key == "BIO") {
                                            Icon(Icons.Outlined.Fingerprint, contentDescription = "Biometric", tint = accentColor, modifier = Modifier.size(32.dp))
                                        } else if (key == "DEL") {
                                            Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = textColor, modifier = Modifier.size(32.dp))
                                        } else {
                                            Text(
                                                text = key,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 26.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

// Helper to calculate touched pattern node index (0 to 8)
private fun detectHitDot(offset: Offset, width: Float, height: Float, thresholdPx: Float): Int? {
    val stepX = width / 6f
    val stepY = height / 6f
    
    for (row in 0..2) {
        for (col in 0..2) {
            val cx = (col * 2 + 1) * stepX
            val cy = (row * 2 + 1) * stepY
            val dist = sqrt((offset.x - cx) * (offset.x - cx) + (offset.y - cy) * (offset.y - cy))
            if (dist < thresholdPx) {
                return row * 3 + col
            }
        }
    }
    return null
}

// Utility mapper for Theme colors
fun getThemeColors(theme: String): ThemeColors {
    return when (theme) {
        "ubuntu" -> ThemeColors(
            textColor = Color(0xFFFFFFFF),
            bgColor = Color(0xFF1E0A16),
            borderColor = Color(0xFFE95420),
            accentColor = Color(0xFFE95420),
            cardBgColor = Color(0xFF351226),
            secText = Color(0xFFCBD5E1)
        )
        "matrix" -> ThemeColors(
            textColor = Color(0xFF4ADE80),
            bgColor = Color(0xFF021004),
            borderColor = Color(0xFF16A34A),
            accentColor = Color(0xFF22C55E),
            cardBgColor = Color(0xFF07260E),
            secText = Color(0xFF86EFAC)
        )
        "cyberpunk" -> ThemeColors(
            textColor = Color(0xFFFFFFFF),
            bgColor = Color(0xFF120324),
            borderColor = Color(0xFF9333EA),
            accentColor = Color(0xFFFF007F),
            cardBgColor = Color(0xFF280E47),
            secText = Color(0xFFF0ABFC)
        )
        "monochrome" -> ThemeColors(
            textColor = Color(0xFFFFFFFF),
            bgColor = Color(0xFF000000),
            borderColor = Color(0xFF333333),
            accentColor = Color(0xFFFFFFFF),
            cardBgColor = Color(0xFF161616),
            secText = Color(0xFFB0B0B0)
        )
        "light" -> ThemeColors(
            textColor = Color(0xFF0F172A),
            bgColor = Color(0xFFF1F5F9),
            borderColor = Color(0xFFCBD5E1),
            accentColor = Color(0xFF0284C7),
            cardBgColor = Color(0xFFFFFFFF),
            secText = Color(0xFF475569)
        )
        else -> ThemeColors(
            textColor = Color(0xFFFFFFFF),
            bgColor = Color(0xFF1E0A16),
            borderColor = Color(0xFFE95420),
            accentColor = Color(0xFFE95420),
            cardBgColor = Color(0xFF351226),
            secText = Color(0xFFCBD5E1)
        )
    }
}

@Composable
fun ProfessionalSpinningLoader(color: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "spinTransition")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "spinAngle"
    )
    
    Box(
        modifier = modifier
            .size(36.dp)
            .graphicsLayer { rotationZ = rotationAngle },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 280f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 3.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }
    }
}

// Helper to zip files recursively
private fun zipFileHelper(context: android.content.Context, fileToZip: File, fileName: String, zos: java.util.zip.ZipOutputStream) {
    if (fileToZip.isHidden) return
    if (fileToZip.isDirectory) {
        if (fileName.endsWith("/")) {
            zos.putNextEntry(java.util.zip.ZipEntry(fileName))
            zos.closeEntry()
        } else {
            zos.putNextEntry(java.util.zip.ZipEntry("$fileName/"))
            zos.closeEntry()
        }
        val children = fileToZip.listFiles() ?: return
        for (child in children) {
            zipFileHelper(context, child, "$fileName/${child.name}", zos)
        }
    } else {
        val stream = SafStorageHelper.getInputStream(context, fileToZip.absolutePath) ?: return
        stream.use { fis ->
            try {
                zos.putNextEntry(java.util.zip.ZipEntry(fileName))
                fis.copyTo(zos)
                zos.closeEntry()
            } catch (e: Exception) {}
        }
    }
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}

