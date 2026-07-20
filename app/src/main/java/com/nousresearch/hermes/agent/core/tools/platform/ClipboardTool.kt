package com.nousresearch.hermes.agent.core.tools.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult
import com.nousresearch.hermes.agent.core.tools.HermesTool
import com.nousresearch.hermes.agent.core.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ClipboardTool — Read and write the system clipboard.
 *
 * Capabilities:
 * - read_clipboard: Read current clipboard content (text, URI description)
 * - write_clipboard: Write text to the system clipboard
 *
 * Uses Android's ClipboardManager system service.
 * No permissions required (clipboard access is implicit).
 *
 * Privacy: Clipboard reads only happen on explicit user request.
 * No telemetry, no clipboard monitoring.
 */
class ClipboardTool : HermesTool {

    override val descriptor = ToolDescriptor(
        name = "clipboard",
        description = "Read from and write to the device clipboard. " +
            "Use read_clipboard to retrieve the current clipboard content. " +
            "Use write_clipboard to copy text to the clipboard.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("read_clipboard", "write_clipboard"),
                    "description" to "The clipboard action to perform",
                ),
                "text" to mapOf(
                    "type" to "string",
                    "description" to "Text to copy to clipboard (for write_clipboard)",
                ),
                "label" to mapOf(
                    "type" to "string",
                    "description" to "Optional label for the clipboard entry (for write_clipboard)",
                ),
            ),
            "required" to listOf("action"),
        ),
    )

    override val requiresConfirmation: Boolean get() = false

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.Main) {
            val action = args["action"] as? String ?: return@withContext ToolResult.Error(
                message = "Missing required parameter: action",
                recoverable = true,
            )

            try {
                when (action) {
                    "read_clipboard" -> readClipboard(context)
                    "write_clipboard" -> writeClipboard(context, args)
                    else -> ToolResult.Error(
                        message = "Unknown clipboard action: '$action'. Valid: read_clipboard, write_clipboard",
                        recoverable = true,
                    )
                }
            } catch (e: Exception) {
                ToolResult.Error(
                    message = "Clipboard operation failed: ${e.message ?: "Unknown error"}",
                    recoverable = true,
                )
            }
        }

    override fun toString(): String = "ClipboardTool"

    // ── Actions ──────────────────────────────────────────────────────

    private fun readClipboard(context: ToolContext): ToolResult {
        val clipboard = context.androidContext.getSystemService(Context.CLIPBOARD_SERVICE)
            as? ClipboardManager
            ?: return ToolResult.Error(
                message = "Clipboard service not available",
                recoverable = false,
            )

        val clipData = clipboard.primaryClip
        if (clipData == null || clipData.itemCount == 0) {
            return ToolResult.Success("""{"text": null, "hasContent": false}""")
        }

        val item = clipData.getItemAt(0)
        val text = item.text?.toString()
        val uri = item.uri?.toString()
        val description = clipData.description?.label?.toString()

        val result = mutableMapOf<String, Any?>(
            "hasContent" to true,
            "text" to text,
            "uri" to uri,
            "label" to description,
            "mimeType" to clipData.description?.mimeType,
        )

        return ToolResult.Success(toJson(result))
    }

    private fun writeClipboard(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val text = args["text"] as? String
            ?: return ToolResult.Error(
                message = "Missing required parameter: text",
                recoverable = true,
            )

        val label = args["label"] as? String ?: "Hermes Agent"

        val clipboard = context.androidContext.getSystemService(Context.CLIPBOARD_SERVICE)
            as? ClipboardManager
            ?: return ToolResult.Error(
                message = "Clipboard service not available",
                recoverable = false,
            )

        val clipData = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clipData)

        return ToolResult.Success(
            """{"status": "copied", "length": ${text.length}, "label": "${label.replace("\"", "\\\"")}"}"""
        )
    }

    companion object {
        private fun toJson(map: Map<String, Any?>): String {
            val sb = StringBuilder("{")
            map.entries.forEachIndexed { i, (key, value) ->
                if (i > 0) sb.append(", ")
                sb.append("\"$key\": ")
                when (value) {
                    null -> sb.append("null")
                    is String -> sb.append("\"").append(value.replace("\\", "\\\\")
                        .replace("\"", "\\\"").replace("\n", "\\n")).append("\"")
                    is Number -> sb.append(value)
                    is Boolean -> sb.append(value)
                    else -> sb.append("\"").append(value.toString()).append("\"")
                }
            }
            sb.append("}")
            return sb.toString()
        }
    }
}
