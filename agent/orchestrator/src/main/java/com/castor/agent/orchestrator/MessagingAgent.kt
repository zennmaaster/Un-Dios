package com.castor.agent.orchestrator

import com.castor.core.common.model.CastorMessage
import com.castor.core.data.repository.MessageRepository
import com.castor.core.inference.InferenceEngine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagingAgent @Inject constructor(
    private val engine: InferenceEngine,
    private val messageRepository: MessageRepository
) {

    companion object {
        private const val MAX_CONTEXT_MESSAGES = 20
        private const val SMART_REPLY_MAX_TOKENS = 256
        private const val SUMMARY_MAX_TOKENS = 512
        private const val COMPOSE_MAX_TOKENS = 256

        private val FALLBACK_SMART_REPLIES = listOf(
            "Sounds good!",
            "Let me get back to you",
            "Thanks!"
        )

        private const val SMART_REPLY_SYSTEM_PROMPT = """You are a smart reply assistant for a messaging app. Given a conversation, generate exactly 3 brief, contextually appropriate reply suggestions.

Rules:
- Each reply must be concise (under 30 words)
- Replies should be varied in tone: one casual/positive, one informative/neutral, one brief acknowledgment
- Match the conversation's language and formality level
- Output ONLY the 3 replies in this exact numbered format, nothing else:
1. [first reply]
2. [second reply]
3. [third reply]"""

        private const val SUMMARY_SYSTEM_PROMPT = """You are a conversation summarizer. Given a conversation thread, provide a concise summary that captures:
- The main topic(s) discussed
- Key decisions or action items
- Current status or open questions

Keep the summary under 3 sentences. Be factual and concise. Do not include any preamble like "Here is a summary" — just provide the summary directly."""

        private const val COMPOSE_SYSTEM_PROMPT = """You are a message composer. Given a natural language instruction describing what the user wants to say, compose an appropriate message.

Rules:
- Write the message directly, ready to send — no quotes, no explanation, no preamble
- Match the appropriate tone (casual for friends/family, professional for work)
- Keep it natural and human-sounding
- Be concise but complete
- If conversation context is provided, make the message fit naturally into the conversation"""
    }

    /**
     * Generate smart reply suggestions given recent conversation messages.
     *
     * @param conversationMessages Recent messages from the conversation, in chronological order.
     * @param maxReplies Maximum number of replies to generate (default 3).
     * @return A list of suggested reply strings.
     */
    suspend fun generateSmartReplies(
        conversationMessages: List<CastorMessage>,
        maxReplies: Int = 3
    ): List<String> {
        if (!engine.isLoaded) {
            return FALLBACK_SMART_REPLIES.take(maxReplies)
        }

        if (conversationMessages.isEmpty()) {
            return FALLBACK_SMART_REPLIES.take(maxReplies)
        }

        val context = formatConversationContext(conversationMessages)
        val prompt = """Here is the recent conversation:

$context

Generate exactly $maxReplies smart reply suggestions for this conversation."""

        return try {
            val response = engine.generate(
                prompt = prompt,
                systemPrompt = SMART_REPLY_SYSTEM_PROMPT,
                maxTokens = SMART_REPLY_MAX_TOKENS,
                temperature = 0.8f
            )
            parseSmartReplies(response, maxReplies)
        } catch (e: Exception) {
            FALLBACK_SMART_REPLIES.take(maxReplies)
        }
    }

    /**
     * Summarize a conversation thread into a concise overview.
     *
     * @param messages The conversation messages to summarize, in chronological order.
     * @return A concise summary string.
     */
    suspend fun summarizeThread(
        messages: List<CastorMessage>
    ): String {
        if (!engine.isLoaded) {
            return buildFallbackSummary(messages)
        }

        if (messages.isEmpty()) {
            return "No messages to summarize."
        }

        val context = formatConversationContext(messages)
        val prompt = """Summarize the following conversation:

$context"""

        return try {
            engine.generate(
                prompt = prompt,
                systemPrompt = SUMMARY_SYSTEM_PROMPT,
                maxTokens = SUMMARY_MAX_TOKENS,
                temperature = 0.3f
            ).trim()
        } catch (e: Exception) {
            buildFallbackSummary(messages)
        }
    }

    /**
     * Compose a message given a natural language instruction.
     *
     * For example, "tell Mom I'll be late" might produce:
     * "Hey Mom, I'll be running a bit late. I'll let you know when I'm on my way!"
     *
     * @param instruction Natural language description of what to say.
     * @param context Optional conversation context for better message composition.
     * @return The composed message text.
     */
    suspend fun composeMessage(
        instruction: String,
        context: List<CastorMessage>? = null
    ): String {
        if (!engine.isLoaded) {
            return instruction
        }

        val prompt = buildString {
            if (!context.isNullOrEmpty()) {
                append("Here is the recent conversation for context:\n\n")
                append(formatConversationContext(context))
                append("\n\n")
            }
            append("Compose a message based on this instruction: $instruction")
        }

        return try {
            engine.generate(
                prompt = prompt,
                systemPrompt = COMPOSE_SYSTEM_PROMPT,
                maxTokens = COMPOSE_MAX_TOKENS,
                temperature = 0.7f
            ).trim()
        } catch (e: Exception) {
            // If generation fails, return the instruction as-is
            instruction
        }
    }

    /**
     * Format a list of conversation messages into a chronological text representation
     * suitable for LLM context input.
     */
    private fun formatConversationContext(messages: List<CastorMessage>): String {
        val recentMessages = if (messages.size > MAX_CONTEXT_MESSAGES) {
            messages.takeLast(MAX_CONTEXT_MESSAGES)
        } else {
            messages
        }

        return recentMessages
            .sortedBy { it.timestamp }
            .joinToString("\n") { msg ->
                val source = msg.source.name.lowercase().replaceFirstChar { it.uppercase() }
                val group = if (msg.groupName != null) " [${msg.groupName}]" else ""
                "[$source$group] ${msg.sender}: ${msg.content}"
            }
    }

    /**
     * Parse the LLM response to extract numbered smart replies.
     * Expected format: "1. reply one\n2. reply two\n3. reply three"
     */
    private fun parseSmartReplies(response: String, maxReplies: Int): List<String> {
        val numberedPattern = Regex("""^\d+\.\s*(.+)""", RegexOption.MULTILINE)
        val matches = numberedPattern.findAll(response)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .take(maxReplies)
            .toList()

        // If we couldn't parse enough replies, supplement with fallbacks
        return if (matches.size >= maxReplies) {
            matches
        } else {
            val result = matches.toMutableList()
            for (fallback in FALLBACK_SMART_REPLIES) {
                if (result.size >= maxReplies) break
                if (fallback !in result) {
                    result.add(fallback)
                }
            }
            result.take(maxReplies)
        }
    }

    /**
     * Build a simple fallback summary when the LLM is not available.
     */
    private fun buildFallbackSummary(messages: List<CastorMessage>): String {
        if (messages.isEmpty()) return "No messages to summarize."

        val participants = messages.map { it.sender }.distinct()
        val sources = messages.map { it.source.name.lowercase() }.distinct()
        val messageCount = messages.size

        return "Conversation with ${participants.joinToString(", ")} " +
                "($messageCount messages via ${sources.joinToString(", ")})"
    }
}
