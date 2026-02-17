package com.castor.feature.recommendations.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.castor.core.data.repository.WatchHistoryRepository
import com.castor.feature.recommendations.engine.RecommendationEngine
import com.castor.feature.recommendations.engine.TasteProfileEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker that rebuilds the taste profile and generates fresh
 * recommendations in the background.
 *
 * Scheduled to run daily via a periodic work request (see [RecommendationWorkScheduler]).
 * Can also be triggered on-demand when the user opens the recommendations screen.
 *
 * All work is on-device — no network calls.
 */
@HiltWorker
class RecommendationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val tasteProfileEngine: TasteProfileEngine,
    private val recommendationEngine: RecommendationEngine,
    private val watchHistoryRepository: WatchHistoryRepository
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "RecommendationWorker"
        const val WORK_NAME = "recommendation_refresh"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting recommendation refresh...")

            // Step 1: Rebuild the taste profile from watch history.
            val profile = tasteProfileEngine.rebuildProfile()
            Log.d(TAG, "Taste profile rebuilt: ${profile.size} genres")

            // Step 2: Fetch recent watch history for the LLM prompt context.
            val recentHistory = watchHistoryRepository.getRecentSuspend(limit = 30)

            // Step 3: Generate new recommendations via local LLM.
            val recommendations = recommendationEngine.generateRecommendations(recentHistory)
            Log.d(TAG, "Generated ${recommendations.size} recommendations")

            // Step 4: Clean up stale recommendations (older than 30 days).
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            recommendationEngine.clearOld(thirtyDaysAgo)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Recommendation refresh failed", e)
            Result.retry()
        }
    }
}
