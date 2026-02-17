package com.castor.agent.orchestrator

import com.castor.core.common.model.AgentType
import com.castor.core.common.model.MediaSource
import com.castor.core.common.model.MessageSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.read
import kotlin.concurrent.write

// =========================================================================================
// Event hierarchy
// =========================================================================================

/**
 * All events that flow through the agent system. Each agent can emit events and
 * any other agent (or the UI layer) can subscribe to specific event types.
 */
sealed class AgentEvent {

    /** Timestamp when the event was created. */
    open val timestamp: Long = System.currentTimeMillis()

    // ---------------------------------------------------------------------------------
    // Messaging events
    // ---------------------------------------------------------------------------------

    /**
     * A new message was detected by the notification listener.
     *
     * @param sender    Display name of the message sender.
     * @param content   Message body text (may be truncated for notifications).
     * @param source    The messaging platform the message arrived on.
     * @param isUrgent  True if the message was flagged as high-priority or from a VIP contact.
     */
    data class NewMessage(
        val sender: String,
        val content: String,
        val source: MessageSource,
        val isUrgent: Boolean = false,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    // ---------------------------------------------------------------------------------
    // Media events
    // ---------------------------------------------------------------------------------

    /**
     * Media playback state has changed (play, pause, track change).
     *
     * @param isPlaying Whether media is currently playing.
     * @param title     Track / episode title.
     * @param artist    Artist or creator name (null for non-music media).
     * @param source    The media source app that triggered the change.
     */
    data class MediaStateChanged(
        val isPlaying: Boolean,
        val title: String?,
        val artist: String?,
        val source: MediaSource?,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    // ---------------------------------------------------------------------------------
    // Reminder events
    // ---------------------------------------------------------------------------------

    /**
     * A reminder alarm has fired and the user should be notified.
     *
     * @param reminderId  Unique identifier of the reminder in the database.
     * @param description Human-readable description of what the reminder is about.
     */
    data class ReminderFired(
        val reminderId: Long,
        val description: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    // ---------------------------------------------------------------------------------
    // Calendar events
    // ---------------------------------------------------------------------------------

    /**
     * A calendar event is approaching.
     *
     * @param eventTitle   Title of the calendar event.
     * @param minutesUntil Minutes until the event starts.
     */
    data class CalendarEventSoon(
        val eventTitle: String,
        val minutesUntil: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    // ---------------------------------------------------------------------------------
    // Model lifecycle events
    // ---------------------------------------------------------------------------------

    /**
     * An LLM model has been loaded and is ready for inference.
     *
     * @param modelName Human-readable name of the loaded model.
     */
    data class ModelLoaded(
        val modelName: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    /**
     * The LLM model has been unloaded; inference is no longer available.
     */
    data class ModelUnloaded(
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    // ---------------------------------------------------------------------------------
    // System events
    // ---------------------------------------------------------------------------------

    /**
     * A system-level event (battery, screen, bluetooth) detected by [SystemEventReceiver].
     *
     * @param type The specific system event that occurred.
     */
    data class SystemEvent(
        val type: SystemEventType,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    // ---------------------------------------------------------------------------------
    // Agent health events
    // ---------------------------------------------------------------------------------

    /**
     * Health status report from an individual agent.
     *
     * @param agentType    Which agent is reporting.
     * @param isHealthy    Whether the agent considers itself healthy.
     * @param errorMessage Description of the error if [isHealthy] is false.
     */
    data class AgentHealthCheck(
        val agentType: AgentType,
        val isHealthy: Boolean,
        val errorMessage: String? = null,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    // ---------------------------------------------------------------------------------
    // Proactive insight events
    // ---------------------------------------------------------------------------------

    /**
     * An agent wants to surface a proactive insight to the user (e.g. a suggestion
     * or contextual information that was not explicitly requested).
     *
     * @param agentType Which agent generated the insight.
     * @param message   Human-readable insight text.
     * @param priority  Importance level used for ordering and display decisions.
     */
    data class ProactiveInsight(
        val agentType: AgentType,
        val message: String,
        val priority: InsightPriority = InsightPriority.MEDIUM,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()
}

// =========================================================================================
// Supporting enums
// =========================================================================================

/**
 * Types of system-level events detected by the [SystemEventReceiver].
 */
enum class SystemEventType {
    BATTERY_LOW,
    CHARGING_STARTED,
    CHARGING_STOPPED,
    BLUETOOTH_CONNECTED,
    BLUETOOTH_DISCONNECTED,
    SCREEN_ON,
    SCREEN_OFF,
    BOOT_COMPLETED
}

/**
 * Priority levels for proactive insights, used by the [ProactiveEngine] to order
 * and filter insights displayed on the home screen.
 */
enum class InsightPriority {
    HIGH,
    MEDIUM,
    LOW
}

// =========================================================================================
// AgentEventBus
// =========================================================================================

/**
 * Singleton event bus for inter-agent communication.
 *
 * All agents emit events through [emit] and subscribe via [events] or [eventsOfType].
 * The bus uses a [MutableSharedFlow] with extra buffer capacity so that slow collectors
 * do not block fast emitters. A ring buffer of the last [HISTORY_CAPACITY] events is
 * kept for debugging.
 *
 * Usage:
 * ```kotlin
 * // Emit an event
 * eventBus.emit(AgentEvent.NewMessage(sender = "Alice", content = "Hi", source = WHATSAPP))
 *
 * // Subscribe to all events
 * eventBus.events.collect { event -> ... }
 *
 * // Subscribe to a specific event type
 * eventBus.eventsOfType<AgentEvent.NewMessage>().collect { msg -> ... }
 * ```
 */
@Singleton
class AgentEventBus @Inject constructor() {

    companion object {
        /** Maximum number of events retained in the history ring buffer. */
        private const val HISTORY_CAPACITY = 100
    }

    // -------------------------------------------------------------------------------------
    // Internal flow
    // -------------------------------------------------------------------------------------

    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 64)

    // -------------------------------------------------------------------------------------
    // Event history ring buffer (thread-safe)
    // -------------------------------------------------------------------------------------

    private val _history = ArrayDeque<AgentEvent>(HISTORY_CAPACITY)
    private val historyLock = ReentrantReadWriteLock()

    // -------------------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------------------

    /**
     * Stream of all events. Collectors receive events emitted after they begin collecting.
     */
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    /**
     * Emit an event onto the bus. This is a suspend function but will not suspend under
     * normal conditions because the flow has a 64-slot extra buffer.
     */
    suspend fun emit(event: AgentEvent) {
        addToHistory(event)
        _events.emit(event)
    }

    /**
     * Convenience: filter the event stream to only events of type [T].
     *
     * Example:
     * ```kotlin
     * eventBus.eventsOfType<AgentEvent.NewMessage>().collect { msg ->
     *     println("New message from ${msg.sender}")
     * }
     * ```
     */
    inline fun <reified T : AgentEvent> eventsOfType(): Flow<T> {
        return events.filterIsInstance<T>()
    }

    /**
     * Returns a snapshot of the event history (most recent last).
     * The list is a copy; mutations do not affect the internal buffer.
     */
    fun getHistory(): List<AgentEvent> {
        return historyLock.read {
            _history.toList()
        }
    }

    /**
     * Returns the number of events currently in the history buffer.
     */
    fun historySize(): Int {
        return historyLock.read {
            _history.size
        }
    }

    /**
     * Clears the event history. Useful during testing or when resetting state.
     */
    fun clearHistory() {
        historyLock.write {
            _history.clear()
        }
    }

    // -------------------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------------------

    private fun addToHistory(event: AgentEvent) {
        historyLock.write {
            if (_history.size >= HISTORY_CAPACITY) {
                _history.removeFirst()
            }
            _history.addLast(event)
        }
    }
}
