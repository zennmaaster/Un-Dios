package com.castor.app.launcher

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.app.lockscreen.LockScreenViewModel
import com.castor.app.settings.ThemeManager
import com.castor.core.ui.theme.TerminalColors
import com.castor.core.ui.theme.TerminalThemeType

/**
 * Launcher settings screen accessible via long-press on the home screen.
 *
 * Organized as a terminal-style configuration file display (`$ cat /etc/un-dios/config`),
 * with settings grouped into logical sections: Appearance, Home Screen, Privacy,
 * Accounts, System, and About. Each section is introduced with a comment-style header
 * and contains actionable settings rows.
 *
 * The entire screen maintains the monospace terminal aesthetic consistent with the
 * rest of the Un-Dios launcher experience.
 *
 * @param onBack Called when the user navigates back from settings
 */
@Composable
fun LauncherSettingsScreen(
    onBack: () -> Unit,
    onNavigateToUsageStats: () -> Unit = {},
    onNavigateToThemeSelector: () -> Unit = {},
    themeManager: ThemeManager? = null,
    lockScreenViewModel: LockScreenViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // Lock screen preferences
    val isLockEnabled by lockScreenViewModel.isLockEnabled.collectAsState()
    val showBootAnimation by lockScreenViewModel.showBootAnimation.collectAsState()
    val showNotificationsOnLock by lockScreenViewModel.showNotificationsOnLock.collectAsState()
    val lockTimeoutMs by lockScreenViewModel.lockTimeoutMs.collectAsState()

    // Theme preferences
    val selectedTheme = themeManager?.selectedTheme?.collectAsState()?.value
        ?: TerminalThemeType.CATPPUCCIN_MOCHA

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
            .statusBarsPadding()
    ) {
        // ---- Top bar ----
        SettingsTopBar(onBack = onBack)

        // ---- Settings content ----
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // File header
            item {
                TerminalFileHeader()
            }

            // ================================================================
            // [appearance] section
            // ================================================================
            item { SettingsSectionHeader(title = "appearance") }

            item {
                SettingsToggleRow(
                    key = "theme.mode",
                    label = "Dark theme",
                    description = "Always dark (terminal aesthetic)",
                    initialValue = true,
                    onValueChange = { /* TODO: Persist theme preference */ }
                )
            }

            item {
                SettingsActionRow(
                    key = "theme.colors",
                    label = "Terminal theme",
                    description = "Current: ${selectedTheme.displayName}",
                    onClick = onNavigateToThemeSelector
                )
            }

            item {
                SettingsValueRow(
                    key = "font.family",
                    value = "monospace",
                    description = "System monospace font"
                )
            }

            item {
                SettingsValueRow(
                    key = "font.size",
                    value = "13sp",
                    description = "Terminal text size"
                )
            }

            item { SettingsDivider() }

            // ================================================================
            // [homescreen] section
            // ================================================================
            item { SettingsSectionHeader(title = "homescreen") }

            item {
                SettingsToggleRow(
                    key = "dock.auto_hide",
                    label = "Auto-hide dock",
                    description = "Hide dock when apps are open",
                    initialValue = false,
                    onValueChange = { /* TODO: Persist dock preference */ }
                )
            }

            item {
                SettingsValueRow(
                    key = "grid.columns",
                    value = "4 (phone) / 6 (tablet)",
                    description = "App drawer grid columns"
                )
            }

            item {
                SettingsToggleRow(
                    key = "gestures.enabled",
                    label = "Gesture navigation",
                    description = "Swipe up: drawer, Swipe down: notifications",
                    initialValue = true,
                    onValueChange = { /* TODO: Persist gesture preference */ }
                )
            }

            item {
                SettingsToggleRow(
                    key = "gestures.double_tap_lock",
                    label = "Double-tap to lock",
                    description = "Double-tap home screen to sleep",
                    initialValue = true,
                    onValueChange = { /* TODO: Persist */ }
                )
            }

            item {
                SettingsValueRow(
                    key = "widgets.enabled",
                    value = "true",
                    description = "Android widget hosting"
                )
            }

            item { SettingsDivider() }

            // ================================================================
            // [lock-screen] section
            // ================================================================
            item { SettingsSectionHeader(title = "lock-screen") }

            item {
                SettingsToggleRow(
                    key = "lockscreen.enabled",
                    label = "Enable lock screen",
                    description = "Show terminal lock screen on wake",
                    initialValue = isLockEnabled,
                    onValueChange = { lockScreenViewModel.setLockEnabled(it) }
                )
            }

            item {
                SettingsToggleRow(
                    key = "lockscreen.boot_animation",
                    label = "Boot animation",
                    description = "Play kernel boot sequence on lock screen",
                    initialValue = showBootAnimation,
                    onValueChange = { lockScreenViewModel.setShowBootAnimation(it) }
                )
            }

            item {
                SettingsToggleRow(
                    key = "lockscreen.show_notifications",
                    label = "Notifications on lock screen",
                    description = "Show last 3 notification previews",
                    initialValue = showNotificationsOnLock,
                    onValueChange = { lockScreenViewModel.setShowNotificationsOnLock(it) }
                )
            }

            item {
                LockTimeoutSelector(
                    currentTimeoutMs = lockTimeoutMs,
                    onTimeoutSelected = { lockScreenViewModel.setLockTimeout(it) }
                )
            }

            item { SettingsDivider() }

            // ================================================================
            // [privacy] section
            // ================================================================
            item { SettingsSectionHeader(title = "privacy") }

            item {
                SettingsValueRow(
                    key = "privacy.default_tier",
                    value = "LOCAL",
                    description = "Default privacy tier for agents",
                    valueColor = TerminalColors.PrivacyLocal
                )
            }

            item {
                SettingsValueRow(
                    key = "privacy.cloud_provider",
                    value = "none",
                    description = "Cloud inference provider (optional)"
                )
            }

            item {
                SettingsActionRow(
                    key = "privacy.api_keys",
                    label = "Manage API keys",
                    description = "Configure provider credentials",
                    onClick = { /* TODO: Open API key manager */ }
                )
            }

            item {
                SettingsToggleRow(
                    key = "privacy.analytics",
                    label = "Usage analytics",
                    description = "Send anonymous usage data",
                    initialValue = false,
                    onValueChange = { /* TODO: Persist */ }
                )
            }

            item { SettingsDivider() }

            // ================================================================
            // [usage-stats] section
            // ================================================================
            item { SettingsSectionHeader(title = "usage-stats") }

            item {
                SettingsActionRow(
                    key = "usage.screen_time",
                    label = "View screen time",
                    description = "Open the screen time dashboard (htop --user-apps)",
                    onClick = onNavigateToUsageStats
                )
            }

            item {
                SettingsToggleRow(
                    key = "usage.daily_summary",
                    label = "Daily summary notification",
                    description = "Show a daily screen time summary notification",
                    initialValue = false,
                    onValueChange = { /* TODO: Persist daily summary preference */ }
                )
            }

            item {
                SettingsToggleRow(
                    key = "usage.track_categories",
                    label = "Track app categories",
                    description = "Classify apps into categories for breakdown view",
                    initialValue = true,
                    onValueChange = { /* TODO: Persist category tracking preference */ }
                )
            }

            item { SettingsDivider() }

            // ================================================================
            // [accounts] section
            // ================================================================
            item { SettingsSectionHeader(title = "accounts") }

            item {
                SettingsAccountRow(
                    service = "Google",
                    status = "connected",
                    statusColor = TerminalColors.Success
                )
            }

            item {
                SettingsAccountRow(
                    service = "Spotify",
                    status = "not configured",
                    statusColor = TerminalColors.Timestamp
                )
            }

            item {
                SettingsAccountRow(
                    service = "YouTube",
                    status = "not configured",
                    statusColor = TerminalColors.Timestamp
                )
            }

            item {
                SettingsAccountRow(
                    service = "WhatsApp",
                    status = "requires Notification Access",
                    statusColor = TerminalColors.Warning
                )
            }

            item {
                SettingsAccountRow(
                    service = "Teams",
                    status = "not configured",
                    statusColor = TerminalColors.Timestamp
                )
            }

            item { SettingsDivider() }

            // ================================================================
            // [system] section
            // ================================================================
            item { SettingsSectionHeader(title = "system") }

            item {
                SettingsSystemStatusRow(
                    key = "notification_access",
                    label = "Notification listener",
                    isEnabled = isNotificationListenerEnabled(context),
                    onClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) { }
                    }
                )
            }

            item {
                SettingsSystemStatusRow(
                    key = "usage_access",
                    label = "Usage stats access",
                    isEnabled = isUsageStatsEnabled(context),
                    onClick = {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) { }
                    }
                )
            }

            item {
                SettingsActionRow(
                    key = "battery.optimization",
                    label = "Battery optimization",
                    description = "Disable battery optimization for Un-Dios",
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (_: Exception) { }
                    }
                )
            }

            item {
                SettingsActionRow(
                    key = "default_home",
                    label = "Set as default launcher",
                    description = "Make Un-Dios the default home screen",
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (_: Exception) { }
                    }
                )
            }

            item { SettingsDivider() }

            // ================================================================
            // [about] section
            // ================================================================
            item { SettingsSectionHeader(title = "about") }

            item {
                SettingsValueRow(
                    key = "app.name",
                    value = "Un-Dios",
                    description = "Open convergence platform for Android"
                )
            }

            item {
                SettingsValueRow(
                    key = "app.version",
                    value = "0.1.0-alpha",
                    description = "Build version"
                )
            }

            item {
                SettingsValueRow(
                    key = "app.codename",
                    value = "Castor",
                    description = "Internal project name",
                    valueColor = TerminalColors.Accent
                )
            }

            item {
                SettingsValueRow(
                    key = "android.sdk",
                    value = "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})",
                    description = "Android version"
                )
            }

            item {
                SettingsValueRow(
                    key = "device.model",
                    value = "${Build.MANUFACTURER} ${Build.MODEL}",
                    description = "Device hardware"
                )
            }

            item {
                SettingsActionRow(
                    key = "licenses",
                    label = "Open source licenses",
                    description = "Third-party library attributions",
                    onClick = { /* TODO: Open licenses screen */ }
                )
            }

            // EOF marker
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "# EOF",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TerminalColors.Subtext
                    )
                )
            }
        }
    }
}

