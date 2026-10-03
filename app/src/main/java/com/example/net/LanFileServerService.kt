package com.example.net

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class LanFileServerService : Service() {

    companion object {
        private const val TAG = "LanFileServerService"
        const val CHANNEL_ID = "super_terminal_lan_server_channel"
        const val NOTIFICATION_ID = 21088

        const val ACTION_START = "com.example.net.ACTION_START_LAN_SERVER"
        const val ACTION_STOP = "com.example.net.ACTION_STOP_LAN_SERVER"

        fun startService(context: Context) {
            val intent = Intent(context, LanFileServerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LanFileServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Stop action received in Service")
                LanFileServerManager.getInstance(applicationContext).stopServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                val notification = buildNotification(
                    url = LanFileServerManager.getInstance(applicationContext).serverStats.value.fullUrl,
                    clients = LanFileServerManager.getInstance(applicationContext).serverStats.value.activeClients
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    } else {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    }
                    try {
                        startForeground(NOTIFICATION_ID, notification, fgsType)
                    } catch (e: Exception) {
                        Log.w(TAG, "startForeground with type failed, falling back to standard startForeground", e)
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_STICKY
    }

    fun updateNotification(url: String, clients: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val notification = buildNotification(url, clients)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(url: String, clients: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LanFileServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayUrl = if (url.isNotEmpty()) url else "http://0.0.0.0:8080/"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Super Terminal: LAN File Server")
            .setContentText("● Faol: $displayUrl | Ulanganlar: $clients ta")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("LAN File Server 8080-portda faol ishlamoqda.\nManzil: $displayUrl\nUlangan mijozlar: $clients ta")
            )
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Serverni To'xtatish",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Super Terminal LAN Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "LAN File Server fon holatida ishlash bildirishnomasi"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SuperTerminal:LanServerWakeLock"
            )?.apply {
                acquire(24 * 60 * 60 * 1000L) // max 24 hours
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "SuperTerminal:LanServerWifiLock"
            )?.apply {
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire wake or wifi locks", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null
            if (wifiLock?.isHeld == true) wifiLock?.release()
            wifiLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing locks", e)
        }
    }
}
