package com.nousresearch.hermes.agent.core.llm

import android.util.Log
import com.nousresearch.hermes.agent.core.CompletionRequest
import com.nousresearch.hermes.agent.core.CompletionResponse
import com.nousresearch.hermes.agent.core.LlmMessage
import com.nousresearch.hermes.agent.core.LlmToolCall
import com.nousresearch.hermes.agent.core.MessageRole
import com.nousresearch.hermes.agent.core.ProviderConfig
import com.nousresearch.hermes.agent.core.ProviderType
import com.nousresearch.hermes.agent.core.StreamEvent
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.UsageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Provider that connects to the [OpenRouter API](https://openrouter.ai/api/v1)
 * using an API key for authentication.
 *
 * OpenRouter provides unified access to hundreds of models from dozens of
 * providers, with built-in fallback and load balancing.
 *
 * @property config The provider configuration (model, temperature, etc.).
 * @property apiKey The OpenRouter API key.
 * @property httpClient The [OkHttpClient] for HTTP requests.
 */
class OpenRouterProvider(
    override val type: ProviderType = ProviderType.OpenRouter,
    private val config: ProviderConfig,
    private val apiKey: String,
    private val httpClient: OkHttpClient = OpenRouterProvider.defaultHttpClient(),
) : LlmProvider {

    companion object {
        private const val TAG = "OpenRouterProvider"

        /** Default base URL for the OpenRouter API. */
        private const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"

        /** Media type for JSON request bodies. */
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** Creates a default [OkHttpClient] with appropriate timeouts. */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val baseUrl: String = config.baseUrl ?: DEFAULT_BASE_URL

    /** Shared [Json] instance for serializing/deserializing API payloads. */
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // ── LlmProvider implementation ──────────────────────────────────

    override suspend fun complete(request: CompletionRequest): CompletionResponse {
        val httpRequest = buildHttpRequest(request, stream = false)
        return executeWithRetry(httpRequest, maxRetries = 3)
    }

    private suspend fun executeWithRetry(httpRequest: Request, maxRetries: Int): CompletionResponse =
        withContext(Dispatchers.IO) {
            var lastError: Exception? = null
            var retries = 0
            var response = httpClient.newCall(httpRequest).execute()
            var body = response.body?.string() ?: ""
            
            while (retries < maxRetries) {
                if (response.isSuccessful) {
                    return@withContext parseCompleteResponse(body)
                }
                
                when (response.code) {
                    401 -> {
                        if (retries < 2) {
                            retries++
                            response = httpClient.newCall(httpRequest).execute()
                            body = response.body?.string() ?: ""
                            continue
                        }
                        lastError = IOException("Authentication failed after ${retries + 1} attempts")
                        break
                    }
                    429 -> {
                        val retryAfter = response.header("Retry-After")?.toIntOrNull() ?: 5
                        retries++
                        kotlinx.coroutines.delay(retryAfter * 1000L)
                        response = httpClient.newCall(httpRequest).execute()
                        body = response.body?.string() ?: ""
                        continue
                    }
                    500, 502, 503 -> {
                        val backoff = (1L shl retries) * 1000L
                        retries++
                        kotlinx.coroutines.delay(backoff)
                        response = httpClient.newCall(httpRequest).execute()
                        body = response.body?.string() ?: ""
                        continue
                    }
                    else -> {
                        lastError = IOException("API error ${response.code}: ${body.take(200)}")
                        break
                    }
                }
            }
            throw lastError ?: IOException("Request failed after $maxRetries retries: ${response.code}")
        }

override fun stream(request: CompletionRequest): Flow<StreamEvent> {
        val httpRequest = buildHttpRequest(request, stream = true)
        return StreamingAdapter.stream(httpClient, httpRequest).flowOn(Dispatchers.IO)
    }

    // ── Request building ────────────────────────────────────────────

    /**
     * Builds the OkHttp [Request] for the chat completions endpoint
     * with OpenRouter-specific headers.
     */
    private fun buildHttpRequest(
        request: CompletionRequest,
        stream: Boolean,
    ): Request {
        val jsonBody = buildRequestBody(request, stream)
        val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)

        return Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            // OpenRouter recommended headers for identification
            .header("HTTP-Referer", "https://hermes-agent.nousresearch.com")
            .header("X-Title", "Hermes Agent Android")
            .post(requestBody)
            .build()
    }

    /**
     * Serializes a [CompletionRequest] to the OpenAI-compatible JSON payload.
     * (Identical structure to [NousPortalProvider] — OpenRouter mirrors the
     * OpenAI spec faithfully.)
     */
    private fun buildRequestBody(
        request: CompletionRequest,
        stream: Boolean,
    ): String {
        return buildJsonObject {
            put("model", request.model.takeIf { it.isNotBlank() } ?: config.model)
            put("stream", stream)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature.toDouble())

            // Messages
            putJsonArray("messages") {
                for (msg in request.messages) {
                    addJsonObject {
                        put("role", msg.role.name.lowercase())
                        msg.content?.let { put("content", it) } ?: put("content", "")

                        if (msg.role == MessageRole.Tool) {
                            msg.toolCallId?.let { put("tool_call_id", it) }
                            msg.name?.let { put("name", it) }
                        }

                        msg.toolCalls?.let { calls ->
                            putJsonArray("tool_calls") {
                                for (tc in calls) {
                                    addJsonObject {
                                        put("id", tc.id)
                                        put("type", "function")
                                        putJsonObject("function") {
                                            put("name", tc.name)
                                            put("arguments", tc.arguments)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tools (function definitions)
            request.tools?.let { tools ->
                if (tools.isNotEmpty()) {
                    putJsonArray("tools") {
                        for (tool in tools) {
                            addJsonObject {
                                put("type", "function")
                                putJsonObject("function") {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    putJsonObject("parameters") {
                                        for ((key, value) in tool.parameters) {
                                            put(key, serializeParameterValue(value))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.toString()
    }

    /**
     * Serializes a parameter value into a [JsonElement].
     */
    private fun serializeParameterValue(value: Any?): JsonElement = when (value) {
        is Map<*, *> -> {
            buildJsonObject {
                for ((k, v) in value) {
                    k?.toString()?.let { key -> put(key, serializeParameterValue(v)) }
                }
            }
        }
        is List<*> -> buildJsonArray {
            for (item in value) {
                add(serializeParameterValue(item))
            }
        }
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        else -> JsonPrimitive(value?.toString() ?: "null")
    }

    // ── Response parsing ────────────────────────────────────────────

    /**
     * Parses a non-streaming OpenRouter response into [CompletionResponse].
     */
    private fun parseCompleteResponse(body: String): CompletionResponse {
        val root: JsonObject = json.decodeFromString(body)
        val choices = root["choices"]?.jsonArray ?: return CompletionResponse(
            content = null,
            finishReason = "error",
        )

        if (choices.isEmpty()) {
            return CompletionResponse(content = null, finishReason = "empty")
        }

        val choice = choices[0].jsonObject
        val message = choice["message"]?.jsonObject
        val finishReason = choice["finish_reason"]?.jsonPrimitive?.let { it.content }

        val content = message?.get("content")?.jsonPrimitive?.let { it.content }

        val toolCalls = message?.get("tool_calls")?.jsonArray?.mapNotNull { tcElem ->
            val tcObj = tcElem.jsonObject
            val function = tcObj["function"]?.jsonObject ?: return@mapNotNull null
            LlmToolCall(
                id = tcObj["id"]?.jsonPrimitive?.let { it.content } ?: "",
                name = function["name"]?.jsonPrimitive?.let { it.content } ?: "",
                arguments = function["arguments"]?.jsonPrimitive?.let { it.content } ?: "{}",
            )
        }

        val usage = root["usage"]?.jsonObject?.let { usageObj ->
            UsageInfo(
                promptTokens = usageObj["prompt_tokens"]?.jsonPrimitive?.let { it.content.toIntOrNull() } ?: 0,
                completionTokens = usageObj["completion_tokens"]?.jsonPrimitive?.let { it.content.toIntOrNull() } ?: 0,
                totalTokens = usageObj["total_tokens"]?.jsonPrimitive?.let { it.content.toIntOrNull() } ?: 0,
            )
        }

        return CompletionResponse(
            content = content,
            toolCalls = toolCalls?.takeIf { it.isNotEmpty() },
            finishReason = finishReason,
            usage = usage,
        )
    }
}
