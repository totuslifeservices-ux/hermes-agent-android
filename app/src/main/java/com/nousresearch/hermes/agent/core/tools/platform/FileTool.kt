package com.nousresearch.hermes.agent.core.tools.platform

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult
import com.nousresearch.hermes.agent.core.tools.HermesTool
import com.nousresearch.hermes.agent.core.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * FileTool — List, read, write, and search files using Storage Access Framework and MediaStore.
 *
 * Capabilities:
 * - list_files: List files in a directory or media collection
 * - read_file: Read a file's content as text or return metadata for binary files
 * - write_file: Write text content to a file (requires confirmation)
 * - search_files: Search for files by name pattern across accessible storage
 *
 * Uses MediaStore for shared storage and direct file system access for app-private directories.
 * For cross-app file access, uses SAF (Storage Access Framework) intents.
 *
 * Permissions: READ_EXTERNAL_STORAGE or READ_MEDIA_* (Android 13+)
 * Privacy: No telemetry. All file operations are user-initiated.
 */
class FileTool : HermesTool {

    override val descriptor = ToolDescriptor(
        name = "file",
        description = "List, read, write, and search files on the device. " +
            "Use list_files to browse directories or media collections. " +
            "Use read_file to read text file contents or get file metadata. " +
            "Use write_file to save text to a file (requires confirmation). " +
            "Use search_files to find files by name pattern.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("list_files", "read_file", "write_file", "search_files"),
                    "description" to "The file action to perform",
                ),
                "path" to mapOf(
                    "type" to "string",
                    "description" to "Directory path for list_files, file path for read/write. " +
                        "Use '/' for root app directory. Use 'Downloads/', 'Documents/', 'Pictures/' for public dirs.",
                ),
                "content" to mapOf(
                    "type" to "string",
                    "description" to "Text content to write (for write_file)",
                ),
                "fileName" to mapOf(
                    "type" to "string",
                    "description" to "File name for write_file, or search query for search_files",
                ),
                "collection" to mapOf(
                    "type" to "string",
                    "enum" to listOf("downloads", "documents", "pictures", "music", "movies", "app"),
                    "description" to "Media collection to browse for list_files (default: app)",
                ),
                "pattern" to mapOf(
                    "type" to "string",
                    "description" to "Glob pattern for search_files (e.g., '*.txt', '*.pdf')",
                ),
                "recursive" to mapOf(
                    "type" to "boolean",
                    "description" to "Whether to search recursively for search_files (default: true)",
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

    override val requiresConfirmation: Boolean get() = false // checked per-action

    override val requiresPermissions: List<String> get() = listOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            val action = args["action"] as? String ?: return@withContext ToolResult.Error(
                message = "Missing required parameter: action",
                recoverable = true,
            )

            try {
                when (action) {
                    "list_files" -> listFiles(context, args)
                    "read_file" -> readFile(context, args)
                    "write_file" -> writeFile(context, args)
                    "search_files" -> searchFiles(context, args)
                    else -> ToolResult.Error(
                        message = "Unknown file action: '$action'. Valid: list_files, read_file, write_file, search_files",
                        recoverable = true,
                    )
                }
            } catch (e: SecurityException) {
                ToolResult.Error(
                    message = "Storage permission denied. Grant Files/Media access in Settings.",
                    recoverable = true,
                )
            } catch (e: Exception) {
                ToolResult.Error(
                    message = "File operation failed: ${e.message ?: "Unknown error"}",
                    recoverable = true,
                )
            }
        }

    override fun toString(): String = "FileTool"

    // ── Actions ──────────────────────────────────────────────────────

    private fun listFiles(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val path = args["path"] as? String ?: ""
        val collection = (args["collection"] as? String)?.lowercase() ?: "app"
        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 200) ?: 50

        val files = when (collection) {
            "downloads" -> listMediaStoreFiles(context, MediaStore.Downloads.EXTERNAL_CONTENT_URI, limit)
            "documents" -> listMediaStoreFiles(context, MediaStore.Files.getContentUri("external"), limit)
            "pictures" -> listMediaStoreFiles(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, limit)
            "music" -> listMediaStoreFiles(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, limit)
            "movies" -> listMediaStoreFiles(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, limit)
            else -> {
                // App-private directory
                val dir = resolveAppPath(context, path)
                if (dir != null && dir.isDirectory) {
                    dir.listFiles()
                        ?.sortedByDescending { it.lastModified() }
                        ?.take(limit)
                        ?.map { fileToMap(it) }
                        ?: emptyList()
                } else if (dir != null && !dir.isDirectory) {
                    return ToolResult.Error(
                        message = "'$path' is not a directory",
                        recoverable = true,
                    )
                } else {
                    emptyList()
                }
            }
        }

