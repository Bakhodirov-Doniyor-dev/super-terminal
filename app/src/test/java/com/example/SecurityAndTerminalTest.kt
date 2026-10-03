package com.example

import com.example.security.CommandRiskLevel
import com.example.security.CommandSecurityGateway
import com.example.security.ExecutionOrigin
import com.example.terminal.KaliUserspaceManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import android.content.Context

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurityAndTerminalTest {

    @Test
    fun testSecurityGateway_blocksDestructiveAiCommands() {
        val destructiveCommands = listOf(
            "rm -rf /data/local/tmp",
            "rm /sdcard/file.txt",
            "dd if=/dev/zero of=/dev/block/bootdevice",
            "mkfs.ext4 /dev/block/sda",
            "reboot",
            "shutdown",
            "pm uninstall com.example.app",
            "pm disable com.android.systemui"
        )

        for (cmd in destructiveCommands) {
            val assessment = CommandSecurityGateway.assessCommand(cmd, ExecutionOrigin.AI_TOOL)
            assertTrue("Command '$cmd' should be blocked by default for AI", assessment.isBlockedByDefault)
            assertNotEquals(CommandRiskLevel.SAFE, assessment.riskLevel)
            assertTrue(assessment.matchedTokens.isNotEmpty())
        }
    }

    @Test
    fun testSecurityGateway_allowsSafeAiCommands() {
        val safeCommands = listOf(
            "ls -la",
            "pwd",
            "cat /proc/cpuinfo",
            "getprop ro.product.model",
            "dumpsys battery",
            "neofetch",
            "ifconfig"
        )

        for (cmd in safeCommands) {
            val assessment = CommandSecurityGateway.assessCommand(cmd, ExecutionOrigin.AI_TOOL)
            assertFalse("Command '$cmd' should NOT be blocked for AI", assessment.isBlockedByDefault)
            assertEquals(CommandRiskLevel.SAFE, assessment.riskLevel)
        }
    }

    @Test
    fun testSecurityGateway_allowsDeveloperKeyboardCommands() {
        // User keyboard origin allows full developer control without automatic blocking
        val devCmd = "rm -rf /sdcard/temp_test"
        val assessment = CommandSecurityGateway.assessCommand(devCmd, ExecutionOrigin.USER_KEYBOARD)
        assertFalse("User manual keyboard input must not be blocked by default", assessment.isBlockedByDefault)
        assertEquals(CommandRiskLevel.CRITICAL_DESTRUCTIVE, assessment.riskLevel)
    }

    @Test
    fun testKaliUserspaceManager_statusDiagnostic() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = KaliUserspaceManager(context)
        val status = manager.getStatusDiagnostic()

        assertNotNull(status)
        assertTrue(status.contains("KALI LINUX ARM64 USERSPACE"))
        assertTrue(status.contains("Rootfs Location"))
    }

    @Test
    fun testTerminalNativeApi_deployment() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val api = com.example.terminal.TerminalNativeApi(context)
        api.deployTerminalApiBinaries()

        val binDir = java.io.File(context.filesDir, "usr/bin")
        assertTrue(java.io.File(binDir, "terminal-toast").exists())
        assertTrue(java.io.File(binDir, "terminal-vibrate").exists())
        assertTrue(java.io.File(binDir, "terminal-battery-status").exists())
        assertTrue(java.io.File(binDir, "terminal-clipboard-set").exists())
        assertTrue(java.io.File(binDir, "terminal-torch").exists())
        assertTrue(java.io.File(binDir, "pkg").exists())
        assertTrue(java.io.File(binDir, "apt").exists())
    }

    @Test
    fun testTerminalPackageManager_directoriesAndArch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pkgManager = com.example.terminal.TerminalPackageManager(context)

        assertTrue(pkgManager.prefixDir.exists())
        assertTrue(pkgManager.binDir.exists())
        assertTrue(pkgManager.libDir.exists())
        assertNotNull(pkgManager.getHostArchitecture())
    }
}
