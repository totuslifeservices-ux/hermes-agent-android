package com.nousresearch.hermes.agent.core.tools

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import android.content.Intent
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult
import kotlinx.coroutines.CancellationException

/**
 * HermesTool — Base interface for all Android platform tools.
 *
 * Each tool wraps an Android capability (SMS, Contacts, Camera, etc.)
 * and exposes it as a callable function with JSON Schema parameters
 * that can be advertised to an LLM via OpenAI-compatible tool definitions.
 *
 * Design principles:
 * - Every tool is fully suspend/async via coroutines
 * - Tools self-declare required Android runtime permissions
 * - Tools with side effects declare requiresConfirmation = true
 * - No telemetry, no data collection beyond explicit user requests
 * - All Android Context lifecycle is managed through ToolContext
 */
interface HermesTool {
    /**
     * Tool descriptor advertised to the LLM — name, description, JSON Schema parameters.
     */
    val descriptor: ToolDescriptor

    /**
     * Convenience accessor for the tool name.
     */
    val name: String get() = descriptor.name

    /**
     * Whether this tool requires explicit user confirmation before executing.
     * Set to true for tools with side effects (sending messages, writing files, etc.).
     */
    val requiresConfirmation: Boolean get() = false

    /**
     * Android runtime permissions required by this tool.
     * Checked automatically by ToolExecutor before invocation.
     */
    val requiresPermissions: List<String> get() = emptyList()

    /**
     * Execute the tool with parsed arguments.
     *
     * @param context Tool execution context containing Android Context, activity launchers
     * @param args Parsed argument map matching the JSON Schema descriptor.parameters
     * @return ToolResult — Success, Error, or PendingConfirmation
     */
    suspend fun execute(context: ToolContext, args: Map<String, Any?>): ToolResult
}

/**
 * ToolContext — Execution context passed to every HermesTool invocation.
 *
 * Wraps the Android application/activity context and provides access to
 * activity result launchers needed for camera capture, file picker, etc.
 *
 * @property androidContext Root Android application context (safe for ContentResolver, system services)
 * @property activityContext Optional activity context for launching intents that need an Activity
 * @property activityResultLauncher Optional launcher for startActivityForResult patterns (camera, file pick)
 */
data class ToolContext(
    val androidContext: Context,
    val activityContext: Context? = null,
    val activityResultLauncher: ActivityResultLauncher<Intent>? = null,
) {
    /**
     * Resolve the best available context for intent launching.
     * Prefers activity context when available, falls back to application context.
     */
    val resolveContext: Context get() = activityContext ?: androidContext

    /**
     * Check if the tool has access to an activity for launching intents.
     */
    val hasActivity: Boolean get() = activityContext != null
}
