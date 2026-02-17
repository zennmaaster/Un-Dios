package com.castor.app.desktop.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.app.desktop.window.DesktopWindow
import com.castor.app.desktop.window.WindowState
import com.castor.core.ui.components.SystemStats
import com.castor.core.ui.theme.TerminalColors

/**
 * Desktop mode bottom taskbar — analogous to the GNOME bottom panel or Windows taskbar.
 *
 * Layout (left to right):
 * 1. **Activities button**: Opens the GNOME-style overview of all windows
 * 2. **Running window tabs**: Each open window shown as a clickable tab
 * 3. **Spacer**
 * 4. **System tray**: WiFi, Bluetooth, Volume, Battery indicators
 * 5. **Notification badge**: Count of unread notifications
 * 6. **Clock**: Current time in monospace font
 *
 * The taskbar uses the same dark overlay background as the dock and status bar,
 * maintaining the terminal aesthetic throughout the desktop environment.
 *
 * @param windows List of all open desktop windows
 * @param activeWindowId Currently focused window ID
 * @param systemStats System statistics for the tray indicators
 * @param onActivitiesClick Open the Activities/overview screen
 * @param onWindowClick Focus the clicked window
 * @param modifier Modifier for the taskbar container
 */
@Composable
fun DesktopTaskbar(
    windows: List<DesktopWindow>,
    activeWindowId: String?,
    systemStats: SystemStats,
    onActivitiesClick: () -> Unit,
    onWindowClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(TerminalColors.StatusBar)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ---- Activities button ----
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(TerminalColors.Surface.copy(alpha = 0.5f))
                .clickable(onClick = onActivitiesClick)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Dashboard,
                    contentDescription = "Activities",
                    tint = TerminalColors.Accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "activities",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Accent
                    )
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // ---- Separator ----
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(TerminalColors.Surface)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // ---- Running window tabs ----
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            windows.forEach { window ->
                TaskbarWindowTab(
                    window = window,
                    isActive = window.id == activeWindowId,
                    onClick = { onWindowClick(window.id) }
                )
            }
        }

        // ---- System tray ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Volume
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Volume",
                tint = TerminalColors.Subtext,
                modifier = Modifier.size(13.dp)
            )

            // WiFi
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = "WiFi",
                tint = if (systemStats.wifiConnected) TerminalColors.Success
                else TerminalColors.Subtext,
                modifier = Modifier.size(13.dp)
            )

            // Bluetooth
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = "Bluetooth",
                tint = if (systemStats.bluetoothConnected) TerminalColors.Info
                else TerminalColors.Subtext,
                modifier = Modifier.size(13.dp)
            )

            // Battery
            Icon(
                imageVector = if (systemStats.isCharging) Icons.Default.BatteryChargingFull
                else Icons.Default.BatteryFull,
                contentDescription = "Battery",
                tint = getBatteryColor(systemStats.batteryPercent),
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "${systemStats.batteryPercent}%",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = getBatteryColor(systemStats.batteryPercent)
                )
            )

            // Separator
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(TerminalColors.Surface)
            )

            // External display indicator
            Icon(
                imageVector = Icons.Default.Monitor,
                contentDescription = "External display",
                tint = TerminalColors.Accent,
                modifier = Modifier.size(13.dp)
            )

            // Notification badge
            if (systemStats.unreadNotifications > 0) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(TerminalColors.BadgeRed.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = systemStats.unreadNotifications.toString(),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.BadgeRed
                        )
                    )
                }
            }

            // Separator
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(TerminalColors.Surface)
            )

            // Clock
            Text(
                text = systemStats.currentTime,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TerminalColors.Command
                )
            )
        }
    }
}

/**
 * A single window tab in the taskbar showing the window's icon, title,
 * and active/minimized state.
 */
@Composable
private fun TaskbarWindowTab(
    window: DesktopWindow,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isActive -> TerminalColors.Surface
        window.state == WindowState.Minimized -> TerminalColors.Overlay
        else -> TerminalColors.Surface.copy(alpha = 0.3f)
    }

    val textColor = when {
        isActive -> TerminalColors.Command
        window.state == WindowState.Minimized -> TerminalColors.Subtext
        else -> TerminalColors.Timestamp
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = window.icon,
            contentDescription = null,
            tint = if (isActive) TerminalColors.Accent else TerminalColors.Timestamp,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = window.title,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Active indicator
        if (isActive) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(TerminalColors.Accent)
            )
        }
    }
}

/**
 * Returns a color based on battery percentage.
 */
private fun getBatteryColor(percent: Int): Color = when {
    percent <= 15 -> TerminalColors.Error
    percent <= 30 -> TerminalColors.Warning
    else -> TerminalColors.Success
}
