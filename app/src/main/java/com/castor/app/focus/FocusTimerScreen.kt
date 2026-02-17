package com.castor.app.focus

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.core.ui.theme.TerminalColors

/**
 * Full-screen Pomodoro Focus Timer.
 *
 * Features:
 * - Large countdown timer with circular progress indicator
 * - Session progress dots (4 sessions)
 * - Start/Pause/Stop/Skip controls
 * - Collapsible settings section
 * - Terminal-style session log
 * - Today's stats (sessions, total focus time)
 */
@Composable
fun FocusTimerScreen(
    onBack: () -> Unit,
    viewModel: FocusTimerViewModel = hiltViewModel()
) {
    val timerState by viewModel.timerState.collectAsState()
    val remainingSeconds by viewModel.remainingSeconds.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val completedSessions by viewModel.completedSessions.collectAsState()
    val totalFocusMinutesToday by viewModel.totalFocusMinutesToday.collectAsState()
    val sessionLog by viewModel.sessionLog.collectAsState()
    val isSettingsExpanded by viewModel.isSettingsExpanded.collectAsState()

    val focusDuration by viewModel.focusDuration.collectAsState()
    val shortBreakDuration by viewModel.shortBreakDuration.collectAsState()
    val longBreakDuration by viewModel.longBreakDuration.collectAsState()
    val sessionsUntilLongBreak by viewModel.sessionsUntilLongBreak.collectAsState()

    val logListState = rememberLazyListState()

    // Auto-scroll log to bottom when new entries appear
    LaunchedEffect(sessionLog.size) {
        if (sessionLog.isNotEmpty()) {
            logListState.animateScrollToItem(sessionLog.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
            .statusBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalColors.StatusBar)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TerminalColors.Prompt
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$ focus --pomodoro",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = TerminalColors.Prompt
                )
            )
        }

        // Main content
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Timer display with circular progress
            item {
                TimerDisplay(
                    timerState = timerState,
                    remainingSeconds = remainingSeconds,
                    totalSeconds = when (timerState) {
                        FocusTimerService.TimerState.FOCUS -> focusDuration * 60
                        FocusTimerService.TimerState.SHORT_BREAK -> shortBreakDuration * 60
                        FocusTimerService.TimerState.LONG_BREAK -> longBreakDuration * 60
                        FocusTimerService.TimerState.IDLE -> focusDuration * 60
                    },
                    viewModel = viewModel
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Session progress dots
            item {
                SessionProgressDots(
                    completedSessions = completedSessions,
                    totalSessions = sessionsUntilLongBreak,
                    isCurrentActive = timerState == FocusTimerService.TimerState.FOCUS
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Control buttons
            item {
                ControlButtons(
                    timerState = timerState,
                    isRunning = isRunning,
                    onStart = { viewModel.startTimer() },
                    onPause = { viewModel.pauseTimer() },
                    onResume = { viewModel.resumeTimer() },
                    onStop = { viewModel.stopTimer() },
                    onSkip = { viewModel.skipPhase() }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Today's stats
            item {
                TodaysStats(
                    completedSessions = completedSessions,
                    totalFocusMinutes = totalFocusMinutesToday,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Settings section
            item {
                SettingsSection(
                    isExpanded = isSettingsExpanded,
                    focusDuration = focusDuration,
                    shortBreakDuration = shortBreakDuration,
                    longBreakDuration = longBreakDuration,
                    sessionsUntilLongBreak = sessionsUntilLongBreak,
                    onToggleExpanded = { viewModel.toggleSettingsExpanded() },
                    onUpdateFocusDuration = { viewModel.updateFocusDuration(it) },
                    onUpdateShortBreakDuration = { viewModel.updateShortBreakDuration(it) },
                    onUpdateLongBreakDuration = { viewModel.updateLongBreakDuration(it) },
                    onUpdateSessionsUntilLongBreak = { viewModel.updateSessionsUntilLongBreak(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Terminal log section
            item {
                SessionLogSection(
                    sessionLog = sessionLog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TimerDisplay(
    timerState: FocusTimerService.TimerState,
    remainingSeconds: Int,
    totalSeconds: Int,
    viewModel: FocusTimerViewModel
) {
    val color = when (timerState) {
        FocusTimerService.TimerState.FOCUS -> TerminalColors.Accent
        FocusTimerService.TimerState.SHORT_BREAK -> TerminalColors.Success
        FocusTimerService.TimerState.LONG_BREAK -> TerminalColors.Info
        FocusTimerService.TimerState.IDLE -> TerminalColors.Output
    }

    val stateLabel = when (timerState) {
        FocusTimerService.TimerState.IDLE -> "[READY]"
        FocusTimerService.TimerState.FOCUS -> "[FOCUS]"
        FocusTimerService.TimerState.SHORT_BREAK -> "[SHORT BREAK]"
        FocusTimerService.TimerState.LONG_BREAK -> "[LONG BREAK]"
    }

    val progress = if (totalSeconds > 0) {
        1f - (remainingSeconds.toFloat() / totalSeconds.toFloat())
    } else {
        0f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(240.dp)
    ) {
        // Background circle
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.fillMaxSize(),
            color = TerminalColors.Surface,
            strokeWidth = 4.dp,
            trackColor = Color.Transparent
        )

        // Progress circle
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = color,
            strokeWidth = 4.dp,
            trackColor = Color.Transparent
        )

        // Timer text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = viewModel.formatTime(remainingSeconds),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stateLabel,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = color
                )
            )
        }
    }
}

@Composable
private fun SessionProgressDots(
    completedSessions: Int,
    totalSessions: Int,
    isCurrentActive: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSessions) { index ->
            val isCurrent = index == (completedSessions % totalSessions)
            val isCompleted = index < (completedSessions % totalSessions)

            SessionDot(
                isCompleted = isCompleted,
                isCurrent = isCurrent && isCurrentActive
            )
        }
    }
}

@Composable
private fun SessionDot(
    isCompleted: Boolean,
    isCurrent: Boolean
) {
    val scale by if (isCurrent) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
    } else {
        rememberInfiniteTransition(label = "static").animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1000)),
            label = "static"
        )
    }

    Box(
        modifier = Modifier
            .size(16.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                when {
                    isCompleted -> TerminalColors.Success
                    isCurrent -> TerminalColors.Accent
                    else -> TerminalColors.Surface
                }
            )
            .border(
                width = 1.dp,
                color = TerminalColors.Output,
                shape = CircleShape
            )
    )
}

@Composable
private fun ControlButtons(
    timerState: FocusTimerService.TimerState,
    isRunning: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSkip: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        if (timerState == FocusTimerService.TimerState.IDLE) {
            TerminalButton(
                text = "[START]",
                color = TerminalColors.Success,
                onClick = onStart
            )
        } else {
            if (isRunning) {
                TerminalButton(
                    text = "[PAUSE]",
                    color = TerminalColors.Warning,
                    onClick = onPause
                )
            } else {
                TerminalButton(
                    text = "[RESUME]",
                    color = TerminalColors.Success,
                    onClick = onResume
                )
            }

            TerminalButton(
                text = "[STOP]",
                color = TerminalColors.Error,
                onClick = onStop
            )

            TerminalButton(
                text = "[SKIP >>]",
                color = TerminalColors.Info,
                onClick = onSkip
            )
        }
    }
}

@Composable
private fun TerminalButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface)
            .border(
                width = 2.dp,
                color = color,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
    }
}

