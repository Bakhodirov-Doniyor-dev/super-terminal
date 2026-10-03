package com.example.net

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import java.io.*
import java.net.*
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class LanAccessLog(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val clientIp: String,
    val method: String,
    val path: String,
    val statusCode: Int,
    val bytesTransferred: Long
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

/**
 * Production-grade, multithreaded embedded HTTP/1.1 server.
 * Supports:
 * - Streaming downloads with HTTP Range (RFC 7233) for resumable downloads & media streaming
 * - Streaming chunked/multipart file uploads directly to disk without memory buffering
 * - Cryptographically secure token/session authentication
 * - Path traversal and symlink escape defenses
 * - Self-contained responsive OneUI 8.5 Web Interface (100% offline capable)
 * - Real-time client count, bytes down/up counters, and HTTP access logs
 */
class LanFileServer(
    val port: Int = 8080,
    var sharedDirectory: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
    var isAuthEnabled: Boolean = true,
    var authToken: String = generateSecureToken(),
    private val onStatsUpdated: ((activeClients: Int, downloaded: Long, uploaded: Long) -> Unit)? = null,
    private val onLog: ((LanAccessLog) -> Unit)? = null
) {
    companion object {
        private const val TAG = "LanFileServer"
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB

        private fun logI(tag: String, msg: String) {
            try {
                Log.i(tag, msg)
            } catch (e: Throwable) {
                println("[$tag] $msg")
            }
        }

        private fun logE(tag: String, msg: String, tr: Throwable? = null) {
            try {
                if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
            } catch (e: Throwable) {
                println("[$tag] $msg: $tr")
            }
        }

        fun generateSecureToken(): String {
            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
            val random = SecureRandom()
            val sb = StringBuilder(16)
            for (i in 0 until 16) {
                if (i > 0 && i % 4 == 0) sb.append("-")
                sb.append(chars[random.nextInt(chars.length)])
            }
            return sb.toString()
        }
    }

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var workerExecutor: ExecutorService? = null
    private var acceptThread: Thread? = null

    val activeClients = AtomicInteger(0)
    val totalBytesDownloaded = AtomicLong(0)
    val totalBytesUploaded = AtomicLong(0)
    var startTimeMs: Long = 0L
        private set

    /**
     * Verifies whether the specified port is available to bind.
     */
    fun checkPortAvailable(portToCheck: Int): Boolean {
        return try {
            val testSocket = ServerSocket()
            testSocket.reuseAddress = true
            testSocket.bind(InetSocketAddress("0.0.0.0", portToCheck))
            testSocket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    @Synchronized
    fun start() {
        if (isRunning.get()) return

        if (!sharedDirectory.exists()) {
            sharedDirectory.mkdirs()
        }

        try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress("0.0.0.0", port))
            serverSocket = socket
            isRunning.set(true)
            startTimeMs = System.currentTimeMillis()

            workerExecutor = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "LanServerWorker-${System.currentTimeMillis()}").apply {
                    isDaemon = true
                }
            }

            acceptThread = Thread({
                logI(TAG, "LanFileServer started on port $port bound to 0.0.0.0")
                while (isRunning.get() && !socket.isClosed) {
                    try {
                        val clientSocket = socket.accept()
                        clientSocket.soTimeout = 30000 // 30 sec timeout
                        workerExecutor?.submit {
                            handleClient(clientSocket)
                        }
                    } catch (e: SocketException) {
                        if (!isRunning.get()) break
                        logE(TAG, "SocketException in accept loop", e)
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                        logE(TAG, "Exception in accept loop", e)
                    }
                }
            }, "LanServerAcceptThread").apply {
                isDaemon = true
                start()
            }
        } catch (e: BindException) {
            isRunning.set(false)
            throw BindException("Port $port band (boshqa dastur tomonidan band qilingan). Boshqa port tanlang.")
        } catch (e: Exception) {
            isRunning.set(false)
            throw IOException("LAN Serverni ishga tushirishda xatolik: ${e.message}", e)
        }
    }

    @Synchronized
    fun stop() {
        if (!isRunning.getAndSet(false)) return

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            logE(TAG, "Error closing server socket", e)
        }
        serverSocket = null

        acceptThread?.interrupt()
        acceptThread = null

        workerExecutor?.shutdownNow()
        workerExecutor = null

        activeClients.set(0)
        logI(TAG, "LanFileServer successfully stopped")
    }

    fun isServerRunning(): Boolean = isRunning.get() && serverSocket?.isBound == true && serverSocket?.isClosed == false

    private fun handleClient(clientSocket: Socket) {
        val clientIp = (clientSocket.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress ?: "Unknown"
        activeClients.incrementAndGet()
        notifyStats()

        try {
            val input = BufferedInputStream(clientSocket.getInputStream())
            val output = BufferedOutputStream(clientSocket.getOutputStream())

            // Parse HTTP Request Line & Headers
            val requestHeaderLines = readHeaderLines(input)
            if (requestHeaderLines.isEmpty()) {
                return
            }

            val requestLine = requestHeaderLines[0]
            val requestParts = requestLine.split(" ")
            if (requestParts.size < 2) {
                sendSimpleResponse(output, 400, "Bad Request", "Invalid HTTP request line")
                return
            }

            val method = requestParts[0].uppercase(Locale.US)
            val rawUri = requestParts[1]

            val headers = mutableMapOf<String, String>()
            for (i in 1 until requestHeaderLines.size) {
                val line = requestHeaderLines[i]
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim().lowercase(Locale.US)
                    val value = line.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            val uri = try {
                URI(rawUri)
            } catch (e: Exception) {
                sendSimpleResponse(output, 400, "Bad Request", "Malformed URI")
                return
            }

            val path = uri.path ?: "/"
            val queryParams = parseQueryParams(uri.rawQuery)

            // Authentication Check
            if (isAuthEnabled) {
                val hasValidAuth = checkAuthentication(headers, queryParams)
                if (!hasValidAuth) {
                    if (method == "POST" && path == "/api/login") {
                        handleLoginPost(input, output, headers, clientIp)
                        return
                    } else {
                        // Serve Login Page
                        serveLoginPage(output, clientIp)
                        logRequest(clientIp, method, path, 401, 0)
                        return
                    }
                }
            }

            when (method) {
                "GET", "HEAD" -> {
                    when {
                        path == "/api/logout" -> {
                            handleLogout(output, clientIp)
                        }
                        path == "/api/storage-info" -> {
                            handleStorageInfo(output, clientIp)
                        }
                        path.startsWith("/api/download") -> {
                            val filePath = queryParams["path"] ?: ""
                            handleFileDownload(output, filePath, headers, method == "HEAD", clientIp)
                        }
                        else -> {
                            // Either browse directory or serve file
                            handleBrowseOrFile(output, path, headers, method == "HEAD", clientIp)
                        }
                    }
                }
                "POST" -> {
                    when {
                        path == "/api/upload" -> {
                            val targetSubDir = queryParams["dir"] ?: ""
                            handleFileUpload(input, output, headers, targetSubDir, clientIp)
                        }
                        else -> {
                            sendSimpleResponse(output, 404, "Not Found", "Endpoint not found")
                            logRequest(clientIp, method, path, 404, 0)
                        }
                    }
                }
                "OPTIONS" -> {
                    sendOptionsResponse(output)
                    logRequest(clientIp, method, path, 204, 0)
                }
                else -> {
                    sendSimpleResponse(output, 405, "Method Not Allowed", "Method $method is not supported")
                    logRequest(clientIp, method, path, 405, 0)
                }
            }
        } catch (e: SocketTimeoutException) {
            // Client timed out
        } catch (e: SocketException) {
            // Client connection reset/aborted
        } catch (e: Exception) {
            logE(TAG, "Error handling client request from $clientIp", e)
        } finally {
            activeClients.decrementAndGet()
            notifyStats()
            try {
                clientSocket.close()
            } catch (e: Exception) {
                // Ignore close error
            }
        }
    }

    private fun checkAuthentication(headers: Map<String, String>, queryParams: Map<String, String>): Boolean {
        // 1. Check Query parameter ?token=...
        val queryToken = queryParams["token"]
        if (!queryToken.isNullOrEmpty() && queryToken == authToken) {
            return true
        }

        // 2. Check Authorization Header: Bearer <token>
        val authHeader = headers["authorization"]
        if (!authHeader.isNullOrEmpty()) {
            if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
                val token = authHeader.substring(7).trim()
                if (token == authToken) return true
            }
        }

        // 3. Check Cookie: lan_session=<token>
        val cookieHeader = headers["cookie"]
        if (!cookieHeader.isNullOrEmpty()) {
            val cookies = cookieHeader.split(";")
            for (c in cookies) {
                val parts = c.trim().split("=")
                if (parts.size == 2 && parts[0].trim() == "lan_session") {
                    if (parts[1].trim() == authToken) {
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun handleLoginPost(
        input: InputStream,
        output: OutputStream,
        headers: Map<String, String>,
        clientIp: String
    ) {
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val postData = if (contentLength in 1..4096) {
            val bytes = ByteArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = input.read(bytes, read, contentLength - read)
                if (n <= 0) break
                read += n
            }
            String(bytes, 0, read, StandardCharsets.UTF_8)
        } else {
            ""
        }

        val params = parseQueryParams(postData)
        val submittedToken = params["token"]?.trim() ?: ""

        if (submittedToken == authToken) {
            // Set Cookie and redirect to /
            val response = "HTTP/1.1 303 See Other\r\n" +
                    "Location: /\r\n" +
                    "Set-Cookie: lan_session=$authToken; Path=/; HttpOnly; SameSite=Strict\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: close\r\n\r\n"
            output.write(response.toByteArray(StandardCharsets.UTF_8))
            output.flush()
            logRequest(clientIp, "POST", "/api/login", 303, 0)
        } else {
            // Failed auth
            val errorHtml = buildLoginPage(errorMessage = "Noto'g'ri kirish kodi (Token). Iltimos tekshirib qayta kiriting.")
            sendHtmlResponse(output, 401, errorHtml)
            logRequest(clientIp, "POST", "/api/login", 401, errorHtml.length.toLong())
        }
    }

    private fun handleLogout(output: OutputStream, clientIp: String) {
        val response = "HTTP/1.1 303 See Other\r\n" +
                "Location: /\r\n" +
                "Set-Cookie: lan_session=deleted; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(StandardCharsets.UTF_8))
        output.flush()
        logRequest(clientIp, "GET", "/api/logout", 303, 0)
    }

    /**
     * Handles browsing directory or serving individual files directly from the shared tree.
     */
    private fun handleBrowseOrFile(
        output: OutputStream,
        rawPath: String,
        headers: Map<String, String>,
        isHead: Boolean,
        clientIp: String
    ) {
        val decodedPath = try {
            URLDecoder.decode(rawPath, "UTF-8")
        } catch (e: Exception) {
            sendSimpleResponse(output, 400, "Bad Request", "URL decoding failed")
            return
        }

        val targetFile = resolveSafeFile(decodedPath)
        if (targetFile == null) {
            sendSimpleResponse(output, 403, "Forbidden", "Xavfsizlik cheklovi: Belgilangan papkadan tashqariga chiqish taqiqlangan.")
            logRequest(clientIp, "GET", rawPath, 403, 0)
            return
        }

        if (!targetFile.exists()) {
            sendSimpleResponse(output, 404, "Not Found", "Fayl yoki papka topilmadi: $decodedPath")
            logRequest(clientIp, "GET", rawPath, 404, 0)
            return
        }

        if (targetFile.isDirectory) {
            val relativeDir = getRelativePath(targetFile)
            val html = buildDirectoryListingHtml(targetFile, relativeDir)
            sendHtmlResponse(output, 200, html)
            logRequest(clientIp, "GET", rawPath, 200, html.length.toLong())
        } else {
            streamFile(output, targetFile, headers, isHead, clientIp)
        }
    }

    private fun handleFileDownload(
        output: OutputStream,
        filePath: String,
        headers: Map<String, String>,
        isHead: Boolean,
        clientIp: String
    ) {
        val decodedPath = try {
            URLDecoder.decode(filePath, "UTF-8")
        } catch (e: Exception) {
            filePath
        }

        val targetFile = resolveSafeFile(decodedPath)
        if (targetFile == null || !targetFile.exists() || targetFile.isDirectory) {
            sendSimpleResponse(output, 404, "Not Found", "Yuklab olinadigan fayl topilmadi")
            logRequest(clientIp, "GET", "/api/download", 404, 0)
            return
        }

        streamFile(output, targetFile, headers, isHead, clientIp, forceDownload = true)
    }

    /**
     * Production HTTP Range & Streaming File Response (RFC 7233).
     * Supports resume, seeking, and multi-gigabyte media without high memory consumption.
     */
    private fun streamFile(
        output: OutputStream,
        file: File,
        headers: Map<String, String>,
        isHead: Boolean,
        clientIp: String,
        forceDownload: Boolean = false
    ) {
        val fileLength = file.length()
        val mimeType = getMimeType(file.name)
        val rangeHeader = headers["range"]

        if (rangeHeader != null && rangeHeader.startsWith("bytes=", ignoreCase = true)) {
            // Handle HTTP Range Request (206 Partial Content)
            val rangeValue = rangeHeader.substring(6).trim()
            val dashIdx = rangeValue.indexOf('-')
            var start = 0L
            var end = fileLength - 1L

            try {
                if (dashIdx == 0) {
                    // Suffix bytes: bytes=-500
                    val suffix = rangeValue.substring(1).toLong()
                    start = (fileLength - suffix).coerceAtLeast(0L)
                } else if (dashIdx == rangeValue.length - 1) {
                    // Prefix bytes: bytes=500-
                    start = rangeValue.substring(0, dashIdx).toLong()
                } else if (dashIdx > 0) {
                    start = rangeValue.substring(0, dashIdx).toLong()
                    end = rangeValue.substring(dashIdx + 1).toLong().coerceAtMost(fileLength - 1L)
                }
            } catch (e: Exception) {
                start = 0L
                end = fileLength - 1L
            }

            if (start > end || start >= fileLength) {
                val header = "HTTP/1.1 416 Range Not Satisfiable\r\n" +
                        "Content-Range: bytes */$fileLength\r\n" +
                        "Content-Length: 0\r\n\r\n"
                output.write(header.toByteArray(StandardCharsets.UTF_8))
                output.flush()
                logRequest(clientIp, "GET", file.name, 416, 0)
                return
            }

            val rangeLength = end - start + 1L
            val disposition = if (forceDownload) "attachment; filename=\"${sanitizeForHeader(file.name)}\"" else "inline"

            val header = "HTTP/1.1 206 Partial Content\r\n" +
                    "Content-Type: $mimeType\r\n" +
                    "Content-Length: $rangeLength\r\n" +
                    "Content-Range: bytes $start-$end/$fileLength\r\n" +
                    "Accept-Ranges: bytes\r\n" +
                    "Content-Disposition: $disposition\r\n" +
                    "Connection: keep-alive\r\n\r\n"
            output.write(header.toByteArray(StandardCharsets.UTF_8))
            output.flush()

            if (!isHead) {
                var bytesSent = 0L
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(start)
                    val buffer = ByteArray(BUFFER_SIZE)
                    var remaining = rangeLength
                    while (remaining > 0) {
                        val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
                        val read = raf.read(buffer, 0, toRead)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        remaining -= read
                        bytesSent += read
                        totalBytesDownloaded.addAndGet(read.toLong())
                        notifyStats()
                    }
                    output.flush()
                }
                logRequest(clientIp, "GET", file.name, 206, bytesSent)
            } else {
                logRequest(clientIp, "HEAD", file.name, 206, 0)
            }
        } else {
            // Full File Download (200 OK)
            val disposition = if (forceDownload) "attachment; filename=\"${sanitizeForHeader(file.name)}\"" else "inline"
            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: $mimeType\r\n" +
                    "Content-Length: $fileLength\r\n" +
                    "Accept-Ranges: bytes\r\n" +
                    "Content-Disposition: $disposition\r\n" +
                    "Connection: keep-alive\r\n\r\n"
            output.write(header.toByteArray(StandardCharsets.UTF_8))
            output.flush()

            if (!isHead) {
                var bytesSent = 0L
                FileInputStream(file).use { fis ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (fis.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesSent += read
                        totalBytesDownloaded.addAndGet(read.toLong())
                        notifyStats()
                    }
                    output.flush()
                }
                logRequest(clientIp, "GET", file.name, 200, bytesSent)
            } else {
                logRequest(clientIp, "HEAD", file.name, 200, 0)
            }
        }
    }

    /**
     * Streaming File Upload directly to disk.
     * Prevents loading huge files into memory (RAM).
     */
    private fun handleFileUpload(
        input: InputStream,
        output: OutputStream,
        headers: Map<String, String>,
        targetSubDir: String,
        clientIp: String
    ) {
        val targetDir = resolveSafeFile(targetSubDir)
        if (targetDir == null || !targetDir.exists() || !targetDir.isDirectory) {
            sendJsonResponse(output, 400, "{\"success\":false,\"error\":\"Yuklash papkasi mavjud emas\"}")
            logRequest(clientIp, "POST", "/api/upload", 400, 0)
            return
        }

        val contentType = headers["content-type"] ?: ""
        val contentLength = headers["content-length"]?.toLongOrNull() ?: -1L

        if (contentType.startsWith("multipart/form-data", ignoreCase = true)) {
            val boundaryMarker = extractBoundary(contentType)
            if (boundaryMarker == null) {
                sendJsonResponse(output, 400, "{\"success\":false,\"error\":\"Multipart boundary topilmadi\"}")
                return
            }

            try {
                val uploadedFilename = parseMultipartAndSave(input, boundaryMarker, targetDir, contentLength)
                if (uploadedFilename != null) {
                    sendJsonResponse(output, 200, "{\"success\":true,\"filename\":\"$uploadedFilename\"}")
                    logRequest(clientIp, "POST", "/api/upload ($uploadedFilename)", 200, 0)
                } else {
                    sendJsonResponse(output, 400, "{\"success\":false,\"error\":\"Fayl tanlanmagan yoki yuklash bekor qilindi\"}")
                }
            } catch (e: Exception) {
                logE(TAG, "Upload failed", e)
                sendJsonResponse(output, 500, "{\"success\":false,\"error\":\"Yuklashda xatolik: ${e.message}\"}")
            }
        } else {
            // Direct Octet-stream streaming
            val filenameHeader = headers["x-filename"] ?: "upload_${System.currentTimeMillis()}.bin"
            val safeName = sanitizeFilename(filenameHeader)
            val destFile = File(targetDir, safeName)

            var written = 0L
            FileOutputStream(destFile).use { fos ->
                val buffer = ByteArray(BUFFER_SIZE)
                var remaining = if (contentLength > 0) contentLength else Long.MAX_VALUE
                while (remaining > 0) {
                    val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
                    val read = input.read(buffer, 0, toRead)
                    if (read <= 0) break
                    fos.write(buffer, 0, read)
                    remaining -= read
                    written += read
                    totalBytesUploaded.addAndGet(read.toLong())
                    notifyStats()
                }
            }

            sendJsonResponse(output, 200, "{\"success\":true,\"filename\":\"$safeName\",\"bytes\":$written}")
            logRequest(clientIp, "POST", "/api/upload ($safeName)", 200, written)
        }
    }

    private fun extractBoundary(contentType: String): String? {
        val parts = contentType.split(";")
        for (p in parts) {
            val trimmed = p.trim()
            if (trimmed.startsWith("boundary=", ignoreCase = true)) {
                var b = trimmed.substring(9).trim()
                if (b.startsWith("\"") && b.endsWith("\"")) {
                    b = b.substring(1, b.length - 1)
                }
                return b
            }
        }
        return null
    }

    /**
     * Efficient streaming multipart parser that writes straight to disk without RAM buffering.
     */
    private fun parseMultipartAndSave(
        input: InputStream,
        boundary: String,
        targetDir: File,
        totalContentLength: Long
    ): String? {
        val boundaryBytes = "--$boundary".toByteArray(StandardCharsets.US_ASCII)
        val crlf = "\r\n".toByteArray(StandardCharsets.US_ASCII)

        // Read headers of the first part
        val partHeaders = readPartHeaders(input)
        if (partHeaders.isEmpty()) return null

        val contentDisp = partHeaders["content-disposition"] ?: ""
        var filename = extractFilenameFromContentDisposition(contentDisp)
        if (filename.isNullOrBlank()) {
            filename = "uploaded_${System.currentTimeMillis()}.bin"
        }
        val safeName = sanitizeFilename(filename)
        val destFile = File(targetDir, safeName)

        // Stream part body until next boundary
        var bytesUploadedForFile = 0L
        FileOutputStream(destFile).use { fos ->
            val buffer = ByteArray(BUFFER_SIZE)
            var matchIdx = 0
            val matchTarget = ("\r\n--" + boundary).toByteArray(StandardCharsets.US_ASCII)
            var b: Int

            while (input.read().also { b = it } != -1) {
                if (b == matchTarget[matchIdx].toInt()) {
                    matchIdx++
                    if (matchIdx == matchTarget.size) {
                        // Reached ending boundary!
                        break
                    }
                } else {
                    if (matchIdx > 0) {
                        // Write previously matched tentative bytes
                        fos.write(matchTarget, 0, matchIdx)
                        bytesUploadedForFile += matchIdx
                        totalBytesUploaded.addAndGet(matchIdx.toLong())
                        matchIdx = 0
                        // Re-check current byte
                        if (b == matchTarget[0].toInt()) {
                            matchIdx = 1
                            continue
                        }
                    }
                    fos.write(b)
                    bytesUploadedForFile++
                    totalBytesUploaded.incrementAndGet()
                    if (bytesUploadedForFile % (64 * 1024) == 0L) {
                        notifyStats()
                    }
                }
            }
            fos.flush()
        }

        notifyStats()
        return safeName
    }

    private fun readPartHeaders(input: InputStream): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        val line = StringBuilder()
        var lastChar = 0

        while (true) {
            val c = input.read()
            if (c == -1) break
            if (c == '\n'.code && lastChar == '\r'.code) {
                val headerLine = line.toString().trim()
                if (headerLine.isEmpty()) {
                    // End of headers
                    break
                }
                val colon = headerLine.indexOf(':')
                if (colon > 0) {
                    val key = headerLine.substring(0, colon).trim().lowercase(Locale.US)
                    val value = headerLine.substring(colon + 1).trim()
                    headers[key] = value
                }
                line.setLength(0)
            } else if (c != '\r'.code) {
                line.append(c.toChar())
            }
            lastChar = c
        }
        return headers
    }

    private fun extractFilenameFromContentDisposition(disp: String): String? {
        val parts = disp.split(";")
        for (p in parts) {
            val trimmed = p.trim()
            if (trimmed.startsWith("filename=", ignoreCase = true)) {
                var fn = trimmed.substring(9).trim()
                if (fn.startsWith("\"") && fn.endsWith("\"")) {
                    fn = fn.substring(1, fn.length - 1)
                }
                return File(fn).name
            }
        }
        return null
    }

    private fun handleStorageInfo(output: OutputStream, clientIp: String) {
        val (availableBytes, totalBytes) = try {
            val stat = StatFs(sharedDirectory.absolutePath)
            Pair(stat.availableBlocksLong * stat.blockSizeLong, stat.blockCountLong * stat.blockSizeLong)
        } catch (e: Throwable) {
            Pair(sharedDirectory.freeSpace.coerceAtLeast(0L), sharedDirectory.totalSpace.coerceAtLeast(0L))
        }
        val json = "{\"availableBytes\":$availableBytes,\"totalBytes\":$totalBytes,\"sharedPath\":\"${sharedDirectory.absolutePath}\"}"
        sendJsonResponse(output, 200, json)
        logRequest(clientIp, "GET", "/api/storage-info", 200, json.length.toLong())
    }

    /**
     * Path Traversal Defense & Canonical Validation.
     * Verifies that the resolved file is strictly located inside [sharedDirectory].
     */
    fun resolveSafeFile(relativePath: String): File? {
        val cleanPath = relativePath.trim()
            .replace("\\", "/")
            .replace("\u0000", "")

        // Reject obvious traversal patterns
        if (cleanPath.contains("/../") || cleanPath.startsWith("../") || cleanPath.endsWith("/..") || cleanPath == "..") {
            return null
        }

        val baseCanonical = try {
            sharedDirectory.canonicalFile
        } catch (e: Exception) {
            sharedDirectory.absoluteFile
        }

        val subPath = if (cleanPath.startsWith("/")) cleanPath.substring(1) else cleanPath
        val target = File(baseCanonical, subPath)

        val targetCanonical = try {
            target.canonicalFile
        } catch (e: Exception) {
            return null
        }

        // Must start with base canonical path
        val basePathStr = baseCanonical.absolutePath
        val targetPathStr = targetCanonical.absolutePath

        if (targetPathStr == basePathStr || targetPathStr.startsWith(basePathStr + File.separator)) {
            return targetCanonical
        }

        return null
    }

    private fun getRelativePath(file: File): String {
        val baseCanonical = sharedDirectory.canonicalPath
        val fileCanonical = file.canonicalPath
        return if (fileCanonical.startsWith(baseCanonical)) {
            val rel = fileCanonical.substring(baseCanonical.length).replace("\\", "/")
            if (rel.isEmpty()) "/" else rel
        } else {
            "/"
        }
    }

    private fun sanitizeFilename(raw: String): String {
        val name = File(raw).name
        return name.replace(Regex("[^a-zA-Z0-9._\\- ()]"), "_")
            .ifEmpty { "file_${System.currentTimeMillis()}.bin" }
    }

    private fun sanitizeForHeader(str: String): String {
        return str.replace("\"", "").replace("\r", "").replace("\n", "")
    }

    private fun readHeaderLines(input: InputStream): List<String> {
        val lines = mutableListOf<String>()
        val sb = StringBuilder()
        var last = 0

        while (true) {
            val b = input.read()
            if (b == -1) break
            if (b == '\n'.code && last == '\r'.code) {
                val line = sb.toString().trim()
                if (line.isEmpty()) {
                    break // end of HTTP headers
                }
                lines.add(line)
                sb.setLength(0)
            } else if (b != '\r'.code) {
                sb.append(b.toChar())
            }
            last = b
            if (lines.size > 100 || sb.length > 8192) {
                break // prevent DoS with huge headers
            }
        }
        return lines
    }

    private fun parseQueryParams(queryString: String?): Map<String, String> {
        if (queryString.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        val pairs = queryString.split("&")
        for (p in pairs) {
            val idx = p.indexOf('=')
            if (idx > 0) {
                val key = try { URLDecoder.decode(p.substring(0, idx), "UTF-8") } catch (e: Exception) { p.substring(0, idx) }
                val value = try { URLDecoder.decode(p.substring(idx + 1), "UTF-8") } catch (e: Exception) { p.substring(idx + 1) }
                result[key] = value
            } else if (p.isNotEmpty()) {
                val key = try { URLDecoder.decode(p, "UTF-8") } catch (e: Exception) { p }
                result[key] = ""
            }
        }
        return result
    }

    private fun sendSimpleResponse(output: OutputStream, statusCode: Int, statusText: String, message: String) {
        val body = "<!DOCTYPE html><html><head><meta charset='utf-8'><title>$statusCode $statusText</title>" +
                "<style>body{font-family:sans-serif;background:#0f172a;color:#f8fafc;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;}" +
                ".card{background:#1e293b;padding:32px;border-radius:16px;border:1px solid #334155;max-width:480px;text-align:center;}" +
                "h1{color:#ef4444;margin-top:0;}p{color:#94a3b8;line-height:1.6;}</style></head>" +
                "<body><div class='card'><h1>$statusCode $statusText</h1><p>$message</p><p><a href='/' style='color:#38bdf8'>Bosh sahifaga qaytish</a></p></div></body></html>"
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendHtmlResponse(output: OutputStream, statusCode: Int, html: String) {
        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 $statusCode OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: keep-alive\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendJsonResponse(output: OutputStream, statusCode: Int, json: String) {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 $statusCode OK\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendOptionsResponse(output: OutputStream) {
        val header = "HTTP/1.1 204 No Content\r\n" +
                "Allow: GET, HEAD, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type, Range, Authorization\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    private fun serveLoginPage(output: OutputStream, clientIp: String) {
        val html = buildLoginPage()
        sendHtmlResponse(output, 401, html)
    }

    private fun notifyStats() {
        onStatsUpdated?.invoke(
            activeClients.get(),
            totalBytesDownloaded.get(),
            totalBytesUploaded.get()
        )
    }

    private fun logRequest(clientIp: String, method: String, path: String, status: Int, bytes: Long) {
        onLog?.invoke(
            LanAccessLog(
                clientIp = clientIp,
                method = method,
                path = path,
                statusCode = status,
                bytesTransferred = bytes
            )
        )
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[index])
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        return when (ext) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "pdf" -> "application/pdf"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "tar" -> "application/x-tar"
            "gz", "gzip" -> "application/gzip"
            "txt", "log", "md" -> "text/plain"
            "sh" -> "text/x-shellscript"
            "py" -> "text/x-python"
            else -> "application/octet-stream"
        }
    }

    // =========================================================================
    // RESPONSIVE ONEUI 8.5 OFFLINE WEB UI TEMPLATES
    // =========================================================================

    private fun buildLoginPage(errorMessage: String? = null): String {
        val errorBlock = if (errorMessage != null) {
            "<div class='error-banner'><svg viewBox='0 0 24 24' width='18' height='18' fill='none' stroke='currentColor' stroke-width='2'><circle cx='12' cy='12' r='10'></circle><line x1='12' y1='8' x2='12' y2='12'></line><line x1='12' y1='16' x2='12.01' y2='16'></line></svg>$errorMessage</div>"
        } else ""

        return """
<!DOCTYPE html>
<html lang="uz">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
    <title>Super Terminal - Xavfsiz Kirish</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            background: #090B0E;
            color: #F8FAFC;
            display: flex;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            padding: 16px;
        }
        .login-card {
            background: rgba(30, 41, 59, 0.7);
            backdrop-filter: blur(20px);
            -webkit-backdrop-filter: blur(20px);
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: 28px;
            padding: 36px 28px;
            width: 100%;
            max-width: 400px;
            box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7);
            text-align: center;
        }
        .icon-circle {
            width: 68px;
            height: 68px;
            border-radius: 22px;
            background: linear-gradient(135deg, #0284C7, #0369A1);
            display: flex;
            align-items: center;
            justify-content: center;
            margin: 0 auto 20px auto;
            box-shadow: 0 10px 25px -5px rgba(2, 132, 199, 0.5);
        }
        h2 { font-size: 22px; font-weight: 700; margin-bottom: 6px; }
        p.subtitle { font-size: 14px; color: #94A3B8; margin-bottom: 24px; line-height: 1.5; }
        .error-banner {
            background: rgba(239, 68, 68, 0.15);
            border: 1px solid rgba(239, 68, 68, 0.3);
            color: #FCA5A5;
            padding: 12px 14px;
            border-radius: 14px;
            font-size: 13px;
            margin-bottom: 18px;
            display: flex;
            align-items: center;
            gap: 8px;
            text-align: left;
        }
        .input-group {
            margin-bottom: 20px;
            text-align: left;
        }
        label { display: block; font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; color: #94A3B8; margin-bottom: 8px; }
        input[type="password"], input[type="text"] {
            width: 100%;
            background: rgba(15, 23, 42, 0.8);
            border: 1px solid rgba(255, 255, 255, 0.15);
            border-radius: 16px;
            padding: 14px 16px;
            font-size: 16px;
            color: #FFFFFF;
            font-family: monospace;
            letter-spacing: 2px;
            outline: none;
            transition: all 0.2s;
            text-align: center;
        }
        input:focus {
            border-color: #38BDF8;
            box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.25);
        }
        button.btn-login {
            width: 100%;
            background: linear-gradient(135deg, #0284C7, #0369A1);
            color: white;
            border: none;
            border-radius: 16px;
            padding: 15px;
            font-size: 16px;
            font-weight: 600;
            cursor: pointer;
            transition: transform 0.1s, opacity 0.2s;
            box-shadow: 0 10px 20px -5px rgba(2, 132, 199, 0.4);
        }
        button.btn-login:active { transform: scale(0.98); }
        .footer-note { font-size: 12px; color: #64748B; margin-top: 24px; }
    </style>
</head>
<body>
    <div class="login-card">
        <div class="icon-circle">
            <svg viewBox="0 0 24 24" width="34" height="34" fill="none" stroke="white" stroke-width="2">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
            </svg>
        </div>
        <h2>Super Terminal LAN Server</h2>
        <p class="subtitle">Ushbu server xavfsiz himoyalangan. Fayllarga kirish uchun ilovadagi Kirish Kodini (Token) kiriting.</p>
        $errorBlock
        <form method="POST" action="/api/login">
            <div class="input-group">
                <label for="token">KIRISH TOKENI / KODI</label>
                <input type="text" id="token" name="token" placeholder="••••-••••-••••-••••" autocomplete="off" autofocus required />
            </div>
            <button type="submit" class="btn-login">Tizimga Kirish</button>
        </form>
        <p class="footer-note">Super Terminal Manager &bull; Xavfsiz LAN File Server</p>
    </div>
</body>
</html>
        """.trimIndent()
    }

    private fun buildDirectoryListingHtml(currentDir: File, relativeDir: String): String {
        val files = currentDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) })) ?: emptyList()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        val breadcrumbsHtml = buildBreadcrumbs(relativeDir)

        val fileRows = StringBuilder()
        for (f in files) {
            val isDir = f.isDirectory
            val name = f.name
            val safeNameHtml = escapeHtml(name)
            val subPath = if (relativeDir == "/" || relativeDir.isEmpty()) "/$name" else "$relativeDir/$name"
            val encodedSubPath = URLEncoder.encode(subPath, "UTF-8")
            val sizeStr = if (isDir) "${(f.listFiles()?.size ?: 0)} element" else formatSize(f.length())
            val dateStr = sdf.format(Date(f.lastModified()))

            val iconSvg = if (isDir) {
                "<svg viewBox='0 0 24 24' width='22' height='22' fill='#F59E0B'><path d='M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z'/></svg>"
            } else {
                getFileIconSvg(name)
            }

            val actionBtn = if (isDir) {
                "<a class='btn-action btn-open' href='$subPath'>Ochish</a>"
            } else {
                "<a class='btn-action btn-download' href='/api/download?path=$encodedSubPath' download='$safeNameHtml'>Yuklab olish</a>"
            }

            val linkUrl = if (isDir) subPath else "/api/download?path=$encodedSubPath"

            fileRows.append("""
                <tr class="file-row" data-name="${safeNameHtml.lowercase(Locale.ROOT)}">
                    <td class="col-icon">$iconSvg</td>
                    <td class="col-name">
                        <a href="$linkUrl" class="file-link ${if (isDir) "is-dir" else ""}">$safeNameHtml</a>
                    </td>
                    <td class="col-size">$sizeStr</td>
                    <td class="col-date">$dateStr</td>
                    <td class="col-actions">$actionBtn</td>
                </tr>
            """.trimIndent())
        }

        if (files.isEmpty()) {
            fileRows.append("""
                <tr>
                    <td colspan="5" class="empty-state">
                        <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#64748B" stroke-width="1.5">
                            <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
                            <circle cx="8.5" cy="8.5" r="1.5"></circle>
                            <polyline points="21 15 16 10 5 21"></polyline>
                        </svg>
                        <p>Ushbu papka bo'sh</p>
                    </td>
                </tr>
            """.trimIndent())
        }

        val (freeBytes, totalBytes) = try {
            val stat = StatFs(sharedDirectory.absolutePath)
            Pair(stat.availableBlocksLong * stat.blockSizeLong, stat.blockCountLong * stat.blockSizeLong)
        } catch (e: Throwable) {
            Pair(sharedDirectory.freeSpace.coerceAtLeast(0L), sharedDirectory.totalSpace.coerceAtLeast(0L))
        }
        val freeStr = formatSize(freeBytes)
        val totalStr = formatSize(totalBytes)

        return """
<!DOCTYPE html>
<html lang="uz">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Super Terminal LAN File Server</title>
    <style>
        :root {
            --bg-color: #090B0E;
            --surface-color: rgba(30, 41, 59, 0.6);
            --surface-hover: rgba(51, 65, 85, 0.7);
            --border-color: rgba(255, 255, 255, 0.1);
            --text-primary: #F8FAFC;
            --text-secondary: #94A3B8;
            --accent-primary: #38BDF8;
            --accent-gradient: linear-gradient(135deg, #0284C7, #0369A1);
            --card-radius: 20px;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            background: var(--bg-color);
            color: var(--text-primary);
            min-height: 100vh;
            padding: 24px 16px;
        }
        .container {
            max-width: 1080px;
            margin: 0 auto;
        }
        /* Top Navigation Header */
        header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 24px;
            background: var(--surface-color);
            backdrop-filter: blur(20px);
            border: 1px solid var(--border-color);
            padding: 16px 24px;
            border-radius: var(--card-radius);
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
            flex-wrap: wrap;
            gap: 16px;
        }
        .brand {
            display: flex;
            align-items: center;
            gap: 14px;
        }
        .brand-icon {
            width: 44px;
            height: 44px;
            border-radius: 14px;
            background: var(--accent-gradient);
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 4px 12px rgba(2, 132, 199, 0.4);
        }
        .brand-text h1 {
            font-size: 18px;
            font-weight: 700;
        }
        .brand-text p {
            font-size: 12px;
            color: var(--text-secondary);
        }
        .header-actions {
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .btn-logout {
            background: rgba(239, 68, 68, 0.15);
            border: 1px solid rgba(239, 68, 68, 0.3);
            color: #F87171;
            padding: 8px 16px;
            border-radius: 12px;
            font-size: 13px;
            font-weight: 600;
            text-decoration: none;
            transition: all 0.2s;
        }
        .btn-logout:hover { background: rgba(239, 68, 68, 0.25); }

        /* Breadcrumbs bar */
        .breadcrumbs-bar {
            display: flex;
            align-items: center;
            padding: 12px 20px;
            background: rgba(15, 23, 42, 0.6);
            border: 1px solid var(--border-color);
            border-radius: 14px;
            margin-bottom: 20px;
            overflow-x: auto;
            white-space: nowrap;
            gap: 8px;
            font-size: 14px;
        }
        .breadcrumb-item {
            color: var(--accent-primary);
            text-decoration: none;
            font-weight: 500;
        }
        .breadcrumb-item:hover { text-decoration: underline; }
        .breadcrumb-separator { color: var(--text-secondary); }
        .breadcrumb-current { color: var(--text-primary); font-weight: 600; }

        /* Upload Card */
        .upload-section {
            background: var(--surface-color);
            backdrop-filter: blur(20px);
            border: 1px dashed rgba(56, 189, 248, 0.4);
            border-radius: var(--card-radius);
            padding: 24px;
            margin-bottom: 24px;
            text-align: center;
            transition: all 0.2s;
            cursor: pointer;
        }
        .upload-section.dragover {
            border-color: #38BDF8;
            background: rgba(56, 189, 248, 0.1);
        }
        .upload-content {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 10px;
        }
        .upload-btn {
            background: var(--accent-gradient);
            color: white;
            border: none;
            border-radius: 12px;
            padding: 10px 20px;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
            margin-top: 8px;
        }
        #uploadProgressContainer {
            display: none;
            width: 100%;
            margin-top: 16px;
        }
        .progress-bar-bg {
            background: rgba(15, 23, 42, 0.8);
            border-radius: 10px;
            height: 12px;
            width: 100%;
            overflow: hidden;
            border: 1px solid var(--border-color);
        }
        .progress-bar-fill {
            background: #10B981;
            height: 100%;
            width: 0%;
            transition: width 0.15s ease;
        }
        .progress-label {
            display: flex;
            justify-content: space-between;
            font-size: 12px;
            color: var(--text-secondary);
            margin-top: 6px;
        }

        /* Filter Search Bar */
        .search-bar {
            display: flex;
            align-items: center;
            background: rgba(15, 23, 42, 0.8);
            border: 1px solid var(--border-color);
            border-radius: 14px;
            padding: 10px 16px;
            margin-bottom: 16px;
            gap: 10px;
        }
        .search-bar input {
            background: transparent;
            border: none;
            color: white;
            font-size: 14px;
            width: 100%;
            outline: none;
        }

        /* Files Table */
        .table-card {
            background: var(--surface-color);
            backdrop-filter: blur(20px);
            border: 1px solid var(--border-color);
            border-radius: var(--card-radius);
            overflow: hidden;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
            margin-bottom: 24px;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            text-align: left;
        }
        th {
            background: rgba(15, 23, 42, 0.7);
            padding: 14px 18px;
            font-size: 12px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            color: var(--text-secondary);
            border-bottom: 1px solid var(--border-color);
        }
        td {
            padding: 14px 18px;
            font-size: 14px;
            border-bottom: 1px solid rgba(255, 255, 255, 0.05);
            vertical-align: middle;
        }
        tr.file-row:hover { background: var(--surface-hover); }
        .col-icon { width: 36px; padding-right: 0; }
        .col-name { font-weight: 500; }
        .file-link {
            color: var(--text-primary);
            text-decoration: none;
            word-break: break-all;
        }
        .file-link.is-dir { color: #F59E0B; font-weight: 600; }
        .file-link:hover { color: var(--accent-primary); }
        .col-size { color: var(--text-secondary); white-space: nowrap; width: 110px; }
        .col-date { color: var(--text-secondary); white-space: nowrap; width: 150px; }
        .col-actions { text-align: right; width: 130px; }

        .btn-action {
            display: inline-block;
            padding: 6px 14px;
            border-radius: 10px;
            font-size: 12px;
            font-weight: 600;
            text-decoration: none;
            transition: all 0.2s;
        }
        .btn-open {
            background: rgba(245, 158, 11, 0.15);
            border: 1px solid rgba(245, 158, 11, 0.3);
            color: #FBBF24;
        }
        .btn-open:hover { background: rgba(245, 158, 11, 0.3); }
        .btn-download {
            background: rgba(56, 189, 248, 0.15);
            border: 1px solid rgba(56, 189, 248, 0.3);
            color: #38BDF8;
        }
        .btn-download:hover { background: rgba(56, 189, 248, 0.3); }

        .empty-state {
            text-align: center;
            padding: 48px 16px;
            color: var(--text-secondary);
        }
        .empty-state p { margin-top: 12px; }

        /* Footer */
        footer {
            display: flex;
            justify-content: space-between;
            align-items: center;
            font-size: 13px;
            color: var(--text-secondary);
            padding: 0 8px;
            flex-wrap: wrap;
            gap: 10px;
        }

        @media (max-width: 640px) {
            .col-date { display: none; }
            .col-size { display: none; }
        }
    </style>
</head>
<body>
    <div class="container">
        <!-- Header -->
        <header>
            <div class="brand">
                <div class="brand-icon">
                    <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="white" stroke-width="2">
                        <rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect>
                        <line x1="8" y1="21" x2="16" y2="21"></line>
                        <line x1="12" y1="17" x2="12" y2="21"></line>
                    </svg>
                </div>
                <div class="brand-text">
                    <h1>Super Terminal LAN File Server</h1>
                    <p>Android Qurilmasidagi Fayllarni Tezkor Ulashish</p>
                </div>
            </div>
            <div class="header-actions">
                <a href="/api/logout" class="btn-logout">Chiqish</a>
            </div>
        </header>

        <!-- Breadcrumbs -->
        <div class="breadcrumbs-bar">
            $breadcrumbsHtml
        </div>

        <!-- File Upload Section -->
        <div class="upload-section" id="dropZone">
            <input type="file" id="fileInput" multiple style="display: none;" />
            <div class="upload-content">
                <svg viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="#38BDF8" stroke-width="2">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                    <polyline points="17 8 12 3 7 8"></polyline>
                    <line x1="12" y1="3" x2="12" y2="15"></line>
                </svg>
                <p><strong>Faylni shu yerga tashlang</strong> yoki kompyuterdan tanlang</p>
                <button type="button" class="upload-btn" onclick="document.getElementById('fileInput').click()">Fayl tanlash</button>
            </div>
            <div id="uploadProgressContainer">
                <div class="progress-bar-bg">
                    <div class="progress-bar-fill" id="progressBar"></div>
                </div>
                <div class="progress-label">
                    <span id="progressText">0%</span>
                    <span id="speedText">Yuklanmoqda...</span>
                </div>
            </div>
        </div>

        <!-- Filter Search Bar -->
        <div class="search-bar">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#64748B" stroke-width="2">
                <circle cx="11" cy="11" r="8"></circle>
                <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
            </svg>
            <input type="text" id="searchInput" placeholder="Fayllar orasidan qidirish..." oninput="filterFiles()" />
        </div>

        <!-- Files Table -->
        <div class="table-card">
            <table>
                <thead>
                    <tr>
                        <th class="col-icon"></th>
                        <th class="col-name">Nomi</th>
                        <th class="col-size">Hajmi</th>
                        <th class="col-date">Sana</th>
                        <th class="col-actions">Harakat</th>
                    </tr>
                </thead>
                <tbody id="fileTableBody">
                    $fileRows
                </tbody>
            </table>
        </div>

        <!-- Footer -->
        <footer>
            <span>Xotira: $freeStr bo'sh (Jami: $totalStr)</span>
            <span>Super Terminal Manager &bull; HTTP 1.1 Local LAN Server</span>
        </footer>
    </div>

    <script>
        // Search Filter
        function filterFiles() {
            const query = document.getElementById('searchInput').value.toLowerCase();
            const rows = document.querySelectorAll('.file-row');
            rows.forEach(r => {
                const name = r.getAttribute('data-name');
                if (!query || name.includes(query)) {
                    r.style.display = '';
                } else {
                    r.style.display = 'none';
                }
            });
        }

        // Drag and Drop Upload
        const dropZone = document.getElementById('dropZone');
        const fileInput = document.getElementById('fileInput');

        ['dragenter', 'dragover'].forEach(eventName => {
            dropZone.addEventListener(eventName, (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropZone.classList.add('dragover');
            }, false);
        });

        ['dragleave', 'drop'].forEach(eventName => {
            dropZone.addEventListener(eventName, (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropZone.classList.remove('dragover');
            }, false);
        });

        dropZone.addEventListener('drop', (e) => {
            const dt = e.dataTransfer;
            const files = dt.files;
            handleFiles(files);
        });

        fileInput.addEventListener('change', (e) => {
            handleFiles(e.target.files);
        });

        function handleFiles(files) {
            if (!files || files.length === 0) return;
            uploadFiles(files);
        }

        function uploadFiles(files) {
            const currentDir = '$relativeDir';
            const progressContainer = document.getElementById('uploadProgressContainer');
            const progressBar = document.getElementById('progressBar');
            const progressText = document.getElementById('progressText');
            const speedText = document.getElementById('speedText');

            progressContainer.style.display = 'block';

            let index = 0;
            function uploadNext() {
                if (index >= files.length) {
                    progressText.innerText = "Barcha fayllar muvaffaqiyatli yuklandi!";
                    setTimeout(() => { window.location.reload(); }, 800);
                    return;
                }

                const file = files[index];
                const formData = new FormData();
                formData.append('file', file);

                const xhr = new XMLHttpRequest();
                const url = '/api/upload?dir=' + encodeURIComponent(currentDir);
                xhr.open('POST', url, true);

                let startTime = Date.now();
                xhr.upload.onprogress = function(e) {
                    if (e.lengthComputable) {
                        const percent = Math.round((e.loaded / e.total) * 100);
                        progressBar.style.width = percent + '%';
                        const elapsed = (Date.now() - startTime) / 1000;
                        const speed = elapsed > 0 ? (e.loaded / elapsed / 1024 / 1024).toFixed(1) : 0;
                        progressText.innerText = "Yuklanmoqda (" + (index + 1) + "/" + files.length + "): " + file.name + " - " + percent + "%";
                        speedText.innerText = speed + " MB/s";
                    }
                };

                xhr.onload = function() {
                    if (xhr.status === 200) {
                        index++;
                        uploadNext();
                    } else {
                        alert("Yuklashda xatolik yuz berdi: " + xhr.responseText);
                        progressContainer.style.display = 'none';
                    }
                };

                xhr.onerror = function() {
                    alert("Tarmoq xatoligi yuz berdi!");
                    progressContainer.style.display = 'none';
                };

                xhr.send(formData);
            }

            uploadNext();
        }
    </script>
</body>
</html>
        """.trimIndent()
    }

    private fun buildBreadcrumbs(relativeDir: String): String {
        val sb = StringBuilder()
        sb.append("<a href='/' class='breadcrumb-item'>🏠 Asosiy</a>")

        if (relativeDir == "/" || relativeDir.isEmpty()) {
            return sb.toString()
        }

        val parts = relativeDir.split("/").filter { it.isNotEmpty() }
        var currentAccPath = ""
        for (i in parts.indices) {
            val p = parts[i]
            currentAccPath += "/$p"
            sb.append("<span class='breadcrumb-separator'>/</span>")
            if (i == parts.size - 1) {
                sb.append("<span class='breadcrumb-current'>${escapeHtml(p)}</span>")
            } else {
                sb.append("<a href='$currentAccPath' class='breadcrumb-item'>${escapeHtml(p)}</a>")
            }
        }
        return sb.toString()
    }

    private fun getFileIconSvg(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "zip", "tar", "gz", "rar", "7z", "bz2", "xz" ->
                "<svg viewBox='0 0 24 24' width='22' height='22' fill='#EC4899'><path d='M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-6 10h-4v-2h4v2zm0-4h-4v-2h4v2z'/></svg>"
            "apk" ->
                "<svg viewBox='0 0 24 24' width='22' height='22' fill='#10B981'><path d='M6 18c0 .55.45 1 1 1h1v3.5c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5V19h2v3.5c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5V19h1c.55 0 1-.45 1-1V8H6v10zM3.5 8C2.67 8 2 8.67 2 9.5v7c0 .83.67 1.5 1.5 1.5S5 17.33 5 16.5v-7C5 8.67 4.33 8 3.5 8zm17 0c-.83 0-1.5.67-1.5 1.5v7c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5v-7c0-.83-.67-1.5-1.5-1.5zm-4.97-4.84l1.3-1.3c.2-.2.2-.51 0-.71-.2-.2-.51-.2-.71 0l-1.48 1.48C13.85 2.23 12.95 2 12 2c-.96 0-1.86.23-2.66.63L7.85 1.14c-.2-.2-.51-.2-.71 0-.2.2-.2.51 0 .71l1.31 1.31C6.97 4.26 6 5.76 6 7.5h12c0-1.74-.97-3.24-2.47-4.34zM10 5.5c-.41 0-.75-.34-.75-.75s.34-.75.75-.75.75.34.75.75-.34.75-.75.75zm4 0c-.41 0-.75-.34-.75-.75s.34-.75.75-.75.75.34.75.75-.34.75-.75.75z'/></svg>"
            "jpg", "jpeg", "png", "webp", "gif", "svg" ->
                "<svg viewBox='0 0 24 24' width='22' height='22' fill='#8B5CF6'><path d='M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z'/></svg>"
            "mp4", "mkv", "webm", "avi", "mov" ->
                "<svg viewBox='0 0 24 24' width='22' height='22' fill='#EF4444'><path d='M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z'/></svg>"
            "mp3", "wav", "flac", "ogg", "m4a" ->
                "<svg viewBox='0 0 24 24' width='22' height='22' fill='#06B6D4'><path d='M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z'/></svg>"
            "pdf" ->
                "<svg viewBox='0 0 24 24' width='22' height='22' fill='#DC2626'><path d='M20 2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-8.5 7.5c0 .83-.67 1.5-1.5 1.5H9v2H7.5V7H10c.83 0 1.5.67 1.5 1.5v1zm5 2c0 .83-.67 1.5-1.5 1.5h-2.5V7H15c.83 0 1.5.67 1.5 1.5v3zm4-3H19v1h1.5V11H19v2h-1.5V7h3v1.5zM9 9.5h1v-1H9v1zM4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm10 5.5h1v-3h-1v3z'/></svg>"
            "sh", "py", "kt", "java", "c", "cpp", "js", "html", "css", "json", "xml" ->
                "<svg viewBox='0 0 24 24' width='22' height='22' fill='#38BDF8'><path d='M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z'/></svg>"
            else ->
                "<svg viewBox='0 0 24 24' width='22' height='22' fill='#94A3B8'><path d='M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z'/></svg>"
        }
    }

    private fun escapeHtml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
