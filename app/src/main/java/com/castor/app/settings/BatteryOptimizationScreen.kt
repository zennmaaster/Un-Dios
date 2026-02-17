package com.castor.app.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.castor.core.ui.theme.TerminalColors

/**
 * Battery optimization guide screen styled as a terminal manual page.
 *
 * Detects the device manufacturer and shows manufacturer-specific instructions
 * for disabling battery optimization. Includes quick-action buttons to open
 * system settings and check current optimization status.
 *
 * Styled as: `$ cat /etc/un-dios/battery-optimization.md`
 *
 * @param onBack Called when the user navigates back.
 */
@Composable
fun BatteryOptimizationScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val manufacturer = remember { Build.MANUFACTURER.lowercase() }
    var isOptimizationDisabled by remember {
        mutableStateOf(checkBatteryOptimizationStatus(context))
    }

    // Re-check battery status when returning from settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isOptimizationDisabled = checkBatteryOptimizationStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
            .statusBarsPadding()
    ) {
        // Top bar
        BatteryTopBar(onBack = onBack)

        // Content
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
            item { BatteryFileHeader() }

            // Current status
            item { BatteryStatusSection(isOptimizationDisabled = isOptimizationDisabled) }

            item { BatterySectionDivider() }

            // Why it matters
            item { WhyItMattersSection() }

            item { BatterySectionDivider() }

            // Services explanation
            item { ServicesSection() }

            item { BatterySectionDivider() }

            // Manufacturer-specific instructions
            item { ManufacturerInstructionsSection(manufacturer = manufacturer) }

            item { BatterySectionDivider() }

            // Action buttons
            item {
                ActionButtonsSection(
                    context = context,
                    isOptimizationDisabled = isOptimizationDisabled
                )
            }

            item { BatterySectionDivider() }

            // Learn more
            item { LearnMoreSection(context = context) }

            // EOF
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
private fun BatteryTopBar(onBack: () -> Unit) {
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
            imageVector = Icons.Default.BatteryFull,
            contentDescription = null,
            tint = TerminalColors.Accent,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "/etc/un-dios/battery-optimization.md",
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
// File header
// =============================================================================

@Composable
private fun BatteryFileHeader() {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "$ cat /etc/un-dios/battery-optimization.md",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Prompt
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "# Battery Optimization Guide",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Subtext
            )
        )
        Text(
            text = "# Ensure Un-Dios services run reliably in the background",
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
// Current status section
// =============================================================================

@Composable
private fun BatteryStatusSection(isOptimizationDisabled: Boolean) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        SectionHeader(title = "status")

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(TerminalColors.Surface.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$ systemctl status battery-optimization",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Prompt
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(TerminalColors.Surface.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOptimizationDisabled) TerminalColors.Success
                            else TerminalColors.Error
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isOptimizationDisabled)
                            "battery_optimization = DISABLED"
                        else
                            "battery_optimization = ENABLED",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOptimizationDisabled)
                                TerminalColors.Success
                            else
                                TerminalColors.Error
                        )
                    )
                    Text(
                        text = if (isOptimizationDisabled)
                            "# Un-Dios is exempt from battery optimization"
                        else
                            "# WARNING: Un-Dios may be killed in the background",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = if (isOptimizationDisabled)
                                TerminalColors.Success
                            else
                                TerminalColors.Warning
                        ),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Icon(
                imageVector = if (isOptimizationDisabled)
                    Icons.Default.CheckCircle
                else
                    Icons.Default.Warning,
                contentDescription = if (isOptimizationDisabled) "OK" else "Warning",
                tint = if (isOptimizationDisabled)
                    TerminalColors.Success
                else
                    TerminalColors.Warning,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// =============================================================================
// Why it matters section
// =============================================================================

@Composable
private fun WhyItMattersSection() {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        SectionHeader(title = "why-it-matters")

        Spacer(modifier = Modifier.height(8.dp))

        TerminalOutputBlock(
            lines = listOf(
                "Android aggressively kills background apps to save battery.",
                "This causes Un-Dios services to stop working, including:",
                "",
                "  - Notification monitoring and smart replies",
                "  - Media session tracking (now playing)",
                "  - Background agents and automations",
                "  - Lock screen updates",
                "",
                "Disabling battery optimization tells Android to let Un-Dios",
                "run reliably. This has minimal impact on battery life since",
                "Un-Dios uses efficient event-driven architecture."
            )
        )
    }
}

