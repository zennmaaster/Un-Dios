package com.castor.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.agent.orchestrator.ProactiveSuggestion
import com.castor.agent.orchestrator.SuggestionType
import com.castor.core.ui.theme.TerminalColors

/**
 * Represents a context-aware suggestion that changes based on time of day.
 *
 * @param command The terminal-style command text (e.g., "check-messages")
 * @param label Display label for the suggestion
 * @param accentColor The accent color for the chip
 */
data class ContextSuggestion(
    val command: String,
    val label: String,
    val accentColor: @Composable () -> Color
)

/**
 * Horizontally scrolling row of proactive suggestion chips, combining
 * agent-generated suggestions with context-aware time-of-day suggestions.
 *
 * Time-of-day suggestions:
 * - Morning: "Check messages", "View today's schedule", "Play morning playlist"
 * - Afternoon: "Unread notifications", "Upcoming reminders"
 * - Evening: "Screen time report", "Tomorrow's schedule"
 *
 * Each suggestion chip is styled as a terminal command badge: `$ check-messages`.
 * If there are no agent suggestions AND no time-based suggestions, this composable
 * renders nothing (zero height).
 *
 * @param suggestions List of proactive suggestions from [BriefingViewModel]
 * @param timeOfDay Current time of day: "morning", "afternoon", or "evening"
 * @param onSuggestionClick Callback when an agent suggestion card is tapped
 * @param onDismiss Callback when an agent suggestion is dismissed (receives the list index)
 * @param onCheckMessages Callback when "check-messages" is tapped
 * @param onViewSchedule Callback when "view-schedule" is tapped
 * @param onPlayPlaylist Callback when "play-playlist" is tapped
 * @param onViewNotifications Callback when "view-notifications" is tapped
 * @param onViewReminders Callback when "view-reminders" is tapped
 * @param onScreenTime Callback when "screen-time" is tapped
 * @param modifier Modifier for the root composable
 */
@Composable
fun SuggestionsRow(
    suggestions: List<ProactiveSuggestion>,
    timeOfDay: String,
    onSuggestionClick: (ProactiveSuggestion) -> Unit,
    onDismiss: (Int) -> Unit,
    onCheckMessages: () -> Unit,
    onViewSchedule: () -> Unit,
    onPlayPlaylist: () -> Unit,
    onViewNotifications: () -> Unit,
    onViewReminders: () -> Unit,
    onScreenTime: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Build context-aware suggestions based on time of day
    val contextSuggestions = getContextSuggestions(timeOfDay)

    // If no suggestions at all, render nothing
    if (suggestions.isEmpty() && contextSuggestions.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        // Section header
        SuggestionsHeader()

        Spacer(modifier = Modifier.height(6.dp))

        // Horizontally scrolling row of suggestion chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Leading padding
            Spacer(modifier = Modifier.width(4.dp))

            // Context-aware time-of-day chips (always first)
            contextSuggestions.forEach { ctx ->
                ContextSuggestionChip(
                    command = ctx.command,
                    accentColor = ctx.accentColor(),
                    onClick = {
                        when (ctx.command) {
                            "check-messages" -> onCheckMessages()
                            "view-schedule", "tomorrow-schedule" -> onViewSchedule()
                            "play-playlist" -> onPlayPlaylist()
                            "view-notifications" -> onViewNotifications()
                            "upcoming-reminders" -> onViewReminders()
                            "screen-time" -> onScreenTime()
                        }
                    }
                )
            }

            // Agent-generated suggestion chips
            suggestions.forEachIndexed { index, suggestion ->
                SuggestionChip(
                    suggestion = suggestion,
                    onClick = { onSuggestionClick(suggestion) },
                    onDismiss = { onDismiss(index) }
                )
            }

            // Trailing padding
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

// =====================================================================================
// Context-aware suggestion generation
// =====================================================================================

/**
 * Returns time-of-day context suggestions.
 *
 * - Morning: "Check messages", "View today's schedule", "Play morning playlist"
 * - Afternoon: "Unread notifications", "Upcoming reminders"
 * - Evening: "Screen time report", "Tomorrow's schedule"
 */
@Composable
private fun getContextSuggestions(timeOfDay: String): List<ContextSuggestion> {
    return when (timeOfDay) {
        "morning" -> listOf(
            ContextSuggestion(
                command = "check-messages",
                label = "Check messages",
                accentColor = { TerminalColors.Success }
            ),
            ContextSuggestion(
                command = "view-schedule",
                label = "View today's schedule",
                accentColor = { TerminalColors.Info }
            ),
            ContextSuggestion(
                command = "play-playlist",
                label = "Play morning playlist",
                accentColor = { TerminalColors.Accent }
            )
        )
        "afternoon" -> listOf(
            ContextSuggestion(
                command = "view-notifications",
                label = "Unread notifications",
                accentColor = { TerminalColors.Info }
            ),
            ContextSuggestion(
                command = "upcoming-reminders",
                label = "Upcoming reminders",
                accentColor = { TerminalColors.Warning }
            )
        )
        "evening" -> listOf(
            ContextSuggestion(
                command = "screen-time",
                label = "Screen time report",
                accentColor = { TerminalColors.Accent }
            ),
            ContextSuggestion(
                command = "tomorrow-schedule",
                label = "Tomorrow's schedule",
                accentColor = { TerminalColors.Info }
            )
        )
        else -> emptyList()
    }
}

// =====================================================================================
// Internal composables
// =====================================================================================

/**
 * Section header styled as a terminal command: `$ suggest --proactive`
 */
@Composable
private fun SuggestionsHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = "$ ",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Prompt
            )
        )
        Text(
            text = "suggest --proactive",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

/**
 * A context-aware suggestion chip styled as a terminal command badge: `$ command`.
 */
@Composable
private fun ContextSuggestionChip(
    command: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accentColor.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$ ",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Prompt
                )
            )
            Text(
                text = command,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            )
        }
    }
}

