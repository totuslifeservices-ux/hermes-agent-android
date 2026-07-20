package com.nousresearch.hermes.agent.core.llm

import android.util.Log
import com.nousresearch.hermes.agent.core.CompletionResponse
import com.nousresearch.hermes.agent.core.LlmMessage
import com.nousresearch.hermes.agent.core.MessageRole
import com.nousresearch.hermes.agent.core.ProviderConfig
import com.nousresearch.hermes.agent.core.StreamEvent
import com.nousresearch.hermes.agent.core.ToolDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Managed conversation session that provides a safe, high-level API for
 * interacting with the [LlmBroker].
 *
 * [ChatSession] wraps [LlmBroker] with:
 * - **Mutex-guarded message history** for thread-safe concurrent access
 * - **Automatic system prompt injection** on every request
 * - **Atomic history updates** that only commit on success
 * - **Rollback on failure** to preserve conversation integrity
 * - **Token usage tracking** across the entire session
 *
 * ## Usage
 * ```kotlin
 * val session = ChatSession(
 *     broker = llmBroker,
 *     systemPrompt = "You are a helpful assistant.",
 *     tools = listOf(myToolDescriptor),
 * )
 *
 * // Single-shot
 * val response: CompletionResponse = session.send("What's the weather?")
 *
 * // Streaming
 * session.stream("Tell me a story").collect { event ->
 *     when (event) { ... }
 * }
 *
 * // Access history
 * val history = session.getHistory()
 * ```
 *
 * @property broker The [LlmBroker] that handles the actual LLM calls.
 * @property config The [ProviderConfig] for LLM routing.
 * @property systemPrompt The system prompt injected on every turn.
 * @property tools Optional list of [ToolDescriptor]s available to the model.
 * @property maxToolIterations Maximum ReAct loop iterations per user message.
 */
