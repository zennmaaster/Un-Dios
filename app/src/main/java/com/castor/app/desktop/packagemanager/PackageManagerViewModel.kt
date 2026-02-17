package com.castor.app.desktop.packagemanager

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Represents an installed application with detailed package information.
 *
 * Used by the [PackageManagerViewModel] and [PackageManagerScreen] to
 * display app details in the GNOME Software-style package manager.
 *
 * @param packageName Unique package identifier (e.g., "com.spotify.music")
 * @param appName Human-readable application name
 * @param versionName Version string (e.g., "1.2.3")
 * @param versionCode Numeric version code for comparison
 * @param installTime Timestamp when the app was first installed
 * @param lastUpdateTime Timestamp of the last app update
 * @param appSize Size of the app in bytes (APK + data)
 * @param icon The app's launcher icon drawable
 * @param category Categorization label (e.g., "Productivity", "Media")
 * @param isSystemApp Whether this is a pre-installed system app
 */
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val installTime: Long,
    val lastUpdateTime: Long,
    val appSize: Long,
    val icon: Drawable?,
    val category: String,
    val isSystemApp: Boolean
)

/**
 * Represents a category grouping of apps in the package manager.
 *
 * @param id Unique category identifier
 * @param name Display name for the category
 * @param icon Material icon representing the category
 * @param packagePrefixes Known package name prefixes for auto-classification
 */
data class AppCategory(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val packagePrefixes: List<String> = emptyList()
)

/**
 * Sort order options for the app list in the package manager.
 */
enum class SortOrder {
    /** Sort alphabetically by app name (A-Z). */
    NAME,

    /** Sort by APK/app size (largest first). */
    SIZE,

    /** Sort by last update time (most recent first). */
    LAST_UPDATED,

    /** Sort by install time (oldest first). */
    INSTALL_DATE
}

/**
 * ViewModel for the GNOME Software-style package manager screen.
 *
 * Loads all installed applications using Android's PackageManager API,
 * classifies them into categories based on well-known package prefixes,
 * and provides search, filter, and sort functionality.
 *
 * All heavy package scanning work runs on [Dispatchers.IO] to keep
 * the UI thread responsive.
 *
 * @param context Application context for accessing PackageManager
 */
