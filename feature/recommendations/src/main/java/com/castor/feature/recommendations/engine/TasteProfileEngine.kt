package com.castor.feature.recommendations.engine

import android.util.Log
import com.castor.core.data.db.dao.TasteProfileDao
import com.castor.core.data.db.dao.WatchHistoryDao
import com.castor.core.data.db.entity.TasteProfileEntity
import com.castor.core.data.db.entity.WatchHistoryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.max

/**
 * Builds and maintains the on-device taste profile from watch history.
 *
 * The profile is a set of per-genre scores in [0.0, 1.0] that encode the
 * user's viewing preferences, weighted by:
 *
 * 1. **Completion rate** — content the user finished scores higher.
 * 2. **Recency** — recent watches have an exponential decay advantage.
 * 3. **Frequency** — genres watched more often accumulate a higher base score.
 * 4. **Source affinity** — which streaming platforms the user prefers.
 *
 * All computation is done on-device; no data leaves the device.
 */
@Singleton
class TasteProfileEngine @Inject constructor(
    private val watchHistoryDao: WatchHistoryDao,
    private val tasteProfileDao: TasteProfileDao
) {
    companion object {
        private const val TAG = "TasteProfileEngine"

        /** Half-life for recency weighting, in milliseconds (~7 days). */
        private const val RECENCY_HALF_LIFE_MS = 7L * 24 * 60 * 60 * 1000

        /** Minimum number of watch events before we start computing a profile. */
        private const val MIN_EVENTS_FOR_PROFILE = 3

        /** Default genre assigned to events that have no genre tag. */
        private const val UNKNOWN_GENRE = "unknown"
    }

    // -------------------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------------------

    /**
     * Re-computes the full taste profile from the current watch history
     * and persists the result. This is idempotent — safe to call repeatedly.
     *
     * @return The computed list of [TasteProfileEntity] rows, sorted by score descending.
     */
    suspend fun rebuildProfile(): List<TasteProfileEntity> {
        val history = watchHistoryDao.getRecentSuspend(limit = 500)
        if (history.size < MIN_EVENTS_FOR_PROFILE) {
            Log.d(TAG, "Not enough history (${history.size}) to build a profile")
            return emptyList()
        }

        val now = System.currentTimeMillis()

        // Step 1: Group events by genre and compute weighted scores.
        val genreScores = mutableMapOf<String, Double>()
        val genreSourceWeights = mutableMapOf<String, MutableMap<String, Double>>()

        for (event in history) {
            val genre = (event.genre ?: UNKNOWN_GENRE).lowercase().trim()
            val weight = computeEventWeight(event, now)

            genreScores[genre] = (genreScores[genre] ?: 0.0) + weight

            // Track per-source weight for this genre.
            val sourceMap = genreSourceWeights.getOrPut(genre) { mutableMapOf() }
            sourceMap[event.source] = (sourceMap[event.source] ?: 0.0) + weight
        }

        // Step 2: Normalise scores to [0, 1] relative to the maximum.
        val maxScore = genreScores.values.maxOrNull() ?: 1.0
        val profiles = genreScores.map { (genre, rawScore) ->
            val normalisedScore = (rawScore / max(maxScore, 1.0)).toFloat().coerceIn(0f, 1f)

            // Normalise source affinities for this genre.
            val sourceMap = genreSourceWeights[genre] ?: emptyMap()
            val sourceMax = sourceMap.values.maxOrNull() ?: 1.0
            val normalisedSources = sourceMap.mapValues { (_, v) ->
                (v / max(sourceMax, 1.0)).coerceIn(0.0, 1.0)
            }

            TasteProfileEntity(
                genre = genre,
                score = normalisedScore,
                sourceAffinity = buildSourceAffinityJson(normalisedSources),
                lastUpdated = now
            )
        }.sortedByDescending { it.score }

        // Step 3: Persist (full replace).
        tasteProfileDao.deleteAll()
        tasteProfileDao.upsertAll(profiles)

        Log.d(TAG, "Taste profile rebuilt: ${profiles.size} genres")
        return profiles
    }

    /**
     * Incrementally updates the taste profile with a single new watch event
     * without re-scanning the full history.
     */
    suspend fun onNewWatchEvent(event: WatchHistoryEntity) {
        val genre = (event.genre ?: UNKNOWN_GENRE).lowercase().trim()
        val now = System.currentTimeMillis()
        val weight = computeEventWeight(event, now)

        val existing = tasteProfileDao.getByGenre(genre)
        if (existing != null) {
            // Blend: new score = 0.8 * existing + 0.2 * new_contribution (capped at 1)
            val blendedScore = (existing.score * 0.8f + weight.toFloat() * 0.2f).coerceIn(0f, 1f)
            tasteProfileDao.upsert(
                existing.copy(
                    score = blendedScore,
                    lastUpdated = now
                )
            )
        } else {
            // New genre entry.
            val sourceJson = buildSourceAffinityJson(mapOf(event.source to 1.0))
            tasteProfileDao.upsert(
                TasteProfileEntity(
                    genre = genre,
                    score = weight.toFloat().coerceIn(0f, 1f),
                    sourceAffinity = sourceJson,
                    lastUpdated = now
                )
            )
        }
    }

    /**
     * Returns the current top genres from the persisted taste profile.
     */
    suspend fun getTopGenres(limit: Int = 5): List<TasteProfileEntity> =
        tasteProfileDao.getTopGenresSuspend(limit)

    /**
     * Returns the full persisted taste profile.
     */
    suspend fun getFullProfile(): List<TasteProfileEntity> =
        tasteProfileDao.getAllSuspend()

    // -------------------------------------------------------------------------------------
    // Weighting
    // -------------------------------------------------------------------------------------

    /**
     * Computes a composite weight for a single watch event.
     *
     * weight = completionFactor * recencyFactor
     *
     * - completionFactor: 0.3 for 0% completion, up to 1.0 for 100%.
     * - recencyFactor: exponential decay with [RECENCY_HALF_LIFE_MS] half-life.
     */
    private fun computeEventWeight(event: WatchHistoryEntity, now: Long): Double {
        val completionFactor = 0.3 + 0.7 * (event.completionPercent / 100f).coerceIn(0f, 1f)
        val ageMs = (now - event.timestamp).coerceAtLeast(0)
        val recencyFactor = exp(-0.693 * ageMs / RECENCY_HALF_LIFE_MS)
        return completionFactor * recencyFactor
    }

    // -------------------------------------------------------------------------------------
    // JSON helpers
    // -------------------------------------------------------------------------------------

    private fun buildSourceAffinityJson(sources: Map<String, Double>): String {
        if (sources.isEmpty()) return "{}"
        val entries = sources.entries.joinToString(",") { (k, v) ->
            "\"$k\":${String.format("%.3f", v)}"
        }
        return "{$entries}"
    }
}
