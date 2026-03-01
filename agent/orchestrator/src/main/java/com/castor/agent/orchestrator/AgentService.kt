package com.castor.agent.orchestrator

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.castor.core.common.model.AgentType
import com.castor.core.inference.ModelManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Foreground service that hosts the entire agent runtime.
 *
 * Responsibilities:
 * - Loads the on-device LLM model on start and unloads on stop.
 * - Registers the [SystemEventReceiver] to funnel system events into the [AgentEventBus].
 * - Starts the [ProactiveEngine] monitoring loop.
 * - Runs periodic health checks via [AgentHealthMonitor].
 * - Keeps an ongoing notification updated with current service status.
 * - Handles restart-after-kill via [onStartCommand] returning [START_STICKY].
 */
@AndroidEntryPoint
class AgentService : LifecycleService() {

    // -------------------------------------------------------------------------------------
    // Injected dependencies
    // -------------------------------------------------------------------------------------

    @Inject lateinit var modelManager: ModelManager
    @Inject lateinit var eventBus: AgentEventBus
    @Inject lateinit var healthMonitor: AgentHealthMonitor
    @Inject lateinit var proactiveEngine: ProactiveEngine

    // -------------------------------------------------------------------------------------
    // Internal state
    // -------------------------------------------------------------------------------------

    private var systemEventReceiver: SystemEventReceiver? = null
    private var healthCheckJob: Job? = null
    private var statusUpdateJob: Job? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "agent_status"
        private const val CHANNEL_NAME = "Agent Status"

        /** How often the foreground notification text is refreshed. */
        private const val STATUS_UPDATE_INTERVAL_MS = 15_000L
    }

    // -------------------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()

        // 1. Ensure the notification channel exists before starting foreground
        ensureNotificationChannel()

        // 2. Go foreground immediately to satisfy Android's timing requirement
        startForegroundWithNotification(
            title = "Un-Dios Starting...",
            text = "Initializing AI agents"
        )

        // 3. Register the system event receiver
        val receiver = SystemEventReceiver(eventBus, lifecycleScope)
        registerSystemEventReceiver(receiver)
        systemEventReceiver = receiver

        // 4. Start the proactive engine
        proactiveEngine.start()

        // 5. Start periodic health checks
        healthCheckJob = lifecycleScope.launch {
            while (isActive) {
                delay(AgentHealthMonitor.HEALTH_CHECK_INTERVAL_MS)
                healthMonitor.performHealthCheck()
                checkForAgentRestarts()
            }
        }

        // 6. Start periodic status notification updates
        statusUpdateJob = lifecycleScope.launch {
            while (isActive) {
                delay(STATUS_UPDATE_INTERVAL_MS)
                updateNotificationStatus()
            }
        }

        // 7. Load the LLM model
        lifecycleScope.launch {
            modelManager.loadDefaultModel()

            // Emit model lifecycle event
            val modelState = modelManager.modelState.value
            if (modelState is ModelManager.ModelState.Loaded) {
                eventBus.emit(AgentEvent.ModelLoaded(modelState.modelName))
                updateNotificationStatus()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // START_STICKY ensures the system restarts the service if it is killed
        return START_STICKY
    }

    override fun onDestroy() {
        // Cancel background jobs
        healthCheckJob?.cancel()
        healthCheckJob = null
        statusUpdateJob?.cancel()
        statusUpdateJob = null

        // Stop the proactive engine
        proactiveEngine.stop()

        // Unregister the system event receiver
        systemEventReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // Receiver was already unregistered -- safe to ignore
            }
        }
        systemEventReceiver = null

        // Unload the model synchronously before the scope is cancelled.
        // lifecycleScope.launch would be cancelled by super.onDestroy(),
        // so we use runBlocking to ensure the model is actually freed.
        runBlocking {
            eventBus.emit(AgentEvent.ModelUnloaded())
            modelManager.unloadModel()
        }

        super.onDestroy()
    }

    // -------------------------------------------------------------------------------------
    // Notification management
    // -------------------------------------------------------------------------------------

    /**
     * Create the notification channel for the foreground service. Idempotent --
     * calling multiple times is safe and has no effect after the first creation.
     */
    private fun ensureNotificationChannel() {
        val manager = getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing status of active AI agents"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Start the service in the foreground with the given notification content.
     */
    private fun startForegroundWithNotification(title: String, text: String) {
        val notification = buildStatusNotification(title, text)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    /**
     * Update the ongoing notification with current service status.
     */
    private fun updateNotificationStatus() {
        val modelState = modelManager.modelState.value
        val modelInfo = when (modelState) {
            is ModelManager.ModelState.Loaded -> modelState.modelName
            is ModelManager.ModelState.Loading -> "Loading ${modelState.modelName}..."
            is ModelManager.ModelState.Error -> "Model error"
            is ModelManager.ModelState.NotLoaded -> "No model"
        }

        val agentCount = AgentHealthMonitor.MONITORED_AGENTS.size
        val healthyCount = healthMonitor.agentStatuses.value.count { it.value.isHealthy }
        val healthText = if (healthMonitor.isSystemHealthy.value) {
            "$agentCount agents healthy"
        } else {
            "$healthyCount/$agentCount agents healthy"
        }

        val insightCount = proactiveEngine.proactiveInsights.value.size
        val insightText = if (insightCount > 0) " | $insightCount insights" else ""

        val notification = buildStatusNotification(
            title = "Un-Dios Active",
            text = "$modelInfo | $healthText$insightText"
        )

        val manager = getSystemService<NotificationManager>()
        manager?.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Build a notification with the given title and text.
     */
    private fun buildStatusNotification(
        title: String,
        text: String
    ): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }

    // -------------------------------------------------------------------------------------
    // Health monitoring
    // -------------------------------------------------------------------------------------

    /**
     * Check if any agent has been flagged for restart and attempt recovery.
     *
     * Currently, recovery consists of resetting the agent's error counter, which
     * clears the restart flag. In the future this could trigger an actual agent
     * re-initialization sequence.
     */
    private suspend fun checkForAgentRestarts() {
        val statuses = healthMonitor.agentStatuses.value
        for ((agentType, status) in statuses) {
            if (status.needsRestart) {
                // Log the restart attempt via the event bus
                eventBus.emit(
                    AgentEvent.AgentHealthCheck(
                        agentType = agentType,
                        isHealthy = false,
                        errorMessage = "Agent flagged for restart after ${status.errorCount} errors"
                    )
                )

                // Reset the agent so it can start fresh
                healthMonitor.resetAgent(agentType)

                // Emit a proactive insight so the user is aware
                eventBus.emit(
                    AgentEvent.ProactiveInsight(
                        agentType = agentType,
                        message = "${agentType.name} agent was restarted due to repeated errors.",
                        priority = InsightPriority.MEDIUM
                    )
                )
            }
        }
    }
}
