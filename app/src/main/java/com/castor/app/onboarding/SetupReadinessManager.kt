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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted setup-readiness snapshot used after onboarding.
 *
 * This keeps checking core requirements and stores results so users can
 * revisit readiness state across app launches.
 */
data class SetupReadinessState(
    val checkedAtMillis: Long = 0L,
    val statuses: PermissionStatuses = PermissionStatuses(),
    val missingCritical: List<String> = emptyList(),
    val missingOptional: List<String> = emptyList()
) {
    val hasChecked: Boolean = checkedAtMillis > 0L
    val isReady: Boolean = missingCritical.isEmpty()
}

@Singleton
class SetupReadinessManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.onboardingDataStore

    val readiness: Flow<SetupReadinessState> = dataStore.data
        .map { preferences ->
            val statuses = PermissionStatuses(
                isDefaultLauncher = preferences[OnboardingPreferences.READINESS_DEFAULT_LAUNCHER] ?: false,
                isNotificationAccessGranted = preferences[OnboardingPreferences.READINESS_NOTIFICATION_ACCESS] ?: false,
                isAccessibilityServiceEnabled = preferences[OnboardingPreferences.READINESS_ACCESSIBILITY_SERVICE] ?: false,
                isStorageGranted = preferences[OnboardingPreferences.READINESS_STORAGE_ACCESS] ?: false,
                isBatteryOptimizationDisabled = preferences[OnboardingPreferences.READINESS_BATTERY_OPT_DISABLED] ?: false,
                isCalendarGranted = preferences[OnboardingPreferences.READINESS_CALENDAR_ACCESS] ?: false
            )

            SetupReadinessState(
                checkedAtMillis = preferences[OnboardingPreferences.READINESS_LAST_CHECK_MS] ?: 0L,
                statuses = statuses,
                missingCritical = getMissingCriticalItems(statuses),
                missingOptional = getMissingOptionalItems(statuses)
            )
        }
        .distinctUntilChanged()

    suspend fun maybeRefreshIfStale(maxAgeMillis: Long = DEFAULT_MAX_AGE_MS) {
        val lastCheck = dataStore.data
            .map { preferences ->
                preferences[OnboardingPreferences.READINESS_LAST_CHECK_MS] ?: 0L
            }
            .first()

        val isStale =
            lastCheck <= 0L || (System.currentTimeMillis() - lastCheck) >= maxAgeMillis

        if (isStale) {
            refresh()
        }
    }

    suspend fun refresh() {
        val statuses = checkStatuses()
        dataStore.edit { preferences ->
            preferences[OnboardingPreferences.READINESS_LAST_CHECK_MS] = System.currentTimeMillis()
            preferences[OnboardingPreferences.READINESS_DEFAULT_LAUNCHER] = statuses.isDefaultLauncher
            preferences[OnboardingPreferences.READINESS_NOTIFICATION_ACCESS] = statuses.isNotificationAccessGranted
            preferences[OnboardingPreferences.READINESS_ACCESSIBILITY_SERVICE] = statuses.isAccessibilityServiceEnabled
            preferences[OnboardingPreferences.READINESS_STORAGE_ACCESS] = statuses.isStorageGranted
            preferences[OnboardingPreferences.READINESS_BATTERY_OPT_DISABLED] = statuses.isBatteryOptimizationDisabled
            preferences[OnboardingPreferences.READINESS_CALENDAR_ACCESS] = statuses.isCalendarGranted
        }
    }

    private fun checkStatuses(): PermissionStatuses {
        return PermissionStatuses(
            isDefaultLauncher = isDefaultLauncher(),
            isNotificationAccessGranted = isNotificationAccessGranted(),
            isAccessibilityServiceEnabled = isAccessibilityServiceEnabled(),
            isStorageGranted = isStorageGranted(),
            isBatteryOptimizationDisabled = isBatteryOptimizationDisabled(),
            isCalendarGranted = isCalendarGranted()
        )
    }

    private fun getMissingCriticalItems(statuses: PermissionStatuses): List<String> {
        val missing = mutableListOf<String>()

        if (!statuses.isDefaultLauncher) {
            missing.add("Set Un-Dios as default launcher")
        }
        if (!statuses.isNotificationAccessGranted) {
            missing.add("Grant notification access")
        }
        if (!statuses.isStorageGranted) {
            missing.add("Grant storage access")
        }
        if (!statuses.isBatteryOptimizationDisabled) {
            missing.add("Disable battery optimization for Un-Dios")
        }
        if (!statuses.isCalendarGranted) {
            missing.add("Grant calendar access")
        }

        return missing
    }

    private fun getMissingOptionalItems(statuses: PermissionStatuses): List<String> {
        return buildList {
            if (!statuses.isAccessibilityServiceEnabled) {
                add("Enable accessibility service for Kindle and Audible sync")
            }
        }
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    private fun isNotificationAccessGranted(): Boolean {
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

    private fun isAccessibilityServiceEnabled(): Boolean {
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
            it.resolveInfo.serviceInfo.let { serviceInfo ->
                ComponentName(serviceInfo.packageName, serviceInfo.name)
                    .flattenToString() == targetComponent
            }
        }
    }

    private fun isStorageGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isBatteryOptimizationDisabled(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    private fun isCalendarGranted(): Boolean {
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

    companion object {
        private const val DEFAULT_MAX_AGE_MS = 6 * 60 * 60 * 1000L // 6h
    }
}
