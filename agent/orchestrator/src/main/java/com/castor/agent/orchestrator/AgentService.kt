package com.castor.agent.orchestrator

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.castor.core.inference.ModelManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AgentService : LifecycleService() {

    @Inject lateinit var modelManager: ModelManager

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()

        lifecycleScope.launch {
            modelManager.loadDefaultModel()
        }
    }

    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, "agent_status")
            .setContentTitle("Castor Active")
            .setContentText("AI agents are running")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        ServiceCompat.startForeground(
            this,
            1,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }
}
