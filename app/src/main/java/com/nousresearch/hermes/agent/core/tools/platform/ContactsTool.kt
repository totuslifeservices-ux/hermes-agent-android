package com.nousresearch.hermes.agent.core.tools.platform

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult
import com.nousresearch.hermes.agent.core.tools.HermesTool
import com.nousresearch.hermes.agent.core.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ContactsTool — Search, list, and create contacts on Android.
 *
 * Capabilities:
 * - search_contacts: Search contacts by name, phone, or email
 * - list_all_contacts: List all contacts with pagination
 * - create_contact: Create a new contact entry
 *
 * Permissions: READ_CONTACTS for reading, WRITE_CONTACTS for creating.
 * Privacy: No telemetry. Contact data stays on-device.
 */
class ContactsTool(private val context: Context) : HermesTool {

    companion object {
        private const val TAG = "ContactsTool"
    }

    override val descriptor = ToolDescriptor(
        name = "contacts",
        description = "Search contacts by name, phone number, or email address; " +
            "list all contacts on the device; and create new contact entries. " +
            "Use search_contacts with a query to find matching contacts. " +
            "Use list_all_contacts to get a paginated list. " +
            "Use create_contact to add a new contact.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("search_contacts", "list_all_contacts", "create_contact"),
                    "description" to "The contacts action to perform",
                ),
                "query" to mapOf(
                    "type" to "string",
                    "description" to "Search query for search_contacts (matches display name, phone number, email)",
                ),
                "name" to mapOf(
                    "type" to "string",
                    "description" to "Contact display name (required for create_contact)",
                ),
                "phone" to mapOf(
                    "type" to "string",
                    "description" to "Phone number (optional for create_contact)",
                ),
                "email" to mapOf(
                    "type" to "string",
                    "description" to "Email address (optional for create_contact)",
                ),
                "limit" to mapOf(
                    "type" to "integer",
                    "description" to "Maximum results (default: 50, max: 500)",
                    "default" to 50,
                ),
            ),
            "required" to listOf("action"),
        ),
    )

    override val requiresConfirmation: Boolean get() = true

    override val requiresPermissions: List<String> = listOf(
        Manifest.permission.READ_CONTACTS,
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
            try {
                val action = args["action"] as? String
                    ?: throw IllegalArgumentException("Missing required parameter: action")

                when (action) {
                    "search_contacts" -> searchContacts(args)
                    "list_all_contacts" -> listAllContacts(args)
                    "create_contact" -> createContact(context, args)
                    else -> ToolResult.Error(
                        message = "Unknown contacts action: '$action'. Valid: search_contacts, list_all_contacts, create_contact",
                        recoverable = true,
                    )
                }
            } catch (e: SecurityException) {
                ToolResult.Error(
                    message = "Contacts permission denied: ${e.message}",
                    recoverable = true,
                )
            } catch (e: Exception) {
                ToolResult.Error(
                    message = "Contacts operation failed: ${e.message ?: "Unknown error"}",
                    recoverable = true,
                )
            }
        }

    override fun toString(): String = "ContactsTool"

    // ── Actions ──────────────────────────────────────────────────────

    private fun searchContacts(args: Map<String, Any?>): ToolResult {
        val query = args["query"] as? String
            ?: return ToolResult.Error(
                message = "Missing required parameter: query",
                recoverable = true,
            )
        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 500) ?: 50

        val cr = context.contentResolver
        val uri = ContactsContract.Contacts.CONTENT_URI
        val selection = "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")
        val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME} ASC LIMIT $limit"

        val cursor = cr.query(uri, null, selection, selectionArgs, sortOrder)
            ?: return ToolResult.Success("""{"contacts": [], "count": 0}""")

        val contacts = mutableListOf<Map<String, Any?>>()
        cursor.use { c ->
            while (c.moveToNext()) {
                val contactId = c.getString(c.getColumnIndex(ContactsContract.Contacts._ID))
                val name = c.getString(c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                val hasPhone = c.getString(c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER))

                val phones = if (hasPhone.toIntOrNull() == 1) getPhoneNumbers(contactId) else emptyList()
                val emails = getEmails(contactId)

                contacts.add(mapOf(
                    "id" to contactId,
                    "name" to (name ?: "Unknown"),
                    "phones" to phones,
                    "emails" to emails,
                ))
            }
        }

        val json = buildJsonArray(contacts)
        return ToolResult.Success("""{"query": "$query", "contacts": $json, "count": ${contacts.size}}""")
    }

    private fun listAllContacts(args: Map<String, Any?>): ToolResult {
        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 500) ?: 50
        val cr = context.contentResolver

        val cursor = cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            null, null, null,
            "${ContactsContract.Contacts.DISPLAY_NAME} ASC LIMIT $limit"
        ) ?: return ToolResult.Success("""{"contacts": [], "count": 0}""")

        val contacts = mutableListOf<Map<String, Any?>>()
        cursor.use { c ->
            while (c.moveToNext()) {
                val contactId = c.getString(c.getColumnIndex(ContactsContract.Contacts._ID))
                val name = c.getString(c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                val hasPhone = c.getString(c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER))

                val phones = if (hasPhone.toIntOrNull() == 1) getPhoneNumbers(contactId) else emptyList()
                val emails = getEmails(contactId)

                contacts.add(mapOf(
                    "id" to contactId,
                    "name" to (name ?: "Unknown"),
                    "phones" to phones,
                    "emails" to emails,
                ))
            }
        }

        val json = buildJsonArray(contacts)
        return ToolResult.Success("""{"contacts": $json, "count": ${contacts.size}}""")
    }

    private fun createContact(toolContext: ToolContext, args: Map<String, Any?>): ToolResult {
        val name = args["name"] as? String
            ?: return ToolResult.Error(
                message = "Missing required parameter: name",
                recoverable = true,
            )

        val operations = ArrayList<ContentProviderOperation>()

        // Insert raw contact
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )

        // Insert display name
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                )
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build()
        )

        // Insert phone number if provided
        val phone = args["phone"] as? String
        if (!phone.isNullOrBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                    )
                    .build()
            )
        }

        // Insert email if provided
        val email = args["email"] as? String
        if (!email.isNullOrBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                    .withValue(
                        ContactsContract.CommonDataKinds.Email.TYPE,
                        ContactsContract.CommonDataKinds.Email.TYPE_HOME
                    )
                    .build()
            )
        }

        try {
            val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
            val contactUri = results.last()?.uri
            val contactId = contactUri?.lastPathSegment ?: "unknown"
            return ToolResult.Success(
                """{"status": "created", "name": "$name", "contact_id": "$contactId"}"""
            )
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            return ToolResult.Error(
                message = "Failed to create contact: ${e.message ?: "Unknown error"}",
                recoverable = true,
            )
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun getPhoneNumbers(contactId: String): List<Map<String, Any?>> {
        val phones = mutableListOf<Map<String, Any?>>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        ) ?: return phones

        cursor.use { c ->
            while (c.moveToNext()) {
                phones.add(mapOf(
                    "number" to (c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""),
                    "type" to phoneTypeString(
                        c.getInt(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE))
                    ),
                ))
            }
        }
        return phones
    }

    private fun getEmails(contactId: String): List<Map<String, Any?>> {
        val emails = mutableListOf<Map<String, Any?>>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            null,
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        ) ?: return emails

        cursor.use { c ->
            while (c.moveToNext()) {
                emails.add(mapOf(
                    "address" to (c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)) ?: ""),
                    "type" to emailTypeString(
                        c.getInt(c.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE))
                    ),
                ))
            }
        }
        return emails
    }

    private fun phoneTypeString(type: Int): String = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "home"
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "work"
        ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "other"
        else -> "other"
    }

    private fun emailTypeString(type: Int): String = when (type) {
        ContactsContract.CommonDataKinds.Email.TYPE_HOME -> "home"
        ContactsContract.CommonDataKinds.Email.TYPE_WORK -> "work"
        ContactsContract.CommonDataKinds.Email.TYPE_OTHER -> "other"
        else -> "other"
    }

    private fun buildJsonArray(items: List<Map<String, Any?>>): String {
        val sb = StringBuilder("[")
        items.forEachIndexed { i, map ->
            if (i > 0) sb.append(", ")
            sb.append("{")
            map.entries.forEachIndexed { j, (key, value) ->
                if (j > 0) sb.append(", ")
                sb.append("\"${escapeJson(key)}\": ${jsonValue(value)}")
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
        is List<*> -> buildJsonArray(value.filterNotNull().map { it as Map<String, Any?> })
        else -> "\"${escapeJson(value.toString())}\""
    }

    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
