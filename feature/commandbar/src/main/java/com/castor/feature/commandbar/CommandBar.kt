package com.castor.feature.commandbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.components.CastorTerminal
import com.castor.core.ui.components.TerminalEntry
import com.castor.core.ui.theme.TerminalColors

/**
 * Voice input state holder.
 *
 * @param isListening Whether voice recognition is currently active
 * @param partialTranscript The partial transcript being recognized in real-time
 * @param error Any error message from voice recognition
 */
data class VoiceInputState(
    val isListening: Boolean = false,
    val partialTranscript: String = "",
    val error: String? = null
)

/**
 * State holder for the CommandBar / terminal interface.
 *
 * @param isProcessing Whether a command is currently being processed by the orchestrator
 * @param lastResponse The most recent response text (kept for backward compatibility)
 * @param privacyTier The privacy tier of the most recent operation
 * @param commandHistory Full list of terminal entries for scrollback
 * @param isExpanded Whether the terminal is in full/expanded view or collapsed single-line view
 * @param autocompleteSuggestions Current autocomplete suggestions based on user input.
 *        Populated when the user types a `/` or `:` prefix and matches available commands.
 * @param showVoiceOverlay Whether to show the full-screen voice input overlay
 */
data class CommandBarState(
    val isProcessing: Boolean = false,
    val lastResponse: String? = null,
    val privacyTier: String = "Local",
    val commandHistory: List<TerminalEntry> = emptyList(),
    val isExpanded: Boolean = false,
    val autocompleteSuggestions: List<BuiltInCommand> = emptyList(),
    val showVoiceOverlay: Boolean = false
)

/**
 * The primary command interface for Castor -- a full terminal emulator wrapper
 * with built-in command autocompletion and voice input.
 *
 * When collapsed, shows a single-line monospace input with the terminal prompt.
 * When expanded, shows the full command history scrollback and input line,
 * styled like a Linux terminal emulator.
 *
 * When the user types input starting with `/` or `:`, an autocomplete dropdown
 * appears above the input showing matching built-in commands. Tapping a suggestion
 * fills the input and submits the command.
 *
 * This composable delegates terminal rendering to [CastorTerminal] from `:core:ui`,
 * and adds the autocomplete overlay on top.
 *
 * @param state The current command bar state from [CommandBarViewModel]
 * @param onSubmit Callback when the user submits a command
 * @param onToggleExpanded Callback to toggle between expanded and collapsed views
 * @param onInputChanged Callback when the input text changes (for autocomplete)
 * @param voiceInputState Voice input state (isListening, partialTranscript, error)
 * @param onStartVoiceInput Callback to start voice input
 * @param onStopVoiceInput Callback to stop voice input
 * @param onClearVoiceError Callback to clear voice error
 * @param modifier Modifier for the root composable
 */
@Composable
fun CommandBar(
    state: CommandBarState,
    onSubmit: (String) -> Unit,
    onToggleExpanded: () -> Unit,
    onInputChanged: (String) -> Unit = {},
    voiceInputState: VoiceInputState = VoiceInputState(),
    onStartVoiceInput: () -> Unit = {},
    onStopVoiceInput: () -> Unit = {},
    onClearVoiceError: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
        ) {
            // Autocomplete dropdown -- shown when suggestions are available
            AnimatedVisibility(
                visible = state.autocompleteSuggestions.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                AutocompleteDropdown(
                    suggestions = state.autocompleteSuggestions,
                    onSuggestionSelected = { command ->
                        onSubmit(command.command)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Terminal
            CastorTerminal(
                commandHistory = state.commandHistory,
                onSubmit = onSubmit,
                isProcessing = state.isProcessing,
                isExpanded = state.isExpanded,
                onToggleExpanded = onToggleExpanded,
                showTypingAnimation = true,
                onInputChanged = onInputChanged,
                voiceButton = {
                    VoiceInputButton(
                        isListening = voiceInputState.isListening,
                        partialTranscript = voiceInputState.partialTranscript,
                        error = voiceInputState.error,
                        onStartListening = onStartVoiceInput,
                        onStopListening = onStopVoiceInput,
                        onClearError = onClearVoiceError
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Full-screen voice input overlay
        if (state.showVoiceOverlay && voiceInputState.isListening) {
            VoiceInputOverlay(
                partialTranscript = voiceInputState.partialTranscript,
                onCancel = onStopVoiceInput,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ============================================================================
// Autocomplete Dropdown
// ============================================================================

/**
 * A dropdown list of matching built-in commands, shown above the terminal input
 * when the user types a `/` or `:` prefix.
 *
 * Each row shows the command name, its alias (if any), and a brief description.
 * Tapping a row submits the command immediately.
 *
 * Styled with the terminal aesthetic: dark background, monospace font,
 * accent-colored command names.
 */
@Composable
private fun AutocompleteDropdown(
    suggestions: List<BuiltInCommand>,
    onSuggestionSelected: (BuiltInCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(TerminalColors.Surface)
            .heightIn(max = 200.dp)
    ) {
        HorizontalDivider(color = TerminalColors.Accent.copy(alpha = 0.3f), thickness = 1.dp)

        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = suggestions,
                key = { it.command }
            ) { command ->
                AutocompleteRow(
                    command = command,
                    onClick = { onSuggestionSelected(command) }
                )
            }
        }

        HorizontalDivider(color = TerminalColors.Accent.copy(alpha = 0.3f), thickness = 1.dp)
    }
}

/**
 * A single row in the autocomplete dropdown.
 *
 * Layout: `/command  :alias  Description text`
 */
@Composable
private fun AutocompleteRow(
    command: BuiltInCommand,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Primary command
        Text(
            text = command.command,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )

        // Alias (if any)
        if (command.aliases.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = command.aliases.first(),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Description
        Text(
            text = command.description,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Subtext
            ),
            modifier = Modifier.weight(1f)
        )
    }
}
