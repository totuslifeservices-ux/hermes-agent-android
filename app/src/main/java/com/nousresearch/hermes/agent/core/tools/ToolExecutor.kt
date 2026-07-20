package com.nousresearch.hermes.agent.core.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.nousresearch.hermes.agent.core.LlmToolCall
import com.nousresearch.hermes.agent.core.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * ToolExecutor — Executes tool calls from the LLM with full lifecycle management.
 *
 * Handles:
 * - Permission checks before execution
 * - Confirmation gates for sensitive operations
 * - Timeout enforcement per tool call
 * - Error recovery (retryable vs. fatal errors)
 * - Serial execution matching the Hermes Python agent pattern
 *
 * The executor follows a strict pipeline:
 *   1. Resolve tool by name
 *   2. Parse JSON arguments
 *   3. Check required permissions (return error if missing)
 *   4. Check confirmation gate (return PendingConfirmation if required)
 *   5. Execute with timeout
 *   6. Return result or formatted error
 */
class ToolExecutor(
    private val registry: ToolRegistry,
    private val defaultTimeoutSeconds: Long = 60L,
) {
    companion object {
        private const val TAG = "ToolExecutor"
        private const val MAX_ARGUMENTS_SIZE = 1024 * 100 // 100KB max JSON arguments
    }

    /**
     * Execute a single tool call from the LLM.
     *
     * @param call The LLM tool call (id, name, arguments JSON)
     * @param context Tool execution context
     * @param timeoutSeconds Per-call timeout (overrides default)
     * @return ToolResult
     */
    suspend fun execute(
        call: LlmToolCall,
        context: ToolContext,
        timeoutSeconds: Long = defaultTimeoutSeconds,
    ): ToolResult {
        val tool = registry.get(call.name)
        if (tool == null) {
            Log.w(TAG, "Tool not found: ${call.name}")
            return ToolResult.Error(
                message = "Unknown tool: '${call.name}'. Available tools: ${registry.getToolNames().joinToString(", ")}",
                recoverable = false,
            )
        }

        // ── 1. Parse JSON arguments ───────────────────────────────────
        val args = try {
            parseArguments(call.arguments)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse arguments for ${call.name}", e)
            return ToolResult.Error(
                message = "Invalid JSON arguments for '${call.name}': ${e.message}",
                recoverable = true,
            )
        }

        // ── 2. Check permissions ─────────────────────────────────────
        val permissionResult = checkPermissions(context, tool)
        if (permissionResult != null) {
            return permissionResult
        }

        // ── 3. Check confirmation gate ───────────────────────────────
        if (tool.requiresConfirmation) {
            Log.i(TAG, "Tool '${call.name}' requires user confirmation")
            return ToolResult.PendingConfirmation
        }

        // ── 4. Execute with timeout ──────────────────────────────────
        return try {
            withTimeout(timeoutSeconds * 1000L) {
                tool.execute(context, args)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Tool '${call.name}' timed out after ${timeoutSeconds}s")
            ToolResult.Error(
                message = "Tool '${call.name}' timed out after ${timeoutSeconds} seconds",
                recoverable = true,
            )
        } catch (e: CancellationException) {
            throw e // Re-throw cancellation
        } catch (e: Exception) {
            Log.e(TAG, "Tool '${call.name}' failed", e)
            ToolResult.Error(
                message = "Tool '${call.name}' failed: ${e.message ?: "Unknown error"}",
                recoverable = isRecoverable(e),
            )
        }
    }

    /**
     * Execute multiple tool calls serially, matching the Hermes Python agent pattern.
     *
     * Unlike parallel execution, serial execution ensures deterministic ordering
     * and allows each tool call's result to be fed back to the LLM before the
     * next call. This prevents race conditions when tools share state.
     *
     * @param calls List of tool calls from the LLM
     * @param context Tool execution context
     * @return List of (LlmToolCall, ToolResult) pairs
     */
    suspend fun executeSerial(
        calls: List<LlmToolCall>,
        context: ToolContext,
    ): List<Pair<LlmToolCall, ToolResult>> {
        return calls.map { call ->
            call to execute(call, context)
        }
    }

    /**
     * Execute multiple tool calls with a maximum iteration limit.
     * Returns results for all calls, truncated if limit is exceeded.
     */
    suspend fun executeWithLimit(
        calls: List<LlmToolCall>,
        context: ToolContext,
        maxIterations: Int = 25,
    ): List<Pair<LlmToolCall, ToolResult>> {
        val limited = calls.take(maxIterations)
        if (limited.size < calls.size) {
            Log.w(TAG, "Tool call limit exceeded: ${calls.size} calls, max $maxIterations")
        }
        return executeSerial(limited, context)
    }

    /**
     * Retry a failed tool call if the error is recoverable.
     *
     * @param call The original tool call
     * @param context Tool execution context
     * @param maxRetries Maximum number of retries
     * @return ToolResult
     */
    suspend fun executeWithRetry(
        call: LlmToolCall,
        context: ToolContext,
        maxRetries: Int = 3,
    ): ToolResult {
        repeat(maxRetries) {
            val result = execute(call, context)
            if (result !is ToolResult.Error || !result.recoverable) {
                return result
            }
            Log.i(TAG, "Retrying tool '${call.name}' (attempt ${it + 1}/$maxRetries)")
        }
        return ToolResult.Error(
            message = "Tool '${call.name}' failed after $maxRetries retries",
            recoverable = false,
        )
    }

    // ── Private Helpers ─────────────────────────────────────────────

    /**
     * Parse JSON arguments from the LLM tool call.
     * Supports both JSONObject and JSONArray formats.
     */
    private fun parseArguments(arguments: String): Map<String, Any?> {
        if (arguments.isBlank()) return emptyMap()

        if (arguments.length > MAX_ARGUMENTS_SIZE) {
            throw IllegalArgumentException("Arguments too large (${arguments.length} bytes)")
        }

        val json = JSONObject(arguments)
        return json.toMap()
    }

    /**
     * Check if all required permissions are granted.
     * Returns a ToolResult.Error if any permission is missing,
     * or null if all permissions are granted.
     */
    private fun checkPermissions(context: ToolContext, tool: HermesTool): ToolResult.Error? {
        val missingPermissions = tool.requiresPermissions.filter { permission ->
            !isPermissionGranted(context.androidContext, permission)
        }
        if (missingPermissions.isEmpty()) return null

        val missing = missingPermissions.joinToString(", ") { simplifyPermission(it) }
        return ToolResult.Error(
            message = "Missing required permission(s) for '${tool.name}': $missing. " +
                "Please grant these permissions in Settings > Apps > Hermes Agent > Permissions.",
            recoverable = true,
        )
    }

    /**
     * Check if an Android permission is granted.
     * Handles both old and new permission models.
     */
    private fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Simplify Android permission names for user-facing messages.
     */
    private fun simplifyPermission(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> "Microphone"
            Manifest.permission.CAMERA -> "Camera"
            Manifest.permission.ACCESS_FINE_LOCATION -> "Fine Location"
            Manifest.permission.ACCESS_COARSE_LOCATION -> "Coarse Location"
            Manifest.permission.READ_CONTACTS -> "Read Contacts"
            Manifest.permission.WRITE_CONTACTS -> "Write Contacts"
            Manifest.permission.READ_SMS -> "Read SMS"
            Manifest.permission.SEND_SMS -> "Send SMS"
            Manifest.permission.READ_CALENDAR -> "Read Calendar"
            Manifest.permission.WRITE_CALENDAR -> "Write Calendar"
            Manifest.permission.READ_EXTERNAL_STORAGE -> "Read Storage"
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> "Write Storage"
            Manifest.permission.READ_CALL_LOG -> "Read Call Log"
            Manifest.permission.READ_PHONE_STATE -> "Phone State"
            Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
            Manifest.permission.ACCESS_NETWORK_STATE -> "Network State"
            Manifest.permission.ACCESS_WIFI_STATE -> "WiFi State"
            Manifest.permission.INTERNET -> "Internet"
            Manifest.permission.BODY_SENSORS -> "Body Sensors"
            else -> permission.substringAfterLast('.')
        }
    }

    /**
     * Determine if an exception represents a recoverable error.
     */
    private fun isRecoverable(e: Exception): Boolean {
        return when (e) {
            is java.io.IOException -> true
            is java.net.SocketException -> true
            is java.net.ConnectException -> true
            is java.net.SocketTimeoutException -> true
            is InterruptedException -> true
            is SecurityException -> false
            is IllegalArgumentException -> false
            is IllegalStateException -> false
            else -> true // Default to recoverable for unknown errors
        }
    }

    /**
     * Convert JSONObject to Map<String, Any?> recursively.
     */
    private fun JSONObject.toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for (key in keys()) {
            map[key] = convertJsonValue(get(key))
        }
        return map
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertJsonValue(value: Any?): Any? {
        return when (value) {
            is JSONObject -> value.toMap()
            is JSONArray -> (0 until value.length()).map { convertJsonValue(value[it]) }
            is Number -> value
            is String -> value
            is Boolean -> value
            JSONObject.NULL -> null
            else -> value?.toString()
        }
    }
}
