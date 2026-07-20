package com.nousresearch.hermes.agent.core.prompt

import com.nousresearch.hermes.agent.core.AgentConfig
import com.nousresearch.hermes.agent.core.LlmMessage
import com.nousresearch.hermes.agent.core.MessageRole
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.session.MessageEntity
import com.nousresearch.hermes.agent.core.session.SessionEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ── Token estimation constants ────────────────────────────────────────

/**
 * Rough token-to-character ratio for various content types.
 * Used for approximate token counts when the LLM backend doesn't
 * return usage info (e.g., streaming partial responses).
 */
private const val CHARS_PER_TOKEN_ESTIMATE = 4.0f
private const val SYSTEM_PROMPT_OVERHEAD_TOKENS = 50
private const val TOOL_SCHEMA_OVERHEAD_PER_TOOL = 100

// ── JSON encoder for tool schema ─────────────────────────────────────

private val json = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
    encodeDefaults = false
}

// ── Tool schema generation ────────────────────────────────────────────

/**
 * Converts a list of [ToolDescriptor] into a JSON array of OpenAI-compatible
 * function-calling tool definitions.
 *
 * Each tool gets:
 * ```json
 * {
 *   "type": "function",
 *   "function": {
 *     "name": "...",
 *     "description": "...",
 *     "parameters": { ... }
 *   }
 * }
 * ```
 */
fun buildToolSchemasJson(tools: List<ToolDescriptor>): String {
    if (tools.isEmpty()) return "[]"

    val schemas = tools.map { tool ->
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to tool.name,
                "description" to tool.description,
                "parameters" to tool.parameters,
            ),
        )
    }
    return json.encodeToString(schemas)
}

/**
 * Builds the tool-system prompt segment that tells the LLM which tools
 * are available and how to call them.
 *
 * This is the textual description inserted into the system prompt so the
 * model understands the function-calling contract even in non-OpenAI
 * backends that don't support native tool schemas.
 */
fun buildToolSystemSegment(tools: List<ToolDescriptor>): String {
    if (tools.isEmpty()) return ""

    val sb = StringBuilder("\n\n## Available Tools\n\n")
    sb.appendLine(
        "You have access to the following tools. To use a tool, respond with " +
            "a JSON object in a <tool_call> block exactly matching the schema below.",
    )
    sb.appendLine()

    tools.forEach { tool ->
        sb.appendLine("### ${tool.name}")
        sb.appendLine(tool.description)
        sb.appendLine("Parameters:")
        sb.appendLine("```json")
        sb.appendLine(json.encodeToString(tool.parameters))
        sb.appendLine("```")
        sb.appendLine()
    }

    // Add the function-calling format instruction
    sb.appendLine(
        "When calling a tool, respond with a JSON code block:\n" +
            "```json\n" +
            "{\"name\": \"tool_name\", \"arguments\": { ... }}\n" +
            "```\n" +
            "You may call multiple tools in a single response.\n" +
            "Always wait for the tool result before proceeding.\n" +
            "If a tool call fails, report the error and try an alternative approach.",
    )

    return sb.toString()
}

/**
 * Estimates the token count of a string using a rough character-based heuristic.
 * Actual tokenisation is model-specific, so this is an approximation used for
 * context-window management decisions.
 */
fun estimateTokenCount(text: String): Int {
    if (text.isEmpty()) return 0
    return (text.length / CHARS_PER_TOKEN_ESTIMATE).coerceAtLeast(1)
}

/**
 * Estimates the total token count for a list of messages.
 */
fun estimateMessageTokenCount(messages: List<LlmMessage>): Int {
    var total = 0
    for (msg in messages) {
        total += estimateTokenCount(msg.content ?: "")
        total += estimateTokenCount(msg.name ?: "")
        total += 4 // per-message overhead (role, metadata)
        msg.toolCalls?.forEach { call ->
            total += estimateTokenCount(call.name)
            total += estimateTokenCount(call.arguments)
        }
    }
    return total
}

// ── PromptBuilder ─────────────────────────────────────────────────────