@Composable
private fun TodaysStats(
    completedSessions: Int,
    totalFocusMinutes: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface)
            .padding(16.dp)
    ) {
        Text(
            text = "# today's stats",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Accent
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        StatRow("sessions_completed", completedSessions.toString())
        StatRow("total_focus_time", "${totalFocusMinutes / 60}h ${totalFocusMinutes % 60}m")
        StatRow("current_streak", "$completedSessions sessions")
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label=",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Output
            )
        )
        Text(
            text = value,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Command
            )
        )
    }
}

@Composable
private fun SettingsSection(
    isExpanded: Boolean,
    focusDuration: Int,
    shortBreakDuration: Int,
    longBreakDuration: Int,
    sessionsUntilLongBreak: Int,
    onToggleExpanded: () -> Unit,
    onUpdateFocusDuration: (Int) -> Unit,
    onUpdateShortBreakDuration: (Int) -> Unit,
    onUpdateLongBreakDuration: (Int) -> Unit,
    onUpdateSessionsUntilLongBreak: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "# config",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Accent
                )
            )
            Text(
                text = if (isExpanded) "[-]" else "[+]",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Accent
                )
            )
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(12.dp))
            SettingRow("focus_duration", focusDuration, 15, 60, onUpdateFocusDuration)
            SettingRow("short_break", shortBreakDuration, 3, 15, onUpdateShortBreakDuration)
            SettingRow("long_break", longBreakDuration, 10, 30, onUpdateLongBreakDuration)
            SettingRow("sessions_until_long_break", sessionsUntilLongBreak, 2, 6, onUpdateSessionsUntilLongBreak)
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onUpdate: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label=",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Output
            ),
            modifier = Modifier.weight(1f)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { if (value > min) onUpdate(value - 1) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease",
                    tint = TerminalColors.Command,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = value.toString(),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Command
                ),
                modifier = Modifier.width(30.dp)
            )

            IconButton(
                onClick = { if (value < max) onUpdate(value + 1) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase",
                    tint = TerminalColors.Command,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SessionLogSection(
    sessionLog: List<FocusLogEntry>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface)
            .padding(16.dp)
    ) {
        Text(
            text = "# session log",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Accent
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (sessionLog.isEmpty()) {
            Text(
                text = "[No activity yet]",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Timestamp
                )
            )
        } else {
            sessionLog.takeLast(10).forEach { entry ->
                LogEntry(entry)
            }
        }
    }
}

@Composable
private fun LogEntry(entry: FocusLogEntry) {
    val color = when (entry.type) {
        "Prompt" -> TerminalColors.Prompt
        "Success" -> TerminalColors.Success
        "Info" -> TerminalColors.Info
        "Warning" -> TerminalColors.Warning
        "Error" -> TerminalColors.Error
        else -> TerminalColors.Output
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "[${entry.timestamp}] ",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Timestamp
            )
        )
        Text(
            text = entry.text,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = color
            )
        )
    }
}
