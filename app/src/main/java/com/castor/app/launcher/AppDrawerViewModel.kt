package com.castor.app.launcher

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Data class representing an installed application on the device.
 *
 * @param packageName The app's unique package identifier (e.g. "com.spotify.music")
 * @param label Human-readable app name
 * @param icon The app's launcher icon drawable (nullable if unavailable)
 * @param isSystemApp Whether this is a pre-installed system app
 * @param lastUsed Timestamp of last usage (from UsageStatsManager), 0 if unknown
 * @param usageCount Number of times the app was launched in the tracking period
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    val lastUsed: Long = 0,
    val usageCount: Int = 0
)

/**
 * ViewModel for the App Drawer, managing the full list of installed launchable
 * applications, search/filter state, and recent apps from UsageStats.
 *
 * Uses PackageManager.queryIntentActivities() with ACTION_MAIN + CATEGORY_LAUNCHER
 * to enumerate all apps the user can launch. Integrates with UsageStatsManager
 * (when permission is granted) to surface recently-used apps at the top.
 */
@HiltViewModel
class AppDrawerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** All installed launchable apps, sorted alphabetically. */
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps

    /** Current text in the search bar. */
    val searchQuery = MutableStateFlow("")

    /** Recently used apps (from UsageStatsManager), max 8. */
    private val _recentApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val recentApps: StateFlow<List<AppInfo>> = _recentApps

    /** Whether apps are currently being loaded. */
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    /**
     * Apps filtered by the current search query. When the query is empty,
     * returns the full sorted list. Otherwise, matches against app label
     * (case-insensitive) and package name.
     */
    val filteredApps: StateFlow<List<AppInfo>> = combine(
        _installedApps,
        searchQuery
    ) { apps, query ->
        if (query.isBlank()) {
            apps
        } else {
            val lowerQuery = query.lowercase()
            apps.filter { app ->
                app.label.lowercase().contains(lowerQuery) ||
                    app.packageName.lowercase().contains(lowerQuery)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    init {
        loadApps()
    }

    /**
     * Queries PackageManager for all launchable activities and populates
     * [_installedApps] and [_recentApps]. Runs on the IO dispatcher to
     * avoid blocking the main thread during icon loading.
     */
    fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }

                val resolveInfos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(
                        mainIntent,
                        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
                }

                // Build the usage map for last-used timestamps and counts
                val usageMap = getUsageStats()

                val apps = resolveInfos.mapNotNull { resolveInfo ->
                    val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                    val pkgName = activityInfo.packageName

                    // Skip our own package in the drawer
                    if (pkgName == context.packageName) return@mapNotNull null

                    val label = resolveInfo.loadLabel(pm)?.toString() ?: pkgName
                    val icon = try {
                        resolveInfo.loadIcon(pm)
                    } catch (_: Exception) {
                        null
                    }

                    val isSystem = (activityInfo.applicationInfo?.flags
                        ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0

                    val usage = usageMap[pkgName]

                    AppInfo(
                        packageName = pkgName,
                        label = label,
                        icon = icon,
                        isSystemApp = isSystem,
                        lastUsed = usage?.first ?: 0L,
                        usageCount = usage?.second ?: 0
                    )
                }.sortedBy { it.label.lowercase() }

                _installedApps.value = apps

                // Recent apps: top 8 by last-used time, excluding apps not used
                _recentApps.value = apps
                    .filter { it.lastUsed > 0 }
                    .sortedByDescending { it.lastUsed }
                    .take(8)
            }
            _isLoading.value = false
        }
    }

    /**
     * Launches the specified app via its package name. Creates a launch intent
     * from PackageManager and starts the activity. Silently fails if the app
     * cannot be resolved (e.g., it was uninstalled).
     */
    fun launchApp(appInfo: AppInfo) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(appInfo.packageName)
        launchIntent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(it)
            } catch (_: Exception) {
                // App could not be launched (uninstalled, disabled, etc.)
            }
        }
    }

    /**
     * Opens the system App Info settings page for the given app, allowing
     * the user to manage permissions, storage, etc.
     */
    fun openAppInfo(appInfo: AppInfo) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${appInfo.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Settings could not be opened
        }
    }

    /**
     * Requests uninstallation of the given app via the system uninstall dialog.
     * Only works for user-installed apps; system apps will show an error.
     */
    fun uninstallApp(appInfo: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:${appInfo.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Uninstall could not be initiated
        }
    }

    /**
     * Queries UsageStatsManager for app usage data over the last 7 days.
     * Returns a map of packageName -> Pair(lastTimeUsed, launchCount).
     *
     * Requires the PACKAGE_USAGE_STATS permission (granted via Settings > Usage Access).
     * Returns an empty map if permission is not granted.
     */
    private fun getUsageStats(): Map<String, Pair<Long, Int>> {
        return try {
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    ?: return emptyMap()

            val endTime = System.currentTimeMillis()
            val startTime = endTime - 7 * 24 * 60 * 60 * 1000L // 7 days

            val usageStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_WEEKLY,
                startTime,
                endTime
            )

            if (usageStatsList.isNullOrEmpty()) return emptyMap()

            usageStatsList.associate { stats ->
                stats.packageName to Pair(
                    stats.lastTimeUsed,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        stats.appLaunchCount
                    } else {
                        0
                    }
                )
            }
        } catch (_: SecurityException) {
            // PACKAGE_USAGE_STATS permission not granted
            emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
