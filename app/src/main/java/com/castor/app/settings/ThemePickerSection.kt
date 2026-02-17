package com.castor.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.theme.TerminalColorScheme
import com.castor.core.ui.theme.TerminalColors
import com.castor.core.ui.theme.TerminalThemeType
import com.castor.core.ui.theme.toColorScheme

/**
 * Theme picker section for the launcher settings screen.
 *
 * Displays a grid of 6 theme preview cards arranged in 2 columns x 3 rows.
 * Each card shows a miniature preview of the theme's key colors (background,
 * surface, accent, prompt) as horizontal strips, with the theme name below
 * in terminal monospace style.
 *
 * The currently selected theme has a highlighted border and a checkmark
 * indicator overlay. Tapping any card immediately applies that theme via
 * the [onThemeSelected] callback.
 *
 * @param selectedTheme The currently active theme type.
 * @param onThemeSelected Called when the user taps a theme card.
 * @param modifier Optional modifier for the section root.
 */
@Composable
fun ThemePickerSection(
    selectedTheme: TerminalThemeType,
    onThemeSelected: (TerminalThemeType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section header
        Text(
            text = "[theme]",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Info
            ),
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )

        Text(
            text = "# Select a terminal color scheme",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Subtext
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Theme grid: 2 columns x 3 rows
        val themes = TerminalThemeType.entries
        val rows = themes.chunked(2)

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            rows.forEach { rowThemes ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowThemes.forEach { themeType ->
                        ThemePreviewCard(
                            themeType = themeType,
                            colorScheme = themeType.toColorScheme(),
                            isSelected = themeType == selectedTheme,
                            onClick = { onThemeSelected(themeType) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // If the last row has an odd number of items, fill space
                    if (rowThemes.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * A single theme preview card showing a miniature color preview
 * and the theme's display name.
 *
 * The card shows four horizontal color strips representing the theme's
 * Background, Surface, Accent, and Prompt colors, giving the user an
 * at-a-glance sense of the palette.
 *
 * @param themeType The theme this card represents.
 * @param colorScheme The resolved color scheme for the preview.
 * @param isSelected Whether this is the currently active theme.
 * @param onClick Called when the card is tapped.
 * @param modifier Optional modifier.
 */
@Composable
private fun ThemePreviewCard(
    themeType: TerminalThemeType,
    colorScheme: TerminalColorScheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) TerminalColors.Accent else TerminalColors.Selection
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
        // Color preview area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .clip(RoundedCornerShape(4.dp))
        ) {
            // Background base
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .background(colorScheme.background)
            )

            // Color strips overlaid on the background
            Column(
                verticalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .padding(6.dp)
            ) {
                // Strip 1: Surface color
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colorScheme.surface)
                )

                // Strip 2: Prompt (green) color
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colorScheme.prompt)
                )

                // Strip 3: Accent color
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colorScheme.accent)
                )

                // Strip 4: Info + Error side by side
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colorScheme.info)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colorScheme.error)
                    )
                }
            }

            // Checkmark overlay for the selected theme
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

        // Theme name label
        Text(
            text = themeType.displayName,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) TerminalColors.Accent else TerminalColors.Command,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
    }
}
