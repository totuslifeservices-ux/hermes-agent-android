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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Provider that connects to [Nous Research inference API](https://inference-api.nousresearch.com/v1)
 * using OpenAI-compatible endpoints with OAuth token authentication.
 *
 * The token is resolved at call time via the provided [tokenProvider] lambda,
 * allowing the caller to manage token refresh without provider lifecycle coupling.
 *
 * @property config The provider configuration (model, temperature, etc.).
 * @property httpClient The [OkHttpClient] for HTTP requests.
 * @property tokenProvider Suspending lambda that returns the current OAuth access token.
 *     Return `null` to indicate no token is available (request will fail with 401).
 */
class NousPortalProvider(
    override val type: ProviderType = ProviderType.NousPortal,
    private val config: ProviderConfig,
    private val httpClient: OkHttpClient = NousPortalProvider.defaultHttpClient(),
    private val tokenProvider: suspend () -> String?,
) : LlmProvider {

    companion object {
        private const val TAG = "NousPortalProvider"

        /** Default base URL for the Nous Research inference API. */
        private const val DEFAULT_BASE_URL = "https://inference-api.nousresearch.com/v1"

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
        return withContext(Dispatchers.IO) {
            httpClient.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    throw IOException("NousPortal API error ${response.code}: $body")
                }
                parseCompleteResponse(body)
            }
        }
    }

    override fun stream(request: CompletionRequest): Flow<StreamEvent> {
        val httpRequest = buildHttpRequest(request, stream = true)
        return StreamingAdapter.stream(httpClient, httpRequest)
    }

    // ── Request building ────────────────────────────────────────────

    /**
     * Builds the OkHttp [Request] for the chat completions endpoint.
     *
     * @param request The logical completion request.
     * @param stream Whether to request a streaming response.
     */
    private suspend fun buildHttpRequest(
        request: CompletionRequest,
        stream: Boolean,
    ): Request {
        val token = tokenProvider()
            ?: throw IOException("NousPortal: no OAuth token available")

        val requestBody = buildRequestBody(request, stream).toRequestBody(JSON_MEDIA_TYPE)

        return Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()
    }

    /**
     * Serializes a [CompletionRequest] to the OpenAI-compatible JSON payload.
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

                        // Tool call results from the assistant
                        if (msg.role == MessageRole.Tool) {
                            msg.toolCallId?.let { put("tool_call_id", it) }
                            msg.name?.let { put("name", it) }
                        }

                        // Tool calls made by the assistant
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
     * Serializes a parameter value (from [ToolDescriptor.parameters] map)
     * into a [JsonElement]. Handles maps, lists, strings, numbers, booleans.
     */
    private fun serializeParameterValue(value: Any?): JsonElement = when (value) {
        is Map<*, *> -> {
            val obj = buildJsonObject {
                for ((k, v) in value) {
                    k?.toString()?.let { key ->
                        put(key, serializeParameterValue(v))
                    }
                }
            }
            obj
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
     * Parses a non-streaming chat completion response into [CompletionResponse].
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
        val finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull

        val content = message?.get("content")?.jsonPrimitive?.contentOrNull

        val toolCalls = message?.get("tool_calls")?.jsonArray?.mapNotNull { tcElem ->
            val tcObj = tcElem.jsonObject
            val function = tcObj["function"]?.jsonObject ?: return@mapNotNull null
            LlmToolCall(
                id = tcObj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                name = function["name"]?.jsonPrimitive?.contentOrNull ?: "",
                arguments = function["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}",
            )
        }

        // Parse usage info
        val usage = root["usage"]?.jsonObject?.let { usageObj ->
            UsageInfo(
                promptTokens = usageObj["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                completionTokens = usageObj["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                totalTokens = usageObj["total_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
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
