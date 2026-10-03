package com.example.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RealNetworkInspector {

    fun getLocalDeviceIpAddress(context: Context): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            return "Error: ${e.localizedMessage ?: "Unable to read IP"}"
        }
        return "127.0.0.1"
    }

    fun getIfconfigOutput(context: Context): String {
        val sb = StringBuilder()
        try {
            // First attempt: run real system 'ip addr' or 'ifconfig' command
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "ip addr show 2>/dev/null || ifconfig 2>/dev/null"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            val cmdOutput = StringBuilder()
            while (reader.readLine().also { line = it } != null) {
                cmdOutput.append(line).append("\n")
            }
            process.waitFor()
            if (cmdOutput.isNotBlank()) {
                return cmdOutput.toString().trimEnd()
            }
        } catch (_: Exception) {}

        // Second real inspection: query actual Java/Android NetworkInterface list
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            if (interfaces.isEmpty()) {
                return "ifconfig: No network interfaces found."
            }
            for (intf in interfaces) {
                sb.append("${intf.name}: flags=${if (intf.isUp) "UP" else "DOWN"}")
                if (intf.isLoopback) sb.append(",LOOPBACK")
                if (intf.isPointToPoint) sb.append(",POINTOPOINT")
                if (intf.supportsMulticast()) sb.append(",MULTICAST")
                sb.append(" mtu ${try { intf.mtu } catch (_: Exception) { 0 }}\n")

                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    val host = addr.hostAddress ?: continue
                    if (addr is Inet4Address) {
                        sb.append("        inet $host\n")
                    } else {
                        sb.append("        inet6 $host\n")
                    }
                }
                val macBytes = try { intf.hardwareAddress } catch (_: Exception) { null }
                if (macBytes != null && macBytes.isNotEmpty()) {
                    val mac = macBytes.joinToString(":") { String.format("%02x", it) }
                    sb.append("        ether $mac\n")
                }
                sb.append("\n")
            }
        } catch (e: Exception) {
            sb.append("ifconfig: Error inspecting interfaces: ${e.localizedMessage}\n")
        }

        return sb.toString().trimEnd()
    }

    fun getDetailedNetworkDiagnostics(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "Network Diagnostics: ConnectivityManager unavailable."

        val activeNetwork = cm.activeNetwork
            ?: return "Network Diagnostics: No active network connection (Device is Offline)."

        val caps = cm.getNetworkCapabilities(activeNetwork)
            ?: return "Network Diagnostics: Active network has no capabilities."

        val linkProps = cm.getLinkProperties(activeNetwork)

        val sb = StringBuilder()
        sb.append("=== ACTIVE NETWORK DIAGNOSTICS ===\n")
        val transports = mutableListOf<String>()
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transports.add("Wi-Fi")
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transports.add("Cellular")
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transports.add("Ethernet")
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) transports.add("Bluetooth")
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) transports.add("VPN")

        sb.append("Transport: ${transports.joinToString(", ").ifEmpty { "Unknown" }}\n")
        sb.append("Internet validated: ${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}\n")
        sb.append("Link Downstream Bandwidth: ${caps.linkDownstreamBandwidthKbps} Kbps\n")
        sb.append("Link Upstream Bandwidth: ${caps.linkUpstreamBandwidthKbps} Kbps\n")

        if (linkProps != null) {
            sb.append("Interface: ${linkProps.interfaceName ?: "Unknown"}\n")
            sb.append("Domains: ${linkProps.domains ?: "N/A"}\n")
            sb.append("Addresses:\n")
            linkProps.linkAddresses.forEach { addr ->
                sb.append("  • ${addr.address.hostAddress} / ${addr.prefixLength}\n")
            }
            sb.append("DNS Servers:\n")
            linkProps.dnsServers.forEach { dns ->
                sb.append("  • ${dns.hostAddress}\n")
            }
            sb.append("Routes:\n")
            linkProps.routes.forEach { route ->
                sb.append("  • $route\n")
            }
        }
        return sb.toString().trimEnd()
    }

    fun getNetstatOutput(context: Context): String {
        val sb = StringBuilder()
        // Attempt 1: Execute real netstat / ss from system
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "netstat -tuln 2>/dev/null || ss -tuln 2>/dev/null"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            val cmdOutput = StringBuilder()
            while (reader.readLine().also { line = it } != null) {
                cmdOutput.append(line).append("\n")
            }
            process.waitFor()
            if (cmdOutput.isNotBlank()) {
                return cmdOutput.toString().trimEnd()
            }
        } catch (_: Exception) {}

        // Attempt 2: Read directly from /proc/net/tcp and /proc/net/tcp6
        sb.append("Active Internet connections (only permitted /proc sockets)\n")
        sb.append("%-6s %-6s %-6s %-22s %-22s %s\n".format("Proto", "Recv-Q", "Send-Q", "Local Address", "Foreign Address", "State"))
        var foundAny = false
        val procFiles = listOf("/proc/net/tcp", "/proc/net/tcp6")
        for (pPath in procFiles) {
            try {
                val f = File(pPath)
                if (f.canRead()) {
                    val lines = f.readLines().drop(1)
                    for (l in lines) {
                        val parts = l.trim().split("\\s+".toRegex())
                        if (parts.size >= 4) {
                            val local = parseHexSocket(parts[1])
                            val remote = parseHexSocket(parts[2])
                            val state = tcpStateName(parts[3])
                            sb.append("%-6s %-6s %-6s %-22s %-22s %s\n".format("tcp", "0", "0", local, remote, state))
                            foundAny = true
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (!foundAny) {
            sb.append("\nnetstat: /proc/net/tcp: Permission denied (Android SELinux blocks app access to raw socket tables).\n")
            sb.append("Use ADB connection or root shell ('su') for unrestricted socket inspection.")
        }
        return sb.toString().trimEnd()
    }

    private fun parseHexSocket(hex: String): String {
        return try {
            val tokens = hex.split(":")
            if (tokens.size != 2) return hex
            val ipHex = tokens[0]
            val port = tokens[1].toInt(16)
            if (ipHex.length == 8) {
                val b1 = ipHex.substring(6, 8).toInt(16)
                val b2 = ipHex.substring(4, 6).toInt(16)
                val b3 = ipHex.substring(2, 4).toInt(16)
                val b4 = ipHex.substring(0, 2).toInt(16)
                "$b1.$b2.$b3.$b4:$port"
            } else {
                "[IPv6]:$port"
            }
        } catch (_: Exception) {
            hex
        }
    }

    private fun tcpStateName(stateHex: String): String {
        return when (stateHex.uppercase()) {
            "01" -> "ESTABLISHED"
            "02" -> "SYN_SENT"
            "03" -> "SYN_RECV"
            "04" -> "FIN_WAIT1"
            "05" -> "FIN_WAIT2"
            "06" -> "TIME_WAIT"
            "07" -> "CLOSE"
            "08" -> "CLOSE_WAIT"
            "09" -> "LAST_ACK"
            "0A" -> "LISTEN"
            "0B" -> "CLOSING"
            else -> "UNKNOWN"
        }
    }

    fun performPing(target: String, onLine: (String) -> Unit = {}): String {
        val cleanTarget = target.trim()
        if (!cleanTarget.matches(Regex("^[a-zA-Z0-9.:_%-]+$"))) {
            return "ping: invalid target host specification"
        }
        val sb = StringBuilder()
        // Execute real /system/bin/ping safely with direct argument array
        try {
            val process = Runtime.getRuntime().exec(arrayOf("ping", "-c", "4", cleanTarget))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
                onLine(line ?: "")
            }
            while (errReader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
                onLine(line ?: "")
            }
            process.waitFor()
            if (sb.isNotBlank()) {
                return sb.toString().trimEnd()
            }
        } catch (_: Exception) {}

        // Real Java InetAddress ICMP / Echo fallback if ping binary fails
        try {
            onLine("PING $cleanTarget via Java InetAddress isReachable()...")
            val address = InetAddress.getByName(cleanTarget)
            val startTime = System.currentTimeMillis()
            val reachable = address.isReachable(4000)
            val elapsed = System.currentTimeMillis() - startTime
            if (reachable) {
                val out = "$cleanTarget is alive (time=${elapsed}ms, host=${address.canonicalHostName})"
                onLine(out)
                return out
            } else {
                val out = "$cleanTarget: Request timed out (unreachable after ${elapsed}ms)"
                onLine(out)
                return out
            }
        } catch (e: Exception) {
            val out = "ping: $cleanTarget: ${e.localizedMessage ?: "Unknown host"}"
            onLine(out)
            return out
        }
    }

    suspend fun performPortScan(target: String, onProgress: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        val cleanTarget = target.trim()
        val commonPorts = listOf(21, 22, 23, 25, 53, 80, 110, 143, 443, 445, 554, 8080, 8443, 5555, 9090)
        val sb = StringBuilder()
        sb.append("Starting real TCP port scan against $cleanTarget...\n")
        onProgress("Scanning $cleanTarget (${commonPorts.size} common ports)...")

        val openPorts = mutableListOf<Int>()
        for (port in commonPorts) {
            try {
                val socket = java.net.Socket()
                val socketAddress = java.net.InetSocketAddress(cleanTarget, port)
                socket.connect(socketAddress, 600)
                socket.close()
                openPorts.add(port)
                val line = "Discovered open port $port/tcp on $cleanTarget"
                sb.append("$line\n")
                onProgress(line)
            } catch (_: Exception) {
                // Port closed or filtered
            }
        }
        sb.append("\nScan report for $cleanTarget:\n")
        if (openPorts.isEmpty()) {
            sb.append("All scanned common ports are closed or filtered.")
        } else {
            sb.append("PORT     STATE SERVICE\n")
            openPorts.forEach { p ->
                val service = when (p) {
                    21 -> "ftp"; 22 -> "ssh"; 23 -> "telnet"; 25 -> "smtp"; 53 -> "domain"
                    80 -> "http"; 110 -> "pop3"; 143 -> "imap"; 443 -> "https"; 445 -> "microsoft-ds"
                    554 -> "rtsp"; 5555 -> "adb"; 8080 -> "http-proxy"; 8443 -> "https-alt"
                    else -> "unknown"
                }
                sb.append("%-8s %-5s %s\n".format("$p/tcp", "open", service))
            }
        }
        sb.toString().trimEnd()
    }

    suspend fun performRealSpeedtest(context: Context, onProgress: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        val testUrl = "https://speed.cloudflare.com/__down?bytes=10000000"
        onProgress("Connecting to real speedtest node ($testUrl)...")
        try {
            val url = URL(testUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 7000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "SuperTerminalSpeedtest/1.0")

            val startTime = System.currentTimeMillis()
            conn.connect()
            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                return@withContext "Speedtest HTTP Error: Response code $responseCode"
            }

            val inputStream = conn.inputStream
            val buffer = ByteArray(8192)
            var totalBytesRead = 0L
            var bytesRead: Int
            var lastUpdate = System.currentTimeMillis()

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                totalBytesRead += bytesRead
                val now = System.currentTimeMillis()
                if (now - lastUpdate > 300) {
                    val durationSec = (now - startTime) / 1000.0
                    val currentMbps = if (durationSec > 0) (totalBytesRead * 8.0) / (durationSec * 1024 * 1024) else 0.0
                    onProgress(String.format("Downloading: %.2f MB | Current: %.2f Mbps", totalBytesRead / (1024.0 * 1024.0), currentMbps))
                    lastUpdate = now
                }
            }
            inputStream.close()
            conn.disconnect()

            val totalTimeSec = (System.currentTimeMillis() - startTime) / 1000.0
            val speedMbps = (totalBytesRead * 8.0) / (totalTimeSec * 1024 * 1024)
            val totalMb = totalBytesRead / (1024.0 * 1024.0)

            val summary = String.format(
                "Speedtest finished:\n• Transferred: %.2f MB\n• Time elapsed: %.2f s\n• Real Download Speed: %.2f Mbps",
                totalMb, totalTimeSec, speedMbps
            )
            onProgress(summary)
            summary
        } catch (e: Exception) {
            val err = "speedtest: Download failed: ${e.localizedMessage ?: "Network unreachable"}"
            onProgress(err)
            err
        }
    }

    suspend fun performDnsLookup(domain: String): String = withContext(Dispatchers.IO) {
        val cleanDomain = domain.trim().removePrefix("http://").removePrefix("https://").substringBefore("/")
        if (cleanDomain.isBlank()) {
            return@withContext "dns: Invalid or empty hostname specified."
        }
        val sb = StringBuilder()
        sb.append("=== DNS QUERY: $cleanDomain ===\n")
        try {
            val addresses = InetAddress.getAllByName(cleanDomain)
            sb.append("Resolved ${addresses.size} address(es):\n")
            for (addr in addresses) {
                val type = if (addr is Inet4Address) "A (IPv4)" else "AAAA (IPv6)"
                sb.append("  • %-12s %s\n".format(type, addr.hostAddress))
            }
            sb.append("Canonical Hostname: ${addresses.firstOrNull()?.canonicalHostName ?: "N/A"}")
        } catch (e: Exception) {
            sb.append("DNS Resolution failed: ${e.localizedMessage ?: "Host not found"}")
        }
        sb.toString().trimEnd()
    }
}

