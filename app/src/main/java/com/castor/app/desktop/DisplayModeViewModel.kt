package com.castor.app.desktop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel that exposes the current [DisplayMode] to the Compose UI layer.
 *
 * Collects from [DisplayModeDetector.displayMode] and re-exposes it as a
 * [StateFlow] scoped to this ViewModel's lifecycle. The UI layer uses this
 * to decide whether to render the phone layout (HomeScreen) or the desktop
 * layout (DesktopHomeScreen).
 *
 * The ViewModel is @HiltViewModel-injected and automatically receives
 * the singleton [DisplayModeDetector] via constructor injection.
 *
 * Usage in Compose:
 * ```
 * val viewModel: DisplayModeViewModel = hiltViewModel()
 * val displayMode by viewModel.displayMode.collectAsState()
 * when (displayMode) {
 *     is DisplayMode.Phone -> PhoneLayout()
 *     is DisplayMode.Desktop -> DesktopLayout(displayMode)
 * }
 * ```
 */
@HiltViewModel
class DisplayModeViewModel @Inject constructor(
    private val displayModeDetector: DisplayModeDetector
) : ViewModel() {

    /**
     * Current display mode, collected from the detector and shared
     * with WhileSubscribed semantics (stays active for 5s after last
     * subscriber disconnects, to survive quick config changes).
     */
    val displayMode: StateFlow<DisplayMode> = displayModeDetector.displayMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DisplayMode.Phone
        )

    /**
     * Whether the device is currently in desktop mode.
     * Convenience accessor for simple conditional checks.
     */
    val isDesktopMode: Boolean
        get() = displayModeDetector.displayMode.value is DisplayMode.Desktop

    /**
     * Start display monitoring. Called from the Activity when it starts.
     */
    fun startMonitoring() {
        displayModeDetector.startMonitoring()
    }

    /**
     * Stop display monitoring. Called from the Activity when it is destroyed.
     */
    fun stopMonitoring() {
        displayModeDetector.stopMonitoring()
    }
}
