package com.castor.app.desktop.transition

import com.castor.app.desktop.DisplayMode
import com.castor.app.desktop.window.WindowManager
import com.castor.app.desktop.window.WindowManagerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages smooth transitions between Phone and Desktop display modes.
 *
 * When the device connects/disconnects an external display, this manager
 * handles state preservation and restoration:
 *
 * - **Phone -> Desktop**: Saves the current phone navigation state, then
 *   initializes the desktop layout with a default terminal window.
 * - **Desktop -> Phone**: Saves desktop window state (positions, active
 *   windows), restores the phone navigation to its previous state.
 *
 * The transition itself is animated at the Compose level by
 * [AdaptiveLayoutManager], but this class handles the logical state
 * management underneath.
 *
 * State persistence strategy:
 * - Phone state: remembered via Compose state (survives recomposition)
 * - Desktop state: managed by [WindowManager] singleton (survives config changes)
 * - Both: Do NOT survive process death (acceptable for display mode which
 *   requires physical connection change)
 *
 * Configuration change handling:
 * - The Activity is NOT recreated on display mode change (handled via
 *   `android:configChanges` if needed, but Compose's reactive model
 *   handles this naturally through StateFlow observation).
 * - The ViewModel and WindowManager singletons survive config changes,
 *   so window state is preserved.
 */
@Singleton
class ModeTransitionManager @Inject constructor(
    private val windowManager: WindowManager
) {
    /**
     * Saved phone navigation route so we can restore it when returning
     * from desktop mode. Null means "home" (default).
     */
    private val _savedPhoneRoute = MutableStateFlow<String?>(null)
    val savedPhoneRoute: StateFlow<String?> = _savedPhoneRoute.asStateFlow()

    /**
     * Saved desktop window state for restoration when re-entering desktop mode.
     * Null means no previous desktop state (first time entering desktop).
     */
    private val _savedDesktopState = MutableStateFlow<WindowManagerState?>(null)
    val savedDesktopState: StateFlow<WindowManagerState?> = _savedDesktopState.asStateFlow()

    /**
     * The previous display mode, used to detect transitions.
     */
    private var previousMode: DisplayMode = DisplayMode.Phone

    /**
     * Whether a transition is currently in progress.
     */
    private val _isTransitioning = MutableStateFlow(false)
    val isTransitioning: StateFlow<Boolean> = _isTransitioning.asStateFlow()

    /**
     * Called when the display mode changes. Handles saving/restoring
     * state for the transition.
     *
     * @param newMode The new display mode
     * @param currentPhoneRoute The current phone navigation route (if in phone mode)
     */
    fun onModeChange(newMode: DisplayMode, currentPhoneRoute: String? = null) {
        if (isSameMode(previousMode, newMode)) return

        _isTransitioning.value = true

        when {
            // Phone -> Desktop transition
            previousMode is DisplayMode.Phone && newMode is DisplayMode.Desktop -> {
                // Save phone state
                _savedPhoneRoute.value = currentPhoneRoute

                // Restore previous desktop state if available, otherwise start fresh
                val savedState = _savedDesktopState.value
                if (savedState != null && savedState.windows.isNotEmpty()) {
                    // Desktop state will be managed by WindowManager singleton
                    // which persists across mode changes
                }
            }

            // Desktop -> Phone transition
            previousMode is DisplayMode.Desktop && newMode is DisplayMode.Phone -> {
                // Save desktop window state
                _savedDesktopState.value = windowManager.state.value
            }
        }

        previousMode = newMode
        _isTransitioning.value = false
    }

    /**
     * Checks if two display modes are functionally the same
     * (both Phone, or both Desktop regardless of display details).
     */
    private fun isSameMode(a: DisplayMode, b: DisplayMode): Boolean {
        return (a is DisplayMode.Phone && b is DisplayMode.Phone) ||
            (a is DisplayMode.Desktop && b is DisplayMode.Desktop)
    }

    /**
     * Resets all saved state. Called when the app is being fully destroyed
     * (not just a config change).
     */
    fun reset() {
        _savedPhoneRoute.value = null
        _savedDesktopState.value = null
        previousMode = DisplayMode.Phone
        _isTransitioning.value = false
    }
}
