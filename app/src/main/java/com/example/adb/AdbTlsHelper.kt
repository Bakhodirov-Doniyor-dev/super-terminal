package com.example.adb

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.Socket
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.net.ssl.*

/**
 * AdbTlsHelper provides TLS 1.3 mutual-authentication support for Android 11+
 * Wireless Debugging (A_STLS protocol handshake).
 *
 * Generates self-signed X.509 client certificates for both EC (P-256) and RSA (2048) keys
 * and establishes secure TLS socket tunnels over the existing ADB TCP socket.
 */
object AdbTlsHelper {
    private const val TAG = "AdbTlsHelper"
    private const val PREFS_NAME = "super_adb_tls_keys"
    private const val KEY_EC_PRIV = "ec_private"
    private const val KEY_EC_PUB = "ec_public"

    @Volatile
    private var cachedEcKeyPair: KeyPair? = null
    @Volatile
    private var cachedEcCert: X509Certificate? = null
    @Volatile
    private var cachedRsaCert: X509Certificate? = null

    fun encodeLength(len: Int): ByteArray {
        return when {
            len < 0x80 -> byteArrayOf(len.toByte())
            len <= 0xFF -> byteArrayOf(0x81.toByte(), len.toByte())
            len <= 0xFFFF -> byteArrayOf(0x82.toByte(), (len shr 8).toByte(), (len and 0xFF).toByte())
            else -> {
                val out = ByteArrayOutputStream()
                out.write(0x84)
                out.write((len shr 24) and 0xFF)
                out.write((len shr 16) and 0xFF)
                out.write((len shr 8) and 0xFF)
                out.write(len and 0xFF)
                out.toByteArray()
            }
        }
    }

    fun encodeTl(tag: Int, content: ByteArray): ByteArray {
        val lenBytes = encodeLength(content.size)
        val out = ByteArray(1 + lenBytes.size + content.size)
        out[0] = tag.toByte()
        System.arraycopy(lenBytes, 0, out, 1, lenBytes.size)
        System.arraycopy(content, 0, out, 1 + lenBytes.size, content.size)
        return out
    }

    private fun sequence(content: ByteArray): ByteArray = encodeTl(0x30, content)
    private fun set(content: ByteArray): ByteArray = encodeTl(0x31, content)

    private fun integer(value: BigInteger): ByteArray {
        val b = value.toByteArray()
        return encodeTl(0x02, b)
    }

    private fun bitString(content: ByteArray): ByteArray {
        val buf = ByteArray(1 + content.size)
        buf[0] = 0x00 // 0 unused bits
        System.arraycopy(content, 0, buf, 1, content.size)
        return encodeTl(0x03, buf)
    }

    private fun utf8String(str: String): ByteArray {
        return encodeTl(0x0C, str.toByteArray(Charsets.UTF_8))
    }

    private fun utcTime(timeStr: String): ByteArray {
        return encodeTl(0x17, timeStr.toByteArray(Charsets.US_ASCII))
    }

    /**
     * Generates an RFC 5280 compliant self-signed X.509 v3 certificate for the given KeyPair
     * without any external dependencies.
     */
    fun generateSelfSignedCert(keyPair: KeyPair, commonName: String = "SuperAdb"): X509Certificate {
        val isEc = keyPair.public.algorithm.equals("EC", ignoreCase = true)
        val sigAlgOidDer = if (isEc) {
            // ecdsa-with-SHA256 (1.2.840.10045.4.3.2)
            byteArrayOf(0x30, 0x0a, 0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x04, 0x03, 0x02)
        } else {
            // sha256WithRSAEncryption (1.2.840.113549.1.1.11) with ASN.1 NULL parameter
            byteArrayOf(0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x0b, 0x05, 0x00)
        }

        // 1. Version: [0] EXPLICIT INTEGER 2 (v3)
        val version = byteArrayOf(0xa0.toByte(), 0x03, 0x02, 0x01, 0x02)

        // 2. Serial Number: positive 8-byte random BigInteger
        val serialBytes = ByteArray(8)
        SecureRandom().nextBytes(serialBytes)
        val serial = BigInteger(1, serialBytes).max(BigInteger.ONE)
        val serialDer = integer(serial)

        // 3. Signature AlgorithmIdentifier in TBS
        val sigAlgDer = sigAlgOidDer

        // 4. Issuer: SEQUENCE { SET { SEQUENCE { OID 2.5.4.3 (CN), UTF8String } } }
        val cnOid = byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03)
        val cnVal = utf8String(commonName)
        val atv = sequence(cnOid + cnVal)
        val rdn = set(atv)
        val nameDer = sequence(rdn)

