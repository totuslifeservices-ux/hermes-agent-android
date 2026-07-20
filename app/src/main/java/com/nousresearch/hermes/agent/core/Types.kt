package com.nousresearch.hermes.agent.core

/**
 * Core shared types for the Hermes Agent Android orchestrator.
 * These are the foundational data classes used across all subsystems
 * — tools, LLM providers, session store, and the agent loop.
 */

// ── Messages ──────────────────────────────────────────────────────
enum class MessageRole { System, User, Assistant, Tool }

data class LlmMessage(
    val role: MessageRole,
    val content: String? = null,
    val toolCalls: List<LlmToolCall>? = null,
    val toolCallId: String? = null,
    val name: String? = null,
)

data class LlmToolCall(
    val id: String,
    val name: String,
    val arguments: String, // JSON string
)

// ── Provider types ────────────────────────────────────────────────
enum class ProviderType { NousPortal, OpenRouter, Ollama, Custom }

data class ProviderConfig(
    val type: ProviderType,
    val model: String,
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val maxTokens: Int = 32768,
    val temperature: Float = 0.7f,
)

// ── Completion / Response types ───────────────────────────────────
data class CompletionRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val tools: List<ToolDescriptor>? = null,
    val maxTokens: Int = 32768,
    val temperature: Float = 0.7f,
    val stream: Boolean = false,
)

data class CompletionResponse(
    val content: String?,
    val toolCalls: List<LlmToolCall>? = null,
    val finishReason: String? = null,
    val usage: UsageInfo? = null,
)

data class UsageInfo(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
)

sealed class StreamEvent {
    data class TextChunk(val text: String) : StreamEvent()
    data class ToolCall(val call: LlmToolCall) : StreamEvent()
    data class ToolResult(
        val call: LlmToolCall,
        val result: String,
        val isError: Boolean = false,
    ) : StreamEvent()
    data object Done : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}

// ── Tool types ────────────────────────────────────────────────────
data class ToolDescriptor(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>, // JSON Schema
)

sealed class ToolResult {
    data class Success(val content: String) : ToolResult()
    data class Error(val message: String, val recoverable: Boolean = false) : ToolResult()
    data object PendingConfirmation : ToolResult()
}

// ── Session types ─────────────────────────────────────────────────
data class SessionInfo(
    val id: String,
    val title: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val modelConfig: String? = null,
    val messageCount: Int = 0,
    val tokenCount: Int = 0,
)

data class SessionMessage(
    val id: String = "",
    val sessionId: String,
    val role: MessageRole,
    val content: String?,
    val toolCalls: String? = null, // JSON
    val toolResult: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

// ── Agent config ──────────────────────────────────────────────────
data class AgentConfig(
    val maxTurns: Int = 90,
    val maxToolIterations: Int = 25,
    val contextLength: Int = 32768,
    val compressionThreshold: Float = 0.50f,
    val compressionTargetRatio: Float = 0.20f,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT: String = """You are Hermes Agent, an intelligent AI assistant running natively on an Android device. You have full access to the device's capabilities — SMS, contacts, email, calendar, files, camera, microphone, location, clipboard, notifications, and more.

You are helpful, knowledgeable, and direct. You communicate clearly and prioritize being genuinely useful.

You run entirely on-device with offline-first architecture. No telemetry, no data harvesting, no mandatory cloud dependencies."""
    }
}
