package com.castor.feature.recommendations.engine

import android.util.Log
import com.castor.core.data.db.dao.RecommendationDao
import com.castor.core.data.db.entity.RecommendationEntity
import com.castor.core.data.db.entity.TasteProfileEntity
import com.castor.core.data.db.entity.WatchHistoryEntity
import com.castor.core.inference.InferenceEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates personalised content recommendations using the on-device LLM.
 *
 * Workflow:
 * 1. Reads the user's taste profile and recent watch history.
 * 2. Constructs a ChatML prompt summarising preferences.
 * 3. Sends the prompt to [InferenceEngine.generate] (local llama.cpp).
 * 4. Parses the structured LLM output into [RecommendationEntity] objects.
 * 5. Persists recommendations into Room for offline access.
 *
 * No data leaves the device at any point.
 */
@Singleton
class RecommendationEngine @Inject constructor(
    private val inferenceEngine: InferenceEngine,
    private val tasteProfileEngine: TasteProfileEngine,
    private val catalogMatcher: ContentCatalogMatcher,
    private val recommendationDao: RecommendationDao
) {
    companion object {
        private const val TAG = "RecommendationEngine"

        private const val SYSTEM_PROMPT = """You are a media recommendation assistant running entirely on-device. Based on the user's viewing history and taste profile, suggest exactly 5 new titles they would enjoy.

For EACH recommendation, output EXACTLY this format (one per line, fields separated by " | "):
TITLE | GENRE | CONTENT_TYPE | MATCH_SCORE | PLATFORM | REASON

Rules:
- TITLE: The name of the movie, series, or documentary.
- GENRE: Primary genre (e.g. drama, comedy, sci-fi, thriller, documentary, anime, horror, action, fantasy, romance).
- CONTENT_TYPE: One of: movie, series, documentary.
- MATCH_SCORE: A number between 0.0 and 1.0 indicating how well it matches.
- PLATFORM: Where to watch — one of: Netflix, Prime Video, YouTube.
- REASON: A short sentence explaining why they would like it.

Output ONLY the 5 lines. No numbering, no headers, no extra text."""

        private const val MAX_TOKENS = 1024
        private const val TEMPERATURE = 0.7f
    }

    // -------------------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------------------

    /**
     * Generates fresh recommendations based on the current taste profile and
     * recent watch history, persists them, and returns the new list.
     *
     * @param recentHistory Optional pre-fetched history; if null, the engine
     *                       will use the taste profile alone.
     */
    suspend fun generateRecommendations(
        recentHistory: List<WatchHistoryEntity> = emptyList()
    ): List<RecommendationEntity> {
        return try {
            // Step 1: Get taste profile.
            val profile = tasteProfileEngine.getTopGenres(limit = 10)
            if (profile.isEmpty() && recentHistory.isEmpty()) {
                Log.d(TAG, "No taste profile or history — skipping generation")
                return emptyList()
            }

            // Step 2: Build the user prompt.
            val userPrompt = buildUserPrompt(profile, recentHistory)
            Log.d(TAG, "Generating recommendations with prompt (${userPrompt.length} chars)")

            // Step 3: Run local inference.
            val rawOutput = inferenceEngine.generate(
                prompt = userPrompt,
                systemPrompt = SYSTEM_PROMPT,
                maxTokens = MAX_TOKENS,
                temperature = TEMPERATURE
            )

            Log.d(TAG, "LLM output:\n$rawOutput")

            // Step 4: Parse and persist.
            val recommendations = parseLlmOutput(rawOutput)
            if (recommendations.isNotEmpty()) {
                recommendationDao.insertAll(recommendations)
                Log.d(TAG, "Persisted ${recommendations.size} new recommendations")
            }

            recommendations
        } catch (e: Exception) {
            Log.e(TAG, "Recommendation generation failed", e)
            emptyList()
        }
    }

    /**
     * Returns the current undismissed recommendations from the database
     * without triggering a new generation cycle.
     */
    suspend fun getCachedRecommendations(): List<RecommendationEntity> =
        recommendationDao.getUndismissedSuspend()

    /**
     * Dismisses a single recommendation by ID.
     */
    suspend fun dismiss(id: Long) = recommendationDao.dismiss(id)

    /**
     * Clears recommendations older than [olderThanMs] epoch millis.
     */
    suspend fun clearOld(olderThanMs: Long) = recommendationDao.clearOld(olderThanMs)

    // -------------------------------------------------------------------------------------
    // Prompt construction
    // -------------------------------------------------------------------------------------

    private fun buildUserPrompt(
        profile: List<TasteProfileEntity>,
        history: List<WatchHistoryEntity>
    ): String {
        val sb = StringBuilder()

        // Taste profile section.
        if (profile.isNotEmpty()) {
            sb.appendLine("My taste profile (genre -> preference score):")
            for (p in profile) {
                sb.appendLine("  - ${p.genre}: ${String.format("%.2f", p.score)}")
            }
            sb.appendLine()
        }

        // Recent watch history section (last 15 items for context window efficiency).
        val recentSlice = history.take(15)
        if (recentSlice.isNotEmpty()) {
            sb.appendLine("Recent watch history:")
            for (h in recentSlice) {
                val completion = if (h.completionPercent > 0) " (${h.completionPercent.toInt()}% watched)" else ""
                sb.appendLine("  - [${h.source}] ${h.title} (${h.contentType})$completion")
            }
            sb.appendLine()
        }

        sb.appendLine("Based on my viewing patterns, suggest 5 new titles I would enjoy.")
        return sb.toString()
    }

    // -------------------------------------------------------------------------------------
    // LLM output parsing
    // -------------------------------------------------------------------------------------

    /**
     * Parses the structured LLM output into [RecommendationEntity] objects.
     *
     * Expected line format:
     * `TITLE | GENRE | CONTENT_TYPE | MATCH_SCORE | PLATFORM | REASON`
     */
    private fun parseLlmOutput(raw: String): List<RecommendationEntity> {
        val now = System.currentTimeMillis()
        return raw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains("|") }
            .mapNotNull { line -> parseSingleLine(line, now) }
    }

    private fun parseSingleLine(line: String, timestamp: Long): RecommendationEntity? {
        val parts = line.split("|").map { it.trim() }
        if (parts.size < 5) {
            Log.w(TAG, "Skipping malformed line: $line")
            return null
        }

        val title = parts[0]
        val genre = parts[1]
        val matchScore = if (parts.size > 3) {
            parts[3].toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.5f
        } else 0.5f
        val platformRaw = if (parts.size > 4) parts[4] else ""
        val reason = if (parts.size > 5) parts[5] else ""

        // Use the catalog matcher to validate/enrich the platform suggestion.
        val platforms = catalogMatcher.suggestPlatforms(title, genre, platformRaw)
        val primarySource = platforms.firstOrNull()?.name ?: platformRaw

        return RecommendationEntity(
            title = title,
            description = reason,
            genre = genre,
            estimatedMatchScore = matchScore,
            source = primarySource,
            reason = reason,
            createdAt = timestamp,
            dismissed = false
        )
    }
}
