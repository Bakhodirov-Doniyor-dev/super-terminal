package com.example.adb.device

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.adb.AdbClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

class AdbDeviceManager(
    private val context: Context,
    private val primaryClient: AdbClient,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AdbDeviceManager"
    }

    private val usbAdbManager = UsbAdbManager(context)
    private val mutex = Mutex()

    // Default registered devices matching the user's scenario
    private val initialDevices = emptyList<AdbDevice>()

    private val _devices = MutableStateFlow(initialDevices)
    val devices: StateFlow<List<AdbDevice>> = _devices.asStateFlow()

    private val _activeDeviceId = MutableStateFlow("dev_local")
    val activeDeviceId: StateFlow<String> = _activeDeviceId.asStateFlow()

    private val _isMultiDeviceMode = MutableStateFlow(false)
    val isMultiDeviceMode: StateFlow<Boolean> = _isMultiDeviceMode.asStateFlow()

    private val _isExecutingMulti = MutableStateFlow(false)
    val isExecutingMulti: StateFlow<Boolean> = _isExecutingMulti.asStateFlow()

    private val _multiCommandResults = MutableStateFlow<List<MultiCommandResult>>(emptyList())
    val multiCommandResults: StateFlow<List<MultiCommandResult>> = _multiCommandResults.asStateFlow()

    private val _lastBroadcastCommand = MutableStateFlow("getprop ro.build.version.release")
    val lastBroadcastCommand: StateFlow<String> = _lastBroadcastCommand.asStateFlow()

    fun setMultiDeviceMode(enabled: Boolean) {
        _isMultiDeviceMode.value = enabled
    }

    fun setActiveDevice(deviceId: String) {
        _activeDeviceId.value = deviceId
    }

    fun toggleDeviceSelection(deviceId: String) {
        _devices.value = _devices.value.map { dev ->
            if (dev.id == deviceId) dev.copy(isSelected = !dev.isSelected) else dev
        }
    }

    fun selectAllDevices() {
        _devices.value = _devices.value.map { it.copy(isSelected = true) }
    }

    fun deselectAllDevices() {
        _devices.value = _devices.value.map { it.copy(isSelected = false) }
    }

    fun removeDevice(deviceId: String) {
        _devices.value = _devices.value.filter { it.id != deviceId }
        if (_activeDeviceId.value == deviceId) {
            _activeDeviceId.value = "dev_local"
        }
    }

    fun updateDeviceStatus(deviceId: String, status: DeviceStatus) {
        _devices.value = _devices.value.map { dev ->
            if (dev.id == deviceId) dev.copy(status = status) else dev
        }
    }

    /**
     * Adds a newly discovered or manually configured device.
     */
    fun addDevice(
        name: String,
        model: String,
        type: ConnectionType,
        address: String,
        androidVersion: String = ""
    ) {
        val newDev = AdbDevice(
            id = "dev_${System.currentTimeMillis()}",
            name = name,
            model = model,
            type = type,
            address = address,
            status = DeviceStatus.CONNECTED,
            isSelected = true,
            androidVersion = androidVersion,
            batteryLevel = "",
            pingMs = 0L
        )
        _devices.value = _devices.value + newDev
    }

    /**
     * Scans real USB devices via Android UsbManager and updates registry.
     */
    fun scanUsbDevices() {
        scope.launch(Dispatchers.IO) {
            val usbDevices = usbAdbManager.scanAttachedUsbDevices()
            if (usbDevices.isNotEmpty()) {
                val currentIds = _devices.value.map { it.id }.toSet()
                val newDevices = usbDevices.filter { it.id !in currentIds }
                if (newDevices.isNotEmpty()) {
                    _devices.value = _devices.value + newDevices
                }
            }
        }
    }

    /**
     * Executes a command across all selected devices concurrently using Kotlin Coroutines async/awaitAll.
     */
    suspend fun executeOnSelectedDevices(command: String): List<MultiCommandResult> = withContext(Dispatchers.IO) {
        val selected = _devices.value.filter { it.isSelected && it.status == DeviceStatus.CONNECTED }
        if (selected.isEmpty()) {
            return@withContext emptyList()
        }

        _lastBroadcastCommand.value = command
        _isExecutingMulti.value = true

        val results = coroutineScope {
            selected.map { device ->
                async {
                    executeOnSingleDevice(device, command)
                }
            }.awaitAll()
        }

        _multiCommandResults.value = results
        _isExecutingMulti.value = false
        results
    }

    private val deviceClients = java.util.concurrent.ConcurrentHashMap<String, AdbClient>()

    fun getClientForDevice(device: AdbDevice): AdbClient {
        if (device.isLocalDevice) return primaryClient
        return deviceClients.computeIfAbsent(device.id) {
            AdbClient(context).apply {
                loadOrGenerateKeys()
            }
        }
    }

    /**
     * Executes command on an individual device, measuring response time.
     */
    private suspend fun executeOnSingleDevice(device: AdbDevice, command: String): MultiCommandResult {
        val startNs = SystemClock.elapsedRealtimeNanos()
        var output: String
        var isSuccess = true
        var exitCode = 0
        var errorMsg: String? = null

        try {
            val client = getClientForDevice(device)
            if (!client.isConnected()) {
                // If it has host:port address, attempt connection
                val addr = device.address
                if (addr.contains(":")) {
                    val host = addr.substringBefore(":").trim()
                    val port = addr.substringAfter(":").trim().toIntOrNull() ?: 5555
                    val status = client.connect(port = port, targetHost = host)
                    if (!client.isConnected()) {
                        throw IOException("Failed to connect to device at $host:$port: $status")
                    }
                } else if (device.isLocalDevice) {
                    throw IOException("Local ADB client is not connected")
                } else {
                    throw IOException("Device $addr is not currently connected")
                }
            }

            output = client.executeCommand(command)
            if (output.startsWith("Error:") || output.startsWith("Xato:")) {
                isSuccess = false
                exitCode = 1
                errorMsg = output
            }
        } catch (e: Exception) {
            isSuccess = false
            exitCode = 1
            errorMsg = e.localizedMessage ?: "Unknown error"
            output = "Error: ${e.localizedMessage ?: e.message}"
        }

        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000

        return MultiCommandResult(
            deviceId = device.id,
            deviceName = device.name,
            deviceModel = device.model,
            connectionType = device.type,
            address = device.address,
            command = command,
            output = output.trim(),
            exitCode = exitCode,
            executionTimeMs = if (elapsedMs < 1) 1L else elapsedMs,
            isSuccess = isSuccess,
            errorMessage = errorMsg
        )
    }
}
