package com.castor.core.ui.theme

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
    val Background: Color get() = LocalTerminalColors.current.background

    /** Slightly lighter surface for cards and elevated elements */
    val Surface: Color get() = LocalTerminalColors.current.surface

    /** Green prompt text -- the classic terminal prompt color */
    val Prompt: Color get() = LocalTerminalColors.current.prompt

    /** Light text for user-typed commands */
    val Command: Color get() = LocalTerminalColors.current.command

    /** Slightly dimmer text for command output / responses */
    val Output: Color get() = LocalTerminalColors.current.output

    /** Red for error messages and failed commands */
    val Error: Color get() = LocalTerminalColors.current.error

    /** Orange for warnings and caution indicators */
    val Warning: Color get() = LocalTerminalColors.current.warning

    /** Green for success confirmations */
    val Success: Color get() = LocalTerminalColors.current.success

    /** Blue for informational messages */
    val Info: Color get() = LocalTerminalColors.current.info

    /** Purple accent -- Castor brand identity within the terminal */
    val Accent: Color get() = LocalTerminalColors.current.accent

    /** Warm white cursor color */
    val Cursor: Color get() = LocalTerminalColors.current.cursor

    /** Selection highlight for text selection */
    val Selection: Color get() = LocalTerminalColors.current.selection

    /** Even darker color for the top status bar panel */
    val StatusBar: Color get() = LocalTerminalColors.current.statusBar

    /** Dim color for timestamps and secondary metadata */
    val Timestamp: Color get() = LocalTerminalColors.current.timestamp

    /** Overlay color for semi-transparent backgrounds (dock, panels) */
    val Overlay: Color get() = LocalTerminalColors.current.overlay

    /** Subtext for less important UI elements */
    val Subtext: Color get() = LocalTerminalColors.current.subtext

    /** Badge background for notification counts */
    val BadgeRed: Color get() = LocalTerminalColors.current.badgeRed

    /** Local privacy tier indicator */
    val PrivacyLocal: Color get() = LocalTerminalColors.current.privacyLocal

    /** Cloud privacy tier indicator */
    val PrivacyCloud: Color get() = LocalTerminalColors.current.privacyCloud

    /** Anonymized privacy tier indicator */
    val PrivacyAnonymized: Color get() = LocalTerminalColors.current.privacyAnonymized
}
