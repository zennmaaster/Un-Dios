package com.castor.app.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.core.content.getSystemService
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
 * Real-time system statistics for the Ubuntu-style status bar.
 *
 * Reads CPU usage from /proc/stat, RAM from ActivityManager,
 * battery from BatteryManager, and WiFi connectivity from ConnectivityManager.
 * Emits updated [SystemStats] every 2 seconds while monitoring is active.
 *
 * Provided as a singleton via [com.castor.app.di.AppModule].
 */
class SystemStatsProvider(
    private val context: Context
) {

    companion object {
        private const val UPDATE_INTERVAL_MS = 2000L
        private const val TIME_FORMAT = "EEE MMM d  HH:mm"
    }

    private val _systemStats = MutableStateFlow(SystemStats())
    val systemStats: StateFlow<SystemStats> = _systemStats.asStateFlow()

    private var monitoringScope: CoroutineScope? = null

    // Previous CPU readings for delta calculation
    private var previousCpuIdle: Long = 0L
    private var previousCpuTotal: Long = 0L

    /**
     * Start periodic monitoring of system statistics.
     * Stats are emitted to [systemStats] every 2 seconds.
     * Safe to call multiple times — restarts if already running.
     */
    fun startMonitoring() {
        // Avoid duplicate monitoring loops
        stopMonitoring()

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        monitoringScope = scope

        scope.launch {
            // Initial CPU reading to establish a baseline
            readCpuRaw()

            while (isActive) {
                val stats = collectStats()
                _systemStats.value = stats
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    /**
     * Stop monitoring and release resources.
     */
    fun stopMonitoring() {
        monitoringScope?.cancel()
        monitoringScope = null
    }

    /**
     * Collect all system stats into a single [SystemStats] snapshot.
     */
    private fun collectStats(): SystemStats {
        val cpuUsage = getCpuUsage()
        val (ramUsedMb, ramTotalMb) = getMemoryInfo()
        val ramUsage = if (ramTotalMb > 0) (ramUsedMb.toFloat() / ramTotalMb.toFloat()) * 100f else 0f
        val (batteryPercent, isCharging) = getBatteryInfo()
        val wifiConnected = isWifiConnected()
        val currentTime = getCurrentTime()

        return SystemStats(
            cpuUsage = cpuUsage,
            ramUsage = ramUsage,
            ramUsedMb = ramUsedMb,
            ramTotalMb = ramTotalMb,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            wifiConnected = wifiConnected,
            currentTime = currentTime
        )
    }

    /**
     * Read CPU usage by parsing /proc/stat and computing the delta of idle vs total time
     * between two consecutive readings.
     *
     * The first line of /proc/stat looks like:
     * cpu  user nice system idle iowait irq softirq steal guest guest_nice
     *
     * @return CPU usage as a percentage (0.0 to 100.0).
     */
    private fun getCpuUsage(): Float {
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
        } catch (e: Exception) {
            0f
        }
    }

    /**
     * Read raw CPU counters from /proc/stat.
     *
     * @return Pair of (idle ticks, total ticks).
     */
    private fun readCpuRaw(): Pair<Long, Long> {
        val reader = RandomAccessFile("/proc/stat", "r")
        val line = reader.readLine()
        reader.close()

        // Example: "cpu  4705 356 584 3699 23 23 0 0 0 0"
        val parts = line.split(Regex("\\s+"))
        // parts[0] = "cpu", parts[1..] = user, nice, system, idle, iowait, irq, softirq, steal, ...

        if (parts.size < 5) return Pair(0L, 0L)

        val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
        val total = values.sum()
        // idle is at index 3 (4th value after "cpu"), iowait at index 4
        val idle = values.getOrElse(3) { 0L } + values.getOrElse(4) { 0L }

        return Pair(idle, total)
    }

    /**
     * Read memory information from ActivityManager.
     *
     * @return Pair of (used MB, total MB).
     */
    private fun getMemoryInfo(): Pair<Long, Long> {
        return try {
            val activityManager = context.getSystemService<ActivityManager>()
                ?: return Pair(0L, 0L)

            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)

            val totalMb = memInfo.totalMem / (1024L * 1024L)
            val availMb = memInfo.availMem / (1024L * 1024L)
            val usedMb = totalMb - availMb

            Pair(usedMb, totalMb)
        } catch (e: Exception) {
            Pair(0L, 0L)
        }
    }

    /**
     * Read battery level and charging status using a sticky broadcast.
     *
     * @return Pair of (battery percent 0-100, isCharging).
     */
    private fun getBatteryInfo(): Pair<Int, Boolean> {
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
        } catch (e: Exception) {
            Pair(0, false)
        }
    }

    /**
     * Check whether the device is connected to a WiFi network.
     *
     * @return true if WiFi is the active transport.
     */
    private fun isWifiConnected(): Boolean {
        return try {
            val connectivityManager = context.getSystemService<ConnectivityManager>()
                ?: return false

            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                ?: return false

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Format the current time in Ubuntu-style format.
     * Example: "Tue Feb 17  14:23"
     */
    private fun getCurrentTime(): String {
        return try {
            val formatter = SimpleDateFormat(TIME_FORMAT, Locale.getDefault())
            formatter.format(Date())
        } catch (e: Exception) {
            ""
        }
    }
}

/**
 * Snapshot of system statistics displayed in the Ubuntu-style status bar.
 */
data class SystemStats(
    /** CPU usage as a percentage (0.0 to 100.0). */
    val cpuUsage: Float = 0f,
    /** RAM usage as a percentage (0.0 to 100.0). */
    val ramUsage: Float = 0f,
    /** RAM currently in use, in megabytes. */
    val ramUsedMb: Long = 0,
    /** Total device RAM, in megabytes. */
    val ramTotalMb: Long = 0,
    /** Battery level as a percentage (0 to 100). */
    val batteryPercent: Int = 0,
    /** Whether the device is currently charging. */
    val isCharging: Boolean = false,
    /** Whether the device is connected to WiFi. */
    val wifiConnected: Boolean = false,
    /** Number of unread notifications (populated externally). */
    val unreadNotifications: Int = 0,
    /** Formatted current time string. */
    val currentTime: String = ""
)
