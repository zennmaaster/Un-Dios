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
}
