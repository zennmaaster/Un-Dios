package com.castor.app.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * DataStore-backed preferences for onboarding state persistence.
 *
 * Stores whether the user has completed the first-launch onboarding wizard
 * and which version of onboarding was last shown (to support future
 * re-onboarding when new permissions or features are added).
 *
 * Usage:
 * ```
 * val dataStore = context.onboardingDataStore
 * val completed = dataStore.data.map { it[OnboardingPreferences.ONBOARDING_COMPLETED] ?: false }
 * ```
 */
val Context.onboardingDataStore by preferencesDataStore(name = "onboarding_prefs")

/**
 * Keys for onboarding-related DataStore preferences.
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
    const val CURRENT_VERSION = 1
}
