package com.castor.app.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.app.desktop.window.WindowManager
import com.castor.app.desktop.window.WindowState
import com.castor.core.ui.theme.TerminalColors

/**
 * Manages keyboard shortcuts for the desktop mode when an external keyboard
 * is connected (e.g., via USB-C, Bluetooth, or Samsung DeX dock keyboard).
 *
 * Supports the following shortcuts (modeled after Ubuntu/GNOME):
 *
 * | Shortcut         | Action                              |
 * |------------------|-------------------------------------|
 * | Super+Left       | Tile active window to left half     |
 * | Super+Right      | Tile active window to right half    |
 * | Super+Up         | Maximize active window              |
 * | Super+Down       | Restore/unmaximize active window    |
 * | Alt+Tab          | Switch to next window               |
 * | Super+D          | Show desktop (minimize all)         |
 * | Super (tap)      | Show shortcut overlay               |
 * | Alt+F4           | Close active window                 |
 *
 * The shortcut handler is implemented as a Compose [Modifier.onPreviewKeyEvent]
 * modifier that intercepts key events before they reach the focused composable.
 *
 * Usage:
 * ```kotlin
 * Box(
 *     modifier = Modifier
 *         .fillMaxSize()
 *         .then(keyboardShortcutManager.keyEventModifier(windowManager))
 * )
 * ```
 */
class KeyboardShortcutManager {

    /**
     * Whether the keyboard shortcut overlay is currently visible.
     * Set to true when the Super key is tapped alone (without a combo).
     */
    var isOverlayVisible by mutableStateOf(false)
        private set

    /**
     * Toggles the shortcut overlay visibility.
     */
    fun toggleOverlay() {
        isOverlayVisible = !isOverlayVisible
    }

    /**
     * Dismisses the shortcut overlay.
     */
    fun dismissOverlay() {
        isOverlayVisible = false
    }

    /**
     * Creates a Compose [Modifier] that intercepts key events and maps
     * them to window management actions.
     *
     * @param windowManager The desktop [WindowManager] to dispatch actions to
     * @param onActivities Callback to open the Activities/app drawer overlay
     * @return A modifier that handles keyboard shortcuts
     */
    fun keyEventModifier(
        windowManager: WindowManager,
        onActivities: () -> Unit = {}
    ): Modifier {
        return Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

            val activeId = windowManager.state.value.activeWindowId

            when {
                // Super+Left: Tile left
                event.isMetaPressed && event.key == Key.DirectionLeft -> {
                    activeId?.let { windowManager.tileWindow(it, WindowState.TiledLeft) }
                    dismissOverlay()
                    true
                }

                // Super+Right: Tile right
                event.isMetaPressed && event.key == Key.DirectionRight -> {
                    activeId?.let { windowManager.tileWindow(it, WindowState.TiledRight) }
                    dismissOverlay()
                    true
                }

                // Super+Up: Maximize
                event.isMetaPressed && event.key == Key.DirectionUp -> {
                    activeId?.let { windowManager.tileWindow(it, WindowState.Maximized) }
                    dismissOverlay()
                    true
                }

                // Super+Down: Restore / un-maximize
                event.isMetaPressed && event.key == Key.DirectionDown -> {
                    activeId?.let { windowManager.tileWindow(it, WindowState.Normal) }
                    dismissOverlay()
                    true
                }

                // Alt+Tab: Switch windows
                event.isAltPressed && event.key == Key.Tab -> {
                    windowManager.switchToNextWindow()
                    dismissOverlay()
                    true
                }

                // Super+D: Show desktop (minimize all)
                event.isMetaPressed && event.key == Key.D -> {
                    windowManager.showDesktop()
                    dismissOverlay()
                    true
                }

                // Alt+F4: Close active window
                event.isAltPressed && event.key == Key.F4 -> {
                    activeId?.let { windowManager.closeWindow(it) }
                    dismissOverlay()
                    true
                }

                // Super (alone): Toggle shortcut overlay
                event.key == Key.MetaLeft || event.key == Key.MetaRight -> {
                    // Only toggle on key-up of Super without combo
                    // (handled by checking no other keys pressed)
                    if (!event.isAltPressed) {
                        toggleOverlay()
                    }
                    true
                }

                // Super+A: Activities / app drawer
                event.isMetaPressed && event.key == Key.A -> {
                    onActivities()
                    dismissOverlay()
                    true
                }

                else -> false
            }
        }
    }
}

/**
 * Overlay composable that shows available keyboard shortcuts.
 *
 * Displayed when the user taps the Super key alone (without a combo),
 * similar to GNOME's shortcut overlay. Shows all available shortcuts
 * in a terminal-styled card.
 *
 * @param isVisible Whether the overlay is currently shown
 * @param onDismiss Callback to close the overlay
 */
@Composable
fun KeyboardShortcutOverlay(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background.copy(alpha = 0.85f))
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(TerminalColors.Surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "# keyboard-shortcuts",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Accent
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            val shortcuts = listOf(
                "Super + Left" to "Tile window left",
                "Super + Right" to "Tile window right",
                "Super + Up" to "Maximize window",
                "Super + Down" to "Restore window",
                "Alt + Tab" to "Switch windows",
                "Super + D" to "Show desktop",
                "Alt + F4" to "Close window",
                "Super + A" to "Activities / app drawer",
                "Super" to "Toggle this overlay"
            )

            shortcuts.forEach { (shortcut, description) ->
                ShortcutRow(shortcut = shortcut, description = description)
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "press any key to dismiss",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }
    }
}

/**
 * A single row in the shortcut overlay showing a key combination
 * and its description.
 */
@Composable
private fun ShortcutRow(
    shortcut: String,
    description: String
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(360.dp)
    ) {
        // Key badge
        Row {
            shortcut.split(" + ").forEach { key ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(TerminalColors.Selection)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = key,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Command
                        )
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
        }

        // Description
        Text(
            text = description,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}
