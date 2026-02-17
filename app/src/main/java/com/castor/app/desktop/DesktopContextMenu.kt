package com.castor.app.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.theme.TerminalColors

/**
 * Terminal-styled right-click context menu for the desktop environment.
 *
 * When the user right-clicks (or long-presses with an external mouse/trackpad)
 * on the desktop workspace, this context menu appears with options appropriate
 * to the context.
 *
 * Two contexts are supported:
 * - **Desktop**: Right-clicking on empty workspace space shows desktop management
 *   options (change wallpaper, open terminal, display settings, refresh)
 * - **Window**: Right-clicking on a window shows window management options
 *   (currently not used, but extensible)
 *
 * Styling follows the terminal aesthetic:
 * - Dark surface background matching [TerminalColors.Surface]
 * - Monospace font for all menu items
 * - Commands prefixed with `$` like terminal commands
 * - Rounded corners with minimal shadow
 *
 * @param isVisible Whether the context menu is currently shown
 * @param onDismiss Callback to close the menu
 * @param onOpenTerminal Open a new terminal window
 * @param onChangeWallpaper Open wallpaper settings
 * @param onDisplaySettings Open display/settings
 * @param onRefresh Refresh the desktop (reload windows)
 * @param offset Position offset for the menu (near the click point)
 */
@Composable
fun DesktopContextMenu(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onOpenTerminal: () -> Unit,
    onChangeWallpaper: () -> Unit,
    onDisplaySettings: () -> Unit,
    onRefresh: () -> Unit,
    offset: DpOffset = DpOffset(0.dp, 0.dp)
) {
    DropdownMenu(
        expanded = isVisible,
        onDismissRequest = onDismiss,
        offset = offset,
        modifier = Modifier
            .background(
                color = TerminalColors.Surface,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        // Open Terminal
        ContextMenuItem(
            icon = Icons.Default.Terminal,
            label = "$ open-terminal",
            onClick = {
                onDismiss()
                onOpenTerminal()
            }
        )

        // Change Wallpaper
        ContextMenuItem(
            icon = Icons.Default.Image,
            label = "$ set-wallpaper",
            onClick = {
                onDismiss()
                onChangeWallpaper()
            }
        )

        // Display Settings
        ContextMenuItem(
            icon = Icons.Default.Settings,
            label = "$ display-settings",
            onClick = {
                onDismiss()
                onDisplaySettings()
            }
        )

        // Refresh
        ContextMenuItem(
            icon = Icons.Default.Refresh,
            label = "$ refresh",
            onClick = {
                onDismiss()
                onRefresh()
            }
        )
    }
}

/**
 * Terminal-styled right-click context menu for file/item operations.
 *
 * Shown when right-clicking on a specific item (file, folder, etc.)
 * within a window. Provides standard file operations styled as terminal
 * commands.
 *
 * @param isVisible Whether the context menu is currently shown
 * @param onDismiss Callback to close the menu
 * @param onOpen Open/launch the item
 * @param onCopy Copy the item
 * @param onPaste Paste clipboard contents
 * @param onDelete Delete the item
 * @param onSelectAll Select all items
 * @param offset Position offset for the menu
 */
@Composable
fun FileContextMenu(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
    offset: DpOffset = DpOffset(0.dp, 0.dp)
) {
    DropdownMenu(
        expanded = isVisible,
        onDismissRequest = onDismiss,
        offset = offset,
        modifier = Modifier
            .background(
                color = TerminalColors.Surface,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        ContextMenuItem(
            icon = Icons.Default.FolderOpen,
            label = "$ open",
            onClick = {
                onDismiss()
                onOpen()
            }
        )

        ContextMenuItem(
            icon = Icons.Default.ContentCopy,
            label = "$ cp",
            onClick = {
                onDismiss()
                onCopy()
            }
        )

        ContextMenuItem(
            icon = Icons.Default.ContentPaste,
            label = "$ paste",
            onClick = {
                onDismiss()
                onPaste()
            }
        )

        ContextMenuItem(
            icon = Icons.Default.SelectAll,
            label = "$ select-all",
            onClick = {
                onDismiss()
                onSelectAll()
            }
        )

        ContextMenuItem(
            icon = Icons.Default.Delete,
            label = "$ rm",
            color = TerminalColors.Error,
            onClick = {
                onDismiss()
                onDelete()
            }
        )
    }
}

/**
 * A single context menu item with terminal-command styling.
 *
 * Renders an icon + monospace label in the terminal color palette.
 * The `$` prefix is part of the label string itself, providing the
 * shell-command aesthetic.
 *
 * @param icon Material icon for the menu item
 * @param label Terminal-style label (e.g., "$ open-terminal")
 * @param color Text/icon color (defaults to [TerminalColors.Command])
 * @param onClick Action when the item is selected
 */
@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    label: String,
    color: Color = TerminalColors.Command,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.width(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = color
                    )
                )
            }
        },
        onClick = onClick
    )
}
