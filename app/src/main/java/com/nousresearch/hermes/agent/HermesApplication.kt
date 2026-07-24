package com.nousresearch.hermes.agent

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.StrictMode
import android.util.Log
import com.nousresearch.hermes.agent.core.AgentConfig
import com.nousresearch.hermes.agent.core.ProviderConfig
import com.nousresearch.hermes.agent.core.ProviderType
import com.nousresearch.hermes.agent.core.agent.AgentOrchestrator
import com.nousresearch.hermes.agent.core.llm.LlmBroker
import com.nousresearch.hermes.agent.core.llm.NousPortalProvider
import com.nousresearch.hermes.agent.core.llm.OpenRouterProvider
import com.nousresearch.hermes.agent.core.llm.OllamaProvider
import com.nousresearch.hermes.agent.core.tools.ToolExecutor
import com.nousresearch.hermes.agent.core.tools.ToolRegistry
import com.nousresearch.hermes.agent.core.tools.platform.PlatformTools
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

    /** Whether the native orchestrator is initialized */
    var isOrchestratorReady: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // ── 0. Install crash handler ────────────────────────────────────
        installCrashHandler()

        // ── 1. Load privacy & safety policy before any other init ─────
        policy = PolicyEnloader.loadFromAssets(this)

        // ── 2. Initialize tool registry ────────────────────────────────
        toolRegistry = ToolRegistry()
        PlatformTools.registerAll(toolRegistry, this)

        // ── 3. Initialize AgentOrchestrator (deferred) ─────────────────
        appScope.launch {
            initAgentOrchestrator()
        }

        // ── 4. Start gateway service ───────────────────────────────────
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

        // Create demo providers with Nous Portal as default
        val providerConfig = ProviderConfig(type = ProviderType.NousPortal, model = "claude-sonnet-4")
        val nousProvider = NousPortalProvider(config = providerConfig, tokenProvider = { "demo-token" })
        val openRouterProvider = OpenRouterProvider(config = providerConfig.copy(type = ProviderType.OpenRouter), apiKey = "demo-key")
        val ollamaProvider = OllamaProvider(config = providerConfig.copy(type = ProviderType.Ollama))

        val providers = mapOf(
            ProviderType.NousPortal to nousProvider,
            ProviderType.OpenRouter to openRouterProvider,
            ProviderType.Ollama to ollamaProvider,
        )

        val defaultConfig = ProviderConfig(
            type = ProviderType.NousPortal,
            model = "claude-sonnet-4",
            maxTokens = 32768,
            temperature = 0.7f,
        )

        val llmBroker = LlmBroker(
            providers = providers,
            defaultConfig = defaultConfig,
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

    /**
     * Installs a default uncaught exception handler that writes crash
     * reports to the app's cache directory. Zero telemetry — crashes
     * are stored locally for debugging only.
     */
    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashFile = java.io.File(cacheDir, "crash_log.txt")
                crashFile.appendText(
                    "=== CRASH ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                        .format(java.util.Date())} ===\n" +
                    "Thread: ${thread.name}\n" +
                    "SDK: ${Build.VERSION.SDK_INT}, Release: ${Build.VERSION.RELEASE}\n" +
                    "Device: ${Build.MODEL} (${Build.MANUFACTURER})\n" +
                    "Exception: ${throwable.javaClass.name}: ${throwable.message}\n" +
                    android.util.Log.getStackTraceString(throwable) + "\n\n"
                )
                Log.e(TAG, "Crash logged to ${crashFile.absolutePath}", throwable)
            } catch (_: Exception) {
                // Best-effort logging, never crash in crash handler
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
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
