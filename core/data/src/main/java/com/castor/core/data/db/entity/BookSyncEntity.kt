package com.castor.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks the reading/listening position of a book across Kindle and Audible.
 *
 * Each row represents a single book that has been detected on at least one
 * of the two platforms. When the same book is identified on both platforms
 * (via [BookMatcher]), both the Kindle and Audible columns are populated,
 * enabling the user to seamlessly switch between reading and listening.
 *
 * All data stays on-device inside the SQLCipher-encrypted Room database.
 */
@Entity(tableName = "book_sync")
data class BookSyncEntity(
    /** Generated from a hash of title + author for stable identity. */
    @PrimaryKey val id: String,
    /** Book title as detected from Kindle or Audible metadata. */
    val title: String,
    /** Author name as detected from Kindle or Audible metadata. */
    val author: String,

    // -- Kindle progress --
    /** Kindle reading progress as a fraction (0.0 to 1.0). */
    val kindleProgress: Float? = null,
    /** Last page number detected from Kindle. */
    val kindleLastPage: Int? = null,
    /** Total page count detected from Kindle (if available). */
    val kindleTotalPages: Int? = null,
    /** Chapter name last seen in Kindle (if accessible). */
    val kindleChapter: String? = null,
    /** Epoch millis when Kindle progress was last updated. */
    val kindleLastSync: Long? = null,

    // -- Audible progress --
    /** Audible listening progress as a fraction (0.0 to 1.0). */
    val audibleProgress: Float? = null,
    /** Current chapter name in Audible playback. */
    val audibleChapter: String? = null,
    /** Current playback position within the audiobook in milliseconds. */
    val audiblePositionMs: Long? = null,
    /** Total audiobook duration in milliseconds. */
    val audibleTotalMs: Long? = null,
    /** Epoch millis when Audible progress was last updated. */
    val audibleLastSync: Long? = null,

    // -- Shared metadata --
    /** Cover art URL (usually extracted from Audible's MediaSession metadata). */
    val coverUrl: String? = null,
    /** Whether this book has been manually matched by the user. */
    val manualMatch: Boolean = false,
    /** Epoch millis of the last update to any field in this row. */
    val lastUpdated: Long = System.currentTimeMillis()
)
