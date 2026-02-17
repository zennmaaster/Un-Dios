package com.castor.app.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Full-screen habit tracker styled as a terminal.
 *
 * Features:
 * - Date selector: horizontal scrollable row of the last 7 days
 * - Habit list: each habit shows checkbox, name, streak, and weekly progress
 * - Add habit button: opens a dialog to create a new habit
 * - Stats section: shows total tracked, completed today, best streak
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitTrackerScreen(
    onBack: () -> Unit,
    viewModel: HabitViewModel = hiltViewModel()
) {
    val habits by viewModel.habits.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val showCreateDialog by viewModel.showCreateDialog.collectAsState()

    val last7Days = remember { viewModel.getLast7Days() }
    val today = remember {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.format(Calendar.getInstance().time)
    }

    // Calculate stats
    val totalTracked = habits.size
    val completedToday = habits.count { it.isCompletedToday }
    val bestStreak = habits.maxOfOrNull { it.currentStreak } ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
    ) {
        // Top bar
        TopAppBar(
            title = {
                Text(
                    text = "$ habits --track",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Prompt
                    )
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TerminalColors.Command
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = TerminalColors.StatusBar
            ),
            modifier = Modifier.statusBarsPadding()
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Date selector
            item {
                Spacer(modifier = Modifier.height(8.dp))
                DateSelector(
                    dates = last7Days,
                    selectedDate = selectedDate,
                    today = today,
                    onDateSelected = { viewModel.selectDate(it) }
                )
            }

            // Section header
            item {
                Text(
                    text = "# tracked_habits",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Timestamp
                    ),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Habit list
            items(habits) { habitWithStatus ->
                HabitItem(
                    habitWithStatus = habitWithStatus,
                    onToggle = { viewModel.toggleHabit(habitWithStatus.habit.id) },
                    onArchive = { viewModel.archiveHabit(habitWithStatus.habit.id) }
                )
            }

            // Add habit button
            item {
                AddHabitButton(onClick = { viewModel.showCreateDialog() })
            }

            // Stats section
            item {
                StatsSection(
                    totalTracked = totalTracked,
                    completedToday = completedToday,
                    totalToday = totalTracked,
                    bestStreak = bestStreak
                )
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Create habit dialog
    if (showCreateDialog) {
        CreateHabitDialog(
            onDismiss = { viewModel.dismissCreateDialog() },
            onCreate = { name, targetDays ->
                viewModel.createHabit(name, targetDays)
            }
        )
    }
}

/**
 * Date selector showing the last 7 days in a horizontal row.
 */
@Composable
private fun DateSelector(
    dates: List<String>,
    selectedDate: String,
    today: String,
    onDateSelected: (String) -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val dayFormat = SimpleDateFormat("EEE", Locale.US)
    val dateNumberFormat = SimpleDateFormat("d", Locale.US)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        dates.forEach { dateStr ->
            val date = dateFormat.parse(dateStr) ?: return@forEach
            val cal = Calendar.getInstance().apply { time = date }
            val dayAbbr = dayFormat.format(cal.time)
            val dateNumber = dateNumberFormat.format(cal.time)
            val isSelected = dateStr == selectedDate
            val isToday = dateStr == today

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) TerminalColors.Accent else Color.Transparent
                    )
                    .clickable { onDateSelected(dateStr) }
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(
                    text = dayAbbr,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (isSelected) TerminalColors.Background else TerminalColors.Subtext
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateNumber,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) TerminalColors.Background else TerminalColors.Command
                    )
                )
                if (isToday) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isSelected) TerminalColors.Background else TerminalColors.Accent)
                    )
                }
            }
        }
    }
}

/**
 * Individual habit item showing checkbox, name, streak, and weekly progress.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitItem(
    habitWithStatus: HabitWithStatus,
    onToggle: () -> Unit,
    onArchive: () -> Unit
) {
    var showArchiveConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = TerminalColors.Surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Checkbox
                Text(
                    text = if (habitWithStatus.isCompletedToday) "[x]" else "[ ]",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (habitWithStatus.isCompletedToday) TerminalColors.Success else TerminalColors.Subtext
                    )
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Name and streak
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habitWithStatus.habit.name,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Command
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "streak=${habitWithStatus.currentStreak} days",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TerminalColors.Info
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Weekly progress
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                habitWithStatus.weeklyCompletions.forEach { completed ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (completed) TerminalColors.Success else TerminalColors.Background
                            )
                    )
                }
            }
        }
    }

    // Archive confirmation dialog
    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = {
                Text(
                    text = "Archive habit?",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Text(
                    text = "This will hide the habit from your list but keep all completion data.",
                    style = TextStyle(fontFamily = FontFamily.Monospace)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onArchive()
                    showArchiveConfirm = false
                }) {
                    Text(
                        text = "Archive",
                        style = TextStyle(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirm = false }) {
                    Text(
                        text = "Cancel",
                        style = TextStyle(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            containerColor = TerminalColors.Surface,
            textContentColor = TerminalColors.Command
        )
    }
}

/**
 * Button to add a new habit.
 */
@Composable
private fun AddHabitButton(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = TerminalColors.Surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "$ habit --new \"...\"",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = TerminalColors.Prompt
                )
            )
        }
    }
}

/**
 * Stats section showing total tracked, completed today, and best streak.
 */
@Composable
private fun StatsSection(
    totalTracked: Int,
    completedToday: Int,
    totalToday: Int,
    bestStreak: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "# stats",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Timestamp
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        StatRow(key = "total_tracked", value = totalTracked.toString())
        StatRow(key = "completed_today", value = "$completedToday/$totalToday")
        StatRow(key = "best_streak", value = "$bestStreak days")
    }
}

/**
 * Single stat row in terminal key=value style.
 */
@Composable
private fun StatRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "$key=",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Info
            )
        )
        Text(
            text = value,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Success
            )
        )
    }
}

/**
 * Dialog to create a new habit.
 */
@Composable
private fun CreateHabitDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Int) -> Unit
) {
    var habitName by remember { mutableStateOf("") }
    var targetDays by remember { mutableStateOf(7) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "$ habit --new",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Prompt
                )
            )
        },
        text = {
            Column {
                Text(
                    text = "Habit name:",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalColors.Subtext
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = habitName,
                    onValueChange = { habitName = it },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        color = TerminalColors.Command
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = TerminalColors.Background,
                        unfocusedContainerColor = TerminalColors.Background,
                        focusedIndicatorColor = TerminalColors.Accent,
                        unfocusedIndicatorColor = TerminalColors.Subtext
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Target days per week: $targetDays",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalColors.Subtext
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    (1..7).forEach { day ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (day <= targetDays) TerminalColors.Accent else TerminalColors.Surface
                                )
                                .clickable { targetDays = day },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day.toString(),
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (day <= targetDays) TerminalColors.Background else TerminalColors.Subtext
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (habitName.isNotBlank()) {
                    onCreate(habitName, targetDays)
                }
            }) {
                Text(
                    text = "[ENTER]",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        color = TerminalColors.Success
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        color = TerminalColors.Subtext
                    )
                )
            }
        },
        containerColor = TerminalColors.Surface,
        textContentColor = TerminalColors.Command
    )
}
