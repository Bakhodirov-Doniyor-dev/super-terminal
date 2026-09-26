package com.example.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Locale

object RealProcessInspector {

    fun getRealPsOutput(context: Context, args: String = ""): String {
        return try {
            val cleanArgs = args.trim()
            if (cleanArgs.isNotEmpty() && !cleanArgs.matches(Regex("^[a-zA-Z0-9\\s\\-_]+$"))) {
                return "ps: invalid arguments specification"
            }
            val cmd = if (cleanArgs.isNotBlank()) "ps $cleanArgs" else "ps -ef 2>/dev/null || ps -A 2>/dev/null || ps"
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            val sb = StringBuilder()
            var line: String? = reader.readLine()
            while (line != null) {
                sb.append(line).append("\n")
                line = reader.readLine()
            }
            val errSb = StringBuilder()
            var errLine: String? = errReader.readLine()
            while (errLine != null) {
                errSb.append(errLine).append("\n")
                errLine = errReader.readLine()
            }
            process.waitFor()
            if (sb.isNotBlank()) {
                sb.toString().trimEnd()
            } else if (errSb.isNotBlank()) {
                errSb.toString().trimEnd()
            } else {
                "ps: No processes returned or permission denied by Android SELinux."
            }
        } catch (e: Exception) {
            "ps: execution failed: ${e.localizedMessage}"
        }
    }
    
    fun getRealTopOutput(context: Context, limitCount: Int = 15): String {
        // First try real system top execution
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "top -b -n 1 -m $limitCount 2>/dev/null || top -n 1 2>/dev/null"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val sb = StringBuilder()
            var line: String? = reader.readLine()
            var count = 0
            while (line != null && count < limitCount + 10) {
                sb.append(line).append("\n")
                count++
                line = reader.readLine()
            }
            process.waitFor()
            if (sb.isNotBlank()) {
                return sb.toString().trimEnd()
            }
        } catch (_: Exception) {}

        // Fallback: Read real running processes from ActivityManager without synthetic metrics
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val pm = context.packageManager
        val sb = StringBuilder()
        sb.append("USER       PID   PPID  VSIZE  RSS   WCHAN            PC  NAME\n")
        val runningProcesses = try { am?.runningAppProcesses ?: emptyList() } catch (e: Exception) { emptyList() }
        if (runningProcesses.isEmpty()) {
            return "top: No processes accessible from Android sandbox."
        }
        for (proc in runningProcesses.take(limitCount)) {
            val user = if (proc.uid == 0) "root" else if (proc.uid == 1000) "system" else "u0_a${proc.uid - 10000}"
            val cmd = proc.processName
            sb.append("%-10s %-5d %-5s %-6s %-5s %-16s %-3s %s\n".format(
                user.take(10), proc.pid, "-", "-", "-", "-", "-", cmd
            ))
        }
        return sb.toString().trimEnd()
    }

    fun getProcessThreadsDiagnostic(): String {
        val sb = java.lang.StringBuilder()
        val allStackTraces = Thread.getAllStackTraces()
        sb.append("=== JVM THREAD DIAGNOSTIC ===\n")
        for ((thread, _) in allStackTraces) {
            sb.append("${thread.id} ${thread.name} ${thread.state}\n")
        }
        return sb.toString()
    }

    fun getDetailedMemoryDiagnostic(context: Context): String {
        return "Memory Diagnostic:\nMax: ${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB"
    }

    fun killProcess(context: Context, target: String): String {
        val pid = target.toIntOrNull()
        if (pid != null) {
            Process.sendSignal(pid, Process.SIGNAL_KILL)
            return "Sent SIGKILL to $pid"
        }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.killBackgroundProcesses(target)
        return "Killed background process $target"
    }

    fun calculateRealCpuUsage(): Pair<Float, Float> {
        try {
            val statFile = java.io.File("/proc/stat")
            if (statFile.canRead()) {
                val lines = statFile.readLines()
                for (line in lines) {
                    if (line.startsWith("cpu ")) {
                        val parts = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
                        if (parts.size >= 5) {
                            val user = parts[1].toFloat()
                            val nice = parts[2].toFloat()
                            val sys = parts[3].toFloat()
                            val idle = parts[4].toFloat()
                            val total = user + nice + sys + idle
                            val uPct = ((user + nice) / total) * 100f
                            val sPct = (sys / total) * 100f
                            return Pair(uPct, sPct)
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return Pair(0f, 0f)
    }
}
