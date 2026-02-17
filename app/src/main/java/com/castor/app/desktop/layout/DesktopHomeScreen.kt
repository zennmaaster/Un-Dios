package com.castor.app.desktop.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.app.desktop.DisplayMode
import com.castor.app.desktop.activities.ActivitiesOverview
import com.castor.app.desktop.clipboard.ClipboardHistoryManager
import com.castor.app.desktop.clipboard.ClipboardPanel
import com.castor.app.desktop.filemanager.FileManagerScreen
import com.castor.app.desktop.systray.NotificationItem
import com.castor.app.desktop.systray.NotificationShade
import com.castor.app.desktop.systray.SystemTrayPanel
import com.castor.app.desktop.window.DesktopWindow
import com.castor.app.desktop.window.WindowFrame
import com.castor.app.desktop.window.WindowManager
import com.castor.app.desktop.window.toEffectiveBounds
import com.castor.app.launcher.AppDrawer
import com.castor.core.ui.components.SystemStatusBar
import com.castor.core.ui.components.SystemStats
import com.castor.app.system.SystemStatsViewModel
import com.castor.core.ui.theme.TerminalColors
import com.castor.feature.commandbar.CommandBar
import com.castor.feature.commandbar.CommandBarViewModel
import com.castor.feature.media.ui.MediaScreen
import com.castor.feature.messaging.ui.MessagingScreen
import com.castor.feature.reminders.ui.RemindersScreen
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full desktop layout composable -- the primary UI when an external display is connected.
 *
 * Provides a complete Ubuntu/GNOME-inspired desktop environment:
 *
 * ```
 * +------------------------------------------------------------------+
 * | SystemStatusBar (top panel, wider, more info)                     |
 * +------+---------------------------------------------------+-------+
 * |      |                                                   |       |
 * | Dock |           Main Workspace                          | (opt) |
 * | (L)  |     (tiled windows, z-ordered)                    |       |
 * |      |                                                   |       |
 * |      |                                                   |       |
 * +------+---------------------------------------------------+-------+
 * | Taskbar (running apps, system tray, clock)                       |
 * +------------------------------------------------------------------+
 * ```
 *
 * Features:
 * - **Top panel**: Reuses [SystemStatusBar] from core:ui, wider for desktop
 * - **Left dock**: Vertical [DesktopDock] with favorite apps and running indicators
 * - **Workspace**: [BoxWithConstraints]-based tiling area where windows are rendered
 * - **Bottom taskbar**: [DesktopTaskbar] with running apps, system tray, clock
 * - **App drawer overlay**: Triggered from dock's "apps" button or Activities button
 * - **Multi-window**: Windows managed by [WindowManager] with tiling/stacking support
 * - **Right-click context menu**: Desktop workspace right-click opens context actions
 * - **Improved empty state**: Shows system info, quick launch buttons, and date/time
 *
 * @param desktopMode The detected desktop display mode with resolution info
 * @param windowManager Singleton window manager for opening/closing/tiling windows
 * @param onNavigateToSettings Callback to open settings in a window or standalone
 * @param clipboardManager Optional clipboard history manager for clipboard panel
 */