/**
 * Builds and manages the message array for LLM conversation calls.
 *
 * Responsibilities:
 * 1. Construct the system prompt from [AgentConfig] + available tool schemas
 * 2. Inject tool descriptions as JSON Schema for OpenAI-compatible function calling
 * 3. Manage context window limits using head+tail compression (mirrors Hermes Python)
 * 4. Build the full message array for LLM completion requests
 *
 * ## Context Compression Strategy
 *
 * When the estimated token count exceeds [AgentConfig.compressionThreshold] of the
 * context window, the builder applies **head+tail compression**:
 * - Preserve the system prompt (always)
 * - Preserve the most recent messages (tail)
 * - Compress the middle by concatenating with a summary note
 *
 * This mirrors the approach in hermes-agent/agent/prompt_builder.py.
 */
class PromptBuilder(private val config: AgentConfig) {

    /**
     * Build the complete system prompt string combining the user-configured
     * system prompt with auto-generated tool descriptions.
     *
     * @param tools Available tool descriptors to inject.
     * @return The assembled system prompt.
     */
    fun buildSystemPrompt(tools: List<ToolDescriptor>): String {
        val sb = StringBuilder(config.systemPrompt.trimEnd())
        sb.append(buildToolSystemSegment(tools))

        // Add context-window awareness note
        sb.appendLine()
        sb.appendLine(
            "Your context window is ${config.contextLength} tokens. " +
                "The system will manage history truncation automatically.",
        )

        return sb.toString()
    }

    /**
     * Build the full message array for an LLM completion request.
     *
     * Includes:
     * 1. System message (always first)
     * 2. Conversation history
     * 3. The new user message
     *
     * If the estimated total exceeds the context budget, the history is
     * automatically compressed using head+tail truncation.
     *
     * @param history Previous messages in the conversation.
     * @param userMessage The new user input.
     * @param tools Available tools (for system prompt generation).
     * @return List of [LlmMessage] ready for the LLM provider.
     */
    fun buildMessages(
        history: List<LlmMessage>,
        userMessage: String,
        tools: List<ToolDescriptor> = emptyList(),
    ): List<LlmMessage> {
        val systemMessage = LlmMessage(
            role = MessageRole.System,
            content = buildSystemPrompt(tools),
        )

        val messages = mutableListOf(systemMessage)
        messages.addAll(history)
        messages.add(LlmMessage(role = MessageRole.User, content = userMessage))

        // Check if we need to compress
        val estimatedTokens = estimateMessageTokenCount(messages)
        val threshold = (config.contextLength * config.compressionThreshold).toInt()

        if (estimatedTokens > threshold) {
            return compressMessages(messages)
        }

        return messages
    }

    /**
     * Build messages from Room entities (for session restoration).
     */
    fun buildMessagesFromEntities(
        session: SessionEntity,
        messages: List<MessageEntity>,
        userMessage: String,
        tools: List<ToolDescriptor> = emptyList(),
        existingSystemPrompt: String? = null,
    ): List<LlmMessage> {
        val systemContent = existingSystemPrompt
            ?: buildSystemPrompt(tools)

        val history = messages.map { entity ->
            when (entity.role.lowercase()) {
                "user" -> LlmMessage(role = MessageRole.User, content = entity.content)
                "assistant" -> {
                    if (entity.toolCalls != null) {
                        @Suppress("UNCHECKED_CAST")
                        val calls = parseToolCalls(entity.toolCalls)
                        LlmMessage(
                            role = MessageRole.Assistant,
                            content = entity.content,
                            toolCalls = calls,
                        )
                    } else {
                        LlmMessage(role = MessageRole.Assistant, content = entity.content)
                    }
                }
                "tool" -> LlmMessage(
                    role = MessageRole.Tool,
                    content = entity.toolResult ?: entity.content,
                    name = parseToolName(entity),
                    toolCallId = parseToolCallId(entity),
                )
                else -> LlmMessage(role = MessageRole.User, content = entity.content)
            }
        }

        val messagesList = mutableListOf(
            LlmMessage(role = MessageRole.System, content = systemContent),
        )
        messagesList.addAll(history)
        messagesList.add(LlmMessage(role = MessageRole.User, content = userMessage))

        return messagesList
    }

