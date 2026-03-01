package com.castor.core.inference.context

import android.util.Log
import com.castor.core.inference.InferenceEngine
import com.castor.core.inference.prompt.ConversationTurn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compresses conversation context to fit within the LLM's token limit.
 *
 * Ported from Hermes Agent's `context_compressor.py`. Strategy:
 * 1. **Protect** the first turn (system prompt) and last N turns (recent context).
 * 2. **Summarize** the middle turns using the on-device LLM.
 * 3. **Fallback** to simple truncation if LLM is not loaded.
 *
 * This is critical for Qwen2.5-3B's 4096-token context window — without
 * compression, multi-turn conversations overflow after ~3 exchanges with tools.
 */
@Singleton
class ContextCompressor @Inject constructor(
    private val engine: InferenceEngine
) {

    companion object {
        private const val TAG = "ContextCompressor"

        /** Number of recent turns to always preserve (user + assistant pairs). */
        private const val PROTECTED_TAIL_TURNS = 4

        /** Max tokens for the summary generation. */
        private const val SUMMARY_MAX_TOKENS = 200

        /** Heuristic: average characters per token for English text. */
        private const val CHARS_PER_TOKEN = 4

        /** Trigger compression when estimated tokens exceed this fraction of context. */
        const val COMPRESSION_THRESHOLD = 0.80f

        private const val SUMMARY_SYSTEM_PROMPT =
            "Summarize the following conversation turns concisely. " +
            "Preserve key facts, decisions, and tool results. " +
            "Output only the summary, no preamble."
    }

    /**
     * Estimate the token count for a list of conversation turns.
     *
     * Uses the engine's tokenizer when loaded, otherwise falls back to a
     * character-based heuristic (4 chars ≈ 1 token).
     */
    suspend fun estimateTokens(turns: List<ConversationTurn>): Int {
        val text = turns.joinToString("\n") { "${it.role}: ${it.content}" }
        return try {
            if (engine.isLoaded) {
                engine.getTokenCount(text)
            } else {
                text.length / CHARS_PER_TOKEN
            }
        } catch (e: Exception) {
            text.length / CHARS_PER_TOKEN
        }
    }

    /**
     * Compress conversation turns if they exceed the token budget.
     *
     * @param turns The full conversation (system + user/assistant/tool turns).
     * @param maxContextTokens The model's context window size (e.g. 4096).
     * @return Compressed turn list that fits within the budget.
     */
    suspend fun compress(
        turns: List<ConversationTurn>,
        maxContextTokens: Int = 4096
    ): List<ConversationTurn> {
        val estimated = estimateTokens(turns)
        val threshold = (maxContextTokens * COMPRESSION_THRESHOLD).toInt()

        if (estimated <= threshold) {
            return turns // No compression needed
        }

        Log.d(TAG, "Compressing: $estimated estimated tokens > $threshold threshold")

        // Separate system prompt (first turn) from the rest
        val systemTurn = turns.firstOrNull { it.role == "system" }
        val nonSystemTurns = if (systemTurn != null) turns.drop(1) else turns

        // Protect the last N turns
        val protectedTail = nonSystemTurns.takeLast(PROTECTED_TAIL_TURNS)
        val middleTurns = nonSystemTurns.dropLast(PROTECTED_TAIL_TURNS)

        if (middleTurns.isEmpty()) {
            // Nothing to compress — just return as-is
            return turns
        }

        // Summarize middle turns
        val summary = summarizeTurns(middleTurns)

        // Rebuild the turn list
        return buildList {
            if (systemTurn != null) add(systemTurn)
            add(ConversationTurn(
                role = "user",
                content = "[Previous conversation summary: $summary]"
            ))
            addAll(protectedTail)
        }
    }

    /**
     * Summarize a list of conversation turns into a compact text.
     */
    private suspend fun summarizeTurns(turns: List<ConversationTurn>): String {
        val turnText = turns.joinToString("\n") { turn ->
            "${turn.role}: ${turn.content.take(300)}"
        }

        return try {
            if (engine.isLoaded) {
                engine.generate(
                    prompt = turnText,
                    systemPrompt = SUMMARY_SYSTEM_PROMPT,
                    maxTokens = SUMMARY_MAX_TOKENS,
                    temperature = 0.3f
                ).trim()
            } else {
                // Fallback: simple truncation summary
                truncateSummary(turns)
            }
        } catch (e: Exception) {
            Log.w(TAG, "LLM summarization failed, falling back to truncation: ${e.message}")
            truncateSummary(turns)
        }
    }

    /**
     * Fallback: create a truncated summary by taking the first line of each turn.
     */
    private fun truncateSummary(turns: List<ConversationTurn>): String {
        return turns.joinToString("; ") { turn ->
            val preview = turn.content.lines().firstOrNull()?.take(80) ?: ""
            "${turn.role}: $preview"
        }.take(500)
    }
}