// =============================================================================
// Top bar
// =============================================================================

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalColors.StatusBar)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TerminalColors.Command
            )
        }

        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            tint = TerminalColors.Accent,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "/etc/un-dios/config",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )
    }
}

// =============================================================================
// Terminal file header
// =============================================================================

@Composable
private fun TerminalFileHeader() {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "$ cat /etc/un-dios/config",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Prompt
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "# Un-Dios launcher configuration",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Subtext
            )
        )
        Text(
            text = "# Modify values below to customize your experience",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Subtext
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// =============================================================================
// Section header
// =============================================================================

/**
 * Section header styled as a TOML/INI section: `[section_name]`
 */
@Composable
private fun SettingsSectionHeader(title: String) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Text(
            text = "[$title]",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Info
            )
        )
    }
}

// =============================================================================
// Setting rows
// =============================================================================

/**
 * A settings row displaying a key=value pair with an optional description.
 * Read-only display of a configuration value.
 */
@Composable
private fun SettingsValueRow(
    key: String,
    value: String,
    description: String,
    valueColor: Color = TerminalColors.Prompt
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = key,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Command
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "= $value",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = valueColor
                )
            )
        }

        Text(
            text = "# $description",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Subtext
            ),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * A settings row with a toggle switch, styled as key=true/false.
 */