// =============================================================================
// Services section
// =============================================================================

@Composable
private fun ServicesSection() {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        SectionHeader(title = "services")

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "# Services that need background access:",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Subtext
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        ServiceRow(
            name = "NotificationListenerService",
            description = "Reads notifications from WhatsApp, Teams, and other apps " +
                "to power the unified messaging inbox and smart replies.",
            status = "REQUIRED"
        )

        Spacer(modifier = Modifier.height(6.dp))

        ServiceRow(
            name = "AgentService",
            description = "Runs background agents for automations, reminders, " +
                "and context-aware suggestions.",
            status = "REQUIRED"
        )

        Spacer(modifier = Modifier.height(6.dp))

        ServiceRow(
            name = "MediaSessionMonitor",
            description = "Tracks currently playing media across Spotify, YouTube Music, " +
                "and other players for the media dashboard.",
            status = "RECOMMENDED"
        )
    }
}

@Composable
private fun ServiceRow(
    name: String,
    description: String,
    status: String
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
                text = "$ systemctl show $name",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Prompt
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "[$status]",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (status == "REQUIRED") TerminalColors.Warning
                    else TerminalColors.Info
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "# $description",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Subtext,
                lineHeight = 15.sp
            )
        )
    }
}

// =============================================================================
// Manufacturer-specific instructions
// =============================================================================

@Composable
private fun ManufacturerInstructionsSection(manufacturer: String) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        SectionHeader(title = "manufacturer-guide")

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "$ uname -m  # detected: ${Build.MANUFACTURER}",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Prompt
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Show detected manufacturer instructions prominently
        val (detectedTitle, detectedSteps) = getManufacturerInstructions(manufacturer)

        ManufacturerCard(
            title = detectedTitle,
            steps = detectedSteps,
            isDetected = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "# Instructions for other manufacturers:",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Subtext
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Show all other manufacturer instructions
        val allManufacturers = listOf(
            "samsung" to getManufacturerInstructions("samsung"),
            "xiaomi" to getManufacturerInstructions("xiaomi"),
            "huawei" to getManufacturerInstructions("huawei"),
            "oneplus" to getManufacturerInstructions("oneplus"),
            "google" to getManufacturerInstructions("google"),
            "generic" to getManufacturerInstructions("generic")
        )

        allManufacturers.forEach { (mfr, pair) ->
            val isCurrentDevice = manufacturer.contains(mfr) ||
                (mfr == "generic" && !listOf(
                    "samsung", "xiaomi", "redmi", "poco",
                    "huawei", "honor", "oneplus", "oppo",
                    "realme", "google"
                ).any { manufacturer.contains(it) })

            if (!isCurrentDevice) {
                ManufacturerCard(
                    title = pair.first,
                    steps = pair.second,
                    isDetected = false
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun ManufacturerCard(
    title: String,
    steps: List<String>,
    isDetected: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isDetected) TerminalColors.Accent.copy(alpha = 0.1f)
                else TerminalColors.Surface.copy(alpha = 0.5f)
            )
            .then(
                if (isDetected) Modifier.padding(2.dp) else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "# $title",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDetected) TerminalColors.Accent
                    else TerminalColors.Info
                )
            )
            if (isDetected) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "[DETECTED]",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Success
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        steps.forEachIndexed { index, step ->
            Text(
                text = "  ${index + 1}. $step",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Output,
                    lineHeight = 17.sp
                ),
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }
    }
}

