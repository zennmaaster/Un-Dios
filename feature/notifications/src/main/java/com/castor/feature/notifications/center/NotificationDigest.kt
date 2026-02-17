package com.castor.feature.notifications.center

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

// =====================================================================================
// Data models
// =====================================================================================

/**
 * A single entry in the notification digest/summary view.
 *
 * Represents aggregated notification data for one app over the last 24 hours.
 */
data class NotificationDigestEntry(
    val appName: String,
    val packageName: String,
    val count: Int,
    val latestMessage: String?,
    val latestTimestamp: Long
)

// =====================================================================================
// NotificationDigestView -- $ journalctl --summary
// =====================================================================================

/**
 * Terminal-styled notification digest that shows a per-app summary of
 * notifications over the last 24 hours.
 *
 * Renders as journalctl --summary output with ASCII bar charts showing
 * the relative percentage of notifications per app.
 *
 * @param digestEntries List of digest entries to display, ordered by count descending.
 * @param totalCount Total notification count across all apps (for percentage calculation).
 * @param onEntryClick Callback invoked when user taps a digest entry to drill into that app's notifications.
 */
@Composable
fun NotificationDigestView(
    digestEntries: List<NotificationDigestEntry>,
    totalCount: Int,
    onEntryClick: (packageName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Header
        item(key = "digest_header") {
            DigestHeader(totalCount = totalCount)
        }

        item(key = "digest_divider_top") {
            HorizontalDivider(
                color = TerminalColors.Surface,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // Column headers
        item(key = "digest_columns") {
            DigestColumnHeaders()
        }

        item(key = "digest_separator") {
            Text(
                text = "-".repeat(60),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Surface
                ),
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }

        // Digest entries
        if (digestEntries.isEmpty()) {
            item(key = "digest_empty") {
                DigestEmptyState()
            }
        } else {
            items(
                items = digestEntries,
                key = { "digest_${it.packageName}" }
            ) { entry ->
                DigestEntryRow(
                    entry = entry,
                    totalCount = totalCount,
                    onClick = { onEntryClick(entry.packageName) }
                )
            }

            // Footer summary
            item(key = "digest_footer") {
                DigestFooter(
                    entryCount = digestEntries.size,
                    totalCount = totalCount
                )
            }
        }
    }
}

// =====================================================================================
// Digest header
// =====================================================================================

@Composable
private fun DigestHeader(totalCount: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$ journalctl --summary --since=\"24h ago\"",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Prompt
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Notification digest -- $totalCount total entries in last 24h",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

// =====================================================================================
// Column headers
// =====================================================================================

@Composable
private fun DigestColumnHeaders() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "APP",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Info
            ),
            modifier = Modifier.width(100.dp)
        )

        Text(
            text = "CNT",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Info
            ),
            modifier = Modifier.width(36.dp)
        )

        Text(
            text = "DISTRIBUTION",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Info
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

// =====================================================================================
// Digest entry row with ASCII bar
// =====================================================================================

@Composable
private fun DigestEntryRow(
    entry: NotificationDigestEntry,
    totalCount: Int,
    onClick: () -> Unit
) {
    val percentage = if (totalCount > 0) {
        (entry.count.toFloat() / totalCount * 100).toInt()
    } else {
        0
    }

    val barWidth = 10 // ASCII bar character width
    val filledChars = if (totalCount > 0) {
        (entry.count.toFloat() / totalCount * barWidth).toInt().coerceIn(0, barWidth)
    } else {
        0
    }
    val emptyChars = barWidth - filledChars
    val asciiBar = "=".repeat(filledChars) + " ".repeat(emptyChars)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .animateContentSize(animationSpec = tween(200))
    ) {
        // Main row: app name | count | ASCII bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App name (truncated to fit)
            Text(
                text = entry.appName,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TerminalColors.Command
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(100.dp)
            )

            // Count
            Text(
                text = entry.count.toString().padStart(3),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Accent
                ),
                modifier = Modifier.width(36.dp)
            )

            // ASCII bar: [========  ] 42%
            Text(
                text = "[$asciiBar] ${percentage}%",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = when {
                        percentage >= 40 -> TerminalColors.Error
                        percentage >= 20 -> TerminalColors.Warning
                        else -> TerminalColors.Success
                    }
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }

        // Sub-row: latest message preview + timestamp
        if (!entry.latestMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "  latest: ",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = TerminalColors.Timestamp
                    )
                )

                Text(
                    text = entry.latestMessage,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = TerminalColors.Output
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = formatDigestTimestamp(entry.latestTimestamp),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = TerminalColors.Timestamp
                    )
                )
            }
        }
    }
}

// =====================================================================================
// Footer
// =====================================================================================

@Composable
private fun DigestFooter(
    entryCount: Int,
    totalCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        HorizontalDivider(
            color = TerminalColors.Surface,
            thickness = 1.dp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "-- $entryCount apps, $totalCount notifications --",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Timestamp
            ),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Tap an app to drill into its notifications.",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Subtext
            ),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

// =====================================================================================
// Empty state
// =====================================================================================

@Composable
private fun DigestEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "-- No entries in the last 24 hours --",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Timestamp
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "All quiet on the notification front.",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Subtext
                )
            )
        }
    }
}

// =====================================================================================
// Utilities
// =====================================================================================

/**
 * Formats a timestamp for the digest view: "Feb 17 14:32"
 */
private fun formatDigestTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd HH:mm", Locale.US)
    return sdf.format(Date(timestamp))
}
