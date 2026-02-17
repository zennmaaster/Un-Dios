package com.castor.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = CastorPrimary,
    onPrimary = CastorOnPrimary,
    primaryContainer = CastorPrimaryContainer,
    onPrimaryContainer = CastorOnPrimaryContainer,
    secondary = CastorSecondary,
    onSecondary = CastorOnSecondary,
    secondaryContainer = CastorSecondaryContainer,
    tertiary = CastorTertiary,
    tertiaryContainer = CastorTertiaryContainer,
    surface = CastorSurface,
    surfaceVariant = CastorSurfaceVariant,
    onSurface = CastorOnSurface
)

private val DarkColorScheme = darkColorScheme(
    primary = CastorPrimaryDark,
    onPrimary = CastorOnPrimaryDark,
    primaryContainer = CastorPrimaryContainerDark,
    surface = CastorSurfaceDark,
    onSurface = CastorOnSurfaceDark
)

/**
 * Root theme composable for the Un-Dios launcher.
 *
 * Wraps Material 3 theming with the terminal color scheme provided
 * through [LocalTerminalColors]. All composables that read from
 * [TerminalColors] will automatically pick up the active color scheme.
 *
 * @param terminalColorScheme The terminal color scheme to apply.
 *   Defaults to [CatppuccinMocha] (the original default).
 * @param darkTheme Whether the Material 3 color scheme should use dark mode.
 * @param dynamicColor Whether to use Material You dynamic colors (API 31+).
 * @param content The composable content tree.
 */
@Composable
fun CastorTheme(
    terminalColorScheme: TerminalColorScheme = CatppuccinMocha,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalTerminalColors provides terminalColorScheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CastorTypography,
            content = content
        )
    }
}
