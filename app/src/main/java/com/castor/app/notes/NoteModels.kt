package com.castor.app.notes

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.castor.core.ui.theme.TerminalColors

/**
 * Visual accent color options for notes.
 *
 * Each value maps to a Catppuccin Mocha terminal color for consistent theming.
 * The [key] is persisted in the database; the Compose [Color] is derived at
 * render time via [toComposeColor].
 */
enum class NoteColor(val key: String) {
    DEFAULT("default"),
    RED("red"),
    ORANGE("orange"),
    YELLOW("yellow"),
    GREEN("green"),
    BLUE("blue");

    /**
     * Returns the [Color] from [TerminalColors] associated with this note color.
     * Must be called from a @Composable scope because [TerminalColors] reads
     * a CompositionLocal.
     */
    val composeColor: Color
        @Composable get() = when (this) {
            DEFAULT -> TerminalColors.Accent
            RED -> TerminalColors.Error
            ORANGE -> TerminalColors.Warning
            YELLOW -> TerminalColors.Cursor
            GREEN -> TerminalColors.Success
            BLUE -> TerminalColors.Info
        }

    companion object {
        fun fromKey(key: String): NoteColor =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * Sort modes for the notes list, styled as terminal flags.
 */
enum class NoteSortMode(val flag: String) {
    MODIFIED("--sort=modified"),
    CREATED("--sort=created"),
    NAME("--sort=name");
}
