package com.castor.app.desktop.systray

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/**
 * Data class representing a single notification entry in the notification shade.
 *
 * @param id Unique identifier for this notification
 * @param appName Name of the app that sent the notification
 * @param title Notification title
 * @param text Notification body text
 * @param timestamp Human-readable timestamp (e.g., "14:32")
 * @param packageName Android package name of the source app
 */
data class NotificationItem(
    val id: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: String,
    val packageName: String
)

/**
 * Desktop notification shade panel that drops down from the notification badge area.
 *
 * Lists recent notifications in a terminal-log style format:
 * `[timestamp] app: message`
 *
 * Features:
 * - Monospace font throughout with dark Surface background
 * - Each notification is clickable (fires onNotificationClick with packageName)
 * - "Do Not Disturb" toggle at the top
 * - "Clear all" button styled as `$ clear-notifications`
 * - Animated entrance via slideInVertically
 *
 * @param isVisible Whether the notification shade is currently shown
 * @param onDismiss Callback to close the shade
 * @param notifications List of notification items to display
 * @param onNotificationClick Callback when a notification is clicked, with the package name
 * @param onClearAll Callback to clear all notifications
 */
@Composable
fun NotificationShade(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    notifications: List<NotificationItem>,
    onNotificationClick: (String) -> Unit,
    onClearAll: () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it })
    ) {
        NotificationShadeContent(
            notifications = notifications,
            onDismiss = onDismiss,
            onNotificationClick = onNotificationClick,
            onClearAll = onClearAll
        )
    }
}

/**
 * Internal content layout for the notification shade.
 *
 * @param notifications List of notification items to display
 * @param onDismiss Callback to close the shade
 * @param onNotificationClick Callback when a notification is clicked
 * @param onClearAll Callback to clear all notifications
 */
@Composable
private fun NotificationShadeContent(
    notifications: List<NotificationItem>,
    onDismiss: () -> Unit,
    onNotificationClick: (String) -> Unit,
    onClearAll: () -> Unit
) {
    var doNotDisturb by remember { mutableStateOf(false) }

    // Dismiss backdrop
    Box(
        modifier = Modifier
            .clickable(onClick = onDismiss)
            .background(TerminalColors.Overlay.copy(alpha = 0.5f))
    ) {
        // Panel content anchored to top-right
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopEnd
        ) {
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .background(TerminalColors.Surface)
                    .padding(16.dp)
            ) {
                // ---- Panel header ----
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = TerminalColors.Accent,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "# notifications",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Accent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${notifications.size}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TerminalColors.Timestamp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Do Not Disturb toggle ----
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(TerminalColors.Background.copy(alpha = 0.5f))
                        .clickable { doNotDisturb = !doNotDisturb }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DoNotDisturb,
                        contentDescription = "Do Not Disturb",
                        tint = if (doNotDisturb) TerminalColors.Warning else TerminalColors.Subtext,
                        modifier = Modifier.size(14.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "do-not-disturb",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (doNotDisturb) TerminalColors.Warning else TerminalColors.Subtext
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (doNotDisturb) TerminalColors.Warning
                                else TerminalColors.Subtext.copy(alpha = 0.3f)
                            )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Separator ----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TerminalColors.Background)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ---- Notification list ----
                if (notifications.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$ no new notifications",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TerminalColors.Timestamp
                            )
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    ) {
                        items(
                            items = notifications,
                            key = { it.id }
                        ) { notification ->
                            NotificationRow(
                                notification = notification,
                                onClick = { onNotificationClick(notification.packageName) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ---- Separator ----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TerminalColors.Background)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ---- Clear all button ----
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onClearAll)
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear all",
                        tint = TerminalColors.Error,
                        modifier = Modifier.size(14.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "$ clear-notifications",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TerminalColors.Error
                        )
                    )
                }
            }
        }
    }
}

/**
 * A single notification entry row styled as a terminal log line.
 *
 * Displays in the format: `[timestamp] appName: title - text`
 *
 * @param notification The notification data to display
 * @param onClick Callback when the notification row is clicked
 */
@Composable
private fun NotificationRow(
    notification: NotificationItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        // Timestamp + app name line
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "[${notification.timestamp}]",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TerminalColors.Timestamp
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = notification.appName,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Accent
                )
            )
        }

        // Title
        Text(
            text = notification.title,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Command
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Body text
        if (notification.text.isNotBlank()) {
            Text(
                text = notification.text,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Output
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Separator
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TerminalColors.Background.copy(alpha = 0.5f))
        )
    }
}
