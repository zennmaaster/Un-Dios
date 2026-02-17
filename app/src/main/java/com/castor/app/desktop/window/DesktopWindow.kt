package com.castor.app.desktop.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents a single "window" in the desktop mode layout.
 *
 * In desktop mode, Un-Dios provides a tiling window manager experience
 * where multiple windows can be arranged across the workspace. Each window
 * has its own title bar chrome (minimize/maximize/close), can be tiled to
 * various screen regions, and maintains its own z-order for stacking.
 *
 * The [content] lambda is a Composable that renders the actual window body
 * (e.g., Messages screen, Terminal, Media player). The window frame/chrome
 * is rendered separately by [WindowFrame].
 *
 * @param id Unique identifier for this window instance
 * @param title Window title displayed in the title bar and taskbar
 * @param icon Icon displayed in the title bar and taskbar
 * @param content The Composable content rendered inside the window
 * @param state Current window state (normal, maximized, minimized, tiled)
 * @param bounds Position and size of the window as fractions of the workspace
 * @param isActive Whether this window is currently focused/active
 * @param zOrder Stacking order — higher values render on top
 */
data class DesktopWindow(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val content: @Composable () -> Unit,
    val state: WindowState = WindowState.Normal,
    val bounds: WindowBounds = WindowBounds(),
    val isActive: Boolean = false,
    val zOrder: Int = 0
)

/**
 * Position and size of a desktop window, expressed as fractions of the
 * available workspace area (0.0 to 1.0).
 *
 * Using fractional coordinates allows the layout to scale naturally when
 * the workspace size changes (e.g., different external display resolutions).
 *
 * @param x Left edge position as a fraction of workspace width (0.0 = left edge)
 * @param y Top edge position as a fraction of workspace height (0.0 = top edge)
 * @param width Window width as a fraction of workspace width (1.0 = full width)
 * @param height Window height as a fraction of workspace height (1.0 = full height)
 */
data class WindowBounds(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0.5f,
    val height: Float = 1f
)

/**
 * Describes the current visual state of a desktop window.
 *
 * Supports standard window states as well as tiling positions inspired
 * by Ubuntu/GNOME's keyboard-driven tiling (Super+Arrow keys):
 *
 * - [Normal]: Free-positioned window at its [WindowBounds] coordinates
 * - [Maximized]: Fills the entire workspace area
 * - [Minimized]: Hidden from the workspace but visible in the taskbar
 * - Tiled states: Window snapped to half or quarter of the workspace
 */
enum class WindowState {
    /** Window at its specified bounds, with title bar and resize handles. */
    Normal,

    /** Window fills the entire workspace (no margins). */
    Maximized,

    /** Window hidden from workspace, icon shown in taskbar. */
    Minimized,

    /** Tiled to the left half of the workspace. */
    TiledLeft,

    /** Tiled to the right half of the workspace. */
    TiledRight,

    /** Tiled to the top-left quarter of the workspace. */
    TiledTopLeft,

    /** Tiled to the top-right quarter of the workspace. */
    TiledTopRight,

    /** Tiled to the bottom-left quarter of the workspace. */
    TiledBottomLeft,

    /** Tiled to the bottom-right quarter of the workspace. */
    TiledBottomRight
}

/**
 * Computes the effective [WindowBounds] for a given [WindowState].
 *
 * For tiled and maximized states, the bounds are predefined fractions
 * of the workspace. For Normal and Minimized, the window's own bounds
 * are returned unchanged.
 */
fun WindowState.toEffectiveBounds(originalBounds: WindowBounds): WindowBounds {
    return when (this) {
        WindowState.Normal -> originalBounds
        WindowState.Maximized -> WindowBounds(x = 0f, y = 0f, width = 1f, height = 1f)
        WindowState.Minimized -> originalBounds // Position preserved for restore
        WindowState.TiledLeft -> WindowBounds(x = 0f, y = 0f, width = 0.5f, height = 1f)
        WindowState.TiledRight -> WindowBounds(x = 0.5f, y = 0f, width = 0.5f, height = 1f)
        WindowState.TiledTopLeft -> WindowBounds(x = 0f, y = 0f, width = 0.5f, height = 0.5f)
        WindowState.TiledTopRight -> WindowBounds(x = 0.5f, y = 0f, width = 0.5f, height = 0.5f)
        WindowState.TiledBottomLeft -> WindowBounds(x = 0f, y = 0.5f, width = 0.5f, height = 0.5f)
        WindowState.TiledBottomRight -> WindowBounds(x = 0.5f, y = 0.5f, width = 0.5f, height = 0.5f)
    }
}
