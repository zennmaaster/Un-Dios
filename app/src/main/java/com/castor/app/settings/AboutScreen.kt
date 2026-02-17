package com.castor.app.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.theme.TerminalColors
import java.io.BufferedReader
import java.io.FileReader

/**
 * About screen styled as a terminal information display.
 *
 * Shows app version info, device/system info, a neofetch-style ASCII art
 * section, open source licenses, and credits.
 *
 * Styled as: `$ cat /etc/un-dios/about`
 *
 * @param onBack Called when the user navigates back.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appInfo = remember { getAppVersionInfo(context) }
    val kernelVersion = remember { readKernelVersion() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
            .statusBarsPadding()
    ) {
        // Top bar
        AboutTopBar(onBack = onBack)

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
            item { AboutFileHeader() }

            // App info section
            item { AppInfoSection(appInfo = appInfo) }

            item { AboutSectionDivider() }

            // System info section
            item { SystemInfoSection(kernelVersion = kernelVersion) }

            item { AboutSectionDivider() }

            // Neofetch section
            item { NeofetchSection(appInfo = appInfo, kernelVersion = kernelVersion) }

            item { AboutSectionDivider() }

            // Open source licenses
            item { LicensesSection() }

            item { AboutSectionDivider() }

            // Links section
            item { LinksSection(context = context) }

            item { AboutSectionDivider() }

            // Credits
            item { CreditsSection() }

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
private fun AboutTopBar(onBack: () -> Unit) {
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
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = TerminalColors.Accent,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "/etc/un-dios/about",
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
private fun AboutFileHeader() {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "$ cat /etc/un-dios/about",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Prompt
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "# Un-Dios system information",
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
// App info section
// =============================================================================

@Composable
private fun AppInfoSection(appInfo: AppVersionInfo) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        AboutSectionHeader(title = "app-info")

        Spacer(modifier = Modifier.height(8.dp))

        AboutValueRow(key = "app.name", value = "Un-Dios", valueColor = TerminalColors.Accent)
        Spacer(modifier = Modifier.height(4.dp))
        AboutValueRow(key = "app.version", value = appInfo.versionName)
        Spacer(modifier = Modifier.height(4.dp))
        AboutValueRow(key = "app.build", value = appInfo.versionCode.toString())
        Spacer(modifier = Modifier.height(4.dp))
        AboutValueRow(key = "app.package", value = "com.castor.app")
        Spacer(modifier = Modifier.height(4.dp))
        AboutValueRow(
            key = "app.min_sdk",
            value = "29 (Android 10)",
            description = "Minimum supported version"
        )
        Spacer(modifier = Modifier.height(4.dp))
        AboutValueRow(
            key = "app.target_sdk",
            value = "35 (Android 15)",
            description = "Target API level"
        )
        Spacer(modifier = Modifier.height(4.dp))
        AboutValueRow(
            key = "app.codename",
            value = "Castor",
            valueColor = TerminalColors.Accent,
            description = "Internal project name"
        )
    }
}

// =============================================================================
// System info section
// =============================================================================

@Composable
private fun SystemInfoSection(kernelVersion: String) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        AboutSectionHeader(title = "system-info")

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "$ uname -a",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Prompt
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        AboutValueRow(
            key = "device.manufacturer",
            value = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        )
        Spacer(modifier = Modifier.height(4.dp))
        AboutValueRow(key = "device.model", value = Build.MODEL)
        Spacer(modifier = Modifier.height(4.dp))
        AboutValueRow(
            key = "android.version",
            value = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        )
        Spacer(modifier = Modifier.height(4.dp))
        AboutValueRow(key = "android.build", value = Build.DISPLAY)
        Spacer(modifier = Modifier.height(4.dp))
        AboutValueRow(
            key = "kernel.version",
            value = kernelVersion,
            description = "From /proc/version"
        )
        Spacer(modifier = Modifier.height(4.dp))
        AboutValueRow(key = "arch", value = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
    }
}

// =============================================================================
// Neofetch section
// =============================================================================

@Composable
private fun NeofetchSection(appInfo: AppVersionInfo, kernelVersion: String) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        AboutSectionHeader(title = "neofetch")

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "$ neofetch --un-dios",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Prompt
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(TerminalColors.Surface.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            // ASCII art + info grid side by side
            val asciiArt = listOf(
                "  _   _         ____  _",
                " | | | |_ __   |  _ \\(_) ___  ___",
                " | | | | '_ \\  | | | | |/ _ \\/ __|",
                " | |_| | | | | | |_| | | (_) \\__ \\",
                "  \\___/|_| |_| |____/|_|\\___/|___/",
                ""
            )

            val infoLines = listOf(
                "OS" to "Android ${Build.VERSION.RELEASE}",
                "Host" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "Kernel" to kernelVersion,
                "Shell" to "un-dios ${appInfo.versionName}",
                "SDK" to "API ${Build.VERSION.SDK_INT}",
                "Arch" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
            )

            // ASCII art
            asciiArt.forEach { line ->
                Text(
                    text = line,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Accent
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Info grid
            infoLines.forEach { (label, value) ->
                Row(modifier = Modifier.padding(vertical = 1.dp)) {
                    Text(
                        text = "$label: ",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Info
                        )
                    )
                    Text(
                        text = value,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TerminalColors.Output
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Color palette row
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val colors = listOf(
                    TerminalColors.Error,
                    TerminalColors.Warning,
                    TerminalColors.Success,
                    TerminalColors.Info,
                    TerminalColors.Accent,
                    TerminalColors.Prompt,
                    TerminalColors.Command,
                    TerminalColors.Output
                )
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}

// =============================================================================
// Licenses section
// =============================================================================

@Composable
private fun LicensesSection() {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        AboutSectionHeader(title = "open-source-licenses")

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "# Third-party libraries and their licenses:",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Subtext
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        val licenses = listOf(
            LicenseEntry(
                name = "Jetpack Compose",
                description = "Modern declarative UI toolkit for Android",
                license = "Apache 2.0",
                url = "https://developer.android.com/jetpack/compose"
            ),
            LicenseEntry(
                name = "Hilt (Dagger)",
                description = "Dependency injection framework",
                license = "Apache 2.0",
                url = "https://dagger.dev/hilt/"
            ),
            LicenseEntry(
                name = "Room",
                description = "SQLite object-mapping persistence library",
                license = "Apache 2.0",
                url = "https://developer.android.com/training/data-storage/room"
            ),
            LicenseEntry(
                name = "OkHttp",
                description = "HTTP client for the JVM and Android",
                license = "Apache 2.0",
                url = "https://square.github.io/okhttp/"
            ),
            LicenseEntry(
                name = "Retrofit",
                description = "Type-safe HTTP client for Android and Java",
                license = "Apache 2.0",
                url = "https://square.github.io/retrofit/"
            ),
            LicenseEntry(
                name = "Coil",
                description = "Image loading library backed by Kotlin Coroutines",
                license = "Apache 2.0",
                url = "https://coil-kt.github.io/coil/"
            ),
            LicenseEntry(
                name = "DataStore",
                description = "Data storage solution for key-value and typed objects",
                license = "Apache 2.0",
                url = "https://developer.android.com/topic/libraries/architecture/datastore"
            ),
            LicenseEntry(
                name = "Kotlin Coroutines",
                description = "Asynchronous programming framework for Kotlin",
                license = "Apache 2.0",
                url = "https://kotlinlang.org/docs/coroutines-overview.html"
            ),
            LicenseEntry(
                name = "Material 3",
                description = "Material Design 3 components for Compose",
                license = "Apache 2.0",
                url = "https://m3.material.io/"
            )
        )

        licenses.forEach { entry ->
            LicenseRow(entry = entry)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun LicenseRow(entry: LicenseEntry) {
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
            Text(
                text = entry.name,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Command
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "[${entry.license}]",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Info
                )
            )
        }
        Text(
            text = "# ${entry.description}",
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
// Links section
// =============================================================================

@Composable
private fun LinksSection(context: Context) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        AboutSectionHeader(title = "links")

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
                            Uri.parse("https://github.com/zennmaaster/Un-Dios")
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Exception) { }
                }
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                text = "$ git remote -v",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Prompt
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "origin  https://github.com/zennmaaster/Un-Dios (fetch)",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Accent
                )
            )
            Text(
                text = "origin  https://github.com/zennmaaster/Un-Dios (push)",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Accent
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "# Tap to open in browser",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Subtext
                )
            )
        }
    }
}

// =============================================================================
// Credits section
// =============================================================================

@Composable
private fun CreditsSection() {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        AboutSectionHeader(title = "credits")

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(TerminalColors.Surface.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                text = "$ cat /etc/un-dios/CREDITS",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Prompt
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Un-Dios",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Accent
                )
            )
            Text(
                text = "Open convergence platform for Android",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Output
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Built with Claude Code by Anthropic",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Info
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "# Privacy-first. Local-first. Fully on-device.",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Subtext
                )
            )
            Text(
                text = "# Your phone, your rules.",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Subtext
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Copyright (c) 2024-2025 Un-Dios Contributors",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Timestamp
                )
            )
            Text(
                text = "Licensed under the MIT License",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }
    }
}

// =============================================================================
// Shared components
// =============================================================================

@Composable
private fun AboutSectionHeader(title: String) {
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
private fun AboutValueRow(
    key: String,
    value: String,
    valueColor: Color = TerminalColors.Prompt,
    description: String? = null
) {
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

        if (description != null) {
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
}

@Composable
private fun AboutSectionDivider() {
    Spacer(modifier = Modifier.height(4.dp))
    HorizontalDivider(
        thickness = 1.dp,
        color = TerminalColors.Selection
    )
    Spacer(modifier = Modifier.height(4.dp))
}

// =============================================================================
// Data classes
// =============================================================================

private data class LicenseEntry(
    val name: String,
    val description: String,
    val license: String,
    val url: String
)

private data class AppVersionInfo(
    val versionName: String,
    val versionCode: Long
)

// =============================================================================
// Utility functions
// =============================================================================

/**
 * Reads app version info from the PackageManager.
 *
 * @param context The application context.
 * @return [AppVersionInfo] containing the version name and code.
 */
private fun getAppVersionInfo(context: Context): AppVersionInfo {
    return try {
        val packageInfo: PackageInfo = context.packageManager.getPackageInfo(
            context.packageName, 0
        )
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        AppVersionInfo(
            versionName = packageInfo.versionName ?: "0.1.0",
            versionCode = versionCode
        )
    } catch (_: Exception) {
        AppVersionInfo(versionName = "0.1.0", versionCode = 1)
    }
}

/**
 * Reads the kernel version from /proc/version.
 *
 * @return The first line of /proc/version, truncated to 60 characters.
 */
private fun readKernelVersion(): String {
    return try {
        val reader = BufferedReader(FileReader("/proc/version"))
        val line = reader.readLine() ?: "unknown"
        reader.close()
        // Extract just the kernel version (e.g., "Linux version 5.15.0-...")
        val parts = line.split(" ")
        if (parts.size >= 3) {
            parts[2]  // Usually the version number
        } else {
            line.take(60)
        }
    } catch (_: Exception) {
        "unknown"
    }
}
