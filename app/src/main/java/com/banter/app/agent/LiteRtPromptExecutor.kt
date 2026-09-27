package com.banter.app.agent

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import com.banter.app.llm.ChatEngine
import com.banter.app.llm.ReplySpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.lastOrNull

/**
 * Lets Koog drive the model that is already on the phone.
 *
 * Koog ships clients for OpenAI, Anthropic, Google and friends — all of which need a network
 * and an API key. This is the seam where a local runtime plugs in instead: Koog builds the
 * prompt and owns the agent, LiteRT-LM does the generating, and nothing leaves the device.
 */
class LiteRtPromptExecutor(private val engine: () -> ChatEngine?) : PromptExecutor() {

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant = Message.Assistant(
        content = generate(prompt, model),
        metaInfo = ResponseMetaInfo.Empty,
    )

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> = flow {
        emit(StreamFrame.TextDelta(generate(prompt, model)))
        emit(StreamFrame.End())
    }

    /** No local moderation model, and inventing a verdict would be worse than saying so. */
    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        throw UnsupportedOperationException("Banter runs offline and has no moderation model")

    override fun close() = Unit

    private suspend fun generate(prompt: Prompt, model: LLModel): String {
        val active = engine() ?: error("No model is loaded on this device")

        // Koog's prompt is a list of typed messages. LiteRT-LM wants a system instruction and
        // one user turn, so the conversation is flattened into exactly that.
        val system = prompt.messages.filterIsInstance<Message.System>()
            .joinToString("\n") { it.textContent() }
        val body = prompt.messages.filterNot { it is Message.System }
            .joinToString("\n") { it.textContent() }

        val spec = ReplySpec(
            speaker = model.id,
            system = system,
            prompt = body,
            temperature = prompt.params.temperature ?: DEFAULT_TEMPERATURE,
            maxOutputTokens = model.maxOutputTokens?.toInt() ?: DEFAULT_MAX_TOKENS,
        )
        return active.reply(spec).lastOrNull().orEmpty()
    }

    private companion object {
        const val DEFAULT_TEMPERATURE = 0.8
        const val DEFAULT_MAX_TOKENS = 64
    }
}
