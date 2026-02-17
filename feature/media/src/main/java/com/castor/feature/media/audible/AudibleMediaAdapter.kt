package com.castor.feature.media.audible

import android.media.session.MediaController
import com.castor.core.common.model.MediaSource
import com.castor.core.common.model.MediaType
import com.castor.core.common.model.UnifiedMediaItem
import com.castor.feature.media.adapter.UnifiedMediaAdapter
import com.castor.feature.media.session.MediaSessionInfo
import com.castor.feature.media.session.MediaSessionMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [UnifiedMediaAdapter] for Audible.
 *
 * Audible does not expose a public API. All playback monitoring and control
 * is performed through the Android [MediaSession][android.media.session.MediaSession]
 * that Audible publishes when an audiobook is playing.
 *
 * The adapter delegates to [MediaSessionMonitor] to discover Audible's active
 * session and uses the [MediaController.TransportControls] exposed in
 * [MediaSessionInfo] for play/pause/skip/seek. Metadata (title, author,
 * cover art, duration, position) is read from the [MediaSessionInfo.metadata]
 * which is a [NowPlayingState][com.castor.feature.media.session.NowPlayingState]
 * pre-extracted by the monitor.
 *
 * Limitations:
 * - [search] always returns an empty list (no API to search the Audible library).
 * - [addToQueue] is a no-op (cannot programmatically queue in Audible).
 * - The user must open the Audible app to browse and select audiobooks.
 */
@Singleton
class AudibleMediaAdapter @Inject constructor(
    private val sessionMonitor: MediaSessionMonitor
) : UnifiedMediaAdapter {

    companion object {
        /** Audible's application package name used to identify its MediaSession. */
        const val AUDIBLE_PACKAGE = "com.audible.application"
    }

    override val source: MediaSource = MediaSource.AUDIBLE

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /**
     * Call from a coroutine scope to keep [isConnected] in sync with
     * whether Audible has an active media session.
     */
    suspend fun observeSessionState() {
        sessionMonitor.activeSessions.collect { sessions ->
            _isConnected.value = sessions.any { it.packageName == AUDIBLE_PACKAGE }
        }
    }

    // -------------------------------------------------------------------------
    // Playback controls -- all via MediaController.TransportControls
    // -------------------------------------------------------------------------

    /**
     * Resume Audible playback.
     *
     * @param uri Ignored. Audible does not support deep-linking to a specific
     *   audiobook chapter via MediaSession. The user must select content in
     *   the Audible app.
     */
    override suspend fun play(uri: String?) {
        getTransportControls()?.play()
    }

    override suspend fun pause() {
        getTransportControls()?.pause()
    }

    /** Skip to the next chapter in the current audiobook. */
    override suspend fun skipNext() {
        getTransportControls()?.skipToNext()
    }

    /** Skip to the previous chapter in the current audiobook. */
    override suspend fun skipPrevious() {
        getTransportControls()?.skipToPrevious()
    }

    /**
     * Seek to [positionMs] within the current chapter.
     */
    override suspend fun seekTo(positionMs: Long) {
        getTransportControls()?.seekTo(positionMs)
    }

    /**
     * No-op. Cannot programmatically add items to Audible's queue.
     */
    override suspend fun addToQueue(uri: String) {
        // Audible does not support external queue management.
    }

    /**
     * Always returns an empty list. There is no API to search the user's
     * Audible library programmatically.
     */
    override suspend fun search(query: String): List<UnifiedMediaItem> = emptyList()

    // -------------------------------------------------------------------------
    // Current playback -- read from MediaSessionInfo.metadata (NowPlayingState)
    // -------------------------------------------------------------------------

    /**
     * Build a [UnifiedMediaItem] from the Audible session's [NowPlayingState]
     * metadata (pre-extracted by [MediaSessionMonitor]).
     *
     * Fields mapped from [NowPlayingState]:
     * - `title` -> book title
     * - `artist` -> author
     * - `albumArtUri` -> cover image URL
     * - `durationMs` -> chapter duration
     * - `positionMs` -> current playback position (encoded in sourceUri)
     *
     * @return The current audiobook item, or null if Audible is not playing.
     */
    override suspend fun getCurrentPlayback(): UnifiedMediaItem? {
        val session = getAudibleSession() ?: return null
        val meta = session.metadata

        val title = meta.title ?: return null

        return UnifiedMediaItem(
            id = "audible_${title.hashCode()}",
            source = MediaSource.AUDIBLE,
            sourceUri = "audible://playback?position=${meta.positionMs}",
            title = title,
            artist = meta.artist,
            albumArtUrl = meta.albumArtUri,
            durationMs = meta.durationMs.takeIf { it > 0 },
            mediaType = MediaType.AUDIOBOOK
        )
    }

    // -------------------------------------------------------------------------
    // Additional metadata helpers
    // -------------------------------------------------------------------------

    /**
     * Check if Audible is currently playing (vs. paused).
     * Reads from the pre-extracted [MediaSessionInfo.isActive] flag.
     */
    fun isPlaying(): Boolean {
        val session = getAudibleSession() ?: return false
        return session.isActive
    }

    /**
     * Get the current playback position in milliseconds.
     */
    fun getCurrentPositionMs(): Long {
        val session = getAudibleSession() ?: return 0L
        return session.metadata.positionMs
    }

    /**
     * Get the total duration of the current chapter in milliseconds.
     */
    fun getCurrentDurationMs(): Long {
        val session = getAudibleSession() ?: return 0L
        return session.metadata.durationMs.takeIf { it > 0 } ?: 0L
    }

    /**
     * Get the book title from the current Audible session, or null.
     */
    fun getBookTitle(): String? = getAudibleSession()?.metadata?.title

    /**
     * Get the author from the current Audible session, or null.
     */
    fun getAuthor(): String? = getAudibleSession()?.metadata?.artist

    /**
     * Get the album art URI from the current Audible session, or null.
     */
    fun getCoverArtUrl(): String? = getAudibleSession()?.metadata?.albumArtUri

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Look up Audible's [MediaSessionInfo] from the [MediaSessionMonitor].
     *
     * @return The session info, or null if Audible does not have an active session.
     */
    private fun getAudibleSession(): MediaSessionInfo? {
        return sessionMonitor.activeSessions.value.firstOrNull {
            it.packageName == AUDIBLE_PACKAGE
        }
    }

    /**
     * Shorthand: get transport controls from the Audible media session.
     * [MediaSessionInfo.transportControls] is pre-extracted by [MediaSessionMonitor].
     */
    private fun getTransportControls(): MediaController.TransportControls? {
        return getAudibleSession()?.transportControls
    }
}
