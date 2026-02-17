package com.castor.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.agent.orchestrator.Briefing
import com.castor.core.data.db.entity.ReminderEntity
import com.castor.core.ui.theme.TerminalColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home screen briefing card styled as a terminal log output block.
 *
 * Header shows `$ cat /var/log/briefing.log` in the prompt style.
 * Body contains:
 * - A real-data briefing summary from [BriefingViewModel]
 * - Collapsible sections for each briefing domain (calendar, messages, reminders, media)
 * - Today's agenda section showing next 3 upcoming reminders with times
 * - Quick action chips: "View Messages", "View Reminders", "View Notifications"
 * - A "Last updated: HH:mm" footer
 *
 * The card uses monospace typography throughout and the Catppuccin Mocha
 * color palette from [TerminalColors].
 *
 * @param briefing The current [Briefing] data (null shows a loading/empty state)
 * @param isRefreshing Whether a refresh is currently in progress
 * @param onRefresh Callback to trigger a manual refresh
 * @param briefingSummary Natural-language summary string from real data
 * @param upcomingReminders Next 3 upcoming reminders with times
 * @param lastUpdatedMs Timestamp of last successful refresh (null if never refreshed)
 * @param onViewMessages Callback when "View Messages" chip is tapped
 * @param onViewReminders Callback when "View Reminders" chip is tapped
 * @param onViewNotifications Callback when "View Notifications" chip is tapped
 * @param modifier Modifier for the root composable
 */
@Composable
fun BriefingCard(
    briefing: Briefing?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    briefingSummary: String,
    upcomingReminders: List<ReminderEntity>,
    lastUpdatedMs: Long?,
    onViewMessages: () -> Unit,
    onViewReminders: () -> Unit,
    onViewNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(true) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = TerminalColors.Surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // ----------------------------------------------------------------
            // Header: $ cat /var/log/briefing.log
            // ----------------------------------------------------------------
            BriefingHeader(
                isExpanded = isExpanded,
                isRefreshing = isRefreshing,
                onToggleExpanded = { isExpanded = !isExpanded },
                onRefresh = onRefresh
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ----------------------------------------------------------------
            // Real-data briefing summary (always visible)
            // ----------------------------------------------------------------
            val timestampPrefix = formatTimestampPrefix()
            Text(
                text = "[$timestampPrefix] $briefingSummary",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Prompt
                )
            )

            // ----------------------------------------------------------------
            // Collapsible body
            // ----------------------------------------------------------------
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                ),
                exit = shrinkVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                )
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Divider
                    TerminalDivider()

                    // ---- Briefing Agent sections (if available) ----
                    if (briefing != null) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Calendar section
                        BriefingLogLine(
                            icon = Icons.Default.Today,
                            label = "calendar",
                            content = briefing.calendarSummary,
                            accentColor = TerminalColors.Info
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Messages section
                        BriefingLogLine(
                            icon = Icons.Default.ChatBubble,
                            label = "messages",
                            content = briefing.messageSummary,
                            accentColor = TerminalColors.Success
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Reminders section
                        BriefingLogLine(
                            icon = Icons.Default.Notifications,
                            label = "reminders",
                            content = briefing.reminderSummary,
                            accentColor = TerminalColors.Warning
                        )

                        // Media section (optional)
                        val media = briefing.mediaSuggestion
                        if (media != null) {
                            Spacer(modifier = Modifier.height(6.dp))

                            BriefingLogLine(
                                icon = Icons.Default.MusicNote,
                                label = "media",
                                content = media,
                                accentColor = TerminalColors.Accent
                            )
                        }
                    } else if (isRefreshing) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "[$timestampPrefix] Generating briefing...",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TerminalColors.Timestamp
                            )
                        )
                    }

                    // ---- Today's Agenda: next 3 upcoming reminders ----
                    if (upcomingReminders.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        TerminalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        AgendaSection(reminders = upcomingReminders)
                    }

                    // ---- Quick action chips ----
                    Spacer(modifier = Modifier.height(10.dp))
                    TerminalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    QuickActionChips(
                        onViewMessages = onViewMessages,
                        onViewReminders = onViewReminders,
                        onViewNotifications = onViewNotifications
                    )

                    // ---- Last updated footer ----
                    Spacer(modifier = Modifier.height(8.dp))
                    LastUpdatedFooter(lastUpdatedMs = lastUpdatedMs)
                }
            }
        }
    }
}

