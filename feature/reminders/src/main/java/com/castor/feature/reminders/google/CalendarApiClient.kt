package com.castor.feature.reminders.google

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for Google Calendar API v3.
 *
 * All endpoints require a valid Bearer token in the Authorization header.
 * Base URL: https://www.googleapis.com/
 *
 * @see <a href="https://developers.google.com/calendar/api/v3/reference">Calendar API Reference</a>
 */
interface GoogleCalendarApi {

    /**
     * Lists events on the primary calendar within a time range.
     *
     * @param auth Bearer token (e.g., "Bearer ya29.xxx")
     * @param timeMin Lower bound (inclusive) for event start time, RFC3339 format
     * @param timeMax Upper bound (exclusive) for event end time, RFC3339 format
     * @param singleEvents Whether to expand recurring events into instances
     * @param orderBy Sort order — "startTime" requires singleEvents=true
     * @param maxResults Maximum number of events returned
     */
    @GET("calendar/v3/calendars/primary/events")
    suspend fun getEvents(
        @Header("Authorization") auth: String,
        @Query("timeMin") timeMin: String,
        @Query("timeMax") timeMax: String,
        @Query("singleEvents") singleEvents: Boolean = true,
        @Query("orderBy") orderBy: String = "startTime",
        @Query("maxResults") maxResults: Int = 50
    ): Response<CalendarEventsResponse>

    /**
     * Creates a new event on the primary calendar.
     *
     * @param auth Bearer token
     * @param event The event to create
     */
    @POST("calendar/v3/calendars/primary/events")
    suspend fun createEvent(
        @Header("Authorization") auth: String,
        @Body event: CalendarEventRequest
    ): Response<CalendarEvent>

    /**
     * Updates an existing event on the primary calendar.
     *
     * @param auth Bearer token
     * @param eventId The event ID to update
     * @param event The updated event data
     */
    @PATCH("calendar/v3/calendars/primary/events/{eventId}")
    suspend fun updateEvent(
        @Header("Authorization") auth: String,
        @Path("eventId") eventId: String,
        @Body event: CalendarEventRequest
    ): Response<CalendarEvent>

    /**
     * Deletes an event from the primary calendar.
     *
     * @param auth Bearer token
     * @param eventId The event ID to delete
     */
    @DELETE("calendar/v3/calendars/primary/events/{eventId}")
    suspend fun deleteEvent(
        @Header("Authorization") auth: String,
        @Path("eventId") eventId: String
    ): Response<Unit>
}

// =============================================================================
// Response Data Classes
// =============================================================================

/**
 * Response wrapper for the Calendar events list endpoint.
 */
@Serializable
data class CalendarEventsResponse(
    @SerialName("kind") val kind: String? = null,
    @SerialName("summary") val summary: String? = null,
    @SerialName("timeZone") val timeZone: String? = null,
    @SerialName("items") val items: List<CalendarEvent> = emptyList(),
    @SerialName("nextPageToken") val nextPageToken: String? = null
)

/**
 * Represents a single Google Calendar event.
 */
@Serializable
data class CalendarEvent(
    @SerialName("id") val id: String = "",
    @SerialName("summary") val summary: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("location") val location: String? = null,
    @SerialName("start") val start: CalendarDateTime? = null,
    @SerialName("end") val end: CalendarDateTime? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("htmlLink") val htmlLink: String? = null,
    @SerialName("created") val created: String? = null,
    @SerialName("updated") val updated: String? = null,
    @SerialName("colorId") val colorId: String? = null,
    @SerialName("creator") val creator: CalendarAttendee? = null,
    @SerialName("organizer") val organizer: CalendarAttendee? = null
)

/**
 * Represents a date-time value from Calendar API.
 *
 * Either [dateTime] (for timed events) or [date] (for all-day events) will be set.
 */
@Serializable
data class CalendarDateTime(
    @SerialName("dateTime") val dateTime: String? = null,
    @SerialName("date") val date: String? = null,
    @SerialName("timeZone") val timeZone: String? = null
)

/**
 * Represents a calendar event attendee / creator / organizer.
 */
@Serializable
data class CalendarAttendee(
    @SerialName("email") val email: String? = null,
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("self") val self: Boolean? = null
)

// =============================================================================
// Request Data Classes
// =============================================================================

/**
 * Request body for creating or updating a calendar event.
 */
@Serializable
data class CalendarEventRequest(
    @SerialName("summary") val summary: String,
    @SerialName("description") val description: String? = null,
    @SerialName("location") val location: String? = null,
    @SerialName("start") val start: CalendarDateTimeRequest,
    @SerialName("end") val end: CalendarDateTimeRequest
)

/**
 * Date-time value for event creation requests.
 */
@Serializable
data class CalendarDateTimeRequest(
    @SerialName("dateTime") val dateTime: String? = null,
    @SerialName("date") val date: String? = null,
    @SerialName("timeZone") val timeZone: String? = null
)
