package com.castor.app.desktop

/**
 * Represents the current display mode of the device.
 *
 * The app runs in one of two modes:
 * - [Phone]: Standard mobile layout (single column, bottom dock)
 * - [Desktop]: External display connected, switches to full desktop layout
 *   with multi-window support, dock on left, taskbar at bottom
 *
 * The display mode is detected by [DisplayModeDetector] and exposed as a
 * [StateFlow] for reactive UI composition.
 */
sealed interface DisplayMode {

    /** Standard phone/tablet mode — existing HomeScreen layout. */
    data object Phone : DisplayMode

    /**
     * Desktop mode — external display detected.
     *
     * @param displayId The Android Display ID of the external display
     * @param width Display width in pixels
     * @param height Display height in pixels
     * @param density Display density (DPI scale factor)
     * @param isDeX Whether Samsung DeX mode is active
     * @param connectionType How the external display is connected
     */
    data class Desktop(
        val displayId: Int,
        val width: Int,
        val height: Int,
        val density: Float,
        val isDeX: Boolean,
        val connectionType: ConnectionType
    ) : DisplayMode
}

/**
 * Describes how the external display is connected to the device.
 */
enum class ConnectionType {
    /** Samsung DeX desktop experience */
    DEX,

    /** USB-C to DisplayPort (wired) */
    USB_C_DISPLAYPORT,

    /** HDMI connection (typically via adapter) */
    HDMI,

    /** Miracast / wireless display (e.g. Chromecast, Smart TV) */
    MIRACAST,

    /** Unknown external display connection */
    UNKNOWN
}
