package com.castor.feature.media.sync

import com.castor.core.data.db.entity.BookSyncEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculates estimated resume positions when switching between Kindle
 * (reading) and Audible (listening) for the same book.
 *
 * The core assumption is **linear interpolation**: if the user is 42% through
 * the Kindle book, we estimate 42% of the Audible total duration. This is
 * imperfect — narration pace varies by chapter — but improves over time as
 * both data sources provide calibration points.
 *
 * All calculations are pure functions operating on [BookSyncEntity] data.
 */
@Singleton
class SyncPositionCalculator @Inject constructor() {

    // -------------------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------------------

    /**
     * Given the current Kindle progress, estimate where the user should
     * resume in Audible.
     *
     * @return An [AudibleResumePoint] with estimated chapter/time, or null
     *   if insufficient data is available.
     */
    fun estimateAudiblePosition(book: BookSyncEntity): AudibleResumePoint? {
        val kindleProgress = book.kindleProgress ?: return null
        val audibleTotalMs = book.audibleTotalMs ?: return null
        if (audibleTotalMs <= 0) return null

        val estimatedPositionMs = (kindleProgress * audibleTotalMs).toLong()

        return AudibleResumePoint(
            estimatedPositionMs = estimatedPositionMs,
            estimatedProgress = kindleProgress,
            basedOnKindleProgress = kindleProgress,
            description = buildAudibleDescription(estimatedPositionMs, audibleTotalMs)
        )
    }

    /**
     * Given the current Audible progress, estimate where the user should
     * resume in Kindle.
     *
     * @return A [KindleResumePoint] with estimated page, or null if
     *   insufficient data is available.
     */
    fun estimateKindlePosition(book: BookSyncEntity): KindleResumePoint? {
        val audibleProgress = book.audibleProgress ?: return null

        val estimatedPage = if (book.kindleTotalPages != null && book.kindleTotalPages > 0) {
            (audibleProgress * book.kindleTotalPages).toInt().coerceAtLeast(1)
        } else {
            null
        }

        return KindleResumePoint(
            estimatedPage = estimatedPage,
            estimatedTotalPages = book.kindleTotalPages,
            estimatedProgress = audibleProgress,
            basedOnAudibleProgress = audibleProgress,
            description = buildKindleDescription(estimatedPage, book.kindleTotalPages, audibleProgress)
        )
    }

    /**
     * Compute the sync delta between Kindle and Audible positions.
     *
     * @return A [SyncDelta] describing which platform is ahead and by how much,
     *   or null if both progress values are not available.
     */
    fun computeSyncDelta(book: BookSyncEntity): SyncDelta? {
        val kindleProgress = book.kindleProgress ?: return null
        val audibleProgress = book.audibleProgress ?: return null

        val delta = kindleProgress - audibleProgress
        val absDelta = kotlin.math.abs(delta)
        val percentDelta = (absDelta * 100).toInt()

        return when {
            absDelta < SYNC_THRESHOLD -> SyncDelta(
                deltaPercent = percentDelta,
                aheadPlatform = null,
                description = "In sync",
                isSynced = true
            )
            delta > 0 -> SyncDelta(
                deltaPercent = percentDelta,
                aheadPlatform = SyncPlatform.KINDLE,
                description = "Kindle is $percentDelta% ahead",
                isSynced = false
            )
            else -> SyncDelta(
                deltaPercent = percentDelta,
                aheadPlatform = SyncPlatform.AUDIBLE,
                description = "Audible is $percentDelta% ahead",
                isSynced = false
            )
        }
    }

    // -------------------------------------------------------------------------------------
    // Description builders
    // -------------------------------------------------------------------------------------

    private fun buildAudibleDescription(positionMs: Long, totalMs: Long): String {
        val positionFormatted = formatTimeMs(positionMs)
        val totalFormatted = formatTimeMs(totalMs)
        return "Resume at $positionFormatted / $totalFormatted"
    }

    private fun buildKindleDescription(
        estimatedPage: Int?,
        totalPages: Int?,
        progress: Float
    ): String {
        return if (estimatedPage != null && totalPages != null) {
            "Resume at page $estimatedPage of $totalPages"
        } else {
            "Resume at ~${(progress * 100).toInt()}%"
        }
    }

    // -------------------------------------------------------------------------------------
    // Formatting
    // -------------------------------------------------------------------------------------

    /**
     * Format milliseconds into a human-readable "Xh Ym" or "Ym Zs" string.
     */
    private fun formatTimeMs(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    companion object {
        /**
         * Positions within this threshold (5%) are considered "in sync".
         */
        private const val SYNC_THRESHOLD = 0.05f
    }
}

// -------------------------------------------------------------------------------------
// Result data classes
// -------------------------------------------------------------------------------------

/**
 * Estimated resume point for Audible, derived from Kindle progress.
 */
data class AudibleResumePoint(
    /** Estimated position in the audiobook in milliseconds. */
    val estimatedPositionMs: Long,
    /** Estimated overall progress (0.0 to 1.0). */
    val estimatedProgress: Float,
    /** The Kindle progress value this estimate is based on. */
    val basedOnKindleProgress: Float,
    /** Human-readable description of the resume point. */
    val description: String
)

/**
 * Estimated resume point for Kindle, derived from Audible progress.
 */
data class KindleResumePoint(
    /** Estimated page number, or null if total pages are unknown. */
    val estimatedPage: Int?,
    /** Total pages in the Kindle edition, or null if unknown. */
    val estimatedTotalPages: Int?,
    /** Estimated overall progress (0.0 to 1.0). */
    val estimatedProgress: Float,
    /** The Audible progress value this estimate is based on. */
    val basedOnAudibleProgress: Float,
    /** Human-readable description of the resume point. */
    val description: String
)

/**
 * Describes the synchronisation delta between Kindle and Audible.
 */
data class SyncDelta(
    /** Absolute progress difference as a percentage (0-100). */
    val deltaPercent: Int,
    /** Which platform is ahead, or null if in sync. */
    val aheadPlatform: SyncPlatform?,
    /** Human-readable description. */
    val description: String,
    /** True if the positions are within the sync threshold. */
    val isSynced: Boolean
)

enum class SyncPlatform {
    KINDLE, AUDIBLE
}
