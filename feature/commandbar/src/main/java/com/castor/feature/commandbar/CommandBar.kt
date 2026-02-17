package com.castor.feature.commandbar

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.castor.core.ui.components.CastorTerminal
import com.castor.core.ui.components.TerminalEntry

/**
 * State holder for the CommandBar / terminal interface.
 *
 * @param isProcessing Whether a command is currently being processed by the orchestrator
 * @param lastResponse The most recent response text (kept for backward compatibility)
 * @param privacyTier The privacy tier of the most recent operation
 * @param commandHistory Full list of terminal entries for scrollback
 * @param isExpanded Whether the terminal is in full/expanded view or collapsed single-line view
 */
data class CommandBarState(
    val isProcessing: Boolean = false,
    val lastResponse: String? = null,
    val privacyTier: String = "Local",
    val commandHistory: List<TerminalEntry> = emptyList(),
    val isExpanded: Boolean = false
)

/**
 * The primary command interface for Castor — a full terminal emulator wrapper.
 *
 * When collapsed, shows a single-line monospace input with the terminal prompt.
 * When expanded, shows the full command history scrollback and input line,
 * styled like a Linux terminal emulator.
 *
 * This composable delegates all rendering to [CastorTerminal] from `:core:ui`,
 * acting as a thin feature-module wrapper that connects the terminal UI to the
 * CommandBar ViewModel state.
 *
 * @param state The current command bar state from [CommandBarViewModel]
 * @param onSubmit Callback when the user submits a command
 * @param onToggleExpanded Callback to toggle between expanded and collapsed views
 * @param modifier Modifier for the root composable
 */
@Composable
fun CommandBar(
    state: CommandBarState,
    onSubmit: (String) -> Unit,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    CastorTerminal(
        commandHistory = state.commandHistory,
        onSubmit = onSubmit,
        isProcessing = state.isProcessing,
        isExpanded = state.isExpanded,
        onToggleExpanded = onToggleExpanded,
        showTypingAnimation = true,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
    )
}
