package com.castor.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room entity representing a single habit completion on a specific date.
 *
 * Each row records that habit [habitId] was completed on [date].
 * The [date] is stored in "yyyy-MM-dd" format (e.g., "2026-02-17").
 *
 * Foreign key cascade ensures that when a habit is deleted, all its
 * completions are automatically removed as well.
 */
@Entity(
    tableName = "habit_completions",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("habitId"), Index("date")]
)
data class HabitCompletionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val habitId: String,
    val date: String,
    val completedAt: Long = System.currentTimeMillis()
)
