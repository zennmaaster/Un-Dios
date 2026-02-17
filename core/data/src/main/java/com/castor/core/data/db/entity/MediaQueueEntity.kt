package com.castor.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_queue")
data class MediaQueueEntity(
    @PrimaryKey val id: String,
    val source: String,
    val sourceUri: String,
    val title: String,
    val artist: String?,
    val albumArtUrl: String?,
    val durationMs: Long?,
    val mediaType: String,
    val queuePosition: Int,
    val addedAt: Long = System.currentTimeMillis()
)
