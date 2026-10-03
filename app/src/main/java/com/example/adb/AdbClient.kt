package com.example.adb

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.*
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class AdbClient(context: Context) {

    private val appContext = context.applicationContext

    companion object {
        private const val TAG = "SuperAdbClient"

        // ADB Protocol commands (Little Endian)
        private const val CMD_CNXN = 0x4e584e43 // "CNXN"
        private const val CMD_AUTH = 0x48545541 // "AUTH"
        private const val CMD_OPEN = 0x4e45504f // "OPEN"
        private const val CMD_OKAY = 0x59414b4f // "OKAY"
        private const val CMD_CLSE = 0x45534c43 // "CLSE"
        private const val CMD_WRTE = 0x45545257 // "WRTE"
        private const val CMD_STLS = 0x534c5453 // "STLS" (Android 11+ Wireless Debugging)
        private const val A_STLS_VERSION = 0x01000000

        private const val AUTH_TYPE_TOKEN = 1
        private const val AUTH_TYPE_SIGNATURE = 2
        private const val AUTH_TYPE_RSAPUBLICKEY = 3

        private const val PREFS_NAME = "super_adb_keys"
        private const val KEY_PRIVATE = "private_key"
        private const val KEY_PUBLIC = "public_key"
    }

    private val executeMutex = kotlinx.coroutines.sync.Mutex()
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var _isConnected = false
    private var localChannelId = 1
    private var remoteChannelId = 0

    // Key management
    private var privateKey: PrivateKey? = null
    private var publicKey: PublicKey? = null

    fun loadOrGenerateKeys() {
        synchronized(this) {
            if (privateKey != null && publicKey != null) return

            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val privBase64 = prefs.getString(KEY_PRIVATE, null)
            val pubBase64 = prefs.getString(KEY_PUBLIC, null)

            if (privBase64 != null && pubBase64 != null) {
                try {
                    val keyFactory = KeyFactory.getInstance("RSA")
                    val privBytes = Base64.decode(privBase64, Base64.DEFAULT)
                    val pubBytes = Base64.decode(pubBase64, Base64.DEFAULT)

                    privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                    publicKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))
                    Log.d(TAG, "Mavjud RSA kalit juftligi muvaffaqiyatli yuklandi.")
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "Kalitlarni yuklashda xato, yangisini yaratamiz", e)
                }
            }

            // Generate new keypair
            try {
                val kpg = KeyPairGenerator.getInstance("RSA")
                kpg.initialize(2048)
                val kp = kpg.generateKeyPair()
                privateKey = kp.private
                publicKey = kp.public

                val privStr = Base64.encodeToString(privateKey!!.encoded, Base64.DEFAULT)
                val pubStr = Base64.encodeToString(publicKey!!.encoded, Base64.DEFAULT)

                prefs.edit()
                    .putString(KEY_PRIVATE, privStr)
                    .putString(KEY_PUBLIC, pubStr)
                    .apply()
                Log.d(TAG, "Yangi professional 2048-bitli RSA kalit juftligi yaratildi.")
            } catch (e: Exception) {
                Log.e(TAG, "Kalit generatorida xatolik", e)
            }
        }
    }

    /**
     * Discovers active local network IPv4 addresses (Wi-Fi, Ethernet, Hotspot, etc.)
     * Required for Android 11+ Wireless Debugging because adbd binds to the Wi-Fi interface IP.
     */
    fun getLocalDeviceIpAddresses(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.isNotEmpty() && !ip.startsWith("127.")) {
                            ips.add(ip)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lokal IP larni olishda xato: ${e.message}")
        }
        return ips
    }

    /**
     * Connects to on-device wireless ADB server at port (supports standard 5555 and dynamic Android 11+ Wireless Debugging ports).
     * Automatically attempts connection across candidate hosts (targetHost, active Wi-Fi IPs, and 127.0.0.1).
     * Handshakes using RSA PKI and Android mincrypt wire format.
     */
    suspend fun connect(
        port: Int,
        targetHost: String? = null,
        timeoutMs: Int = 3500,
        onStatusUpdate: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        close()
        // Ensure RSA keys are loaded/generated before attempting connection.
        loadOrGenerateKeys()

        // Build list of candidate hosts to attempt connection
        val candidateHosts = mutableListOf<String>()
        val activeIps = getLocalDeviceIpAddresses()
        if (!targetHost.isNullOrBlank() && targetHost != "127.0.0.1" && targetHost != "localhost") {
            candidateHosts.add(targetHost.trim())
        }
        // Include device's active local Wi-Fi IPs (essential for Android 11+ Wireless Debugging)
        candidateHosts.addAll(activeIps)
        // Include loopback addresses
        candidateHosts.add("127.0.0.1")
        candidateHosts.add("localhost")
        if (!targetHost.isNullOrBlank()) {
            candidateHosts.add(targetHost.trim())
        }

        val uniqueHosts = candidateHosts.distinct()
        var sock: Socket? = null
        var connectedHost = ""
        var lastException: Exception? = null

        try {
            for (attempt in 1..2) {
                for (host in uniqueHosts) {
                    try {
                        onStatusUpdate?.invoke("Ulanish sinab ko'rilmoqda: $host:$port...")
                        val s = Socket()
                        s.keepAlive = true
                        s.tcpNoDelay = true
                        s.reuseAddress = true
                        s.connect(InetSocketAddress(host, port), timeoutMs)
                        s.soTimeout = 25000 // 25 seconds read timeout for user approval
                        sock = s
                        connectedHost = host
                        break
                    } catch (e: Exception) {
                        lastException = e
                    }
                }
                if (sock != null) break
                kotlinx.coroutines.delay(400)
            }

            if (sock == null) {
                throw lastException ?: IOException("Soket ulanishi amalga oshmadi ($uniqueHosts:$port)")
            }

            socket = sock
            inputStream = sock.getInputStream()
            outputStream = sock.getOutputStream()

            // 1. Send CNXN packet
            val version = 0x01000000
            val maxData = 256 * 1024
            val identity = "host::\u0000".toByteArray(Charsets.UTF_8)
            writeMessage(CMD_CNXN, version, maxData, identity)

            // 2. Read Auth Challenge, Connection Response, or TLS Upgrade Request (STLS)
            val response = readMessage()
            if (response.command == CMD_CNXN) {
                _isConnected = true
                return@withContext "Ulandi ($connectedHost:$port)! (Ulanish RSA qabul qilindi)"
            }

            // Android 11+ Wireless Debugging STLS handshake
            if (response.command == CMD_STLS) {
                onStatusUpdate?.invoke("Simsiz nosozliklarni tuzatish: TLS 1.3 shifrlangan kanal ochilmoqda...")
                // Reply with CMD_STLS
                writeMessage(CMD_STLS, A_STLS_VERSION, 0, ByteArray(0))

                val rsaPriv = privateKey ?: throw IOException("RSA maxfiy kaliti topilmadi")
                val rsaPub = publicKey ?: throw IOException("RSA ommaviy kaliti topilmadi")
                val rsaKp = KeyPair(rsaPub, rsaPriv)

                val sslSocket = try {
                    AdbTlsHelper.upgradeToTls(
                        rawSocket = sock,
                        host = connectedHost,
                        port = port,
                        context = appContext,
                        rsaKeyPair = rsaKp
                    )
                } catch (sslEx: javax.net.ssl.SSLException) {
                    throw IOException("TLS ulanishi rad etildi (${sslEx.localizedMessage}). Simsiz nosozliklarni tuzatish sozlamalarida ulanishga ruxsat berilganini tekshiring.")
                }

                socket = sslSocket
                inputStream = sslSocket.inputStream
                outputStream = sslSocket.outputStream

                onStatusUpdate?.invoke("TLS xavfsiz kanali ulandi, ADB ma'lumotlari yuborilmoqda...")
                // Inside the TLS tunnel, re-send CNXN packet
                writeMessage(CMD_CNXN, version, maxData, identity)

                val tlsResp = readMessage()
                if (tlsResp.command == CMD_CNXN) {
                    _isConnected = true
                    return@withContext "Simsiz nosozliklarni tuzatish (TLS 1.3) orqali muvaffaqiyatli ulandi ($connectedHost:$port)!"
                }

                if (tlsResp.command == CMD_AUTH && tlsResp.arg0 == AUTH_TYPE_TOKEN) {
                    val challengeToken = tlsResp.data
                    val signed = signChallenge(challengeToken)
                    if (signed != null) {
                        writeMessage(CMD_AUTH, AUTH_TYPE_SIGNATURE, 0, signed)
                        val secondResp = readMessage()
                        if (secondResp.command == CMD_CNXN) {
                            _isConnected = true
                            return@withContext "Muvaffaqiyatli ulandi ($connectedHost:$port)! (TLS + RSA tasdiqlandi)"
                        }
                        if (secondResp.command == CMD_AUTH) {
                            val pubEncoded = getAdbPublicKeyPayload()
                            writeMessage(CMD_AUTH, AUTH_TYPE_RSAPUBLICKEY, 0, pubEncoded)
                            onStatusUpdate?.invoke("Ekranda chiqqan ruxsat oynasida 'Allow / Har doim ruxsat berish' tugmasini bosing...")
                            val thirdResp = readMessage()
                            if (thirdResp.command == CMD_CNXN) {
                                _isConnected = true
                                return@withContext "Yangi kalit tasdiqlandi va ulandi ($connectedHost:$port)!"
                            }
                        }
                    }
                }

                throw IOException("TLS orqali autentifikatsiya rad etildi (javob: cmd=${tlsResp.command})")
            }

            if (response.command == CMD_AUTH && response.arg0 == AUTH_TYPE_TOKEN) {
                // Sign the token challenge using SHA1withRSA private key
                val challengeToken = response.data
                val signed = signChallenge(challengeToken)
                if (signed == null) {
                    throw IOException("Challenge tokenini imzolashda xatolik yuz berdi.")
                }

                // Send signed signature
                writeMessage(CMD_AUTH, AUTH_TYPE_SIGNATURE, 0, signed)

                // Read next message (usually CNXN on success, or AUTH again for public key request)
                val secondResp = readMessage()
                if (secondResp.command == CMD_CNXN) {
                    _isConnected = true
                    return@withContext "Muvaffaqiyatli ulandi ($connectedHost:$port)! (RSA imzosi tasdiqlandi)"
                }

                if (secondResp.command == CMD_AUTH) {
                    // Send RSA Public Key in Android mincrypt structure format
                    val pubEncoded = getAdbPublicKeyPayload()
                    writeMessage(CMD_AUTH, AUTH_TYPE_RSAPUBLICKEY, 0, pubEncoded)

                    onStatusUpdate?.invoke("Ekranda chiqqan ruxsat oynasida 'Allow / Har doim ruxsat berish' tugmasini bosing...")

                    val thirdResp = readMessage()
                    if (thirdResp.command == CMD_CNXN) {
                        _isConnected = true
                        return@withContext "Yangi RSA kaliti tasdiqlandi va ulandi ($connectedHost:$port)!"
                    }
                }
                throw IOException("ADB autentifikatsiya amalga oshmadi. Ekranda 'Allow Debugging' so'rovini tasdiqlang.")
            }

            throw IOException("Kutilmagan ADB javobi: cmd=${response.command}")
        } catch (e: Exception) {
            close()
            throw IOException("Ulanish amalga oshmadi (${e.localizedMessage}). Simsiz nosozliklarni tuzatish porti to'g'ri yozilganini va yoqilganini tekshiring.")
        }
    }

    /**
     * Executes shell command and gathers entire output.
     */
    suspend fun executeCommand(command: String): String = executeMutex.withLock {
        withContext(Dispatchers.IO) {
            var attempt = 0
            var result = ""
            while (attempt < 2) {
                attempt++
                try {
                    // Check if socket is dead or uninitialized
                    if (!_isConnected || socket == null || socket?.isClosed == true || outputStream == null || inputStream == null) {
                        Log.d(TAG, "executeCommand: Socket state invalid. Attempting silent auto-reconnect...")
                        val prefs = appContext.getSharedPreferences("SuperTerminalPrefs", Context.MODE_PRIVATE)
                        val host = prefs.getString("last_adb_host", "") ?: ""
                        val port = prefs.getInt("last_adb_port", 0)
                        if (host.isNotEmpty() && port > 0) {
                            connect(port, host)
                            if (!_isConnected) throw IOException("Avto-ulanish muvaffaqiyatsiz.")
                        } else {
                            throw IOException("ADB ulanmagan! Avval bog'laning.")
                        }
                    }

                    // 1. Flush any leftover stale bytes in the socket stream buffer to prevent desynchronization
                    val stream = inputStream
                    if (stream != null) {
                        try {
                            val available = stream.available()
                            if (available > 0) {
                                val discardBuf = ByteArray(available)
                                stream.read(discardBuf)
                            }
                        } catch (ignored: Exception) {}
                    }

                    // 2. Open local channel
                    var connectedChannel = false
                    var tryCount = 0
                    
                    while (!connectedChannel && tryCount < 4) {
                        tryCount++
                        val payload = when (tryCount) {
                            1 -> "shell:$command\u0000".toByteArray(Charsets.UTF_8)
                            2 -> "exec:$command\u0000".toByteArray(Charsets.UTF_8)
                            3 -> "shell:$command".toByteArray(Charsets.UTF_8)
                            else -> "exec:$command".toByteArray(Charsets.UTF_8)
                        }
                        
                        localChannelId++
                        writeMessage(CMD_OPEN, localChannelId, 0, payload)
                        
                        var resp = readMessage()
                        while (resp.command != CMD_OKAY && resp.command != CMD_CLSE) {
                            resp = readMessage()
                        }
                        
                        if (resp.command == CMD_OKAY) {
                            remoteChannelId = resp.arg0
                            connectedChannel = true
                        }
                    }

                    if (!connectedChannel) {
                        return@withContext "Xatolik: ADB shell kanali ochilmadi."
                    }

                    val outputBuilder = StringBuilder()
                    var finished = false

                    while (!finished) {
                        val dataResp = readMessage()
                        if (dataResp.command == CMD_WRTE) {
                            val textOutput = String(dataResp.data, Charsets.UTF_8)
                            outputBuilder.append(textOutput)
                            writeMessage(CMD_OKAY, localChannelId, remoteChannelId, ByteArray(0))
                        } else if (dataResp.command == CMD_CLSE) {
                            writeMessage(CMD_CLSE, localChannelId, remoteChannelId, ByteArray(0))
                            finished = true
                        }
                    }
                    
                    result = outputBuilder.toString()
                    break
                    
                } catch (e: Exception) {
                    close()
                    if (attempt >= 2) {
                        throw IOException("Buyruqni bajarishda tarmoq xatoligi: ${e.localizedMessage}")
                    }
                }
            }
            return@withContext result
        }
    }

    interface AdbInteractiveChannel : AutoCloseable {
        val localId: Int
        val remoteId: Int
        val isOpen: Boolean
        fun send(text: String)
    }

    suspend fun openInteractiveChannel(): AdbInteractiveChannel? = executeMutex.withLock {
        withContext(Dispatchers.IO) {
            if (!_isConnected || socket == null || socket?.isClosed == true) return@withContext null
            try {
                localChannelId++
                val myLocalId = localChannelId
                writeMessage(CMD_OPEN, myLocalId, 0, "shell:\u0000".toByteArray(Charsets.UTF_8))
                var resp = readMessage()
                while (resp.command != CMD_OKAY && resp.command != CMD_CLSE) {
                    resp = readMessage()
                }
                if (resp.command != CMD_OKAY) return@withContext null
                val myRemoteId = resp.arg0
                val channel = object : AdbInteractiveChannel {
                    private val alive = java.util.concurrent.atomic.AtomicBoolean(true)
                    override val localId: Int = myLocalId
                    override val remoteId: Int = myRemoteId
                    override val isOpen: Boolean get() = alive.get() && isConnected()

                    override fun send(text: String) {
                        if (!isOpen) return
                        try {
                            writeMessage(CMD_WRTE, localId, remoteId, text.toByteArray(Charsets.UTF_8))
                        } catch (e: Exception) {
                            alive.set(false)
                        }
                    }

                    override fun close() {
                        if (alive.compareAndSet(true, false)) {
                            try {
                                writeMessage(CMD_CLSE, localId, remoteId, ByteArray(0))
                            } catch (_: Exception) {}
                        }
                    }
                }
                return@withContext channel
            } catch (e: Exception) {
                null
            }
        }
    }

    fun isConnected(): Boolean {
        val s = socket
        return _isConnected && s != null && s.isConnected && !s.isClosed
    }

    fun close() {
        try {
            socket?.close()
        } catch (e: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
        _isConnected = false
    }

    // ADB Protocol Packet Writing
    private fun writeMessage(cmd: Int, arg0: Int, arg1: Int, data: ByteArray) {
        val out = outputStream ?: throw IOException("Output stream is null")
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        header.clear() // Explicitly clear/reset buffer before filling
        header.putInt(cmd)
        header.putInt(arg0)
        header.putInt(arg1)
        header.putInt(data.size)
        header.putInt(calculateChecksum(data))
        header.putInt(cmd xor -1)

        // Verify position is correct
        if (header.position() != 24) {
            header.flip() // Fallback to correct limit/position
        }

        out.write(header.array())
        if (data.isNotEmpty()) {
            out.write(data)
        }
        out.flush()
    }

    // ADB Protocol Packet Reading
    private suspend fun readMessage(): AdbPacket {
        val stream = inputStream ?: throw IOException("Input stream is null")
        val headerBytes = ByteArray(24)
        var totalRead = 0
        var zeroReadCount = 0
        while (totalRead < 24) {
            val read = stream.read(headerBytes, totalRead, 24 - totalRead)
            if (read <= 0) {
                if (read < 0) throw IOException("ADB xizmati aloqani uzdi (Socket closed)")
                zeroReadCount++
                if (zeroReadCount > 100) {
                    throw IOException("Sarlavhani o'qishda kutish vaqti tugadi (repeated 0-byte reads)")
                }
                // Prevent tight 100% CPU loop if read returns 0
                kotlinx.coroutines.delay(10)
                continue
            }
            zeroReadCount = 0
            totalRead += read
        }

        val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.clear() // Explicitly clear to make sure position=0, limit=24
        
        if (buffer.remaining() < 24) {
            throw IOException("Xato: Sarlavha buferi to'liq emas.")
        }
        val cmd = buffer.getInt()
        val arg0 = buffer.getInt()
        val arg1 = buffer.getInt()
        val length = buffer.getInt()
        val checksum = buffer.getInt()
        val magic = buffer.getInt()

        if (cmd xor magic != -1) {
            throw IOException("ADB Protokolining xavfsizlik tekshiruvi (hex magic xor) mos kelmadi.")
        }

        val payload = ByteArray(length)
        var totalPayloadRead = 0
        var zeroPayloadReadCount = 0
        while (totalPayloadRead < length) {
            val read = stream.read(payload, totalPayloadRead, length - totalPayloadRead)
            if (read <= 0) {
                if (read < 0) throw IOException("Uzatilgan ma'lumot uzildi.")
                zeroPayloadReadCount++
                if (zeroPayloadReadCount > 100) {
                    throw IOException("Ma'lumotlar paketini o'qishda kutish vaqti tugadi (repeated 0-byte reads)")
                }
                // Prevent tight 100% CPU loop if read returns 0
                kotlinx.coroutines.delay(10)
                continue
            }
            zeroPayloadReadCount = 0
            totalPayloadRead += read
        }

        return AdbPacket(cmd, arg0, arg1, length, checksum, payload)
    }

    private fun calculateChecksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) {
            sum += (b.toInt() and 0xFF)
        }
        return sum
    }

    private fun signChallenge(challenge: ByteArray): ByteArray? {
        val priv = privateKey ?: return null
        return try {
            val sig = Signature.getInstance("SHA1withRSA")
            sig.initSign(priv)
            sig.update(challenge)
            sig.sign()
        } catch (e: Exception) {
            Log.e(TAG, "Imzolash xatosi", e)
            null
        }
    }

    private fun getAdbPublicKeyPayload(): ByteArray {
        val pub = publicKey as? RSAPublicKey
        if (pub == null) {
            val fallback = Base64.encodeToString(publicKey?.encoded ?: ByteArray(0), Base64.NO_WRAP)
            return "$fallback super_adb@localhost\u0000".toByteArray(Charsets.UTF_8)
        }

        return try {
            val modulus = pub.modulus
            val exponent = pub.publicExponent

            val modulusWords = 64 // 2048-bit RSA = 64 32-bit words
            val modulusBytes = 256 // 2048 bits = 256 bytes

            // 1. Calculate n0inv = -1 / N[0] mod 2^32 (Montgomery parameter)
            val r32 = BigInteger.ONE.shiftLeft(32)
            val n0 = modulus.remainder(r32)
            val n0invBi = n0.modInverse(r32).negate().remainder(r32).let {
                if (it.signum() < 0) it.add(r32) else it
            }
            val n0inv = n0invBi.toLong()

            // 2. Modulus in Little-Endian byte array (256 bytes)
            val modBytesBigEndian = modulus.toByteArray()
            val modLittleEndian = ByteArray(modulusBytes)
            val modClean = if (modBytesBigEndian.size > modulusBytes && modBytesBigEndian[0] == 0.toByte()) {
                modBytesBigEndian.copyOfRange(1, modBytesBigEndian.size)
            } else {
                modBytesBigEndian
            }
            for (i in modClean.indices) {
                if (i < modulusBytes) {
                    modLittleEndian[i] = modClean[modClean.size - 1 - i]
                }
            }

            // 3. Calculate R^2 mod N where R = 2^2048 (Montgomery parameter)
            val r = BigInteger.ONE.shiftLeft(2048)
            val rr = r.multiply(r).remainder(modulus)
            val rrBytesBigEndian = rr.toByteArray()
            val rrLittleEndian = ByteArray(modulusBytes)
            val rrClean = if (rrBytesBigEndian.size > modulusBytes && rrBytesBigEndian[0] == 0.toByte()) {
                rrBytesBigEndian.copyOfRange(1, rrBytesBigEndian.size)
            } else {
                rrBytesBigEndian
            }
            for (i in rrClean.indices) {
                if (i < modulusBytes) {
                    rrLittleEndian[i] = rrClean[rrClean.size - 1 - i]
                }
            }

            // 4. Build 524-byte RSAPublicKey structure (Little Endian) required by Android adbd
            val buffer = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(modulusWords)
            buffer.putInt(n0inv.toInt())
            buffer.put(modLittleEndian)
            buffer.put(rrLittleEndian)
            buffer.putInt(exponent.toInt())

            val base64Key = Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
            "$base64Key super_adb@device\u0000".toByteArray(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "mincrypt encoding xatosi, fallback ishlatiladi", e)
            val fallbackBase64 = Base64.encodeToString(pub.encoded, Base64.NO_WRAP)
            "$fallbackBase64 super_adb@device\u0000".toByteArray(Charsets.UTF_8)
        }
    }

    private data class AdbPacket(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val length: Int,
        val checksum: Int,
        val data: ByteArray
    )
}