/**
 * A single agent suggestion chip: icon + title + description + dismiss button.
 * Compact, terminal-styled, and tappable.
 */
@Composable
private fun SuggestionChip(
    suggestion: ProactiveSuggestion,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val chipConfig = suggestion.type.toChipConfig()

    Box(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface)
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.width(180.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(chipConfig.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = chipConfig.icon,
                    contentDescription = suggestion.type.name,
                    tint = chipConfig.color,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.title,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = chipConfig.color
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = suggestion.description,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Output
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$ ${suggestion.actionLabel.lowercase().replace(" ", "-")}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Accent
                    )
                )
            }

            // Dismiss button
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss suggestion",
                    tint = TerminalColors.Timestamp,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

// =====================================================================================
// Chip configuration per suggestion type
// =====================================================================================

/**
 * Visual configuration for a suggestion chip based on its [SuggestionType].
 */
private data class ChipConfig(
    val icon: ImageVector,
    val color: Color
)

/**
 * Maps a [SuggestionType] to its visual icon and accent color.
 */
@Composable
private fun SuggestionType.toChipConfig(): ChipConfig = when (this) {
    SuggestionType.UPCOMING_EVENT -> ChipConfig(
        icon = Icons.Default.Event,
        color = TerminalColors.Info
    )
    SuggestionType.UNREAD_MESSAGES -> ChipConfig(
        icon = Icons.Default.ChatBubble,
        color = TerminalColors.Success
    )
    SuggestionType.PENDING_REMINDER -> ChipConfig(
        icon = Icons.Default.Notifications,
        color = TerminalColors.Warning
    )
    SuggestionType.CONTINUE_MEDIA -> ChipConfig(
        icon = Icons.Default.PlayCircle,
        color = TerminalColors.Accent
    )
    SuggestionType.GENERAL -> ChipConfig(
        icon = Icons.Default.AutoAwesome,
        color = TerminalColors.Cursor
    )
}
