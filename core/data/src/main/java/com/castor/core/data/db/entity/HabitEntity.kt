package com.castor.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room entity representing a habit tracked by the user.
 *
 * Each habit has a target number of days per week (e.g., "Exercise 5 days/week").
 * Completions are tracked separately in [HabitCompletionEntity] with one row per day.
 *
 * The [icon] field stores a material icon name hint (e.g., "check", "fitness_center").
 * The [color] field maps to a theme color key (e.g., "accent", "success").
 */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String = "check",
    val color: String = "accent",
    val targetDaysPerWeek: Int = 7,
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)
