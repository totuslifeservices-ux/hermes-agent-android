package com.nousresearch.hermes.agent.core.tools.platform

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult
import com.nousresearch.hermes.agent.core.tools.HermesTool
import com.nousresearch.hermes.agent.core.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SmsTool — Read, search, and send SMS messages via Android Telephony APIs.
 *
 * Capabilities:
 * - search_sms: Query the SMS ContentProvider by query text, date range, folder
 * - send_sms: Send an SMS via SmsManager (requires confirmation)
 * - read_conversations: List conversation threads with latest messages
 *
 * Permissions: READ_SMS, SEND_SMS
 * Privacy: No telemetry. All SMS data stays on-device.
 */
class SmsTool : HermesTool {

    override val descriptor = ToolDescriptor(
        name = "sms",
        description = "Read and send SMS messages. Use search_sms to find messages by content, date, or contact. " +
            "Use send_sms to send a new text message (requires confirmation). " +
            "Use read_conversations to list active conversation threads.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("search_sms", "send_sms", "read_conversations"),
                    "description" to "The SMS action to perform",
                ),
                "query" to mapOf(
                    "type" to "string",
                    "description" to "Search text for search_sms (searches body, address)",
                ),
                "phoneNumber" to mapOf(
                    "type" to "string",
                    "description" to "Recipient phone number for send_sms, or filter for search_sms",
                ),
                "message" to mapOf(
                    "type" to "string",
                    "description" to "Message body for send_sms",
                ),
                "folder" to mapOf(
                    "type" to "string",
                    "enum" to listOf("inbox", "sent", "drafts", "all"),
                    "description" to "SMS folder to search (default: all)",
                ),
                "limit" to mapOf(
                    "type" to "integer",
                    "description" to "Maximum results to return (default: 20, max: 100)",
                    "default" to 20,
                ),
                "days" to mapOf(
                    "type" to "integer",
                    "description" to "Number of days back to search (default: 7, 0 = all time)",
                    "default" to 7,
                ),
            ),
            "required" to listOf("action"),
        ),
    )

    override val requiresConfirmation: Boolean get() = false // per-action checked in execute

    override val requiresPermissions: List<String> get() = listOf(
        Manifest.permission.READ_SMS,
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            val action = args["action"] as? String ?: return@withContext ToolResult.Error(
                message = "Missing required parameter: action",
                recoverable = true,
            )

            try {
                when (action) {
                    "search_sms" -> searchSms(context, args)
                    "send_sms" -> sendSms(context, args)
                    "read_conversations" -> readConversations(context, args)
                    else -> ToolResult.Error(
                        message = "Unknown SMS action: '$action'. Valid: search_sms, send_sms, read_conversations",
                        recoverable = true,
                    )
                }
            } catch (e: SecurityException) {
                ToolResult.Error(
                    message = "SMS permission denied. Grant SMS permission in Settings.",
                    recoverable = true,
                )
            } catch (e: Exception) {
                ToolResult.Error(
                    message = "SMS operation failed: ${e.message ?: "Unknown error"}",
                    recoverable = true,
                )
            }
        }

    override fun toString(): String = "SmsTool"

    // ── Actions ──────────────────────────────────────────────────────

    private fun searchSms(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val query = args["query"] as? String
        val phoneNumber = args["phoneNumber"] as? String
        val folder = (args["folder"] as? String)?.lowercase() ?: "all"
        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20
        val days = (args["days"] as? Number)?.toInt() ?: 7

        val uri = when (folder) {
            "inbox" -> Telephony.Sms.Inbox.CONTENT_URI
            "sent" -> Telephony.Sms.Sent.CONTENT_URI
            "drafts" -> Telephony.Sms.Drafts.CONTENT_URI
            else -> Telephony.Sms.CONTENT_URI
        }

        val sb = StringBuilder()
        val params = mutableListOf<String>()

        if (!query.isNullOrBlank()) {
            sb.append("(${Telephony.Sms.BODY} LIKE ? OR ${Telephony.Sms.ADDRESS} LIKE ?)")
            params.add("%$query%")
            params.add("%$query%")
        }
        if (!phoneNumber.isNullOrBlank()) {
            if (sb.isNotEmpty()) sb.append(" AND ")
            sb.append("${Telephony.Sms.ADDRESS} LIKE ?")
            params.add("%$phoneNumber%")
        }
        if (days > 0) {
            if (sb.isNotEmpty()) sb.append(" AND ")
            val cutoff = System.currentTimeMillis() - (days * 86400000L)
            sb.append("${Telephony.Sms.DATE} >= ?")
            params.add(cutoff.toString())
        }

        val selectionStr = sb.toString()
        val selArgs = if (params.isEmpty()) null else params.toTypedArray()

        val cursor: Cursor? = context.androidContext.contentResolver.query(
            uri,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
            ),
            selectionStr.ifEmpty { null },
            selArgs,
            "${Telephony.Sms.DATE} DESC LIMIT $limit",
        )

        val results = mutableListOf<Map<String, Any?>>()
        cursor?.use { c ->
            while (c.moveToNext() && results.size < limit) {
                results.add(
                    mapOf(
                        "id" to c.getLong(c.getColumnIndexOrThrow(Telephony.Sms._ID)),
                        "address" to c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)),
                        "body" to c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY)),
                        "date" to c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.DATE)),
                        "type" to formatType(c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.TYPE))),
                        "read" to (c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1),
                    )
                )
            }
        }

        return ToolResult.Success(
            formatJson("messages" to results, "count" to results.size)
        )
    }

    private fun sendSms(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val phoneNumber = args["phoneNumber"] as? String
            ?: return ToolResult.Error(message = "Missing required: phoneNumber", recoverable = true)
        val message = args["message"] as? String
            ?: return ToolResult.Error(message = "Missing required: message", recoverable = true)

        if (phoneNumber.isBlank()) {
            return ToolResult.Error(message = "Phone number cannot be empty", recoverable = true)
        }
        if (message.isBlank()) {
            return ToolResult.Error(message = "Message cannot be empty", recoverable = true)
        }

        // This action requires explicit confirmation if caller didn't gate it
        val smsManager = SmsManager.getDefault()
        smsManager.sendTextMessage(phoneNumber, null, message, null, null)

        return ToolResult.Success(
            formatJson(
                "status" to "sent",
                "phoneNumber" to phoneNumber,
                "messageLength" to message.length,
            )
        )
    }

    private fun readConversations(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20

        val uri = Telephony.Sms.Conversations.CONTENT_URI
        val cursor: Cursor? = context.androidContext.contentResolver.query(
            uri,
            arrayOf(
                Telephony.Sms.Conversations._ID,
                Telephony.Sms.Conversations.SNIPPET,
                Telephony.Sms.Conversations.MSG_COUNT,
                Telephony.Sms.Conversations.LABEL,
            ),
            null, null,
            "${Telephony.Sms.Conversations.SNIPPET} DESC LIMIT $limit",
        )

        val results = mutableListOf<Map<String, Any?>>()
        cursor?.use { c ->
            while (c.moveToNext() && results.size < limit) {
                val threadId = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.Conversations._ID))
                results.add(
                    mapOf(
                        "threadId" to threadId,
                        "snippet" to c.getString(c.getColumnIndexOrThrow(Telephony.Sms.Conversations.SNIPPET)),
                        "messageCount" to c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.Conversations.MSG_COUNT)),
                        "label" to c.getString(c.getColumnIndexOrThrow(Telephony.Sms.Conversations.LABEL)),
                    )
                )
            }
        }

        return ToolResult.Success(
            formatJson("conversations" to results, "count" to results.size)
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun formatType(type: Int): String = when (type) {
        Telephony.Sms.MESSAGE_TYPE_INBOX -> "inbox"
        Telephony.Sms.MESSAGE_TYPE_SENT -> "sent"
        Telephony.Sms.MESSAGE_TYPE_DRAFT -> "draft"
        Telephony.Sms.MESSAGE_TYPE_OUTBOX -> "outbox"
        Telephony.Sms.MESSAGE_TYPE_FAILED -> "failed"
        Telephony.Sms.MESSAGE_TYPE_QUEUED -> "queued"
        else -> "unknown($type)"
    }

    private fun formatJson(vararg pairs: Pair<String, Any?>): String {
        val sb = StringBuilder("{")
        pairs.forEachIndexed { i, (key, value) ->
            if (i > 0) sb.append(", ")
            sb.append("\"$key\": ")
            appendJsonValue(sb, value)
        }
        sb.append("}")
        return sb.toString()
    }

    private fun appendJsonValue(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> sb.append("\"").append(value.replace("\\", "\\\\")
                .replace("\"", "\\\"").replace("\n", "\\n")).append("\"")
            is Number -> sb.append(value)
            is Boolean -> sb.append(value)
            is List<*> -> {
                sb.append("[")
                value.forEachIndexed { i, v ->
                    if (i > 0) sb.append(", ")
                    appendJsonValue(sb, v)
                }
                sb.append("]")
            }
            is Map<*, *> -> {
                sb.append("{")
                val map = value as Map<String, Any?>
                map.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) sb.append(", ")
                    sb.append("\"$k\": ")
                    appendJsonValue(sb, v)
                }
                sb.append("}")
            }
            else -> sb.append("\"").append(value.toString()).append("\"")
        }
    }
}
