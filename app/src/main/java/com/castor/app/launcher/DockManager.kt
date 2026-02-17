package com.castor.app.launcher

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing a user-pinned app in the quick-launch dock.
 *
 * @param packageName The app's unique package identifier (e.g. "com.spotify.music")
 * @param label Human-readable app name displayed beneath the icon
 * @param icon The app's launcher icon drawable, loaded lazily from PackageManager.
 *             Null if the app has been uninstalled or the icon cannot be loaded.
 */
data class PinnedApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

/**
 * Application-scoped manager for the customizable quick-launch dock.
 *
 * Persists the list of pinned app package names as a JSON-style comma-separated
 * string in the launcher DataStore. Exposes the resolved [PinnedApp] list as a
 * [StateFlow] so the dock UI recomposes reactively when pins change.
 *
 * When no apps have been explicitly pinned, the dock falls back to showing
 * the 4 most-used apps from UsageStats (if available) to provide a useful
 * default experience. Supports up to [MAX_PINNED_APPS] (6) dock slots.
 *
 * Injected as a singleton via Hilt so that both the home screen dock and the
 * app drawer's "Pin to Dock" action share the same state.
 *
 * Usage:
 * ```kotlin
 * val pinnedApps by dockManager.pinnedApps.collectAsState()
 * dockManager.pinApp("com.spotify.music")
 * dockManager.unpinApp("com.spotify.music")
 * dockManager.reorderApps(newOrder)
 * ```
 */
