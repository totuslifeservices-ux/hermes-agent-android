package com.nousresearch.hermes.agent.core.llm

import com.nousresearch.hermes.agent.core.CompletionRequest
import com.nousresearch.hermes.agent.core.CompletionResponse
import com.nousresearch.hermes.agent.core.ProviderType
import com.nousresearch.hermes.agent.core.StreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * Base interface for all LLM providers in the multi-provider broker system.
 *
 * Each concrete provider implements an OpenAI-compatible chat completions API
 * (with streaming and tool calling) and abstracts the transport, auth, and
 * endpoint details.
 */
interface LlmProvider {

    /** The provider type constant used by [LlmBroker] for routing. */
    val type: ProviderType

    /**
     * Single-shot (non-streaming) completion.
     *
     * @param request The completion request including messages, tools, and params.
     * @return A [CompletionResponse] with the model's reply.
     */
    suspend fun complete(request: CompletionRequest): CompletionResponse

    /**
     * Streaming completion — emits [StreamEvent] values as the model generates.
     *
     * The caller consumes the [Flow] until [StreamEvent.Done] or [StreamEvent.Error].
     * Cancelling the coroutine scope releases the HTTP connection.
     *
     * @param request The completion request.
     * @return A cold [Flow] of [StreamEvent] events.
     */
    fun stream(request: CompletionRequest): Flow<StreamEvent>
}
