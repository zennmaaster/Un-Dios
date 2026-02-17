package com.castor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.castor.app.desktop.DisplayModeDetector
import com.castor.app.desktop.DisplayModeViewModel
import com.castor.app.desktop.layout.AdaptiveLayoutManager
import com.castor.app.desktop.layout.DesktopHomeScreen
import com.castor.app.desktop.window.WindowManager
import com.castor.app.navigation.CastorNavHost
import com.castor.core.ui.theme.CastorTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main Activity for the Un-Dios launcher.
 *
 * Serves as the single entry point for both phone and desktop modes.
 * On startup, the [DisplayModeDetector] begins monitoring for external
 * display connections. The top-level layout branches based on the
 * detected [DisplayMode]:
 *
 * - **Phone mode**: Renders the standard [CastorNavHost] with navigation-based
 *   single-screen flow (HomeScreen -> Messages -> Media -> etc.)
 * - **Desktop mode**: Renders [DesktopHomeScreen] with multi-window tiling
 *   layout, left dock, bottom taskbar, and window management.
 *
 * The transition between modes is animated via [AdaptiveLayoutManager]
 * using a crossfade + scale animation.
 *
 * Lifecycle integration:
 * - onCreate: Enables edge-to-edge, starts display monitoring, sets content
 * - onDestroy: Stops display monitoring to prevent leaks
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var displayModeDetector: DisplayModeDetector

    @Inject
    lateinit var windowManager: WindowManager

    private val displayModeViewModel: DisplayModeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start monitoring for external display connections
        displayModeViewModel.startMonitoring()

        setContent {
            CastorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AdaptiveLayoutManager(
                        displayModeViewModel = displayModeViewModel,
                        phoneContent = {
                            CastorNavHost()
                        },
                        desktopContent = { desktopMode ->
                            DesktopHomeScreen(
                                desktopMode = desktopMode,
                                windowManager = windowManager,
                                onNavigateToSettings = {
                                    // Settings in desktop mode could open as a window
                                    // For now, this is a no-op; settings window to be added
                                }
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop monitoring to prevent DisplayManager listener leaks
        displayModeViewModel.stopMonitoring()
    }
}
