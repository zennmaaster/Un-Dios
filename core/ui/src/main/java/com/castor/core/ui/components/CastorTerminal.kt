package com.castor.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.theme.TerminalColors
import kotlinx.coroutines.delay

/**
 * Represents a single command/response pair in the terminal history.
 */
data class TerminalEntry(
    val input: String,
    val output: String,
    val timestamp: Long,
    val isError: Boolean = false,
    val privacyTier: String = "LOCAL"
)

/**
 * Full terminal-style command interface — the heart of the Castor Ubuntu experience.
 *
 * Features:
 * - Dark background with green/amber text (classic terminal aesthetic)
 * - Monospace font throughout
 * - Prompt: `castor@local $` or `castor@cloud $`
 * - Command history scrollback via LazyColumn
 * - Color-coded entries: green commands, white/gray output, red errors
 * - Blinking cursor animation on the input line
 * - Auto-scroll to bottom on new entries
 * - Expandable/collapsible between single-line and full view
 * - Privacy tier badge next to each response
 * - Optional typing animation for LLM responses
 *
 * @param commandHistory The full list of terminal entries to display
 * @param onSubmit Callback when the user submits a command
 * @param isProcessing Whether the system is currently processing a command
 * @param isExpanded Whether the terminal is in expanded (full) or collapsed (single-line) mode
 * @param onToggleExpanded Callback to toggle expanded/collapsed state
 * @param showTypingAnimation Whether to show a character-by-character typing effect on the latest response
 * @param onInputChanged Callback when input text changes
 * @param voiceButton Optional composable for voice input button
 * @param modifier Modifier for the root composable
 */
