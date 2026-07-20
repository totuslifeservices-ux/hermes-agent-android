package com.nousresearch.hermes.agent.core.tools.platform

import android.Manifest
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult
import com.nousresearch.hermes.agent.core.tools.HermesTool
import com.nousresearch.hermes.agent.core.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * WebSearchTool — Perform web searches and retrieve search results.
 *
 * Capabilities:
 * - web_search: Execute a web search query and return results
 * - fetch_page: Fetch and return the text content of a URL
 *
 * Uses direct HTTP requests (OkHttp or HttpURLConnection) to a configured
 * search engine. By default, uses a configurable search endpoint.
 *
 * Permissions: INTERNET
 * Privacy: Search queries are sent to the configured search engine.
 * No additional telemetry or tracking.
 */
class WebSearchTool : HermesTool {

    override val descriptor = ToolDescriptor(
        name = "web_search",
        description = "Search the web and fetch web page content. " +
            "Use web_search to search for information on the internet. " +
            "Use fetch_page to retrieve the content of a specific URL.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("web_search", "fetch_page"),
                    "description" to "The web search action to perform",
                ),
                "query" to mapOf(
                    "type" to "string",
                    "description" to "Search query (for web_search)",
                ),
                "url" to mapOf(
                    "type" to "string",
                    "description" to "URL to fetch (for fetch_page)",
                ),
                "maxResults" to mapOf(
                    "type" to "integer",
                    "description" to "Maximum number of search results (default: 5, max: 20)",
                    "default" to 5,
                ),
                "engine" to mapOf(
                    "type" to "string",
                    "enum" to listOf("google", "duckduckgo", "bing"),
                    "description" to "Search engine to use (default: duckduckgo)",
                ),
                "timeoutMs" to mapOf(
                    "type" to "integer",
                    "description" to "Request timeout in milliseconds (default: 10000)",
                    "default" to 10000,
                ),
            ),
            "required" to listOf("action"),
        ),
    )

    override val requiresPermissions: List<String> get() = listOf(
        Manifest.permission.INTERNET,
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            val action = args["action"] as? String ?: return@withContext ToolResult.Error(
                message = "Missing required parameter: action",
                recoverable = true,
            )

            try {
                when (action) {
                    "web_search" -> webSearch(args)
                    "fetch_page" -> fetchPage(args)
                    else -> ToolResult.Error(
                        message = "Unknown web search action: '$action'. Valid: web_search, fetch_page",
                        recoverable = true,
                    )
                }
            } catch (e: Exception) {
                ToolResult.Error(
                    message = "Web search failed: ${e.message ?: "Unknown error"}",
                    recoverable = true,
                )
            }
        }

    override fun toString(): String = "WebSearchTool"

    // ── Actions ──────────────────────────────────────────────────────

    private fun webSearch(args: Map<String, Any?>): ToolResult {
        val query = args["query"] as? String
            ?: return ToolResult.Error(
                message = "Missing required parameter: query",
                recoverable = true,
            )
        val maxResults = (args["maxResults"] as? Number)?.toInt()?.coerceIn(1, 20) ?: 5
        val engine = (args["engine"] as? String)?.lowercase() ?: "duckduckgo"
        val timeoutMs = (args["timeoutMs"] as? Number)?.toLong()?.coerceIn(1000L, 30000L) ?: 10000L

        // Use DuckDuckGo's HTML Lite for faster results without JavaScript
        val searchUrl = when (engine) {
            "google" -> "https://www.google.com/search?q=${URLEncoder.encode(query, "UTF-8")}"
            "duckduckgo" -> "https://lite.duckduckgo.com/lite/?q=${URLEncoder.encode(query, "UTF-8")}"
            "bing" -> "https://www.bing.com/search?q=${URLEncoder.encode(query, "UTF-8")}"
            else -> "https://lite.duckduckgo.com/lite/?q=${URLEncoder.encode(query, "UTF-8")}"
        }

        val connection = URL(searchUrl).openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs.toInt()
            readTimeout = timeoutMs.toInt()
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 16; HermesAgent) AppleWebKit/537.36"
            )
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }

        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            return ToolResult.Error(
                message = "Search engine returned HTTP $responseCode",
                recoverable = true,
            )
        }

        val html = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        // Extract search results from HTML (DuckDuckGo lite format)
        val results = parseDuckDuckGoLite(html, maxResults)

        if (results.isEmpty()) {
            // Return raw HTML snippet if parsing failed — useful for debugging
            return ToolResult.Success(
                """{"query": "${query.replace("\"", "\\\"")}", "results": [], "count": 0, "engine": "$engine", "htmlSnippet": "${html.take(500).replace("\"", "\\\"").replace("\n", "\\n")}"}"""
            )
        }

        return ToolResult.Success(
            """{"query": "${query.replace("\"", "\\\"")}", "results": ${results.toJsonArray()}, "count": ${results.size}, "engine": "$engine"}"""
        )
    }

    private fun fetchPage(args: Map<String, Any?>): ToolResult {
        val urlStr = args["url"] as? String
            ?: return ToolResult.Error(
                message = "Missing required parameter: url",
                recoverable = true,
            )
        val timeoutMs = (args["timeoutMs"] as? Number)?.toLong()?.coerceIn(1000L, 30000L) ?: 10000L

        val url = try {
            URL(urlStr)
        } catch (e: Exception) {
            return ToolResult.Error(
                message = "Invalid URL: ${e.message}",
                recoverable = true,
            )
        }

        // Restrict to http/https only
        if (url.protocol !in listOf("http", "https")) {
            return ToolResult.Error(
                message = "Only http and https URLs are supported",
                recoverable = true,
            )
        }

        val connection = url.openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs.toInt()
            readTimeout = timeoutMs.toInt()
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 16; HermesAgent) AppleWebKit/537.36"
            )
            instanceFollowRedirects = true
        }

        val responseCode = connection.responseCode
        val contentType = connection.contentType ?: ""
        val contentLength = connection.contentLength

        val body = connection.inputStream.bufferedReader().use { it.readText() }

        // Truncate very large pages
        val maxBodyLength = 50_000
        val truncated = body.length > maxBodyLength
        val bodyText = if (truncated) body.take(maxBodyLength) else body

        connection.disconnect()

        return ToolResult.Success(
            """{"url": "${urlStr.replace("\"", "\\\"")}", "responseCode": $responseCode, "contentType": "${contentType.replace("\"", "\\\"")}", "contentLength": $contentLength, "body": "${bodyText.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t")}", "truncated": $truncated}"""
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Parse DuckDuckGo Lite HTML search results.
     * Lite.ddg.com returns simple HTML tables for results.
     */
    private fun parseDuckDuckGoLite(html: String, maxResults: Int): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()

        // Simple regex-based extraction for DuckDuckGo Lite
        // Results are in <a href="...">title</a> format within result tables
        val linkRegex = Regex(
            """<a\s+(?:[^>]*?\s+)?href="((?:https?://)?[^"]+)"[^>]*?>([^<]+)</a>""",
            RegexOption.IGNORE_CASE,
        )

        // DuckDuckGo Lite uses <a rel="nofollow" href="...">Title</a>
        val resultLinks = linkRegex.findAll(html).toList()

        // Skip the first few links (nav) and take actual results
        var skipCount = 3  // Skip navigation links
        val snippetRegex = Regex("""<td[^>]*class="result-snippet"[^>]*>(.*?)</td>""",
            RegexOption.DOT_MATCHES_ALL or RegexOption.IGNORE_CASE)
        val snippets = snippetRegex.findAll(html).toList()

        for ((index, match) in resultLinks.withIndex()) {
            if (results.size >= maxResults) break
            if (index < skipCount) continue

            val url = match.groupValues[1]
            val title = match.groupValues[2].trim()
                .replace(Regex("<[^>]+>"), "")  // Strip HTML tags

            // Skip if URL is a DuckDuckGo internal link
            if (url.contains("duckduckgo.com") && !url.contains("http", ignoreCase = true)) continue
            if (title.isBlank()) continue

            val snippet = snippets.getOrNull(results.size)?.groupValues?.get(1)
                ?.replace(Regex("<[^>]+>"), "")
                ?.trim()
                ?: ""

            results.add(
                mapOf(
                    "title" to title,
                    "url" to url,
                    "snippet" to snippet,
                )
            )
        }

        return results
    }

    private fun List<Map<String, String>>.toJsonArray(): String {
        val sb = StringBuilder("[")
        forEachIndexed { i, map ->
            if (i > 0) sb.append(", ")
            sb.append("{")
            map.entries.forEachIndexed { j, (key, value) ->
                if (j > 0) sb.append(", ")
                sb.append("\"$key\": \"")
                sb.append(value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n"))
                sb.append("\"")
            }
            sb.append("}")
        }
        sb.append("]")
        return sb.toString()
    }
}
