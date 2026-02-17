package com.castor.feature.recommendations.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules [RecommendationWorker] via WorkManager — both a daily periodic
 * refresh and on-demand one-shot triggers.
 */
@Singleton
class RecommendationWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    /**
     * Enqueues a periodic work request that runs the recommendation engine
     * once per day when the device is idle and has sufficient battery.
     */
    fun scheduleDailyRefresh() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<RecommendationWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(RecommendationWorker.WORK_NAME)
            .build()

        workManager.enqueueUniquePeriodicWork(
            RecommendationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Triggers an immediate one-shot refresh. Used when the user manually
     * requests new recommendations from the UI.
     */
    fun triggerImmediateRefresh() {
        val request = OneTimeWorkRequestBuilder<RecommendationWorker>()
            .addTag("${RecommendationWorker.WORK_NAME}_oneshot")
            .build()

        workManager.enqueueUniqueWork(
            "${RecommendationWorker.WORK_NAME}_oneshot",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Cancels all scheduled recommendation work.
     */
    fun cancelAll() {
        workManager.cancelUniqueWork(RecommendationWorker.WORK_NAME)
        workManager.cancelUniqueWork("${RecommendationWorker.WORK_NAME}_oneshot")
    }
}
