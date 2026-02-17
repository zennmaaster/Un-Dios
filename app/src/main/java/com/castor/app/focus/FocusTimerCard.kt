package com.castor.app.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.core.ui.theme.TerminalColors

/**
 * Compact card showing Focus Timer status on the home screen.
 *
 * When idle: Shows "Ready to focus" with a [START] button
 * When active: Shows countdown timer + current state + session progress dots
 *
 * Tappable to navigate to the full FocusTimerScreen.
 */
@Composable
fun FocusTimerCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FocusTimerViewModel = hiltViewModel()
) {
    val timerState by viewModel.timerState.collectAsState()
    val remainingSeconds by viewModel.remainingSeconds.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val completedSessions by viewModel.completedSessions.collectAsState()
    val sessionsUntilLongBreak by viewModel.sessionsUntilLongBreak.collectAsState()

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Text(
                text = "$ focus --status",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Prompt
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (timerState == FocusTimerService.TimerState.IDLE) {
                // Idle state
                Column {
                    Text(
                        text = "Ready to focus",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = TerminalColors.Output
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TerminalColors.Surface)
                            .border(
                                width = 1.dp,
                                color = TerminalColors.Success,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "[START]",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TerminalColors.Success
                            )
                        )
                    }
                }
            } else {
                // Active state
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Timer and state
                        Column {
                            val color = getStateColor(timerState)
                            Text(
                                text = viewModel.formatTime(remainingSeconds),
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                            )
                            Text(
                                text = getStateLabel(timerState),
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = color
                                )
                            )
                        }

                        // Running indicator
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isRunning) TerminalColors.Success else TerminalColors.Warning
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Session progress dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(sessionsUntilLongBreak) { index ->
                            val isCompleted = index < (completedSessions % sessionsUntilLongBreak)
                            val isCurrent = index == (completedSessions % sessionsUntilLongBreak)

                            Box(
                                modifier = Modifier
                                    .size(12.dp)
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

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            text = "Session ${(completedSessions % sessionsUntilLongBreak) + 1}/$sessionsUntilLongBreak",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TerminalColors.Timestamp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun getStateColor(state: FocusTimerService.TimerState): Color {
    return when (state) {
        FocusTimerService.TimerState.FOCUS -> TerminalColors.Accent
        FocusTimerService.TimerState.SHORT_BREAK -> TerminalColors.Success
        FocusTimerService.TimerState.LONG_BREAK -> TerminalColors.Info
        FocusTimerService.TimerState.IDLE -> TerminalColors.Output
    }
}

private fun getStateLabel(state: FocusTimerService.TimerState): String {
    return when (state) {
        FocusTimerService.TimerState.IDLE -> "Ready"
        FocusTimerService.TimerState.FOCUS -> "[FOCUS]"
        FocusTimerService.TimerState.SHORT_BREAK -> "[SHORT BREAK]"
        FocusTimerService.TimerState.LONG_BREAK -> "[LONG BREAK]"
    }
}
