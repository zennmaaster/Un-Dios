package com.castor.feature.media.youtube

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

// =============================================================================
// Retrofit API interface
// =============================================================================

/**
 * Retrofit service for the YouTube Data API v3.
 * Base URL: https://www.googleapis.com/
 *
 * All calls require an Authorization header with a valid Bearer token
 * obtained from [YouTubeAuthManager].
 */
interface YouTubeApi {

    @GET("youtube/v3/playlists")
    suspend fun getPlaylists(
        @Header("Authorization") auth: String,
        @Query("part") part: String = "snippet,contentDetails",
        @Query("mine") mine: Boolean = true,
        @Query("maxResults") maxResults: Int = 25
    ): Response<YouTubePlaylistResponse>

    @GET("youtube/v3/playlistItems")
    suspend fun getPlaylistItems(
        @Header("Authorization") auth: String,
        @Query("part") part: String = "snippet,contentDetails",
        @Query("playlistId") playlistId: String,
        @Query("maxResults") maxResults: Int = 25
    ): Response<YouTubePlaylistItemResponse>

    @GET("youtube/v3/search")
    suspend fun search(
        @Header("Authorization") auth: String,
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 10
    ): Response<YouTubeSearchResponse>

    @GET("youtube/v3/videos")
    suspend fun getVideoDetails(
        @Header("Authorization") auth: String,
        @Query("part") part: String = "snippet,contentDetails",
        @Query("id") ids: String
    ): Response<YouTubeVideoResponse>
}

// =============================================================================
// Response data classes
// =============================================================================

// -- Playlists ----------------------------------------------------------------

@Serializable
data class YouTubePlaylistResponse(
    @SerialName("kind") val kind: String = "",
    @SerialName("etag") val etag: String = "",
    @SerialName("nextPageToken") val nextPageToken: String? = null,
    @SerialName("pageInfo") val pageInfo: PageInfo = PageInfo(),
    @SerialName("items") val items: List<YouTubePlaylist> = emptyList()
)

@Serializable
data class YouTubePlaylist(
    @SerialName("kind") val kind: String = "",
    @SerialName("etag") val etag: String = "",
    @SerialName("id") val id: String = "",
    @SerialName("snippet") val snippet: PlaylistSnippet = PlaylistSnippet(),
    @SerialName("contentDetails") val contentDetails: PlaylistContentDetails? = null
)

@Serializable
data class PlaylistSnippet(
    @SerialName("publishedAt") val publishedAt: String = "",
    @SerialName("channelId") val channelId: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("thumbnails") val thumbnails: ThumbnailSet = ThumbnailSet(),
    @SerialName("channelTitle") val channelTitle: String = ""
)

@Serializable
data class PlaylistContentDetails(
    @SerialName("itemCount") val itemCount: Int = 0
)

// -- Playlist Items -----------------------------------------------------------

@Serializable
data class YouTubePlaylistItemResponse(
    @SerialName("kind") val kind: String = "",
    @SerialName("etag") val etag: String = "",
    @SerialName("nextPageToken") val nextPageToken: String? = null,
    @SerialName("pageInfo") val pageInfo: PageInfo = PageInfo(),
    @SerialName("items") val items: List<YouTubePlaylistItem> = emptyList()
)

@Serializable
data class YouTubePlaylistItem(
    @SerialName("kind") val kind: String = "",
    @SerialName("etag") val etag: String = "",
    @SerialName("id") val id: String = "",
    @SerialName("snippet") val snippet: PlaylistItemSnippet = PlaylistItemSnippet(),
    @SerialName("contentDetails") val contentDetails: PlaylistItemContentDetails? = null
)

@Serializable
data class PlaylistItemSnippet(
    @SerialName("publishedAt") val publishedAt: String = "",
    @SerialName("channelId") val channelId: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("thumbnails") val thumbnails: ThumbnailSet = ThumbnailSet(),
    @SerialName("channelTitle") val channelTitle: String = "",
    @SerialName("playlistId") val playlistId: String = "",
    @SerialName("position") val position: Int = 0,
    @SerialName("resourceId") val resourceId: ResourceId = ResourceId()
)

@Serializable
data class PlaylistItemContentDetails(
    @SerialName("videoId") val videoId: String = "",
    @SerialName("videoPublishedAt") val videoPublishedAt: String = ""
)

// -- Search -------------------------------------------------------------------

@Serializable
data class YouTubeSearchResponse(
    @SerialName("kind") val kind: String = "",
    @SerialName("etag") val etag: String = "",
    @SerialName("nextPageToken") val nextPageToken: String? = null,
    @SerialName("regionCode") val regionCode: String = "",
    @SerialName("pageInfo") val pageInfo: PageInfo = PageInfo(),
    @SerialName("items") val items: List<YouTubeSearchResult> = emptyList()
)