@Composable
private fun SettingsToggleRow(
    key: String,
    label: String,
    description: String,
    initialValue: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    var isEnabled by remember { mutableStateOf(initialValue) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$key = ${if (isEnabled) "true" else "false"}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalColors.Command
                    )
                )
                Text(
                    text = "# $description",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Subtext
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = isEnabled,
                onCheckedChange = { newValue ->
                    isEnabled = newValue
                    onValueChange(newValue)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TerminalColors.Prompt,
                    checkedTrackColor = TerminalColors.Prompt.copy(alpha = 0.3f),
                    uncheckedThumbColor = TerminalColors.Subtext,
                    uncheckedTrackColor = TerminalColors.Surface
                )
            )
        }
    }
}

/**
 * A settings row that acts as a clickable action (opens a screen, dialog, or system intent).
 */
@Composable
private fun SettingsActionRow(
    key: String,
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$ ",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Prompt
                )
            )
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Accent
                )
            )
        }

        Text(
            text = "# $description",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Subtext
            ),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * A settings row showing the connection status of an external service account.
 */
@Composable
private fun SettingsAccountRow(
    service: String,
    status: String,
    statusColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .clickable { /* TODO: Open account setup flow */ }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.People,
                contentDescription = null,
                tint = TerminalColors.Command,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = service,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Command
                )
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = statusColor
                )
            )
        }
    }
}

