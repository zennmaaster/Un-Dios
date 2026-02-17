package com.castor.app.desktop.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages desktop windows in Un-Dios desktop mode.
 *
 * The WindowManager provides a tiling window manager experience:
 * - Opening/closing windows with unique IDs
 * - Focusing windows (brings to front, updates z-order)
 * - Minimizing, maximizing, and restoring windows
 * - Tiling windows to screen halves and quarters
 * - Alt+Tab-style window switching
 * - Show Desktop (minimize all)
 *
 * All state is held in a [MutableStateFlow] of [WindowManagerState],
 * which the Compose UI layer observes for reactive rendering.
 *
 * The WindowManager is a Hilt [Singleton] — a single instance manages
 * all windows across the desktop session.
 */
@Singleton
class WindowManager @Inject constructor() {

    private val _state = MutableStateFlow(WindowManagerState())

    /** Observable state of all managed windows. */
    val state: StateFlow<WindowManagerState> = _state.asStateFlow()

    /** Counter for generating z-order values. */
    private var nextZOrder = 1

    /**
     * Opens a new window or focuses it if it already exists.
     *
     * If a window with the given [id] is already open, it is focused
     * (brought to front, unminimized if needed). Otherwise, a new window
     * is created with the specified properties and added to the workspace.
     *
     * @param id Unique window identifier (e.g., "terminal", "messages")
     * @param title Window title for the title bar
     * @param icon Window icon for the title bar and taskbar
     * @param initialState Initial window state (default: TiledLeft for first, TiledRight for second)
     * @param content Composable content to render inside the window
     */
    fun openWindow(
        id: String,
        title: String,
        icon: ImageVector,
        initialState: WindowState? = null,
        content: @Composable () -> Unit
    ) {
        _state.update { currentState ->
            val existingIndex = currentState.windows.indexOfFirst { it.id == id }
            if (existingIndex >= 0) {
                // Window already exists — focus it
                val windows = currentState.windows.toMutableList()
                val existing = windows[existingIndex]
                val restored = if (existing.state == WindowState.Minimized) {
                    existing.copy(state = WindowState.Normal)
                } else {
                    existing
                }
                windows[existingIndex] = restored.copy(
                    isActive = true,
                    zOrder = nextZOrder++
                )
                // Deactivate all other windows
                for (i in windows.indices) {
                    if (i != existingIndex) {
                        windows[i] = windows[i].copy(isActive = false)
                    }
                }
                currentState.copy(
                    windows = windows,
                    activeWindowId = id
                )
            } else {
                // Create new window
                val windowCount = currentState.windows.size
                val state = initialState ?: when {
                    windowCount == 0 -> WindowState.Maximized
                    windowCount == 1 -> WindowState.TiledRight
                    else -> WindowState.Normal
                }

                val bounds = when {
                    windowCount == 0 -> WindowBounds(x = 0f, y = 0f, width = 1f, height = 1f)
                    windowCount == 1 -> WindowBounds(x = 0.5f, y = 0f, width = 0.5f, height = 1f)
                    else -> WindowBounds(
                        x = 0.05f * windowCount,
                        y = 0.05f * windowCount,
                        width = 0.6f,
                        height = 0.7f
                    )
                }

                // If opening a second window, tile the first window left
                val updatedWindows = if (windowCount == 1 && initialState == null) {
                    currentState.windows.map { w ->
                        w.copy(
                            state = WindowState.TiledLeft,
                            bounds = WindowBounds(x = 0f, y = 0f, width = 0.5f, height = 1f),
                            isActive = false
                        )
                    }
                } else {
                    currentState.windows.map { w -> w.copy(isActive = false) }
                }

                val newWindow = DesktopWindow(
                    id = id,
                    title = title,
                    icon = icon,
                    content = content,
                    state = state,
                    bounds = bounds,
                    isActive = true,
                    zOrder = nextZOrder++
                )

                currentState.copy(
                    windows = updatedWindows + newWindow,
                    activeWindowId = id
                )
            }
        }
    }

    /**
     * Closes and removes a window from the workspace.
     *
     * If the closed window was the active window, the next highest
     * z-order window becomes active.
     */
    fun closeWindow(id: String) {
        _state.update { currentState ->
            val remaining = currentState.windows.filter { it.id != id }
            val newActive = if (currentState.activeWindowId == id) {
                remaining.maxByOrNull { it.zOrder }?.let { topWindow ->
                    remaining.map { w ->
                        w.copy(isActive = w.id == topWindow.id)
                    }
                } ?: remaining
            } else {
                remaining
            }

            currentState.copy(
                windows = newActive,
                activeWindowId = newActive.firstOrNull { it.isActive }?.id
            )
        }
    }

