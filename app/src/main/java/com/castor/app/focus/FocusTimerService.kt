package com.castor.app.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.castor.app.MainActivity
import com.castor.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that manages the Pomodoro Focus Timer.
 *
 * Runs a countdown timer through FOCUS, SHORT_BREAK, and LONG_BREAK states.
 * Shows a persistent notification with current state and remaining time.
 *
 * State is exposed via companion object StateFlows so the UI can observe
 * timer progress without binding to the service.
 */
class FocusTimerService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "focus_timer"

        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_SKIP = "ACTION_SKIP"

        // Shared state flows for UI observation
        private val _timerState = MutableStateFlow(TimerState.IDLE)
        val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

        private val _remainingSeconds = MutableStateFlow(0)
        val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _completedSessions = MutableStateFlow(0)
        val completedSessions: StateFlow<Int> = _completedSessions.asStateFlow()

        private val _totalFocusMinutesToday = MutableStateFlow(0)
        val totalFocusMinutesToday: StateFlow<Int> = _totalFocusMinutesToday.asStateFlow()
    }

    enum class TimerState {
        IDLE,
        FOCUS,
        SHORT_BREAK,
        LONG_BREAK
    }

    private var timerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var notificationManager: NotificationManager

    // Default durations in seconds
    private var focusDuration = 25 * 60
    private var shortBreakDuration = 5 * 60
    private var longBreakDuration = 15 * 60
    private var sessionsUntilLongBreak = 4

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // Load settings from SharedPreferences
        loadSettings()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer()
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopTimer()
            ACTION_SKIP -> skipPhase()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun loadSettings() {
        val prefs = getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        focusDuration = prefs.getInt("focus_duration", 25) * 60
        shortBreakDuration = prefs.getInt("short_break_duration", 5) * 60
        longBreakDuration = prefs.getInt("long_break_duration", 15) * 60
        sessionsUntilLongBreak = prefs.getInt("sessions_until_long_break", 4)
    }

    private fun startTimer() {
        loadSettings() // Reload settings in case they changed
        _timerState.value = TimerState.FOCUS
        _remainingSeconds.value = focusDuration
        _isRunning.value = true

        startForeground(NOTIFICATION_ID, buildNotification())
        startCountdown()
    }

    private fun pauseTimer() {
        _isRunning.value = false
        timerJob?.cancel()
        updateNotification()
    }

    private fun resumeTimer() {
        _isRunning.value = true
        startCountdown()
    }

    private fun stopTimer() {
        _isRunning.value = false
        _timerState.value = TimerState.IDLE
        _remainingSeconds.value = 0
        timerJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun skipPhase() {
        timerJob?.cancel()
        completeCurrentPhase()
    }

    private fun startCountdown() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (_remainingSeconds.value > 0 && _isRunning.value) {
                delay(1000)
                _remainingSeconds.value -= 1
                updateNotification()
            }

            if (_remainingSeconds.value == 0 && _isRunning.value) {
                completeCurrentPhase()
            }
        }
    }

    private fun completeCurrentPhase() {
        when (_timerState.value) {
            TimerState.FOCUS -> {
                _completedSessions.value += 1
                _totalFocusMinutesToday.value += (focusDuration / 60)

                // Determine next break type
                val nextState = if (_completedSessions.value % sessionsUntilLongBreak == 0) {
                    TimerState.LONG_BREAK
                } else {
                    TimerState.SHORT_BREAK
                }

                _timerState.value = nextState
                _remainingSeconds.value = if (nextState == TimerState.LONG_BREAK) {
                    longBreakDuration
                } else {
                    shortBreakDuration
                }
            }
            TimerState.SHORT_BREAK, TimerState.LONG_BREAK -> {
                // After break, start new focus session
                _timerState.value = TimerState.FOCUS
                _remainingSeconds.value = focusDuration
            }
            TimerState.IDLE -> {
                // Should not happen
            }
        }

        updateNotification()
        startCountdown()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Pomodoro focus timer notifications"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val timeString = formatTime(_remainingSeconds.value)
        val stateLabel = when (_timerState.value) {
            TimerState.IDLE -> "Ready"
            TimerState.FOCUS -> "Focus"
            TimerState.SHORT_BREAK -> "Short Break"
            TimerState.LONG_BREAK -> "Long Break"
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeAction = if (_isRunning.value) {
            NotificationCompat.Action.Builder(
                0,
                "Pause",
                createActionPendingIntent(ACTION_PAUSE)
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                0,
                "Resume",
                createActionPendingIntent(ACTION_RESUME)
            ).build()
        }

        val stopAction = NotificationCompat.Action.Builder(
            0,
            "Stop",
            createActionPendingIntent(ACTION_STOP)
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$stateLabel • $timeString")
            .setContentText("Session ${_completedSessions.value + 1}")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, FocusTimerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%d:%02d".format(mins, secs)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
    }
}
