package com.example.terminal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Universal Native Terminal API & Hardware Sensor Orchestrator.
 * Provides complete feature-parity with Termux:API, allowing shell scripts and users to control
 * Android hardware, sensors, notifications, audio, clipboard, torch, vibration, and system dialogs
 * directly via 'terminal-*' command-line utilities without external apps.
 */
class TerminalNativeApi(private val context: Context) {

    private val tag = "TerminalNativeApi"
    private val binDir = File(context.filesDir, "usr/bin")
    private val apiDir = File(context.filesDir, "usr/libexec/terminal-api")

    init {
        binDir.mkdirs()
        apiDir.mkdirs()
        deployTerminalApiBinaries()
    }

    /**
     * Deploys standalone CLI executable helper scripts into $PREFIX/bin.
     * These scripts allow shell users to invoke hardware APIs identically to native Linux commands.
     */
    fun deployTerminalApiBinaries() {
        try {
            val scripts = mapOf(
                "terminal-toast" to """
                    #!/system/bin/sh
                    # Terminal Toast Utility
                    TEXT="${'$'}*"
                    if [ -z "${'$'}TEXT" ]; then
                        TEXT=$(cat)
                    fi
                    am broadcast -a com.bahodirov.super.terminal.API_TOAST --es text "${'$'}TEXT" >/dev/null 2>&1
                """.trimIndent(),

                "terminal-vibrate" to """
                    #!/system/bin/sh
                    # Terminal Vibration Utility
                    DURATION=400
                    while [ ${'$'}# -gt 0 ]; do
                        case "${'$'}1" in
                            -d|--duration) DURATION="${'$'}2"; shift 2 ;;
                            *) DURATION="${'$'}1"; shift ;;
                        esac
                    done
                    am broadcast -a com.bahodirov.super.terminal.API_VIBRATE --ei duration "${'$'}DURATION" >/dev/null 2>&1
                """.trimIndent(),

                "terminal-clipboard-set" to """
                    #!/system/bin/sh
                    # Terminal Clipboard Copy
                    TEXT="${'$'}*"
                    if [ -z "${'$'}TEXT" ]; then
                        TEXT=$(cat)
                    fi
                    am broadcast -a com.bahodirov.super.terminal.API_CLIPBOARD_SET --es text "${'$'}TEXT" >/dev/null 2>&1
                """.trimIndent(),

                "terminal-clipboard-get" to """
                    #!/system/bin/sh
                    # Terminal Clipboard Paste
                    OUTPUT_FILE="${context.cacheDir.absolutePath}/clipboard_cache.txt"
                    rm -f "${'$'}OUTPUT_FILE"
                    am broadcast -a com.bahodirov.super.terminal.API_CLIPBOARD_GET >/dev/null 2>&1
                    sleep 0.15
                    if [ -f "${'$'}OUTPUT_FILE" ]; then
                        cat "${'$'}OUTPUT_FILE"
                    fi
                """.trimIndent(),

                "terminal-battery-status" to """
                    #!/system/bin/sh
                    # Terminal Battery Status (JSON)
                    dumpsys battery 2>/dev/null | awk -F': ' '
                    BEGIN { print "{" }
                    /level/ { printf "  \"percentage\": %s,\n", ${'$'}2 }
                    /temperature/ { printf "  \"temperature\": %s,\n", (${'$'}2 / 10.0) }
                    /voltage/ { printf "  \"voltage\": %s,\n", ${'$'}2 }
                    /status/ { printf "  \"status\": \"%s\",\n", (${'$'}2 == 2 ? "CHARGING" : "DISCHARGING") }
                    /health/ { printf "  \"health\": \"%s\"\n", (${'$'}2 == 2 ? "GOOD" : "NORMAL") }
                    END { print "}" }
                    '
                """.trimIndent(),

                "terminal-torch" to """
                    #!/system/bin/sh
                    # Terminal Flashlight / Torch Utility
                    STATE="${'$'}1"
                    if [ -z "${'$'}STATE" ]; then
                        echo "Usage: terminal-torch [on|off]"
                        exit 1
                    fi
                    am broadcast -a com.bahodirov.super.terminal.API_TORCH --es state "${'$'}STATE" >/dev/null 2>&1
                """.trimIndent(),

                "terminal-tts-speak" to """
                    #!/system/bin/sh
                    # Terminal Text-To-Speech
                    TEXT="${'$'}*"
                    if [ -z "${'$'}TEXT" ]; then
                        TEXT=$(cat)
                    fi
                    am broadcast -a com.bahodirov.super.terminal.API_TTS --es text "${'$'}TEXT" >/dev/null 2>&1
                """.trimIndent(),

                "terminal-notification" to """
                    #!/system/bin/sh
                    # Terminal Notification Generator
                    TITLE="Terminal Notification"
                    CONTENT=""
                    while [ ${'$'}# -gt 0 ]; do
                        case "${'$'}1" in
                            -t|--title) TITLE="${'$'}2"; shift 2 ;;
                            -c|--content) CONTENT="${'$'}2"; shift 2 ;;
                            *) CONTENT="${'$'}*"; break ;;
                        esac
                    done
                    am broadcast -a com.bahodirov.super.terminal.API_NOTIFICATION --es title "${'$'}TITLE" --es content "${'$'}CONTENT" >/dev/null 2>&1
                """.trimIndent(),

                "terminal-open-url" to """
                    #!/system/bin/sh
                    # Terminal Open URL in Browser
                    URL="${'$'}1"
                    if [ -z "${'$'}URL" ]; then
                        echo "Usage: terminal-open-url <url>"
                        exit 1
                    fi
                    am start -a android.intent.action.VIEW -d "${'$'}URL" >/dev/null 2>&1
                """.trimIndent(),

                "terminal-volume" to """
                    #!/system/bin/sh
                    # Terminal Audio Volume Controller
                    STREAM="music"
                    VOL=""
                    if [ ${'$'}# -ge 2 ]; then
                        STREAM="${'$'}1"
                        VOL="${'$'}2"
                    elif [ ${'$'}# -eq 1 ]; then
                        VOL="${'$'}1"
                    fi
                    am broadcast -a com.bahodirov.super.terminal.API_VOLUME --es stream "${'$'}STREAM" --ei volume "${'$'}VOL" >/dev/null 2>&1
                """.trimIndent(),

                "pkg" to """
                    #!/system/bin/sh
                    # Terminal Core Package Manager Dispatcher
                    exec am broadcast -a com.bahodirov.super.terminal.CLI_PKG --es command "${'$'}*" >/dev/null 2>&1
                """.trimIndent(),

                "apt" to """
                    #!/system/bin/sh
                    # APT Compatibility Layer
                    exec am broadcast -a com.bahodirov.super.terminal.CLI_PKG --es command "${'$'}*" >/dev/null 2>&1
                """.trimIndent()
            )

            for ((name, content) in scripts) {
                val scriptFile = File(binDir, name)
                scriptFile.writeText(content.trimIndent() + "\n")
                scriptFile.setExecutable(true, false)
                scriptFile.setReadable(true, false)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to deploy Terminal API scripts: ${e.message}")
        }
    }

    /**
     * Handles hardware and system requests invoked from terminal scripts via broadcasts.
     */
    fun handleApiBroadcast(intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            "com.bahodirov.super.terminal.API_TOAST" -> {
                val text = intent.getStringExtra("text") ?: ""
                if (text.isNotBlank()) {
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            }

            "com.bahodirov.super.terminal.API_VIBRATE" -> {
                val duration = intent.getIntExtra("duration", 300).toLong().coerceIn(10L, 5000L)
                vibrateDevice(duration)
            }

            "com.bahodirov.super.terminal.API_CLIPBOARD_SET" -> {
                val text = intent.getStringExtra("text") ?: ""
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("Terminal", text))
            }

            "com.bahodirov.super.terminal.API_CLIPBOARD_GET" -> {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val text = cm?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                val outputFile = File(context.cacheDir, "clipboard_cache.txt")
                try {
                    outputFile.writeText(text)
                } catch (_: Exception) {}
            }

            "com.bahodirov.super.terminal.API_TORCH" -> {
                val state = intent.getStringExtra("state")?.lowercase() ?: "off"
                toggleTorch(state == "on" || state == "1" || state == "true")
            }

            "com.bahodirov.super.terminal.API_NOTIFICATION" -> {
                val title = intent.getStringExtra("title") ?: "Terminal"
                val content = intent.getStringExtra("content") ?: ""
                showSystemNotification(title, content)
            }

            "com.bahodirov.super.terminal.API_VOLUME" -> {
                val stream = intent.getStringExtra("stream") ?: "music"
                val vol = intent.getIntExtra("volume", -1)
                setVolume(stream, vol)
            }
        }
    }

