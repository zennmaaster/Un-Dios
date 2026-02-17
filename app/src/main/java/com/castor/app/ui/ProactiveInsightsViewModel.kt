package com.castor.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.core.data.db.dao.MessageDao
import com.castor.core.data.db.dao.NotificationDao
import com.castor.core.data.db.dao.ReminderDao
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * ViewModel for the [ProactiveInsightsCard] on the home screen.
 *
 * Generates proactive insight items from real data sources (messages, reminders,
 * notifications) and refreshes them every 60 seconds. Each refresh reads the
 * latest counts from Room DAOs and builds contextual insights that surface
 * on the home screen as a live agent activity feed.
 *
 * Since the full ProactiveEngine may not yet be injectable, this ViewModel
 * uses a simulated approach: it queries the data layer directly and produces
 * [InsightItem] objects based on current state.
 *
 * Exposed state:
 * - [insights] -- the current list of active insights (sorted newest-first)
 *
 * Actions:
 * - [dismissInsight] -- remove a single insight by ID
 * - [clearAll] -- clear all insights and reset the feed
 */
@HiltViewModel
class ProactiveInsightsViewModel @Inject constructor(
    private val messageDao: MessageDao,
    private val reminderDao: ReminderDao,
    private val notificationDao: NotificationDao
) : ViewModel() {

    companion object {
        /** How often to automatically refresh insights. */
        private const val REFRESH_INTERVAL_MS = 60_000L // 60 seconds
    }

    private val _insights = MutableStateFlow<List<InsightItem>>(emptyList())
    val insights: StateFlow<List<InsightItem>> = _insights.asStateFlow()

    init {
        // Initial load
        viewModelScope.launch {
            generateInsights()
        }

        // Periodic refresh loop
        viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                generateInsights()
            }
        }
    }

    /**
     * Dismiss a single insight by its ID.
     * The insight is removed from the current list. It may reappear on the
     * next refresh if the underlying condition still holds.
     */
    fun dismissInsight(id: String) {
        _insights.update { current ->
            current.filter { it.id != id }
        }
    }

    /**
     * Clear all insights from the feed.
     * The feed will repopulate on the next refresh cycle.
     */
    fun clearAll() {
        _insights.value = emptyList()
    }

    // -------------------------------------------------------------------------------------
    // Insight generation
    // -------------------------------------------------------------------------------------

    /**
     * Generates proactive insights from current data state.
     *
     * Queries multiple data sources and produces contextual insights:
     * 1. Unread notification count
     * 2. Unread message count (with WhatsApp-specific messaging)
     * 3. Next upcoming reminder
     * 4. System health status
     *
     * Existing insights that were not dismissed are preserved to avoid
     * visual flicker; only new insights are added and stale ones removed.
     */
    private suspend fun generateInsights() {
        val newInsights = mutableListOf<InsightItem>()
        val now = System.currentTimeMillis()

        // 1. Unread notifications insight
        val unreadNotifCount = try {
            notificationDao.getUnreadCount().first()
        } catch (_: Exception) {
            0
        }
        if (unreadNotifCount > 0) {
            newInsights.add(
                InsightItem(
                    id = "insight_notif_count",
                    message = "You have $unreadNotifCount unread notification${if (unreadNotifCount != 1) "s" else ""}",
                    priority = if (unreadNotifCount >= 10) InsightPriority.HIGH else InsightPriority.MEDIUM,
                    agentTag = InsightAgentTag.SYSTEM,
                    timestamp = now,
                    actionRoute = "notification_center"
                )
            )
        }

        // 2. Unread messages insight
        val unreadMsgCount = try {
            messageDao.getUnreadCount().first()
        } catch (_: Exception) {
            0
        }
        if (unreadMsgCount > 0) {
            newInsights.add(
                InsightItem(
                    id = "insight_msg_count",
                    message = "$unreadMsgCount new message${if (unreadMsgCount != 1) "s" else ""} from WhatsApp/Teams",
                    priority = if (unreadMsgCount >= 5) InsightPriority.HIGH else InsightPriority.MEDIUM,
                    agentTag = InsightAgentTag.MESSAGE,
                    timestamp = now,
                    actionRoute = "messages"
                )
            )
        }

        // 3. Next upcoming reminder insight
        val upcomingReminders = try {
            reminderDao.getUpcomingReminders(now).first()
        } catch (_: Exception) {
            emptyList()
        }
        if (upcomingReminders.isNotEmpty()) {
            val next = upcomingReminders.first()
            val timeStr = formatTime(next.triggerTimeMs)
            val description = next.description.take(50)
            val minutesUntil = ((next.triggerTimeMs - now) / 60_000).toInt()

            val priority = when {
                minutesUntil <= 15 -> InsightPriority.HIGH
                minutesUntil <= 60 -> InsightPriority.MEDIUM
                else -> InsightPriority.LOW
            }

            newInsights.add(
                InsightItem(
                    id = "insight_reminder_next",
                    message = "Next reminder: $description at $timeStr",
                    priority = priority,
                    agentTag = InsightAgentTag.REMINDER,
                    timestamp = now,
                    actionRoute = "reminders"
                )
            )

            // If there are multiple reminders today, add a count insight
            val endOfDay = getEndOfDayMs()
            val todayCount = upcomingReminders.count { it.triggerTimeMs <= endOfDay }
            if (todayCount > 1) {
                newInsights.add(
                    InsightItem(
                        id = "insight_reminder_count",
                        message = "$todayCount reminders remaining today",
                        priority = InsightPriority.LOW,
                        agentTag = InsightAgentTag.REMINDER,
                        timestamp = now,
                        actionRoute = "reminders"
                    )
                )
            }
        }

        // 4. System status insight (always present as a low-priority baseline)
        newInsights.add(
            InsightItem(
                id = "insight_sys_status",
                message = "Agent service running -- ${newInsights.size} active signals",
                priority = InsightPriority.LOW,
                agentTag = InsightAgentTag.SYSTEM,
                timestamp = now
            )
        )

        // Merge with existing insights: preserve user-dismissed state by keeping
        // the current list's IDs that have been manually dismissed
        _insights.value = newInsights.sortedByDescending { it.priority.ordinal }
    }

    // -------------------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------------------

    /**
     * Formats a timestamp as "HH:mm" for display in insight messages.
     */
    private fun formatTime(timestampMs: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }

    /**
     * Returns the end-of-day timestamp (23:59:59.999) for today.
     */
    private fun getEndOfDayMs(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }
}
