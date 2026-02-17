package com.castor.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.castor.core.data.db.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for the [NotificationEntity] table.
 *
 * Provides reactive queries via [Flow] for the notification center UI
 * and suspend functions for mutation operations (insert, update, dismiss).
 *
 * All queries exclude soft-deleted (isDismissed) rows unless explicitly requested.
 */
@Dao
interface NotificationDao {

    /** Returns all non-dismissed notifications ordered by pinned-first then newest-first. */
    @Query(
        """
        SELECT * FROM notifications
        WHERE isDismissed = 0
        ORDER BY isPinned DESC, timestamp DESC
        """
    )
    fun getAll(): Flow<List<NotificationEntity>>

    /** Returns non-dismissed notifications matching the given [category]. */
    @Query(
        """
        SELECT * FROM notifications
        WHERE isDismissed = 0 AND category = :category
        ORDER BY isPinned DESC, timestamp DESC
        """
    )
    fun getByCategory(category: String): Flow<List<NotificationEntity>>

    /** Returns only pinned, non-dismissed notifications. */
    @Query(
        """
        SELECT * FROM notifications
        WHERE isDismissed = 0 AND isPinned = 1
        ORDER BY timestamp DESC
        """
    )
    fun getPinned(): Flow<List<NotificationEntity>>

    /** Inserts a notification, replacing on conflict (same id = same system notification key). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity)

    /** Bulk insert for initial population from active notifications. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notifications: List<NotificationEntity>)

    /** Toggle pin status for a notification. */
    @Query("UPDATE notifications SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePinned(id: String, isPinned: Boolean)

    /** Set snooze state and the snooze-until timestamp. */
    @Query("UPDATE notifications SET isSnoozed = :isSnoozed, snoozeUntil = :snoozeUntil WHERE id = :id")
    suspend fun updateSnoozed(id: String, isSnoozed: Boolean, snoozeUntil: Long)

    /** Mark a notification as read. */
    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    /** Soft-delete: mark a notification as dismissed. */
    @Query("UPDATE notifications SET isDismissed = 1 WHERE id = :id")
    suspend fun markDismissed(id: String)

    /** Hard-delete notifications older than [olderThan] timestamp. */
    @Query("DELETE FROM notifications WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)

    /** Soft-delete all non-dismissed notifications. */
    @Query("UPDATE notifications SET isDismissed = 1 WHERE isDismissed = 0")
    suspend fun clearAll()

    /** Returns the count of unread, non-dismissed notifications. */
    @Query("SELECT COUNT(*) FROM notifications WHERE isDismissed = 0 AND isRead = 0")
    fun getUnreadCount(): Flow<Int>

    /** Returns the most recent unread, non-dismissed notifications up to [limit]. */
    @Query(
        """
        SELECT * FROM notifications
        WHERE isDismissed = 0 AND isRead = 0
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    fun getRecentUnread(limit: Int = 5): Flow<List<NotificationEntity>>

    /** Un-snooze all notifications whose snooze window has elapsed. */
    @Query("UPDATE notifications SET isSnoozed = 0, snoozeUntil = 0 WHERE isSnoozed = 1 AND snoozeUntil <= :now")
    suspend fun unsnoozeExpired(now: Long)

    /** Search notifications by title or content. */
    @Query(
        """
        SELECT * FROM notifications
        WHERE isDismissed = 0
          AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    fun search(query: String, limit: Int = 50): Flow<List<NotificationEntity>>

    // -------------------------------------------------------------------------------------
    // Grouping & Digest queries
    // -------------------------------------------------------------------------------------

    /**
     * Returns the latest notification per app package with a count of notifications
     * in each group. Groups by packageName for app-level grouping.
     *
     * Each row is the most recent notification for that package, with a virtual
     * groupCount column (not in entity -- use raw query via DAO wrapper).
     */
    @Query(
        """
        SELECT * FROM notifications
        WHERE isDismissed = 0
          AND id IN (
              SELECT id FROM notifications AS n2
              WHERE n2.isDismissed = 0
              GROUP BY n2.packageName
              HAVING n2.timestamp = MAX(n2.timestamp)
          )
        ORDER BY timestamp DESC
        """
    )
    fun getGroupedNotifications(): Flow<List<NotificationEntity>>

    /**
     * Returns the count of non-dismissed notifications per package name.
     */
    @Query(
        """
        SELECT packageName, COUNT(*) AS count
        FROM notifications
        WHERE isDismissed = 0
        GROUP BY packageName
        ORDER BY count DESC
        """
    )
    fun getGroupCountsByPackage(): Flow<List<PackageNotificationCount>>

    /** Returns all non-dismissed notifications matching the given [groupKey]. */
    @Query(
        """
        SELECT * FROM notifications
        WHERE isDismissed = 0 AND groupKey = :groupKey
        ORDER BY timestamp DESC
        """
    )
    fun getNotificationsByGroup(groupKey: String): Flow<List<NotificationEntity>>

    /** Returns all non-dismissed notifications for a given [packageName]. */
    @Query(
        """
        SELECT * FROM notifications
        WHERE isDismissed = 0 AND packageName = :packageName
        ORDER BY timestamp DESC
        """
    )
    fun getNotificationsByPackage(packageName: String): Flow<List<NotificationEntity>>

    /** Returns non-dismissed notifications that have a direct reply action. */
    @Query(
        """
        SELECT * FROM notifications
        WHERE isDismissed = 0 AND hasReplyAction = 1
        ORDER BY timestamp DESC
        """
    )
    fun getReplyableNotifications(): Flow<List<NotificationEntity>>

    /**
     * Returns a per-app summary digest for the last 24 hours.
     * Each row contains the package name, app name, count of notifications,
     * and the latest content and timestamp.
     */
    @Query(
        """
        SELECT
            packageName,
            appName,
            COUNT(*) AS count,
            MAX(content) AS latestMessage,
            MAX(timestamp) AS latestTimestamp
        FROM notifications
        WHERE isDismissed = 0
          AND timestamp >= :since
        GROUP BY packageName
        ORDER BY count DESC
        """
    )
    fun getNotificationDigest(since: Long): Flow<List<NotificationDigestRow>>

    /** Soft-delete all read (but non-dismissed) notifications. */
    @Query("UPDATE notifications SET isDismissed = 1 WHERE isDismissed = 0 AND isRead = 1")
    suspend fun clearAllRead()
}

/**
 * Projection for per-package notification counts used in grouped view.
 */
data class PackageNotificationCount(
    val packageName: String,
    val count: Int
)

/**
 * Projection for the notification digest query.
 * Maps to columns returned by [NotificationDao.getNotificationDigest].
 */
data class NotificationDigestRow(
    val packageName: String,
    val appName: String,
    val count: Int,
    val latestMessage: String?,
    val latestTimestamp: Long
)
