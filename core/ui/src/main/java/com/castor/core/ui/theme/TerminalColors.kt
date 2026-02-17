package com.castor.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * CompositionLocal providing the active [TerminalColorScheme].
 *
 * Defaults to [CatppuccinMocha] so that previews and tests work without
 * explicitly wrapping in a provider. In production, [CastorTheme] supplies
 * the user-selected scheme via [CompositionLocalProvider].
 */
val LocalTerminalColors = staticCompositionLocalOf { CatppuccinMocha }

/**
 * Global accessor for terminal theme colors.
 *
 * Every property delegates to the current [TerminalColorScheme] provided
 * through [LocalTerminalColors]. This means all 160+ existing call sites
 * (e.g. `TerminalColors.Background`) continue to compile and work without
 * modification, while the actual color values change dynamically when the
 * user switches themes.
 *
 * IMPORTANT: These properties use custom `get()` accessors and must be
 * read from within a `@Composable` scope so that the CompositionLocal
 * resolution works correctly.
 */
object TerminalColors {

    /** Deep dark base background */
    val Background: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.background

    /** Slightly lighter surface for cards and elevated elements */
    val Surface: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.surface

    /** Green prompt text -- the classic terminal prompt color */
    val Prompt: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.prompt

    /** Light text for user-typed commands */
    val Command: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.command

    /** Slightly dimmer text for command output / responses */
    val Output: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.output

    /** Red for error messages and failed commands */
    val Error: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.error

    /** Orange for warnings and caution indicators */
    val Warning: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.warning

    /** Green for success confirmations */
    val Success: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.success

    /** Blue for informational messages */
    val Info: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.info

    /** Purple accent -- Castor brand identity within the terminal */
    val Accent: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.accent

    /** Warm white cursor color */
    val Cursor: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.cursor

    /** Selection highlight for text selection */
    val Selection: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.selection

    /** Even darker color for the top status bar panel */
    val StatusBar: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.statusBar

    /** Dim color for timestamps and secondary metadata */
    val Timestamp: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.timestamp

    /** Overlay color for semi-transparent backgrounds (dock, panels) */
    val Overlay: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.overlay

    /** Subtext for less important UI elements */
    val Subtext: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.subtext

    /** Badge background for notification counts */
    val BadgeRed: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.badgeRed

    /** Local privacy tier indicator */
    val PrivacyLocal: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.privacyLocal

    /** Cloud privacy tier indicator */
    val PrivacyCloud: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.privacyCloud

    /** Anonymized privacy tier indicator */
    val PrivacyAnonymized: Color
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current.privacyAnonymized
}
