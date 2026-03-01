package com.castor.agent.orchestrator

import android.util.Log
import com.castor.core.inference.InferenceEngine
import com.castor.core.inference.context.ContextCompressor
import com.castor.core.inference.prompt.ConversationTurn
import com.castor.core.inference.prompt.PromptFormat
import com.castor.core.inference.prompt.PromptFormatter
import com.castor.core.inference.tool.ToolCallParser
import com.castor.core.inference.tool.ToolRegistry
import com.castor.agent.orchestrator.tools.ToolInitializer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of running the agent loop.
 *
 * @param response The final text response to show the user.
 * @param turnsUsed How many LLM inference turns were needed.
 * @param toolCallsMade Total number of tool calls dispatched.
 */
data class AgentLoopResult(
    val response: String,
    val turnsUsed: Int,
    val toolCallsMade: Int
)

/**
 * Multi-turn agent loop — the core of Un-Dios's intelligence.
 *
 * Ported from Hermes Agent's `run_agent.py`. Replaces the single-shot
 * classify→route→respond flow with an iterative loop:
 *
 * ```
 * 1. Build system prompt (identity + tools + memory)
 * 2. messages = [system, ...history, user(input)]
 * 3. for turn in 0..MAX_TURNS:
 *    a. Check context window, compress if needed
 *    b. Format messages via PromptFormatter
 *    c. response = engine.generateRaw(formattedPrompt)
 *    d. toolCalls = ToolCallParser.parse(response)
 *    e. if no tool calls: return response text (done)
 *    f. messages += assistant(response)
 *    g. for each toolCall:
 *       result = toolRegistry.dispatch(toolCall)
 *       messages += tool(result)
 * 4. Return last response or timeout message
 * ```
 */
@Singleton
class AgentLoop @Inject constructor(
    private val engine: InferenceEngine,
    private val toolRegistry: ToolRegistry,
    private val promptBuilder: PromptBuilder,
    private val contextCompressor: ContextCompressor,
    private val toolInitializer: ToolInitializer
) {

    companion object {
        private const val TAG = "AgentLoop"

        /** Maximum agent turns before giving up. */
        private const val MAX_TURNS = 5

        /** Max tokens for the LLM response per turn. */
        private const val GENERATION_MAX_TOKENS = 512

        /** Temperature for tool-calling turns (low for precision). */
        private const val TOOL_TEMPERATURE = 0.4f

        /** Temperature for final response (slightly warmer for natural language). */
        private const val RESPONSE_TEMPERATURE = 0.7f

        /** Context window size for Qwen2.5-3B. */
        private const val CONTEXT_WINDOW = 4096

        private const val TIMEOUT_MSG = "I ran out of steps while working on your request. " +
            "Here's what I found so far."
    }

    /**
     * Run the agent loop for a single user input.
     *
     * @param userInput The user's natural language input.
     * @param conversationHistory Previous turns from [ConversationManager], if any.
     * @return An [AgentLoopResult] with the final response.
     */
    suspend fun run(
        userInput: String,
        conversationHistory: List<ConversationTurn> = emptyList()
    ): AgentLoopResult {
        // Step 0: Ensure all tools are registered
        toolInitializer.ensureRegistered()

        // Step 1: Build system prompt and tools block
        val systemPrompt = promptBuilder.buildSystemPrompt()
        val toolsBlock = promptBuilder.getToolsBlock()

        // Step 2: Initialize messages
        val messages = mutableListOf<ConversationTurn>()
        messages.add(ConversationTurn(role = "system", content = systemPrompt))

        // Add conversation history (skip system turns from history)
        for (turn in conversationHistory) {
            if (turn.role != "system") {
                messages.add(turn)
            }
        }

        // Add current user input
        messages.add(ConversationTurn(role = "user", content = userInput))

        var totalToolCalls = 0
        var lastResponse = ""

        // Step 3: Agent loop
        for (turn in 0 until MAX_TURNS) {
            Log.d(TAG, "Agent turn $turn/${MAX_TURNS - 1}, messages=${messages.size}")

            // 3a: Check context window, compress if needed
            val compressed = contextCompressor.compress(messages, CONTEXT_WINDOW)
            val workingMessages = compressed.toMutableList()

            // 3b: Format messages with tools block
            val formattedPrompt = PromptFormatter.formatMultiTurnWithTools(
                format = PromptFormat.CHATML,
                turns = workingMessages,
                toolsBlock = toolsBlock.takeIf { it.isNotBlank() }
            )

            // 3c: Generate response
            val temperature = if (turn == 0 && toolsBlock.isBlank()) {
                RESPONSE_TEMPERATURE
            } else {
                TOOL_TEMPERATURE
            }

            val response = try {
                engine.generateRaw(
                    formattedPrompt = formattedPrompt,
                    maxTokens = GENERATION_MAX_TOKENS,
                    temperature = temperature
                )
            } catch (e: Exception) {
                Log.e(TAG, "Generation failed on turn $turn", e)
                return AgentLoopResult(
                    response = "I encountered an error while thinking. Please try again.",
                    turnsUsed = turn + 1,
                    toolCallsMade = totalToolCalls
                )
            }

            lastResponse = response

            // 3d: Parse tool calls
            val toolCalls = ToolCallParser.parse(response)

            // 3e: If no tool calls, we're done — return the text response
            if (toolCalls.isEmpty()) {
                val cleanResponse = ToolCallParser.stripToolCalls(response).trim()
                Log.d(TAG, "No tool calls on turn $turn, returning response (${cleanResponse.length} chars)")
                return AgentLoopResult(
                    response = cleanResponse,
                    turnsUsed = turn + 1,
                    toolCallsMade = totalToolCalls
                )
            }

            // 3f: Add assistant response to messages
            messages.add(ConversationTurn(role = "assistant", content = response))

            // 3g: Dispatch each tool call and add results
            for (call in toolCalls) {
                Log.d(TAG, "Dispatching tool: ${call.name}")
                val result = toolRegistry.dispatch(call)
                totalToolCalls++

                val toolResponseText = ToolCallParser.formatToolResponse(call, result)
                messages.add(ConversationTurn(role = "tool", content = toolResponseText))

                Log.d(TAG, "Tool ${call.name} result: success=${result.success}, " +
                    "output=${result.output.take(100)}")
            }
        }

        // Step 4: Ran out of turns
        Log.w(TAG, "Agent loop exhausted $MAX_TURNS turns")
        val cleanResponse = ToolCallParser.stripToolCalls(lastResponse).trim()
        val finalResponse = if (cleanResponse.isNotBlank()) {
            "$TIMEOUT_MSG\n\n$cleanResponse"
        } else {
            TIMEOUT_MSG
        }

        return AgentLoopResult(
            response = finalResponse,
            turnsUsed = MAX_TURNS,
            toolCallsMade = totalToolCalls
        )
    }
}
