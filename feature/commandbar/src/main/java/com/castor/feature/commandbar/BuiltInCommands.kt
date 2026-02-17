package com.castor.feature.commandbar

import com.castor.core.ui.components.TerminalEntry

/**
 * Represents a single built-in shortcut command available in the Castor terminal.
 *
 * Built-in commands start with `/` or `:` and provide quick navigation to
 * different sections of the app without needing natural language processing.
 *
 * @param command The primary command string (e.g. "/messages")
 * @param aliases Alternative command strings (e.g. [":msg"])
 * @param description Human-readable description shown in help and autocomplete
 * @param route The navigation route to emit, or null for commands handled locally
 *              (like /clear and /help which don't navigate anywhere)
 * @param takesArgument Whether this command accepts an argument after it (e.g. /search <query>)
 */
data class BuiltInCommand(
    val command: String,
    val aliases: List<String>,
    val description: String,
    val route: String?,
    val takesArgument: Boolean = false
)

/**
 * Registry and matcher for all built-in terminal shortcut commands.
 *
 * Commands are organized by category:
 * - Navigation: /messages, /media, /reminders, /notes, /weather, /contacts, /notifications, /usage
 * - System: /settings, /models, /about, /battery, /theme
 * - Terminal: /help, /clear, /search
 *
 * Each command has a primary form (starting with `/`) and a short alias (starting with `:`),
 * following the vim-like convention used throughout the Un-Dios terminal aesthetic.
 */
object BuiltInCommands {

    /**
     * The complete list of all registered built-in commands.
     */
    val commands: List<BuiltInCommand> = listOf(
        // Navigation commands
        BuiltInCommand(
            command = "/messages",
            aliases = listOf(":msg"),
            description = "Open the messaging inbox",
            route = "messages"
        ),
        BuiltInCommand(
            command = "/media",
            aliases = listOf(":play"),
            description = "Open the media player",
            route = "media"
        ),
        BuiltInCommand(
            command = "/reminders",
            aliases = listOf(":rem"),
            description = "Open reminders and calendar",
            route = "reminders"
        ),
        BuiltInCommand(
            command = "/notes",
            aliases = listOf(":note"),
            description = "Open the scratchpad notes",
            route = "notes"
        ),
        BuiltInCommand(
            command = "/weather",
            aliases = listOf(":wttr"),
            description = "Open the weather detail view",
            route = "weather"
        ),
        BuiltInCommand(
            command = "/contacts",
            aliases = listOf(":addr"),
            description = "Open the contacts hub",
            route = "contacts"
        ),
        BuiltInCommand(
            command = "/notifications",
            aliases = listOf(":notif"),
            description = "Open the notification center",
            route = "notification_center"
        ),
        BuiltInCommand(
            command = "/usage",
            aliases = listOf(":top"),
            description = "Open app usage statistics",
            route = "usage_stats"
        ),
        // System commands
        BuiltInCommand(
            command = "/settings",
            aliases = listOf(":config"),
            description = "Open launcher settings",
            route = "settings"
        ),
        BuiltInCommand(
            command = "/models",
            aliases = listOf(":apt"),
            description = "Open the model manager",
            route = "model_manager"
        ),
        BuiltInCommand(
            command = "/about",
            aliases = emptyList(),
            description = "Show about screen",
            route = "about"
        ),
        BuiltInCommand(
            command = "/battery",
            aliases = emptyList(),
            description = "Battery optimization guide",
            route = "battery_optimization"
        ),
        BuiltInCommand(
            command = "/theme",
            aliases = emptyList(),
            description = "Open the theme selector",
            route = "theme_selector"
        ),
        // Terminal commands (no route -- handled locally)
        BuiltInCommand(
            command = "/help",
            aliases = listOf(":h"),
            description = "Show this list of commands",
            route = null
        ),
        BuiltInCommand(
            command = "/clear",
            aliases = listOf(":cls"),
            description = "Clear terminal history",
            route = null
        ),
        BuiltInCommand(
            command = "/search",
            aliases = listOf(":find"),
            description = "Universal search  --  /search <query>",
            route = "search",
            takesArgument = true
        )
    )

