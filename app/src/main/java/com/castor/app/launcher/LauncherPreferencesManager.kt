package com.castor.app.launcher

import android.content.Context
import androidx.datastore.preferences.core.edit
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
 * Application-scoped manager for launcher preferences.
 *
 * Persists all user-configurable launcher settings to DataStore and exposes
 * each as a [StateFlow] so that the UI recomposes immediately when a setting
 * changes. Injected as a singleton via Hilt so that all screens share the
 * same preference state.
 *
 * Pattern follows [ThemeManager] — a dedicated `launcher_prefs` DataStore
 * file with a [CoroutineScope] for background persistence.
 *
 * Usage from a `@Composable`:
 * ```kotlin
 * val showDock by launcherPrefs.showDock.collectAsState()
 * ```
 *
 * Usage from a ViewModel or non-Compose code:
 * ```kotlin
 * launcherPrefs.setShowDock(false)
 * ```
 */
@Singleton
class LauncherPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.launcherDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // =========================================================================
    // [homescreen] StateFlows
    // =========================================================================

    private val _showDock = MutableStateFlow(LauncherPreferences.Defaults.SHOW_DOCK)
    /** Whether the quick-launch dock is visible on the home screen. */
    val showDock: StateFlow<Boolean> = _showDock.asStateFlow()

    private val _swipeUpDrawer = MutableStateFlow(LauncherPreferences.Defaults.SWIPE_UP_DRAWER)
    /** Whether swiping up on the home screen opens the app drawer. */
    val swipeUpDrawer: StateFlow<Boolean> = _swipeUpDrawer.asStateFlow()

    private val _doubleTapLock = MutableStateFlow(LauncherPreferences.Defaults.DOUBLE_TAP_LOCK)
    /** Whether double-tapping the home screen locks the device. */
    val doubleTapLock: StateFlow<Boolean> = _doubleTapLock.asStateFlow()

    private val _showAgentLabels = MutableStateFlow(LauncherPreferences.Defaults.SHOW_AGENT_LABELS)
    /** Whether labels are shown under agent cards. */
    val showAgentLabels: StateFlow<Boolean> = _showAgentLabels.asStateFlow()

    // =========================================================================
    // [privacy] StateFlows
    // =========================================================================

    private val _analyticsEnabled = MutableStateFlow(LauncherPreferences.Defaults.ANALYTICS_ENABLED)
    /** Whether anonymous usage analytics are enabled. */
    val analyticsEnabled: StateFlow<Boolean> = _analyticsEnabled.asStateFlow()

    private val _cloudApiEnabled = MutableStateFlow(LauncherPreferences.Defaults.CLOUD_API_ENABLED)
    /** Whether cloud AI fallback is allowed. */
    val cloudApiEnabled: StateFlow<Boolean> = _cloudApiEnabled.asStateFlow()

    // =========================================================================
    // [intelligence] StateFlows
    // =========================================================================

    private val _dailySummary = MutableStateFlow(LauncherPreferences.Defaults.DAILY_SUMMARY)
    /** Whether the morning briefing notification is enabled. */
    val dailySummary: StateFlow<Boolean> = _dailySummary.asStateFlow()

    private val _categoryTracking = MutableStateFlow(LauncherPreferences.Defaults.CATEGORY_TRACKING)
    /** Whether app usage category tracking is enabled. */
    val categoryTracking: StateFlow<Boolean> = _categoryTracking.asStateFlow()

    // =========================================================================
    // Initialization
    // =========================================================================

    init {
        // Load persisted values on construction.
        scope.launch {
            val prefs = dataStore.data.first()
            _showDock.value =
                prefs[LauncherPreferences.SHOW_DOCK] ?: LauncherPreferences.Defaults.SHOW_DOCK
            _swipeUpDrawer.value =
                prefs[LauncherPreferences.SWIPE_UP_DRAWER] ?: LauncherPreferences.Defaults.SWIPE_UP_DRAWER
            _doubleTapLock.value =
                prefs[LauncherPreferences.DOUBLE_TAP_LOCK] ?: LauncherPreferences.Defaults.DOUBLE_TAP_LOCK
            _showAgentLabels.value =
                prefs[LauncherPreferences.SHOW_AGENT_LABELS] ?: LauncherPreferences.Defaults.SHOW_AGENT_LABELS
            _analyticsEnabled.value =
                prefs[LauncherPreferences.ANALYTICS_ENABLED] ?: LauncherPreferences.Defaults.ANALYTICS_ENABLED
            _cloudApiEnabled.value =
                prefs[LauncherPreferences.CLOUD_API_ENABLED] ?: LauncherPreferences.Defaults.CLOUD_API_ENABLED
            _dailySummary.value =
                prefs[LauncherPreferences.DAILY_SUMMARY] ?: LauncherPreferences.Defaults.DAILY_SUMMARY
            _categoryTracking.value =
                prefs[LauncherPreferences.CATEGORY_TRACKING] ?: LauncherPreferences.Defaults.CATEGORY_TRACKING
        }

        // Continuously observe DataStore changes (e.g. from another process or restore).
        observePreference(LauncherPreferences.SHOW_DOCK, LauncherPreferences.Defaults.SHOW_DOCK, _showDock)
        observePreference(LauncherPreferences.SWIPE_UP_DRAWER, LauncherPreferences.Defaults.SWIPE_UP_DRAWER, _swipeUpDrawer)
        observePreference(LauncherPreferences.DOUBLE_TAP_LOCK, LauncherPreferences.Defaults.DOUBLE_TAP_LOCK, _doubleTapLock)
        observePreference(LauncherPreferences.SHOW_AGENT_LABELS, LauncherPreferences.Defaults.SHOW_AGENT_LABELS, _showAgentLabels)
        observePreference(LauncherPreferences.ANALYTICS_ENABLED, LauncherPreferences.Defaults.ANALYTICS_ENABLED, _analyticsEnabled)
        observePreference(LauncherPreferences.CLOUD_API_ENABLED, LauncherPreferences.Defaults.CLOUD_API_ENABLED, _cloudApiEnabled)
        observePreference(LauncherPreferences.DAILY_SUMMARY, LauncherPreferences.Defaults.DAILY_SUMMARY, _dailySummary)
        observePreference(LauncherPreferences.CATEGORY_TRACKING, LauncherPreferences.Defaults.CATEGORY_TRACKING, _categoryTracking)
    }

    /**
     * Helper that observes a single boolean DataStore key and keeps the
     * corresponding [MutableStateFlow] in sync.
     */
    private fun observePreference(
        key: androidx.datastore.preferences.core.Preferences.Key<Boolean>,
        default: Boolean,
        target: MutableStateFlow<Boolean>
    ) {
        scope.launch {
            dataStore.data
                .map { prefs -> prefs[key] ?: default }
                .collect { value -> target.value = value }
        }
    }

    // =========================================================================
    // [homescreen] setters
    // =========================================================================

    /** Show or hide the quick-launch dock and persist the choice. */
    fun setShowDock(value: Boolean) {
        _showDock.value = value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[LauncherPreferences.SHOW_DOCK] = value
            }
        }
    }

    /** Enable or disable swipe-up-to-open-drawer gesture and persist the choice. */
    fun setSwipeUpDrawer(value: Boolean) {
        _swipeUpDrawer.value = value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[LauncherPreferences.SWIPE_UP_DRAWER] = value
            }
        }
    }

    /** Enable or disable double-tap-to-lock gesture and persist the choice. */
    fun setDoubleTapLock(value: Boolean) {
        _doubleTapLock.value = value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[LauncherPreferences.DOUBLE_TAP_LOCK] = value
            }
        }
    }

    /** Show or hide agent card labels and persist the choice. */
    fun setShowAgentLabels(value: Boolean) {
        _showAgentLabels.value = value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[LauncherPreferences.SHOW_AGENT_LABELS] = value
            }
        }
    }

    // =========================================================================
    // [privacy] setters
    // =========================================================================

    /** Enable or disable anonymous usage analytics and persist the choice. */
    fun setAnalyticsEnabled(value: Boolean) {
        _analyticsEnabled.value = value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[LauncherPreferences.ANALYTICS_ENABLED] = value
            }
        }
    }

    /** Enable or disable cloud AI fallback and persist the choice. */
    fun setCloudApiEnabled(value: Boolean) {
        _cloudApiEnabled.value = value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[LauncherPreferences.CLOUD_API_ENABLED] = value
            }
        }
    }

    // =========================================================================
    // [intelligence] setters
    // =========================================================================

    /** Enable or disable the daily summary notification and persist the choice. */
    fun setDailySummary(value: Boolean) {
        _dailySummary.value = value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[LauncherPreferences.DAILY_SUMMARY] = value
            }
        }
    }

    /** Enable or disable app usage category tracking and persist the choice. */
    fun setCategoryTracking(value: Boolean) {
        _categoryTracking.value = value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[LauncherPreferences.CATEGORY_TRACKING] = value
            }
        }
    }

    // =========================================================================
    // Reset
    // =========================================================================

    /**
     * Reset all launcher preferences to their default values.
     *
     * Clears the entire DataStore and resets all in-memory StateFlows to
     * their defaults. This is an irreversible operation.
     */
    fun resetToDefaults() {
        _showDock.value = LauncherPreferences.Defaults.SHOW_DOCK
        _swipeUpDrawer.value = LauncherPreferences.Defaults.SWIPE_UP_DRAWER
        _doubleTapLock.value = LauncherPreferences.Defaults.DOUBLE_TAP_LOCK
        _showAgentLabels.value = LauncherPreferences.Defaults.SHOW_AGENT_LABELS
        _analyticsEnabled.value = LauncherPreferences.Defaults.ANALYTICS_ENABLED
        _cloudApiEnabled.value = LauncherPreferences.Defaults.CLOUD_API_ENABLED
        _dailySummary.value = LauncherPreferences.Defaults.DAILY_SUMMARY
        _categoryTracking.value = LauncherPreferences.Defaults.CATEGORY_TRACKING

        scope.launch {
            dataStore.edit { prefs ->
                prefs.clear()
            }
        }
    }
}
