package com.nousresearch.hermes.agent.core.agent

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import com.nousresearch.hermes.agent.core.AgentConfig
import com.nousresearch.hermes.agent.core.LlmToolCall
import com.nousresearch.hermes.agent.core.ProviderType
import com.nousresearch.hermes.agent.core.StreamEvent
import com.nousresearch.hermes.agent.core.llm.LlmBroker
import com.nousresearch.hermes.agent.core.memory.MemoryStore
import com.nousresearch.hermes.agent.core.prompt.PromptBuilder
import com.nousresearch.hermes.agent.core.session.SessionEntity
import com.nousresearch.hermes.agent.core.session.SessionStore
import com.nousresearch.hermes.agent.core.tools.ToolContext
import com.nousresearch.hermes.agent.core.tools.ToolExecutor
import com.nousresearch.hermes.agent.core.tools.ToolRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

// ── Orchestrator State ────────────────────────────────────────────────

/**
 * Snapshot of the orchestrator's state for lifecycle observation.
 */
data class OrchestratorState(
    val activeSessionId: String? = null,
    val isRunning: Boolean = false,
    val modelConfig: String? = null,
    val totalSessionsCreated: Int = 0,
)

// ── AgentOrchestrator ─────────────────────────────────────────────────

/**
 * Top-level orchestrator for the Hermes Agent conversation system.
 *
 * Wires together all existing subsystems:
 * - [LlmBroker] — LLM provider with built-in ReAct loop
 * - [ToolRegistry] — Tool registration and schema generation
 * - [ToolExecutor] — Tool execution with permission checks, timeouts, retries
 * - [SessionStore] — Session and message persistence (Room + FTS5)
 * - [PromptBuilder] — Context-aware prompt construction
 * - [MemoryStore] — On-device vector memory for cross-session recall
 * - [ConversationLoop] — Turn lifecycle with persistence
 *
 * ## Lifecycle
 *
 * Implements [ComponentCallbacks2] to respond to Android memory pressure:
 * - ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN: persist state snapshot
 * - ComponentCallbacks2.TRIM_MEMORY_COMPLETE: cancel active conversations and persist
 *
 * ## Architecture
 *
 * ```
 * ┌──────────────── AgentOrchestrator ─────────────────────┐
 * │  ┌──────────────┐  ┌─────────────┐  ┌──────────────┐  │
 * │  │ LlmBroker     │  │ Conversation│  │ SessionStore  │  │
 * │  │ (ReAct loop)  │  │ Loop (turn) │  │ (Room DB)     │  │
 * │  ├──────────────┤  ├─────────────┤  ├──────────────┤  │
 * │  │ ToolExecutor  │  │ PromptBuild │  │ MemoryStore   │  │
 * │  │ ToolRegistry  │  │ (context)   │  │ (vectors)     │  │
 * │  └──────────────┘  └─────────────┘  └──────────────┘  │
 * └────────────────────────────────────────────────────────┘
 * ```
 */
