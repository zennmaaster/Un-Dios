package com.castor.feature.recommendations.engine

import com.castor.core.common.model.MediaSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps recommended content titles to probable streaming platforms using
 * keyword-based heuristics.
 *
 * This is a lightweight, on-device approach that avoids any network calls.
 * It uses a simple keyword/pattern strategy to guess platform availability.
 * In a future iteration this can be enhanced with a local catalog database
 * (e.g. a bundled TMDb snapshot).
 */
@Singleton
class ContentCatalogMatcher @Inject constructor() {

    /**
     * Given a title and optional genre, returns a ranked list of
     * [MediaSource] values where the content is likely available.
     *
     * The LLM-generated recommendation may already include a suggested
     * platform; this method provides a fallback / validation layer.
     */
    fun suggestPlatforms(
        title: String,
        genre: String? = null,
        llmSuggestedSource: String? = null
    ): List<MediaSource> {
        val platforms = mutableListOf<MediaSource>()

        // If the LLM already suggested a specific source, trust it first.
        llmSuggestedSource?.let { suggested ->
            val matched = parseSourceString(suggested)
            if (matched != null) platforms.add(matched)
        }

        // Heuristic: certain genres or keywords map to likely platforms.
        val lowerTitle = title.lowercase()
        val lowerGenre = genre?.lowercase().orEmpty()

        // YouTube — free content, tutorials, shorts, music videos.
        if (lowerTitle.contains("tutorial") ||
            lowerTitle.contains("music video") ||
            lowerTitle.contains("vlog") ||
            lowerGenre.contains("music")
        ) {
            addIfAbsent(platforms, MediaSource.YOUTUBE)
        }

        // Netflix — broad catalog, originals.
        if (lowerTitle.contains("netflix") || lowerGenre in NETFLIX_STRONG_GENRES) {
            addIfAbsent(platforms, MediaSource.NETFLIX)
        }

        // Prime Video — broad catalog, Amazon originals.
        if (lowerTitle.contains("prime") || lowerTitle.contains("amazon") ||
            lowerGenre in PRIME_STRONG_GENRES
        ) {
            addIfAbsent(platforms, MediaSource.PRIME_VIDEO)
        }

        // Default: if nothing matched, suggest all three major platforms.
        if (platforms.isEmpty()) {
            platforms.addAll(listOf(MediaSource.NETFLIX, MediaSource.PRIME_VIDEO, MediaSource.YOUTUBE))
        }

        return platforms.distinct()
    }

    /**
     * Parses a free-form source string (from LLM output) into a [MediaSource].
     */
    fun parseSourceString(raw: String): MediaSource? {
        val lower = raw.lowercase().trim()
        return when {
            "netflix" in lower -> MediaSource.NETFLIX
            "prime" in lower || "amazon" in lower -> MediaSource.PRIME_VIDEO
            "youtube" in lower -> MediaSource.YOUTUBE
            "chrome" in lower || "browser" in lower -> MediaSource.CHROME_BROWSER
            "spotify" in lower -> MediaSource.SPOTIFY
            "audible" in lower -> MediaSource.AUDIBLE
            else -> null
        }
    }

    private fun addIfAbsent(list: MutableList<MediaSource>, source: MediaSource) {
        if (source !in list) list.add(source)
    }

    companion object {
        /** Genres where Netflix tends to have strong catalog representation. */
        private val NETFLIX_STRONG_GENRES = setOf(
            "drama", "thriller", "sci-fi", "comedy", "documentary",
            "anime", "romance", "horror", "true crime", "stand-up"
        )

        /** Genres where Prime Video tends to have strong catalog representation. */
        private val PRIME_STRONG_GENRES = setOf(
            "action", "thriller", "sci-fi", "fantasy", "drama",
            "documentary", "comedy", "horror", "adventure"
        )
    }
}