/**
 * A system status row showing whether a required permission/service is enabled,
 * with a clickable action to open the relevant settings.
 */
@Composable
private fun SettingsSystemStatusRow(
    key: String,
    label: String,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Command
                )
            )
            Text(
                text = if (isEnabled) "# status: ENABLED" else "# status: DISABLED (tap to configure)",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = if (isEnabled) TerminalColors.Success else TerminalColors.Warning
                ),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Icon(
            imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = if (isEnabled) "Enabled" else "Disabled",
            tint = if (isEnabled) TerminalColors.Success else TerminalColors.Warning,
            modifier = Modifier.size(20.dp)
        )
    }
}

// =============================================================================
// Lock timeout selector
// =============================================================================

/**
 * A selector row for choosing the auto-lock timeout duration.
 * Displays timeout options as tappable terminal-styled chips:
 * 30s, 1m, 2m, 5m, never.
 */
@Composable
private fun LockTimeoutSelector(
    currentTimeoutMs: Long,
    onTimeoutSelected: (Long) -> Unit
) {
    val timeoutOptions = listOf(
        "30s" to 30_000L,
        "1m" to 60_000L,
        "2m" to 120_000L,
        "5m" to 300_000L,
        "never" to -1L
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = "lockscreen.timeout = ${formatTimeout(currentTimeoutMs)}",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Command
            )
        )

        Text(
            text = "# Auto-lock after inactivity",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Subtext
            ),
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            timeoutOptions.forEach { (label, timeoutMs) ->
                val isSelected = currentTimeoutMs == timeoutMs ||
                    (timeoutMs == -1L && (currentTimeoutMs < 0 || currentTimeoutMs == Long.MAX_VALUE))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isSelected) TerminalColors.Accent.copy(alpha = 0.2f)
                            else TerminalColors.Background
                        )
                        .clickable { onTimeoutSelected(timeoutMs) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) TerminalColors.Accent else TerminalColors.Timestamp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Format a timeout value in milliseconds to a human-readable label.
 */
private fun formatTimeout(timeoutMs: Long): String {
    return when {
        timeoutMs < 0 || timeoutMs == Long.MAX_VALUE -> "never"
        timeoutMs < 60_000L -> "${timeoutMs / 1000}s"
        else -> "${timeoutMs / 60_000}m"
    }
}

// =============================================================================
// Divider
// =============================================================================

@Composable
private fun SettingsDivider() {
    Spacer(modifier = Modifier.height(4.dp))
    HorizontalDivider(
        thickness = 1.dp,
        color = TerminalColors.Selection
    )
    Spacer(modifier = Modifier.height(4.dp))
}

// =============================================================================
// Utility functions
// =============================================================================

/**
 * Checks if the app has Notification Listener access by examining the
 * secure settings for enabled notification listeners.
 */
private fun isNotificationListenerEnabled(context: Context): Boolean {
    return try {
        val listeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        listeners.contains(context.packageName)
    } catch (_: Exception) {
        false
    }
}

/**
 * Checks if the app has Usage Stats access permission.
 * Attempts to query usage stats; if the result is non-empty, access is granted.
 */
private fun isUsageStatsEnabled(context: Context): Boolean {
    return try {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? android.app.usage.UsageStatsManager ?: return false
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 60 * 1000L // 1 minute
        val stats = usageStatsManager.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )
        stats != null && stats.isNotEmpty()
    } catch (_: Exception) {
        false
    }
}
