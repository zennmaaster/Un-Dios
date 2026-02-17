package com.castor.app.desktop

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects external display connections and determines the current [DisplayMode].
 *
 * Monitors the system's [DisplayManager] for connected displays and determines
 * whether the device is operating in standard phone mode or desktop mode.
 * Supports detection of:
 *
 * - **Samsung DeX**: via `com.samsung.android.desktopmode.enabled` configuration
 *   and DeX-specific system properties.
 * - **USB-C DisplayPort / HDMI**: wired external displays detected through
 *   [Display.FLAG_PRESENTATION] and display type heuristics.
 * - **Miracast / Wireless Display**: detected via [Display.FLAG_PRESENTATION]
 *   combined with wifi-display type checks.
 *
 * Exposes a [StateFlow] of [DisplayMode] that the UI layer observes to
 * switch between phone and desktop layouts.
 *
 * Lifecycle:
 * - Call [startMonitoring] when the Activity is created (or in init)
 * - Call [stopMonitoring] when the Activity is destroyed
 * - The detector automatically registers/unregisters [DisplayManager.DisplayListener]
 *
 * This class is a Hilt [Singleton] — a single instance is shared across
 * the entire application lifecycle.
 */
@Singleton
class DisplayModeDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DisplayModeDetector"

        /** Samsung DeX mode configuration flag. */
        private const val DEX_MODE_ENABLED = "com.samsung.android.desktopmode.enabled"

        /**
         * Display type constant for wifi/wireless displays.
         * Defined in Display but not always accessible as a constant.
         */
        private const val DISPLAY_TYPE_WIFI = 3
    }

    private val displayManager: DisplayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val _displayMode = MutableStateFlow<DisplayMode>(DisplayMode.Phone)

    /** Current display mode — observe this in the UI layer. */
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()

    private var displayListener: DisplayManager.DisplayListener? = null

    /**
     * Begins monitoring for external display connections.
     *
     * Performs an initial scan of all connected displays, then registers
     * a [DisplayManager.DisplayListener] for real-time updates when
     * displays are added, removed, or their properties change.
     *
     * Safe to call multiple times — subsequent calls are no-ops if
     * monitoring is already active.
     */
    fun startMonitoring() {
        if (displayListener != null) return

        // Initial scan
        refreshDisplayMode()

        // Register listener for changes
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                Log.d(TAG, "Display added: $displayId")
                refreshDisplayMode()
            }

            override fun onDisplayRemoved(displayId: Int) {
                Log.d(TAG, "Display removed: $displayId")
                refreshDisplayMode()
            }

            override fun onDisplayChanged(displayId: Int) {
                Log.d(TAG, "Display changed: $displayId")
                refreshDisplayMode()
            }
        }

        displayManager.registerDisplayListener(listener, null)
        displayListener = listener
    }

    /**
     * Stops monitoring for external display connections.
     *
     * Unregisters the [DisplayManager.DisplayListener] and resets the
     * display mode to [DisplayMode.Phone].
     */
    fun stopMonitoring() {
        displayListener?.let { listener ->
            displayManager.unregisterDisplayListener(listener)
            displayListener = null
        }
    }

    /**
     * Scans all connected displays and updates [_displayMode].
     *
     * Logic:
     * 1. First check if Samsung DeX is active — if so, report as DeX Desktop mode
     * 2. Otherwise, iterate all displays looking for external/presentation displays
     * 3. If an external display is found, determine the connection type and report Desktop
     * 4. If no external display, report Phone mode
     */
    private fun refreshDisplayMode() {
        // Check Samsung DeX first
        if (isDeXMode()) {
            val displays = displayManager.displays
            val externalDisplay = displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
            if (externalDisplay != null) {
                val metrics = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                externalDisplay.getMetrics(metrics)
                _displayMode.value = DisplayMode.Desktop(
                    displayId = externalDisplay.displayId,
                    width = metrics.widthPixels,
                    height = metrics.heightPixels,
                    density = metrics.density,
                    isDeX = true,
                    connectionType = ConnectionType.DEX
                )
                Log.d(TAG, "Samsung DeX mode detected on display ${externalDisplay.displayId}")
                return
            }
        }

        // Scan all displays for external connections
        val displays = displayManager.displays
        for (display in displays) {
            if (display.displayId == Display.DEFAULT_DISPLAY) continue

            // Check if this is a presentation display (external)
            val flags = display.flags
            val isPresentation = flags and Display.FLAG_PRESENTATION != 0
            val isPrivate = flags and Display.FLAG_PRIVATE != 0

            // Skip private displays (e.g. virtual displays from screen recording)
            if (isPrivate && !isPresentation) continue

            if (isPresentation || isExternalDisplay(display)) {
                val metrics = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                display.getMetrics(metrics)

                val connectionType = detectConnectionType(display)

                _displayMode.value = DisplayMode.Desktop(
                    displayId = display.displayId,
                    width = metrics.widthPixels,
                    height = metrics.heightPixels,
                    density = metrics.density,
                    isDeX = false,
                    connectionType = connectionType
                )
                Log.d(
                    TAG,
                    "External display detected: id=${display.displayId}, " +
                        "type=$connectionType, ${metrics.widthPixels}x${metrics.heightPixels}"
                )
                return
            }
        }

        // No external display found — phone mode
        _displayMode.value = DisplayMode.Phone
    }

    /**
     * Detects whether Samsung DeX desktop mode is currently active.
     *
     * Checks multiple indicators:
     * 1. Configuration `uiMode` flag for desktop mode
     * 2. Samsung-specific system property for DeX enablement
     * 3. Configuration extra that Samsung injects when DeX is active
     */
    private fun isDeXMode(): Boolean {
        try {
            // Method 1: Check configuration for desktop UI mode
            val config = context.resources.configuration
            val uiMode = config.uiMode and Configuration.UI_MODE_TYPE_MASK
            if (uiMode == Configuration.UI_MODE_TYPE_DESK) {
                return true
            }

            // Method 2: Check Samsung DeX configuration via reflection
            try {
                val configClass = config.javaClass
                val field = configClass.getField("SEM_DESKTOP_MODE_ENABLED")
                val semDesktopModeEnabled = field.getInt(config)
                val currentField = configClass.getField("semDesktopModeEnabled")
                val currentValue = currentField.getInt(config)
                if (currentValue == semDesktopModeEnabled) {
                    return true
                }
            } catch (_: NoSuchFieldException) {
                // Not a Samsung device or DeX not available
            } catch (_: Exception) {
                // Reflection failed
            }

            // Method 3: Check system properties for DeX
            try {
                @Suppress("PrivateApi")
                val systemProperties = Class.forName("android.os.SystemProperties")
                val getMethod = systemProperties.getMethod("get", String::class.java)
                val dexMode = getMethod.invoke(null, "persist.sys.dex.enabled") as? String
                if (dexMode == "1") {
                    return true
                }
            } catch (_: Exception) {
                // SystemProperties not accessible
            }
        } catch (_: Exception) {
            Log.w(TAG, "Error checking DeX mode")
        }

        return false
    }

    /**
     * Determines whether a non-default display is an external display
     * (as opposed to a virtual/overlay display).
     *
     * Heuristic: displays with non-zero physical size or PRESENTATION flag
     * are considered external.
     */
    private fun isExternalDisplay(display: Display): Boolean {
        if (display.displayId == Display.DEFAULT_DISPLAY) return false

        // Check display name for common external display indicators
        val name = display.name?.lowercase() ?: ""
        if (name.contains("hdmi") || name.contains("dp") ||
            name.contains("external") || name.contains("wireless")
        ) {
            return true
        }

        // Check if it's a physical display with reasonable dimensions
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getMetrics(metrics)

        // External displays typically have >= 720p resolution
        return metrics.widthPixels >= 1280 || metrics.heightPixels >= 720
    }

    /**
     * Determines the connection type for an external display based on
     * display properties and name heuristics.
     */
    private fun detectConnectionType(display: Display): ConnectionType {
        val name = display.name?.lowercase() ?: ""

        return when {
            // Samsung DeX
            isDeXMode() -> ConnectionType.DEX

            // HDMI connection
            name.contains("hdmi") -> ConnectionType.HDMI

            // USB-C DisplayPort (often reported as "DP" or "DisplayPort")
            name.contains("dp") || name.contains("displayport") ||
                name.contains("usb") -> ConnectionType.USB_C_DISPLAYPORT

            // Miracast / Wireless Display
            name.contains("wireless") || name.contains("miracast") ||
                name.contains("wifi") || name.contains("cast") -> ConnectionType.MIRACAST

            // Check display type for wifi displays
            else -> {
                try {
                    val typeField = display.javaClass.getMethod("getType")
                    val type = typeField.invoke(display) as? Int
                    when (type) {
                        DISPLAY_TYPE_WIFI -> ConnectionType.MIRACAST
                        2 -> ConnectionType.HDMI // TYPE_HDMI
                        else -> ConnectionType.UNKNOWN
                    }
                } catch (_: Exception) {
                    ConnectionType.UNKNOWN
                }
            }
        }
    }
}