@Composable
fun DesktopHomeScreen(
    desktopMode: DisplayMode.Desktop,
    windowManager: WindowManager,
    onNavigateToSettings: () -> Unit,
    clipboardManager: ClipboardHistoryManager? = null
) {
    val commandBarViewModel: CommandBarViewModel = hiltViewModel()
    val systemStatsViewModel: SystemStatsViewModel = hiltViewModel()
    val commandBarState by commandBarViewModel.uiState.collectAsState()
    val windowState by windowManager.state.collectAsState()

    var isAppDrawerVisible by rememberSaveable { mutableStateOf(false) }
    var isActivitiesVisible by rememberSaveable { mutableStateOf(false) }
    var isSystemTrayVisible by rememberSaveable { mutableStateOf(false) }
    var isNotificationShadeVisible by rememberSaveable { mutableStateOf(false) }
    var isClipboardVisible by rememberSaveable { mutableStateOf(false) }

    // Real-time system stats from SystemStatsProvider (CPU, RAM, battery, WiFi, BT, time, etc.)
    val systemStats by systemStatsViewModel.stats.collectAsState()

    // Placeholder notification items
    val notifications = remember {
        listOf(
            NotificationItem(
                id = "notif-1",
                appName = "Signal",
                title = "New message",
                text = "Hey, are you free for lunch today?",
                timestamp = "14:28",
                packageName = "org.thoughtcrime.securesms"
            ),
            NotificationItem(
                id = "notif-2",
                appName = "Calendar",
                title = "Meeting in 15 minutes",
                text = "Sprint planning -- Conference Room B",
                timestamp = "14:17",
                packageName = "com.google.android.calendar"
            ),
            NotificationItem(
                id = "notif-3",
                appName = "System",
                title = "Update available",
                text = "Un-Dios v2.1.0 is ready to install",
                timestamp = "13:45",
                packageName = "com.castor.app"
            )
        )
    }

    // Track running window IDs for the dock indicators
    val runningWindowIds = remember(windowState.windows) {
        windowState.windows.map { it.id }.toSet()
    }

    // ---- Helper lambdas for opening windows (shared between dock and empty state) ----
    val openTerminal: () -> Unit = {
        windowManager.openWindow(
            id = "terminal",
            title = "$ castor-terminal",
            icon = Icons.Default.Terminal
        ) {
            CommandBar(
                state = commandBarState,
                onSubmit = commandBarViewModel::onSubmit,
                onToggleExpanded = commandBarViewModel::toggleExpanded,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        }
    }

    val openMessages: () -> Unit = {
        windowManager.openWindow(
            id = "messages",
            title = "messages",
            icon = Icons.Default.ChatBubble
        ) {
            MessagingScreen(
                onBack = { windowManager.closeWindow("messages") },
                onOpenConversation = { _, _ -> /* handled within */ }
            )
        }
    }

    val openMedia: () -> Unit = {
        windowManager.openWindow(
            id = "media",
            title = "media",
            icon = Icons.Default.Album
        ) {
            MediaScreen(
                onBack = { windowManager.closeWindow("media") }
            )
        }
    }

    val openReminders: () -> Unit = {
        windowManager.openWindow(
            id = "reminders",
            title = "reminders",
            icon = Icons.Default.Notifications
        ) {
            RemindersScreen(
                onBack = { windowManager.closeWindow("reminders") }
            )
        }
    }

    val openAI: () -> Unit = {
        windowManager.openWindow(
            id = "ai-engine",
            title = "ai-engine",
            icon = Icons.Default.SmartToy
        ) {
            CommandBar(
                state = commandBarState,
                onSubmit = commandBarViewModel::onSubmit,
                onToggleExpanded = commandBarViewModel::toggleExpanded,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        }
    }

    val openFiles: () -> Unit = {
        windowManager.openWindow(
            id = "files",
            title = "file-manager",
            icon = Icons.Default.Folder
        ) {
            FileManagerScreen(
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ====================================================================
        // Layer 1: Main desktop layout
        // ====================================================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TerminalColors.Background)
        ) {
            // ============================================================
            // Top Panel -- SystemStatusBar (extended for desktop)
            // ============================================================
            SystemStatusBar(
                stats = systemStats,
                modifier = Modifier.fillMaxWidth()
            )

            // ============================================================
            // Middle Area -- Dock + Workspace
            // ============================================================
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // ---- Left Dock ----
                DesktopDock(
                    runningWindowIds = runningWindowIds,
                    activeWindowId = windowState.activeWindowId,
                    onOpenTerminal = openTerminal,
                    onOpenMessages = openMessages,
                    onOpenMedia = openMedia,
                    onOpenReminders = openReminders,
                    onOpenAI = openAI,
                    onOpenFiles = openFiles,
                    onOpenAppDrawer = { isAppDrawerVisible = true }
                )

                // ---- Main Workspace Area ----
                DesktopWorkspace(
                    windows = windowState.visibleWindows,
                    activeWindowId = windowState.activeWindowId,
                    windowManager = windowManager,
                    systemStats = systemStats,
                    onOpenTerminal = openTerminal,
                    onOpenMessages = openMessages,
                    onOpenMedia = openMedia,
                    onOpenReminders = openReminders,
                    onOpenFiles = openFiles,
                    onNavigateToSettings = {
                        onNavigateToSettings()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            // ============================================================
            // Bottom Taskbar
            // ============================================================
            DesktopTaskbar(
                windows = windowState.windows,
                activeWindowId = windowState.activeWindowId,
                systemStats = systemStats,
                onActivitiesClick = { isActivitiesVisible = true },
                onWindowClick = { windowManager.focusWindow(it) },
                onWindowClose = { windowManager.closeWindow(it) },
                onSystemTrayClick = {
                    isSystemTrayVisible = !isSystemTrayVisible
                    isNotificationShadeVisible = false
                    isClipboardVisible = false
                },
                onNotificationClick = {
                    isNotificationShadeVisible = !isNotificationShadeVisible
                    isSystemTrayVisible = false
                    isClipboardVisible = false
                },
                onClipboardClick = {
                    isClipboardVisible = !isClipboardVisible
                    isSystemTrayVisible = false
                    isNotificationShadeVisible = false
                }
            )
        }

        // ====================================================================
        // Layer 2: App Drawer Overlay (same as phone, but covers whole desktop)
        // ====================================================================
        AppDrawer(
            isVisible = isAppDrawerVisible,
            onDismiss = { isAppDrawerVisible = false }
        )

        // ====================================================================
        // Layer 3: Activities Overview Overlay
        // ====================================================================
        ActivitiesOverview(
            isVisible = isActivitiesVisible,
            onDismiss = { isActivitiesVisible = false },
            windowManager = windowManager,
            onWindowClick = { windowId ->
                windowManager.focusWindow(windowId)
                isActivitiesVisible = false
            }
        )

        // ====================================================================
        // Layer 4: System Tray Panel Overlay
        // ====================================================================
        SystemTrayPanel(
            isVisible = isSystemTrayVisible,
            onDismiss = { isSystemTrayVisible = false },
            systemStats = systemStats,
            onOpenSettings = {
                isSystemTrayVisible = false
                onNavigateToSettings()
            }
        )

        // ====================================================================
        // Layer 5: Notification Shade Overlay
        // ====================================================================
        NotificationShade(
            isVisible = isNotificationShadeVisible,
            onDismiss = { isNotificationShadeVisible = false },
            notifications = notifications,
            onNotificationClick = { _ ->
                isNotificationShadeVisible = false
            },
            onClearAll = {
                isNotificationShadeVisible = false
            }
        )

        // ====================================================================
        // Layer 6: Clipboard Panel Overlay
        // ====================================================================
        if (clipboardManager != null) {
            ClipboardPanel(
                isVisible = isClipboardVisible,
                onDismiss = { isClipboardVisible = false },
                clipboardManager = clipboardManager
            )
        }
    }
}

/**
 * The main workspace area where desktop windows are rendered.
 *
 * Uses [BoxWithConstraints] to know the available workspace size, then
 * positions each visible window according to its effective bounds
 * (calculated from [WindowState] and [WindowBounds]).
 *
 * Windows are rendered in z-order (lowest first, highest on top) so that
 * focused windows appear above other windows. Each window is wrapped
 * in a [WindowFrame] providing title bar chrome, dragging, resize, and controls.
 *
 * When no windows are open, shows an informative empty state with:
 * - Terminal prompt with system info
 * - Quick launch buttons for common windows
 * - Current date/time
 * - System stats summary
 *
 * Right-click on the desktop opens a context menu with common actions.
 *
 * @param windows List of visible windows to render (sorted by z-order)
 * @param activeWindowId Currently focused window ID
 * @param windowManager The window manager for handling window actions
 * @param systemStats Current system statistics for the empty state
 * @param onOpenTerminal Open a terminal window
 * @param onOpenMessages Open the messages window
 * @param onOpenMedia Open the media window
 * @param onOpenReminders Open the reminders window
 * @param onOpenFiles Open the file manager window
 * @param onNavigateToSettings Open settings
 * @param modifier Modifier for the workspace container
 */
@Composable
private fun DesktopWorkspace(
    windows: List<DesktopWindow>,
    activeWindowId: String?,
    windowManager: WindowManager,
    systemStats: SystemStats,
    onOpenTerminal: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenFiles: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // Right-click context menu state
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

    BoxWithConstraints(
        modifier = modifier
            .background(TerminalColors.Background)
            .padding(4.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { offset ->
                        // Long-press acts as right-click on touchscreens
                        contextMenuOffset = DpOffset(
                            x = with(density) { offset.x.toDp() },
                            y = with(density) { offset.y.toDp() }
                        )
                        showContextMenu = true
                    }
                )
            }
    ) {
        val workspaceWidthPx = with(density) { maxWidth.toPx() }
        val workspaceHeightPx = with(density) { maxHeight.toPx() }

        // Render each window at its effective bounds
        windows.forEach { window ->
            val effectiveBounds = window.state.toEffectiveBounds(window.bounds)

            val windowWidthDp = with(density) { (workspaceWidthPx * effectiveBounds.width).toDp() }
            val windowHeightDp = with(density) { (workspaceHeightPx * effectiveBounds.height).toDp() }
            val offsetX = with(density) { (workspaceWidthPx * effectiveBounds.x).toDp() }
            val offsetY = with(density) { (workspaceHeightPx * effectiveBounds.y).toDp() }

            WindowFrame(
                window = window,
                onClose = { windowManager.closeWindow(window.id) },
                onMinimize = { windowManager.minimizeWindow(window.id) },
                onToggleMaximize = { windowManager.toggleMaximize(window.id) },
                onFocus = { windowManager.focusWindow(window.id) },
                onDrag = { deltaX, deltaY ->
                    val currentBounds = window.bounds
                    val newX = (currentBounds.x + deltaX / workspaceWidthPx)
                        .coerceIn(0f, 1f - currentBounds.width)
                    val newY = (currentBounds.y + deltaY / workspaceHeightPx)
                        .coerceIn(0f, 1f - currentBounds.height)
                    windowManager.updateWindowBounds(
                        id = window.id,
                        bounds = currentBounds.copy(x = newX, y = newY)
                    )
                },
                onResize = { deltaX, deltaY ->
                    val currentBounds = window.bounds
                    val newWidth = (currentBounds.width + deltaX / workspaceWidthPx)
                        .coerceIn(0.15f, 1f - currentBounds.x)
                    val newHeight = (currentBounds.height + deltaY / workspaceHeightPx)
                        .coerceIn(0.15f, 1f - currentBounds.y)
                    windowManager.updateWindowBounds(
                        id = window.id,
                        bounds = currentBounds.copy(width = newWidth, height = newHeight)
                    )
                },
                modifier = Modifier
                    .width(windowWidthDp)
                    .height(windowHeightDp)
                    .offset(x = offsetX, y = offsetY)
            )
        }

        // Empty workspace placeholder when no windows are open
        if (windows.isEmpty()) {
            DesktopEmptyState(
                systemStats = systemStats,
                onOpenTerminal = onOpenTerminal,
                onOpenMessages = onOpenMessages,
                onOpenMedia = onOpenMedia,
                onOpenReminders = onOpenReminders,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ---- Right-click / Long-press context menu ----
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = contextMenuOffset,
            modifier = Modifier
                .background(TerminalColors.Surface)
        ) {
            DesktopContextMenuItem(
                icon = Icons.Default.Terminal,
                label = "$ open-terminal",
                onClick = {
                    showContextMenu = false
                    onOpenTerminal()
                }
            )
            DesktopContextMenuItem(
                icon = Icons.Default.Folder,
                label = "$ open-file-manager",
                onClick = {
                    showContextMenu = false
                    onOpenFiles()
                }
            )
            DesktopContextMenuItem(
                icon = Icons.Default.Settings,
                label = "$ system-settings",
                onClick = {
                    showContextMenu = false
                    onNavigateToSettings()
                }
            )
            DesktopContextMenuItem(
                icon = Icons.Default.Info,
                label = "$ about-undios",
                onClick = {
                    showContextMenu = false
                    // Open a small about info window
                    windowManager.openWindow(
                        id = "about",
                        title = "about-undios",
                        icon = Icons.Default.Info
                    ) {
                        AboutWindowContent()
                    }
                }
            )
        }
    }
}

/**
 * A single item in the desktop right-click context menu, styled as a terminal command.
 *
 * @param icon Material icon for the menu item
 * @param label Terminal-style command label (e.g., "$ open-terminal")
 * @param onClick Callback when the item is clicked
 */
@Composable
private fun DesktopContextMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TerminalColors.Accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TerminalColors.Command
                    )
                )
            }
        },
        onClick = onClick
    )
}