private fun getManufacturerInstructions(manufacturer: String): Pair<String, List<String>> {
    return when {
        manufacturer.contains("samsung") -> "Samsung" to listOf(
            "Open Settings > Battery",
            "Tap 'Background usage limits'",
            "Tap 'Never sleeping apps'",
            "Add Un-Dios to the list",
            "Also check: Settings > Apps > Un-Dios > Battery > Unrestricted"
        )
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
            manufacturer.contains("poco") -> "Xiaomi / Redmi / POCO" to listOf(
            "Open Settings > Battery & performance",
            "Tap 'App battery saver'",
            "Find Un-Dios and select 'No restrictions'",
            "Also enable: Settings > Apps > Manage apps > Un-Dios > Autostart"
        )
        manufacturer.contains("huawei") || manufacturer.contains("honor") ->
            "Huawei / Honor" to listOf(
            "Open Settings > Battery",
            "Tap 'App launch'",
            "Find Un-Dios and tap it",
            "Select 'Manage manually'",
            "Enable all three toggles: Auto-launch, Secondary launch, Run in background"
        )
        manufacturer.contains("oneplus") || manufacturer.contains("oppo") ||
            manufacturer.contains("realme") -> "OnePlus / OPPO / Realme" to listOf(
            "Open Settings > Battery",
            "Tap 'Battery optimization'",
            "Find Un-Dios in the list",
            "Select 'Don't optimize'",
            "Also check: Settings > Apps > Un-Dios > Battery usage > Allow background activity"
        )
        manufacturer.contains("google") -> "Google Pixel" to listOf(
            "Open Settings > Battery",
            "Tap 'Battery optimization'",
            "Tap 'All apps' dropdown at the top",
            "Find Un-Dios",
            "Select 'Don't optimize'"
        )
        else -> "Generic Android" to listOf(
            "Open Settings > Apps",
            "Find and tap Un-Dios",
            "Tap 'Battery'",
            "Select 'Unrestricted'",
            "If not available, check Settings > Battery > Battery optimization"
        )
    }
}

// =============================================================================
// Action buttons
// =============================================================================

@Composable
private fun ActionButtonsSection(
    context: Context,
    isOptimizationDisabled: Boolean
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        SectionHeader(title = "actions")

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "# Quick actions to configure battery settings:",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Subtext
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Request ignore battery optimizations
        TerminalActionButton(
            command = if (isOptimizationDisabled)
                "$ check-status  # already exempt"
            else
                "$ request-battery-exemption",
            description = if (isOptimizationDisabled)
                "# Battery optimization is already disabled for Un-Dios"
            else
                "# Request Android to exempt Un-Dios from battery optimization",
            onClick = {
                try {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    )
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (_: Exception) {
                    // Fallback to general battery optimization settings
                    try {
                        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(fallback)
                    } catch (_: Exception) { }
                }
            },
            isSuccess = isOptimizationDisabled
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Open battery settings
        TerminalActionButton(
            command = "$ open-battery-settings",
            description = "# Open system battery saver settings",
            onClick = {
                try {
                    val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (_: Exception) {
                    try {
                        val fallback = Intent(Settings.ACTION_SETTINGS)
                        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(fallback)
                    } catch (_: Exception) { }
                }
            },
            isSuccess = false
        )
    }
}

@Composable
private fun TerminalActionButton(
    command: String,
    description: String,
    onClick: () -> Unit,
    isSuccess: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(
            text = command,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSuccess) TerminalColors.Success else TerminalColors.Accent
            )
        )
        Text(
            text = description,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Subtext
            ),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

// =============================================================================
// Learn more section
// =============================================================================

@Composable
private fun LearnMoreSection(context: Context) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        SectionHeader(title = "learn-more")

        Spacer(modifier = Modifier.height(8.dp))

        TerminalOutputBlock(
            lines = listOf(
                "For detailed, device-specific instructions on preventing",
                "Android from killing background apps, visit:",
                "",
                "  https://dontkillmyapp.com",
                "",
                "This community-maintained database covers all major",
                "manufacturers and Android versions."
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(TerminalColors.Accent.copy(alpha = 0.1f))
                .clickable {
                    try {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://dontkillmyapp.com")
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Exception) { }
                }
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                text = "$ xdg-open https://dontkillmyapp.com",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Accent
                )
            )
            Text(
                text = "# Open dontkillmyapp.com in your browser",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Subtext
                ),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// =============================================================================
// Shared components
// =============================================================================

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = "[$title]",
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TerminalColors.Info
        ),
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun TerminalOutputBlock(lines: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        lines.forEach { line ->
            if (line.isEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
            } else {
                Text(
                    text = line,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (line.trimStart().startsWith("http"))
                            TerminalColors.Accent
                        else
                            TerminalColors.Output,
                        lineHeight = 17.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun BatterySectionDivider() {
    Spacer(modifier = Modifier.height(4.dp))
    HorizontalDivider(
        thickness = 1.dp,
        color = TerminalColors.Selection
    )
    Spacer(modifier = Modifier.height(4.dp))
}

// =============================================================================
// Utility
// =============================================================================

/**
 * Checks whether the app is currently exempt from battery optimization.
 *
 * @param context The application context.
 * @return `true` if battery optimization is disabled (app is exempt), `false` otherwise.
 */
private fun checkBatteryOptimizationStatus(context: Context): Boolean {
    return try {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    } catch (_: Exception) {
        false
    }
}
