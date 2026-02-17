package com.castor.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Defines all semantic color slots used by the Un-Dios terminal UI.
 *
 * Every UI element in the launcher reads its color from one of these fields
 * via the [TerminalColors] object, which delegates to the active scheme
 * provided through [LocalTerminalColors].
 *
 * To add a new theme, create an instance of this class with all fields
 * populated and register it in [TerminalThemeType].
 */
data class TerminalColorScheme(
    val background: Color,
    val surface: Color,
    val prompt: Color,
    val command: Color,
    val output: Color,
    val error: Color,
    val warning: Color,
    val success: Color,
    val info: Color,
    val accent: Color,
    val cursor: Color,
    val selection: Color,
    val statusBar: Color,
    val timestamp: Color,
    val overlay: Color,
    val subtext: Color,
    val badgeRed: Color,
    val privacyLocal: Color,
    val privacyCloud: Color,
    val privacyAnonymized: Color
)

/**
 * Enumeration of all built-in terminal color themes.
 *
 * Each entry maps to a predefined [TerminalColorScheme] instance.
 * The [displayName] is the terminal-friendly kebab-case label shown
 * in the theme picker UI.
 */
enum class TerminalThemeType(val displayName: String) {
    CATPPUCCIN_MOCHA("catppuccin-mocha"),
    CATPPUCCIN_LATTE("catppuccin-latte"),
    NORD("nord"),
    DRACULA("dracula"),
    GRUVBOX_DARK("gruvbox-dark"),
    SOLARIZED_DARK("solarized-dark");

    companion object {
        /**
         * Resolve a [TerminalThemeType] from its persisted [displayName].
         * Falls back to [CATPPUCCIN_MOCHA] if the name is unrecognized.
         */
        fun fromDisplayName(name: String): TerminalThemeType {
            return entries.firstOrNull { it.displayName == name } ?: CATPPUCCIN_MOCHA
        }
    }
}

// =============================================================================
// Predefined color schemes
// =============================================================================

/**
 * Catppuccin Mocha -- the default dark theme.
 * Warm, pastel colors on a deep purple-gray base.
 * https://github.com/catppuccin/catppuccin
 */
val CatppuccinMocha = TerminalColorScheme(
    background = Color(0xFF1E1E2E),
    surface = Color(0xFF313244),
    prompt = Color(0xFFA6E3A1),
    command = Color(0xFFCDD6F4),
    output = Color(0xFFBAC2DE),
    error = Color(0xFFF38BA8),
    warning = Color(0xFFFAB387),
    success = Color(0xFFA6E3A1),
    info = Color(0xFF89B4FA),
    accent = Color(0xFFCBA6F7),
    cursor = Color(0xFFF5E0DC),
    selection = Color(0xFF45475A),
    statusBar = Color(0xFF181825),
    timestamp = Color(0xFF6C7086),
    overlay = Color(0xFF11111B),
    subtext = Color(0xFF585B70),
    badgeRed = Color(0xFFF38BA8),
    privacyLocal = Color(0xFFA6E3A1),
    privacyCloud = Color(0xFFF9E2AF),
    privacyAnonymized = Color(0xFF89B4FA)
)

/**
 * Catppuccin Latte -- a light theme variant.
 * Soft, pastel colors on a warm off-white base.
 */
val CatppuccinLatte = TerminalColorScheme(
    background = Color(0xFFEFF1F5),
    surface = Color(0xFFCCD0DA),
    prompt = Color(0xFF40A02B),
    command = Color(0xFF4C4F69),
    output = Color(0xFF5C5F77),
    error = Color(0xFFD20F39),
    warning = Color(0xFFFE640B),
    success = Color(0xFF40A02B),
    info = Color(0xFF1E66F5),
    accent = Color(0xFF8839EF),
    cursor = Color(0xFFDC8A78),
    selection = Color(0xFFACB0BE),
    statusBar = Color(0xFFE6E9EF),
    timestamp = Color(0xFF8C8FA1),
    overlay = Color(0xFFDCE0E8),
    subtext = Color(0xFF6C6F85),
    badgeRed = Color(0xFFD20F39),
    privacyLocal = Color(0xFF40A02B),
    privacyCloud = Color(0xFFDF8E1D),
    privacyAnonymized = Color(0xFF1E66F5)
)

/**
 * Nord -- a dark, blue-gray arctic theme.
 * Cool blue tones with muted pastel accents.
 * https://www.nordtheme.com/
 */
