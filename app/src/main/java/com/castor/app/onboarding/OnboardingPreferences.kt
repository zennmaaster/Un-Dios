package com.castor.app.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * DataStore-backed preferences for onboarding and setup-readiness persistence.
 *
 * Stores onboarding completion/version and the latest post-onboarding setup
 * readiness snapshot so the app can keep validating critical prerequisites.
 */
val Context.onboardingDataStore by preferencesDataStore(name = "onboarding_prefs")

/**
 * Keys for onboarding and setup-readiness preferences.
 */
object OnboardingPreferences {
    /** Whether the user has completed the onboarding wizard at least once. */
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

    /**
     * The version of onboarding that was last completed.
     * Increment this when adding new onboarding steps that require
     * existing users to re-run the wizard.
     */
    val ONBOARDING_VERSION = intPreferencesKey("onboarding_version")

    /** Current onboarding version. Bump when new steps are added. */
    const val CURRENT_VERSION = 2

    /** Epoch millis of the latest setup-readiness check. */
    val READINESS_LAST_CHECK_MS = longPreferencesKey("readiness_last_check_ms")

    /** Snapshot fields from the latest readiness check. */
    val READINESS_DEFAULT_LAUNCHER = booleanPreferencesKey("readiness_default_launcher")
    val READINESS_NOTIFICATION_ACCESS = booleanPreferencesKey("readiness_notification_access")
    val READINESS_ACCESSIBILITY_SERVICE = booleanPreferencesKey("readiness_accessibility_service")
    val READINESS_STORAGE_ACCESS = booleanPreferencesKey("readiness_storage_access")
    val READINESS_BATTERY_OPT_DISABLED = booleanPreferencesKey("readiness_battery_opt_disabled")
    val READINESS_CALENDAR_ACCESS = booleanPreferencesKey("readiness_calendar_access")
}
