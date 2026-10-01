package com.banter.app.data

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import com.banter.app.domain.Character
import com.banter.app.domain.ChatMessage
import com.banter.app.domain.PromptBuilder
import com.banter.app.domain.Scenario
import com.banter.app.domain.Voices
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.lastOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One Koog agent per profile in the cast.
 *
 * Each character is a standing agent with its own system prompt — its name, what it does, how
 * it talks, and the secret only it knows. Asking Musa for a line is asking Musa's agent, not
 * asking one shared model to pretend.
 *
 * Koog normally talks to hosted providers. Here it is wired to [LiteRtPromptExecutor], so the
 * agents run entirely on the model sitting in the phone's storage.
 */
@Singleton
class CharacterAgents @Inject constructor(
    private val engines: EngineManager,
) : Voices {

    private val executor = LiteRtPromptExecutor { engines.engine }
    private val agents = mutableMapOf<String, Agent>()

    private class Agent(val agent: GraphAIAgent<String, String>, val fingerprint: String)

    override val ready: Boolean
        get() = engines.engine != null

    override suspend fun reply(
        character: Character,
        scenario: Scenario,
        recent: List<ChatMessage>,
    ): String {
        val system = PromptBuilder.system(character, scenario)
        val agent = agentFor(character, system)
        return agent.run(PromptBuilder.turn(character, scenario, recent))
    }

    /** A persona is fixed for an agent's lifetime, so an edited profile gets a new agent. */
    private fun agentFor(character: Character, system: String): GraphAIAgent<String, String> {
        agents[character.id]?.let { if (it.fingerprint == system) return it.agent }

        val built = AIAgent(
            promptExecutor = executor,
            llmModel = modelFor(character),
            systemPrompt = system,
            temperature = TEMPERATURE,
        )
        agents[character.id] = Agent(built, system)
        return built
    }

    /**
     * The character's name rides along as the model id purely so the runtime logs say who was
     * speaking; there is only ever one model file on the device.
     */
    private fun modelFor(character: Character) = LLModel(
        provider = OnDevice,
        id = character.name,
        capabilities = listOf(LLMCapability.Completion, LLMCapability.Temperature),
        contextLength = CONTEXT_TOKENS,
        maxOutputTokens = MAX_OUTPUT_TOKENS,
    )

    private companion object {
        /** Not a hosted provider: the weights are a file in this app's storage. */
        val OnDevice = object : LLMProvider("litertlm", "On-device") {}

        const val TEMPERATURE = 0.8
        const val CONTEXT_TOKENS = 2048L
        const val MAX_OUTPUT_TOKENS = 64L
    }
}

/**
 * Lets Koog drive the model that is already on the phone.
 *
 * Koog ships clients for OpenAI, Anthropic, Google and friends — all of which need a network
 * and an API key. This is the seam where a local runtime plugs in instead: Koog builds the
 * prompt and owns the agent, LiteRT-LM does the generating, and nothing leaves the device.
 */
private class LiteRtPromptExecutor(private val engine: () -> ChatEngine?) : PromptExecutor() {

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
