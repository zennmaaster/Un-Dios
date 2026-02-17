package com.castor.agent.orchestrator

import com.castor.core.common.model.AgentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

// =========================================================================================
// Data models
// =========================================================================================

/**
 * Current health status of a single agent.
 *
 * @param agentType     Which agent this status describes.
 * @param isHealthy     True if the agent has responded within the heartbeat window.
 * @param lastHeartbeat Epoch millis of the last successful heartbeat, or 0 if never received.
 * @param errorCount    Cumulative number of errors recorded for this agent.
 * @param lastError     Description of the most recent error, or null if no errors.
 * @param uptimeMs      Milliseconds since the agent was first registered with the monitor.
 * @param needsRestart  True if the error count exceeds the restart threshold.
 */
data class AgentStatus(
    val agentType: AgentType,
    val isHealthy: Boolean = true,
    val lastHeartbeat: Long = 0L,
    val errorCount: Int = 0,
    val lastError: String? = null,
    val uptimeMs: Long = 0L,
    val needsRestart: Boolean = false
)

// =========================================================================================
// AgentHealthMonitor
// =========================================================================================

/**
 * Tracks the health of all registered agents via heartbeats and error reporting.
 *
 * Each agent is expected to call [recordHeartbeat] after successful operations.
 * If an agent fails to heartbeat within [HEARTBEAT_TIMEOUT_MS] of its last operation,
 * it is marked unhealthy. Errors are tracked with exponential backoff so that a flurry
 * of identical failures does not overwhelm the system.
 *
 * The monitor exposes:
 * - [agentStatuses] -- a [StateFlow] of all agent statuses for the UI.
 * - [isSystemHealthy] -- a [StateFlow] that is true only when every agent is healthy.
 * - [getStatusReport] -- a terminal-styled string for the command bar.
 *
 * Auto-recovery: when an agent's error count exceeds [RESTART_THRESHOLD], the
 * [AgentStatus.needsRestart] flag is set. The owning service can observe this and
 * trigger a restart.
 */
@Singleton
class AgentHealthMonitor @Inject constructor() {

    companion object {
        /** An agent is considered unhealthy if no heartbeat arrives within this window. */
        private const val HEARTBEAT_TIMEOUT_MS = 60_000L

        /** Error count at which an agent is flagged for restart. */
        private const val RESTART_THRESHOLD = 5

        /** Minimum interval between error recordings for the same agent (backoff base). */
        private const val ERROR_BACKOFF_BASE_MS = 2_000L

        /** Maximum backoff interval for error recording. */
        private const val ERROR_BACKOFF_MAX_MS = 60_000L

        /** Interval at which the monitor checks for stale heartbeats. */
        const val HEALTH_CHECK_INTERVAL_MS = 30_000L

        /** Monitored agent types. ROUTER is excluded since it is not a long-lived agent. */
        val MONITORED_AGENTS = listOf(
            AgentType.MESSAGING,
            AgentType.MEDIA,
            AgentType.REMINDER,
            AgentType.GENERAL
        )
    }

    // -------------------------------------------------------------------------------------
    // Internal state
    // -------------------------------------------------------------------------------------

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Per-agent mutable tracking data. */
    private data class AgentTracker(
        var lastHeartbeat: Long = 0L,
        var errorCount: Int = 0,
        var lastError: String? = null,
        var lastErrorTime: Long = 0L,
        var registeredAt: Long = System.currentTimeMillis(),
        var hadActivity: Boolean = false
    )

    private val trackers = ConcurrentHashMap<AgentType, AgentTracker>()

    // -------------------------------------------------------------------------------------
    // Public state flows
    // -------------------------------------------------------------------------------------

    private val _agentStatuses = MutableStateFlow<Map<AgentType, AgentStatus>>(emptyMap())

    /**
     * Observable map of agent statuses. Updated whenever a heartbeat or error is
     * recorded, and periodically by the health check loop.
     */
    val agentStatuses: StateFlow<Map<AgentType, AgentStatus>> = _agentStatuses.asStateFlow()

    /**
     * True when every monitored agent is healthy. Useful for a global health indicator
     * in the status bar.
     */
    val isSystemHealthy: StateFlow<Boolean> = _agentStatuses
        .map { statuses ->
            if (statuses.isEmpty()) true
            else statuses.values.all { it.isHealthy }
        }
        .stateIn(scope, SharingStarted.Eagerly, true)

    // -------------------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------------------

    init {
        // Pre-register all monitored agents with default healthy status
        for (agentType in MONITORED_AGENTS) {
            trackers[agentType] = AgentTracker()
        }
        publishStatuses()
    }

    // -------------------------------------------------------------------------------------
    // Public API -- heartbeats and errors
    // -------------------------------------------------------------------------------------

    /**
     * Record a successful heartbeat from an agent. This resets the agent's health
     * timer and marks it as healthy.
     *
     * Agents should call this after every successful dispatch or periodic check.
     */
    fun recordHeartbeat(agentType: AgentType) {
        val tracker = trackers.getOrPut(agentType) { AgentTracker() }
        tracker.lastHeartbeat = System.currentTimeMillis()
        tracker.hadActivity = true
        publishStatuses()
    }

    /**
     * Record an error for an agent. Applies exponential backoff so that rapid
     * repeated errors do not flood the system.
     *
     * @param agentType The agent that encountered the error.
     * @param error     Description of the error.
     */
    fun recordError(agentType: AgentType, error: String) {
        val tracker = trackers.getOrPut(agentType) { AgentTracker() }
        val now = System.currentTimeMillis()

        // Exponential backoff: only record if enough time has passed
        val backoffMs = calculateBackoff(tracker.errorCount)
        if (now - tracker.lastErrorTime < backoffMs) {
            return // Too soon since last error -- skip
        }

        tracker.errorCount++
        tracker.lastError = error
        tracker.lastErrorTime = now
        publishStatuses()
    }