class AgentOrchestrator(
    private val application: Application,
    private val llmBroker: LlmBroker,
    private val toolRegistry: ToolRegistry,
    private val toolExecutor: ToolExecutor,
    private val config: AgentConfig = AgentConfig(),
) : ComponentCallbacks2 {

    // ── Subsystems ─────────────────────────────────────────────────────

    /** Room-backed persistent session store. */
    val sessionStore: SessionStore = SessionStore(application)

    /** Context-aware prompt builder with compression strategies. */
    val promptBuilder: PromptBuilder = PromptBuilder(config)

    /** On-device vector memory store (simple n-gram embeddings). */
    val memoryStore: MemoryStore = MemoryStore(
        context = application,
        ollamaBaseUrl = null, // uses simple embedding by default
    )

    // ── Internal state ─────────────────────────────────────────────────

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Map of session ID → active conversation job. */
    private val activeJobs = mutableMapOf<String, Job>()

    /** Current orchestrator state. */
    private var state = OrchestratorState()

    /** Observable state flow for UI layer. */
    private val _stateFlow = MutableStateFlow(state)
    val stateFlow: StateFlow<OrchestratorState> = _stateFlow.asStateFlow()

    // ── Initialisation / Shutdown ──────────────────────────────────────

    /**
     * Initialise the orchestrator. Call once from [Application.onCreate].
     *
     * Loads persisted memory and registers lifecycle callbacks.
     */
    suspend fun init(): Unit = withContext(Dispatchers.Default) {
        application.registerComponentCallbacks(this@AgentOrchestrator)
        memoryStore.load()
        sessionStore.archiveOldSessions(90)
    }

    /**
     * Clean shutdown. Call from [Application.onTerminate].
     */
    suspend fun shutdown(): Unit = withContext(Dispatchers.Default) {
        application.unregisterComponentCallbacks(this@AgentOrchestrator)
        cancelAllConversations()
        scope.cancel()
    }

    // ── Session Management ─────────────────────────────────────────────

    /**
     * Start a new conversation session.
     *
     * Creates a Room-backed [SessionEntity] with optional title and model config.
     *
     * @return The new session's UUID string.
     */
    suspend fun startNewSession(
        title: String? = null,
        modelConfig: String? = null,
    ): String = withContext(Dispatchers.Default) {
        val resolvedModelConfig = modelConfig ?: llmBroker.getCurrentConfig().let {
            "${it.type.name.lowercase()}/${it.model}"
        }

        val session = sessionStore.createSession(
            title = title ?: "Session ${System.currentTimeMillis() % 10000}",
            modelConfig = resolvedModelConfig,
        )

        state = state.copy(
            activeSessionId = session.id,
            totalSessionsCreated = state.totalSessionsCreated + 1,
        )
        _stateFlow.value = state

        session.id
    }

    /**
     * Send a user message in an existing session and stream the response.
     *
     * Delegates to [ConversationLoop.runConversation] which wraps the
     * existing [LlmBroker.stream] with persistence and context management.
     *
     * Only one conversation can run per session at a time. Starting a new
     * one cancels the previous.
     *
     * @param sessionId The target session ID.
     * @param userMessage The user's text input.
     * @return Flow of [StreamEvent] for real-time consumption.
     */
    fun sendMessage(
        sessionId: String,
        userMessage: String,
    ): Flow<StreamEvent> = channelFlow {
        // Cancel any existing job for this session
        activeJobs[sessionId]?.cancel()

        state = state.copy(isRunning = true, activeSessionId = sessionId)
        _stateFlow.value = state

        val conversationLoop = ConversationLoop(
            llmBroker = llmBroker,
            sessionStore = sessionStore,
            promptBuilder = promptBuilder,
        )

        val job = scope.launch(Dispatchers.Default) {
            try {
                conversationLoop
                    .runConversation(sessionId, userMessage)
                    .collect { event ->
                        send(event)
                    }
            } catch (e: CancellationException) {
                send(StreamEvent.TextChunk("\n\n[Conversation cancelled]"))
                send(StreamEvent.Done)
            } catch (e: Exception) {
                send(StreamEvent.Error("Conversation error: ${e.message}"))
                send(StreamEvent.Done)
            }
        }

        activeJobs[sessionId] = job

        // Cleanup when the channel closes
        awaitClose {
            job.cancel()
            state = state.copy(isRunning = false)
            _stateFlow.value = state
            activeJobs.remove(sessionId)
        }
    }

    /**
     * Cancel an active conversation for a session.
     */
    fun cancelConversation(sessionId: String) {
        activeJobs[sessionId]?.cancel()
        activeJobs.remove(sessionId)
        state = state.copy(isRunning = false)
        _stateFlow.value = state
    }

    /**
     * Cancel all active conversations.
     */
    fun cancelAllConversations() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        state = state.copy(isRunning = false)
        _stateFlow.value = state
    }

    // ── Query ──────────────────────────────────────────────────────────

    /**
     * Observe all sessions, ordered by most-recently-updated first.
     */
    fun listSessions() = sessionStore.listSessions()

    /**
     * Get a specific session by ID.
     */
    suspend fun getSession(sessionId: String): SessionEntity? =
        sessionStore.getSession(sessionId)

    /**
     * Delete a session and all its messages.
     */
    suspend fun deleteSession(sessionId: String) {
        cancelConversation(sessionId)
        sessionStore.deleteSession(sessionId)
    }

    // ── Tool Management ────────────────────────────────────────────────

    /**
     * Execute a tool synchronously outside the conversation loop.
     * Useful for one-shot tool calls from the UI layer.
     */
    suspend fun executeTool(
        name: String,
        arguments: String,
    ): com.nousresearch.hermes.agent.core.ToolResult {
        val toolContext = ToolContext(androidContext = application)
        return toolExecutor.execute(
            call = LlmToolCall(id = "", name = name, arguments = arguments),
            context = toolContext,
        )
    }

    /**
     * Get all registered tool names.
     */
    fun getToolNames(): Set<String> = toolRegistry.getToolNames()

    /**
     * Get the number of registered tools.
     */
    val toolCount: Int get() = toolRegistry.size

    // ── Provider Management ────────────────────────────────────────────

    /**
     * Get the currently active model name.
     */
    fun getCurrentModel(): String = llmBroker.getCurrentConfig().model

    /**
     * Get the currently active provider type.
     */
    fun getCurrentProvider(): ProviderType = llmBroker.getCurrentConfig().type

    // ── Memory ─────────────────────────────────────────────────────────

    /**
     * Store an entry in long-term memory.
     */
    suspend fun remember(
        key: String,
        text: String,
        metadata: Map<String, String> = emptyMap(),
    ) {
        memoryStore.store(key, text, metadata)
    }

    /**
     * Search long-term memory by semantic similarity.
     */
    suspend fun recall(query: String, limit: Int = 5) =
        memoryStore.search(query, limit)

    /**
     * Forget a specific memory entry.
     */
    suspend fun forget(key: String) = memoryStore.forget(key)

    /**
     * Clear all memory.
     */
    suspend fun clearMemory() = memoryStore.clear()

    // ── Lifecycle (ComponentCallbacks2) ────────────────────────────────

    override fun onTrimMemory(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                cancelAllConversations()
                scope.launch { persistState() }
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                scope.launch { persistState() }
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                scope.launch { persistState() }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // No-op: state is in-memory
    }

    override fun onLowMemory() {
        cancelAllConversations()
        scope.launch { persistState() }
    }

    // ── State Persistence ──────────────────────────────────────────────

    private suspend fun persistState() {
        _stateFlow.value = state
    }

    /**
     * Restore orchestrator state from a previously-persisted snapshot.
     */
    suspend fun restoreState(savedState: OrchestratorState) {
        state = savedState
        _stateFlow.value = state
    }
}
