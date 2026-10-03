package com.example.terminal

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.viewmodel.AdbViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Universal Command Execution Service (equivalent to Termux:Tasker / RunCommandService).
 * Allows external applications (Tasker, Automate, MacroDroid, third-party apps, ADB)
 * to securely execute shell scripts, terminal commands, and Linux binaries in background or foreground.
 */
class TerminalRunCommandService : Service() {

    private val tag = "TerminalRunCommand"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ACTION_RUN_COMMAND = "com.bahodirov.super.terminal.RUN_COMMAND"
        const val EXTRA_COMMAND = "com.bahodirov.super.terminal.RUN_COMMAND_COMMAND"
        const val EXTRA_ARGUMENTS = "com.bahodirov.super.terminal.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_WORKDIR = "com.bahodirov.super.terminal.RUN_COMMAND_WORKDIR"
        const val EXTRA_BACKGROUND = "com.bahodirov.super.terminal.RUN_COMMAND_BACKGROUND"
        const val EXTRA_SESSION_ACTION = "com.bahodirov.super.terminal.RUN_COMMAND_SESSION_ACTION"
        const val EXTRA_RESULT_PENDING_INTENT = "com.bahodirov.super.terminal.RUN_COMMAND_PENDING_INTENT"

        // Result extras
        const val EXTRA_RESULT_STDOUT = "stdout"
        const val EXTRA_RESULT_STDERR = "stderr"
        const val EXTRA_RESULT_EXIT_CODE = "exitCode"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action != ACTION_RUN_COMMAND) {
            return START_NOT_STICKY
        }

        val rawCommand = intent.getStringExtra(EXTRA_COMMAND)
            ?: intent.getStringExtra("command")
            ?: intent.getStringExtra("cmd")
            ?: ""

        if (rawCommand.isBlank()) {
            Log.w(tag, "Received empty command intent.")
            return START_NOT_STICKY
        }

        val arguments = intent.getStringArrayExtra(EXTRA_ARGUMENTS) ?: emptyArray()
        val workDir = intent.getStringExtra(EXTRA_WORKDIR) ?: File(applicationContext.filesDir, "usr").absolutePath
        val inBackground = intent.getBooleanExtra(EXTRA_BACKGROUND, true)
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_PENDING_INTENT, android.app.PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_PENDING_INTENT)
        }

        serviceScope.launch {
            try {
                val fullCmd = if (arguments.isNotEmpty()) {
                    "$rawCommand ${arguments.joinToString(" ")}"
                } else {
                    rawCommand
                }

                Log.d(tag, "Executing external command: $fullCmd (workdir: $workDir, bg: $inBackground)")

                val pb = ProcessBuilder("sh", "-c", fullCmd)
                val dirFile = File(workDir)
                if (dirFile.exists() && dirFile.isDirectory) {
                    pb.directory(dirFile)
                }

                val env = pb.environment()
                val prefixDir = File(applicationContext.filesDir, "usr")
                val usrBin = File(prefixDir, "bin").absolutePath
                val usrLib = File(prefixDir, "lib").absolutePath
                env["PREFIX"] = prefixDir.absolutePath
                env["HOME"] = File(applicationContext.filesDir, "home").apply { mkdirs() }.absolutePath
                env["LD_LIBRARY_PATH"] = usrLib
                env["TERM"] = "xterm-256color"
                val currentPath = env["PATH"] ?: "/system/bin:/system/xbin"
                env["PATH"] = "$usrBin:$currentPath:/sbin:/system/sbin"

                val proc = pb.start()
                val stdout = proc.inputStream.bufferedReader().use { it.readText() }
                val stderr = proc.errorStream.bufferedReader().use { it.readText() }
                val exitCode = proc.waitFor()

                // If caller requested result callback via PendingIntent
                if (pendingIntent != null) {
                    val resultIntent = Intent().apply {
                        putExtra(EXTRA_RESULT_STDOUT, stdout)
                        putExtra(EXTRA_RESULT_STDERR, stderr)
                        putExtra(EXTRA_RESULT_EXIT_CODE, exitCode)
                    }
                    try {
                        pendingIntent.send(applicationContext, 0, resultIntent)
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to send pending intent result: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Command execution failed: ${e.message}", e)
            }
        }

        return START_NOT_STICKY
    }
}