// =====================================================================================
// Internal composables
// =====================================================================================

/**
 * Header row: terminal prompt + expand/collapse + refresh controls.
 * Styled as `$ cat /var/log/briefing.log`.
 */
@Composable
private fun BriefingHeader(
    isExpanded: Boolean,
    isRefreshing: Boolean,
    onToggleExpanded: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
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
            text = "cat /var/log/briefing.log",
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

        // Refresh button
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = TerminalColors.Accent,
                strokeWidth = 2.dp
            )
        } else {
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh briefing",
                    tint = TerminalColors.Timestamp,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Expand/collapse toggle
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

/**
 * A single briefing log line with timestamp prefix: `[HH:mm] [label] content`.
 * Styled as terminal log output.
 */
@Composable
private fun BriefingLogLine(
    icon: ImageVector,
    label: String,
    content: String,
    accentColor: androidx.compose.ui.graphics.Color
) {
    val timestamp = formatTimestampPrefix()
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accentColor,
            modifier = Modifier
                .size(14.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = "[$timestamp] [$label]",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            )
            Text(
                text = content,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Output
                ),
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

/**
 * Today's agenda section showing the next 3 upcoming reminders with times.
 * Styled as a terminal agenda listing.
 */
@Composable
private fun AgendaSection(reminders: List<ReminderEntity>) {
    // Section header
    Text(
        text = "# today's agenda",
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TerminalColors.Accent
        )
    )

    Spacer(modifier = Modifier.height(4.dp))

    reminders.take(3).forEach { reminder ->
        val time = formatReminderTime(reminder.triggerTimeMs)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = TerminalColors.Warning,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = time,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Info
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = reminder.description,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Output
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Horizontally scrolling row of quick action chips.
 * Each chip is styled as a terminal command: `$ view-messages`.
 */
@Composable
private fun QuickActionChips(
    onViewMessages: () -> Unit,
    onViewReminders: () -> Unit,
    onViewNotifications: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActionChip(
            label = "view-messages",
            accentColor = TerminalColors.Success,
            onClick = onViewMessages
        )
        ActionChip(
            label = "view-reminders",
            accentColor = TerminalColors.Warning,
            onClick = onViewReminders
        )
        ActionChip(
            label = "view-notifications",
            accentColor = TerminalColors.Info,
            onClick = onViewNotifications
        )
    }
}

/**
 * A single quick action chip styled as a terminal command badge: `$ label`.
 */
@Composable
private fun ActionChip(
    label: String,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(accentColor.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                text = label,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            )
        }
    }
}

/**
 * Horizontal terminal-style divider.
 */
@Composable
private fun TerminalDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(TerminalColors.Selection)
    )
}

/**
 * Footer showing when the briefing was last updated: `# last updated: HH:mm`.
 */
@Composable
private fun LastUpdatedFooter(lastUpdatedMs: Long?) {
    val timeText = if (lastUpdatedMs != null) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(lastUpdatedMs))
    } else {
        "--:--"
    }
    Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "# last updated: $timeText",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

// =====================================================================================
// Formatting helpers
// =====================================================================================

/**
 * Formats the current time as a log-style timestamp prefix: "HH:mm:ss".
 */
private fun formatTimestampPrefix(): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date())
}

/**
 * Formats a reminder trigger timestamp as "HH:mm" for the agenda display.
 */
private fun formatReminderTime(triggerTimeMs: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(triggerTimeMs))
}
