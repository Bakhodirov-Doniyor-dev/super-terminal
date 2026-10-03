package com.example

import com.example.net.LanFileServer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LanFileServerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var sharedDir: File
    private val testToken = "SecretTestToken1234"

    @Before
    fun setUp() {
        sharedDir = tempFolder.newFolder("lan_shared_downloads")
        File(sharedDir, "test_file.txt").writeText("Salom Super Terminal LAN Server!")
        val subDir = File(sharedDir, "sub_folder")
        subDir.mkdirs()
        File(subDir, "inner_doc.txt").writeText("Ichki hujjat matni")
    }

    private fun findFreePort(): Int {
        val socket = ServerSocket(0)
        val port = socket.localPort
        socket.close()
        return port
    }

    @Test
    fun testServerStartAndStop() {
        val port = findFreePort()
        val server = LanFileServer(
            port = port,
            sharedDirectory = sharedDir,
            isAuthEnabled = false
        )

        assertFalse(server.isServerRunning())
        server.start()
        assertTrue(server.isServerRunning())
        assertTrue(server.startTimeMs > 0)

        server.stop()
        assertFalse(server.isServerRunning())
    }

    @Test
    fun testDirectoryListingHtml() {
        val port = findFreePort()
        val server = LanFileServer(
            port = port,
            sharedDirectory = sharedDir,
            isAuthEnabled = false
        )
        server.start()

        try {
            val url = URL("http://127.0.0.1:$port/")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"

            val code = conn.responseCode
            assertEquals(200, code)
            val html = conn.inputStream.bufferedReader().readText()
            assertTrue(html.contains("Super Terminal LAN File Server"))
            assertTrue(html.contains("test_file.txt"))
            assertTrue(html.contains("sub_folder"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun testFileDownloadFullContent() {
        val port = findFreePort()
        val server = LanFileServer(
            port = port,
            sharedDirectory = sharedDir,
            isAuthEnabled = false
        )
        server.start()

        try {
            val url = URL("http://127.0.0.1:$port/test_file.txt")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val code = conn.responseCode
            assertEquals(200, code)

            val content = conn.inputStream.bufferedReader().readText()
            assertEquals("Salom Super Terminal LAN Server!", content)
        } finally {
            server.stop()
        }
    }

    @Test
    fun testHttpRangePartialContent() {
        val port = findFreePort()
        val largeFile = File(sharedDir, "data.bin")
        val dataBytes = ByteArray(1024) { it.toByte() }
        largeFile.writeBytes(dataBytes)

        val server = LanFileServer(
            port = port,
            sharedDirectory = sharedDir,
            isAuthEnabled = false
        )
        server.start()

        try {
            val url = URL("http://127.0.0.1:$port/data.bin")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Range", "bytes=100-199")
            conn.connectTimeout = 3000
            conn.readTimeout = 3000

            val code = conn.responseCode
            assertEquals(206, code)
            val contentRange = conn.getHeaderField("Content-Range")
            assertNotNull(contentRange)
            assertTrue(contentRange.startsWith("bytes 100-199/1024"))

            val readBytes = conn.inputStream.readBytes()
            assertEquals(100, readBytes.size)
            assertEquals(dataBytes[100], readBytes[0])
            assertEquals(dataBytes[199], readBytes[99])
        } finally {
            server.stop()
        }
    }

    @Test
    fun testAuthenticationProtection() {
        val port = findFreePort()
        val server = LanFileServer(
            port = port,
            sharedDirectory = sharedDir,
            isAuthEnabled = true,
            authToken = testToken
        )
        server.start()

        try {
            // Request without token should return 401 Unauthorized / Login page
            val unauthUrl = URL("http://127.0.0.1:$port/test_file.txt")
            val conn1 = unauthUrl.openConnection() as HttpURLConnection
            conn1.instanceFollowRedirects = false
            conn1.connectTimeout = 3000
            conn1.readTimeout = 3000
            val code1 = conn1.responseCode
            assertEquals(401, code1)

            // Request with valid ?token= parameter should return 200 OK
            val authUrl = URL("http://127.0.0.1:$port/test_file.txt?token=$testToken")
            val conn2 = authUrl.openConnection() as HttpURLConnection
            conn2.connectTimeout = 3000
            conn2.readTimeout = 3000
            val code2 = conn2.responseCode
            assertEquals(200, code2)
            assertEquals("Salom Super Terminal LAN Server!", conn2.inputStream.bufferedReader().readText())
        } finally {
            server.stop()
        }
    }

    @Test
    fun testPathTraversalProtection() {
        val port = findFreePort()
        val server = LanFileServer(
            port = port,
            sharedDirectory = sharedDir,
            isAuthEnabled = false
        )

        // Directly test resolveSafeFile against various traversal attack payloads
        assertNull(server.resolveSafeFile("../secret.txt"))
        assertNull(server.resolveSafeFile("../../etc/passwd"))
        assertNull(server.resolveSafeFile("/../../etc/passwd"))
        assertNull(server.resolveSafeFile("sub_folder/../../outside.txt"))
        assertNull(server.resolveSafeFile(".."))

        // Legitimate subpaths must resolve within shared directory
        val safeFile = server.resolveSafeFile("test_file.txt")
        assertNotNull(safeFile)
        assertTrue(safeFile!!.exists())

        val safeSubDir = server.resolveSafeFile("sub_folder/inner_doc.txt")
        assertNotNull(safeSubDir)
        assertTrue(safeSubDir!!.exists())
    }

    @Test
    fun testPortBusyDetection() {
        val port = findFreePort()
        val blockingSocket = ServerSocket(port)

        val server = LanFileServer(
            port = port,
            sharedDirectory = sharedDir
        )

        assertFalse(server.checkPortAvailable(port))

        blockingSocket.close()
        assertTrue(server.checkPortAvailable(port))
    }
}
