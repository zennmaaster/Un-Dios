package com.castor.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks a single media watch event — a movie, series episode, documentary,
 * or video the user consumed on any monitored streaming platform.
 *
 * All data stays on-device inside the SQLCipher-encrypted Room database.
 */
@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The streaming source (NETFLIX, PRIME_VIDEO, YOUTUBE, CHROME_BROWSER). */
    val source: String,
    /** Extracted title of the content. */
    val title: String,
    /** Genre or category (drama, comedy, documentary, etc.). May be null initially. */
    val genre: String? = null,
    /** Content type discriminator: movie, series, documentary, video. */
    val contentType: String = "video",
    /** Epoch millis when the watch event was recorded. */
    val timestamp: Long = System.currentTimeMillis(),
    /** How long the user actually watched, in milliseconds. */
    val durationWatchedMs: Long = 0L,
    /** Total duration of the content, in milliseconds. Zero if unknown. */
    val totalDurationMs: Long = 0L,
    /** Completion percentage (0–100). Derived from durationWatched / totalDuration. */
    val completionPercent: Float = 0f,
    /**
     * Flexible JSON metadata blob for auxiliary info (e.g. IMDB ID, actors, season,
     * episode number). Stored as a plain string — parsed by the consumer.
     */
    val metadata: String? = null
)