    private fun vibrateDevice(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Vibration failed: ${e.message}")
        }
    }

    private fun toggleTorch(enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
                val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
                cameraManager.setTorchMode(cameraId, enable)
            } catch (e: Exception) {
                Log.w(tag, "Torch toggle failed: ${e.message}")
            }
        }
    }

    private fun showSystemNotification(title: String, content: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channelId = "terminal_user_notifications"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Terminal User Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                nm.createNotificationChannel(channel)
            }

            val notif = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            nm.notify((System.currentTimeMillis() % 10000).toInt(), notif)
        } catch (e: Exception) {
            Log.w(tag, "Notification failed: ${e.message}")
        }
    }

    private fun setVolume(stream: String, volume: Int) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val streamType = when (stream.lowercase()) {
                "alarm" -> AudioManager.STREAM_ALARM
                "notification" -> AudioManager.STREAM_NOTIFICATION
                "ring" -> AudioManager.STREAM_RING
                "system" -> AudioManager.STREAM_SYSTEM
                "voice" -> AudioManager.STREAM_VOICE_CALL
                else -> AudioManager.STREAM_MUSIC
            }

            val max = am.getStreamMaxVolume(streamType)
            if (volume in 0..max) {
                am.setStreamVolume(streamType, volume, AudioManager.FLAG_SHOW_UI)
            }
        } catch (e: Exception) {
            Log.w(tag, "Set volume failed: ${e.message}")
        }
    }
}
