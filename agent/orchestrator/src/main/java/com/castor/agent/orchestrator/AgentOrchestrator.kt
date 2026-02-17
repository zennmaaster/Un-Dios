package com.castor.agent.orchestrator

import com.castor.core.common.model.AgentIntent
import com.castor.core.common.model.MediaSource
import com.castor.core.common.model.MessageSource
import com.castor.core.inference.InferenceEngine
import com.castor.core.inference.ModelManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentOrchestrator @Inject constructor(
    private val engine: InferenceEngine,
    private val modelManager: ModelManager,
    private val messagingAgent: MessagingAgent
) {

    companion object {
        private const val CLASSIFY_MAX_TOKENS = 128
        private const val PROCESS_MAX_TOKENS = 256

        private const val ROUTER_SYSTEM_PROMPT = """You are Un-Dios, a helpful AI assistant running on the user's Android phone.
You can help with:
- Messaging: Read and reply to WhatsApp and Teams messages
- Media: Control Spotify, YouTube, and Audible playback
- Reminders: Set and manage reminders and calendar events
- General questions: Answer questions using your knowledge

Respond concisely and helpfully. If you need to take an action (like sending a message or playing music), describe what you would do."""

        private const val CLASSIFY_SYSTEM_PROMPT = """You are an intent classifier. Given user input, classify it into exactly one category.

Categories:
- SEND_MESSAGE: User wants to send, reply to, or compose a message (e.g., "text Mom", "reply to John", "tell Sarah I'm on my way")
- PLAY_MEDIA: User wants to play music, a video, a podcast, or an audiobook (e.g., "play some jazz", "put on a podcast")
- QUEUE_MEDIA: User wants to add media to a queue without playing immediately (e.g., "add this to my queue", "queue up the next episode")
- SET_REMINDER: User wants to set a reminder or alarm (e.g., "remind me at 5pm", "set an alarm for tomorrow")
- SUMMARIZE: User wants a summary of messages, conversations, or information (e.g., "summarize my unread messages", "what did I miss")
- GENERAL_QUERY: Any other question or request that doesn't fit the above categories

Respond with ONLY the category name (e.g., "SEND_MESSAGE"), nothing else."""

        // Keyword mappings for fallback classification
        private val MESSAGE_KEYWORDS = listOf(
            "message", "text", "reply", "send", "tell", "write to",
            "respond", "whatsapp", "teams", "chat", "dm", "msg"
        )
        private val PLAY_KEYWORDS = listOf(
            "play", "listen", "music", "song", "podcast", "audiobook",
            "spotify", "youtube", "audible", "stream", "put on"
        )
        private val QUEUE_KEYWORDS = listOf(
            "queue", "add to queue", "play next", "up next", "add to playlist"
        )
        private val REMINDER_KEYWORDS = listOf(
            "remind", "reminder", "alarm", "schedule", "timer",
            "alert me", "notify me", "don't forget"
        )
        private val SUMMARIZE_KEYWORDS = listOf(
            "summarize", "summary", "what did i miss", "catch me up",
            "recap", "unread", "overview", "tldr", "tl;dr"
        )
    }

    /**
     * Classify the user's input into an [AgentIntent] using the LLM.
     * Falls back to keyword-based classification if the model is not loaded.
     */
    suspend fun classifyIntent(input: String): AgentIntent {
        val category = if (engine.isLoaded) {
            classifyWithLlm(input)
        } else {
            classifyWithKeywords(input)
        }
        return mapCategoryToIntent(category, input)
    }

    /**
     * Process user input: classify intent, route to the appropriate agent, and return a response.
     */
    suspend fun processInput(input: String): String {
        if (!engine.isLoaded) {
            return "Model not loaded. Place a .gguf model file in the models directory and restart Castor."
        }

        return try {
            val intent = classifyIntent(input)
            routeIntent(intent, input)
        } catch (e: Exception) {
            // Fall back to general generation if routing fails
            engine.generate(
                prompt = input,
                systemPrompt = ROUTER_SYSTEM_PROMPT,
                maxTokens = PROCESS_MAX_TOKENS,
                temperature = 0.7f
            )
        }
    }

    /**
     * Direct access to the messaging agent for UI components that need
     * messaging-specific features (smart replies, compose, etc.).
     */
    fun getMessagingAgent(): MessagingAgent = messagingAgent

    /**
     * Use the LLM to classify user input into an intent category string.
     */
    private suspend fun classifyWithLlm(input: String): String {
        return try {
            val response = engine.generate(
                prompt = input,
                systemPrompt = CLASSIFY_SYSTEM_PROMPT,
                maxTokens = CLASSIFY_MAX_TOKENS,
                temperature = 0.1f
            ).trim().uppercase()

            // Validate the response is a known category
            when {
                response.contains("SEND_MESSAGE") -> "SEND_MESSAGE"
                response.contains("PLAY_MEDIA") -> "PLAY_MEDIA"
                response.contains("QUEUE_MEDIA") -> "QUEUE_MEDIA"
                response.contains("SET_REMINDER") -> "SET_REMINDER"
                response.contains("SUMMARIZE") -> "SUMMARIZE"
                else -> "GENERAL_QUERY"
            }
        } catch (e: Exception) {
            classifyWithKeywords(input)
        }
    }

    /**
     * Fallback keyword-based intent classification when the LLM is not available.
     */
    private fun classifyWithKeywords(input: String): String {
        val lowered = input.lowercase()

        // Check each category's keywords — order matters for priority
        // Queue before play since "add to queue" contains no play keywords
        if (QUEUE_KEYWORDS.any { lowered.contains(it) }) return "QUEUE_MEDIA"
        if (REMINDER_KEYWORDS.any { lowered.contains(it) }) return "SET_REMINDER"
        if (MESSAGE_KEYWORDS.any { lowered.contains(it) }) return "SEND_MESSAGE"
        if (SUMMARIZE_KEYWORDS.any { lowered.contains(it) }) return "SUMMARIZE"
        if (PLAY_KEYWORDS.any { lowered.contains(it) }) return "PLAY_MEDIA"

        return "GENERAL_QUERY"
    }

    /**
     * Map a classification category string to a concrete [AgentIntent].
     */
    private fun mapCategoryToIntent(category: String, input: String): AgentIntent {
        return when (category) {
            "SEND_MESSAGE" -> AgentIntent.SendMessage(
                recipient = extractRecipient(input),
                content = input,
                source = guessMessageSource(input)
            )
            "PLAY_MEDIA" -> AgentIntent.PlayMedia(
                query = input,
                source = guessMediaSource(input)
            )
            "QUEUE_MEDIA" -> AgentIntent.QueueMedia(
                query = input,
                source = guessMediaSource(input)
            )
            "SET_REMINDER" -> AgentIntent.SetReminder(
                description = input,
                timeDescription = extractTimeDescription(input)
            )
            "SUMMARIZE" -> AgentIntent.Summarize(
                context = input
            )
            else -> AgentIntent.GeneralQuery(
                query = input
            )
        }
    }

    /**
     * Route a classified intent to the appropriate agent and generate a response.
     */
    private suspend fun routeIntent(intent: AgentIntent, originalInput: String): String {
        return when (intent) {
            is AgentIntent.SendMessage -> {
                val composed = messagingAgent.composeMessage(originalInput)
                "Draft message for ${intent.recipient}: \"$composed\""
            }
            is AgentIntent.Summarize -> {
                // For summarize intents, use the general engine since we don't have
                // specific conversation context from just text input
                engine.generate(
                    prompt = originalInput,
                    systemPrompt = ROUTER_SYSTEM_PROMPT,
                    maxTokens = PROCESS_MAX_TOKENS,
                    temperature = 0.5f
                )
            }
            is AgentIntent.PlayMedia,
            is AgentIntent.QueueMedia,
            is AgentIntent.SetReminder,
            is AgentIntent.GeneralQuery -> {
                // These intents will be handled by their respective agents in future phases.
                // For now, use the general router to generate a helpful response.
                engine.generate(
                    prompt = originalInput,
                    systemPrompt = ROUTER_SYSTEM_PROMPT,
                    maxTokens = PROCESS_MAX_TOKENS,
                    temperature = 0.7f
                )
            }
        }
    }

    /**
     * Attempt to extract a recipient name from the user input.
     * Simple heuristic: look for patterns like "tell X", "message X", "text X".
     */
    private fun extractRecipient(input: String): String {
        val patterns = listOf(
            Regex("""(?:tell|text|message|msg|dm|write to|reply to|send to|respond to)\s+(\w+)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(input)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return "unknown"
    }

    /**
     * Guess which messaging platform to use based on keywords in the input.
     */
    private fun guessMessageSource(input: String): MessageSource {
        val lowered = input.lowercase()
        return when {
            lowered.contains("teams") || lowered.contains("work") -> MessageSource.TEAMS
            else -> MessageSource.WHATSAPP
        }
    }

    /**
     * Guess which media source to use based on keywords in the input.
     */
    private fun guessMediaSource(input: String): MediaSource? {
        val lowered = input.lowercase()
        return when {
            lowered.contains("spotify") -> MediaSource.SPOTIFY
            lowered.contains("youtube") -> MediaSource.YOUTUBE
            lowered.contains("audible") || lowered.contains("audiobook") -> MediaSource.AUDIBLE
            else -> null
        }
    }

    /**
     * Extract a time description from reminder-related input.
     */
    private fun extractTimeDescription(input: String): String {
        val timePatterns = listOf(
            Regex("""(?:at|in|by|around|before|after)\s+(.+?)(?:\s+to\s+|\s*$)""", RegexOption.IGNORE_CASE),
            Regex("""(tomorrow|tonight|today|next\s+\w+|\d+\s*(?:am|pm|minutes?|hours?|mins?|hrs?))""", RegexOption.IGNORE_CASE)
        )
        for (pattern in timePatterns) {
            val match = pattern.find(input)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return "unspecified time"
    }
}
