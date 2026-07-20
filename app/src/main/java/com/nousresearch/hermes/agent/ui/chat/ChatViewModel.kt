package com.nousresearch.hermes.agent.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nousresearch.hermes.agent.core.MessageRole
import com.nousresearch.hermes.agent.core.StreamEvent
import com.nousresearch.hermes.agent.core.agent.AgentOrchestrator
import com.nousresearch.hermes.agent.core.session.MessageEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ChatViewModel — Manages chat state, streaming, and session lifecycle.
 *
 * Holds messages, streaming state, and current session ID.
 * Connects to AgentOrchestrator for LLM interactions.
 */
class ChatViewModel(
    private val orchestrator: AgentOrchestrator,
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    // ── State ───────────────────────────────────────────────────────

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _streamingContent = MutableStateFlow<String?>(null)
    val streamingContent: StateFlow<String?> = _streamingContent.asStateFlow()

    private val _currentToolCalls = MutableStateFlow<List<ToolCallState>>(emptyList())
    val currentToolCalls: StateFlow<List<ToolCallState>> = _currentToolCalls.asStateFlow()

    private var currentStreamJob: Job? = null

    // ── Initialization ──────────────────────────────────────────────

    init {
        // Observe orchestrator state for session ID and streaming status
        viewModelScope.launch {
            orchestrator.stateFlow.collect { state ->
                _sessionId.value = state.activeSessionId
                _isStreaming.value = state.isRunning
            }
        }
    }

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Send a text message and begin streaming a response.
     */
    fun sendMessage(text: String) {
        if (text.isBlank() || _isStreaming.value) return

        _error.value = null
        val currentId = _sessionId.value

        if (currentId == null) {
            // Need to start a new session first
            viewModelScope.launch {
                try {
                    val newId = orchestrator.startNewSession()
                    _sessionId.value = newId
                    _messages.value = emptyList()
                    _streamingContent.value = null
                    _currentToolCalls.value = emptyList()
                    beginStreaming(newId, text)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create session", e)
                    _error.value = e.message ?: "Failed to create session"
                }
            }
        } else {
            beginStreaming(currentId, text)
        }
    }

    /**
     * Load an existing session by ID.
     */
    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            try {
                _sessionId.value = sessionId
                val entities = orchestrator.sessionStore.getMessages(sessionId)
                _messages.value = entities.map { it.toChatMessage() }
                _streamingContent.value = null
                _currentToolCalls.value = emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load session", e)
                _error.value = "Failed to load session"
            }
        }
    }

    /**
     * Create a new session.
     */
    fun startNewSession() {
        viewModelScope.launch {
            try {
                val id = orchestrator.startNewSession()
                _sessionId.value = id
                _messages.value = emptyList()
                _streamingContent.value = null
                _currentToolCalls.value = emptyList()
                _error.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create session", e)
                _error.value = "Failed to create session"
            }
        }
    }

    /**
     * Regenerate the last assistant response.
     */
    fun regenerateLastResponse() {
        val currentId = _sessionId.value ?: return
        if (_isStreaming.value) return

        // Remove last assistant message(s) from UI
        val currentMessages = _messages.value
        val lastAssistantIndex = currentMessages.indexOfLast { it.role == MessageRole.Assistant }
        if (lastAssistantIndex >= 0) {
            _messages.value = currentMessages.subList(0, lastAssistantIndex)
        }

        beginStreaming(currentId, "(regenerate)")
    }

    /**
     * Cancel the current streaming response.
     */
    fun cancelStream() {
        val currentId = _sessionId.value
        if (currentId != null) {
            orchestrator.cancelConversation(currentId)
        }
        currentStreamJob?.cancel()
        _isStreaming.value = false
        _streamingContent.value = null
        _currentToolCalls.value = emptyList()
    }

    /**
     * Delete a message from the current session.
     */
    fun deleteMessage(messageId: String) {
        _messages.value = _messages.value.filter { it.id != messageId }
    }

    /**
     * Clear any displayed error.
     */
    fun clearError() {
        _error.value = null
    }

    // ── Private ─────────────────────────────────────────────────────

    private fun beginStreaming(sessionId: String, text: String) {
        // Add user message immediately
        val userMessage = ChatMessage(
            id = java.util.UUID.randomUUID().toString(),
            role = MessageRole.User,
            content = text,
            timestamp = System.currentTimeMillis(),
        )
        _messages.value = _messages.value + userMessage
        _isStreaming.value = true
        _streamingContent.value = null
        _currentToolCalls.value = emptyList()

        // Collect stream events from the orchestrator
        currentStreamJob = viewModelScope.launch {
            try {
                orchestrator.sendMessage(sessionId, text).collect { event ->
                    handleStreamEvent(event)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Stream error", e)
                    _error.value = e.message ?: "Stream error"
                }
            }
        }
    }

    private fun handleStreamEvent(event: StreamEvent) {
        when (event) {
            is StreamEvent.TextChunk -> {
                _streamingContent.value = event.text
            }

            is StreamEvent.ToolCall -> {
                val toolCall = ToolCallState(
                    id = event.call.id,
                    toolName = event.call.name,
                    arguments = event.call.arguments,
                    isRunning = true,
                )
                _currentToolCalls.value = _currentToolCalls.value + toolCall
            }

            is StreamEvent.ToolResult -> {
                val updatedCalls = _currentToolCalls.value.map { call ->
                    if (call.id == event.call.id) {
                        call.copy(
                            result = event.result,
                            isError = event.isError,
                            isRunning = false,
                        )
                    } else {
                        call
                    }
                }
                _currentToolCalls.value = updatedCalls
            }

            is StreamEvent.Done -> {
                val content = _streamingContent.value
                if (content != null) {
                    val assistantMessage = ChatMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        role = MessageRole.Assistant,
                        content = content,
                        toolCallsSummary = if (_currentToolCalls.value.isNotEmpty()) {
                            _currentToolCalls.value.joinToString("\n") { tc ->
                                tc.toolName + if (tc.result != null) " \u2713" else ""
                            }
                        } else null,
                        timestamp = System.currentTimeMillis(),
                    )
                    _messages.value = _messages.value + assistantMessage
                }

                // Reload messages from store to ensure consistency
                val sid = _sessionId.value
                if (sid != null) {
                    viewModelScope.launch {
                        try {
                            val entities = orchestrator.sessionStore.getMessages(sid)
                            _messages.value = entities.map { it.toChatMessage() }
                        } catch (_: Exception) {}
                    }
                }

                _streamingContent.value = null
                _currentToolCalls.value = emptyList()
            }

            is StreamEvent.Error -> {
                _error.value = event.message
                _streamingContent.value = null
                _currentToolCalls.value = emptyList()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentStreamJob?.cancel()
    }
}

/**
 * UI representation of a chat message.
 */
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String?,
    val toolCallsSummary: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * State of an active tool call in the UI.
 */
data class ToolCallState(
    val id: String,
    val toolName: String,
    val arguments: String,
    val result: String? = null,
    val isError: Boolean = false,
    val isRunning: Boolean = true,
)

/**
 * Convert a MessageEntity (Room) to a ChatMessage.
 */
private fun MessageEntity.toChatMessage() = ChatMessage(
    id = id,
    role = try { MessageRole.valueOf(role.replaceFirstChar { it.uppercase() }) }
        catch (_: Exception) { MessageRole.User },
    content = content,
    toolCallsSummary = toolCalls,
    timestamp = createdAt,
)
