package com.castor.app.desktop.systray

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.castor.core.ui.components.SystemStats
import com.castor.core.ui.theme.TerminalColors

/**
 * Expandable system tray panel that drops down from the top-right corner of the taskbar.
 *
 * Provides quick-access toggles and sliders for common system settings, styled
 * with the terminal aesthetic. Each toggle is rendered as a terminal-styled row
 * with an icon, label, and on/off status indicator (green dot = on, dim = off).
 *
 * Includes:
 * - WiFi toggle
 * - Bluetooth toggle
 * - Volume slider
 * - Brightness slider
 * - Do Not Disturb toggle
 * - Battery details (percentage, estimated time)
 * - Night Light toggle
 *
 * All states are placeholder (remember { mutableStateOf }) -- real system
 * integration would come in a later phase.
 *
 * @param isVisible Whether the system tray panel is currently shown
 * @param onDismiss Callback to close the panel
 * @param systemStats Current system statistics for initial state display
 * @param onOpenSettings Callback to open the full settings screen
 */
@Composable
fun SystemTrayPanel(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    systemStats: SystemStats,
    onOpenSettings: () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it })
    ) {
        SystemTrayPanelContent(
            systemStats = systemStats,
            onDismiss = onDismiss,
            onOpenSettings = onOpenSettings
        )
    }
}

/**
 * Internal content layout for the system tray panel.
 *
 * @param systemStats Current system statistics
 * @param onDismiss Callback to close the panel
 * @param onOpenSettings Callback to open settings
 */
@Composable
private fun SystemTrayPanelContent(
    systemStats: SystemStats,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    // Placeholder toggle states
    var wifiEnabled by remember { mutableStateOf(systemStats.wifiConnected) }
    var bluetoothEnabled by remember { mutableStateOf(systemStats.bluetoothConnected) }
    var doNotDisturb by remember { mutableStateOf(false) }
    var nightLight by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(0.75f) }
    var brightnessLevel by remember { mutableFloatStateOf(0.8f) }

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
                    .width(320.dp)
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .background(TerminalColors.Surface)
                    .padding(16.dp)
            ) {
                // ---- Panel header ----
                Text(
                    text = "# system-tray",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Accent
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ---- WiFi toggle ----
                ToggleRow(
                    icon = Icons.Default.Wifi,
                    label = "wifi",
                    isEnabled = wifiEnabled,
                    onClick = { wifiEnabled = !wifiEnabled }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ---- Bluetooth toggle ----
                ToggleRow(
                    icon = Icons.Default.Bluetooth,
                    label = "bluetooth",
                    isEnabled = bluetoothEnabled,
                    onClick = { bluetoothEnabled = !bluetoothEnabled }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Separator ----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TerminalColors.Background)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Volume slider ----
                SliderRow(
                    icon = Icons.Default.VolumeUp,
                    label = "volume",
                    value = volumeLevel,
                    onValueChange = { volumeLevel = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ---- Brightness slider ----
                SliderRow(
                    icon = Icons.Default.BrightnessHigh,
                    label = "brightness",
                    value = brightnessLevel,
                    onValueChange = { brightnessLevel = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Separator ----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TerminalColors.Background)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Do Not Disturb toggle ----
                ToggleRow(
                    icon = Icons.Default.DoNotDisturb,
                    label = "do-not-disturb",
                    isEnabled = doNotDisturb,
                    onClick = { doNotDisturb = !doNotDisturb }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ---- Night Light toggle ----
                ToggleRow(
                    icon = Icons.Default.DarkMode,
                    label = "night-light",
                    isEnabled = nightLight,
                    onClick = { nightLight = !nightLight }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Separator ----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TerminalColors.Background)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Battery details ----
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "battery:",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TerminalColors.Timestamp
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "${systemStats.batteryPercent}%",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (systemStats.batteryPercent > 30) TerminalColors.Success
                            else TerminalColors.Warning
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (systemStats.isCharging) "(charging)" else "(~3h 42m remaining)",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TerminalColors.Subtext
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ---- Separator ----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TerminalColors.Background)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Panel footer: open settings ----
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenSettings)
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TerminalColors.Accent,
                        modifier = Modifier.size(14.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "$ open-settings",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TerminalColors.Accent
                        )
                    )
                }
            }
        }
    }
}

/**
 * A single toggle row in the system tray panel.
 *
 * Displays an icon, a monospace label, and an on/off status indicator
 * (green dot = on, dim dot = off). The entire row is clickable to toggle.
 *
 * @param icon Material icon for the toggle
 * @param label Terminal-style label text
 * @param isEnabled Whether the toggle is currently on
 * @param onClick Callback when the row is clicked
 */
@Composable
private fun ToggleRow(
    icon: ImageVector,
    label: String,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isEnabled) TerminalColors.Background.copy(alpha = 0.5f)
                else TerminalColors.Background.copy(alpha = 0.2f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isEnabled) TerminalColors.Command else TerminalColors.Subtext,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isEnabled) TerminalColors.Command else TerminalColors.Subtext
            ),
            modifier = Modifier.weight(1f)
        )

        // Status indicator dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (isEnabled) TerminalColors.Success
                    else TerminalColors.Subtext.copy(alpha = 0.3f)
                )
        )
    }
}

/**
 * A slider row in the system tray panel for continuous values (volume, brightness).
 *
 * Displays an icon, a monospace label, and a slider with accent-colored track.
 *
 * @param icon Material icon for the slider
 * @param label Terminal-style label text
 * @param value Current slider value (0.0 to 1.0)
 * @param onValueChange Callback when the slider value changes
 */
@Composable
private fun SliderRow(
    icon: ImageVector,
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = TerminalColors.Command,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = label,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TerminalColors.Command
                ),
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "${(value * 100).toInt()}%",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = TerminalColors.Accent,
                activeTrackColor = TerminalColors.Accent,
                inactiveTrackColor = TerminalColors.Background
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )
    }
}
