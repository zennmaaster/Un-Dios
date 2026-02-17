package com.castor.app.desktop.layout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.theme.TerminalColors

/**
 * Ubuntu-style vertical dock on the left side of the desktop layout.
 *
 * Displays favorite/pinned application shortcuts as icon buttons arranged
 * vertically, with dot indicators for currently running windows. The dock
 * sits on a dark semi-transparent background, consistent with the terminal
 * aesthetic, and includes an app drawer launcher button at the bottom.
 *
 * Layout (top to bottom):
 * 1. Pinned/favorite app icons with running indicators
 * 2. Flexible spacer
 * 3. App drawer button (Activities / grid icon)
 *
 * Styling:
 * - Dark overlay background matching the Ubuntu dock
 * - Monospace labels on hover (simulated via long-press tooltip)
 * - Accent-colored dot indicators for running apps
 * - Rounded corners on the dock container
 *
 * @param runningWindowIds Set of window IDs that are currently open
 * @param activeWindowId The currently focused window ID (null if none)
 * @param onOpenTerminal Open/focus the terminal window
 * @param onOpenMessages Open/focus the messages window
 * @param onOpenMedia Open/focus the media window
 * @param onOpenReminders Open/focus the reminders window
 * @param onOpenAI Open/focus the AI engine window
 * @param onOpenAppDrawer Open the app drawer overlay
 * @param modifier Modifier for the dock container
 */
@Composable
fun DesktopDock(
    runningWindowIds: Set<String>,
    activeWindowId: String?,
    onOpenTerminal: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenAI: () -> Unit,
    onOpenAppDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(56.dp)
            .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
            .background(TerminalColors.Overlay.copy(alpha = 0.95f))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // ---- Pinned apps ----
        DockIcon(
            icon = Icons.Default.Terminal,
            label = "term",
            tint = TerminalColors.Accent,
            isRunning = "terminal" in runningWindowIds,
            isActive = activeWindowId == "terminal",
            onClick = onOpenTerminal
        )

        DockIcon(
            icon = Icons.Default.ChatBubble,
            label = "msgs",
            tint = TerminalColors.Success,
            isRunning = "messages" in runningWindowIds,
            isActive = activeWindowId == "messages",
            onClick = onOpenMessages
        )

        DockIcon(
            icon = Icons.Default.Album,
            label = "media",
            tint = TerminalColors.Info,
            isRunning = "media" in runningWindowIds,
            isActive = activeWindowId == "media",
            onClick = onOpenMedia
        )

        DockIcon(
            icon = Icons.Default.Notifications,
            label = "tasks",
            tint = TerminalColors.Warning,
            isRunning = "reminders" in runningWindowIds,
            isActive = activeWindowId == "reminders",
            onClick = onOpenReminders
        )

        DockIcon(
            icon = Icons.Default.SmartToy,
            label = "ai",
            tint = TerminalColors.Accent,
            isRunning = "ai-engine" in runningWindowIds,
            isActive = activeWindowId == "ai-engine",
            onClick = onOpenAI
        )

        // ---- Spacer to push app drawer to bottom ----
        Spacer(modifier = Modifier.weight(1f))

        // ---- Separator ----
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(1.dp)
                .background(TerminalColors.Surface)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ---- App drawer / activities button ----
        DockIcon(
            icon = Icons.Default.Apps,
            label = "apps",
            tint = TerminalColors.Command,
            isRunning = false,
            isActive = false,
            onClick = onOpenAppDrawer
        )

        Spacer(modifier = Modifier.height(4.dp))
    }
}

/**
 * A single icon button in the desktop dock.
 *
 * Renders the app icon with optional running/active indicators:
 * - A small dot on the left side indicates the app has an open window
 * - A brighter tint and bolder dot indicate the app is currently focused
 * - The monospace label is always visible below the icon at small size
 *
 * @param icon The Material icon to display
 * @param label Short monospace label shown below the icon
 * @param tint Base tint color for the icon
 * @param isRunning Whether this app has an open window
 * @param isActive Whether this app's window is currently focused
 * @param onClick Action when the icon is tapped
 */
@Composable
private fun DockIcon(
    icon: ImageVector,
    label: String,
    tint: Color,
    isRunning: Boolean,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val iconTint by animateColorAsState(
        targetValue = when {
            isActive -> tint
            isRunning -> tint.copy(alpha = 0.7f)
            else -> TerminalColors.Subtext
        },
        animationSpec = tween(durationMillis = 200),
        label = "${label}Tint"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .height(48.dp)
    ) {
        // Running indicator dot (left side)
        if (isRunning) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(if (isActive) 4.dp else 3.dp)
                    .clip(CircleShape)
                    .background(if (isActive) tint else tint.copy(alpha = 0.5f))
            )
        }

        // Icon + label
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = label,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) tint else TerminalColors.Timestamp
                )
            )
        }
    }
}