@HiltViewModel
class PackageManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** All installed applications loaded from PackageManager. */
    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())

    /** Whether apps are currently being loaded. */
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Current search query text. */
    val searchQuery = MutableStateFlow("")

    /** Currently selected category filter (null = show all). */
    val selectedCategory = MutableStateFlow<String?>(null)

    /** Current sort order. */
    val sortOrder = MutableStateFlow(SortOrder.NAME)

    /** Predefined app categories with known package prefix mappings. */
    val categories: List<AppCategory> = listOf(
        AppCategory(
            id = "productivity",
            name = "Productivity",
            icon = Icons.Default.Work,
            packagePrefixes = listOf(
                "com.microsoft.office", "com.google.android.apps.docs",
                "com.google.android.calendar", "com.google.android.gm",
                "com.notion", "com.todoist", "com.evernote", "com.dropbox",
                "com.google.android.keep", "com.microsoft.office.outlook"
            )
        ),
        AppCategory(
            id = "communication",
            name = "Communication",
            icon = Icons.Default.ChatBubble,
            packagePrefixes = listOf(
                "com.whatsapp", "com.microsoft.teams", "com.slack",
                "com.discord", "com.Slack", "com.telegram", "com.viber",
                "com.skype", "org.telegram", "com.google.android.apps.messaging",
                "com.facebook.orca", "com.snapchat"
            )
        ),
        AppCategory(
            id = "media",
            name = "Media",
            icon = Icons.Default.Movie,
            packagePrefixes = listOf(
                "com.spotify", "com.google.android.youtube", "com.netflix",
                "com.amazon.avod", "com.google.android.apps.photos",
                "com.soundcloud", "com.deezer", "com.apple.android.music",
                "com.hulu", "com.disney"
            )
        ),
        AppCategory(
            id = "games",
            name = "Games",
            icon = Icons.Default.Games,
            packagePrefixes = listOf(
                "com.supercell", "com.king", "com.rovio",
                "com.epicgames", "com.mojang", "com.activision",
                "com.ea.game", "com.gameloft", "com.zynga"
            )
        ),
        AppCategory(
            id = "utilities",
            name = "Utilities",
            icon = Icons.Default.Build,
            packagePrefixes = listOf(
                "com.android.calculator", "com.google.android.deskclock",
                "com.android.contacts", "com.android.dialer",
                "com.google.android.apps.walletnfcrel",
                "com.android.vending", "com.google.android.apps.maps"
            )
        ),
        AppCategory(
            id = "system",
            name = "System",
            icon = Icons.Default.Settings,
            packagePrefixes = listOf(
                "com.android.settings", "com.android.systemui",
                "com.android.providers", "com.google.android.gms",
                "com.google.android.gsf", "com.android.bluetooth",
                "com.android.nfc"
            )
        ),
        AppCategory(
            id = "development",
            name = "Development",
            icon = Icons.Default.Code,
            packagePrefixes = listOf(
                "com.termux", "com.github", "com.gitlab",
                "io.github", "com.jetbrains", "com.google.android.studio"
            )
        )
    )

    /**
     * Filtered and sorted list of apps, combining search, category, and sort filters.
     *
     * This is the primary data source for the PackageManagerScreen list.
     * Recomputes whenever the search query, selected category, or sort order changes.
     */
    val filteredApps: StateFlow<List<InstalledApp>> = combine(
        _apps,
        searchQuery,
        selectedCategory,
        sortOrder
    ) { apps, query, category, sort ->
        var result = apps

        // Filter by search query
        if (query.isNotBlank()) {
            val lowerQuery = query.lowercase()
            result = result.filter { app ->
                app.appName.lowercase().contains(lowerQuery) ||
                    app.packageName.lowercase().contains(lowerQuery)
            }
        }

        // Filter by category
        if (category != null) {
            result = result.filter { app -> app.category == category }
        }

        // Sort
        result = when (sort) {
            SortOrder.NAME -> result.sortedBy { it.appName.lowercase() }
            SortOrder.SIZE -> result.sortedByDescending { it.appSize }
            SortOrder.LAST_UPDATED -> result.sortedByDescending { it.lastUpdateTime }
            SortOrder.INSTALL_DATE -> result.sortedBy { it.installTime }
        }

        result
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    init {
        loadApps()
    }

    /**
     * Loads all installed applications from PackageManager on the IO thread.
     *
     * Queries all apps with launcher activities, extracts package info,
     * and classifies each app into a category based on its package name prefix.
     */
    fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
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

                val apps = resolveInfos.mapNotNull { resolveInfo ->
                    val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                    val pkgName = activityInfo.packageName

                    // Skip our own package
                    if (pkgName == context.packageName) return@mapNotNull null

                    try {
                        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getPackageInfo(
                                pkgName,
                                PackageManager.PackageInfoFlags.of(0)
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getPackageInfo(pkgName, 0)
                        }

                        val appInfo = activityInfo.applicationInfo
                        val label = resolveInfo.loadLabel(pm)?.toString() ?: pkgName
                        val icon = try {
                            resolveInfo.loadIcon(pm)
                        } catch (_: Exception) {
                            null
                        }

                        val isSystem = appInfo != null &&
                            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                        val versionName = packageInfo.versionName ?: "unknown"
                        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            packageInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            packageInfo.versionCode.toLong()
                        }

                        // Estimate app size from APK path
                        val appSize = try {
                            appInfo?.sourceDir?.let { File(it).length() } ?: 0L
                        } catch (_: Exception) {
                            0L
                        }

                        val category = classifyApp(pkgName)

                        InstalledApp(
                            packageName = pkgName,
                            appName = label,
                            versionName = versionName,
                            versionCode = versionCode,
                            installTime = packageInfo.firstInstallTime,
                            lastUpdateTime = packageInfo.lastUpdateTime,
                            appSize = appSize,
                            icon = icon,
                            category = category,
                            isSystemApp = isSystem
                        )
                    } catch (_: PackageManager.NameNotFoundException) {
                        null
                    }
                }.sortedBy { it.appName.lowercase() }

                _apps.value = apps
            }
            _isLoading.value = false
        }
    }

    /**
     * Searches apps by the given query string.
     *
     * Updates the [searchQuery] state flow, which triggers recomputation
     * of [filteredApps].
     *
     * @param query The search query text
     */
    fun searchApps(query: String) {
        searchQuery.value = query
    }

    /**
     * Filters the app list by the given category ID.
     *
     * Pass null to clear the category filter and show all apps.
     *
     * @param category The category ID to filter by, or null to clear
     */
    fun filterByCategory(category: String?) {
        selectedCategory.value = category
    }

    /**
     * Sets the sort order for the app list.
     *
     * @param order The desired sort order
     */
    fun sortBy(order: SortOrder) {
        sortOrder.value = order
    }

    /**
     * Gets detailed info for a specific app by package name.
     *
     * @param packageName The package name to look up
     * @return The [InstalledApp] if found, null otherwise
     */
    fun getAppDetails(packageName: String): InstalledApp? {
        return _apps.value.find { it.packageName == packageName }
    }

    /**
     * Returns the count of apps in each category.
     *
     * Used by the category grid to show how many apps are in each group.
     *
     * @return Map of category ID to app count
     */
    fun getCategoryCounts(): Map<String, Int> {
        return _apps.value.groupBy { it.category }.mapValues { it.value.size }
    }

    /**
     * Classifies an app into a category based on its package name prefix.
     *
     * Checks the package name against known prefixes for each category.
     * Falls back to "Utilities" for unrecognized packages.
     *
     * @param packageName The package name to classify
     * @return The category name string
     */
    private fun classifyApp(packageName: String): String {
        for (category in categories) {
            if (category.packagePrefixes.any { prefix ->
                    packageName.startsWith(prefix)
                }) {
                return category.id
            }
        }

        // Heuristic classification based on common patterns
        return when {
            packageName.contains("game", ignoreCase = true) -> "games"
            packageName.contains("music", ignoreCase = true) ||
                packageName.contains("video", ignoreCase = true) ||
                packageName.contains("player", ignoreCase = true) -> "media"
            packageName.contains("chat", ignoreCase = true) ||
                packageName.contains("messenger", ignoreCase = true) -> "communication"
            packageName.startsWith("com.android.") -> "system"
            packageName.startsWith("com.google.android.") -> "utilities"
            else -> "utilities"
        }
    }
}
