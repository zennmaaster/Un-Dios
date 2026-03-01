package com.castor.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for persistent agent memory.
 *
 * The LLM can save and recall facts across sessions using the `save_memory`
 * and `recall_memory` tools. Memories are categorized for efficient retrieval:
 * - `"agent_note"`: Observations and context the agent wants to remember
 * - `"user_profile"`: Facts about the user (preferences, habits, names)
 *
 * All data stays on-device in the encrypted Room database.
 */
@Entity(
    tableName = "memories",
    indices = [
        Index(value = ["category"]),
        Index(value = ["category", "key"], unique = true)
    ]
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String,
    @ColumnInfo(name = "key")
    val key: String,
    val value: String,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
)
