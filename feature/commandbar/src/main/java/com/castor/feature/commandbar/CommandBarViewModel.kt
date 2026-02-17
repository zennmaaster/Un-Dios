package com.castor.feature.commandbar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.agent.orchestrator.AgentOrchestrator
import com.castor.core.inference.InferenceEngine
import com.castor.core.ui.components.TerminalEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Castor terminal / command bar interface.
 *
 * Maintains the full command history, handles command submission to the
 * [AgentOrchestrator], manages the expanded/collapsed state of the terminal,
 * and provides a built-in shortcut command system for quick navigation.
 *
 * Command dispatch flow:
 * 1. Input starting with `/` or `:` is matched against [BuiltInCommands].
 *    - Navigation commands emit a route on [navigationEvent] for the UI to collect.
 *    - Local commands (`/help`, `/clear`) are handled inline.
 * 2. Legacy bare-word commands (`help`, `clear`, `history`, etc.) are still supported.
 * 3. All other input is dispatched to the [AgentOrchestrator] for AI processing.
 *    - If no model is loaded, a helpful message directs the user to `/models`.
 *    - A processing indicator is shown while the orchestrator is working.
 *    - The response appears in the terminal history as a system response entry.
 *
 * Built-in shortcut commands (see [BuiltInCommands] for the full registry):
 * - `/messages` or `:msg` -- navigate to messages
 * - `/media` or `:play` -- navigate to media
 * - `/reminders` or `:rem` -- navigate to reminders
 * - `/notes` or `:note` -- navigate to notes
 * - `/weather` or `:wttr` -- navigate to weather
 * - `/settings` or `:config` -- navigate to settings
 * - `/notifications` or `:notif` -- navigate to notification center
 * - `/contacts` or `:addr` -- navigate to contacts
 * - `/usage` or `:top` -- navigate to usage stats
 * - `/models` or `:apt` -- navigate to model manager
 * - `/help` or `:h` -- show list of all commands
 * - `/clear` or `:cls` -- clear terminal history
 * - `/about` -- navigate to about screen
 * - `/battery` -- navigate to battery optimization
 * - `/search <query>` or `:find <query>` -- trigger universal search
 * - `/theme` -- navigate to theme selector
 */
