package com.castor.app.focus

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for the Focus Timer screen.
 *
 * Collects state from the [FocusTimerService] companion StateFlows
 * and provides actions to control the timer. Also manages user settings
 * (durations, sessions) stored in SharedPreferences.
 */
@HiltViewModel
class FocusTimerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Collect service state
    val timerState: StateFlow<FocusTimerService.TimerState> = FocusTimerService.timerState
    val remainingSeconds: StateFlow<Int> = FocusTimerService.remainingSeconds
    val isRunning: StateFlow<Boolean> = FocusTimerService.isRunning
    val completedSessions: StateFlow<Int> = FocusTimerService.completedSessions
    val totalFocusMinutesToday: StateFlow<Int> = FocusTimerService.totalFocusMinutesToday

    // Settings
    private val prefs = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)

    private val _focusDuration = MutableStateFlow(prefs.getInt("focus_duration", 25))
    val focusDuration: StateFlow<Int> = _focusDuration.asStateFlow()

    private val _shortBreakDuration = MutableStateFlow(prefs.getInt("short_break_duration", 5))
    val shortBreakDuration: StateFlow<Int> = _shortBreakDuration.asStateFlow()

    private val _longBreakDuration = MutableStateFlow(prefs.getInt("long_break_duration", 15))
    val longBreakDuration: StateFlow<Int> = _longBreakDuration.asStateFlow()

    private val _sessionsUntilLongBreak = MutableStateFlow(prefs.getInt("sessions_until_long_break", 4))
    val sessionsUntilLongBreak: StateFlow<Int> = _sessionsUntilLongBreak.asStateFlow()

    // Session log (in-memory for this session)
    private val _sessionLog = MutableStateFlow<List<FocusLogEntry>>(emptyList())
    val sessionLog: StateFlow<List<FocusLogEntry>> = _sessionLog.asStateFlow()

    // Settings expanded state
    private val _isSettingsExpanded = MutableStateFlow(false)
    val isSettingsExpanded: StateFlow<Boolean> = _isSettingsExpanded.asStateFlow()

    init {
        // Monitor timer state changes to update log
        // Note: This is a simplified approach; in production you'd want to persist the log
    }

    fun startTimer() {
        addLogEntry("$ focus --start --duration=${_focusDuration.value}m", "Prompt")
        addLogEntry("Focus session #${completedSessions.value + 1} started", "Info")

        val intent = Intent(context, FocusTimerService::class.java).apply {
            action = FocusTimerService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    fun pauseTimer() {
        addLogEntry("$ focus --pause", "Prompt")

        val intent = Intent(context, FocusTimerService::class.java).apply {
            action = FocusTimerService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeTimer() {
        addLogEntry("$ focus --resume", "Prompt")

        val intent = Intent(context, FocusTimerService::class.java).apply {
            action = FocusTimerService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun stopTimer() {
        addLogEntry("$ focus --stop", "Prompt")
        addLogEntry("Focus session terminated", "Warning")

        val intent = Intent(context, FocusTimerService::class.java).apply {
            action = FocusTimerService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun skipPhase() {
        addLogEntry("$ focus --skip", "Prompt")
        addLogEntry("Skipping to next phase...", "Info")

        val intent = Intent(context, FocusTimerService::class.java).apply {
            action = FocusTimerService.ACTION_SKIP
        }
        context.startService(intent)
    }

    fun updateFocusDuration(minutes: Int) {
        val clamped = minutes.coerceIn(15, 60)
        _focusDuration.value = clamped
        prefs.edit().putInt("focus_duration", clamped).apply()
    }

    fun updateShortBreakDuration(minutes: Int) {
        val clamped = minutes.coerceIn(3, 15)
        _shortBreakDuration.value = clamped
        prefs.edit().putInt("short_break_duration", clamped).apply()
    }

    fun updateLongBreakDuration(minutes: Int) {
        val clamped = minutes.coerceIn(10, 30)
        _longBreakDuration.value = clamped
        prefs.edit().putInt("long_break_duration", clamped).apply()
    }

    fun updateSessionsUntilLongBreak(sessions: Int) {
        val clamped = sessions.coerceIn(2, 6)
        _sessionsUntilLongBreak.value = clamped
        prefs.edit().putInt("sessions_until_long_break", clamped).apply()
    }

    fun toggleSettingsExpanded() {
        _isSettingsExpanded.value = !_isSettingsExpanded.value
    }

    private fun addLogEntry(text: String, type: String) {
        val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val entry = FocusLogEntry(timestamp, text, type)
        _sessionLog.value = (_sessionLog.value + entry).takeLast(50) // Keep last 50 entries
    }

    fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%d:%02d".format(mins, secs)
    }
}

data class FocusLogEntry(
    val timestamp: String,
    val text: String,
    val type: String // "Prompt", "Info", "Success", "Warning", "Error"
)
