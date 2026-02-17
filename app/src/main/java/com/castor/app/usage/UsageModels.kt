package com.castor.app.usage

import android.graphics.drawable.Drawable

/**
 * Time period for usage stats aggregation.
 *
 * Each period determines the date range passed to UsageStatsManager:
 * - TODAY: midnight of current day to now
 * - THIS_WEEK: Monday 00:00 of current week to now
 * - THIS_MONTH: 1st of current month 00:00 to now
 */
enum class UsagePeriod(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month")
}

/**
 * High-level categories for grouping apps by their primary purpose.
 * Used in the category breakdown section of the screen time dashboard.
 */
enum class UsageCategory(val label: String, val icon: String) {
    SOCIAL("Social", "\uD83D\uDCAC"),
    ENTERTAINMENT("Entertainment", "\uD83C\uDFAC"),
    PRODUCTIVITY("Productivity", "\uD83D\uDCBC"),
    TOOLS("Tools", "\uD83D\uDD27"),
    GAMES("Games", "\uD83C\uDFAE"),
    OTHER("Other", "\uD83D\uDCE6")
}

/**
 * Summary statistics for the selected usage period.
 *
 * @param totalScreenTimeMs Total foreground time across all apps in milliseconds
 * @param pickupCount Number of device unlock/pickup events (approximated from
 *        SCREEN_INTERACTIVE events in UsageStatsManager.queryEvents)
 * @param avgSessionMs Average duration per session in milliseconds
 * @param changePercent Percentage change compared to the equivalent previous period
 *        (e.g. today vs yesterday, this week vs last week)
 * @param isIncrease True if usage increased compared to previous period
 */
data class UsageSummary(
    val totalScreenTimeMs: Long = 0L,
    val pickupCount: Int = 0,
    val avgSessionMs: Long = 0L,
    val changePercent: Int = 0,
    val isIncrease: Boolean = false
)

/**
 * Per-app usage data for the "top processes" list.
 *
 * @param packageName Android package name (e.g. "com.whatsapp")
 * @param appName Human-readable label resolved from PackageManager
 * @param usageTimeMs Total foreground time in milliseconds for the selected period
 * @param icon Launcher icon drawable, nullable if the app was uninstalled
 * @param category Resolved category based on [AppCategoryMapper]
 */
data class AppUsage(
    val packageName: String,
    val appName: String,
    val usageTimeMs: Long,
    val icon: Drawable? = null,
    val category: UsageCategory = UsageCategory.OTHER
)

/**
 * Aggregated usage for a single [UsageCategory].
 *
 * @param category The category enum value
 * @param totalTimeMs Total foreground time for all apps in this category
 * @param percentage Percentage of total screen time this category represents (0.0-1.0)
 * @param appCount Number of distinct apps contributing to this category
 */
data class CategoryUsage(
    val category: UsageCategory,
    val totalTimeMs: Long,
    val percentage: Float,
    val appCount: Int
)

/**
 * Daily usage data for the weekly bar chart visualization.
 *
 * @param dayOfWeek Display label (e.g. "Mon", "Tue")
 * @param date ISO date string (yyyy-MM-dd) for precise identification
 * @param totalTimeMs Total foreground time for that calendar day
 * @param isToday True if this entry represents the current day
 */
data class DailyUsage(
    val dayOfWeek: String,
    val date: String,
    val totalTimeMs: Long,
    val isToday: Boolean = false
)

/**
 * Maps well-known Android package names to [UsageCategory] values.
 *
 * The mapper uses prefix matching: a package starting with any known prefix
 * is assigned to that category. Unknown packages fall back to [UsageCategory.OTHER].
 *
 * This classification is performed entirely on-device with no network calls —
 * consistent with the privacy-first design of the Un-Dios launcher.
 */
object AppCategoryMapper {

    private val socialPrefixes = listOf(
        "com.whatsapp", "com.facebook", "com.instagram", "com.twitter",
        "com.snapchat", "com.linkedin", "org.telegram", "com.discord",
        "com.reddit", "com.tumblr", "com.pinterest", "com.tiktok",
        "com.viber", "com.skype", "org.thoughtcrime.securesms",
        "com.Slack", "com.microsoft.teams", "com.google.android.apps.messaging",
        "com.signal", "com.kakao", "jp.naver.line", "com.beeper"
    )

    private val entertainmentPrefixes = listOf(
        "com.spotify", "com.google.android.youtube", "com.netflix",
        "com.amazon.avod", "com.disney", "com.hulu", "com.hbo",
        "com.apple.android.music", "tv.twitch", "com.soundcloud",
        "com.pandora", "com.deezer", "com.tidal", "com.plexapp",
        "com.crunchyroll", "org.videolan", "com.mxtech",
        "com.google.android.apps.youtube.music", "com.amazon.mp3",
        "com.audible", "com.amazon.kindle"
    )

    private val productivityPrefixes = listOf(
        "com.google.android.apps.docs", "com.google.android.apps.sheets",
        "com.google.android.apps.slides", "com.microsoft.office",
        "com.google.android.calendar", "com.google.android.gm",
        "com.microsoft.outlook", "com.notion", "com.todoist",
        "com.ticktick", "com.google.android.keep", "com.evernote",
        "com.google.android.apps.tasks", "md.obsidian",
        "com.google.android.apps.drive", "com.dropbox",
        "com.microsoft.onedrive", "com.google.android.apps.meet",
        "us.zoom", "com.atlassian"
    )

    private val toolsPrefixes = listOf(
        "com.google.android.apps.maps", "com.google.android.apps.photos",
        "com.google.android.deskclock", "com.google.android.calculator",
        "com.google.android.contacts", "com.google.android.dialer",
        "com.android.settings", "com.android.vending",
        "com.google.android.apps.walletnfcrel", "com.google.android.apps.translate",
        "com.google.android.gms", "com.android.chrome",
        "org.mozilla.firefox", "com.brave.browser",
        "com.opera.browser", "com.microsoft.emmx",
        "com.google.android.apps.authenticator2", "com.authy",
        "com.weather", "com.accuweather"
    )

    private val gamesPrefixes = listOf(
        "com.supercell", "com.king", "com.rovio", "com.mojang",
        "com.epicgames", "com.activision", "com.ea.game",
        "com.gameloft", "com.nintendo", "com.nianticlabs",
        "com.innersloth", "com.roblox", "com.valve.steam"
    )

    /**
     * Returns the [UsageCategory] for the given package name by matching
     * against known prefixes. Falls back to [UsageCategory.OTHER].
     */
    fun categorize(packageName: String): UsageCategory {
        val lower = packageName.lowercase()
        return when {
            socialPrefixes.any { lower.startsWith(it.lowercase()) } -> UsageCategory.SOCIAL
            entertainmentPrefixes.any { lower.startsWith(it.lowercase()) } -> UsageCategory.ENTERTAINMENT
            productivityPrefixes.any { lower.startsWith(it.lowercase()) } -> UsageCategory.PRODUCTIVITY
            toolsPrefixes.any { lower.startsWith(it.lowercase()) } -> UsageCategory.TOOLS
            gamesPrefixes.any { lower.startsWith(it.lowercase()) } -> UsageCategory.GAMES
            else -> UsageCategory.OTHER
        }
    }
}
