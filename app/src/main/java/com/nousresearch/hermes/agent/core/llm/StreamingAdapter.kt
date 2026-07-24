package com.nousresearch.hermes.agent.core.llm

import android.util.Log
import com.nousresearch.hermes.agent.core.CompletionResponse
import com.nousresearch.hermes.agent.core.LlmToolCall
import com.nousresearch.hermes.agent.core.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Transforms an OkHttp streaming (SSE) response into a coroutine [Flow] of [StreamEvent]s.
 *
 * The adapter parses Server-Sent Events as emitted by OpenAI-compatible chat
 * completions endpoints. Each `data: {...}` line is parsed into either a
 * [StreamEvent.TextChunk] (delta content), [StreamEvent.ToolCall] (delta tool
 * call fragments), or [StreamEvent.Done] (the `[DONE]` signal).
 *
 * Usage:
 * ```kotlin
 * val events: Flow<StreamEvent> = StreamingAdapter.stream(client, request)
 * events.collect { event ->
 *     when (event) { ... }
 * }
 * ```
 */
object StreamingAdapter {

    private const val TAG = "StreamingAdapter"

    /** Shared [Json] instance configured for lenient parsing of partial deltas. */
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Perform a streaming request and return a [Flow] of [StreamEvent].
     *
     * The HTTP connection is kept open for the duration of the flow collection.
     * Cancelling the collector's coroutine scope cancels the HTTP call.
     *
     * @param client The [OkHttpClient] to use.
     * @param request The [Request] to execute (must have streaming enabled server-side).
     * @return A cold [Flow] emitting [StreamEvent] values.
     */
    fun stream(
        client: OkHttpClient,
        request: Request,
    ): Flow<StreamEvent> = callbackFlow {
        val cancelled = AtomicBoolean(false)

        val call = client.newCall(request)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cancelled.get()) return
                trySend(StreamEvent.Error("HTTP request failed: ${e.message}"))
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cancelled.get()) {
                    response.close()
                    return
                }

                if (!response.isSuccessful) {
                    val body = response.body?.string() ?: "<no body>"
                    trySend(StreamEvent.Error("HTTP ${response.code}: $body"))
                    response.close()
                    close()
                    return
                }

                val body = response.body ?: run {
                    trySend(StreamEvent.Error("Empty response body"))
                    response.close()
                    close()
                    return
                }

                // Collect the SSE stream on IO dispatcher
                try {
                    collectSse(body, cancelled) { event ->
                        trySend(event)
                    }
                } catch (e: Exception) {
                    if (!cancelled.get()) {
                        Log.w(TAG, "SSE collection error", e)
                        trySend(StreamEvent.Error(e.message ?: "Unknown SSE error"))
                    }
                } finally {
                    body.close()
                    close()
                }
            }
        })

        // Cleanup on cancellation
        awaitClose {
            cancelled.set(true)
            call.cancel()
        }
    }

    /**
     * Synchronous streaming helper — returns [Flow] from an already-opened [Response].
     *
     * Used when the caller wants to manage the HTTP call lifecycle externally.
     */
    fun streamFromResponse(
        response: Response,
    ): Flow<StreamEvent> = callbackFlow {
        val cancelled = AtomicBoolean(false)

        if (!response.isSuccessful) {
            val body = response.body?.string() ?: "<no body>"
            trySend(StreamEvent.Error("HTTP ${response.code}: $body"))
            response.close()
            close()
            return@callbackFlow
        }

        val body = response.body ?: run {
            trySend(StreamEvent.Error("Empty response body"))
            response.close()
            close()
            return@callbackFlow
        }

        try {
            collectSse(body, cancelled) { event ->
                trySend(event)
            }
        } catch (e: Exception) {
            if (!cancelled.get()) {
                Log.w(TAG, "SSE collection error", e)
                trySend(StreamEvent.Error(e.message ?: "Unknown SSE error"))
            }
        } finally {
            body.close()
            close()
        }

        awaitClose {
            cancelled.set(true)
            response.close()
        }
    }

    // ── Internal SSE parsing ────────────────────────────────────────

    /**
     * Read lines from the response body, parse SSE `data: ...` events,
     * and emit [StreamEvent] values through [emit].
     */
    private fun collectSse(
        body: ResponseBody,
        cancelled: AtomicBoolean,
        emit: (StreamEvent) -> Unit,
    ) {
        val reader: BufferedReader = body.charStream().buffered()

        // Buffers for delta tool-call accumulation
        var toolCallBuffer: MutableList<LlmToolCall>? = null
        val toolCallIndexes = mutableMapOf<Int, String>() // index -> id
        val toolCallNames = mutableMapOf<Int, String>()   // index -> name
        val toolCallArgs = mutableMapOf<Int, StringBuilder>() // index -> args

        reader.use { r ->
            var line: String?
            while (r.readLine().also { line = it } != null) {
                if (cancelled.get()) return@use

                val l = line ?: continue
                if (!l.startsWith("data: ")) continue

                val payload = l.removePrefix("data: ").trim()

                // Terminal signal
                if (payload == "[DONE]") {
                    // Flush any accumulated tool calls
                    toolCallBuffer?.let { calls ->
                        // Merge deltas into complete calls
                        val merged = calls.map { call ->
                            val idx = call.id.toIntOrNull() ?: return@let
                            LlmToolCall(
                                id = toolCallIndexes[idx] ?: call.id,
                                name = toolCallNames[idx] ?: call.name,
                                arguments = toolCallArgs[idx]?.toString() ?: call.arguments,
                            )
                        }
                        emit(StreamEvent.ToolCall(merged.last()))
                    }
                    emit(StreamEvent.Done)
                    return@use
                }

                try {
                    val root: JsonObject = json.decodeFromString(payload)
                    val choices = root["choices"]?.jsonArray ?: continue
                    if (choices.isEmpty()) continue

                    val choice = choices[0].jsonObject
                    val delta = choice["delta"]?.jsonObject
                    val finishReason = choice["finishReason"]?.jsonPrimitive?.let { it.content }
                        ?: choice["finish_reason"]?.jsonPrimitive?.let { it.content }

                    // ── Content delta ─────────────────────────────────
                    if (delta != null) {
                        val content = delta["content"]?.jsonPrimitive?.let { it.content }
                        if (content != null) {
                            emit(StreamEvent.TextChunk(content))
                        }

                        // ── Tool call deltas (accumulate across chunks) ──
                        val toolCallsJson = delta["tool_calls"]?.jsonArray
                            ?: delta["toolCalls"]?.jsonArray
                        if (toolCallsJson != null) {
                            if (toolCallBuffer == null) {
                                toolCallBuffer = mutableListOf()
                            }
                            for (tcElem in toolCallsJson) {
                                val tcObj = tcElem.jsonObject
                                val index = tcObj["index"]?.jsonPrimitive?.let { it.content.toIntOrNull() } ?: 0
                                val funcObj = tcObj["function"]?.jsonObject
                                val id = tcObj["id"]?.jsonPrimitive?.let { it.content }
                                val name = funcObj?.get("name")?.jsonPrimitive?.let { it.content }
                                val argsPart = funcObj?.get("arguments")?.jsonPrimitive?.let { it.content }

                                if (id != null) toolCallIndexes[index] = id
                                if (name != null) toolCallNames[index] = name
                                if (argsPart != null) {
                                    toolCallArgs.getOrPut(index) { StringBuilder() }
                                        .append(argsPart)
                                }
                            }
                        }
                    }

                    // ── Finish reason ─────────────────────────────────
                    if (finishReason != null && finishReason != "null") {
                        // Flush accumulated tool calls
                        if (finishReason == "tool_calls" && toolCallBuffer != null) {
                            val finalCall = toolCallArgs.entries.map { (idx, sb) ->
                                LlmToolCall(
                                    id = toolCallIndexes[idx] ?: "call_$idx",
                                    name = toolCallNames[idx] ?: "unknown",
                                    arguments = sb.toString(),
                                )
                            }.lastOrNull()
                            if (finalCall != null) {
                                emit(StreamEvent.ToolCall(finalCall))
                            }
                            toolCallBuffer = null
                        }

                        if (finishReason == "stop" || finishReason == "length") {
                            emit(StreamEvent.Done)
                            return@use
                        }
                    }
                } catch (e: Exception) {
                    // Malformed JSON in a delta chunk — skip, don't crash
                    Log.v(TAG, "SSE parse skip on chunk: ${e.message}")
                }
            }

            // Stream ended without [DONE] — emit done anyway
            emit(StreamEvent.Done)
        }
    }
}
