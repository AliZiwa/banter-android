package com.banter.app.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.banter.app.data.Character
import com.banter.app.data.ChatMessage
import com.banter.app.data.Scenario
import com.banter.app.director.PromptBuilder
import com.banter.app.director.Voices
import com.banter.app.llm.ChatEngine

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
class CharacterAgents(private val engine: () -> ChatEngine?) : Voices {

    private val executor = LiteRtPromptExecutor(engine)
    private val agents = mutableMapOf<String, Agent>()

    private class Agent(val agent: GraphAIAgent<String, String>, val fingerprint: String)

    override val ready: Boolean
        get() = engine() != null

    override suspend fun reply(
        character: Character,
        scenario: Scenario,
        recent: List<ChatMessage>,
    ): String {
        val system = PromptBuilder.system(character, scenario)
        val agent = agentFor(character, system)
        return agent.run(PromptBuilder.turn(character, scenario, recent))
    }

    /** Rebuilt whenever the profile is edited: a persona is fixed for an agent's lifetime. */
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

    fun close() {
        agents.clear()
        executor.close()
    }

    private companion object {
        /** Not a hosted provider: the weights are a file in this app's storage. */
        val OnDevice = object : LLMProvider("litertlm", "On-device") {}

        const val TEMPERATURE = 0.8
        const val CONTEXT_TOKENS = 2048L
        const val MAX_OUTPUT_TOKENS = 64L
    }
}
