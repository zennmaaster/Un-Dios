package com.castor.app.desktop.activities

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.app.desktop.window.DesktopWindow
import com.castor.app.desktop.window.WindowManager
import com.castor.core.ui.theme.TerminalColors

/**
 * GNOME-style Activities overview screen.
 *
 * A full-screen overlay composable that displays all open windows as scaled-down
 * previews in a grid layout, inspired by GNOME's Activities view. Users can:
 * - Click a window preview to focus that window
 * - Close windows via the (X) button on each preview
 * - Search/filter windows by title using the terminal-styled search bar
 * - Switch between two virtual workspaces
 *
 * The overlay animates in with a combined fade-in + scale-up (0.9 to 1.0)
 * transition for a polished feel.
 *
 * @param isVisible Whether the Activities overview is currently shown
 * @param onDismiss Callback to close the overview
 * @param windowManager The singleton window manager instance
 * @param onWindowClick Callback fired when a window preview is clicked, with the window ID
 */
@Composable
fun ActivitiesOverview(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    windowManager: WindowManager,
    onWindowClick: (String) -> Unit
) {
    val windowState by windowManager.state.collectAsState()

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f)
    ) {
        ActivitiesOverviewContent(
            windows = windowState.windows,
            onDismiss = onDismiss,
            onWindowClick = onWindowClick,
            onCloseWindow = { id -> windowManager.closeWindow(id) }
        )
    }
}

/**
 * Internal content layout for the Activities overview.
 *
 * Separated from the animated wrapper to keep the composable hierarchy clean.
 * Renders the search bar, workspace tabs, and the window preview grid.
 *
 * @param windows List of all open desktop windows
 * @param onDismiss Callback to close the overview
 * @param onWindowClick Callback when a window preview is clicked
 * @param onCloseWindow Callback to close a specific window by ID
 */
@Composable
private fun ActivitiesOverviewContent(
    windows: List<DesktopWindow>,
    onDismiss: () -> Unit,
    onWindowClick: (String) -> Unit,
    onCloseWindow: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var activeWorkspace by remember { mutableIntStateOf(0) }

    val filteredWindows = remember(windows, searchQuery) {
        if (searchQuery.isBlank()) {
            windows
        } else {
            windows.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.id.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Overlay.copy(alpha = 0.95f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- Header: Title + Search bar ----
            Text(
                text = "# activities-overview",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Accent
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Search bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "$ search-windows...",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = TerminalColors.Timestamp
                        )
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TerminalColors.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TerminalColors.Command
                ),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = TerminalColors.Surface,
                    unfocusedContainerColor = TerminalColors.Surface,
                    cursorColor = TerminalColors.Cursor,
                    focusedIndicatorColor = TerminalColors.Accent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Workspace tabs ----
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WorkspaceTab(
                    index = 0,
                    label = "workspace-0",
                    isActive = activeWorkspace == 0,
                    onClick = { activeWorkspace = 0 }
                )

                Spacer(modifier = Modifier.width(12.dp))

                WorkspaceTab(
                    index = 1,
                    label = "workspace-1",
                    isActive = activeWorkspace == 1,
                    onClick = { activeWorkspace = 1 }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- Window previews grid ----
            if (filteredWindows.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$ ps aux",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TerminalColors.Accent
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "no running processes",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = TerminalColors.Timestamp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "open an app from the dock to get started",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TerminalColors.Subtext
                            )
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 220.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(
                        items = filteredWindows,
                        key = { it.id }
                    ) { window ->
                        WindowPreviewCard(
                            window = window,
                            onClick = {
                                onWindowClick(window.id)
                                // Dismiss is handled by the parent
                            },
                            onClose = { onCloseWindow(window.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * A scaled-down preview card for a single window in the Activities overview.
 *
 * Shows the window's icon, title, and a close button. The card is clickable
 * to focus the window and dismiss the overview.
 *
 * @param window The desktop window to preview
 * @param onClick Callback when the preview card is clicked
 * @param onClose Callback when the close button is clicked
 */
@Composable
private fun WindowPreviewCard(
    window: DesktopWindow,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(TerminalColors.Surface)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(12.dp)
        ) {
            // Top row: icon + title + close button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = window.icon,
                    contentDescription = null,
                    tint = TerminalColors.Accent,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = window.title,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Command
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Close button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(TerminalColors.Error.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close window",
                        tint = TerminalColors.Error,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Window content preview area (placeholder representation)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerminalColors.Background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = window.icon,
                        contentDescription = null,
                        tint = TerminalColors.Accent.copy(alpha = 0.4f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "PID: ${window.id.hashCode() and 0xFFFF}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = TerminalColors.Timestamp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // State indicator
            Text(
                text = "state: ${window.state.name.lowercase()}",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TerminalColors.Subtext
                )
            )
        }
    }
}

/**
 * A workspace tab button for switching between virtual desktops.
 *
 * @param index The workspace index number
 * @param label Display label for the workspace
 * @param isActive Whether this workspace is currently selected
 * @param onClick Callback when the tab is clicked
 */
@Composable
private fun WorkspaceTab(
    index: Int,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isActive) TerminalColors.Accent.copy(alpha = 0.2f)
                else TerminalColors.Surface.copy(alpha = 0.5f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$ $label",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) TerminalColors.Accent else TerminalColors.Timestamp
            ),
            textAlign = TextAlign.Center
        )
    }
}
