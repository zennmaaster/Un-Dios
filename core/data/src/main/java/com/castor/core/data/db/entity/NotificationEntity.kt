package com.castor.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for persisted notifications in the notification center.
 *
 * Each row represents a single notification that was captured by the
 * [CastorNotificationListener] and classified by category and priority.
 * Notifications persist across app restarts, enabling history, snooze
 * tracking, and pinning even after the original system notification is removed.
 *
 * Soft-deletion: [isDismissed] marks a notification as removed from the UI
 * without physically deleting the row, allowing undo and analytics.
 */
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val appName: String,
    val packageName: String,
    val title: String,
    val content: String,
    val timestamp: Long,
    val category: String,
    val priority: String,
    val isPinned: Boolean = false,
    val isSnoozed: Boolean = false,
    val snoozeUntil: Long = 0L,
    val isRead: Boolean = false,
    val isDismissed: Boolean = false
)
