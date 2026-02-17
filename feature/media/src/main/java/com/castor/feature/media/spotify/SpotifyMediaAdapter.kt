package com.castor.feature.media.spotify

import android.util.Log
import com.castor.core.common.model.MediaSource
import com.castor.core.common.model.MediaType
import com.castor.core.common.model.UnifiedMediaItem
import com.castor.feature.media.adapter.UnifiedMediaAdapter
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

// =============================================================================
// Spotify implementation
// =============================================================================

/**
 * Bridges the Spotify Web API to the [UnifiedMediaAdapter] contract.
 *
 * All network calls go through [SpotifyApi]; authentication is handled by
 * [SpotifyAuthManager] which supplies a valid Bearer token on every request.
 */
@Singleton
class SpotifyMediaAdapter @Inject constructor(
    private val spotifyApi: SpotifyApi,
    private val authManager: SpotifyAuthManager
) : UnifiedMediaAdapter {

    override val source: MediaSource = MediaSource.SPOTIFY

    override val isConnected: StateFlow<Boolean> = authManager.isAuthenticated

    // -----------------------------------------------------------------
    // Playback controls
    // -----------------------------------------------------------------

    override suspend fun play(uri: String?) {
        val token = bearerToken() ?: return
        val body = uri?.let { SpotifyPlayBody(uris = listOf(it)) }
        runCatching { spotifyApi.play(token, body) }
            .onFailure { Log.e(TAG, "play() failed", it) }
    }

    override suspend fun pause() {
        val token = bearerToken() ?: return
        runCatching { spotifyApi.pause(token) }
            .onFailure { Log.e(TAG, "pause() failed", it) }
    }

    override suspend fun skipNext() {
        val token = bearerToken() ?: return
        runCatching { spotifyApi.skipNext(token) }
            .onFailure { Log.e(TAG, "skipNext() failed", it) }
    }

    override suspend fun skipPrevious() {
        val token = bearerToken() ?: return
        runCatching { spotifyApi.skipPrevious(token) }
            .onFailure { Log.e(TAG, "skipPrevious() failed", it) }
    }

    override suspend fun seekTo(positionMs: Long) {
        val token = bearerToken() ?: return
        runCatching { spotifyApi.seekTo(token, positionMs) }
            .onFailure { Log.e(TAG, "seekTo() failed", it) }
    }

    override suspend fun addToQueue(uri: String) {
        val token = bearerToken() ?: return
        runCatching { spotifyApi.addToQueue(token, uri) }
            .onFailure { Log.e(TAG, "addToQueue() failed", it) }
    }

    // -----------------------------------------------------------------
    // Library / search
    // -----------------------------------------------------------------

    override suspend fun search(query: String): List<UnifiedMediaItem> {
        val token = bearerToken() ?: return emptyList()
        return runCatching {
            val response = spotifyApi.search(token, query)
            if (response.isSuccessful) {
                response.body()
                    ?.tracks
                    ?.items
                    ?.map { it.toUnifiedMediaItem() }
                    .orEmpty()
            } else {
                Log.w(TAG, "search() HTTP ${response.code()}")
                emptyList()
            }
        }.getOrElse {
            Log.e(TAG, "search() failed", it)
            emptyList()
        }
    }

    /**
     * Fetch the authenticated user's Spotify playlists.
     *
     * This is a Spotify-specific operation not covered by the shared
     * [UnifiedMediaAdapter] contract (consistent with the YouTube adapter's
     * `getMyPlaylists()` pattern).
     */
    suspend fun getPlaylists(): List<SpotifyPlaylist> {
        val token = bearerToken() ?: return emptyList()
        return runCatching {
            val response = spotifyApi.getPlaylists(token)
            if (response.isSuccessful) {
                response.body()?.items.orEmpty()
            } else {
                Log.w(TAG, "getPlaylists() HTTP ${response.code()}")
                emptyList()
            }
        }.getOrElse {
            Log.e(TAG, "getPlaylists() failed", it)
            emptyList()
        }
    }

    /**
     * Fetch tracks for a specific Spotify playlist.
     *
     * @param playlistId The Spotify playlist ID.
     * @return List of [UnifiedMediaItem]s for the tracks in the playlist.
     */
    suspend fun getPlaylistTracks(playlistId: String): List<UnifiedMediaItem> {
        val token = bearerToken() ?: return emptyList()
        return runCatching {
            val response = spotifyApi.getPlaylistTracks(token, playlistId)
            if (response.isSuccessful) {
                response.body()
                    ?.items
                    ?.mapNotNull { it.track?.toUnifiedMediaItem() }
                    .orEmpty()
            } else {
                Log.w(TAG, "getPlaylistTracks() HTTP ${response.code()}")
                emptyList()
            }
        }.getOrElse {
            Log.e(TAG, "getPlaylistTracks() failed", it)
            emptyList()
        }
    }

    override suspend fun getCurrentPlayback(): UnifiedMediaItem? {
        val token = bearerToken() ?: return null
        return runCatching {
            val response = spotifyApi.getPlaybackState(token)
            if (response.isSuccessful) {
                response.body()?.item?.toUnifiedMediaItem()
            } else {
                // 204 No Content = no active playback
                null
            }
        }.getOrElse {
            Log.e(TAG, "getCurrentPlayback() failed", it)
            null
        }
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private suspend fun bearerToken(): String? {
        val accessToken = authManager.getAccessToken()
        if (accessToken == null) {
            Log.w(TAG, "No valid Spotify access token available")
        }
        return accessToken?.let { "Bearer $it" }
    }

    companion object {
        private const val TAG = "SpotifyMediaAdapter"
    }
}

// =============================================================================
// Mapping extensions
// =============================================================================

/**
 * Converts a Spotify Web API [SpotifyTrack] to the domain [UnifiedMediaItem].
 */
fun SpotifyTrack.toUnifiedMediaItem(): UnifiedMediaItem {
    return UnifiedMediaItem(
        id = id ?: uri,
        source = MediaSource.SPOTIFY,
        sourceUri = uri,
        title = name,
        artist = artists.joinToString(", ") { it.name },
        albumArtUrl = album?.images?.firstOrNull()?.url,
        durationMs = durationMs,
        mediaType = MediaType.MUSIC
    )
}
