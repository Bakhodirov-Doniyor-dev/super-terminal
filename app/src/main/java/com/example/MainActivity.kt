@file:Suppress("DEPRECATION")
package com.example

import com.example.ui.verticalFadingEdge
import com.example.ui.glassCapsule
import com.example.ui.oneUiGlassCard
import com.example.ui.oneUiGlassCapsule
import com.example.ui.OneUiAmbientBackdrop
import com.example.ui.OneUiGlassCapsuleButton
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.db.HistoryEntity
import com.example.db.ScriptEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AdbViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import android.net.Uri
import kotlin.math.sqrt

private fun detectHitDotLocal(offset: Offset, width: Float, height: Float): Int? {
    val stepX = width / 6f
    val stepY = height / 6f
    val threshold = 35f
    for (row in 0..2) {
        for (col in 0..2) {
            val cx = (col * 2 + 1) * stepX
            val cy = (row * 2 + 1) * stepY
            val dist = sqrt((offset.x - cx) * (offset.x - cx) + (offset.y - cy) * (offset.y - cy))
            if (dist < threshold) {
                return row * 3 + col
            }
        }
    }
    return null
}

fun Modifier.drawScrollbar(
    scrollState: androidx.compose.foundation.ScrollState,
    color: Color = Color.White
): Modifier = this.drawWithContent {
    drawContent()
    if (scrollState.maxValue > 0) {
        val viewPortHeight = size.height
        val contentHeight = scrollState.maxValue + viewPortHeight
        if (contentHeight > 0f) {
            val scrollPercent = (scrollState.value.toFloat() / scrollState.maxValue).coerceIn(0f, 1f)
            
            // Explicitly resolve dp to px within density scope
            val minHeightPx = with(this) { 16.dp.toPx() }
            val scrollbarHeight = ((viewPortHeight / contentHeight) * viewPortHeight).coerceIn(minHeightPx, viewPortHeight)
            val scrollbarTop = (scrollPercent * (viewPortHeight - scrollbarHeight)).coerceIn(0f, (viewPortHeight - scrollbarHeight).coerceAtLeast(0f))
            
            val trackWidthPx = with(this) { 2.dp.toPx() }
            val thumbWidthPx = with(this) { 4.dp.toPx() }
            val offsetTrackPx = with(this) { 5.dp.toPx() }
            val offsetThumbPx = with(this) { 6.dp.toPx() }
            val cornerRadiusPx = with(this) { 2.dp.toPx() }
            
            // Draw track on the right
            drawRect(
                color = color.copy(alpha = 0.15f),
                topLeft = androidx.compose.ui.geometry.Offset(size.width - offsetTrackPx, 0f),
                size = androidx.compose.ui.geometry.Size(trackWidthPx, viewPortHeight)
            )
            // Draw thumb - highly visible scroll bar
            drawRoundRect(
                color = color.copy(alpha = 0.9f),
                topLeft = androidx.compose.ui.geometry.Offset(size.width - offsetThumbPx, scrollbarTop),
                size = androidx.compose.ui.geometry.Size(thumbWidthPx, scrollbarHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx, cornerRadiusPx)
            )
        }
    }
}

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val viewModel: AdbViewModel by viewModels()
    var filePickerCallback: ((List<android.net.Uri>) -> Unit)? = null
    var isSelectingFileForAi = false
    var isBypassingLock = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        handleIncomingIntent(intent)
        setContent {
            MyApplicationTheme {
                MainScreen(viewModel, this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val sharedCmd = intent?.getStringExtra("shared_terminal_command")
        if (!sharedCmd.isNullOrBlank()) {
            viewModel.onCommandChange(sharedCmd)
            android.widget.Toast.makeText(this, "Terminal buyrug'i yuklandi: $sharedCmd", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        val sharedPrefs = getSharedPreferences("terminal_prefs", android.content.Context.MODE_PRIVATE)
        if (sharedPrefs.getBoolean("lock_enabled", false)) {
            window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onResume() {
        super.onResume()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        viewModel.checkConnectionOnResume()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            isSelectingFileForAi = false
            isBypassingLock = false
        }, 500)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            if (resultCode == RESULT_OK && data != null) {
                val matches = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0]
                    viewModel.onCommandChange(spokenText)
                }
            }
            viewModel.setVoiceInputActive(false)
        } else if (requestCode == 1002) {
            if (resultCode == RESULT_OK && data != null) {
                val uris = mutableListOf<android.net.Uri>()
                val clipData = data.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        uris.add(clipData.getItemAt(i).uri)
                    }
                } else {
                    data.data?.let { uris.add(it) }
                }
                if (uris.isNotEmpty()) {
                    filePickerCallback?.invoke(uris)
                }
            }
        } else if (requestCode == 1003) {
            isSelectingFileForAi = false
            if (resultCode == RESULT_OK && data != null) {
                val uri = data.data
                if (uri != null) {
                    try {
                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        contentResolver.takePersistableUriPermission(uri, takeFlags)
                        viewModel.safPermissionRefreshTrigger.value++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}




@Composable
fun MainScreen(viewModel: AdbViewModel, activityOverride: ComponentActivity? = null) {
    val portInput by viewModel.portInput.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val terminalOutput by viewModel.terminalOutput.collectAsState()
    val commandInput by viewModel.commandInput.collectAsState()

    val savedScripts by viewModel.savedScripts.collectAsState()
    val commandHistory by viewModel.commandHistory.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val isPythonActive by viewModel.isPythonReplActive.collectAsState()
    val isMatrixRainActive by viewModel.isMatrixRainActive.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()

    val terminalFontSize by viewModel.terminalFontSize.collectAsState()
    val terminalTheme by viewModel.terminalTheme.collectAsState()
    val tabs by viewModel.tabs.collectAsState()
    val currentTabId by viewModel.currentTabId.collectAsState()
    val isTtsEnabled by viewModel.isTtsEnabled.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val currentWorkingDirectory by viewModel.currentWorkingDirectory.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showAntiOverlayGuardDialog by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = remember(context, activityOverride) { activityOverride ?: context.findActivity() }
    var isImmersiveMode by rememberSaveable { mutableStateOf(false) }

    // Navigation and screen toggles
    var activeMainView by rememberSaveable { mutableStateOf(0) } // 0 = Terminal, 1 = Apps, 2 = Monitor, 3 = Files
    val sharedPrefs = remember { context.getSharedPreferences("super_adb_settings", android.content.Context.MODE_PRIVATE) }
    var isAppLocked by remember {
        mutableStateOf(
            if (sharedPrefs.getBoolean("lock_enabled", false)) {
                val delayMs = sharedPrefs.getLong("lock_delay_ms", 0L)
                if (delayMs == 0L) {
                    true
                } else {
                    val lastExit = sharedPrefs.getLong("last_exit_time", 0L)
                    lastExit == 0L || (System.currentTimeMillis() - lastExit >= delayMs)
                }
            } else {
                false
            }
        )
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var isColdStart by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            val mainActivity = activityOverride as? MainActivity
            val isSelecting = mainActivity?.isSelectingFileForAi == true
            val isBypassing = mainActivity?.isBypassingLock == true
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                if (sharedPrefs.getBoolean("lock_enabled", false) && !isSelecting && !isBypassing) {
                    sharedPrefs.edit().putLong("last_exit_time", System.currentTimeMillis()).apply()
                    // Proactively lock the app so the UI state is already the LockScreen when returning.
                    isAppLocked = true
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START || event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (isColdStart) {
                    isColdStart = false
                } else {
                    if (sharedPrefs.getBoolean("lock_enabled", false) && !isSelecting && !isBypassing) {
                        val delayMs = sharedPrefs.getLong("lock_delay_ms", 0L)
                        val lastExit = sharedPrefs.getLong("last_exit_time", 0L)
                        if (delayMs > 0L && lastExit > 0L && System.currentTimeMillis() - lastExit < delayMs) {
                            // If they returned before the delay expired, unlock it again immediately
                            isAppLocked = false
                        } else {
                            isAppLocked = true
                        }
                    } else if (isBypassing) {
                        isAppLocked = false
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showOpenTabsScreen by rememberSaveable { mutableStateOf(false) }
    var tabSearchQuery by rememberSaveable { mutableStateOf("") }
    var showMainMenu by rememberSaveable { mutableStateOf(false) }
    var isBottomBarVisible by rememberSaveable { mutableStateOf(true) }

    // Dialog sheets
    var showAiAssistantDialog by rememberSaveable { mutableStateOf(false) }
    var isAiAssistantFullScreen by rememberSaveable { mutableStateOf(false) }

    var showShortcutsDialog by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var showPrivacyDialog by rememberSaveable { mutableStateOf(false) }
    var showSigningDialog by rememberSaveable { mutableStateOf(false) }
    var showConnectionManagerDialog by rememberSaveable { mutableStateOf(false) }
    val showAppUpdateDialog by viewModel.showAppUpdateDialog.collectAsState()

    // Auto-check for updates on startup if enabled
    LaunchedEffect(Unit) {
        if (viewModel.appUpdateManager.isAutoCheckEnabled()) {
            kotlinx.coroutines.delay(3500)
            viewModel.appUpdateManager.checkForUpdates()
        }
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isAppLocked) {
        if (isAppLocked) {
            focusManager.clearFocus()
            keyboardController?.hide()
            showSettingsDialog = false
            showShortcutsDialog = false
            showAboutDialog = false
            showPrivacyDialog = false
            showSigningDialog = false
            showConnectionManagerDialog = false
            showAntiOverlayGuardDialog = false
            showAiAssistantDialog = false
            showMainMenu = false
            viewModel.showAppUpdateDialog.value = false
            showOpenTabsScreen = false
        }
    }

    // Automatically hide software keyboard and release focus when modals/dialogs/menus are displayed
    LaunchedEffect(showSettingsDialog, showShortcutsDialog, showAboutDialog, showMainMenu, showOpenTabsScreen, showPrivacyDialog, showSigningDialog, showAiAssistantDialog, showConnectionManagerDialog, showAntiOverlayGuardDialog) {
        if (showSettingsDialog || showShortcutsDialog || showAboutDialog || showMainMenu || showOpenTabsScreen || showPrivacyDialog || showSigningDialog || showAiAssistantDialog || showConnectionManagerDialog || showAntiOverlayGuardDialog) {
            focusManager.clearFocus()
            kotlinx.coroutines.delay(100)
            keyboardController?.hide()
        }
    }

    // Immersive Mode controller
    val shouldBeImmersive = if (showAiAssistantDialog) isAiAssistantFullScreen else isImmersiveMode

    LaunchedEffect(shouldBeImmersive) {
        val window = activity?.window ?: return@LaunchedEffect
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (shouldBeImmersive) {
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        } else {
            insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        }
    }

    if (showSettingsDialog && !isAppLocked) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false },
            onResetScripts = {
                viewModel.resetScriptsToDefault()
                scope.launch {
                    snackbarHostState.showSnackbar("Namunaviy skriptlar asliga qaytarildi!")
                }
            },
            onResetConnection = {
                viewModel.disconnectAdb()
                scope.launch {
                    snackbarHostState.showSnackbar("ADB ulanishi tozalab qayta tiklandi.")
                }
            },
            savedScripts = savedScripts,
            commandHistory = commandHistory,
            deviceInfo = deviceInfo,
            scope = scope,
            snackbarHostState = snackbarHostState,
            onOpenAntiOverlaySentry = { showAntiOverlayGuardDialog = true },
            onOpenAppUpdates = { viewModel.showAppUpdateDialog.value = true }
        )
    }

    if (showAntiOverlayGuardDialog && !isAppLocked) {
        com.example.ui.AntiOverlayGuardDialog(
            terminalTheme = terminalTheme,
            selectedLanguage = selectedLanguage,
            viewModel = viewModel,
            onDismiss = { showAntiOverlayGuardDialog = false }
        )
    }

    val currentThemeScheme = when (terminalTheme) {
        "ubuntu" -> androidx.compose.material3.darkColorScheme(
            primary = Color(0xFFE95420),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF381426),
            onPrimaryContainer = Color.White,
            secondary = Color.White.copy(alpha = 0.6f),
            onSecondary = Color.White,
            surface = Color(0xFF381426),
            onSurface = Color.White,
            background = Color(0xFF2C001E),
            onBackground = Color.White,
            tertiary = Color(0xFFE95420),
            tertiaryContainer = Color(0xFFE95420).copy(alpha = 0.25f),
            errorContainer = Color(0xFF4E1410)
        )
        "matrix" -> androidx.compose.material3.darkColorScheme(
            primary = Color(0xFF33FF33),
            onPrimary = Color.Black,
            primaryContainer = Color(0xFF002200),
            onPrimaryContainer = Color(0xFF33FF33),
            secondary = Color(0xFF33FF33).copy(alpha = 0.6f),
            onSecondary = Color.Black,
            surface = Color(0xFF002200),
            onSurface = Color(0xFF33FF33),
            background = Color(0xFF001100),
            onBackground = Color(0xFF33FF33),
            tertiary = Color(0xFF33FF33),
            tertiaryContainer = Color(0xFF33FF33).copy(alpha = 0.25f),
            errorContainer = Color(0xFF220000)
        )
        "cyberpunk" -> androidx.compose.material3.darkColorScheme(
            primary = Color(0xFFFF007F),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF230D3E),
            onPrimaryContainer = Color.White,
            secondary = Color(0xFF7E00FF),
            onSecondary = Color.White,
            surface = Color(0xFF230D3E),
            onSurface = Color(0xFFFF007F),
            background = Color(0xFF120422),
            onBackground = Color(0xFFFF007F),
            tertiary = Color(0xFFFF007F),
            tertiaryContainer = Color(0xFFFF007F).copy(alpha = 0.25f),
            errorContainer = Color(0xFF3A001C)
        )
        "monochrome" -> androidx.compose.material3.darkColorScheme(
            primary = Color(0xFFFFFFFF),
            onPrimary = Color.Black,
            primaryContainer = Color(0xFF1E1E1E),
            onPrimaryContainer = Color.White,
            secondary = Color.White.copy(alpha = 0.5f),
            onSecondary = Color.Black,
            surface = Color(0xFF1E1E1E),
            onSurface = Color.White,
            background = Color(0xFF000000),
            onBackground = Color.White,
            tertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFFFFF).copy(alpha = 0.25f),
            errorContainer = Color(0xFF1E1E1E)
        )
        "light" -> androidx.compose.material3.lightColorScheme(
            primary = Color(0xFF1E293B),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE2E8F0),
            onPrimaryContainer = Color(0xFF1E293B),
            secondary = Color(0xFF64748B),
            onSecondary = Color.White,
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1E293B),
            background = Color(0xFFF1F5F9),
            onBackground = Color(0xFF1E293B),
            tertiary = Color(0xFF1E293B),
            tertiaryContainer = Color(0xFF1E293B).copy(alpha = 0.25f),
            errorContainer = Color(0xFFFFE0E0)
        )
        else -> androidx.compose.material3.darkColorScheme(
            primary = Color(0xFFE95420),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF381426),
            onPrimaryContainer = Color.White,
            secondary = Color.White.copy(alpha = 0.6f),
            onSecondary = Color.White,
            surface = Color(0xFF381426),
            onSurface = Color.White,
            background = Color(0xFF2C001E),
            onBackground = Color.White,
            tertiary = Color(0xFFE95420),
            tertiaryContainer = Color(0xFFE95420).copy(alpha = 0.25f),
            errorContainer = Color(0xFF4E1410)
        )
    }

    if (showShortcutsDialog && !isAppLocked) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        
        class LocalShortcut(val cmd: String, val descUz: String, val descEn: String, val descRu: String)
        val shortcutsList = listOf(
            LocalShortcut("clear", "Terminal ekranini tozalash", "Clear terminal screen", "Очистить экран терминала"),
            LocalShortcut("pm list packages -3", "O'rnatilgan tashqi ilovalarni ko'rish", "View third-party app packages", "Список сторонних приложений"),
            LocalShortcut("pm list packages -s", "Tizim ilovalarini (system packages) ko'rish", "View system installed packages", "Список системных пакетов"),
            LocalShortcut("dumpsys battery", "Batareya diagnostikasini ko'rish", "Get battery status diagnostics", "Вывести диагностику батареи"),
            LocalShortcut("dumpsys cpuinfo", "Protsessor (CPU) yuki va sarfi statistikasi", "CPU load and usage stats", "Статистика нагрузки на процессор"),
            LocalShortcut("dumpsys meminfo", "Operativ xotira (RAM) batafsil taqsimoti", "Detailed RAM memory breakdown", "Подробное распределение памяти RAM"),
            LocalShortcut("df -h", "Disk xotirasi va bo'limlar sig'imi", "Filesystem disk space usage", "Использование дискового пространства"),
            LocalShortcut("free -m", "RAM va Swap xotira sarfi (Megabaytda)", "RAM & Swap memory stats in MB", "Статистика RAM и Swap памяти (МБ)"),
            LocalShortcut("uptime", "Tizimning uzluksiz ishlash vaqti va yuklama", "Device system uptime and load average", "Время непрерывной работы устройства"),
            LocalShortcut("getprop ro.product.model", "Qurilma modelini aniqlash", "Show device model", "Определить модель устройства"),
            LocalShortcut("getprop ro.build.version.release", "Android tizimi talqinini ko'rish", "Android OS release version", "Версия операционной системы Android"),
            LocalShortcut("wm size", "Ekran o'lchamini/rezolyutsiyasini ko'rish", "Get screen size/resolution", "Показать разрешение экрана"),
            LocalShortcut("wm density", "Ekran piksel zichligini (DPI) ko'rish", "Display screen density (DPI)", "Плотность пикселей экрана (DPI)"),
            LocalShortcut("ls -la /sdcard", "Ichki xotiradagi fayllar va papkalar", "List storage root files and permissions", "Список файлов внутреннего хранилища"),
            LocalShortcut("screencap -p /sdcard/s.png", "Ekran rasmini olish va saqlash", "Take and save a screenshot", "Сделать снимок экрана"),
            LocalShortcut("top -n 1", "Faol protsesslar va ularning CPU ulushi", "Snapshot of active processes and CPU", "Снимок активных процессов и CPU"),
            LocalShortcut("ps -ef", "Barcha tizim protsesslari va PID ro'yxati", "List all running processes and PIDs", "Список всех запущенных процессов и PID"),
            LocalShortcut("ifconfig", "Tarmoq interfeyslari (WiFi, IP) ma'lumoti", "Display network interface info", "Информация о сетевых интерфейсах"),
            LocalShortcut("ip addr show", "Barcha tarmoq IP manzillari", "Show all network IP addresses", "Показать все сетевые IP адреса"),
            LocalShortcut("ping -c 4 8.8.8.8", "Internet tarmog'iga ulanishni tekshirish", "Ping Google DNS to test connectivity", "Проверка связи с сетью через Google DNS"),
            LocalShortcut("logcat -d -v time *:E", "Tizimning xatolik (Error) jurnallari", "Dump latest system error logs", "Журнал критических системных ошибок"),
            LocalShortcut("settings get secure android_id", "Qurilmaning unikal Android ID kodi", "Get device secure Android ID", "Уникальный Android ID устройства"),
            LocalShortcut("input keyevent 26", "Ekran quvvat tugmasini bosish simulyatsiyasi", "Simulate power button press", "Симуляция нажатия кнопки питания"),
            LocalShortcut("adb devices", "ADB ulanishlari va qurilmalar ro'yxati", "List connected ADB targets", "Список подключенных ADB устройств"),
            LocalShortcut("adb tcpip 5555", "Simsiz ADB portini faollashtirish (5555)", "Restart ADB in TCP mode on port 5555", "Включить беспроводной порт ADB 5555")
        )

        Dialog(
            onDismissRequest = { showShortcutsDialog = false }
        ) {
            MaterialTheme(colorScheme = currentThemeScheme) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = if (isLandscape) 8.dp else 24.dp)
                        .widthIn(max = 480.dp)
                        .heightIn(max = if (isLandscape) 280.dp else 500.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = Translations.get("shortcuts_title", selectedLanguage),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        val shortcutsScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .drawScrollbar(shortcutsScrollState)
                                .verticalScroll(shortcutsScrollState)
                                .padding(end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val clipboardShortcutManager = androidx.compose.ui.platform.LocalClipboardManager.current
                            shortcutsList.forEach { item ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            clipboardShortcutManager.setText(androidx.compose.ui.text.AnnotatedString(item.cmd))
                                            android.widget.Toast.makeText(
                                                context,
                                                if (selectedLanguage == "uz") "Nusxalandi: ${item.cmd}"
                                                else if (selectedLanguage == "ru") "Скопировано: ${item.cmd}"
                                                else "Copied: ${item.cmd}",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF1D1F21), RoundedCornerShape(6.dp))
                                                .border(0.5.dp, Color(0xFFE95420).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = item.cmd,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = Color(0xFF4AF626),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = when (selectedLanguage) {
                                                "uz" -> item.descUz
                                                "ru" -> item.descRu
                                                else -> item.descEn
                                            },
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = { showShortcutsDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                modifier = Modifier.glassCapsule()
                            ) {
                                Text(Translations.get("shortcuts_ok", selectedLanguage), color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAboutDialog && !isAppLocked) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        Dialog(
            onDismissRequest = { showAboutDialog = false }
        ) {
            MaterialTheme(colorScheme = currentThemeScheme) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = if (isLandscape) 4.dp else 24.dp)
                        .widthIn(max = 480.dp)
                        .then(if (isLandscape) Modifier.fillMaxHeight(0.92f) else Modifier.wrapContentHeight())
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = Translations.get("about_title", selectedLanguage),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(Translations.get("about_version", selectedLanguage), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            Text(Translations.get("about_desc", selectedLanguage), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(Translations.get("about_creator", selectedLanguage), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Button(
                                onClick = { showPrivacyDialog = true },
                                modifier = Modifier.fillMaxWidth().glassCapsule(),
                                shape = CircleShape,
                                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = Translations.get("btn_privacy", selectedLanguage),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = { showAboutDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                modifier = Modifier.glassCapsule()
                            ) {
                                Text(Translations.get("btn_close", selectedLanguage), color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPrivacyDialog && !isAppLocked) {
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        Dialog(
            onDismissRequest = { showPrivacyDialog = false }
        ) {
            MaterialTheme(colorScheme = currentThemeScheme) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = if (isLandscape) 8.dp else 24.dp)
                        .widthIn(max = 480.dp)
                        .wrapContentHeight()
                        .heightIn(max = if (isLandscape) 360.dp else 640.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = Translations.get("privacy_policy_title", selectedLanguage),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        val privacyScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .drawScrollbar(privacyScrollState)
                                .verticalScroll(privacyScrollState)
                                .padding(end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = Translations.get("privacy_policy_content", selectedLanguage),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            uriHandler.openUri("https://sites.google.com/view/bahodirov/home")
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Browser not found", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                    val btnOpenText = if (selectedLanguage == "uz") "Saytni ochish" else if (selectedLanguage == "ru") "Открыть сайт" else "Open URL"
                                    Text(btnOpenText, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }

                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString("https://sites.google.com/view/bahodirov/home"))
                                        val copiedMsg = if (selectedLanguage == "uz") "Havola nusxalandi!" else if (selectedLanguage == "ru") "Ссылка скопирована!" else "Link copied!"
                                        android.widget.Toast.makeText(context, copiedMsg, android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                    val btnCopyText = if (selectedLanguage == "uz") "Nusxalash" else if (selectedLanguage == "ru") "Копировать" else "Copy Link"
                                    Text(btnCopyText, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = { showPrivacyDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(Translations.get("btn_close", selectedLanguage), color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSigningDialog && !isAppLocked) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        Dialog(
            onDismissRequest = { showSigningDialog = false }
        ) {
            MaterialTheme(colorScheme = currentThemeScheme) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = if (isLandscape) 8.dp else 24.dp)
                        .widthIn(max = 480.dp)
                        .heightIn(max = if (isLandscape) 280.dp else 500.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = Translations.get("app_signing_title", selectedLanguage),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = Translations.get("app_signing_content", selectedLanguage),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = { showSigningDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(Translations.get("btn_close", selectedLanguage), color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    OneUiAmbientBackdrop(theme = terminalTheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = Color.White,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (isBottomBarVisible && !(showAiAssistantDialog && isAiAssistantFullScreen)) {
                    BottomNavBar(
                        selectedTab = activeMainView,
                        terminalTheme = terminalTheme,
                        selectedLanguage = selectedLanguage,
                        onTabSelected = { activeMainView = it }
                    )
                }
            }
        ) { innerPadding ->
            androidx.activity.compose.BackHandler(
                enabled = showSettingsDialog || showShortcutsDialog || showAboutDialog || showMainMenu || showOpenTabsScreen || showPrivacyDialog || showSigningDialog || showAiAssistantDialog || showConnectionManagerDialog || showAntiOverlayGuardDialog || showAppUpdateDialog || activeMainView != 0
            ) {
                if (showAppUpdateDialog) viewModel.showAppUpdateDialog.value = false
                else if (showSettingsDialog) showSettingsDialog = false
                else if (showConnectionManagerDialog) showConnectionManagerDialog = false
                else if (showAntiOverlayGuardDialog) showAntiOverlayGuardDialog = false
                else if (showAiAssistantDialog) {
                    showAiAssistantDialog = false
                    isAiAssistantFullScreen = false
                }
                else if (showShortcutsDialog) showShortcutsDialog = false
                else if (showAboutDialog) showAboutDialog = false
                else if (showMainMenu) showMainMenu = false
                else if (showOpenTabsScreen) showOpenTabsScreen = false
                else if (showPrivacyDialog) showPrivacyDialog = false
                else if (showSigningDialog) showSigningDialog = false
                else if (activeMainView != 0) activeMainView = 0
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (showAiAssistantDialog && isAiAssistantFullScreen) androidx.compose.foundation.layout.PaddingValues(0.dp) else innerPadding)
                    .background(Color.Transparent)
            ) {
                when (activeMainView) {
                    0 -> {
                        if (showOpenTabsScreen) {
                            TabManagerScreen(
                                tabs = tabs,
                                currentTabId = currentTabId,
                                tabSearchQuery = tabSearchQuery,
                                selectedLanguage = selectedLanguage,
                                terminalTheme = terminalTheme,
                                onSearchQueryChange = { tabSearchQuery = it },
                                onTabSelect = { id ->
                                    viewModel.switchToTab(id)
                                    showOpenTabsScreen = false
                                },
                                onTabClose = { id ->
                                    viewModel.closeTab(id)
                                },
                                onNewTabClick = {
                                    viewModel.addNewTab()
                                    showOpenTabsScreen = false
                                },
                                onTabRename = { id, name ->
                                    viewModel.renameTab(id, name)
                                },
                                onBackClick = {
                                    showOpenTabsScreen = false
                                }
                            )
                        } else {
                            TerminalScreen(
                                portInput = portInput,
                                isConnected = isConnected,
                                isConnecting = isConnecting,
                                connectionStatus = connectionStatus,
                                terminalOutput = terminalOutput,
                                commandInput = commandInput,
                                currentWorkingDirectory = currentWorkingDirectory,
                                terminalFontSize = terminalFontSize,
                                terminalTheme = terminalTheme,
                                selectedLanguage = selectedLanguage,
                                tabsCount = tabs.size,
                                tabsList = tabs,
                                currentTabId = currentTabId,
                                onTabSelect = { viewModel.switchToTab(it) },
                                onTabClose = { viewModel.closeTab(it) },
                                showMainMenu = showMainMenu,
                                isPythonActive = isPythonActive,
                                isAdbConnected = isConnected,
                                commandHistory = commandHistory,
                                savedScripts = savedScripts,
                                isTtsEnabled = isTtsEnabled,
                                isSpeaking = isSpeaking,
                                onTtsToggleClick = { viewModel.toggleTtsEnabled() },
                                onStopSpeaking = { viewModel.stopSpeaking() },
                                onPortChange = { viewModel.onPortChange(it) },
                                onConnectClick = { viewModel.connectAdb() },
                                onDisconnectClick = { viewModel.disconnectAdb() },
                                onCommandChange = { viewModel.onCommandChange(it) },
                                onSendCommand = { viewModel.executeTerminalCommand() },
                                onClearTerminal = { viewModel.clearTerminal() },
                                onSettingsClick = { showSettingsDialog = true },
                                onMainMenuToggle = { showMainMenu = !showMainMenu },
                                onShowOpenTabsClick = { showOpenTabsScreen = true },
                                onAddNewTabClick = { viewModel.addNewTab() },
                                onThemeChange = { theme -> viewModel.updateTerminalTheme(theme) },
                                onFontSizeChange = { size -> viewModel.updateTerminalFontSize(size) },
                                onShortcutsClick = { showShortcutsDialog = true },
                                onAboutClick = { showAboutDialog = true },
                                onCheckUpdatesClick = { viewModel.showAppUpdateDialog.value = true },
                                isImmersiveMode = isImmersiveMode,
                                onImmersiveToggle = { isImmersiveMode = !isImmersiveMode },
                                onTerminalColumnsChanged = { viewModel.updateTerminalColumns(it) },
                                isBottomBarVisible = isBottomBarVisible,
                                onBottomBarVisibleChange = { isBottomBarVisible = it },
                                onVoiceInputStart = { viewModel.setVoiceInputActive(true) },
                                onVoiceInputEnd = { viewModel.setVoiceInputActive(false) },
                                onAiClick = { showAiAssistantDialog = true },
                                onConnectionManagerClick = { showConnectionManagerDialog = true },
                                connectedDevicesCount = devices.count { it.status == com.example.adb.device.DeviceStatus.CONNECTED }
                            )
                        }
                    }
                    1 -> {
                        com.example.ui.AppManagerScreen(
                            viewModel = viewModel,
                            terminalTheme = terminalTheme,
                            selectedLanguage = selectedLanguage,
                            onBack = { activeMainView = 0 }
                        )
                    }
                    2 -> {
                        com.example.ui.ResourceMonitorScreen(
                            viewModel = viewModel,
                            terminalTheme = terminalTheme,
                            selectedLanguage = selectedLanguage,
                            onBack = { activeMainView = 0 }
                        )
                    }
                    3 -> {
                        com.example.ui.FileSystemBrowserScreen(
                            viewModel = viewModel,
                            terminalTheme = terminalTheme,
                            selectedLanguage = selectedLanguage,
                            onBack = { activeMainView = 0 }
                        )
                    }
                }

                if (isMatrixRainActive) {
                    MatrixRainOverlay(
                        onDismiss = { viewModel.stopMatrixRain() }
                    )
                }

                if (showAiAssistantDialog) {
                    com.example.ui.AiAssistantDialog(
                        viewModel = viewModel,
                        terminalTheme = terminalTheme,
                        selectedLanguage = selectedLanguage,
                        onDismiss = { 
                            showAiAssistantDialog = false 
                            isAiAssistantFullScreen = false
                        },
                        isAiFullScreen = isAiAssistantFullScreen,
                        onAiFullScreenChange = { isAiAssistantFullScreen = it },
                        onTriggerFilePicker = { callback ->
                            try {
                                val mainActivity = activityOverride as? MainActivity
                                if (mainActivity != null) {
                                    mainActivity.isSelectingFileForAi = true
                                    mainActivity.filePickerCallback = callback
                                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                        type = "*/*"
                                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                    }
                                    mainActivity.startActivityForResult(intent, 1002)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                throw e
                            }
                        },
                        onExecuteAction = { action, args ->
                            when (action) {
                                "disconnect_adb" -> {
                                    viewModel.disconnectAdb()
                                }
                                "clear_terminal" -> {
                                    viewModel.clearTerminal()
                                }
                                "toggle_fullscreen" -> {
                                    isImmersiveMode = !isImmersiveMode
                                }
                                "add_new_tab" -> {
                                    activeMainView = 0
                                    viewModel.addNewTab()
                                }
                                "open_tabs_screen" -> {
                                    activeMainView = 0
                                    showOpenTabsScreen = true
                                }
                                "toggle_main_menu" -> {
                                    activeMainView = 0
                                    showMainMenu = !showMainMenu
                                }
                                "run_terminal_command" -> {
                                    val cmd = args["command"] as? String ?: ""
                                    if (cmd.isNotEmpty()) {
                                        val assessment = com.example.security.CommandSecurityGateway.assessCommand(
                                            cmd,
                                            com.example.security.ExecutionOrigin.AI_TOOL
                                        )
                                        if (assessment.isBlockedByDefault) {
                                            viewModel.onCommandChange(cmd)
                                            android.widget.Toast.makeText(context, when (selectedLanguage) {
                                                "uz" -> "Diqqat: AI xavfli buyruq taklif qildi. Tasdiqlash uchun terminal maydoniga joylandi."
                                                "ru" -> "Внимание: Потенциально опасная команда загружена в терминал для проверки."
                                                else -> "Warning: Potentially destructive command from AI loaded into terminal for manual review."
                                            }, android.widget.Toast.LENGTH_LONG).show()
                                        } else {
                                            viewModel.executeTerminalCommand(cmd)
                                        }
                                    }
                                }
                                "trigger_voice_input" -> {
                                    viewModel.setVoiceInputActive(true)
                                }
                                "toggle_voice_playback" -> {
                                    viewModel.toggleTtsEnabled()
                                }
                                "share_output" -> {
                                    val exportIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Terminal Log Export")
                                        putExtra(Intent.EXTRA_TEXT, terminalOutput)
                                    }
                                    (context as? com.example.MainActivity)?.isBypassingLock = true
                                    context.startActivity(Intent.createChooser(exportIntent, "Export Logs"))
                                }
                                "press_virtual_key" -> {
                                    val keyLabel = args["key"] as? String ?: ""
                                    when (keyLabel) {
                                        "ESC" -> {
                                            viewModel.onCommandChange("")
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                        }
                                        "TAB" -> {
                                            val completed = performAutocomplete(commandInput, commandHistory, savedScripts)
                                            viewModel.onCommandChange(completed)
                                        }
                                        "CTRL" -> {
                                            viewModel.onCommandChange(commandInput + "^")
                                        }
                                        "ALT" -> {
                                            viewModel.onCommandChange(commandInput + "\\")
                                        }
                                        "^C" -> {
                                            viewModel.onCommandChange("")
                                        }
                                        "^D" -> {
                                            viewModel.executeTerminalCommand("exit")
                                        }
                                        "SPC" -> {
                                            viewModel.onCommandChange(commandInput + " ")
                                        }
                                        "|" -> {
                                            viewModel.onCommandChange(commandInput + " | ")
                                        }
                                        "&" -> {
                                            viewModel.onCommandChange(commandInput + " & ")
                                        }
                                        ">" -> {
                                            viewModel.onCommandChange(commandInput + " > ")
                                        }
                                        ">>" -> {
                                            viewModel.onCommandChange(commandInput + " >> ")
                                        }
                                        ";" -> {
                                            viewModel.onCommandChange(commandInput + " ; ")
                                        }
                                        "~" -> {
                                            viewModel.onCommandChange(commandInput + "~")
                                        }
                                        "/" -> {
                                            viewModel.onCommandChange(commandInput + "/")
                                        }
                                        "-" -> {
                                            viewModel.onCommandChange(commandInput + "-")
                                        }
                                        "CLSR" -> {
                                            viewModel.clearTerminal()
                                            viewModel.onCommandChange("")
                                        }
                                    }
                                }
                                "toggle_bottom_bar" -> {
                                    isBottomBarVisible = !isBottomBarVisible
                                }
                                "switch_view" -> {
                                    val view = (args["view"] as? Number)?.toInt() ?: 0
                                    activeMainView = view.coerceIn(0, 3)
                                }
                            }
                        }
                    )
                }
            }
        }

        if (showConnectionManagerDialog && !isAppLocked) {
            com.example.ui.ConnectionManagerDialog(
                viewModel = viewModel,
                terminalTheme = terminalTheme,
                selectedLanguage = selectedLanguage,
                onDismiss = { showConnectionManagerDialog = false }
            )
        }

        if (showAppUpdateDialog && !isAppLocked) {
            com.example.ui.AppUpdateDialog(
                terminalTheme = terminalTheme,
                selectedLanguage = selectedLanguage,
                onDismiss = { viewModel.showAppUpdateDialog.value = false }
            )
        }

        if (isAppLocked) {
            com.example.ui.LockScreenOverlay(
                terminalTheme = terminalTheme,
                selectedLanguage = selectedLanguage,
                onSuccess = { isAppLocked = false }
            )
        }
    }
}

fun android.content.Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun formatTerminalPath(path: String): String {
    if (path.isEmpty()) return "~"
    val trimmed = path.trim()
    return when {
        trimmed == "/sdcard/Home" ||
        trimmed == "/sdcard/Home/" ||
        trimmed == "/storage/emulated/0/Home" ||
        trimmed == "/storage/emulated/0/Home/" ||
        trimmed.endsWith("/files/home") ||
        trimmed.endsWith("/files/home/") -> "~"
        trimmed.startsWith("/sdcard/Home/") -> "~/" + trimmed.removePrefix("/sdcard/Home/")
        trimmed.startsWith("/storage/emulated/0/Home/") -> "~/" + trimmed.removePrefix("/storage/emulated/0/Home/")
        trimmed.startsWith("/data/user/0/") && trimmed.contains("/files/home/") -> "~/" + trimmed.substringAfter("/files/home/")
        trimmed.startsWith("/data/data/") && trimmed.contains("/files/home/") -> "~/" + trimmed.substringAfter("/files/home/")
        trimmed.startsWith("/sdcard/") -> "/" + trimmed.removePrefix("/sdcard/")
        trimmed.startsWith("/storage/emulated/0/") -> "/" + trimmed.removePrefix("/storage/emulated/0/")
        else -> trimmed
    }
}

class TerminalVisualTransformation(
    private val isPython: Boolean,
    private val isAdbConnected: Boolean = false,
    private val currentWorkingDirectory: String = "",
    private val isLight: Boolean = false
) : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val promptUserColor = if (isLight) Color(0xFF15803D) else Color(0xFF50FA7B)
        val promptSeparatorColor = if (isLight) Color(0xFF0F172A) else Color.White
        val promptTildeColor = if (isLight) Color(0xFF1D4ED8) else Color(0xFF729FCF)
        val promptPythonColor = if (isLight) Color(0xFFB45309) else Color(0xFFFFCC00)
        val promptCommandColor = if (isLight) Color(0xFFB45309) else Color(0xFFF1FA8C)

        val displayPath = formatTerminalPath(currentWorkingDirectory)

        val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
            if (isPython) {
                withStyle(androidx.compose.ui.text.SpanStyle(color = promptPythonColor, fontWeight = FontWeight.Bold)) {
                    append(">>> ")
                }
            } else {
                val userLabel = if (isAdbConnected) "adb@android" else "Super@user"
                withStyle(androidx.compose.ui.text.SpanStyle(color = promptUserColor, fontWeight = FontWeight.Bold)) {
                    append(userLabel)
                }
                withStyle(androidx.compose.ui.text.SpanStyle(color = promptSeparatorColor)) {
                    append(":")
                }
                withStyle(androidx.compose.ui.text.SpanStyle(color = promptTildeColor, fontWeight = FontWeight.Bold)) {
                    append(displayPath)
                }
                withStyle(androidx.compose.ui.text.SpanStyle(color = promptSeparatorColor)) {
                    append("$ ")
                }
            }
            withStyle(androidx.compose.ui.text.SpanStyle(color = promptCommandColor, fontWeight = FontWeight.Bold)) {
                append(text.text)
            }
        }

        val userLabel = if (isAdbConnected) "adb@android" else "Super@user"
        val promptPrefix = if (isPython) ">>> " else "$userLabel:$displayPath$ "
        val promptLength = promptPrefix.length

        val offsetMapping = object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return offset + promptLength
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset < promptLength) return 0
                return offset - promptLength
            }
        }

        return androidx.compose.ui.text.input.TransformedText(annotatedString, offsetMapping)
    }
}

val FULL_TERMINAL_COMMANDS: List<String> = listOf(
    // Terminal Core Package Manager & Linux Environments
    "pkg install", "pkg search", "pkg update", "pkg upgrade", "pkg uninstall", "pkg show", "pkg clean", "pkg list-all", "pkg list-installed", "pkg bootstrap",
    "apt install", "apt update", "apt search", "apt show", "apt remove",
    "kali login", "kali install", "kali test", "kali status", "proot install", "proot test",
    // Native Hardware & System Sensors API (Termux:API full parity)
    "terminal-toast", "terminal-vibrate", "terminal-torch on", "terminal-torch off", "terminal-battery-status",
    "terminal-clipboard-set", "terminal-clipboard-get", "terminal-tts-speak", "terminal-notification",
    "terminal-volume", "terminal-open-url",
    // Linux Coreutils & Shell Navigation
    "clear", "ls -la", "ls -l", "ls", "cd /sdcard", "pwd", "cat", "grep -i", "head -n 20", "tail -n 20", "wc -l",
    "mkdir -p", "rm -rf", "touch", "cp -r", "mv", "chmod 755", "chmod 644", "chown",
    "df -h", "free -m", "uptime", "whoami", "id", "uname -a", "date", "env", "export",
    "echo \$PATH", "find /sdcard/ -type f", "stat", "dmesg | tail -n 30",
    // Real Process & System Inspection (Linux Kernel & POSIX)
    "top -n 1", "top", "ps -ef", "ps -A", "ps", "threads", "procmem", "memmap", "kill -9", "kill",
    // Network Diagnostics & Sockets
    "ifconfig", "ip addr show", "ip route", "netstat -tlpn", "ping -c 4 8.8.8.8", "ping -c 4 1.1.1.1",
    // Android Package & Activity Manager
    "pm list packages", "pm list packages -3", "pm list packages -s", "pm list packages -d",
    "pm path", "pm clear", "pm dump",
    "am start -n", "am force-stop", "am restart", "am broadcast",
    // Android Dumpsys Diagnostics
    "dumpsys battery", "dumpsys cpuinfo", "dumpsys meminfo", "dumpsys diskstats",
    "dumpsys display", "dumpsys wifi", "dumpsys audio", "dumpsys power",
    // System Properties & Window Configuration
    "getprop", "getprop ro.product.model", "getprop ro.build.version.release", "getprop ro.serialno",
    "wm size", "wm density",
    // Input, Screen Capture & Media
    "screencap -p /sdcard/screenshot.png", "screenrecord --time-limit 10 /sdcard/rec.mp4",
    "input text", "input tap", "input swipe", "input keyevent 26", "input keyevent 3", "input keyevent 4",
    // Android Settings & System Services
    "settings get global", "settings get secure", "settings get system",
    "service list", "svc wifi enable", "svc wifi disable", "svc data enable",
    // ADB Core & Logging
    "adb devices", "adb tcpip 5555", "adb connect", "adb disconnect", "adb shell",
    "logcat -d -v time *:E", "logcat -d -v time *:W", "logcat -c"
)

fun performAutocomplete(
    currentInput: String,
    history: List<HistoryEntity> = emptyList(),
    savedScripts: List<ScriptEntity> = emptyList()
): String {
    val trimmed = currentInput.trim()
    if (trimmed.isEmpty()) return "ls -la "

    // 1. Check history for matches
    val historyMatch = history.map { it.command.trim() }.firstOrNull { it.startsWith(trimmed, ignoreCase = true) && it != trimmed }
    if (historyMatch != null) return historyMatch

    // 2. Check saved custom scripts
    val scriptMatch = savedScripts.map { it.command.trim() }.firstOrNull { it.startsWith(trimmed, ignoreCase = true) && it != trimmed }
    if (scriptMatch != null) return scriptMatch

    // 3. Check system and ADB commands
    val sysMatch = FULL_TERMINAL_COMMANDS.firstOrNull { it.startsWith(trimmed, ignoreCase = true) }
    if (sysMatch != null) return sysMatch

    return "$currentInput "
}

private val ANSI_REGEX = java.util.regex.Pattern.compile("\u001B\\[([0-9;]*)m")
private val PROMPT_REGEX = java.util.regex.Pattern.compile("^(\\s*)([a-zA-Z0-9._-]+@[a-zA-Z0-9._-]+):([^$#\\n]+)([$#])\\s*(.*)$")
private val PYTHON_PROMPT_REGEX = java.util.regex.Pattern.compile("^(\\s*)(>>>|\\.\\.\\.)\\s*(.*)$")

fun parseUbuntuText(text: String, textColor: Color, cols: Int = 55, isLight: Boolean = false): androidx.compose.ui.text.AnnotatedString {
    val promptUserColor = if (isLight) Color(0xFF15803D) else Color(0xFF50FA7B)
    val promptRootColor = if (isLight) Color(0xFFDC2626) else Color(0xFFFF5555)
    val promptSeparatorColor = if (isLight) Color(0xFF0F172A) else Color.White
    val promptTildeColor = if (isLight) Color(0xFF1D4ED8) else Color(0xFF729FCF)
    val promptCommandColor = if (isLight) Color(0xFFB45309) else Color(0xFFF1FA8C)
    val promptPythonColor = if (isLight) Color(0xFFB45309) else Color(0xFFFFCC00)

    val successColor = if (isLight) Color(0xFF16A34A) else Color(0xFF50FA7B)
    val errorColor = if (isLight) Color(0xFFDC2626) else Color(0xFFFF6E6E)
    val warnColor = if (isLight) Color(0xFFD97706) else Color(0xFFFFFFA5)
    val infoColor = if (isLight) Color(0xFF0284C7) else Color(0xFF8BE9FD)
    val headerColor = if (isLight) Color(0xFF7C3AED) else Color(0xFFBD93F9)

    return buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { i, rawLine ->
            if (i > 0) append("\n")
            
            // Adjust separators dynamically to prevent clipping or wrapping
            val line = if (rawLine.isEmpty()) {
                rawLine
            } else {
                val firstNonWhitespaceIndex = rawLine.indexOfFirst { !it.isWhitespace() }
                if (firstNonWhitespaceIndex == -1) {
                    rawLine
                } else {
                    val safeIdx = firstNonWhitespaceIndex.coerceIn(0, rawLine.length)
                    val leadingSpaces = rawLine.substring(0, safeIdx)
                    val trimmed = rawLine.substring(safeIdx)
                    if (trimmed.length >= 10 && trimmed.all { it == '=' }) {
                        leadingSpaces + "=".repeat((cols - leadingSpaces.length).coerceIn(10, 200))
                    } else if (trimmed.length >= 10 && trimmed.all { it == '-' }) {
                        leadingSpaces + "-".repeat((cols - leadingSpaces.length).coerceIn(10, 200))
                    } else {
                        rawLine
                    }
                }
            }
            
            val promptMatcher = PROMPT_REGEX.matcher(line)
            val pythonMatcher = PYTHON_PROMPT_REGEX.matcher(line)

            if (promptMatcher.matches()) {
                val leading = promptMatcher.group(1) ?: ""
                val userHost = promptMatcher.group(2) ?: ""
                val rawPath = promptMatcher.group(3) ?: ""
                val sym = promptMatcher.group(4) ?: "$"
                val cmdPart = promptMatcher.group(5) ?: ""

                if (leading.isNotEmpty()) append(leading)

                val userCol = if (userHost.startsWith("root")) promptRootColor else promptUserColor
                withStyle(SpanStyle(color = userCol, fontWeight = FontWeight.Bold)) {
                    append(userHost)
                }
                withStyle(SpanStyle(color = promptSeparatorColor)) {
                    append(":")
                }
                val dispPath = formatTerminalPath(rawPath)
                withStyle(SpanStyle(color = promptTildeColor, fontWeight = FontWeight.Bold)) {
                    append(dispPath)
                }
                val symCol = if (sym == "#") promptRootColor else promptSeparatorColor
                withStyle(SpanStyle(color = symCol, fontWeight = FontWeight.Bold)) {
                    append(sym)
                }
                append(" ")
                if (cmdPart.isNotEmpty()) {
                    withStyle(SpanStyle(color = promptCommandColor, fontWeight = FontWeight.Bold)) {
                        append(cmdPart)
                    }
                }
            } else if (pythonMatcher.matches()) {
                val leading = pythonMatcher.group(1) ?: ""
                val sym = pythonMatcher.group(2) ?: ">>>"
                val code = pythonMatcher.group(3) ?: ""

                if (leading.isNotEmpty()) append(leading)
                withStyle(SpanStyle(color = promptPythonColor, fontWeight = FontWeight.Bold)) {
                    append(sym)
                }
                append(" ")
                if (code.isNotEmpty()) {
                    withStyle(SpanStyle(color = promptCommandColor, fontWeight = FontWeight.Bold)) {
                        append(code)
                    }
                }
            } else if (line.contains("\u001B[")) {
                // High-fidelity ANSI Escape Code parser for Linux terminals
                val matcher = ANSI_REGEX.matcher(line)
                var lastEnd = 0
                var currentColor = textColor
                var isBold = false

                while (matcher.find()) {
                    if (matcher.start() > lastEnd) {
                        val seg = line.substring(lastEnd, matcher.start())
                        withStyle(SpanStyle(color = currentColor, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)) {
                            append(seg)
                        }
                    }
                    val codeStr = matcher.group(1) ?: ""
                    if (codeStr.isEmpty() || codeStr == "0") {
                        currentColor = textColor
                        isBold = false
                    } else {
                        val codes = codeStr.split(";").mapNotNull { it.toIntOrNull() }
                        for (c in codes) {
                            when (c) {
                                0 -> { currentColor = textColor; isBold = false }
                                1 -> isBold = true
                                30 -> currentColor = if (isLight) Color(0xFF0F172A) else Color(0xFF555555)
                                31 -> currentColor = if (isLight) Color(0xFFDC2626) else Color(0xFFFF5555)
                                32 -> currentColor = if (isLight) Color(0xFF16A34A) else Color(0xFF50FA7B)
                                33 -> currentColor = if (isLight) Color(0xFFD97706) else Color(0xFFF1FA8C)
                                34 -> currentColor = if (isLight) Color(0xFF2563EB) else Color(0xFFBD93F9)
                                35 -> currentColor = if (isLight) Color(0xFFC026D3) else Color(0xFFFF79C6)
                                36 -> currentColor = if (isLight) Color(0xFF0891B2) else Color(0xFF8BE9FD)
                                37 -> currentColor = if (isLight) Color(0xFF1E293B) else Color(0xFFF8F8F2)
                                90 -> currentColor = if (isLight) Color(0xFF475569) else Color(0xFF6272A4)
                                91 -> currentColor = if (isLight) Color(0xFFEF4444) else Color(0xFFFF6E6E)
                                92 -> currentColor = if (isLight) Color(0xFF22C55E) else Color(0xFF69FF94)
                                93 -> currentColor = if (isLight) Color(0xFFB45309) else Color(0xFFFFFFA5)
                                94 -> currentColor = if (isLight) Color(0xFF3B82F6) else Color(0xFFD6ACFF)
                                95 -> currentColor = if (isLight) Color(0xFFE11D48) else Color(0xFFFF92DF)
                                96 -> currentColor = if (isLight) Color(0xFF0284C7) else Color(0xFFA4FFFF)
                                97 -> currentColor = if (isLight) Color(0xFF0F172A) else Color.White
                            }
                        }
                    }
                    lastEnd = matcher.end()
                }
                if (lastEnd < line.length) {
                    val remainder = line.substring(lastEnd)
                    withStyle(SpanStyle(color = currentColor, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)) {
                        append(remainder)
                    }
                }
            } else {
                val trimmed = line.trimStart()
                when {
                    trimmed.startsWith("[+]") || trimmed.startsWith("✔") || trimmed.startsWith("[SUCCESS]") || trimmed.startsWith("Success") || trimmed.startsWith("🚀") -> {
                        withStyle(SpanStyle(color = successColor, fontWeight = FontWeight.SemiBold)) {
                            append(line)
                        }
                    }
                    trimmed.startsWith("[-]") || trimmed.startsWith("❌") || trimmed.startsWith("[ERROR]") || trimmed.startsWith("error:") || trimmed.startsWith("Error:") || trimmed.startsWith("FAILED") || trimmed.startsWith("cd: ") || trimmed.startsWith("Permission denied") || trimmed.contains("No such file or directory") -> {
                        withStyle(SpanStyle(color = errorColor, fontWeight = FontWeight.SemiBold)) {
                            append(line)
                        }
                    }
                    trimmed.startsWith("[!]") || trimmed.startsWith("⚠") || trimmed.startsWith("[WARN]") || trimmed.startsWith("Warning:") || trimmed.startsWith("warning:") -> {
                        withStyle(SpanStyle(color = warnColor, fontWeight = FontWeight.Normal)) {
                            append(line)
                        }
                    }
                    trimmed.startsWith("[*]") || trimmed.startsWith("ℹ") || trimmed.startsWith("[INFO]") || trimmed.startsWith("Info:") || trimmed.startsWith("📊") -> {
                        withStyle(SpanStyle(color = infoColor, fontWeight = FontWeight.Normal)) {
                            append(line)
                        }
                    }
                    trimmed.startsWith("===") || trimmed.startsWith("---") || trimmed.startsWith("════") -> {
                        withStyle(SpanStyle(color = headerColor, fontWeight = FontWeight.Bold)) {
                            append(line)
                        }
                    }
                    else -> {
                        append(line)
                    }
                }
            }
        }
    }
}

fun localizeTabTitle(title: String, lang: String): String {
    if (title.startsWith("Oyna ")) {
        val num = title.substringAfter("Oyna ")
        return when (lang) {
            "en" -> "Window $num"
            "ru" -> "Окно $num"
            else -> "Oyna $num"
        }
    }
    return title
}

@Composable
fun TabManagerScreen(
    tabs: List<com.example.viewmodel.TerminalTab>,
    currentTabId: String,
    tabSearchQuery: String,
    selectedLanguage: String,
    terminalTheme: String,
    onSearchQueryChange: (String) -> Unit,
    onTabSelect: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTabClick: () -> Unit,
    onTabRename: (String, String) -> Unit = { _, _ -> },
    onBackClick: () -> Unit
) {
    var renamingTabId by remember { mutableStateOf<String?>(null) }
    var renameTitleInput by remember { mutableStateOf("") }

    val filteredTabs = tabs.filter {
        val localizedTitle = localizeTabTitle(it.title, selectedLanguage)
        localizedTitle.contains(tabSearchQuery, ignoreCase = true) ||
        it.terminalOutput.contains(tabSearchQuery, ignoreCase = true)
    }

    // Dynamic colors based on terminalTheme with Glassmorphism transparency
    val themeBg = when (terminalTheme) {
        "ubuntu" -> Color(0xFF11000B).copy(alpha = 0.5f)
        "matrix" -> Color(0xFF000500).copy(alpha = 0.5f)
        "cyberpunk" -> Color(0xFF10041C).copy(alpha = 0.5f)
        "monochrome" -> Color(0xFF000000).copy(alpha = 0.5f)
        "light" -> Color(0xFFF8FAFC).copy(alpha = 0.5f)
        else -> Color(0xFF11000B).copy(alpha = 0.5f)
    }
    val themeAccent = when (terminalTheme) {
        "ubuntu" -> Color(0xFFE95420)
        "matrix" -> Color(0xFF33FF33)
        "cyberpunk" -> Color(0xFFFF007F)
        "monochrome" -> Color.White
        "light" -> Color(0xFF1E293B)
        else -> Color(0xFFE95420)
    }
    val themeActiveCard = when (terminalTheme) {
        "ubuntu" -> Color(0xFF381426).copy(alpha = 0.4f)
        "matrix" -> Color(0xFF002200).copy(alpha = 0.4f)
        "cyberpunk" -> Color(0xFF2A0D3E).copy(alpha = 0.4f)
        "monochrome" -> Color(0xFF222222).copy(alpha = 0.4f)
        "light" -> Color(0xFFE2E8F0).copy(alpha = 0.4f)
        else -> Color(0xFF381426).copy(alpha = 0.4f)
    }
    val themeInactiveCard = when (terminalTheme) {
        "ubuntu" -> Color(0xFF241C1A).copy(alpha = 0.2f)
        "matrix" -> Color(0xFF001100).copy(alpha = 0.2f)
        "cyberpunk" -> Color(0xFF1D0E2B).copy(alpha = 0.2f)
        "monochrome" -> Color(0xFF111111).copy(alpha = 0.2f)
        "light" -> Color(0xFFF1F5F9).copy(alpha = 0.2f)
        else -> Color(0xFF241C1A).copy(alpha = 0.2f)
    }
    val themeText = when (terminalTheme) {
        "light" -> Color(0xFF1E293B)
        else -> Color.White
    }
    val themeSearchFocusedContainer = when (terminalTheme) {
        "ubuntu" -> Color(0xFF2C001E)
        "matrix" -> Color(0xFF001800)
        "cyberpunk" -> Color(0xFF1E0A2D)
        "monochrome" -> Color(0xFF1A1A1A)
        "light" -> Color(0xFFE2E8F0)
        else -> Color(0xFF2C001E)
    }
    val themeSearchUnfocusedContainer = when (terminalTheme) {
        "ubuntu" -> Color(0xFF200015)
        "matrix" -> Color(0xFF000C00)
        "cyberpunk" -> Color(0xFF13051C)
        "monochrome" -> Color(0xFF0D0D0D)
        "light" -> Color(0xFFF1F5F9)
        else -> Color(0xFF200015)
    }

    // Tab Rename Dialog
    if (renamingTabId != null) {
        AlertDialog(
            onDismissRequest = { renamingTabId = null },
            title = {
                Text(
                    text = when (selectedLanguage) {
                        "uz" -> "Tab nomini o'zgartirish"
                        "ru" -> "Переименовать вкладку"
                        else -> "Rename Tab"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                OutlinedTextField(
                    value = renameTitleInput,
                    onValueChange = { renameTitleInput = it },
                    singleLine = true,
                    placeholder = { Text("Tab nomi...") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = renamingTabId
                        if (id != null && renameTitleInput.isNotBlank()) {
                            onTabRename(id, renameTitleInput.trim())
                        }
                        renamingTabId = null
                    }
                ) {
                    Text("OK", fontWeight = FontWeight.Bold, color = themeAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingTabId = null }) {
                    Text(
                        text = when (selectedLanguage) {
                            "uz" -> "Bekor qilish"
                            "ru" -> "Отмена"
                            else -> "Cancel"
                        }
                    )
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBg)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onBackClick() }
            .padding(16.dp)
    ) {
        // Top Part: Back button, count, and New Tab (+) button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = themeText)
            }

            // Tabs count centered text
            Text(
                text = "${tabs.size} ${Translations.get("tabs_count_label", selectedLanguage)}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = themeText,
                fontFamily = FontFamily.Monospace
            )

            // Top Quick Add Button
            IconButton(
                onClick = onNewTabClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(themeAccent.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Tab",
                    tint = themeAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar inside open tabs
        OutlinedTextField(
            value = tabSearchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(Translations.get("search_tabs_placeholder", selectedLanguage), color = themeText.copy(alpha = 0.5f)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = CircleShape,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = themeText.copy(alpha = 0.5f)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = themeSearchFocusedContainer,
                unfocusedContainerColor = themeSearchUnfocusedContainer,
                focusedBorderColor = themeAccent,
                unfocusedBorderColor = themeText.copy(alpha = 0.2f),
                focusedTextColor = themeText,
                unfocusedTextColor = themeText
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Grid contents
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { onBackClick() },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredTabs, key = { it.id }) { tab ->
                val isActive = tab.id == currentTabId
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(155.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) themeActiveCard else themeInactiveCard)
                        .border(
                            width = if (isActive) 1.5.dp else 0.5.dp,
                            color = if (isActive) themeAccent else themeText.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onTabSelect(tab.id) }
                        .padding(10.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Title row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = localizeTabTitle(tab.title, selectedLanguage),
                                    color = if (isActive) themeAccent else themeText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (tab.isPythonActive) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "PY",
                                        color = Color(0xFFFFD43B),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Rename button
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(themeText.copy(alpha = 0.08f))
                                    .clickable {
                                        renameTitleInput = tab.title
                                        renamingTabId = tab.id
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Rename",
                                    tint = themeText.copy(alpha = 0.7f),
                                    modifier = Modifier.size(11.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Close button
                            if (tabs.size > 1) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(themeText.copy(alpha = 0.1f))
                                        .clickable { onTabClose(tab.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Yopish",
                                        tint = themeText,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        // Path / Directory snippet
                        val dirName = if (tab.workingDir.isNotEmpty()) {
                            tab.workingDir.substringAfterLast("/").ifEmpty { "home" }
                        } else {
                            "home"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = "📁 $dirName",
                                color = if (isActive) themeAccent.copy(alpha = 0.9f) else themeText.copy(alpha = 0.45f),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isActive) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "● Active",
                                    color = themeAccent,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Output preview snippet
                        val snippetLines = tab.terminalOutput.split("\n")
                        val showSnippet = snippetLines.takeLast(4).joinToString("\n")
                        Text(
                            text = showSnippet,
                            color = themeText.copy(alpha = 0.6f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
        }

        // Bottom Add New Tab Button with theme-aware contrast
        val isMonochrome = terminalTheme == "monochrome"
        val btnContainerColor = when {
            isMonochrome -> Color(0xFF1E1E1E)
            terminalTheme == "light" -> Color(0xFF1E293B)
            terminalTheme == "matrix" -> Color(0xFF0D280D)
            else -> themeAccent
        }
        val btnContentColor = when {
            isMonochrome -> Color.White
            terminalTheme == "matrix" -> Color(0xFF33FF33)
            else -> Color.White
        }
        val btnBorder = when {
            isMonochrome -> BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
            terminalTheme == "matrix" -> BorderStroke(1.dp, Color(0xFF33FF33).copy(alpha = 0.6f))
            else -> null
        }

        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onNewTabClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = btnContainerColor,
                contentColor = btnContentColor
            ),
            border = btnBorder,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = btnContentColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = Translations.get("menu_new_tab", selectedLanguage),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = btnContentColor
            )
        }
    }
}

@Composable
fun TerminalScreen(
    portInput: String,
    isConnected: Boolean,
    isConnecting: Boolean,
    connectionStatus: String,
    terminalOutput: String,
    commandInput: String,
    currentWorkingDirectory: String = "",
    terminalFontSize: Int,
    terminalTheme: String,
    selectedLanguage: String,
    tabsCount: Int,
    showMainMenu: Boolean,
    isPythonActive: Boolean = false,
    isAdbConnected: Boolean = false,
    commandHistory: List<HistoryEntity> = emptyList(),
    savedScripts: List<ScriptEntity> = emptyList(),
    isTtsEnabled: Boolean = false,
    isSpeaking: Boolean = false,
    onTtsToggleClick: () -> Unit = {},
    onStopSpeaking: () -> Unit = {},
    onPortChange: (String) -> Unit,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onCommandChange: (String) -> Unit,
    onSendCommand: () -> Unit,
    onClearTerminal: () -> Unit,
    onSettingsClick: () -> Unit,
    onMainMenuToggle: () -> Unit,
    onShowOpenTabsClick: () -> Unit,
    onAddNewTabClick: () -> Unit,
    onThemeChange: (String) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onShortcutsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onCheckUpdatesClick: () -> Unit = {},
    isImmersiveMode: Boolean,
    onImmersiveToggle: () -> Unit,
    onTerminalColumnsChanged: (Int) -> Unit = {},
    isBottomBarVisible: Boolean = true,
    onBottomBarVisibleChange: (Boolean) -> Unit = {},
    onVoiceInputStart: () -> Unit = {},
    onVoiceInputEnd: () -> Unit = {},
    onAiClick: () -> Unit = {},
    onConnectionManagerClick: () -> Unit = {},
    connectedDevicesCount: Int = 0,
    tabsList: List<com.example.viewmodel.TerminalTab> = emptyList(),
    currentTabId: String = "",
    onTabSelect: (String) -> Unit = {},
    onTabClose: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val speechLang = when (selectedLanguage) {
                "uz" -> "uz-UZ"
                "ru" -> "ru-RU"
                else -> "en-US"
            }
            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, speechLang)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, speechLang)
                putExtra(android.speech.RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, speechLang)
                putExtra(android.speech.RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf(speechLang))
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, if (selectedLanguage == "uz") "Gapiring..." else if (selectedLanguage == "ru") "Говорите..." else "Speak...")
            }
            try {
                if (activity != null) {
                    activity.startActivityForResult(intent, 1001)
                } else {
                    throw IllegalStateException("Activity not found")
                }
            } catch (e: Throwable) {
                val errText = when (selectedLanguage) {
                    "uz" -> "Qurilmada ovozli qidiruv xizmati topilmadi: ${e.message}"
                    "ru" -> "Служба распознавания речи не найдена: ${e.message}"
                    else -> "Speech recognition service not found: ${e.message}"
                }
                android.widget.Toast.makeText(context, errText, android.widget.Toast.LENGTH_LONG).show()
                onVoiceInputEnd()
            }
        } else {
            val errMsg = when (selectedLanguage) {
                "uz" -> "Mikrofon ruxsati berilmadi. Ovoz orqali yozish uchun ruxsat bering."
                "ru" -> "Разрешение на доступ к микрофону отклонено."
                else -> "Microphone permission denied."
            }
            android.widget.Toast.makeText(context, errMsg, android.widget.Toast.LENGTH_LONG).show()
            onVoiceInputEnd()
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var textFieldValueState by remember {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(text = commandInput, selection = androidx.compose.ui.text.TextRange(commandInput.length)))
    }

    LaunchedEffect(commandInput) {
        if (textFieldValueState.text != commandInput) {
            textFieldValueState = androidx.compose.ui.text.input.TextFieldValue(
                text = commandInput,
                selection = androidx.compose.ui.text.TextRange(commandInput.length)
            )
        }
    }

    LaunchedEffect(isLandscape) {
        if (isLandscape) {
            onFontSizeChange(12)
        } else {
            onFontSizeChange(9)
        }
    }

    // Terminal Themes style dictionary
    val (textColor, bgColor, borderColor) = when (terminalTheme) {
        "ubuntu" -> Triple(Color(0xFFFFFFFF), Color(0xFF2C001E), Color(0xFFE95420))
        "matrix" -> Triple(Color(0xFF33FF33), Color(0xFF001100), Color(0xFF004400))
        "cyberpunk" -> Triple(Color(0xFFFF007F), Color(0xFF120422), Color(0xFF7E00FF))
        "monochrome" -> Triple(Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFF333333))
        "light" -> Triple(Color(0xFF1E293B), Color(0xFFF1F5F9), Color(0xFFCBD5E1))
        else -> Triple(Color(0xFFFFFFFF), Color(0xFF2C001E), Color(0xFFE95420))
    }

    val terminalScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val hasTrain = terminalOutput.contains("═╦═") || 
            terminalOutput.contains("Tutuq-tutuq! Bug'") || 
            terminalOutput.contains("Чу-чу! Паровоз") || 
            terminalOutput.contains("Steam locomotive is passing")

    LaunchedEffect(terminalOutput) {
        terminalScrollState.scrollTo(terminalScrollState.maxValue)
    }

    // Cursor visibility blinking
    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            cursorVisible = !cursorVisible
        }
    }

    var historyIndex by remember { mutableStateOf(-1) }
    LaunchedEffect(commandInput) {
        val filteredHistory = commandHistory.filter { it.command.isNotBlank() }
        val currentIsHistory = filteredHistory.any { it.command == commandInput }
        if (!currentIsHistory) {
            historyIndex = -1
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .oneUiGlassCard(
                    shape = RoundedCornerShape(24.dp),
                    backgroundColor = bgColor.copy(alpha = 0.65f),
                    borderColor = borderColor.copy(alpha = 0.45f),
                    borderWidth = 1.5.dp
                )
        ) {
            // Header: Frosted Glass Window Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.04f)
                            )
                        )
                    )
                    .border(
                        BorderStroke(
                            0.5.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.28f), Color.Transparent)
                            )
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // Left Part: Circle lights AND + icon button for New Tab AND Search Magnifier for open tabs
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Red, Yellow, Green Window Controls with slim, professional gaps
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Red Window Button -> Disconnect active ADB
                        Box(
                            modifier = Modifier
                                .size(width = 20.dp, height = 32.dp)
                                .clickable { onDisconnectClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.size(12.dp).background(Color(0xFFFF5F56), CircleShape))
                        }

                        // Yellow Window Button -> Clear active terminal log
                        Box(
                            modifier = Modifier
                                .size(width = 20.dp, height = 32.dp)
                                .clickable { onClearTerminal() },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.size(12.dp).background(Color(0xFFFFBD2E), CircleShape))
                        }

                        // Green Window Button -> Toggle Fullscreen / Immersive
                        Box(
                            modifier = Modifier
                                .size(width = 20.dp, height = 32.dp)
                                .clickable { onImmersiveToggle() },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.size(12.dp).background(Color(0xFF27C93F), CircleShape))
                        }
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // + icon button (New Tab)
                    IconButton(
                        onClick = onAddNewTabClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Tab",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Center Title: displaying exclusively "Ubuntu" (strictly centered)
                Text(
                    text = "Ubuntu",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )

                // Right Part: Tab grid (4 squares) and Main Menu ellipses
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // AI Sparkle Button (Glowing/Stylish sparkle)
                    IconButton(
                        onClick = onAiClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Assistant",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 4-squares grid icon (`Show Open Tabs` button) with badge
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(
                                onClick = onShowOpenTabsClick,
                                role = androidx.compose.ui.semantics.Role.Button
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Show Open Tabs",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        if (tabsCount > 1) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 1.dp, end = 1.dp)
                                    .size(14.dp)
                                    .background(borderColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (tabsCount > 9) "9+" else "$tabsCount",
                                    color = Color.White,
                                    fontSize = 8.5.sp,
                                    lineHeight = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    style = androidx.compose.ui.text.TextStyle(
                                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                            includeFontPadding = false
                                        )
                                    )
                                )
                            }
                        }
                    }

                    // Triple dots menu icon called "Mani Meni"
                    IconButton(
                        onClick = onMainMenuToggle,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Main Menu",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Multi-tab quick switcher strip
            if (tabsList.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabsList.forEach { tab ->
                        val isCurrent = tab.id == currentTabId
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isCurrent) borderColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, if (isCurrent) borderColor else Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onTabSelect(tab.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (tab.isPythonActive) {
                                    Text(
                                        text = "PY",
                                        color = Color(0xFFFFD43B),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = localizeTabTitle(tab.title, selectedLanguage),
                                    color = if (isCurrent) textColor else textColor.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                                if (tabsList.size > 1) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = textColor.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable { onTabClose(tab.id) }
                                    )
                                }
                            }
                        }
                    }

                    // Add Tab Button on strip
                    IconButton(
                        onClick = onAddNewTabClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Tab",
                            tint = borderColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // CLI log console output (takes up full height)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 8.dp)
                    .clipToBounds()
                    .verticalFadingEdge(scrollState = terminalScrollState, edgeSize = 42.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .verticalScroll(terminalScrollState)
                ) {
                    // Dynamic terminal columns calculation to prevent word wrapping of separator lines
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
                    val screenWidthDp = configuration.screenWidthDp
                    val sidePadding = 56
                    val netWidthDp = (screenWidthDp - sidePadding).coerceAtLeast(100)
                    val charWidthDp = terminalFontSize * fontScale * 0.63f
                    val cols = (netWidthDp / charWidthDp).toInt().coerceIn(10, 120)
                    LaunchedEffect(cols) {
                        onTerminalColumnsChanged(cols)
                    }

                    // Selection selection enabled outputs
                    if (terminalOutput.isNotEmpty()) {
                        val parsedOutput = remember(terminalOutput, textColor, cols, terminalTheme) {
                            parseUbuntuText(terminalOutput, textColor, cols, isLight = terminalTheme == "light")
                        }
                        SelectionContainer {
                            Text(
                                text = parsedOutput,
                                fontFamily = FontFamily.Monospace,
                                fontSize = terminalFontSize.sp,
                                color = textColor,
                                lineHeight = (terminalFontSize + 4).sp,
                                softWrap = !hasTrain
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Active Inline keyboard entry terminal row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            BasicTextField(
                                value = textFieldValueState,
                                onValueChange = { newValue ->
                                    textFieldValueState = newValue
                                    if (commandInput != newValue.text) {
                                        onCommandChange(newValue.text)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .testTag("command_input"),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = terminalFontSize.sp,
                                    color = textColor
                                ),
                                cursorBrush = SolidColor(textColor),
                                visualTransformation = remember(isPythonActive, isAdbConnected, currentWorkingDirectory, terminalTheme) {
                                    TerminalVisualTransformation(isPythonActive, isAdbConnected, currentWorkingDirectory, isLight = terminalTheme == "light")
                                },
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Send
                                ),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (commandInput.isNotBlank()) {
                                            onSendCommand()
                                            coroutineScope.launch {
                                                terminalScrollState.scrollTo(terminalScrollState.maxValue)
                                            }
                                        }
                                    }
                                )
                            )
                        }

                        // Microphone Voice Input
                        IconButton(
                            onClick = {
                                try {
                                    onStopSpeaking()
                                } catch (e: Exception) {}
                                onVoiceInputStart()
                                
                                val hasRecordPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                
                                if (hasRecordPermission) {
                                    val speechLang = when (selectedLanguage) {
                                        "uz" -> "uz-UZ"
                                        "ru" -> "ru-RU"
                                        else -> "en-US"
                                    }
                                    val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, speechLang)
                                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, speechLang)
                                        putExtra(android.speech.RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, speechLang)
                                        putExtra(android.speech.RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf(speechLang))
                                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, if (selectedLanguage == "uz") "Gapiring..." else if (selectedLanguage == "ru") "Говорите..." else "Speak...")
                                    }
                                    try {
                                        if (activity != null) {
                                            activity.startActivityForResult(intent, 1001)
                                        } else {
                                            throw IllegalStateException("Activity not found")
                                        }
                                    } catch (e: Throwable) {
                                        val errText = when (selectedLanguage) {
                                            "uz" -> "Qurilmada ovozli qidiruv xizmati topilmadi: ${e.message}"
                                            "ru" -> "Служба распознавания речи не найдена: ${e.message}"
                                            else -> "Speech recognition service not found on device: ${e.message}"
                                        }
                                        android.widget.Toast.makeText(context, errText, android.widget.Toast.LENGTH_LONG).show()
                                        onVoiceInputEnd()
                                    }
                                } else {
                                    try {
                                        recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    } catch (e: Throwable) {
                                        val errText = when (selectedLanguage) {
                                            "uz" -> "Ruxsat so'rashda xatolik: ${e.message}"
                                            "ru" -> "Ошибка запроса разрешения: ${e.message}"
                                            else -> "Error requesting permission: ${e.message}"
                                        }
                                        android.widget.Toast.makeText(context, errText, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice input", tint = if (terminalTheme == "light") Color(0xFF0F172A) else textColor.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                        }

                        // Text-To-Speech (Audio Output) Toggle
                        val ttsScale = if (isSpeaking) {
                            val infiniteTransition = rememberInfiniteTransition()
                            val scale by infiniteTransition.animateFloat(
                                initialValue = 1.0f,
                                targetValue = 1.3f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(400, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )
                            scale
                        } else {
                            1.0f
                        }

                        IconButton(
                            onClick = onTtsToggleClick,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Default.RecordVoiceOver else if (isTtsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = "Toggle TTS audio reading",
                                tint = if (isSpeaking) {
                                    if (terminalTheme == "matrix") Color(0xFF00FF00) else Color(0xFF4CAF50)
                                } else if (isTtsEnabled) {
                                    textColor
                                } else {
                                    if (terminalTheme == "light") Color(0xFF64748B) else textColor.copy(alpha = 0.5f)
                                },
                                modifier = Modifier.size((18 * ttsScale).dp)
                            )
                        }

                        // Export log
                        IconButton(
                            onClick = {
                                val exportIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Terminal Log Export")
                                    putExtra(Intent.EXTRA_TEXT, terminalOutput)
                                }
                                (context as? com.example.MainActivity)?.isBypassingLock = true
                                context.startActivity(Intent.createChooser(exportIntent, "Export Logs"))
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Export logs", tint = if (terminalTheme == "light") Color(0xFF0F172A) else textColor.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val keyboardBg = if (terminalTheme == "light") Color(0xFFE2E8F0) else Color(0xFF1E1E1E)
            val keyBg = if (terminalTheme == "light") Color(0xFFF1F5F9) else Color(0xFF2D2D2D)
            val keyBorder = if (terminalTheme == "light") Color(0xFFCBD5E1) else Color(0xFF444444)
            val keyTextColor = if (terminalTheme == "light") Color(0xFF1E293B) else Color.White
            val keyArrowColor = when (terminalTheme) {
                "ubuntu" -> Color(0xFFE95420)
                "matrix" -> Color(0xFF33FF33)
                "cyberpunk" -> Color(0xFFFF007F)
                "monochrome" -> Color.White
                "light" -> Color(0xFF1E293B)
                else -> Color(0xFFE95420)
            }

            // Premium on-screen terminal utility keybar matching active theme and style
            val keysList = listOf("ESC", "TAB", "CTRL", "ALT", "↑", "↓", "←", "→", "HOME", "END", "PGUP", "PGDN", "^C", "^D", "SPC", "|", "&", ">", ">>", ";", "/", "-", "_", "~", "$", "=", "+", "\\", "CLSR")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.08f),
                                Color.Black.copy(alpha = 0.25f)
                            )
                        )
                    )
                    .border(
                        BorderStroke(
                            0.5.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.20f), Color.Transparent)
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 44.dp, top = 6.dp, bottom = 6.dp)
                        .let { 
                            if (isLandscape) it else it.horizontalScroll(rememberScrollState())
                        },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    keysList.forEach { keyLabel ->
                        Box(
                            modifier = Modifier
                                .let { 
                                    if (isLandscape) it.weight(1f) else it 
                                }
                                .oneUiGlassCapsule(
                                    backgroundColor = if (keyLabel == "CLSR" || keyLabel == "ESC") keyArrowColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
                                    accentColor = keyArrowColor.copy(alpha = 0.40f)
                                )
                                .clickable {
                                    when (keyLabel) {
                                        "ESC" -> {
                                            onCommandChange("")
                                            historyIndex = -1
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                        }
                                        "TAB" -> {
                                            val completed = performAutocomplete(commandInput, commandHistory, savedScripts)
                                            onCommandChange(completed)
                                        }
                                        "CTRL" -> {
                                            onCommandChange(commandInput + "^")
                                        }
                                        "ALT" -> {
                                            onCommandChange(commandInput + "\\")
                                        }
                                        "^C" -> {
                                            onCommandChange("")
                                            historyIndex = -1
                                        }
                                        "^D" -> {
                                            onCommandChange("exit")
                                            onSendCommand()
                                        }
                                        "SPC" -> {
                                            onCommandChange(commandInput + " ")
                                        }
                                        "|" -> {
                                            onCommandChange(commandInput + " | ")
                                        }
                                        "&" -> {
                                            onCommandChange(commandInput + " & ")
                                        }
                                        ">" -> {
                                            onCommandChange(commandInput + " > ")
                                        }
                                        ">>" -> {
                                            onCommandChange(commandInput + " >> ")
                                        }
                                        ";" -> {
                                            onCommandChange(commandInput + " ; ")
                                        }
                                        "~" -> {
                                            onCommandChange(commandInput + "~")
                                        }
                                        "/" -> {
                                            onCommandChange(commandInput + "/")
                                        }
                                        "-" -> {
                                            onCommandChange(commandInput + "-")
                                        }
                                        "_" -> {
                                            onCommandChange(commandInput + "_")
                                        }
                                        "$" -> {
                                            onCommandChange(commandInput + "$")
                                        }
                                        "=" -> {
                                            onCommandChange(commandInput + "=")
                                        }
                                        "+" -> {
                                            onCommandChange(commandInput + "+")
                                        }
                                        "\\" -> {
                                            onCommandChange(commandInput + "\\")
                                        }
                                        "←" -> {
                                            val cur = textFieldValueState.selection.start
                                            if (cur > 0) {
                                                textFieldValueState = textFieldValueState.copy(selection = androidx.compose.ui.text.TextRange(cur - 1))
                                            }
                                        }
                                        "→" -> {
                                            val cur = textFieldValueState.selection.end
                                            if (cur < textFieldValueState.text.length) {
                                                textFieldValueState = textFieldValueState.copy(selection = androidx.compose.ui.text.TextRange(cur + 1))
                                            }
                                        }
                                        "HOME" -> {
                                            textFieldValueState = textFieldValueState.copy(selection = androidx.compose.ui.text.TextRange(0))
                                        }
                                        "END" -> {
                                            textFieldValueState = textFieldValueState.copy(selection = androidx.compose.ui.text.TextRange(textFieldValueState.text.length))
                                        }
                                        "PGUP" -> {
                                            coroutineScope.launch {
                                                val cur = terminalScrollState.value
                                                terminalScrollState.scrollTo((cur - 600).coerceAtLeast(0))
                                            }
                                        }
                                        "PGDN" -> {
                                            coroutineScope.launch {
                                                val cur = terminalScrollState.value
                                                terminalScrollState.scrollTo((cur + 600).coerceAtMost(terminalScrollState.maxValue))
                                            }
                                        }
                                        "CLSR" -> {
                                            onClearTerminal()
                                            onCommandChange("")
                                            historyIndex = -1
                                        }
                                        "↑" -> {
                                            val filteredHistory = commandHistory.filter { it.command.isNotBlank() }
                                            if (filteredHistory.isNotEmpty()) {
                                                val nextIndex = historyIndex + 1
                                                if (nextIndex < filteredHistory.size) {
                                                    historyIndex = nextIndex
                                                    onCommandChange(filteredHistory[nextIndex].command)
                                                }
                                            }
                                        }
                                        "↓" -> {
                                            val filteredHistory = commandHistory.filter { it.command.isNotBlank() }
                                            val prevIndex = historyIndex - 1
                                            if (prevIndex >= 0 && filteredHistory.isNotEmpty()) {
                                                historyIndex = prevIndex
                                                onCommandChange(filteredHistory[prevIndex].command)
                                            } else {
                                                historyIndex = -1
                                                onCommandChange("")
                                            }
                                        }
                                    }
                                }
                                .let { 
                                    if (isLandscape) it.padding(vertical = 6.dp) else it.padding(horizontal = 10.dp, vertical = 6.dp)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = keyLabel,
                                color = if (keyLabel == "↑" || keyLabel == "↓" || keyLabel == "←" || keyLabel == "→") keyArrowColor else keyTextColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Show/hide bottom bar toggle button placed in the top-right / right corner of the keybar
                IconButton(
                    onClick = { onBottomBarVisibleChange(!isBottomBarVisible) },
                    modifier = Modifier
                        .size(36.dp)
                        .padding(end = 6.dp)
                        .align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = if (isBottomBarVisible) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = "Toggle Bottom Navigation",
                        tint = keyArrowColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } // Close keybar Row
        } // Close main window Column
                 // Custom Main Menu overlay box (Drawer Popup) matching screenshot specs
        if (showMainMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onMainMenuToggle() }
            ) {
                // Map the popup menu background, border, text, and accent colors dynamically based on terminalTheme
                val menuBgColors = when (terminalTheme) {
                    "ubuntu" -> listOf(Color(0xFF381426), Color(0xFF1B0512))
                    "matrix" -> listOf(Color(0xFF001F00), Color(0xFF000800))
                    "cyberpunk" -> listOf(Color(0xFF230D3E), Color(0xFF0C0318))
                    "monochrome" -> listOf(Color(0xFF1D1D1D), Color(0xFF070707))
                    "light" -> listOf(Color(0xFFF1F5F9), Color(0xFFE2E8F0))
                    else -> listOf(Color(0xFF381426), Color(0xFF1B0512))
                }
                val menuBorderCol = when (terminalTheme) {
                    "ubuntu" -> Color.White.copy(alpha = 0.12f)
                    "matrix" -> Color(0xFF33FF33).copy(alpha = 0.2f)
                    "cyberpunk" -> Color(0xFFFF007F).copy(alpha = 0.2f)
                    "monochrome" -> Color.White.copy(alpha = 0.12f)
                    "light" -> Color(0xFF1E293B).copy(alpha = 0.12f)
                    else -> Color.White.copy(alpha = 0.12f)
                }
                val menuTextCol = when (terminalTheme) {
                    "ubuntu" -> Color.White
                    "matrix" -> Color(0xFF33FF33)
                    "cyberpunk" -> Color(0xFFFF007F)
                    "monochrome" -> Color.White
                    "light" -> Color(0xFF1E293B)
                    else -> Color.White
                }
                val menuSecTextCol = when (terminalTheme) {
                    "ubuntu" -> Color.White.copy(alpha = 0.7f)
                    "matrix" -> Color(0xFF33FF33).copy(alpha = 0.7f)
                    "cyberpunk" -> Color(0xFFFF007F).copy(alpha = 0.7f)
                    "monochrome" -> Color.White.copy(alpha = 0.7f)
                    "light" -> Color(0xFF64748B)
                    else -> Color.White.copy(alpha = 0.7f)
                }
                val menuAccentCol = when (terminalTheme) {
                    "ubuntu" -> Color(0xFFE95420)
                    "matrix" -> Color(0xFF33FF33)
                    "cyberpunk" -> Color(0xFFFF007F)
                    "monochrome" -> Color.White
                    "light" -> Color(0xFF0F172A)
                    else -> Color(0xFFE95420)
                }

                val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val menuMaxHeight = if (isLandscape) 280.dp else 560.dp

                // Main Menu content container box on the top right
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 56.dp, end = 12.dp, bottom = 12.dp)
                        .width(280.dp)
                        .heightIn(max = menuMaxHeight)
                        .oneUiGlassCard(
                            shape = RoundedCornerShape(18.dp),
                            backgroundColor = menuBgColors.first().copy(alpha = 0.75f),
                            borderColor = menuAccentCol.copy(alpha = 0.35f),
                            borderWidth = 1.dp
                        )
                        .clickable(enabled = true, onClick = {}) // swallow click
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Row 1: Themes selector (3 dynamic circles)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Translations.get("dropdown_layout_theme", selectedLanguage),
                            color = menuSecTextCol,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Dark Auburn Theme Circle
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2C001E))
                                    .border(
                                        width = if (terminalTheme == "ubuntu") 1.5.dp else 0.5.dp,
                                        color = if (terminalTheme == "ubuntu") Color(0xFFE95420) else menuSecTextCol.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                                    .clickable { onThemeChange("ubuntu") },
                                contentAlignment = Alignment.Center
                            ) {
                                if (terminalTheme == "ubuntu") {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }

                            // Dark Obsidian Black Theme Circle
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF000000))
                                    .border(
                                        width = if (terminalTheme == "monochrome") 1.5.dp else 0.5.dp,
                                        color = if (terminalTheme == "monochrome") Color.White else menuSecTextCol.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                                    .clickable { onThemeChange("monochrome") },
                                contentAlignment = Alignment.Center
                            ) {
                                if (terminalTheme == "monochrome") {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }

                            // Light Slate Theme Circle
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF1F5F9))
                                    .border(
                                        width = if (terminalTheme == "light") 1.5.dp else 0.5.dp,
                                        color = if (terminalTheme == "light") Color(0xFF1E293B) else menuSecTextCol.copy(alpha = 0.8f),
                                        shape = CircleShape
                                    )
                                    .clickable { onThemeChange("light") },
                                contentAlignment = Alignment.Center
                            ) {
                                if (terminalTheme == "light") {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF1E293B), modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }

                    // Row 2: Font Zoom / Sizing controls: Min - Scale % - Plus
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Translations.get("dropdown_font_scale", selectedLanguage),
                            color = menuSecTextCol,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Minus Circle
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(menuTextCol.copy(alpha = 0.15f))
                                    .clickable { if (terminalFontSize > 6) onFontSizeChange(terminalFontSize - 1) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = menuTextCol, modifier = Modifier.size(14.dp))
                            }

                            // Percent Text
                            Text(
                                text = "${(terminalFontSize * 100 / 12).toInt()}%",
                                color = menuTextCol,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )

                            // Plus Circle
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(menuTextCol.copy(alpha = 0.15f))
                                    .clickable { if (terminalFontSize < 30) onFontSizeChange(terminalFontSize + 1) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", tint = menuTextCol, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    HorizontalDivider(color = menuTextCol.copy(alpha = 0.08f))

                    // Menu Options links per instructions (No keyboard shortcuts printed)
                    val options = listOf(
                        Triple(Translations.get("menu_new_tab", selectedLanguage), Icons.Default.Add, onAddNewTabClick),
                        Triple(Translations.get("menu_show_tabs", selectedLanguage), Icons.Default.GridView, onShowOpenTabsClick),
                        Triple(Translations.get("menu_fullscreen", selectedLanguage), Icons.Default.Fullscreen, onImmersiveToggle),
                        Triple(Translations.get("menu_preferences", selectedLanguage), Icons.Default.Settings, onSettingsClick),
                        Triple(Translations.get("cm_title", selectedLanguage), Icons.Default.Devices, onConnectionManagerClick),
                        Triple(Translations.get("menu_shortcuts", selectedLanguage), Icons.Default.Keyboard, onShortcutsClick),
                        Triple(Translations.get("menu_about", selectedLanguage), Icons.Default.Info, onAboutClick)
                    )

                    options.forEach { (label, icon, action) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    onMainMenuToggle()
                                    action()
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = menuAccentCol,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = label,
                                color = menuTextCol,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    HorizontalDivider(color = menuTextCol.copy(alpha = 0.08f))

                    // DIRECT REQUESTED RELAYOUT: Port connection integrated exactly at the bottom!
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = Translations.get("menu_connect_wireless_port", selectedLanguage),
                            color = menuAccentCol,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Status Dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (isConnected) Color(0xFF27C93F) else Color(0xFFFF5F56),
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = if (isConnected) Translations.get("status_connected", selectedLanguage) else Translations.get("status_disconnected", selectedLanguage),
                                color = menuSecTextCol,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mini Port / Host:Port TextField (Adapted to current theme colors)
                            BasicTextField(
                                value = portInput,
                                onValueChange = onPortChange,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(menuTextCol.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                    .border(1.dp, menuBorderCol.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = menuTextCol,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Ascii,
                                    imeAction = ImeAction.Done
                                ),
                                decorationBox = { innerTextField ->
                                    if (portInput.isEmpty()) {
                                        Text(
                                            text = when (selectedLanguage) {
                                                "ru" -> "Порт (5555 или IP:Порт)"
                                                "en" -> "Port (5555 or IP:Port)"
                                                else -> "Port (5555 yoki IP:Port)"
                                            },
                                            color = menuSecTextCol.copy(alpha = 0.6f),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        )
                                    }
                                    innerTextField()
                                },
                                cursorBrush = SolidColor(menuAccentCol)
                            )

                            // Connection toggle button (One UI 8.5 Glass Capsule)
                            val buttonBgColor = if (isConnected) Color(0xFFB91C1C).copy(alpha = 0.75f) else menuAccentCol.copy(alpha = 0.85f)
                            val buttonTextColor = if (terminalTheme == "light" && !isConnected) Color.White else (if (isConnected) Color.White else menuBgColors.first())
                            Box(
                                modifier = Modifier
                                    .oneUiGlassCapsule(
                                        backgroundColor = buttonBgColor,
                                        accentColor = Color.White.copy(alpha = 0.5f)
                                    )
                                    .clickable { if (isConnected) onDisconnectClick() else onConnectClick() }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isConnecting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = buttonTextColor,
                                        strokeWidth = 1.5.dp
                                    )
                                } else {
                                    Text(
                                        text = if (isConnected) Translations.get("btn_disconnect", selectedLanguage) else Translations.get("btn_connect", selectedLanguage),
                                        color = buttonTextColor,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
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

@Composable
fun ScriptsScreen(
    savedScripts: List<ScriptEntity>,
    isConnected: Boolean,
    selectedLanguage: String,
    onRunScript: (String) -> Unit,
    onAddScript: (String, String, String, String) -> Unit,
    onDeleteScript: (ScriptEntity) -> Unit
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var searchKeyword by rememberSaveable { mutableStateOf("") }

    val localizedScripts = remember(savedScripts, selectedLanguage) {
        savedScripts.map { Translations.getLocalizedScript(it, selectedLanguage) }
    }

    val filteredScripts = localizedScripts.filter {
        it.title.contains(searchKeyword, ignoreCase = true) || 
        it.description.contains(searchKeyword, ignoreCase = true) ||
        it.category.contains(searchKeyword, ignoreCase = true)
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 16.dp)
    ) {
        if (!isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Translations.get("scripts_tab_title", selectedLanguage),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                IconButton(
                    onClick = { showDialog = true },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("add_script_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = Translations.get("btn_add_script", selectedLanguage),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            OutlinedTextField(
                value = searchKeyword,
                onValueChange = { searchKeyword = it },
                placeholder = { Text(Translations.get("search_scripts_placeholder", selectedLanguage)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = CircleShape,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = Translations.get("btn_search", selectedLanguage)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                )
            )
        } else {
            // Highly optimized compact landscape row: combine Search block and Create Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sleek custom text input box for search in landscape
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(15.dp))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(15.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.secondary)
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (searchKeyword.isEmpty()) {
                                Text(
                                    text = Translations.get("search_scripts_placeholder", selectedLanguage),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            BasicTextField(
                                value = searchKeyword,
                                onValueChange = { searchKeyword = it },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Button(
                    onClick = { showDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = CircleShape,
                    modifier = Modifier.height(30.dp).testTag("add_script_button_landscape").glassCapsule(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = Translations.get("btn_add_script", selectedLanguage),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = Translations.get("btn_add_script", selectedLanguage), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (filteredScripts.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = Translations.get("scripts_empty", selectedLanguage),
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .verticalFadingEdge(topEdgeSize = 32.dp, bottomEdgeSize = 32.dp),
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 12.dp)
            ) {
                items(filteredScripts, key = { it.id }) { script ->
                    ScriptCard(
                        script = script,
                        isConnected = isConnected,
                        selectedLanguage = selectedLanguage,
                        onRun = { onRunScript(script.command) },
                        onDelete = { onDeleteScript(script) }
                    )
                }
            }
        }
    }

    if (showDialog) {
        AddScriptDialog(
            selectedLanguage = selectedLanguage,
            onDismiss = { showDialog = false },
            onSave = { title, command, desc, category ->
                onAddScript(title, command, desc, category)
                showDialog = false
            }
        )
    }
}

@Composable
fun ScriptCard(
    script: ScriptEntity,
    isConnected: Boolean,
    selectedLanguage: String,
    onRun: () -> Unit,
    onDelete: () -> Unit
) {
    val displayedCategory = remember(script.category, selectedLanguage) {
        when (script.category.trim()) {
            "Ilova" -> when (selectedLanguage) {
                "en" -> "App"
                "ru" -> "Приложение"
                else -> "Ilova"
            }
            "Tizim" -> when (selectedLanguage) {
                "en" -> "System"
                "ru" -> "Система"
                else -> "Tizim"
            }
            "Ekran" -> when (selectedLanguage) {
                "en" -> "Screen"
                "ru" -> "Экран"
                else -> "Ekran"
            }
            "Paketlar" -> when (selectedLanguage) {
                "en" -> "Packages"
                "ru" -> "Пакеты"
                else -> "Paketlar"
            }
            "Tarmoq" -> when (selectedLanguage) {
                "en" -> "Network"
                "ru" -> "Сеть"
                else -> "Tarmoq"
            }
            "Boshqa" -> when (selectedLanguage) {
                "en" -> "Other"
                "ru" -> "Другое"
                else -> "Boshqa"
            }
            else -> script.category
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .oneUiGlassCard(
                shape = RoundedCornerShape(if (isLandscape) 14.dp else 20.dp),
                backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                borderWidth = 1.dp
            )
            .padding(if (isLandscape) 10.dp else 14.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .oneUiGlassCapsule(
                            backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                            accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.50f),
                            shape = CircleShape
                        )
                        .padding(horizontal = if (isLandscape) 8.dp else 12.dp, vertical = if (isLandscape) 2.dp else 4.dp)
                ) {
                    Text(
                        text = displayedCategory,
                        fontSize = if (isLandscape) 9.5.sp else 11.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(if (isLandscape) 24.dp else 28.dp)
                        .oneUiGlassCapsule(
                            backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            accentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = Translations.get("btn_delete", selectedLanguage),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(if (isLandscape) 12.dp else 15.dp)
                    )
                }
            }

            Text(
                text = script.title,
                fontSize = if (isLandscape) 13.5.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = script.description,
                fontSize = if (isLandscape) 11.sp else 12.5.sp,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Normal,
                maxLines = if (isLandscape) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .padding(if (isLandscape) 6.dp else 10.dp)
            ) {
                Text(
                    text = "shell: ${script.command}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (isLandscape) 9.5.sp else 11.sp,
                    color = Color(0xFFA8BBA2),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OneUiGlassCapsuleButton(
                onClick = onRun,
                enabled = isConnected,
                backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                accentColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                padding = PaddingValues(horizontal = 12.dp, vertical = if (isLandscape) 4.dp else 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (isLandscape) 32.dp else 42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = Translations.get("run_script_btn", selectedLanguage),
                    modifier = Modifier.size(if (isLandscape) 14.dp else 18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = Translations.get("run_script_btn", selectedLanguage),
                    fontSize = if (isLandscape) 11.5.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun AddScriptDialog(
    selectedLanguage: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Sistem") }

    val categories = listOf("Sistem", "Ekran", "Ilova", "Tweak")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Translations.get("add_script_title", selectedLanguage), fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(Translations.get("label_title", selectedLanguage)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text(Translations.get("label_command", selectedLanguage)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(Translations.get("label_desc", selectedLanguage)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(Translations.get("label_category", selectedLanguage), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = category == cat
                        val catLabel = when (cat) {
                            "Sistem" -> Translations.get("cat_sys", selectedLanguage)
                            "Ekran" -> Translations.get("cat_scr", selectedLanguage)
                            "Ilova" -> Translations.get("cat_pkg", selectedLanguage)
                            else -> Translations.get("cat_oth", selectedLanguage)
                        }
                        Box(
                            modifier = Modifier
                                .oneUiGlassCapsule(
                                    backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.08f),
                                    accentColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.20f),
                                    shape = CircleShape
                                )
                                .clickable { category = cat }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = catLabel,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            OneUiGlassCapsuleButton(
                onClick = { if (title.isNotBlank() && command.isNotBlank()) onSave(title, command, description, category) },
                enabled = title.isNotBlank() && command.isNotBlank(),
                backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                accentColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                padding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(Translations.get("btn_save", selectedLanguage), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
        },
        dismissButton = {
            OneUiGlassCapsuleButton(
                onClick = onDismiss,
                backgroundColor = Color.White.copy(alpha = 0.10f),
                accentColor = Color.White.copy(alpha = 0.25f),
                contentColor = Color.White,
                padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(Translations.get("btn_cancel", selectedLanguage), fontSize = 12.sp)
            }
        }
    )
}

@Composable
fun HistoryScreen(
    historyList: List<HistoryEntity>,
    selectedLanguage: String,
    onRerunCommand: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Translations.get("history_tab_title", selectedLanguage),
                fontWeight = FontWeight.Bold,
                fontSize = if (isLandscape) 13.sp else 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (historyList.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .oneUiGlassCapsule(
                            backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            accentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                        )
                        .clickable(onClick = onClearHistory)
                        .padding(horizontal = if (isLandscape) 8.dp else 12.dp, vertical = if (isLandscape) 4.dp else 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = Translations.get("btn_clear", selectedLanguage),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(if (isLandscape) 13.dp else 15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = Translations.get("btn_clear", selectedLanguage),
                            fontSize = if (isLandscape) 11.sp else 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = Translations.get("history_empty", selectedLanguage),
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(if (isLandscape) 8.dp else 32.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .verticalFadingEdge(topEdgeSize = 32.dp, bottomEdgeSize = 32.dp),
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 12.dp)
            ) {
                items(historyList, key = { it.id }) { history ->
                    HistoryCard(
                        history = history,
                        selectedLanguage = selectedLanguage,
                        onRerun = { onRerunCommand(history.command) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryCard(
    history: HistoryEntity,
    selectedLanguage: String,
    onRerun: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    val sdf = remember { SimpleDateFormat("HH:mm:ss, dd.MM.yyyy", Locale.getDefault()) }
    val timeString = remember(history.timestamp) { sdf.format(Date(history.timestamp)) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .oneUiGlassCard(
                shape = RoundedCornerShape(if (isLandscape) 12.dp else 18.dp),
                backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                borderColor = if (history.isSuccess) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                borderWidth = 1.dp
            )
            .clickable { expanded = !expanded }
            .padding(if (isLandscape) 10.dp else 14.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(if (isLandscape) 8.dp else 10.dp)
                            .background(
                                color = if (history.isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (history.isSuccess) Translations.get("status_success", selectedLanguage) else Translations.get("status_fail", selectedLanguage),
                        fontSize = if (isLandscape) 10.sp else 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (history.isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    text = timeString,
                    fontSize = if (isLandscape) 10.sp else 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Text(
                text = history.command,
                fontFamily = FontFamily.Monospace,
                fontSize = if (isLandscape) 12.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 8.dp),
                    modifier = Modifier.padding(top = if (isLandscape) 4.dp else 8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    
                    Text(
                        text = Translations.get("label_output", selectedLanguage),
                        fontSize = if (isLandscape) 10.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(if (isLandscape) 8.dp else 12.dp)
                    ) {
                        Text(
                            text = history.output,
                            fontFamily = FontFamily.Monospace,
                            fontSize = if (isLandscape) 9.5.sp else 11.sp,
                            color = Color(0xFFE2E8F0),
                            lineHeight = if (isLandscape) 13.sp else 15.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .oneUiGlassCapsule(
                                backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.50f),
                                shape = CircleShape
                            )
                            .clickable(onClick = onRerun)
                            .padding(
                                horizontal = if (isLandscape) 12.dp else 16.dp,
                                vertical = if (isLandscape) 4.dp else 7.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = Translations.get("command_run_again", selectedLanguage),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(if (isLandscape) 12.dp else 15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Translations.get("command_run_again", selectedLanguage),
                                fontSize = if (isLandscape) 10.5.sp else 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceInfoScreen(
    deviceInfo: Map<String, String>,
    isConnected: Boolean,
    selectedLanguage: String,
    onRefreshInfo: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val filteredItems = remember(deviceInfo, searchQuery, selectedLanguage) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            deviceInfo.toList()
        } else {
            deviceInfo.filter { (k, v) ->
                val localizedKey = Translations.localizeDeviceLabel(k, selectedLanguage)
                val localizedVal = Translations.localizeDeviceValue(v, selectedLanguage)
                k.contains(q, ignoreCase = true) || 
                v.contains(q, ignoreCase = true) ||
                localizedKey.contains(q, ignoreCase = true) ||
                localizedVal.contains(q, ignoreCase = true)
            }.toList()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = Translations.get("device_diagnostic_title", selectedLanguage),
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isLandscape) 13.sp else 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isLandscape) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isConnected) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isConnected) "ADB CONNECTED" else "LOCAL",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                if (!isLandscape) {
                    Text(
                        text = if (isConnected) Translations.get("sub_diagnostic_adb", selectedLanguage) else Translations.get("sub_diagnostic_local", selectedLanguage),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            IconButton(
                onClick = onRefreshInfo,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.size(if (isLandscape) 28.dp else 40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = Translations.get("btn_refresh", selectedLanguage),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (isLandscape) 14.dp else 24.dp)
                )
            }
        }

        // Search Bar for instant metric filtering
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = if (selectedLanguage == "uz") "Xususiyatlarni qidirish..." else if (selectedLanguage == "ru") "Поиск параметров..." else "Search diagnostics...",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp, max = 46.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                unfocusedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
            )
        )

        // Connection/Mode status banner card only in portrait to avoid landscape screen waste
        if (!isLandscape) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected) 
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                    else 
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isConnected) Translations.get("banner_diagnostic_adb", selectedLanguage) else Translations.get("banner_diagnostic_local", selectedLanguage),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isConnected) 
                                Translations.get("desc_diagnostic_adb", selectedLanguage)
                            else 
                                Translations.get("desc_diagnostic_local", selectedLanguage),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (deviceInfo.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = Translations.get("diagnostic_analyzing", selectedLanguage),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    Text(
                        text = if (selectedLanguage == "uz") "Hech qanday ma'lumot topilmadi" else if (selectedLanguage == "ru") "Параметры не найдены" else "No matching diagnostics found",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 10.dp),
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 10.dp),
                contentPadding = PaddingValues(bottom = if (isLandscape) 4.dp else 16.dp)
            ) {
                items(filteredItems) { (key, value) ->
                    InfoTile(label = key, value = value, selectedLanguage = selectedLanguage)
                }
            }
        }
    }
}

@Composable
fun InfoTile(label: String, value: String, selectedLanguage: String) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val localizedLabel = remember(label, selectedLanguage) {
        Translations.localizeDeviceLabel(label, selectedLanguage)
    }
    val localizedValue = remember(value, selectedLanguage) {
        Translations.localizeDeviceValue(value, selectedLanguage)
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(if (isLandscape) 10.dp else 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f), RoundedCornerShape(if (isLandscape) 10.dp else 16.dp))
            .clickable {
                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString("$localizedLabel: $localizedValue"))
                val toastMsg = when (selectedLanguage) {
                    "uz" -> "$localizedLabel nusxalandi"
                    "ru" -> "$localizedLabel скопировано"
                    else -> "Copied $localizedLabel"
                }
                android.widget.Toast.makeText(context, toastMsg, android.widget.Toast.LENGTH_SHORT).show()
            }
    ) {
        Column(
            modifier = Modifier.padding(if (isLandscape) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 2.dp else 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = localizedLabel.uppercase(),
                    fontSize = if (isLandscape) 8.sp else 9.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(if (isLandscape) 10.dp else 12.dp)
                )
            }
            Text(
                text = localizedValue,
                fontSize = if (isLandscape) 11.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BottomNavBar(
    selectedTab: Int,
    terminalTheme: String,
    selectedLanguage: String,
    onTabSelected: (Int) -> Unit
) {
    val (textColor, bgColor, borderColor, accentColor, cardBgColor, secText) = com.example.ui.getThemeColors(terminalTheme)
    
    // [SENIOR ARCHITECTURE]: One UI 8.5 Advanced Glassmorphism Capsule
    // Features: Hardware-accelerated blur fallback, dynamic width distribution,
    // spring-physics scaling, and pure active-state accenting.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            shadowElevation = 8.dp,
            modifier = Modifier
                .height(54.dp)
                .fillMaxWidth()
                .oneUiGlassCard(
                    shape = CircleShape,
                    backgroundColor = cardBgColor.copy(alpha = 0.82f),
                    borderColor = borderColor.copy(alpha = 0.20f),
                    borderWidth = 1.dp
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val navItems = listOf(
                    Triple(0, Icons.Default.Terminal, Translations.get("nav_terminal", selectedLanguage)),
                    Triple(1, Icons.Default.Android, Translations.get("nav_apps", selectedLanguage)),
                    Triple(2, Icons.Default.Speed, Translations.get("nav_monitor", selectedLanguage)),
                    Triple(3, Icons.Default.Folder, Translations.get("nav_files", selectedLanguage))
                )

                navItems.forEach { (index, icon, label) ->
                    val isSelected = selectedTab == index
                    
                    val weight by animateFloatAsState(
                        targetValue = if (isSelected) 1.6f else 1f,
                        animationSpec = spring(
                            dampingRatio = 0.65f,
                            stiffness = 300f
                        ),
                        label = "NavWeight"
                    )


                    
                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) accentColor else textColor.copy(alpha = 0.45f),
                        animationSpec = tween(250),
                        label = "NavTint"
                    )

                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .height(44.dp)
                            .padding(horizontal = 2.dp)
                            .let {
                                if (isSelected) {
                                    it.oneUiGlassCapsule(
                                        backgroundColor = accentColor.copy(alpha = 0.20f),
                                        accentColor = accentColor.copy(alpha = 0.40f),
                                        borderWidth = 0.5.dp
                                    )
                                } else {
                                    it.clip(CircleShape).background(Color.Transparent)
                                }
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTabSelected(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = iconTint,
                                modifier = Modifier.size(20.dp)
                            )
                            
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn(tween(250)) + expandHorizontally(spring(dampingRatio = 0.65f, stiffness = 300f)),
                                exit = fadeOut(tween(250)) + shrinkHorizontally(spring(dampingRatio = 0.65f, stiffness = 300f))
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    modifier = Modifier.padding(start = 6.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    viewModel: AdbViewModel,
    onDismiss: () -> Unit,
    onResetScripts: () -> Unit,
    onResetConnection: () -> Unit,
    savedScripts: List<ScriptEntity>,
    commandHistory: List<HistoryEntity>,
    deviceInfo: Map<String, String>,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onOpenAntiOverlaySentry: () -> Unit = {},
    onOpenAppUpdates: () -> Unit = {}
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("super_adb_settings", android.content.Context.MODE_PRIVATE) }
    val terminalFontSize by viewModel.terminalFontSize.collectAsState()
    val terminalTheme by viewModel.terminalTheme.collectAsState()
    val autoScrollEnabled by viewModel.autoScrollEnabled.collectAsState()
    val isTtsEnabled by viewModel.isTtsEnabled.collectAsState()
    val historyLimit by viewModel.historyLimit.collectAsState()
    val portInput by viewModel.portInput.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()

    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    var activeDialogTab by rememberSaveable { mutableStateOf(0) } // 0 = Sozlamalar, 1 = Skriptlar, 2 = Tarix, 3 = Tizim Info

    val textCol: Color
    val bgCol: Color
    val borderCol: Color
    val headerBgCol: Color
    val accentCol: Color
    val cardBgCol: Color
    val secTextCol: Color
    val fontFam: FontFamily

    when (terminalTheme) {
        "ubuntu" -> {
            textCol = Color(0xFFFFFFFF)
            bgCol = Color(0xFF2C001E)
            borderCol = Color(0xFFE95420)
            headerBgCol = Color(0xFF241C1A)
            accentCol = Color(0xFFE95420)
            cardBgCol = Color(0xFF381426)
            secTextCol = Color.White.copy(alpha = 0.6f)
            fontFam = FontFamily.Monospace
        }
        "matrix" -> {
            textCol = Color(0xFF33FF33)
            bgCol = Color(0xFF001100)
            borderCol = Color(0xFF005500)
            headerBgCol = Color(0xFF000800)
            accentCol = Color(0xFF33FF33)
            cardBgCol = Color(0xFF002200)
            secTextCol = Color(0xFF33FF33).copy(alpha = 0.6f)
            fontFam = FontFamily.Monospace
        }
        "cyberpunk" -> {
            textCol = Color(0xFFFF007F)
            bgCol = Color(0xFF120422)
            borderCol = Color(0xFF7E00FF)
            headerBgCol = Color(0xFF0C0318)
            accentCol = Color(0xFFFF007F)
            cardBgCol = Color(0xFF230D3E)
            secTextCol = Color(0xFFFF007F).copy(alpha = 0.6f)
            fontFam = FontFamily.Monospace
        }
        "monochrome" -> {
            textCol = Color(0xFFFFFFFF)
            bgCol = Color(0xFF000000)
            borderCol = Color(0xFF333333)
            headerBgCol = Color(0xFF111111)
            accentCol = Color(0xFFFFFFFF)
            cardBgCol = Color(0xFF1E1E1E)
            secTextCol = Color.White.copy(alpha = 0.5f)
            fontFam = FontFamily.Monospace
        }
        "light" -> {
            textCol = Color(0xFF1E293B)
            bgCol = Color(0xFFF1F5F9)
            borderCol = Color(0xFFCBD5E1)
            headerBgCol = Color(0xFFE2E8F0)
            accentCol = Color(0xFF1E293B)
            cardBgCol = Color(0xFFFFFFFF)
            secTextCol = Color(0xFF64748B)
            fontFam = FontFamily.Default
        }
        else -> {
            textCol = Color(0xFFFFFFFF)
            bgCol = Color(0xFF2C001E)
            borderCol = Color(0xFFE95420)
            headerBgCol = Color(0xFF241C1A)
            accentCol = Color(0xFFE95420)
            cardBgCol = Color(0xFF381426)
            secTextCol = Color.White.copy(alpha = 0.6f)
            fontFam = FontFamily.Monospace
        }
    }

    val dynamicColorScheme = when (terminalTheme) {
        "ubuntu" -> darkColorScheme(
            primary = Color(0xFFE95420),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF381426),
            onPrimaryContainer = Color.White,
            secondary = Color.White.copy(alpha = 0.6f),
            onSecondary = Color.White,
            surface = Color(0xFF381426),
            onSurface = Color.White,
            background = Color(0xFF2C001E),
            onBackground = Color.White,
            tertiary = Color(0xFFE95420),
            tertiaryContainer = Color(0xFFE95420).copy(alpha = 0.25f),
            errorContainer = Color(0xFF4E1410)
        )
        "matrix" -> darkColorScheme(
            primary = Color(0xFF33FF33),
            onPrimary = Color.Black,
            primaryContainer = Color(0xFF002200),
            onPrimaryContainer = Color(0xFF33FF33),
            secondary = Color(0xFF33FF33).copy(alpha = 0.6f),
            onSecondary = Color.Black,
            surface = Color(0xFF002200),
            onSurface = Color(0xFF33FF33),
            background = Color(0xFF001100),
            onBackground = Color(0xFF33FF33),
            tertiary = Color(0xFF33FF33),
            tertiaryContainer = Color(0xFF33FF33).copy(alpha = 0.25f),
            errorContainer = Color(0xFF220000)
        )
        "cyberpunk" -> darkColorScheme(
            primary = Color(0xFFFF007F),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF230D3E),
            onPrimaryContainer = Color.White,
            secondary = Color(0xFF7E00FF),
            onSecondary = Color.White,
            surface = Color(0xFF230D3E),
            onSurface = Color(0xFFFF007F),
            background = Color(0xFF120422),
            onBackground = Color(0xFFFF007F),
            tertiary = Color(0xFFFF007F),
            tertiaryContainer = Color(0xFFFF007F).copy(alpha = 0.25f),
            errorContainer = Color(0xFF3A001C)
        )
        "monochrome" -> darkColorScheme(
            primary = Color(0xFFFFFFFF),
            onPrimary = Color.Black,
            primaryContainer = Color(0xFF1E1E1E),
            onPrimaryContainer = Color.White,
            secondary = Color.White.copy(alpha = 0.5f),
            onSecondary = Color.Black,
            surface = Color(0xFF1E1E1E),
            onSurface = Color.White,
            background = Color(0xFF000000),
            onBackground = Color.White,
            tertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFFFFF).copy(alpha = 0.25f),
            errorContainer = Color(0xFF1E1E1E)
        )
        "light" -> lightColorScheme(
            primary = Color(0xFF1E293B),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE2E8F0),
            onPrimaryContainer = Color(0xFF1E293B),
            secondary = Color(0xFF64748B),
            onSecondary = Color.White,
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1E293B),
            background = Color(0xFFF1F5F9),
            onBackground = Color(0xFF1E293B),
            tertiary = Color(0xFF1E293B),
            tertiaryContainer = Color(0xFF1E293B).copy(alpha = 0.25f),
            errorContainer = Color(0xFFFFE0E0)
        )
        else -> darkColorScheme(
            primary = Color(0xFFE95420),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF381426),
            onPrimaryContainer = Color.White,
            secondary = Color.White.copy(alpha = 0.6f),
            onSecondary = Color.White,
            surface = Color(0xFF381426),
            onSurface = Color.White,
            background = Color(0xFF2C001E),
            onBackground = Color.White,
            tertiary = Color(0xFFE95420),
            tertiaryContainer = Color(0xFFE95420).copy(alpha = 0.25f),
            errorContainer = Color(0xFF4E1410)
        )
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        MaterialTheme(colorScheme = dynamicColorScheme) {
            OneUiAmbientBackdrop(theme = terminalTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = bgCol.copy(alpha = 0.82f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                    // Top Custom Header (Ubuntu Window style matching the Main Terminal Window)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerBgCol)
                            .padding(horizontal = 14.dp, vertical = if (isLandscape) 6.dp else 10.dp)
                    ) {
                        // Left Window control buttons (Red, Yellow, Green circles)
                        Row(
                            modifier = Modifier.align(Alignment.CenterStart),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(0xFFFF5F56), CircleShape)
                                    .clickable { onDismiss() }
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(0xFFFFBD2E), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(0xFF27C93F), CircleShape)
                            )
                        }

                        // Center Title
                        Text(
                            text = Translations.get("settings_title", selectedLanguage),
                            color = textCol,
                            fontFamily = fontFam,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )

                        // Right close button (In both portrait and landscape mode!)
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .oneUiGlassCapsule(
                                    backgroundColor = cardBgCol.copy(alpha = 0.40f),
                                    accentColor = borderCol.copy(alpha = 0.30f)
                                )
                                .clickable(onClick = onDismiss)
                                .padding(
                                    horizontal = if (isLandscape) 14.dp else 18.dp,
                                    vertical = if (isLandscape) 4.dp else 6.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = Translations.get("btn_close", selectedLanguage),
                                fontFamily = fontFam,
                                fontSize = if (isLandscape) 11.sp else 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol
                            )
                        }
                    }

                    // Navigation Scrollable Tabs bar - dynamically chooses TabRow for Full Screen stretch on Landscape
                    @Composable
                    fun MyTabItem(index: Int, labelKey: String, modifier: Modifier = Modifier) {
                        val tabVerticalPadding = if (isLandscape) 6.dp else 12.dp
                        Tab(
                            selected = activeDialogTab == index,
                            onClick = { activeDialogTab = index },
                            modifier = modifier,
                            selectedContentColor = accentCol,
                            unselectedContentColor = secTextCol
                        ) {
                            Text(
                                text = Translations.get(labelKey, selectedLanguage),
                                modifier = Modifier.padding(vertical = tabVerticalPadding, horizontal = 12.dp),
                                fontSize = 12.sp,
                                fontFamily = fontFam,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isLandscape) {
                        TabRow(
                            selectedTabIndex = activeDialogTab,
                            containerColor = headerBgCol.copy(alpha = 0.6f),
                            contentColor = accentCol,
                            indicator = { tabPositions ->
                                if (activeDialogTab < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeDialogTab]),
                                        color = accentCol
                                    )
                                }
                            },
                            divider = {}
                        ) {
                            MyTabItem(0, "tab_settings", Modifier.weight(1f))
                            MyTabItem(1, "tab_scripts", Modifier.weight(1f))
                            MyTabItem(2, "tab_history", Modifier.weight(1f))
                            MyTabItem(3, "tab_system_info", Modifier.weight(1f))
                            MyTabItem(4, "tab_pc_permissions", Modifier.weight(1f))
                        }
                    } else {
                        ScrollableTabRow(
                            selectedTabIndex = activeDialogTab,
                            edgePadding = 0.dp,
                            containerColor = headerBgCol.copy(alpha = 0.6f),
                            contentColor = accentCol,
                            indicator = { tabPositions ->
                                if (activeDialogTab < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeDialogTab]),
                                        color = accentCol
                                    )
                                }
                            },
                            divider = {}
                        ) {
                            MyTabItem(0, "tab_settings")
                            MyTabItem(1, "tab_scripts")
                            MyTabItem(2, "tab_history")
                            MyTabItem(3, "tab_system_info")
                            MyTabItem(4, "tab_pc_permissions")
                        }
                    }

                    // Content Zone
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(if (isLandscape) 8.dp else 16.dp)
                    ) {
                        when (activeDialogTab) {
                            0 -> {
                                // Settings content group
                                val settingsScrollState = rememberScrollState()
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 16.dp),
                                    modifier = Modifier
                                        .verticalFadingEdge(scrollState = settingsScrollState, edgeSize = 32.dp)
                                        .verticalScroll(settingsScrollState)
                                ) {
                                    Text(
                                        text = Translations.get("settings_subtitle", selectedLanguage),
                                        fontSize = 12.sp,
                                        fontFamily = fontFam,
                                        color = secTextCol
                                    )

                                    // Language Selection Block
                                    val langList = listOf("uz" to "O'zbekcha", "en" to "English", "ru" to "Русский")
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = Translations.get("language_select_label", selectedLanguage),
                                            fontSize = 11.sp,
                                            fontFamily = fontFam,
                                            fontWeight = FontWeight.SemiBold,
                                            color = textCol
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            langList.forEach { (langId, langName) ->
                                                val isSelected = selectedLanguage == langId
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .oneUiGlassCapsule(
                                                            backgroundColor = if (isSelected) accentCol.copy(alpha = 0.35f) else cardBgCol.copy(alpha = 0.40f),
                                                            accentColor = if (isSelected) accentCol else borderCol.copy(alpha = 0.25f)
                                                        )
                                                        .clickable { viewModel.updateSelectedLanguage(langId) }
                                                        .padding(vertical = if (isLandscape) 5.dp else 10.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = langName,
                                                        fontSize = 11.sp,
                                                        fontFamily = fontFam,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) (if (terminalTheme == "monochrome" || terminalTheme == "matrix") Color.Black else Color.White) else textCol
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Theme Selection Block
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = Translations.get("settings_theme_label", selectedLanguage),
                                            fontSize = 11.sp,
                                            fontFamily = fontFam,
                                            fontWeight = FontWeight.SemiBold,
                                            color = textCol
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(
                                                "ubuntu" to "Ubuntu",
                                                "matrix" to "Matrix",
                                                "cyberpunk" to "Cyber",
                                                "monochrome" to "Mono",
                                                "light" to "Light"
                                            ).forEach { (id, name) ->
                                                val isSelected = terminalTheme == id
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .oneUiGlassCapsule(
                                                            backgroundColor = if (isSelected) accentCol.copy(alpha = 0.35f) else cardBgCol.copy(alpha = 0.40f),
                                                            accentColor = if (isSelected) accentCol else borderCol.copy(alpha = 0.25f)
                                                        )
                                                        .clickable { viewModel.updateTerminalTheme(id) }
                                                        .padding(vertical = if (isLandscape) 5.dp else 10.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = name,
                                                        fontSize = 10.sp,
                                                        fontFamily = fontFam,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) (if (terminalTheme == "monochrome" || terminalTheme == "matrix") Color.Black else Color.White) else textCol
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Custom Terminal Font Size Slider removed per user request

                                    // History Limit Block
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = Translations.get("settings_history_limit_label", selectedLanguage),
                                                fontSize = 11.sp,
                                                fontFamily = fontFam,
                                                fontWeight = FontWeight.SemiBold,
                                                color = textCol
                                            )
                                            val limitSuffix = if (selectedLanguage == "uz") " ta" else if (selectedLanguage == "ru") " шт." else " items"
                                            Text(
                                                text = "$historyLimit$limitSuffix",
                                                fontSize = 11.sp,
                                                fontFamily = fontFam,
                                                fontWeight = FontWeight.Bold,
                                                color = accentCol
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(20, 50, 100, 200).forEach { limit ->
                                                val isSelected = historyLimit == limit
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .oneUiGlassCapsule(
                                                            backgroundColor = if (isSelected) accentCol.copy(alpha = 0.35f) else cardBgCol.copy(alpha = 0.40f),
                                                            accentColor = if (isSelected) accentCol else borderCol.copy(alpha = 0.25f)
                                                        )
                                                        .clickable { viewModel.updateHistoryLimit(limit) }
                                                        .padding(vertical = if (isLandscape) 5.dp else 10.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "$limit",
                                                        fontSize = 11.sp,
                                                        fontFamily = fontFam,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) (if (terminalTheme == "monochrome" || terminalTheme == "matrix") Color.Black else Color.White) else textCol
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Auto Scroll Toggle Control Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = Translations.get("settings_auto_scroll_title", selectedLanguage),
                                                fontSize = 12.sp,
                                                fontFamily = fontFam,
                                                fontWeight = FontWeight.Bold,
                                                color = textCol
                                            )
                                            Text(
                                                text = Translations.get("settings_auto_scroll_desc", selectedLanguage),
                                                fontSize = 10.sp,
                                                fontFamily = fontFam,
                                                color = secTextCol
                                            )
                                        }
                                        Switch(
                                            checked = autoScrollEnabled,
                                            onCheckedChange = { viewModel.updateAutoScroll(it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = if (terminalTheme == "monochrome" || terminalTheme == "matrix") Color.Black else Color.White,
                                                checkedTrackColor = accentCol,
                                                checkedBorderColor = accentCol,
                                                uncheckedThumbColor = if (terminalTheme == "monochrome") Color(0xFF888888) else textCol.copy(alpha = 0.5f),
                                                uncheckedTrackColor = if (terminalTheme == "monochrome") Color(0xFF222222) else cardBgCol.copy(alpha = 0.8f),
                                                uncheckedBorderColor = if (terminalTheme == "monochrome") Color(0xFF444444) else borderCol.copy(alpha = 0.3f)
                                            )
                                        )
                                    }

                                    HorizontalDivider(color = borderCol.copy(alpha = 0.25f))

                                    // Text to Speech (TTS) Toggle Control Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = Translations.get("settings_tts_title", selectedLanguage),
                                                fontSize = 12.sp,
                                                fontFamily = fontFam,
                                                fontWeight = FontWeight.Bold,
                                                color = textCol
                                            )
                                            Text(
                                                text = Translations.get("settings_tts_desc", selectedLanguage),
                                                fontSize = 10.sp,
                                                fontFamily = fontFam,
                                                color = secTextCol
                                            )
                                        }
                                        Switch(
                                            checked = isTtsEnabled,
                                            onCheckedChange = { viewModel.toggleTtsEnabled() },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = if (terminalTheme == "monochrome" || terminalTheme == "matrix") Color.Black else Color.White,
                                                checkedTrackColor = accentCol,
                                                checkedBorderColor = accentCol,
                                                uncheckedThumbColor = if (terminalTheme == "monochrome") Color(0xFF888888) else textCol.copy(alpha = 0.5f),
                                                uncheckedTrackColor = if (terminalTheme == "monochrome") Color(0xFF222222) else cardBgCol.copy(alpha = 0.8f),
                                                uncheckedBorderColor = if (terminalTheme == "monochrome") Color(0xFF444444) else borderCol.copy(alpha = 0.3f)
                                            )
                                        )
                                    }

                                    HorizontalDivider(color = borderCol.copy(alpha = 0.25f))

                                    // 24/7 Anti-Overlay Sentry Card (OneUI Frosted Glass Card)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .oneUiGlassCard(
                                                shape = RoundedCornerShape(18.dp),
                                                backgroundColor = cardBgCol.copy(alpha = 0.85f),
                                                borderColor = accentCol.copy(alpha = 0.45f),
                                                borderWidth = 1.2.dp
                                            )
                                            .clickable { onOpenAntiOverlaySentry() }
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .oneUiGlassCapsule(
                                                        backgroundColor = accentCol.copy(alpha = 0.22f),
                                                        accentColor = accentCol.copy(alpha = 0.55f),
                                                        borderWidth = 1.dp
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Shield,
                                                    contentDescription = null,
                                                    tint = accentCol,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = Translations.get("anti_overlay_title", selectedLanguage),
                                                    fontSize = 13.5.sp,
                                                    fontFamily = fontFam,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = Translations.get("anti_overlay_desc", selectedLanguage),
                                                    fontSize = 11.5.sp,
                                                    fontFamily = fontFam,
                                                    color = secTextCol,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .oneUiGlassCapsule(
                                                        backgroundColor = Color.White.copy(alpha = 0.08f),
                                                        accentColor = Color.White.copy(alpha = 0.20f)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = borderCol.copy(alpha = 0.25f))

                                    // In-App OTA Updates Card (OneUI Frosted Glass Card)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .oneUiGlassCard(
                                                shape = RoundedCornerShape(18.dp),
                                                backgroundColor = cardBgCol.copy(alpha = 0.85f),
                                                borderColor = Color(0xFF10B981).copy(alpha = 0.45f),
                                                borderWidth = 1.2.dp
                                            )
                                            .clickable { onOpenAppUpdates() }
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .oneUiGlassCapsule(
                                                        backgroundColor = Color(0xFF10B981).copy(alpha = 0.22f),
                                                        accentColor = Color(0xFF10B981).copy(alpha = 0.55f),
                                                        borderWidth = 1.dp
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.SystemUpdate,
                                                    contentDescription = null,
                                                    tint = Color(0xFF34D399),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = Translations.get("menu_check_updates", selectedLanguage),
                                                    fontSize = 13.5.sp,
                                                    fontFamily = fontFam,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = if (selectedLanguage == "uz") "Yangi talqinni tekshirish, yuklab olish va o'rnatish (OTA)" else if (selectedLanguage == "ru") "Проверка, загрузка и установка обновлений" else "Check, download and install in-app updates",
                                                    fontSize = 11.5.sp,
                                                    fontFamily = fontFam,
                                                    color = secTextCol,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .oneUiGlassCapsule(
                                                        backgroundColor = Color(0xFF10B981).copy(alpha = 0.12f),
                                                        accentColor = Color(0xFF10B981).copy(alpha = 0.35f)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = null,
                                                    tint = Color(0xFF34D399),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = borderCol.copy(alpha = 0.25f))

                                    // Developer Options Shortcut Card (OneUI Frosted Glass Card)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .oneUiGlassCard(
                                                shape = RoundedCornerShape(18.dp),
                                                backgroundColor = cardBgCol.copy(alpha = 0.85f),
                                                borderColor = borderCol.copy(alpha = 0.35f),
                                                borderWidth = 1.2.dp
                                            )
                                            .clickable {
                                                try {
                                                    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    (context as? com.example.MainActivity)?.isBypassingLock = true
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    try {
                                                        val intent = Intent(Settings.ACTION_SETTINGS)
                                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        (context as? com.example.MainActivity)?.isBypassingLock = true
                                                        context.startActivity(intent)
                                                    } catch (ex: Exception) {}
                                                }
                                            }
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .oneUiGlassCapsule(
                                                        backgroundColor = accentCol.copy(alpha = 0.22f),
                                                        accentColor = accentCol.copy(alpha = 0.55f),
                                                        borderWidth = 1.dp
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Build,
                                                    contentDescription = null,
                                                    tint = accentCol,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = Translations.get("settings_dev_options_title", selectedLanguage),
                                                    fontSize = 13.5.sp,
                                                    fontFamily = fontFam,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = Translations.get("settings_dev_options_desc", selectedLanguage),
                                                    fontSize = 11.5.sp,
                                                    fontFamily = fontFam,
                                                    color = secTextCol,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }

                                    // WiFi / Wireless display Shortcut Card (OneUI Frosted Glass Card)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .oneUiGlassCard(
                                                shape = RoundedCornerShape(18.dp),
                                                backgroundColor = cardBgCol.copy(alpha = 0.85f),
                                                borderColor = borderCol.copy(alpha = 0.35f),
                                                borderWidth = 1.2.dp
                                            )
                                            .clickable {
                                                try {
                                                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    (context as? com.example.MainActivity)?.isBypassingLock = true
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {}
                                            }
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .oneUiGlassCapsule(
                                                        backgroundColor = accentCol.copy(alpha = 0.22f),
                                                        accentColor = accentCol.copy(alpha = 0.55f),
                                                        borderWidth = 1.dp
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Wifi,
                                                    contentDescription = null,
                                                    tint = accentCol,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = Translations.get("settings_wifi_settings_title", selectedLanguage),
                                                    fontSize = 13.5.sp,
                                                    fontFamily = fontFam,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = Translations.get("settings_wifi_settings_desc", selectedLanguage),
                                                    fontSize = 11.5.sp,
                                                    fontFamily = fontFam,
                                                    color = secTextCol,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }

                                    // Security Settings Card
                                    var securityLockEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("lock_enabled", false)) }
                                    var securityLockType by remember { mutableStateOf(sharedPrefs.getString("lock_type", "pin") ?: "pin") }
                                    var securityLockKeyInput by remember { mutableStateOf(sharedPrefs.getString("saved_lock_key", "1234") ?: "") }
                                    var lockDelayMs by remember { mutableStateOf(sharedPrefs.getLong("lock_delay_ms", 0L)) }

                                    var currentPinInput by remember { mutableStateOf("") }
                                     // PIN setup states for confirmation
                                     var pinSetupStage by remember { mutableStateOf(1) } // 1 = First input, 2 = Confirm
                                     var firstPinString by remember { mutableStateOf("") }
                                     var pinConfirmInput by remember { mutableStateOf("") }
                                     var pinSetupMessage by remember { mutableStateOf("") }
                                    var currentPasswordInput by remember { mutableStateOf("") }
                                     // Password setup states for confirmation
                                     var passwordSetupStage by remember { mutableStateOf(1) } // 1 = First input, 2 = Confirm
                                     var firstPasswordString by remember { mutableStateOf("") }
                                     var passwordConfirmInput by remember { mutableStateOf("") }
                                     var passwordSetupMessage by remember { mutableStateOf("") }

                                    // Pattern setup states
                                    var patternSetupStage by remember { mutableStateOf(1) } // 1 = First draw, 2 = Confirm
                                    var firstPatternString by remember { mutableStateOf("") }
                                    var patternSetupMessage by remember { mutableStateOf("") }
                                    val tempTouchedDots = remember { mutableStateListOf<Int>() }
                                    var tempDragX by remember { mutableStateOf(-1f) }
                                    var tempDragY by remember { mutableStateOf(-1f) }

                                     LaunchedEffect(securityLockType) {
                                         val activeSavedType = sharedPrefs.getString("lock_type", "pin") ?: "pin"
                                         val isCurrentlyActive = activeSavedType == securityLockType
                                         if (securityLockType == "pin") {
                                             currentPinInput = ""
                                             pinSetupStage = 1
                                             firstPinString = ""
                                             pinConfirmInput = ""
                                             pinSetupMessage = ""
                                         } else if (securityLockType == "password") {
                                             currentPasswordInput = ""
                                             passwordSetupStage = 1
                                             firstPasswordString = ""
                                             passwordConfirmInput = ""
                                             passwordSetupMessage = ""
                                         } else if (securityLockType == "pattern") {
                                             patternSetupStage = 1
                                             firstPatternString = ""
                                             patternSetupMessage = ""
                                             tempTouchedDots.clear()
                                         }
                                     }

                                    // Security Settings Card (OneUI Frosted Glass Card)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .oneUiGlassCard(
                                                shape = RoundedCornerShape(18.dp),
                                                backgroundColor = cardBgCol.copy(alpha = 0.85f),
                                                borderColor = borderCol.copy(alpha = 0.35f),
                                                borderWidth = 1.2.dp
                                            )
                                            .padding(14.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .oneUiGlassCapsule(
                                                                backgroundColor = accentCol.copy(alpha = 0.22f),
                                                                accentColor = accentCol.copy(alpha = 0.55f),
                                                                borderWidth = 1.dp
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Lock,
                                                            contentDescription = null,
                                                            tint = accentCol,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = if (selectedLanguage == "uz") "Xavfsizlik Himoyasi" else if (selectedLanguage == "ru") "Защита приложения" else "App Protection",
                                                        fontSize = 13.5.sp,
                                                        fontFamily = fontFam,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                                Switch(
                                                    checked = securityLockEnabled,
                                                    onCheckedChange = { isEnabled ->
                                                        securityLockEnabled = isEnabled
                                                        if (isEnabled) {
                                                            val curKey = sharedPrefs.getString("saved_lock_key", "") ?: ""
                                                            if (curKey.isEmpty()) {
                                                                sharedPrefs.edit()
                                                                    .putBoolean("lock_enabled", true)
                                                                    .putString("saved_lock_key", "1234")
                                                                    .putString("lock_type", "pin")
                                                                    .apply()
                                                                securityLockKeyInput = "1234"
                                                                securityLockType = "pin"
                                                            } else {
                                                                sharedPrefs.edit().putBoolean("lock_enabled", true).apply()
                                                            }
                                                        } else {
                                                            sharedPrefs.edit().putBoolean("lock_enabled", false).apply()
                                                        }
                                                    },
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = if (terminalTheme == "monochrome" || terminalTheme == "matrix") Color.Black else Color.White,
                                                        checkedTrackColor = accentCol
                                                    )
                                                )
                                            }

                                            if (securityLockEnabled) {
                                                HorizontalDivider(color = borderCol.copy(alpha = 0.15f))
                                                
                                                // Auto-lock duration settings
                                                Text(
                                                    text = if (selectedLanguage == "uz") "Avtomatik qulflash vaqti:" else if (selectedLanguage == "ru") "Время автоблокировки:" else "Auto-lock delay:",
                                                    fontSize = 11.5.sp,
                                                    fontFamily = fontFam,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White
                                                )

                                                val delayOptions = listOf(
                                                    0L to (if (selectedLanguage == "uz") "Darhol" else if (selectedLanguage == "ru") "Сразу" else "Immediately"),
                                                    60000L to "1 min",
                                                    120000L to "2 min",
                                                    300000L to "5 min",
                                                    600000L to "10 min",
                                                    900000L to "15 min",
                                                    1800000L to "30 min",
                                                    3600000L to "60 min"
                                                )

                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    // Row 1: take(4)
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        delayOptions.take(4).forEach { (delayVal, delayLabel) ->
                                                            val isSel = lockDelayMs == delayVal
                                                            Box(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .oneUiGlassCapsule(
                                                                        backgroundColor = if (isSel) accentCol.copy(alpha = 0.35f) else cardBgCol.copy(alpha = 0.45f),
                                                                        accentColor = if (isSel) accentCol else borderCol.copy(alpha = 0.25f),
                                                                        shape = CircleShape
                                                                    )
                                                                    .clickable {
                                                                        lockDelayMs = delayVal
                                                                        sharedPrefs.edit().putLong("lock_delay_ms", delayVal).apply()
                                                                    }
                                                                    .padding(vertical = 7.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = delayLabel,
                                                                    fontSize = 9.5.sp,
                                                                    fontFamily = fontFam,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isSel) (if (terminalTheme == "monochrome" || terminalTheme == "matrix") Color.Black else Color.White) else textCol
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // Row 2: drop(4)
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        delayOptions.drop(4).forEach { (delayVal, delayLabel) ->
                                                            val isSel = lockDelayMs == delayVal
                                                            Box(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .oneUiGlassCapsule(
                                                                        backgroundColor = if (isSel) accentCol.copy(alpha = 0.35f) else cardBgCol.copy(alpha = 0.45f),
                                                                        accentColor = if (isSel) accentCol else borderCol.copy(alpha = 0.25f),
                                                                        shape = CircleShape
                                                                    )
                                                                    .clickable {
                                                                        lockDelayMs = delayVal
                                                                        sharedPrefs.edit().putLong("lock_delay_ms", delayVal).apply()
                                                                    }
                                                                    .padding(vertical = 7.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = delayLabel,
                                                                    fontSize = 9.5.sp,
                                                                    fontFamily = fontFam,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isSel) (if (terminalTheme == "monochrome" || terminalTheme == "matrix") Color.Black else Color.White) else textCol
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                HorizontalDivider(color = borderCol.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))

                                                // Type selector
                                                Text(
                                                    text = if (selectedLanguage == "uz") "Himoya turi:" else if (selectedLanguage == "ru") "Тип защиты:" else "Protection Type:",
                                                    fontSize = 11.5.sp,
                                                    fontFamily = fontFam,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White
                                                )
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    listOf(
                                                        "pin" to "PIN", 
                                                        "password" to (if (selectedLanguage == "uz") "Parol" else if (selectedLanguage == "ru") "Пароль" else "Password"), 
                                                        "pattern" to (if (selectedLanguage == "uz") "Grafik" else if (selectedLanguage == "ru") "Графический ключ" else "Pattern"),
                                                        
                                                    ).forEach { (typeKey, typeLabel) ->
                                                        val isSel = securityLockType == typeKey
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .oneUiGlassCapsule(
                                                                    backgroundColor = if (isSel) accentCol.copy(alpha = 0.35f) else cardBgCol.copy(alpha = 0.45f),
                                                                    accentColor = if (isSel) accentCol else borderCol.copy(alpha = 0.25f),
                                                                    shape = CircleShape
                                                                )
                                                                .clickable {
                                                                    securityLockType = typeKey
                                                                }
                                                                .padding(vertical = 7.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = typeLabel,
                                                                fontSize = 10.sp,
                                                                fontFamily = fontFam,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isSel) (if (terminalTheme == "monochrome" || terminalTheme == "matrix") Color.Black else Color.White) else textCol
                                                            )
                                                        }
                                                    }
                                                }

                                                HorizontalDivider(color = borderCol.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))

                                                Spacer(modifier = Modifier.height(4.dp))

                                                when (securityLockType) {
                                                    "pin" -> {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                        ) {
                                                            Text(
                                                                text = if (pinSetupStage == 1) {
                                                                    if (selectedLanguage == "uz") "Yangi PIN kiriting (kamida 4 ta raqam):" 
                                                                    else if (selectedLanguage == "ru") "Введите новый PIN (минимум 4 цифры):" 
                                                                    else "Enter new PIN (min 4 digits):"
                                                                } else {
                                                                    if (selectedLanguage == "uz") "PIN Kodni tasdiqlang:" 
                                                                    else if (selectedLanguage == "ru") "Подтвердите PIN-код:" 
                                                                    else "Confirm PIN:"
                                                                },
                                                                fontSize = 11.sp,
                                                                fontFamily = fontFam,
                                                                fontWeight = FontWeight.Bold,
                                                                color = textCol
                                                            )

                                                            val currentLength = if (pinSetupStage == 1) currentPinInput.length else pinConfirmInput.length
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                                modifier = Modifier.padding(vertical = 6.dp)
                                                            ) {
                                                                val totalDots = maxOf(4, currentLength)
                                                                repeat(totalDots) { idx ->
                                                                    val filled = currentLength > idx
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(14.dp)
                                                                            .clip(CircleShape)
                                                                            .background(
                                                                                if (filled) accentCol else textCol.copy(alpha = 0.15f)
                                                                            )
                                                                            .border(1.5.dp, borderCol.copy(alpha = 0.55f), CircleShape)
                                                                    )
                                                                }
                                                            }

                                                            if (pinSetupMessage.isNotEmpty()) {
                                                                Text(
                                                                    text = pinSetupMessage,
                                                                    color = Color.Red,
                                                                    fontSize = 9.sp,
                                                                    fontFamily = fontFam,
                                                                    textAlign = TextAlign.Center
                                                                )
                                                            }

                                                            val keys = listOf(
                                                                listOf("1", "2", "3"),
                                                                listOf("4", "5", "6"),
                                                                listOf("7", "8", "9"),
                                                                listOf("DEL", "0", "✔")
                                                            )

                                                            Column(
                                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                                horizontalAlignment = Alignment.CenterHorizontally
                                                             ) {
                                                                keys.forEach { rowKeys ->
                                                                    Row(
                                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                                    ) {
                                                                        rowKeys.forEach { key ->
                                                                            Box(
                                                                                modifier = Modifier
                                                                                    .size(width = 54.dp, height = 36.dp)
                                                                                    .clip(RoundedCornerShape(6.dp))
                                                                                    .background(cardBgCol)
                                                                                    .border(1.5.dp, borderCol.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                                                                    .clickable {
                                                                                        val activeInput = if (pinSetupStage == 1) currentPinInput else pinConfirmInput
                                                                                        when (key) {
                                                                                            "DEL" -> {
                                                                                                if (activeInput.isNotEmpty()) {
                                                                                                    if (pinSetupStage == 1) currentPinInput = currentPinInput.dropLast(1)
                                                                                                    else pinConfirmInput = pinConfirmInput.dropLast(1)
                                                                                                }
                                                                                            }
                                                                                            "✔" -> {
                                                                                                if (pinSetupStage == 1) {
                                                                                                    if (currentPinInput.length >= 4) {
                                                                                                        firstPinString = currentPinInput
                                                                                                        pinConfirmInput = ""
                                                                                                        pinSetupStage = 2
                                                                                                        pinSetupMessage = ""
                                                                                                    } else {
                                                                                                        pinSetupMessage = if (selectedLanguage == "uz") "Xato: Kamida 4 ta raqam!" else if (selectedLanguage == "ru") "Ошибка: минимум 4 цифры!" else "Error: Min 4 digits!"
                                                                                                    }
                                                                                                } else {
                                                                                                    if (pinConfirmInput == firstPinString) {
                                                                                                        sharedPrefs.edit()
                                                                                                            .putString("saved_lock_key", pinConfirmInput)
                                                                                                            .putString("lock_type", "pin")
                                                                                                            .apply()
                                                                                                        securityLockKeyInput = pinConfirmInput
                                                                                                        val savedMsg = if (selectedLanguage == "uz") "PIN Kod muvaffaqiyatli saqlandi!" else if (selectedLanguage == "ru") "PIN-код успешно сохранен!" else "PIN Saved successfully!"
                                                                                                        android.widget.Toast.makeText(context, savedMsg, android.widget.Toast.LENGTH_SHORT).show()
                                                                                                        pinSetupStage = 1
                                                                                                        currentPinInput = ""
                                                                                                        pinConfirmInput = ""
                                                                                                    } else {
                                                                                                        pinSetupMessage = if (selectedLanguage == "uz") "Xato: Kodlar mos kelmadi, boshidan boshlang!" else if (selectedLanguage == "ru") "Ошибка: PIN-коды не совпадают, начните заново!" else "Error: PINs did not match!"
                                                                                                        pinSetupStage = 1
                                                                                                        currentPinInput = ""
                                                                                                        pinConfirmInput = ""
                                                                                                    }
                                                                                                }
                                                                                            }
                                                                                            else -> {
                                                                                                if (activeInput.length < 16) {
                                                                                                    if (pinSetupStage == 1) currentPinInput += key
                                                                                                    else pinConfirmInput += key
                                                                                                }
                                                                                            }
                                                                                        }
                                                                                    },
                                                                                contentAlignment = Alignment.Center
                                                                            ) {
                                                                                if (key == "DEL") {
                                                                                    Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = textCol, modifier = Modifier.size(16.dp))
                                                                                } else if (key == "✔") {
                                                                                    Icon(Icons.Default.Check, contentDescription = "Confirm", tint = accentCol, modifier = Modifier.size(18.dp))
                                                                                } else {
                                                                                    Text(
                                                                                        text = key,
                                                                                        fontFamily = fontFam,
                                                                                        fontSize = 14.sp,
                                                                                        fontWeight = FontWeight.Bold,
                                                                                        color = textCol
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    "disabled_old_pin" -> {
                                                         if (pinSetupStage == 1) {
                                                             Text(
                                                                 text = if (selectedLanguage == "uz") "PIN Kodni kiriting (kamida 4 ta raqam):" else if (selectedLanguage == "ru") "Введите PIN-код (минимум 4 цифры):" else "Enter PIN (min 4 digits):",
                                                                 fontSize = 10.sp,
                                                                 fontFamily = fontFam,
                                                                 color = secTextCol
                                                             )

                                                             BasicTextField(
                                                                 value = currentPinInput,
                                                                 onValueChange = { newVal ->
                                                                     val filtered = newVal.filter { it.isDigit() }
                                                                     if (filtered.length <= 16) {
                                                                         currentPinInput = filtered
                                                                     }
                                                                 },
                                                                 modifier = Modifier
                                                                     .fillMaxWidth()
                                                                     .background(cardBgCol, RoundedCornerShape(6.dp))
                                                                     .border(1.5.dp, borderCol.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                                                     .padding(horizontal = 8.dp, vertical = 6.dp),
                                                                 textStyle = androidx.compose.ui.text.TextStyle(
                                                                     fontFamily = fontFam,
                                                                     fontSize = 11.sp,
                                                                     color = textCol
                                                                 )
                                                             )

                                                             if (currentPinInput.isNotEmpty() && currentPinInput.length < 4) {
                                                                 Text(
                                                                     text = if (selectedLanguage == "uz") "Xato: Kamida 4 ta raqam bo'lishi shart!" else if (selectedLanguage == "ru") "Ошибка: минимум 4 цифры!" else "Error: Min 4 digits required!",
                                                                     color = Color.Red,
                                                                     fontSize = 9.sp,
                                                                     fontFamily = fontFam
                                                                 )
                                                             }

                                                             if (pinSetupMessage.isNotEmpty()) {
                                                                 Text(
                                                                     text = pinSetupMessage,
                                                                     color = Color.Red,
                                                                     fontSize = 9.sp,
                                                                     fontFamily = fontFam
                                                                 )
                                                             }

                                                             Button(
                                                                 onClick = {
                                                                     if (currentPinInput.length >= 4) {
                                                                         firstPinString = currentPinInput
                                                                         pinConfirmInput = ""
                                                                         pinSetupStage = 2
                                                                         pinSetupMessage = ""
                                                                     }
                                                                 },
                                                                 enabled = currentPinInput.length >= 4,
                                                                 colors = ButtonDefaults.buttonColors(
                                                                     containerColor = accentCol.copy(alpha = 0.5f),
                                                                     disabledContainerColor = accentCol.copy(alpha = 0.2f)
                                                                 ),
                                                                 modifier = Modifier.fillMaxWidth().height(36.dp).glassCapsule(),
                                                                 contentPadding = PaddingValues(0.dp),
                                                                 shape = CircleShape
                                                             ) {
                                                                 Text(
                                                                     text = if (selectedLanguage == "uz") "Keyingisi (Tasdiqlash)" else if (selectedLanguage == "ru") "Далее (Подтвердить)" else "Next (Confirm)",
                                                                     fontSize = 11.sp,
                                                                     fontFamily = fontFam,
                                                                     fontWeight = FontWeight.Bold,
                                                                     color = if (terminalTheme == "monochrome" || terminalTheme == "matrix") Color.Black else Color.White
                                                                 )
                                                             }
                                                         } else {
                                                             Text(
                                                                 text = if (selectedLanguage == "uz") "PIN Kodni tasdiqlang (qaytadan kiriting):" else if (selectedLanguage == "ru") "Подтвердите PIN-код (введите еще раз):" else "Confirm PIN (enter again):",
                                                                 fontSize = 10.sp,
                                                                 fontFamily = fontFam,
                                                                 color = secTextCol
                                                             )

                                                             BasicTextField(
                                                                 value = pinConfirmInput,
                                                                 onValueChange = { newVal ->
                                                                     val filtered = newVal.filter { it.isDigit() }
                                                                     if (filtered.length <= 16) {
                                                                         pinConfirmInput = filtered
                                                                     }
                                                                 },
                                                                 modifier = Modifier
                                                                     .fillMaxWidth()
                                                                     .background(cardBgCol, RoundedCornerShape(6.dp))
                                                                     .border(1.5.dp, borderCol.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                                                     .padding(horizontal = 8.dp, vertical = 6.dp),
                                                                 textStyle = androidx.compose.ui.text.TextStyle(
                                                                     fontFamily = fontFam,
                                                                     fontSize = 11.sp,
                                                                     color = textCol
                                                                 )
                                                             )

                                                             if (pinConfirmInput.isNotEmpty() && pinConfirmInput != firstPinString && pinConfirmInput.length >= firstPinString.length) {
                                                                 Text(
                                                                     text = if (selectedLanguage == "uz") "Xato: PIN-kodlar mos kelmadi!" else if (selectedLanguage == "ru") "Ошибка: PIN-коды не совпадают!" else "Error: PINs do not match!",
                                                                     color = Color.Red,
                                                                     fontSize = 9.sp,
                                                                     fontFamily = fontFam
                                                                 )
                                                             }

                                                             Row(
                                                                 horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                 modifier = Modifier.fillMaxWidth()
                                                             ) {
                                                                 Button(
                                                                     onClick = {
                                                                         pinSetupStage = 1
                                                                         currentPinInput = ""
                                                                         pinConfirmInput = ""
                                                                     },
                                                                     colors = ButtonDefaults.buttonColors(containerColor = cardBgCol.copy(alpha = 0.5f)),
                                                                     shape = CircleShape,
                                                                     modifier = Modifier.weight(1f).height(36.dp).glassCapsule(),
                                                                     contentPadding = PaddingValues(0.dp)
                                                                 ) {
                                                                     Text(
                                                                         text = if (selectedLanguage == "uz") "Orqaga" else if (selectedLanguage == "ru") "Назад" else "Back",
                                                                         fontSize = 11.sp,
                                                                         fontFamily = fontFam,
                                                                         color = textCol
                                                                     )
                                                                 }

                                                                 Button(
                                                                     onClick = {
                                                                         if (pinConfirmInput == firstPinString) {
                                                                             sharedPrefs.edit()
                                                                                 .putString("saved_lock_key", pinConfirmInput)
                                                                                 .putString("lock_type", "pin")
                                                                                 .apply()
                                                                             securityLockKeyInput = pinConfirmInput
                                                                             val savedMsg = if (selectedLanguage == "uz") "PIN Kod muvaffaqiyatli saqlandi!" else if (selectedLanguage == "ru") "PIN-код успешно сохранен!" else "PIN Saved successfully!"
                                                                             android.widget.Toast.makeText(context, savedMsg, android.widget.Toast.LENGTH_SHORT).show()
                                                                             pinSetupStage = 1
                                                                             currentPinInput = ""
                                                                         } else {
                                                                             pinSetupMessage = if (selectedLanguage == "uz") "Xato: Kodlar mos kelmadi, qaytadan urinib ko'ring!" else if (selectedLanguage == "ru") "Ошибка: PIN-коды не совпадают!" else "Error: PINs did not match!"
                                                                             pinSetupStage = 1
                                                                             currentPinInput = ""
                                                                             pinConfirmInput = ""
                                                                         }
                                                                     },
                                                                     enabled = pinConfirmInput.length >= 4,
                                                                     colors = ButtonDefaults.buttonColors(containerColor = accentCol.copy(alpha = 0.5f)),
                                                                     modifier = Modifier.weight(1.5f).height(36.dp).glassCapsule(),
                                                                     contentPadding = PaddingValues(0.dp),
                                                                     shape = CircleShape
                                                                 ) {
                                                                     Text(
                                                                         text = if (selectedLanguage == "uz") "Tasdiqlash va Saqlash" else if (selectedLanguage == "ru") "Подтвердить и Сохранить" else "Confirm & Save",
                                                                         fontSize = 11.sp,
                                                                         fontFamily = fontFam,
                                                                         fontWeight = FontWeight.Bold,
                                                                         color = if (terminalTheme == "monochrome" || terminalTheme == "matrix") Color.Black else Color.White
                                                                     )
                                                                 }
                                                             }
                                                         }
                                                     }
                                                     "password" -> {
                                                         if (passwordSetupStage == 1) {
                                                             Text(
                                                                 text = if (selectedLanguage == "uz") "Parolni kiriting (kamida 8 ta belgi):" else if (selectedLanguage == "ru") "Введите пароль (минимум 8 символов):" else "Enter Password (min 8 chars):",
                                                                 fontSize = 10.sp,
                                                                 fontFamily = fontFam,
                                                                 color = secTextCol
                                                             )

                                                             BasicTextField(
                                                                 value = currentPasswordInput,
                                                                 onValueChange = { currentPasswordInput = it },
                                                                 modifier = Modifier
                                                                     .fillMaxWidth()
                                                                     .background(cardBgCol, RoundedCornerShape(6.dp))
                                                                     .border(1.5.dp, borderCol.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                                                     .padding(horizontal = 8.dp, vertical = 6.dp),
                                                                 textStyle = androidx.compose.ui.text.TextStyle(
                                                                     fontFamily = fontFam,
                                                                     fontSize = 11.sp,
                                                                     color = textCol
                                                                 )
                                                             )

                                                             if (currentPasswordInput.isNotEmpty() && currentPasswordInput.length < 8) {
                                                                 Text(
                                                                     text = if (selectedLanguage == "uz") "Xato: Kamida 8 ta belgi bo'lishi shart!" else if (selectedLanguage == "ru") "Ошибка: минимум 8 символов!" else "Error: Min 8 characters required!",
                                                                     color = Color.Red,
                                                                     fontSize = 9.sp,
                                                                     fontFamily = fontFam
                                                                 )
                                                             }

                                                             if (passwordSetupMessage.isNotEmpty()) {
                                                                 Text(
                                                                     text = passwordSetupMessage,
                                                                     color = Color.Red,
                                                                     fontSize = 9.sp,
                                                                     fontFamily = fontFam
                                                                 )
                                                             }

                                                             Button(
                                                                 onClick = {
                                                                     if (currentPasswordInput.length >= 8) {
                                                                         firstPasswordString = currentPasswordInput
                                                                         passwordConfirmInput = ""
                                                                         passwordSetupStage = 2
                                                                         passwordSetupMessage = ""
                                                                     }
                                                                 },
                                                                 enabled = currentPasswordInput.length >= 8,
                                                                 colors = ButtonDefaults.buttonColors(
                                                                     containerColor = accentCol,
                                                                     disabledContainerColor = accentCol.copy(alpha = 0.3f)
                                                                 ),
                                                                 modifier = Modifier.fillMaxWidth().height(36.dp),
                                                                 contentPadding = PaddingValues(0.dp),
                                                                 shape = RoundedCornerShape(6.dp)
                                                             ) {
                                                                 Text(
                                                                     text = if (selectedLanguage == "uz") "Keyingisi (Tasdiqlash)" else if (selectedLanguage == "ru") "Далее (Подтвердить)" else "Next (Confirm)",
                                                                     fontSize = 11.sp,
                                                                     fontFamily = fontFam,
                                                                     fontWeight = FontWeight.Bold,
                                                                     color = if (terminalTheme == "monochrome" || terminalTheme == "matrix") Color.Black else Color.White
                                                                 )
                                                             }
                                                         } else {
                                                             Text(
                                                                 text = if (selectedLanguage == "uz") "Parolni tasdiqlang (qaytadan kiriting):" else if (selectedLanguage == "ru") "Подтвердите пароль (введите еще раз):" else "Confirm Password (enter again):",
                                                                 fontSize = 10.sp,
                                                                 fontFamily = fontFam,
                                                                 color = secTextCol
                                                             )

                                                             BasicTextField(
                                                                 value = passwordConfirmInput,
                                                                 onValueChange = { passwordConfirmInput = it },
                                                                 modifier = Modifier
                                                                     .fillMaxWidth()
                                                                     .background(cardBgCol, RoundedCornerShape(6.dp))
                                                                     .border(1.5.dp, borderCol.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                                                     .padding(horizontal = 8.dp, vertical = 6.dp),
                                                                 textStyle = androidx.compose.ui.text.TextStyle(
                                                                     fontFamily = fontFam,
                                                                     fontSize = 11.sp,
                                                                     color = textCol
                                                                 )
                                                             )

                                                             if (passwordConfirmInput.isNotEmpty() && passwordConfirmInput != firstPasswordString && passwordConfirmInput.length >= firstPasswordString.length) {
                                                                 Text(
                                                                     text = if (selectedLanguage == "uz") "Xato: Parollar mos kelmadi!" else if (selectedLanguage == "ru") "Ошибка: пароли не совпадают!" else "Error: Passwords do not match!",
                                                                     color = Color.Red,
                                                                     fontSize = 9.sp,
                                                                     fontFamily = fontFam
                                                                 )
                                                             }

                                                             Row(
                                                                 horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                 modifier = Modifier.fillMaxWidth()
                                                             ) {
                                                                 Button(
                                                                     onClick = {
                                                                         passwordSetupStage = 1
                                                                         currentPasswordInput = ""
                                                                         passwordConfirmInput = ""
                                                                     },
                                                                     colors = ButtonDefaults.buttonColors(containerColor = cardBgCol.copy(alpha = 0.5f)),
                                                                     shape = CircleShape,
                                                                     modifier = Modifier.weight(1f).height(36.dp).glassCapsule(),
                                                                     contentPadding = PaddingValues(0.dp)
                                                                 ) {
                                                                     Text(
                                                                         text = if (selectedLanguage == "uz") "Orqaga" else if (selectedLanguage == "ru") "Назад" else "Back",
                                                                         fontSize = 11.sp,
                                                                         fontFamily = fontFam,
                                                                         color = textCol
                                                                     )
                                                                 }

                                                                 Button(
                                                                     onClick = {
                                                                         if (passwordConfirmInput == firstPasswordString) {
                                                                             sharedPrefs.edit()
                                                                                 .putString("saved_lock_key", passwordConfirmInput)
                                                                                 .putString("lock_type", "password")
                                                                                 .apply()
                                                                             securityLockKeyInput = passwordConfirmInput
                                                                             val savedMsg = if (selectedLanguage == "uz") "Parol muvaffaqiyatli saqlandi!" else if (selectedLanguage == "ru") "Пароль успешно сохранен!" else "Password Saved successfully!"
                                                                             android.widget.Toast.makeText(context, savedMsg, android.widget.Toast.LENGTH_SHORT).show()
                                                                             passwordSetupStage = 1
                                                                             currentPasswordInput = ""
                                                                         } else {
                                                                             passwordSetupMessage = if (selectedLanguage == "uz") "Xato: Parollar mos kelmadi, qaytadan urinib ko'ring!" else if (selectedLanguage == "ru") "Ошибка: пароли не совпадают!" else "Error: Passwords did not match!"
                                                                             passwordSetupStage = 1
                                                                             currentPasswordInput = ""
                                                                             passwordConfirmInput = ""
                                                                         }
                                                                     },
                                                                     enabled = passwordConfirmInput.length >= 8,
                                                                     colors = ButtonDefaults.buttonColors(containerColor = accentCol.copy(alpha = 0.5f)),
                                                                     modifier = Modifier.weight(1.5f).height(36.dp).glassCapsule(),
                                                                     contentPadding = PaddingValues(0.dp),
                                                                     shape = CircleShape
                                                                 ) {
                                                                     Text(
                                                                         text = if (selectedLanguage == "uz") "Tasdiqlash va Saqlash" else if (selectedLanguage == "ru") "Подтвердить и Сохранить" else "Confirm & Save",
                                                                         fontSize = 11.sp,
                                                                         fontFamily = fontFam,
                                                                         fontWeight = FontWeight.Bold,
                                                                         color = if (terminalTheme == "monochrome" || terminalTheme == "matrix") Color.Black else Color.White
                                                                     )
                                                                 }
                                                             }
                                                         }
                                                     }
                                                     "pattern" -> {
                                                        val stateMsg = if (patternSetupMessage.isNotEmpty()) {
                                                            patternSetupMessage
                                                        } else {
                                                            if (patternSetupStage == 1) {
                                                                 if (selectedLanguage == "uz") "Grafik kalitni chizib o'rnating:" else if (selectedLanguage == "ru") "Нарисуйте графический ключ для установки:" else "Draw your pattern to set:"
                                                            } else {
                                                                 if (selectedLanguage == "uz") "Tasdiqlash uchun qaytadan chizing:" else if (selectedLanguage == "ru") "Нарисуйте еще раз для подтверждения:" else "Draw again to confirm:"
                                                            }
                                                        }

                                                        Text(
                                                            text = stateMsg,
                                                            fontSize = 10.sp,
                                                            fontFamily = fontFam,
                                                            color = if (stateMsg.contains("Xato") || stateMsg.contains("Error") || stateMsg.contains("Mos kelmadi") || stateMsg.contains("did not match") || stateMsg.contains("didn't match")) Color.Red else accentCol,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.fillMaxWidth(),
                                                            textAlign = TextAlign.Center
                                                        )

                                                        Spacer(modifier = Modifier.height(6.dp))

                                                        val gridSize = 180.dp
                                                        Box(
                                                            modifier = Modifier
                                                                .size(gridSize)
                                                                .align(Alignment.CenterHorizontally)
                                                                .background(cardBgCol.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                                                .border(1.8.dp, borderCol.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                                                .pointerInput(Unit) {
                                                                    detectDragGestures(
                                                                        onDragStart = { offset ->
                                                                            tempTouchedDots.clear()
                                                                            val hit = detectHitDotLocal(offset, size.width.toFloat(), size.height.toFloat())
                                                                            if (hit != null) tempTouchedDots.add(hit)
                                                                        },
                                                                        onDrag = { change, _ ->
                                                                            val position = change.position
                                                                            tempDragX = position.x
                                                                            tempDragY = position.y
                                                                            val hit = detectHitDotLocal(position, size.width.toFloat(), size.height.toFloat())
                                                                            if (hit != null && !tempTouchedDots.contains(hit)) {
                                                                                tempTouchedDots.add(hit)
                                                                            }
                                                                        },
                                                                        onDragEnd = {
                                                                            if (tempTouchedDots.size < 4) {
                                                                                patternSetupMessage = if (selectedLanguage == "uz") "Xato: Kamida 4 ta nuqtani birlashtiring!" else if (selectedLanguage == "ru") "Ошибка: соедините минимум 4 точки!" else "Error: Connect at least 4 dots!"
                                                                                tempTouchedDots.clear()
                                                                            } else {
                                                                                val drawnStr = tempTouchedDots.joinToString(",")
                                                                                if (patternSetupStage == 1) {
                                                                                    firstPatternString = drawnStr
                                                                                    patternSetupStage = 2
                                                                                    patternSetupMessage = if (selectedLanguage == "uz") "Yaxshi! Endi tasdiqlash uchun xuddi shu grafik kalitni qaytadan chizing:" else if (selectedLanguage == "ru") "Отлично! Теперь нарисуйте тот же графический ключ для подтверждения:" else "Good! Draw the exact same pattern to confirm:"
                                                                                } else {
                                                                                    if (drawnStr == firstPatternString) {
                                                                                        sharedPrefs.edit()
                                                                                            .putString("saved_lock_key", drawnStr)
                                                                                            .putString("lock_type", "pattern")
                                                                                            .apply()
                                                                                        securityLockKeyInput = drawnStr
                                                                                        patternSetupMessage = if (selectedLanguage == "uz") "Muvaffaqiyatli saqlandi!" else if (selectedLanguage == "ru") "Успешно сохранено!" else "Pattern saved successfully!"
                                                                                        patternSetupStage = 1
                                                                                        firstPatternString = ""
                                                                                        val successMsg = if (selectedLanguage == "uz") "Grafik kalit o'rnatildi!" else if (selectedLanguage == "ru") "Графический ключ установлен!" else "Pattern Lock set successfully!"
                                                                                        android.widget.Toast.makeText(context, successMsg, android.widget.Toast.LENGTH_SHORT).show()
                                                                                    } else {
                                                                                        patternSetupMessage = if (selectedLanguage == "uz") "Xato: Grafik kalit mos kelmadi! Qaytadan boshidan chizing:" else if (selectedLanguage == "ru") "Ошибка: графические ключи не совпадают! Нарисуйте заново:" else "Error: Patterns didn't match! Draw again from step 1:"
                                                                                        patternSetupStage = 1
                                                                                        firstPatternString = ""
                                                                                    }
                                                                                }
                                                                                tempTouchedDots.clear()
                                                                            }
                                                                            tempDragX = -1f
                                                                            tempDragY = -1f
                                                                        }
                                                                    )
                                                                }
                                                                .drawBehind {
                                                                    val stepX = size.width / 4f
                                                                    val stepY = size.height / 4f
                                                                    val path = Path()
                                                                    tempTouchedDots.forEachIndexed { index, dot ->
                                                                        val row = dot / 3
                                                                        val col = dot % 3
                                                                        val cx = (col + 1) * stepX
                                                                        val cy = (row + 1) * stepY
                                                                        if (index == 0) {
                                                                            path.moveTo(cx, cy)
                                                                        } else {
                                                                            path.lineTo(cx, cy)
                                                                        }
                                                                    }
                                                                    if (tempTouchedDots.isNotEmpty() && tempDragX != -1f) {
                                                                        path.lineTo(tempDragX, tempDragY)
                                                                    }
                                                                    if (tempTouchedDots.isNotEmpty()) {
                                                                        drawPath(
                                                                            path = path,
                                                                            color = accentCol,
                                                                            style = Stroke(width = 4.dp.toPx())
                                                                        )
                                                                    }
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Column(
                                                                verticalArrangement = Arrangement.SpaceEvenly,
                                                                modifier = Modifier.fillMaxSize()
                                                            ) {
                                                                for (row in 0..2) {
                                                                    Row(
                                                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    ) {
                                                                        for (col in 0..2) {
                                                                            val index = row * 3 + col
                                                                            val isSelected = tempTouchedDots.contains(index)
                                                                            Box(
                                                                                modifier = Modifier
                                                                                    .size(16.dp)
                                                                                    .clip(CircleShape)
                                                                                    .background(
                                                                                        if (isSelected) accentCol else textCol.copy(alpha = 0.15f)
                                                                                    )
                                                                                    .border(
                                                                                        width = 1.8.dp,
                                                                                        color = if (isSelected) accentCol else borderCol.copy(alpha = 0.5f),
                                                                                        shape = CircleShape
                                                                                    )
                                                                                    .border(
                                                                                        width = 1.8.dp,
                                                                                        color = if (isSelected) accentCol else borderCol.copy(alpha = 0.5f),
                                                                                        shape = CircleShape
                                                                                    )
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    "biometric" -> {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Fingerprint,
                                                                contentDescription = "Biometric Setup",
                                                                tint = accentCol,
                                                                modifier = Modifier.size(48.dp)
                                                            )

                                                            Text(
                                                                text = if (selectedLanguage == "uz") 
                                                                    "Qurilmangizdagi barmoq izi yoki yuz skanerlash tizimini ilovaga qulf sifatida o'rnatish." 
                                                                    else "Активируйте биометрическую защиту (отпечаток пальца) для вашего приложения.",
                                                                fontSize = 10.sp,
                                                                fontFamily = fontFam,
                                                                color = secTextCol,
                                                                textAlign = TextAlign.Center,
                                                                modifier = Modifier.padding(horizontal = 8.dp)
                                                            )

                                                            Button(
                                                                onClick = {
                                                                    sharedPrefs.edit().putString("saved_lock_key", "biometric_registered").apply()
                                                                    securityLockKeyInput = "biometric_registered"
                                                                    val savedMsg = if (selectedLanguage == "uz") "Biometrik ma'lumot qabul qilindi!" else "Biometric protection activated!"
                                                                    android.widget.Toast.makeText(context, savedMsg, android.widget.Toast.LENGTH_SHORT).show()
                                                                },
                                                                colors = ButtonDefaults.buttonColors(containerColor = accentCol.copy(alpha = 0.5f)),
                                                                modifier = Modifier.fillMaxWidth().height(36.dp).glassCapsule(),
                                                                contentPadding = PaddingValues(0.dp),
                                                                shape = CircleShape
                                                            ) {
                                                                Text(
                                                                    text = if (selectedLanguage == "uz") "Biometriyani Faollashtirish" else "Activate Biometrics",
                                                                    fontSize = 11.sp,
                                                                    fontFamily = fontFam,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color.White
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Bottom Reset buttons removed per user request
                                }
                            }
                            1 -> {
                                ScriptsScreen(
                                    savedScripts = savedScripts,
                                    isConnected = isConnected,
                                    selectedLanguage = selectedLanguage,
                                    onRunScript = { command ->
                                        viewModel.executeTerminalCommand(command)
                                        onDismiss()
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Skript Terminalda ishga tushirildi!")
                                        }
                                    },
                                    onAddScript = { title, command, desc, cat ->
                                        viewModel.saveCustomScript(title, command, desc, cat)
                                    },
                                    onDeleteScript = { viewModel.deleteScript(it) }
                                )
                            }
                            2 -> {
                                HistoryScreen(
                                    historyList = commandHistory,
                                    selectedLanguage = selectedLanguage,
                                    onRerunCommand = { command ->
                                        viewModel.executeTerminalCommand(command)
                                        onDismiss()
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Buyruq qayta ishlatildi!")
                                        }
                                    },
                                    onClearHistory = { viewModel.clearHistory() }
                                )
                            }
                            3 -> {
                                DeviceInfoScreen(
                                    deviceInfo = deviceInfo,
                                    isConnected = isConnected,
                                    selectedLanguage = selectedLanguage,
                                    onRefreshInfo = {
                                        if (isConnected) {
                                            viewModel.fetchBasicDeviceInfo()
                                        } else {
                                            viewModel.loadLocalDeviceInfo()
                                        }
                                    }
                                )
                            }
                            4 -> {
                                ComputerPermissionsScreen(selectedLanguage)
                            }
                        }
                    }

                    // Bottom Dismiss Bar completely removed per user request
                }
            }
        }
        }
    }
}

@Composable
fun ComputerPermissionsScreen(selectedLanguage: String) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    val titleDevices = if (selectedLanguage == "uz") "Mavjud qurilmalarni ko'rish (Devices)" else if (selectedLanguage == "ru") "Список устройств (Devices)" else "Check Devices List"
    val titleTcpip = if (selectedLanguage == "uz") "Simsiz portni faollashtirish (Port 5555)" else if (selectedLanguage == "ru") "Активация беспроводного порта (Port 5555)" else "Activate TCP Wireless Port 5555"
    val titleConnect = if (selectedLanguage == "uz") "Mahalliy simsiz ulanish (Net Connect)" else if (selectedLanguage == "ru") "Локальное подключение через сеть" else "Local ADB Network Connect"

    val commands = listOf(
        titleDevices to "adb devices",
        titleTcpip to "adb tcpip 5555",
        titleConnect to "adb connect 127.0.0.1:5555"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Title card removed per user request

        commands.forEach { (name, command) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(command))
                        val copiedMsg = when (selectedLanguage) {
                            "uz" -> "Nusxalandi: $command"
                            "ru" -> "Скопировано: $command"
                            else -> "Copied: $command"
                        }
                        android.widget.Toast.makeText(context, copiedMsg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        IconButton(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(command))
                                val copiedMsg = when (selectedLanguage) {
                                    "uz" -> "Nusxalandi: $command"
                                    "ru" -> "Скопировано: $command"
                                    else -> "Copied: $command"
                                }
                                android.widget.Toast.makeText(context, copiedMsg, android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Kopiya", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF231F20), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = command,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFFC5C8C6)
                        )
                    }
                }
            }
        }

        // Bottom copy commands removed per user request
    }
}

@Composable
fun MatrixRainOverlay(
    onDismiss: () -> Unit
) {
    androidx.activity.compose.BackHandler {
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                onClick = onDismiss,
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            )
    ) {
        MatrixRainAnimation()
    }
}

@Composable
fun MatrixRainAnimation() {
    val characters = listOf('0', '1')
    var columnsState by remember { mutableStateOf<List<MatrixColumn>>(emptyList()) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val fontSize = 42f // Font size in pixels for beautiful dense layout
        val columnsCount = (width / fontSize).toInt().coerceAtLeast(1)

        // Initialize columns
        LaunchedEffect(columnsCount) {
            columnsState = List(columnsCount) {
                MatrixColumn(
                    x = it * fontSize + (fontSize / 4f),
                    y = (Math.random() * -height).toFloat(),
                    speed = (15 + Math.random() * 30).toFloat(),
                    chars = List(25) { characters.random() },
                    activeCharIndex = 0
                )
            }
        }

        // Animation ticker loop
        LaunchedEffect(Unit) {
            while (true) {
                // Approximate 30 FPS tick
                columnsState = columnsState.map { col ->
                    var newY = col.y + col.speed * 0.45f
                    val newActiveIndex = col.activeCharIndex
                    
                    // Mutate text data
                    val newChars = col.chars.toMutableList()
                    if (Math.random() < 0.20) {
                        val randIdx = (0 until newChars.size).random()
                        newChars[randIdx] = characters.random()
                    }
                    
                    if (newY > height) {
                        newY = (Math.random() * -400).toFloat() - fontSize
                    }
                    
                    col.copy(
                        y = newY,
                        chars = newChars,
                        activeCharIndex = (newActiveIndex + 1) % col.chars.size
                    )
                }
                kotlinx.coroutines.delay(33)
            }
        }

        val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
        val textStyle = androidx.compose.ui.text.TextStyle(
            color = Color(0xFF33FF33),
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            columnsState.forEach { col ->
                for (i in col.chars.indices) {
                    val charY = col.y - (i * fontSize)
                    if (charY < -fontSize || charY > height) continue
                    
                    // Tails are dimmer and fade out beautifully
                    val alpha = (1.0f - (i.toFloat() / col.chars.size)).coerceIn(0f, 1f)
                    
                    // Leading head characters glow white or neon green
                    val colColor = if (i == 0) {
                        Color.White
                    } else if (i < 3) {
                        Color(0xFF88FF88)
                    } else {
                        Color(0xFF33FF33)
                    }
                    
                    val textLayoutResult = textMeasurer.measure(
                        text = col.chars[i].toString(),
                        style = textStyle.copy(color = colColor.copy(alpha = alpha))
                    )
                    
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = androidx.compose.ui.geometry.Offset(col.x, charY)
                    )
                }
            }
        }
    }
}

data class MatrixColumn(
    val x: Float,
    val y: Float,
    val speed: Float,
    val chars: List<Char>,
    val activeCharIndex: Int
)
