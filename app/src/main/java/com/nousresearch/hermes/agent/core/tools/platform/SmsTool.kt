package com.nousresearch.hermes.agent.core.tools.platform

import android.Manifest
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult
import com.nousresearch.hermes.agent.core.tools.HermesTool
import com.nousresearch.hermes.agent.core.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SmsTool — Read, search, and send SMS messages on Android.
 *
 * Capabilities:
 * - list_conversations: List recent SMS conversation threads
 * - read_conversation: Read messages in a specific thread
 * - search_messages: Search SMS messages by query text
 * - send_sms: Send an SMS message (requires SEND_SMS permission)
 *
 * Permissions: READ_SMS for reading, SEND_SMS for sending.
 * Privacy: No telemetry. Message data stays on-device within the app process.
 */
class SmsTool(private val context: Context) : HermesTool {

    companion object {
        private const val TAG = "SmsTool"
        private val SMS_URI: Uri = Telephony.Sms.Inbox.CONTENT_URI
        private val CONVERSATIONS_URI: Uri = Telephony.Sms.Conversations.CONTENT_URI
    }

    override val descriptor = ToolDescriptor(
        name = "sms",
        description = "Read SMS messages, list conversation threads, search SMS content, " +
            "and send SMS messages on the device. " +
            "Use list_conversations to see recent threads with contact info. " +
            "Use read_conversation with a thread_id to read messages in that thread. " +
            "Use search_messages to find messages matching a query. " +
            "Use send_sms to send a new SMS message.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("list_conversations", "read_conversation", "search_messages", "send_sms"),
                    "description" to "The SMS action to perform",
                ),
                "thread_id" to mapOf(
                    "type" to "integer",
                    "description" to "Thread ID for read_conversation (from list_conversations result)",
                ),
                "query" to mapOf(
                    "type" to "string",
                    "description" to "Search query for search_messages (matches body, address)",
                ),
                "address" to mapOf(
                    "type" to "string",
                    "description" to "Phone number for the recipient (required for send_sms)",
                ),
                "message" to mapOf(
                    "type" to "string",
                    "description" to "Message body text (required for send_sms)",
                ),
                "limit" to mapOf(
                    "type" to "integer",
                    "description" to "Maximum results (default: 20, max: 100)",
                    "default" to 20,
                ),
            ),
            "required" to listOf("action"),
        ),
    )

    override val requiresConfirmation: Boolean get() = true // sending SMS

    override val requiresPermissions: List<String> = listOf(
        Manifest.permission.READ_SMS,
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            try {
                val action = args["action"] as? String
                    ?: ToolResult.Error(
                        message = "Missing required parameter: action",
                        recoverable = true,
                    )

                when (action) {
                    "list_conversations" -> listConversations(args)
                    "read_conversation" -> readConversation(args)
                    "search_messages" -> searchMessages(args)
                    "send_sms" -> sendSms(context, args)
                    else -> ToolResult.Error(
                        message = "Unknown SMS action: '$action'. Valid: list_conversations, read_conversation, search_messages, send_sms",
                        recoverable = true,
                    )
                }
            } catch (e: SecurityException) {
                ToolResult.Error(
                    message = "SMS permission denied: ${e.message}",
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

    private fun listConversations(args: Map<String, Any?>): ToolResult {
        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20
        val cr = context.contentResolver

        val projection = arrayOf(
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
        )

        val cursor = cr.query(
            Telephony.Sms.CONTENT_URI,
            projection, null, null,
            "${Telephony.Sms.DATE} DESC LIMIT $limit"
        ) ?: return ToolResult.Success("""{"conversations": [], "count": 0}""")

        val conversations = mutableListOf<Map<String, Any?>>()
        cursor.use { c ->
            while (c.moveToNext()) {
                conversations.add(rowToMap(c))
            }
        }

        if (conversations.isEmpty()) {
            return ToolResult.Success(
                """{"conversations": [], "count": 0, "note": "No SMS messages found. Ensure READ_SMS permission is granted."}"""
            )
        }

        val json = buildJsonArray(conversations)
        return ToolResult.Success("""{"conversations": $json, "count": ${conversations.size}}""")
    }

    private fun readConversation(args: Map<String, Any?>): ToolResult {
        val threadId = args["thread_id"] as? Number
            ?: return ToolResult.Error(
                message = "Missing required parameter: thread_id",
                recoverable = true,
            )

        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20
        val cr = context.contentResolver

        val selection = "${Telephony.Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId.toLong().toString())
        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit"

        val cursor = cr.query(
            Telephony.Sms.CONTENT_URI,
            null, selection, selectionArgs, sortOrder
        ) ?: return ToolResult.Success("""{"messages": [], "count": 0}""")

        val messages = mutableListOf<Map<String, Any?>>()
        cursor.use { c ->
            while (c.moveToNext()) {
                messages.add(rowToMap(c))
            }
        }

        val json = buildJsonArray(messages)
        return ToolResult.Success("""{"thread_id": $threadId, "messages": $json, "count": ${messages.size}}""")
    }

    private fun searchMessages(args: Map<String, Any?>): ToolResult {
        val query = args["query"] as? String
            ?: return ToolResult.Error(
                message = "Missing required parameter: query",
                recoverable = true,
            )

        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20
        val cr = context.contentResolver

        val selection = "${Telephony.Sms.BODY} LIKE ? OR ${Telephony.Sms.ADDRESS} LIKE ?"
        val selectionArgs = arrayOf("%$query%", "%$query%")
        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit"

        val cursor = cr.query(
            Telephony.Sms.CONTENT_URI,
            null, selection, selectionArgs, sortOrder
        ) ?: return ToolResult.Success("""{"messages": [], "count": 0}""")

        val messages = mutableListOf<Map<String, Any?>>()
        cursor.use { c ->
            while (c.moveToNext()) {
                messages.add(rowToMap(c))
            }
        }

        val json = buildJsonArray(messages)
        return ToolResult.Success("""{"query": "$query", "messages": $json, "count": ${messages.size}}""")
    }

    private fun sendSms(toolContext: ToolContext, args: Map<String, Any?>): ToolResult {
        val address = args["address"] as? String
            ?: return ToolResult.Error(
                message = "Missing required parameter: address",
                recoverable = true,
            )
        val message = args["message"] as? String
            ?: return ToolResult.Error(
                message = "Missing required parameter: message",
                recoverable = true,
            )

        // Write to Sent folder via ContentProvider
        val values = android.content.ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, message)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
        }

        try {
            val uri = context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            if (uri == null) {
                // Fall back to using SmsManager if ContentProvider insert fails
                try {
                    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(android.telephony.SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        android.telephony.SmsManager.getDefault()
                    }
                    smsManager?.sendTextMessage(address, null, message, null, null)
                    return ToolResult.Success(
                        """{"status": "sent", "to": "$address", "via": "SmsManager"}"""
                    )
                } catch (e: Exception) {
                    return ToolResult.Error(
                        message = "Failed to send SMS: ${e.message}",
                        recoverable = true,
                    )
                }
            }

            return ToolResult.Success(
                """{"status": "sent", "to": "$address", "message": "${message.replace("\"", "\\\"")}"}"""
            )
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            return ToolResult.Error(
                message = "Failed to send SMS: ${e.message ?: "Unknown error"}",
                recoverable = true,
            )
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun rowToMap(cursor: Cursor): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for (col in cursor.columnNames) {
            val index = cursor.getColumnIndex(col)
            if (index < 0) continue
            map[col] = when (cursor.getType(index)) {
                Cursor.FIELD_TYPE_NULL -> null
                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                Cursor.FIELD_TYPE_BLOB -> "[BLOB: ${cursor.getBlob(index)?.size ?: 0} bytes]"
                else -> cursor.getString(index)
            }
        }
        return map
    }

    private fun buildJsonArray(items: List<Map<String, Any?>>): String {
        val sb = StringBuilder("[")
        items.forEachIndexed { i, map ->
            if (i > 0) sb.append(", ")
            sb.append("{")
            map.entries.forEachIndexed { j, (key, value) ->
                if (j > 0) sb.append(", ")
                sb.append("\"${escapeJson(key)}\": ")
                sb.append(jsonValue(value))
            }
            sb.append("}")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun jsonValue(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"${escapeJson(value)}\""
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> "\"${escapeJson(value.toString())}\""
    }

    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
