package com.castor.app.ui

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.data.repository.ReminderRepository
import com.castor.core.ui.theme.TerminalColors
import com.castor.feature.reminders.engine.ReminderScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// =============================================================================
// Priority Model
// =============================================================================

/**
 * Reminder priority levels, styled as terminal flags.
 */
private enum class ReminderPriority(val label: String, val flag: String) {
    LOW("Low", "--priority=low"),
    MEDIUM("Medium", "--priority=medium"),
    HIGH("High", "--priority=high")
}

// =============================================================================
// Date Shortcut Model
// =============================================================================

/**
 * Quick date selection options for the reminder.
 */
private enum class DateShortcut(val label: String) {
    TODAY("Today"),
    TOMORROW("Tomorrow"),
    NEXT_WEEK("Next week")
}

// =============================================================================
// Public Composable
// =============================================================================

/**
 * Modal bottom sheet for quickly adding a new reminder from the home screen.
 *
 * Styled as a terminal form: `$ remind --new`. Contains:
 * - Text input for the reminder description
 * - Date shortcuts (Today / Tomorrow / Next week) + time selector
 * - Priority selector displayed as terminal flags
 * - Save (`$ crontab -e`) and Cancel (`:q!`) buttons
 *
 * On save the reminder is persisted via [ReminderRepository] and scheduled
 * via [ReminderScheduler].
 *
 * @param isVisible              Whether the sheet is currently shown.
 * @param onDismiss              Callback to hide the sheet.
 * @param reminderRepository     Repository for persisting the reminder.
 * @param reminderScheduler      Scheduler for setting the alarm. May be null if
 *                               the reminders module is not fully wired.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddReminderSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    reminderRepository: ReminderRepository,
    reminderScheduler: ReminderScheduler? = null
) {
    if (!isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // ---- Form state ----
    var description by rememberSaveable { mutableStateOf("") }
    var selectedDate by rememberSaveable { mutableIntStateOf(0) }  // 0=Today, 1=Tomorrow, 2=NextWeek
    var selectedHour by rememberSaveable { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by rememberSaveable { mutableIntStateOf(0) }
    var selectedPriority by rememberSaveable { mutableStateOf(ReminderPriority.MEDIUM) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TerminalColors.Background,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        dragHandle = {
            // Custom drag handle styled as terminal bar
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TerminalColors.Surface)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ----------------------------------------------------------------
            // Header: $ remind --new
            // ----------------------------------------------------------------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$ ",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Prompt
                    )
                )
                Text(
                    text = "remind --new",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Command
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TerminalColors.Timestamp,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SheetDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // ----------------------------------------------------------------
            // Description input
            // ----------------------------------------------------------------
            Text(
                text = "# description:",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Accent
                )
            )
            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(TerminalColors.Surface)
                    .padding(12.dp)
            ) {
                if (description.isEmpty()) {
                    Text(
                        text = "Enter reminder description...",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = TerminalColors.Timestamp
                        )
                    )
                }
                BasicTextField(
                    value = description,
                    onValueChange = { description = it },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = TerminalColors.Command
                    ),
                    cursorBrush = SolidColor(TerminalColors.Cursor),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = false,
                    maxLines = 3
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ----------------------------------------------------------------
            // Date shortcuts
            // ----------------------------------------------------------------
            Text(
                text = "# schedule:",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Accent
                )
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DateShortcut.entries.forEachIndexed { index, shortcut ->
                    DateChip(
                        label = shortcut.label,
                        isSelected = selectedDate == index,
                        onClick = { selectedDate = index }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Time selector row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(TerminalColors.Surface)
                    .clickable { showTimePicker = !showTimePicker }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = TerminalColors.Info,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Command
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "tap to change",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Timestamp
                    )
                )
            }

            // Inline time adjuster
            if (showTimePicker) {
                Spacer(modifier = Modifier.height(6.dp))
                TimeAdjuster(
                    hour = selectedHour,
                    minute = selectedMinute,
                    onHourChange = { selectedHour = it },
                    onMinuteChange = { selectedMinute = it }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ----------------------------------------------------------------
            // Priority selector
            // ----------------------------------------------------------------
            Text(
                text = "# priority:",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Accent
                )
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReminderPriority.entries.forEach { priority ->
                    PriorityChip(
                        priority = priority,
                        isSelected = selectedPriority == priority,
                        onClick = { selectedPriority = priority }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ----------------------------------------------------------------
            // Error message
            // ----------------------------------------------------------------
            if (errorMessage != null) {
                Text(
                    text = "ERR $errorMessage",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Error
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ----------------------------------------------------------------
            // Action buttons
            // ----------------------------------------------------------------
            SheetDivider()
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel button: :q!
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(TerminalColors.Error.copy(alpha = 0.12f))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = ":q!",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Error
                        )
                    )
                }

                // Save button: $ crontab -e
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (description.isNotBlank() && !isSaving)
                                TerminalColors.Success.copy(alpha = 0.15f)
                            else
                                TerminalColors.Surface
                        )
                        .clickable(enabled = description.isNotBlank() && !isSaving) {
                            scope.launch {
                                isSaving = true
                                errorMessage = null
                                try {
                                    val triggerMs = computeTriggerTime(
                                        dateIndex = selectedDate,
                                        hour = selectedHour,
                                        minute = selectedMinute
                                    )

                                    val prefixedDescription = when (selectedPriority) {
                                        ReminderPriority.HIGH -> "[!] $description"
                                        ReminderPriority.LOW -> description
                                        ReminderPriority.MEDIUM -> description
                                    }

                                    val id = reminderRepository.createReminder(
                                        description = prefixedDescription,
                                        triggerTimeMs = triggerMs
                                    )

                                    reminderScheduler?.scheduleReminder(id, triggerMs)

                                    // Reset form and dismiss
                                    description = ""
                                    selectedDate = 0
                                    selectedPriority = ReminderPriority.MEDIUM
                                    onDismiss()
                                } catch (e: Exception) {
                                    Log.e("QuickAddReminder", "Failed to save reminder", e)
                                    errorMessage = e.message ?: "Failed to save reminder"
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = TerminalColors.Success
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                tint = if (description.isNotBlank()) TerminalColors.Success else TerminalColors.Timestamp,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = "$ ",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TerminalColors.Prompt
                            )
                        )
                        Text(
                            text = "crontab -e",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (description.isNotBlank()) TerminalColors.Success else TerminalColors.Timestamp
                            )
                        )
                    }
                }
            }

            // Preview line showing what will be saved
            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                val dateLabel = DateShortcut.entries.getOrNull(selectedDate)?.label ?: "Today"
                val timeStr = String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute)
                Text(
                    text = "# $dateLabel @ $timeStr ${selectedPriority.flag} \"$description\"",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Timestamp
                    ),
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// =============================================================================
// Sub-components
// =============================================================================

@Composable
private fun DateChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) TerminalColors.Info.copy(alpha = 0.2f) else TerminalColors.Surface,
        label = "dateChipBg"
    )
    val textColor = if (isSelected) TerminalColors.Info else TerminalColors.Timestamp

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
        )
    }
}

@Composable
private fun PriorityChip(
    priority: ReminderPriority,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = when (priority) {
        ReminderPriority.LOW -> TerminalColors.Success
        ReminderPriority.MEDIUM -> TerminalColors.Warning
        ReminderPriority.HIGH -> TerminalColors.Error
    }
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) color.copy(alpha = 0.2f) else TerminalColors.Surface,
        label = "priorityChipBg"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = priority.flag,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) color else TerminalColors.Timestamp
            )
        )
    }
}

/**
 * Simple inline time adjuster with +/- buttons for hour and minute.
 */
