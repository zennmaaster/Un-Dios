package com.castor.app.quicksettings

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.core.ui.theme.TerminalColors

/**
 * Quick Settings panel displayed as a modal bottom sheet.
 *
 * Features:
 * - 2x3 grid of toggle tiles (WiFi, Bluetooth, Flashlight, Auto-Rotate, DND, Airplane Mode)
 * - Brightness slider
 * - Volume slider
 * - Battery percentage and network status
 *
 * All UI styled with terminal/hacker aesthetic using TerminalColors and monospace fonts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsPanel(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    viewModel: QuickSettingsViewModel = hiltViewModel()
) {
    if (!isVisible) return

    val wifiEnabled by viewModel.wifiEnabled.collectAsState()
    val bluetoothEnabled by viewModel.bluetoothEnabled.collectAsState()
    val flashlightOn by viewModel.flashlightOn.collectAsState()
    val autoRotateEnabled by viewModel.autoRotateEnabled.collectAsState()
    val dndEnabled by viewModel.dndEnabled.collectAsState()
    val brightnessLevel by viewModel.brightnessLevel.collectAsState()
    val mediaVolume by viewModel.mediaVolume.collectAsState()
    val batteryPercent by viewModel.batteryPercent.collectAsState()
    val networkName by viewModel.networkName.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TerminalColors.Surface,
        contentColor = TerminalColors.Command
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: $ systemctl status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$ systemctl status",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Prompt
                    )
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TerminalColors.Subtext
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Toggle tiles grid (2 columns, 3 rows)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp) // Fixed height for 3 rows of tiles
            ) {
                // WiFi
                item {
                    QuickSettingsTile(
                        icon = if (wifiEnabled) Icons.Default.Wifi else Icons.Default.WifiOff,
                        label = "wifi",
                        isActive = wifiEnabled,
                        onClick = { viewModel.toggleWifi() }
                    )
                }

                // Bluetooth
                item {
                    QuickSettingsTile(
                        icon = if (bluetoothEnabled) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                        label = "bluetooth",
                        isActive = bluetoothEnabled,
                        onClick = { viewModel.toggleBluetooth() }
                    )
                }

                // Flashlight
                item {
                    QuickSettingsTile(
                        icon = if (flashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                        label = "torch",
                        isActive = flashlightOn,
                        onClick = { viewModel.toggleFlashlight() }
                    )
                }

                // Auto-Rotate
                item {
                    QuickSettingsTile(
                        icon = if (autoRotateEnabled) Icons.Default.ScreenRotation else Icons.Default.ScreenLockRotation,
                        label = "rotation",
                        isActive = autoRotateEnabled,
                        onClick = { viewModel.toggleAutoRotate() }
                    )
                }

                // DND
                item {
                    QuickSettingsTile(
                        icon = if (dndEnabled) Icons.Default.DoNotDisturb else Icons.Default.NotificationsActive,
                        label = "dnd",
                        isActive = dndEnabled,
                        onClick = { viewModel.toggleDnd() }
                    )
                }

                // Airplane Mode (placeholder - no toggle)
                item {
                    QuickSettingsTile(
                        icon = Icons.Default.AirplanemodeActive,
                        label = "airplane",
                        isActive = false,
                        onClick = { /* No-op */ }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Brightness slider
            SliderControl(
                label = "brightness",
                value = brightnessLevel,
                onValueChange = { viewModel.setBrightness(it) },
                iconStart = Icons.Default.BrightnessLow,
                iconEnd = Icons.Default.BrightnessHigh
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Volume slider
            SliderControl(
                label = "volume",
                value = mediaVolume,
                onValueChange = { viewModel.setVolume(it) },
                iconStart = Icons.Default.VolumeDown,
                iconEnd = Icons.Default.VolumeUp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom info row: Battery + Network
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Battery
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BatteryFull,
                        contentDescription = "Battery",
                        tint = TerminalColors.Success,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$batteryPercent%",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TerminalColors.Success
                        )
                    )
                }

                // Network name
                Text(
                    text = networkName,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalColors.Timestamp
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * A single quick settings tile.
 *
 * Active state: Accent color background with accent icon
 * Inactive state: Surface background with subdued icon
 */
@Composable
private fun QuickSettingsTile(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isActive) {
        TerminalColors.Accent.copy(alpha = 0.15f)
    } else {
        TerminalColors.Surface
    }

    val iconColor = if (isActive) {
        TerminalColors.Accent
    } else {
        TerminalColors.Subtext
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(85.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = if (isActive) TerminalColors.Accent else TerminalColors.Subtext
                )
            )
        }
    }
}

/**
 * A slider control with label, value percentage, and start/end icons.
 */
@Composable
private fun SliderControl(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    iconStart: ImageVector,
    iconEnd: ImageVector
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Label row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label=",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Timestamp
                )
            )
            Text(
                text = "${(value * 100).toInt()}%",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Success
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Slider row with icons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = iconStart,
                contentDescription = null,
                tint = TerminalColors.Subtext,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = TerminalColors.Accent,
                    activeTrackColor = TerminalColors.Accent,
                    inactiveTrackColor = TerminalColors.Surface
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = iconEnd,
                contentDescription = null,
                tint = TerminalColors.Accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
