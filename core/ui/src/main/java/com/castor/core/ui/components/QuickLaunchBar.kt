package com.castor.core.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.castor.core.ui.theme.TerminalColors

/**
 * Data class representing a user-pinned app in the dock.
 * Defined here in the core:ui module so QuickLaunchBar can reference it
 * without depending on the app module.
 *
 * @param packageName Unique package identifier
 * @param label Human-readable app name
 * @param icon The app's launcher icon drawable (nullable if unavailable)
 */
data class DockPinnedApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

/**
 * Ubuntu-style dock / launcher bar at the bottom of the screen.
 *
 * Supports two modes:
 * 1. **Default mode** (no pinned apps): Shows the built-in quick action buttons
 *    for Messages, Media, Reminders, Terminal, Search, and App Drawer.
 * 2. **Pinned mode** (1-6 pinned apps): Shows pinned app icons in the dock,
 *    with the Terminal and App Drawer buttons always present at the edges.
 *
 * Long-pressing the dock enters **edit mode**, which shows:
 * - A delete badge on each pinned app (tap to unpin)
 * - Position numbers for reorder reference
 * - An "Add" button to open the app drawer for pinning more apps
 * - A "done" label to exit edit mode
 *
 * @param onMessages Navigate to the Messages agent
 * @param onMedia Navigate to the Media agent
 * @param onReminders Navigate to the Reminders agent
 * @param onAppDrawer Open the app drawer / grid
 * @param onTerminal Focus the terminal / bring it to front
 * @param onSearch Open the universal search overlay
 * @param unreadMessages Badge count for unread messages
 * @param pinnedApps List of user-pinned apps to show in the dock
 * @param onPinnedAppClick Called when a pinned app is tapped
 * @param onPinnedAppRemove Called when a pinned app's delete badge is tapped (edit mode)
 * @param onAddPinnedApp Called when the "add" button is tapped (edit mode)
 * @param modifier Modifier for the root composable
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickLaunchBar(
    onMessages: () -> Unit,
    onMedia: () -> Unit,
    onReminders: () -> Unit,
    onAppDrawer: () -> Unit,
    onTerminal: () -> Unit,
    onSearch: () -> Unit = {},
    unreadMessages: Int = 0,
    pinnedApps: List<DockPinnedApp> = emptyList(),
    onPinnedAppClick: (String) -> Unit = {},
    onPinnedAppRemove: (String) -> Unit = {},
    onAddPinnedApp: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isEditMode by remember { mutableStateOf(false) }

    if (pinnedApps.isEmpty()) {
        // =====================================================================
        // Default dock: built-in quick action buttons
        // =====================================================================
        DefaultDock(
            onMessages = onMessages,
            onMedia = onMedia,
            onReminders = onReminders,
            onAppDrawer = onAppDrawer,
            onTerminal = onTerminal,
            onSearch = onSearch,
            unreadMessages = unreadMessages,
            modifier = modifier
        )
    } else {
        // =====================================================================
        // Pinned apps dock: user-customizable
        // =====================================================================
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(TerminalColors.Overlay.copy(alpha = 0.92f))
                .navigationBarsPadding()
        ) {
            // Edit mode header
            AnimatedVisibility(
                visible = isEditMode,
                enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.95f),
                exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.95f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$ dock --edit",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TerminalColors.Warning
                        )
                    )
                    Text(
                        text = "[done]",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Accent
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { isEditMode = false }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Dock items row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Always show Terminal at the left edge
                DockItem(
                    icon = Icons.Default.Terminal,
                    label = "term",
                    onClick = onTerminal,
                    tint = TerminalColors.Accent,
                    isHighlighted = true
                )

                // Pinned app icons
                pinnedApps.forEachIndexed { index, app ->
                    PinnedAppDockItem(
                        app = app,
                        position = index + 1,
                        isEditMode = isEditMode,
                        onClick = {
                            if (!isEditMode) {
                                onPinnedAppClick(app.packageName)
                            }
                        },
                        onLongClick = { isEditMode = true },
                        onRemove = { onPinnedAppRemove(app.packageName) }
                    )
                }

                // "Add" button in edit mode (if under max limit)
                if (isEditMode && pinnedApps.size < 6) {
                    DockAddButton(onClick = {
                        isEditMode = false
                        onAddPinnedApp()
                    })
                }

                // Always show App Drawer at the right edge
                DockItem(
                    icon = Icons.Default.Apps,
                    label = "apps",
                    onClick = {
                        if (isEditMode) {
                            isEditMode = false
                        }
                        onAppDrawer()
                    },
                    tint = TerminalColors.Command
                )
            }
        }
    }
}

/**
 * The default dock layout shown when no apps are pinned.
 * Contains the original 6 quick action buttons.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DefaultDock(
    onMessages: () -> Unit,
    onMedia: () -> Unit,
    onReminders: () -> Unit,
    onAppDrawer: () -> Unit,
    onTerminal: () -> Unit,
    onSearch: () -> Unit,
    unreadMessages: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(TerminalColors.Overlay.copy(alpha = 0.92f))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockItem(
            icon = Icons.Default.ChatBubble,
            label = "msgs",
            onClick = onMessages,
            badgeCount = unreadMessages,
            tint = TerminalColors.Success
        )

        DockItem(
            icon = Icons.Default.MusicNote,
            label = "media",
            onClick = onMedia,
            tint = TerminalColors.Info
        )

        DockItem(
            icon = Icons.Default.Terminal,
            label = "term",
            onClick = onTerminal,
            tint = TerminalColors.Accent,
            isHighlighted = true
        )

        DockItem(
            icon = Icons.Default.Search,
            label = "grep",
            onClick = onSearch,
            tint = TerminalColors.Cursor
        )

        DockItem(
            icon = Icons.Default.Notifications,
            label = "tasks",
            onClick = onReminders,
            tint = TerminalColors.Warning
        )

        DockItem(
            icon = Icons.Default.Apps,
            label = "apps",
            onClick = onAppDrawer,
            tint = TerminalColors.Command
        )
    }
}

/**
 * A pinned app icon in the dock with optional edit-mode overlays.
 *
 * In normal mode, shows the app icon with a short label.
 * In edit mode, shows a position number and a delete badge in the top-right corner.
 * Long-press enters edit mode.
 *
 * @param app The pinned app data
 * @param position 1-based position number (displayed in edit mode)
 * @param isEditMode Whether the dock is in edit mode
 * @param onClick Called on tap (launches the app in normal mode)
 * @param onLongClick Called on long-press (enters edit mode)
 * @param onRemove Called when the delete badge is tapped
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedAppDockItem(
    app: DockPinnedApp,
    position: Int,
    isEditMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            // App icon button area
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
            ) {
                if (app.icon != null) {
                    val bitmap = remember(app.packageName) {
                        try {
                            app.icon.toBitmap(96, 96).asImageBitmap()
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = app.label,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        PinnedAppFallbackIcon()
                    }
                } else {
                    PinnedAppFallbackIcon()
                }
            }

            // Edit mode: delete badge in top-right
            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(TerminalColors.Error)
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove ${app.label} from dock",
                        tint = TerminalColors.Background,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        // Label or position number
        if (isEditMode) {
            Text(
                text = "#$position",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Warning
                )
            )
        } else {
            Text(
                text = app.label.take(5).lowercase(),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    color = TerminalColors.Timestamp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Fallback icon for pinned apps when their drawable cannot be loaded.
 */
