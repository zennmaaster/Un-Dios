package com.castor.feature.notifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper that monitors whether the Castor notification listener has been granted access and
 * provides convenience methods for guiding the user to the system settings screen.
 *
 * The [isAccessGranted] [StateFlow] is periodically refreshed so that the UI can react
 * when the user navigates back after enabling the permission.
 */
@Singleton
class NotificationAccessHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "NotificationAccessHelper"

        /** Interval between permission status checks. */
        private const val POLL_INTERVAL_MS = 2_000L
    }

    private val _isAccessGranted = MutableStateFlow(checkAccess())

    /** Observable permission state. Emits `true` when the notification listener is enabled. */
    val isAccessGranted: StateFlow<Boolean> = _isAccessGranted.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        // Start a lightweight polling loop that keeps the state flow up-to-date.
        scope.launch {
            while (isActive) {
                val granted = checkAccess()
                if (_isAccessGranted.value != granted) {
                    _isAccessGranted.value = granted
                    Log.d(TAG, "Notification access changed: granted=$granted")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------------------

    /**
     * Performs a one-shot check of whether the notification listener is currently enabled.
     */
    fun checkAccess(): Boolean {
        return try {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false

            val componentName = ComponentName(context, CastorNotificationListener::class.java)
            flat.contains(componentName.flattenToString())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notification access", e)
            false
        }
    }

    /**
     * Forces a refresh of the [isAccessGranted] state flow.
     *
     * Call this when you know the user may have just changed the setting (e.g., on Activity
     * resume).
     */
    fun refresh() {
        _isAccessGranted.value = checkAccess()
    }

    /**
     * Creates an [Intent] that opens the system notification listener settings screen where the
     * user can enable Castor's notification access.
     *
     * The returned intent has [Intent.FLAG_ACTIVITY_NEW_TASK] so it can be started from a
     * non-Activity context.
     */
    fun createSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Convenience method that directly starts the notification listener settings activity.
     */
    fun openSettings() {
        try {
            context.startActivity(createSettingsIntent())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open notification listener settings", e)
        }
    }
}