@Composable
fun CastorTerminal(
    commandHistory: List<TerminalEntry>,
    onSubmit: (String) -> Unit,
    isProcessing: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    showTypingAnimation: Boolean = false,
    onInputChanged: (String) -> Unit = {},
    voiceButton: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val monoStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        color = TerminalColors.Command
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Background)
    ) {
        // ---- Title bar ----
        TerminalTitleBar(
            isExpanded = isExpanded,
            onToggleExpanded = onToggleExpanded,
            entryCount = commandHistory.size
        )

        // ---- Expanded: History + Input ----
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(250)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut()
        ) {
            Column {
                // Command history scrollback
                TerminalHistory(
                    entries = commandHistory,
                    isProcessing = isProcessing,
                    showTypingAnimation = showTypingAnimation,
                    monoStyle = monoStyle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 120.dp, max = 360.dp)
                )

                // Divider
                HorizontalDivider(
                    color = TerminalColors.Surface,
                    thickness = 1.dp
                )

                // Input line
                TerminalInputLine(
                    onSubmit = onSubmit,
                    onInputChanged = onInputChanged,
                    isProcessing = isProcessing,
                    monoStyle = monoStyle,
                    voiceButton = voiceButton,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ---- Collapsed: Single-line input ----
        AnimatedVisibility(
            visible = !isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            TerminalInputLine(
                onSubmit = onSubmit,
                onInputChanged = onInputChanged,
                isProcessing = isProcessing,
                monoStyle = monoStyle,
                voiceButton = voiceButton,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ============================================================================
// Terminal Title Bar
// ============================================================================

/**
 * The title bar of the terminal window, styled like a Linux terminal emulator header.
 * Shows three dot indicators (red/yellow/green), a title, and an expand/collapse toggle.
 */
@Composable
private fun TerminalTitleBar(
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    entryCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalColors.Surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleExpanded
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Three dots (window controls aesthetic)
            WindowDot(color = TerminalColors.Error)
            Spacer(modifier = Modifier.width(5.dp))
            WindowDot(color = TerminalColors.Warning)
            Spacer(modifier = Modifier.width(5.dp))
            WindowDot(color = TerminalColors.Success)
            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "un-dios ~ terminal",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TerminalColors.Timestamp
                )
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (entryCount > 0) {
                Text(
                    text = "$entryCount cmd${if (entryCount != 1) "s" else ""}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Subtext
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse terminal" else "Expand terminal",
                tint = TerminalColors.Timestamp,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * A small circular dot used in the terminal title bar, imitating Linux window controls.
 */
@Composable
private fun WindowDot(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.8f))
    )
}

// ============================================================================
// Terminal History (Scrollback)
// ============================================================================

/**
 * Scrollable list of previous command/response entries.
 * Auto-scrolls to the bottom when new entries appear.
 */
@Composable
private fun TerminalHistory(
    entries: List<TerminalEntry>,
    isProcessing: Boolean,
    showTypingAnimation: Boolean,
    monoStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to the bottom when entries change
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Welcome message as the first entry
        if (entries.isEmpty()) {
            item {
                WelcomeMessage(monoStyle = monoStyle)
            }
        }

        items(
            items = entries,
            key = { "${it.timestamp}_${it.input.hashCode()}" }
        ) { entry ->
            val isLatest = entry == entries.lastOrNull()
            TerminalEntryRow(
                entry = entry,
                monoStyle = monoStyle,
                showTypingAnimation = showTypingAnimation && isLatest && !isProcessing
            )
        }

        // Processing indicator
        if (isProcessing) {
            item {
                ProcessingIndicator(monoStyle = monoStyle)
            }
        }
    }
}

/**
 * Welcome text shown when the terminal has no history yet.
 */
@Composable
private fun WelcomeMessage(monoStyle: TextStyle) {
    Column {
        Text(
            text = "Un-Dios Terminal v0.1.0",
            style = monoStyle.copy(
                color = TerminalColors.Accent,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Privacy-first AI assistant. All processing local by default.",
            style = monoStyle.copy(
                color = TerminalColors.Timestamp,
                fontSize = 11.sp
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Type a command or ask anything. Use 'help' for available commands.",
            style = monoStyle.copy(
                color = TerminalColors.Timestamp,
                fontSize = 11.sp
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ============================================================================
// Single Terminal Entry
// ============================================================================

/**
 * A single command + response row in the terminal history.
 * Shows the prompt, the user command, the privacy tier badge, and the output.
 */
@Composable
private fun TerminalEntryRow(
    entry: TerminalEntry,
    monoStyle: TextStyle,
    showTypingAnimation: Boolean
) {
    val clipboardManager = LocalClipboardManager.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // ---- Command line: prompt + user input ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            TerminalPrompt(
                privacyTier = entry.privacyTier,
                monoStyle = monoStyle
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = entry.input,
                style = monoStyle.copy(color = TerminalColors.Command)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // ---- Output block ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (showTypingAnimation && entry.output.isNotEmpty()) {
                    TypingAnimationText(
                        fullText = entry.output,
                        isError = entry.isError,
                        monoStyle = monoStyle
                    )
                } else {
                    Text(
                        text = entry.output,
                        style = monoStyle.copy(
                            color = if (entry.isError) TerminalColors.Error else TerminalColors.Output,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            // Privacy tier badge + copy button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                PrivacyTierBadge(privacyTier = entry.privacyTier)
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(entry.output))
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy output",
                        tint = TerminalColors.Subtext,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

/**
 * The command prompt display: `castor@local $` or `castor@cloud $`.
 * Color-coded by privacy tier.
 */
@Composable
private fun TerminalPrompt(
    privacyTier: String,
    monoStyle: TextStyle
) {
    val host = when (privacyTier.uppercase()) {
        "CLOUD" -> "cloud"
        "ANONYMIZED" -> "anon"
        else -> "local"
    }

    val hostColor = when (privacyTier.uppercase()) {
        "CLOUD" -> TerminalColors.PrivacyCloud
        "ANONYMIZED" -> TerminalColors.PrivacyAnonymized
        else -> TerminalColors.PrivacyLocal
    }

    Row {
        Text(
            text = "un-dios",
            style = monoStyle.copy(
                color = TerminalColors.Prompt,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        )
        Text(
            text = "@",
            style = monoStyle.copy(
                color = TerminalColors.Timestamp,
                fontSize = 12.sp
            )
        )
        Text(
            text = host,
            style = monoStyle.copy(
                color = hostColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        )
        Text(
            text = " $",
            style = monoStyle.copy(
                color = TerminalColors.Accent,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        )
    }
}

/**
 * Small badge indicating whether processing was LOCAL, CLOUD, or ANONYMIZED.
 */
@Composable
private fun PrivacyTierBadge(privacyTier: String) {
    val (icon, color, label) = when (privacyTier.uppercase()) {
        "CLOUD" -> Triple(Icons.Default.Cloud, TerminalColors.PrivacyCloud, "CLOUD")
        "ANONYMIZED" -> Triple(Icons.Default.Shield, TerminalColors.PrivacyAnonymized, "ANON")
        else -> Triple(Icons.Default.Shield, TerminalColors.PrivacyLocal, "LOCAL")
    }

    Box(
        modifier = Modifier
            .background(
                color.copy(alpha = 0.12f),
                RoundedCornerShape(3.dp)
            )
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(9.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            )
        }
    }
}

// ============================================================================
// Typing Animation
// ============================================================================

/**
 * Displays text with a character-by-character typing animation,
 * simulating an LLM generating a response in real-time.
 */
@Composable
private fun TypingAnimationText(
    fullText: String,
    isError: Boolean,
    monoStyle: TextStyle
) {
    var visibleCharCount by remember(fullText) { mutableIntStateOf(0) }

    LaunchedEffect(fullText) {
        visibleCharCount = 0
        for (i in fullText.indices) {
            delay(15L)
            visibleCharCount = i + 1
        }
    }

    Text(
        text = fullText.take(visibleCharCount),
        style = monoStyle.copy(
            color = if (isError) TerminalColors.Error else TerminalColors.Output,
            fontSize = 12.sp
        )
    )
}

// ============================================================================
// Processing Indicator
// ============================================================================

/**
 * Shows an animated indicator while the system is processing a command.
 */
@Composable
private fun ProcessingIndicator(monoStyle: TextStyle) {
    val infiniteTransition = rememberInfiniteTransition(label = "processing")
    val dotCount by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots"
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.5.dp,
            color = TerminalColors.Accent
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "processing" + ".".repeat(dotCount.toInt()),
            style = monoStyle.copy(
                color = TerminalColors.Accent,
                fontSize = 11.sp
            )
        )
    }
}

// ============================================================================
// Terminal Input Line
// ============================================================================

/**
 * The active input line at the bottom of the terminal with a blinking cursor.
 */
@Composable
private fun TerminalInputLine(
    onSubmit: (String) -> Unit,
    onInputChanged: (String) -> Unit = {},
    isProcessing: Boolean,
    monoStyle: TextStyle,
    voiceButton: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Blinking cursor animation
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(530, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorBlink"
    )

    Row(
        modifier = modifier
            .background(TerminalColors.Background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusRequester.requestFocus()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Prompt
        Text(
            text = "un-dios",
            style = monoStyle.copy(
                color = TerminalColors.Prompt,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        )
        Text(
            text = "@",
            style = monoStyle.copy(
                color = TerminalColors.Timestamp,
                fontSize = 12.sp
            )
        )
        Text(
            text = "local",
            style = monoStyle.copy(
                color = TerminalColors.PrivacyLocal,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        )
        Text(
            text = " $ ",
            style = monoStyle.copy(
                color = TerminalColors.Accent,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        )

        // Text input with blinking cursor
        Box(modifier = Modifier.weight(1f).height(IntrinsicSize.Min)) {
            BasicTextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                    onInputChanged(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = monoStyle.copy(
                    color = TerminalColors.Command,
                    fontSize = 13.sp
                ),
                cursorBrush = SolidColor(TerminalColors.Cursor.copy(alpha = cursorAlpha)),
                singleLine = true,
                enabled = !isProcessing,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank() && !isProcessing) {
                            onSubmit(inputText.trim())
                            inputText = ""
                        }
                    }
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (inputText.isEmpty()) {
                            Text(
                                text = if (isProcessing) "processing..." else "type a command...",
                                style = monoStyle.copy(
                                    color = TerminalColors.Subtext,
                                    fontSize = 13.sp
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Block cursor overlay when input is empty and not processing
            if (inputText.isEmpty() && !isProcessing) {
                val cursorColor = TerminalColors.Cursor.copy(alpha = cursorAlpha * 0.5f)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .drawBehind {
                            drawRect(
                                color = cursorColor,
                                topLeft = Offset.Zero,
                                size = Size(8.dp.toPx(), size.height)
                            )
                        }
                )
            }
        }

        // Voice input button
        if (voiceButton != null) {
            Spacer(modifier = Modifier.width(8.dp))
            voiceButton()
        }

        // Processing spinner on the input line
        if (isProcessing) {
            Spacer(modifier = Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = TerminalColors.Accent
            )
        }
    }
}
