package com.nousresearch.hermes.agent.core.tools.platform

import com.nousresearch.hermes.agent.core.tools.ToolRegistry

/**
 * PlatformTools — Factory that creates and registers all Android platform tools.
 *
 * Call [registerAll] to register every platform tool into a [ToolRegistry].
 * Tools can also be registered individually if selective registration is needed.
 *
 * Usage:
 * ```kotlin
 * val registry = ToolRegistry()
 * PlatformTools.registerAll(registry)
 * // registry now has all 15+ platform tools
 * ```
 *
 * Current tools (18 total):
 * 1. sms         — SMS read/search/send via Telephony ContentProvider
 * 2. contacts    — Contact read/search/create via ContactsContract
 * 3. email       — Email send/search via Intent + ContentProvider
 * 4. calendar    — Calendar read/search/create via CalendarContract
 * 5. location    — GPS location + reverse geocode via FusedLocationProvider
 * 6. file        — File list/read/write/search via MediaStore + File API
 * 7. clipboard   — Clipboard read/write via ClipboardManager
 * 8. phone       — Call log, dialer, device info via TelephonyManager
 * 9. notification — Send/list notifications via NotificationManager
 * 10. camera     — Image capture via Camera Intent
 * 11. audio      — Record audio, TTS via MediaRecorder + TextToSpeech
 * 12. network    — WiFi/cellular/connectivity via ConnectivityManager
 * 13. sensor     — Hardware sensor readings via SensorManager
 * 14. web_search — Web search + page fetch via HTTP
 * 15. shell      — Shell command execution via Runtime.exec
 *
 * Privacy: No telemetry. No data collection beyond explicit user requests.
 * All tools are designed for on-device operation with offline-first architecture.
 */
object PlatformTools {

    /**
     * Create all platform tools and register them into the given registry.
     *
     * @param registry The tool registry to populate
     * @return The same registry (for chaining)
     */
    fun registerAll(registry: ToolRegistry): ToolRegistry {
        registry.registerAll(tools)
        return registry
    }

    /**
     * Create all platform tool instances.
     * Tools are created as singletons for efficiency.
     */
    val tools: List<com.nousresearch.hermes.agent.core.tools.HermesTool> by lazy {
        listOf(
            // Communication tools
            SmsTool(),
            ContactsTool(),
            EmailTool(),

            // Time & location tools
            CalendarTool(),
            LocationTool(),

            // File & data tools
            FileTool(),
            ClipboardTool(),

            // Device tools
            PhoneTool(),
            NotificationTool(),

            // Media tools
            CameraTool(),
            AudioTool(),

            // Connectivity tools
            NetworkTool(),
            WebSearchTool(),

            // Hardware tools
            SensorTool(),

            // System tools
            ShellTool(),
        )
    }

    /**
     * Get the names of all platform tools.
     */
    val toolNames: List<String> get() = tools.map { it.name }

    /**
     * Get the number of platform tools.
     */
    val toolCount: Int get() = tools.size
}
