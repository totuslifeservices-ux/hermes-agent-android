package com.nousresearch.hermes.agent.core.tools.platform

import android.content.Context
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult
import com.nousresearch.hermes.agent.core.tools.HermesTool
import com.nousresearch.hermes.agent.core.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * WebSearchTool — Fetch web pages and search the web via HTTP.
 *
 * Capabilities:
 * - fetch_page: Fetch and return the text content of a web page
 * - search_web: Perform a web search using DuckDuckGo's Lite search (no API key needed)
 *
 * This tool uses OkHttp for HTTP and DuckDuckGo Lite for search (privacy-respecting,
 * no JavaScript needed, no API key required).
 *
 * Permissions: INTERNET (already declared in manifest).
 * Privacy: Fetched content is processed locally. No browsing data is shared.
 */
class WebSearchTool(private val context: Context) : HermesTool {

    companion object {
        private const val TAG = "WebSearchTool"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; HermesAgent) AppleWebKit/537.36"
        private const val MAX_PAGE_SIZE = 100_000 // 100KB max page fetch
        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    override val descriptor = ToolDescriptor(
        name = "web_search",
        description = "Fetch a web page's text content by URL, or search the web using a query. " +
            "Use fetch_page to get the readable text from a URL. " +
            "Use search_web to perform a web search and get result links.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("fetch_page", "search_web"),
                    "description" to "The web action to perform",
                ),
                "url" to mapOf(
                    "type" to "string",
                    "description" to "The URL to fetch (required for fetch_page)",
                ),
                "query" to mapOf(
                    "type" to "string",
                    "description" to "Search query (required for search_web)",
                ),
                "max_results" to mapOf(
                    "type" to "integer",
                    "description" to "Maximum search results (default: 5, max: 20)",
                    "default" to 5,
                ),
            ),
            "required" to listOf("action"),
        ),
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
                    "fetch_page" -> fetchPage(args)
                    "search_web" -> searchWeb(args)
                    else -> ToolResult.Error(
                        message = "Unknown web action: '$action'. Valid: fetch_page, search_web",
                        recoverable = true,
                    )
                }
            } catch (e: Exception) {
                ToolResult.Error(
                    message = "Web operation failed: ${e.message ?: "Unknown error"}",
                    recoverable = true,
                )
            }
        }

    override fun toString(): String = "WebSearchTool"

    // ── Actions ──────────────────────────────────────────────────────

    private fun fetchPage(args: Map<String, Any?>): ToolResult {
        val url = args["url"] as? String
            ?: return ToolResult.Error(
                message = "Missing required parameter: url",
                recoverable = true,
            )

        // Basic URL validation
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult.Error(
                message = "Invalid URL: must start with http:// or https://",
                recoverable = true,
            )
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return ToolResult.Error(
                    message = "HTTP ${response.code} fetching $url",
                    recoverable = true,
                )
            }

            // Extract text content — strip HTML tags
            val textContent = stripHtml(body)
            val truncated = if (textContent.length > MAX_PAGE_SIZE) {
                textContent.take(MAX_PAGE_SIZE) + "\n\n[Content truncated at ${MAX_PAGE_SIZE / 1000}KB]"
            } else {
                textContent
            }

            if (truncated.isBlank()) {
                return ToolResult.Success(
                    """{"url": "${escapeJson(url)}", "content": "(empty page)", "size_bytes": ${body.length}}"""
                )
            }

            return ToolResult.Success(
                """{"url": "${escapeJson(url)}", "content": "${escapeJson(truncated.take(5000))}", 
                    |"size_bytes": ${body.length}, "truncated": ${textContent.length > MAX_PAGE_SIZE}}""".trimMargin()
            )
        } catch (e: java.net.UnknownHostException) {
            return ToolResult.Error(
                message = "Network error: Cannot resolve host for $url. Check internet connection.",
                recoverable = true,
            )
        } catch (e: java.net.SocketTimeoutException) {
            return ToolResult.Error(
                message = "Timeout fetching $url (15s)",
                recoverable = true,
            )
        } catch (e: Exception) {
            return ToolResult.Error(
                message = "Failed to fetch $url: ${e.message ?: "Unknown error"}",
                recoverable = true,
            )
        }
    }

    private fun searchWeb(args: Map<String, Any?>): ToolResult {
        val query = args["query"] as? String
            ?: return ToolResult.Error(
                message = "Missing required parameter: query",
                recoverable = true,
            )
        val maxResults = (args["max_results"] as? Number)?.toInt()?.coerceIn(1, 20) ?: 5

        try {
            // Use DuckDuckGo Lite search (no API key, no JS, privacy-respecting)
            val searchUrl = "https://lite.duckduckgo.com/lite/?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return ToolResult.Error(
                    message = "Search engine returned HTTP ${response.code}",
                    recoverable = true,
                )
            }

            // Parse results from DuckDuckGo Lite's HTML table structure
            val results = parseDuckDuckGoResults(body, maxResults)

            if (results.isEmpty()) {
                return ToolResult.Success(
                    """{"query": "${escapeJson(query)}", "results": [], "count": 0, 
                        |"note": "No results found. Try a different query."}""".trimMargin()
                )
            }

            val jsonResults = buildString {
                append("[")
                results.forEachIndexed { i, (title, snippet, url) ->
                    if (i > 0) append(", ")
                    append("""{"title": "${escapeJson(title)}", "snippet": "${escapeJson(snippet)}", "url": "${escapeJson(url)}"}""")
                }
                append("]")
            }

            return ToolResult.Success(
                """{"query": "${escapeJson(query)}", "results": $jsonResults, "count": ${results.size}}"""
            )
        } catch (e: Exception) {
            return ToolResult.Error(
                message = "Search failed: ${e.message ?: "Unknown error"}",
                recoverable = true,
            )
        }
    }

    // ── HTML Parsing ────────────────────────────────────────────────

    /**
     * Strip HTML tags and return plain text content.
     */
    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&nbsp;", " ")
            .trim()
    }

    /**
     * Parse DuckDuckGo Lite search results from the HTML table structure.
     * Returns list of (title, snippet, url) tuples.
     */
    private fun parseDuckDuckGoResults(html: String, maxResults: Int): List<Triple<String, String, String>> {
        val results = mutableListOf<Triple<String, String, String>>()

        // Find all result tables — DDG Lite uses <table> with <tr> for each result
        val resultTables = Regex(
            "<table[^>]*class=\"[^\"]*result[^\"]*\"[^>]*>.*?</table>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        val tables = resultTables.findAll(html).toList()
        if (tables.isNotEmpty()) {
            for (table in tables.take(maxResults)) {
                val tableHtml = table.value
                // Extract title from <a> tag
                val titleMatch = Regex("<a[^>]*class=\"[^\"]*result-link[^\"]*\"[^>]*>(.*?)</a>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(tableHtml)
                val title = titleMatch?.groupValues?.get(1)?.let { stripHtml(it).trim() } ?: ""

                // Extract URL from <a> href
                val urlMatch = Regex("<a[^>]*href=\"([^\"]+)\"",
                    RegexOption.IGNORE_CASE).find(tableHtml)
                val url = urlMatch?.groupValues?.get(1) ?: ""

                // Extract snippet from result snippet class
                val snippetMatch = Regex("<td[^>]*class=\"[^\"]*result-snippet[^\"]*\"[^>]*>(.*?)</td>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(tableHtml)
                val snippet = snippetMatch?.groupValues?.get(1)?.let { stripHtml(it).trim() } ?: ""

                if (title.isNotBlank() && url.isNotBlank()) {
                    results.add(Triple(title, snippet, url))
                }
            }
        } else {
            // Fallback: try to parse links directly
            val links = Regex("<a[^>]*href=\"(https?://[^\"]+)\"[^>]*>(.*?)</a>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val matches = links.findAll(html).toList()
            for (match in matches.take(maxResults)) {
                val url = match.groupValues[1]
                val title = stripHtml(match.groupValues[2]).trim()
                if (title.isNotBlank() && !url.contains("duckduckgo.com")) {
                    results.add(Triple(title, "", url))
                }
            }
        }

        return results
    }

    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
