package com.castor.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.agent.orchestrator.Briefing
import com.castor.core.ui.theme.TerminalColors

/**
 * Home screen briefing card styled as a terminal output block.
 *
 * Header shows `$ briefing --today` in the prompt style.
 * Body contains collapsible sections for each briefing domain:
 * calendar, messages, reminders, and media.
 *
 * The card uses monospace typography throughout and the Catppuccin Mocha
 * color palette from [TerminalColors].
 *
 * @param briefing The current [Briefing] data (null shows a loading/empty state)
 * @param isRefreshing Whether a refresh is currently in progress
 * @param onRefresh Callback to trigger a manual refresh
 * @param modifier Modifier for the root composable
 */
@Composable
fun BriefingCard(
    briefing: Briefing?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
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
            // Header: $ briefing --today
            // ----------------------------------------------------------------
            BriefingHeader(
                isExpanded = isExpanded,
                isRefreshing = isRefreshing,
                onToggleExpanded = { isExpanded = !isExpanded },
                onRefresh = onRefresh
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ----------------------------------------------------------------
            // Greeting
            // ----------------------------------------------------------------
            if (briefing != null) {
                Text(
                    text = briefing.greeting,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Prompt
                    )
                )
            } else {
                Text(
                    text = if (isRefreshing) "Generating briefing..." else "No briefing available.",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalColors.Timestamp
                    )
                )
            }

            // ----------------------------------------------------------------
            // Collapsible body
            // ----------------------------------------------------------------
            AnimatedVisibility(
                visible = isExpanded && briefing != null,
                enter = expandVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                ),
                exit = shrinkVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                )
            ) {
                if (briefing != null) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Divider
                        TerminalDivider()

                        Spacer(modifier = Modifier.height(8.dp))

                        // Calendar section
                        BriefingSection(
                            icon = Icons.Default.Today,
                            label = "calendar",
                            content = briefing.calendarSummary,
                            accentColor = TerminalColors.Info
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Messages section
                        BriefingSection(
                            icon = Icons.Default.ChatBubble,
                            label = "messages",
                            content = briefing.messageSummary,
                            accentColor = TerminalColors.Success
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Reminders section
                        BriefingSection(
                            icon = Icons.Default.Notifications,
                            label = "reminders",
                            content = briefing.reminderSummary,
                            accentColor = TerminalColors.Warning
                        )

                        // Media section (optional)
                        if (briefing.mediaSuggestion != null) {
                            Spacer(modifier = Modifier.height(6.dp))

                            BriefingSection(
                                icon = Icons.Default.MusicNote,
                                label = "media",
                                content = briefing.mediaSuggestion,
                                accentColor = TerminalColors.Accent
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Timestamp footer
                        BriefingTimestamp(briefing.generatedAt)
                    }
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
            text = "briefing --today",
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
 * A single briefing section: icon + label prefix + content text.
 * Styled as a terminal key-value line.
 */
@Composable
private fun BriefingSection(
    icon: ImageVector,
    label: String,
    content: String,
    accentColor: androidx.compose.ui.graphics.Color
) {
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
                text = "[$label]",
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
 * Footer showing when the briefing was generated, formatted as a terminal comment.
 */
@Composable
private fun BriefingTimestamp(generatedAt: Long) {
    val timeAgo = formatBriefingAge(generatedAt)
    Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "# generated $timeAgo",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

/**
 * Formats a timestamp into a human-readable relative time string.
 */
private fun formatBriefingAge(timestampMs: Long): String {
    val deltaMs = System.currentTimeMillis() - timestampMs
    val minutes = deltaMs / (1000 * 60)
    val hours = minutes / 60

    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}min ago"
        hours < 24 -> "${hours}h ago"
        else -> {
            val days = hours / 24
            "${days}d ago"
        }
    }
}
