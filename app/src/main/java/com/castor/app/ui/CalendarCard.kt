package com.castor.app.ui

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.castor.core.ui.theme.TerminalColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Maximum number of events displayed in the card. If there are more, a
 * "+N more" indicator is shown at the bottom.
 */
private const val MAX_VISIBLE_EVENTS = 5

// =============================================================================
// Public Composable
// =============================================================================

/**
 * Home screen card showing today's calendar agenda from the Android
 * [CalendarContract] content provider.
 *
 * Styled as terminal output of `$ gcal --agenda`. Shows today's date,
 * up to [MAX_VISIBLE_EVENTS] events (all-day first, then timed), and a
 * "view all" link to the reminders screen.
 *
 * @param calendarViewModel ViewModel that reads events from the content provider.
 * @param onViewAll          Callback to navigate to the full reminders / calendar screen.
 * @param modifier           Modifier for the root composable.
 */
@Composable
fun CalendarCard(
    calendarViewModel: CalendarViewModel,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by calendarViewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Refresh events every time the screen resumes (e.g. returning from
    // the calendar app after editing an event).
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            calendarViewModel.refreshEvents()
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            calendarViewModel.refreshEvents()
        }
    }

    var isExpanded by rememberSaveable { mutableStateOf(true) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = TerminalColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // ----------------------------------------------------------------
            // Header: $ gcal --agenda
            // ----------------------------------------------------------------
            CalendarCardHeader(
                isExpanded = isExpanded,
                isLoading = uiState is CalendarUiState.Loading,
                onToggleExpanded = { isExpanded = !isExpanded },
                onRefresh = { calendarViewModel.refreshEvents() }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Date sub-header: # Mon, Feb 17 2026
            Text(
                text = formatTodayDateHeader(),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Accent
                )
            )

            // ----------------------------------------------------------------
            // Collapsible body
            // ----------------------------------------------------------------
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    CalendarDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    when (val state = uiState) {
                        is CalendarUiState.PermissionRequired -> {
                            PermissionRequiredBlock(
                                onRequestPermission = {
                                    permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                                }
                            )
                        }

                        is CalendarUiState.Loading -> {
                            LoadingBlock()
                        }

                        is CalendarUiState.Error -> {
                            ErrorBlock(message = state.message)
                        }

                        is CalendarUiState.Success -> {
                            if (state.events.isEmpty()) {
                                EmptyBlock()
                            } else {
                                EventsList(
                                    events = state.events,
                                    onEventClick = { event ->
                                        openEventInCalendarApp(context, event.eventId)
                                    }
                                )
                            }
                        }
                    }

                    // ---- View all footer ----
                    Spacer(modifier = Modifier.height(10.dp))
                    CalendarDivider()
                    Spacer(modifier = Modifier.height(6.dp))
                    ViewAllFooter(onClick = onViewAll)
                }
            }
        }
    }
}

// =============================================================================
// Header
// =============================================================================

@Composable
private fun CalendarCardHeader(
    isExpanded: Boolean,
    isLoading: Boolean,
    onToggleExpanded: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.CalendarMonth,
            contentDescription = null,
            tint = TerminalColors.Info,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))

        // Terminal prompt
        Text(
            text = "$ ",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Prompt
            )
        )
        Text(
            text = "gcal --agenda",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Command
            ),
            modifier = Modifier
                .weight(1f)
                .clickable { onToggleExpanded() }
        )

        // Refresh button / spinner
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 1.5.dp,
                color = TerminalColors.Accent
            )
        } else {
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh calendar",
                    tint = TerminalColors.Timestamp,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Expand / collapse
        IconButton(
            onClick = onToggleExpanded,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = TerminalColors.Timestamp,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// =============================================================================
// Events List
// =============================================================================

@Composable
private fun EventsList(
    events: List<CalendarEvent>,
    onEventClick: (CalendarEvent) -> Unit
) {
    val visible = events.take(MAX_VISIBLE_EVENTS)
    val overflow = events.size - visible.size

    visible.forEachIndexed { index, event ->
        EventRow(event = event, onClick = { onEventClick(event) })
        if (index < visible.lastIndex) {
            Spacer(modifier = Modifier.height(2.dp))
        }
    }

    if (overflow > 0) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "  +$overflow more event${if (overflow > 1) "s" else ""}",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

@Composable
private fun EventRow(
    event: CalendarEvent,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Calendar color dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(event.calendarColor)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Time range or ALL DAY badge
        if (event.isAllDay) {
            Text(
                text = "[ALL DAY]",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Warning
                )
            )
        } else {
            Text(
                text = formatTimeRange(event.startTime, event.endTime),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = TerminalColors.Info
                )
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Event title
        Text(
            text = event.title,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TerminalColors.Command
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Chevron
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TerminalColors.Subtext,
            modifier = Modifier.size(14.dp)
        )
    }
}

// =============================================================================
// State Blocks (Permission, Loading, Error, Empty)
// =============================================================================

@Composable
private fun PermissionRequiredBlock(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onRequestPermission)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = TerminalColors.Warning,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "# calendar permission required",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Warning
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tap to grant READ_CALENDAR access",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

@Composable
private fun LoadingBlock() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.5.dp,
            color = TerminalColors.Accent
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "querying calendar provider...",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Accent
            )
        )
    }
}

@Composable
private fun ErrorBlock(message: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(
            text = "ERR ",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Error
            )
        )
        Text(
            text = message,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Error
            )
        )
    }
}

@Composable
private fun EmptyBlock() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            tint = TerminalColors.Subtext,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "# no events scheduled today",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Subtext
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Your calendar is clear. Enjoy the free cycles.",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

// =============================================================================
// View All Footer
// =============================================================================

@Composable
private fun ViewAllFooter(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$ ",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Prompt
            )
        )
        Text(
            text = "view-all --reminders",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Info
            )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "View all",
            tint = TerminalColors.Info,
            modifier = Modifier.size(14.dp)
        )
    }
}

// =============================================================================
// Divider
// =============================================================================

@Composable
private fun CalendarDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(TerminalColors.Selection)
    )
}

// =============================================================================
// Formatting Helpers
// =============================================================================

/**
 * Formats today's date as: `# Mon, Feb 17 2026`
 */
private fun formatTodayDateHeader(): String {
    val sdf = SimpleDateFormat("EEE, MMM dd yyyy", Locale.US)
    return "# ${sdf.format(Date())}"
}

/**
 * Formats a start/end epoch pair as `HH:mm-HH:mm`.
 */
private fun formatTimeRange(startMs: Long, endMs: Long): String {
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return "${fmt.format(Date(startMs))}-${fmt.format(Date(endMs))}"
}

/**
 * Launches the default calendar app to show a specific event.
 */
private fun openEventInCalendarApp(context: android.content.Context, eventId: Long) {
    try {
        val uri: Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = uri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        // Gracefully ignore if no calendar app can handle the intent
    }
}
