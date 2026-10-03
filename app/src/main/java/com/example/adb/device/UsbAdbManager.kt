package com.example.adb.device

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

class UsbAdbManager(private val context: Context) {

    companion object {
        private const val TAG = "UsbAdbManager"
        const val ACTION_USB_PERMISSION = "com.example.USB_PERMISSION"

        // ADB USB Class Specification: Class 255 (Vendor), Subclass 66 (0x42), Protocol 1
        private const val ADB_USB_CLASS = 255
        private const val ADB_USB_SUBCLASS = 66
        private const val ADB_USB_PROTOCOL = 1
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    /**
     * Scans currently attached USB devices via Android UsbManager.
     * Identifies potential Android devices connected via OTG cable.
     */
    fun scanAttachedUsbDevices(): List<AdbDevice> {
        val discovered = mutableListOf<AdbDevice>()
        try {
            val deviceList = usbManager.deviceList
            for ((name, device) in deviceList) {
                val isAdb = isAdbCompliant(device)
                val hasPerm = usbManager.hasPermission(device)
                val devName = device.productName?.takeIf { it.isNotBlank() }
                    ?: device.manufacturerName?.let { "$it Device" }
                    ?: "USB Android Device (${device.deviceId})"
                val devModel = device.productName ?: "USB OTG Device"
                val devAddr = "USB (ID: ${device.deviceId}, Vendor: 0x${device.vendorId.toString(16).uppercase()})"

                discovered.add(
                    AdbDevice(
                        id = "usb_${device.deviceId}_${device.vendorId}",
                        name = devName,
                        model = devModel,
                        type = ConnectionType.USB,
                        address = devAddr,
                        status = when {
                            hasPerm && isAdb -> DeviceStatus.CONNECTED
                            !hasPerm -> DeviceStatus.UNAUTHORIZED
                            else -> DeviceStatus.CONNECTED
                        },
                        isSelected = true,
                        androidVersion = "",
                        batteryLevel = "",
                        pingMs = 0L
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "USB scan failed: ${e.message}", e)
        }
        return discovered
    }

    /**
     * Verifies whether the USB device exposes standard ADB bulk transfer interface.
     */
    fun isAdbCompliant(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == ADB_USB_CLASS &&
                iface.interfaceSubclass == ADB_USB_SUBCLASS &&
                iface.interfaceProtocol == ADB_USB_PROTOCOL
            ) {
                return true
            }
        }
        // Also recognize standard Android Vendor IDs (Google, Samsung, Xiaomi, Huawei, etc.)
        val vendorId = device.vendorId
        val commonAndroidVendors = setOf(
            0x18d1, // Google
            0x04e8, // Samsung
            0x2717, // Xiaomi
            0x12d1, // Huawei
            0x0bb4, // HTC
            0x2a70, // OnePlus
            0x22b8, // Motorola
            0x0fce, // Sony
            0x1004  // LG
        )
        return vendorId in commonAndroidVendors
    }

    /**
     * Requests runtime USB permission for the selected device.
     */
    fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    /**
     * Finds ADB endpoint pair (Bulk IN, Bulk OUT).
     */
    fun findAdbEndpoints(usbInterface: UsbInterface): Pair<UsbEndpoint?, UsbEndpoint?> {
        var endpointIn: UsbEndpoint? = null
        var endpointOut: UsbEndpoint? = null
        for (i in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(i)
            if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                    endpointIn = endpoint
                } else {
                    endpointOut = endpoint
                }
            }
        }
        return Pair(endpointIn, endpointOut)
    }

    /**
     * Real USB ADB Connection managing claimed USB interface and Bulk endpoints.
     */
    data class UsbAdbConnection(
        val connection: android.hardware.usb.UsbDeviceConnection,
        val usbInterface: UsbInterface,
        val endpointIn: UsbEndpoint,
        val endpointOut: UsbEndpoint
    ) : AutoCloseable {
        fun send(data: ByteArray, timeoutMs: Int = 3000): Int {
            return connection.bulkTransfer(endpointOut, data, data.size, timeoutMs)
        }

        fun receive(buffer: ByteArray, timeoutMs: Int = 3000): Int {
            return connection.bulkTransfer(endpointIn, buffer, buffer.size, timeoutMs)
        }

        override fun close() {
            try {
                connection.releaseInterface(usbInterface)
                connection.close()
            } catch (e: Exception) {
                Log.e("UsbAdbManager", "Error closing USB connection", e)
            }
        }
    }

    /**
     * Opens real USB hardware communication on the device with claimed bulk endpoints.
     */
    fun openUsbConnection(device: UsbDevice): UsbAdbConnection? {
        if (!usbManager.hasPermission(device)) {
            Log.w(TAG, "USB permission missing for device: ${device.deviceName}")
            return null
        }

        val connection = usbManager.openDevice(device) ?: run {
            Log.e(TAG, "Failed to open UsbDeviceConnection")
            return null
        }

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            val (inEp, outEp) = findAdbEndpoints(iface)
            if (inEp != null && outEp != null) {
                if (connection.claimInterface(iface, true)) {
                    return UsbAdbConnection(connection, iface, inEp, outEp)
                }
            }
        }

        connection.close()
        return null
    }
}
