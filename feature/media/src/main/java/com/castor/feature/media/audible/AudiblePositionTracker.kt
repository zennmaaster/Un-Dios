package com.castor.feature.media.audible

import android.util.Log
import com.castor.core.data.db.entity.BookSyncEntity
import com.castor.core.data.repository.BookSyncRepository
import com.castor.feature.media.session.MediaSessionMonitor
import com.castor.feature.media.sync.BookMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors the Audible [MediaSession] for playback position changes and
 * persists the listening position in the [BookSyncRepository].
 *
 * This tracker works in tandem with the existing [AudibleMediaAdapter],
 * which provides low-level MediaSession control. This class adds the
 * higher-level concern of periodically sampling position and persisting
 * it for the Kindle-Audible sync feature.
 *
 * Polling interval: every [POLL_INTERVAL_MS] (30 seconds) while Audible
 * is actively playing. Polling stops automatically when Audible pauses or
 * the session disappears.
 */
@Singleton
class AudiblePositionTracker @Inject constructor(
    private val audibleAdapter: AudibleMediaAdapter,
    private val mediaSessionMonitor: MediaSessionMonitor,
    private val bookSyncRepository: BookSyncRepository,
    private val bookMatcher: BookMatcher
) {
    companion object {
        private const val TAG = "AudiblePosTracker"

        /** How often to sample Audible's playback position while playing. */
        private const val POLL_INTERVAL_MS = 30_000L

        /**
         * Minimum change in position (in ms) to trigger a DB write.
         * Prevents redundant writes when the user is paused.
         */
        private const val MIN_POSITION_DELTA_MS = 5_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Background polling job — only active while Audible is playing. */
    private var pollingJob: Job? = null

    /** Last known Audible listening state for quick UI access. */
    private val _currentState = MutableStateFlow<AudibleListeningState?>(null)
    val currentState: StateFlow<AudibleListeningState?> = _currentState.asStateFlow()

    /** Last position (ms) that was persisted — used to avoid redundant writes. */
    private var lastPersistedPositionMs: Long = -1L

    /**
     * Start polling for Audible position changes.
     *
     * Observes the [MediaSessionMonitor.activeSessions] flow and starts/stops
     * polling based on whether Audible has an active, playing session.
     */
    fun startTracking() {
        scope.launch {
            mediaSessionMonitor.activeSessions.collect { sessions ->
                val audibleSession = sessions.firstOrNull {
                    it.packageName == AudibleMediaAdapter.AUDIBLE_PACKAGE
                }

                if (audibleSession != null && audibleSession.isActive) {
                    ensurePolling()
                } else {
                    stopPolling()
                    // Still capture the paused state for UI display.
                    if (audibleSession != null) {
                        captureCurrentPosition()
                    }
                }
            }
        }
    }

    /**
     * Stop all tracking and clean up resources.
     */
    fun stopTracking() {
        stopPolling()
    }

    // -------------------------------------------------------------------------------------
    // Polling
    // -------------------------------------------------------------------------------------

    private fun ensurePolling() {
        if (pollingJob?.isActive == true) return

        pollingJob = scope.launch {
            Log.d(TAG, "Started position polling for Audible")
            while (isActive) {
                captureCurrentPosition()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    // -------------------------------------------------------------------------------------
    // Position capture
    // -------------------------------------------------------------------------------------

    /**
     * Read the current Audible position from the MediaSession and persist it.
     */
    private suspend fun captureCurrentPosition() {
        try {
            val title = audibleAdapter.getBookTitle() ?: return
            val author = audibleAdapter.getAuthor()
            val positionMs = audibleAdapter.getCurrentPositionMs()
            val durationMs = audibleAdapter.getCurrentDurationMs()
            val coverUrl = audibleAdapter.getCoverArtUrl()
            val isPlaying = audibleAdapter.isPlaying()

            // Calculate progress as a fraction of total duration.
            val progress = if (durationMs > 0) {
                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

            // Extract chapter name from the session metadata if available.
            val session = mediaSessionMonitor.activeSessions.value.firstOrNull {
                it.packageName == AudibleMediaAdapter.AUDIBLE_PACKAGE
            }
            val chapterName = session?.metadata?.title
                ?.takeIf { it != title } // Sometimes title == chapter name

            val state = AudibleListeningState(
                bookTitle = title,
                author = author,
                positionMs = positionMs,
                totalDurationMs = durationMs,
                progress = progress,
                chapterName = chapterName,
                coverUrl = coverUrl,
                isPlaying = isPlaying
            )

            _currentState.value = state

            // Only persist if the position has changed meaningfully.
            val delta = kotlin.math.abs(positionMs - lastPersistedPositionMs)
            if (delta >= MIN_POSITION_DELTA_MS || lastPersistedPositionMs < 0) {
                persistAudibleProgress(state)
                lastPersistedPositionMs = positionMs
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture Audible position", e)
        }
    }

    // -------------------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------------------

    /**
     * Persist the Audible listening state to the book sync database.
     *
     * If a matching book already exists (by title), update its Audible columns.
     * Otherwise, create a new [BookSyncEntity] for it.
     */
    private suspend fun persistAudibleProgress(state: AudibleListeningState) {
        try {
            val bookId = bookMatcher.generateBookId(state.bookTitle, state.author ?: "")

            val existing = bookSyncRepository.getBook(bookId)
                ?: bookSyncRepository.findByFuzzyMatch(state.bookTitle, state.author ?: "")

            if (existing != null) {
                bookSyncRepository.updateAudibleProgress(
                    bookId = existing.id,
                    progress = state.progress,
                    chapter = state.chapterName,
                    positionMs = state.positionMs,
                    totalMs = state.totalDurationMs
                )
                // Update cover URL if we have one and the existing entry doesn't.
                if (state.coverUrl != null && existing.coverUrl == null) {
                    bookSyncRepository.upsertBook(existing.copy(coverUrl = state.coverUrl))
                }
            } else {
                // Create a new entry for this audiobook.
                bookSyncRepository.upsertBook(
                    BookSyncEntity(
                        id = bookId,
                        title = state.bookTitle,
                        author = state.author ?: "",
                        audibleProgress = state.progress,
                        audibleChapter = state.chapterName,
                        audiblePositionMs = state.positionMs,
                        audibleTotalMs = state.totalDurationMs,
                        audibleLastSync = System.currentTimeMillis(),
                        coverUrl = state.coverUrl
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist Audible progress", e)
        }
    }
}

/**
 * Snapshot of the current Audible listening state for a single audiobook.
 */
data class AudibleListeningState(
    val bookTitle: String,
    val author: String?,
    val positionMs: Long,
    val totalDurationMs: Long,
    val progress: Float,
    val chapterName: String?,
    val coverUrl: String?,
    val isPlaying: Boolean
)