        // 5. Validity: SEQUENCE { UTCTime (notBefore), UTCTime (notAfter) }
        val notBefore = utcTime("240101000000Z")
        val notAfter = utcTime("440101000000Z")
        val validityDer = sequence(notBefore + notAfter)

        // 6. Subject: same as Issuer
        val subjectDer = nameDer

        // 7. SubjectPublicKeyInfo: directly from keyPair.public.encoded
        val spkiDer = keyPair.public.encoded

        // Assemble TBSCertificate
        val tbsContent = ByteArrayOutputStream().apply {
            write(version)
            write(serialDer)
            write(sigAlgDer)
            write(nameDer)
            write(validityDer)
            write(subjectDer)
            write(spkiDer)
        }.toByteArray()

        val tbsDer = sequence(tbsContent)

        // Sign TBSCertificate
        val sigAlgorithm = if (isEc) "SHA256withECDSA" else "SHA256withRSA"
        val signatureBytes = Signature.getInstance(sigAlgorithm).apply {
            initSign(keyPair.private)
            update(tbsDer)
        }.sign()

        val sigValueDer = bitString(signatureBytes)

        // Final Certificate SEQUENCE
        val certContent = ByteArrayOutputStream().apply {
            write(tbsDer)
            write(sigAlgOidDer)
            write(sigValueDer)
        }.toByteArray()

        val certDer = sequence(certContent)

        val certFactory = CertificateFactory.getInstance("X.509")
        return certFactory.generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate
    }

    /**
     * Loads or generates an EC (secp256r1) keypair and certificate.
     */
        fun getOrCreateEcKeyAndCert(context: Context): Pair<KeyPair, X509Certificate> {
        val cachedKp = cachedEcKeyPair
        val cachedCert = cachedEcCert
        if (cachedKp != null && cachedCert != null) {
            return Pair(cachedKp, cachedCert)
        }

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "super_adb_ec_tls_key"

        try {
            if (keyStore.containsAlias(alias)) {
                val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
                if (entry != null) {
                    val pub = entry.certificate.publicKey
                    val priv = entry.privateKey
                    val kp = KeyPair(pub, priv)
                    // The certificate in Keystore is already self-signed
                    val cert = entry.certificate as X509Certificate
                    cachedEcKeyPair = kp
                    cachedEcCert = cert
                    return Pair(kp, cert)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load EC key from KeyStore, generating new one: ${e.message}")
        }

        // Generate new EC KeyPair in AndroidKeyStore
        val kpg = KeyPairGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        val parameterSpec = android.security.keystore.KeyGenParameterSpec.Builder(
            alias,
            android.security.keystore.KeyProperties.PURPOSE_SIGN or android.security.keystore.KeyProperties.PURPOSE_VERIFY
        ).run {
            setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
            // Specify a self-signed cert info
            setCertificateSubject(javax.security.auth.x500.X500Principal("CN=SuperAdb-EC"))
            setCertificateSerialNumber(java.math.BigInteger.ONE)
            setCertificateNotBefore(java.util.Date(System.currentTimeMillis() - 86400000L))
            setCertificateNotAfter(java.util.Date(System.currentTimeMillis() + 86400000L * 3650L))
            build()
        }
        kpg.initialize(parameterSpec)
        val kp = kpg.generateKeyPair()
        
        val cert = keyStore.getCertificate(alias) as X509Certificate
        cachedEcKeyPair = kp
        cachedEcCert = cert
        return Pair(kp, cert)
    }

    /**
     * Obtains or generates a self-signed X.509 certificate for an existing RSA KeyPair.
     */
    fun getRsaCert(rsaKeyPair: KeyPair): X509Certificate {
        val cached = cachedRsaCert
        if (cached != null) return cached
        val cert = generateSelfSignedCert(rsaKeyPair, "SuperAdb-RSA")
        cachedRsaCert = cert
        return cert
    }

    /**
     * Upgrades an existing connected raw socket to a TLS 1.3 encrypted SSLSocket
     * providing mutual client certificate authentication.
     */
    fun upgradeToTls(
        rawSocket: Socket,
        host: String,
        port: Int,
        context: Context,
        rsaKeyPair: KeyPair
    ): SSLSocket {
        val (ecKeyPair, ecCert) = getOrCreateEcKeyAndCert(context)
        val rsaCert = getRsaCert(rsaKeyPair)

        val keyManager = AdbTlsKeyManager(ecKeyPair, ecCert, rsaKeyPair, rsaCert)

        // Note: ADB Wireless debugging inherently uses device-generated self-signed certificates.
        // Standard PKI (Public Key Infrastructure) validation via CA roots is impossible.
        // In a production environment, this should validate against a known paired keystore 
        // matching the device's exact public key fingerprint established during Pairing.
        val adbDeveloperTrustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (chain.isNullOrEmpty()) throw java.security.cert.CertificateException("No client certificate provided")
            }
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (chain.isNullOrEmpty()) throw java.security.cert.CertificateException("No server certificate provided")
                val serverCert = chain[0]
                try {
                    val md = MessageDigest.getInstance("SHA-256")
                    val digest = md.digest(serverCert.encoded)
                    val fingerprint = digest.joinToString(":") { String.format("%02X", it) }
                    val prefs = context.getSharedPreferences("adb_tls_fingerprints", Context.MODE_PRIVATE)
                    val hostKey = "$host:$port"
                    val storedFingerprint = prefs.getString(hostKey, null)
                    if (storedFingerprint == null) {
                        // Trust On First Use (TOFU): Record device fingerprint securely
                        prefs.edit().putString(hostKey, fingerprint).apply()
                        Log.i("AdbTlsHelper", "Pinned new server TLS certificate for $hostKey (SHA-256: $fingerprint)")
                    } else if (!storedFingerprint.equals(fingerprint, ignoreCase = true)) {
                        throw java.security.cert.CertificateException(
                            "TLS MITM Alert! Server certificate fingerprint ($fingerprint) does NOT match pinned fingerprint ($storedFingerprint) for $hostKey!"
                        )
                    }
                } catch (e: Exception) {
                    if (e is java.security.cert.CertificateException) throw e
                    throw java.security.cert.CertificateException("Certificate validation failure: ${e.localizedMessage}", e)
                }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val sslContext = try {
            SSLContext.getInstance("TLSv1.3").apply {
                init(arrayOf(keyManager), arrayOf(adbDeveloperTrustManager), SecureRandom())
            }
        } catch (e: Exception) {
            SSLContext.getInstance("TLS").apply {
                init(arrayOf(keyManager), arrayOf(adbDeveloperTrustManager), SecureRandom())
            }
        }

        val sslSocket = sslContext.socketFactory.createSocket(
            rawSocket,
            host,
            port,
            true // autoClose underlying raw socket
        ) as SSLSocket

        sslSocket.useClientMode = true
        sslSocket.soTimeout = 25000
        sslSocket.startHandshake()
        return sslSocket
    }
}

