package com.castor.app.desktop.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
import com.castor.app.desktop.window.WindowState
import com.castor.app.desktop.window.toEffectiveBounds
import com.castor.app.launcher.AppDrawer
import com.castor.core.ui.components.SystemStatusBar
import com.castor.app.system.SystemStatsViewModel
import com.castor.core.ui.theme.TerminalColors
import com.castor.feature.commandbar.CommandBar
import com.castor.feature.commandbar.CommandBarViewModel
import com.castor.feature.media.ui.MediaScreen
import com.castor.feature.messaging.ui.MessagingScreen
import com.castor.feature.reminders.ui.RemindersScreen

/**
 * Full desktop layout composable — the primary UI when an external display is connected.
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
                text = "Sprint planning — Conference Room B",
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
            // Top Panel — SystemStatusBar (extended for desktop)
            // ============================================================
            SystemStatusBar(
                stats = systemStats,
                modifier = Modifier.fillMaxWidth()
            )

            // ============================================================
            // Middle Area — Dock + Workspace
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
                    onOpenTerminal = {
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
                    },
                    onOpenMessages = {
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
                    },
                    onOpenMedia = {
                        windowManager.openWindow(
                            id = "media",
                            title = "media",
                            icon = Icons.Default.Album
                        ) {
                            MediaScreen(
                                onBack = { windowManager.closeWindow("media") }
                            )
                        }
                    },
                    onOpenReminders = {
                        windowManager.openWindow(
                            id = "reminders",
                            title = "reminders",
                            icon = Icons.Default.Notifications
                        ) {
                            RemindersScreen(
                                onBack = { windowManager.closeWindow("reminders") }
                            )
                        }
                    },
                    onOpenAI = {
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
                    },
                    onOpenFiles = {
                        windowManager.openWindow(
                            id = "files",
                            title = "file-manager",
                            icon = Icons.Default.Folder
                        ) {
                            FileManagerScreen(
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    },
                    onOpenAppDrawer = { isAppDrawerVisible = true }
                )

                // ---- Main Workspace Area ----
                DesktopWorkspace(
                    windows = windowState.visibleWindows,
                    activeWindowId = windowState.activeWindowId,
                    windowManager = windowManager,
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
 * in a [WindowFrame] providing title bar chrome, dragging, and controls.
 *
 * @param windows List of visible windows to render (sorted by z-order)
 * @param activeWindowId Currently focused window ID
 * @param windowManager The window manager for handling window actions
 * @param modifier Modifier for the workspace container
 */
@Composable
private fun DesktopWorkspace(
    windows: List<DesktopWindow>,
    activeWindowId: String?,
    windowManager: WindowManager,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .background(TerminalColors.Background)
            .padding(4.dp)
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
                modifier = Modifier
                    .width(windowWidthDp)
                    .height(windowHeightDp)
                    .offset(x = offsetX, y = offsetY)
            )
        }

        // Empty workspace placeholder when no windows are open
        if (windows.isEmpty()) {
            DesktopEmptyState(
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Placeholder content shown when no windows are open in desktop mode.
 * Shows terminal-style branding and hints.
 */
@Composable
private fun DesktopEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Text(
                text = "un-dios desktop",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = TerminalColors.Accent
                )
            )
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.height(8.dp)
            )
            androidx.compose.material3.Text(
                text = "$ click an app in the dock to begin",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }
    }
}
