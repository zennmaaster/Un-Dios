package com.castor.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.getSystemService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CastorApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService<NotificationManager>() ?: return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_AGENT_STATUS, "Agent Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing status of active agents"
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_REMINDERS, "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Scheduled reminders and alerts"
                enableVibration(true)
                enableLights(true)
            },
            NotificationChannel(
                CHANNEL_MESSAGES, "Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Aggregated messages from connected apps"
            }
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    companion object {
        const val CHANNEL_AGENT_STATUS = "agent_status"
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_MESSAGES = "messages"
    }
}
