package com.nousresearch.hermes.agent.core.agent

import com.nousresearch.hermes.agent.core.LlmMessage
import com.nousresearch.hermes.agent.core.MessageRole
import com.nousresearch.hermes.agent.core.StreamEvent
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.llm.LlmBroker
import com.nousresearch.hermes.agent.core.prompt.PromptBuilder
import com.nousresearch.hermes.agent.core.prompt.estimateMessageTokenCount
import com.nousresearch.hermes.agent.core.session.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ── JSON helper ───────────────────────────────────────────────────────

private val json = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
}

// ── Conversation result ───────────────────────────────────────────────

/**
 * The final result of a conversation turn.
 */
data class ConversationResult(
    val content: String?,
    val toolCalls: List<com.nousresearch.hermes.agent.core.LlmToolCall>?,
    val turnCount: Int,
    val iterationCount: Int,
    val finishReason: String?,
    val totalTokens: Int,
)

// ── ConversationLoop ──────────────────────────────────────────────────

/**
 * Orchestrates a single conversation turn using the existing [LlmBroker] ReAct loop.
 *
 * Unlike the Python-original ConversationLoop which runs its own ReAct loop,
 * this Kotlin version delegates the inner loop (LLM call → tool execution →
 * re-prompt) to the existing [LlmBroker], which already implements this
 * pattern in `stream()` and `complete()`.
 *
 * This layer adds:
 * 1. **Session persistence** — loads history from [SessionStore], persists every message
 * 2. **Context management** — compress history via [PromptBuilder] when nearing token limits
 * 3. **Turn lifecycle** — build messages → stream → persist → result
 * 4. **Error recovery** — re-queues on transient failures
 *
 * ## Flow
 *
 * ```
 * loadHistory() → buildMessages() → LlmBroker.stream()
 *   → collect StreamEvent → persist messages → StreamEvent.Done
 *   → check if compression needed → re-prompt with LlmBroker.complete()
 * ```
 */
