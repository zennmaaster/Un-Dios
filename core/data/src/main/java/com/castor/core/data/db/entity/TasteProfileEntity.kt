package com.castor.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists a single genre preference in the user's on-device taste profile.
 *
 * Each row represents the user's affinity for one genre, weighted by
 * watch time, completion rate, and recency. The [sourceAffinity] JSON
 * string maps streaming sources to per-source weight factors.
 */
@Entity(tableName = "taste_profile")
data class TasteProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The genre / category this row tracks (e.g. "drama", "sci-fi", "comedy"). */
    val genre: String,
    /** Normalised preference score in the range [0.0, 1.0]. */
    val score: Float = 0f,
    /**
     * JSON map of source -> weight.
     * Example: {"NETFLIX":0.6,"YOUTUBE":0.3,"PRIME_VIDEO":0.1}
     */
    val sourceAffinity: String = "{}",
    /** Epoch millis of the last time this row was recalculated. */
    val lastUpdated: Long = System.currentTimeMillis()
)
