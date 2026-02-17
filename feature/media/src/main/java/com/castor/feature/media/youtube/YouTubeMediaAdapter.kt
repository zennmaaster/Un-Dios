package com.castor.feature.media.youtube

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.castor.core.common.model.MediaSource
import com.castor.core.common.model.MediaType
import com.castor.core.common.model.UnifiedMediaItem
import com.castor.feature.media.adapter.UnifiedMediaAdapter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [UnifiedMediaAdapter] implementation for YouTube.
 *
 * YouTube's Terms of Service require that video playback occurs inside a
 * visible YouTube player (native app or embedded WebView). Therefore this
 * adapter does NOT provide direct playback control -- instead, [play] opens
 * the YouTube app or a browser intent.
 *
 * Search is powered by the YouTube Data API v3 via [YouTubeApi]. Queue
 * management adds items to the Un-Dios unified queue rather than YouTube's
 * own queue.
 */
@Singleton
class YouTubeMediaAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: YouTubeAuthManager,
    private val youTubeApi: YouTubeApi
) : UnifiedMediaAdapter {

    override val source: MediaSource = MediaSource.YOUTUBE

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /**
     * Tracks the last known "now playing" item so [getCurrentPlayback] has
     * something to return after a [play] call. Since playback happens outside
     * our process we cannot observe real-time state.
     */
    private var lastPlayedItem: UnifiedMediaItem? = null

    /**
     * Un-Dios unified queue of YouTube items the user has queued.
     * This is NOT YouTube's own queue.
     */
    private val _queue = mutableListOf<UnifiedMediaItem>()
    val queue: List<UnifiedMediaItem> get() = _queue.toList()

    init {
        // Mirror the auth manager's state into our isConnected flow
        // Collect is not possible in init without a scope; we rely on
        // refreshConnectionState() being called from the ViewModel.
        _isConnected.value = authManager.isAuthenticated.value
    }

    /** Call from a coroutine scope to keep connection state in sync. */
    suspend fun observeAuthState() {
        authManager.isAuthenticated.collect { authenticated ->
            _isConnected.value = authenticated
        }
    }

    // -------------------------------------------------------------------------
    // Playback -- delegates to YouTube app / browser (ToS compliance)
    // -------------------------------------------------------------------------

    /**
     * Open a YouTube video for playback.
     *
     * @param uri YouTube video ID (e.g. "dQw4w9WgXcQ") or a full
     *   "https://www.youtube.com/watch?v=..." URL.
     *   When null, attempts to resume the last played item.
     */
    override suspend fun play(uri: String?) {
        val videoId = uri?.let { extractVideoId(it) }
            ?: lastPlayedItem?.id
            ?: return

        // Try to fetch video details to populate lastPlayedItem
        val token = authManager.getValidAccessToken()
        if (token != null) {
            try {
                val response = youTubeApi.getVideoDetails(
                    auth = "Bearer $token",
                    ids = videoId
                )
                response.body()?.items?.firstOrNull()?.let { video ->
                    lastPlayedItem = video.toUnifiedMediaItem()
                }
            } catch (_: Exception) {
                // If the API call fails, still open the video
            }
        }

        // Open YouTube via intent
        val watchUrl = "https://www.youtube.com/watch?v=$videoId"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(watchUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Prefer YouTube app if installed
            setPackage("com.google.android.youtube")
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // YouTube app not installed -- fall back to browser
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(watchUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        }
    }

    /**
     * Pause is a no-op for YouTube.
     * Playback happens in the YouTube app; we have no transport controls.
     */
    override suspend fun pause() {
        // YouTube playback is controlled by the YouTube player.
        // No-op: the user must pause via the YouTube app or notification.
    }

    /**
     * Skip next is a no-op for YouTube.
     * The user manages playback in the YouTube player.
     */
    override suspend fun skipNext() {
        // No-op: YouTube manages its own queue within its app.
    }

    /**
     * Skip previous is a no-op for YouTube.
     */
    override suspend fun skipPrevious() {
        // No-op: YouTube manages its own queue within its app.
    }

    /**
     * Seek is a no-op for YouTube.
     */
    override suspend fun seekTo(positionMs: Long) {
        // No-op: cannot control YouTube player programmatically per ToS.
    }

    // -------------------------------------------------------------------------
    // Queue -- Un-Dios unified queue, NOT YouTube's
    // -------------------------------------------------------------------------

    /**
     * Add a YouTube video to the Un-Dios unified queue.
     *
     * @param uri YouTube video ID or URL.
     */
    override suspend fun addToQueue(uri: String) {
        val videoId = extractVideoId(uri) ?: return
        val token = authManager.getValidAccessToken() ?: return

        try {
            val response = youTubeApi.getVideoDetails(
                auth = "Bearer $token",
                ids = videoId
            )
            response.body()?.items?.firstOrNull()?.let { video ->
                _queue.add(video.toUnifiedMediaItem())
            }
        } catch (_: Exception) {
            // If we cannot resolve metadata, add a minimal item
            _queue.add(
                UnifiedMediaItem(
                    id = videoId,
                    source = MediaSource.YOUTUBE,
                    sourceUri = "https://www.youtube.com/watch?v=$videoId",
                    title = "YouTube Video ($videoId)",
                    mediaType = MediaType.VIDEO
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // Search
    // -------------------------------------------------------------------------

    /**
     * Search YouTube for videos matching [query].
     *
     * @return List of [UnifiedMediaItem] with [MediaType.VIDEO].
     */
    override suspend fun search(query: String): List<UnifiedMediaItem> {
        val token = authManager.getValidAccessToken() ?: return emptyList()

        return try {
            val searchResponse = youTubeApi.search(
                auth = "Bearer $token",
                query = query
            )

            val searchItems = searchResponse.body()?.items ?: return emptyList()
            val videoIds = searchItems.mapNotNull { it.id.videoId }

            if (videoIds.isEmpty()) return emptyList()

            // Fetch full video details for duration information
            val detailsResponse = youTubeApi.getVideoDetails(
                auth = "Bearer $token",
                ids = videoIds.joinToString(",")
            )

            val videoMap = detailsResponse.body()?.items
                ?.associateBy { it.id }
                ?: emptyMap()

            searchItems.mapNotNull { searchResult ->
                val videoId = searchResult.id.videoId ?: return@mapNotNull null
                val video = videoMap[videoId]
                val durationMs = video?.contentDetails?.duration
                    ?.let { parseIsoDuration(it) }
                val thumbnail = searchResult.snippet.thumbnails.high
                    ?: searchResult.snippet.thumbnails.medium
                    ?: searchResult.snippet.thumbnails.default

                UnifiedMediaItem(
                    id = videoId,
                    source = MediaSource.YOUTUBE,
                    sourceUri = "https://www.youtube.com/watch?v=$videoId",
                    title = searchResult.snippet.title,
                    artist = searchResult.snippet.channelTitle,
                    albumArtUrl = thumbnail?.url,
                    durationMs = durationMs,
                    mediaType = MediaType.VIDEO
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // -------------------------------------------------------------------------
    // Current playback
    // -------------------------------------------------------------------------

    /**
     * Return the last item we opened for playback, if any.
     *
     * Because YouTube playback happens out-of-process, we cannot observe
     * real-time state. This is a best-effort representation.
     */
    override suspend fun getCurrentPlayback(): UnifiedMediaItem? = lastPlayedItem

    // -------------------------------------------------------------------------
    // Playlist helpers (for future use by the media hub UI)
    // -------------------------------------------------------------------------

    /**
     * Fetch the authenticated user's playlists.
     */
    suspend fun getMyPlaylists(): List<YouTubePlaylist> {
        val token = authManager.getValidAccessToken() ?: return emptyList()
        return try {
            val response = youTubeApi.getPlaylists(auth = "Bearer $token")
            response.body()?.items ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Fetch items in a specific playlist and convert to [UnifiedMediaItem]s.
     */
    suspend fun getPlaylistItems(playlistId: String): List<UnifiedMediaItem> {
        val token = authManager.getValidAccessToken() ?: return emptyList()
        return try {
            val response = youTubeApi.getPlaylistItems(
                auth = "Bearer $token",
                playlistId = playlistId
            )
            response.body()?.items?.map { item ->
                val thumbnail = item.snippet.thumbnails.high
                    ?: item.snippet.thumbnails.medium
                    ?: item.snippet.thumbnails.default
                val videoId = item.contentDetails?.videoId
                    ?: item.snippet.resourceId.videoId

                UnifiedMediaItem(
                    id = videoId,
                    source = MediaSource.YOUTUBE,
                    sourceUri = "https://www.youtube.com/watch?v=$videoId",
                    title = item.snippet.title,
                    artist = item.snippet.channelTitle,
                    albumArtUrl = thumbnail?.url,
                    mediaType = MediaType.VIDEO
                )
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Remove all items from the Un-Dios unified queue for YouTube. */
    fun clearQueue() {
        _queue.clear()
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Extract a YouTube video ID from a URL or return the input as-is if it
     * already looks like a bare video ID.
     */
    private fun extractVideoId(input: String): String? {
        // Full URL patterns
        val urlPatterns = listOf(
            Regex("(?:youtube\\.com/watch\\?v=|youtu\\.be/)([\\w-]{11})"),
            Regex("youtube\\.com/embed/([\\w-]{11})"),
            Regex("youtube\\.com/v/([\\w-]{11})")
        )
        for (pattern in urlPatterns) {
            pattern.find(input)?.groupValues?.getOrNull(1)?.let { return it }
        }

        // Bare video ID (11 characters, alphanumeric + dash + underscore)
        if (input.matches(Regex("[\\w-]{11}"))) return input

        return null
    }

    /**
     * Convert a YouTube API [YouTubeVideo] into a [UnifiedMediaItem].
     */
    private fun YouTubeVideo.toUnifiedMediaItem(): UnifiedMediaItem {
        val thumbnail = snippet.thumbnails.high
            ?: snippet.thumbnails.medium
            ?: snippet.thumbnails.default
        val durationMs = contentDetails?.duration?.let { parseIsoDuration(it) }

        return UnifiedMediaItem(
            id = id,
            source = MediaSource.YOUTUBE,
            sourceUri = "https://www.youtube.com/watch?v=$id",
            title = snippet.title,
            artist = snippet.channelTitle,
            albumArtUrl = thumbnail?.url,
            durationMs = durationMs,
            mediaType = MediaType.VIDEO
        )
    }
}