    /**
     * Brings a window to the front and makes it the active window.
     */
    fun focusWindow(id: String) {
        _state.update { currentState ->
            val windows = currentState.windows.map { w ->
                if (w.id == id) {
                    val restored = if (w.state == WindowState.Minimized) {
                        w.copy(state = WindowState.Normal)
                    } else {
                        w
                    }
                    restored.copy(isActive = true, zOrder = nextZOrder++)
                } else {
                    w.copy(isActive = false)
                }
            }
            currentState.copy(
                windows = windows,
                activeWindowId = id
            )
        }
    }

    /**
     * Minimizes a window — hides it from the workspace but keeps it in the taskbar.
     */
    fun minimizeWindow(id: String) {
        _state.update { currentState ->
            val windows = currentState.windows.map { w ->
                if (w.id == id) {
                    w.copy(state = WindowState.Minimized, isActive = false)
                } else {
                    w
                }
            }
            // Activate the next top-most visible window
            val topVisible = windows
                .filter { it.state != WindowState.Minimized }
                .maxByOrNull { it.zOrder }
            val finalWindows = if (topVisible != null) {
                windows.map { w ->
                    w.copy(isActive = w.id == topVisible.id)
                }
            } else {
                windows
            }

            currentState.copy(
                windows = finalWindows,
                activeWindowId = topVisible?.id
            )
        }
    }

    /**
     * Toggles a window between maximized and normal state.
     */
    fun toggleMaximize(id: String) {
        _state.update { currentState ->
            val windows = currentState.windows.map { w ->
                if (w.id == id) {
                    val newState = if (w.state == WindowState.Maximized) {
                        WindowState.Normal
                    } else {
                        WindowState.Maximized
                    }
                    w.copy(state = newState)
                } else {
                    w
                }
            }
            currentState.copy(windows = windows)
        }
    }

    /**
     * Tiles the active window to the specified [WindowState].
     * Used for keyboard shortcuts like Super+Left, Super+Right.
     */
    fun tileWindow(id: String, tileState: WindowState) {
        _state.update { currentState ->
            val windows = currentState.windows.map { w ->
                if (w.id == id) {
                    w.copy(state = tileState)
                } else {
                    w
                }
            }
            currentState.copy(windows = windows)
        }
    }

    /**
     * Cycles focus to the next window (Alt+Tab behavior).
     *
     * Iterates windows by descending z-order, moving focus from the
     * currently active window to the next one in the stack.
     */
    fun switchToNextWindow() {
        _state.update { currentState ->
            val windows = currentState.windows
            if (windows.size < 2) return@update currentState

            val sorted = windows.sortedByDescending { it.zOrder }
            val currentIndex = sorted.indexOfFirst { it.isActive }
            val nextIndex = if (currentIndex >= 0) {
                (currentIndex + 1) % sorted.size
            } else {
                0
            }

            val nextWindowId = sorted[nextIndex].id
            val updatedWindows = windows.map { w ->
                val isNext = w.id == nextWindowId
                if (isNext && w.state == WindowState.Minimized) {
                    w.copy(state = WindowState.Normal, isActive = true, zOrder = nextZOrder++)
                } else {
                    w.copy(isActive = isNext, zOrder = if (isNext) nextZOrder++ else w.zOrder)
                }
            }

            currentState.copy(
                windows = updatedWindows,
                activeWindowId = nextWindowId
            )
        }
    }

    /**
     * Minimizes all windows — "Show Desktop" (Super+D).
     */
    fun showDesktop() {
        _state.update { currentState ->
            val allMinimized = currentState.windows.all { it.state == WindowState.Minimized }
            if (allMinimized) {
                // Restore all windows
                val windows = currentState.windows.map { w ->
                    w.copy(state = WindowState.Normal)
                }
                val topWindow = windows.maxByOrNull { it.zOrder }
                val finalWindows = windows.map { w ->
                    w.copy(isActive = w.id == topWindow?.id)
                }
                currentState.copy(
                    windows = finalWindows,
                    activeWindowId = topWindow?.id
                )
            } else {
                // Minimize all windows
                val windows = currentState.windows.map { w ->
                    w.copy(state = WindowState.Minimized, isActive = false)
                }
                currentState.copy(
                    windows = windows,
                    activeWindowId = null
                )
            }
        }
    }

    /**
     * Updates a window's bounds (used during drag/resize).
     */
    fun updateWindowBounds(id: String, bounds: WindowBounds) {
        _state.update { currentState ->
            val windows = currentState.windows.map { w ->
                if (w.id == id) {
                    w.copy(bounds = bounds, state = WindowState.Normal)
                } else {
                    w
                }
            }
            currentState.copy(windows = windows)
        }
    }
}

/**
 * Immutable snapshot of the window manager's complete state.
 *
 * @param windows All managed windows (visible, minimized, etc.)
 * @param activeWindowId The ID of the currently focused window (null if none)
 */
data class WindowManagerState(
    val windows: List<DesktopWindow> = emptyList(),
    val activeWindowId: String? = null
) {
    /** Windows that should be rendered in the workspace (not minimized). */
    val visibleWindows: List<DesktopWindow>
        get() = windows
            .filter { it.state != WindowState.Minimized }
            .sortedBy { it.zOrder }
}
