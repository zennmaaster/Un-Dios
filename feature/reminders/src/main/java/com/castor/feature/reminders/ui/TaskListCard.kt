package com.castor.feature.reminders.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.theme.TerminalColors
import com.castor.feature.reminders.google.GoogleTask
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Terminal-styled card displaying Google Tasks.
 *
 * Features:
 * - `$ tasks --pending` terminal header
 * - Checkbox items with task title and due date
 * - Tap checkbox to mark complete
 * - Inline "add task" input at the bottom
 * - Monospace font throughout, dark terminal background
 *
 * @param tasks List of active (incomplete) tasks
 * @param isLoading Whether tasks are currently syncing
 * @param errorMessage Optional error message to display
 * @param onRefresh Callback to trigger a tasks sync
 * @param onCompleteTask Callback when a task's checkbox is tapped (taskId)
 * @param onAddTask Callback when the user submits a new task title
 * @param onTaskClick Callback when a task row is tapped
 * @param modifier Modifier for the root composable
 */
@Composable
fun TaskListCard(
    tasks: List<GoogleTask>,
    isLoading: Boolean,
    errorMessage: String? = null,
    onRefresh: () -> Unit = {},
    onCompleteTask: (GoogleTask) -> Unit = {},
    onAddTask: (String) -> Unit = {},
    onTaskClick: (GoogleTask) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val monoStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        color = TerminalColors.Command
    )

    var isExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Background)
    ) {
        // ---- Header ----
        TasksHeader(
            taskCount = tasks.size,
            isLoading = isLoading,
            isExpanded = isExpanded,
            onToggleExpanded = { isExpanded = !isExpanded },
            onRefresh = onRefresh,
            monoStyle = monoStyle
        )

        // ---- Content ----
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Error state
                if (errorMessage != null) {
                    TasksErrorOutput(errorMessage, monoStyle)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Task list or empty state
                if (tasks.isEmpty() && !isLoading) {
                    EmptyTaskList(monoStyle)
                } else {
                    tasks.forEachIndexed { index, task ->
                        TaskRow(
                            task = task,
                            monoStyle = monoStyle,
                            onComplete = { onCompleteTask(task) },
                            onClick = { onTaskClick(task) }
                        )
                        if (index < tasks.lastIndex) {
                            HorizontalDivider(
                                color = TerminalColors.Surface,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }

                // Loading indicator
                if (isLoading && tasks.isEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = TerminalColors.Accent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "syncing tasks...",
                            style = monoStyle.copy(
                                fontSize = 11.sp,
                                color = TerminalColors.Accent
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Inline add task
                HorizontalDivider(
                    color = TerminalColors.Surface,
                    thickness = 0.5.dp
                )
                Spacer(modifier = Modifier.height(6.dp))
                InlineAddTask(
                    monoStyle = monoStyle,
                    onAddTask = onAddTask
                )
            }
        }
    }
}

// =============================================================================
// Header
// =============================================================================

@Composable
private fun TasksHeader(
    taskCount: Int,
    isLoading: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRefresh: () -> Unit,
    monoStyle: TextStyle
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalColors.Surface)
            .clickable(onClick = onToggleExpanded)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Checklist,
                contentDescription = null,
                tint = TerminalColors.Success,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            // Terminal prompt
            Text(
                text = "$ ",
                style = monoStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Prompt
                )
            )
            Text(
                text = "tasks --pending",
                style = monoStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Command
                )
            )

            if (taskCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(
                            TerminalColors.Accent.copy(alpha = 0.15f),
                            RoundedCornerShape(3.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "$taskCount",
                        style = monoStyle.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Accent
                        )
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color = TerminalColors.Accent
                )
            } else {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh tasks",
                        tint = TerminalColors.Timestamp,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// =============================================================================
// Task Row
// =============================================================================

@Composable
private fun TaskRow(
    task: GoogleTask,
    monoStyle: TextStyle,
    onComplete: () -> Unit,
    onClick: () -> Unit
) {
    val isCompleted = task.isCompleted
    val checkboxColor by animateColorAsState(
        targetValue = if (isCompleted) TerminalColors.Success else TerminalColors.Subtext,
        animationSpec = tween(200),
        label = "checkboxColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Checkbox
        IconButton(
            onClick = onComplete,
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                imageVector = if (isCompleted) {
                    Icons.Default.CheckBox
                } else {
                    Icons.Default.CheckBoxOutlineBlank
                },
                contentDescription = if (isCompleted) "Completed" else "Mark complete",
                tint = checkboxColor,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Task details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title.ifBlank { "(Untitled task)" },
                style = monoStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isCompleted) {
                        TerminalColors.Subtext
                    } else {
                        TerminalColors.Command
                    },
                    textDecoration = if (isCompleted) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    }
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Notes preview
            if (!task.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = task.notes,
                    style = monoStyle.copy(
                        fontSize = 10.sp,
                        color = TerminalColors.Timestamp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Due date
            val dueLabel = formatDueDate(task.due)
            if (dueLabel != null) {
                Spacer(modifier = Modifier.height(2.dp))
                val isOverdue = isTaskOverdue(task.due)
                Text(
                    text = "due: $dueLabel",
                    style = monoStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isOverdue) {
                            TerminalColors.Error
                        } else {
                            TerminalColors.Warning
                        }
                    )
                )
            }
        }
    }
}

