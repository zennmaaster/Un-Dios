package com.castor.feature.reminders.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.castor.core.data.repository.Reminder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules and cancels exact alarms for reminders via [AlarmManager].
 *
 * Each reminder maps to a single [PendingIntent] keyed by its database ID, so
 * scheduling a reminder with the same ID replaces any previously scheduled alarm
 * for that reminder. When the alarm fires it broadcasts to [ReminderReceiver].
 *
 * Uses [AlarmManager.setAlarmClock] for exact-time delivery that survives Doze
 * mode and shows in the system alarm UI.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "ReminderScheduler"

        /** Intent extra key for the reminder database row ID. */
        const val EXTRA_REMINDER_ID = "reminder_id"
    }

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // -------------------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------------------

    /**
     * Schedules an exact alarm that will fire [ReminderReceiver] at [triggerTimeMs].
     *
     * If an alarm for the given [reminderId] already exists it is implicitly replaced
     * because the [PendingIntent] uses a request code derived from the ID.
     *
     * On Android 12+ (API 31) we first check [AlarmManager.canScheduleExactAlarms]
     * and fall back to [AlarmManager.setAndAllowWhileIdle] if exact scheduling is
     * not permitted.
     */
    fun scheduleReminder(reminderId: Long, triggerTimeMs: Long) {
        val pendingIntent = createPendingIntent(reminderId)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    scheduleExact(triggerTimeMs, pendingIntent)
                } else {
                    Log.w(TAG, "Exact alarms not permitted; using inexact setAndAllowWhileIdle")
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                }
            } else {
                scheduleExact(triggerTimeMs, pendingIntent)
            }
            Log.d(TAG, "Scheduled reminder $reminderId for $triggerTimeMs")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm for reminder $reminderId", e)
            // Graceful degradation: use inexact alarm
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMs,
                pendingIntent
            )
        }
    }

    /**
     * Cancels any pending alarm for the given reminder.
     */
    fun cancelReminder(reminderId: Long) {
        val pendingIntent = createPendingIntent(reminderId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.d(TAG, "Cancelled alarm for reminder $reminderId")
    }

    /**
     * Reschedules alarms for all supplied reminders.
     *
     * Intended to be called on device boot (from [BootReceiver]) or when the app
     * detects that alarms may have been lost (e.g. app update, timezone change).
     * Only schedules reminders whose trigger time is in the future.
     */
    suspend fun rescheduleAll(reminders: List<Reminder>) {
        val now = System.currentTimeMillis()
        var scheduled = 0

        for (reminder in reminders) {
            if (!reminder.isCompleted && reminder.triggerTimeMs > now) {
                scheduleReminder(reminder.id, reminder.triggerTimeMs)
                scheduled++
            }
        }

        Log.i(TAG, "Rescheduled $scheduled of ${reminders.size} reminders")
    }

    // -------------------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------------------

    /**
     * Uses [AlarmManager.setAlarmClock] which is the most reliable way to deliver
     * an exact-time alarm. It also causes the system to show the alarm in the
     * status bar and lock screen.
     */
    private fun scheduleExact(triggerTimeMs: Long, pendingIntent: PendingIntent) {
        // The "show intent" launches the app when the user taps the alarm icon in the
        // status bar. We reuse the same PendingIntent for simplicity.
        val showIntent = PendingIntent.getActivity(
            context,
            0,
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTimeMs, showIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
    }

    /**
     * Creates a [PendingIntent] that will broadcast to [ReminderReceiver] when the
     * alarm fires. The request code is derived from the reminder ID so that each
     * reminder has a unique pending intent (and can be cancelled independently).
     */
    private fun createPendingIntent(reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.castor.REMINDER_TRIGGER"
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }

        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(), // request code — unique per reminder
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
