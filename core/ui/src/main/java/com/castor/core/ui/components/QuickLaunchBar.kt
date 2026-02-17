package com.castor.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.theme.TerminalColors

/**
 * Ubuntu-style dock / launcher bar at the bottom of the screen.
 *
 * Provides quick access to core Castor features: Messages, Media, Reminders,
 * Terminal, and the App Drawer. Styled as a semi-transparent dark bar similar
 * to the Ubuntu Dock or macOS Dock, but with monospace labeling to maintain
 * the power-user terminal aesthetic.
 *
 * @param onMessages Navigate to the Messages agent
 * @param onMedia Navigate to the Media agent
 * @param onReminders Navigate to the Reminders agent
 * @param onAppDrawer Open the app drawer / grid
 * @param onTerminal Focus the terminal / bring it to front
 * @param unreadMessages Badge count for unread messages
 * @param modifier Modifier for the root composable
 */
@Composable
fun QuickLaunchBar(
    onMessages: () -> Unit,
    onMedia: () -> Unit,
    onReminders: () -> Unit,
    onAppDrawer: () -> Unit,
    onTerminal: () -> Unit,
    unreadMessages: Int = 0,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(TerminalColors.Overlay.copy(alpha = 0.92f))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockItem(
            icon = Icons.Default.ChatBubble,
            label = "msgs",
            onClick = onMessages,
            badgeCount = unreadMessages,
            tint = TerminalColors.Success
        )

        DockItem(
            icon = Icons.Default.MusicNote,
            label = "media",
            onClick = onMedia,
            tint = TerminalColors.Info
        )

        DockItem(
            icon = Icons.Default.Terminal,
            label = "term",
            onClick = onTerminal,
            tint = TerminalColors.Accent,
            isHighlighted = true
        )

        DockItem(
            icon = Icons.Default.Notifications,
            label = "tasks",
            onClick = onReminders,
            tint = TerminalColors.Warning
        )

        DockItem(
            icon = Icons.Default.Apps,
            label = "apps",
            onClick = onAppDrawer,
            tint = TerminalColors.Command
        )
    }
}

/**
 * A single item in the dock: icon button with a small label below it,
 * and an optional notification badge.
 *
 * @param icon The Material icon to display
 * @param label Short monospace label shown below the icon
 * @param onClick Action when the item is tapped
 * @param badgeCount Optional notification badge count (0 = no badge)
 * @param tint Icon tint color
 * @param isHighlighted Whether to show a highlight indicator (for the active/primary item)
 */
@Composable
private fun DockItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    badgeCount: Int = 0,
    tint: androidx.compose.ui.graphics.Color = TerminalColors.Command,
    isHighlighted: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Notification badge
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-2).dp, y = 4.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(TerminalColors.BadgeRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 99) "99" else badgeCount.toString(),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Background
                        )
                    )
                }
            }
        }

        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                color = if (isHighlighted) TerminalColors.Accent else TerminalColors.Timestamp
            )
        )

        // Active indicator dot
        if (isHighlighted) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(TerminalColors.Accent)
            )
        }
    }
}
