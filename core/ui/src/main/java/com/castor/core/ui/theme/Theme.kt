package com.castor.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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

@Composable
fun CastorTheme(
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CastorTypography,
        content = content
    )
}