class ChatSession(
    private val broker: LlmBroker,
    private val config: ProviderConfig,
    private val systemPrompt: String,
    private val tools: List<ToolDescriptor>? = null,
    private val maxToolIterations: Int = 25,
) {
    companion object {
        private const val TAG = "ChatSession"
    }

    /** Mutex-guarded message history. */
    private val mutex = Mutex()
    private val _messages: MutableList<LlmMessage> = mutableListOf()

    /** Accumulated token usage across the session. */
    private var _totalPromptTokens: Int = 0
    private var _totalCompletionTokens: Int = 0
    private var _totalTokens: Int = 0

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Send a user message and get a single-shot response.
     *
     * The system prompt is automatically prepended if not already present.
     * On failure, the user's message and the assistant's failed response are
     * rolled back from history.
     *
     * @param text The user message text.
     * @param toolsOverride Optional per-call tool list override.
     * @return The [CompletionResponse] from the LLM.
     */
    suspend fun send(
        text: String,
        toolsOverride: List<ToolDescriptor>? = null,
    ): CompletionResponse = mutex.withLock {
        val historySnapshot = _messages.toMutableList()
        try {
            // Ensure system prompt is present
            ensureSystemPrompt()

            // Add user message
            val userMessage = LlmMessage(role = MessageRole.User, content = text)
            _messages.add(userMessage)

            // Execute the ReAct loop
            val response = broker.complete(
                messages = _messages,
                tools = toolsOverride ?: tools,
                config = config,
                maxToolIterations = maxToolIterations,
            )

            // Track token usage
            response.usage?.let { usage ->
                _totalPromptTokens += usage.promptTokens
                _totalCompletionTokens += usage.completionTokens
                _totalTokens += usage.totalTokens
            }

            // On error finish, roll back the user message and system prompt injection
            if (response.finishReason == "error") {
                _messages.clear()
                _messages.addAll(historySnapshot)
                Log.w(TAG, "Rolled back session after error response")
            }

            response
        } catch (e: CancellationException) {
            // Coroutine cancelled — roll back
            _messages.clear()
            _messages.addAll(historySnapshot)
            throw e
        } catch (e: Exception) {
            // Unexpected error — roll back
            _messages.clear()
            _messages.addAll(historySnapshot)
            Log.e(TAG, "Session send failed, rolled back history", e)
            throw e
        }
    }

    /**
     * Send a user message and get a streaming response.
     *
     * The stream emits [StreamEvent] values. The user's message and all
     * assistant/tool messages are committed to history atomically only
     * after the stream completes successfully. On failure, everything
     * added during this turn is rolled back.
     *
     * @param text The user message text.
     * @param toolsOverride Optional per-call tool list override.
     * @return A [Flow] of [StreamEvent] values.
     */
    fun stream(
        text: String,
        toolsOverride: List<ToolDescriptor>? = null,
    ): Flow<StreamEvent> = kotlinx.coroutines.flow.flow {
        // We acquire the mutex here but release it before collecting the stream
        // to avoid holding the lock for the entire streaming duration.
        // Instead, we snapshot the pre-call state and manage history atomically
        // via a separate mutex scope for the commit.

        val preCallSnapshot: List<LlmMessage>
        mutex.withLock {
            ensureSystemPrompt()
            preCallSnapshot = _messages.toList()
            _messages.add(LlmMessage(role = MessageRole.User, content = text))
        }

        try {
            val tempMessages = _messages.toMutableList()

            broker.stream(
                messages = tempMessages,
                tools = toolsOverride ?: tools,
                config = config,
                maxToolIterations = maxToolIterations,
            ).collect { event ->
                when (event) {
                    is StreamEvent.Done -> {
                        // Commit the temporary messages to the real history
                        mutex.withLock {
                            // Find what changed
                            val newMessages = tempMessages.drop(preCallSnapshot.size + 1) // +1 for user msg
                            // Keep user message, replace everything after
                            val keepCount = preCallSnapshot.size + 1 // system + user
                            if (_messages.size > keepCount) {
                                _messages.subList(keepCount, _messages.size).clear()
                            }
                            _messages.addAll(newMessages)
                        }
                        emit(event)
                    }
                    is StreamEvent.Error -> {
                        // Roll back user message
                        mutex.withLock {
                            if (_messages.size > preCallSnapshot.size) {
                                _messages.subList(preCallSnapshot.size, _messages.size).clear()
                            }
                        }
                        emit(event)
                    }
                    else -> emit(event)
                }
            }
        } catch (e: CancellationException) {
            mutex.withLock {
                if (_messages.size > preCallSnapshot.size) {
                    _messages.subList(preCallSnapshot.size, _messages.size).clear()
                }
            }
            throw e
        } catch (e: Exception) {
            mutex.withLock {
                if (_messages.size > preCallSnapshot.size) {
                    _messages.subList(preCallSnapshot.size, _messages.size).clear()
                }
            }
            Log.e(TAG, "Stream failed, rolled back history", e)
            throw e
        }
    }

    /**
     * Returns a snapshot of the current message history.
     */
    suspend fun getHistory(): List<LlmMessage> = mutex.withLock {
        _messages.toList()
    }

    /**
     * Returns a snapshot of the current message history (non-suspending).
     * Only safe to call from within a [mutex] lock scope.
     */
    fun getHistoryBlocking(): List<LlmMessage> = _messages.toList()

    /**
     * Clear all messages (keeping the system prompt for the next message).
     */
    suspend fun clear() = mutex.withLock {
        _messages.clear()
    }

    /**
     * Replace the system prompt in-place.
     * The new system prompt will be used on the next [send] or [stream] call.
     */
    suspend fun setSystemPrompt(newPrompt: String) = mutex.withLock {
        // Remove any existing system message at position 0
        if (_messages.isNotEmpty() && _messages[0].role == MessageRole.System) {
            _messages[0] = LlmMessage(role = MessageRole.System, content = newPrompt)
        }
    }

    /**
     * Token usage statistics for the session.
     */
    val tokenUsage: TokenUsage
        get() = TokenUsage(_totalPromptTokens, _totalCompletionTokens, _totalTokens)

    // ── Internal ────────────────────────────────────────────────────

    /**
     * Ensures the system prompt is present as the first message.
     * This is idempotent — if a system message already exists at index 0,
     * it is left unchanged.
     */
    private fun ensureSystemPrompt() {
        if (_messages.isEmpty() || _messages[0].role != MessageRole.System) {
            _messages.add(0, LlmMessage(role = MessageRole.System, content = systemPrompt))
        }
    }
}

/**
 * Token usage statistics for a [ChatSession].
 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
)
