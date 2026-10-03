package com.example.security

import java.util.Locale

/**
 * Execution origin describing who or what initiated the terminal command.
 */
enum class ExecutionOrigin {
    USER_KEYBOARD,
    AI_TOOL,
    SAVED_SCRIPT,
    INTERNAL_SYSTEM
}

/**
 * Risk classification for command execution safety.
 */
enum class CommandRiskLevel {
    SAFE,
    MODERATE,
    CRITICAL_DESTRUCTIVE
}

/**
 * Security assessment outcome.
 */
data class SecurityAssessment(
    val riskLevel: CommandRiskLevel,
    val isBlockedByDefault: Boolean,
    val matchedTokens: List<String>,
    val warningMessage: String?
)

/**
 * Centralized Command Security Gateway.
 * Validates, parses, and restricts commands based on their execution origin.
 * Prevents AI agents or untrusted sources from automatically executing destructive commands
 * while granting developers full manual terminal control.
 */
object CommandSecurityGateway {

    private val DESTRUCTIVE_KEYWORDS = setOf(
        "rm", "mkfs", "dd", "format", "reboot", "shutdown", "wipe", "fastboot", "flash"
    )

    private val SYSTEM_MODIFICATION_KEYWORDS = setOf(
        "pm uninstall", "pm disable", "pm hide", "dpm remove-active-admin",
        "settings put", "setprop", "chmod", "chown", "mount", "umount", "iptables"
    )

    fun assessCommand(command: String, origin: ExecutionOrigin): SecurityAssessment {
        val trimmed = command.trim()
        val lower = trimmed.lowercase(Locale.US)
        val matchedTokens = mutableListOf<String>()

        // 1. Check for critical destructive commands
        for (kw in DESTRUCTIVE_KEYWORDS) {
            val pattern = Regex("(?:^|[\\s;|&|/])$kw(?:[\\s.;&|/|$]|$)", RegexOption.IGNORE_CASE)
            if (pattern.containsMatchIn(lower) || lower.startsWith("$kw ") || lower.startsWith("$kw.") || lower == kw) {
                matchedTokens.add(kw)
            }
        }

        // 2. Check for system modification commands
        for (kw in SYSTEM_MODIFICATION_KEYWORDS) {
            if (lower.contains(kw)) {
                matchedTokens.add(kw)
            }
        }

        val riskLevel = when {
            matchedTokens.any { it in DESTRUCTIVE_KEYWORDS } -> CommandRiskLevel.CRITICAL_DESTRUCTIVE
            matchedTokens.isNotEmpty() -> CommandRiskLevel.MODERATE
            else -> CommandRiskLevel.SAFE
        }

        // Commands originated from AI_TOOL are blocked by default if they are not strictly SAFE
        val isBlockedByDefault = origin == ExecutionOrigin.AI_TOOL && riskLevel != CommandRiskLevel.SAFE

        val warning = if (riskLevel == CommandRiskLevel.CRITICAL_DESTRUCTIVE) {
            "Critical: Potentially destructive system command detected: ${matchedTokens.joinToString(", ")}"
        } else if (riskLevel == CommandRiskLevel.MODERATE) {
            "Warning: System configuration modification command: ${matchedTokens.joinToString(", ")}"
        } else null

        return SecurityAssessment(
            riskLevel = riskLevel,
            isBlockedByDefault = isBlockedByDefault,
            matchedTokens = matchedTokens,
            warningMessage = warning
        )
    }
}
