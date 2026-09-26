package com.example.adb

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton holder for the shared AdbClient instance.
 * Ensures that AdbViewModel, AdbKeepAliveService, and AdbDeviceManager
 * interact with the exact same socket connection and stay alive across Android lifecycle events.
 */
object AdbClientHolder {
    @Volatile
    private var instance: AdbClient? = null

    private val _lastHost = MutableStateFlow("127.0.0.1")
    val lastHost: StateFlow<String> = _lastHost.asStateFlow()

    private val _lastPort = MutableStateFlow(5555)
    val lastPort: StateFlow<Int> = _lastPort.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    fun getClient(context: Context): AdbClient {
        return instance ?: synchronized(this) {
            instance ?: AdbClient(context.applicationContext).also { instance = it }
        }
    }

    fun initLastConnectionFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("SuperTerminalPrefs", Context.MODE_PRIVATE)
        val host = prefs.getString("last_adb_host", "127.0.0.1") ?: "127.0.0.1"
        val port = prefs.getInt("last_adb_port", 5555)
        _lastHost.value = host
        _lastPort.value = port
    }

    fun setLastConnection(host: String, port: Int) {
        _lastHost.value = host
        _lastPort.value = port
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }
}