@Serializable
data class YouTubeSearchResult(
    @SerialName("kind") val kind: String = "",
    @SerialName("etag") val etag: String = "",
    @SerialName("id") val id: SearchResultId = SearchResultId(),
    @SerialName("snippet") val snippet: SearchSnippet = SearchSnippet()
)

@Serializable
data class SearchResultId(
    @SerialName("kind") val kind: String = "",
    @SerialName("videoId") val videoId: String? = null,
    @SerialName("channelId") val channelId: String? = null,
    @SerialName("playlistId") val playlistId: String? = null
)

@Serializable
data class SearchSnippet(
    @SerialName("publishedAt") val publishedAt: String = "",
    @SerialName("channelId") val channelId: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("thumbnails") val thumbnails: ThumbnailSet = ThumbnailSet(),
    @SerialName("channelTitle") val channelTitle: String = "",
    @SerialName("liveBroadcastContent") val liveBroadcastContent: String = ""
)

// -- Videos -------------------------------------------------------------------

@Serializable
data class YouTubeVideoResponse(
    @SerialName("kind") val kind: String = "",
    @SerialName("etag") val etag: String = "",
    @SerialName("pageInfo") val pageInfo: PageInfo = PageInfo(),
    @SerialName("items") val items: List<YouTubeVideo> = emptyList()
)

@Serializable
data class YouTubeVideo(
    @SerialName("kind") val kind: String = "",
    @SerialName("etag") val etag: String = "",
    @SerialName("id") val id: String = "",
    @SerialName("snippet") val snippet: VideoSnippet = VideoSnippet(),
    @SerialName("contentDetails") val contentDetails: VideoContentDetails? = null
)

@Serializable
data class VideoSnippet(
    @SerialName("publishedAt") val publishedAt: String = "",
    @SerialName("channelId") val channelId: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("thumbnails") val thumbnails: ThumbnailSet = ThumbnailSet(),
    @SerialName("channelTitle") val channelTitle: String = "",
    @SerialName("tags") val tags: List<String> = emptyList(),
    @SerialName("categoryId") val categoryId: String = ""
)

@Serializable
data class VideoContentDetails(
    @SerialName("duration") val duration: String = "", // ISO 8601 duration, e.g. "PT4M13S"
    @SerialName("dimension") val dimension: String = "",
    @SerialName("definition") val definition: String = "",
    @SerialName("caption") val caption: String = "",
    @SerialName("licensedContent") val licensedContent: Boolean = false
)

// -- Shared data classes ------------------------------------------------------

@Serializable
data class PageInfo(
    @SerialName("totalResults") val totalResults: Int = 0,
    @SerialName("resultsPerPage") val resultsPerPage: Int = 0
)

@Serializable
data class ThumbnailSet(
    @SerialName("default") val default: Thumbnail? = null,
    @SerialName("medium") val medium: Thumbnail? = null,
    @SerialName("high") val high: Thumbnail? = null,
    @SerialName("standard") val standard: Thumbnail? = null,
    @SerialName("maxres") val maxres: Thumbnail? = null
)

@Serializable
data class Thumbnail(
    @SerialName("url") val url: String = "",
    @SerialName("width") val width: Int = 0,
    @SerialName("height") val height: Int = 0
)

@Serializable
data class ResourceId(
    @SerialName("kind") val kind: String = "",
    @SerialName("videoId") val videoId: String = ""
)

// =============================================================================
// Retrofit client provider
// =============================================================================

/**
 * Factory for the [YouTubeApi] Retrofit service.
 *
 * Provided via Hilt's DI module in the media feature. The instance uses
 * kotlinx-serialization for JSON and includes an OkHttp logging interceptor
 * in debug builds.
 */
object YouTubeApiClient {
    const val BASE_URL = "https://www.googleapis.com/"
}

// =============================================================================
// ISO 8601 duration helper
// =============================================================================

/**
 * Parse an ISO 8601 duration string (e.g. "PT4M13S") into milliseconds.
 * Returns null for unparseable values.
 */
fun parseIsoDuration(iso: String): Long? {
    val regex = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")
    val match = regex.matchEntire(iso) ?: return null
    val hours = match.groupValues[1].toLongOrNull() ?: 0L
    val minutes = match.groupValues[2].toLongOrNull() ?: 0L
    val seconds = match.groupValues[3].toLongOrNull() ?: 0L
    return ((hours * 3600) + (minutes * 60) + seconds) * 1000
}
