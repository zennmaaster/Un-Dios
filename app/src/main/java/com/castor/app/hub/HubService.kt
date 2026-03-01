package com.castor.app.hub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.castor.agent.orchestrator.AgentOrchestrator
import com.castor.core.common.hub.HubServer
import com.castor.core.common.hub.NsdDiscovery
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

@AndroidEntryPoint
class HubService : LifecycleService() {

    companion object {
        private const val TAG = "HubService"
        private const val NOTIFICATION_ID = 8484
        private const val CHANNEL_ID = "hub_status"
        private const val CHANNEL_NAME = "Hub Status"
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 10_000L
    }

    @Inject lateinit var orchestrator: AgentOrchestrator

    private var hubServer: HubServer? = null
    private var nsdDiscovery: NsdDiscovery? = null
    private var notificationUpdateJob: Job? = null

    val isRunning: StateFlow<Boolean>
        get() = hubServer?.isRunning ?: MutableStateFlow(false)

    val connectedClients: StateFlow<Int>
        get() = hubServer?.connectedClients ?: MutableStateFlow(0)

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()

        val ip = getWifiIpAddress() ?: "unknown"
        val port = HubServer.DEFAULT_PORT
        startForegroundWithNotification("Un-Dios Hub: Starting...", "Initializing on $ip:$port")

        val server = HubServer(
            port = port,
            orchestratorProvider = { input -> orchestrator.processInput(input) }
        )
        server.start()
        hubServer = server

        val discovery = NsdDiscovery(this)
        discovery.register(port)
        nsdDiscovery = discovery

        val resolvedIp = getWifiIpAddress() ?: "127.0.0.1"
        updateNotification("Un-Dios Hub: Running", "http://$resolvedIp:$port")

        notificationUpdateJob = lifecycleScope.launch {
            while (isActive) {
                delay(NOTIFICATION_UPDATE_INTERVAL_MS)
                refreshNotification()
            }
        }
        Log.i(TAG, "Hub service started on $resolvedIp:$port")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        notificationUpdateJob?.cancel()
        nsdDiscovery?.unregister()
        hubServer?.stop()
        hubServer = null
        nsdDiscovery = null
        Log.i(TAG, "Hub service stopped")
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows Un-Dios Hub server status and URL"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundWithNotification(title: String, text: String) {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(title, text),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun updateNotification(title: String, text: String) {
        getSystemService<NotificationManager>()?.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun refreshNotification() {
        val ip = getWifiIpAddress() ?: "127.0.0.1"
        val port = HubServer.DEFAULT_PORT
        val clients = hubServer?.connectedClients?.value ?: 0
        val running = hubServer?.isRunning?.value ?: false
        val title = if (running) "Un-Dios Hub: Running" else "Un-Dios Hub: Stopped"
        val clientText = if (clients > 0) " | $clients active" else ""
        updateNotification(title, "http://$ip:$port$clientText")
    }

    private fun buildNotification(title: String, text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()

    @Suppress("deprecation")
    private fun getWifiIpAddress(): String? {
        try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                val ip = String.format("%d.%d.%d.%d",
                    ipInt and 0xff, ipInt shr 8 and 0xff, ipInt shr 16 and 0xff, ipInt shr 24 and 0xff)
                if (ip != "0.0.0.0") return ip
            }
        } catch (_: Exception) {}
        try {
            for (ni in NetworkInterface.getNetworkInterfaces().asSequence()) {
                if (ni.isLoopback || !ni.isUp) continue
                for (addr in ni.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
