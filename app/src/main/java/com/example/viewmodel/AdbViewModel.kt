package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.adb.AdbClient
import com.example.adb.device.AdbDevice
import com.example.adb.device.AdbDeviceManager
import com.example.adb.device.ConnectionType
import com.example.adb.device.DeviceStatus
import com.example.adb.device.MultiCommandResult
import com.example.db.AdbDatabase
import com.example.db.HistoryEntity
import com.example.db.ScriptEntity
import com.example.gemini.GeminiClient
import com.example.terminal.ArchiveEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

// Tab Data model
data class TerminalTab(
    val id: String,
    val title: String,
    val terminalOutput: String,
    val commandInput: String,
    val workingDir: String = "",
    val isPythonActive: Boolean = false,
    val isPythonMultiline: Boolean = false,
    val isRunning: Boolean = false
)

class AdbViewModel(application: Application) : AndroidViewModel(application) {

    val safPermissionRefreshTrigger = MutableStateFlow(0)
    val appUpdateManager = com.example.updater.AppUpdateManager.getInstance(application)
    val showAppUpdateDialog = MutableStateFlow(false)

    private val adbDao = AdbDatabase.getDatabase(application).adbDao()
    private val adbClient = com.example.adb.AdbClientHolder.getClient(application)

    // ADB Multi-Device Connection Manager
    val deviceManager = AdbDeviceManager(application, adbClient, viewModelScope)
    val devices: StateFlow<List<AdbDevice>> = deviceManager.devices
    val activeDeviceId: StateFlow<String> = deviceManager.activeDeviceId
    val isMultiDeviceMode: StateFlow<Boolean> = deviceManager.isMultiDeviceMode
    val isExecutingMulti: StateFlow<Boolean> = deviceManager.isExecutingMulti
    val multiCommandResults: StateFlow<List<MultiCommandResult>> = deviceManager.multiCommandResults
    val lastBroadcastCommand: StateFlow<String> = deviceManager.lastBroadcastCommand

    fun toggleDeviceSelection(id: String) = deviceManager.toggleDeviceSelection(id)
    fun selectAllDevices() = deviceManager.selectAllDevices()
    fun deselectAllDevices() = deviceManager.deselectAllDevices()
    fun setActiveDevice(id: String) = deviceManager.setActiveDevice(id)
    fun setMultiDeviceMode(enabled: Boolean) = deviceManager.setMultiDeviceMode(enabled)

