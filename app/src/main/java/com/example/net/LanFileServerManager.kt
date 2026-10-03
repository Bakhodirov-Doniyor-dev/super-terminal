package com.example.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Environment
import android.util.Log
import com.example.adb.device.AdbDevice
import com.example.adb.device.DeviceStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

enum class LanServerState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

data class LanServerConfig(
    val port: Int = 8080,
    val sharedDirectoryPath: String = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
    val isAuthEnabled: Boolean = true,
    val authToken: String = LanFileServer.generateSecureToken(),
    val autoStopOnAdbDisconnect: Boolean = true
)

data class LanServerStats(
    val ipAddress: String = "",
    val port: Int = 8080,
    val fullUrl: String = "",
    val activeClients: Int = 0,
    val bytesDownloaded: Long = 0L,
    val bytesUploaded: Long = 0L,
    val uptimeSeconds: Long = 0L,
    val errorMessage: String? = null
)

class LanFileServerManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "LanFileServerManager"

        @Volatile
        private var INSTANCE: LanFileServerManager? = null

        fun getInstance(context: Context): LanFileServerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LanFileServerManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _serverState = MutableStateFlow(LanServerState.STOPPED)
    val serverState: StateFlow<LanServerState> = _serverState.asStateFlow()

    private val _serverConfig = MutableStateFlow(LanServerConfig())
    val serverConfig: StateFlow<LanServerConfig> = _serverConfig.asStateFlow()

    private val _serverStats = MutableStateFlow(LanServerStats())
    val serverStats: StateFlow<LanServerStats> = _serverStats.asStateFlow()

    private val _detectedLanIps = MutableStateFlow<List<String>>(emptyList())
    val detectedLanIps: StateFlow<List<String>> = _detectedLanIps.asStateFlow()

    private val _recentLogs = MutableStateFlow<List<LanAccessLog>>(emptyList())
    val recentLogs: StateFlow<List<LanAccessLog>> = _recentLogs.asStateFlow()

    private val _connectedAdbDevice = MutableStateFlow<AdbDevice?>(null)
    val connectedAdbDevice: StateFlow<AdbDevice?> = _connectedAdbDevice.asStateFlow()

    private val logQueue = ConcurrentLinkedDeque<LanAccessLog>()
    private var activeServer: LanFileServer? = null
    private var uptimeJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        detectAllLanIps()
        registerNetworkMonitoring()
    }

    fun startServer(customPort: Int? = null) {
        val portToUse = customPort ?: _serverConfig.value.port
        if (portToUse !in 1024..65535) {
            _serverState.value = LanServerState.ERROR
            _serverStats.value = _serverStats.value.copy(
                errorMessage = "Noto'g'ri port: $portToUse. Port 1024 va 65535 oralig'ida bo'lishi kerak."
            )
            return
        }

        _serverState.value = LanServerState.STARTING
        _serverStats.value = _serverStats.value.copy(errorMessage = null)

        scope.launch(Dispatchers.IO) {
            try {
                // 1. IP Detection
                val primaryIp = getPrimaryLanIp()
                if (primaryIp == null) {
                    withContext(Dispatchers.Main) {
                        _serverState.value = LanServerState.ERROR
                        _serverStats.value = _serverStats.value.copy(
                            errorMessage = "Faol LAN yoki Wi-Fi tarmog'i aniqlanmadi. Iltimos Wi-Fi yoki Hotspot tarmog'ini yoqing."
                        )
                    }
                    return@launch
                }

                // 2. Shared directory validation
                val sharedDir = File(_serverConfig.value.sharedDirectoryPath)
                if (!sharedDir.exists()) {
                    sharedDir.mkdirs()
                }

                // 3. Create HTTP Server instance
                val server = LanFileServer(
                    port = portToUse,
                    sharedDirectory = sharedDir,
                    isAuthEnabled = _serverConfig.value.isAuthEnabled,
                    authToken = _serverConfig.value.authToken,
                    onStatsUpdated = { clients, down, up ->
                        scope.launch(Dispatchers.Main) {
                            _serverStats.value = _serverStats.value.copy(
                                activeClients = clients,
                                bytesDownloaded = down,
                                bytesUploaded = up
                            )
                        }
                    },
                    onLog = { log ->
                        addLog(log)
                    }
                )

                // 4. Check if port is bound
                if (!server.checkPortAvailable(portToUse)) {
                    withContext(Dispatchers.Main) {
                        _serverState.value = LanServerState.ERROR
                        _serverStats.value = _serverStats.value.copy(
                            errorMessage = "Port $portToUse allaqachon band. Iltimos sozlamalarda boshqa port tanlang (masalan, 8081 yoki 8888)."
                        )
                    }
                    return@launch
                }

                // 5. Start Server
                server.start()
                activeServer = server

                val tokenParam = if (_serverConfig.value.isAuthEnabled) "?token=${_serverConfig.value.authToken}" else ""
                val fullUrl = "http://$primaryIp:$portToUse/$tokenParam"

                withContext(Dispatchers.Main) {
                    _serverConfig.value = _serverConfig.value.copy(port = portToUse)
                    _serverState.value = LanServerState.RUNNING
                    _serverStats.value = LanServerStats(
                        ipAddress = primaryIp,
                        port = portToUse,
                        fullUrl = "http://$primaryIp:$portToUse/",
                        activeClients = 0,
                        bytesDownloaded = 0L,
                        bytesUploaded = 0L,
                        uptimeSeconds = 0L,
                        errorMessage = null
                    )

                    // Start Foreground Service
                    LanFileServerService.startService(appContext)

                    // Start Uptime ticker
                    startUptimeTicker()

                    addLog(
                        LanAccessLog(
                            clientIp = "127.0.0.1",
                            method = "START",
                            path = "Server port $portToUse da ishga tushirildi",
                            statusCode = 200,
                            bytesTransferred = 0
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server start exception", e)
                withContext(Dispatchers.Main) {
                    _serverState.value = LanServerState.ERROR
                    _serverStats.value = _serverStats.value.copy(
                        errorMessage = e.message ?: "Serverni ishga tushirishda noma'lum xatolik yuz berdi"
                    )
                }
            }
        }
    }

    fun stopServer() {
        uptimeJob?.cancel()
        uptimeJob = null

        scope.launch(Dispatchers.IO) {
            try {
                activeServer?.stop()
                activeServer = null
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping activeServer", e)
            }

            withContext(Dispatchers.Main) {
                _serverState.value = LanServerState.STOPPED
                _serverStats.value = _serverStats.value.copy(
                    activeClients = 0
                )
                LanFileServerService.stopService(appContext)
                addLog(
                    LanAccessLog(
                        clientIp = "127.0.0.1",
                        method = "STOP",
                        path = "Server to'xtatildi",
                        statusCode = 200,
                        bytesTransferred = 0
                    )
                )
            }
        }
    }

    fun setPort(newPort: Int) {
        if (newPort in 1024..65535) {
            _serverConfig.value = _serverConfig.value.copy(port = newPort)
        }
    }

    fun setSharedDirectory(path: String) {
        val dir = File(path)
        if (!dir.exists()) dir.mkdirs()
        _serverConfig.value = _serverConfig.value.copy(sharedDirectoryPath = dir.absolutePath)
        activeServer?.sharedDirectory = dir
    }

    fun setAuthEnabled(enabled: Boolean) {
        _serverConfig.value = _serverConfig.value.copy(isAuthEnabled = enabled)
        activeServer?.isAuthEnabled = enabled
    }

    fun regenerateToken(): String {
        val newToken = LanFileServer.generateSecureToken()
        _serverConfig.value = _serverConfig.value.copy(authToken = newToken)
        activeServer?.authToken = newToken
        return newToken
    }

    fun setAutoStopOnAdbDisconnect(enabled: Boolean) {
        _serverConfig.value = _serverConfig.value.copy(autoStopOnAdbDisconnect = enabled)
    }

    /**
     * Called when an ADB device connects in the app.
     */
    fun onAdbDeviceConnected(device: AdbDevice) {
        _connectedAdbDevice.value = device
        scope.launch(Dispatchers.IO) {
            detectAllLanIps()
        }
    }

    /**
     * Called when an ADB device disconnects.
     */
    fun onAdbDeviceDisconnected(deviceId: String) {
        if (_connectedAdbDevice.value?.id == deviceId) {
            _connectedAdbDevice.value = null
            if (_serverConfig.value.autoStopOnAdbDisconnect && _serverState.value == LanServerState.RUNNING) {
                Log.i(TAG, "Auto-stopping LAN server due to ADB disconnect")
                stopServer()
            }
        }
    }

    fun addLog(log: LanAccessLog) {
        logQueue.addFirst(log)
        while (logQueue.size > 50) {
            logQueue.removeLast()
        }
        _recentLogs.value = logQueue.toList()
    }

    fun getPrimaryLanIp(): String? {
        val ips = detectAllLanIps()
        return ips.firstOrNull()
    }

    fun detectAllLanIps(): List<String> {
        val result = mutableListOf<String>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

            // Priority: wlan, eth, rndis, ap
            val sortedInterfaces = interfaces.sortedWith(compareBy({ iface ->
                val name = iface.name.lowercase(Locale.ROOT)
                when {
                    name.startsWith("wlan") -> 0
                    name.startsWith("eth") -> 1
                    name.startsWith("rndis") -> 2
                    name.startsWith("ap") || name.startsWith("softap") -> 3
                    else -> 4
                }
            }, { it.name }))

            for (iface in sortedInterfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = Collections.list(iface.inetAddresses)
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddr = addr.hostAddress ?: continue
                        // Filter out link-local and loopback
                        if (!hostAddr.startsWith("127.") && !hostAddr.startsWith("169.254.")) {
                            if (!result.contains(hostAddr)) {
                                result.add(hostAddr)
                            }
                        }
                    }
                }
            }

            // Fallback to WifiManager if empty
            if (result.isEmpty()) {
                val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
                if (ipInt != 0) {
                    val ipStr = String.format(
                        Locale.US,
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        ipInt shr 8 and 0xff,
                        ipInt shr 16 and 0xff,
                        ipInt shr 24 and 0xff
                    )
                    if (ipStr != "0.0.0.0" && !ipStr.startsWith("127.")) {
                        result.add(ipStr)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting LAN IPs", e)
        }

        _detectedLanIps.value = result
        return result
    }

    private fun startUptimeTicker() {
        uptimeJob?.cancel()
        val start = System.currentTimeMillis()
        uptimeJob = scope.launch(Dispatchers.Default) {
            while (isActive && _serverState.value == LanServerState.RUNNING) {
                delay(1000)
                val elapsedSec = (System.currentTimeMillis() - start) / 1000
                _serverStats.value = _serverStats.value.copy(uptimeSeconds = elapsedSec)
            }
        }
    }

    private fun registerNetworkMonitoring() {
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch(Dispatchers.IO) {
                        delay(500)
                        detectAllLanIps()
                    }
                }

                override fun onLost(network: Network) {
                    scope.launch(Dispatchers.IO) {
                        delay(500)
                        detectAllLanIps()
                    }
                }
            }
            cm.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register network callback", e)
        }
    }
}
