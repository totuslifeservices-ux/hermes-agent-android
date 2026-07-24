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
 * Provider that connects to a local [Ollama](https://ollama.com) instance
 * via its OpenAI-compatible endpoint (`/v1/chat/completions`).
 *
 * Ollama runs models locally with no external network dependency and no
 * API key required. The default base URL is `http://localhost:11434/v1`,
 * but this can be overridden via [ProviderConfig.baseUrl] for remote or
 * custom Ollama servers.
 *
 * @property config The provider configuration (model, temperature, etc.).
 * @property httpClient The [OkHttpClient] for HTTP requests.
 */
class OllamaProvider(
    override val type: ProviderType = ProviderType.Ollama,
    private val config: ProviderConfig,
    private val httpClient: OkHttpClient = OllamaProvider.defaultHttpClient(),
) : LlmProvider {

    companion object {
        private const val TAG = "OllamaProvider"

        /** Default Ollama OpenAI-compatible endpoint. */
        private const val DEFAULT_BASE_URL = "http://localhost:11434/v1"

        /** Media type for JSON request bodies. */
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** Creates a default [OkHttpClient] with generous timeouts for local models. */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)  // 5 min — local models can be slow
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
            for (attempt in 0..maxRetries) {
                try {
                    val response = httpClient.newCall(httpRequest).execute()
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        throw IOException("Ollama API error ${response.code}: ${body.take(200)}")
                    }
                    return@withContext parseCompleteResponse(body)
                } catch (e: java.net.ConnectException) {
                    if (attempt < maxRetries) {
                        val backoff = (1L shl attempt) * 1000L
                        Log.w(TAG, "Ollama not running? Retrying in ${backoff}ms (attempt ${attempt + 1}/$maxRetries)")
                        kotlinx.coroutines.delay(backoff)
                    } else {
                        lastError = IOException("Ollama connection refused. Ensure Ollama is running (http://localhost:11434)")
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    if (attempt < maxRetries) {
                        val backoff = (1L shl attempt) * 1000L
                        kotlinx.coroutines.delay(backoff)
                    } else {
                        lastError = IOException("Ollama connection timed out. Check that Ollama is running.")
                    }
                } catch (e: Exception) {
                    if (attempt >= maxRetries) throw e
                    kotlinx.coroutines.delay(1000L)
                }
            }
            throw lastError ?: IOException("Ollama request failed after ${maxRetries + 1} attempts")
        }

    override fun stream(request: CompletionRequest): Flow<StreamEvent> {
        val httpRequest = buildHttpRequest(request, stream = true)
        return StreamingAdapter.stream(httpClient, httpRequest).flowOn(Dispatchers.IO)
    }

    // ── Request building ────────────────────────────────────────────

    /**
     * Builds the OkHttp [Request] for the chat completions endpoint.
     * No auth headers — Ollama runs locally without authentication.
     */
    private fun buildHttpRequest(
        request: CompletionRequest,
        stream: Boolean,
    ): Request {
        val jsonBody = buildRequestBody(request, stream)
        val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)

        return Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()
    }

    /**
     * Serializes a [CompletionRequest] to the OpenAI-compatible JSON payload.
     *
     * Note: Ollama's OpenAI compatibility layer supports the standard schema
     * including tool_calls, though tool support depends on the specific model
     * being served.
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
     * Parses a non-streaming Ollama response into [CompletionResponse].
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
