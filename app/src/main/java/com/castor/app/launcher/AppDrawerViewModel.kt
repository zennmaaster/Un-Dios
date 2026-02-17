package com.castor.app.launcher

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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
import kotlin.math.min

// =============================================================================
// App category enum
// =============================================================================

/**
 * Categories for grouping installed apps in the drawer.
 *
 * Each category has a human-readable [displayName] and a terminal-style
 * [terminalLabel] rendered as a directory path (e.g. `~/apps/social/`).
 */
enum class AppCategory(val displayName: String, val terminalLabel: String) {
    SOCIAL("Social", "social/"),
    WORK("Work", "work/"),
    MEDIA("Media", "media/"),
    GAMES("Games", "games/"),
    UTILITIES("Utilities", "utils/"),
    SYSTEM("System", "sys/"),
    OTHER("Other", "other/");

    /** Short tab label with no trailing slash, e.g. "social" */
    val tabLabel: String get() = terminalLabel.removeSuffix("/")
}

// =============================================================================
// Data model
// =============================================================================

/**
 * Data class representing an installed application on the device.
 *
 * @param packageName The app's unique package identifier (e.g. "com.spotify.music")
 * @param label Human-readable app name
 * @param icon The app's launcher icon drawable (nullable if unavailable)
 * @param isSystemApp Whether this is a pre-installed system app
 * @param lastUsed Timestamp of last usage (from UsageStatsManager), 0 if unknown
 * @param usageCount Number of times the app was launched in the tracking period
 * @param category The resolved [AppCategory] for this app
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    val lastUsed: Long = 0,
    val usageCount: Int = 0,
    val category: AppCategory = AppCategory.OTHER
)

// =============================================================================
// Fuzzy search result
// =============================================================================

/**
 * Wraps an [AppInfo] with search-match metadata for highlighting.
 *
 * @param appInfo The matched app
 * @param matchedIndices Character indices in [AppInfo.label] that matched the query
 * @param score Lower is better. Used to sort search results by relevance.
 */
data class SearchResult(
    val appInfo: AppInfo,
    val matchedIndices: List<Int> = emptyList(),
    val score: Int = Int.MAX_VALUE
)

// =============================================================================
// ViewModel
// =============================================================================

/**
 * ViewModel for the App Drawer, managing the full list of installed launchable
 * applications, search/filter state, recent/frequent apps, and category grouping.
 *
 * Uses PackageManager.queryIntentActivities() with ACTION_MAIN + CATEGORY_LAUNCHER
 * to enumerate all apps the user can launch. Integrates with UsageStatsManager
 * (when permission is granted) to surface recently-used apps at the top.
 */