    /**
     * Reset the error count and restart flag for an agent, typically after a
     * successful restart.
     */
    fun resetAgent(agentType: AgentType) {
        val tracker = trackers.getOrPut(agentType) { AgentTracker() }
        tracker.errorCount = 0
        tracker.lastError = null
        tracker.lastErrorTime = 0L
        tracker.registeredAt = System.currentTimeMillis()
        tracker.hadActivity = false
        publishStatuses()
    }

    // -------------------------------------------------------------------------------------
    // Health check loop
    // -------------------------------------------------------------------------------------

    /**
     * Run a single health check pass. Marks agents as unhealthy if their last
     * heartbeat is stale (only if they have had at least one operation).
     *
     * This is designed to be called periodically from [AgentService].
     */
    fun performHealthCheck() {
        val now = System.currentTimeMillis()
        for ((_, tracker) in trackers) {
            // Only mark unhealthy if the agent has had activity and then gone silent
            if (tracker.hadActivity && tracker.lastHeartbeat > 0) {
                val elapsed = now - tracker.lastHeartbeat
                if (elapsed > HEARTBEAT_TIMEOUT_MS) {
                    // Heartbeat is stale -- the agent may be stuck
                    // We do not increment errorCount here; this is purely a staleness flag
                }
            }
        }
        publishStatuses()
    }

    // -------------------------------------------------------------------------------------
    // Status report
    // -------------------------------------------------------------------------------------

    /**
     * Generate a terminal-styled health report suitable for display in the command bar
     * or debug overlay.
     *
     * Example output:
     * ```
     * === Un-Dios Agent Health ===
     * MESSAGING   [OK]  last: 12:34:05  errors: 0
     * MEDIA       [OK]  last: 12:34:02  errors: 0
     * REMINDER    [!!]  last: 12:33:10  errors: 3  (flagged for restart)
     * GENERAL     [OK]  last: 12:34:04  errors: 0
     * ---
     * System: DEGRADED
     * ```
     */
    fun getStatusReport(): String {
        val statuses = _agentStatuses.value
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        return buildString {
            appendLine("=== Un-Dios Agent Health ===")

            for (agentType in MONITORED_AGENTS) {
                val status = statuses[agentType]
                if (status == null) {
                    appendLine("${agentType.name.padEnd(12)} [--]  not registered")
                    continue
                }

                val healthTag = when {
                    status.needsRestart -> "[!!]"
                    status.isHealthy -> "[OK]"
                    else -> "[!!]"
                }

                val lastTime = if (status.lastHeartbeat > 0) {
                    timeFormat.format(Date(status.lastHeartbeat))
                } else {
                    "never"
                }

                val uptimeStr = formatDuration(status.uptimeMs)

                append("${agentType.name.padEnd(12)} $healthTag")
                append("  last: $lastTime")
                append("  errors: ${status.errorCount}")
                if (status.needsRestart) {
                    append("  (flagged for restart)")
                }
                if (status.lastError != null) {
                    append("  err: ${status.lastError}")
                }
                appendLine()
            }

            appendLine("---")
            val systemStatus = if (isSystemHealthy.value) "HEALTHY" else "DEGRADED"
            appendLine("System: $systemStatus")
            val hasRestart = statuses.values.any { it.needsRestart }
            if (hasRestart) {
                val restartAgents = statuses.values
                    .filter { it.needsRestart }
                    .joinToString(", ") { it.agentType.name }
                appendLine("Restart needed: $restartAgents")
            }
        }.trimEnd()
    }

    // -------------------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------------------

    /**
     * Snapshot all trackers into immutable [AgentStatus] objects and publish to the
     * state flow.
     */
    private fun publishStatuses() {
        val now = System.currentTimeMillis()
        val snapshot = trackers.entries.associate { (agentType, tracker) ->
            val isHealthy = when {
                // Agent has never had activity -- consider it healthy (not yet used)
                !tracker.hadActivity -> true
                // Agent has activity but no heartbeat yet -- healthy for now
                tracker.lastHeartbeat == 0L -> true
                // Check if heartbeat is stale
                else -> (now - tracker.lastHeartbeat) <= HEARTBEAT_TIMEOUT_MS
            }

            val needsRestart = tracker.errorCount >= RESTART_THRESHOLD

            agentType to AgentStatus(
                agentType = agentType,
                isHealthy = isHealthy && !needsRestart,
                lastHeartbeat = tracker.lastHeartbeat,
                errorCount = tracker.errorCount,
                lastError = tracker.lastError,
                uptimeMs = now - tracker.registeredAt,
                needsRestart = needsRestart
            )
        }
        _agentStatuses.value = snapshot
    }

    /**
     * Calculate exponential backoff interval based on the current error count.
     * Doubles the base interval for each error, capped at [ERROR_BACKOFF_MAX_MS].
     */
    private fun calculateBackoff(errorCount: Int): Long {
        if (errorCount <= 0) return 0L
        val backoff = ERROR_BACKOFF_BASE_MS * (1L shl minOf(errorCount - 1, 15))
        return minOf(backoff, ERROR_BACKOFF_MAX_MS)
    }

    /**
     * Format a millisecond duration into a human-readable string (e.g. "2h 15m").
     */
    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}
