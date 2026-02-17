package com.castor.app.launcher

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * DataStore instance for launcher-specific preferences.
 *
 * Stored in a dedicated file (`launcher_prefs`) so launcher settings are
 * independent of theme, lock screen, and onboarding preferences and can be
 * migrated or cleared independently.
 */
val Context.launcherDataStore by preferencesDataStore(name = "launcher_prefs")

/**
 * Keys for launcher-related DataStore preferences.
 *
 * Organized by section matching the settings screen layout:
 * - `home.*`         -- Home screen behavior (dock, gestures, labels)
 * - `privacy.*`      -- Analytics and cloud fallback toggles
 * - `intelligence.*` -- Smart features (daily summary, category tracking)
 */
object LauncherPreferences {

    // =========================================================================
    // [homescreen] section
    // =========================================================================

    /**
     * Whether the quick-launch dock is visible on the home screen.
     * Default: true.
     */
    val SHOW_DOCK = booleanPreferencesKey("home.show_dock")

    /**
     * Whether swiping up on the home screen opens the app drawer.
     * Default: true.
     */
    val SWIPE_UP_DRAWER = booleanPreferencesKey("home.swipe_up_drawer")

    /**
     * Whether double-tapping the home screen locks the device.
     * Default: true.
     */
    val DOUBLE_TAP_LOCK = booleanPreferencesKey("home.double_tap_lock")

    /**
     * Whether labels are shown under agent cards on the home screen.
     * Default: true.
     */
    val SHOW_AGENT_LABELS = booleanPreferencesKey("home.show_agent_labels")

    // =========================================================================
    // [privacy] section
    // =========================================================================

    /**
     * Whether anonymous usage analytics are enabled.
     * Default: false (opt-in only).
     */
    val ANALYTICS_ENABLED = booleanPreferencesKey("privacy.analytics_enabled")

    /**
     * Whether the app is allowed to fall back to cloud AI providers
     * when local inference is insufficient.
     * Default: false (local-first philosophy).
     */
    val CLOUD_API_ENABLED = booleanPreferencesKey("privacy.cloud_api_enabled")

    // =========================================================================
    // [intelligence] section
    // =========================================================================

    /**
     * Whether the morning briefing / daily summary notification is enabled.
     * Default: true.
     */
    val DAILY_SUMMARY = booleanPreferencesKey("intelligence.daily_summary")

    /**
     * Whether app usage is classified into categories for the
     * screen-time breakdown view.
     * Default: true.
     */
    val CATEGORY_TRACKING = booleanPreferencesKey("intelligence.category_tracking")

    // =========================================================================
    // Defaults
    // =========================================================================

    /** Default values for all launcher preferences, keyed by preference key name. */
    object Defaults {
        const val SHOW_DOCK = true
        const val SWIPE_UP_DRAWER = true
        const val DOUBLE_TAP_LOCK = true
        const val SHOW_AGENT_LABELS = true
        const val ANALYTICS_ENABLED = false
        const val CLOUD_API_ENABLED = false
        const val DAILY_SUMMARY = true
        const val CATEGORY_TRACKING = true
    }
}
