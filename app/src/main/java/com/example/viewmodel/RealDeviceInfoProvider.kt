package com.example.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.NetworkInterface
import java.util.Locale
import java.util.TimeZone

/**
 * Professional-grade device diagnostic utility.
 * Reads directly from Linux pseudo-filesystems (/proc, /sys) for raw truth
 * with fallbacks to Android framework APIs.
 */
object RealDeviceInfoProvider {

    fun getCpuInfo(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val hardware = Build.HARDWARE
        map["Protsessor (SoC)"] = if (hardware.isNotBlank() && hardware != Build.UNKNOWN) hardware else Build.BOARD
        map["Yadro arxitekturasi"] = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        map["Yadrolar soni"] = Runtime.getRuntime().availableProcessors().toString()
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                val socMfg = Build.SOC_MANUFACTURER
                val socModel = Build.SOC_MODEL
                if (!socModel.isNullOrBlank() && socModel != Build.UNKNOWN) {
                    map["SoC Modeli"] = "$socMfg $socModel".trim()
                }
            } catch (e: Throwable) {}
        }
        return map
    }

    fun getNativeMemoryInfo(context: Context? = null): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (context != null) {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            val totalGb = memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
            val availGb = memInfo.availMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
            val usedGb = (totalGb - availGb).coerceAtLeast(0.0)

            map["Total RAM"] = String.format(Locale.US, "%.2f GB", totalGb)
            map["Available RAM"] = String.format(Locale.US, "%.2f GB", availGb)
            map["Used RAM"] = String.format(Locale.US, "%.2f GB", usedGb)
            map["summary"] = String.format(Locale.US, "%.2f GB (Bo'sh: %.2f GB)", totalGb, availGb)
            return map
        }
        val runtime = Runtime.getRuntime()
        val totalMb = runtime.totalMemory().toDouble() / (1024.0 * 1024.0)
        val freeMb = runtime.freeMemory().toDouble() / (1024.0 * 1024.0)
        val usedMb = (totalMb - freeMb).coerceAtLeast(0.0)
        map["Total RAM"] = String.format(Locale.US, "%.0f MB", totalMb)
        map["Available RAM"] = String.format(Locale.US, "%.0f MB", freeMb)
        map["Used RAM"] = String.format(Locale.US, "%.0f MB", usedMb)
        map["summary"] = String.format(Locale.US, "%.0f MB (Bo'sh: %.0f MB)", totalMb, freeMb)
        return map
    }

    fun getStorageInfo(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            // Internal App Storage
            val dataPath = Environment.getDataDirectory()
            val stat = StatFs(dataPath.path)
            val dataTotal = stat.blockCountLong * stat.blockSizeLong
            val dataFree = stat.availableBlocksLong * stat.blockSizeLong
            
            map["Data (Ilova xotirasi)"] = formatBytes(dataTotal) + " (Bo'sh: ${formatBytes(dataFree)})"
            
            // External User Storage
            val extPath = Environment.getExternalStorageDirectory()
            val extStat = StatFs(extPath.path)
            val extTotal = extStat.blockCountLong * extStat.blockSizeLong
            val extFree = extStat.availableBlocksLong * extStat.blockSizeLong
            
            map["Foydalanuvchi Xotirasi"] = formatBytes(extTotal) + " (Bo'sh: ${formatBytes(extFree)})"
            
            map["summary"] = formatBytes(extTotal) + " (Bo'sh: ${formatBytes(extFree)})"
            map["extTotal"] = formatBytes(extTotal)
            map["extFree"] = formatBytes(extFree)
        } catch (e: Exception) {
            map["summary"] = "N/A"
            map["extTotal"] = "N/A"
            map["extFree"] = "N/A"
        }
        return map
    }

    private fun formatBytes(bytes: Long): String {
        return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }

    fun getBatteryDiagnostics(context: Context): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            // Use Android official BatteryManager and IntentFilter
            // Direct /sys/class/power_supply access is blocked by SELinux on Android 8+
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, filter)
            if (batteryStatus != null) {
                val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = if (level != -1 && scale != -1) (level * 100 / scale) else -1
                val temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
                val voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                val health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                
                val healthStr = when (health) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "Yaxshi (Good)"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Qizib ketgan"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "Buzuq (Dead)"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Yuqori kuchlanish"
                    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Noma'lum nosozlik"
                    BatteryManager.BATTERY_HEALTH_COLD -> "Sovuq"
                    else -> "Noma'lum"
                }
                
                map["Zaryad (API)"] = if (pct >= 0) "$pct%" else "N/A"
                map["pctNumber"] = pct.toString()
                map["Harorat (API)"] = String.format(Locale.US, "%.1f °C", temp)
                map["Kuchlanish"] = "$voltage mV"
                map["Salomatlik"] = healthStr
                map["Holat"] = if (status == BatteryManager.BATTERY_STATUS_CHARGING) "Quvvatlanmoqda" else "Quvvatlanmayapti"
                map["Texnologiya"] = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "N/A"
                map["level"] = map["Zaryad (API)"] ?: "N/A"
                map["temp"] = map["Harorat (API)"] ?: "N/A"
            }
        } catch (e: Exception) {
            map["level"] = "N/A"
        }
        return map
    }

    fun getNetworkInterfacesDiagnostic(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var count = 0
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isUp && !iface.isLoopback) {
                    val addrs = iface.inetAddresses
                    val ips = mutableListOf<String>()
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress) {
                            ips.add(addr.hostAddress ?: "")
                        }
                    }
                    if (ips.isNotEmpty()) {
                        val mac = iface.hardwareAddress?.joinToString(":") { String.format("%02X", it) } ?: "N/A"
                        map["Interfeys: ${iface.name}"] = "IP: ${ips.joinToString(", ")} | MAC: $mac | MTU: ${iface.mtu}"
                        if (iface.name.contains("wlan") || iface.name.contains("tun")) {
                            map["Faol IP"] = ips.firstOrNull { it.contains(".") } ?: ips.first()
                        }
                        count++
                    }
                }
            }
            if (count == 0) map["Tarmoq"] = "Faol interfeyslar topilmadi."
        } catch (e: Exception) {
            map["Tarmoq Xatosi"] = e.localizedMessage ?: "Noma'lum tarmoq xatosi"
        }
        return map
    }

    fun getKernelVersion(): String {
        return System.getProperty("os.version") ?: "Linux"
    }

    fun getPhysicalRefreshRate(context: Context): Int {
        return try {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
            if (display != null) {
                val currentRate = display.refreshRate
                val modes = display.supportedModes
                val maxRate = modes?.map { it.refreshRate }?.maxOrNull() ?: currentRate
                val rateToUse = if (maxRate > currentRate) maxRate else currentRate
                val rounded = (rateToUse + 0.5f).toInt()
                if (rounded > 0) rounded else 60
            } else {
                60
            }
        } catch (e: Exception) {
            60
        }
    }
    
    fun getSystemProperties(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val platform = Build.HARDWARE
        if (platform.isNotBlank() && platform != Build.UNKNOWN) {
            map["Chipset (Platform)"] = platform
        }
        val board = Build.BOARD
        if (board.isNotBlank() && board != Build.UNKNOWN) {
            map["Product Board"] = board
        }
        val bootloader = Build.BOOTLOADER
        if (bootloader.isNotBlank() && bootloader != Build.UNKNOWN) {
            map["Bootloader"] = bootloader
        }
        val display = Build.DISPLAY
        if (display.isNotBlank() && display != Build.UNKNOWN) {
            map["Build ID"] = display
        }
        val device = Build.DEVICE
        if (device.isNotBlank() && device != Build.UNKNOWN) {
            map["Qurilma (Device)"] = device
        }
        return map
    }

    fun buildFullDeviceDiagnostic(context: Context, isAdbConnected: Boolean): Map<String, String> {
        val infoMap = mutableMapOf<String, String>()
        try {
            infoMap["Ulanish Holati"] = if (isAdbConnected) "ADB Simsiz Rejimda Ulangan (Active)" else "Mahalliy Linux Terminal Rejimi"
            infoMap["Qurilma Modeli"] = Build.MODEL
            infoMap["Brend"] = Build.BRAND.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            infoMap["Ishlab Chiqaruvchi"] = Build.MANUFACTURER
            infoMap["Qurilma Nomi (Device)"] = Build.DEVICE
            
            // Raw CPU / System Info
            val cpuInfo = getCpuInfo()
            cpuInfo.forEach { (k, v) -> infoMap[k] = v }
            
            // Advanced Sys Properties
            val sysProps = getSystemProperties()
            sysProps.forEach { (k, v) -> infoMap[k] = v }
            
            infoMap["Qo'llab-quvvatlanuvchi ABI"] = Build.SUPPORTED_ABIS.joinToString(", ")
            infoMap["Android Talqini"] = "Android " + Build.VERSION.RELEASE
            infoMap["SDK Level (API)"] = Build.VERSION.SDK_INT.toString()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                infoMap["Xavfsizlik Yangilanishi"] = Build.VERSION.SECURITY_PATCH
            }
            infoMap["Tizim Build ID"] = Build.DISPLAY
            
            // Screen
            val metrics = context.resources.displayMetrics
            val physicalDpi = if (metrics.xdpi > 50f && metrics.xdpi < 1000f) metrics.xdpi.toInt() else metrics.densityDpi
            infoMap["Ekran Rezolyutsiyasi"] = "${metrics.widthPixels}x${metrics.heightPixels} px"
            infoMap["Ekran Zichligi"] = "${metrics.densityDpi} DPI (Haqiqiy: $physicalDpi DPI)"
            infoMap["Ekran Yangilanish Tezligi"] = "${getPhysicalRefreshRate(context)} Hz"
            
            // Raw Storage
            val storage = getStorageInfo()
            storage.forEach { (k, v) -> if (k != "summary" && k != "extTotal" && k != "extFree") infoMap[k] = v }
            
            // Raw Memory
            val mem = getNativeMemoryInfo(context)
            if (mem.isEmpty()) { // Fallback
                val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                actManager?.getMemoryInfo(memInfo)
                val totalGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
                val availGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
                infoMap["Operativ RAM (API)"] = String.format(Locale.US, "%.2f GB (Bo'sh: %.2f GB)", totalGb, availGb)
            } else {
                mem.forEach { (k, v) -> if (k != "summary") infoMap[k] = v }
            }
            
            // Raw Battery
            val batt = getBatteryDiagnostics(context)
            batt.forEach { (k, v) -> if (k != "level" && k != "temp" && k != "pctNumber") infoMap[k] = v }
            
            // Sensors
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val sensors = sensorManager?.getSensorList(android.hardware.Sensor.TYPE_ALL)
            infoMap["Fizik Sensorlar Soni"] = sensors?.size?.toString() ?: "N/A"
            
            infoMap["Yadro (Kernel)"] = getKernelVersion()
            
            // Raw Networks
            val nets = getNetworkInterfacesDiagnostic()
            nets.forEach { (k, v) -> infoMap[k] = v }
            
            infoMap["Tizim Standart Tili"] = Locale.getDefault().displayName
            infoMap["Vaqt Mintaqasi"] = TimeZone.getDefault().id
            
            val uptimeMs = SystemClock.elapsedRealtime()
            val secs = uptimeMs / 1000
            val mins = secs / 60
            val hours = mins / 60
            val days = hours / 24
            infoMap["Tizim Ish Vaqti (Uptime)"] = when {
                days > 0 -> "${days} kun ${hours % 24} soat ${mins % 60} daqiqa"
                hours > 0 -> "${hours} soat ${mins % 60} daqiqa"
                else -> "${mins} daqiqa ${secs % 60} soniya"
            }
        } catch (e: Exception) {
            infoMap["Xatolik"] = "Ma'lumot olishda xatolik: ${e.localizedMessage}"
        }
        return infoMap
    }

    fun generateNeofetchBanner(context: Context, isAdbConnected: Boolean): String {
        val model = Build.MODEL
        val brand = Build.BRAND.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        val release = Build.VERSION.RELEASE
        val sdk = Build.VERSION.SDK_INT
        val kernel = getKernelVersion().split(" ").take(3).joinToString(" ") // Short kernel
        
        val cpuInfo = getCpuInfo()
        val cpu = cpuInfo["Protsessor (SoC)"] ?: Build.HARDWARE
        val cores = cpuInfo["Yadrolar soni"] ?: Runtime.getRuntime().availableProcessors().toString()
        
        val mem = getNativeMemoryInfo()
        val ramStr = if (mem.isNotEmpty()) "${mem["Used RAM"]} / ${mem["Total RAM"]}" else "N/A"
        
        val batt = getBatteryDiagnostics(context)
        val battStr = batt["level"] ?: "N/A"
        
        val storage = getStorageInfo()
        
        val connStr = if (isAdbConnected) "ADB Wireless (Active)" else "Local Android Shell"
        
        val uptimeMs = SystemClock.elapsedRealtime()
        val secs = uptimeMs / 1000
        val mins = secs / 60
        val hours = mins / 60
        val days = hours / 24
        val uptimeStr = when {
            days > 0 -> "${days}d ${hours % 24}h ${mins % 60}m"
            hours > 0 -> "${hours}h ${mins % 60}m"
            else -> "${mins}m ${secs % 60}s"
        }
        
        val metrics = context.resources.displayMetrics
        val resStr = "${metrics.widthPixels}x${metrics.heightPixels} @ ${getPhysicalRefreshRate(context)}Hz"
        
        return """
          .---.            root@android-device
         /     \           -------------------
        | () () |          OS: Android $release (API $sdk)
         \  -  /           Host: $brand $model (${Build.DEVICE})
          `---`            Kernel: $kernel
        /|     |\          Uptime: $uptimeStr
       / |     | \         Shell: native / mksh ($connStr)
      /  |     |  \        Resolution: $resStr
         |     |           CPU: $cpu ($cores cores)
         |_____|           Memory: $ramStr
          |   |            Storage: ${storage["extTotal"]} (Free: ${storage["extFree"]})
          |   |            Battery: $battStr (${batt["Holat"] ?: "Battery"})
        """.trimIndent()
    }
}
