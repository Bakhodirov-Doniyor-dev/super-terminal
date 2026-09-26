package com.example.terminal

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Persistent local shell session executing a real long-running shell process (/system/bin/sh).
 * Preserves CWD, environment variables, aliases, subshells, and streams stdin/stdout/stderr
 * continuously rather than terminating per-command.
 */
class PtyShellProcess(
    private val context: Context,
    override val id: String = UUID.randomUUID().toString(),
    override val title: String = "Local Shell",
    private val initialDir: File? = null,
    private val useRoot: Boolean = false
) : TerminalSession {

    private val tag = "PtyShellProcess"
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private val _isAlive = AtomicBoolean(false)
    private val sessionScope = CoroutineScope(Dispatchers.IO + Job())

    private val _currentWorkingDir = MutableStateFlow(initialDir?.absolutePath ?: getInitialHomeDir().absolutePath)
    override val currentWorkingDir: StateFlow<String> = _currentWorkingDir.asStateFlow()

    override val isAlive: Boolean
        get() = _isAlive.get() && (process?.isAlive ?: false)

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(String) -> Unit>()

    override fun addOutputListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    override fun removeOutputListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }

    private fun getInitialHomeDir(): File {
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

    override fun start(onOutput: (String) -> Unit) {
        if (_isAlive.get()) return

        try {
            val defaultHome = getInitialHomeDir()
            val startingDir = initialDir ?: defaultHome
            _currentWorkingDir.value = startingDir.absolutePath

            val shellBinary = when {
                useRoot -> "su"
                File("/system/bin/sh").exists() -> "/system/bin/sh"
                else -> "sh"
            }

            val pb = if (useRoot) ProcessBuilder("su") else ProcessBuilder(shellBinary)
            val safeDir = try {
                if (startingDir.exists() && (startingDir.absolutePath.startsWith(context.filesDir.absolutePath) || startingDir.absolutePath.startsWith(context.cacheDir.absolutePath))) {
                    startingDir
                } else {
                    context.filesDir
                }
            } catch (_: Exception) {
                context.filesDir
            }
            pb.directory(safeDir)
            pb.redirectErrorStream(true) // Merge stdout and stderr into unified terminal stream

            val env = pb.environment()
            env["TERM"] = "xterm-256color"
            env["COLORTERM"] = "truecolor"
            env["LANG"] = "en_US.UTF-8"
            val prefixDir = File(context.filesDir, "usr")
            val usrBin = File(prefixDir, "bin").absolutePath
            val usrLib = File(prefixDir, "lib").absolutePath
            val homeDir = startingDir.absolutePath
            env["PREFIX"] = prefixDir.absolutePath
            env["HOME"] = homeDir
            env["LD_LIBRARY_PATH"] = usrLib
            env["PS1"] = "Super@user:~$ "
            val currentPath = env["PATH"] ?: "/system/bin:/system/xbin"
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val prootBin = File(context.filesDir, "bin").absolutePath
            val prootTmp = File(context.cacheDir, "proot_tmp").apply { mkdirs() }.absolutePath
            env["PROOT_TMP_DIR"] = prootTmp
            env["TMPDIR"] = prootTmp
            val kaliDir = File(context.filesDir, "kali")
            val kaliBin = File(kaliDir, "usr/bin").absolutePath
            val kaliRootBin = File(kaliDir, "bin").absolutePath
            env["PATH"] = "$nativeLibDir:$usrBin:$prootBin:$kaliBin:$kaliRootBin:$currentPath:/sbin:/system/sbin:/odm/bin:/vendor/bin"

            val p = try {
                pb.start()
            } catch (e: Exception) {
                try {
                    pb.directory(context.filesDir)
                    pb.start()
                } catch (e2: Exception) {
                    pb.directory(null)
                    pb.start()
                }
            }
            process = p
            val wr = BufferedWriter(OutputStreamWriter(p.outputStream, Charsets.UTF_8))
            writer = wr
            reader = BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8))
            _isAlive.set(true)

            if (startingDir.absolutePath != safeDir.absolutePath) {
                try {
                    wr.write("cd \"${startingDir.absolutePath}\" 2>/dev/null || true\n")
                    wr.flush()
                } catch (_: Exception) {}
            }

            // Background worker reading stdout/stderr continuously
            sessionScope.launch {
                val buf = CharArray(4096)
                var isStartup = true
                try {
                    while (isActive && _isAlive.get()) {
                        val charsRead = reader?.read(buf) ?: -1
                        if (charsRead == -1) break
                        if (charsRead > 0) {
                            var chunk = String(buf, 0, charsRead)
                            if (isStartup) {
                                isStartup = false
                                if (chunk.contains("can't find tty fd") || chunk.contains("won't have full job control") || chunk.contains("files/home $") || chunk.contains("Home $") || chunk.contains("Super@user:~$ ")) {
                                    chunk = chunk.lines().filter { line ->
                                        val trimmed = line.trim()
                                        !trimmed.contains("can't find tty fd") &&
                                        !trimmed.contains("won't have full job control") &&
                                        !trimmed.endsWith("files/home $") &&
                                        !trimmed.endsWith("/sdcard/Home $") &&
                                        !trimmed.endsWith("Home $") &&
                                        !trimmed.endsWith("Super@user:~$ ") &&
                                        !trimmed.endsWith("Super@user:~$") &&
                                        !trimmed.contains("/system/bin/sh:")
                                    }.joinToString("\n").trimStart('\n')
                                }
                            }
                            if (chunk.isNotEmpty()) {
                                onOutput(chunk)
                                listeners.forEach { l ->
                                    try { l(chunk) } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (_isAlive.get()) {
                        Log.d(tag, "Read stream ended: ${e.message}")
                    }
                } finally {
                    _isAlive.set(false)
                    onOutput("\n[Process completed]\n")
                }
            }

        } catch (e: Exception) {
            Log.e(tag, "Failed to start persistent shell", e)
            _isAlive.set(false)
            onOutput("Failed to launch persistent shell: ${e.localizedMessage}\n")
        }
    }

    override fun writeInput(input: String) {
        if (!_isAlive.get() || writer == null) return
        sessionScope.launch {
            try {
                // Check if user changed directory so we can update our reactive CWD state
                val trimmed = input.trim()
                if (trimmed.startsWith("cd ") || trimmed == "cd") {
                    trackDirectoryChange(trimmed)
                }

                writer?.write(input)
                writer?.flush()
            } catch (e: Exception) {
                Log.e(tag, "Failed to write input to shell", e)
            }
        }
    }

    private fun trackDirectoryChange(cdCommand: String) {
        var target = cdCommand.removePrefix("cd").trim()
        // Split on standard shell separators to isolate the target path
        for (sep in listOf("&&", ";", "||", "|", ">", "<")) {
            if (target.contains(sep)) {
                target = target.substringBefore(sep).trim()
            }
        }
        val current = File(_currentWorkingDir.value)
        try {
            val newFile = when {
                target.isEmpty() || target == "~" -> getInitialHomeDir()
                target.startsWith("/") -> File(target)
                target == ".." -> current.parentFile ?: current
                else -> File(current, target)
            }
            val canonical = newFile.canonicalFile
            if (canonical.exists() && canonical.isDirectory) {
                _currentWorkingDir.value = canonical.absolutePath
            }
        } catch (_: Exception) {}
    }

    override fun sendInterrupt() {
        if (!_isAlive.get() || writer == null) return
        sessionScope.launch {
            try {
                writer?.write("\u0003") // ASCII ETX (Ctrl+C)
                writer?.flush()
            } catch (e: Exception) {
                Log.e(tag, "Error sending Ctrl+C", e)
            }
        }
    }

    override fun sendEof() {
        if (!_isAlive.get() || writer == null) return
        sessionScope.launch {
            try {
                writer?.write("\u0004") // ASCII EOT (Ctrl+D)
                writer?.flush()
            } catch (e: Exception) {
                Log.e(tag, "Error sending Ctrl+D", e)
            }
        }
    }

    override fun resize(cols: Int, rows: Int) {
        // Can be forwarded to stty when supported
        sessionScope.launch {
            try {
                writer?.write("stty cols $cols rows $rows 2>/dev/null\n")
                writer?.flush()
            } catch (_: Exception) {}
        }
    }

    override fun close() {
        _isAlive.set(false)
        try {
            writer?.close()
        } catch (_: Exception) {}
        try {
            reader?.close()
        } catch (_: Exception) {}
        try {
            process?.destroy()
        } catch (_: Exception) {}
        process = null
        writer = null
        reader = null
    }
}
