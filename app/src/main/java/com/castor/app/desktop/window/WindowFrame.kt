package com.castor.app.desktop.window

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.theme.TerminalColors

/**
 * Window chrome / frame for a desktop window.
 *
 * Renders Ubuntu-style window decorations with a terminal aesthetic:
 * - Title bar with app icon, window title (monospace), and window controls
 * - Window controls: minimize (yellow), maximize/restore (green), close (red)
 *   styled as small colored circles (similar to macOS/Ubuntu close/minimize/maximize)
 * - Draggable title bar for repositioning
 * - Double-click title bar to toggle maximize
 * - Shadow and rounded corners for the overall window frame
 * - Active/inactive visual states (brighter title bar when focused)
 *
 * The window body (content) is rendered below the title bar inside
 * a clipped container.
 *
 * @param window The [DesktopWindow] data to render
 * @param onClose Called when the close button is clicked
 * @param onMinimize Called when the minimize button is clicked
 * @param onToggleMaximize Called when maximize is toggled (button or double-click title bar)
 * @param onFocus Called when the window is clicked anywhere (bring to front)
 * @param onDrag Called with delta position during title bar drag
 * @param modifier Modifier for the outer frame container
 */
@Composable
fun WindowFrame(
    window: DesktopWindow,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onFocus: () -> Unit,
    onDrag: (deltaX: Float, deltaY: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = window.isActive

    val titleBarColor by animateColorAsState(
        targetValue = if (isActive) TerminalColors.StatusBar else TerminalColors.Surface,
        animationSpec = tween(durationMillis = 200),
        label = "titleBarColor"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isActive) TerminalColors.Accent.copy(alpha = 0.3f)
        else TerminalColors.Surface.copy(alpha = 0.5f),
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )

    val shadowElevation = if (isActive) 8.dp else 2.dp

    Column(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(8.dp),
                ambientColor = if (isActive) TerminalColors.Accent.copy(alpha = 0.1f)
                else Color.Black.copy(alpha = 0.2f)
            )
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { onFocus() }
                )
            }
    ) {
        // ====================================================================
        // Title Bar
        // ====================================================================
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(titleBarColor)
                .height(32.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onToggleMaximize() }
                    )
                }
                .padding(horizontal = 8.dp)
        ) {
            // Window control buttons (Ubuntu/macOS style: close, minimize, maximize)
            WindowControlButton(
                color = TerminalColors.Error,
                onClick = onClose,
                contentDescription = "Close window"
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = TerminalColors.Background,
                    modifier = Modifier.size(8.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            WindowControlButton(
                color = TerminalColors.Warning,
                onClick = onMinimize,
                contentDescription = "Minimize window"
            ) {
                Icon(
                    imageVector = Icons.Default.Minimize,
                    contentDescription = null,
                    tint = TerminalColors.Background,
                    modifier = Modifier.size(8.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            WindowControlButton(
                color = TerminalColors.Success,
                onClick = onToggleMaximize,
                contentDescription = if (window.state == WindowState.Maximized) {
                    "Restore window"
                } else {
                    "Maximize window"
                }
            ) {
                Icon(
                    imageVector = if (window.state == WindowState.Maximized) {
                        Icons.Default.FullscreenExit
                    } else {
                        Icons.Default.Fullscreen
                    },
                    contentDescription = null,
                    tint = TerminalColors.Background,
                    modifier = Modifier.size(8.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Window icon
            Icon(
                imageVector = window.icon,
                contentDescription = null,
                tint = if (isActive) TerminalColors.Accent else TerminalColors.Timestamp,
                modifier = Modifier.size(14.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Window title
            Text(
                text = window.title,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) TerminalColors.Command else TerminalColors.Timestamp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // ====================================================================
        // Window Content
        // ====================================================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TerminalColors.Background)
        ) {
            window.content()
        }
    }
}

/**
 * Circular window control button styled after Ubuntu/macOS window buttons.
 *
 * Renders as a small colored circle that reveals its icon on hover.
 * In this terminal-aesthetic implementation, icons are always subtly visible.
 *
 * @param color The button's background color (red for close, yellow for minimize, green for maximize)
 * @param onClick Action when the button is clicked
 * @param contentDescription Accessibility description
 * @param icon The icon composable to render inside the button
 */
@Composable
private fun WindowControlButton(
    color: Color,
    onClick: () -> Unit,
    contentDescription: String,
    icon: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            icon()
        }
    }
}