@Singleton
class DockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.launcherDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        /** Maximum number of pinned apps in the dock. */
        const val MAX_PINNED_APPS = 6

        /** DataStore key for persisted pinned package names. */
        val PINNED_APPS_KEY = stringPreferencesKey("dock.pinned_apps")

        /** Delimiter for the comma-separated package name string. */
        private const val DELIMITER = ","
    }

    // =========================================================================
    // State
    // =========================================================================

    /** Raw package name list from DataStore (source of truth). */
    private val _pinnedPackageNames = MutableStateFlow<List<String>>(emptyList())

    /** Whether user has explicitly configured dock pins (vs. using defaults). */
    private val _hasExplicitPins = MutableStateFlow(false)

    /** Resolved pinned apps with labels and icons, ready for the dock UI. */
    private val _pinnedApps = MutableStateFlow<List<PinnedApp>>(emptyList())
    val pinnedApps: StateFlow<List<PinnedApp>> = _pinnedApps.asStateFlow()

    /** Whether a given package name is currently pinned. */
    fun isPinned(packageName: String): Boolean {
        return _pinnedPackageNames.value.contains(packageName)
    }

    // =========================================================================
    // Initialization
    // =========================================================================

    init {
        scope.launch {
            loadPinnedApps()
        }

        // Continuously observe DataStore changes.
        scope.launch {
            dataStore.data.collect { prefs ->
                val raw = prefs[PINNED_APPS_KEY] ?: ""
                val packages = if (raw.isBlank()) emptyList() else raw.split(DELIMITER)
                _pinnedPackageNames.value = packages
                _hasExplicitPins.value = raw.isNotBlank()
                resolveApps(packages)
            }
        }
    }

    // =========================================================================
    // Public actions
    // =========================================================================

    /**
     * Pin an app to the dock by package name.
     *
     * If the dock is already at max capacity ([MAX_PINNED_APPS]), the request
     * is silently ignored. If the app is already pinned, this is a no-op.
     *
     * @param packageName Package name of the app to pin
     */
    fun pinApp(packageName: String) {
        val current = _pinnedPackageNames.value.toMutableList()
        if (current.contains(packageName)) return
        if (current.size >= MAX_PINNED_APPS) return

        current.add(packageName)
        persistPins(current)
    }

    /**
     * Remove a pinned app from the dock.
     *
     * @param packageName Package name of the app to unpin
     */
    fun unpinApp(packageName: String) {
        val current = _pinnedPackageNames.value.toMutableList()
        if (!current.remove(packageName)) return
        persistPins(current)
    }

    /**
     * Reorder the dock by providing a new list of package names.
     *
     * The new order must contain the same set of package names as the current
     * list. Any discrepancies are silently corrected by falling back to the
     * new order as-is (to avoid losing pins).
     *
     * @param newOrder New ordering of pinned package names
     */
    fun reorderApps(newOrder: List<String>) {
        persistPins(newOrder.take(MAX_PINNED_APPS))
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Load the initial pinned apps list from DataStore on startup.
     */
    private suspend fun loadPinnedApps() {
        val prefs = dataStore.data.first()
        val raw = prefs[PINNED_APPS_KEY] ?: ""
        val packages = if (raw.isBlank()) emptyList() else raw.split(DELIMITER)
        _pinnedPackageNames.value = packages
        _hasExplicitPins.value = raw.isNotBlank()

        if (packages.isEmpty()) {
            // Default: show top 4 most-used apps from UsageStats
            val defaults = getDefaultDockApps()
            resolveApps(defaults.map { it.packageName })
            _pinnedApps.value = defaults
        } else {
            resolveApps(packages)
        }
    }

    /**
     * Persist the pinned package names list to DataStore.
     */
    private fun persistPins(packageNames: List<String>) {
        _pinnedPackageNames.value = packageNames
        _hasExplicitPins.value = packageNames.isNotEmpty()
        scope.launch {
            dataStore.edit { prefs ->
                prefs[PINNED_APPS_KEY] = packageNames.joinToString(DELIMITER)
            }
            resolveApps(packageNames)
        }
    }

    /**
     * Resolve package names into [PinnedApp] instances by loading labels and
     * icons from PackageManager. Runs on the IO dispatcher.
     */
    private fun resolveApps(packageNames: List<String>) {
        val pm = context.packageManager
        val resolved = packageNames.mapNotNull { pkgName ->
            try {
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(
                        pkgName,
                        PackageManager.ApplicationInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkgName, 0)
                }
                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = try {
                    pm.getApplicationIcon(appInfo)
                } catch (_: Exception) {
                    null
                }
                PinnedApp(
                    packageName = pkgName,
                    label = label,
                    icon = icon
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // App uninstalled -- skip it
                null
            }
        }
        _pinnedApps.value = resolved
    }

    /**
     * Returns the top 4 most-used apps as default dock entries when no explicit
     * pins are configured. Uses the same UsageStats query as AppDrawerViewModel
     * but limited to 4 results.
     */
    private fun getDefaultDockApps(): List<PinnedApp> {
        return try {
            val usageStatsManager = context.getSystemService(
                Context.USAGE_STATS_SERVICE
            ) as? android.app.usage.UsageStatsManager ?: return emptyList()

            val endTime = System.currentTimeMillis()
            val startTime = endTime - 7 * 24 * 60 * 60 * 1000L // 7 days

            val stats = usageStatsManager.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_WEEKLY,
                startTime,
                endTime
            ) ?: return emptyList()

            val pm = context.packageManager
            val launcherPackages = getLaunchablePackages(pm)

            stats
                .filter { it.packageName in launcherPackages }
                .filter { it.packageName != context.packageName }
                .filter { it.totalTimeInForeground > 0 }
                .sortedByDescending { it.totalTimeInForeground }
                .take(4)
                .mapNotNull { usage ->
                    try {
                        val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getApplicationInfo(
                                usage.packageName,
                                PackageManager.ApplicationInfoFlags.of(0)
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getApplicationInfo(usage.packageName, 0)
                        }
                        PinnedApp(
                            packageName = usage.packageName,
                            label = pm.getApplicationLabel(appInfo).toString(),
                            icon = try { pm.getApplicationIcon(appInfo) } catch (_: Exception) { null }
                        )
                    } catch (_: PackageManager.NameNotFoundException) {
                        null
                    }
                }
        } catch (_: SecurityException) {
            // PACKAGE_USAGE_STATS permission not granted
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Returns the set of package names that have a launchable activity.
     * Used to filter usage stats to only include user-facing apps.
     */
    private fun getLaunchablePackages(pm: PackageManager): Set<String> {
        val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
        }
        return resolveInfos.mapNotNull { it.activityInfo?.packageName }.toSet()
    }
}
