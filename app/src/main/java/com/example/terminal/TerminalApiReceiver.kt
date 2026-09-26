package com.example.terminal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Broadcast receiver connecting shell scripts and CLI binaries (terminal-toast, terminal-vibrate, etc.)
 * directly to the app's internal TerminalNativeApi and TerminalPackageManager.
 */
class TerminalApiReceiver : BroadcastReceiver() {

    private val tag = "TerminalApiReceiver"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return

        if (action == "com.bahodirov.super.terminal.CLI_PKG") {
            val cmdArgs = intent.getStringExtra("command") ?: ""
            Log.d(tag, "CLI PKG execution triggered: $cmdArgs")
            scope.launch {
                val pkgManager = TerminalPackageManager(context)
                val parts = cmdArgs.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
                val subCmd = parts.firstOrNull()?.lowercase() ?: ""
                val subArgs = parts.drop(1).joinToString(" ")

                when (subCmd) {
                    "install", "in", "add" -> {
                        for (p in parts.drop(1)) {
                            pkgManager.installPackage(p)
                        }
                    }
                    "update", "up" -> {
                        pkgManager.updateRepositoryIndex()
                    }
                }
            }
            return
        }

        // Forward to TerminalNativeApi
        try {
            val api = TerminalNativeApi(context)
            api.handleApiBroadcast(intent)
        } catch (e: Exception) {
            Log.e(tag, "Error processing API broadcast: ${e.message}")
        }
    }
}
