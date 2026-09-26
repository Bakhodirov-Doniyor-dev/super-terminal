package com.example.adb.device

import java.util.UUID

enum class ConnectionType(val title: String) {
    USB("USB"),
    WIRELESS_ADB("Wireless ADB"),
    TCPIP("TCP/IP")
}

enum class DeviceStatus(val label: String) {
    CONNECTED("Connected"),
    CONNECTING("Connecting"),
    DISCONNECTED("Disconnected"),
    UNAUTHORIZED("Unauthorized"),
    ERROR("Error")
}

data class AdbDevice(
    val id: String,
    val name: String,
    val model: String,
    val type: ConnectionType,
    val address: String,
    val status: DeviceStatus = DeviceStatus.CONNECTED,
    val isSelected: Boolean = true,
    val androidVersion: String = "",
    val batteryLevel: String = "",
    val pingMs: Long = 0L,
    val isLocalDevice: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)

data class MultiCommandResult(
    val id: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val deviceName: String,
    val deviceModel: String,
    val connectionType: ConnectionType,
    val address: String,
    val command: String,
    val output: String,
    val exitCode: Int = 0,
    val executionTimeMs: Long,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
