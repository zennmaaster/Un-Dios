package com.castor.app.habits

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.core.ui.theme.TerminalColors

/**
 * Compact habit tracker card for the HomeScreen agent grid.
 *
 * Shows:
 * - Header: "$ habits --today"
 * - Progress: "N/M completed" with progress bar
 * - Top 3 habits with [x]/[ ] status
 *
 * Tappable to navigate to the full HabitTrackerScreen.
 */
@Composable
fun HabitCard(
    viewModel: HabitViewModel = hiltViewModel()
) {
    val habits by viewModel.habits.collectAsState()

    val totalHabits = habits.size
    val completedToday = habits.count { it.isCompletedToday }
    val progress = if (totalHabits > 0) completedToday.toFloat() / totalHabits else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        // Header
        Text(
            text = "$ habits --today",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Prompt
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Progress
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$completedToday/$totalHabits",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Command
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp),
                color = TerminalColors.Success,
                trackColor = TerminalColors.Surface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Top 3 habits
        habits.take(3).forEach { habitWithStatus ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(
                    text = if (habitWithStatus.isCompletedToday) "[x]" else "[ ]",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (habitWithStatus.isCompletedToday) TerminalColors.Success else TerminalColors.Subtext
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = habitWithStatus.habit.name,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TerminalColors.Output
                    ),
                    maxLines = 1
                )
            }
        }

        // Show more indicator if there are more than 3 habits
        if (totalHabits > 3) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "... +${totalHabits - 3} more",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }
    }
}
