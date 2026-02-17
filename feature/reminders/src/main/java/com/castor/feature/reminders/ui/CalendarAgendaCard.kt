package com.castor.feature.reminders.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.theme.TerminalColors
import com.castor.feature.reminders.google.CalendarEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Calendar event color palette — maps Google Calendar colorId to display colors.
 * Falls back to a default blue when colorId is null or unrecognized.
 */
private object CalendarEventColors {
    val Default = Color(0xFF89B4FA)       // Blue (Catppuccin)
    val Lavender = Color(0xFFCBA6F7)      // Purple
    val Sage = Color(0xFFA6E3A1)          // Green
    val Grape = Color(0xFFB4BEFE)         // Indigo
    val Flamingo = Color(0xFFF38BA8)      // Red/Pink
    val Banana = Color(0xFFF9E2AF)        // Yellow
    val Tangerine = Color(0xFFFAB387)     // Orange
    val Peacock = Color(0xFF74C7EC)       // Teal

    fun forColorId(colorId: String?): Color = when (colorId) {
        "1" -> Lavender
        "2" -> Sage
        "3" -> Grape
        "4" -> Flamingo
        "5" -> Banana
        "6" -> Tangerine
        "7" -> Peacock
        "8" -> Default
        "9" -> Default
        "10" -> Sage
        "11" -> Flamingo
        else -> Default
    }
}

/**
 * Terminal-styled card showing today's Google Calendar agenda.
 *
 * Styled to match the Un-Dios desktop aesthetic with monospace fonts,
 * a `$ cal --today` header, timeline view, and color-coded events.
 *
 * @param events List of today's calendar events
 * @param isLoading Whether the calendar is currently syncing
 * @param errorMessage Optional error message to display
 * @param onRefresh Callback to trigger a calendar sync
 * @param onEventClick Callback when an event is tapped
 * @param modifier Modifier for the root composable
 */
@Composable
fun CalendarAgendaCard(
    events: List<CalendarEvent>,
    isLoading: Boolean,
    errorMessage: String? = null,
    onRefresh: () -> Unit = {},
    onEventClick: (CalendarEvent) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val monoStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        color = TerminalColors.Command
    )

    var isExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Background)
    ) {
        // ---- Header: Terminal-style title bar ----
        CalendarHeader(
            isLoading = isLoading,
            isExpanded = isExpanded,
            onToggleExpanded = { isExpanded = !isExpanded },
            onRefresh = onRefresh,
            monoStyle = monoStyle
        )

        // ---- Content ----
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Date sub-header
                Text(
                    text = formatTodayDate(),
                    style = monoStyle.copy(
                        fontSize = 10.sp,
                        color = TerminalColors.Timestamp
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Error state
                if (errorMessage != null) {
                    ErrorOutput(errorMessage, monoStyle)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Event list or empty state
                if (events.isEmpty() && !isLoading) {
                    EmptyAgenda(monoStyle)
                } else {
                    events.forEachIndexed { index, event ->
                        CalendarEventRow(
                            event = event,
                            monoStyle = monoStyle,
                            onClick = { onEventClick(event) }
                        )
                        if (index < events.lastIndex) {
                            HorizontalDivider(
                                color = TerminalColors.Surface,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }

                // Loading indicator at bottom
                if (isLoading && events.isEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = TerminalColors.Accent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "syncing calendar...",
                            style = monoStyle.copy(
                                fontSize = 11.sp,
                                color = TerminalColors.Accent
                            )
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// Header
// =============================================================================

@Composable
private fun CalendarHeader(
    isLoading: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRefresh: () -> Unit,
    monoStyle: TextStyle
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalColors.Surface)
            .clickable(onClick = onToggleExpanded)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = TerminalColors.Info,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            // Terminal prompt
            Text(
                text = "$ ",
                style = monoStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Prompt
                )
            )
            Text(
                text = "cal --today",
                style = monoStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Command
                )
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color = TerminalColors.Accent
                )
            } else {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh calendar",
                        tint = TerminalColors.Timestamp,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// =============================================================================
// Event Row
// =============================================================================

@Composable
private fun CalendarEventRow(
    event: CalendarEvent,
    monoStyle: TextStyle,
    onClick: () -> Unit
) {
    val eventColor = CalendarEventColors.forColorId(event.colorId)
    val timeRange = formatEventTimeRange(event)
    val isAllDay = event.start?.date != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Color indicator dot
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(eventColor)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Time column (fixed-width monospace)
        Column(modifier = Modifier.width(60.dp)) {
            if (isAllDay) {
                Text(
                    text = "ALL DAY",
                    style = monoStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Timestamp
                    )
                )
            } else {
                Text(
                    text = timeRange.first,
                    style = monoStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TerminalColors.Output
                    )
                )
                Text(
                    text = timeRange.second,
                    style = monoStyle.copy(
                        fontSize = 10.sp,
                        color = TerminalColors.Timestamp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Event details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.summary ?: "(No title)",
                style = monoStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TerminalColors.Command
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!event.location.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = TerminalColors.Timestamp,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = event.location,
                        style = monoStyle.copy(
                            fontSize = 10.sp,
                            color = TerminalColors.Timestamp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Chevron
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TerminalColors.Subtext,
            modifier = Modifier
                .size(16.dp)
                .align(Alignment.CenterVertically)
        )
    }
}

// =============================================================================
// Empty State
// =============================================================================

@Composable
private fun EmptyAgenda(monoStyle: TextStyle) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            tint = TerminalColors.Subtext,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "# No events today",
            style = monoStyle.copy(
                fontSize = 12.sp,
                color = TerminalColors.Subtext
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Your calendar is clear. Enjoy the free cycles.",
            style = monoStyle.copy(
                fontSize = 10.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

// =============================================================================
// Error Output
// =============================================================================

@Composable
private fun ErrorOutput(message: String, monoStyle: TextStyle) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "ERR ",
            style = monoStyle.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Error
            )
        )
        Text(
            text = message,
            style = monoStyle.copy(
                fontSize = 10.sp,
                color = TerminalColors.Error
            )
        )
    }
}

// =============================================================================
// Formatting Helpers
// =============================================================================

private fun formatTodayDate(): String {
    val format = SimpleDateFormat("EEE MMM dd yyyy", Locale.US)
    return "# ${format.format(Date())}"
}

/**
 * Extracts start and end times from a CalendarEvent.
 * Returns a Pair of (startTime, endTime) formatted as "HH:mm".
 */
private fun formatEventTimeRange(event: CalendarEvent): Pair<String, String> {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }
    val isoParserMs = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    fun parseDateTime(raw: String?): Date? {
        if (raw == null) return null
        return try {
            isoParserMs.parse(raw)
        } catch (_: Exception) {
            try {
                isoParser.parse(raw)
            } catch (_: Exception) {
                null
            }
        }
    }

    val startDate = parseDateTime(event.start?.dateTime)
    val endDate = parseDateTime(event.end?.dateTime)

    val startStr = startDate?.let { timeFormat.format(it) } ?: "--:--"
    val endStr = endDate?.let { timeFormat.format(it) } ?: "--:--"

    return Pair(startStr, endStr)
}