/**
 * Content shown when no windows are open in desktop mode.
 *
 * Displays an informative terminal-styled empty state:
 * - `$ startx` prompt with system info
 * - Current date/time
 * - System stats summary (CPU, RAM, battery)
 * - Quick launch buttons for common windows
 *
 * @param systemStats Current system statistics
 * @param onOpenTerminal Open a terminal window
 * @param onOpenMessages Open the messages window
 * @param onOpenMedia Open the media window
 * @param onOpenReminders Open the reminders window
 * @param modifier Modifier for the container
 */
@Composable
private fun DesktopEmptyState(
    systemStats: SystemStats,
    onOpenTerminal: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenReminders: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Live clock for the empty state
    var currentDateTime by remember { mutableStateOf(formatDesktopDateTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentDateTime = formatDesktopDateTime()
            delay(60_000L)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ---- Terminal boot prompt ----
            Text(
                text = "un-dios desktop",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Accent
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ---- Boot-style log messages ----
            Text(
                text = "$ startx",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Prompt
                )
            )

            Text(
                text = "[ok] desktop environment loaded",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Success
                )
            )

            Text(
                text = "[ok] window manager ready (0 windows)",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Success
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ---- Date/time ----
            Text(
                text = currentDateTime,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TerminalColors.Command
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ---- System stats summary ----
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatChip(
                    label = "cpu",
                    value = "${systemStats.cpuUsage.toInt()}%"
                )
                StatChip(
                    label = "ram",
                    value = "${systemStats.ramUsage.toInt()}%"
                )
                StatChip(
                    label = "bat",
                    value = "${systemStats.batteryPercent}%${if (systemStats.isCharging) "+" else ""}"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Separator ----
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(1.dp)
                    .background(TerminalColors.Surface)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ---- Quick launch section ----
            Text(
                text = "-- quick launch --",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TerminalColors.Timestamp
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickLaunchButton(
                    icon = Icons.Default.Terminal,
                    label = "terminal",
                    tint = TerminalColors.Accent,
                    onClick = onOpenTerminal
                )
                QuickLaunchButton(
                    icon = Icons.Default.ChatBubble,
                    label = "messages",
                    tint = TerminalColors.Success,
                    onClick = onOpenMessages
                )
                QuickLaunchButton(
                    icon = Icons.Default.Album,
                    label = "media",
                    tint = TerminalColors.Info,
                    onClick = onOpenMedia
                )
                QuickLaunchButton(
                    icon = Icons.Default.Notifications,
                    label = "reminders",
                    tint = TerminalColors.Warning,
                    onClick = onOpenReminders
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- Hint text ----
            Text(
                text = "long-press desktop for context menu",
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
 * Small chip displaying a system stat (CPU, RAM, battery) in terminal style.
 *
 * @param label Stat name (e.g., "cpu")
 * @param value Stat value string (e.g., "32%")
 */
@Composable
private fun StatChip(
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$label:",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Timestamp
            )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Command
            )
        )
    }
}

/**
 * Quick launch button for the desktop empty state.
 *
 * @param icon Material icon for the button
 * @param label Short label below the icon
 * @param tint Icon tint color
 * @param onClick Callback when clicked
 */
@Composable
private fun QuickLaunchButton(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

/**
 * Content for the "About Un-Dios" window opened from the context menu.
 */
@Composable
private fun AboutWindowContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "un-dios",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "desktop environment for android",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Command
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        val infoLines = listOf(
            "version:  2.1.0",
            "kernel:   castor-wm",
            "shell:    monospace-ui",
            "theme:    terminal-dark",
            "license:  Apache-2.0"
        )

        infoLines.forEach { line ->
            Text(
                text = line,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Output
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "built with kotlin + jetpack compose",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

/**
 * Formats the current date and time for the desktop empty state.
 * Format: "EEE, MMM d yyyy  HH:mm" (e.g., "Mon, Feb 17 2026  14:23").
 */
private fun formatDesktopDateTime(): String {
    return try {
        val formatter = SimpleDateFormat("EEE, MMM d yyyy  HH:mm", Locale.getDefault())
        formatter.format(Date())
    } catch (_: Exception) {
        ""
    }
}
