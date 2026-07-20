package com.nousresearch.hermes.agent.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.toml.Toml

/**
 * Hermes Agent policy enforcer — loaded from policy.toml at startup.
 *
 * Proverbs 4:23 NLT:
 * "Guard your heart above all else, for it determines the course of your life."
 *
 * This hard-coded configuration layer enforces privacy, safety, and autonomy
 * boundaries that the LLM cannot override. All settings are loaded before
 * any agent interaction and are immutable at runtime.
 */
@Serializable
data class PolicyEnforcer(
    /** Policy version for audit trail */
    val version: String = "1.0.0",

    /** Application identity */
    val appName: String = "Hermes Agent",
    val appOrg: String = "Nous Research",

    // ── Operating Mode ──────────────────────────────────────────────
    /** Offline-first: never send data to external LLM providers without consent */
    val offlineFirst: Boolean = true,
    /** Allow the user to override offline-first and enable cloud inference */
    val allowCloudInferenceOverride: Boolean = false,
    /** Automatically start the background gateway service */
    val autoStartService: Boolean = true,

    // ── Privacy ─────────────────────────────────────────────────────
    /** Completely disable telemetry — no analytics, no crash reporting */
    val telemetryDisabled: Boolean = true,
    /** Disable all third-party tracker SDKs */
    val disableThirdPartyTrackers: Boolean = true,
    /** Redact personally identifiable information from agent context */
    val redactPII: Boolean = true,
    /** Never log raw conversation text to disk outside the session store */
    val noConversationLogging: Boolean = true,
    /** Encrypt the local session database */
    val encryptLocalDatabase: Boolean = true,
    /** Purge session data older than this many days (0 = never) */
    val sessionRetentionDays: Int = 90,

    // ── Content Safety ──────────────────────────────────────────────
    /** Block prompts requesting deceptive, harmful, or illegal content */
    val blockDeceptiveContent: Boolean = true,
    /** Block unpermitted autonomous actions (file system changes, purchasing) */
    val blockUnpermittedAutonomy: Boolean = true,
    /** Require explicit user consent for every file system write */
    val requireFileWriteConsent: Boolean = true,
    /** Require explicit user consent for network operations */
    val requireNetworkConsent: Boolean = false,
    /** Reject prompts that attempt to disable or modify the policy itself */
    val blockPolicyModificationAttempts: Boolean = true,

    // ── Agent Autonomy ─────────────────────────────────────────────
    /** Max agent turns before requiring user re-engagement */
    val maxAutonomousTurns: Int = 30,
    /** Timeout for autonomous execution (seconds) */
    val autonomyTimeoutSeconds: Int = 300,
    /** Require user approval before executing shell commands */
    val requireShellApproval: Boolean = true,
    /** Block dangerous shell commands (rm -rf, dd, etc.) */
    val blockDangerousCommands: Boolean = true,

    // ── Local AI Model Settings ─────────────────────────────────────
    /** Default local model to use when offline */
    val defaultLocalModel: String = "qwen3:14b",
    /** Fallback model if default is unavailable */
    val fallbackModel: String = "deepseek-coder:32b",
    /** Maximum context length (tokens) */
    val maxContextTokens: Int = 32768,
    /** Enable streaming responses */
    val enableStreaming: Boolean = true,
    /** WebSocket gateway port */
    val gatewayPort: Int = 8320,
    /** Gateway host (localhost for embedded backend) */
    val gatewayHost: String = "127.0.0.1",

    // ── Permissions ────────────────────────────────────────────────
    /** Whether to request microphone permission on first launch */
    val requestMicrophoneOnFirstLaunch: Boolean = true,
    /** Whether to request notification permission on first launch */
    val requestNotificationOnFirstLaunch: Boolean = true,
    /** Whether to allow the accessibility service API integration */
    val allowAccessibilityIntegration: Boolean = false,
    /** Whether to allow calendar read access */
    val allowCalendarAccess: Boolean = false,
    /** Whether to allow storage read access (for file upload) */
    val allowStorageAccess: Boolean = true
) {
    /**
     * Check if a given shell command is dangerous and should be blocked.
     */
    fun isDangerousCommand(command: String): Boolean {
        if (!blockDangerousCommands) return false
        val dangerousPatterns = listOf(
            Regex("""\brm\s+-rf\b"""),
            Regex("""\bdd\s+if=""")
        )
        return dangerousPatterns.any { it.containsMatchIn(command) }
    }

    /**
     * Check if a prompt attempts to modify policy content.
     */
    fun isPolicyModificationAttempt(prompt: String): Boolean {
        if (!blockPolicyModificationAttempts) return false
        val patterns = listOf(
            Regex("""(?:disable|bypass|ignore|override)\s+(?:the\s+)?policy""", RegexOption.IGNORE_CASE),
            Regex("""(?:change|modify|delete)\s+(?:policy\.toml|policy)""", RegexOption.IGNORE_CASE)
        )
        return patterns.any { it.containsMatchIn(prompt) }
    }

    companion object {
        private const val TAG = "PolicyEnforcer"

        /** Load from TOML string */
        fun fromToml(tomlContent: String): PolicyEnforcer {
            return try {
                Toml.decodeFromString<PolicyEnforcer>(tomlContent)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "TOML parse error, using defaults", e)
                defaults()
            }
        }

        /** Safe defaults when policy.toml is unavailable */
        fun defaults(): PolicyEnforcer = PolicyEnforcer()
    }
}
