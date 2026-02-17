package com.castor.app.desktop.integration

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents a single keyboard shortcut bound to a specific action.
 *
 * Used by the [KeyboardShortcutRegistry] to track app-specific shortcuts
 * that are active when a particular window is focused.
 *
 * @param keys Human-readable key combination (e.g., "Ctrl+N", "Ctrl+Shift+P")
 * @param description Short description of what the shortcut does
 * @param action Intent action or callback identifier to invoke
 */
data class AppShortcut(
    val keys: String,
    val description: String,
    val action: String
)

/**
 * Registry for app-specific keyboard shortcuts in desktop mode.
 *
 * When a window is focused in the tiling window manager, its app-specific
 * shortcuts become active. This registry maps window IDs to their sets
 * of keyboard shortcuts, allowing the UI layer to display context-sensitive
 * shortcut information and route key events to the correct handler.
 *
 * The registry is pre-loaded with default shortcuts for known window types
 * (terminal, messages, media). Additional shortcuts can be registered
 * dynamically when new windows are opened.
 *
 * This is a Hilt [Singleton] — a single instance manages shortcuts across
 * the entire desktop session.
 *
 * Usage:
 * ```kotlin
 * // Get shortcuts for the focused window
 * val shortcuts = registry.getShortcutsForWindow("terminal")
 *
 * // Register custom shortcuts for a new window
 * registry.registerShortcuts("code-editor", listOf(
 *     AppShortcut("Ctrl+S", "Save file", "save"),
 *     AppShortcut("Ctrl+P", "Quick open", "quick_open")
 * ))
 * ```
 */
@Singleton
class KeyboardShortcutRegistry @Inject constructor() {

    /** Internal mutable map of window/app IDs to their registered shortcuts. */
    private val _shortcuts = MutableStateFlow<Map<String, List<AppShortcut>>>(emptyMap())

    /** Observable map of all registered keyboard shortcuts by window/app ID. */
    val shortcuts: StateFlow<Map<String, List<AppShortcut>>> = _shortcuts.asStateFlow()

    init {
        // Register default shortcuts for known window types
        registerShortcuts(
            "terminal",
            listOf(
                AppShortcut("Ctrl+C", "Copy / Cancel", "copy_cancel"),
                AppShortcut("Ctrl+V", "Paste", "paste"),
                AppShortcut("Ctrl+L", "Clear screen", "clear"),
                AppShortcut("Ctrl+Z", "Undo", "undo"),
                AppShortcut("Ctrl+A", "Select all", "select_all"),
                AppShortcut("Ctrl+Shift+C", "Copy selection", "copy_selection"),
                AppShortcut("Ctrl+Shift+V", "Paste clipboard", "paste_clipboard"),
                AppShortcut("Tab", "Autocomplete", "autocomplete")
            )
        )

        registerShortcuts(
            "messages",
            listOf(
                AppShortcut("Ctrl+N", "New message", "new_message"),
                AppShortcut("Ctrl+F", "Search messages", "search"),
                AppShortcut("Enter", "Send message", "send"),
                AppShortcut("Shift+Enter", "New line", "new_line"),
                AppShortcut("Ctrl+K", "Insert link", "insert_link"),
                AppShortcut("Escape", "Close chat", "close")
            )
        )

        registerShortcuts(
            "media",
            listOf(
                AppShortcut("Space", "Play/Pause", "toggle_playback"),
                AppShortcut("Ctrl+Right", "Next track", "next"),
                AppShortcut("Ctrl+Left", "Previous track", "previous"),
                AppShortcut("Ctrl+Up", "Volume up", "volume_up"),
                AppShortcut("Ctrl+Down", "Volume down", "volume_down"),
                AppShortcut("M", "Mute/Unmute", "toggle_mute"),
                AppShortcut("S", "Shuffle", "toggle_shuffle"),
                AppShortcut("R", "Repeat", "toggle_repeat")
            )
        )

        registerShortcuts(
            "reminders",
            listOf(
                AppShortcut("Ctrl+N", "New reminder", "new_reminder"),
                AppShortcut("Ctrl+D", "Mark done", "mark_done"),
                AppShortcut("Delete", "Delete reminder", "delete"),
                AppShortcut("Ctrl+F", "Search reminders", "search"),
                AppShortcut("Ctrl+E", "Edit reminder", "edit")
            )
        )

        registerShortcuts(
            "ai-engine",
            listOf(
                AppShortcut("Ctrl+Enter", "Submit query", "submit"),
                AppShortcut("Ctrl+L", "Clear conversation", "clear"),
                AppShortcut("Ctrl+C", "Copy response", "copy"),
                AppShortcut("Escape", "Cancel generation", "cancel"),
                AppShortcut("Ctrl+S", "Save conversation", "save")
            )
        )
    }

    /**
     * Registers keyboard shortcuts for a window or app type.
     *
     * If shortcuts were previously registered for the given [windowId],
     * they are replaced with the new list.
     *
     * @param windowId The window or app identifier to associate shortcuts with
     * @param shortcuts The list of keyboard shortcuts to register
     */
    fun registerShortcuts(windowId: String, shortcuts: List<AppShortcut>) {
        _shortcuts.update { currentMap ->
            currentMap + (windowId to shortcuts)
        }
    }

    /**
     * Unregisters all keyboard shortcuts for the given window.
     *
     * Typically called when a window is closed to clean up its shortcuts.
     *
     * @param windowId The window identifier whose shortcuts to remove
     */
    fun unregisterShortcuts(windowId: String) {
        _shortcuts.update { currentMap ->
            currentMap - windowId
        }
    }

    /**
     * Gets the keyboard shortcuts registered for a specific window.
     *
     * Returns an empty list if no shortcuts are registered for the given ID.
     *
     * @param windowId The window identifier to look up
     * @return The list of shortcuts for that window, or empty if none
     */
    fun getShortcutsForWindow(windowId: String): List<AppShortcut> {
        return _shortcuts.value[windowId] ?: emptyList()
    }

    /**
     * Adds a single shortcut to an existing window's shortcut list.
     *
     * If no shortcuts are registered for the window, creates a new entry.
     *
     * @param windowId The window identifier
     * @param shortcut The shortcut to add
     */
    fun addShortcut(windowId: String, shortcut: AppShortcut) {
        _shortcuts.update { currentMap ->
            val existing = currentMap[windowId] ?: emptyList()
            currentMap + (windowId to (existing + shortcut))
        }
    }

    /**
     * Removes a specific shortcut from a window's shortcut list by action name.
     *
     * @param windowId The window identifier
     * @param action The action string of the shortcut to remove
     */
    fun removeShortcut(windowId: String, action: String) {
        _shortcuts.update { currentMap ->
            val existing = currentMap[windowId] ?: return@update currentMap
            currentMap + (windowId to existing.filter { it.action != action })
        }
    }
}
