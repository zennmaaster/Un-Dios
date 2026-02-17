package com.castor.feature.reminders.engine

import android.util.Log
import com.castor.core.inference.InferenceEngine
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses natural-language reminder strings into structured [ParsedReminder] objects.
 *
 * Uses a two-tier strategy:
 *   1. If the on-device LLM is loaded, ask it to extract action / date / time / recurrence
 *      and return a JSON blob.
 *   2. If the LLM is unavailable (not yet loaded, or inference fails), fall back to a
 *      deterministic keyword-based parser that handles the most common patterns.
 */
@Singleton
class ReminderParser @Inject constructor(
    private val engine: InferenceEngine
) {

    companion object {
        private const val TAG = "ReminderParser"

        // ---- Relative time patterns ----
        private val RELATIVE_MINUTES = Pattern.compile(
            """(?:in\s+)?(\d+)\s*(?:min(?:ute)?s?)""", Pattern.CASE_INSENSITIVE
        )
        private val RELATIVE_HOURS = Pattern.compile(
            """(?:in\s+)?(\d+)\s*(?:hr|hour)s?""", Pattern.CASE_INSENSITIVE
        )
        private val RELATIVE_DAYS = Pattern.compile(
            """(?:in\s+)?(\d+)\s*days?""", Pattern.CASE_INSENSITIVE
        )

        // ---- Absolute time patterns ----
        // Matches "at 5pm", "at 5:30 PM", "at 17:00", "at 5 pm"
        private val ABSOLUTE_TIME = Pattern.compile(
            """at\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""", Pattern.CASE_INSENSITIVE
        )

        // ---- Day-of-week patterns ----
        private val DAY_OF_WEEK = Pattern.compile(
            """(?:every\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)""",
            Pattern.CASE_INSENSITIVE
        )

        // ---- Recurrence patterns ----
        private val EVERY_N_MINUTES = Pattern.compile(
            """every\s+(\d+)\s*(?:min(?:ute)?s?)""", Pattern.CASE_INSENSITIVE
        )
        private val EVERY_N_HOURS = Pattern.compile(
            """every\s+(\d+)\s*(?:hr|hour)s?""", Pattern.CASE_INSENSITIVE
        )
        private val EVERY_DAY = Pattern.compile(
            """every\s*day""", Pattern.CASE_INSENSITIVE
        )
        private val EVERY_WEEK = Pattern.compile(
            """every\s*week""", Pattern.CASE_INSENSITIVE
        )

        // ---- Keywords that signal the start of the time component ----
        private val TIME_SIGNAL_WORDS = listOf(
            "in ", "at ", "every ", "tomorrow", "today", "tonight"
        )

        // ---- Common prefixes to strip when extracting description ----
        private val STRIP_PREFIXES = listOf(
            "remind me to ", "reminder to ", "remind me ", "reminder ",
            "set a reminder to ", "set reminder to ",
            "set a reminder for ", "set reminder for ",
            "please remind me to ", "can you remind me to "
        )

        // Millisecond constants
        private const val MS_PER_MINUTE = 60_000L
        private const val MS_PER_HOUR = 3_600_000L
        private const val MS_PER_DAY = 86_400_000L
        private const val MS_PER_WEEK = 604_800_000L
    }

    /**
     * Structured output of the parser.
     */
    data class ParsedReminder(
        val description: String,
        val triggerTimeMs: Long,
        val isRecurring: Boolean,
        val recurringInterval: Long? // ms
    )

    // -------------------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------------------

    /**
     * Parse a natural-language reminder string into a [ParsedReminder].
     *
     * Returns `null` if parsing fails entirely (no time component detected and
     * LLM also cannot make sense of the input).
     */
    suspend fun parse(input: String): ParsedReminder? {
        if (input.isBlank()) return null

        // Try LLM-based parsing first when the model is loaded.
        if (engine.isLoaded) {
            try {
                val llmResult = llmParse(input)
                if (llmResult != null) return llmResult
            } catch (e: Exception) {
                Log.w(TAG, "LLM parse failed, falling back to keyword parser", e)
            }
        }

        // Fallback: deterministic keyword-based parser.
        return keywordParse(input)
    }

    // -------------------------------------------------------------------------------------
    // LLM-based parsing
    // -------------------------------------------------------------------------------------

    private suspend fun llmParse(input: String): ParsedReminder? {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val currentTimeDescription = String.format(
            Locale.US,
            "%04d-%02d-%02d %02d:%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
        val dayOfWeek = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.US)

        val systemPrompt = """You are a reminder extraction assistant. Given a natural language reminder request, extract the following fields and return ONLY a JSON object with no other text:

{
  "action": "the thing the user wants to be reminded about",
  "date": "YYYY-MM-DD or null if relative time",
  "time": "HH:mm in 24-hour format or null",
  "relative_minutes": number of minutes from now or null,
  "is_recurring": true/false,
  "recurring_interval_minutes": number of minutes between recurrences or null
}

Current date/time: $currentTimeDescription (${dayOfWeek})
Current epoch millis: $now

Rules:
- "tomorrow" means the next calendar day
- "tonight" means today at 21:00
- "in 30 minutes" means relative_minutes=30
- "every Monday at 9am" means is_recurring=true, recurring_interval_minutes=10080 (7 days), date=next Monday, time=09:00
- "every day at 8am" means is_recurring=true, recurring_interval_minutes=1440
- If no time is specified, default to 09:00
- Return ONLY the JSON object, no explanation"""

        val response = engine.generate(
            prompt = input,
            systemPrompt = systemPrompt,
            maxTokens = 256,
            temperature = 0.1f
        )

        return parseLlmJsonResponse(response, now)
    }

    /**
     * Attempts to parse the JSON blob returned by the LLM into a [ParsedReminder].
     */
    private fun parseLlmJsonResponse(response: String, now: Long): ParsedReminder? {
        return try {
            // The LLM might wrap the JSON in markdown code fences -- strip those.
            val cleaned = response.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = JSONObject(cleaned)
            val action = json.optString("action", "").ifBlank { return null }

            // Calculate trigger time
            val triggerTimeMs: Long
            val relativeMinutes = json.optInt("relative_minutes", -1)

            if (relativeMinutes > 0) {
                triggerTimeMs = now + relativeMinutes * MS_PER_MINUTE
            } else {
                val datePart = json.optString("date", "")
                val timePart = json.optString("time", "09:00")
                if (datePart.isBlank()) return null

                val cal = Calendar.getInstance()
                val dateParts = datePart.split("-")
                if (dateParts.size != 3) return null

                cal.set(Calendar.YEAR, dateParts[0].toInt())
                cal.set(Calendar.MONTH, dateParts[1].toInt() - 1)
                cal.set(Calendar.DAY_OF_MONTH, dateParts[2].toInt())

                val timeParts = timePart.split(":")
                cal.set(Calendar.HOUR_OF_DAY, timeParts.getOrNull(0)?.toIntOrNull() ?: 9)
                cal.set(Calendar.MINUTE, timeParts.getOrNull(1)?.toIntOrNull() ?: 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)

                triggerTimeMs = cal.timeInMillis

                // If the computed time is in the past and not recurring, push to tomorrow
                if (triggerTimeMs <= now && !json.optBoolean("is_recurring", false)) {
                    return null // Let keyword parser attempt or return null
                }
            }

            val isRecurring = json.optBoolean("is_recurring", false)
            val recurringIntervalMinutes = json.optInt("recurring_interval_minutes", -1)
            val recurringIntervalMs = if (isRecurring && recurringIntervalMinutes > 0) {
                recurringIntervalMinutes.toLong() * MS_PER_MINUTE
            } else {
                null
            }

            ParsedReminder(
                description = action,
                triggerTimeMs = triggerTimeMs,
                isRecurring = isRecurring,
                recurringInterval = recurringIntervalMs
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse LLM JSON response: $response", e)
            null
        }
    }

    // -------------------------------------------------------------------------------------
    // Keyword-based fallback parser
    // -------------------------------------------------------------------------------------

    /**
     * Deterministic parser that handles common natural-language reminder patterns:
     *
     *   - Relative time: "in 30 minutes", "in 2 hours", "in 3 days"
     *   - Absolute time: "at 5pm", "at 5:30 PM", "at 17:00"
     *   - Tomorrow:      "tomorrow at 9am", "tomorrow"
     *   - Tonight:       "tonight", "tonight at 10pm"
     *   - Day-of-week:   "Monday at 9am", "every Tuesday at 3pm"
     *   - Recurring:     "every day at 8am", "every week", "every 30 minutes"
     */
    private fun keywordParse(input: String): ParsedReminder? {
        val normalized = input.trim().lowercase(Locale.US)
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        var triggerTimeMs: Long? = null
        var isRecurring = false
        var recurringInterval: Long? = null

        // --- 1) Check for recurring patterns first ---

        val everyMinMatcher = EVERY_N_MINUTES.matcher(normalized)
        if (everyMinMatcher.find()) {
            val minutes = everyMinMatcher.group(1)!!.toLong()
            recurringInterval = minutes * MS_PER_MINUTE
            isRecurring = true
            triggerTimeMs = now + recurringInterval
        }

        if (triggerTimeMs == null) {
            val everyHrMatcher = EVERY_N_HOURS.matcher(normalized)
            if (everyHrMatcher.find()) {
                val hours = everyHrMatcher.group(1)!!.toLong()
                recurringInterval = hours * MS_PER_HOUR
                isRecurring = true
                triggerTimeMs = now + recurringInterval
            }
        }

        if (triggerTimeMs == null && EVERY_DAY.matcher(normalized).find()) {
            isRecurring = true
            recurringInterval = MS_PER_DAY
            // Parse time component if present, default to 09:00
            val timeCal = parseTimeComponent(normalized) ?: run {
                val c = Calendar.getInstance()
                c.set(Calendar.HOUR_OF_DAY, 9)
                c.set(Calendar.MINUTE, 0)
                c
            }
            cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
            cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis <= now) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            triggerTimeMs = cal.timeInMillis
        }

        if (triggerTimeMs == null && EVERY_WEEK.matcher(normalized).find()) {
            isRecurring = true
            recurringInterval = MS_PER_WEEK
            val timeCal = parseTimeComponent(normalized)
            cal.set(Calendar.HOUR_OF_DAY, timeCal?.get(Calendar.HOUR_OF_DAY) ?: 9)
            cal.set(Calendar.MINUTE, timeCal?.get(Calendar.MINUTE) ?: 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.WEEK_OF_YEAR, 1)
            triggerTimeMs = cal.timeInMillis
        }

        // --- 2) "every <day-of-week>" → recurring weekly on that day ---

        if (triggerTimeMs == null) {
            val dowMatcher = DAY_OF_WEEK.matcher(normalized)
            if (dowMatcher.find()) {
                val dayName = dowMatcher.group(1)!!
                val targetDow = dayNameToCalendarDay(dayName)
                val recurringPrefix = normalized.contains("every")

                if (recurringPrefix) {
                    isRecurring = true
                    recurringInterval = MS_PER_WEEK
                }

                val timeCal = parseTimeComponent(normalized)
                cal.set(Calendar.HOUR_OF_DAY, timeCal?.get(Calendar.HOUR_OF_DAY) ?: 9)
                cal.set(Calendar.MINUTE, timeCal?.get(Calendar.MINUTE) ?: 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)

                // Advance to the next occurrence of the target day
                val currentDow = cal.get(Calendar.DAY_OF_WEEK)
                var daysUntil = targetDow - currentDow
                if (daysUntil < 0) daysUntil += 7
                if (daysUntil == 0 && cal.timeInMillis <= now) daysUntil = 7
                cal.add(Calendar.DAY_OF_YEAR, daysUntil)

                triggerTimeMs = cal.timeInMillis
            }
        }

        // --- 3) Relative time: "in N minutes/hours/days" ---

        if (triggerTimeMs == null) {
            val minMatcher = RELATIVE_MINUTES.matcher(normalized)
            if (minMatcher.find()) {
                val minutes = minMatcher.group(1)!!.toLong()
                triggerTimeMs = now + minutes * MS_PER_MINUTE
            }
        }

        if (triggerTimeMs == null) {
            val hrMatcher = RELATIVE_HOURS.matcher(normalized)
            if (hrMatcher.find()) {
                val hours = hrMatcher.group(1)!!.toLong()
                triggerTimeMs = now + hours * MS_PER_HOUR
            }
        }

        if (triggerTimeMs == null) {
            val dayMatcher = RELATIVE_DAYS.matcher(normalized)
            if (dayMatcher.find()) {
                val days = dayMatcher.group(1)!!.toLong()
                val timeCal = parseTimeComponent(normalized)
                if (timeCal != null) {
                    cal.add(Calendar.DAY_OF_YEAR, days.toInt())
                    cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                    cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    triggerTimeMs = cal.timeInMillis
                } else {
                    triggerTimeMs = now + days * MS_PER_DAY
                }
            }
        }

        // --- 4) "tomorrow" with optional time ---

        if (triggerTimeMs == null && normalized.contains("tomorrow")) {
            val timeCal = parseTimeComponent(normalized)
            cal.add(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, timeCal?.get(Calendar.HOUR_OF_DAY) ?: 9)
            cal.set(Calendar.MINUTE, timeCal?.get(Calendar.MINUTE) ?: 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            triggerTimeMs = cal.timeInMillis
        }

        // --- 5) "tonight" → today at 21:00 (or specified time) ---

        if (triggerTimeMs == null && normalized.contains("tonight")) {
            val timeCal = parseTimeComponent(normalized)
            cal.set(Calendar.HOUR_OF_DAY, timeCal?.get(Calendar.HOUR_OF_DAY) ?: 21)
            cal.set(Calendar.MINUTE, timeCal?.get(Calendar.MINUTE) ?: 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis <= now) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            triggerTimeMs = cal.timeInMillis
        }

        // --- 6) Absolute time only: "at 5pm" → today (or tomorrow if past) ---

        if (triggerTimeMs == null) {
            val timeCal = parseTimeComponent(normalized)
            if (timeCal != null) {
                cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                if (cal.timeInMillis <= now) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                triggerTimeMs = cal.timeInMillis
            }
        }

        // If we could not determine a trigger time, parsing failed.
        if (triggerTimeMs == null) return null

        // --- Extract description by stripping time and prefix words ---
        val description = extractDescription(input, normalized)
        if (description.isBlank()) return null

        return ParsedReminder(
            description = description,
            triggerTimeMs = triggerTimeMs,
            isRecurring = isRecurring,
            recurringInterval = recurringInterval
        )
    }

    // -------------------------------------------------------------------------------------
    // Helper: parse "at HH:mm am/pm" from a string
    // -------------------------------------------------------------------------------------

    /**
     * Scans the input for an "at <time>" pattern and returns a [Calendar] set to
     * that hour/minute, or null if no time component was found.
     */
    private fun parseTimeComponent(normalized: String): Calendar? {
        val matcher = ABSOLUTE_TIME.matcher(normalized)
        if (!matcher.find()) return null

        var hour = matcher.group(1)!!.toInt()
        val minute = matcher.group(2)?.toIntOrNull() ?: 0
        val amPm = matcher.group(3)?.lowercase(Locale.US)

        when (amPm) {
            "am" -> if (hour == 12) hour = 0
            "pm" -> if (hour != 12) hour += 12
            else -> {
                // No am/pm specified -- if hour <= 12 we keep it as-is (24h interpretation
                // for values > 12; for values <= 12 we assume the most reasonable next
                // occurrence, which the caller handles by checking against "now").
            }
        }

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    // -------------------------------------------------------------------------------------
    // Helper: extract human-readable description
    // -------------------------------------------------------------------------------------

    /**
     * Strips common prefixes ("remind me to ...") and time-related suffixes to
     * produce a clean, capitalized description of the reminder action.
     */
    private fun extractDescription(originalInput: String, normalized: String): String {
        var desc = normalized

        // Remove common prefixes
        for (prefix in STRIP_PREFIXES) {
            if (desc.startsWith(prefix)) {
                desc = desc.removePrefix(prefix)
                break
            }
        }

        // Remove time-related chunks
        desc = desc
            .replace(RELATIVE_MINUTES.toRegex(), "")
            .replace(RELATIVE_HOURS.toRegex(), "")
            .replace(RELATIVE_DAYS.toRegex(), "")
            .replace(ABSOLUTE_TIME.toRegex(), "")
            .replace(EVERY_N_MINUTES.toRegex(), "")
            .replace(EVERY_N_HOURS.toRegex(), "")
            .replace(EVERY_DAY.toRegex(), "")
            .replace(EVERY_WEEK.toRegex(), "")
            .replace(DAY_OF_WEEK.toRegex(), "")
            .replace(Regex("""(?i)\btomorrow\b"""), "")
            .replace(Regex("""(?i)\btonight\b"""), "")
            .replace(Regex("""(?i)\btoday\b"""), "")
            .replace(Regex("""(?i)\bevery\b"""), "")
            .replace(Regex("""(?i)\bin\b"""), "")

        // Clean up leftover whitespace, commas, leading/trailing punctuation
        desc = desc
            .replace(Regex("""\s{2,}"""), " ")
            .replace(Regex("""^[\s,\-:]+"""), "")
            .replace(Regex("""[\s,\-:]+$"""), "")
            .trim()

        // Capitalize first letter
        return desc.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    }

    // -------------------------------------------------------------------------------------
    // Helper: day name → Calendar constant
    // -------------------------------------------------------------------------------------

    private fun dayNameToCalendarDay(name: String): Int = when (name.lowercase(Locale.US)) {
        "sunday" -> Calendar.SUNDAY
        "monday" -> Calendar.MONDAY
        "tuesday" -> Calendar.TUESDAY
        "wednesday" -> Calendar.WEDNESDAY
        "thursday" -> Calendar.THURSDAY
        "friday" -> Calendar.FRIDAY
        "saturday" -> Calendar.SATURDAY
        else -> Calendar.MONDAY
    }
}
