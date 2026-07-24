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
import java.io.IOException
import com.nousresearch.hermes.agent.core.UsageInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Executes tool calls and returns their results as string content.
 *
 * This is the bridge between the LLM's tool-call decisions and the actual
 * tool implementations. The concrete implementation is injected at
 * construction time.
 */
fun interface ToolExecutor {
    /**
     * Execute a tool call and return its string result.
     *
     * @param toolCall The tool call to execute.
     * @return The tool result as a string (JSON, text, etc.).
     * @throws Exception if the tool execution fails — the [LlmBroker] will
     *   capture this and return it as an error result to the model.
     */
    suspend fun execute(toolCall: LlmToolCall): String
}

/**
 * Tracer/observability hook for monitoring LLM broker operations.
 *
 * Implementations can log, record metrics, or emit events for each phase
 * of the ReAct loop.
 */
interface LlmBrokerTracer {
    /** Called before an LLM completion request is sent. */
    suspend fun onProviderCall(
        provider: ProviderType,
        model: String,
        messageCount: Int,
        toolCount: Int,
        iteration: Int,
    )

    /** Called after a successful LLM completion response is received. */
    suspend fun onProviderResponse(
        provider: ProviderType,
        model: String,
        response: CompletionResponse,
        durationMs: Long,
        iteration: Int,
    )

    /** Called after each tool execution. */
    suspend fun onToolExecuted(
        toolName: String,
        result: String,
        isError: Boolean,
        durationMs: Long,
        iteration: Int,
    )

    /** Called when the ReAct loop completes (stop, max iterations, or error). */
    suspend fun onLoopComplete(
        totalIterations: Int,
        totalTokens: Int,
        totalDurationMs: Long,
        reason: String,
    )

    companion object {
        /** No-op tracer for production use when observability is not needed. */
        val NOOP = object : LlmBrokerTracer {
            override suspend fun onProviderCall(
                provider: ProviderType, model: String,
                messageCount: Int, toolCount: Int, iteration: Int,
            ) = Unit

            override suspend fun onProviderResponse(
                provider: ProviderType, model: String,
                response: CompletionResponse, durationMs: Long, iteration: Int,
            ) = Unit

            override suspend fun onToolExecuted(
                toolName: String, result: String,
                isError: Boolean, durationMs: Long, iteration: Int,
            ) = Unit

            override suspend fun onLoopComplete(
                totalIterations: Int, totalTokens: Int,
                totalDurationMs: Long, reason: String,
            ) = Unit
        }
    }
}

/**
 * Central broker that routes LLM requests to the correct provider and
 * manages the ReAct (Reasoning + Acting) tool-use loop.
 *
 * ## ReAct Loop
 * 1. Send the message history (including system prompt) to the LLM
 * 2. If the model returns tool calls, execute them and append results
 * 3. Re-prompt the LLM with the tool results
 * 4. Repeat until the model responds with content (stop) or max iterations is hit
 *
 * ## Usage
 * ```kotlin
 * val broker = LlmBroker(
 *     providers = mapOf(
 *         ProviderType.NousPortal to nousProvider,
 *         ProviderType.Ollama to ollamaProvider,
 *     ),
 *     toolExecutor = myToolExecutor,
 * )
 *
 * val response = broker.complete(messages, tools)
 * ```
 *
 * @param providers Map from [ProviderType] to [LlmProvider] instances.
 *   The broker routes based on the [ProviderConfig.type] associated with
 *   each request.
 * @param defaultConfig Default [ProviderConfig] used when no config is
 *   explicitly provided per-request.
 * @param toolExecutor The [ToolExecutor] that runs tool calls.
 * @param defaultMaxToolIterations Maximum ReAct loop iterations before
 *   forced stop (default: 25).
 * @param tracer Optional [LlmBrokerTracer] for observability.
 */
