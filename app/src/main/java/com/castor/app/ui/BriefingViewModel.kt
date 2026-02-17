package com.castor.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.agent.orchestrator.Briefing
import com.castor.agent.orchestrator.BriefingAgent
import com.castor.agent.orchestrator.ProactiveSuggestion
import com.castor.agent.orchestrator.QuickStatus
import com.castor.core.data.db.dao.MessageDao
import com.castor.core.data.db.dao.NotificationDao
import com.castor.core.data.db.dao.ReminderDao
import com.castor.core.data.db.entity.ReminderEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for the briefing card and suggestions row on the home screen.
 *
 * Loads data from [BriefingAgent] on init and periodically refreshes every 15 minutes.
 * Also pulls real-time counts from [ReminderDao], [MessageDao], and [NotificationDao]
 * to build a natural-language briefing summary.
 *
 * Exposes observable state flows:
 * - [briefing] -- the full morning briefing (null while loading)
 * - [suggestions] -- proactive suggestions (empty while loading)
 * - [quickStatus] -- compact status snapshot
 * - [briefingSummary] -- natural-language summary string
 * - [reminderCount] -- today's active reminder count
 * - [unreadMessages] -- unread message count
 * - [unreadNotifications] -- unread notification count
 * - [upcomingReminders] -- next 3 upcoming reminders with times
 * - [lastUpdated] -- timestamp of last successful refresh
 *
 * The refresh loop runs as long as the ViewModel is alive and can be manually
 * triggered via [refreshBriefing].
 */
