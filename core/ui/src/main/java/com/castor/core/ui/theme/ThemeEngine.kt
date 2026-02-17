package com.castor.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * A fully described terminal theme combining identity metadata with
 * the concrete [TerminalColorScheme] color palette.
 *
 * This is the public-facing model that the theme picker and settings
 * screens work with. It wraps the lower-level [TerminalColorScheme]
 * with the [TerminalThemeType] enum, display name, and a dark-mode
 * flag so UI code doesn't need to map between the two manually.
 *
 * @property id   The enum identifier for persistence and lookup.
 * @property displayName  Human-readable name shown in the UI (kebab-case).
 * @property isDark  Whether the theme is a dark variant (affects status bar icons, etc.).
 * @property colors  The full color palette.
 */
data class TerminalTheme(
    val id: TerminalThemeType,
    val displayName: String,
    val isDark: Boolean,
    val colors: TerminalColorScheme
) {
    /** Convenience alias matching the legacy field names in [TerminalColors]. */
    val background: Color get() = colors.background
    val surface: Color get() = colors.surface
    val prompt: Color get() = colors.prompt
    val command: Color get() = colors.command
    val output: Color get() = colors.output
    val error: Color get() = colors.error
    val warning: Color get() = colors.warning
    val success: Color get() = colors.success
    val info: Color get() = colors.info
    val accent: Color get() = colors.accent
    val cursor: Color get() = colors.cursor
    val selection: Color get() = colors.selection
    val statusBar: Color get() = colors.statusBar
    val timestamp: Color get() = colors.timestamp
    val overlay: Color get() = colors.overlay
    val subtext: Color get() = colors.subtext
    val badgeRed: Color get() = colors.badgeRed
    val privacyLocal: Color get() = colors.privacyLocal
    val privacyCloud: Color get() = colors.privacyCloud
    val privacyAnonymized: Color get() = colors.privacyAnonymized
}

// =============================================================================
// Theme Registry
// =============================================================================

/**
 * Central registry of all built-in terminal themes.
 *
 * Provides lookup by [TerminalThemeType] and exposes the ordered list of
 * available themes for the picker UI. This is a pure object with no DI
 * dependencies, so it can be used from both Compose and non-Compose code.
 *
 * To add a new theme:
 * 1. Add an entry to [TerminalThemeType].
 * 2. Define a [TerminalColorScheme] instance in `TerminalTheme.kt`.
 * 3. Wire the mapping in [TerminalThemeType.toColorScheme].
 * 4. Register the [TerminalTheme] in [themes] below.
 */
object ThemeRegistry {

    // -- Catppuccin Mocha (default dark) ------------------------------------

    private val catppuccinMochaTheme = TerminalTheme(
        id = TerminalThemeType.CATPPUCCIN_MOCHA,
        displayName = "catppuccin-mocha",
        isDark = true,
        colors = CatppuccinMocha
    )

    // -- Catppuccin Latte (light) -------------------------------------------

    private val catppuccinLatteTheme = TerminalTheme(
        id = TerminalThemeType.CATPPUCCIN_LATTE,
        displayName = "catppuccin-latte",
        isDark = false,
        colors = CatppuccinLatte
    )

    // -- Nord (dark) --------------------------------------------------------

    private val nordTheme = TerminalTheme(
        id = TerminalThemeType.NORD,
        displayName = "nord",
        isDark = true,
        colors = Nord
    )

    // -- Dracula (dark) -----------------------------------------------------

    private val draculaTheme = TerminalTheme(
        id = TerminalThemeType.DRACULA,
        displayName = "dracula",
        isDark = true,
        colors = Dracula
    )

    // -- Gruvbox Dark -------------------------------------------------------

    private val gruvboxDarkTheme = TerminalTheme(
        id = TerminalThemeType.GRUVBOX_DARK,
        displayName = "gruvbox-dark",
        isDark = true,
        colors = GruvboxDark
    )

    // -- Solarized Dark -----------------------------------------------------

