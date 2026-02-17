package com.castor.core.common.model

import kotlinx.serialization.Serializable

@Serializable
data class UnifiedMediaItem(
    val id: String,
    val source: MediaSource,
    val sourceUri: String,
    val title: String,
    val artist: String? = null,
    val albumArtUrl: String? = null,
    val durationMs: Long? = null,
    val mediaType: MediaType = MediaType.MUSIC
)

@Serializable
enum class MediaType {
    MUSIC, VIDEO, AUDIOBOOK, PODCAST
}
