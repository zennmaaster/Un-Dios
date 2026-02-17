package com.castor.feature.commandbar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.agent.orchestrator.AgentOrchestrator
import com.castor.core.ui.components.TerminalEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Castor terminal / command bar interface.
 *
 * Maintains the full command history, handles command submission to the
 * [AgentOrchestrator], and manages the expanded/collapsed state of the terminal.
 *
 * Built-in commands:
 * - `clear` / `cls` — clears the terminal history
 * - `help` — shows available commands and usage info
 * - `history` — lists all previous commands
 * - `privacy` — shows current privacy tier information
 */
@HiltViewModel
class CommandBarViewModel @Inject constructor(
    private val orchestrator: AgentOrchestrator
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommandBarState())
    val uiState: StateFlow<CommandBarState> = _uiState

    /**
     * Processes a user-submitted command.
     *
     * First checks for built-in terminal commands (clear, help, etc.).
     * If not a built-in command, delegates to the AgentOrchestrator for
     * AI-powered processing. The result is appended to the command history
     * with the appropriate privacy tier and error state.
     */
    fun onSubmit(input: String) {
        val trimmedInput = input.trim()
        if (trimmedInput.isEmpty()) return

        // Handle built-in commands locally
        val builtInResult = handleBuiltInCommand(trimmedInput)
        if (builtInResult != null) {
            val entry = TerminalEntry(
                input = trimmedInput,
                output = builtInResult,
                timestamp = System.currentTimeMillis(),
                isError = false,
                privacyTier = "LOCAL"
            )
            _uiState.update { state ->
                state.copy(
                    commandHistory = state.commandHistory + entry,
                    lastResponse = builtInResult,
                    privacyTier = "Local"
                )
            }
            return
        }

        // Mark as processing and auto-expand the terminal to show the response
        _uiState.update { state ->
            state.copy(
                isProcessing = true,
                isExpanded = true
            )
        }

        viewModelScope.launch {
            val timestamp = System.currentTimeMillis()
            try {
                val response = orchestrator.processInput(trimmedInput)
                val entry = TerminalEntry(
                    input = trimmedInput,
                    output = response,
                    timestamp = timestamp,
                    isError = false,
                    privacyTier = "LOCAL"
                )
                _uiState.update { state ->
                    state.copy(
                        isProcessing = false,
                        lastResponse = response,
                        privacyTier = "Local",
                        commandHistory = state.commandHistory + entry
                    )
                }
            } catch (e: Exception) {
                val errorMessage = "Error: ${e.message ?: "Unknown error occurred"}"
                val entry = TerminalEntry(
                    input = trimmedInput,
                    output = errorMessage,
                    timestamp = timestamp,
                    isError = true,
                    privacyTier = "LOCAL"
                )
                _uiState.update { state ->
                    state.copy(
                        isProcessing = false,
                        lastResponse = errorMessage,
                        privacyTier = "Local",
                        commandHistory = state.commandHistory + entry
                    )
                }
            }
        }
    }

    /**
     * Toggles the terminal between expanded (full scrollback) and collapsed (single-line) views.
     */
    fun toggleExpanded() {
        _uiState.update { it.copy(isExpanded = !it.isExpanded) }
    }

    /**
     * Clears the entire command history.
     */
    fun clearHistory() {
        _uiState.update { state ->
            state.copy(
                commandHistory = emptyList(),
                lastResponse = null
            )
        }
    }

    /**
     * Handles built-in terminal commands that don't require the AI orchestrator.
     *
     * @return The response text if the input was a built-in command, or null if it should
     *         be delegated to the orchestrator.
     */
    private fun handleBuiltInCommand(input: String): String? {
        return when (input.lowercase()) {
            "clear", "cls" -> {
                clearHistory()
                null // Don't add an entry — just clear
            }
            "help" -> {
                buildString {
                    appendLine("Castor Terminal v0.1.0")
                    appendLine("======================")
                    appendLine()
                    appendLine("Built-in commands:")
                    appendLine("  help       Show this help message")
                    appendLine("  clear/cls  Clear terminal history")
                    appendLine("  history    List previous commands")
                    appendLine("  privacy    Show privacy tier info")
                    appendLine("  version    Show version information")
                    appendLine()
                    appendLine("All other input is processed by the Castor AI agent.")
                    appendLine("Processing is local-first — your data stays on-device")
                    appendLine("unless cloud fallback is explicitly enabled.")
                }
            }
            "history" -> {
                val history = _uiState.value.commandHistory
                if (history.isEmpty()) {
                    "No commands in history."
                } else {
                    buildString {
                        appendLine("Command history (${history.size} entries):")
                        history.forEachIndexed { index, entry ->
                            appendLine("  ${index + 1}. ${entry.input}")
                        }
                    }
                }
            }
            "privacy" -> {
                buildString {
                    appendLine("Privacy Tier: LOCAL")
                    appendLine("All processing runs on-device by default.")
                    appendLine()
                    appendLine("Tiers:")
                    appendLine("  LOCAL       Never leaves device (green shield)")
                    appendLine("  ANONYMIZED  Anonymized cloud processing (blue shield)")
                    appendLine("  CLOUD       Cloud processing allowed (yellow indicator)")
                    appendLine()
                    appendLine("Current model runs entirely on-device via GGUF inference.")
                }
            }
            "version" -> {
                buildString {
                    appendLine("Castor v0.1.0")
                    appendLine("Privacy-first multi-agent Android system")
                    appendLine("Runtime: On-device GGUF inference")
                }
            }
            else -> null
        }
    }
}
