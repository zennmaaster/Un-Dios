package com.castor.feature.reminders.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.castor.core.data.db.dao.ReminderDao
import com.castor.core.data.repository.Reminder
import com.castor.feature.reminders.engine.ReminderScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * [CoroutineWorker] responsible for periodic reminder maintenance.
 *
 * Three duties:
 *   1. **Reschedule** all active future reminders (restores alarms lost after
 *      reboot, app update, or timezone change).
 *   2. **Handle recurring reminders** that may have been missed while the device
 *      was off: advance the trigger time past "now" and reschedule.
 *   3. **Clean up** completed reminders older than 30 days.
 *
 * Enqueued as:
 *   - A one-shot work item by [BootReceiver] on device boot.
 *   - A periodic work item (e.g. every 6 hours) by the application initializer.
 */
@HiltWorker
class ReminderSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val reminderDao: ReminderDao,
    private val reminderScheduler: ReminderScheduler
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ReminderSyncWorker"

        /** Completed reminders older than 30 days are eligible for cleanup. */
        private const val CLEANUP_AGE_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }

    override suspend fun doWork(): Result {
        return try {
            Log.i(TAG, "Starting reminder sync work")

            val now = System.currentTimeMillis()

            // 1. Get all active (non-completed) reminders in one shot.
            val activeReminders = reminderDao.getActiveReminders().first()

            // 2. Handle recurring reminders whose trigger time has passed.
            val remindersToSchedule = mutableListOf<Reminder>()

            for (entity in activeReminders) {
                if (entity.isRecurring
                    && entity.recurringIntervalMs != null
                    && entity.recurringIntervalMs > 0
                    && entity.triggerTimeMs <= now
                ) {
                    // Advance the trigger time past "now" by stepping forward in
                    // interval-sized increments. This handles cases where the device
                    // was off for longer than one interval.
                    var nextTrigger = entity.triggerTimeMs
                    while (nextTrigger <= now) {
                        nextTrigger += entity.recurringIntervalMs
                    }

                    val updated = entity.copy(triggerTimeMs = nextTrigger)
                    reminderDao.updateReminder(updated)

                    remindersToSchedule.add(
                        Reminder(
                            id = updated.id,
                            description = updated.description,
                            triggerTimeMs = updated.triggerTimeMs,
                            isRecurring = updated.isRecurring,
                            recurringIntervalMs = updated.recurringIntervalMs,
                            isCompleted = updated.isCompleted,
                            createdAt = updated.createdAt
                        )
                    )
                    Log.d(TAG, "Advanced recurring reminder ${entity.id} to $nextTrigger")
                } else if (entity.triggerTimeMs > now) {
                    // Future one-shot or future recurring — just reschedule.
                    remindersToSchedule.add(
                        Reminder(
                            id = entity.id,
                            description = entity.description,
                            triggerTimeMs = entity.triggerTimeMs,
                            isRecurring = entity.isRecurring,
                            recurringIntervalMs = entity.recurringIntervalMs,
                            isCompleted = entity.isCompleted,
                            createdAt = entity.createdAt
                        )
                    )
                }
                // Non-recurring reminders in the past are effectively "missed" -- they
                // stay in the DB as active but we don't reschedule them. The UI can
                // surface them as overdue.
            }

            // 3. Reschedule all eligible reminders.
            reminderScheduler.rescheduleAll(remindersToSchedule)

            // 4. Clean up completed reminders older than 30 days.
            val cutoff = now - CLEANUP_AGE_MS
            reminderDao.cleanupOldCompleted(cutoff)
            Log.i(TAG, "Cleaned up completed reminders older than 30 days (cutoff=$cutoff)")

            Log.i(TAG, "Reminder sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Reminder sync failed", e)
            Result.retry()
        }
    }
}
