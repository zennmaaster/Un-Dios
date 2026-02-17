package com.castor.app.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for the Screen Time / App Usage Stats feature.
 *
 * Reads all data from Android's [UsageStatsManager] — foreground time per app,
 * screen-on events (approximating "pickups"), and daily totals for the weekly
 * bar chart. All processing is on-device; no data leaves the device.
 *
 * Exposes:
 * - [selectedPeriod]    — the time range the user selected (Today / This Week / This Month)
 * - [usageSummary]      — aggregate stats (total time, pickups, avg session, change %)
 * - [topApps]           — per-app usage sorted by foreground time descending
 * - [categoryBreakdown] — usage grouped by [UsageCategory]
 * - [dailyUsage]        — per-day totals for the current week (bar chart)
 * - [hasPermission]     — whether USAGE_ACCESS is granted
 * - [isLoading]         — true while a query is in progress
 *
 * Data is reloaded every time [selectedPeriod] changes or [refresh] is called.
 *
 * Threading: heavy I/O (UsageStatsManager queries, PackageManager icon loads)
 * runs on [Dispatchers.IO]; state mutations happen on the main thread via
 * [MutableStateFlow.value] assignment.
 */
@HiltViewModel
class UsageStatsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    // =========================================================================
    // Public state
    // =========================================================================

    private val _selectedPeriod = MutableStateFlow(UsagePeriod.TODAY)
    val selectedPeriod: StateFlow<UsagePeriod> = _selectedPeriod.asStateFlow()

    private val _usageSummary = MutableStateFlow(UsageSummary())
    val usageSummary: StateFlow<UsageSummary> = _usageSummary.asStateFlow()

    private val _topApps = MutableStateFlow<List<AppUsage>>(emptyList())
    val topApps: StateFlow<List<AppUsage>> = _topApps.asStateFlow()

    private val _categoryBreakdown = MutableStateFlow<List<CategoryUsage>>(emptyList())
    val categoryBreakdown: StateFlow<List<CategoryUsage>> = _categoryBreakdown.asStateFlow()

    private val _dailyUsage = MutableStateFlow<List<DailyUsage>>(emptyList())
    val dailyUsage: StateFlow<List<DailyUsage>> = _dailyUsage.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // =========================================================================
    // Initialization
    // =========================================================================

    init {
        checkPermissionAndLoad()
    }

    // =========================================================================
    // Public actions
    // =========================================================================

    /**
     * Changes the aggregation period and reloads all usage data.
     */
    fun selectPeriod(period: UsagePeriod) {
        if (_selectedPeriod.value == period) return
        _selectedPeriod.value = period
        loadUsageStats()
    }

    /**
     * Force-refreshes all data (e.g. when returning from Settings after granting permission).
     */
    fun refresh() {
        checkPermissionAndLoad()
    }

    /**
     * Opens the system Usage Access settings screen so the user can grant
     * the PACKAGE_USAGE_STATS permission to Un-Dios.
     */
    fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback: open general app settings if the usage access intent fails
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (_: Exception) {
                // Device has no settings activity — nothing we can do
            }
        }
    }

    /**
     * Checks whether USAGE_ACCESS is granted using AppOpsManager.
     * This is more reliable than attempting a dummy query.
     */
    fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    // =========================================================================
    // Private — permission check and data loading
    // =========================================================================

    private fun checkPermissionAndLoad() {
        _hasPermission.value = hasUsageStatsPermission()
        if (_hasPermission.value) {
            loadUsageStats()
        }
    }

    /**
     * Main data loading entry point. Queries UsageStatsManager for the
     * selected period, resolves app names and icons from PackageManager,
     * computes category breakdowns, daily totals, and the summary card.
     */
    private fun loadUsageStats() {
        viewModelScope.launch {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                try {
                    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
                        as? UsageStatsManager ?: return@withContext

                    val pm = context.packageManager
                    val now = System.currentTimeMillis()
                    val period = _selectedPeriod.value

                    // ---- 1. Determine time range for the selected period ----
                    val (startTime, endTime) = getTimeRange(period, now)

                    // ---- 2. Query per-app usage stats ----
                    val usageStatsList = usageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        startTime,
                        endTime
                    ) ?: emptyList()

                    // Aggregate by package name (multiple daily entries for multi-day periods)
                    val aggregated = mutableMapOf<String, Long>()
                    for (stat in usageStatsList) {
                        if (stat.totalTimeInForeground > 0) {
                            aggregated[stat.packageName] =
                                (aggregated[stat.packageName] ?: 0L) + stat.totalTimeInForeground
                        }
                    }

                    // Filter out our own package and system packages with trivial usage
                    val filteredAggregated = aggregated.filter { (pkg, time) ->
                        pkg != context.packageName && time > 60_000L // > 1 minute
                    }

                    // ---- 3. Build AppUsage list with icons & names ----
                    val appUsages = filteredAggregated.map { (pkg, timeMs) ->
                        val appName = try {
                            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                pm.getApplicationInfo(
                                    pkg,
                                    PackageManager.ApplicationInfoFlags.of(0)
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                pm.getApplicationInfo(pkg, 0)
                            }
                            pm.getApplicationLabel(appInfo).toString()
                        } catch (_: PackageManager.NameNotFoundException) {
                            pkg.substringAfterLast(".")
                                .replaceFirstChar { it.uppercaseChar() }
                        }

                        val icon = try {
                            pm.getApplicationIcon(pkg)
                        } catch (_: Exception) {
                            null
                        }

                        AppUsage(
                            packageName = pkg,
                            appName = appName,
                            usageTimeMs = timeMs,
                            icon = icon,
                            category = AppCategoryMapper.categorize(pkg)
                        )
                    }.sortedByDescending { it.usageTimeMs }

                    _topApps.value = appUsages.take(15)

                    // ---- 4. Build category breakdown ----
                    val totalTime = appUsages.sumOf { it.usageTimeMs }
                    val categoryMap = appUsages.groupBy { it.category }
                    val categories = UsageCategory.entries
                        .mapNotNull { cat ->
                            val apps = categoryMap[cat] ?: return@mapNotNull null
                            val catTime = apps.sumOf { it.usageTimeMs }
                            CategoryUsage(
                                category = cat,
                                totalTimeMs = catTime,
                                percentage = if (totalTime > 0) catTime.toFloat() / totalTime else 0f,
                                appCount = apps.size
                            )
                        }
                        .sortedByDescending { it.totalTimeMs }

                    _categoryBreakdown.value = categories

                    // ---- 5. Build daily usage for the week chart ----
                    val dailyData = buildDailyUsage(usageStatsManager, now)
                    _dailyUsage.value = dailyData

                    // ---- 6. Compute summary ----
                    val pickups = countPickups(usageStatsManager, startTime, endTime)
                    val avgSession = if (pickups > 0) totalTime / pickups else 0L

                    // Previous period comparison
                    val periodDuration = endTime - startTime
                    val prevStart = startTime - periodDuration
                    val prevEnd = startTime
                    val prevStats = usageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        prevStart,
                        prevEnd
                    ) ?: emptyList()
                    val prevTotal = prevStats.sumOf { it.totalTimeInForeground }
                    val changePercent = if (prevTotal > 0) {
                        ((totalTime - prevTotal) * 100 / prevTotal).toInt()
                    } else {
                        0
                    }

                    _usageSummary.value = UsageSummary(
                        totalScreenTimeMs = totalTime,
                        pickupCount = pickups,
                        avgSessionMs = avgSession,
                        changePercent = kotlin.math.abs(changePercent),
                        isIncrease = changePercent >= 0
                    )
                } catch (_: SecurityException) {
                    // Permission revoked while loading
                    _hasPermission.value = false
                } catch (_: Exception) {
                    // Unexpected error — leave current state as-is
                }
            }
            _isLoading.value = false
        }
    }

    // =========================================================================
    // Private — time range helpers
    // =========================================================================

    /**
     * Returns (startTimeMs, endTimeMs) for the given [UsagePeriod].
     */
    private fun getTimeRange(period: UsagePeriod, now: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        return when (period) {
            UsagePeriod.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now)
            }
            UsagePeriod.THIS_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now)
            }
            UsagePeriod.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now)
            }
        }
    }

    /**
     * Builds per-day usage data for the current week (Monday through Sunday).
     * Each day queries UsageStatsManager independently to get accurate daily totals.
     */
    private fun buildDailyUsage(
        usageStatsManager: UsageStatsManager,
        now: Long
    ): List<DailyUsage> {
        val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayCal = Calendar.getInstance()
        val todayDate = dateFormat.format(todayCal.time)

        // Find the Monday of the current week
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // If the calendar rolled to next week's Monday, go back a week
        if (cal.timeInMillis > now) {
            cal.add(Calendar.WEEK_OF_YEAR, -1)
        }

        return dayLabels.mapIndexed { index, label ->
            val dayStart = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = minOf(cal.timeInMillis, now)

            val dayDate = dateFormat.format(dayStart)

            val dayTotal = if (dayStart <= now) {
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    dayStart,
                    dayEnd
                ) ?: emptyList()

                stats.filter { it.packageName != context.packageName }
                    .sumOf { it.totalTimeInForeground }
            } else {
                0L
            }

            DailyUsage(
                dayOfWeek = label,
                date = dayDate,
                totalTimeMs = dayTotal,
                isToday = dayDate == todayDate
            )
        }
    }

    /**
     * Counts the number of "pickups" (screen unlock / interactive events)
     * by querying [UsageEvents] for SCREEN_INTERACTIVE events.
     *
     * On API < 30 (where SCREEN_INTERACTIVE is unavailable), we approximate
     * by counting ACTIVITY_RESUMED events with gaps > 30 seconds between them.
     */
    private fun countPickups(
        usageStatsManager: UsageStatsManager,
        startTime: Long,
        endTime: Long
    ): Int {
        return try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            var pickupCount = 0
            var lastEventTime = 0L
            val sessionGapMs = 30_000L // 30 seconds gap = new session

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Use SCREEN_INTERACTIVE on API 30+
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    @Suppress("NewApi")
                    if (event.eventType == UsageEvents.Event.SCREEN_INTERACTIVE) {
                        pickupCount++
                    }
                }
            } else {
                // Fallback: count resumed-activity gaps as distinct sessions
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                        if (event.timeStamp - lastEventTime > sessionGapMs) {
                            pickupCount++
                        }
                        lastEventTime = event.timeStamp
                    }
                }
            }

            // If we got zero from events, provide a reasonable estimate
            if (pickupCount == 0 && _topApps.value.isNotEmpty()) {
                pickupCount = 1 // At least one session if there's usage data
            }

            pickupCount
        } catch (_: Exception) {
            0
        }
    }
}
