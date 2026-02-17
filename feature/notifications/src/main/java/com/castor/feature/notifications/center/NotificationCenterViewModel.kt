package com.castor.feature.notifications.center

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.core.data.db.dao.NotificationDao
import com.castor.core.data.db.entity.NotificationEntity
import com.castor.feature.notifications.CastorNotificationListener
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

// -------------------------------------------------------------------------------------
// Cached app metadata (icon + name) keyed by package name
// -------------------------------------------------------------------------------------

/**
 * Holds resolved metadata for an installed app, cached to avoid
 * repeated PackageManager lookups on every recomposition.
 */
data class AppMetadata(
    val appName: String,
    val appIcon: Drawable?
)

// -------------------------------------------------------------------------------------
// ViewModel
// -------------------------------------------------------------------------------------

/**
 * ViewModel for the Notification Center screen.
 *
 * Reads live notifications from [CastorNotificationListener], persists them via
 * [NotificationDao] (Room), and exposes a filtered, sorted, time-grouped list
 * for the UI layer.
 *
 * ## Data flow
 * 1. Periodic polling reads active [StatusBarNotification]s from the listener
 * 2. Each notification is classified by category and priority, then upserted into Room
 * 3. Room emits a reactive [Flow] of [NotificationEntity] rows
 * 4. Entities are mapped to [NotificationEntry] domain models with UI-ready fields
 * 5. Filters, snooze state, and muted apps are applied via [combine]
 * 6. The final list is grouped by [TimeGroup] for section headers
 *
 * ## Auto-categorization
 * Package names are matched against known prefix lists defined in [NotificationCategory].
 *
 * ## Auto-priority
 * - Work notifications during work hours (9 AM - 6 PM, Mon-Fri): [NotificationPriority.HIGH]
 * - Social notifications: [NotificationPriority.NORMAL]
 * - System / media / other: [NotificationPriority.LOW]
 *
 * ## Pinning
 * Pinned notifications are persisted in Room and always appear at the top of
 * the list, above the time-grouped sections.
 *
 * ## Snoozing
 * Snoozed notifications are hidden until their snooze-until timestamp elapses.
 * The snooze state is persisted in Room so it survives process death.
 *
 * ## Muting
 * Muted app packages are held in memory and filter out all notifications from
 * that package entirely.
 */
