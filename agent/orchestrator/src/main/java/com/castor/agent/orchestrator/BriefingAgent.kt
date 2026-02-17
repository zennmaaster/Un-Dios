package com.castor.agent.orchestrator

import com.castor.core.data.db.dao.MediaQueueDao
import com.castor.core.data.db.dao.ReminderDao
import com.castor.core.data.db.entity.MediaQueueEntity
import com.castor.core.data.db.entity.ReminderEntity
import com.castor.core.data.repository.MessageRepository
import com.castor.core.inference.InferenceEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// =====================================================================================
// Data models
// =====================================================================================

/**
 * A complete morning briefing containing summaries across all agent domains.
 *
 * @param greeting Time-of-day greeting (e.g. "Good morning, here's your day:")
 * @param calendarSummary Overview of today's calendar events
 * @param messageSummary Overview of unread messages and senders
 * @param reminderSummary Overview of pending reminders due today
 * @param mediaSuggestion Optional suggestion to continue media playback
 * @param generatedAt Timestamp when the briefing was generated
 */
data class Briefing(
    val greeting: String,
    val calendarSummary: String,
    val messageSummary: String,
    val reminderSummary: String,
    val mediaSuggestion: String?,
    val generatedAt: Long = System.currentTimeMillis()
)

/**
 * A proactive suggestion surfaced by the briefing agent based on current state.
 *
 * @param type The category of the suggestion
 * @param title Short headline for the suggestion
 * @param description Longer explanation or context
 * @param actionLabel Label for the action button (e.g. "Open", "Dismiss")
 * @param actionData Intent data or deeplink for executing the action
 */
data class ProactiveSuggestion(
    val type: SuggestionType,
    val title: String,
    val description: String,
    val actionLabel: String,
    val actionData: String
)

/**
 * Categories of proactive suggestions.
 */
enum class SuggestionType {
    UPCOMING_EVENT,
    UNREAD_MESSAGES,
    PENDING_REMINDER,
    CONTINUE_MEDIA,
    GENERAL
}

/**
 * Compact status snapshot for the home screen card widget.
 *
 * @param unreadMessages Total number of unread messages across all sources
 * @param upcomingReminders Number of reminders due in the next 24 hours
 * @param nextEventIn Human-readable string for the soonest upcoming event (e.g. "Meeting in 15 min")
 * @param nowPlaying Title of the currently-playing or most recently queued media item
 */
data class QuickStatus(
    val unreadMessages: Int = 0,
    val upcomingReminders: Int = 0,
    val nextEventIn: String? = null,
    val nowPlaying: String? = null
)

// =====================================================================================
// BriefingAgent
// =====================================================================================

/**
 * Generates daily briefings and proactive suggestions by aggregating data from
 * messaging, reminders, and media repositories.
 *
 * If the on-device LLM ([InferenceEngine]) is loaded, the agent generates
 * natural-language summaries. Otherwise it falls back to structured, template-based
 * briefings that require no inference.
 *
 * Usage:
 * - Call [generateMorningBriefing] once per day (typically via [BriefingWorker])
 * - Call [generateSuggestions] periodically to surface contextual actions
 * - Call [getQuickStatus] for the home screen status card
 */