    private val solarizedDarkTheme = TerminalTheme(
        id = TerminalThemeType.SOLARIZED_DARK,
        displayName = "solarized-dark",
        isDark = true,
        colors = SolarizedDark
    )

    // -- Registry data ------------------------------------------------------

    /**
     * All built-in themes in display order.
     */
    val themes: List<TerminalTheme> = listOf(
        catppuccinMochaTheme,
        catppuccinLatteTheme,
        nordTheme,
        draculaTheme,
        gruvboxDarkTheme,
        solarizedDarkTheme
    )

    /**
     * Ordered list of all available theme identifiers.
     */
    val availableThemeIds: List<TerminalThemeType> =
        themes.map { it.id }

    /**
     * Internal lookup map keyed by [TerminalThemeType].
     */
    private val byId: Map<TerminalThemeType, TerminalTheme> =
        themes.associateBy { it.id }

    /**
     * The default theme used when no persisted preference exists.
     */
    val defaultTheme: TerminalTheme = catppuccinMochaTheme

    /**
     * Returns the [TerminalTheme] for [themeId], falling back to
     * [defaultTheme] if the ID is somehow unrecognized.
     */
    fun getTheme(themeId: TerminalThemeType): TerminalTheme =
        byId[themeId] ?: defaultTheme

    /**
     * Returns the [TerminalColorScheme] for [themeId].
     * Convenience shortcut equivalent to `getTheme(themeId).colors`.
     */
    fun getColorScheme(themeId: TerminalThemeType): TerminalColorScheme =
        getTheme(themeId).colors

    /**
     * Returns `true` when the given theme has a light background,
     * meaning status bar icons should be drawn dark.
     */
    fun isDarkTheme(themeId: TerminalThemeType): Boolean =
        getTheme(themeId).isDark
}

// =============================================================================
// Theme description helpers (for the preview UI)
// =============================================================================

/**
 * A short human-readable label for the theme, suitable for UI badges.
 * E.g. "Dark", "Light".
 */
val TerminalTheme.modeLabel: String
    get() = if (isDark) "Dark" else "Light"

/**
 * Returns a list of the six most visually distinctive colors from this
 * theme, useful for rendering a compact palette swatch.
 */
val TerminalTheme.paletteSwatches: List<Color>
    get() = listOf(
        colors.background,
        colors.surface,
        colors.prompt,
        colors.accent,
        colors.info,
        colors.error
    )

/**
 * Returns the appropriate content color (light or dark) to draw
 * readable text on top of this theme's background.
 */
val TerminalTheme.onBackground: Color
    get() = if (colors.background.luminance() > 0.5f) {
        Color(0xFF1C1B1F) // dark text on light background
    } else {
        Color(0xFFE6E1E5) // light text on dark background
    }

/**
 * Sample terminal lines used by the theme preview cards.
 *
 * Each entry is a pair of (color-slot-name, display-text) so the
 * preview renderer can apply the corresponding color from the scheme.
 */
data class TerminalPreviewLine(
    val color: (TerminalColorScheme) -> Color,
    val text: String
)

/**
 * Standard set of preview lines shown inside each theme card.
 */
val themePreviewLines: List<TerminalPreviewLine> = listOf(
    TerminalPreviewLine(
        color = { it.prompt },
        text = "castor@un-dios:~$"
    ),
    TerminalPreviewLine(
        color = { it.command },
        text = " ls -la --color"
    ),
    TerminalPreviewLine(
        color = { it.output },
        text = "drwxr-xr-x  12 castor"
    ),
    TerminalPreviewLine(
        color = { it.success },
        text = "[OK] System ready"
    ),
    TerminalPreviewLine(
        color = { it.error },
        text = "[ERR] Connection refused"
    ),
    TerminalPreviewLine(
        color = { it.accent },
        text = ":: Loading modules..."
    ),
    TerminalPreviewLine(
        color = { it.info },
        text = "[INFO] 3 updates available"
    ),
    TerminalPreviewLine(
        color = { it.warning },
        text = "[WARN] Battery low: 15%"
    )
)
