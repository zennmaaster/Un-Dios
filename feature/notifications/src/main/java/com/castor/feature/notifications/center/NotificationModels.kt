package com.castor.feature.notifications.center

import androidx.compose.ui.graphics.Color
import com.castor.core.ui.theme.TerminalColors

// =====================================================================================
// NotificationEntry — UI-layer model for the notification center
// =====================================================================================

/**
 * Domain model representing a single notification in the notification center.
 *
 * This is the primary data class consumed by the Compose UI layer. It is mapped
 * from [NotificationEntity] (Room persistence) or from live [StatusBarNotification]
 * data enriched with app metadata and classification.
 */
data class NotificationEntry(
    val id: String,
    val appName: String,
    val packageName: String,
    val title: String,
    val content: String,
    val timestamp: Long,
    val category: NotificationCategory,
    val priority: NotificationPriority,
    val isPinned: Boolean = false,
    val isSnoozed: Boolean = false,
    val snoozeUntil: Long = 0L,
    val isRead: Boolean = false
)

// =====================================================================================
// NotificationCategory — auto-classification by package name
// =====================================================================================

/**
 * Broad categories that notifications are sorted into.
 *
 * Each category carries a list of known package-name prefixes so the
 * [NotificationCenterViewModel] can auto-classify incoming notifications.
 * Categories also define their display color and icon label for the UI.
 */
enum class NotificationCategory(
    val displayName: String,
    val packagePrefixes: List<String>,
    val color: Color
) {
    SOCIAL(
        displayName = "social",
        packagePrefixes = listOf(
            "com.whatsapp",
            "org.telegram",
            "org.thoughtcrime.securesms",   // Signal
            "com.instagram",
            "com.twitter",
            "com.facebook",
            "com.snapchat",
            "com.discord",
            "com.reddit"
        ),
        color = TerminalColors.Accent
    ),
    WORK(
        displayName = "work",
        packagePrefixes = listOf(
            "com.microsoft.teams",
            "com.slack",
            "com.microsoft.office.outlook",
            "com.google.android.gm",        // Gmail
            "us.zoom.videomeetings",
            "com.google.android.apps.meetings", // Google Meet
            "com.atlassian",
            "com.notion",
            "com.todoist",
            "com.asana"
        ),
        color = TerminalColors.Info
    ),
    MEDIA(
        displayName = "media",
        packagePrefixes = listOf(
            "com.spotify.music",
            "com.google.android.youtube",
            "com.netflix.mediaclient",
            "com.audible",
            "com.amazon.avod",
            "com.apple.android.music",
            "com.soundcloud",
            "com.amazon.kindle",
            "com.google.android.apps.youtube.music"
        ),
        color = TerminalColors.Success
    ),
    SYSTEM(
        displayName = "sys",
        packagePrefixes = emptyList(),
        color = TerminalColors.Timestamp
    ),
    OTHER(
        displayName = "other",
        packagePrefixes = emptyList(),
        color = TerminalColors.Subtext
    );

    companion object {
        /**
         * Resolves a [NotificationCategory] from a package name.
         *
         * Iterates through [SOCIAL], [WORK], and [MEDIA] prefix lists. If the package
         * does not match any known prefix the notification falls into [SYSTEM].
         */
        fun fromPackageName(packageName: String): NotificationCategory {
            for (category in listOf(SOCIAL, WORK, MEDIA)) {
                if (category.packagePrefixes.any { packageName.startsWith(it) }) {
                    return category
                }
            }
            return SYSTEM
        }
    }
}

// =====================================================================================
// NotificationPriority — severity levels
// =====================================================================================

/**
 * Priority levels assigned to categorized notifications.
 *
 * Priority is auto-detected based on the notification category and time of day, but
 * can also be manually overridden by the user via the long-press action sheet.
 */
enum class NotificationPriority(val displayName: String, val color: Color) {
    HIGH("high", TerminalColors.Error),
    NORMAL("normal", TerminalColors.Command),
    LOW("low", TerminalColors.Subtext)
}

// =====================================================================================
// NotificationFilter — filter chip model
// =====================================================================================

/**
 * Filter categories for the notification center.
 *
 * Each filter corresponds to a terminal-style flag displayed in the filter tab bar
 * (e.g., `--filter=social`). [ALL] shows every notification; the remaining values
 * narrow down to a specific [NotificationCategory].
 */
enum class NotificationFilter(val flag: String) {
    ALL("--filter=all"),
    SOCIAL("--filter=social"),
    WORK("--filter=work"),
    SYSTEM("--filter=system"),
    MEDIA("--filter=media")
}

// =====================================================================================
// TimeGroup — grouping notifications by relative time
// =====================================================================================

/**
 * Time-based grouping for the notification list.
 *
 * Notifications are bucketed into these groups for section headers
 * styled as journalctl date separators.
 */
enum class TimeGroup(val header: String) {
    TODAY("-- today --"),
    YESTERDAY("-- yesterday --"),
    THIS_WEEK("-- this-week --"),
    OLDER("-- older --")
}

// =====================================================================================
// ViewMode — notification center display modes
// =====================================================================================

/**
 * Display mode for the notification center.
 *
 * Controls how notifications are organized and presented:
 * - [FLAT]: Traditional chronological list with time-grouped sections (default).
 * - [GROUPED]: Notifications grouped by app with expand/collapse per group.
 * - [DIGEST]: Aggregated summary view showing per-app counts and percentages.
 */
enum class ViewMode(val label: String, val flag: String) {
    FLAT("flat", "--view=flat"),
    GROUPED("grouped", "--view=grouped"),
    DIGEST("digest", "--view=digest")
}

// =====================================================================================
// NotificationGroup — app-level notification grouping
// =====================================================================================

/**
 * Represents a group of notifications from the same app package.
 *
 * Used in the GROUPED view mode to show a collapsible header per app
 * with a count badge and the list of individual notifications within.
 */
data class NotificationGroup(
    val appName: String,
    val packageName: String,
    val notifications: List<NotificationEntry>,
    val count: Int,
    val latestTimestamp: Long
)

// =====================================================================================
// SnoozeDuration — predefined snooze intervals
// =====================================================================================

/**
 * Predefined snooze durations available via the snooze picker.
 *
 * Each entry maps to a terminal-style label and a concrete duration in milliseconds.
 * [TOMORROW] computes a dynamic offset to 9:00 AM the next day at snooze time.
 */
enum class SnoozeDuration(val label: String, val durationMs: Long) {
    FIFTEEN_MIN("15m", 15 * 60 * 1000L),
    THIRTY_MIN("30m", 30 * 60 * 1000L),
    ONE_HOUR("1h", 60 * 60 * 1000L),
    TWO_HOURS("2h", 2 * 60 * 60 * 1000L),
    TOMORROW("tomorrow", 0L) // Computed dynamically at snooze time
}
