package com.castor.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single note in the scratchpad.
 *
 * Notes are stored locally in the `notes` table. The [tags] field stores
 * a comma-separated list of user-defined tags (e.g. "work,todo,idea").
 * The [color] field maps to a [NoteColor] enum name (e.g. "DEFAULT", "RED")
 * used as a visual accent for quick identification in the notes list.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false,
    @ColumnInfo(defaultValue = "default")
    val color: String = "default",
    @ColumnInfo(defaultValue = "")
    val tags: String = ""
)
