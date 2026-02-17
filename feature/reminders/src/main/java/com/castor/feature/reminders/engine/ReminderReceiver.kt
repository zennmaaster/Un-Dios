package com.castor.feature.reminders.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.castor.core.data.db.dao.ReminderDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [BroadcastReceiver] that fires when a reminder alarm triggers via [AlarmManager].
 *
 * Responsibilities:
 *   1. Look up the reminder from the database.
 *   2. Post a notification with the reminder description.
 *   3. Add "Complete" and "Snooze" action buttons to the notification.
 *   4. If the reminder is recurring, schedule the next occurrence.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"

        const val CHANNEL_ID = "castor_reminders"
        const val CHANNEL_NAME = "Reminders"

        const val ACTION_COMPLETE = "com.castor.REMINDER_COMPLETE"
        const val ACTION_SNOOZE = "com.castor.REMINDER_SNOOZE"

        /** Default snooze duration: 10 minutes. */
        const val SNOOZE_DURATION_MS = 10 * 60 * 1000L

        private const val NOTIFICATION_GROUP = "com.castor.REMINDER_GROUP"
    }

    @Inject lateinit var reminderDao: ReminderDao
    @Inject lateinit var reminderScheduler: ReminderScheduler

    // BroadcastReceiver.onReceive runs on the main thread and has a ~10 s window,
    // so we use goAsync() + a coroutine to do the DB lookup safely.
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1)
        if (reminderId == -1L) {
            Log.w(TAG, "Received broadcast with no reminder_id extra")
            return
        }

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                when (action) {
                    ACTION_COMPLETE -> handleComplete(reminderId)
                    ACTION_SNOOZE -> handleSnooze(context, reminderId)
                    else -> handleTrigger(context, reminderId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling reminder $reminderId with action=$action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Action handlers
    // -------------------------------------------------------------------------------------

    /**
     * The alarm fired normally -- show the notification and handle recurrence.
     */
    private suspend fun handleTrigger(context: Context, reminderId: Long) {
        val entity = reminderDao.getReminderById(reminderId)
        if (entity == null) {
            Log.w(TAG, "Reminder $reminderId not found in database")
            return
        }

        if (entity.isCompleted) {
            Log.d(TAG, "Reminder $reminderId is already completed; skipping notification")
            return
        }

        showNotification(context, reminderId, entity.description)

        // If recurring, schedule the next occurrence.
        val intervalMs = entity.recurringIntervalMs
        if (entity.isRecurring && intervalMs != null && intervalMs > 0) {
            val nextTrigger = entity.triggerTimeMs + intervalMs
            reminderDao.updateReminder(entity.copy(triggerTimeMs = nextTrigger))
            reminderScheduler.scheduleReminder(reminderId, nextTrigger)
            Log.d(TAG, "Recurring reminder $reminderId rescheduled for $nextTrigger")
        }
    }

    /**
     * User tapped "Complete" on the notification.
     */
    private suspend fun handleComplete(reminderId: Long) {
        reminderDao.markCompleted(reminderId)
        reminderScheduler.cancelReminder(reminderId)
        Log.d(TAG, "Reminder $reminderId marked complete")
    }

    /**
     * User tapped "Snooze" on the notification -- reschedule for +10 minutes.
     */
    private suspend fun handleSnooze(context: Context, reminderId: Long) {
        val entity = reminderDao.getReminderById(reminderId) ?: return
        val newTrigger = System.currentTimeMillis() + SNOOZE_DURATION_MS
        reminderDao.updateReminder(entity.copy(triggerTimeMs = newTrigger))
        reminderScheduler.scheduleReminder(reminderId, newTrigger)

        // Dismiss the current notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(reminderId.toInt())
        Log.d(TAG, "Reminder $reminderId snoozed until $newTrigger")
    }

    // -------------------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------------------

    private fun showNotification(context: Context, reminderId: Long, description: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureNotificationChannel(nm)

        // -- "Complete" action --
        val completeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_COMPLETE
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
        }
        val completePi = PendingIntent.getBroadcast(
            context,
            ("complete_$reminderId").hashCode(),
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // -- "Snooze" action --
        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
        }
        val snoozePi = PendingIntent.getBroadcast(
            context,
            ("snooze_$reminderId").hashCode(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // -- Content tap → open app --
        val contentIntent = PendingIntent.getActivity(
            context,
            ("open_$reminderId").hashCode(),
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Reminder")
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setGroup(NOTIFICATION_GROUP)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Complete",
                completePi
            )
            .addAction(
                android.R.drawable.ic_menu_recent_history,
                "Snooze (10 min)",
                snoozePi
            )
            .build()

        nm.notify(reminderId.toInt(), notification)
    }

    private fun ensureNotificationChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for scheduled reminders"
                    enableVibration(true)
                    setShowBadge(true)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }
}
