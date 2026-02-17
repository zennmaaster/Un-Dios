package com.castor.app.search

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.app.launcher.AppInfo
import com.castor.core.data.repository.MessageRepository
import com.castor.core.data.repository.ReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for the Universal Search Overlay, providing GNOME Spotlight-style
 * search across apps, messages, reminders, files, and built-in commands.
 *
 * All search operations run in parallel on [Dispatchers.IO] and results are
 * debounced by 300ms to avoid excessive queries while the user is still typing.
 *
 * The search pipeline:
 * 1. User types a query -> [searchQuery] MutableStateFlow is updated
 * 2. After 300ms debounce, the query triggers parallel searches across all categories
 * 3. Results are merged into categorized [SearchResultSection]s
 * 4. Each section is capped at 5 visible results with a totalCount for "show all"
 * 5. The UI observes [searchState] and renders the results
 */
@HiltViewModel
class UniversalSearchViewModel @Inject constructor(
    private val application: Application,
    private val messageRepository: MessageRepository,
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    /** The current search query text, directly bound to the search field. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** The overall UI state containing query, results, and loading flags. */
    private val _searchState = MutableStateFlow(UniversalSearchState())
    val searchState: StateFlow<UniversalSearchState> = _searchState.asStateFlow()

    /** Cached list of installed apps to avoid repeated PackageManager queries. */
    private var cachedApps: List<AppInfo>? = null

    /** Maximum results shown per category in the preview. */
    private val maxResultsPerCategory = 5

    /** Date formatter for message and reminder timestamps. */
    private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    init {
        observeSearchQuery()
    }

    // =====================================================================================
    // Public API
    // =====================================================================================

    /**
     * Updates the search query. Called from the UI whenever the text field changes.
     * The actual search is debounced internally.
     */
    fun onQueryChanged(query: String) {
        _searchQuery.value = query
    }

    /**
     * Clears the search query and resets the state to initial.
     */
    fun clearSearch() {
        _searchQuery.value = ""
        _searchState.value = UniversalSearchState()
    }

    /**
     * Launches an app by its package name.
     */
    fun launchApp(packageName: String) {
        val launchIntent = application.packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                application.startActivity(it)
            } catch (_: Exception) {
                // App could not be launched
            }
        }
    }

    /**
     * Executes a built-in command. Returns the navigation route if applicable,
     * or null if the command was handled internally (e.g. "lock").
     */
    fun executeCommand(command: String): String? {
        return when (command) {
            "settings" -> "settings"
            "media" -> "media"
            "messages" -> "messages"
            "reminders" -> "reminders"
            "recommendations" -> "recommendations"
            "apps" -> null // Handled by opening app drawer
            "lock" -> {
                // Lock screen intent — requires Device Admin permission
                null
            }
            "wifi" -> {
                try {
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    application.startActivity(intent)
                } catch (_: Exception) { }
                null
            }
            "bluetooth" -> {
                try {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    application.startActivity(intent)
                } catch (_: Exception) { }
                null
            }
            "battery" -> {
                try {
                    val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    application.startActivity(intent)
                } catch (_: Exception) { }
                null
            }
            else -> null
        }
    }

    /**
     * Opens a file using the system's default handler.
     */
    fun openFile(filePath: String) {
        try {
            val file = File(filePath)
            val uri = Uri.fromFile(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            application.startActivity(intent)
        } catch (_: Exception) {
            // File could not be opened
        }
    }

    // =====================================================================================
    // Search pipeline
    // =====================================================================================

    /**
     * Sets up the debounced search pipeline. Observes [_searchQuery] with a 300ms
     * debounce, then triggers parallel searches across all categories.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSearchQuery() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300L)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isBlank()) {
                        _searchState.value = UniversalSearchState()
                        return@collectLatest
                    }

                    _searchState.value = _searchState.value.copy(
                        query = query,
                        isSearching = true
                    )

                    val sections = performSearch(query)

                    _searchState.value = UniversalSearchState(
                        query = query,
                        sections = sections,
                        isSearching = false,
                        hasSearched = true
                    )
                }
        }
    }

    /**
     * Runs all five search categories in parallel on [Dispatchers.IO] and
     * returns the merged, non-empty sections.
     */
    private suspend fun performSearch(query: String): List<SearchResultSection> {
        return withContext(Dispatchers.IO) {
            val deferredApps = async { searchApps(query) }
            val deferredMessages = async { searchMessages(query) }
            val deferredReminders = async { searchReminders(query) }
            val deferredFiles = async { searchFiles(query) }
            val deferredCommands = async { searchCommands(query) }

            val results = awaitAll(
                deferredApps,
                deferredMessages,
                deferredReminders,
                deferredFiles,
                deferredCommands
            )

            // Filter out empty sections and return
            results.filterNotNull().filter { it.totalCount > 0 }
        }
    }

    // =====================================================================================
    // Individual search implementations
    // =====================================================================================

    /**
     * Searches installed apps by label and package name (case-insensitive).
     * Queries [PackageManager] for all launchable activities. Results are cached
     * after the first query to avoid repeated system calls.
     */
    private fun searchApps(query: String): SearchResultSection? {
        val apps = getInstalledApps()
        val lowerQuery = query.lowercase()

        val matchingApps = apps.filter { app ->
            app.label.lowercase().contains(lowerQuery) ||
                app.packageName.lowercase().contains(lowerQuery)
        }

        if (matchingApps.isEmpty()) return null

        val results = matchingApps.take(maxResultsPerCategory).map { app ->
            SearchResult.AppResult(
                id = "app_${app.packageName}",
                title = app.label,
                subtitle = app.packageName,
                packageName = app.packageName,
                icon = app.icon
            )
        }

        return SearchResultSection(
            category = SearchCategory.APPS,
            categoryPath = SearchCategory.APPS.path,
            results = results,
            totalCount = matchingApps.size
        )
    }

    /**
     * Searches messages by content and sender using the Room DAO's LIKE query.
     * Collects the first emission from the search Flow (snapshot query).
     */
    private suspend fun searchMessages(query: String): SearchResultSection? {
        return try {
            val messages = messageRepository.searchMessages(query, limit = 20).first()

            if (messages.isEmpty()) return null

            val results = messages.take(maxResultsPerCategory).map { msg ->
                val senderDisplay = if (msg.groupName != null) {
                    "${msg.sender} in ${msg.groupName}"
                } else {
                    msg.sender
                }

                SearchResult.MessageResult(
                    id = "msg_${msg.id}",
                    title = senderDisplay,
                    subtitle = msg.content.take(80),
                    sender = msg.sender,
                    groupName = msg.groupName,
                    source = msg.source.name,
                    timestamp = msg.timestamp
                )
            }

            SearchResultSection(
                category = SearchCategory.MESSAGES,
                categoryPath = SearchCategory.MESSAGES.path,
                results = results,
                totalCount = messages.size
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Searches reminders by description using the Room DAO's LIKE query.
     * Collects the first emission from the search Flow (snapshot query).
     */
    private suspend fun searchReminders(query: String): SearchResultSection? {
        return try {
            val reminders = reminderRepository.searchReminders(query, limit = 20).first()

            if (reminders.isEmpty()) return null

            val results = reminders.take(maxResultsPerCategory).map { reminder ->
                val timeStr = dateFormat.format(Date(reminder.triggerTimeMs))
                val statusPrefix = if (reminder.isCompleted) "[done] " else ""

                SearchResult.ReminderResult(
                    id = "rem_${reminder.id}",
                    title = "${statusPrefix}${reminder.description}",
                    subtitle = "Scheduled: $timeStr",
                    reminderId = reminder.id,
                    triggerTimeMs = reminder.triggerTimeMs,
                    isCompleted = reminder.isCompleted
                )
            }

            SearchResultSection(
                category = SearchCategory.REMINDERS,
                categoryPath = SearchCategory.REMINDERS.path,
                results = results,
                totalCount = reminders.size
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Searches common user-accessible directories (Downloads, Documents, DCIM,
     * Pictures, Music) for files whose names contain the query string.
     * Only searches one level deep to keep it fast.
     */
    private fun searchFiles(query: String): SearchResultSection? {
        val lowerQuery = query.lowercase()
        val matchingFiles = mutableListOf<File>()

        val searchDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        )

        for (dir in searchDirs) {
            if (!dir.exists() || !dir.canRead()) continue
            try {
                dir.listFiles()?.forEach { file ->
                    if (file.name.lowercase().contains(lowerQuery)) {
                        matchingFiles.add(file)
                    }
                    // Search one level of subdirectories
                    if (file.isDirectory) {
                        file.listFiles()?.forEach { subFile ->
                            if (subFile.isFile && subFile.name.lowercase().contains(lowerQuery)) {
                                matchingFiles.add(subFile)
                            }
                        }
                    }
                }
            } catch (_: SecurityException) {
                // Permission not granted for this directory
            }

            // Cap total file results to avoid excessive scanning
            if (matchingFiles.size >= 50) break
        }

        if (matchingFiles.isEmpty()) return null

        // Sort by last modified descending
        val sorted = matchingFiles.sortedByDescending { it.lastModified() }

        val results = sorted.take(maxResultsPerCategory).map { file ->
            val parentDir = file.parentFile?.name ?: ""
            val sizeStr = formatFileSize(file.length())

            SearchResult.FileResult(
                id = "file_${file.absolutePath.hashCode()}",
                title = file.name,
                subtitle = "$parentDir/ -- $sizeStr",
                filePath = file.absolutePath,
                sizeBytes = file.length()
            )
        }

        return SearchResultSection(
            category = SearchCategory.FILES,
            categoryPath = SearchCategory.FILES.path,
            results = results,
            totalCount = sorted.size
        )
    }

    /**
     * Searches built-in launcher commands by keyword and description.
     * Matches are case-insensitive against both the command name and its description.
     */
    private fun searchCommands(query: String): SearchResultSection? {
        val lowerQuery = query.lowercase()

        val matching = BUILT_IN_COMMANDS.filter { (cmd, desc) ->
            cmd.lowercase().contains(lowerQuery) ||
                desc.lowercase().contains(lowerQuery)
        }

        if (matching.isEmpty()) return null

        val results = matching.take(maxResultsPerCategory).map { (cmd, desc) ->
            SearchResult.CommandResult(
                id = "cmd_$cmd",
                title = "$ $cmd",
                subtitle = desc,
                command = cmd,
                description = desc
            )
        }

        return SearchResultSection(
            category = SearchCategory.COMMANDS,
            categoryPath = SearchCategory.COMMANDS.path,
            results = results,
            totalCount = matching.size
        )
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    /**
     * Queries PackageManager for all launchable apps. Caches the result after
     * the first call to avoid repeated expensive system queries.
     */
    private fun getInstalledApps(): List<AppInfo> {
        cachedApps?.let { return it }

        val pm = application.packageManager
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

        val apps = resolveInfos.mapNotNull { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
            val pkgName = activityInfo.packageName

            // Skip our own package
            if (pkgName == application.packageName) return@mapNotNull null

            val label = resolveInfo.loadLabel(pm)?.toString() ?: pkgName
            val icon = try {
                resolveInfo.loadIcon(pm)
            } catch (_: Exception) {
                null
            }

            val isSystem = (activityInfo.applicationInfo?.flags
                ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0

            AppInfo(
                packageName = pkgName,
                label = label,
                icon = icon,
                isSystemApp = isSystem
            )
        }.sortedBy { it.label.lowercase() }

        cachedApps = apps
        return apps
    }

    /**
     * Formats a file size in bytes to a human-readable string (B, KB, MB, GB).
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))}MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))}GB"
        }
    }

    /**
     * Returns a basic MIME type for a file based on its extension.
     */
    private fun getMimeType(file: File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "txt" -> "text/plain"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "*/*"
        }
    }
}
