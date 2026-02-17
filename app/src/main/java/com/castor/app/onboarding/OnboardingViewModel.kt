package com.castor.app.onboarding

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel managing the first-launch onboarding wizard state.
 *
 * Tracks the current step, checks permission statuses, and persists
 * completion state to DataStore so the wizard is only shown once
 * (unless the onboarding version is bumped for new features).
 *
 * Permission checks are designed to be re-evaluated when the user
 * returns from system settings screens, using [refreshPermissionStatuses].
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** Current step index in the onboarding wizard (0-7). */
    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    /** Total number of steps in the onboarding wizard. */
    val totalSteps = 8 // Steps 0 through 7

    /** Observable permission statuses, refreshed on each lifecycle resume. */
    private val _permissionStatuses = MutableStateFlow(PermissionStatuses())
    val permissionStatuses: StateFlow<PermissionStatuses> = _permissionStatuses.asStateFlow()

    private val dataStore = context.onboardingDataStore

    init {
        refreshPermissionStatuses()
    }

    /**
     * Navigate to a specific onboarding step.
     *
     * @param step The step index (0-based, clamped to valid range).
     */
    fun goToStep(step: Int) {
        _currentStep.value = step.coerceIn(0, totalSteps - 1)
    }

    /**
     * Advance to the next onboarding step. No-op if already on the last step.
     */
    fun nextStep() {
        val next = (_currentStep.value + 1).coerceAtMost(totalSteps - 1)
        _currentStep.value = next
    }

    /**
     * Go back to the previous onboarding step. No-op if already on the first step.
     */
    fun previousStep() {
        val prev = (_currentStep.value - 1).coerceAtLeast(0)
        _currentStep.value = prev
    }

    /**
     * Re-check all permission statuses. Should be called whenever the screen
     * resumes (e.g., after returning from a system settings activity).
     */
    fun refreshPermissionStatuses() {
        _permissionStatuses.value = PermissionStatuses(
            isDefaultLauncher = isDefaultLauncher(),
            isNotificationAccessGranted = isNotificationAccessGranted(),
            isAccessibilityServiceEnabled = isAccessibilityServiceEnabled(),
            isStorageGranted = isStorageGranted(),
            isBatteryOptimizationDisabled = isBatteryOptimizationDisabled(),
            isCalendarGranted = isCalendarGranted()
        )
    }

    /**
     * Check whether Un-Dios is currently set as the default home launcher.
     *
     * @return true if Un-Dios is the system's default home app.
     */
    fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    /**
     * Check whether the notification listener service has been granted access.
     *
     * Reads the `enabled_notification_listeners` secure setting to find
     * if our [CastorNotificationListener] component is listed.
     *
     * @return true if notification access is granted for Un-Dios.
     */
    fun isNotificationAccessGranted(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val componentName = ComponentName(
            context.packageName,
            "com.castor.feature.notifications.CastorNotificationListener"
        ).flattenToString()
        return enabledListeners.contains(componentName)
    }

    /**
     * Check whether the media accessibility service is enabled.
     *
     * Queries the [AccessibilityManager] for currently enabled services
     * and checks if our [MediaAccessibilityService] is among them.
     *
     * @return true if the accessibility service is enabled.
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as? AccessibilityManager ?: return false
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        val targetComponent = ComponentName(
            context.packageName,
            "com.castor.feature.media.accessibility.MediaAccessibilityService"
        ).flattenToString()
        return enabledServices.any {
            it.resolveInfo.serviceInfo.let { si ->
                ComponentName(si.packageName, si.name).flattenToString() == targetComponent
            }
        }
    }

    /**
     * Check whether storage access has been granted.
     *
     * On Android 11+ (API 30+), checks [MANAGE_EXTERNAL_STORAGE].
     * On Android 10 (API 29), checks [READ_EXTERNAL_STORAGE].
     *
     * @return true if the app has storage read access.
     */
    fun isStorageGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check whether battery optimization is disabled for Un-Dios.
     *
     * When battery optimization is disabled, the app can run background
     * agents without being killed by Doze mode.
     *
     * @return true if the app is whitelisted from battery optimization.
     */
    fun isBatteryOptimizationDisabled(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    /**
     * Check whether both READ_CALENDAR and WRITE_CALENDAR permissions are granted.
     *
     * @return true if calendar read and write access is available.
     */
    fun isCalendarGranted(): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    /**
     * Mark onboarding as completed and persist the state to DataStore.
     *
     * This prevents the onboarding wizard from appearing on subsequent launches.
     */
    fun completeOnboarding() {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[OnboardingPreferences.ONBOARDING_COMPLETED] = true
                preferences[OnboardingPreferences.ONBOARDING_VERSION] =
                    OnboardingPreferences.CURRENT_VERSION
            }
        }
    }

    /**
     * Observe whether onboarding has been completed.
     *
     * @return A [Flow] emitting true if onboarding is done, false otherwise.
     */
    fun isOnboardingCompleted(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[OnboardingPreferences.ONBOARDING_COMPLETED] ?: false
        }
    }

    /**
     * Get the manufacturer name (lowercase) for OEM-specific battery tips.
     *
     * @return The device manufacturer string in lowercase.
     */
    fun getManufacturer(): String {
        return Build.MANUFACTURER.lowercase()
    }
}

/**
 * Data class holding the current status of all permissions checked during onboarding.
 *
 * Each field corresponds to a permission or system access that the onboarding
 * wizard guides the user through granting.
 */
data class PermissionStatuses(
    /** Whether Un-Dios is the default home launcher. */
    val isDefaultLauncher: Boolean = false,
    /** Whether notification listener access is granted. */
    val isNotificationAccessGranted: Boolean = false,
    /** Whether the accessibility service is enabled. */
    val isAccessibilityServiceEnabled: Boolean = false,
    /** Whether storage access is granted. */
    val isStorageGranted: Boolean = false,
    /** Whether battery optimization is disabled for the app. */
    val isBatteryOptimizationDisabled: Boolean = false,
    /** Whether calendar read/write permissions are granted. */
    val isCalendarGranted: Boolean = false
)
