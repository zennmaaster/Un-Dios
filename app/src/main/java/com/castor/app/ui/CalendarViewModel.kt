package com.castor.app.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

/**
 * Data class representing a single calendar event read from the Android
 * [CalendarContract] content provider.
 *
 * @param eventId    The row ID from the Events table (used to build a VIEW intent).
 * @param title      Event summary / title.
 * @param startTime  Epoch millis of the event start.
 * @param endTime    Epoch millis of the event end.
 * @param isAllDay   Whether the event spans the entire day.
 * @param calendarColor The display color of the calendar this event belongs to.
 */
data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val isAllDay: Boolean,
    val calendarColor: Color
)

/**
 * Sealed interface modelling the UI state for the calendar card.
 */
sealed interface CalendarUiState {
    /** Calendar permission has not been granted yet. */
    data object PermissionRequired : CalendarUiState

    /** Events are being loaded from the content provider. */
    data object Loading : CalendarUiState

    /** Events loaded successfully (list may be empty). */
    data class Success(val events: List<CalendarEvent>) : CalendarUiState

    /** An error occurred while querying the content provider. */
    data class Error(val message: String) : CalendarUiState
}

/**
 * ViewModel that reads today's calendar events from the Android [CalendarContract]
 * content provider and exposes them as a [StateFlow].
 *
 * Events are sorted with all-day events first, followed by timed events
 * ordered by start time ascending.
 *
 * The ViewModel checks for [Manifest.permission.READ_CALENDAR] before querying.
 * The UI layer is responsible for requesting the permission and then calling
 * [refreshEvents] once it has been granted.
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "CalendarViewModel"
    }

    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)

    /** Observable UI state for the calendar card. */
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        refreshEvents()
    }

    // -------------------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------------------

    /**
     * Re-queries the content provider for today's events.
     *
     * Safe to call from the UI layer on lifecycle resume or after the user
     * grants the calendar permission.
     */
    fun refreshEvents() {
        viewModelScope.launch {
            loadTodayEvents()
        }
    }

    /**
     * Returns `true` if [Manifest.permission.READ_CALENDAR] is currently granted.
     */
    fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    // -------------------------------------------------------------------------------------
    // Internal loading
    // -------------------------------------------------------------------------------------

    private suspend fun loadTodayEvents() {
        if (!hasCalendarPermission()) {
            _uiState.value = CalendarUiState.PermissionRequired
            return
        }

        _uiState.value = CalendarUiState.Loading

        try {
            val events = withContext(Dispatchers.IO) { queryTodayEvents() }
            _uiState.value = CalendarUiState.Success(events)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query calendar events", e)
            _uiState.value = CalendarUiState.Error(
                e.message ?: "Unknown error reading calendar"
            )
        }
    }

    /**
     * Queries [CalendarContract.Instances] for all events that overlap with today
     * (midnight to midnight in the device's local timezone).
     *
     * We use the Instances table rather than the Events table because it
     * correctly expands recurring events into individual occurrences.
     */
    private fun queryTodayEvents(): List<CalendarEvent> {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayEnd = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR
        )

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .let { builder ->
                ContentUris.appendId(builder, todayStart)
                ContentUris.appendId(builder, todayEnd)
                builder.build()
            }

        val events = mutableListOf<CalendarEvent>()

        val cursor: Cursor? = context.contentResolver.query(
            uri,
            projection,
            null,   // selection
            null,   // selectionArgs
            "${CalendarContract.Instances.BEGIN} ASC" // sortOrder
        )

        cursor?.use {
            val idIdx = it.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val titleIdx = it.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIdx = it.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIdx = it.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayIdx = it.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val colorIdx = it.getColumnIndexOrThrow(CalendarContract.Instances.DISPLAY_COLOR)

            while (it.moveToNext()) {
                val eventId = it.getLong(idIdx)
                val title = it.getString(titleIdx) ?: "(No title)"
                val begin = it.getLong(beginIdx)
                val end = it.getLong(endIdx)
                val allDay = it.getInt(allDayIdx) == 1
                val colorInt = it.getInt(colorIdx)

                events.add(
                    CalendarEvent(
                        eventId = eventId,
                        title = title,
                        startTime = begin,
                        endTime = end,
                        isAllDay = allDay,
                        calendarColor = Color(colorInt)
                    )
                )
            }
        }

        // Sort: all-day events first, then by start time
        return events.sortedWith(
            compareByDescending<CalendarEvent> { it.isAllDay }
                .thenBy { it.startTime }
        )
    }
}
