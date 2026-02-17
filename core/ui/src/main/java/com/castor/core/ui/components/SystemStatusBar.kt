package com.castor.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.theme.TerminalColors

/**
 * Real-time system statistics data displayed in the status bar.
 * In production, this will be populated by a ViewModel reading actual system info.
 */
data class SystemStats(
    val cpuUsage: Float = 0f,
    val ramUsage: Float = 0f,
    val ramUsedMb: Long = 0,
    val ramTotalMb: Long = 0,
    val batteryPercent: Int = 0,
    val isCharging: Boolean = false,
    val wifiConnected: Boolean = false,
    val bluetoothConnected: Boolean = false,
    val unreadNotifications: Int = 0,
    val currentTime: String = ""
)

/**
 * Ubuntu-style top panel status bar.
 *
 * Always renders with a dark background regardless of the current theme,
 * mimicking the persistent dark panel in Ubuntu's GNOME desktop.
 *
 * Layout:
 *   Left   — "Castor" branding + current time
 *   Center — Quick status icons (wifi, bluetooth, notification count)
 *   Right  — CPU % | RAM % | Battery % with compact progress indicators
 */
@Composable
fun SystemStatusBar(
    stats: SystemStats,
    modifier: Modifier = Modifier,
    onNotificationClick: (() -> Unit)? = null
) {
    val monoStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = TerminalColors.Command
    )

    val dimStyle = monoStyle.copy(
        color = TerminalColors.Timestamp,
        fontSize = 10.sp
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TerminalColors.StatusBar)
            .height(34.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // ---- Left section: Branding + Time ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "un-dios",
                style = monoStyle.copy(
                    color = TerminalColors.Accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stats.currentTime,
                style = monoStyle
            )
        }

        // ---- Center section: Status icons ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            // Wifi indicator
            Icon(
                imageVector = if (stats.wifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                contentDescription = if (stats.wifiConnected) "WiFi connected" else "WiFi disconnected",
                tint = if (stats.wifiConnected) TerminalColors.Success else TerminalColors.Subtext,
                modifier = Modifier.size(14.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Bluetooth indicator
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = "Bluetooth",
                tint = if (stats.bluetoothConnected) TerminalColors.Info else TerminalColors.Subtext,
                modifier = Modifier.size(14.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Notification count — tappable to open notification center
            if (stats.unreadNotifications > 0) {
                Box(
                    modifier = Modifier
                        .background(
                            TerminalColors.BadgeRed.copy(alpha = 0.2f),
                            RoundedCornerShape(4.dp)
                        )
                        .then(
                            if (onNotificationClick != null) {
                                Modifier.clickable(onClick = onNotificationClick)
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = TerminalColors.BadgeRed,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = stats.unreadNotifications.toString(),
                            style = monoStyle.copy(
                                color = TerminalColors.BadgeRed,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "No notifications",
                    tint = TerminalColors.Subtext,
                    modifier = Modifier
                        .size(14.dp)
                        .then(
                            if (onNotificationClick != null) {
                                Modifier.clickable(onClick = onNotificationClick)
                            } else {
                                Modifier
                            }
                        )
                )
            }
        }

        // ---- Right section: System stats ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.weight(1.5f)
        ) {
            // CPU usage
            StatIndicator(
                label = "CPU",
                value = stats.cpuUsage,
                displayText = "${stats.cpuUsage.toInt()}%",
                color = getStatColor(stats.cpuUsage),
                textStyle = dimStyle
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Separator
            Text(text = "|", style = dimStyle.copy(color = TerminalColors.Subtext))

            Spacer(modifier = Modifier.width(8.dp))

            // RAM usage
            StatIndicator(
                label = "RAM",
                value = stats.ramUsage,
                displayText = "${stats.ramUsage.toInt()}%",
                color = getStatColor(stats.ramUsage),
                textStyle = dimStyle
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Separator
            Text(text = "|", style = dimStyle.copy(color = TerminalColors.Subtext))

            Spacer(modifier = Modifier.width(8.dp))

            // Battery
            Icon(
                imageVector = if (stats.isCharging) {
                    Icons.Default.BatteryChargingFull
                } else {
                    Icons.Default.BatteryFull
                },
                contentDescription = "Battery",
                tint = getBatteryColor(stats.batteryPercent),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "${stats.batteryPercent}%",
                style = dimStyle.copy(color = getBatteryColor(stats.batteryPercent))
            )
        }
    }
}

/**
 * Compact stat indicator showing a label, a tiny progress bar, and a percentage value.
 */
@Composable
private fun StatIndicator(
    label: String,
    value: Float,
    displayText: String,
    color: Color,
    textStyle: TextStyle
) {
    val animatedProgress by animateFloatAsState(
        targetValue = value / 100f,
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "${label}Progress"
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = textStyle)
        Spacer(modifier = Modifier.width(4.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .width(24.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = TerminalColors.Surface,
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = displayText, style = textStyle.copy(color = color))
    }
}

/**
 * Returns a color based on how high a system stat (CPU/RAM) percentage is.
 * Green for low usage, orange for moderate, red for high.
 */
private fun getStatColor(percent: Float): Color = when {
    percent < 50f -> TerminalColors.Success
    percent < 80f -> TerminalColors.Warning
    else -> TerminalColors.Error
}

/**
 * Returns a color based on battery percentage.
 * Red for critically low, orange for low, green for healthy.
 */
private fun getBatteryColor(percent: Int): Color = when {
    percent <= 15 -> TerminalColors.Error
    percent <= 30 -> TerminalColors.Warning
    else -> TerminalColors.Success
}