    fun executeMultiDeviceCommand(command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            deviceManager.executeOnSelectedDevices(command)
        }
    }

    fun addNewDevice(name: String, model: String, type: ConnectionType, address: String) {
        deviceManager.addDevice(name, model, type, address)
    }

    fun removeAdbDevice(id: String) = deviceManager.removeDevice(id)
    fun scanUsbDevices() = deviceManager.scanUsbDevices()

    // UI States
    private val _portInput = MutableStateFlow("")
    val portInput: StateFlow<String> = _portInput.asStateFlow()

    // Persistent Shell Session & CWD Management
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

    fun getDefaultHomeDirectory(): File {
        val externalStorage = android.os.Environment.getExternalStorageDirectory()
        val sdHome = File(externalStorage, "Home")
        if (!sdHome.exists()) {
            try { sdHome.mkdirs() } catch (_: Exception) {}
        }
        val altSdHome = File("/sdcard/Home")
        if (!altSdHome.exists()) {
            try { altSdHome.mkdirs() } catch (_: Exception) {}
        }
        val target = when {
            sdHome.exists() && sdHome.canWrite() -> sdHome
            altSdHome.exists() && altSdHome.canWrite() -> altSdHome
            sdHome.exists() -> sdHome
            altSdHome.exists() -> altSdHome
            else -> File("/sdcard/Home").apply { try { mkdirs() } catch (_: Exception) {} }
        }
        try {
            if (target.exists()) {
                val noMedia = File(target, ".nomedia")
                if (!noMedia.exists()) noMedia.createNewFile()
            }
        } catch (_: Exception) {}
        return target
    }

    val shellSessionManager = com.example.terminal.ShellSessionManager(application)
    private var localCwdFile: File = getDefaultHomeDirectory()
    private val _currentWorkingDirectory = MutableStateFlow(localCwdFile.absolutePath)
    val currentWorkingDirectory: StateFlow<String> = _currentWorkingDirectory.asStateFlow()

    fun sendSignalInterrupt() {
        shellSessionManager.sendInterrupt(_currentTabId.value)
        updateTerminal("^C\n")
    }

    fun sendSignalEof() {
        shellSessionManager.sendEof(_currentTabId.value)
    }

    private val _isPythonReplActive = MutableStateFlow(false)
    val isPythonReplActive: StateFlow<Boolean> = _isPythonReplActive.asStateFlow()

    private val _isPythonMultiline = MutableStateFlow(false)
    val isPythonMultiline: StateFlow<Boolean> = _isPythonMultiline.asStateFlow()

    val kaliManager = com.example.terminal.KaliUserspaceManager(application)
    val prootManager = com.example.terminal.PRootManager(application)
    val packageManager = com.example.terminal.TerminalPackageManager(application)

    private val _isMatrixRainActive = MutableStateFlow(false)
    val isMatrixRainActive: StateFlow<Boolean> = _isMatrixRainActive.asStateFlow()

    fun stopMatrixRain() {
        _isMatrixRainActive.value = false
    }

    private fun findNativePackageByQuery(query: String): PackageInfo? {
        val pm = getApplication<Application>().packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        val cleanQuery = query.trim().lowercase(Locale.US)
        if (cleanQuery.isEmpty()) return null

        val directMatch = packages.find { it.packageName.lowercase(Locale.US) == cleanQuery }
        if (directMatch != null) return directMatch

        return packages.find { pkg ->
            val label = try { pm.getApplicationLabel(pkg.applicationInfo!!).toString().lowercase(Locale.US) } catch (e: Exception) { "" }
            label.contains(cleanQuery) || pkg.packageName.lowercase(Locale.US).contains(cleanQuery)
        }
    }

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isRootAccessActive = MutableStateFlow(false)
    val isRootAccessActive: StateFlow<Boolean> = _isRootAccessActive.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Ulanmagan")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    // Multiple active tabs State
    private val _tabs = MutableStateFlow<List<TerminalTab>>(emptyList())
    val tabs: StateFlow<List<TerminalTab>> = _tabs.asStateFlow()

    private val _currentTabId = MutableStateFlow("")
    val currentTabId: StateFlow<String> = _currentTabId.asStateFlow()

    private val _terminalOutput = MutableStateFlow("")
    val terminalOutput: StateFlow<String> = _terminalOutput.asStateFlow()

    private val _commandInput = MutableStateFlow("")
    val commandInput: StateFlow<String> = _commandInput.asStateFlow()

    private val _savedScripts = MutableStateFlow<List<ScriptEntity>>(emptyList())
    val savedScripts: StateFlow<List<ScriptEntity>> = _savedScripts.asStateFlow()

    private val _commandHistory = MutableStateFlow<List<HistoryEntity>>(emptyList())
    val commandHistory: StateFlow<List<HistoryEntity>> = _commandHistory.asStateFlow()

    // Device parsed info state
    private val _deviceInfo = MutableStateFlow<Map<String, String>>(emptyMap())
    val deviceInfo: StateFlow<Map<String, String>> = _deviceInfo.asStateFlow()

    private val _terminalColumns = MutableStateFlow(45)
    val terminalColumns: StateFlow<Int> = _terminalColumns.asStateFlow()

    fun updateTerminalColumns(cols: Int) {
        _terminalColumns.value = cols.coerceIn(10, 120)
    }

    // Persistent Settings Configuration
    private val sharedPrefs = application.getSharedPreferences("super_adb_settings", Context.MODE_PRIVATE)

    private val _selectedLanguage = MutableStateFlow(sharedPrefs.getString("selected_language", "uz") ?: "uz")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _selectedGeminiModel = MutableStateFlow(sharedPrefs.getString("selected_gemini_model", GeminiClient.DEFAULT_MODEL_ID) ?: GeminiClient.DEFAULT_MODEL_ID)
    val selectedGeminiModel: StateFlow<String> = _selectedGeminiModel.asStateFlow()

    fun updateSelectedGeminiModel(modelId: String) {
        _selectedGeminiModel.value = modelId
        sharedPrefs.edit().putString("selected_gemini_model", modelId).apply()
    }

    fun updateSelectedLanguage(lang: String) {
        _selectedLanguage.value = lang
        sharedPrefs.edit().putString("selected_language", lang).apply()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                adbDao.clearAllScripts()
                setupPreDefinedScripts()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private val _terminalFontSize = MutableStateFlow(sharedPrefs.getInt("terminal_font_size", 12))
    val terminalFontSize: StateFlow<Int> = _terminalFontSize.asStateFlow()

    private val _terminalTheme = MutableStateFlow(sharedPrefs.getString("terminal_theme", "ubuntu") ?: "ubuntu")
    val terminalTheme: StateFlow<String> = _terminalTheme.asStateFlow()

    private val _autoScrollEnabled = MutableStateFlow(sharedPrefs.getBoolean("auto_scroll", true))
    val autoScrollEnabled: StateFlow<Boolean> = _autoScrollEnabled.asStateFlow()

    private val _historyLimit = MutableStateFlow(sharedPrefs.getInt("history_limit", 50))
    val historyLimit: StateFlow<Int> = _historyLimit.asStateFlow()

    fun updateTerminalFontSize(size: Int) {
        _terminalFontSize.value = size
        sharedPrefs.edit().putInt("terminal_font_size", size).apply()
    }

    fun updateTerminalTheme(theme: String) {
        _terminalTheme.value = theme
        sharedPrefs.edit().putString("terminal_theme", theme).apply()
    }

    fun updateAutoScroll(enabled: Boolean) {
        _autoScrollEnabled.value = enabled
        sharedPrefs.edit().putBoolean("auto_scroll", enabled).apply()
    }

    fun updateHistoryLimit(limit: Int) {
        _historyLimit.value = limit
        sharedPrefs.edit().putInt("history_limit", limit).apply()
    }

    private val _isTtsEnabled = MutableStateFlow(sharedPrefs.getBoolean("tts_enabled", false))
    val isTtsEnabled: StateFlow<Boolean> = _isTtsEnabled.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isVoiceInputActive = MutableStateFlow(false)
    val isVoiceInputActive: StateFlow<Boolean> = _isVoiceInputActive.asStateFlow()

    fun setVoiceInputActive(active: Boolean) {
        _isVoiceInputActive.value = active
        if (active) {
            stopSpeaking()
        }
    }

    @Volatile
    private var tts: TextToSpeech? = null
    @Volatile
    private var isTtsReady = false
    @Volatile
    private var isInitializingTts = false
    private var lastTtsInitTime = 0L
    private var pendingText: String? = null
    @Volatile
    private var ttsChunks: List<String> = emptyList()
    @Volatile
    private var currentChunkIndex: Int = 0

    fun toggleTtsEnabled() {
        val nextVal = !_isTtsEnabled.value
        _isTtsEnabled.value = nextVal
        sharedPrefs.edit().putBoolean("tts_enabled", nextVal).apply()
        if (!nextVal) {
            stopSpeaking()
        } else {
            val welcome = when (_selectedLanguage.value) {
                "uz" -> "Ovozli o'qish yoqildi"
                "ru" -> "Голосовое чтение включено"
                else -> "Text to speech enabled"
            }
            speakText(welcome)
        }
    }

    private fun convertUzToTrPhonetics(text: String): String {
        return text.lowercase(Locale.US)
            .replace("ch", "ç")
            .replace("sh", "ş")
            .replace("o'", "o")
            .replace("g'", "ğ")
            .replace("ya", "ya")
            .replace("yu", "yu")
            .replace("yo", "yo")
    }

    private fun splitIntoTtsChunks(text: String, maxChunkSize: Int = 1000): List<String> {
        val clean = text.replace(Regex("[#*_`~>•|\\[\\]()\\\\-]"), " ").trim()
        if (clean.length <= maxChunkSize) return listOf(clean)
        val chunks = mutableListOf<String>()
        val sentences = clean.split(Regex("(?<=[.!?\\n])\\s*"))
        val currentChunk = StringBuilder()
        for (sentence in sentences) {
            if (currentChunk.length + sentence.length > maxChunkSize) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString().trim())
                    currentChunk.clear()
                }
            }
            currentChunk.append(sentence).append(" ")
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }
        return chunks.filter { it.isNotBlank() }
    }

    private fun speakNextChunk(onReady: (() -> Unit)? = null) {
        if (currentChunkIndex >= ttsChunks.size) {
            _isSpeaking.value = false
            return
        }
        val chunk = ttsChunks[currentChunkIndex]
        currentChunkIndex++

        val langCode = _selectedLanguage.value
        val locale = when (langCode) {
            "uz" -> Locale("tr", "TR")
            "ru" -> Locale("ru", "RU")
            else -> Locale.US
        }
        tts?.language = locale
        val textToRead = if (langCode == "uz") convertUzToTrPhonetics(chunk) else chunk

        val utteranceId = "SUPER_ADB_TTS_${System.currentTimeMillis()}_$currentChunkIndex"
        tts?.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun speakText(text: String) {
        if (!_isTtsEnabled.value) return
        stopSpeaking()
        ttsChunks = splitIntoTtsChunks(text)
        currentChunkIndex = 0
        if (ttsChunks.isEmpty()) return

        if (isTtsReady && tts != null) {
            _isSpeaking.value = true
            speakNextChunk()
        } else {
            pendingText = text
            initTts {
                _isSpeaking.value = true
                speakNextChunk()
            }
        }
    }

    fun stopSpeaking() {
        _isSpeaking.value = false
        try {
            tts?.stop()
        } catch (e: Exception) {}
    }

    private fun initTts(onReady: (() -> Unit)? = null) {
        val now = System.currentTimeMillis()
        if (isInitializingTts && (now - lastTtsInitTime) < 5000) return
        isInitializingTts = true
        lastTtsInitTime = now

        try {
            tts = TextToSpeech(getApplication<Application>()) { status ->
                isInitializingTts = false
                if (status == TextToSpeech.SUCCESS) {
                    isTtsReady = true
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }
                        override fun onDone(utteranceId: String?) {
                            if (currentChunkIndex < ttsChunks.size) {
                                speakNextChunk()
                            } else {
                                _isSpeaking.value = false
                            }
                        }
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                        }
                    })

                    val langCode = _selectedLanguage.value
                    val locale = when (langCode) {
                        "uz" -> Locale("tr", "TR")
                        "ru" -> Locale("ru", "RU")
                        else -> Locale.US
                    }
                    tts?.language = locale
                    tts?.setPitch(1.0f)
                    tts?.setSpeechRate(1.0f)
                    onReady?.invoke()
                } else {
                    isTtsReady = false
                }
            }
        } catch (e: Exception) {
            isInitializingTts = false
            isTtsReady = false
        }
    }

    fun createDefaultMotd(tabIndex: Int): String {
        return ""
    }

    private val maxScrollbackChars = 120_000

    private fun boundScrollback(output: String): String {
        return if (output.length > maxScrollbackChars) {
            output.substring(output.length - maxScrollbackChars)
        } else {
            output
        }
    }

    private fun appendOutputToTab(targetTabId: String, chunk: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val list = _tabs.value.toMutableList()
            val idx = list.indexOfFirst { it.id == targetTabId }
            if (idx != -1) {
                val existing = list[idx]
                val combined = existing.terminalOutput + chunk
                val updatedOutput = boundScrollback(combined)
                list[idx] = existing.copy(terminalOutput = updatedOutput)
                _tabs.value = list
                if (_currentTabId.value == targetTabId) {
                    _terminalOutput.value = updatedOutput
                }
            }
        }
    }

    private fun saveCurrentTabState() {
        val currentId = _currentTabId.value
        if (currentId.isNotEmpty()) {
            val list = _tabs.value.toMutableList()
            val index = list.indexOfFirst { it.id == currentId }
            if (index != -1) {
                list[index] = list[index].copy(
                    terminalOutput = _terminalOutput.value,
                    commandInput = _commandInput.value,
                    workingDir = _currentWorkingDirectory.value,
                    isPythonActive = _isPythonReplActive.value,
                    isPythonMultiline = _isPythonMultiline.value
                )
                _tabs.value = list
            }
        }
    }

    fun addNewTab(customTitle: String? = null) {
        saveCurrentTabState()
        val newIndex = _tabs.value.size + 1
        val newId = UUID.randomUUID().toString()
        val title = customTitle ?: "Terminal $newIndex"
        val defaultHome = getDefaultHomeDirectory()
        val defaultMotd = createDefaultMotd(newIndex)
        val newTab = TerminalTab(
            id = newId,
            title = title,
            terminalOutput = defaultMotd,
            commandInput = "",
            workingDir = defaultHome.absolutePath,
            isPythonActive = false,
            isPythonMultiline = false,
            isRunning = false
        )
        _tabs.value = _tabs.value + newTab
        _currentTabId.value = newId
        _terminalOutput.value = defaultMotd
        _commandInput.value = ""
        localCwdFile = defaultHome
        _currentWorkingDirectory.value = defaultHome.absolutePath
        _isPythonReplActive.value = false
        _isPythonMultiline.value = false
        shellSessionManager.setActiveTab(newId)
        shellSessionManager.getOrCreateSession(newId, defaultHome, useRoot = false) { targetTabId, chunk ->
            appendOutputToTab(targetTabId, chunk)
        }
    }

    fun closeTab(tabId: String) {
        shellSessionManager.closeSession(tabId)
        val list = _tabs.value.filter { it.id != tabId }
        if (list.isEmpty()) {
            val newId = UUID.randomUUID().toString()
            val defaultHome = getDefaultHomeDirectory()
            val defaultMotd = createDefaultMotd(1)
            val fallbackTab = TerminalTab(
                id = newId,
                title = "Terminal 1",
                terminalOutput = defaultMotd,
                commandInput = "",
                workingDir = defaultHome.absolutePath,
                isPythonActive = false,
                isPythonMultiline = false,
                isRunning = false
            )
            _tabs.value = listOf(fallbackTab)
            _currentTabId.value = newId
            _terminalOutput.value = defaultMotd
            _commandInput.value = ""
            localCwdFile = defaultHome
            _currentWorkingDirectory.value = defaultHome.absolutePath
            _isPythonReplActive.value = false
            _isPythonMultiline.value = false
            shellSessionManager.setActiveTab(newId)
            shellSessionManager.getOrCreateSession(newId, defaultHome, useRoot = false) { targetTabId, chunk ->
                appendOutputToTab(targetTabId, chunk)
            }
        } else {
            _tabs.value = list
            if (_currentTabId.value == tabId) {
                val nextTab = list.last()
                _currentTabId.value = nextTab.id
                _terminalOutput.value = nextTab.terminalOutput
                _commandInput.value = nextTab.commandInput
                val dirPath = nextTab.workingDir.ifEmpty { getDefaultHomeDirectory().absolutePath }
                localCwdFile = File(dirPath).apply { if (!exists()) mkdirs() }
                _currentWorkingDirectory.value = localCwdFile.absolutePath
                _isPythonReplActive.value = nextTab.isPythonActive
                _isPythonMultiline.value = nextTab.isPythonMultiline
                shellSessionManager.setActiveTab(nextTab.id)
            }
        }
    }

    fun switchToTab(tabId: String) {
        if (tabId == _currentTabId.value) return
        saveCurrentTabState()
        val tab = _tabs.value.find { it.id == tabId }
        if (tab != null) {
            _currentTabId.value = tab.id
            _terminalOutput.value = tab.terminalOutput
            _commandInput.value = tab.commandInput
            val dirPath = tab.workingDir.ifEmpty { getDefaultHomeDirectory().absolutePath }
            localCwdFile = File(dirPath).apply { if (!exists()) mkdirs() }
            _currentWorkingDirectory.value = localCwdFile.absolutePath
            _isPythonReplActive.value = tab.isPythonActive
            _isPythonMultiline.value = tab.isPythonMultiline
            shellSessionManager.setActiveTab(tab.id)
            shellSessionManager.getOrCreateSession(tab.id, localCwdFile, useRoot = false) { targetTabId, chunk ->
                appendOutputToTab(targetTabId, chunk)
            }
        }
    }

    fun renameTab(tabId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        val list = _tabs.value.map { tab ->
            if (tab.id == tabId) tab.copy(title = trimmed) else tab
        }
        _tabs.value = list
    }

    init {
        val initialId = UUID.randomUUID().toString()
        val defaultHome = getDefaultHomeDirectory()
        localCwdFile = defaultHome
        _currentWorkingDirectory.value = defaultHome.absolutePath
        val initialMotd = createDefaultMotd(1)
        val initialTab = TerminalTab(
            id = initialId,
            title = "Terminal 1",
            terminalOutput = initialMotd,
            commandInput = "",
            workingDir = defaultHome.absolutePath,
            isPythonActive = false,
            isPythonMultiline = false,
            isRunning = false
        )
        _tabs.value = listOf(initialTab)
        _currentTabId.value = initialId
        _terminalOutput.value = initialMotd
        shellSessionManager.setActiveTab(initialId)
        shellSessionManager.getOrCreateSession(initialId, defaultHome, useRoot = false) { targetTabId, chunk ->
            appendOutputToTab(targetTabId, chunk)
        }

        // Initialize Native Terminal API binaries (terminal-toast, terminal-vibrate, etc.)
        viewModelScope.launch(Dispatchers.IO) {
            com.example.terminal.TerminalNativeApi(application).deployTerminalApiBinaries()
        }

        viewModelScope.launch(Dispatchers.IO) {
            adbDao.getAllScripts().collectLatest { scripts ->
                _savedScripts.value = scripts
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            adbDao.getCommandHistory().collectLatest { history ->
                _commandHistory.value = history
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            setupPreDefinedScripts()
        }

        viewModelScope.launch {
            shellSessionManager.activeSession.collectLatest { session ->
                session?.currentWorkingDir?.collectLatest { dir ->
                    _currentWorkingDirectory.value = dir
                    localCwdFile = File(dir).apply { if (!exists()) mkdirs() }
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (adbClient.isConnected()) {
                    _isConnected.value = true
                    val host = com.example.adb.AdbClientHolder.lastHost.value
                    val port = com.example.adb.AdbClientHolder.lastPort.value
                    _connectionStatus.value = if (_selectedLanguage.value == "uz") "Ulangan ($host:$port)" else "Connected ($host:$port)"
                    fetchBasicDeviceInfo()
                } else {
                    val prefs = application.getSharedPreferences("SuperTerminalPrefs", android.content.Context.MODE_PRIVATE)
                    val host = prefs.getString("last_adb_host", "") ?: ""
                    val port = prefs.getInt("last_adb_port", 0)
                    if (host.isNotEmpty() && port > 0) {
                        try {
                            adbClient.connect(port, host)
                            _isConnected.value = true
                            _connectionStatus.value = if (_selectedLanguage.value == "uz") "Ulangan ($host:$port)" else "Connected ($host:$port)"
                            com.example.adb.AdbClientHolder.setLastConnection(host, port)
                            fetchBasicDeviceInfo()
                        } catch (e: Exception) {
                            // Failed to auto-connect
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        loadLocalDeviceInfo()
        initTts()
    }

    fun onPortChange(newPort: String) {
        _portInput.value = newPort
    }

    fun onCommandChange(newCommand: String) {
        _commandInput.value = newCommand
    }

    fun connectAdb() {
        val input = _portInput.value.trim()
        val lang = _selectedLanguage.value

        if (input.isEmpty()) {
            val emptyMsg = when (lang) {
                "en" -> "Error: Port or IP:Port cannot be empty! (e.g. 5555 or 192.168.1.15:5555)"
                "ru" -> "Ошибка: Порт или IP:Порт не может быть пустым! (например: 5555 или 192.168.1.15:5555)"
                else -> "Xato: Port yoki IP:Port bo'sh bo'lishi mumkin emas! (Masalan: 5555 yoki 192.168.1.15:5555)"
            }
            updateTerminal("[-] $emptyMsg")
            return
        }

        // Clean input from accidental spaces or url schemes
        var cleanInput = input.replace("http://", "").replace("https://", "").replace(" ", "")
        var host = "127.0.0.1"
        var port = 5555

        if (cleanInput.contains(":")) {
            val parts = cleanInput.split(":")
            host = parts[0].trim().ifEmpty { "127.0.0.1" }
            port = parts[1].trim().toIntOrNull() ?: 5555
        } else {
            port = cleanInput.toIntOrNull() ?: 5555
        }

        if (port !in 1024..65535) {
            val invalidMsg = when (lang) {
                "en" -> "Error: Port must be between 1024 and 65535!"
                "ru" -> "Ошибка: Порт должен быть в диапазоне от 1024 до 65535!"
                else -> "Xato: Port 1024 va 65535 oralig'idagi son bo'lishi kerak!"
            }
            updateTerminal("[-] $invalidMsg")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isConnecting.value = true
            _connectionStatus.value = if (lang == "uz") "Ulanmoqda ($host:$port)..." else "Connecting ($host:$port)..."

            var success = false
            var finalException: Exception? = null

            for (attempt in 1..2) {
                try {
                    val result = adbClient.connect(port = port, targetHost = host) { status ->
                        updateTerminal("[*] $status")
                    }
                    _isConnected.value = true
                    _connectionStatus.value = if (lang == "uz") "Ulangan ($host:$port)" else "Connected ($host:$port)"

                    // Save to Holder for the Accessibility Service Daemon to pick up
                    com.example.adb.AdbClientHolder.setLastConnection(host, port)
                    sharedPrefs.edit().putString("last_adb_host", host).putInt("last_adb_port", port).apply()

                    val successMsg = if (lang == "uz") {
                        "[+] ADB serveriga muvaffaqiyatli ulandi ($host:$port)!\n🔒 Maxfiy fon xizmati ishga tushdi (To'xtatish tugmasisiz ishlaydi).\n$result"
                    } else if (lang == "ru") {
                        "[+] Успешно подключено к ADB серверу ($host:$port)!\n🔒 Скрытая фоновая служба активна.\n$result"
                    } else {
                        "[+] Successfully connected to ADB ($host:$port)!\n🔒 Silent background persistent daemon active.\n$result"
                    }
                    updateTerminal(successMsg)
                    fetchBasicDeviceInfo()
                    autoGrantMaxPermissions()
                    success = true
                    break
                } catch (e: Exception) {
                    finalException = e
                    kotlinx.coroutines.delay(1000)
                }
            }

            if (!success) {
                _isConnected.value = false
                _connectionStatus.value = if (lang == "uz") "Bog'lanish bajarilmadi" else "Connection failed"
                val errorMsg = when (lang) {
                    "uz" -> "[-] Bog'lanishda xatolik (ECONNREFUSED): ${finalException?.localizedMessage}\n\n💡 Muhim Maslahat (Port turi):\n1. Siz kiritgan port ($port) **Juftlash porti (Pairing Port)** bo'lishi mumkin. Android 11+ da juftlash porti orqali emas, balki 'Simsiz nosozliklarni tuzatish' asosiy menyusida ko'rsatilgan **Ulanish porti (Connection Port)** orqali ulanish kerak.\n2. Juftlash porti (masalan: 40000-45000) faqat 'Pair device with pairing code' uchun ishlatiladi.\n3. Ulanish porti (masalan: 30000-40000) esa to'g'ridan-to'g'ri 'adb connect' uchundir."
                    "ru" -> "[-] Ошибка подключения (ECONNREFUSED): ${finalException?.localizedMessage}\n\n💡 Важный совет (Тип порта):\n1. Указанный порт ($port) может быть **Портом сопряжения (Pairing Port)**. Для подключения нужен **Порт подключения (Connection Port)** с главного экрана 'Отладка по Wi-Fi'.\n2. Порт сопряжения используется только для 'adb pair'."
                    else -> "[-] Connection error (ECONNREFUSED): ${finalException?.localizedMessage}\n\n💡 Important Tip (Port Type):\n1. The port ($port) you entered might be the **Pairing Port**. Make sure to use the **Connection Port** shown on the main Wireless debugging screen, not the pairing port.\n2. Pairing port is only for 'adb pair'."
                }
                updateTerminal(errorMsg)
            }
            _isConnecting.value = false
        }
    }

    private fun autoGrantMaxPermissions() {
        viewModelScope.launch(Dispatchers.IO) {
            val pName = getApplication<Application>().packageName
            val permissionsToGrant = listOf(
                "android.permission.WRITE_SECURE_SETTINGS",
                "android.permission.PACKAGE_USAGE_STATS",
                "android.permission.DUMP",
                "android.permission.BATTERY_STATS",
                "android.permission.READ_LOGS",
                "android.permission.CHANGE_CONFIGURATION"
            )
            for (perm in permissionsToGrant) {
                try {
                    adbClient.executeCommand("pm grant $pName $perm")
                } catch (e: Exception) {}
            }
        }
    }

    fun disconnectAdb() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.example.adb.AdbClientHolder.setLastConnection("", 0)
                adbClient.close()
            } catch (e: Exception) {}
            _isConnected.value = false
            _connectionStatus.value = "Ulanmagan"
            updateTerminal("[*] ADB ulanishi uzildi. Mahalliy terminal rejimiga qaytildi.")
            loadLocalDeviceInfo()
        }
    }

    fun checkConnectionOnResume() {
        viewModelScope.launch(Dispatchers.IO) {
            var isConn = adbClient.isConnected()
            
            // [SENIOR ARCHITECTURE]: Silent auto-reconnect on UI resume
            if (!isConn) {
                val prefs = getApplication<Application>().getSharedPreferences("SuperTerminalPrefs", android.content.Context.MODE_PRIVATE)
                val host = prefs.getString("last_adb_host", "") ?: ""
                val port = prefs.getInt("last_adb_port", 0)
                if (host.isNotEmpty() && port > 0) {
                    try {
                        adbClient.connect(port, host)
                        isConn = adbClient.isConnected()
                    } catch (e: Exception) {}
                }
            }

            if (isConn) {
                _isConnected.value = true
                val host = com.example.adb.AdbClientHolder.lastHost.value
                val port = com.example.adb.AdbClientHolder.lastPort.value
                _connectionStatus.value = if (_selectedLanguage.value == "uz") "Ulangan ($host:$port)" else "Connected ($host:$port)"
            } else {
                _isConnected.value = false
                _connectionStatus.value = "Ulanmagan"
            }
        }
    }

    private fun tryLaunchApp(inputName: String): Boolean {
        val pm = getApplication<Application>().packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        
        val resolveInfos = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, android.content.pm.PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
        } catch (e: Exception) {
            return false
        }
        
        val normalizedInput = inputName.lowercase(Locale.US).replace(Regex("[\\s\\-_.]"), "")
        if (normalizedInput.isEmpty()) return false
        
        data class AppMatch(val resolveInfo: android.content.pm.ResolveInfo, val label: String, val score: Int)
        val matches = mutableListOf<AppMatch>()
        
        for (resolveInfo in resolveInfos) {
            val label = try { resolveInfo.loadLabel(pm).toString() } catch (e: Exception) { "" }
            if (label.isEmpty()) continue
            
            val normalizedLabel = label.lowercase(Locale.US).replace(Regex("[\\s\\-_.]"), "")
            
            if (normalizedLabel == normalizedInput) {
                matches.add(AppMatch(resolveInfo, label, 100))
            } else if (normalizedInput.length > 2 && normalizedLabel.contains(normalizedInput)) {
                // Input is substring of the app name (e.g. "tele" -> "telegram")
                matches.add(AppMatch(resolveInfo, label, 70))
            } else if (normalizedInput.length > 2 && normalizedInput.contains(normalizedLabel)) {
                // App name is substring of input (e.g. "google chrome" -> "chrome")
                matches.add(AppMatch(resolveInfo, label, 50))
            }
        }
        
        if (matches.isNotEmpty()) {
            // Sort by score descending, then by shortest label length
            matches.sortWith(compareByDescending<AppMatch> { it.score }.thenBy { it.label.length })
            val bestMatch = matches.first()
            val pkg = bestMatch.resolveInfo.activityInfo.packageName
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    getApplication<Application>().startActivity(launchIntent)
                    updateTerminal("🚀 Ilova ishga tushirildi: ${bestMatch.label} ($pkg)")
                    return true
                } catch (e: Exception) {
                    updateTerminal("❌ Ilovani ishga tushirishda xatolik: ${e.message}")
                    return true
                }
            }
        }
        return false
    }

    fun executeTerminalCommand(cmdString: String = _commandInput.value) {
        val cmd = cmdString.trim()
        if (cmd.isEmpty()) return

        val cwd = _currentWorkingDirectory.value
        val formattedPath = formatTerminalPath(cwd)
        val promptPrefix = when {
            _isPythonReplActive.value -> if (_isPythonMultiline.value) "... " else ">>> "
            _isConnected.value -> "adb@android:$formattedPath$ "
            else -> "Super@user:$formattedPath$ "
        }
        updateTerminal("\n$promptPrefix$cmd")
        _commandInput.value = ""

        val cleanCmd = cmd.trim()
        val lowerCmd = cleanCmd.lowercase(Locale.US)
        val lang = _selectedLanguage.value

        viewModelScope.launch(Dispatchers.IO) {
            // Assess command safety through centralized Security Gateway
            com.example.security.CommandSecurityGateway.assessCommand(
                cmd,
                com.example.security.ExecutionOrigin.USER_KEYBOARD
            )

            // cd command handler to maintain real working directory
            if (lowerCmd == "cd" || lowerCmd.startsWith("cd ")) {
                val target = cleanCmd.removePrefix("cd").trim()
                val targetFile = when {
                    target.isEmpty() || target == "~" -> getDefaultHomeDirectory()
                    target.startsWith("/") -> File(target)
                    else -> File(localCwdFile, target)
                }
                val canonical = try { targetFile.canonicalFile } catch (_: Exception) { targetFile }
                if (!canonical.exists()) {
                    updateTerminal("cd: $target: No such file or directory")
                } else if (!canonical.isDirectory) {
                    updateTerminal("cd: $target: Not a directory")
                } else if (!canonical.canRead() && !canonical.canExecute()) {
                    updateTerminal("cd: $target: Permission denied")
                } else {
                    localCwdFile = canonical
                    _currentWorkingDirectory.value = canonical.absolutePath
                    shellSessionManager.write("cd \"${canonical.absolutePath}\"\n")
                }
                return@launch
            }

            if (lowerCmd == "pwd") {
                updateTerminal(localCwdFile.absolutePath)
                return@launch
            }

            // Built-in commands dispatcher
            when {
                lowerCmd == "clear" || lowerCmd == "reset" -> {
                    clearTerminal()
                    return@launch
                }

                lowerCmd == "help" || lowerCmd == "h" || lowerCmd == "?" -> {
                    val helpText = when (lang) {
                        "uz" -> """
============================================================
              TERMINAL BUYRUQLARI BO'YICHA QO'LLANMA
============================================================
 📊 Tizim va Diagnostika:
   • sysinfo               - To'liq apparat va tizim diagnostikasi
   • neofetch              - Android tizim parametrlari va banner
   • ifconfig / ip addr    - Tarmoq interfeyslari va IP manzillar
   • netinfo               - To'liq Wi-Fi/Mobil tarmoq diagnostikasi
   • ping <host>           - ICMP/TCP ping va kechikish sinovi
   • speedtest             - Jonli internet tezligi va ping sinovi
   • tcp-scan <host>           - Haqiqiy parallel TCP port skaneri
   • dns / dig <domain>    - DNS server va domenni tahlil qilish
   • netstat               - Faol tarmoq portlari va soketlar
   • top / ps              - Haqiqiy ishlayotgan protsesslar va RAM
   • booster               - RAM optimallash va kesh tozalash
   • lsblk / df -h         - Xotira disklari va bo'limlar hajmi

 📦 Paketlar va Linux Muhiti:
   • pkg install <nom>     - Linux paketini o'rnatish (python, git, nano, curl...)
   • pkg uninstall <nom>   - Paketni o'chirish (remove)
   • pkg upgrade           - Barcha paketlarni yangilash
   • pkg search <so'z>     - 2000+ paketlar orasidan qidirish
   • pkg update            - Repozitoriya indeksini yangilash
   • pkg list-all          - Barcha mavjud paketlar ro'yxati
   • kali login            - Kali Linux ARM64 root konsoliga kirish
   • kali install          - Kali Linux to'liq rootfs tizimini o'rnatish
   • open <ilova_nomi>     - Ilovani topish va ishga tushirish (yoki nomini yozing)
   • apk-pull <paket_nomi> - APK faylini yuklab olish
   • app-update            - Ilovalarni yangilash
   • sys-update            - Tizim (OTA) yangilanishini ochish

 📱 Qurilma Apparat & Sensor API (Termux:API muqobili):
   • terminal-toast <matn>      - Ekranga Toast bildirishnoma chiqarish
   • terminal-vibrate -d <ms>   - Telefon vibratsiyasini yoqish
   • terminal-torch on|off      - Fonar / Chiroqni boshqarish
   • terminal-battery-status    - Batareya holati (JSON)
   • terminal-clipboard-set/get - Bufer (Clipboard) bilan ishlash
   • terminal-tts-speak <matn>  - Ovozli sintez (TTS)
   • terminal-notification      - Tizim bildirishnomasini yuborish
   • terminal-volume <stream>   - Ovoz balandligini boshqarish

 🔧 Ishlab chiquvchi va Utility:
   • curl <url>            - HTTP so'rovlar yuborish
   • git clone <url>       - GitHub/GitLab loyihasini yuklab olish
   • calc <ifoda>          - Ilmiy matematik hisoblagich
   • math-repl                - Interaktiv matematik/Python konsoli
   • matrix                - Yashil raqamli yomg'ir animatsiyasi
   • pc-link               - Kompyuterga ulash bo'yicha qo'llanma
   • history / clear       - Tarix / Terminalni tozalash

 💡 Shuningdek, istalgan standart ADB yoki Linux shell buyruqlarini
    (ls, cat, mkdir, rm, getprop, dumpsys, pm, wm, input...) kiritishingiz mumkin.
============================================================
                        """.trimIndent()
                        "ru" -> """
============================================================
                 СПРАВОЧНИК КОМАНД ТЕРМИНАЛА
============================================================
 📊 Система и Диагностика:
   • sysinfo               - Полная диагностика оборудования и ОС
   • neofetch              - Информация о системе и баннер
   • ifconfig / ip addr    - Сетевые адаптеры и IP адреса
   • top / ps              - Реальные запущенные процессы и RAM
   • speedtest             - Проверка скорости интернета и пинга
   • tcp-scan <host>           - Сканирование открытых TCP портов
   • booster               - Очистка RAM и кэша
   • lsblk / df -h         - Разделы диска и свободное место

 📦 Пакеты и Приложения:
   • open <имя_прил.>      - Найти и запустить приложение
   • pkg list              - Список установленных пакетов
   • apk-pull <пакет>      - Извлечение APK файла
   • app-update            - Обновление приложений
   • sys-update            - Проверка обновлений системы

 🔧 Утилиты:
   • curl <url>            - Отправка HTTP запросов
   • git clone <url>       - Клонирование репозиториев
   • calc <выражение>      - Научный калькулятор
   • math-repl                - Interactive Math REPL
   • pc-link               - Инструкция подключения к ПК
   • history / clear       - История / Очистка экрана
============================================================
                        """.trimIndent()
                        else -> """
============================================================
                TERMINAL COMMAND MANUAL
============================================================
 📊 System & Diagnostics:
   • sysinfo               - Full hardware and OS diagnostics
   • neofetch              - System specs ASCII banner
   • ifconfig / ip addr    - Network interfaces & IP addresses
   • top / ps              - Running processes & RAM usage
   • speedtest             - Live bandwidth & latency test
   • tcp-scan <host>           - TCP port scanner
   • booster               - Reclaim volatile RAM & clear cache
   • lsblk / df -h         - Storage partitions & free space

 📦 Packages & Apps:
   • open <app_name>       - Smart find and launch app
   • pkg list              - List installed packages
   • apk-pull <package>    - Extract application APK
   • app-update            - Update installed apps
   • sys-update            - Check OTA system updates

 🔧 Developer Tools:
   • curl <url>            - HTTP request client
   • git clone <url>       - Clone git repository archive
   • calc <expression>     - Scientific math evaluator
   • math-repl                - Interactive Math REPL
   • pc-link               - PC connection instructions
   • history / clear       - View history / Clear terminal
============================================================
                        """.trimIndent()
                    }
                    updateTerminal(helpText)
                    return@launch
                }

                lowerCmd == "sysinfo" || lowerCmd == "deviceinfo" || lowerCmd == "info" -> {
                    val info = RealDeviceInfoProvider.buildFullDeviceDiagnostic(getApplication(), _isConnected.value)
                    _deviceInfo.value = info
                    val sb = StringBuilder()
                    sb.append("📊 [QURILMA VA TIZIM DIAGNOSTIKASI]\n")
                    sb.append("------------------------------------------------------------\n")
                    for ((k, v) in info) {
                        sb.append("  • %-26s : %s\n".format(k, v))
                    }
                    sb.append("------------------------------------------------------------")
                    updateTerminal(sb.toString())
                    return@launch
                }

                lowerCmd == "neofetch" -> {
                    val banner = RealDeviceInfoProvider.generateNeofetchBanner(getApplication(), _isConnected.value)
                    updateTerminal(banner)
                    return@launch
                }

                lowerCmd == "ifconfig" || lowerCmd == "ip a" || lowerCmd == "ip addr" -> {
                    val ifconfigRes = RealNetworkInspector.getIfconfigOutput(getApplication())
                    updateTerminal(ifconfigRes)
                    return@launch
                }

                lowerCmd == "top" || lowerCmd.startsWith("top ") || lowerCmd.startsWith("top-m") -> {
                    val limit = when {
                        lowerCmd.contains("-m ") -> lowerCmd.substringAfter("-m ").trim().toIntOrNull() ?: 15
                        lowerCmd.contains("-m") -> lowerCmd.substringAfter("-m").trim().toIntOrNull() ?: 15
                        else -> 15
                    }
                    val topOutput = RealProcessInspector.getRealTopOutput(getApplication(), limit)
                    updateTerminal(topOutput)
                    return@launch
                }

                lowerCmd == "ps" || lowerCmd.startsWith("ps ") || lowerCmd.startsWith("ps-") -> {
                    val psArgs = cleanCmd.substringAfter("ps").trim()
                    val psOutput = RealProcessInspector.getRealPsOutput(getApplication(), psArgs)
                    updateTerminal(psOutput)
                    return@launch
                }

                lowerCmd == "threads" || lowerCmd == "ps -t" || lowerCmd == "jthreads" -> {
                    val threadsOutput = RealProcessInspector.getProcessThreadsDiagnostic()
                    updateTerminal(threadsOutput)
                    return@launch
                }

                lowerCmd == "procmem" || lowerCmd == "memmap" -> {
                    val memOutput = RealProcessInspector.getDetailedMemoryDiagnostic(getApplication())
                    updateTerminal(memOutput)
                    return@launch
                }

                lowerCmd.startsWith("kill ") || lowerCmd.startsWith("killall ") -> {
                    val target = cleanCmd.substringAfter("kill").substringAfter("all").trim().removePrefix("-9").trim()
                    val killOutput = RealProcessInspector.killProcess(getApplication(), target)
                    updateTerminal(killOutput)
                    return@launch
                }

                lowerCmd == "speedtest" -> {
                    val result = RealNetworkInspector.performRealSpeedtest(getApplication()) { progress ->
                        updateTerminal(progress)
                    }
                    updateTerminal(result)
                    return@launch
                }

                lowerCmd == "nmap" || lowerCmd.startsWith("nmap ") -> {
                    val args = cleanCmd.removePrefix("nmap").trim()
                    val sysNmap = shellSessionManager.writeAndRead("which nmap\n", 400)
                    if (sysNmap.contains("/nmap")) {
                        shellSessionManager.write("nmap $args\n")
                    } else {
                        updateTerminal("nmap: command not found\nNmap is not installed in the system shell. Use 'tcp-scan <host>' for the built-in TCP port scanner.")
                    }
                    return@launch
                }

                lowerCmd == "tcp-scan" || lowerCmd.startsWith("tcp-scan ") || lowerCmd.startsWith("portscan") -> {
                    val args = cleanCmd.removePrefix("tcp-scan").removePrefix("portscan").trim()
                    val result = com.example.net.KaliNetworkSuite.performNmapScan(args) { statusLine ->
                        updateTerminal(statusLine)
                    }
                    updateTerminal(result)
                    return@launch
                }

                lowerCmd.startsWith("traceroute") || lowerCmd.startsWith("tracepath") -> {
                    val target = cleanCmd.substringAfter(" ").trim().ifEmpty { "8.8.8.8" }
                    val result = com.example.net.KaliNetworkSuite.performTraceroute(target) { line ->
                        updateTerminal(line)
                    }
                    updateTerminal(result)
                    return@launch
                }

                lowerCmd.startsWith("whois") -> {
                    val target = cleanCmd.removePrefix("whois").trim()
                    val result = com.example.net.KaliNetworkSuite.performWhois(target)
                    updateTerminal(result)
                    return@launch
                }

                lowerCmd == "netdiscover" || lowerCmd == "arp-scan" || lowerCmd == "arp" || lowerCmd.startsWith("arp ") -> {
                    val result = com.example.net.KaliNetworkSuite.performNetdiscover(getApplication()) { line ->
                        updateTerminal(line)
                    }
                    updateTerminal(result)
                    return@launch
                }

                lowerCmd.startsWith("ping") -> {
                    val target = cleanCmd.substringAfter("ping").trim().ifEmpty { "1.1.1.1" }
                    val result = RealNetworkInspector.performPing(target) { line ->
                        updateTerminal(line)
                    }
                    updateTerminal(result)
                    return@launch
                }

                lowerCmd.startsWith("dns ") || lowerCmd.startsWith("dig ") || lowerCmd.startsWith("nslookup ") -> {
                    val query = when {
                        cleanCmd.startsWith("dns ") -> cleanCmd.substringAfter("dns ")
                        cleanCmd.startsWith("dig ") -> cleanCmd.substringAfter("dig ")
                        else -> cleanCmd.substringAfter("nslookup ")
                    }.trim()
                    val result = com.example.net.KaliNetworkSuite.performAdvancedDig(query)
                    updateTerminal(result)
                    return@launch
                }

                cleanCmd.startsWith("wget ") || cleanCmd == "wget" -> {
                    val rawUrl = cleanCmd.removePrefix("wget").trim()
                    if (rawUrl.isEmpty() || rawUrl == "-h" || rawUrl == "--help") {
                        updateTerminal("Usage: wget <url> [-O filename]")
                        return@launch
                    }
                    val targetUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) "https://$rawUrl" else rawUrl
                    val fileName = targetUrl.substringBefore("?").substringAfterLast("/").ifEmpty { "download_${System.currentTimeMillis()}" }
                    val targetFile = File(localCwdFile, fileName)
                    updateTerminal("[*] Connecting to $targetUrl...")
                    try {
                        val url = java.net.URL(targetUrl)
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 8000
                        conn.readTimeout = 15000
                        conn.setRequestProperty("User-Agent", "Wget/1.21.3 (linux-gnu)")
                        conn.connect()
                        val code = conn.responseCode
                        if (code !in 200..299) {
                            updateTerminal("wget: HTTP request sent, awaiting response... $code ${conn.responseMessage}\nERROR $code: Failed to download.")
                            conn.disconnect()
                            return@launch
                        }
                        val totalBytes = conn.contentLengthLong
                        updateTerminal("Saving to: '$fileName' (${if (totalBytes > 0) "${totalBytes / 1024} KB" else "unknown size"})")
                        val input = conn.inputStream
                        val output = targetFile.outputStream()
                        val buf = ByteArray(8192)
                        var read: Int
                        var downloaded = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            downloaded += read
                        }
                        output.flush()
                        output.close()
                        input.close()
                        conn.disconnect()
                        updateTerminal("[100%] '$fileName' saved [$downloaded bytes].")
                    } catch (e: Exception) {
                        updateTerminal("wget: unable to resolve host address or download failed: ${e.localizedMessage}")
                    }
                    return@launch
                }

                lowerCmd == "netstat" || lowerCmd == "ss" -> {
                    val result = RealNetworkInspector.getNetstatOutput(getApplication())
                    updateTerminal(result)
                    return@launch
                }

                lowerCmd == "netinfo" || lowerCmd == "network" -> {
                    val result = RealNetworkInspector.getDetailedNetworkDiagnostics(getApplication())
                    updateTerminal(result)
                    return@launch
                }

                lowerCmd == "booster" || lowerCmd == "clean-ram" || lowerCmd == "boost" -> {
                    updateTerminal("[*] Operativ xotira (RAM) optimallashtirilmoqda...")
                    val report = RealMemoryBooster.optimizeMemory(getApplication())
                    val msg = """
                    [✔️] RAM optimallashtirish yakunlandi!
                    • Umumiy RAM      : ${report["totalGb"]}
                    • Oldingi bo'sh   : ${report["availBeforeMb"]}
                    • Hozirgi bo'sh   : ${report["availAfterMb"]}
                    • Bo'shatilgan RAM: ${report["freedMb"]} ⚡
                    • Yopilgan vazifalar: ${report["killedCount"]} ta fon ilovasi
                    """.trimIndent()
                    updateTerminal(msg)
                    return@launch
                }

                lowerCmd == "lsblk" || lowerCmd == "df -h" || lowerCmd == "df" -> {
                    val realDf = executeRootOrAdbOrLocalCommand(if (lowerCmd == "lsblk") "lsblk" else "df -h")
                    if (realDf.isNotBlank()) {
                        updateTerminal(realDf)
                    } else {
                        updateTerminal("Error: Command restricted by Android SELinux policy or not found.")
                    }
                    return@launch
                }

                cleanCmd.startsWith("git clone ") -> {
                    val repoUrl = cleanCmd.substringAfter("git clone ").trim()
                    val result = RealGitCloneHelper.cloneGitRepo(getApplication(), repoUrl) { progress ->
                        updateTerminal(progress)
                    }
                    updateTerminal(result)
                    return@launch
                }

                lowerCmd.startsWith("calc ") -> {
                    val expr = cleanCmd.substringAfter("calc ").trim()
                    val ans = RealMathEvaluator.eval(expr)
                    updateTerminal("= $ans")
                    return@launch
                }

                lowerCmd == "unxz" || lowerCmd.startsWith("unxz ") ||
                (lowerCmd.startsWith("xz ") && (lowerCmd.contains("-d") || lowerCmd.contains("--decompress"))) -> {
                    val rawArgs = if (lowerCmd.startsWith("unxz")) cleanCmd.removePrefix("unxz").trim() else cleanCmd.removePrefix("xz").trim()
                    if (rawArgs.isEmpty() || rawArgs == "--help" || rawArgs == "-h") {
                        val help = """
                        Usage: unxz [OPTION]... [FILE]...
                        Decompress FILEs in the .xz format (LZMA2).
                          -k, --keep       Keep (don't delete) input files
                          -f, --force      Force overwrite of output file
                          -v, --verbose    Be verbose
                        Examples:
                          unxz -k kali-rootfs-arm64.tar.xz
                          unxz /storage/emulated/0/Download/kali-rootfs-arm64.tar.xz
                        """.trimIndent()
                        updateTerminal(help)
                        return@launch
                    }

                    val keep = !rawArgs.contains("--delete")
                    val cleanFileArg = rawArgs.replace("-k", "").replace("--keep", "")
                        .replace("-f", "").replace("--force", "")
                        .replace("-v", "").replace("--verbose", "")
                        .replace("-d", "").replace("--decompress", "")
                        .trim()

                    val targetFile = ArchiveEngine.resolveFile(cleanFileArg, localCwdFile)
                    if (!targetFile.exists()) {
                        updateTerminal("unxz: ${targetFile.absolutePath}: No such file or directory")
                        return@launch
                    }

                    val origSize = ArchiveEngine.formatSize(targetFile.length())
                    updateTerminal("[*] XZ decompress boshlandi: ${targetFile.name} ($origSize)\n[*] Dvijok: Native LZMA2 Multi-threaded Decoder...")

                    val result = ArchiveEngine.decompressXz(
                        sourceFile = targetFile,
                        keepOriginal = keep
                    ) { prog ->
                        if (prog.percent == 100 || prog.percent % 25 == 0) {
                            val doneMb = "%.1f MB".format(Locale.US, prog.bytesProcessed / (1024.0 * 1024.0))
                            val speed = "%.1f MB/s".format(Locale.US, prog.speedMbPerSec)
                            updateTerminal("[...] Decompress jarayoni: $doneMb (${prog.percent}%) | Tezlik: $speed")
                        }
                    }

                    if (result.isSuccess) {
                        val out = result.getOrNull()!!
                        val outSize = ArchiveEngine.formatSize(out.length())
                        val msg = """
                        [✔️] Muvaffaqiyatli decompress qilindi!
                        • Natija fayli : ${out.absolutePath}
                        • Asl hajm     : $origSize (.xz)
                        • Chiqqan hajm : $outSize (.tar)
                        """.trimIndent()
                        updateTerminal(msg)
                    } else {
                        updateTerminal("[-] Xatolik: ${result.exceptionOrNull()?.localizedMessage}")
                    }
                    return@launch
                }

                lowerCmd == "xz" || lowerCmd.startsWith("xz ") -> {
                    val rawArgs = cleanCmd.removePrefix("xz").trim()
                    if (rawArgs.isEmpty() || rawArgs == "--help" || rawArgs == "-h") {
                        val help = """
                        Usage: xz [OPTION]... [FILE]...
                        Compress or decompress FILEs in the .xz format.
                          -d, --decompress  Decompress archive
                          -k, --keep        Keep (don't delete) input files
                          -f, --force       Force overwrite of output file
                        Example:
                          xz -d -k kali-rootfs-arm64.tar.xz
                        """.trimIndent()
                        updateTerminal(help)
                        return@launch
                    }
                    updateTerminal("xz: Faylni ochish uchun 'unxz fayl.tar.xz' yoki 'xz -d fayl.tar.xz' buyrug'idan foydalaning.")
                    return@launch
                }

                lowerCmd.startsWith("tar ") -> {
                    val rawArgs = cleanCmd.substringAfter("tar ").trim()
                    if (rawArgs.isEmpty() || rawArgs == "--help" || rawArgs == "-h") {
                        val help = """
                        Usage: tar [OPTION...] [FILE]...
                        GNU / POSIX TAR archive manager.
                          -x, --extract    Extract files from an archive
                          -v, --verbose    Verbosely list files processed
                          -f, --file=ARCHIVE Use archive file
                          -C, --directory=DIR Change to DIR before operation
                        Examples:
                          tar -xvf kali-rootfs-arm64.tar.xz
                          tar -xvf kali-rootfs-arm64.tar -C /sdcard/Home/kali/
                        """.trimIndent()
                        updateTerminal(help)
                        return@launch
                    }

                    val isExtract = rawArgs.contains("-x") || rawArgs.contains("--extract")
                    if (isExtract) {
                        val destDir = if (rawArgs.contains("-C")) {
                            val afterC = rawArgs.substringAfter("-C").trim().split(" ").firstOrNull() ?: ""
                            ArchiveEngine.resolveFile(afterC, localCwdFile)
                        } else {
                            localCwdFile
                        }

                        val tokens = rawArgs.split("\\s+".toRegex())
                        val archiveToken = tokens.find { 
                            it.endsWith(".tar", ignoreCase = true) || 
                            it.endsWith(".tar.xz", ignoreCase = true) || 
                            it.endsWith(".tar.gz", ignoreCase = true) ||
                            it.endsWith(".tgz", ignoreCase = true)
                        } ?: tokens.lastOrNull { !it.startsWith("-") && it != destDir.absolutePath }

                        if (archiveToken == null) {
                            updateTerminal("tar: Arxiv fayli ko'rsatilmadi.")
                            return@launch
                        }

                        val archiveFile = ArchiveEngine.resolveFile(archiveToken, localCwdFile)
                        if (!archiveFile.exists()) {
                            updateTerminal("tar: ${archiveFile.absolutePath}: No such file or directory")
                            return@launch
                        }

                        updateTerminal("[*] TAR arxivdan chiqarilmoqda: ${archiveFile.name} -> ${destDir.absolutePath}...")
                        var lastLoggedEntries = 0
                        val res = ArchiveEngine.extractTarArchive(archiveFile, destDir) { prog ->
                            if (prog.entriesProcessed - lastLoggedEntries >= 500) {
                                lastLoggedEntries = prog.entriesProcessed
                                updateTerminal("[...] ${prog.entriesProcessed} ta fayl ajratildi: ${prog.currentEntryName.takeLast(40)}")
                            }
                        }

                        if (res.isSuccess) {
                            updateTerminal("[✔️] TAR muvaffaqiyatli ochildi! Jami ${res.getOrNull()} ta fayl chiqarildi: ${destDir.absolutePath}")
                        } else {
                            updateTerminal("[-] TAR xatosi: ${res.exceptionOrNull()?.localizedMessage}")
                        }
                        return@launch
                    }

                    shellSessionManager.write("$cleanCmd\n")
                    return@launch
                }

                lowerCmd == "pkg" || lowerCmd.startsWith("pkg ") ||
                lowerCmd == "apt" || lowerCmd.startsWith("apt ") -> {
                    val rawSub = if (lowerCmd.startsWith("pkg")) cleanCmd.removePrefix("pkg").trim() else cleanCmd.removePrefix("apt").trim()
                    val subCmd = rawSub.substringBefore(" ").trim().lowercase(Locale.US)
                    val subArgs = rawSub.substringAfter(" ", "").trim()

                    when {
                        subCmd.isEmpty() || subCmd == "help" || subCmd == "--help" || subCmd == "-h" -> {
                            val help = """
                            ============================================================
                                      TERMINAL CORE PACKAGE MANAGER (PKG / APT)
                            ============================================================
                            Mavjud buyruqlar:
                              pkg install <nom>     - Linux paketini o'rnatish (masalan: pkg install python)
                              pkg uninstall <nom>   - Paketni tizimdan o'chirish (remove)
                              pkg upgrade           - Barcha o'rnatilgan paketlarni eng yangi versiyaga yangilash
                              pkg search <so'z>     - 2000+ paketlar ichidan qidirish
                              pkg show <nom>        - Paket haqida to'liq metama'lumotlar
                              pkg update            - Repozitoriya indeksini yangilash
                              pkg list-all          - Barcha mavjud paketlar ro'yxati
                              pkg list-installed    - O'rnatilgan paketlar ro'yxati
                              pkg clean             - Keshdagi .deb arxivlarni tozalash
                              pkg bootstrap         - To'liq Native Linux Userspace muhitini o'rnatish
                            Misollar:
                              pkg install python git nano nmap curl
                              pkg search clang
                              pkg show python
                              pkg upgrade
                              pkg update
                            ============================================================
                            """.trimIndent()
                            updateTerminal(help)
                        }
                        subCmd == "update" || subCmd == "up" || subCmd == "refresh" -> {
                            val res = packageManager.updateRepositoryIndex { updateTerminal(it) }
                            if (res.isFailure) {
                                updateTerminal("[-] Yangilanishda xatolik: ${res.exceptionOrNull()?.localizedMessage}")
                            }
                        }
                        subCmd == "upgrade" || subCmd == "full-upgrade" -> {
                            updateTerminal("[*] Barcha o'rnatilgan paketlar yangilanmoqda...")
                            val res = packageManager.upgradePackages { updateTerminal(it) }
                            if (res.isFailure) {
                                updateTerminal("[-] Yangilashda xatolik: ${res.exceptionOrNull()?.localizedMessage}")
                            }
                        }
                        subCmd == "install" || subCmd == "in" || subCmd == "add" -> {
                            if (subArgs.isEmpty()) {
                                updateTerminal("Usage: pkg install <package_name>")
                                return@launch
                            }
                            val packages = subArgs.split("\\s+".toRegex()).filter { it.isNotBlank() }
                            for (p in packages) {
                                updateTerminal("[*] '$p' paketi o'rnatilmoqda...")
                                val res = packageManager.installPackage(p) { updateTerminal(it) }
                                if (res.isFailure) {
                                    updateTerminal("[-] '$p' o'rnatishda xatolik: ${res.exceptionOrNull()?.localizedMessage}")
                                }
                            }
                        }
                        subCmd == "uninstall" || subCmd == "remove" || subCmd == "rm" -> {
                            if (subArgs.isEmpty()) {
                                updateTerminal("Usage: pkg uninstall <package_name>")
                                return@launch
                            }
                            val packages = subArgs.split("\\s+".toRegex()).filter { it.isNotBlank() }
                            for (p in packages) {
                                val res = packageManager.uninstallPackage(p) { updateTerminal(it) }
                                if (res.isFailure) {
                                    updateTerminal("[-] '$p' o'chirishda xatolik: ${res.exceptionOrNull()?.localizedMessage}")
                                }
                            }
                        }
                        subCmd == "reinstall" -> {
                            if (subArgs.isEmpty()) {
                                updateTerminal("Usage: pkg reinstall <package_name>")
                                return@launch
                            }
                            val packages = subArgs.split("\\s+".toRegex()).filter { it.isNotBlank() }
                            for (p in packages) {
                                updateTerminal("[*] '$p' qayta o'rnatilmoqda...")
                                packageManager.uninstallPackage(p)
                                packageManager.installPackage(p) { updateTerminal(it) }
                            }
                        }
                        subCmd == "show" || subCmd == "info" -> {
                            if (subArgs.isEmpty()) {
                                updateTerminal("Usage: pkg show <package_name>")
                                return@launch
                            }
                            val info = packageManager.getPackageInfo(subArgs)
                            updateTerminal(info)
                        }
                        subCmd == "clean" || subCmd == "autoclean" -> {
                            val freed = packageManager.cleanCache()
                            val mb = String.format(Locale.US, "%.2f MB", freed / (1024.0 * 1024.0))
                            updateTerminal("[✔️] Paketlar keshi tozalandi! Bo'shatilgan joy: $mb")
                        }
                        subCmd == "search" || subCmd == "find" -> {
                            if (subArgs.isEmpty()) {
                                updateTerminal("Usage: pkg search <keyword>")
                                return@launch
                            }
                            updateTerminal("[*] '$subArgs' bo'yicha qidirilmoqda...")
                            val found = packageManager.searchPackages(subArgs)
                            if (found.isEmpty()) {
                                updateTerminal("[-] Hech qanday paket topilmadi: '$subArgs'")
                            } else {
                                val sb = StringBuilder()
                                sb.append("============================================================\n")
                                sb.append("        TOPILGAN PAKETLAR (${found.size} ta)\n")
                                sb.append("============================================================\n")
                                found.take(25).forEach { p ->
                                    val status = if (p.isInstalled) " [O'rnatilgan]" else ""
                                    sb.append("• ${p.name}/${p.section} v${p.version} (${p.size / 1024} KB)$status\n")
                                    if (p.description.isNotEmpty()) {
                                        sb.append("  ${p.description.take(70)}\n")
                                    }
                                }
                                if (found.size > 25) {
                                    sb.append("\n... va yana ${found.size - 25} ta paket. Aniqroq qidirish uchun: 'pkg search <nom>'\n")
                                }
                                sb.append("============================================================")
                                updateTerminal(sb.toString())
                            }
                        }
                        subCmd == "list" || subCmd == "list-all" -> {
                            val index = packageManager.getPackageIndex()
                            val sb = StringBuilder()
                            sb.append("============================================================\n")
                            sb.append("        BARCHA MAVJUD PAKETLAR (${index.size} ta)\n")
                            sb.append("============================================================\n")
                            val installed = packageManager.getInstalledPackageNames()
                            index.values.sortedBy { it.name }.take(40).forEach { p ->
                                val status = if (installed.contains(p.name)) " [O'rnatilgan]" else ""
                                sb.append("• ${p.name} v${p.version}$status - ${p.description.take(50)}\n")
                            }
                            if (index.size > 40) {
                                sb.append("\n... jami ${index.size} ta paket. Qidirish uchun: 'pkg search <nom>'\n")
                            }
                            sb.append("============================================================")
                            updateTerminal(sb.toString())
                        }
                        subCmd == "list-installed" || subCmd == "installed" -> {
                            val installed = packageManager.getInstalledPackageNames()
                            if (installed.isEmpty()) {
                                updateTerminal("Hozircha o'rnatilgan qo'shimcha paketlar yo'q. 'pkg install <nom>' orqali o'rnating.")
                            } else {
                                val sb = StringBuilder("O'RNATILGAN PAKETLAR (${installed.size} ta):\n")
                                installed.sorted().forEach { sb.append("• $it\n") }
                                updateTerminal(sb.toString().trimEnd())
                            }
                        }
                        subCmd == "bootstrap" || subCmd == "setup" -> {
                            updateTerminal("[*] Terminal Native Linux Userspace Bootstrap o'rnatilmoqda...")
                            val res = packageManager.installCoreBootstrap { updateTerminal(it) }
                            if (res.isFailure) {
                                updateTerminal("[-] Bootstrap o'rnatishda xatolik: ${res.exceptionOrNull()?.localizedMessage}")
                            }
                        }
                        else -> {
                            updateTerminal("pkg: Noma'lum buyruq: '$subCmd'. 'pkg help' deb yozing.")
                        }
                    }
                    return@launch
                }

                lowerCmd == "proot" || lowerCmd.startsWith("proot ") ||
                lowerCmd == "proot-distro" || lowerCmd.startsWith("proot-distro ") -> {
                    val sub = cleanCmd.removePrefix("proot-distro").removePrefix("proot").trim()
                    when {
                        sub.isEmpty() || sub == "status" || sub == "--help" || sub == "-h" -> {
                            updateTerminal(prootManager.getStatusDiagnostic())
                        }
                        sub == "install" || sub == "setup" || sub == "download" -> {
                            updateTerminal("[*] PRoot dvijogi yuklanmoqda, ELF header va arxitektura tekshirilmoqda...")
                            val res = prootManager.downloadAndInstallProot { updateTerminal(it) }
                            if (res.isSuccess) {
                                val file = res.getOrNull()!!
                                val elf = prootManager.validateElfBinary(file)
                                val smoke = prootManager.performSmokeTest(file)
                                val msg = """
                                [✔️] PRoot dvijogi muvaffaqiyatli o'rnatildi va tasdiqlandi!
                                • Manzil: ${file.absolutePath}
                                • Arxitektura: ${elf.archName}
                                • SHA-256: ${elf.sha256}
                                • Smoke Test: ${smoke.rawOutput.lines().firstOrNull() ?: "OK"}
                                Endi 'kali login' yoki 'proot login kali' deb yozing.
                                """.trimIndent()
                                updateTerminal(msg)
                            } else {
                                updateTerminal("[-] PRoot yuklashda xatolik: ${res.exceptionOrNull()?.localizedMessage}")
                            }
                        }
                        sub == "test" || sub == "--version" || sub == "-v" -> {
                            val verified = prootManager.findVerifiedProotBinary()
                            if (verified != null) {
                                val elf = prootManager.validateElfBinary(verified)
                                val smoke = prootManager.performSmokeTest(verified)
                                val report = """
                                ============================================================
                                            PROOT DVIJOGI SMOKE TESTI
                                ============================================================
                                • Fayl         : ${verified.absolutePath}
                                • Fayl hajmi   : ${verified.length()} bayt (${verified.length() / 1024} KB)
                                • ELF Magic    : Tasdiqlandi (0x7F 'E' 'L' 'F')
                                • Arxitektura  : ${elf.archName} (64-bit: ${elf.is64Bit}, LittleEndian: ${elf.isLittleEndian})
                                • Machine Code : ${elf.machineCode}
                                • SHA-256      : ${elf.sha256}
                                • Exit Code    : ${smoke.exitCode}
                                • Natija       :
                                ${smoke.rawOutput}
                                ============================================================
                                """.trimIndent()
                                updateTerminal(report)
                            } else {
                                updateTerminal("[-] PRoot dvijogi topilmadi yoki tasdiqlanmadi. 'proot install' buyrug'ini bering.")
                            }
                        }
                        sub.startsWith("login") || sub.startsWith("shell") || sub.startsWith("enter") -> {
                            val targetDistro = sub.substringAfter(" ").trim().ifEmpty { "kali" }
                            val targetDir = if (targetDistro == "kali") prootManager.kaliRootDir else File(prootManager.rootfsBaseDir, targetDistro)
                            
                            val hasSh = File(targetDir, "bin/sh").exists() || File(targetDir, "usr/bin/bash").exists() || File(targetDir, "bin/bash").exists()
                            if (!hasSh) {
                                updateTerminal("[-] PRoot: '$targetDistro' rootfs tizimi topilmadi (${targetDir.absolutePath}).\nAvval 'kali install' yoki 'kali deploy <fayl.tar.xz>' buyrug'ini bering.")
                                return@launch
                            }

                            prootManager.configureRootfsEnvironment(targetDir)
                            prootManager.fixRootfsPermissions(targetDir)

                            var prootBin = prootManager.findVerifiedProotBinary()
                            if (prootBin == null) {
                                updateTerminal("[*] PRoot dvijogi mavjud emas, avtomatik yuklanmoqda va tekshirilmoqda...")
                                val installRes = prootManager.downloadAndInstallProot { updateTerminal(it) }
                                prootBin = installRes.getOrNull()
                            }

                            if (prootBin != null) {
                                val shellToUse = if (File(targetDir, "usr/bin/bash").exists() || File(targetDir, "bin/bash").exists()) "/bin/bash -l" else "/bin/sh -l"
                                val prootCmd = prootManager.buildProotCommand(targetDir, shellToUse)
                                updateTerminal("[*] Kali Linux PRoot konteyneri ishga tushirilmoqda (root@$targetDistro)...")
                                shellSessionManager.write("$prootCmd\n")
                            } else {
                                updateTerminal("[-] PRoot dvijogini yuklab bo'lmadi. Internetga ulanib 'proot install' buyrug'ini bering.")
                            }
                        }
                        sub.startsWith("exec ") || sub.startsWith("run ") -> {
                            val cmdToExec = sub.substringAfter(" ").trim()
                            var prootBin = prootManager.findVerifiedProotBinary()
                            if (prootBin == null) {
                                val installRes = prootManager.downloadAndInstallProot { updateTerminal(it) }
                                prootBin = installRes.getOrNull()
                            }
                            if (prootBin != null) {
                                val fullCmd = prootManager.buildProotCommand(prootManager.kaliRootDir, cmdToExec)
                                shellSessionManager.write("$fullCmd\n")
                            } else {
                                updateTerminal("[-] PRoot topilmadi. 'proot install' buyrug'ini bering.")
                            }
                        }
                        else -> {
                            updateTerminal("proot: Noma'lum buyruq. 'proot status', 'proot install', 'proot test' yoki 'proot login kali' buyrug'ini yozing.")
                        }
                    }
                    return@launch
                }

                lowerCmd == "kali" || lowerCmd.startsWith("kali ") -> {
                    val sub = cleanCmd.removePrefix("kali").trim()
                    when {
                        sub == "status" || sub.isEmpty() -> {
                            updateTerminal(kaliManager.getStatusDiagnostic())
                        }
                        sub == "install" || sub == "setup" || sub == "download" -> {
                            updateTerminal("[*] 1/2: Kali Linux ARM64 rootfs tizimi internetdan yuklanmoqda va o'rnatilmoqda...")
                            val res = kaliManager.downloadAndInstallKaliRootfs { updateTerminal(it) }
                            if (res.isSuccess) {
                                prootManager.configureRootfsEnvironment(kaliManager.kaliRootDir)
                                prootManager.fixRootfsPermissions(kaliManager.kaliRootDir)
                                updateTerminal("[✔️] Kali Linux Rootfs tayyor (${res.getOrNull()} ta fayl)!")
                                
                                updateTerminal("[*] 2/2: PRoot konteyner dvijogi yuklanmoqda va sozlanmoqda...")
                                val prootRes = prootManager.downloadAndInstallProot { updateTerminal(it) }
                                if (prootRes.isSuccess) {
                                    val verified = prootRes.getOrNull()!!
                                    val elf = prootManager.validateElfBinary(verified)
                                    val smoke = prootManager.performSmokeTest(verified)
                                    val msg = """
                                    [✔️] PRoot dvijogi tasdiqlandi:
                                    • Manzil: ${verified.absolutePath}
                                    • Arxitektura: ${elf.archName}
                                    • Smoke Test: ${smoke.rawOutput.lines().firstOrNull() ?: "OK"}
                                    ========================================
                                    [🚀] Kali Linux to'liq o'rnatildi! Endi 'kali login' deb yozing.
                                    ========================================
                                    """.trimIndent()
                                    updateTerminal(msg)
                                } else {
                                    updateTerminal("[-] PRoot yuklashda xatolik: ${prootRes.exceptionOrNull()?.localizedMessage}\n'proot install' buyrug'ini bering.")
                                }
                            } else {
                                updateTerminal("[-] O'rnatishda xatolik: ${res.exceptionOrNull()?.localizedMessage}")
                            }
                        }
                        sub == "test" -> {
                            updateTerminal("[*] Kali Linux Userspace & PRoot Smoke Test bajarilmoqda...")
                            val report = prootManager.runContainerSmokeTest(kaliManager.kaliRootDir)
                            updateTerminal(report)
                        }
                        sub == "init" -> {
                            val res = kaliManager.initializeHierarchy()
                            updateTerminal(res)
                        }
                        sub == "login" || sub == "shell" || sub == "enter" -> {
                            var hasSh = File(kaliManager.kaliRootDir, "bin/sh").exists() || 
                                        File(kaliManager.kaliRootDir, "usr/bin/bash").exists() || 
                                        File(kaliManager.kaliRootDir, "bin/bash").exists() ||
                                        File(kaliManager.kaliRootDir, "usr/bin/sh").exists()
                            if (!hasSh) {
                                updateTerminal("[*] Kali Linux tizimi topilmadi. Avtomatik ravishda ARM64 Kali Rootfs yuklanmoqda va o'rnatilmoqda...")
                                val dlRootfs = kaliManager.downloadAndInstallKaliRootfs { updateTerminal(it) }
                                if (dlRootfs.isFailure) {
                                    updateTerminal("[-] Kali Rootfs yuklab bo'lmadi: ${dlRootfs.exceptionOrNull()?.localizedMessage}\nIltimos, internetga ulanib 'kali install' buyrug'ini bering.")
                                    return@launch
                                }
                                hasSh = true
                            }
                            prootManager.configureRootfsEnvironment(kaliManager.kaliRootDir)
                            prootManager.fixRootfsPermissions(kaliManager.kaliRootDir)

                            var prootBin = prootManager.findVerifiedProotBinary()
                            if (prootBin == null) {
                                updateTerminal("[*] PRoot dvijogi tekshirilmoqda, mavjud bo'lmasa yuklanadi...")
                                val dlRes = prootManager.downloadAndInstallProot { updateTerminal(it) }
                                prootBin = dlRes.getOrNull()
                            }

                            if (prootBin != null) {
                                val shellToUse = if (File(kaliManager.kaliRootDir, "usr/bin/bash").exists() || File(kaliManager.kaliRootDir, "bin/bash").exists()) "/bin/bash -l" else "/bin/sh -l"
                                val prootCmd = prootManager.buildProotCommand(kaliManager.kaliRootDir, shellToUse)
                                updateTerminal("[*] Kali Linux PRoot muhiti ishga tushirilmoqda (root@kali)...")
                                shellSessionManager.write("$prootCmd\n")
                            } else {
                                updateTerminal("[-] PRoot dvijogini avtomatik sozlab bo'lmadi. Internetga ulanib 'proot install' deb yozing.")
                            }
                        }
                        sub.startsWith("deploy") || sub.startsWith("extract") -> {
                            val rawPath = sub.substringAfter(" ").trim()
                            if (rawPath.isEmpty() || rawPath == "deploy" || rawPath == "extract") {
                                updateTerminal("Usage: kali deploy <path-to-kali-rootfs.tar.xz>")
                                return@launch
                            }
                            val archiveFile = ArchiveEngine.resolveFile(rawPath, localCwdFile)
                            if (!archiveFile.exists()) {
                                updateTerminal("kali deploy: ${archiveFile.absolutePath}: Fayl topilmadi!")
                                return@launch
                            }
                            updateTerminal("[*] Kali Linux ARM64 Userspace tizimi o'rnatilmoqda...\n• Manba: ${archiveFile.absolutePath}\n• Nishon: ${kaliManager.kaliRootDir.absolutePath}")
                            var lastCount = 0
                            val res = kaliManager.deployRootfs(archiveFile) { prog ->
                                if (prog.entriesProcessed - lastCount >= 500) {
                                    lastCount = prog.entriesProcessed
                                    updateTerminal("[...] Joylashtirilmoqda (${prog.entriesProcessed} ta fayl): ${prog.currentEntryName.takeLast(35)}")
                                }
                            }
                            if (res.isSuccess) {
                                prootManager.configureRootfsEnvironment(kaliManager.kaliRootDir)
                                prootManager.fixRootfsPermissions(kaliManager.kaliRootDir)
                                updateTerminal("[✔️] Kali Linux ARM64 tizimi to'liq o'rnatildi! (${res.getOrNull()} ta fayl)\nEndi 'kali login', 'kali test' yoki 'proot login kali' buyruqlaridan to'liq foydalanishingiz mumkin.")
                            } else {
                                updateTerminal("[-] O'rnatishda xatolik: ${res.exceptionOrNull()?.localizedMessage}")
                            }
                        }
                        else -> {
                            val targetBin = kaliManager.findBinary(sub.substringBefore(" "))
                            if (targetBin != null) {
                                shellSessionManager.write("$targetBin ${sub.substringAfter(" ", "")}\n")
                            } else {
                                updateTerminal("kali: command '${sub.substringBefore(" ")}' not found in rootfs. Type 'kali status', 'kali test' or 'kali login'.")
                            }
                        }
                    }
                    return@launch
                }

                lowerCmd == "python" || lowerCmd == "python3" || lowerCmd == "py" ||
                lowerCmd.startsWith("python ") || lowerCmd.startsWith("python3 ") || lowerCmd.startsWith("py ") -> {
                    val prefixPython = File(packageManager.binDir, "python3").takeIf { it.exists() }
                        ?: File(packageManager.binDir, "python").takeIf { it.exists() }
                    val realPython = prefixPython?.absolutePath 
                        ?: kaliManager.findBinary("python3") 
                        ?: kaliManager.findBinary("python")

                    val argsStr = when {
                        cleanCmd.startsWith("python3 ") -> cleanCmd.removePrefix("python3 ").trim()
                        cleanCmd.startsWith("python ") -> cleanCmd.removePrefix("python ").trim()
                        cleanCmd.startsWith("py ") -> cleanCmd.removePrefix("py ").trim()
                        else -> ""
                    }

                    if (realPython != null) {
                        shellSessionManager.write("$realPython $argsStr\n")
                    } else {
                        val msg = """
python3: command not found
Note: Python 3 is not yet installed in Terminal.
To install authentic Python on Android:
  • Run: 'pkg install python'
  • Or deploy Kali Linux ARM64 rootfs: 'kali install'
                        """.trimIndent()
                        updateTerminal(msg)
                    }
                    return@launch
                }

                cleanCmd.startsWith("apk-pull ") || cleanCmd.startsWith("apk ") -> {
                    val pkgQuery = cleanCmd.substringAfter(" ").trim()
                    val pkgInfo = findNativePackageByQuery(pkgQuery)
                    if (pkgInfo == null) {
                        updateTerminal("[-] Xatolik: '$pkgQuery' nomli ilova topilmadi.")
                        return@launch
                    }
                    val sourceApk = File(pkgInfo.applicationInfo!!.publicSourceDir ?: pkgInfo.applicationInfo!!.sourceDir)
                    if (!sourceApk.exists()) {
                        updateTerminal("[-] APK fayl manzili topilmadi: ${sourceApk.absolutePath}")
                        return@launch
                    }
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val targetApk = File(downloadDir, "${pkgInfo.packageName}.apk")
                    try {
                        FileInputStream(sourceApk).use { input ->
                            FileOutputStream(targetApk).use { output ->
                                input.copyTo(output)
                            }
                        }
                        val sizeMb = "%.2f MB".format(Locale.US, targetApk.length() / (1024.0 * 1024.0))
                        updateTerminal("[✔️] APK muvaffaqiyatli saqlandi!\n• Manzil: ${targetApk.absolutePath}\n• Hajm: $sizeMb")
                    } catch (e: Exception) {
                        updateTerminal("[-] Nusxalashda xatolik: ${e.localizedMessage}")
                    }
                    return@launch
                }

                lowerCmd == "pc-link" -> {
                    val ip = RealNetworkInspector.getLocalDeviceIpAddress(getApplication())
                    val msg = """
                    ============================================================
                              KOMPYUTERGA ADB ORQALI ULASH YO'RIQNOMASI
                    ============================================================
                    1. Telefoningiz va Kompyuteringiz bir xil Wi-Fi tarmog'iga
                       ulanganligini tekshiring.
                    2. Telefonda: Sozlamalar -> Ishlab chiquvchi opsiyalari ->
                       'Simsiz nosozliklarni tuzatish' (Wireless Debugging) ni yoqing.
                    3. Kompyuteringiz terminalida (CMD / PowerShell / Bash) yozing:
                       👉  adb connect $ip:5555
                    4. Ekranda ulanishni tasdiqlash oynasi chiqsa, 'Ruxsat berish' ni bosing.
                    ============================================================
                    """.trimIndent()
                    updateTerminal(msg)
                    return@launch
                }

                lowerCmd == "app-update" || lowerCmd.startsWith("app-update ") ||
                lowerCmd == "terminal-update" || lowerCmd.startsWith("terminal-update ") ||
                lowerCmd == "self-update" || lowerCmd.startsWith("self-update ") -> {
                    val rawArgs = cleanCmd.substringAfter(" ").trim()
                    val sub = if (cleanCmd.contains(" ")) rawArgs else ""

                    when {
                        sub == "gui" || sub == "dialog" -> {
                            showAppUpdateDialog.value = true
                            updateTerminal("[*] OTA Yangilanish dialogi ochildi.")
                        }
                        sub.startsWith("set-url ") -> {
                            val newUrl = sub.removePrefix("set-url ").trim()
                            appUpdateManager.setUpdateUrl(newUrl)
                            updateTerminal("[✔️] Yangilanish serveri manzili saqlandi:\n$newUrl")
                        }
                        sub.startsWith("http://") || sub.startsWith("https://") -> {
                            updateTerminal("[*] Maxsus APK havolasi orqali yangilanmoqda: $sub")
                            val dlRes = appUpdateManager.downloadApk(sub, "manual") { pct, down, tot, spd ->
                                if (pct % 25 == 0) {
                                    updateTerminal("  Yuklanmoqda: $pct% (${down / (1024 * 1024)}MB / ${tot / (1024 * 1024)}MB) | ${String.format(Locale.US, "%.1f", spd)} MB/s")
                                }
                            }
                            if (dlRes.isSuccess) {
                                val apkFile = dlRes.getOrNull()!!
                                appUpdateManager.installApk(apkFile) { updateTerminal(it) }
                            } else {
                                updateTerminal("[-] Yuklab olishda xatolik: ${dlRes.exceptionOrNull()?.localizedMessage}")
                            }
                        }
                        sub == "check" || sub.isEmpty() -> {
                            updateTerminal("[*] Yangi talqin serverdan tekshirilmoqda...")
                            val checkRes = appUpdateManager.checkForUpdates { updateTerminal(it) }
                            if (checkRes.isSuccess) {
                                val info = checkRes.getOrNull()!!
                                if (info.hasUpdate) {
                                    val msg = """
                                    ============================================================
                                    [🎉] YANGI VERSİYA TOPILDI!
                                    ============================================================
                                      Joriy versiya : v${info.currentVersionName} (Build ${info.currentVersionCode})
                                      Yangi versiya : v${info.latestVersionName} (Build ${info.latestVersionCode})
                                      Sana          : ${info.releaseDate}
                                      Changelog     : ${info.changelog}
                                      Yuklab olish  : ${info.downloadUrl}
                                    ------------------------------------------------------------
                                    O'rnatish uchun buyruq:
                                      app-update install
                                    Yoki grafik menyuda:
                                      app-update gui
                                    ============================================================
                                    """.trimIndent()
                                    updateTerminal(msg)
                                } else {
                                    updateTerminal("[✔️] Siz eng so'nggi versiyadan foydalanmoqdasiz (v${info.currentVersionName}). Yangilanish shart emas.")
                                }
                            } else {
                                updateTerminal("[-] Tekshirishda xatolik: ${checkRes.exceptionOrNull()?.localizedMessage}")
                            }
                        }
                        sub == "install" || sub == "now" || sub == "download" -> {
                            updateTerminal("[*] Yangi talqin tekshirilmoqda va avtomatik yuklab olinmoqda...")
                            val checkRes = appUpdateManager.checkForUpdates { updateTerminal(it) }
                            val info = checkRes.getOrNull()
                            if (info != null && info.hasUpdate && info.downloadUrl.isNotBlank()) {
                                updateTerminal("[*] v${info.latestVersionName} yuklab olinmoqda...")
                                val dlRes = appUpdateManager.downloadApk(info.downloadUrl, info.latestVersionName) { pct, down, tot, spd ->
                                    if (pct % 25 == 0) {
                                        updateTerminal("  Yuklanmoqda: $pct% (${down / (1024 * 1024)}MB / ${tot / (1024 * 1024)}MB)")
                                    }
                                }
                                if (dlRes.isSuccess) {
                                    val apkFile = dlRes.getOrNull()!!
                                    appUpdateManager.installApk(apkFile) { updateTerminal(it) }
                                } else {
                                    updateTerminal("[-] Yuklab olishda xatolik: ${dlRes.exceptionOrNull()?.localizedMessage}")
                                }
                            } else if (info != null && !info.hasUpdate) {
                                updateTerminal("[✔️] Siz allaqachon eng so'nggi versiyadasiz (v${info.currentVersionName}).")
                            } else {
                                updateTerminal("[-] Yangilanish ma'lumotlarini olib bo'lmadi. Havola orqali yangilash uchun: 'app-update <apk_url>'")
                            }
                        }
                        else -> {
                            updateTerminal("Usage: app-update [check | install | gui | set-url <url> | <direct_apk_url>]")
                        }
                    }
                    return@launch
                }

                lowerCmd.startsWith("sys-update") || lowerCmd.startsWith("os-update") -> {
                    try {
                        val updateIntent = Intent("android.settings.SYSTEM_UPDATE_SETTINGS").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        getApplication<Application>().startActivity(updateIntent)
                        updateTerminal("[✔️] Rasmiy 'Tizim Yangilanishi' sahifasi ochildi.")
                    } catch (e: Exception) {
                        val genIntent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        getApplication<Application>().startActivity(genIntent)
                        updateTerminal("[✔️] Sozlamalar menyusi ochildi.")
                    }
                    return@launch
                }

                cleanCmd.startsWith("curl ") || cleanCmd == "curl" -> {
                    val rawArgs = cleanCmd.substringAfter("curl").trim()
                    if (rawArgs.isEmpty() || rawArgs == "--help" || rawArgs == "-h") {
                        updateTerminal("Usage: curl [-I|--head] [-s|--silent] [-v|--verbose] <url>")
                        return@launch
                    }
                    val isHead = rawArgs.contains("-I") || rawArgs.contains("--head")
                    val isSilent = rawArgs.contains("-s") || rawArgs.contains("--silent")
                    val isVerbose = rawArgs.contains("-v") || rawArgs.contains("--verbose")

                    var targetUrl = rawArgs.replace("-I", "").replace("--head", "")
                        .replace("-s", "").replace("--silent", "")
                        .replace("-v", "").replace("--verbose", "").trim()

                    if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                        targetUrl = "https://$targetUrl"
                    }

                    try {
                        val url = URL(targetUrl)
                        val conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = if (isHead) "HEAD" else "GET"
                        conn.connectTimeout = 6000
                        conn.readTimeout = 8000
                        conn.instanceFollowRedirects = true
                        conn.setRequestProperty("User-Agent", "SuperADB-Curl/8.5.0")

                        val code = conn.responseCode
                        val sb = StringBuilder()
                        if (!isSilent || isVerbose) {
                            sb.append("HTTP/1.1 $code ${conn.responseMessage.orEmpty()}\n")
                            for ((k, v) in conn.headerFields) {
                                if (k != null) sb.append("$k: ${v.joinToString(", ")}\n")
                            }
                            sb.append("\n")
                        }

                        if (!isHead) {
                            val inStream = if (code in 200..299) conn.inputStream else conn.errorStream
                            val text = inStream?.bufferedReader()?.use { it.readText() } ?: ""
                            sb.append(text.take(3000))
                            if (text.length > 3000) sb.append("\n\n... [Truncated]")
                        }
                        updateTerminal(sb.toString())
                    } catch (e: Exception) {
                        updateTerminal("curl: (7) Failed to connect to $targetUrl: ${e.localizedMessage}")
                    }
                    return@launch
                }

                lowerCmd == "matrix" || lowerCmd == "cmatrix" -> {
                    updateTerminal("Starting digital matrix rain overlay...")
                    _isMatrixRainActive.value = true
                    return@launch
                }


                lowerCmd == "sl" -> {
                    val train = """
                     ====        ________                ___________ 
                 _D _|  |_______/        \__I_I_____===__|_________| 
                  |(_)---  |   H\________/ _____ \   (|host-engine|) 
                  /     |  |   H  |  |     |   | |   (|-----------|) 
                 |      |  |   H  |__-------------------------------- 
                 | ________|___H__/__|_____/[][]~\_______|       |   
                 |/ |   |_____====                 \_____|  ==== |   
                """.trimIndent()
                    updateTerminal(train)
                    return@launch
                }

                lowerCmd == "history" -> {
                    val hist = _commandHistory.value.take(20)
                    if (hist.isEmpty()) {
                        updateTerminal("Buyruqlar tarixi bo'sh.")
                    } else {
                        val sb = StringBuilder("BUYRUQLAR TARIXI:\n")
                        hist.forEachIndexed { i, h ->
                            sb.append(" %3d  %s\n".format(i + 1, h.command))
                        }
                        updateTerminal(sb.toString().trimEnd())
                    }
                    return@launch
                }

                cleanCmd.startsWith("open ", ignoreCase = true) -> {
                    val appName = cleanCmd.substring(5).trim()
                    if (!tryLaunchApp(appName)) {
                        updateTerminal("❌ Ilova topilmadi yoki ishga tushirish imkonsiz: $appName")
                    }
                    return@launch
                }

                // General ADB / Shell command fallback
                else -> {
                    if (_isConnected.value) {
                        val output = try {
                            adbClient.executeCommand(cleanCmd)
                        } catch (e: Exception) {
                            "adb: ${e.localizedMessage}"
                        }
                        if (output.isNotBlank()) {
                            updateTerminal(output)
                        }
                        try {
                            adbDao.insertHistory(
                                HistoryEntity(
                                    command = cleanCmd,
                                    output = output.take(500),
                                    isSuccess = !output.contains("Error") && !output.contains("failed")
                                )
                            )
                        } catch (_: Exception) {}
                    } else {
                        // Real stateful persistent PTY shell execution
                        shellSessionManager.write(cleanCmd + "\n")
                        try {
                            adbDao.insertHistory(
                                HistoryEntity(
                                    command = cleanCmd,
                                    output = "",
                                    isSuccess = true
                                )
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    fun executeLocalCommand(command: String): String {
        return try {
            val appFiles = getApplication<Application>().filesDir
            val safeDir = try {
                if (localCwdFile.exists() && (localCwdFile.absolutePath.startsWith(appFiles.absolutePath) || localCwdFile.absolutePath.startsWith(getApplication<Application>().cacheDir.absolutePath))) {
                    localCwdFile
                } else {
                    appFiles
                }
            } catch (_: Exception) {
                appFiles
            }
            val pb = ProcessBuilder("sh", "-c", "cd \"${localCwdFile.absolutePath}\" 2>/dev/null; $command")
            pb.directory(safeDir)
            val env = pb.environment()
            env["TERM"] = "xterm-256color"
            env["HOME"] = localCwdFile.absolutePath
            env["PWD"] = localCwdFile.absolutePath
            val currentPath = env["PATH"] ?: "/system/bin:/system/xbin"
            env["PATH"] = "$currentPath:/sbin:/system/sbin:/odm/bin:/vendor/bin:/data/local/tmp"

            val process = try {
                pb.start()
            } catch (_: Exception) {
                pb.directory(null)
                pb.start()
            }
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            val output = StringBuilder()
            var line: String? = reader.readLine()
            while (line != null) {
                output.append(line).append("\n")
                line = reader.readLine()
            }

            val errorOutput = StringBuilder()
            var errLine: String? = errorReader.readLine()
            while (errLine != null) {
                errorOutput.append(errLine).append("\n")
                errLine = errorReader.readLine()
            }

            process.waitFor()
            val exitCode = process.exitValue()

            val result = output.toString().trim()
            val errors = errorOutput.toString().trim()

            if (exitCode != 0) {
                buildString {
                    if (errors.isNotEmpty()) append(errors).append("\n")
                    if (result.isNotEmpty()) append(result).append("\n")
                    append("[Exit code: $exitCode]")
                }
            } else {
                if (result.isEmpty() && errors.isEmpty()) {
                    ""
                } else if (errors.isNotEmpty()) {
                    "$errors\n$result".trim()
                } else {
                    result
                }
            }
        } catch (e: Exception) {
            "sh: command execution failed: ${e.localizedMessage}"
        }
    }

    fun executeLocalRootCommand(command: String): String {
        return try {
            val appFiles = getApplication<Application>().filesDir
            val safeDir = try {
                if (localCwdFile.exists() && (localCwdFile.absolutePath.startsWith(appFiles.absolutePath) || localCwdFile.absolutePath.startsWith(getApplication<Application>().cacheDir.absolutePath))) {
                    localCwdFile
                } else {
                    appFiles
                }
            } catch (_: Exception) {
                appFiles
            }
            val pb = ProcessBuilder("su", "-c", "cd \"${localCwdFile.absolutePath}\" 2>/dev/null; $command")
            pb.directory(safeDir)
            val env = pb.environment()
            env["TERM"] = "xterm-256color"
            env["HOME"] = localCwdFile.absolutePath
            env["PWD"] = localCwdFile.absolutePath

            val process = try {
                pb.start()
            } catch (_: Exception) {
                pb.directory(null)
                pb.start()
            }
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            val output = StringBuilder()
            var line: String? = reader.readLine()
            while (line != null) {
                output.append(line).append("\n")
                line = reader.readLine()
            }
            val errorOutput = StringBuilder()
            var errLine: String? = errorReader.readLine()
            while (errLine != null) {
                errorOutput.append(errLine).append("\n")
                errLine = errorReader.readLine()
            }
            process.waitFor()

            val result = output.toString().trim()
            val errors = errorOutput.toString().trim()
            if (result.isNotEmpty()) result else errors
        } catch (e: Exception) {
            executeLocalCommand(command)
        }
    }

    fun executeAdbCommand(command: String): String {
        return try {
            if (_isConnected.value) {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    adbClient.executeCommand(command)
                }
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    
            private var suAvailableCache: Boolean? = null

    fun isSuAvailable(): Boolean {
        if (suAvailableCache != null) return suAvailableCache!!
        suAvailableCache = try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val exitCode = process.waitFor()
            if (exitCode == 0) true else {
                val f1 = java.io.File("/system/bin/su")
                val f2 = java.io.File("/system/xbin/su")
                val f3 = java.io.File("/sbin/su")
                f1.exists() || f2.exists() || f3.exists()
            }
        } catch (e: Exception) {
            false
        }
        return suAvailableCache!!
    }

    fun executeRootOrAdbOrLocalCommand(command: String): String {
        if (_isConnected.value) {
            val adbRes = executeAdbCommand(command)
            if (adbRes.isNotBlank() && !adbRes.contains("Permission denied", ignoreCase = true) && !adbRes.contains("not allowed", ignoreCase = true)) {
                return adbRes
            }
        }
        
        if (isSuAvailable()) {
            val rootRes = executeLocalRootCommand(command)
            if (rootRes.isNotBlank() && !rootRes.contains("not found", ignoreCase = true) && !rootRes.contains("Permission denied", ignoreCase = true)) {
                return rootRes
            }
        }

        val localRes = executeLocalCommand(command)
        return localRes
    }

    suspend fun forceNukeApp(packageName: String, adminReceivers: List<String> = emptyList()): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        
        // 1. Force Stop process
        executeRootOrAdbOrLocalCommand("am force-stop $packageName")
        
        // 2. Kill all overlays and window alerts
        executeRootOrAdbOrLocalCommand("appops set $packageName SYSTEM_ALERT_WINDOW ignore")
        executeRootOrAdbOrLocalCommand("appops set $packageName GET_USAGE_STATS ignore")
        executeRootOrAdbOrLocalCommand("appops set $packageName ACCESS_RESTRICTED_SETTINGS ignore")
        
        // 3. Remove Device Admin permissions
        val knownReceivers = (adminReceivers + listOf(
            "$packageName/.DeviceAdminReceiver",
            "$packageName/.AdminReceiver",
            "$packageName/.receiver.DeviceAdminReceiver",
            "$packageName/.receiver.AdminReceiver",
            "$packageName/.receivers.DeviceAdminReceiver",
            "$packageName/.receivers.AdminReceiver",
            "$packageName/.MyDeviceAdminReceiver",
            "$packageName/.DeviceAdministrator"
        )).distinct()
        
        for (rcv in knownReceivers) {
            val comp = if (rcv.contains("/")) rcv else "$packageName/$rcv"
            executeRootOrAdbOrLocalCommand("dpm remove-active-admin $comp")
        }
        
        // Try to dump device policy to find any dynamic admin
        val dpmDump = executeRootOrAdbOrLocalCommand("dumpsys device_policy")
        val pattern = Regex("$packageName/[a-zA-Z0-9_.]+")
        val matches = pattern.findAll(dpmDump)
        for (m in matches) {
            executeRootOrAdbOrLocalCommand("dpm remove-active-admin ${m.value}")
        }
        
        // 4. Force Stop again
        executeRootOrAdbOrLocalCommand("am force-stop $packageName")
        
        // 5. Try uninstallation commands
        var res = executeRootOrAdbOrLocalCommand("pm uninstall --user 0 $packageName")
        sb.append(res).append("\n")
        if (!res.contains("Success", ignoreCase = true)) {
            res = executeRootOrAdbOrLocalCommand("pm uninstall $packageName")
            sb.append(res).append("\n")
        }
        if (!res.contains("Success", ignoreCase = true)) {
            res = executeRootOrAdbOrLocalCommand("pm uninstall -k --user 0 $packageName")
            sb.append(res).append("\n")
        }
        
        // If uninstall failed due to system lock, force freeze as aggressive fallback
        if (!res.contains("Success", ignoreCase = true)) {
            val freezeRes = executeRootOrAdbOrLocalCommand("pm disable-user --user 0 $packageName || pm disable $packageName || pm hide $packageName")
            sb.append("Fallback Freeze: ").append(freezeRes).append("\n")
        }
        
        sb.toString().trim()
    }

    suspend fun forceFreezeApp(packageName: String, freeze: Boolean, receivers: List<String> = emptyList()): String = withContext(Dispatchers.IO) {
        if (freeze) {
            executeRootOrAdbOrLocalCommand("am force-stop $packageName")
            executeRootOrAdbOrLocalCommand("appops set $packageName SYSTEM_ALERT_WINDOW ignore")
            val res = executeRootOrAdbOrLocalCommand("pm disable-user --user 0 $packageName || pm disable $packageName || pm hide $packageName || pm suspend $packageName")
            res
        } else {
            val sb = StringBuilder()
            val res = executeRootOrAdbOrLocalCommand("pm enable $packageName || pm unhide $packageName || pm unsuspend $packageName || cmd package unsuspended $packageName")
            sb.append(res).append("\n")

            // Remove Android 13/14 Restricted Settings block
            executeRootOrAdbOrLocalCommand("appops set $packageName ACCESS_RESTRICTED_SETTINGS allow")
            executeRootOrAdbOrLocalCommand("cmd appops set $packageName ACCESS_RESTRICTED_SETTINGS allow")

            // Allow Overlay and Usage Stats
            executeRootOrAdbOrLocalCommand("appops set $packageName SYSTEM_ALERT_WINDOW allow")
            executeRootOrAdbOrLocalCommand("appops set $packageName GET_USAGE_STATS allow")

            // Enable all package components
            executeRootOrAdbOrLocalCommand("pm default-state --user 0 $packageName")

            val knownReceivers = (receivers + listOf(
                "$packageName/.DeviceAdminReceiver",
                "$packageName/.AdminReceiver",
                "$packageName/.receiver.DeviceAdminReceiver",
                "$packageName/.receiver.AdminReceiver",
                "$packageName/.receivers.DeviceAdminReceiver",
                "$packageName/.receivers.AdminReceiver",
                "$packageName/.MyDeviceAdminReceiver",
                "$packageName/.DeviceAdministrator"
            )).distinct()

            for (rcv in knownReceivers) {
                val comp = if (rcv.contains("/")) rcv else "$packageName/$rcv"
                executeRootOrAdbOrLocalCommand("pm enable $comp")
            }

            sb.toString().trim()
        }
    }

    suspend fun unlockRestrictedSettingsViaAdb(packageName: String): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.append(executeRootOrAdbOrLocalCommand("appops set $packageName ACCESS_RESTRICTED_SETTINGS allow")).append("\n")
        sb.append(executeRootOrAdbOrLocalCommand("cmd appops set $packageName ACCESS_RESTRICTED_SETTINGS allow")).append("\n")
        sb.append(executeRootOrAdbOrLocalCommand("pm enable $packageName")).append("\n")
        sb.toString().trim()
    }

    suspend fun toggleDeviceAdminViaAdb(packageName: String, enable: Boolean, receivers: List<String> = emptyList()): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        val knownReceivers = (receivers + listOf(
            "$packageName/.DeviceAdminReceiver",
            "$packageName/.AdminReceiver",
            "$packageName/.receiver.DeviceAdminReceiver",
            "$packageName/.receiver.AdminReceiver",
            "$packageName/.receivers.DeviceAdminReceiver",
            "$packageName/.receivers.AdminReceiver",
            "$packageName/.MyDeviceAdminReceiver",
            "$packageName/.DeviceAdministrator"
        )).distinct()

        if (enable) {
            executeRootOrAdbOrLocalCommand("appops set $packageName ACCESS_RESTRICTED_SETTINGS allow")
            executeRootOrAdbOrLocalCommand("cmd appops set $packageName ACCESS_RESTRICTED_SETTINGS allow")
            executeRootOrAdbOrLocalCommand("pm enable $packageName")

            for (rcv in knownReceivers) {
                val comp = if (rcv.contains("/")) rcv else "$packageName/$rcv"
                executeRootOrAdbOrLocalCommand("pm enable $comp")
                val res = executeRootOrAdbOrLocalCommand("dpm set-active-admin --user current $comp || dpm set-active-admin $comp")
                sb.append(res).append("\n")
            }
        } else {
            for (rcv in knownReceivers) {
                val comp = if (rcv.contains("/")) rcv else "$packageName/$rcv"
                val res = executeRootOrAdbOrLocalCommand("dpm remove-active-admin --user current $comp || dpm remove-active-admin $comp")
                sb.append(res).append("\n")
            }
            val dpmDump = executeRootOrAdbOrLocalCommand("dumpsys device_policy")
            val pattern = Regex("$packageName/[a-zA-Z0-9_.]+")
            for (m in pattern.findAll(dpmDump)) {
                executeRootOrAdbOrLocalCommand("dpm remove-active-admin ${m.value}")
            }
        }
        sb.toString().trim()
    }


    private fun updateTerminal(text: String) {
        val current = _terminalOutput.value
        val appendText = text.trim('\n')
        if (appendText.isEmpty()) return

        val newOutput = if (current.isEmpty()) {
            appendText
        } else {
            "$current\n$appendText"
        }
        _terminalOutput.value = boundScrollback(newOutput)

        saveCurrentTabState()
    }

    fun clearTerminal() {
        _terminalOutput.value = ""
        saveCurrentTabState()
    }

    fun saveCustomScript(title: String, command: String, description: String, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val script = ScriptEntity(
                title = title,
                command = command,
                description = description,
                category = category
            )
            adbDao.insertScript(script)
        }
    }

    fun deleteScript(script: ScriptEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            adbDao.deleteScript(script)
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            adbDao.clearHistory()
        }
    }

    fun fetchBasicDeviceInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val infoMap = RealDeviceInfoProvider.buildFullDeviceDiagnostic(getApplication(), _isConnected.value).toMutableMap()
                if (_isConnected.value) {
                    try {
                        val model = adbClient.executeCommand("getprop ro.product.model").trim()
                        if (model.isNotBlank()) infoMap["Qurilma Modeli"] = model
                        val brand = adbClient.executeCommand("getprop ro.product.brand").trim()
                        if (brand.isNotBlank()) infoMap["Brend"] = brand
                        val release = adbClient.executeCommand("getprop ro.build.version.release").trim()
                        if (release.isNotBlank()) infoMap["Android Talqini"] = "Android $release"
                        val wmSize = adbClient.executeCommand("wm size").trim()
                        if (wmSize.isNotBlank()) infoMap["Ekran Rezolyutsiyasi"] = wmSize.replace("Physical size: ", "")
                    } catch (e: Exception) {}
                }
                _deviceInfo.value = infoMap
            } catch (e: Exception) {
                Log.e("AdbVM", "Error fetching device info: ", e)
            }
        }
    }

    fun loadLocalDeviceInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _deviceInfo.value = RealDeviceInfoProvider.buildFullDeviceDiagnostic(getApplication(), false)
            } catch (e: Exception) {
                Log.e("AdbVM", "Error loading local device info: ", e)
            }
        }
    }

    fun resetScriptsToDefault() {
        viewModelScope.launch(Dispatchers.IO) {
            adbDao.clearAllScripts()
            setupPreDefinedScripts()
        }
    }

    private suspend fun setupPreDefinedScripts() {
        val count = adbDao.getScriptCount()
        if (count == 0) {
            val lang = _selectedLanguage.value
            val defaultScripts = listOf(
                ScriptEntity(
                    title = if (lang == "uz") "Batareya diagnostikasi (dumpsys)" else if (lang == "ru") "Диагностика батареи (dumpsys)" else "Battery Diagnostics (dumpsys)",
                    command = "dumpsys battery",
                    description = if (lang == "uz") "Batareya quvvati, harorati va monitoring jurnallarini olish." else "Get deep battery diagnostics.",
                    category = if (lang == "uz") "Tizim" else "System"
                ),
                ScriptEntity(
                    title = if (lang == "uz") "Uchinchi tomon ilovalar ro'yxati (pm list)" else if (lang == "ru") "Список сторонних пакетов (pm list)" else "List Third-Party Apps (pm list)",
                    command = "pm list packages -3",
                    description = if (lang == "uz") "Qurilmadagi barcha tashqi o'rnatilgan ilovalarni ko'rish." else "List all user installed third party apps.",
                    category = if (lang == "uz") "Ilova" else "App"
                ),
                ScriptEntity(
                    title = if (lang == "uz") "Ekran o'lchami va ruxsati (wm size)" else if (lang == "ru") "Показать разрешение экрана (wm size)" else "Get Display Resolution (wm size)",
                    command = "wm size",
                    description = if (lang == "uz") "Qurilma displey o'lchamlarini ko'rish." else "Display screen resolution.",
                    category = if (lang == "uz") "Ekran" else "Screen"
                ),
                ScriptEntity(
                    title = if (lang == "uz") "Xotira bo'limlari hajmi (df -h)" else if (lang == "ru") "Разделы диска (df -h)" else "Disk Space (df -h)",
                    command = "df -h",
                    description = if (lang == "uz") "Xotiradagi bo'sh va band joylarni ko'rish." else "Check filesystem disk space.",
                    category = if (lang == "uz") "Tizim" else "System"
                ),
                ScriptEntity(
                    title = if (lang == "uz") "Qurilma xususiyatlari (getprop)" else if (lang == "ru") "Свойства устройства (getprop)" else "Device Properties (getprop)",
                    command = "getprop ro.product.model",
                    description = if (lang == "uz") "Android apparat va model ma'lumotlarini olish." else "Get device hardware properties.",
                    category = if (lang == "uz") "Tizim" else "System"
                ),
                ScriptEntity(
                    title = if (lang == "uz") "Ekran rasmini olish (screencap)" else if (lang == "ru") "Снимок экрана (screencap)" else "Take Screenshot (screencap)",
                    command = "screencap -p /sdcard/s.png",
                    description = if (lang == "uz") "Ekran tasvirini PNG fayl qilib saqlash." else "Capture screen framebuffer.",
                    category = if (lang == "uz") "Ekran" else "Screen"
                )
            )
            for (script in defaultScripts) {
                adbDao.insertScript(script)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            shellSessionManager.closeAll()
        } catch (_: Exception) {}
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {}
    }
}
