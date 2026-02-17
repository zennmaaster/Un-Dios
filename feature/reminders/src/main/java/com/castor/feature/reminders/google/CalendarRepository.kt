package com.castor.feature.reminders.google

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that bridges the Google Calendar API with local state.
 *
 * Provides reactive [StateFlow] streams of calendar events for today and the
 * upcoming week, along with methods to create and delete events. All network
 * operations are suspended and safe to call from coroutines.
 */
@Singleton
class CalendarRepository @Inject constructor(
    private val calendarApi: GoogleCalendarApi,
    private val authManager: GoogleAuthManager
) {
    companion object {
        private const val TAG = "CalendarRepository"
    }

    private val _todayEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())

    /** Today's calendar events, sorted by start time. */
    val todayEvents: StateFlow<List<CalendarEvent>> = _todayEvents.asStateFlow()

    private val _upcomingEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())

    /** Calendar events for the next 7 days (excluding today), sorted by start time. */
    val upcomingEvents: StateFlow<List<CalendarEvent>> = _upcomingEvents.asStateFlow()

    private val _isLoading = MutableStateFlow(false)

    /** Whether a sync operation is currently in progress. */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)

    /** The most recent sync error, or null if the last sync succeeded. */
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    /**
     * Fetches events from Google Calendar for today and the next 7 days.
     *
     * Events are partitioned into [todayEvents] and [upcomingEvents] flows.
     * Errors are exposed via [syncError].
     */
    suspend fun syncEvents() {
        val auth = authManager.getAuthorizationHeader()
        if (auth == null) {
            _syncError.value = "Not authenticated with Google"
            return
        }

        _isLoading.value = true
        _syncError.value = null

        try {
            val now = Calendar.getInstance()
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val todayEnd = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val weekEnd = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 7)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }

            val timeMin = toRfc3339(todayStart.time)
            val timeMax = toRfc3339(weekEnd.time)
            val todayEndStr = toRfc3339(todayEnd.time)

            val response = calendarApi.getEvents(
                auth = auth,
                timeMin = timeMin,
                timeMax = timeMax,
                singleEvents = true,
                orderBy = "startTime",
                maxResults = 250
            )

            if (response.isSuccessful) {
                val allEvents = response.body()?.items.orEmpty()

                // Partition events: today vs. upcoming
                val todayList = mutableListOf<CalendarEvent>()
                val upcomingList = mutableListOf<CalendarEvent>()

                for (event in allEvents) {
                    val eventStart = event.start?.dateTime ?: event.start?.date
                    if (eventStart != null && isBeforeOrEqual(eventStart, todayEndStr)) {
                        todayList.add(event)
                    } else {
                        upcomingList.add(event)
                    }
                }

                _todayEvents.value = todayList
                _upcomingEvents.value = upcomingList
                Log.d(TAG, "Synced ${todayList.size} today events, ${upcomingList.size} upcoming")
            } else {
                val errorBody = response.errorBody()?.string()
                _syncError.value = "Calendar sync failed: ${response.code()} ${errorBody.orEmpty()}"
                Log.e(TAG, "Calendar API error: ${response.code()} $errorBody")
            }
        } catch (e: Exception) {
            _syncError.value = "Calendar sync failed: ${e.message}"
            Log.e(TAG, "Calendar sync exception", e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Creates a new event on the primary calendar.
     *
     * @param title Event summary/title
     * @param startTime Start time in epoch milliseconds
     * @param endTime End time in epoch milliseconds
     * @param description Optional event description
     * @param location Optional event location
     * @return The created [CalendarEvent], or null on failure
     */
    suspend fun createEvent(
        title: String,
        startTime: Long,
        endTime: Long,
        description: String? = null,
        location: String? = null
    ): CalendarEvent? {
        val auth = authManager.getAuthorizationHeader() ?: return null

        val timeZoneId = TimeZone.getDefault().id
        val request = CalendarEventRequest(
            summary = title,
            description = description,
            location = location,
            start = CalendarDateTimeRequest(
                dateTime = toRfc3339(Date(startTime)),
                timeZone = timeZoneId
            ),
            end = CalendarDateTimeRequest(
                dateTime = toRfc3339(Date(endTime)),
                timeZone = timeZoneId
            )
        )

        return try {
            val response = calendarApi.createEvent(auth = auth, event = request)
            if (response.isSuccessful) {
                val event = response.body()
                Log.d(TAG, "Created event: ${event?.id}")
                // Re-sync to update flows
                syncEvents()
                event
            } else {
                Log.e(TAG, "Create event failed: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Create event exception", e)
            null
        }
    }

    /**
     * Deletes an event from the primary calendar.
     *
     * @param eventId The Google Calendar event ID to delete
     * @return true if deletion succeeded
     */
    suspend fun deleteEvent(eventId: String): Boolean {
        val auth = authManager.getAuthorizationHeader() ?: return false

        return try {
            val response = calendarApi.deleteEvent(auth = auth, eventId = eventId)
            if (response.isSuccessful || response.code() == 204) {
                Log.d(TAG, "Deleted event: $eventId")
                // Re-sync to update flows
                syncEvents()
                true
            } else {
                Log.e(TAG, "Delete event failed: ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete event exception", e)
            false
        }
    }

    // ---- Utilities ----

    private val rfc3339Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    private fun toRfc3339(date: Date): String {
        return rfc3339Format.format(date)
    }

    /**
     * Simple string comparison to check if [a] is before or equal to [b] in ISO/RFC3339 order.
     * Works for both "yyyy-MM-dd" (all-day) and "yyyy-MM-dd'T'HH:mm:ss..." formats.
     */
    private fun isBeforeOrEqual(a: String, b: String): Boolean {
        return a <= b
    }
}