@HiltViewModel
class BriefingViewModel @Inject constructor(
    private val briefingAgent: BriefingAgent,
    private val reminderDao: ReminderDao,
    private val messageDao: MessageDao,
    private val notificationDao: NotificationDao
) : ViewModel() {

    companion object {
        /** How often to automatically refresh the briefing and suggestions. */
        private const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
    }

    private val _briefing = MutableStateFlow<Briefing?>(null)
    val briefing: StateFlow<Briefing?> = _briefing.asStateFlow()

    private val _suggestions = MutableStateFlow<List<ProactiveSuggestion>>(emptyList())
    val suggestions: StateFlow<List<ProactiveSuggestion>> = _suggestions.asStateFlow()

    private val _quickStatus = MutableStateFlow(QuickStatus())
    val quickStatus: StateFlow<QuickStatus> = _quickStatus.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // --- Real data flows ---

    private val _briefingSummary = MutableStateFlow("Loading briefing...")
    val briefingSummary: StateFlow<String> = _briefingSummary.asStateFlow()

    private val _reminderCount = MutableStateFlow(0)
    val reminderCount: StateFlow<Int> = _reminderCount.asStateFlow()

    private val _unreadMessages = MutableStateFlow(0)
    val unreadMessages: StateFlow<Int> = _unreadMessages.asStateFlow()

    private val _unreadNotifications = MutableStateFlow(0)
    val unreadNotifications: StateFlow<Int> = _unreadNotifications.asStateFlow()

    private val _upcomingReminders = MutableStateFlow<List<ReminderEntity>>(emptyList())
    val upcomingReminders: StateFlow<List<ReminderEntity>> = _upcomingReminders.asStateFlow()

    private val _lastUpdated = MutableStateFlow<Long?>(null)
    val lastUpdated: StateFlow<Long?> = _lastUpdated.asStateFlow()

    private var refreshJob: Job? = null

    init {
        // Initial load
        refreshBriefing()

        // Periodic refresh loop
        viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                loadAll()
            }
        }
    }

    /**
     * Manually trigger a full refresh of the briefing, suggestions, and quick status.
     * Safe to call multiple times -- concurrent refresh requests are coalesced.
     */
    fun refreshBriefing() {
        // Cancel any in-flight refresh to avoid duplicate work
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loadAll()
        }
    }

    /**
     * Remove a suggestion from the list at the given [index].
     * This is a UI-only dismissal -- the suggestion may reappear on the next refresh.
     */
    fun dismissSuggestion(index: Int) {
        _suggestions.update { current ->
            if (index in current.indices) {
                current.toMutableList().apply { removeAt(index) }
            } else {
                current
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Internal loading
    // -------------------------------------------------------------------------------------

    private suspend fun loadAll() {
        _isRefreshing.value = true
        try {
            // Load real-time counts from DAOs
            loadRealTimeCounts()

            // Load quick status first -- it is cheap and gives immediate feedback
            val status = try {
                briefingAgent.getQuickStatus()
            } catch (_: Exception) {
                QuickStatus()
            }
            _quickStatus.value = status

            // Load full briefing
            val briefing = try {
                briefingAgent.generateMorningBriefing()
            } catch (_: Exception) {
                null
            }
            _briefing.value = briefing

            // Load suggestions
            val suggestions = try {
                briefingAgent.generateSuggestions()
            } catch (_: Exception) {
                emptyList()
            }
            _suggestions.value = suggestions

            // Update the last refreshed timestamp
            _lastUpdated.value = System.currentTimeMillis()
        } finally {
            _isRefreshing.value = false
        }
    }

    /**
     * Load real-time counts from Room DAOs and build the natural-language
     * briefing summary string.
     */
    private suspend fun loadRealTimeCounts() {
        // Reminder count: active reminders due today
        val now = System.currentTimeMillis()
        val endOfDay = getEndOfDayMs()

        val reminders = try {
            reminderDao.getUpcomingReminders(now).first()
        } catch (_: Exception) {
            emptyList()
        }
        val todayReminders = reminders.filter { it.triggerTimeMs <= endOfDay }
        _reminderCount.value = todayReminders.size
        _upcomingReminders.value = reminders.take(3)

        // Unread message count
        val msgCount = try {
            messageDao.getUnreadCount().first()
        } catch (_: Exception) {
            0
        }
        _unreadMessages.value = msgCount

        // Unread notification count
        val notifCount = try {
            notificationDao.getUnreadCount().first()
        } catch (_: Exception) {
            0
        }
        _unreadNotifications.value = notifCount

        // Build natural-language summary
        _briefingSummary.value = buildBriefingSummary(
            greeting = getTimeAwareGreeting(),
            reminderCount = todayReminders.size,
            unreadMessages = msgCount,
            unreadNotifications = notifCount
        )
    }

    // -------------------------------------------------------------------------------------
    // Briefing summary generation
    // -------------------------------------------------------------------------------------

    /**
     * Returns a time-aware greeting string based on the current hour.
     */
    private fun getTimeAwareGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    /**
     * Returns the current time-of-day period as a string.
     */
    fun getTimeOfDay(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "morning"
            hour < 17 -> "afternoon"
            else -> "evening"
        }
    }

    /**
     * Builds a natural-language morning briefing summary from real data.
     *
     * Example: "Good morning. You have 3 reminders today, 5 unread messages, and 12 notifications."
     */
    private fun buildBriefingSummary(
        greeting: String,
        reminderCount: Int,
        unreadMessages: Int,
        unreadNotifications: Int
    ): String {
        val parts = mutableListOf<String>()

        if (reminderCount > 0) {
            parts.add("$reminderCount reminder${if (reminderCount != 1) "s" else ""} today")
        }
        if (unreadMessages > 0) {
            parts.add("$unreadMessages unread message${if (unreadMessages != 1) "s" else ""}")
        }
        if (unreadNotifications > 0) {
            parts.add("$unreadNotifications notification${if (unreadNotifications != 1) "s" else ""}")
        }

        return if (parts.isEmpty()) {
            "$greeting. All clear -- nothing pending."
        } else {
            val joined = when (parts.size) {
                1 -> parts.first()
                2 -> "${parts[0]} and ${parts[1]}"
                else -> "${parts.dropLast(1).joinToString(", ")}, and ${parts.last()}"
            }
            "$greeting. You have $joined."
        }
    }

    /**
     * Formats the [lastUpdated] timestamp as "HH:mm" for display.
     */
    fun formatLastUpdated(timestampMs: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }

    /**
     * Formats a reminder trigger time as "HH:mm" for the agenda display.
     */
    fun formatReminderTime(triggerTimeMs: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(triggerTimeMs))
    }

    private fun getEndOfDayMs(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }
}
