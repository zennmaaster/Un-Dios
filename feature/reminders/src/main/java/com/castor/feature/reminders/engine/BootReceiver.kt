package com.castor.feature.reminders.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingOneTimeWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.castor.feature.reminders.worker.ReminderSyncWorker

/**
 * Reschedules all active reminders after a device reboot.
 *
 * Android clears all [AlarmManager] alarms when the device powers off, so this
 * receiver listens for [Intent.ACTION_BOOT_COMPLETED] and enqueues a one-shot
 * [ReminderSyncWorker] to re-register every pending alarm.
 *
 * This receiver is also triggered by [Intent.ACTION_MY_PACKAGE_REPLACED] in case
 * alarms are lost during an app update, and by [Intent.ACTION_TIMEZONE_CHANGED]
 * to recalculate trigger times when the user changes timezone.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val WORK_NAME = "reminder_boot_reschedule"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.intent.action.QUICKBOOT_POWERON" // HTC / some OEMs
        )

        if (intent.action !in validActions) return

        Log.i(TAG, "Received ${intent.action} — scheduling reminder reschedule work")

        val workRequest = OneTimeWorkRequestBuilder<ReminderSyncWorker>()
            .addTag("boot_reschedule")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingOneTimeWorkPolicy.REPLACE,
            workRequest
        )
    }
}
