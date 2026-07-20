package com.nousresearch.hermes.agent.core.tools.platform

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult
import com.nousresearch.hermes.agent.core.tools.HermesTool
import com.nousresearch.hermes.agent.core.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ContactsTool — Read, search, and create contacts via ContactsContract ContentProvider.
 *
 * Capabilities:
 * - find_contact: Search contacts by name, phone, or email
 * - list_contacts: List all contacts with pagination
 * - create_contact: Create a new contact with name, phone, and email
 *
 * Permissions: READ_CONTACTS (required), WRITE_CONTACTS (for create)
 * Privacy: All contact data stays on-device. No telemetry.
 */
class ContactsTool : HermesTool {

    override val descriptor = ToolDescriptor(
        name = "contacts",
        description = "Read and manage device contacts. Use find_contact to search by name, phone, or email. " +
            "Use list_contacts to browse all contacts. Use create_contact to add a new contact.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("find_contact", "list_contacts", "create_contact"),
                    "description" to "The contacts action to perform",
                ),
                "query" to mapOf(
                    "type" to "string",
                    "description" to "Search query for find_contact (matches name, phone number, or email)",
                ),
                "name" to mapOf(
                    "type" to "string",
                    "description" to "Contact display name for create_contact",
                ),
                "phoneNumber" to mapOf(
                    "type" to "string",
                    "description" to "Phone number for create_contact",
                ),
                "email" to mapOf(
                    "type" to "string",
                    "description" to "Email address for create_contact",
                ),
                "limit" to mapOf(
                    "type" to "integer",
                    "description" to "Maximum results to return (default: 50, max: 200)",
                    "default" to 50,
                ),
                "offset" to mapOf(
                    "type" to "integer",
                    "description" to "Offset for pagination in list_contacts",
                    "default" to 0,
                ),
            ),
            "required" to listOf("action"),
        ),
    )

    override val requiresPermissions: List<String> get() = listOf(
        Manifest.permission.READ_CONTACTS,
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            val action = args["action"] as? String ?: return@withContext ToolResult.Error(
                message = "Missing required parameter: action",
                recoverable = true,
            )

            try {
                when (action) {
                    "find_contact" -> findContact(context, args)
                    "list_contacts" -> listContacts(context, args)
                    "create_contact" -> createContact(context, args)
                    else -> ToolResult.Error(
                        message = "Unknown contacts action: '$action'. Valid: find_contact, list_contacts, create_contact",
                        recoverable = true,
                    )
                }
            } catch (e: SecurityException) {
                ToolResult.Error(
                    message = "Contacts permission denied. Grant Contacts access in Settings.",
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

    private fun findContact(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val query = args["query"] as? String
        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 200) ?: 50

        if (query.isNullOrBlank()) {
            return ToolResult.Error(
                message = "Missing required parameter: query",
                recoverable = true,
            )
        }

        val cr: ContentResolver = context.androidContext.contentResolver

        // Search by name using the primary contact lookup
        val contactsUri = ContactsContract.Contacts.CONTENT_URI
        val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        val cursor = cr.query(
            contactsUri,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ContactsContract.Contacts.PHOTO_URI,
            ),
            selection,
            selectionArgs,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC LIMIT $limit",
        )

        val results = mutableListOf<Map<String, Any?>>()
        cursor?.use { c ->
            while (c.moveToNext() && results.size < limit) {
                val contactId = c.getLong(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val hasPhone = c.getInt(c.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0

                val phones = if (hasPhone) getPhoneNumbers(cr, contactId) else emptyList()
                val emails = getEmailAddresses(cr, contactId)

                results.add(
                    mapOf(
                        "id" to contactId,
                        "name" to c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)),
                        "phones" to phones,
                        "emails" to emails,
                        "photoUri" to c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI)),
                    )
                )
            }
        }

        if (results.isEmpty()) {
            // Try phone number search
            val phoneResults = searchByPhone(cr, query, limit)
            if (phoneResults.isNotEmpty()) {
                return ToolResult.Success(toJson("contacts" to phoneResults, "count" to phoneResults.size))
            }
            // Try email search
            val emailResults = searchByEmail(cr, query, limit)
            if (emailResults.isNotEmpty()) {
                return ToolResult.Success(toJson("contacts" to emailResults, "count" to emailResults.size))
            }
        }

        return ToolResult.Success(toJson("contacts" to results, "count" to results.size))
    }

    private fun listContacts(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 200) ?: 50
        val offset = (args["offset"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0

        val cr: ContentResolver = context.androidContext.contentResolver
        val uri = ContactsContract.Contacts.CONTENT_URI

        val cursor = cr.query(
            uri,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
            ),
            "${ContactsContract.Contacts.HAS_PHONE_NUMBER} > 0",
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC LIMIT $limit OFFSET $offset",
        )

        val results = mutableListOf<Map<String, Any?>>()
        cursor?.use { c ->
            while (c.moveToNext() && results.size < limit) {
                val contactId = c.getLong(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                results.add(
                    mapOf(
                        "id" to contactId,
                        "name" to c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)),
                        "phones" to getPhoneNumbers(cr, contactId),
                    )
                )
            }
        }

        return ToolResult.Success(toJson("contacts" to results, "count" to results.size))
    }

    private fun createContact(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val name = args["name"] as? String
        val phoneNumber = args["phoneNumber"] as? String
        val email = args["email"] as? String

        if (name.isNullOrBlank() && phoneNumber.isNullOrBlank() && email.isNullOrBlank()) {
            return ToolResult.Error(
                message = "At least one of: name, phoneNumber, or email must be provided",
                recoverable = true,
            )
        }

        val cr: ContentResolver = context.androidContext.contentResolver
        val operations = mutableListOf<ContentProviderOperation>()

        // Create raw contact
        val rawContactIndex = operations.size
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )

        // Add display name
        if (!name.isNullOrBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                        name
                    )
                    .build()
            )
        }

        // Add phone number
        if (!phoneNumber.isNullOrBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        phoneNumber
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                    )
                    .build()
            )
        }

        // Add email
        if (!email.isNullOrBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
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

        val results = cr.applyBatch(android.provider.ContactsContract.AUTHORITY, operations)
        val newUri = results.lastOrNull()?.uri

        return ToolResult.Success(
            toJson(
                "status" to "created",
                "uri" to (newUri?.toString() ?: "unknown"),
                "name" to name,
                "phoneNumber" to phoneNumber,
                "email" to email,
            )
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun getPhoneNumbers(cr: ContentResolver, contactId: Long): List<Map<String, Any?>> {
        val phones = mutableListOf<Map<String, Any?>>()
        val cursor = cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
            ),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )
        cursor?.use { c ->
            while (c.moveToNext()) {
                phones.add(
                    mapOf(
                        "number" to c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)),
                        "type" to phoneTypeLabel(
                            c.getInt(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE))
                        ),
                    )
                )
            }
        }
        return phones
    }

    private fun getEmailAddresses(cr: ContentResolver, contactId: Long): List<Map<String, Any?>> {
        val emails = mutableListOf<Map<String, Any?>>()
        val cursor = cr.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.TYPE,
            ),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )
        cursor?.use { c ->
            while (c.moveToNext()) {
                emails.add(
                    mapOf(
                        "address" to c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)),
                        "type" to emailTypeLabel(
                            c.getInt(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.TYPE))
                        ),
                    )
                )
            }
        }
        return emails
    }

    private fun searchByPhone(cr: ContentResolver, phone: String, limit: Int): List<Map<String, Any?>> {
        val results = mutableListOf<Map<String, Any?>>()
        val cursor = cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
            arrayOf("%$phone%"),
            "LIMIT $limit",
        )
        cursor?.use { c ->
            while (c.moveToNext() && results.size < limit) {
                results.add(
                    mapOf(
                        "id" to c.getLong(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)),
                        "name" to c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)),
                        "phone" to c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)),
                    )
                )
            }
        }
        return results
    }

    private fun searchByEmail(cr: ContentResolver, email: String, limit: Int): List<Map<String, Any?>> {
        val results = mutableListOf<Map<String, Any?>>()
        val cursor = cr.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Email.ADDRESS,
            ),
            "${ContactsContract.CommonDataKinds.Email.ADDRESS} LIKE ?",
            arrayOf("%$email%"),
            "LIMIT $limit",
        )
        cursor?.use { c ->
            while (c.moveToNext() && results.size < limit) {
                results.add(
                    mapOf(
                        "id" to c.getLong(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.CONTACT_ID)),
                        "name" to c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY)),
                        "email" to c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)),
                    )
                )
            }
        }
        return results
    }

    private fun phoneTypeLabel(type: Int): String = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "home"
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "work"
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "fax_work"
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "fax_home"
        ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> "pager"
        ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "other"
        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> "custom"
        else -> "unknown($type)"
    }

    private fun emailTypeLabel(type: Int): String = when (type) {
        ContactsContract.CommonDataKinds.Email.TYPE_HOME -> "home"
        ContactsContract.CommonDataKinds.Email.TYPE_WORK -> "work"
        ContactsContract.CommonDataKinds.Email.TYPE_MOBILE -> "mobile"
        ContactsContract.CommonDataKinds.Email.TYPE_OTHER -> "other"
        ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM -> "custom"
        else -> "unknown($type)"
    }

    companion object {
        private fun toJson(vararg pairs: Pair<String, Any?>): String {
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
                    @Suppress("UNCHECKED_CAST")
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
}
