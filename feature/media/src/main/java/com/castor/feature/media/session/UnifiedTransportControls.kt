package com.castor.feature.media.session

import android.media.session.MediaController
import android.util.Log
import com.castor.core.common.model.MediaSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides a single entry-point for media transport commands (play, pause,
 * skip, seek, ...) that are automatically routed to the correct underlying
 * [MediaController.TransportControls] — either the currently prioritised
 * session, or a specific session identified by [MediaSource].
 *
 * All public methods are safe to call from any thread; the transport controls
 * themselves dispatch through the system binder on the correct looper.
 */
@Singleton
class UnifiedTransportControls @Inject constructor(
    private val sessionMonitor: MediaSessionMonitor
) {
    companion object {
        private const val TAG = "UnifiedTransport"
    }

    // ---------------------------------------------------------------------------
    // Control the currently active / prioritised session
    // ---------------------------------------------------------------------------

    /** Resume playback on the primary session. */
    fun play() {
        getPrimaryControls()?.play()
            ?: Log.w(TAG, "play(): no active session")
    }

    /** Pause playback on the primary session. */
    fun pause() {
        getPrimaryControls()?.pause()
            ?: Log.w(TAG, "pause(): no active session")
    }

    /**
     * Toggle play/pause on the primary session. If the session is currently
     * playing it will be paused; if paused or stopped it will be resumed.
     */
    fun playPause() {
        val nowPlaying = sessionMonitor.nowPlaying.value
        if (nowPlaying.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    /** Skip to the next track on the primary session. */
    fun skipNext() {
        getPrimaryControls()?.skipToNext()
            ?: Log.w(TAG, "skipNext(): no active session")
    }

    /** Skip to the previous track on the primary session. */
    fun skipPrevious() {
        getPrimaryControls()?.skipToPrevious()
            ?: Log.w(TAG, "skipPrevious(): no active session")
    }

    /**
     * Seek to an absolute position (in milliseconds) on the primary session.
     *
     * @param positionMs Target position in milliseconds from the start of the track.
     */
    fun seekTo(positionMs: Long) {
        getPrimaryControls()?.seekTo(positionMs)
            ?: Log.w(TAG, "seekTo($positionMs): no active session")
    }

    /** Stop playback on the primary session. */
    fun stop() {
        getPrimaryControls()?.stop()
            ?: Log.w(TAG, "stop(): no active session")
    }

    // ---------------------------------------------------------------------------
    // Control a specific source
    // ---------------------------------------------------------------------------

    /**
     * Resume playback on the session belonging to [source].
     * No-op if there is no active session for that source.
     */
    fun playSource(source: MediaSource) {
        getControls(source)?.play()
            ?: Log.w(TAG, "playSource($source): no session for source")
    }

    /**
     * Pause playback on the session belonging to [source].
     * No-op if there is no active session for that source.
     */
    fun pauseSource(source: MediaSource) {
        getControls(source)?.pause()
            ?: Log.w(TAG, "pauseSource($source): no session for source")
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    /**
     * Return the [MediaController.TransportControls] for the currently
     * prioritised session (the one whose state is surfaced as [MediaSessionMonitor.nowPlaying]).
     */
    private fun getPrimaryControls(): MediaController.TransportControls? {
        val nowPlaying = sessionMonitor.nowPlaying.value
        return getControls(nowPlaying.source)
            ?: sessionMonitor.activeSessions.value.firstOrNull { it.isActive }?.transportControls
            ?: sessionMonitor.activeSessions.value.firstOrNull()?.transportControls
    }

    /**
     * Find the [MediaController.TransportControls] for the session matching
     * a given [MediaSource]. Returns `null` when no session is found.
     */
    private fun getControls(source: MediaSource?): MediaController.TransportControls? {
        if (source == null) return null
        return sessionMonitor.activeSessions.value
            .firstOrNull { it.source == source }
            ?.transportControls
    }
}
