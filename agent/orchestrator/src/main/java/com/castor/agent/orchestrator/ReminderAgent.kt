package com.castor.agent.orchestrator

import com.castor.core.data.repository.Reminder
import com.castor.core.data.repository.ReminderRepository
import com.castor.core.inference.InferenceEngine
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// =========================================================================================
// Data models
// =========================================================================================

/**
 * Parsed command to create or modify a reminder, extracted from natural language input.
 *
 * @param description    What the reminder is about (e.g. "call Mom").
 * @param timeDescription The raw time expression (e.g. "5pm tomorrow", "in 30 minutes").
 * @param isRecurring    Whether the user indicated a repeating schedule.
 * @param recurrencePattern The natural language recurrence pattern (e.g. "every Monday").
 */
data class ReminderCommand(
    val description: String,
    val timeDescription: String,
    val isRecurring: Boolean,
    val recurrencePattern: String?
)

/**
 * Types of reminder queries the user can ask.
 */
enum class ReminderQueryType {
    /** Reminders due today. */
    TODAY,
    /** Reminders due within the next 7 days. */
    THIS_WEEK,
    /** All active (non-completed) reminders. */
    ALL,
    /** Search for reminders matching a specific term. */
    SPECIFIC
}

/**
 * Parsed query about existing reminders.
 *
 * @param queryType  The scope of the query.
 * @param searchTerm An optional search term for [ReminderQueryType.SPECIFIC] queries.
 */
data class ReminderQuery(
    val queryType: ReminderQueryType,
    val searchTerm: String?
)

// =========================================================================================
// ReminderAgent
// =========================================================================================

/**
 * Dedicated agent for handling reminder-related intents: creating reminders, querying
 * upcoming reminders, and describing reminder state to the user.
 *
 * When the on-device LLM is loaded, the agent uses it for structured extraction and
 * natural language description. When the LLM is unavailable, keyword/regex-based parsing
 * and template responses provide full offline functionality.
 *
 * Usage:
 * - Call [parseReminderCommand] to extract a [ReminderCommand] from free-form input.
 * - Call [parseReminderQuery] to determine what reminders the user is asking about.
 * - Call [handleReminder] for a one-shot parse + describe experience.
 * - Call [handleReminderQuery] for a one-shot query + response.
 */
