package com.nousresearch.hermes.agent.core.tools

import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult

/**
 * ToolRegistry — Central registry for all Hermes tools.
 *
 * Tools are registered by name and looked up at invocation time.
 * The registry also produces OpenAI-compatible JSON Schema tool definitions
 * for LLM function-calling requests.
 *
 * Thread-safe: all mutations use a synchronized monitor.
 */
class ToolRegistry {

    private val tools = mutableMapOf<String, HermesTool>()

    /**
     * Register a single tool.
     *
     * @param tool The tool instance to register
     * @throws IllegalArgumentException if a tool with the same name is already registered
     */
    fun register(tool: HermesTool) {
        synchronized(tools) {
            require(tool.name !in tools) {
                "Tool '${tool.name}' is already registered"
            }
            tools[tool.name] = tool
        }
    }

    /**
     * Register multiple tools at once.
     */
    fun registerAll(vararg tools: HermesTool) {
        tools.forEach { register(it) }
    }

    /**
     * Register multiple tools from a collection.
     */
    fun registerAll(tools: Collection<HermesTool>) {
        tools.forEach { register(it) }
    }

    /**
     * Look up a tool by name.
     *
     * @param name The tool name (e.g., "send_sms", "get_location")
     * @return The registered tool, or null if not found
     */
    fun get(name: String): HermesTool? {
        return synchronized(tools) { tools[name] }
    }

    /**
     * Check if a tool is registered.
     */
    fun contains(name: String): Boolean {
        return synchronized(tools) { name in tools }
    }

    /**
     * Get all registered tool names.
     */
    fun getToolNames(): Set<String> {
        return synchronized(tools) { tools.keys.toSet() }
    }

    /**
     * Get the number of registered tools.
     */
    val size: Int get() = synchronized(tools) { tools.size }

    /**
     * Unregister a tool by name.
     *
     * @return The removed tool, or null if not found
     */
    fun unregister(name: String): HermesTool? {
        return synchronized(tools) { tools.remove(name) }
    }

    /**
     * Get OpenAI-compatible tool schemas for all registered tools.
     *
     * Produces the standard `tools` array format that OpenAI, Anthropic, Ollama,
     * and other function-calling LLMs accept.
     *
     * Example output entry:
     * ```json
     * {
     *   "type": "function",
     *   "function": {
     *     "name": "send_sms",
     *     "description": "Send an SMS message to a phone number",
     *     "parameters": { ... JSON Schema ... }
     *   }
     * }
     * ```
     */
    fun getToolSchemas(): List<Map<String, Any>> {
        return synchronized(tools) {
            tools.values.map { tool ->
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to tool.descriptor.name,
                        "description" to tool.descriptor.description,
                        "parameters" to tool.descriptor.parameters,
                    )
                )
            }
        }
    }

    /**
     * Execute a tool by name with the given arguments.
     * Convenience method that combines lookup and execution.
     *
     * @param name Tool name to execute
     * @param context Tool execution context
     * @param args Parsed argument map
     * @return ToolResult, or ToolResult.Error if tool not found
     */
    suspend fun execute(name: String, context: ToolContext, args: Map<String, Any?>): ToolResult {
        val tool = get(name) ?: return ToolResult.Error(
            message = "Tool '$name' not found",
            recoverable = false,
        )
        return tool.execute(context, args)
    }

    /**
     * Clear all registered tools.
     */
    fun clear() {
        synchronized(tools) { tools.clear() }
    }
}
