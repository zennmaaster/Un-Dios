package com.castor.app.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.castor.core.ui.theme.TerminalColorScheme
import com.castor.core.ui.theme.TerminalTheme
import com.castor.core.ui.theme.TerminalThemeType
import com.castor.core.ui.theme.ThemePreferences
import com.castor.core.ui.theme.ThemeRegistry
import com.castor.core.ui.theme.toColorScheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore instance for theme preferences.
 *
 * Stored in a dedicated file so theme prefs are independent of other
 * settings and can be migrated or cleared independently.
 */
private val Context.themeDataStore by preferencesDataStore(
    name = ThemePreferences.DATASTORE_NAME
)

/**
 * Application-scoped manager for the terminal color theme.
 *
 * Persists the user's selected [TerminalThemeType] to DataStore and exposes
 * it as a [StateFlow] so that the UI recomposes immediately when the theme
 * changes. Injected as a singleton via Hilt so that all screens share the
 * same theme state.
 *
 * The manager exposes three levels of granularity:
 * - [selectedTheme]  -- the raw [TerminalThemeType] enum value
 * - [currentTheme]   -- the full [TerminalTheme] with metadata + colors
 * - [getColorScheme] -- just the [TerminalColorScheme] palette
 *
 * Usage from a `@Composable`:
 * ```kotlin
 * val theme by themeManager.currentTheme.collectAsState()
 * CastorTheme(terminalColorScheme = theme.colors) { ... }
 * ```
 *
 * Usage from a ViewModel or other non-Compose code:
 * ```kotlin
 * themeManager.setTheme(TerminalThemeType.DRACULA)
 * ```
 */
@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.themeDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _selectedTheme = MutableStateFlow(TerminalThemeType.CATPPUCCIN_MOCHA)
    private val _currentTheme = MutableStateFlow(ThemeRegistry.defaultTheme)

    /** The currently selected theme type enum, updated live from DataStore. */
    val selectedTheme: StateFlow<TerminalThemeType> = _selectedTheme.asStateFlow()

    /**
     * The full [TerminalTheme] for the current selection, including display
     * name, dark/light flag, and the complete color scheme.
     */
    val currentTheme: StateFlow<TerminalTheme> = _currentTheme.asStateFlow()

    /** Ordered list of all available theme identifiers for the picker UI. */
    val availableThemes: List<TerminalThemeType> = ThemeRegistry.availableThemeIds

    init {
        // Load the persisted theme on initialization.
        scope.launch {
            val persisted = dataStore.data
                .map { prefs ->
                    val name = prefs[ThemePreferences.SELECTED_THEME]
                        ?: ThemePreferences.DEFAULT_THEME_NAME
                    TerminalThemeType.fromDisplayName(name)
                }
                .first()
            applyTheme(persisted)
        }

        // Continuously observe DataStore changes (e.g. from another process or restore).
        scope.launch {
            dataStore.data
                .map { prefs ->
                    val name = prefs[ThemePreferences.SELECTED_THEME]
                        ?: ThemePreferences.DEFAULT_THEME_NAME
                    TerminalThemeType.fromDisplayName(name)
                }
                .collect { theme ->
                    applyTheme(theme)
                }
        }
    }

    /**
     * Change the active theme and persist the selection.
     *
     * Updates both the in-memory state (for immediate UI recomposition)
     * and the DataStore (for persistence across restarts).
     */
    fun setTheme(theme: TerminalThemeType) {
        applyTheme(theme)
        scope.launch {
            dataStore.edit { prefs ->
                prefs[ThemePreferences.SELECTED_THEME] = theme.displayName
            }
        }
    }

    /**
     * Returns the [TerminalColorScheme] for the currently selected theme.
     */
    fun getColorScheme(): TerminalColorScheme {
        return _selectedTheme.value.toColorScheme()
    }

    /**
     * Returns the [TerminalTheme] for a specific theme ID.
     * Useful for preview cards in the theme picker.
     */
    fun getTheme(themeId: TerminalThemeType): TerminalTheme {
        return ThemeRegistry.getTheme(themeId)
    }

    /**
     * Internal helper to synchronize both StateFlows atomically.
     */
    private fun applyTheme(themeType: TerminalThemeType) {
        _selectedTheme.value = themeType
        _currentTheme.value = ThemeRegistry.getTheme(themeType)
    }
}
