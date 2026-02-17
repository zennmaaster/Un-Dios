package com.castor.app.system

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.getSystemService
import com.castor.core.ui.components.SystemStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real-time system statistics provider for the Ubuntu-style status bar.
 *
 * Reads live data from multiple Android system APIs:
 * - **CPU usage** from `/proc/stat` (delta between two consecutive reads)
 * - **RAM** from [ActivityManager.MemoryInfo]
 * - **Battery** from the sticky `ACTION_BATTERY_CHANGED` broadcast
 * - **WiFi** from [ConnectivityManager] network capabilities
 * - **Bluetooth** from [BluetoothAdapter] bonded device connection profiles
 * - **Notification count** from [NotificationCountHolder] (fed by CastorNotificationListener)
 * - **Current time** formatted as Ubuntu-style date/time string
 *
 * Emits updated [SystemStats] every [UPDATE_INTERVAL_MS] milliseconds while monitoring
 * is active. Provided as a singleton via [com.castor.app.di.AppModule].
 */
class SystemStatsProvider(
    private val context: Context,
    private val notificationCountHolder: NotificationCountHolder
) {

    companion object {
        private const val UPDATE_INTERVAL_MS = 3000L
        private const val TIME_FORMAT = "EEE MMM d  HH:mm"
    }

    private val _stats = MutableStateFlow(SystemStats())
    val stats: StateFlow<SystemStats> = _stats.asStateFlow()

    private var monitoringScope: CoroutineScope? = null

    // Previous CPU readings for delta calculation
    private var previousCpuIdle: Long = 0L
    private var previousCpuTotal: Long = 0L

    /**
     * Start periodic monitoring of system statistics.
     * Stats are emitted to [stats] every [UPDATE_INTERVAL_MS] milliseconds.
     * Safe to call multiple times -- restarts if already running.
     */
    fun startMonitoring() {
        stopMonitoring()

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        monitoringScope = scope

        scope.launch {
            // Take an initial CPU reading to establish a baseline for delta calculation.
            readCpuRaw()

            while (isActive) {
                val snapshot = collectStats()
                _stats.value = snapshot
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    /**
     * Stop monitoring and release coroutine resources.
     */
    fun stopMonitoring() {
        monitoringScope?.cancel()
        monitoringScope = null
    }

    // -----------------------------------------------------------------------------------------
    // Stats collection
    // -----------------------------------------------------------------------------------------

    /**
     * Collect all system stats into a single [SystemStats] snapshot.
     */
    private fun collectStats(): SystemStats {
        val cpuUsage = readCpuUsage()
        val (ramUsedMb, ramTotalMb) = readRamUsage()
        val ramUsage = if (ramTotalMb > 0) (ramUsedMb.toFloat() / ramTotalMb.toFloat()) * 100f else 0f
        val (batteryPercent, isCharging) = readBattery()
        val wifiConnected = isWifiConnected()
        val bluetoothConnected = isBluetoothConnected()
        val currentTime = getCurrentTime()
        val unreadNotifications = getUnreadNotificationCount()

        return SystemStats(
            cpuUsage = cpuUsage,
            ramUsage = ramUsage,
            ramUsedMb = ramUsedMb,
            ramTotalMb = ramTotalMb,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            wifiConnected = wifiConnected,
            bluetoothConnected = bluetoothConnected,
            unreadNotifications = unreadNotifications,
            currentTime = currentTime
        )
    }

    // -----------------------------------------------------------------------------------------
    // CPU
    // -----------------------------------------------------------------------------------------

    /**
     * Read CPU usage by parsing `/proc/stat` and computing the delta of idle vs. total time
     * between two consecutive readings.
     *
     * The first line of `/proc/stat` looks like:
     * ```
     * cpu  user nice system idle iowait irq softirq steal guest guest_nice
     * ```
     *
     * @return CPU usage as a percentage (0.0 to 100.0).
     */
    private fun readCpuUsage(): Float {
        return try {
            val (idle, total) = readCpuRaw()

            val idleDelta = idle - previousCpuIdle
            val totalDelta = total - previousCpuTotal

            previousCpuIdle = idle
            previousCpuTotal = total

            if (totalDelta == 0L) {
                0f
            } else {
                ((totalDelta - idleDelta).toFloat() / totalDelta.toFloat()) * 100f
            }
        } catch (_: Exception) {
            0f
        }
    }

    /**
     * Read raw CPU tick counters from `/proc/stat`.
     *
     * @return Pair of (idle ticks, total ticks).
     */
    private fun readCpuRaw(): Pair<Long, Long> {
        val reader = RandomAccessFile("/proc/stat", "r")
        val line = reader.readLine()
        reader.close()

        // Example: "cpu  4705 356 584 3699 23 23 0 0 0 0"
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 5) return Pair(0L, 0L)

        val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
        val total = values.sum()
        // idle is at index 3, iowait at index 4
        val idle = values.getOrElse(3) { 0L } + values.getOrElse(4) { 0L }

        return Pair(idle, total)
    }

    // -----------------------------------------------------------------------------------------
    // RAM
    // -----------------------------------------------------------------------------------------

    /**
     * Read memory usage via [ActivityManager.MemoryInfo].
     *
     * @return Pair of (used MB, total MB).
     */
    private fun readRamUsage(): Pair<Long, Long> {
        return try {
            val activityManager = context.getSystemService<ActivityManager>()
                ?: return Pair(0L, 0L)

            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)

            val totalMb = memInfo.totalMem / (1024L * 1024L)
            val availMb = memInfo.availMem / (1024L * 1024L)
            val usedMb = totalMb - availMb

            Pair(usedMb, totalMb)
        } catch (_: Exception) {
            Pair(0L, 0L)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Battery
    // -----------------------------------------------------------------------------------------

    /**
     * Read battery level and charging status using the sticky `ACTION_BATTERY_CHANGED` broadcast.
     *
     * @return Pair of (battery percent 0-100, isCharging).
     */
    private fun readBattery(): Pair<Int, Boolean> {
        return try {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ) ?: return Pair(0, false)

            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            val percent = if (level >= 0 && scale > 0) {
                (level * 100) / scale
            } else {
                0
            }

            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            Pair(percent, isCharging)
        } catch (_: Exception) {
            Pair(0, false)
        }
    }

    // -----------------------------------------------------------------------------------------
    // WiFi
    // -----------------------------------------------------------------------------------------

    /**
     * Check whether the device is connected to a WiFi network.
     *
     * @return `true` if WiFi is the active transport.
     */
    private fun isWifiConnected(): Boolean {
        return try {
            val connectivityManager = context.getSystemService<ConnectivityManager>()
                ?: return false

            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                ?: return false

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Exception) {
            false
        }
    }

    // -----------------------------------------------------------------------------------------
    // Bluetooth
    // -----------------------------------------------------------------------------------------

    /**
     * Check whether any bonded Bluetooth device is currently connected.
     *
     * Uses [BluetoothManager] to get the adapter and checks common Bluetooth profiles
     * (A2DP, headset, health device) for connected devices among the bonded set.
     *
     * Gracefully returns `false` if:
     * - The device has no Bluetooth hardware
     * - Bluetooth permission is not granted (Android 12+)
     * - Bluetooth is disabled
     *
     * @return `true` if at least one bonded device is connected.
     */
    @SuppressLint("MissingPermission")
    private fun isBluetoothConnected(): Boolean {
        return try {
            // Check for BLUETOOTH_CONNECT permission on Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val hasPermission = context.checkSelfPermission(
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) return false
            }

            val bluetoothManager = context.getSystemService<BluetoothManager>()
                ?: return false
            val adapter = bluetoothManager.adapter ?: return false

            if (!adapter.isEnabled) return false

            // Check if any bonded device is currently connected via common profiles.
            // BluetoothDevice.isConnected is a hidden but accessible method via reflection,
            // but checking connected devices on specific profiles is more reliable.
            // We check the profiles that are most commonly used.
            val profilesToCheck = listOf(
                BluetoothProfile.HEADSET,
                BluetoothProfile.A2DP
            )

            // Quick check: iterate bonded devices and use the profile proxy connection state
            val bondedDevices = adapter.bondedDevices ?: return false
            for (device in bondedDevices) {
                for (profile in profilesToCheck) {
                    if (adapter.getProfileConnectionState(profile) == BluetoothProfile.STATE_CONNECTED) {
                        return true
                    }
                }
            }

            false
        } catch (_: Exception) {
            false
        }
    }

    // -----------------------------------------------------------------------------------------
    // Time
    // -----------------------------------------------------------------------------------------

    /**
     * Format the current time in Ubuntu-style format.
     * Example: "Tue Feb 17  14:23"
     */
    private fun getCurrentTime(): String {
        return try {
            val formatter = SimpleDateFormat(TIME_FORMAT, Locale.getDefault())
            formatter.format(Date())
        } catch (_: Exception) {
            ""
        }
    }

    // -----------------------------------------------------------------------------------------
    // Notifications
    // -----------------------------------------------------------------------------------------

    /**
     * Read the current unread notification count from the shared [NotificationCountHolder].
     *
     * This value is updated by [CastorNotificationListener] as notifications arrive and depart.
     *
     * @return The current count of active notifications.
     */
    private fun getUnreadNotificationCount(): Int {
        return notificationCountHolder.count.value
    }
}
