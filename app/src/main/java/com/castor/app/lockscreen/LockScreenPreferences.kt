package com.castor.app.lockscreen

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * DataStore-backed preferences for the terminal lock screen.
 *
 * Controls whether the lock screen is enabled, what UI elements it shows,
 * and how long the device can sit idle before auto-locking.
 *
 * Usage:
 * ```
 * val dataStore = context.lockScreenDataStore
 * val enabled = dataStore.data.map { it[LockScreenPreferences.LOCK_ENABLED] ?: true }
 * ```
 */
val Context.lockScreenDataStore by preferencesDataStore(name = "lock_screen_prefs")

/**
 * Keys for lock-screen-related DataStore preferences.
 */
object LockScreenPreferences {

    /** Whether the custom lock screen overlay is enabled. Default: true. */
    val LOCK_ENABLED = booleanPreferencesKey("lock_enabled")

    /**
     * Auto-lock timeout in milliseconds. The lock screen engages after this
     * duration of inactivity. Default: 60_000 (1 minute).
     * Set to [Long.MAX_VALUE] or -1 for "never".
     */
    val LOCK_TIMEOUT_MS = longPreferencesKey("lock_timeout_ms")

    /**
     * Whether to display the last 3 notification previews on the lock screen.
     * When false, notifications are hidden until authentication. Default: true.
     */
    val SHOW_NOTIFICATIONS_ON_LOCK = booleanPreferencesKey("show_notifications_on_lock")

    /**
     * Whether to play the Linux-style boot animation sequence when the
     * lock screen appears. When false, the main lock UI is shown immediately.
     * Default: true.
     */
    val SHOW_BOOT_ANIMATION = booleanPreferencesKey("show_boot_animation")
}