val Nord = TerminalColorScheme(
    background = Color(0xFF2E3440),
    surface = Color(0xFF3B4252),
    prompt = Color(0xFFA3BE8C),
    command = Color(0xFFECEFF4),
    output = Color(0xFFD8DEE9),
    error = Color(0xFFBF616A),
    warning = Color(0xFFEBCB8B),
    success = Color(0xFFA3BE8C),
    info = Color(0xFF81A1C1),
    accent = Color(0xFF88C0D0),
    cursor = Color(0xFFD8DEE9),
    selection = Color(0xFF434C5E),
    statusBar = Color(0xFF242933),
    timestamp = Color(0xFF4C566A),
    overlay = Color(0xFF1D2128),
    subtext = Color(0xFF4C566A),
    badgeRed = Color(0xFFBF616A),
    privacyLocal = Color(0xFFA3BE8C),
    privacyCloud = Color(0xFFEBCB8B),
    privacyAnonymized = Color(0xFF81A1C1)
)

/**
 * Dracula -- a dark theme with vivid, high-contrast colors.
 * Rich purples and bright neons on a dark charcoal base.
 * https://draculatheme.com/
 */
val Dracula = TerminalColorScheme(
    background = Color(0xFF282A36),
    surface = Color(0xFF44475A),
    prompt = Color(0xFF50FA7B),
    command = Color(0xFFF8F8F2),
    output = Color(0xFFF8F8F2),
    error = Color(0xFFFF5555),
    warning = Color(0xFFFFB86C),
    success = Color(0xFF50FA7B),
    info = Color(0xFF8BE9FD),
    accent = Color(0xFFBD93F9),
    cursor = Color(0xFFF8F8F2),
    selection = Color(0xFF44475A),
    statusBar = Color(0xFF21222C),
    timestamp = Color(0xFF6272A4),
    overlay = Color(0xFF1E1F29),
    subtext = Color(0xFF6272A4),
    badgeRed = Color(0xFFFF5555),
    privacyLocal = Color(0xFF50FA7B),
    privacyCloud = Color(0xFFFFB86C),
    privacyAnonymized = Color(0xFF8BE9FD)
)

/**
 * Gruvbox Dark -- a retro, warm dark theme.
 * Earthy browns, warm oranges, and muted greens.
 * https://github.com/morhetz/gruvbox
 */
val GruvboxDark = TerminalColorScheme(
    background = Color(0xFF282828),
    surface = Color(0xFF3C3836),
    prompt = Color(0xFFB8BB26),
    command = Color(0xFFEBDBB2),
    output = Color(0xFFD5C4A1),
    error = Color(0xFFFB4934),
    warning = Color(0xFFFE8019),
    success = Color(0xFFB8BB26),
    info = Color(0xFF83A598),
    accent = Color(0xFFD3869B),
    cursor = Color(0xFFEBDBB2),
    selection = Color(0xFF504945),
    statusBar = Color(0xFF1D2021),
    timestamp = Color(0xFF928374),
    overlay = Color(0xFF1D2021),
    subtext = Color(0xFF928374),
    badgeRed = Color(0xFFFB4934),
    privacyLocal = Color(0xFFB8BB26),
    privacyCloud = Color(0xFFFE8019),
    privacyAnonymized = Color(0xFF83A598)
)

/**
 * Solarized Dark -- the classic low-contrast dark theme.
 * Blue-green base with carefully balanced accent colors.
 * https://ethanschoonover.com/solarized/
 */
val SolarizedDark = TerminalColorScheme(
    background = Color(0xFF002B36),
    surface = Color(0xFF073642),
    prompt = Color(0xFF859900),
    command = Color(0xFF839496),
    output = Color(0xFF93A1A1),
    error = Color(0xFFDC322F),
    warning = Color(0xFFCB4B16),
    success = Color(0xFF859900),
    info = Color(0xFF268BD2),
    accent = Color(0xFF6C71C4),
    cursor = Color(0xFF93A1A1),
    selection = Color(0xFF073642),
    statusBar = Color(0xFF001E26),
    timestamp = Color(0xFF586E75),
    overlay = Color(0xFF00212B),
    subtext = Color(0xFF586E75),
    badgeRed = Color(0xFFDC322F),
    privacyLocal = Color(0xFF859900),
    privacyCloud = Color(0xFFCB4B16),
    privacyAnonymized = Color(0xFF268BD2)
)

// =============================================================================
// Theme resolution
// =============================================================================

/**
 * Returns the [TerminalColorScheme] for the given [TerminalThemeType].
 */
fun TerminalThemeType.toColorScheme(): TerminalColorScheme = when (this) {
    TerminalThemeType.CATPPUCCIN_MOCHA -> CatppuccinMocha
    TerminalThemeType.CATPPUCCIN_LATTE -> CatppuccinLatte
    TerminalThemeType.NORD -> Nord
    TerminalThemeType.DRACULA -> Dracula
    TerminalThemeType.GRUVBOX_DARK -> GruvboxDark
    TerminalThemeType.SOLARIZED_DARK -> SolarizedDark
}