@Composable
private fun TimeAdjuster(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hour adjuster
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "hour",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TerminalColors.Timestamp
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TimeButton(text = "-") { onHourChange((hour - 1 + 24) % 24) }
                Text(
                    text = String.format(Locale.US, "%02d", hour),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Command
                    )
                )
                TimeButton(text = "+") { onHourChange((hour + 1) % 24) }
            }
        }

        Text(
            text = ":",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Timestamp
            )
        )

        // Minute adjuster
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "min",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TerminalColors.Timestamp
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TimeButton(text = "-") { onMinuteChange((minute - 5 + 60) % 60) }
                Text(
                    text = String.format(Locale.US, "%02d", minute),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Command
                    )
                )
                TimeButton(text = "+") { onMinuteChange((minute + 5) % 60) }
            }
        }
    }
}

@Composable
private fun TimeButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(TerminalColors.Accent.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )
    }
}

@Composable
private fun SheetDivider() {
    HorizontalDivider(
        color = TerminalColors.Selection,
        thickness = 1.dp
    )
}

// =============================================================================
// Helpers
// =============================================================================

/**
 * Computes the trigger epoch millis based on the selected date shortcut index
 * and the chosen hour/minute.
 *
 * @param dateIndex 0 = today, 1 = tomorrow, 2 = next week
 * @param hour      24-hour hour value (0-23)
 * @param minute    Minute value (0-59)
 */
private fun computeTriggerTime(dateIndex: Int, hour: Int, minute: Int): Long {
    val cal = Calendar.getInstance()

    when (dateIndex) {
        1 -> cal.add(Calendar.DAY_OF_YEAR, 1)
        2 -> cal.add(Calendar.DAY_OF_YEAR, 7)
    }

    cal.set(Calendar.HOUR_OF_DAY, hour)
    cal.set(Calendar.MINUTE, minute)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)

    // If the computed time is in the past (today + earlier hour), push to tomorrow
    if (cal.timeInMillis <= System.currentTimeMillis() && dateIndex == 0) {
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }

    return cal.timeInMillis
}