// =============================================================================
// Inline Add Task
// =============================================================================

@Composable
private fun InlineAddTask(
    monoStyle: TextStyle,
    onAddTask: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var isInputVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    if (isInputVisible) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = TerminalColors.Prompt,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))

            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = monoStyle.copy(
                    fontSize = 12.sp,
                    color = TerminalColors.Command
                ),
                cursorBrush = SolidColor(TerminalColors.Cursor),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (inputText.isNotBlank()) {
                            onAddTask(inputText.trim())
                            inputText = ""
                            isInputVisible = false
                        }
                    }
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (inputText.isEmpty()) {
                            Text(
                                text = "task title...",
                                style = monoStyle.copy(
                                    fontSize = 12.sp,
                                    color = TerminalColors.Subtext
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Submit button
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onAddTask(inputText.trim())
                        inputText = ""
                        isInputVisible = false
                    }
                },
                modifier = Modifier.size(24.dp)
            ) {
                Text(
                    text = "RET",
                    style = monoStyle.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Success
                    )
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .clickable { isInputVisible = true }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add task",
                tint = TerminalColors.Subtext,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "add task",
                style = monoStyle.copy(
                    fontSize = 11.sp,
                    color = TerminalColors.Subtext
                )
            )
        }
    }
}

// =============================================================================
// Empty State
// =============================================================================

@Composable
private fun EmptyTaskList(monoStyle: TextStyle) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Checklist,
            contentDescription = null,
            tint = TerminalColors.Subtext,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "# No pending tasks",
            style = monoStyle.copy(
                fontSize = 12.sp,
                color = TerminalColors.Subtext
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "All tasks completed. Process exited with code 0.",
            style = monoStyle.copy(
                fontSize = 10.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

// =============================================================================
// Error Output
// =============================================================================

@Composable
private fun TasksErrorOutput(message: String, monoStyle: TextStyle) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "ERR ",
            style = monoStyle.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Error
            )
        )
        Text(
            text = message,
            style = monoStyle.copy(
                fontSize = 10.sp,
                color = TerminalColors.Error
            )
        )
    }
}

// =============================================================================
// Formatting Helpers
// =============================================================================

/**
 * Formats an RFC3339 due date into a human-readable label.
 * Returns null if the input is null or unparseable.
 */
private fun formatDueDate(rfc3339: String?): String? {
    if (rfc3339 == null) return null

    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val date = parser.parse(rfc3339) ?: return null
        val displayFormat = SimpleDateFormat("MMM dd", Locale.US)
        displayFormat.format(date)
    } catch (_: Exception) {
        try {
            // Try date-only format
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = parser.parse(rfc3339) ?: return null
            val displayFormat = SimpleDateFormat("MMM dd", Locale.US)
            displayFormat.format(date)
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Checks if a task's due date is in the past.
 */
private fun isTaskOverdue(rfc3339: String?): Boolean {
    if (rfc3339 == null) return false

    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val date = parser.parse(rfc3339)
        date != null && date.before(Date())
    } catch (_: Exception) {
        false
    }
}