class LlmBroker(
    private val providers: Map<ProviderType, LlmProvider>,
    private val defaultConfig: ProviderConfig,
    private val toolExecutor: ToolExecutor,
    private val defaultMaxToolIterations: Int = 25,
    private val tracer: LlmBrokerTracer = LlmBrokerTracer.NOOP,
) {
    companion object {
        private const val TAG = "LlmBroker"
    }

    /** Returns the current default provider configuration. */
    fun getCurrentConfig(): ProviderConfig = defaultConfig

    /**
     * Returns a fallback provider when the primary provider fails.
     * Chains: NousPortal → OpenRouter → Ollama → null
     */
    fun getFallbackProvider(current: ProviderType): LlmProvider? {
        val fallbackOrder = listOf(
            ProviderType.NousPortal,
            ProviderType.OpenRouter,
            ProviderType.Ollama,
        )
        val startIndex = fallbackOrder.indexOf(current) + 1
        for (i in startIndex until fallbackOrder.size) {
            val candidate = providers[fallbackOrder[i]]
            if (candidate != null) return candidate
        }
        return null
    }

    /**
     * Execute with automatic fallback on recoverable errors.
     */
    suspend fun completeWithFallback(
        messages: MutableList<LlmMessage>,
        tools: List<ToolDescriptor>? = null,
        config: ProviderConfig = defaultConfig,
        maxToolIterations: Int = defaultMaxToolIterations,
    ): CompletionResponse {
        var lastError: Exception? = null
        var currentConfig = config
        var attempts = 0
        val maxAttempts = providers.size
        
        while (attempts < maxAttempts) {
            try {
                return complete(messages, tools, currentConfig, maxToolIterations)
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Provider ${currentConfig.type} failed: ${e.message}")
                
                val fallback = getFallbackProvider(currentConfig.type)
                if (fallback == null || attempts >= maxAttempts - 1) break
                
                currentConfig = currentConfig.copy(type = fallback.type)
                Log.i(TAG, "Falling back to ${currentConfig.type}")
                attempts++
            }
        }
        throw lastError ?: IOException("All providers failed")
    }

    // ── Public API: Single-shot ─────────────────────────────────────

    /**
     * Execute a complete ReAct loop and return the final response.
     *
     * @param messages The conversation history (will be appended to
     *   during tool iterations — pass a copy if you need immutability).
     * @param tools Available tool definitions.
     * @param config Optional [ProviderConfig] override.
     * @param maxToolIterations Optional per-call iteration limit override.
     * @return The final [CompletionResponse] from the model.
     */
    suspend fun complete(
        messages: MutableList<LlmMessage>,
        tools: List<ToolDescriptor>? = null,
        config: ProviderConfig = defaultConfig,
        maxToolIterations: Int = defaultMaxToolIterations,
    ): CompletionResponse {
        val startTime = System.currentTimeMillis()
        val provider = resolveProvider(config.type)
        var totalTokens = 0

        // Accumulated usage from all iterations
        var totalUsage: UsageInfo? = null

        for (iteration in 0 until maxToolIterations) {
            val request = CompletionRequest(
                model = config.model,
                messages = messages.toList(), // snapshot for this iteration
                tools = tools,
                maxTokens = config.maxTokens,
                temperature = config.temperature,
                stream = false,
            )

            // ── Call the LLM ───────────────────────────────────────
            tracer.onProviderCall(config.type, config.model, messages.size, tools?.size ?: 0, iteration)
            val iterStart = System.currentTimeMillis()

            val response: CompletionResponse = try {
                provider.complete(request)
            } catch (e: Exception) {
                Log.e(TAG, "Provider call failed at iteration $iteration", e)
                tracer.onLoopComplete(iteration, totalTokens, System.currentTimeMillis() - startTime, "error")
                return CompletionResponse(
                    content = null,
                    finishReason = "error",
                    usage = totalUsage,
                )
            }

            val iterDuration = System.currentTimeMillis() - iterStart
            tracer.onProviderResponse(config.type, config.model, response, iterDuration, iteration)

            // Track token usage
            response.usage?.let { usage ->
                totalTokens += usage.totalTokens
                totalUsage = usage
            }

            // ── Check finish reason ────────────────────────────────
            when (response.finishReason) {
                "stop", "length", null -> {
                    // Model responded with content — we're done
                    tracer.onLoopComplete(
                        iteration + 1, totalTokens,
                        System.currentTimeMillis() - startTime, "stop",
                    )
                    return response.copy(usage = totalUsage)
                }
                "tool_calls" -> {
                    // Model wants to call tools
                    val toolCalls = response.toolCalls
                    if (toolCalls.isNullOrEmpty()) {
                        // Model said tool_calls but provided none — treat as stop
                        tracer.onLoopComplete(
                            iteration + 1, totalTokens,
                            System.currentTimeMillis() - startTime, "stop",
                        )
                        return response.copy(
                            finishReason = "stop",
                            usage = totalUsage,
                        )
                    }

                    // Append the assistant's message with tool calls
                    messages.add(
                        LlmMessage(
                            role = MessageRole.Assistant,
                            content = response.content,
                            toolCalls = toolCalls,
                        )
                    )

                    // ── Execute each tool ──────────────────────────
                    var allSucceeded = true
                    for (tc in toolCalls) {
                        val toolStart = System.currentTimeMillis()
                        try {
                            val result = toolExecutor.execute(tc)
                            val toolDuration = System.currentTimeMillis() - toolStart
                            tracer.onToolExecuted(tc.name, result, false, toolDuration, iteration)

                            messages.add(
                                LlmMessage(
                                    role = MessageRole.Tool,
                                    content = result,
                                    toolCallId = tc.id,
                                    name = tc.name,
                                )
                            )
                        } catch (e: Exception) {
                            val toolDuration = System.currentTimeMillis() - toolStart
                            val errorResult = "Error executing tool '${tc.name}': ${e.message}"
                            tracer.onToolExecuted(tc.name, errorResult, true, toolDuration, iteration)

                            messages.add(
                                LlmMessage(
                                    role = MessageRole.Tool,
                                    content = errorResult,
                                    toolCallId = tc.id,
                                    name = tc.name,
                                )
                            )
                            allSucceeded = false
                        }
                    }

                    // Continue loop — re-prompt the model with tool results
                }
                "error" -> {
                    Log.w(TAG, "Provider returned error finish_reason at iteration $iteration")
                    tracer.onLoopComplete(iteration + 1, totalTokens, System.currentTimeMillis() - startTime, "error")
                    return response.copy(usage = totalUsage)
                }
                else -> {
                    // Unknown finish reason — treat as stop
                    Log.v(TAG, "Unknown finish_reason: ${response.finishReason}, treating as stop")
                    tracer.onLoopComplete(
                        iteration + 1, totalTokens,
                        System.currentTimeMillis() - startTime, "stop",
                    )
                    return response.copy(
                        finishReason = "stop",
                        usage = totalUsage,
                    )
                }
            }
        }

        // ── Max iterations reached ─────────────────────────────────
        val totalDuration = System.currentTimeMillis() - startTime
        tracer.onLoopComplete(defaultMaxToolIterations, totalTokens, totalDuration, "max_iterations")
        Log.w(TAG, "ReAct loop reached max iterations ($defaultMaxToolIterations)")

        return CompletionResponse(
            content = "I've reached the maximum number of tool call iterations ($defaultMaxToolIterations). " +
                "I may not have completed all tasks. Please let me know if you need me to continue.",
            finishReason = "max_iterations",
            usage = totalUsage,
        )
    }

    // ── Public API: Streaming ───────────────────────────────────────

    /**
     * Execute a ReAct loop with streaming. Emits [StreamEvent] values as
     * the model generates and as tools are executed.
     *
     * The emitted flow includes:
     * - [StreamEvent.TextChunk] — streaming content from the model
     * - [StreamEvent.ToolCall] — when the model decides to call a tool
     * - [StreamEvent.ToolResult] — after a tool has been executed
     * - [StreamEvent.Done] — when the loop completes
     * - [StreamEvent.Error] — when an unrecoverable error occurs
     *
     * @param messages The conversation history.
     * @param tools Available tool definitions.
     * @param config Optional [ProviderConfig] override.
     * @param maxToolIterations Optional per-call iteration limit override.
     */
    fun stream(
        messages: MutableList<LlmMessage>,
        tools: List<ToolDescriptor>? = null,
        config: ProviderConfig = defaultConfig,
        maxToolIterations: Int = defaultMaxToolIterations,
    ): Flow<StreamEvent> = flow {
        val startTime = System.currentTimeMillis()
        val provider = resolveProvider(config.type)
        var totalTokens = 0

        for (iteration in 0 until maxToolIterations) {
            val request = CompletionRequest(
                model = config.model,
                messages = messages.toList(),
                tools = tools,
                maxTokens = config.maxTokens,
                temperature = config.temperature,
                stream = true,
            )

            tracer.onProviderCall(config.type, config.model, messages.size, tools?.size ?: 0, iteration)
            val iterStart = System.currentTimeMillis()

            // ── Accumulate streaming response ──────────────────────
            val contentBuilder = StringBuilder()
            val toolCallBuilder = mutableListOf<LlmToolCall>()
            var finishReason: String? = null
            var errorOccurred = false

            try {
                provider.stream(request).collect { event ->
                    when (event) {
                        is StreamEvent.TextChunk -> {
                            contentBuilder.append(event.text)
                            emit(event)
                        }
                        is StreamEvent.ToolCall -> {
                            toolCallBuilder.add(event.call)
                            emit(event)
                        }
                        is StreamEvent.ToolResult -> emit(event)
                        is StreamEvent.Done -> { /* handled after collection */ }
                        is StreamEvent.Error -> {
                            errorOccurred = true
                            emit(event)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Stream error at iteration $iteration", e)
                emit(StreamEvent.Error(e.message ?: "Unknown stream error"))
                return@flow
            }

            if (errorOccurred) return@flow

            val iterDuration = System.currentTimeMillis() - iterStart

            // Determine finish reason from last content analysis
            // In streaming mode we infer from tool calls presence
            val hasToolCalls = toolCallBuilder.isNotEmpty()

            if (hasToolCalls) {
                // ── Tool call path ───────────────────────────────
                val toolCalls = toolCallBuilder.toList()

                messages.add(
                    LlmMessage(
                        role = MessageRole.Assistant,
                        content = contentBuilder.toString().takeIf { it.isNotBlank() },
                        toolCalls = toolCalls,
                    )
                )

                // Execute tools
                for (tc in toolCalls) {
                    val toolStart = System.currentTimeMillis()
                    try {
                        val result = toolExecutor.execute(tc)
                        val toolDuration = System.currentTimeMillis() - toolStart
                        tracer.onToolExecuted(tc.name, result, false, toolDuration, iteration)

                        emit(StreamEvent.ToolResult(tc, result))

                        messages.add(
                            LlmMessage(
                                role = MessageRole.Tool,
                                content = result,
                                toolCallId = tc.id,
                                name = tc.name,
                            )
                        )
                    } catch (e: Exception) {
                        val toolDuration = System.currentTimeMillis() - toolStart
                        val errorResult = "Error: ${e.message}"
                        tracer.onToolExecuted(tc.name, errorResult, true, toolDuration, iteration)

                        emit(StreamEvent.ToolResult(tc, errorResult, isError = true))

                        messages.add(
                            LlmMessage(
                                role = MessageRole.Tool,
                                content = errorResult,
                                toolCallId = tc.id,
                                name = tc.name,
                            )
                        )
                    }
                }

                // Continue loop — re-prompt
            } else {
                // ── Content completion — we're done ───────────────
                val content = contentBuilder.toString().takeIf { it.isNotBlank() }
                messages.add(
                    LlmMessage(
                        role = MessageRole.Assistant,
                        content = content,
                    )
                )

                emit(StreamEvent.Done)
                tracer.onLoopComplete(
                    iteration + 1, totalTokens,
                    System.currentTimeMillis() - startTime, "stop",
                )
                return@flow
            }
        }

        // Max iterations
        emit(
            StreamEvent.Error(
                "Reached maximum tool iterations ($maxToolIterations)"
            )
        )
        tracer.onLoopComplete(
            maxToolIterations, totalTokens,
            System.currentTimeMillis() - startTime, "max_iterations",
        )
    }

    // ── Internal ────────────────────────────────────────────────────

    /**
     * Resolve a [LlmProvider] for the given [ProviderType].
     *
     * @throws IllegalArgumentException if no provider is registered for the type.
     */
    private fun resolveProvider(type: ProviderType): LlmProvider {
        return providers[type]
            ?: throw IllegalArgumentException("No provider registered for type: $type")
    }
}