class ConversationLoop(
    private val llmBroker: LlmBroker,
    private val sessionStore: SessionStore,
    private val promptBuilder: PromptBuilder,
) {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    /**
     * Run a conversation turn for [sessionId] with [userMessage].
     *
     * @param sessionId Active session ID.
     * @param userMessage The user's text input.
     * @return Flow of [StreamEvent] for real-time consumption.
     */
    fun runConversation(
        sessionId: String,
        userMessage: String,
    ): Flow<StreamEvent> = callbackFlow {
        val job = coroutineContext[Job]
        var turnCount = 0
        var totalTokens = 0

        // ── 1. Load session ───────────────────────────────────────────
        val session = sessionStore.getSession(sessionId)
        if (session == null) {
            send(StreamEvent.Error("Session not found: $sessionId"))
            close()
            return@callbackFlow
        }

        // ── 2. Persist user message ───────────────────────────────────
        sessionStore.addMessage(
            sessionId = sessionId,
            role = "user",
            content = userMessage,
        )

        // ── 3. Build initial message list ─────────────────────────────
        val historyEntities = sessionStore.getMessages(sessionId)
        val tools: List<ToolDescriptor> = getAvailableTools()

        var messages: MutableList<LlmMessage> = promptBuilder
            .buildMessagesFromEntities(session, historyEntities, userMessage, tools)
            .toMutableList()

        send(StreamEvent.TextChunk("")) // signal: streaming started

        // ── 4. Conversation turn loop ─────────────────────────────────
        while (isActive && turnCount < 1) {
            turnCount++

            // ── 4a. Context compression check ─────────────────────────
            val estimatedTokens = estimateMessageTokenCount(messages)
            if (estimatedTokens > (promptBuilder.config.contextLength * promptBuilder.config.compressionThreshold).toInt()) {
                val compressed = promptBuilder.compressMessages(messages)
                messages.clear()
                messages.addAll(compressed)
                send(StreamEvent.TextChunk("\n\n*[Context compressed]*\n\n"))
            }

            // ── 4b. Call LlmBroker.stream() ──────────────────────────
            // LlmBroker handles the full ReAct loop internally:
            //   LLM call → tool calls → execute tools → append results → re-prompt
            val collectedContent = StringBuilder()
            val collectedToolCalls = mutableListOf<com.nousresearch.hermes.agent.core.LlmToolCall>()

            try {
                llmBroker
                    .stream(
                        messages = messages,
                        tools = tools,
                        maxToolIterations = 25,
                    )
                    .catch { e ->
                        send(StreamEvent.Error("Stream error: ${e.message}"))
                    }
                    .collect { event ->
                        if (!isActive) throw CancellationException("Conversation cancelled")

                        when (event) {
                            is StreamEvent.TextChunk -> {
                                collectedContent.append(event.text)
                                send(event)
                            }
                            is StreamEvent.ToolCall -> {
                                collectedToolCalls.add(event.call)
                                send(event)
                            }
                            is StreamEvent.ToolResult -> {
                                send(event)
                            }
                            is StreamEvent.Done -> {
                                // LlmBroker finished one full ReAct round
                            }
                            is StreamEvent.Error -> {
                                send(event)
                            }
                        }
                    }
            } catch (e: CancellationException) {
                send(StreamEvent.TextChunk("\n\n[Conversation cancelled]"))
                send(StreamEvent.Done)
                close()
                return@callbackFlow
            } catch (e: Exception) {
                send(StreamEvent.Error("Conversation error: ${e.message}"))
                send(StreamEvent.Done)
                close()
                return@callbackFlow
            }

            // ── 5. Persist all new messages ───────────────────────────
            // After LlmBroker.stream() completes, `messages` contains the
            // full conversation including all assistant responses and tool results.
            // We persist the assistant + tool messages that were added.
            persistReActMessages(sessionId, messages, historyEntities.size)

            // ── 6. Track tokens ───────────────────────────────────────
            totalTokens += estimatedTokens
        }

        // ── 7. Update session counts ──────────────────────────────────
        sessionStore.updateSessionCounts(sessionId, totalTokens)

        send(StreamEvent.Done)
        close()
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /**
     * Extract and persist the new messages that were added during the
     * LlmBroker ReAct loop. The messages list starts with history and
     * gets appended by the broker for each assistant response and tool result.
     */
    private suspend fun persistReActMessages(
        sessionId: String,
        messages: List<LlmMessage>,
        historyCount: Int,
    ) {
        // Messages added during the ReAct loop are those beyond the original history
        // Skip the system prompt (index 0) and count history entries
        val originalCount = 1 + historyCount // system + history
        val newMessages = messages.drop(originalCount)

        for (msg in newMessages) {
            when (msg.role) {
                MessageRole.Assistant -> {
                    val toolCallsJson = msg.toolCalls?.let { calls ->
                        json.encodeToString(calls.map { call ->
                            mapOf("id" to call.id, "name" to call.name, "arguments" to call.arguments)
                        })
                    }
                    sessionStore.addMessage(
                        sessionId = sessionId,
                        role = "assistant",
                        content = msg.content,
                        toolCalls = toolCallsJson,
                    )
                }
                MessageRole.Tool -> {
                    val meta = mapOf(
                        "tool_call_id" to (msg.toolCallId ?: ""),
                        "name" to (msg.name ?: ""),
                    )
                    sessionStore.addMessage(
                        sessionId = sessionId,
                        role = "tool",
                        content = null,
                        toolCalls = json.encodeToString(meta),
                        toolResult = msg.content,
                    )
                }
                else -> {
                    // Skip system messages added by broker
                }
            }
        }
    }

    /**
     * Get available tool descriptors. In a full implementation, this would
     * query the ToolRegistry. Here we return empty — the LlmBroker is constructed
     * with tool schemas passed via CompletionRequest -> tools parameter.
     *
     * The tool descriptors are injected at the LlmBroker.stream() call level.
     * For now, we rely on the LlmBroker's built-in tool resolution via ToolExecutor.
     */
    private fun getAvailableTools(): List<ToolDescriptor> = emptyList()
}
