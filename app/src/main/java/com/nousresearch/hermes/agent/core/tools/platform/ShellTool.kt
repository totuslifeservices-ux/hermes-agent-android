package com.nousresearch.hermes.agent.core.tools.platform

import android.Manifest
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult
import com.nousresearch.hermes.agent.core.tools.HermesTool
import com.nousresearch.hermes.agent.core.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ShellTool — Execute shell commands on the device.
 *
 * Capabilities:
 * - run_command: Execute a shell command and return stdout + stderr
 *
 * WARNING: Shell commands can modify system state. This tool should be
 * restricted by policy enforcement (requireShellApproval in PolicyEnforcer).
 *
 * Uses Runtime.exec for command execution. For Termux-based environments,
 * commands can be routed through the Termux provider.
 *
 * Permissions: None standard — shell execution works within the app's UID.
 * Root-level commands require a rooted device or ADB shell.
 *
 * Security:
 * - Requires user confirmation before execution
 * - Dangerous commands (rm -rf, dd, etc.) should be blocked by PolicyEnforcer
 * - Output is truncated to 100KB to prevent abuse
 * - No telemetry. Command results are returned to the LLM.
 */
class ShellTool : HermesTool {

    override val descriptor = ToolDescriptor(
        name = "shell",
        description = "Execute shell commands on the device. Use run_command to run a shell command " +
            "and get its stdout, stderr, and exit code. " +
            "WARNING: This can modify system state. Dangerous commands are blocked by policy.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("run_command"),
                    "description" to "The shell action to perform",
                ),
                "command" to mapOf(
                    "type" to "string",
                    "description" to "The shell command to execute",
                ),
                "timeoutMs" to mapOf(
                    "type" to "integer",
                    "description" to "Command timeout in milliseconds (default: 30000, max: 120000)",
                    "default" to 30000,
                ),
                "workingDirectory" to mapOf(
                    "type" to "string",
                    "description" to "Working directory for the command (default: app data dir)",
                ),
            ),
            "required" to listOf("action", "command"),
        ),
    )

    override val requiresConfirmation: Boolean get() = true

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            val action = args["action"] as? String ?: return@withContext ToolResult.Error(
                message = "Missing required parameter: action",
                recoverable = true,
            )

            try {
                when (action) {
                    "run_command" -> runCommand(args)
                    else -> ToolResult.Error(
                        message = "Unknown shell action: '$action'. Valid: run_command",
                        recoverable = true,
                    )
                }
            } catch (e: SecurityException) {
                ToolResult.Error(
                    message = "Shell command execution blocked by security policy",
                    recoverable = false,
                )
            } catch (e: Exception) {
                ToolResult.Error(
                    message = "Shell execution failed: ${e.message ?: "Unknown error"}",
                    recoverable = true,
                )
            }
        }

    override fun toString(): String = "ShellTool"

    // ── Actions ──────────────────────────────────────────────────────

    private fun runCommand(args: Map<String, Any?>): ToolResult {
        val command = args["command"] as? String
            ?: return ToolResult.Error(
                message = "Missing required parameter: command",
                recoverable = true,
            )
        val timeoutMs = (args["timeoutMs"] as? Number)?.toLong()?.coerceIn(1000L, 120000L) ?: 30000L

        if (command.isBlank()) {
            return ToolResult.Error(
                message = "Command cannot be empty",
                recoverable = true,
            )
        }

        // Check for dangerous commands
        val dangerousPatterns = listOf(
            Regex("""\brm\s+-rf\b"""),
            Regex("""\bdd\s+if=""", RegexOption.IGNORE_CASE),
            Regex("""\bmkfs\b""", RegexOption.IGNORE_CASE),
            Regex("""\b>:"""),
            Regex("""\b/dev/(?!null\b)"""),
        )

        val isDangerous = dangerousPatterns.any { it.containsMatchIn(command) }
        if (isDangerous) {
            return ToolResult.Error(
                message = "Command blocked: potentially dangerous operation detected. " +
                    "This command has been flagged by the security policy.",
                recoverable = false,
            )
        }

        val runtime = Runtime.getRuntime()
        val process = try {
            runtime.exec(command)
        } catch (e: SecurityException) {
            return ToolResult.Error(
                message = "Shell execution not permitted by the runtime security manager",
                recoverable = false,
            )
        }

        // Read stdout and stderr in parallel
        val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
        val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

        val stdout = StringBuilder()
        val stderr = StringBuilder()

        // Wait for completion with timeout
        val startTime = System.currentTimeMillis()
        var exitCode: Int? = null

        while (System.currentTimeMillis() - startTime < timeoutMs && exitCode == null) {
            try {
                // Read available lines
                while (stdoutReader.ready()) {
                    stdout.appendLine(stdoutReader.readLine())
                }
                while (stderrReader.ready()) {
                    stderr.appendLine(stderrReader.readLine())
                }

                exitCode = try {
                    process.exitValue()
                } catch (e: IllegalThreadStateException) {
                    null // Still running
                }

                if (exitCode == null) {
                    Thread.sleep(50) // Don't busy-wait
                }
            } catch (e: Exception) {
                break
            }
        }

        // Read remaining output
        try {
            stdoutReader.forEachLine { stdout.appendLine(it) }
        } catch (_: Exception) {}
        try {
            stderrReader.forEachLine { stderr.appendLine(it) }
        } catch (_: Exception) {}

        // If still running, destroy
        if (exitCode == null) {
            process.destroyForcibly()
            exitCode = -1
        }

        // Truncate output to 100KB
        val maxOutputLength = 100 * 1024
        val stdoutStr = stdout.toString().take(maxOutputLength)
        val stderrStr = stderr.toString().take(maxOutputLength)
        val stdoutTruncated = stdout.length > maxOutputLength
        val stderrTruncated = stderr.length > maxOutputLength

        val commandEscaped = command.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")

        return ToolResult.Success(
            """{"command": "$commandEscaped", "exitCode": $exitCode, "stdout": "${stdoutStr.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}", "stderr": "${stderrStr.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}", "stdoutTruncated": $stdoutTruncated, "stderrTruncated": $stderrTruncated, "executionTimeMs": ${System.currentTimeMillis() - startTime}}"""
        )
    }
}