@HiltViewModel
class NotificationCenterViewModel @Inject constructor(
    private val application: Application,
    private val notificationDao: NotificationDao
) : ViewModel() {

    companion object {
        private const val TAG = "NotifCenterVM"

        /** Polling interval for refreshing the notification list from the listener. */
        private const val REFRESH_INTERVAL_MS = 2_000L

        /** Work-hours window used for priority boosting work notifications. */
        private const val WORK_HOUR_START = 9
        private const val WORK_HOUR_END = 18
    }

    // -------------------------------------------------------------------------------------
    // App metadata cache
    // -------------------------------------------------------------------------------------

    private val appMetadataCache = mutableMapOf<String, AppMetadata>()

    // -------------------------------------------------------------------------------------
    // Internal mutable state
    // -------------------------------------------------------------------------------------

    private val _selectedFilter = MutableStateFlow(NotificationFilter.ALL)
    val selectedFilter: StateFlow<NotificationFilter> = _selectedFilter.asStateFlow()

    private val _mutedApps = MutableStateFlow<Set<String>>(emptySet())
    val mutedApps: StateFlow<Set<String>> = _mutedApps.asStateFlow()

    private val _priorityOverrides = MutableStateFlow<Map<String, NotificationPriority>>(emptyMap())

    private val _selectedKeys = MutableStateFlow<Set<String>>(emptySet())
    val selectedKeys: StateFlow<Set<String>> = _selectedKeys.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _expandedNotificationId = MutableStateFlow<String?>(null)
    val expandedNotificationId: StateFlow<String?> = _expandedNotificationId.asStateFlow()

    // -------------------------------------------------------------------------------------
    // Public derived state — from Room DB
    // -------------------------------------------------------------------------------------

    /** All non-dismissed notifications from Room, mapped to domain models. */
    private val allNotifications: StateFlow<List<NotificationEntry>> = notificationDao.getAll()
        .map { entities -> entities.map { it.toDomainModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Total count of all active notifications. */
    val notificationCount: StateFlow<Int> = allNotifications
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Unread count from Room. */
    val unreadCount: StateFlow<Int> = notificationDao.getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Filtered and sorted notification list.
     *
     * Applies: category filter, muted apps, snooze window, priority overrides.
     * Sorted: pinned first, then by timestamp descending.
     */
    val filteredNotifications: StateFlow<List<NotificationEntry>> = combine(
        allNotifications,
        _selectedFilter,
        _mutedApps,
        _priorityOverrides
    ) { entries, filter, muted, overrides ->
        val now = System.currentTimeMillis()

        entries
            // Exclude muted app packages
            .filter { it.packageName !in muted }
            // Exclude currently snoozed
            .filter { entry ->
                !entry.isSnoozed || now >= entry.snoozeUntil
            }
            // Apply category filter
            .filter { entry ->
                when (filter) {
                    NotificationFilter.ALL -> true
                    NotificationFilter.SOCIAL -> entry.category == NotificationCategory.SOCIAL
                    NotificationFilter.WORK -> entry.category == NotificationCategory.WORK
                    NotificationFilter.SYSTEM -> entry.category == NotificationCategory.SYSTEM || entry.category == NotificationCategory.OTHER
                    NotificationFilter.MEDIA -> entry.category == NotificationCategory.MEDIA
                }
            }
            // Apply priority overrides
            .map { entry ->
                val override = overrides[entry.id]
                if (override != null) entry.copy(priority = override) else entry
            }
            // Sort: pinned first, then high priority, then by timestamp descending
            .sortedWith(
                compareByDescending<NotificationEntry> { it.isPinned }
                    .thenBy {
                        when (it.priority) {
                            NotificationPriority.HIGH -> 0
                            NotificationPriority.NORMAL -> 1
                            NotificationPriority.LOW -> 2
                        }
                    }
                    .thenByDescending { it.timestamp }
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Pinned notifications extracted from the filtered list for the dedicated
     * "pinned" section at the top of the LazyColumn.
     */
    val pinnedNotifications: StateFlow<List<NotificationEntry>> = filteredNotifications
        .map { entries -> entries.filter { it.isPinned } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Non-pinned notifications grouped by [TimeGroup] for section headers.
     */
    val groupedNotifications: StateFlow<Map<TimeGroup, List<NotificationEntry>>> = filteredNotifications
        .map { entries ->
            entries
                .filter { !it.isPinned }
                .groupBy { classifyTimeGroup(it.timestamp) }
                .toSortedMap(compareBy { it.ordinal })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // -------------------------------------------------------------------------------------
    // Init — start polling + periodic un-snooze
    // -------------------------------------------------------------------------------------

    init {
        syncNotificationsFromListener()
        // Periodic refresh loop
        viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                syncNotificationsFromListener()
                // Un-snooze expired notifications
                notificationDao.unsnoozeExpired(System.currentTimeMillis())
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Public actions
    // -------------------------------------------------------------------------------------

    /** Change the active notification filter. */
    fun setFilter(filter: NotificationFilter) {
        _selectedFilter.value = filter
    }

    /**
     * Dismiss (cancel) a notification by its id.
     *
     * Soft-deletes in Room and cancels from the system notification shade.
     */
    fun dismissNotification(id: String) {
        viewModelScope.launch {
            try {
                CastorNotificationListener.instance?.cancelNotification(id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel system notification: $id", e)
            }
            notificationDao.markDismissed(id)
            _selectedKeys.update { it - id }
            Log.d(TAG, "Dismissed notification: $id")
        }
    }

    /**
     * Snooze a notification using a predefined [SnoozeDuration].
     */
    fun snoozeNotification(id: String, duration: SnoozeDuration) {
        val ms = if (duration == SnoozeDuration.TOMORROW) {
            computeTomorrowMorningMs()
        } else {
            duration.durationMs
        }
        snoozeNotification(id, ms)
    }

    /**
     * Snooze a notification for the given [durationMs] from now.
     */
    fun snoozeNotification(id: String, durationMs: Long) {
        val snoozeUntil = System.currentTimeMillis() + durationMs
        viewModelScope.launch {
            notificationDao.updateSnoozed(id, isSnoozed = true, snoozeUntil = snoozeUntil)
            Log.d(TAG, "Snoozed notification $id until $snoozeUntil")
        }
    }

    /** Toggle the pinned state of a notification. */
    fun pinNotification(id: String) {
        viewModelScope.launch {
            val current = allNotifications.value.find { it.id == id }
            val newPinned = !(current?.isPinned ?: false)
            notificationDao.updatePinned(id, newPinned)
            Log.d(TAG, "Pin toggled for $id: $newPinned")
        }
    }

    /** Mute all notifications from a given package. */
    fun muteApp(packageName: String) {
        _mutedApps.update { it + packageName }
        Log.d(TAG, "Muted app: $packageName")
    }

    /** Unmute a previously muted app. */
    fun unmuteApp(packageName: String) {
        _mutedApps.update { it - packageName }
        Log.d(TAG, "Unmuted app: $packageName")
    }

    /** Mark a notification as read. */
    fun markAsRead(id: String) {
        viewModelScope.launch {
            notificationDao.markRead(id)
        }
    }

    /** Toggle the expanded state of a notification for full content view. */
    fun toggleExpanded(id: String) {
        _expandedNotificationId.update { current ->
            if (current == id) null else id
        }
    }

    /** Override the priority of a specific notification. */
    fun setPriority(id: String, priority: NotificationPriority) {
        _priorityOverrides.update { it + (id to priority) }
        Log.d(TAG, "Priority override: $id -> $priority")
    }

    /** Dismiss all visible (non-snoozed, non-muted) notifications. */
    fun clearAll() {
        viewModelScope.launch {
            try {
                CastorNotificationListener.instance?.cancelAllNotifications()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear system notifications", e)
            }
            notificationDao.clearAll()
            _selectedKeys.value = emptySet()
            _isSelectionMode.value = false
            Log.d(TAG, "Cleared all notifications")
        }
    }

    // -------------------------------------------------------------------------------------
    // Selection / bulk actions
    // -------------------------------------------------------------------------------------

    /** Toggle selection state for a notification key. */
    fun toggleSelection(key: String) {
        _selectedKeys.update { current ->
            if (key in current) current - key else current + key
        }
        _isSelectionMode.value = _selectedKeys.value.isNotEmpty()
    }

    /** Enter selection mode by long-pressing a notification. */
    fun startSelection(key: String) {
        _isSelectionMode.value = true
        _selectedKeys.update { it + key }
    }

    /** Exit selection mode and clear all selections. */
    fun clearSelection() {
        _selectedKeys.value = emptySet()
        _isSelectionMode.value = false
    }

    /** Select all currently visible (filtered) notifications. */
    fun selectAll() {
        _selectedKeys.value = filteredNotifications.value.map { it.id }.toSet()
        _isSelectionMode.value = _selectedKeys.value.isNotEmpty()
    }

    /** Dismiss all selected notifications. */
    fun dismissSelected() {
        val keys = _selectedKeys.value.toList()
        keys.forEach { dismissNotification(it) }
        clearSelection()
    }

    /** Mark all selected notifications as read. */
    fun markSelectedAsRead() {
        viewModelScope.launch {
            _selectedKeys.value.forEach { notificationDao.markRead(it) }
        }
        clearSelection()
    }

    /** Snooze all selected notifications with the given duration. */
    fun snoozeSelected(duration: SnoozeDuration) {
        val keys = _selectedKeys.value.toList()
        keys.forEach { snoozeNotification(it, duration) }
        clearSelection()
    }

    // -------------------------------------------------------------------------------------
    // App metadata
    // -------------------------------------------------------------------------------------

    /**
     * Returns cached [AppMetadata] for the given package name.
     * Resolves from PackageManager on first access and caches the result.
     */
    fun getAppMetadata(packageName: String): AppMetadata {
        return appMetadataCache.getOrPut(packageName) {
            AppMetadata(
                appName = resolveAppName(packageName),
                appIcon = resolveAppIcon(packageName)
            )
        }
    }

    // -------------------------------------------------------------------------------------
    // Sync from NotificationListener → Room
    // -------------------------------------------------------------------------------------

    /**
     * Reads active notifications from the [CastorNotificationListener] singleton,
     * classifies each one, and upserts into the Room database.
     */
    private fun syncNotificationsFromListener() {
        val listener = CastorNotificationListener.instance
        if (listener == null) {
            Log.w(TAG, "NotificationListener not connected")
            return
        }

        viewModelScope.launch {
            try {
                val active: Array<StatusBarNotification> = listener.activeNotifications ?: emptyArray()
                val entities = active.mapNotNull { sbn -> toEntity(sbn) }
                if (entities.isNotEmpty()) {
                    notificationDao.insertAll(entities)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync notifications from listener", e)
            }
        }
    }

    /**
     * Converts a raw [StatusBarNotification] into a [NotificationEntity] for Room persistence.
     */
    private fun toEntity(sbn: StatusBarNotification): NotificationEntity? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras

        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(android.app.Notification.EXTRA_TITLE_BIG)?.toString()
            ?: ""

        val content = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(android.app.Notification.EXTRA_SUMMARY_TEXT)?.toString()
            ?: ""

        // Skip notifications with no visible content
        if (title.isBlank() && content.isBlank()) return null

        val packageName = sbn.packageName
        val category = NotificationCategory.fromPackageName(packageName)
        val priority = detectPriority(category)

        return NotificationEntity(
            id = sbn.key,
            appName = resolveAppName(packageName),
            packageName = packageName,
            title = title,
            content = content,
            timestamp = sbn.postTime,
            category = category.name,
            priority = priority.name
        )
    }

    // -------------------------------------------------------------------------------------
    // Mapping: Entity → Domain
    // -------------------------------------------------------------------------------------

    private fun NotificationEntity.toDomainModel(): NotificationEntry {
        return NotificationEntry(
            id = id,
            appName = appName,
            packageName = packageName,
            title = title,
            content = content,
            timestamp = timestamp,
            category = try {
                NotificationCategory.valueOf(category)
            } catch (e: IllegalArgumentException) {
                NotificationCategory.OTHER
            },
            priority = try {
                NotificationPriority.valueOf(priority)
            } catch (e: IllegalArgumentException) {
                NotificationPriority.NORMAL
            },
            isPinned = isPinned,
            isSnoozed = isSnoozed,
            snoozeUntil = snoozeUntil,
            isRead = isRead
        )
    }

    // -------------------------------------------------------------------------------------
    // Classification helpers
    // -------------------------------------------------------------------------------------

    /**
     * Determines default priority based on category and current time of day.
     */
    private fun detectPriority(category: NotificationCategory): NotificationPriority {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val isWorkHours = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
                && hour in WORK_HOUR_START until WORK_HOUR_END

        return when (category) {
            NotificationCategory.WORK -> {
                if (isWorkHours) NotificationPriority.HIGH else NotificationPriority.NORMAL
            }
            NotificationCategory.SOCIAL -> NotificationPriority.NORMAL
            NotificationCategory.MEDIA -> NotificationPriority.LOW
            NotificationCategory.SYSTEM -> NotificationPriority.LOW
            NotificationCategory.OTHER -> NotificationPriority.LOW
        }
    }

    /**
     * Classifies a timestamp into a [TimeGroup] relative to today.
     */
    private fun classifyTimeGroup(timestamp: Long): TimeGroup {
        val now = Calendar.getInstance()
        val notifCal = Calendar.getInstance().apply { timeInMillis = timestamp }

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val yesterdayStart = (todayStart.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }

        val weekStart = (todayStart.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -7)
        }

        return when {
            timestamp >= todayStart.timeInMillis -> TimeGroup.TODAY
            timestamp >= yesterdayStart.timeInMillis -> TimeGroup.YESTERDAY
            timestamp >= weekStart.timeInMillis -> TimeGroup.THIS_WEEK
            else -> TimeGroup.OLDER
        }
    }

    // -------------------------------------------------------------------------------------
    // PackageManager helpers
    // -------------------------------------------------------------------------------------

    private fun resolveAppName(packageName: String): String {
        return try {
            val pm = application.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.')
                .replaceFirstChar { it.uppercase() }
        }
    }

    private fun resolveAppIcon(packageName: String): Drawable? {
        return try {
            application.packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    // -------------------------------------------------------------------------------------
    // Time helpers
    // -------------------------------------------------------------------------------------

    private fun computeTomorrowMorningMs(): Long {
        val now = Calendar.getInstance()
        val tomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return tomorrow.timeInMillis - now.timeInMillis
    }
}
