package com.castor.agent.orchestrator

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based worker that generates the morning briefing on a daily schedule.
 *
 * The worker:
 * 1. Uses [BriefingAgent] to generate a full [Briefing]
 * 2. Stores the briefing summary in SharedPreferences for quick access by the UI
 * 3. Posts a notification with the briefing headline so the user sees it on their lock screen
 *
 * Scheduling is handled by [scheduleDailyBriefing], which sets up a periodic work request
 * targeting ~7:00 AM daily. WorkManager handles battery optimization, Doze, and retry.
 */
@HiltWorker
class BriefingWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val briefingAgent: BriefingAgent
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "castor_daily_briefing"
        private const val CHANNEL_ID = "briefing_channel"
        private const val NOTIFICATION_ID = 2001
        private const val PREFS_NAME = "castor_briefing_prefs"
        private const val KEY_GREETING = "briefing_greeting"
        private const val KEY_CALENDAR = "briefing_calendar"
        private const val KEY_MESSAGES = "briefing_messages"
        private const val KEY_REMINDERS = "briefing_reminders"
        private const val KEY_MEDIA = "briefing_media"
        private const val KEY_GENERATED_AT = "briefing_generated_at"

        /**
         * Schedules the daily briefing worker to run at approximately 7:00 AM each day.
         *
         * Uses [PeriodicWorkRequestBuilder] with a 24-hour interval. The initial delay
         * is calculated so the first execution aligns with the next 7:00 AM occurrence.
         * If it is already past 7:00 AM today, the first run targets tomorrow at 7:00 AM.
         *
         * The work policy [ExistingPeriodicWorkPolicy.UPDATE] ensures that calling this
         * method multiple times does not create duplicate workers — it updates the existing one.
         */
        fun scheduleDailyBriefing(context: Context) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 7)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // If 7 AM has already passed today, schedule for tomorrow
                if (before(now)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val initialDelayMs = target.timeInMillis - now.timeInMillis

            val workRequest = PeriodicWorkRequestBuilder<BriefingWorker>(
                24, TimeUnit.HOURS
            )
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        /**
         * Cancels the daily briefing worker if it is currently scheduled.
         */
        fun cancelDailyBriefing(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            // Generate the briefing
            val briefing = briefingAgent.generateMorningBriefing()

            // Persist to SharedPreferences for the UI to read
            storeBriefing(briefing)

            // Show notification
            showBriefingNotification(briefing)

            Result.success()
        } catch (e: Exception) {
            // Retry up to the default maximum; after that, give up
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------------------

    /**
     * Stores the briefing in SharedPreferences so the UI can display it immediately
     * without waiting for a new generation. This is intentionally simple — DataStore
     * would be preferable for reactive reads, but SharedPreferences keeps the worker
     * dependency footprint small.
     */
    private fun storeBriefing(briefing: Briefing) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_GREETING, briefing.greeting)
            .putString(KEY_CALENDAR, briefing.calendarSummary)
            .putString(KEY_MESSAGES, briefing.messageSummary)
            .putString(KEY_REMINDERS, briefing.reminderSummary)
            .putString(KEY_MEDIA, briefing.mediaSuggestion)
            .putLong(KEY_GENERATED_AT, briefing.generatedAt)
            .apply()
    }

    // -------------------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------------------

    /**
     * Posts a system notification with the briefing headline.
     * Creates the notification channel on first use (required for Android O+).
     */
    private fun showBriefingNotification(briefing: Briefing) {
        ensureNotificationChannel()

        // Build a compact summary for the notification body
        val summaryLines = mutableListOf<String>()
        if (!briefing.messageSummary.contains("no unread", ignoreCase = true) &&
            !briefing.messageSummary.contains("all caught up", ignoreCase = true)
        ) {
            summaryLines.add(briefing.messageSummary)
        }
        if (!briefing.reminderSummary.contains("no reminders", ignoreCase = true)) {
            summaryLines.add(briefing.reminderSummary)
        }
        if (briefing.mediaSuggestion != null) {
            summaryLines.add(briefing.mediaSuggestion)
        }

        val bodyText = if (summaryLines.isEmpty()) {
            "Nothing urgent today. Enjoy your day!"
        } else {
            summaryLines.joinToString(" | ")
        }

        // Create a launch intent for the main app activity
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            null
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(briefing.greeting)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply {
                if (pendingIntent != null) {
                    setContentIntent(pendingIntent)
                }
            }
            .build()

        // Check notification permission (required on Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Permission not granted — skip notification silently.
                // The briefing is still stored in SharedPreferences.
                return
            }
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /**
     * Ensures the briefing notification channel exists. This is idempotent —
     * calling it multiple times has no effect after the channel is created.
     */
    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily Briefing",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Morning briefing summary from Un-Dios"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