/**
 * Custom X509ExtendedKeyManager providing both EC and RSA client certificates.
 * For Wireless ADB (Android 11+), presenting EC certificate allows instant connection
 * while RSA provides backward-compatible pairing support.
 */
class AdbTlsKeyManager(
    private val ecKeyPair: KeyPair?,
    private val ecCert: X509Certificate?,
    private val rsaKeyPair: KeyPair?,
    private val rsaCert: X509Certificate?
) : X509ExtendedKeyManager() {

    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> {
        val list = mutableListOf<String>()
        if (ecKeyPair != null && ecCert != null) list.add("ec")
        if (rsaKeyPair != null && rsaCert != null) list.add("rsa")
        return list.toTypedArray()
    }

    override fun chooseClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        socket: Socket?
    ): String? {
        val types = keyType?.map { it.uppercase() } ?: emptyList()
        // If server accepts EC or no restriction, choose EC for CVE-2026-0073 wireless bypass
        if (ecKeyPair != null && ecCert != null && (types.isEmpty() || types.any { it.contains("EC") })) {
            return "ec"
        }
        if (rsaKeyPair != null && rsaCert != null) {
            return "rsa"
        }
        return if (ecKeyPair != null) "ec" else "rsa"
    }

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? {
        return when (alias) {
            "ec" -> ecCert?.let { arrayOf(it) }
            "rsa" -> rsaCert?.let { arrayOf(it) }
            else -> ecCert?.let { arrayOf(it) } ?: rsaCert?.let { arrayOf(it) }
        }
    }

    override fun getPrivateKey(alias: String?): PrivateKey? {
        return when (alias) {
            "ec" -> ecKeyPair?.private
            "rsa" -> rsaKeyPair?.private
            else -> ecKeyPair?.private ?: rsaKeyPair?.private
        }
    }

    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
}
