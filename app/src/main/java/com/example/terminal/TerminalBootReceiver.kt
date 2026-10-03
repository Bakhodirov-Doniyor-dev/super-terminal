package com.example.terminal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Native Boot Completion Receiver (equivalent to Termux:Boot addon, fully built-in).
 * Executes user startup scripts located in:
 *   1. ~/.terminal/boot/
 *   2. ~/.boot/
 *   3. /sdcard/Home/.boot/
 * automatically whenever the device finishes booting or restarting.
 */
class TerminalBootReceiver : BroadcastReceiver() {

    private val tag = "TerminalBootReceiver"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i(tag, "Boot completed event received. Executing startup scripts...")

            scope.launch {
                val candidateBootDirs = listOf(
                    File(context.filesDir, "home/.terminal/boot"),
                    File(context.filesDir, "home/.boot"),
                    File(context.filesDir, "usr/etc/boot"),
                    File("/sdcard/Home/.boot"),
                    File("/sdcard/Home/.terminal/boot")
                )

                for (bootDir in candidateBootDirs) {
                    if (bootDir.exists() && bootDir.isDirectory) {
                        val scriptFiles = bootDir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") } ?: continue
                        for (script in scriptFiles.sortedBy { it.name }) {
                            Log.i(tag, "Launching boot script: ${script.absolutePath}")
                            try {
                                script.setExecutable(true, false)
                                val pb = ProcessBuilder("sh", script.absolutePath)
                                pb.directory(bootDir)

                                val env = pb.environment()
                                val prefixDir = File(context.filesDir, "usr")
                                val usrBin = File(prefixDir, "bin").absolutePath
                                val usrLib = File(prefixDir, "lib").absolutePath
                                env["PREFIX"] = prefixDir.absolutePath
                                env["HOME"] = File(context.filesDir, "home").absolutePath
                                env["LD_LIBRARY_PATH"] = usrLib
                                env["PATH"] = "$usrBin:/system/bin:/system/xbin"

                                val p = pb.start()
                                p.waitFor()
                            } catch (e: Exception) {
                                Log.e(tag, "Boot script ${script.name} error: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
    }
}