    /**
     * Compress the message array when it exceeds the context threshold.
     *
     * Strategy (head+tail):
     * - Keep the system message (index 0) intact
     * - Keep the last ~30% of messages (recent history)
     * - Compress the middle into a single summary message
     * - Always keep the latest user message
     *
     * @param messages Full message array to compress.
     * @return Compressed message array within context budget.
     */
    fun compressMessages(messages: List<LlmMessage>): List<LlmMessage> {
        if (messages.size <= 3) return messages // system + 1 history + user = 3

        val systemPrompt = messages.first().content ?: ""
        val userMessage = messages.last()

        // Calculate what to keep
        val tailCount = (messages.size * config.compressionTargetRatio).toInt().coerceAtLeast(2)
        val historyStart = 1 // index after system
        val historyEndExclusive = messages.size - 1 // exclude user message

        // Keep recent tail
        val tailStart = (historyEndExclusive - tailCount).coerceAtLeast(historyStart)
        val tail = messages.subList(tailStart, historyEndExclusive)

        // Compress the middle
        val middleMessages = if (tailStart > historyStart) {
            messages.subList(historyStart, tailStart)
        } else {
            emptyList()
        }

        val compressed = mutableListOf<LlmMessage>()
        compressed.add(LlmMessage(role = MessageRole.System, content = systemPrompt))

        if (middleMessages.isNotEmpty()) {
            val middleContent = middleMessages
                .mapNotNull { msg ->
                    when (msg.role) {
                        MessageRole.User -> "User: ${msg.content}"
                        MessageRole.Assistant -> "Assistant: ${msg.content ?: "(tool call)"}"
                        MessageRole.Tool -> "Tool result: ${msg.content?.take(200)}"
                        else -> null
                    }
                }
                .joinToString("\n")

            compressed.add(
                LlmMessage(
                    role = MessageRole.System,
                    content = "[Compressed conversation history]:\n$middleContent\n\n" +
                        "[End of compressed history. Continuing below.]",
                ),
            )
        }

        compressed.addAll(tail)
        compressed.add(userMessage)

        return compressed
    }

    /**
     * Get the tool schemas in JSON format for OpenAI-compatible providers.
     */
    fun buildOpenAITools(tools: List<ToolDescriptor>): List<Map<String, Any>> {
        return tools.map { tool ->
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to tool.name,
                    "description" to tool.description,
                    "parameters" to tool.parameters,
                ),
            )
        }
    }

    /**
     * Check whether the current conversation state would exceed the context limit
     * if a new user message were added.
     */
    fun wouldExceedContextLimit(
        history: List<LlmMessage>,
        newUserMessage: String,
        tools: List<ToolDescriptor>,
    ): Boolean {
        val systemContent = buildSystemPrompt(tools)
        val estimatedSystem = estimateTokenCount(systemContent)
        val estimatedHistory = estimateMessageTokenCount(history)
        val estimatedUser = estimateTokenCount(newUserMessage)
        val total = estimatedSystem + estimatedHistory + estimatedUser

        return total > config.contextLength
    }
}

// ── Helper functions ──────────────────────────────────────────────────

/**
 * Parse tool calls JSON string from an assistant message into [LlmToolCall] list.
 */
private fun parseToolCalls(jsonStr: String): List<com.nousresearch.hermes.agent.core.LlmToolCall>? {
    return try {
        @Suppress("UNCHECKED_CAST")
        val parsed = json.decodeFromString<List<Map<String, Any>>>(jsonStr)
        parsed.map { call ->
            com.nousresearch.hermes.agent.core.LlmToolCall(
                id = call["id"] as? String ?: "",
                name = call["name"] as? String ?: "",
                arguments = when (val args = call["arguments"]) {
                    is Map<*, *> -> json.encodeToString(args)
                    is String -> args
                    else -> "{}"
                },
            )
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Extract the tool name from a tool-result message entity.
 * The tool_call_id in a tool message is stored in [MessageEntity.toolCalls].
 */
private fun parseToolName(entity: MessageEntity): String? {
    return try {
        @Suppress("UNCHECKED_CAST")
        val data = json.decodeFromString<Map<String, String>>(entity.toolCalls ?: "{}")
        data["name"]
    } catch (_: Exception) {
        null
    }
}

/**
 * Extract the tool-call ID from a tool-result message entity.
 */
private fun parseToolCallId(entity: MessageEntity): String? {
    return try {
        @Suppress("UNCHECKED_CAST")
        val data = json.decodeFromString<Map<String, String>>(entity.toolCalls ?: "{}")
        data["tool_call_id"]
    } catch (_: Exception) {
        null
    }
}