@HiltViewModel
class AppDrawerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dockManager: DockManager
) : ViewModel() {

    // =========================================================================
    // State — app lists
    // =========================================================================

    /** All installed launchable apps, sorted alphabetically. */
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps

    /** Current text in the search bar. */
    val searchQuery = MutableStateFlow("")

    /** Currently selected category filter. Null means "All". */
    val selectedCategory = MutableStateFlow<AppCategory?>(null)

    /** Recently used apps (from UsageStatsManager), max 8. */
    private val _recentApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val recentApps: StateFlow<List<AppInfo>> = _recentApps

    /** Whether apps are currently being loaded. */
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    // =========================================================================
    // State — in-memory usage tracking (session-local)
    // =========================================================================

    /** In-memory record of recently launched apps (most recent first, max 8). */
    private val _sessionRecentApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val sessionRecentApps: StateFlow<List<AppInfo>> = _sessionRecentApps

    /** In-memory launch count per package name. */
    private val _launchCounts = mutableMapOf<String, Int>()

    /** Top 4 most-frequently launched apps in this session. */
    private val _frequentApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val frequentApps: StateFlow<List<AppInfo>> = _frequentApps

    // =========================================================================
    // State — categorized apps
    // =========================================================================

    /**
     * All apps grouped by [AppCategory], sorted alphabetically within each group.
     * Updated whenever [_installedApps] changes.
     */
    val categorizedApps: StateFlow<Map<AppCategory, List<AppInfo>>> =
        combine(_installedApps, MutableStateFlow(Unit)) { apps, _ ->
            apps.groupBy { it.category }
                .toSortedMap(compareBy { it.ordinal })
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    // =========================================================================
    // State — filtered apps (search + category)
    // =========================================================================

    /**
     * Apps filtered by current search query and selected category.
     * When the query is empty and no category is selected, returns the full list.
     * Search uses fuzzy matching against app name and package name.
     */
    val filteredApps: StateFlow<List<AppInfo>> = combine(
        _installedApps,
        searchQuery,
        selectedCategory
    ) { apps, query, category ->
        var result = apps

        // Apply category filter
        if (category != null) {
            result = result.filter { it.category == category }
        }

        // Apply search filter
        if (query.isNotBlank()) {
            val lowerQuery = query.lowercase()
            result = result
                .map { app ->
                    val nameScore = fuzzyMatchScore(app.label.lowercase(), lowerQuery)
                    val pkgScore = fuzzyMatchScore(app.packageName.lowercase(), lowerQuery)
                    val bestScore = min(nameScore, pkgScore)
                    app to bestScore
                }
                .filter { (_, score) -> score < Int.MAX_VALUE }
                .sortedBy { (_, score) -> score }
                .map { (app, _) -> app }
        }

        result
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    /**
     * Search results with matched character indices for highlighting.
     * Only populated when a search query is active.
     */
    val searchResults: StateFlow<List<SearchResult>> = combine(
        _installedApps,
        searchQuery,
        selectedCategory
    ) { apps, query, category ->
        if (query.isBlank()) return@combine emptyList()

        var candidates = apps
        if (category != null) {
            candidates = candidates.filter { it.category == category }
        }

        val lowerQuery = query.lowercase()
        candidates.mapNotNull { app ->
            val (nameScore, nameIndices) = fuzzyMatchWithIndices(app.label.lowercase(), lowerQuery)
            val (pkgScore, _) = fuzzyMatchWithIndices(app.packageName.lowercase(), lowerQuery)

            val bestScore = min(nameScore, pkgScore)
            if (bestScore == Int.MAX_VALUE) return@mapNotNull null

            SearchResult(
                appInfo = app,
                matchedIndices = if (nameScore <= pkgScore) nameIndices else emptyList(),
                score = bestScore
            )
        }.sortedBy { it.score }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    /**
     * Category counts: how many apps are in each category.
     */
    val categoryCounts: StateFlow<Map<AppCategory, Int>> = combine(
        _installedApps,
        MutableStateFlow(Unit)
    ) { apps, _ ->
        val counts = mutableMapOf<AppCategory, Int>()
        for (cat in AppCategory.entries) {
            counts[cat] = apps.count { it.category == cat }
        }
        counts
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap()
    )

    // =========================================================================
    // Initialization
    // =========================================================================

    init {
        loadApps()
    }

    // =========================================================================
    // Public actions
    // =========================================================================

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
                        ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0

                    val usage = usageMap[pkgName]
                    val category = categorizeApp(pkgName, pm)

                    AppInfo(
                        packageName = pkgName,
                        label = label,
                        icon = icon,
                        isSystemApp = isSystem,
                        lastUsed = usage?.first ?: 0L,
                        usageCount = usage?.second ?: 0,
                        category = category
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
     * Launches the specified app via its package name. Also tracks the launch
     * in the session-local recent and frequent lists.
     */
    fun launchApp(appInfo: AppInfo) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(appInfo.packageName)
        launchIntent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(it)
                trackAppLaunch(appInfo)
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
     * Selects a category filter. Pass null to show all apps.
     */
    fun selectCategory(category: AppCategory?) {
        selectedCategory.value = category
    }

    /**
     * Returns whether the given app is currently pinned to the dock.
     */
    fun isAppPinned(packageName: String): Boolean {
        return dockManager.isPinned(packageName)
    }

    /**
     * Pins the given app to the dock. Delegates to [DockManager.pinApp].
     */
    fun pinAppToDock(packageName: String) {
        dockManager.pinApp(packageName)
    }

    /**
     * Unpins the given app from the dock. Delegates to [DockManager.unpinApp].
     */
    fun unpinAppFromDock(packageName: String) {
        dockManager.unpinApp(packageName)
    }

    // =========================================================================
    // Private — usage tracking
    // =========================================================================

    /**
     * Records an app launch in the session-local recent and frequent lists.
     */
    private fun trackAppLaunch(appInfo: AppInfo) {
        // Update session recent list (most recent first, max 8, no duplicates)
        val currentRecent = _sessionRecentApps.value.toMutableList()
        currentRecent.removeAll { it.packageName == appInfo.packageName }
        currentRecent.add(0, appInfo)
        if (currentRecent.size > 8) {
            currentRecent.removeAt(currentRecent.lastIndex)
        }
        _sessionRecentApps.value = currentRecent

        // Update launch count
        val pkg = appInfo.packageName
        _launchCounts[pkg] = (_launchCounts[pkg] ?: 0) + 1

        // Recompute top 4 frequent apps
        updateFrequentApps()
    }

    /**
     * Recomputes the top 4 most-frequently launched apps from the session counter.
     */
    private fun updateFrequentApps() {
        val allApps = _installedApps.value
        val topPackages = _launchCounts.entries
            .sortedByDescending { it.value }
            .take(4)
            .map { it.key }

        _frequentApps.value = topPackages.mapNotNull { pkg ->
            allApps.find { it.packageName == pkg }
        }
    }

    // =========================================================================
    // Private — app categorization
    // =========================================================================

    /**
     * Categorizes an app based on its package name using known prefix patterns.
     * Also checks PackageManager categories for games on API 26+.
     */
    private fun categorizeApp(packageName: String, pm: PackageManager): AppCategory {
        val lower = packageName.lowercase()

        // Check explicit prefix mappings first
        for ((category, prefixes) in categoryPrefixes) {
            if (prefixes.any { lower.startsWith(it) }) {
                return category
            }
        }

        // Check if Android classifies this as a game (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(
                        packageName,
                        PackageManager.ApplicationInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(packageName, 0)
                }
                if (appInfo.category == ApplicationInfo.CATEGORY_GAME) {
                    return AppCategory.GAMES
                }
            } catch (_: PackageManager.NameNotFoundException) {
                // Ignore — app may have been uninstalled
            }
        }

        return AppCategory.OTHER
    }

    companion object {
        /**
         * Package name prefixes mapped to [AppCategory] values.
         * Order matters: first match wins.
         */
        private val categoryPrefixes: Map<AppCategory, List<String>> = mapOf(
            AppCategory.SOCIAL to listOf(
                "com.whatsapp", "com.instagram", "com.facebook",
                "com.twitter", "com.snapchat", "com.linkedin",
                "org.telegram", "com.discord", "com.reddit",
                "com.tumblr", "com.pinterest", "com.tiktok",
                "com.viber", "com.skype", "org.thoughtcrime.securesms",
                "com.signal", "com.kakao", "jp.naver.line",
                "com.beeper", "com.bumble", "com.tinder",
                "im.vector.app", "com.x.android"
            ),
            AppCategory.WORK to listOf(
                "com.microsoft.teams", "com.slack",
                "com.google.android.apps.docs", "com.google.android.apps.sheets",
                "com.google.android.apps.slides", "us.zoom",
                "com.microsoft.office", "com.microsoft.outlook",
                "com.google.android.calendar", "com.google.android.gm",
                "com.notion", "com.todoist", "com.ticktick",
                "com.google.android.keep", "com.evernote",
                "com.google.android.apps.tasks", "md.obsidian",
                "com.google.android.apps.drive", "com.dropbox",
                "com.microsoft.onedrive", "com.google.android.apps.meet",
                "com.atlassian", "com.asana", "com.figma",
                "com.github.android", "com.trello"
            ),
            AppCategory.MEDIA to listOf(
                "com.spotify", "com.google.android.youtube",
                "com.audible", "com.netflix", "com.amazon.avod",
                "com.disney", "com.hulu", "com.hbo",
                "com.apple.android.music", "tv.twitch",
                "com.soundcloud", "com.pandora", "com.deezer",
                "com.tidal", "com.plexapp", "com.crunchyroll",
                "org.videolan", "com.mxtech",
                "com.google.android.apps.youtube.music",
                "com.amazon.mp3", "com.amazon.kindle",
                "com.google.android.apps.photos",
                "com.google.android.apps.podcasts",
                "com.pocket.casts", "com.stitcher"
            ),
            AppCategory.GAMES to listOf(
                "com.supercell", "com.king", "com.rovio",
                "com.mojang", "com.epicgames", "com.activision",
                "com.ea.game", "com.gameloft", "com.nintendo",
                "com.nianticlabs", "com.innersloth", "com.roblox",
                "com.valve.steam", "com.miHoYo", "com.squareenix",
                "com.ubisoft", "com.zynga", "com.playrix",
                "com.kabam", "com.netmarble"
            ),
            AppCategory.UTILITIES to listOf(
                "com.google.android.calculator", "com.google.android.contacts",
                "com.google.android.deskclock", "com.google.android.dialer",
                "com.google.android.apps.maps", "com.google.android.apps.walletnfcrel",
                "com.google.android.apps.translate",
                "com.google.android.apps.authenticator2", "com.authy",
                "com.android.chrome", "org.mozilla.firefox",
                "com.brave.browser", "com.opera.browser",
                "com.microsoft.emmx", "com.weather",
                "com.accuweather", "com.android.vending",
                "com.google.android.apps.files",
                "com.google.android.apps.nbu.files"
            ),
            AppCategory.SYSTEM to listOf(
                "com.android.", "com.samsung.",
                "com.google.android.gms", "com.google.android.gsf",
                "com.google.android.packageinstaller",
                "com.google.android.ext.services",
                "com.google.android.providers",
                "com.sec.", "com.lge.", "com.huawei.",
                "com.oneplus.", "com.oppo.", "com.xiaomi.",
                "com.motorola.", "com.sony.", "com.asus."
            )
        )
    }

    // =========================================================================
    // Private — fuzzy search
    // =========================================================================

    /**
     * Computes a fuzzy match score between [text] and [pattern].
     * Lower score = better match. Returns [Int.MAX_VALUE] if no match.
     *
     * Scoring:
     * - Consecutive character matches are rewarded (lower penalty)
     * - Matches at word boundaries (after space, dot, hyphen) get a bonus
     * - Exact substring match gets best score (0)
     */
    private fun fuzzyMatchScore(text: String, pattern: String): Int {
        if (pattern.isEmpty()) return 0
        if (text.contains(pattern)) return 0 // Exact substring match

        var patternIdx = 0
        var score = 0
        var lastMatchIdx = -2

        for (textIdx in text.indices) {
            if (patternIdx >= pattern.length) break

            if (text[textIdx] == pattern[patternIdx]) {
                // Consecutive match: small cost; non-consecutive: gap penalty
                if (textIdx == lastMatchIdx + 1) {
                    score += 1
                } else {
                    // Gap penalty: how far apart the matches are
                    score += (textIdx - (lastMatchIdx + 1)) * 2
                }

                // Word boundary bonus
                if (textIdx == 0 || text[textIdx - 1] in listOf(' ', '.', '-', '_')) {
                    score -= 3
                }

                lastMatchIdx = textIdx
                patternIdx++
            }
        }

        return if (patternIdx == pattern.length) {
            // All pattern characters matched
            score.coerceAtLeast(1) // Always at least 1 for fuzzy (0 reserved for exact)
        } else {
            Int.MAX_VALUE // Not all characters matched
        }
    }

    /**
     * Fuzzy match that also returns the indices of matched characters
     * (for highlighting in the UI).
     *
     * @return Pair of (score, matchedIndices). Score is [Int.MAX_VALUE] if no match.
     */
    private fun fuzzyMatchWithIndices(text: String, pattern: String): Pair<Int, List<Int>> {
        if (pattern.isEmpty()) return Pair(0, emptyList())

        // Check for exact substring match first
        val substringIdx = text.indexOf(pattern)
        if (substringIdx != -1) {
            val indices = (substringIdx until substringIdx + pattern.length).toList()
            return Pair(0, indices)
        }

        var patternIdx = 0
        var score = 0
        var lastMatchIdx = -2
        val matchedIndices = mutableListOf<Int>()

        for (textIdx in text.indices) {
            if (patternIdx >= pattern.length) break

            if (text[textIdx] == pattern[patternIdx]) {
                matchedIndices.add(textIdx)

                if (textIdx == lastMatchIdx + 1) {
                    score += 1
                } else {
                    score += (textIdx - (lastMatchIdx + 1)) * 2
                }

                if (textIdx == 0 || text[textIdx - 1] in listOf(' ', '.', '-', '_')) {
                    score -= 3
                }

                lastMatchIdx = textIdx
                patternIdx++
            }
        }

        return if (patternIdx == pattern.length) {
            Pair(score.coerceAtLeast(1), matchedIndices)
        } else {
            Pair(Int.MAX_VALUE, emptyList())
        }
    }

    // =========================================================================
    // Private — usage stats
    // =========================================================================

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
                    0
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
