package com.castor.core.ui.theme

import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore preference keys for the terminal theme engine.
 *
 * These keys are used by the `ThemeManager` (in the `:app` module) to
 * persist the user's selected theme across sessions. They live in the
 * `:core:ui` module so that any module can reference them without
 * depending on `:app`.
 *
 * The stored value is the [TerminalThemeType.displayName] string
 * (e.g. "catppuccin-mocha", "nord"), which is resolved back to the
 * enum via [TerminalThemeType.fromDisplayName].
 *
 * Usage:
 * ```kotlin
 * // Writing
 * dataStore.edit { prefs ->
 *     prefs[ThemePreferences.SELECTED_THEME] = TerminalThemeType.DRACULA.displayName
 * }
 *
 * // Reading
 * val themeName = prefs[ThemePreferences.SELECTED_THEME]
 *     ?: ThemePreferences.DEFAULT_THEME_NAME
 * val themeType = TerminalThemeType.fromDisplayName(themeName)
 * ```
 */
object ThemePreferences {

    /**
     * Key for the currently selected terminal theme.
     *
     * Stored as the [TerminalThemeType.displayName] string so that the
     * persisted value is human-readable and survives enum reordering.
     */
    val SELECTED_THEME = stringPreferencesKey("selected_terminal_theme")

    /**
     * The default theme name used when no preference has been persisted.
     */
    const val DEFAULT_THEME_NAME = "catppuccin-mocha"

    /**
     * DataStore file name for theme preferences.
     * Kept separate from other settings so that theme prefs can be
     * exported, backed up, or cleared independently.
     */
    const val DATASTORE_NAME = "theme_prefs"
}
