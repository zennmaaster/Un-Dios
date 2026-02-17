package com.castor.feature.media.adapter

import com.castor.core.common.model.MediaSource
import com.castor.core.common.model.UnifiedMediaItem
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared interface for all media source adapters in Un-Dios.
 *
 * Each media service (Spotify, YouTube, Audible) implements this interface
 * to provide a unified API for playback control, search, and queue management.
 * Adapters that cannot support certain operations (e.g., Audible search) should
 * return sensible defaults (empty list, no-op).
 */
interface UnifiedMediaAdapter {

    /** The media source this adapter wraps. */
    val source: MediaSource

    /**
     * Whether this adapter is currently connected / authenticated.
     * For OAuth-based services this reflects a valid token.
     * For MediaSession-based services this reflects an active session.
     */
    val isConnected: StateFlow<Boolean>

    /**
     * Begin or resume playback.
     * @param uri Optional source-specific URI. When null, resumes current playback.
     */
    suspend fun play(uri: String? = null)

    /** Pause current playback. */
    suspend fun pause()

    /** Skip to the next track / chapter. */
    suspend fun skipNext()

    /** Skip to the previous track / chapter. */
    suspend fun skipPrevious()

    /**
     * Seek to an absolute position in the current track.
     * @param positionMs Position in milliseconds from the start.
     */
    suspend fun seekTo(positionMs: Long)

    /**
     * Add an item to the Un-Dios unified queue.
     * @param uri Source-specific URI identifying the media item.
     */
    suspend fun addToQueue(uri: String)

    /**
     * Search the service's catalog.
     * @param query Free-text search query.
     * @return List of matching [UnifiedMediaItem]s, or empty if unsupported.
     */
    suspend fun search(query: String): List<UnifiedMediaItem>

    /**
     * Retrieve the currently playing item with playback metadata.
     * @return The current item, or null if nothing is playing.
     */
    suspend fun getCurrentPlayback(): UnifiedMediaItem?
}