    /**
     * Try to match raw user input against a built-in command.
     *
     * Matching rules:
     * 1. Input is trimmed and lowercased.
     * 2. For commands that take arguments (e.g. /search), the first token is matched.
     * 3. Both the primary command and all aliases are checked.
     * 4. Returns null if no match is found, meaning the input should be sent
     *    to the agent orchestrator as natural language.
     *
     * @param input The raw user input string
     * @return The matching [BuiltInCommand], or null if no match
     */
    fun matchCommand(input: String): BuiltInCommand? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // Only consider inputs starting with / or :
        val firstChar = trimmed.first()
        if (firstChar != '/' && firstChar != ':') return null

        val lowered = trimmed.lowercase()
        val firstToken = lowered.split("\\s+".toRegex()).first()

        return commands.firstOrNull { cmd ->
            cmd.command == firstToken || cmd.aliases.any { alias -> alias == firstToken }
        }
    }

    /**
     * Extract the argument portion from a command that takes arguments.
     *
     * For example, `/search hello world` returns `"hello world"`.
     *
     * @param input The full user input string
     * @return The argument text, or an empty string if no argument was provided
     */
    fun extractArgument(input: String): String {
        val trimmed = input.trim()
        val spaceIndex = trimmed.indexOf(' ')
        return if (spaceIndex >= 0) {
            trimmed.substring(spaceIndex + 1).trim()
        } else {
            ""
        }
    }

    /**
     * Get autocomplete suggestions based on the current partial input.
     *
     * Returns commands whose primary command or aliases start with the
     * given prefix. Used by the command bar to show a dropdown as the
     * user types.
     *
     * @param prefix The current text in the input field
     * @return A list of matching commands, sorted by primary command name
     */
    fun getAutocompleteSuggestions(prefix: String): List<BuiltInCommand> {
        val trimmed = prefix.trim().lowercase()
        if (trimmed.isEmpty()) return emptyList()

        val firstChar = trimmed.first()
        if (firstChar != '/' && firstChar != ':') return emptyList()

        return commands.filter { cmd ->
            cmd.command.startsWith(trimmed) ||
                cmd.aliases.any { alias -> alias.startsWith(trimmed) }
        }.sortedBy { it.command }
    }

    /**
     * Generate formatted help text as a list of [TerminalEntry] items
     * suitable for displaying in the terminal history.
     *
     * The help text is returned as a single entry with all commands
     * formatted in a table-like monospace layout.
     *
     * @return A list containing a single [TerminalEntry] with the help text
     */
    fun getHelpText(): List<TerminalEntry> {
        val helpOutput = buildString {
            appendLine("Un-Dios Terminal v0.1.0")
            appendLine("=======================")
            appendLine()
            appendLine("Navigation commands:")
            appendLine("  /messages   :msg    Open messaging inbox")
            appendLine("  /media      :play   Open media player")
            appendLine("  /reminders  :rem    Open reminders & calendar")
            appendLine("  /notes      :note   Open scratchpad notes")
            appendLine("  /weather    :wttr   Open weather detail")
            appendLine("  /contacts   :addr   Open contacts hub")
            appendLine("  /notifications :notif  Open notification center")
            appendLine("  /usage      :top    Open usage statistics")
            appendLine()
            appendLine("System commands:")
            appendLine("  /settings   :config Open launcher settings")
            appendLine("  /models     :apt    Open model manager")
            appendLine("  /about             About screen")
            appendLine("  /battery           Battery optimization")
            appendLine("  /theme             Theme selector")
            appendLine()
            appendLine("Terminal commands:")
            appendLine("  /help       :h      Show this help message")
            appendLine("  /clear      :cls    Clear terminal history")
            appendLine("  /search     :find   Universal search (/search <query>)")
            appendLine()
            appendLine("Natural language:")
            appendLine("  Any input not starting with / or : is processed")
            appendLine("  by the on-device AI agent. Examples:")
            appendLine("    \"play some jazz\"")
            appendLine("    \"remind me to call Mom at 5pm\"")
            appendLine("    \"summarize my unread messages\"")
            appendLine()
            appendLine("Legacy commands (still supported):")
            appendLine("  help, clear, cls, history, privacy, version")
        }

        return listOf(
            TerminalEntry(
                input = "/help",
                output = helpOutput.trimEnd(),
                timestamp = System.currentTimeMillis(),
                isError = false,
                privacyTier = "LOCAL"
            )
        )
    }
}
