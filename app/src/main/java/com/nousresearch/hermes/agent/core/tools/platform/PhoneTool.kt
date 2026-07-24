package com.nousresearch.hermes.agent.core.tools.platform

import android.Manifest
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.telephony.TelephonyManager
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult
import com.nousresearch.hermes.agent.core.tools.HermesTool
import com.nousresearch.hermes.agent.core.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PhoneTool — Access call history, dial numbers, and retrieve device info.
 *
 * Capabilities:
 * - call_log: Read the recent call history
 * - dial_number: Open the phone dialer with a number (requires confirmation)
 * - device_info: Get device model, OS version, network operator info
 *
 * Permissions: READ_CALL_LOG (for call_log), READ_PHONE_STATE (for device_info)
 * Privacy: Call log data stays on-device. No telemetry.
 */
class PhoneTool : HermesTool {

    override val descriptor = ToolDescriptor(
        name = "phone",
        description = "Access call history, dial phone numbers, and retrieve device information. " +
            "Use call_log to read recent call history. " +
            "Use dial_number to open the phone dialer (requires confirmation). " +
            "Use device_info to get device model, OS version, and carrier info.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("call_log", "dial_number", "device_info"),
                    "description" to "The phone action to perform",
                ),
                "phoneNumber" to mapOf(
                    "type" to "string",
                    "description" to "Phone number to dial (for dial_number)",
                ),
                "limit" to mapOf(
                    "type" to "integer",
                    "description" to "Maximum call log entries to return (default: 20, max: 100)",
                    "default" to 20,
                ),
                "days" to mapOf(
                    "type" to "integer",
                    "description" to "Number of days of call history to retrieve (default: 7, 0 = all)",
                    "default" to 7,
                ),
            ),
            "required" to listOf("action"),
        ),
    )

    override val requiresConfirmation: Boolean get() = false // per-action

    override val requiresPermissions: List<String> get() = listOf(
        Manifest.permission.READ_CALL_LOG,
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            val action = args["action"] as? String ?: return@withContext ToolResult.Error(
                message = "Missing required parameter: action",
                recoverable = true,
            )

            try {
                when (action) {
                    "call_log" -> callLog(context, args)
                    "dial_number" -> dialNumber(context, args)
                    "device_info" -> deviceInfo(context)
                    else -> ToolResult.Error(
                        message = "Unknown phone action: '$action'. Valid: call_log, dial_number, device_info",
                        recoverable = true,
                    )
                }
            } catch (e: SecurityException) {
                ToolResult.Error(
                    message = "Phone permission denied. Grant Phone/Call Log access in Settings.",
                    recoverable = true,
                )
            } catch (e: Exception) {
                ToolResult.Error(
                    message = "Phone operation failed: ${e.message ?: "Unknown error"}",
                    recoverable = true,
                )
            }
        }

    override fun toString(): String = "PhoneTool"

    // ── Actions ──────────────────────────────────────────────────────

    private fun callLog(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20
        val days = (args["days"] as? Number)?.toInt() ?: 7

        val uri = CallLog.Calls.CONTENT_URI

        val selection = if (days > 0) {
            val cutoff = System.currentTimeMillis() - (days * 86400000L)
            "${CallLog.Calls.DATE} >= ?"
        } else null

        val selectionArgs = if (days > 0) {
            arrayOf((System.currentTimeMillis() - (days * 86400000L)).toString())
        } else null

        val cursor: Cursor? = context.androidContext.contentResolver.query(
            uri,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.COUNTRY_ISO,
            ),
            selection,
            selectionArgs,
            "${CallLog.Calls.DATE} DESC LIMIT $limit",
        )

        val calls = mutableListOf<Map<String, Any?>>()
        cursor?.use { c ->
            while (c.moveToNext() && calls.size < limit) {
                calls.add(
                    mapOf(
                        "id" to c.getLong(c.getColumnIndexOrThrow(CallLog.Calls._ID)),
                        "number" to c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)),
                        "name" to c.getString(c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)),
                        "type" to formatCallType(c.getInt(c.getColumnIndexOrThrow(CallLog.Calls.TYPE))),
                        "date" to c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DATE)),
                        "duration" to c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DURATION)),
                        "country" to c.getString(c.getColumnIndexOrThrow(CallLog.Calls.COUNTRY_ISO)),
                    )
                )
            }
        }

        return ToolResult.Success(toJson("calls" to calls, "count" to calls.size))
    }

    private fun dialNumber(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val phoneNumber = args["phoneNumber"] as? String
            ?: return ToolResult.Error(
                message = "Missing required parameter: phoneNumber",
                recoverable = true,
            )

        if (phoneNumber.isBlank()) {
            return ToolResult.Error(
                message = "Phone number cannot be empty",
                recoverable = true,
            )
        }

        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(context.resolveContext.packageManager) == null) {
            return ToolResult.Error(
                message = "No phone dialer app found on device",
                recoverable = true,
            )
        }

        context.resolveContext.startActivity(intent)

        return ToolResult.Success(
            """{"status": "opened_dialer", "phoneNumber": "${phoneNumber.replace("\"", "\\\"")}"}"""
        )
    }

    private fun deviceInfo(context: ToolContext): ToolResult {
        val tm = context.androidContext.getSystemService(android.content.Context.TELEPHONY_SERVICE)
            as? TelephonyManager

        val info = mutableMapOf<String, Any?>(
            "device" to mapOf(
                "model" to android.os.Build.MODEL,
                "manufacturer" to android.os.Build.MANUFACTURER,
                "brand" to android.os.Build.BRAND,
                "product" to android.os.Build.PRODUCT,
                "device" to android.os.Build.DEVICE,
                "hardware" to android.os.Build.HARDWARE,
            ),
            "os" to mapOf(
                "sdk" to android.os.Build.VERSION.SDK_INT,
                "release" to android.os.Build.VERSION.RELEASE,
                "codename" to android.os.Build.VERSION.CODENAME,
                "incremental" to android.os.Build.VERSION.INCREMENTAL,
            ),
        )

        // Telephony info (requires READ_PHONE_STATE)
        if (tm != null) {
            try {
                info["network"] = mapOf(
                    "networkOperator" to tm.networkOperatorName,
                    "networkCountry" to tm.networkCountryIso,
                    "phoneType" to phoneTypeString(tm.phoneType),
                    "simOperator" to tm.simOperatorName,
                    "simCountry" to tm.simCountryIso,
                    
                )
            } catch (_: SecurityException) {
                info["network"] = mapOf("error" to "READ_PHONE_STATE permission required")
            }
        }

        return ToolResult.Success(toJson(info))
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun formatCallType(type: Int): String = when (type) {
        CallLog.Calls.INCOMING_TYPE -> "incoming"
        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
        CallLog.Calls.MISSED_TYPE -> "missed"
        CallLog.Calls.VOICEMAIL_TYPE -> "voicemail"
        CallLog.Calls.REJECTED_TYPE -> "rejected"
        CallLog.Calls.BLOCKED_TYPE -> "blocked"
        CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> "answered_externally"
        else -> "unknown($type)"
    }

    private fun phoneTypeString(type: Int): String = when (type) {
        TelephonyManager.PHONE_TYPE_NONE -> "none"
        TelephonyManager.PHONE_TYPE_GSM -> "gsm"
        TelephonyManager.PHONE_TYPE_CDMA -> "cdma"
        TelephonyManager.PHONE_TYPE_SIP -> "sip"
        else -> "unknown($type)"
    }

    companion object {
        private fun toJson(map: Map<String, Any?>): String {
            val sb = StringBuilder("{")
            map.entries.forEachIndexed { i, (key, value) ->
                if (i > 0) sb.append(", ")
                sb.append("\"$key\": ")
                appendValue(sb, value)
            }
            sb.append("}")
            return sb.toString()
        }

        private fun toJson(vararg pairs: Pair<String, Any?>): String {
            val sb = StringBuilder("{")
            pairs.forEachIndexed { i, (key, value) ->
                if (i > 0) sb.append(", ")
                sb.append("\"$key\": ")
                appendValue(sb, value)
            }
            sb.append("}")
            return sb.toString()
        }

        private fun appendValue(sb: StringBuilder, value: Any?) {
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
                        appendValue(sb, v)
                    }
                    sb.append("]")
                }
                is Map<*, *> -> {
                    sb.append("{")
                    @Suppress("UNCHECKED_CAST")
                    val map = value as Map<String, Any?>
                    map.entries.forEachIndexed { i, (k, v) ->
                        if (i > 0) sb.append(", ")
                        sb.append("\"$k\": ")
                        appendValue(sb, v)
                    }
                    sb.append("}")
                }
                else -> sb.append("\"").append(value.toString()).append("\"")
            }
        }
    }
}
