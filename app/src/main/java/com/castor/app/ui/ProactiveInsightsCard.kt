package com.castor.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.theme.TerminalColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ======================================================================================
// Data model
// ======================================================================================

/**
 * Priority levels for proactive insights.
 */
enum class InsightPriority { HIGH, MEDIUM, LOW }

/**
 * Agent type tags for display in the insight log line.
 */
enum class InsightAgentTag(val label: String) {
    MESSAGE("msg"),
    MEDIA("media"),
    REMINDER("rem"),
    SYSTEM("sys")
}

/**
 * A single proactive insight item surfaced by the agent layer.
 *
 * @param id Unique identifier for this insight.
 * @param message Human-readable insight text.
 * @param priority The urgency level of this insight.
 * @param agentTag Which agent generated this insight.
 * @param timestamp When the insight was generated (epoch ms).
 * @param actionRoute Optional navigation route; when set, an action button is shown.
 */
data class InsightItem(
    val id: String,
    val message: String,
    val priority: InsightPriority,
    val agentTag: InsightAgentTag,
    val timestamp: Long = System.currentTimeMillis(),
    val actionRoute: String? = null
)

// ======================================================================================
// Main composable
// ======================================================================================

/**
 * Home screen card showing a real-time feed of proactive agent insights.
 *
 * Styled as a terminal log tail: `$ tail -f /var/log/agents.log`.
 *
 * Features:
 * - Terminal-styled header with expand/collapse
 * - Timestamped insight rows with agent badges and priority indicators
 * - Swipe-to-dismiss individual insights
 * - "Clear all" button styled as `$ journalctl --rotate`
 * - Empty state: `$ no active insights -- all systems nominal`
 * - Maximum 5 visible insights with "+N older" expansion
 * - Slide-in animation for new insights
 * - Pulse animation on high-priority insights
 *
 * @param insights List of current insights to display.
 * @param onDismissInsight Callback when the user swipes away an insight.
 * @param onClearAll Callback when the user taps the clear-all button.
 * @param onActionClick Callback when the user taps an insight's action button.
 * @param modifier Modifier for the root composable.
 */
