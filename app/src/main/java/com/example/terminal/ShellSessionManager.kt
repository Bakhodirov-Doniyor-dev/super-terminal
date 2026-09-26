package com.example.terminal

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * ShellSessionManager maintains isolated, independent TerminalSessions for each tab.
 * Ensures background execution continues safely per-tab, isolates CWD and process lifecycle,
 * and handles clean resource termination when tabs close.
 */
class ShellSessionManager(private val context: Context) {

    private val tag = "ShellSessionManager"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Map of tabId -> TerminalSession (Isolated PTY / Shell process per tab)
    private val sessions = ConcurrentHashMap<String, TerminalSession>()

    private val _activeTabId = MutableStateFlow<String>("")
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    private val _activeSession = MutableStateFlow<TerminalSession?>(null)
    val activeSession: StateFlow<TerminalSession?> = _activeSession.asStateFlow()

    private val _terminalOutput = MutableStateFlow("")
    val terminalOutput: StateFlow<String> = _terminalOutput.asStateFlow()

    /**
     * Initializes or gets the isolated session for a specific tab.
     */
    fun getOrCreateSession(
        tabId: String,
        initialDir: File? = null,
        useRoot: Boolean = false,
        onOutput: (tabId: String, text: String) -> Unit
    ): TerminalSession {
        val existing = sessions[tabId]
        if (existing != null && existing.isAlive) {
            if (_activeTabId.value == tabId) {
                _activeSession.value = existing
            }
            return existing
        }

        existing?.close()

        val newSession = PtyShellProcess(
            context = context,
            id = tabId,
            title = "Tab-$tabId",
            initialDir = initialDir,
            useRoot = useRoot
        )
        sessions[tabId] = newSession

        if (_activeTabId.value == tabId || _activeTabId.value.isEmpty()) {
            _activeTabId.value = tabId
            _activeSession.value = newSession
        }

        newSession.start { chunk ->
            scope.launch {
                onOutput(tabId, chunk)
                if (_activeTabId.value == tabId) {
                    _terminalOutput.value += chunk
                }
            }
        }

        return newSession
    }

    /**
     * Switches the active session focus to the selected tab.
     */
    fun setActiveTab(tabId: String) {
        _activeTabId.value = tabId
        _activeSession.value = sessions[tabId]
    }

    /**
     * Starts a fallback or legacy local session.
     */
    fun startLocalSession(useRoot: Boolean = false, onReady: (() -> Unit)? = null) {
        val defaultId = _activeTabId.value.ifEmpty { "default_tab" }
        _activeTabId.value = defaultId
        getOrCreateSession(defaultId, null, useRoot) { _, _ -> }
        onReady?.invoke()
    }

    fun setSession(session: TerminalSession) {
        val tabId = session.id.ifEmpty { _activeTabId.value.ifEmpty { "active" } }
        sessions[tabId]?.close()
        sessions[tabId] = session
        _activeTabId.value = tabId
        _activeSession.value = session
        session.start { output ->
            scope.launch {
                _terminalOutput.value += output
            }
        }
    }

    fun write(input: String) {
        val current = _activeSession.value ?: sessions[_activeTabId.value]
        current?.writeInput(input)
    }

    suspend fun writeAndRead(input: String, timeoutMs: Long = 1000): String {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val current = _activeSession.value ?: sessions[_activeTabId.value] ?: return@withContext ""
            var outputResult = ""
            val listener: (String) -> Unit = { chunk ->
                outputResult += chunk
            }
            current.addOutputListener(listener)
            try {
                current.writeInput(input)
                kotlinx.coroutines.delay(timeoutMs)
            } finally {
                current.removeOutputListener(listener)
            }
            outputResult
        }
    }

    fun writeToTab(tabId: String, input: String) {
        sessions[tabId]?.writeInput(input)
    }

    fun sendInterrupt(tabId: String? = null) {
        val target = if (!tabId.isNullOrEmpty()) sessions[tabId] else (_activeSession.value ?: sessions[_activeTabId.value])
        target?.sendInterrupt()
    }

    fun sendEof(tabId: String? = null) {
        val target = if (!tabId.isNullOrEmpty()) sessions[tabId] else (_activeSession.value ?: sessions[_activeTabId.value])
        target?.sendEof()
    }

    fun resize(cols: Int, rows: Int) {
        val current = _activeSession.value ?: sessions[_activeTabId.value]
        current?.resize(cols, rows)
    }

    fun resizeTab(tabId: String, cols: Int, rows: Int) {
        sessions[tabId]?.resize(cols, rows)
    }

    fun isSessionRunning(tabId: String): Boolean {
        return sessions[tabId]?.isAlive == true
    }

    fun clear() {
        _terminalOutput.value = ""
    }

    fun appendOutput(text: String) {
        _terminalOutput.value += text
    }

    /**
     * Cleanly closes and releases all resources and processes for a specific tab.
     */
    fun closeSession(tabId: String) {
        try {
            val session = sessions.remove(tabId)
            session?.close()
            if (_activeTabId.value == tabId) {
                _activeSession.value = null
            }
        } catch (e: Exception) {
            Log.e(tag, "Error terminating tab session $tabId", e)
        }
    }

    /**
     * Closes all active sessions when application shuts down.
     */
    fun closeAll() {
        sessions.forEach { (_, session) ->
            try {
                session.close()
            } catch (_: Exception) {}
        }
        sessions.clear()
        _activeSession.value = null
    }

    fun close() {
        closeAll()
    }
}