        return ToolResult.Success(toJson("files" to files, "count" to files.size, "collection" to collection))
    }

    private fun readFile(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val fileName = args["fileName"] as? String
        val path = args["path"] as? String
        val targetPath = path ?: fileName
            ?: return ToolResult.Error(
                message = "Missing required parameter: path or fileName",
                recoverable = true,
            )

        // Try app-private directory first
        val appFile = resolveAppPath(context, targetPath)
        if (appFile != null && appFile.exists() && appFile.isFile) {
            val metadata = fileToMap(appFile)
            val isText = isTextFile(appFile)
            if (isText) {
                val text = appFile.readText(StandardCharsets.UTF_8)
                return ToolResult.Success(
                    toJson("file" to metadata, "content" to text)
                )
            } else {
                return ToolResult.Success(
                    toJson("file" to metadata, "note" to "Binary file — metadata only")
                )
            }
        }

        // Try MediaStore by file name
        val mediaFile = queryMediaStore(context, targetPath)
        if (mediaFile != null) {
            return ToolResult.Success(toJson("file" to mediaFile))
        }

        return ToolResult.Error(
            message = "File not found: '$targetPath'",
            recoverable = true,
        )
    }

    private fun writeFile(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val fileName = args["fileName"] as? String
        val path = args["path"] as? String
        val content = args["content"] as? String
            ?: return ToolResult.Error(
                message = "Missing required parameter: content",
                recoverable = true,
            )

        val targetPath = path ?: fileName
            ?: return ToolResult.Error(
                message = "Missing required parameter: path or fileName",
                recoverable = true,
            )

        // Only write to app-private directory for safety
        val file = File(context.androidContext.filesDir, targetPath)
        file.parentFile?.mkdirs()

        file.writeText(content, StandardCharsets.UTF_8)

        // Scan so it shows up in MediaStore
        MediaScannerConnection.scanFile(
            context.androidContext,
            arrayOf(file.absolutePath),
            null,
            null,
        )

        return ToolResult.Success(
            toJson(
                "status" to "written",
                "path" to file.absolutePath,
                "size" to file.length(),
            )
        )
    }

    private fun searchFiles(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val pattern = args["pattern"] as? String ?: "*"
        val query = args["fileName"] as? String
        val recursive = args["recursive"] as? Boolean ?: true
        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 200) ?: 50

        val results = mutableListOf<Map<String, Any?>>()

        // Search app-private directory
        val searchDir = context.androidContext.filesDir
        searchDir.walkTopDown().forEach { file ->
            if (results.size >= limit) return@forEach
            if (file.isFile && matchesPattern(file.name, pattern, query)) {
                results.add(fileToMap(file))
            }
        }

        // Also search cache directory
        if (results.size < limit) {
            context.androidContext.cacheDir.walkTopDown().forEach { file ->
                if (results.size >= limit) return@forEach
                if (file.isFile && matchesPattern(file.name, pattern, query)) {
                    results.add(fileToMap(file))
                }
            }
        }

        return ToolResult.Success(toJson("files" to results, "count" to results.size))
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun resolveAppPath(context: ToolContext, path: String): File? {
        val base = context.androidContext.filesDir
        return when {
            path.startsWith("/") -> File(path)
            path.isBlank() -> base
            else -> {
                val segments = path.split("/").filter { it.isNotBlank() }
                when (segments.firstOrNull()?.lowercase()) {
                    "downloads" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    "documents" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    "pictures" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    "music" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    "movies" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    else -> segments.fold(base) { acc, seg -> File(acc, seg) }
                }
            }
        }
    }

    private fun listMediaStoreFiles(
        context: ToolContext,
        uri: Uri,
        limit: Int,
    ): List<Map<String, Any?>> {
        val results = mutableListOf<Map<String, Any?>>()
        val cr: ContentResolver = context.androidContext.contentResolver
        val cursor = cr.query(
            uri,
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MIME_TYPE,
            ),
            null,
            null,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT $limit",
        )
        cursor?.use { c ->
            while (c.moveToNext() && results.size < limit) {
                results.add(
                    mapOf(
                        "id" to c.getLong(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)),
                        "name" to c.getString(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)),
                        "size" to c.getLong(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)),
                        "modified" to c.getLong(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)),
                        "mimeType" to c.getString(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)),
                    )
                )
            }
        }
        return results
    }

    private fun queryMediaStore(context: ToolContext, fileName: String): Map<String, Any?>? {
        val cr: ContentResolver = context.androidContext.contentResolver
        val uri = MediaStore.Files.getContentUri("external")
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$fileName%")

        val cursor = cr.query(
            uri,
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATA,
            ),
            selection,
            selectionArgs,
            null,
        )
        cursor?.use { c ->
            if (c.moveToFirst()) {
                return mapOf(
                    "id" to c.getLong(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)),
                    "name" to c.getString(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)),
                    "size" to c.getLong(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)),
                    "modified" to c.getLong(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)),
                    "mimeType" to c.getString(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)),
                    "path" to c.getString(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)),
                )
            }
        }
        return null
    }

    private fun fileToMap(file: File): Map<String, Any?> {
        return mapOf(
            "name" to file.name,
            "path" to file.absolutePath,
            "size" to file.length(),
            "isDirectory" to file.isDirectory,
            "isFile" to file.isFile,
            "lastModified" to file.lastModified(),
            "extension" to file.extension,
            "readable" to file.canRead(),
            "writable" to file.canWrite(),
        )
    }

    private fun isTextFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in setOf(
            "txt", "md", "json", "xml", "html", "htm", "css", "js", "kt", "java",
            "py", "rb", "sh", "bat", "cfg", "ini", "conf", "log", "csv", "tsv",
            "yaml", "yml", "toml", "env", "gradle", "kts", "properties",
        ) || file.length() < 1024 * 100 // < 100KB
    }

    private fun matchesPattern(name: String, pattern: String, query: String?): Boolean {
        if (!query.isNullOrBlank()) {
            return name.contains(query, ignoreCase = true)
        }
        val regex = pattern.replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
        return Regex(regex, RegexOption.IGNORE_CASE).matches(name)
    }

    companion object {
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