@Composable
fun ProactiveInsightsCard(
    insights: List<InsightItem>,
    onDismissInsight: (String) -> Unit,
    onClearAll: () -> Unit,
    onActionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(true) }
    var showOlder by rememberSaveable { mutableStateOf(false) }

    // Split insights: first 5 are "visible", rest are "older"
    val visibleInsights = if (showOlder) insights else insights.take(5)
    val olderCount = if (insights.size > 5 && !showOlder) insights.size - 5 else 0

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
            // Header: $ tail -f /var/log/agents.log
            // ----------------------------------------------------------------
            InsightsHeader(
                insightCount = insights.size,
                isExpanded = isExpanded,
                onToggleExpanded = { isExpanded = !isExpanded },
                onClearAll = onClearAll
            )

            // ----------------------------------------------------------------
            // Body (collapsible)
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
                    InsightsDivider()

                    Spacer(modifier = Modifier.height(8.dp))

                    if (insights.isEmpty()) {
                        // Empty state
                        EmptyInsightsState()
                    } else {
                        // Insight rows
                        visibleInsights.forEach { insight ->
                            InsightRow(
                                insight = insight,
                                onDismiss = { onDismissInsight(insight.id) },
                                onActionClick = onActionClick
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // "+N older" expansion button
                        if (olderCount > 0) {
                            OlderInsightsButton(
                                count = olderCount,
                                onClick = { showOlder = true }
                            )
                        }

                        // Collapse older button when expanded
                        if (showOlder && insights.size > 5) {
                            CollapseOlderButton(
                                onClick = { showOlder = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ======================================================================================
// Header
// ======================================================================================

/**
 * Terminal-styled header: `$ tail -f /var/log/agents.log`.
 * Includes expand/collapse toggle and clear-all button.
 */
@Composable
private fun InsightsHeader(
    insightCount: Int,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onClearAll: () -> Unit
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
            text = "tail -f /var/log/agents.log",
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

        // Insight count badge
        if (insightCount > 0) {
            Box(
                modifier = Modifier
                    .background(
                        TerminalColors.Accent.copy(alpha = 0.2f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "$insightCount",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Accent
                    )
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Clear all button
        if (insightCount > 0) {
            IconButton(
                onClick = onClearAll,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ClearAll,
                    contentDescription = "Clear all insights",
                    tint = TerminalColors.Timestamp,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

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

// ======================================================================================
// Individual insight row
// ======================================================================================

/**
 * A single insight row with swipe-to-dismiss support.
 *
 * Layout: `[HH:mm:ss] [agent] [priority] message text   [action >]`
 *
 * High-priority insights have a pulsing alpha animation on the priority indicator.
 */
@Composable
private fun InsightRow(
    insight: InsightItem,
    onDismiss: () -> Unit,
    onActionClick: (String) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()

    // Trigger dismiss callback when swiped
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            onDismiss()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Swipe background: red fade indicating dismissal
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(TerminalColors.Error.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "$ rm",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Error
                    )
                )
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true
    ) {
        // Slide-in animation wrapper
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                )
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(200)
            ) + fadeOut(animationSpec = tween(200))
        ) {
            InsightRowContent(
                insight = insight,
                onActionClick = onActionClick
            )
        }
    }
}

/**
 * The actual content of an insight row (timestamp, badge, priority, message, action).
 */
@Composable
private fun InsightRowContent(
    insight: InsightItem,
    onActionClick: (String) -> Unit
) {
    val timestampText = formatInsightTimestamp(insight.timestamp)
    val agentColor = getAgentColor(insight.agentTag)
    val priorityText = getPriorityIndicator(insight.priority)
    val priorityColor = getPriorityColor(insight.priority)

    // Pulse animation for high-priority insights
    val pulseAlpha = if (insight.priority == InsightPriority.HIGH) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse_${insight.id}")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha_${insight.id}"
        )
        alpha
    } else {
        1f
    }

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(TerminalColors.Background.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        // Timestamp
        Text(
            text = "[$timestampText]",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Timestamp
            )
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Agent badge
        Text(
            text = "[${insight.agentTag.label}]",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = agentColor
            )
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Priority indicator with pulse animation
        Text(
            text = priorityText,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = priorityColor
            ),
            modifier = Modifier.alpha(pulseAlpha)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Message text
        Text(
            text = insight.message,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Output
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Action button (if actionRoute is present)
        if (insight.actionRoute != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(TerminalColors.Accent.copy(alpha = 0.15f))
                    .clickable { onActionClick(insight.actionRoute) }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Open",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Accent
                        )
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Open",
                        tint = TerminalColors.Accent,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

// ======================================================================================
// Empty state
// ======================================================================================

/**
 * Empty state when no insights are active.
 * Styled as: `$ no active insights -- all systems nominal`
 */
@Composable
private fun EmptyInsightsState() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
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
            text = "no active insights -- all systems nominal",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Success
            )
        )
    }
}

// ======================================================================================
// Older insights expansion
// ======================================================================================

/**
 * Button to expand and show older insights beyond the first 5.
 * Styled as: `$ +N older entries -- tap to expand`
 */
@Composable
private fun OlderInsightsButton(
    count: Int,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 8.dp)
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
            text = "+$count older entries -- tap to expand",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

/**
 * Button to collapse the older insights back to showing only 5.
 * Styled as: `$ collapse -- show recent only`
 */
@Composable
private fun CollapseOlderButton(
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 8.dp)
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
            text = "collapse -- show recent only",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

// ======================================================================================
// Clear all footer
// ======================================================================================

/**
 * The "clear all" action row styled as a terminal command.
 * This is embedded in the header via the IconButton, but we also provide
 * a tappable footer variant for accessibility.
 */
@Composable
private fun ClearAllFooter(onClearAll: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClearAll)
            .padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Text(
            text = "$ journalctl --rotate",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Warning
            )
        )
    }
}

// ======================================================================================
// Divider
// ======================================================================================

/**
 * Terminal-style horizontal divider for the insights card.
 */
@Composable
private fun InsightsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(TerminalColors.Selection)
    )
}

// ======================================================================================
// Helpers
// ======================================================================================

/**
 * Formats an epoch timestamp as `HH:mm:ss` for the insight log line.
 */
private fun formatInsightTimestamp(timestampMs: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestampMs))
}

/**
 * Returns the color associated with an agent tag.
 * - msg  -> Success (green -- messaging)
 * - media -> Accent (purple -- media)
 * - rem  -> Warning (orange -- reminders)
 * - sys  -> Info (blue -- system)
 */
@Composable
private fun getAgentColor(agentTag: InsightAgentTag): Color {
    return when (agentTag) {
        InsightAgentTag.MESSAGE -> TerminalColors.Success
        InsightAgentTag.MEDIA -> TerminalColors.Accent
        InsightAgentTag.REMINDER -> TerminalColors.Warning
        InsightAgentTag.SYSTEM -> TerminalColors.Info
    }
}

/**
 * Returns the priority indicator string: `[!]` for high, `[i]` for medium, `[ ]` for low.
 */
private fun getPriorityIndicator(priority: InsightPriority): String {
    return when (priority) {
        InsightPriority.HIGH -> "[!]"
        InsightPriority.MEDIUM -> "[i]"
        InsightPriority.LOW -> "[ ]"
    }
}

/**
 * Returns the color for a priority indicator.
 * - HIGH   -> Error (red)
 * - MEDIUM -> Warning (orange)
 * - LOW    -> Timestamp (dim)
 */
@Composable
private fun getPriorityColor(priority: InsightPriority): Color {
    return when (priority) {
        InsightPriority.HIGH -> TerminalColors.Error
        InsightPriority.MEDIUM -> TerminalColors.Warning
        InsightPriority.LOW -> TerminalColors.Timestamp
    }
}