@Composable
private fun PinnedAppFallbackIcon() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface)
    ) {
        Icon(
            imageVector = Icons.Default.Android,
            contentDescription = null,
            tint = TerminalColors.Accent,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Small "add app" button shown at the end of the pinned apps row in edit mode.
 */
@Composable
private fun DockAddButton(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TerminalColors.Selection.copy(alpha = 0.5f))
                .clickable(onClick = onClick)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add app to dock",
                tint = TerminalColors.Accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = "+add",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )
    }
}

/**
 * A single item in the dock: icon button with a small label below it,
 * and an optional notification badge.
 *
 * @param icon The Material icon to display
 * @param label Short monospace label shown below the icon
 * @param onClick Action when the item is tapped
 * @param badgeCount Optional notification badge count (0 = no badge)
 * @param tint Icon tint color
 * @param isHighlighted Whether to show a highlight indicator (for the active/primary item)
 */
@Composable
private fun DockItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    badgeCount: Int = 0,
    tint: androidx.compose.ui.graphics.Color = TerminalColors.Command,
    isHighlighted: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Notification badge
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-2).dp, y = 4.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(TerminalColors.BadgeRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 99) "99" else badgeCount.toString(),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Background
                        )
                    )
                }
            }
        }

        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                color = if (isHighlighted) TerminalColors.Accent else TerminalColors.Timestamp
            )
        )

        // Active indicator dot
        if (isHighlighted) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(TerminalColors.Accent)
            )
        }
    }
}
