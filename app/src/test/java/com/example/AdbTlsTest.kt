package com.example

import com.example.adb.AdbTlsHelper
import com.example.adb.AdbTlsKeyManager
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class AdbTlsTest {

    @Test
    fun testRsaCertificateGeneration() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        val cert = AdbTlsHelper.generateSelfSignedCert(kp, "SuperAdb-RSA-Test")
        assertNotNull(cert)
        assertEquals("CN=SuperAdb-RSA-Test", cert.subjectX500Principal.name)
        assertEquals("CN=SuperAdb-RSA-Test", cert.issuerX500Principal.name)
        // Verify certificate was signed with the private key
        cert.verify(kp.public)
    }

    @Test
    fun testEcCertificateGeneration() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val kp = kpg.generateKeyPair()

        val cert = AdbTlsHelper.generateSelfSignedCert(kp, "SuperAdb-EC-Test")
        assertNotNull(cert)
        assertEquals("CN=SuperAdb-EC-Test", cert.subjectX500Principal.name)
        assertEquals("CN=SuperAdb-EC-Test", cert.issuerX500Principal.name)
        // Verify certificate was signed with the private key
        cert.verify(kp.public)
    }

    @Test
    fun testKeyManagerAliasSelection() {
        val rsaKpg = KeyPairGenerator.getInstance("RSA")
        rsaKpg.initialize(2048)
        val rsaKp = rsaKpg.generateKeyPair()
        val rsaCert = AdbTlsHelper.generateSelfSignedCert(rsaKp, "RSA")

        val ecKpg = KeyPairGenerator.getInstance("EC")
        ecKpg.initialize(ECGenParameterSpec("secp256r1"))
        val ecKp = ecKpg.generateKeyPair()
        val ecCert = AdbTlsHelper.generateSelfSignedCert(ecKp, "EC")

        val km = AdbTlsKeyManager(ecKp, ecCert, rsaKp, rsaCert)

        // When EC requested
        val ecAlias = km.chooseClientAlias(arrayOf("EC"), null, null)
        assertEquals("ec", ecAlias)

        // When RSA requested
        val rsaAlias = km.chooseClientAlias(arrayOf("RSA"), null, null)
        assertEquals("rsa", rsaAlias)

        assertNotNull(km.getPrivateKey("ec"))
        assertNotNull(km.getPrivateKey("rsa"))
        assertNotNull(km.getCertificateChain("ec"))
        assertNotNull(km.getCertificateChain("rsa"))
    }
}
