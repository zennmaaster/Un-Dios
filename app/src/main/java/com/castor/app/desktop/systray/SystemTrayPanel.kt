package com.castor.app.desktop.systray

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
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
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.components.SystemStats
import com.castor.core.ui.theme.TerminalColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Expandable system tray panel that drops down from the top-right corner of the taskbar.
 *
 * Provides quick-access toggles and sliders for common system settings, styled
 * with the terminal aesthetic. Each toggle is rendered as a terminal-styled row
 * with an icon, label, and on/off status indicator (green dot = on, dim = off).
 *
 * Now reads real system data:
 * - **Battery**: Level and charging status from [SystemStats] (fed by BatteryManager)
 * - **WiFi**: Connection status from [SystemStats] (fed by ConnectivityManager)
 * - **Bluetooth**: Connection status from [SystemStats] (fed by BluetoothManager)
 * - **Notification count**: From [SystemStats.unreadNotifications] (fed by NotificationCountHolder)
 * - **Current time**: Live clock updating every minute
 * - **Volume**: Read/write via [AudioManager] for media stream
 * - **Brightness**: Read/write via [Settings.System.SCREEN_BRIGHTNESS]
 *
 * Quick toggles:
 * - WiFi on/off (visual toggle backed by real status)
 * - Bluetooth on/off (visual toggle backed by real status)
 * - Do Not Disturb
 * - Night Light
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
 * Reads real volume from [AudioManager] and brightness from system settings.
 * Toggles reflect actual system state from [SystemStats].
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
    val context = LocalContext.current

    // ---- Real volume from AudioManager ----
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVolume = remember { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }
    val currentVolume = remember { audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 10 }
    var volumeLevel by remember {
        mutableFloatStateOf(
            if (maxVolume > 0) currentVolume.toFloat() / maxVolume.toFloat() else 0.75f
        )
    }

    // ---- Real brightness from system settings ----
    val currentBrightness = remember {
        try {
            val raw = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            raw.toFloat() / 255f
        } catch (_: Exception) {
            0.8f
        }
    }
    var brightnessLevel by remember { mutableFloatStateOf(currentBrightness) }

    // ---- Toggle states backed by real system data ----
    var wifiEnabled by remember { mutableStateOf(systemStats.wifiConnected) }
    var bluetoothEnabled by remember { mutableStateOf(systemStats.bluetoothConnected) }
    var doNotDisturb by remember { mutableStateOf(false) }
    var nightLight by remember { mutableStateOf(false) }

    // ---- Live clock updating every minute ----
    var currentTime by remember { mutableStateOf(formatTrayTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = formatTrayTime()
            delay(60_000L)
        }
    }

    // Update toggle states when systemStats changes
    LaunchedEffect(systemStats.wifiConnected) {
        wifiEnabled = systemStats.wifiConnected
    }
    LaunchedEffect(systemStats.bluetoothConnected) {
        bluetoothEnabled = systemStats.bluetoothConnected
    }

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
                // ---- Panel header with live clock ----
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "# system-tray",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Accent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = currentTime,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TerminalColors.Command
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Notification count summary ----
                if (systemStats.unreadNotifications > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(TerminalColors.BadgeRed.copy(alpha = 0.1f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(TerminalColors.BadgeRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${systemStats.unreadNotifications} unread notification${if (systemStats.unreadNotifications != 1) "s" else ""}",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TerminalColors.BadgeRed
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ---- Quick toggles section ----
                Text(
                    text = "-- connectivity --",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = TerminalColors.Timestamp
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ---- WiFi toggle ----
                ToggleRow(
                    icon = if (wifiEnabled) Icons.Default.Wifi else Icons.Default.WifiOff,
                    label = "wifi",
                    statusText = if (wifiEnabled) "connected" else "off",
                    isEnabled = wifiEnabled,
                    onClick = { wifiEnabled = !wifiEnabled }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // ---- Bluetooth toggle ----
                ToggleRow(
                    icon = Icons.Default.Bluetooth,
                    label = "bluetooth",
                    statusText = if (bluetoothEnabled) "connected" else "off",
                    isEnabled = bluetoothEnabled,
                    onClick = { bluetoothEnabled = !bluetoothEnabled }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Separator ----
                TrayDivider()

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Sliders section ----
                Text(
                    text = "-- audio & display --",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = TerminalColors.Timestamp
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ---- Volume slider ----
                SliderRow(
                    icon = when {
                        volumeLevel <= 0f -> Icons.Default.VolumeMute
                        volumeLevel < 0.5f -> Icons.Default.VolumeDown
                        else -> Icons.Default.VolumeUp
                    },
                    label = "volume",
                    value = volumeLevel,
                    onValueChange = { newValue ->
                        volumeLevel = newValue
                        // Write real volume to AudioManager
                        audioManager?.let { am ->
                            val targetVolume = (newValue * maxVolume).toInt().coerceIn(0, maxVolume)
                            try {
                                am.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    targetVolume,
                                    0 // no flags (no UI beep)
                                )
                            } catch (_: Exception) {
                                // Permission denied or other error
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ---- Brightness slider ----
                SliderRow(
                    icon = Icons.Default.BrightnessHigh,
                    label = "brightness",
                    value = brightnessLevel,
                    onValueChange = { newValue ->
                        brightnessLevel = newValue
                        // Write real brightness to system settings (requires WRITE_SETTINGS permission)
                        try {
                            if (Settings.System.canWrite(context)) {
                                Settings.System.putInt(
                                    context.contentResolver,
                                    Settings.System.SCREEN_BRIGHTNESS,
                                    (newValue * 255).toInt().coerceIn(0, 255)
                                )
                            }
                        } catch (_: Exception) {
                            // Permission not granted
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Separator ----
                TrayDivider()

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Mode toggles section ----
                Text(
                    text = "-- modes --",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = TerminalColors.Timestamp
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ---- Do Not Disturb toggle ----
                ToggleRow(
                    icon = Icons.Default.DoNotDisturb,
                    label = "do-not-disturb",
                    statusText = if (doNotDisturb) "active" else "off",
                    isEnabled = doNotDisturb,
                    onClick = { doNotDisturb = !doNotDisturb }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // ---- Night Light toggle ----
                ToggleRow(
                    icon = Icons.Default.DarkMode,
                    label = "night-light",
                    statusText = if (nightLight) "on" else "off",
                    isEnabled = nightLight,
                    onClick = { nightLight = !nightLight }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Separator ----
                TrayDivider()

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Battery details (real data) ----
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

                    // Battery bar visualization
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(TerminalColors.Background)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(systemStats.batteryPercent / 100f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        systemStats.batteryPercent <= 15 -> TerminalColors.Error
                                        systemStats.batteryPercent <= 30 -> TerminalColors.Warning
                                        else -> TerminalColors.Success
                                    }
                                )
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "${systemStats.batteryPercent}%",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                systemStats.batteryPercent <= 15 -> TerminalColors.Error
                                systemStats.batteryPercent <= 30 -> TerminalColors.Warning
                                else -> TerminalColors.Success
                            }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (systemStats.isCharging) "(charging)" else "",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TerminalColors.Subtext
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ---- System stats summary ----
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "cpu: ${systemStats.cpuUsage.toInt()}%",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TerminalColors.Timestamp
                        )
                    )
                    Text(
                        text = "ram: ${systemStats.ramUsage.toInt()}%",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TerminalColors.Timestamp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ---- Separator ----
                TrayDivider()

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
 * Formats the current time for the system tray clock.
 * Format: "HH:mm" (e.g., "14:23").
 */
private fun formatTrayTime(): String {
    return try {
        val formatter = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
        formatter.format(Date())
    } catch (_: Exception) {
        ""
    }
}

/**
 * Horizontal divider line styled for the system tray panel.
 */
@Composable
private fun TrayDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(TerminalColors.Background)
    )
}

/**
 * A single toggle row in the system tray panel.
 *
 * Displays an icon, a monospace label, a status text, and an on/off indicator
 * (green dot = on, dim dot = off). The entire row is clickable to toggle.
 *
 * @param icon Material icon for the toggle
 * @param label Terminal-style label text
 * @param statusText Short text indicating current status (e.g., "connected", "off")
 * @param isEnabled Whether the toggle is currently on
 * @param onClick Callback when the row is clicked
 */
@Composable
private fun ToggleRow(
    icon: ImageVector,
    label: String,
    statusText: String = "",
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
            )
        )

        if (statusText.isNotEmpty()) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "($statusText)",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }

        Spacer(modifier = Modifier.weight(1f))

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
 * Displays an icon, a monospace label, percentage text, and a slider with accent-colored track.
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
