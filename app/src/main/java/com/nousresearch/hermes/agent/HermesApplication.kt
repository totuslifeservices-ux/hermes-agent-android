package com.nousresearch.hermes.agent

import android.app.Application
import android.content.Context
import android.util.Log
import com.nousresearch.hermes.agent.core.AgentConfig
import com.nousresearch.hermes.agent.core.agent.AgentOrchestrator
import com.nousresearch.hermes.agent.core.llm.LlmBroker
import com.nousresearch.hermes.agent.core.tools.ToolExecutor
import com.nousresearch.hermes.agent.core.tools.ToolRegistry
import com.nousresearch.hermes.agent.model.PolicyEnforcer
import com.nousresearch.hermes.agent.service.HermesGatewayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hermes Agent for Android — Application entry point.
 *
 * Colossians 3:23 NLT:
 * "Work willingly at whatever you do, as though you were working for the
 * Lord rather than for people."
 *
 * Initializes the offline-first Python backend environment and loads
 * the privacy policy guardrails from policy.toml before any user
 * interaction occurs.
 */
class HermesApplication : Application() {

    /** Runtime policy enforcer loaded from policy.toml */
    lateinit var policy: PolicyEnforcer
        private set

    /** Central tool registry */
    lateinit var toolRegistry: ToolRegistry
        private set

    /** Agent orchestrator — manages sessions, streaming, tool execution */
    var orchestrator: AgentOrchestrator? = null
        internal set

    /** Application-level coroutine scope */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Whether Python backend has been initialized */
    var isPythonReady: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // ── 1. Load privacy & safety policy before any other init ─────
        policy = PolicyEnloader.loadFromAssets(this)

        // ── 2. Initialize tool registry ────────────────────────────────
        toolRegistry = ToolRegistry()

        // ── 3. Initialize Python runtime (Chaquopy) ───────────────────
        if (!isPythonReady) {
            try {
                // Python.start() is called implicitly by Chaquopy when the
                // first Python module is imported.
                isPythonReady = true
            } catch (e: Exception) {
                Log.e(TAG, "Python initialization failed", e)
                isPythonReady = false
            }
        }

        // ── 4. Initialize AgentOrchestrator (deferred to allow tool registration) ──
        appScope.launch {
            initAgentOrchestrator()
        }

        // ── 5. Start gateway service for persistent agent session ─────
        if (policy.autoStartService) {
            HermesGatewayService.start(this)
        }

        Log.i(TAG, "Hermes Agent initialized (policy: ${policy.version})")
    }

    /**
     * Initialize the AgentOrchestrator with all subsystems.
     * Can be called after tools have been registered.
     */
    suspend fun initAgentOrchestrator() {
        if (orchestrator != null) return

        val executor = ToolExecutor(
            registry = toolRegistry,
            defaultTimeoutSeconds = 60L,
        )

        val llmBroker = LlmBroker(
            context = this,
            toolExecutor = com.nousresearch.hermes.agent.core.llm.ToolExecutor { toolCall ->
                executor.execute(
                    call = toolCall,
                    context = com.nousresearch.hermes.agent.core.tools.ToolContext(
                        androidContext = this,
                    ),
                ).let { result ->
                    when (result) {
                        is com.nousresearch.hermes.agent.core.ToolResult.Success -> result.content
                        is com.nousresearch.hermes.agent.core.ToolResult.Error -> "Error: ${result.message}"
                        is com.nousresearch.hermes.agent.core.ToolResult.PendingConfirmation -> "Pending user confirmation"
                    }
                }
            },
        )

        val config = AgentConfig()

        val newOrchestrator = AgentOrchestrator(
            application = this,
            llmBroker = llmBroker,
            toolRegistry = toolRegistry,
            toolExecutor = executor,
            config = config,
        )

        newOrchestrator.init()
        orchestrator = newOrchestrator
        Log.i(TAG, "AgentOrchestrator initialized")
    }

    override fun onTerminate() {
        appScope.launch {
            orchestrator?.shutdown()
        }
        HermesGatewayService.stop(this)
        super.onTerminate()
    }

    companion object {
        private const val TAG = "HermesApp"

        @Volatile
        lateinit var instance: HermesApplication
            private set
    }
}

/**
 * Loads the policy.toml from app assets.
 * The policy file defines content safety filters, privacy constraints,
 * and agent autonomy boundaries.
 */
object PolicyEnloader {
    private const val TAG = "PolicyEnloader"

    fun loadFromAssets(context: Context): PolicyEnforcer {
        return try {
            val toml = context.assets.open("policy.toml")
                .bufferedReader()
                .use { it.readText() }
            PolicyEnforcer.fromToml(toml)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load policy.toml, using defaults", e)
            PolicyEnforcer.defaults()
        }
    }
}
