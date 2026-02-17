package com.castor.app.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.theme.TerminalColorScheme
import com.castor.core.ui.theme.TerminalColors
import com.castor.core.ui.theme.TerminalTheme
import com.castor.core.ui.theme.TerminalThemeType
import com.castor.core.ui.theme.ThemeRegistry
import com.castor.core.ui.theme.modeLabel
import com.castor.core.ui.theme.themePreviewLines

/**
 * Full-screen theme selector styled as a terminal configuration file.
 *
 * Displays `$ cat /etc/un-dios/themes.conf` at the top, followed by a
 * 2-column grid of theme preview cards. Each card renders a miniature
 * terminal preview showing the theme's key colors with sample text
 * lines (prompt, command, output, error, accent).
 *
 * The currently selected theme is indicated with a highlighted border
 * and a checkmark badge. Tapping any card immediately applies that
 * theme (live preview) via [ThemeManager.setTheme].
 *
 * @param themeManager The Hilt-injected [ThemeManager] singleton.
 * @param onBack Called when the user presses the back arrow.
 */
@Composable
fun ThemeSelectorScreen(
    themeManager: ThemeManager,
    onBack: () -> Unit
) {
    val selectedThemeType by themeManager.selectedTheme.collectAsState()
    val allThemes = ThemeRegistry.themes

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
            .statusBarsPadding()
    ) {
        // ---- Top bar ----
        ThemeSelectorTopBar(onBack = onBack)

        // ---- Content ----
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // Terminal file header
            item {
                ThemeSelectorFileHeader()
            }

            // Section comment
            item {
                Text(
                    text = "[themes]",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Info
                    ),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                Text(
                    text = "# ${allThemes.size} themes available | active = ${selectedThemeType.displayName}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Subtext
                    ),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Theme grid: 2 columns
            val rows = allThemes.chunked(2)
            items(rows) { rowThemes ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowThemes.forEach { theme ->
                        ThemePreviewCard(
                            theme = theme,
                            isSelected = theme.id == selectedThemeType,
                            onClick = { themeManager.setTheme(theme.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill space if the last row has an odd count
                    if (rowThemes.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // Current theme detail section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                CurrentThemeDetail(themeType = selectedThemeType)
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
private fun ThemeSelectorTopBar(onBack: () -> Unit) {
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
            imageVector = Icons.Default.Brush,
            contentDescription = null,
            tint = TerminalColors.Accent,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "/etc/un-dios/themes.conf",
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
private fun ThemeSelectorFileHeader() {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "$ cat /etc/un-dios/themes.conf",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Prompt
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "# Terminal color theme configuration",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Subtext
            )
        )
        Text(
            text = "# Tap a theme to preview and apply immediately",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Subtext
            )
        )
    }
}

// =============================================================================
// Theme preview card
// =============================================================================

/**
 * A rich preview card for a single terminal theme.
 *
 * Shows a mini terminal window with sample lines rendered in the theme's
 * actual colors. The card includes the theme name, a dark/light mode badge,
 * and a checkmark overlay when selected.
 *
 * @param theme The [TerminalTheme] to preview.
 * @param isSelected Whether this theme is currently active.
 * @param onClick Called when the card is tapped.
 * @param modifier Optional layout modifier.
 */
@Composable
private fun ThemePreviewCard(
    theme: TerminalTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) TerminalColors.Accent else TerminalColors.Selection,
        animationSpec = tween(durationMillis = 200),
        label = "border"
    )
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        // Mini terminal preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f)
                .clip(RoundedCornerShape(6.dp))
        ) {
            // Theme background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.colors.background)
            )

            // Mini terminal title bar + content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            ) {
                // Title bar
                MiniTitleBar(theme.colors)

                Spacer(modifier = Modifier.height(3.dp))

                // Terminal lines
                val previewLines = themePreviewLines.take(6)
                previewLines.forEach { line ->
                    Text(
                        text = line.text,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 6.sp,
                            lineHeight = 8.sp,
                            color = line.color(theme.colors)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            }

            // Checkmark overlay
            if (isSelected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(TerminalColors.Accent)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Theme name
        Text(
            text = theme.displayName,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) TerminalColors.Accent else TerminalColors.Command,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )

        // Dark/Light badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Icon(
                imageVector = if (theme.isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = null,
                tint = TerminalColors.Timestamp,
                modifier = Modifier.size(10.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = theme.modeLabel,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }
    }
}

/**
 * A miniature terminal title bar rendered inside the preview card.
 * Shows three small colored circles (close, minimize, maximize)
 * reminiscent of macOS window controls, using the theme's own colors.
 */
@Composable
private fun MiniTitleBar(colors: TerminalColorScheme) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .background(colors.statusBar)
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        // Window control dots
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(colors.error)
        )
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(colors.warning)
        )
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(colors.success)
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "un-dios",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 5.sp,
                color = colors.timestamp
            )
        )
    }
}

// =============================================================================
// Current theme detail
// =============================================================================

/**
 * A detail section below the grid showing the currently active theme's
 * metadata and a palette swatch of its key colors.
 */
@Composable
private fun CurrentThemeDetail(themeType: TerminalThemeType) {
    val theme = ThemeRegistry.getTheme(themeType)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Text(
            text = "[active-theme]",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Info
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Key-value pairs
        ThemeDetailRow(key = "name", value = theme.displayName, valueColor = TerminalColors.Accent)
        ThemeDetailRow(key = "mode", value = theme.modeLabel, valueColor = TerminalColors.Prompt)
        ThemeDetailRow(
            key = "id",
            value = theme.id.name,
            valueColor = TerminalColors.Command
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Palette swatch
        Text(
            text = "# color palette",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Subtext
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            PaletteSwatch(color = theme.colors.background, label = "bg")
            PaletteSwatch(color = theme.colors.surface, label = "sf")
            PaletteSwatch(color = theme.colors.prompt, label = "pr")
            PaletteSwatch(color = theme.colors.accent, label = "ac")
            PaletteSwatch(color = theme.colors.info, label = "if")
            PaletteSwatch(color = theme.colors.error, label = "er")
            PaletteSwatch(color = theme.colors.warning, label = "wn")
            PaletteSwatch(color = theme.colors.success, label = "ok")
        }
    }
}

@Composable
private fun ThemeDetailRow(
    key: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Text(
            text = "$key = ",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Command
            )
        )
        Text(
            text = "\"$value\"",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        )
    }
}

@Composable
private fun PaletteSwatch(
    color: Color,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
                .border(
                    width = 1.dp,
                    color = TerminalColors.Selection,
                    shape = RoundedCornerShape(4.dp)
                )
        )
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 7.sp,
                color = TerminalColors.Timestamp,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
