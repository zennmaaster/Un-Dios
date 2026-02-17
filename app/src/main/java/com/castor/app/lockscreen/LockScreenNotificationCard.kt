package com.castor.app.lockscreen

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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
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

// =============================================================================
// Constants
// =============================================================================

/** Maximum number of notification cards shown on the lock screen. */
private const val MAX_VISIBLE_NOTIFICATIONS = 5

// =============================================================================
// Public composable: notification list for lock screen
// =============================================================================

/**
 * Terminal-styled notification preview section for the lock screen.
 *
 * Shows up to [MAX_VISIBLE_NOTIFICATIONS] real notification cards, each displaying
 * the app badge, sender/title, message preview (truncated to 1 line), and timestamp.
 * High-priority notifications receive a subtle background highlight.
 *
 * If there are more notifications than [MAX_VISIBLE_NOTIFICATIONS], a "+N more"
 * indicator is shown at the bottom.
 *
 * @param notifications The full list of recent unread notifications.
 * @param totalCount Total unread notification count (may exceed [notifications] size).
 * @param onNotificationTap Called when any notification card is tapped.
 *   Intended to unlock the device and navigate to the notification center.
 */
@Composable
fun LockScreenNotificationSection(
    notifications: List<LockScreenNotification>,
    totalCount: Int,
    onNotificationTap: () -> Unit
) {
    val visibleNotifications = notifications.take(MAX_VISIBLE_NOTIFICATIONS)
    val remainingCount = totalCount - visibleNotifications.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Header: styled as a terminal tail command
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = TerminalColors.Info,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$ tail -n ${visibleNotifications.size} /var/log/notifications",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Notification cards
        visibleNotifications.forEach { notification ->
            LockScreenNotificationCard(
                notification = notification,
                onClick = onNotificationTap
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // "+N more" indicator
        if (remainingCount > 0) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "  ... +$remainingCount more",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Subtext
                )
            )
        }
    }
}

// =============================================================================
// Single notification card
// =============================================================================

/**
 * A single terminal-styled notification preview card for the lock screen.
 *
 * Layout: `[AppBadge] Title: content preview          HH:mm`
 *
 * High-priority notifications ("high") get a subtle red-tinted background
 * to visually distinguish them from normal/low priority entries.
 *
 * @param notification The notification data to display.
 * @param onClick Called when this card is tapped.
 */
@Composable
fun LockScreenNotificationCard(
    notification: LockScreenNotification,
    onClick: () -> Unit
) {
    val isHighPriority = notification.priority.equals("high", ignoreCase = true)

    // Determine the app badge color based on category
    val badgeColor = when (notification.category.lowercase()) {
        "social" -> TerminalColors.Accent
        "work" -> TerminalColors.Info
        "media" -> TerminalColors.Success
        "sys" -> TerminalColors.Timestamp
        else -> TerminalColors.Subtext
    }

    // Subtle background for high-priority notifications
    val cardBackground = if (isHighPriority) {
        TerminalColors.Error.copy(alpha = 0.08f)
    } else {
        TerminalColors.Surface.copy(alpha = 0f) // transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(cardBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start
    ) {
        // App badge: first 3 characters of app name in brackets
        val badge = notification.appName
            .take(3)
            .uppercase()

        Text(
            text = "[$badge]",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Title + content preview
        Column(modifier = Modifier.weight(1f)) {
            // Sender / title line
            if (notification.title.isNotBlank()) {
                Text(
                    text = notification.title,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isHighPriority) TerminalColors.Error else TerminalColors.Output
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Content preview (truncated to 1 line)
            if (notification.content.isNotBlank()) {
                Text(
                    text = notification.content,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Subtext
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Timestamp
        Text(
            text = notification.formattedTime,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

// =============================================================================
// Notification count badge
// =============================================================================

/**
 * Terminal-styled notification count badge for the lock screen.
 *
 * Displays as `[3 notifications]` or `[no new notifications]` depending on count.
 *
 * @param count The total unread notification count.
 */
@Composable
fun NotificationCountBadge(count: Int) {
    val label = when {
        count == 0 -> "no new notifications"
        count == 1 -> "1 notification"
        else -> "$count notifications"
    }

    Text(
        text = "[$label]",
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = if (count > 0) FontWeight.Bold else FontWeight.Normal,
            color = if (count > 0) TerminalColors.Info else TerminalColors.Subtext
        )
    )
}
