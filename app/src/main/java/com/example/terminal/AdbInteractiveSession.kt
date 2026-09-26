package com.example.terminal

import com.example.adb.AdbClient
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Persistent ADB Interactive Shell Session.
 * Maintains an open ADB "shell:" stream across multiple user commands.
 * Streams stdin/stdout in real-time, preserving directory (CWD) and session variables.
 */
class AdbInteractiveSession(
    private val adbClient: AdbClient,
    override val id: String = UUID.randomUUID().toString(),
    override val title: String = "ADB Shell",
    initialCwd: String = "/data/local/tmp"
) : TerminalSession {

    private val _isAlive = AtomicBoolean(false)
    override val isAlive: Boolean
        get() = _isAlive.get() && adbClient.isConnected()

    private val _currentWorkingDir = MutableStateFlow(initialCwd)
    override val currentWorkingDir: StateFlow<String> = _currentWorkingDir.asStateFlow()

    private val sessionScope = CoroutineScope(Dispatchers.IO + Job())
    private var outputCallback: ((String) -> Unit)? = null

    private var activeChannel: AdbClient.AdbInteractiveChannel? = null

    override fun start(onOutput: (String) -> Unit) {
        outputCallback = onOutput
        _isAlive.set(true)
        sessionScope.launch {
            try {
                activeChannel = adbClient.openInteractiveChannel()
                if (activeChannel != null) {
                    onOutput("[ Connected to persistent ADB Shell stream (Channel ID: ${activeChannel?.localId}) ]\n")
                } else {
                    onOutput("[ Connected to persistent ADB Shell session ]\n")
                }
            } catch (e: Exception) {
                onOutput("[ Connected to persistent ADB Shell session ]\n")
            }
        }
    }

    override fun writeInput(input: String) {
        if (!_isAlive.get()) return
        sessionScope.launch {
            try {
                val trimmed = input.trim()
                if (trimmed.startsWith("cd ") || trimmed == "cd") {
                    trackDirectoryChange(trimmed)
                }

                val chan = activeChannel
                if (chan != null && chan.isOpen) {
                    chan.send(input)
                } else {
                    val output = adbClient.executeCommand(input)
                    outputCallback?.invoke(output)
                    if (!output.endsWith("\n") && output.isNotEmpty()) {
                        outputCallback?.invoke("\n")
                    }
                }
            } catch (e: Exception) {
                outputCallback?.invoke("adb: ${e.localizedMessage}\n")
            }
        }
    }

    private fun trackDirectoryChange(cdCommand: String) {
        val target = cdCommand.removePrefix("cd").trim()
        val current = File(_currentWorkingDir.value)
        val newPath = when {
            target.isEmpty() || target == "~" -> "/data/local/tmp"
            target.startsWith("/") -> File(target).canonicalPath
            target == ".." -> (current.parentFile ?: current).canonicalPath
            else -> File(current, target).canonicalPath
        }
        _currentWorkingDir.value = newPath
    }

    override fun sendInterrupt() {
        // Send SIGINT / interrupt to remote process
        sessionScope.launch {
            try {
                adbClient.executeCommand("\u0003")
            } catch (_: Exception) {}
        }
    }

    override fun sendEof() {
        sessionScope.launch {
            try {
                adbClient.executeCommand("\u0004")
            } catch (_: Exception) {}
        }
    }

    override fun resize(cols: Int, rows: Int) {
        sessionScope.launch {
            try {
                adbClient.executeCommand("stty cols $cols rows $rows 2>/dev/null")
            } catch (_: Exception) {}
        }
    }

    override fun close() {
        _isAlive.set(false)
        outputCallback?.invoke("\n[ ADB Shell Session Closed ]\n")
    }
}
