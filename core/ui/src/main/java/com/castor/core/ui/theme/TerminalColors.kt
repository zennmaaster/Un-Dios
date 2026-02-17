package com.castor.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Catppuccin Mocha-inspired color palette for the Castor terminal experience.
 * These colors provide the Ubuntu/Linux desktop feel with a modern twist.
 * Always used regardless of the system theme — the terminal is always dark.
 */
object TerminalColors {
    /** Deep dark base background — Catppuccin Mocha "Base" */
    val Background = Color(0xFF1E1E2E)

    /** Slightly lighter surface for cards and elevated elements */
    val Surface = Color(0xFF313244)

    /** Green prompt text — the classic terminal prompt color */
    val Prompt = Color(0xFFA6E3A1)

    /** Light text for user-typed commands */
    val Command = Color(0xFFCDD6F4)

    /** Slightly dimmer text for command output / responses */
    val Output = Color(0xFFBAC2DE)

    /** Red for error messages and failed commands */
    val Error = Color(0xFFF38BA8)

    /** Orange for warnings and caution indicators */
    val Warning = Color(0xFFFAB387)

    /** Green for success confirmations */
    val Success = Color(0xFFA6E3A1)

    /** Blue for informational messages */
    val Info = Color(0xFF89B4FA)

    /** Purple accent — Castor brand identity within the terminal */
    val Accent = Color(0xFFCBA6F7)

    /** Warm white cursor color */
    val Cursor = Color(0xFFF5E0DC)

    /** Selection highlight for text selection */
    val Selection = Color(0xFF45475A)

    /** Even darker color for the top status bar panel */
    val StatusBar = Color(0xFF181825)

    /** Dim color for timestamps and secondary metadata */
    val Timestamp = Color(0xFF6C7086)

    /** Overlay color for semi-transparent backgrounds (dock, panels) */
    val Overlay = Color(0xFF11111B)

    /** Subtext for less important UI elements */
    val Subtext = Color(0xFF585B70)

    /** Badge background for notification counts */
    val BadgeRed = Color(0xFFF38BA8)

    /** Local privacy tier indicator */
    val PrivacyLocal = Color(0xFFA6E3A1)

    /** Cloud privacy tier indicator */
    val PrivacyCloud = Color(0xFFF9E2AF)

    /** Anonymized privacy tier indicator */
    val PrivacyAnonymized = Color(0xFF89B4FA)
}
