package com.castor.app.search

import android.graphics.drawable.Drawable

/**
 * Category of a search result, each mapped to a Unix-style filesystem path
 * for the terminal aesthetic. These paths appear as section headers in the
 * universal search overlay.
 *
 * @param path The terminal-style path displayed as the section header
 * @param displayName Human-readable label for the category
 */
enum class SearchCategory(val path: String, val displayName: String) {
    APPS("/usr/bin/", "Apps"),
    MESSAGES("/var/mail/", "Messages"),
    REMINDERS("/etc/cron.d/", "Reminders"),
    FILES("/home/user/", "Files"),
    COMMANDS("/usr/local/bin/", "Commands")
}

/**
 * Sealed class representing a single search result from any category.
 * Each subtype carries the category-specific data needed for display and
 * click-through navigation.
 */
sealed class SearchResult {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String
    abstract val category: SearchCategory

    /**
     * Installed application matching the search query.
     * @param packageName Used to launch the app via PackageManager
     * @param icon The app's launcher drawable, nullable if unavailable
     */
    data class AppResult(
        override val id: String,
        override val title: String,
        override val subtitle: String,
        val packageName: String,
        val icon: Drawable?
    ) : SearchResult() {
        override val category = SearchCategory.APPS
    }

    /**
     * Message from the Room database matching the search query.
     * @param sender The message sender, used for navigating to the conversation
     * @param groupName Optional group name for group conversations
     * @param source The messaging platform (e.g. WHATSAPP, TEAMS)
     * @param timestamp Epoch millis for display formatting
     */
    data class MessageResult(
        override val id: String,
        override val title: String,
        override val subtitle: String,
        val sender: String,
        val groupName: String?,
        val source: String,
        val timestamp: Long
    ) : SearchResult() {
        override val category = SearchCategory.MESSAGES
    }

    /**
     * Reminder from the Room database matching the search query.
     * @param reminderId The database ID for navigation
     * @param triggerTimeMs When the reminder is scheduled to fire
     * @param isCompleted Whether the reminder has already been completed
     */
    data class ReminderResult(
        override val id: String,
        override val title: String,
        override val subtitle: String,
        val reminderId: Long,
        val triggerTimeMs: Long,
        val isCompleted: Boolean
    ) : SearchResult() {
        override val category = SearchCategory.REMINDERS
    }

    /**
     * File on the device filesystem matching the search query.
     * @param filePath Absolute path to the file
     * @param sizeBytes File size in bytes for display
     */
    data class FileResult(
        override val id: String,
        override val title: String,
        override val subtitle: String,
        val filePath: String,
        val sizeBytes: Long
    ) : SearchResult() {
        override val category = SearchCategory.FILES
    }

    /**
     * Built-in launcher command matching the search query.
     * @param command The command keyword (e.g. "lock", "settings")
     * @param description Human-readable description of what the command does
     */
    data class CommandResult(
        override val id: String,
        override val title: String,
        override val subtitle: String,
        val command: String,
        val description: String
    ) : SearchResult() {
        override val category = SearchCategory.COMMANDS
    }
}

/**
 * A categorized group of search results for display in the overlay.
 *
 * @param category The search category this section belongs to
 * @param categoryPath The terminal-style path header (e.g. "/usr/bin/")
 * @param results The list of results to display (capped to 5 for preview)
 * @param totalCount The total number of matches for "show all (N)" link
 */
data class SearchResultSection(
    val category: SearchCategory,
    val categoryPath: String,
    val results: List<SearchResult>,
    val totalCount: Int
)

/**
 * Built-in commands available in the universal search.
 * Each pair is (command keyword, description).
 */
val BUILT_IN_COMMANDS: List<Pair<String, String>> = listOf(
    "lock" to "Lock the screen",
    "settings" to "Open launcher settings",
    "media" to "Open media agent",
    "messages" to "Open messaging agent",
    "reminders" to "Open reminders agent",
    "recommendations" to "Open watch recommendations",
    "clear" to "Clear terminal history",
    "help" to "Show available commands",
    "reboot" to "Restart the launcher",
    "apps" to "Open app drawer",
    "search" to "Universal search",
    "battery" to "Show battery status",
    "wifi" to "Toggle WiFi settings",
    "bluetooth" to "Toggle Bluetooth settings"
)

/**
 * The current state of the universal search overlay.
 */
data class UniversalSearchState(
    val query: String = "",
    val sections: List<SearchResultSection> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false
)
