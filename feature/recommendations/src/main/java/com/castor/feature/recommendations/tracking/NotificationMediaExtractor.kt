package com.castor.feature.recommendations.tracking

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.castor.core.common.model.MediaSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses notification bundles from streaming apps to extract media metadata.
 *
 * Each streaming service uses a slightly different notification format. This
 * class encapsulates the per-source heuristics so the rest of the tracking
 * pipeline only works with a normalised [ExtractedMediaEvent].
 */
@Singleton
class NotificationMediaExtractor @Inject constructor() {

    /**
     * Attempts to extract a media event from the given [StatusBarNotification].
     *
     * @return An [ExtractedMediaEvent] if the notification looks like a media
     *         playback notification, or `null` if it cannot be parsed.
     */
    fun extract(sbn: StatusBarNotification): ExtractedMediaEvent? {
        val source = MediaSource.fromPackageName(sbn.packageName) ?: return null
        return when (source) {
            MediaSource.NETFLIX -> extractNetflix(sbn)
            MediaSource.PRIME_VIDEO -> extractPrimeVideo(sbn)
            MediaSource.YOUTUBE -> extractYouTube(sbn)
            MediaSource.CHROME_BROWSER -> extractChrome(sbn)
            else -> null // Spotify, Audible handled by the existing media module
        }
    }

    // -------------------------------------------------------------------------------------
    // Netflix — com.netflix.mediaclient
    // -------------------------------------------------------------------------------------

    private fun extractNetflix(sbn: StatusBarNotification): ExtractedMediaEvent? {
        val extras = sbn.notification.extras ?: return null
        val title = extractTitle(extras) ?: return null

        // Netflix notifications typically include the show/movie title as the
        // notification title and episode info as the text.
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        // Heuristic: if there is episode info in the text, treat it as a series.
        val contentType = when {
            text?.contains("Episode", ignoreCase = true) == true -> "series"
            text?.contains("Season", ignoreCase = true) == true -> "series"
            title.contains("Documentary", ignoreCase = true) -> "documentary"
            else -> "movie"
        }

        return ExtractedMediaEvent(
            source = MediaSource.NETFLIX,
            title = title,
            contentType = contentType,
            subtitle = text,
            additionalInfo = subText,
            packageName = sbn.packageName
        )
    }

    // -------------------------------------------------------------------------------------
    // Prime Video — com.amazon.avod
    // -------------------------------------------------------------------------------------

    private fun extractPrimeVideo(sbn: StatusBarNotification): ExtractedMediaEvent? {
        val extras = sbn.notification.extras ?: return null
        val title = extractTitle(extras) ?: return null

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        val contentType = when {
            text?.contains("Episode", ignoreCase = true) == true -> "series"
            text?.contains("Season", ignoreCase = true) == true -> "series"
            title.contains("Documentary", ignoreCase = true) -> "documentary"
            else -> "movie"
        }

        return ExtractedMediaEvent(
            source = MediaSource.PRIME_VIDEO,
            title = title,
            contentType = contentType,
            subtitle = text,
            packageName = sbn.packageName
        )
    }

    // -------------------------------------------------------------------------------------
    // YouTube — com.google.android.youtube
    // -------------------------------------------------------------------------------------

    private fun extractYouTube(sbn: StatusBarNotification): ExtractedMediaEvent? {
        val extras = sbn.notification.extras ?: return null
        val title = extractTitle(extras) ?: return null

        // YouTube notifications for playback typically have channel name as text.
        val channelName = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        // Heuristic for content type based on title keywords.
        val contentType = when {
            title.contains("documentary", ignoreCase = true) -> "documentary"
            title.contains("trailer", ignoreCase = true) -> "video"
            title.contains("full movie", ignoreCase = true) -> "movie"
            title.contains("series", ignoreCase = true) -> "series"
            else -> "video"
        }

        return ExtractedMediaEvent(
            source = MediaSource.YOUTUBE,
            title = title,
            contentType = contentType,
            subtitle = channelName,
            packageName = sbn.packageName
        )
    }

    // -------------------------------------------------------------------------------------
    // Chrome — com.android.chrome
    // -------------------------------------------------------------------------------------

    private fun extractChrome(sbn: StatusBarNotification): ExtractedMediaEvent? {
        val extras = sbn.notification.extras ?: return null
        val title = extractTitle(extras) ?: return null

        // Chrome media notifications appear when a tab is playing audio/video.
        // We use heuristics to decide if this is "video content" worth tracking.
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        if (!looksLikeVideoContent(title, text)) return null

        return ExtractedMediaEvent(
            source = MediaSource.CHROME_BROWSER,
            title = title,
            contentType = "video",
            subtitle = text,
            packageName = sbn.packageName
        )
    }

    // -------------------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------------------

    private fun extractTitle(extras: Bundle): String? {
        return extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Heuristic check for whether a Chrome notification looks like video content
     * rather than, say, a news article or random page.
     */
    private fun looksLikeVideoContent(title: String?, text: String?): Boolean {
        val combined = "${title.orEmpty()} ${text.orEmpty()}".lowercase()
        val videoKeywords = listOf(
            "watch", "video", "documentary", "movie", "episode",
            "stream", "playing", "trailer", "film", "series",
            "netflix", "prime", "hulu", "disney+", "hbo"
        )
        return videoKeywords.any { it in combined }
    }
}

/**
 * Normalised media event extracted from a streaming app notification.
 */
data class ExtractedMediaEvent(
    val source: MediaSource,
    val title: String,
    val contentType: String = "video",
    val subtitle: String? = null,
    val additionalInfo: String? = null,
    val packageName: String
)