@Singleton
class ReminderAgent @Inject constructor(
    private val engine: InferenceEngine,
    private val reminderRepository: ReminderRepository
) {

    companion object {
        private const val PARSE_MAX_TOKENS = 192
        private const val DESCRIBE_MAX_TOKENS = 128
        private const val QUERY_PARSE_MAX_TOKENS = 128

        private const val PARSE_SYSTEM_PROMPT = """You are a reminder parser for an Android assistant called Un-Dios.
Given user input, extract the reminder fields and respond in EXACTLY this format (one field per line, no extra text):

DESCRIPTION: <what to be reminded about>
TIME: <when the reminder should fire, e.g. "5pm tomorrow", "in 30 minutes", "next Monday at 9am">
RECURRING: <YES or NO>
PATTERN: <recurrence pattern like "every Monday", "daily at 8am", or NONE>

Rules:
- "Remind me to call Mom at 5pm tomorrow" -> DESCRIPTION=call Mom, TIME=5pm tomorrow, RECURRING=NO, PATTERN=NONE
- "Set a daily reminder to take vitamins at 8am" -> DESCRIPTION=take vitamins, TIME=8am, RECURRING=YES, PATTERN=daily at 8am
- "Remind me every Monday to submit the report" -> DESCRIPTION=submit the report, TIME=Monday, RECURRING=YES, PATTERN=every Monday
- Always extract the core action for DESCRIPTION (strip "remind me to", "don't forget to", etc.)"""

        private const val QUERY_PARSE_SYSTEM_PROMPT = """You are a reminder query parser. Given user input asking about reminders, classify the query.
Respond in EXACTLY this format:

QUERY_TYPE: <TODAY|THIS_WEEK|ALL|SPECIFIC>
SEARCH_TERM: <search term, or NONE>

Rules:
- "What are my reminders for today?" -> QUERY_TYPE=TODAY, SEARCH_TERM=NONE
- "Any reminders this week?" -> QUERY_TYPE=THIS_WEEK, SEARCH_TERM=NONE
- "Show all my reminders" -> QUERY_TYPE=ALL, SEARCH_TERM=NONE
- "Do I have a reminder about the dentist?" -> QUERY_TYPE=SPECIFIC, SEARCH_TERM=dentist"""

        private const val DESCRIBE_SYSTEM_PROMPT = """You are Un-Dios, a helpful AI assistant on the user's Android phone.
Confirm the reminder action in a single concise, friendly sentence.
Do not use markdown. Do not add any preamble."""

        // Keyword sets for fallback parsing
        private val TODAY_KEYWORDS = listOf(
            "today", "today's", "for today", "due today"
        )
        private val WEEK_KEYWORDS = listOf(
            "this week", "week", "upcoming", "next few days"
        )
        private val ALL_KEYWORDS = listOf(
            "all reminders", "all my reminders", "every reminder", "show all"
        )
        private val RECURRING_KEYWORDS = listOf(
            "every", "daily", "weekly", "monthly", "each", "recurring"
        )

        // Time-related extraction patterns
        private val TIME_PATTERNS = listOf(
            Regex("""(?:at|by|around|before|after)\s+(\d{1,2}(?::\d{2})?\s*(?:am|pm)?\s*(?:today|tomorrow|tonight|next\s+\w+)?)""", RegexOption.IGNORE_CASE),
            Regex("""(in\s+\d+\s+(?:minutes?|mins?|hours?|hrs?|days?))""", RegexOption.IGNORE_CASE),
            Regex("""(tomorrow|tonight|today|next\s+\w+)(?:\s+(?:at|around)\s+(\d{1,2}(?::\d{2})?\s*(?:am|pm)?))?""", RegexOption.IGNORE_CASE),
            Regex("""(\d{1,2}(?::\d{2})?\s*(?:am|pm))""", RegexOption.IGNORE_CASE)
        )

        // Patterns for stripping reminder preamble to get the core description
        private val DESCRIPTION_STRIP_PATTERNS = listOf(
            Regex("""^(?:remind\s+me\s+to|remind\s+me\s+about|remind\s+me|set\s+(?:a\s+)?reminder\s+(?:to|for|about)?|don't\s+forget\s+to|don't\s+forget\s+about)\s*""", RegexOption.IGNORE_CASE)
        )
    }

    // -------------------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------------------

    /**
     * Parse a natural language reminder creation request into a [ReminderCommand].
     */
    suspend fun parseReminderCommand(input: String): ReminderCommand {
        return if (engine.isLoaded) {
            parseCommandWithLlm(input)
        } else {
            parseCommandWithKeywords(input)
        }
    }

    /**
     * Parse a natural language reminder query into a [ReminderQuery].
     */
    suspend fun parseReminderQuery(input: String): ReminderQuery {
        return if (engine.isLoaded) {
            parseQueryWithLlm(input)
        } else {
            parseQueryWithKeywords(input)
        }
    }

    /**
     * One-shot: parse the input as a reminder creation command and return a user-facing
     * confirmation response.
     */
    suspend fun handleReminder(input: String): String {
        val command = parseReminderCommand(input)
        return describeReminderAction(command)
    }

    /**
     * One-shot: parse the input as a reminder query, fetch matching reminders, and
     * return a formatted response.
     */
    suspend fun handleReminderQuery(input: String): String {
        val query = parseReminderQuery(input)
        return executeQuery(query)
    }

    /**
     * Describe the reminder action being taken, suitable for displaying to the user.
     */
    suspend fun describeReminderAction(command: ReminderCommand): String {
        if (!engine.isLoaded) {
            return describeWithTemplate(command)
        }

        val prompt = buildString {
            append("Confirm this reminder: \"${command.description}\"")
            append(" scheduled for ${command.timeDescription}")
            if (command.isRecurring && command.recurrencePattern != null) {
                append(", repeating ${command.recurrencePattern}")
            }
        }

        return try {
            engine.generate(
                prompt = prompt,
                systemPrompt = DESCRIBE_SYSTEM_PROMPT,
                maxTokens = DESCRIBE_MAX_TOKENS,
                temperature = 0.5f
            ).trim()
        } catch (e: Exception) {
            describeWithTemplate(command)
        }
    }

    // -------------------------------------------------------------------------------------
    // LLM-based parsing — command
    // -------------------------------------------------------------------------------------

    private suspend fun parseCommandWithLlm(input: String): ReminderCommand {
        return try {
            val response = engine.generate(
                prompt = input,
                systemPrompt = PARSE_SYSTEM_PROMPT,
                maxTokens = PARSE_MAX_TOKENS,
                temperature = 0.1f
            )
            parseLlmCommandResponse(response, input)
        } catch (e: Exception) {
            parseCommandWithKeywords(input)
        }
    }

    private fun parseLlmCommandResponse(response: String, originalInput: String): ReminderCommand {
        val lines = response.lines().associate { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                parts[0].trim().uppercase() to parts[1].trim()
            } else {
                "" to ""
            }
        }

        val description = lines["DESCRIPTION"]
            ?.takeIf { it.isNotBlank() && it.uppercase() != "NONE" }
            ?: extractDescription(originalInput)

        val time = lines["TIME"]
            ?.takeIf { it.isNotBlank() && it.uppercase() != "NONE" }
            ?: extractTimeDescription(originalInput)

        val recurringStr = lines["RECURRING"]?.uppercase() ?: "NO"
        val isRecurring = recurringStr == "YES" || recurringStr == "TRUE"

        val pattern = lines["PATTERN"]
            ?.takeIf { it.isNotBlank() && it.uppercase() != "NONE" }

        return ReminderCommand(
            description = description,
            timeDescription = time,
            isRecurring = isRecurring,
            recurrencePattern = pattern
        )
    }

    // -------------------------------------------------------------------------------------
    // LLM-based parsing — query
    // -------------------------------------------------------------------------------------

    private suspend fun parseQueryWithLlm(input: String): ReminderQuery {
        return try {
            val response = engine.generate(
                prompt = input,
                systemPrompt = QUERY_PARSE_SYSTEM_PROMPT,
                maxTokens = QUERY_PARSE_MAX_TOKENS,
                temperature = 0.1f
            )
            parseLlmQueryResponse(response, input)
        } catch (e: Exception) {
            parseQueryWithKeywords(input)
        }
    }

    private fun parseLlmQueryResponse(response: String, originalInput: String): ReminderQuery {
        val lines = response.lines().associate { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                parts[0].trim().uppercase() to parts[1].trim()
            } else {
                "" to ""
            }
        }

        val queryTypeStr = lines["QUERY_TYPE"]?.uppercase()
        val queryType = try {
            ReminderQueryType.valueOf(queryTypeStr ?: "TODAY")
        } catch (e: IllegalArgumentException) {
            ReminderQueryType.TODAY
        }

        val searchTerm = lines["SEARCH_TERM"]
            ?.takeIf { it.isNotBlank() && it.uppercase() != "NONE" }

        return ReminderQuery(
            queryType = queryType,
            searchTerm = searchTerm
        )
    }

    // -------------------------------------------------------------------------------------
    // Keyword-based fallback parsing
    // -------------------------------------------------------------------------------------

    private fun parseCommandWithKeywords(input: String): ReminderCommand {
        val description = extractDescription(input)
        val timeDescription = extractTimeDescription(input)
        val lowered = input.lowercase()
        val isRecurring = RECURRING_KEYWORDS.any { lowered.contains(it) }
        val recurrencePattern = if (isRecurring) extractRecurrencePattern(input) else null

        return ReminderCommand(
            description = description,
            timeDescription = timeDescription,
            isRecurring = isRecurring,
            recurrencePattern = recurrencePattern
        )
    }

    private fun parseQueryWithKeywords(input: String): ReminderQuery {
        val lowered = input.lowercase()

        val queryType = when {
            ALL_KEYWORDS.any { lowered.contains(it) } -> ReminderQueryType.ALL
            WEEK_KEYWORDS.any { lowered.contains(it) } -> ReminderQueryType.THIS_WEEK
            TODAY_KEYWORDS.any { lowered.contains(it) } -> ReminderQueryType.TODAY
            else -> {
                // Check if there is a specific search term
                val hasSpecificTopic = lowered.contains("about") || lowered.contains("for")
                if (hasSpecificTopic) ReminderQueryType.SPECIFIC else ReminderQueryType.TODAY
            }
        }

        val searchTerm = if (queryType == ReminderQueryType.SPECIFIC) {
            val patterns = listOf(
                Regex("""(?:about|for|regarding)\s+(.+?)(?:\?|\s*$)""", RegexOption.IGNORE_CASE)
            )
            patterns.firstNotNullOfOrNull { it.find(input)?.groupValues?.get(1)?.trim() }
        } else {
            null
        }

        return ReminderQuery(queryType = queryType, searchTerm = searchTerm)
    }

    // -------------------------------------------------------------------------------------
    // Query execution
    // -------------------------------------------------------------------------------------

    /**
     * Execute a parsed reminder query against the repository and format the results
     * as a user-facing response.
     */
    private suspend fun executeQuery(query: ReminderQuery): String {
        return try {
            val reminders = when (query.queryType) {
                ReminderQueryType.TODAY -> {
                    val endOfDay = getEndOfDayMs()
                    reminderRepository.getActiveReminders().first()
                        .filter { it.triggerTimeMs <= endOfDay && !it.isCompleted }
                }
                ReminderQueryType.THIS_WEEK -> {
                    val endOfWeek = getEndOfWeekMs()
                    reminderRepository.getActiveReminders().first()
                        .filter { it.triggerTimeMs <= endOfWeek && !it.isCompleted }
                }
                ReminderQueryType.ALL -> {
                    reminderRepository.getActiveReminders().first()
                }
                ReminderQueryType.SPECIFIC -> {
                    val term = query.searchTerm?.lowercase() ?: ""
                    reminderRepository.getActiveReminders().first()
                        .filter { it.description.lowercase().contains(term) }
                }
            }

            formatRemindersResponse(query, reminders)
        } catch (e: Exception) {
            "I was unable to retrieve your reminders. Please try again."
        }
    }

    private fun formatRemindersResponse(query: ReminderQuery, reminders: List<Reminder>): String {
        if (reminders.isEmpty()) {
            return when (query.queryType) {
                ReminderQueryType.TODAY -> "You have no reminders for today."
                ReminderQueryType.THIS_WEEK -> "You have no reminders this week."
                ReminderQueryType.ALL -> "You have no active reminders."
                ReminderQueryType.SPECIFIC -> {
                    val term = query.searchTerm ?: "that"
                    "I could not find any reminders about \"$term\"."
                }
            }
        }

        val header = when (query.queryType) {
            ReminderQueryType.TODAY -> "You have ${reminders.size} reminder${if (reminders.size > 1) "s" else ""} today:"
            ReminderQueryType.THIS_WEEK -> "You have ${reminders.size} reminder${if (reminders.size > 1) "s" else ""} this week:"
            ReminderQueryType.ALL -> "You have ${reminders.size} active reminder${if (reminders.size > 1) "s" else ""}:"
            ReminderQueryType.SPECIFIC -> "Found ${reminders.size} reminder${if (reminders.size > 1) "s" else ""}:"
        }

        val items = reminders.take(5).joinToString("\n") { reminder ->
            val timeStr = formatTimeUntil(reminder.triggerTimeMs - System.currentTimeMillis())
            val recurringTag = if (reminder.isRecurring) " (recurring)" else ""
            "  - ${reminder.description} ($timeStr)$recurringTag"
        }

        val overflow = if (reminders.size > 5) "\n  ...and ${reminders.size - 5} more." else ""

        return "$header\n$items$overflow"
    }

    // -------------------------------------------------------------------------------------
    // Template-based description
    // -------------------------------------------------------------------------------------

    private fun describeWithTemplate(command: ReminderCommand): String {
        val recurring = if (command.isRecurring && command.recurrencePattern != null) {
            ", repeating ${command.recurrencePattern}"
        } else {
            ""
        }
        return "Reminder set: \"${command.description}\" at ${command.timeDescription}$recurring."
    }

    // -------------------------------------------------------------------------------------
    // Extraction helpers
    // -------------------------------------------------------------------------------------

    /**
     * Extract the core description from a reminder command by stripping preamble phrases.
     */
    private fun extractDescription(input: String): String {
        var result = input
        for (pattern in DESCRIPTION_STRIP_PATTERNS) {
            result = pattern.replace(result, "")
        }

        // Also strip trailing time expressions
        for (timePattern in TIME_PATTERNS) {
            val match = timePattern.find(result)
            if (match != null) {
                // Remove the time portion and any leading "at"/"by" preposition
                result = result.substring(0, maxOf(0, match.range.first)).trim()
                // Remove trailing preposition if left dangling
                result = result.replace(Regex("""(?:\s+(?:at|by|in|on|around|before|after))\s*$""", RegexOption.IGNORE_CASE), "")
                break
            }
        }

        return result.trim().ifBlank { input }
    }

    /**
     * Extract a time description from reminder-related input.
     */
    private fun extractTimeDescription(input: String): String {
        for (pattern in TIME_PATTERNS) {
            val match = pattern.find(input)
            if (match != null) {
                return match.value.trim()
            }
        }
        return "unspecified time"
    }

    /**
     * Extract a recurrence pattern (e.g. "every Monday", "daily").
     */
    private fun extractRecurrencePattern(input: String): String? {
        val patterns = listOf(
            Regex("""(every\s+\w+(?:\s+(?:and|&)\s+\w+)*)""", RegexOption.IGNORE_CASE),
            Regex("""(daily|weekly|monthly)(?:\s+at\s+\d{1,2}(?::\d{2})?\s*(?:am|pm)?)?""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(input)
            if (match != null) {
                return match.value.trim()
            }
        }
        return null
    }

    // -------------------------------------------------------------------------------------
    // Time helpers
    // -------------------------------------------------------------------------------------

    private fun getEndOfDayMs(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    private fun getEndOfWeekMs(): Long {
        val calendar = Calendar.getInstance()
        val daysUntilEndOfWeek = Calendar.SATURDAY - calendar.get(Calendar.DAY_OF_WEEK)
        val adjustedDays = if (daysUntilEndOfWeek < 0) 7 + daysUntilEndOfWeek else daysUntilEndOfWeek
        calendar.add(Calendar.DAY_OF_YEAR, adjustedDays)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    private fun formatTimeUntil(deltaMs: Long): String {
        if (deltaMs <= 0) return "now"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(deltaMs)
        val hours = TimeUnit.MILLISECONDS.toHours(deltaMs)
        return when {
            minutes < 1 -> "in less than a minute"
            minutes < 60 -> "in ${minutes}min"
            hours < 24 -> {
                val remainingMinutes = minutes % 60
                if (remainingMinutes > 0) "in ${hours}h ${remainingMinutes}min" else "in ${hours}h"
            }
            else -> {
                val days = TimeUnit.MILLISECONDS.toDays(deltaMs)
                if (days == 1L) "tomorrow" else "in ${days} days"
            }
        }
    }
}
