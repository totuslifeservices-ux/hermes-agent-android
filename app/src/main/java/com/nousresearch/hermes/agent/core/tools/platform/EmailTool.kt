package com.nousresearch.hermes.agent.core.tools.platform

import android.Manifest
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult
import com.nousresearch.hermes.agent.core.tools.HermesTool
import com.nousresearch.hermes.agent.core.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * EmailTool — Send emails and search the inbox on Android.
 *
 * Capabilities:
 * - send_email: Open email composer via ACTION_SENDTO intent for any email app
 * - search_inbox: Search the Gmail/Latin/Email content provider for recent messages
 *
 * This tool uses Intents for sending (no direct SMTP dependency) and the
 * Email content provider for reading. The content provider approach works
 * with the default Android Email app and Gmail when properly configured.
 *
 * Permissions: None required for sending via Intent.
 * Reading may need permissions depending on provider.
 * Privacy: No telemetry. Email composition stays in the user's email app.
 */
class EmailTool : HermesTool {

    override val descriptor = ToolDescriptor(
        name = "email",
        description = "Send emails via the device email app, and search the inbox/email content provider. " +
            "Use send_email to compose an email (opens the default email app). " +
            "Use search_inbox to find emails by subject, sender, or content.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("send_email", "search_inbox"),
                    "description" to "The email action to perform",
                ),
                "to" to mapOf(
                    "type" to "string",
                    "description" to "Recipient email address(es), comma-separated for multiple",
                ),
                "cc" to mapOf(
                    "type" to "string",
                    "description" to "CC recipient email address(es), comma-separated",
                ),
                "bcc" to mapOf(
                    "type" to "string",
                    "description" to "BCC recipient email address(es), comma-separated",
                ),
                "subject" to mapOf(
                    "type" to "string",
                    "description" to "Email subject line",
                ),
                "body" to mapOf(
                    "type" to "string",
                    "description" to "Email body text (plain text)",
                ),
                "query" to mapOf(
                    "type" to "string",
                    "description" to "Search query for search_inbox (matches subject, from, body)",
                ),
                "limit" to mapOf(
                    "type" to "integer",
                    "description" to "Maximum results for search_inbox (default: 20, max: 100)",
                    "default" to 20,
                ),
            ),
            "required" to listOf("action"),
        ),
    )

    override val requiresConfirmation: Boolean get() = false // intents open externally

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            val action = args["action"] as? String ?: return@withContext ToolResult.Error(
                message = "Missing required parameter: action",
                recoverable = true,
            )

            try {
                when (action) {
                    "send_email" -> sendEmail(context, args)
                    "search_inbox" -> searchInbox(context, args)
                    else -> ToolResult.Error(
                        message = "Unknown email action: '$action'. Valid: send_email, search_inbox",
                        recoverable = true,
                    )
                }
            } catch (e: Exception) {
                ToolResult.Error(
                    message = "Email operation failed: ${e.message ?: "Unknown error"}",
                    recoverable = true,
                )
            }
        }

    override fun toString(): String = "EmailTool"

    // ── Actions ──────────────────────────────────────────────────────

    private fun sendEmail(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val to = args["to"] as? String
        val cc = args["cc"] as? String
        val bcc = args["bcc"] as? String
        val subject = args["subject"] as? String ?: ""
        val body = args["body"] as? String ?: ""

        if (to.isNullOrBlank()) {
            return ToolResult.Error(
                message = "Missing required parameter: to (recipient email address)",
                recoverable = true,
            )
        }

        val uri = Uri.parse("mailto:${Uri.encode(to)}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra(Intent.EXTRA_EMAIL, to.split(",").map { it.trim() }.toTypedArray())
            if (!cc.isNullOrBlank()) {
                putExtra(Intent.EXTRA_CC, cc.split(",").map { it.trim() }.toTypedArray())
            }
            if (!bcc.isNullOrBlank()) {
                putExtra(Intent.EXTRA_BCC, bcc.split(",").map { it.trim() }.toTypedArray())
            }
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Verify there's an app to handle this
        if (intent.resolveActivity(context.resolveContext.packageManager) == null) {
            return ToolResult.Error(
                message = "No email app found on device to handle the send request",
                recoverable = true,
            )
        }

        context.resolveContext.startActivity(intent)

        return ToolResult.Success(
            """{"status": "opened_composer", "to": "$to", "subject": "${subject.replace("\"", "\\\"")}"}"""
        )
    }

    private fun searchInbox(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val query = args["query"] as? String
        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20

        // First try the Gmail content provider
        val gmailUri = Uri.parse("content://gmail-ls/labels/^i")
        var results = queryContentProvider(context, gmailUri, query, limit)

        // Try the default Android Email content provider
        if (results.isEmpty()) {
            val emailUri = Uri.parse("content://com.android.email.provider/mailbox")
            results = queryContentProvider(context, emailUri, query, limit)
        }

        if (results.isEmpty()) {
            return ToolResult.Success(
                """{"messages": [], "count": 0, "note": "No email content provider found. " +
                    "Gmail and the default Email app may not expose a readable content provider. " +
                    "Use send_email to compose emails normally."}"""
            )
        }

        return ToolResult.Success(
            """{"messages": ${results.toJsonArray()}, "count": ${results.size}}"""
        )
    }

    private fun queryContentProvider(
        context: ToolContext,
        uri: Uri,
        query: String?,
        limit: Int,
    ): List<Map<String, Any?>> {
        return try {
            val cr = context.androidContext.contentResolver
            val cursor = cr.query(uri, null, null, null, null) ?: return emptyList()
            val results = mutableListOf<Map<String, Any?>>()
            cursor.use { c ->
                val columns = c.columnNames
                while (c.moveToNext() && results.size < limit) {
                    val row = mutableMapOf<String, Any?>()
                    for (col in columns) {
                        val value = getCursorValue(c, col)
                        // Apply search filter if query provided
                        if (!query.isNullOrBlank()) {
                            val strVal = value?.toString() ?: ""
                            if (strVal.contains(query, ignoreCase = true)) {
                                row[col] = value
                            }
                        } else {
                            row[col] = value
                        }
                    }
                    if (row.isNotEmpty()) {
                        results.add(row)
                    }
                }
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getCursorValue(cursor: Cursor, columnName: String): Any? {
        val index = cursor.getColumnIndex(columnName)
        if (index < 0) return null
        return when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
            Cursor.FIELD_TYPE_BLOB -> "[BLOB: ${cursor.getBlob(index)?.size ?: 0} bytes]"
            else -> cursor.getString(index)
        }
    }

    private fun List<Map<String, Any?>>.toJsonArray(): String {
        val sb = StringBuilder("[")
        forEachIndexed { i, map ->
            if (i > 0) sb.append(", ")
            sb.append("{")
            map.entries.forEachIndexed { j, (key, value) ->
                if (j > 0) sb.append(", ")
                sb.append("\"${key.replace("\"", "\\\"")}\": ")
                when (value) {
                    null -> sb.append("null")
                    is String -> sb.append("\"").append(value.replace("\\", "\\\\")
                        .replace("\"", "\\\"").replace("\n", "\\n")).append("\"")
                    is Number -> sb.append(value)
                    is Boolean -> sb.append(value)
                    else -> sb.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"")
                }
            }
            sb.append("}")
        }
        sb.append("]")
        return sb.toString()
    }
}
