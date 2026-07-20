package com.nousresearch.hermes.agent.core.tools.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult
import com.nousresearch.hermes.agent.core.tools.HermesTool
import com.nousresearch.hermes.agent.core.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * NotificationTool — Send and list device notifications.
 *
 * Capabilities:
 * - send_notification: Post a notification to the system notification center
 * - list_notifications: List active notifications from the notification listener
 *
 * Uses NotificationManager + NotificationManagerCompat for posting.
 * Listing active notifications requires NotificationListenerService binding
 * or the deprecated (and restricted) getActiveNotifications().
 *
 * Permissions: POST_NOTIFICATIONS (Android 13+)
 * Privacy: No telemetry. Notifications are only read/written on explicit request.
 */
class NotificationTool : HermesTool {

    override val descriptor = ToolDescriptor(
        name = "notification",
        description = "Send and list device notifications. " +
            "Use send_notification to post a new notification to the notification drawer. " +
            "Use list_notifications to read currently active notifications.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("send_notification", "list_notifications"),
                    "description" to "The notification action to perform",
                ),
                "title" to mapOf(
                    "type" to "string",
                    "description" to "Notification title (for send_notification)",
                ),
                "message" to mapOf(
                    "type" to "string",
                    "description" to "Notification body text (for send_notification)",
                ),
                "channel" to mapOf(
                    "type" to "string",
                    "description" to "Notification channel ID (for send_notification, default: 'hermes_general')",
                ),
                "channelName" to mapOf(
                    "type" to "string",
                    "description" to "Human-readable channel name for new channels (default: 'Hermes Notifications')",
                ),
                "priority" to mapOf(
                    "type" to "string",
                    "enum" to listOf("low", "default", "high", "max"),
                    "description" to "Notification priority (default: 'default')",
                ),
                "autoCancel" to mapOf(
                    "type" to "boolean",
                    "description" to "Whether the notification dismisses on tap (default: true)",
                ),
                "limit" to mapOf(
                    "type" to "integer",
                    "description" to "Maximum notifications to list (default: 20)",
                    "default" to 20,
                ),
            ),
            "required" to listOf("action"),
        ),
    )

    override val requiresConfirmation: Boolean get() = false

    override val requiresPermissions: List<String> get() = listOf(
        Manifest.permission.POST_NOTIFICATIONS,
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.Main) {
            val action = args["action"] as? String ?: return@withContext ToolResult.Error(
                message = "Missing required parameter: action",
                recoverable = true,
            )

            try {
                when (action) {
                    "send_notification" -> sendNotification(context, args)
                    "list_notifications" -> listNotifications(context, args)
                    else -> ToolResult.Error(
                        message = "Unknown notification action: '$action'. Valid: send_notification, list_notifications",
                        recoverable = true,
                    )
                }
            } catch (e: SecurityException) {
                ToolResult.Error(
                    message = "Notification permission denied. Grant Notification access in Settings.",
                    recoverable = true,
                )
            } catch (e: Exception) {
                ToolResult.Error(
                    message = "Notification operation failed: ${e.message ?: "Unknown error"}",
                    recoverable = true,
                )
            }
        }

    override fun toString(): String = "NotificationTool"

    // ── Actions ──────────────────────────────────────────────────────

    private fun sendNotification(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val title = args["title"] as? String ?: "Hermes Agent"
        val message = args["message"] as? String
            ?: return ToolResult.Error(
                message = "Missing required parameter: message",
                recoverable = true,
            )
        val channelId = (args["channel"] as? String) ?: "hermes_general"
        val channelName = (args["channelName"] as? String) ?: "Hermes Notifications"
        val priority = parsePriority(args["priority"] as? String ?: "default")
        val autoCancel = args["autoCancel"] as? Boolean ?: true

        // Ensure notification channel exists (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.androidContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existingChannel = notificationManager.getNotificationChannel(channelId)
            if (existingChannel == null) {
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    mapPriorityToImportance(priority),
                ).apply {
                    description = "Hermes Agent notifications"
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        val notification = NotificationCompat.Builder(context.androidContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(priority)
            .setAutoCancel(autoCancel)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setLocalOnly(true)
            .build()

        val notificationId = message.hashCode() and 0x7fffffff
        NotificationManagerCompat.from(context.androidContext).notify(notificationId, notification)

        return ToolResult.Success(
            """{"status": "posted", "notificationId": $notificationId, "title": "${title.replace("\"", "\\\"")}"}"""
        )
    }

    private fun listNotifications(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 50) ?: 20

        val notificationManager = context.androidContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notifications = mutableListOf<Map<String, Any?>>()

        // getActiveNotifications() requires BIND_NOTIFICATION_LISTENER_SERVICE
        // which most apps don't have. This is best-effort.
        try {
            @Suppress("DEPRECATION")
            val activeNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Only available for the calling app's own notifications or
                // with NotificationListenerService binding
                notificationManager.activeNotifications
            } else {
                @Suppress("DEPRECATION")
                notificationManager.activeNotificationSnapshots
            }

            activeNotifications?.forEach { sbn ->
                if (notifications.size >= limit) return@forEach
                notifications.add(statusBarNotificationToMap(sbn))
            }
        } catch (e: Exception) {
            // NotificationListenerService not bound — return empty
            return ToolResult.Success(
                """{"notifications": [], "count": 0, "note": "Cannot read active notifications without " +
                    "NotificationListenerService binding. Only own notifications may be visible."}"""
            )
        }

        return ToolResult.Success(toJson("notifications" to notifications, "count" to notifications.size))
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun statusBarNotificationToMap(sbn: StatusBarNotification): Map<String, Any?> {
        val notification = sbn.notification
        return mapOf(
            "id" to sbn.id,
            "tag" to sbn.tag,
            "packageName" to sbn.packageName,
            "isOngoing" to sbn.isOngoing,
            "isClearable" to sbn.isClearable,
            "postTime" to sbn.postTime,
            "title" to notification?.extras?.getString(android.app.Notification.EXTRA_TITLE),
            "text" to notification?.extras?.getString(android.app.Notification.EXTRA_TEXT),
            "channelId" to (if (Build.VERSION.SDK_INT >= 26) notification?.channelId else null),
            "category" to notification?.category,
            "priority" to notification?.priority,
        )
    }

    private fun parsePriority(value: String): Int = when (value.lowercase()) {
        "low" -> NotificationCompat.PRIORITY_LOW
        "default" -> NotificationCompat.PRIORITY_DEFAULT
        "high" -> NotificationCompat.PRIORITY_HIGH
        "max" -> NotificationCompat.PRIORITY_MAX
        else -> NotificationCompat.PRIORITY_DEFAULT
    }

    private fun mapPriorityToImportance(priority: Int): Int {
        return when (priority) {
            NotificationCompat.PRIORITY_LOW -> NotificationManager.IMPORTANCE_LOW
            NotificationCompat.PRIORITY_DEFAULT -> NotificationManager.IMPORTANCE_DEFAULT
            NotificationCompat.PRIORITY_HIGH -> NotificationManager.IMPORTANCE_HIGH
            NotificationCompat.PRIORITY_MAX -> NotificationManager.IMPORTANCE_MAX
            else -> NotificationManager.IMPORTANCE_DEFAULT
        }
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
