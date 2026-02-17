package com.castor.agent.orchestrator

import com.castor.core.common.model.AgentIntent
import com.castor.core.common.model.AgentType
import com.castor.core.common.model.MediaSource
import com.castor.core.common.model.MessageSource
import com.castor.core.inference.InferenceEngine
import com.castor.core.inference.ModelManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central orchestrator that receives user input, classifies intent, detects compound
 * commands, routes to the appropriate agent(s), and returns a natural language response.
 *
 * Lifecycle of a single user request through [processInput]:
 * 1. Record the user turn in [ConversationManager].
 * 2. Detect if the input is a compound command (multi-step).
 *    a. If compound: decompose via [TaskPipeline], execute all steps, return summary.
 *    b. If simple: classify intent, route to the appropriate agent.
 * 3. Record the assistant response in [ConversationManager].
 * 4. Return the response string.
 *
 * The orchestrator supports both LLM-powered classification and keyword-based fallback
 * so that basic functionality is available even when no model is loaded.
 */
@Singleton
class AgentOrchestrator @Inject constructor(
    private val engine: InferenceEngine,
    private val modelManager: ModelManager,
    private val messagingAgent: MessagingAgent,
    private val mediaAgent: MediaAgent,
    private val reminderAgent: ReminderAgent,
    private val briefingAgent: BriefingAgent,
    private val taskPipeline: TaskPipeline,
    private val conversationManager: ConversationManager
) {

    companion object {
        private const val CLASSIFY_MAX_TOKENS = 128
        private const val PROCESS_MAX_TOKENS = 256

        private const val ROUTER_SYSTEM_PROMPT = """You are Un-Dios, a helpful AI assistant running on the user's Android phone.
You can help with:
- Messaging: Read and reply to WhatsApp and Teams messages
- Media: Control Spotify, YouTube, and Audible playback
- Reminders: Set and manage reminders and calendar events
- Briefings: Generate morning briefings and proactive suggestions
- General questions: Answer questions using your knowledge

Respond concisely and helpfully. If you need to take an action (like sending a message or playing music), describe what you would do."""

        private const val CLASSIFY_SYSTEM_PROMPT = """You are an intent classifier. Given user input, classify it into exactly one category.

Categories:
- SEND_MESSAGE: User wants to send, reply to, or compose a message (e.g., "text Mom", "reply to John", "tell Sarah I'm on my way")
- PLAY_MEDIA: User wants to play music, a video, a podcast, or an audiobook (e.g., "play some jazz", "put on a podcast")
- QUEUE_MEDIA: User wants to add media to a queue without playing immediately (e.g., "add this to my queue", "queue up the next episode")
- SET_REMINDER: User wants to set a reminder, alarm, or timer (e.g., "remind me at 5pm", "set an alarm for tomorrow")
- SUMMARIZE: User wants a summary of messages, conversations, or information (e.g., "summarize my unread messages", "what did I miss")
- BRIEFING: User wants their daily briefing or status overview (e.g., "give me my morning briefing", "what's my day look like")
- MEDIA_CONTROL: User wants to control playback without starting new media (e.g., "pause", "skip", "what's playing")
- REMINDER_QUERY: User is asking about existing reminders (e.g., "what are my reminders", "do I have a reminder about dentist")
- GENERAL_QUERY: Any other question or request that doesn't fit the above categories

Respond with ONLY the category name (e.g., "SEND_MESSAGE"), nothing else."""

        // ---------------------------------------------------------------------------------
        // Keyword mappings for fallback classification
        // ---------------------------------------------------------------------------------

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
        private val REMINDER_QUERY_KEYWORDS = listOf(
            "my reminders", "what reminders", "any reminders", "show reminders",
            "list reminders", "upcoming reminders", "reminders for today",
            "reminders this week", "do i have a reminder"
        )
        private val SUMMARIZE_KEYWORDS = listOf(
            "summarize", "summary", "what did i miss", "catch me up",
            "recap", "unread", "overview", "tldr", "tl;dr"
        )
        private val BRIEFING_KEYWORDS = listOf(
            "briefing", "morning briefing", "daily briefing", "my day",
            "what's my day", "what does my day look like", "status",
            "give me my briefing", "today's overview"
        )
        private val MEDIA_CONTROL_KEYWORDS = listOf(
            "pause", "stop playing", "skip", "next song", "next track",
            "previous", "go back", "what's playing", "what is playing",
            "now playing", "current song", "stop music"
        )

        // ---------------------------------------------------------------------------------
        // Error messages
        // ---------------------------------------------------------------------------------

        private const val MODEL_NOT_LOADED_MSG =
            "The language model is not loaded yet. I can still handle basic commands " +
            "using keyword matching, but for best results please ensure a .gguf model " +
            "file is in the models directory."

        private const val GENERAL_ERROR_MSG =
            "I encountered an issue processing your request. Please try again."
    }

    // -------------------------------------------------------------------------------------
    // Command history for lightweight in-memory tracking
    // -------------------------------------------------------------------------------------

    /**
     * In-memory record of recent commands for fast lookup (no DB round-trip).
     * Capped at [MAX_HISTORY_SIZE] entries.
     */
    private val commandHistory = mutableListOf<CommandRecord>()

    private data class CommandRecord(
        val input: String,
        val intent: AgentIntent?,
        val agentType: AgentType,
        val response: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private companion object HistoryConfig {
        const val MAX_HISTORY_SIZE = 50
    }

    // -------------------------------------------------------------------------------------
    // Public API — main entry points
    // -------------------------------------------------------------------------------------

    /**
     * Primary entry point: process user input end-to-end.
     *
     * 1. Records the user turn in conversation history.
     * 2. Checks for compound commands and delegates to [TaskPipeline] if needed.
     * 3. Otherwise classifies intent and routes to the appropriate agent.
     * 4. Records the assistant response in conversation history.
     * 5. Tracks the command in the in-memory history ring.
     *
     * @param input The raw user input string.
     * @return A natural language response suitable for displaying to the user.
     */
    suspend fun processInput(input: String): String {
        // Record user turn
        conversationManager.addUserTurn(input)

        val response: String
        val agentType: AgentType
        val intent: AgentIntent?

        try {
            // Step 1: Check for compound command
            if (taskPipeline.isCompoundCommand(input)) {
                val result = taskPipeline.decomposeAndExecute(input)
                response = result.summary
                agentType = if (result.steps.isNotEmpty()) result.steps.first().step.agentType else AgentType.GENERAL
                intent = null
            } else {
                // Step 2: Classify intent
                intent = classifyIntent(input)
                agentType = intentToAgentType(intent)

                // Step 3: Route to agent
                response = routeIntent(intent, input)
            }
        } catch (e: Exception) {
            // Fallback: generate a general response or return error message
            val fallbackResponse = try {
                if (engine.isLoaded) {
                    engine.generate(
                        prompt = input,
                        systemPrompt = ROUTER_SYSTEM_PROMPT,
                        maxTokens = PROCESS_MAX_TOKENS,
                        temperature = 0.7f
                    )
                } else {
                    GENERAL_ERROR_MSG
                }
            } catch (e2: Exception) {
                GENERAL_ERROR_MSG
            }

            // Record assistant response even on error
            conversationManager.addAssistantTurn(fallbackResponse, AgentType.GENERAL)
            trackCommand(input, null, AgentType.GENERAL, fallbackResponse)
            return fallbackResponse
        }

        // Record assistant response
        conversationManager.addAssistantTurn(response, agentType)
        trackCommand(input, intent, agentType, response)

        return response
    }

    /**
     * Classify the user's input into an [AgentIntent] using the LLM or keyword fallback.
     *
     * This is also exposed publicly so that UI layers can classify without executing,
     * e.g. to show intent chips or route to specialized UI.
     */
    suspend fun classifyIntent(input: String): AgentIntent {
        // Check for conversation context that might affect classification
        val contextPrompt = conversationManager.buildContextPrompt(limit = 3)

        val category = if (engine.isLoaded) {
            classifyWithLlm(input, contextPrompt)
        } else {
            classifyWithKeywords(input)
        }
        return mapCategoryToIntent(category, input)
    }

    /**
     * Process input with explicit conversation context override.
     * Useful when the caller has additional context not yet in the conversation history.
     */
    suspend fun processInputWithContext(input: String, additionalContext: String): String {
        val contextualInput = "$additionalContext\n\nUser: $input"
        return processInput(contextualInput)
    }

    // -------------------------------------------------------------------------------------
    // Agent accessors
    // -------------------------------------------------------------------------------------

    /**
     * Direct access to the messaging agent for UI components that need
     * messaging-specific features (smart replies, compose, etc.).
     */
    fun getMessagingAgent(): MessagingAgent = messagingAgent

    /**
     * Direct access to the media agent for UI components that need
     * media-specific features (play control, queue management, etc.).
     */
    fun getMediaAgent(): MediaAgent = mediaAgent

    /**
     * Direct access to the reminder agent for UI components that need
     * reminder-specific features (create, query, manage).
     */
    fun getReminderAgent(): ReminderAgent = reminderAgent

    /**
     * Direct access to the briefing agent for the home screen widget
     * and morning briefing features.
     */
    fun getBriefingAgent(): BriefingAgent = briefingAgent

    /**
     * Direct access to the task pipeline for programmatic multi-step execution.
     */
    fun getTaskPipeline(): TaskPipeline = taskPipeline

    /**
     * Direct access to the conversation manager for history management.
     */
    fun getConversationManager(): ConversationManager = conversationManager

    // -------------------------------------------------------------------------------------
    // History access
    // -------------------------------------------------------------------------------------

    /**
     * Get the in-memory command history (most recent first).
     */
    fun getRecentCommands(limit: Int = 10): List<CommandRecord> {
        return commandHistory.takeLast(limit).reversed()
    }

    /**
     * Clear in-memory command history and optionally the persistent conversation history.
     */
    suspend fun clearHistory(clearPersistent: Boolean = false) {
        commandHistory.clear()
        if (clearPersistent) {
            conversationManager.clearHistory()
        }
    }

    // -------------------------------------------------------------------------------------
    // Intent classification — LLM
    // -------------------------------------------------------------------------------------

    /**
     * Use the LLM to classify user input into an intent category string.
     * If conversation context is available, it is prepended to help with
     * follow-up and pronoun resolution.
     */
    private suspend fun classifyWithLlm(input: String, contextPrompt: String): String {
        return try {
            val fullPrompt = if (contextPrompt.isNotBlank()) {
                "$contextPrompt\nCurrent user input: $input"
            } else {
                input
            }

            val response = engine.generate(
                prompt = fullPrompt,
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
                response.contains("BRIEFING") -> "BRIEFING"
                response.contains("MEDIA_CONTROL") -> "MEDIA_CONTROL"
                response.contains("REMINDER_QUERY") -> "REMINDER_QUERY"
                else -> "GENERAL_QUERY"
            }
        } catch (e: Exception) {
            // LLM failed — fall back to keywords
            classifyWithKeywords(input)
        }
    }

    // -------------------------------------------------------------------------------------
    // Intent classification — keyword fallback
    // -------------------------------------------------------------------------------------

    /**
     * Fallback keyword-based intent classification when the LLM is not available.
     * Order matters for priority: more specific categories are checked first.
     */
    private fun classifyWithKeywords(input: String): String {
        val lowered = input.lowercase()

        // Briefing (check early — very specific keywords)
        if (BRIEFING_KEYWORDS.any { lowered.contains(it) }) return "BRIEFING"

        // Queue before play since "add to queue" contains no play keywords
        if (QUEUE_KEYWORDS.any { lowered.contains(it) }) return "QUEUE_MEDIA"

        // Media control (pause/skip/what's playing) before play
        if (MEDIA_CONTROL_KEYWORDS.any { lowered.contains(it) }) return "MEDIA_CONTROL"

        // Reminder queries before reminder creation
        if (REMINDER_QUERY_KEYWORDS.any { lowered.contains(it) }) return "REMINDER_QUERY"

        // Reminder creation
        if (REMINDER_KEYWORDS.any { lowered.contains(it) }) return "SET_REMINDER"

        // Messaging
        if (MESSAGE_KEYWORDS.any { lowered.contains(it) }) return "SEND_MESSAGE"

        // Summarize
        if (SUMMARIZE_KEYWORDS.any { lowered.contains(it) }) return "SUMMARIZE"

        // Play media (broad — checked last among specific intents)
        if (PLAY_KEYWORDS.any { lowered.contains(it) }) return "PLAY_MEDIA"

        return "GENERAL_QUERY"
    }

    // -------------------------------------------------------------------------------------
    // Intent mapping
    // -------------------------------------------------------------------------------------

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
            // Map extended categories back to base AgentIntent types
            "BRIEFING" -> AgentIntent.Summarize(
                context = input
            )
            "MEDIA_CONTROL" -> AgentIntent.PlayMedia(
                query = input,
                source = guessMediaSource(input)
            )
            "REMINDER_QUERY" -> AgentIntent.SetReminder(
                description = input,
                timeDescription = ""
            )
            else -> AgentIntent.GeneralQuery(
                query = input
            )
        }
    }

    /**
     * Map an [AgentIntent] to the [AgentType] that should handle it.
     */
    private fun intentToAgentType(intent: AgentIntent): AgentType {
        return when (intent) {
            is AgentIntent.SendMessage -> AgentType.MESSAGING
            is AgentIntent.PlayMedia -> AgentType.MEDIA
            is AgentIntent.QueueMedia -> AgentType.MEDIA
            is AgentIntent.SetReminder -> AgentType.REMINDER
            is AgentIntent.Summarize -> AgentType.MESSAGING
            is AgentIntent.GeneralQuery -> AgentType.GENERAL
        }
    }

    // -------------------------------------------------------------------------------------
    // Intent routing
    // -------------------------------------------------------------------------------------

    /**
     * Route a classified intent to the appropriate agent and generate a response.
     *
     * Each agent is invoked through its own method, ensuring clean separation of concerns.
     * The original input is passed alongside the intent so agents can work with the
     * full natural language rather than just the structured fields.
     */
    private suspend fun routeIntent(intent: AgentIntent, originalInput: String): String {
        val category = classifyWithKeywords(originalInput)

        return when (intent) {
            is AgentIntent.SendMessage -> {
                routeMessaging(intent, originalInput)
            }

            is AgentIntent.PlayMedia -> {
                // Distinguish between "play new media" and "control playback"
                if (category == "MEDIA_CONTROL") {
                    mediaAgent.handleCommand(originalInput)
                } else {
                    routePlayMedia(originalInput)
                }
            }

            is AgentIntent.QueueMedia -> {
                routeQueueMedia(originalInput)
            }

            is AgentIntent.SetReminder -> {
                // Distinguish between creating and querying reminders
                if (category == "REMINDER_QUERY") {
                    reminderAgent.handleReminderQuery(originalInput)
                } else {
                    routeSetReminder(originalInput)
                }
            }

            is AgentIntent.Summarize -> {
                // Distinguish between message summary and briefing
                if (category == "BRIEFING") {
                    routeBriefing()
                } else {
                    routeSummarize(originalInput)
                }
            }

            is AgentIntent.GeneralQuery -> {
                routeGeneral(originalInput)
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Route handlers — one per agent domain
    // -------------------------------------------------------------------------------------

    /**
     * Route to MessagingAgent: compose a message for the extracted recipient.
     */
    private suspend fun routeMessaging(intent: AgentIntent.SendMessage, input: String): String {
        return try {
            val composed = messagingAgent.composeMessage(input)
            "Draft message for ${intent.recipient}: \"$composed\""
        } catch (e: Exception) {
            "I'd like to help with that message, but encountered an error. Please try again."
        }
    }

    /**
     * Route to MediaAgent for play commands.
     */
    private suspend fun routePlayMedia(input: String): String {
        return try {
            mediaAgent.handleCommand(input)
        } catch (e: Exception) {
            "I'd like to play that for you, but encountered an error. Please try again."
        }
    }

    /**
     * Route to MediaAgent for queue commands.
     */
    private suspend fun routeQueueMedia(input: String): String {
        return try {
            val command = mediaAgent.parseMediaCommand(input)
            // Override action to QUEUE regardless of what was parsed
            val queueCommand = command.copy(action = MediaAction.QUEUE)
            mediaAgent.describeAction(queueCommand)
        } catch (e: Exception) {
            "I'd like to add that to the queue, but encountered an error. Please try again."
        }
    }

    /**
     * Route to ReminderAgent for creating new reminders.
     */
    private suspend fun routeSetReminder(input: String): String {
        return try {
            reminderAgent.handleReminder(input)
        } catch (e: Exception) {
            "I'd like to set that reminder, but encountered an error. Please try again."
        }
    }

    /**
     * Route to BriefingAgent for morning briefing / daily overview.
     */
    private suspend fun routeBriefing(): String {
        return try {
            val briefing = briefingAgent.generateMorningBriefing()
            buildString {
                appendLine(briefing.greeting)
                appendLine()
                appendLine("Messages: ${briefing.messageSummary}")
                appendLine("Reminders: ${briefing.reminderSummary}")
                appendLine("Calendar: ${briefing.calendarSummary}")
                if (briefing.mediaSuggestion != null) {
                    appendLine("Media: ${briefing.mediaSuggestion}")
                }
            }.trim()
        } catch (e: Exception) {
            "I was unable to generate your briefing. Please try again."
        }
    }

    /**
     * Route summarize intents. Uses conversation context when available, otherwise
     * falls back to the general engine.
     */
    private suspend fun routeSummarize(input: String): String {
        return try {
            if (engine.isLoaded) {
                val context = conversationManager.buildContextPrompt(limit = 5)
                val prompt = if (context.isNotBlank()) {
                    "$context\n\nUser request: $input"
                } else {
                    input
                }
                engine.generate(
                    prompt = prompt,
                    systemPrompt = ROUTER_SYSTEM_PROMPT,
                    maxTokens = PROCESS_MAX_TOKENS,
                    temperature = 0.5f
                )
            } else {
                "I can help with summaries, but the language model is not currently loaded. " +
                "Please load a model first."
            }
        } catch (e: Exception) {
            "I was unable to generate that summary. Please try again."
        }
    }

    /**
     * Route general queries to the LLM with full conversation context.
     */
    private suspend fun routeGeneral(input: String): String {
        return try {
            if (engine.isLoaded) {
                val context = conversationManager.buildContextPrompt(limit = 5)
                val prompt = if (context.isNotBlank()) {
                    "$context\n\nUser: $input"
                } else {
                    input
                }
                engine.generate(
                    prompt = prompt,
                    systemPrompt = ROUTER_SYSTEM_PROMPT,
                    maxTokens = PROCESS_MAX_TOKENS,
                    temperature = 0.7f
                )
            } else {
                MODEL_NOT_LOADED_MSG
            }
        } catch (e: Exception) {
            GENERAL_ERROR_MSG
        }
    }

    // -------------------------------------------------------------------------------------
    // History tracking
    // -------------------------------------------------------------------------------------

    /**
     * Track a command in the in-memory history ring buffer.
     */
    private fun trackCommand(
        input: String,
        intent: AgentIntent?,
        agentType: AgentType,
        response: String
    ) {
        commandHistory.add(
            CommandRecord(
                input = input,
                intent = intent,
                agentType = agentType,
                response = response
            )
        )
        // Trim to max size
        while (commandHistory.size > MAX_HISTORY_SIZE) {
            commandHistory.removeAt(0)
        }
    }

    // -------------------------------------------------------------------------------------
    // Extraction helpers
    // -------------------------------------------------------------------------------------

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