@HiltViewModel
class CommandBarViewModel @Inject constructor(
    private val orchestrator: AgentOrchestrator,
    private val engine: InferenceEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommandBarState())
    val uiState: StateFlow<CommandBarState> = _uiState

    /**
     * Navigation event flow that emits route strings when the user types
     * a built-in navigation command (e.g. `/messages` emits `"messages"`).
     *
     * The UI layer (HomeScreen) collects this flow and calls the appropriate
     * `onNavigateTo*` callback based on the route string. This keeps the
     * ViewModel free of Android navigation dependencies.
     *
     * This is a [SharedFlow] (not [StateFlow]) because navigation events are
     * one-shot: they should be consumed exactly once and not replayed.
     */
    private val _navigationEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<String> = _navigationEvent.asSharedFlow()

    /**
     * Processes a user-submitted command through the dispatch pipeline.
     *
     * Dispatch order:
     * 1. Check for built-in shortcut commands (starting with `/` or `:`)
     * 2. Check for legacy bare-word commands (`help`, `clear`, etc.)
     * 3. Delegate to the [AgentOrchestrator] for AI-powered processing
     *
     * For shortcut commands that navigate, a confirmation entry is added to
     * the terminal history and a route is emitted on [navigationEvent].
     *
     * For AI commands, the terminal shows a processing indicator while the
     * orchestrator is working, and the response is appended to the history
     * when complete. If no model is loaded, a helpful error message is shown
     * directing the user to `/models`.
     */
    fun onSubmit(input: String) {
        val trimmedInput = input.trim()
        if (trimmedInput.isEmpty()) return

        // Clear autocomplete suggestions on submit
        _uiState.update { it.copy(autocompleteSuggestions = emptyList()) }

        // Step 1: Check for built-in shortcut commands (/ or : prefix)
        val matchedCommand = BuiltInCommands.matchCommand(trimmedInput)
        if (matchedCommand != null) {
            handleShortcutCommand(matchedCommand, trimmedInput)
            return
        }

        // Step 2: Check for legacy bare-word built-in commands
        val legacyResult = handleLegacyBuiltInCommand(trimmedInput)
        if (legacyResult != null) {
            // LEGACY_HANDLED_SENTINEL means the command was fully handled
            // (including adding entries to state) -- don't add a duplicate entry.
            if (legacyResult == LEGACY_HANDLED_SENTINEL) return

            val entry = TerminalEntry(
                input = trimmedInput,
                output = legacyResult,
                timestamp = System.currentTimeMillis(),
                isError = false,
                privacyTier = "LOCAL"
            )
            _uiState.update { state ->
                state.copy(
                    commandHistory = state.commandHistory + entry,
                    lastResponse = legacyResult,
                    privacyTier = "Local"
                )
            }
            return
        }

        // Step 3: Dispatch to agent orchestrator for natural language processing
        dispatchToOrchestrator(trimmedInput)
    }

    /**
     * Updates autocomplete suggestions based on the current input text.
     *
     * Called by the UI as the user types. Only provides suggestions when
     * the input starts with `/` or `:`. The suggestions list is cleared
     * when the input no longer matches any commands.
     *
     * @param currentInput The current text in the input field
     */
    fun onInputChanged(currentInput: String) {
        val suggestions = if (currentInput.isNotBlank()) {
            BuiltInCommands.getAutocompleteSuggestions(currentInput.trim())
        } else {
            emptyList()
        }
        _uiState.update { it.copy(autocompleteSuggestions = suggestions) }
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

    // -------------------------------------------------------------------------------------
    // Built-in shortcut command handling
    // -------------------------------------------------------------------------------------

    /**
     * Handles a matched built-in shortcut command.
     *
     * For navigation commands, adds a confirmation entry to the terminal
     * history and emits the route on [navigationEvent].
     *
     * For local commands (/help, /clear), handles them inline without
     * emitting a navigation event.
     *
     * For /search, extracts the query argument and emits a search route.
     */
    private fun handleShortcutCommand(command: BuiltInCommand, rawInput: String) {
        when (command.command) {
            "/clear" -> {
                clearHistory()
                // Don't add an entry -- just clear
            }

            "/help" -> {
                val helpEntries = BuiltInCommands.getHelpText()
                _uiState.update { state ->
                    state.copy(
                        commandHistory = state.commandHistory + helpEntries,
                        lastResponse = helpEntries.firstOrNull()?.output,
                        privacyTier = "Local",
                        isExpanded = true
                    )
                }
            }

            "/search" -> {
                val query = BuiltInCommands.extractArgument(rawInput)
                if (query.isBlank()) {
                    // No query provided -- show usage hint
                    val entry = TerminalEntry(
                        input = rawInput,
                        output = "Usage: /search <query>\nExample: /search meeting notes",
                        timestamp = System.currentTimeMillis(),
                        isError = false,
                        privacyTier = "LOCAL"
                    )
                    _uiState.update { state ->
                        state.copy(
                            commandHistory = state.commandHistory + entry,
                            lastResponse = entry.output,
                            privacyTier = "Local"
                        )
                    }
                } else {
                    val entry = TerminalEntry(
                        input = rawInput,
                        output = "Searching: $query",
                        timestamp = System.currentTimeMillis(),
                        isError = false,
                        privacyTier = "LOCAL"
                    )
                    _uiState.update { state ->
                        state.copy(
                            commandHistory = state.commandHistory + entry,
                            lastResponse = entry.output,
                            privacyTier = "Local"
                        )
                    }
                    viewModelScope.launch {
                        _navigationEvent.emit("search:$query")
                    }
                }
            }

            else -> {
                // Navigation command: emit route and show confirmation
                val route = command.route ?: return
                val entry = TerminalEntry(
                    input = rawInput,
                    output = "Navigating to ${command.command.removePrefix("/")}...",
                    timestamp = System.currentTimeMillis(),
                    isError = false,
                    privacyTier = "LOCAL"
                )
                _uiState.update { state ->
                    state.copy(
                        commandHistory = state.commandHistory + entry,
                        lastResponse = entry.output,
                        privacyTier = "Local"
                    )
                }
                viewModelScope.launch {
                    _navigationEvent.emit(route)
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Agent orchestrator dispatch
    // -------------------------------------------------------------------------------------

    /**
     * Dispatches natural language input to the [AgentOrchestrator] for AI processing.
     *
     * Shows a processing indicator while the orchestrator is working. If the
     * inference engine has no model loaded, shows a helpful message directing
     * the user to the model manager.
     */
    private fun dispatchToOrchestrator(input: String) {
        // Check if the model is loaded -- provide a helpful message if not
        if (!engine.isLoaded) {
            val noModelMessage = buildString {
                appendLine("No AI model loaded.")
                appendLine()
                appendLine("To get started with AI features:")
                appendLine("  1. Type /models to open the Model Manager")
                appendLine("  2. Download a model (Qwen2.5-3B recommended)")
                appendLine("  3. The model loads automatically")
                appendLine()
                appendLine("Built-in commands still work without a model.")
                appendLine("Type /help to see available commands.")
            }.trimEnd()

            val entry = TerminalEntry(
                input = input,
                output = noModelMessage,
                timestamp = System.currentTimeMillis(),
                isError = true,
                privacyTier = "LOCAL"
            )
            _uiState.update { state ->
                state.copy(
                    commandHistory = state.commandHistory + entry,
                    lastResponse = noModelMessage,
                    privacyTier = "Local",
                    isExpanded = true
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
                val response = orchestrator.processInput(input)
                val entry = TerminalEntry(
                    input = input,
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
                    input = input,
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

    // -------------------------------------------------------------------------------------
    // Legacy bare-word built-in commands
    // -------------------------------------------------------------------------------------

    /**
     * Handles legacy built-in terminal commands that don't use the / or : prefix.
     *
     * These are retained for backward compatibility so users who are accustomed
     * to typing `help` or `clear` (without a slash) still get the expected behavior.
     *
     * @return The response text if the input was a legacy command, or null if it should
     *         be dispatched to the orchestrator.
     */
    private fun handleLegacyBuiltInCommand(input: String): String? {
        return when (input.lowercase()) {
            "clear", "cls" -> {
                clearHistory()
                null // Don't add an entry -- just clear
            }
            "help" -> {
                // Redirect to the new help system
                val helpEntries = BuiltInCommands.getHelpText()
                _uiState.update { state ->
                    state.copy(
                        commandHistory = state.commandHistory + helpEntries.map {
                            it.copy(input = "help")
                        },
                        lastResponse = helpEntries.firstOrNull()?.output,
                        privacyTier = "Local",
                        isExpanded = true
                    )
                }
                // Return a non-null marker to indicate we handled it, but the entry
                // was already added above so we return a sentinel that onSubmit will
                // skip adding a duplicate entry.
                LEGACY_HANDLED_SENTINEL
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
                    appendLine("Un-Dios v0.1.0")
                    appendLine("Open convergence platform for Android")
                    appendLine("Runtime: On-device GGUF inference")
                }
            }
            else -> null
        }
    }

    companion object {
        /**
         * Sentinel value returned by [handleLegacyBuiltInCommand] when the command
         * was fully handled (including adding entries to state) and [onSubmit]
         * should not add a duplicate entry.
         */
        private const val LEGACY_HANDLED_SENTINEL = "\u0000__HANDLED__"
    }
}