@Singleton
class BriefingAgent @Inject constructor(
    private val engine: InferenceEngine,
    private val messageRepository: MessageRepository,
    private val reminderDao: ReminderDao,
    private val mediaQueueDao: MediaQueueDao
) {

    companion object {
        private const val BRIEFING_MAX_TOKENS = 512
        private const val SUGGESTION_MAX_TOKENS = 256

        private const val BRIEFING_SYSTEM_PROMPT = """You are Un-Dios, a personal AI assistant running on the user's Android phone.
Generate a concise, helpful morning briefing based on the data provided.
Use a warm but efficient tone. Keep each section to 1-2 sentences.
Do NOT use markdown formatting — output plain text only.
Structure your response exactly as:
GREETING: [time-appropriate greeting]
CALENDAR: [calendar summary]
MESSAGES: [message summary]
REMINDERS: [reminder summary]
MEDIA: [media suggestion or "No media in queue"]"""

        private const val SUGGESTION_SYSTEM_PROMPT = """You are Un-Dios, a personal AI assistant.
Based on the user's current state data, generate 1-3 brief, actionable suggestions.
Each suggestion should be a single sentence.
Output each suggestion on its own line, prefixed with the category in brackets:
[MESSAGES] suggestion text
[REMINDER] suggestion text
[MEDIA] suggestion text
[GENERAL] suggestion text"""
    }

    // -------------------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------------------

    /**
     * Generate a full morning briefing by collecting data from all repositories
     * and either using the LLM for natural language or falling back to templates.
     */
    suspend fun generateMorningBriefing(): Briefing {
        val unreadMessages = collectUnreadMessages()
        val upcomingReminders = collectUpcomingReminders()
        val currentMedia = collectCurrentMedia()

        return if (engine.isLoaded) {
            generateLlmBriefing(unreadMessages, upcomingReminders, currentMedia)
        } else {
            generateStructuredBriefing(unreadMessages, upcomingReminders, currentMedia)
        }
    }

    /**
     * Generate contextual suggestions based on current app state.
     * Returns a list of actionable suggestions the user can tap to act on.
     */
    suspend fun generateSuggestions(): List<ProactiveSuggestion> {
        val suggestions = mutableListOf<ProactiveSuggestion>()

        // --- Unread messages ---
        try {
            val unreadMessages = messageRepository.getUnreadMessages().first()
            if (unreadMessages.isNotEmpty()) {
                val senders = unreadMessages.map { it.sender }.distinct()
                val sendersText = when {
                    senders.size == 1 -> senders.first()
                    senders.size <= 3 -> senders.dropLast(1).joinToString(", ") + " & " + senders.last()
                    else -> "${senders.take(2).joinToString(", ")} & ${senders.size - 2} others"
                }
                suggestions.add(
                    ProactiveSuggestion(
                        type = SuggestionType.UNREAD_MESSAGES,
                        title = "${unreadMessages.size} unread",
                        description = "From $sendersText",
                        actionLabel = "View",
                        actionData = "castor://messages/unread"
                    )
                )
            }
        } catch (_: Exception) {
            // MessageRepository unavailable — skip
        }

        // --- Upcoming reminders ---
        try {
            val now = System.currentTimeMillis()
            val reminders = reminderDao.getUpcomingReminders(now).first()
            for (reminder in reminders.take(2)) {
                val timeUntil = formatTimeUntil(reminder.triggerTimeMs - now)
                suggestions.add(
                    ProactiveSuggestion(
                        type = SuggestionType.PENDING_REMINDER,
                        title = truncate(reminder.description, 32),
                        description = "Due $timeUntil",
                        actionLabel = "View",
                        actionData = "castor://reminders/${reminder.id}"
                    )
                )
            }
        } catch (_: Exception) {
            // ReminderDao unavailable — skip
        }

        // --- Continue media ---
        try {
            val currentItem = mediaQueueDao.getCurrentItem()
            if (currentItem != null) {
                val artistText = if (currentItem.artist != null) " by ${currentItem.artist}" else ""
                suggestions.add(
                    ProactiveSuggestion(
                        type = SuggestionType.CONTINUE_MEDIA,
                        title = "Continue listening",
                        description = "${currentItem.title}$artistText",
                        actionLabel = "Play",
                        actionData = "castor://media/play/${currentItem.id}"
                    )
                )
            }
        } catch (_: Exception) {
            // MediaQueueDao unavailable — skip
        }

        // --- LLM-powered general suggestion ---
        if (engine.isLoaded && suggestions.isEmpty()) {
            try {
                val llmSuggestion = generateLlmSuggestion()
                if (llmSuggestion != null) {
                    suggestions.add(llmSuggestion)
                }
            } catch (_: Exception) {
                // LLM generation failed — no general suggestion
            }
        }

        return suggestions
    }

    /**
     * Compact status snapshot for the home screen card.
     * This is designed to be cheap to compute (no LLM required).
     */
    suspend fun getQuickStatus(): QuickStatus {
        val unreadCount = try {
            messageRepository.getUnreadCount().first()
        } catch (_: Exception) {
            0
        }

        val now = System.currentTimeMillis()
        val reminderCount: Int
        val nextReminderText: String?

        try {
            val reminders = reminderDao.getUpcomingReminders(now).first()
            reminderCount = reminders.size
            val nextReminder = reminders.firstOrNull()
            nextReminderText = if (nextReminder != null) {
                val timeUntil = formatTimeUntil(nextReminder.triggerTimeMs - now)
                "${truncate(nextReminder.description, 20)} $timeUntil"
            } else {
                null
            }
        } catch (_: Exception) {
            return QuickStatus(
                unreadMessages = unreadCount,
                upcomingReminders = 0,
                nextEventIn = null,
                nowPlaying = null
            )
        }

        val nowPlayingText = try {
            val currentItem = mediaQueueDao.getCurrentItem()
            if (currentItem != null) {
                val artistText = if (currentItem.artist != null) " - ${currentItem.artist}" else ""
                "${currentItem.title}$artistText"
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

        return QuickStatus(
            unreadMessages = unreadCount,
            upcomingReminders = reminderCount,
            nextEventIn = nextReminderText,
            nowPlaying = nowPlayingText
        )
    }

    // -------------------------------------------------------------------------------------
    // Data collection
    // -------------------------------------------------------------------------------------

    private data class UnreadSummary(
        val totalCount: Int,
        val senders: List<String>,
        val sourceCounts: Map<String, Int>
    )

    private data class ReminderSummary(
        val reminders: List<ReminderEntity>,
        val todayCount: Int,
        val nextReminder: ReminderEntity?
    )

    private data class MediaSummary(
        val currentItem: MediaQueueEntity?,
        val queueSize: Int
    )

    private suspend fun collectUnreadMessages(): UnreadSummary {
        return try {
            val unread = messageRepository.getUnreadMessages().first()
            val senders = unread.map { it.sender }.distinct()
            val sourceCounts = unread.groupBy { it.source.name }.mapValues { it.value.size }
            UnreadSummary(
                totalCount = unread.size,
                senders = senders,
                sourceCounts = sourceCounts
            )
        } catch (_: Exception) {
            UnreadSummary(totalCount = 0, senders = emptyList(), sourceCounts = emptyMap())
        }
    }

    private suspend fun collectUpcomingReminders(): ReminderSummary {
        return try {
            val now = System.currentTimeMillis()
            val reminders = reminderDao.getUpcomingReminders(now).first()

            // Filter to today only
            val endOfDay = getEndOfDayMs()
            val todayReminders = reminders.filter { it.triggerTimeMs <= endOfDay }

            ReminderSummary(
                reminders = reminders,
                todayCount = todayReminders.size,
                nextReminder = reminders.firstOrNull()
            )
        } catch (_: Exception) {
            ReminderSummary(reminders = emptyList(), todayCount = 0, nextReminder = null)
        }
    }

    private suspend fun collectCurrentMedia(): MediaSummary {
        return try {
            val currentItem = mediaQueueDao.getCurrentItem()
            val queueSize = mediaQueueDao.getCurrentQueue().size
            MediaSummary(currentItem = currentItem, queueSize = queueSize)
        } catch (_: Exception) {
            MediaSummary(currentItem = null, queueSize = 0)
        }
    }

    // -------------------------------------------------------------------------------------
    // LLM-powered briefing
    // -------------------------------------------------------------------------------------

    private suspend fun generateLlmBriefing(
        messages: UnreadSummary,
        reminders: ReminderSummary,
        media: MediaSummary
    ): Briefing {
        val dataPrompt = buildString {
            appendLine("Current time: ${formatCurrentTime()}")
            appendLine("Time of day: ${getTimeOfDay()}")
            appendLine()
            appendLine("=== Messages ===")
            if (messages.totalCount > 0) {
                appendLine("Unread: ${messages.totalCount}")
                appendLine("From: ${messages.senders.joinToString(", ")}")
                appendLine("Sources: ${messages.sourceCounts.entries.joinToString(", ") { "${it.key}: ${it.value}" }}")
            } else {
                appendLine("No unread messages.")
            }
            appendLine()
            appendLine("=== Reminders ===")
            if (reminders.todayCount > 0) {
                appendLine("Due today: ${reminders.todayCount}")
                for (r in reminders.reminders.take(5)) {
                    val time = formatTimeMs(r.triggerTimeMs)
                    appendLine("  - $time: ${r.description}")
                }
            } else {
                appendLine("No reminders due today.")
            }
            appendLine()
            appendLine("=== Media Queue ===")
            if (media.currentItem != null) {
                val artistText = if (media.currentItem.artist != null) " by ${media.currentItem.artist}" else ""
                appendLine("Current/next: ${media.currentItem.title}$artistText")
                appendLine("Queue size: ${media.queueSize} items")
            } else {
                appendLine("Queue is empty.")
            }
        }

        return try {
            val response = engine.generate(
                prompt = dataPrompt,
                systemPrompt = BRIEFING_SYSTEM_PROMPT,
                maxTokens = BRIEFING_MAX_TOKENS,
                temperature = 0.5f
            )
            parseLlmBriefing(response, media)
        } catch (_: Exception) {
            // Fall back to structured briefing on LLM failure
            generateStructuredBriefing(messages, reminders, media)
        }
    }

    /**
     * Parse the LLM output into a [Briefing]. The LLM is prompted to use a
     * labelled format (GREETING:, CALENDAR:, etc.) which we split on.
     * Missing sections are filled with fallback text.
     */
    private fun parseLlmBriefing(response: String, media: MediaSummary): Briefing {
        val lines = response.lines()
        var greeting = ""
        var calendar = ""
        var messages = ""
        var reminders = ""
        var mediaText: String? = null

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("GREETING:", ignoreCase = true) ->
                    greeting = trimmed.substringAfter(":").trim()
                trimmed.startsWith("CALENDAR:", ignoreCase = true) ->
                    calendar = trimmed.substringAfter(":").trim()
                trimmed.startsWith("MESSAGES:", ignoreCase = true) ->
                    messages = trimmed.substringAfter(":").trim()
                trimmed.startsWith("REMINDERS:", ignoreCase = true) ||
                        trimmed.startsWith("REMINDER:", ignoreCase = true) ->
                    reminders = trimmed.substringAfter(":").trim()
                trimmed.startsWith("MEDIA:", ignoreCase = true) -> {
                    val text = trimmed.substringAfter(":").trim()
                    mediaText = text.takeIf {
                        it.isNotBlank() &&
                                !it.contains("no media", ignoreCase = true) &&
                                !it.contains("queue is empty", ignoreCase = true)
                    }
                }
            }
        }

        // Fallbacks
        if (greeting.isBlank()) greeting = getGreeting()
        if (calendar.isBlank()) calendar = "No calendar data available."
        if (messages.isBlank()) messages = "No unread messages."
        if (reminders.isBlank()) reminders = "No reminders due today."
        if (mediaText == null && media.currentItem != null) {
            val artistText = if (media.currentItem.artist != null) " by ${media.currentItem.artist}" else ""
            mediaText = "Continue: ${media.currentItem.title}$artistText"
        }

        return Briefing(
            greeting = greeting,
            calendarSummary = calendar,
            messageSummary = messages,
            reminderSummary = reminders,
            mediaSuggestion = mediaText
        )
    }

    // -------------------------------------------------------------------------------------
    // Structured (no-LLM) briefing
    // -------------------------------------------------------------------------------------

    private fun generateStructuredBriefing(
        messages: UnreadSummary,
        reminders: ReminderSummary,
        media: MediaSummary
    ): Briefing {
        // Greeting
        val greeting = getGreeting()

        // Messages summary
        val messageSummary = if (messages.totalCount == 0) {
            "All caught up. No unread messages."
        } else {
            val sendersText = when {
                messages.senders.size == 1 -> messages.senders.first()
                messages.senders.size <= 3 ->
                    messages.senders.dropLast(1).joinToString(", ") + " & " + messages.senders.last()
                else ->
                    "${messages.senders.take(2).joinToString(", ")} & ${messages.senders.size - 2} others"
            }
            val sourceText = messages.sourceCounts.entries.joinToString(", ") { "${it.value} ${it.key}" }
            "${messages.totalCount} unread from $sendersText ($sourceText)"
        }

        // Reminders summary
        val reminderSummary = when {
            reminders.todayCount == 0 -> "No reminders due today."
            reminders.todayCount == 1 && reminders.nextReminder != null -> {
                val time = formatTimeMs(reminders.nextReminder.triggerTimeMs)
                "1 reminder: ${reminders.nextReminder.description} at $time"
            }
            else -> {
                val first = reminders.nextReminder
                val firstText = if (first != null) {
                    "Next: ${first.description} at ${formatTimeMs(first.triggerTimeMs)}"
                } else ""
                "${reminders.todayCount} reminders today. $firstText"
            }
        }

        // Calendar summary (placeholder until CalendarRepository is available)
        val calendarSummary = "Calendar sync not yet available."

        // Media suggestion
        val mediaSuggestion = if (media.currentItem != null) {
            val artistText = if (media.currentItem.artist != null) " by ${media.currentItem.artist}" else ""
            "Continue: ${media.currentItem.title}$artistText" +
                    if (media.queueSize > 1) " (+${media.queueSize - 1} in queue)" else ""
        } else {
            null
        }

        return Briefing(
            greeting = greeting,
            calendarSummary = calendarSummary,
            messageSummary = messageSummary,
            reminderSummary = reminderSummary,
            mediaSuggestion = mediaSuggestion
        )
    }

    // -------------------------------------------------------------------------------------
    // LLM-powered suggestion
    // -------------------------------------------------------------------------------------

    private suspend fun generateLlmSuggestion(): ProactiveSuggestion? {
        val prompt = "The user has no pending messages, reminders, or media queued. " +
                "Suggest one brief, helpful tip or action they could take."

        return try {
            val response = engine.generate(
                prompt = prompt,
                systemPrompt = SUGGESTION_SYSTEM_PROMPT,
                maxTokens = SUGGESTION_MAX_TOKENS,
                temperature = 0.8f
            ).trim()

            if (response.isNotBlank()) {
                ProactiveSuggestion(
                    type = SuggestionType.GENERAL,
                    title = "Suggestion",
                    description = response.lines().firstOrNull()?.replace(Regex("^\\[\\w+]\\s*"), "") ?: response,
                    actionLabel = "Got it",
                    actionData = "castor://dismiss"
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    // -------------------------------------------------------------------------------------
    // Formatting helpers
    // -------------------------------------------------------------------------------------

    private fun getTimeOfDay(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "morning"
            hour < 17 -> "afternoon"
            else -> "evening"
        }
    }

    private fun getGreeting(): String {
        val timeOfDay = getTimeOfDay()
        return when (timeOfDay) {
            "morning" -> "Good morning. Here's your day:"
            "afternoon" -> "Good afternoon. Here's your update:"
            else -> "Good evening. Here's your summary:"
        }
    }

    private fun formatCurrentTime(): String {
        val sdf = SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun formatTimeMs(timeMs: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timeMs))
    }

    private fun formatTimeUntil(deltaMs: Long): String {
        if (deltaMs <= 0) return "now"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(deltaMs)
        val hours = TimeUnit.MILLISECONDS.toHours(deltaMs)
        return when {
            minutes < 1 -> "in less than a minute"
            minutes < 60 -> "in ${minutes}min"
            hours < 24 -> "in ${hours}h ${minutes % 60}min"
            else -> {
                val days = TimeUnit.MILLISECONDS.toDays(deltaMs)
                "in ${days}d"
            }
        }
    }

    private fun getEndOfDayMs(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    private fun truncate(text: String, maxLength: Int): String {
        return if (text.length <= maxLength) text else text.take(maxLength - 1) + "\u2026"
    }
}
