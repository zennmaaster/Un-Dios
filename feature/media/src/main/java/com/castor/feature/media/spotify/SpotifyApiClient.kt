package com.castor.feature.media.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// =============================================================================
// Retrofit service definition
// =============================================================================

/**
 * Retrofit interface for the Spotify Web API.
 *
 * Every call requires a Bearer token passed via the `Authorization` header.
 * Tokens are obtained and managed by [SpotifyAuthManager].
 */
interface SpotifyApi {

    // ---- Playback state ---------------------------------------------------

    @GET("v1/me/player")
    suspend fun getPlaybackState(
        @Header("Authorization") auth: String
    ): Response<SpotifyPlaybackState>

    // ---- Playlists --------------------------------------------------------

    @GET("v1/me/playlists")
    suspend fun getPlaylists(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<SpotifyPlaylistsResponse>

    @GET("v1/playlists/{playlist_id}/tracks")
    suspend fun getPlaylistTracks(
        @Header("Authorization") auth: String,
        @Path("playlist_id") id: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<SpotifyTracksResponse>

    // ---- Playback control -------------------------------------------------

    @PUT("v1/me/player/play")
    suspend fun play(
        @Header("Authorization") auth: String,
        @Body body: SpotifyPlayBody? = null
    ): Response<Unit>

    @PUT("v1/me/player/pause")
    suspend fun pause(
        @Header("Authorization") auth: String
    ): Response<Unit>

    @POST("v1/me/player/next")
    suspend fun skipNext(
        @Header("Authorization") auth: String
    ): Response<Unit>

    @POST("v1/me/player/previous")
    suspend fun skipPrevious(
        @Header("Authorization") auth: String
    ): Response<Unit>

    @PUT("v1/me/player/seek")
    suspend fun seekTo(
        @Header("Authorization") auth: String,
        @Query("position_ms") positionMs: Long
    ): Response<Unit>

    // ---- Queue ------------------------------------------------------------

    @POST("v1/me/player/queue")
    suspend fun addToQueue(
        @Header("Authorization") auth: String,
        @Query("uri") uri: String
    ): Response<Unit>

    // ---- Search -----------------------------------------------------------

    @GET("v1/search")
    suspend fun search(
        @Header("Authorization") auth: String,
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 20
    ): Response<SpotifySearchResponse>
}

// =============================================================================
// Request bodies
// =============================================================================

@Serializable
data class SpotifyPlayBody(
    val uris: List<String>? = null,
    @SerialName("context_uri") val contextUri: String? = null,
    @SerialName("position_ms") val positionMs: Long? = null
)

// =============================================================================
// Response data classes
// =============================================================================

@Serializable
data class SpotifyPlaybackState(
    val device: SpotifyDevice? = null,
    @SerialName("is_playing") val isPlaying: Boolean = false,
    @SerialName("progress_ms") val progressMs: Long? = null,
    val item: SpotifyTrack? = null,
    @SerialName("shuffle_state") val shuffleState: Boolean = false,
    @SerialName("repeat_state") val repeatState: String = "off"
)

@Serializable
data class SpotifyDevice(
    val id: String? = null,
    val name: String = "",
    val type: String = "",
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("volume_percent") val volumePercent: Int? = null
)

// ---- Playlists ------------------------------------------------------------

@Serializable
data class SpotifyPlaylistsResponse(
    val items: List<SpotifyPlaylist> = emptyList(),
    val total: Int = 0,
    val limit: Int = 50,
    val offset: Int = 0,
    val next: String? = null
)

@Serializable
data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val description: String? = null,
    val images: List<SpotifyImage> = emptyList(),
    val tracks: SpotifyPlaylistTracksRef? = null,
    val uri: String = "",
    val owner: SpotifyOwner? = null
)

@Serializable
data class SpotifyPlaylistTracksRef(
    val total: Int = 0,
    val href: String? = null
)

@Serializable
data class SpotifyOwner(
    @SerialName("display_name") val displayName: String? = null,
    val id: String = ""
)

// ---- Tracks ---------------------------------------------------------------

@Serializable
data class SpotifyTracksResponse(
    val items: List<SpotifyTrackItem> = emptyList(),
    val total: Int = 0,
    val limit: Int = 100,
    val offset: Int = 0,
    val next: String? = null
)

/**
 * Wrapper used by the playlist-tracks endpoint.
 * Each item contains a [track] field holding the actual track data.
 */
@Serializable
data class SpotifyTrackItem(
    val track: SpotifyTrack? = null,
    @SerialName("added_at") val addedAt: String? = null
)

@Serializable
data class SpotifyTrack(
    val id: String? = null,
    val name: String = "",
    val artists: List<SpotifyArtist> = emptyList(),
    val album: SpotifyAlbum? = null,
    @SerialName("duration_ms") val durationMs: Long = 0,
    val uri: String = "",
    @SerialName("is_local") val isLocal: Boolean = false
)

@Serializable
data class SpotifyArtist(
    val id: String? = null,
    val name: String = ""
)

@Serializable
data class SpotifyAlbum(
    val id: String? = null,
    val name: String = "",
    val images: List<SpotifyImage> = emptyList()
)

@Serializable
data class SpotifyImage(
    val url: String = "",
    val width: Int? = null,
    val height: Int? = null
)

// ---- Search ---------------------------------------------------------------

@Serializable
data class SpotifySearchResponse(
    val tracks: SpotifySearchTracksWrapper? = null
)

@Serializable
data class SpotifySearchTracksWrapper(
    val items: List<SpotifyTrack> = emptyList(),
    val total: Int = 0,
    val limit: Int = 20,
    val offset: Int = 0,
    val next: String? = null
)
