package com.example.security

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.example.adb.AdbClientHolder
import kotlinx.coroutines.*

/**
 * Real-time event-driven accessibility service that catches and neutralizes
 * screen-blocking overlay hijacks safely with zero crash risk.
 * ALSO acts as the 24/7 Silent Background Daemon for ADB (Zero Foreground Service / No Stop Button).
 */
class AntiOverlayAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sentinelManager: AntiOverlaySentinelManager? = null
    private var lastEventCheckTime = 0L
    private var lastHeartbeatTime = 0L


    private fun getSentinel(): AntiOverlaySentinelManager? {
        if (sentinelManager == null) {
            try {
                sentinelManager = AntiOverlaySentinelManager.getInstance(applicationContext)
            } catch (e: Exception) {
                Log.w("AntiOverlayAccService", "Could not get sentinel instance: ${e.message}")
            }
        }
        return sentinelManager
    }

    private fun acquireLocks() {
        // Removed WakeLock and WifiLock completely to hide the "Stop" button in Android 14 Task Manager
    }

    private fun releaseLocks() {
        // Removed
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            getSentinel()

            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                        AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                notificationTimeout = 100
            }
            this.serviceInfo = info
            isAccessibilityServiceRunning = true

            acquireLocks()

            // Restore last known connection
            AdbClientHolder.initLastConnectionFromPrefs(applicationContext)

            // [SENIOR ARCHITECTURE]: Removed infinite while(isActive) loop.
            // One UI flags any infinite loops and places the app in "Active Apps" with a "Stop" button.
            // ADB auto-reconnection is now handled ON-DEMAND inside neutralizePackage().
        } catch (e: Exception) {
            Log.e("AntiOverlayAccService", "Error in onServiceConnected: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            // [SENIOR ARCHITECTURE]: Zero-Overhead Lazy Heartbeat!
            // Instead of an infinite while(true) loop that triggers Samsung Knox,
            // we piggyback on natural OS accessibility events to ping the ADB socket.
            val nowTime = System.currentTimeMillis()
            if (nowTime - lastHeartbeatTime > 45000) { // Every 45 seconds max
                lastHeartbeatTime = nowTime
                serviceScope.launch {
                    val client = com.example.adb.AdbClientHolder.getClient(applicationContext)
                    val host = com.example.adb.AdbClientHolder.lastHost.value
                    val port = com.example.adb.AdbClientHolder.lastPort.value
                    if (!client.isConnected() && port > 0 && host.isNotBlank()) {
                        try { client.connect(port, host) } catch (_: Exception) {}
                    } else if (client.isConnected()) {
                        try { client.executeCommand("echo ping") } catch (_: Exception) {}
                    }
                }
            }

            val sentinel = getSentinel() ?: return
            if (event == null || !sentinel.isGuardEnabled.value) return

            val pkgName = event.packageName?.toString() ?: return
            if (pkgName.isBlank() || sentinel.isWhitelisted(pkgName)) return

            // Throttle rapid events to prevent UI thread lag or ANR
            val now = System.currentTimeMillis()
            if (now - lastEventCheckTime < 400) return
            lastEventCheckTime = now

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val activeWindows = try {
                    windows
                } catch (_: Exception) {
                    null
                }
                if (activeWindows.isNullOrEmpty()) return

                val wm = getSystemService(WINDOW_SERVICE) as? WindowManager
                val displayMetrics = DisplayMetrics()
                try {
                    wm?.defaultDisplay?.getRealMetrics(displayMetrics)
                } catch (_: Exception) {}

                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels

                if (screenWidth <= 0 || screenHeight <= 0) return

                for (window in activeWindows) {
                    try {
                        val rect = Rect()
                        window.getBoundsInScreen(rect)
                        val windowPkg = try {
                            window.root?.packageName?.toString()
                        } catch (_: Exception) {
                            null
                        } ?: pkgName

                        if (sentinel.isWhitelisted(windowPkg)) continue

                        val windowType = window.type
                        val isOverlay = windowType == AccessibilityWindowInfo.TYPE_SYSTEM ||
                                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && windowType == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY)

                        val coversScreen = (rect.width() >= (screenWidth * 0.85f)) && (rect.height() >= (screenHeight * 0.85f))

                        if (isOverlay && coversScreen) {
                            serviceScope.launch {
                                try {
                                    sentinel.neutralizePackage(
                                        packageName = windowPkg,
                                        reason = "Accessibility Service: Full-Screen Overlay Hijack Detected"
                                    )
                                } catch (_: Exception) {}
                            }
                            break
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Throwable) {
            // Guarantee no crash can propagate to the system
        }
    }

    override fun onInterrupt() {
        try {
            isAccessibilityServiceRunning = false
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            isAccessibilityServiceRunning = false
            serviceScope.cancel()
            releaseLocks()
        } catch (_: Exception) {}
    }

    companion object {
        @Volatile
        var isAccessibilityServiceRunning: Boolean = false
            private set
    }
}
