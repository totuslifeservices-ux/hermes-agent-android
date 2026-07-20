package com.nousresearch.hermes.agent.core.tools.platform

import android.Manifest
import android.content.ContentValues
import android.content.ContentResolver
import android.database.Cursor
import android.provider.CalendarContract
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult
import com.nousresearch.hermes.agent.core.tools.HermesTool
import com.nousresearch.hermes.agent.core.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

/**
 * CalendarTool — Read, create, and search calendar events via CalendarContract.
 *
 * Capabilities:
 * - read_events: List events within a date range
 * - create_event: Create a new calendar event
 * - search_events: Search events by title, description, or location
 *
 * Permissions: READ_CALENDAR (required), WRITE_CALENDAR (for create)
 * Privacy: All calendar data stays on-device. No telemetry.
 */
class CalendarTool : HermesTool {

    override val descriptor = ToolDescriptor(
        name = "calendar",
        description = "Read and manage calendar events. Use read_events to list events in a date range. " +
            "Use create_event to add a new event to the default calendar. " +
            "Use search_events to find events by title, description, or location.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("read_events", "create_event", "search_events"),
                    "description" to "The calendar action to perform",
                ),
                "startTime" to mapOf(
                    "type" to "integer",
                    "description" to "Start time in epoch milliseconds for date range filtering",
                ),
                "endTime" to mapOf(
                    "type" to "integer",
                    "description" to "End time in epoch milliseconds for date range filtering",
                ),
                "query" to mapOf(
                    "type" to "string",
                    "description" to "Search query for search_events (matches title, description, location)",
                ),
                "title" to mapOf(
                    "type" to "string",
                    "description" to "Event title for create_event",
                ),
                "description" to mapOf(
                    "type" to "string",
                    "description" to "Event description for create_event",
                ),
                "location" to mapOf(
                    "type" to "string",
                    "description" to "Event location for create_event",
                ),
                "eventStart" to mapOf(
                    "type" to "integer",
                    "description" to "Event start time in epoch milliseconds (required for create_event)",
                ),
                "eventEnd" to mapOf(
                    "type" to "integer",
                    "description" to "Event end time in epoch milliseconds (required for create_event)",
                ),
                "allDay" to mapOf(
                    "type" to "boolean",
                    "description" to "Whether the event is all-day (default: false)",
                ),
                "limit" to mapOf(
                    "type" to "integer",
                    "description" to "Maximum results to return (default: 50, max: 200)",
                    "default" to 50,
                ),
            ),
            "required" to listOf("action"),
        ),
    )

    override val requiresPermissions: List<String> get() = listOf(
        Manifest.permission.READ_CALENDAR,
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            val action = args["action"] as? String ?: return@withContext ToolResult.Error(
                message = "Missing required parameter: action",
                recoverable = true,
            )

            try {
                when (action) {
                    "read_events" -> readEvents(context, args)
                    "create_event" -> createEvent(context, args)
                    "search_events" -> searchEvents(context, args)
                    else -> ToolResult.Error(
                        message = "Unknown calendar action: '$action'. Valid: read_events, create_event, search_events",
                        recoverable = true,
                    )
                }
            } catch (e: SecurityException) {
                ToolResult.Error(
                    message = "Calendar permission denied. Grant Calendar access in Settings.",
                    recoverable = true,
                )
            } catch (e: Exception) {
                ToolResult.Error(
                    message = "Calendar operation failed: ${e.message ?: "Unknown error"}",
                    recoverable = true,
                )
            }
        }

    override fun toString(): String = "CalendarTool"

    // ── Actions ──────────────────────────────────────────────────────

    private fun readEvents(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 200) ?: 50
        val startTime = (args["startTime"] as? Number)?.toLong()
            ?: System.currentTimeMillis() - 7 * 86400000L // Default: 7 days ago
        val endTime = (args["endTime"] as? Number)?.toLong()
            ?: System.currentTimeMillis() + 30 * 86400000L // Default: 30 days ahead

        val cr: ContentResolver = context.androidContext.contentResolver
        val uri = CalendarContract.Events.CONTENT_URI

        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(startTime.toString(), endTime.toString())

        val cursor = cr.query(
            uri,
            EVENT_PROJECTION,
            selection,
            selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC LIMIT $limit",
        )

        val events = mutableListOf<Map<String, Any?>>()
        cursor?.use { c ->
            while (c.moveToNext() && events.size < limit) {
                events.add(eventFromCursor(c))
            }
        }

        return ToolResult.Success(toJson("events" to events, "count" to events.size))
    }

    private fun createEvent(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val title = args["title"] as? String ?: "Untitled Event"
        val description = args["description"] as? String ?: ""
        val location = args["location"] as? String ?: ""
        val eventStart = (args["eventStart"] as? Number)?.toLong()
            ?: return ToolResult.Error(
                message = "Missing required parameter: eventStart (epoch milliseconds)",
                recoverable = true,
            )
        val eventEnd = (args["eventEnd"] as? Number)?.toLong()
            ?: return ToolResult.Error(
                message = "Missing required parameter: eventEnd (epoch milliseconds)",
                recoverable = true,
            )
        val allDay = args["allDay"] as? Boolean ?: false

        if (eventEnd <= eventStart) {
            return ToolResult.Error(
                message = "eventEnd must be after eventStart",
                recoverable = true,
            )
        }

        val cr: ContentResolver = context.androidContext.contentResolver

        // Find the primary calendar ID
        val calendarId = getPrimaryCalendarId(cr)
            ?: return ToolResult.Error(
                message = "No writable calendar found on device",
                recoverable = true,
            )

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, eventStart)
            put(CalendarContract.Events.DTEND, eventEnd)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
            put(CalendarContract.Events.GUESTS_CAN_MODIFY, 0)
            put(CalendarContract.Events.GUESTS_CAN_INVITE_OTHERS, 0)
        }

        val insertedUri = cr.insert(CalendarContract.Events.CONTENT_URI, values)
        val eventId = insertedUri?.lastPathSegment ?: "unknown"

        return ToolResult.Success(
            toJson(
                "status" to "created",
                "eventId" to eventId,
                "title" to title,
                "start" to eventStart,
                "end" to eventEnd,
            )
        )
    }

    private fun searchEvents(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val query = args["query"] as? String
            ?: return ToolResult.Error(
                message = "Missing required parameter: query",
                recoverable = true,
            )
        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 200) ?: 50

        val cr: ContentResolver = context.androidContext.contentResolver
        val uri = CalendarContract.Events.CONTENT_URI

        val selection = """
            ${CalendarContract.Events.TITLE} LIKE ? OR
            ${CalendarContract.Events.DESCRIPTION} LIKE ? OR
            ${CalendarContract.Events.EVENT_LOCATION} LIKE ?
        """.trimIndent()
        val selectionArgs = arrayOf("%$query%", "%$query%", "%$query%")

        val cursor = cr.query(
            uri,
            EVENT_PROJECTION,
            selection,
            selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC LIMIT $limit",
        )

        val events = mutableListOf<Map<String, Any?>>()
        cursor?.use { c ->
            while (c.moveToNext() && events.size < limit) {
                events.add(eventFromCursor(c))
            }
        }

        return ToolResult.Success(toJson("events" to events, "count" to events.size))
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun getPrimaryCalendarId(cr: ContentResolver): Long? {
        val cursor = cr.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            null,
        )
        cursor?.use { c ->
            if (c.moveToFirst()) {
                return c.getLong(c.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
            }
        }
        return null
    }

    private fun eventFromCursor(c: Cursor): Map<String, Any?> {
        return mapOf(
            "id" to c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events._ID)),
            "title" to c.getString(c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)),
            "description" to c.getString(c.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)),
            "location" to c.getString(c.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)),
            "startTime" to c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)),
            "endTime" to c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events.DTEND)),
            "allDay" to (c.getInt(c.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)) == 1),
            "timezone" to c.getString(c.getColumnIndexOrThrow(CalendarContract.Events.EVENT_TIMEZONE)),
            "status" to c.getInt(c.getColumnIndexOrThrow(CalendarContract.Events.STATUS)),
        )
    }

    companion object {
        private val EVENT_PROJECTION = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.STATUS,
        )

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
