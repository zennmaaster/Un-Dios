package com.castor.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.app.launcher.AppDrawer
import com.castor.app.launcher.GestureHandler
import com.castor.core.ui.components.AgentCard
import com.castor.core.ui.components.QuickLaunchBar
import com.castor.core.ui.components.SystemStats
import com.castor.core.ui.components.SystemStatusBar
import com.castor.core.ui.theme.CastorPrimary
import com.castor.core.ui.theme.SpotifyGreen
import com.castor.core.ui.theme.TeamsBlue
import com.castor.core.ui.theme.TerminalColors
import com.castor.core.ui.theme.WhatsAppGreen
import com.castor.feature.commandbar.CommandBar
import com.castor.feature.commandbar.CommandBarViewModel

/**
 * The main home screen of Castor, styled as an Ubuntu/Linux desktop environment.
 *
 * Layout structure (top to bottom):
 * 1. SystemStatusBar — persistent dark panel with system stats (CPU, RAM, battery, time)
 * 2. Main workspace area — scrollable content containing:
 *    a. CastorTerminal (via CommandBar) — expanded by default, ~40% of screen
 *    b. Agent cards — 2-column grid (Messages, Media, Reminders, AI)
 *       each showing live status info
 * 3. QuickLaunchBar — Ubuntu-style dock at the bottom
 *
 * The entire home screen is wrapped in a [GestureHandler] that provides:
 * - Swipe up: Opens the GNOME Activities-style app drawer
 * - Swipe down (from top): Expands the system notification shade
 * - Double tap: (Reserved for screen lock / sleep)
 * - Long press: Navigates to launcher settings
 *
 * The app drawer is rendered as a full-screen animated overlay on top of the
 * home content, rather than as a separate navigation destination, to allow
 * smooth slide-up/slide-down animations without navigation transitions.
 *
 * The overall aesthetic is dark-theme-first, information-dense, monospace stats,
 * designed for power users who want a DIY Linux desktop feel on Android.
 */
@Composable
fun HomeScreen(
    onNavigateToMessages: () -> Unit,
    onNavigateToMedia: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: CommandBarViewModel = hiltViewModel()
) {
    val commandBarState by viewModel.uiState.collectAsState()

    // App drawer visibility state — survives recomposition but not process death
    var isAppDrawerVisible by rememberSaveable { mutableStateOf(false) }

    // Placeholder system stats — in production, a dedicated ViewModel would provide real values
    val systemStats = remember {
        SystemStats(
            cpuUsage = 23f,
            ramUsage = 47f,
            ramUsedMb = 2867,
            ramTotalMb = 6144,
            batteryPercent = 72,
            isCharging = false,
            wifiConnected = true,
            bluetoothConnected = true,
            unreadNotifications = 3,
            currentTime = "14:32"
        )
    }

    // Placeholder counts — in production, from agent ViewModels
    val unreadMessages = 5
    val nowPlaying = "Nothing playing"
    val nextReminder = "No upcoming tasks"

    // The entire home screen is layered: home content underneath, app drawer overlay on top
    Box(modifier = Modifier.fillMaxSize()) {
        // ====================================================================
        // Layer 1: Home screen content with gesture detection
        // ====================================================================
        GestureHandler(
            onSwipeUp = { isAppDrawerVisible = true },
            onSwipeDown = { /* Notification shade handled inside GestureHandler */ },
            onDoubleTap = { /* TODO: Lock screen via DevicePolicyManager in future phase */ },
            onLongPress = { onNavigateToSettings() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TerminalColors.Background)
            ) {
                // ============================================================
                // 1. System Status Bar (Ubuntu panel — always dark, always visible)
                // ============================================================
                SystemStatusBar(
                    stats = systemStats,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                )

                // ============================================================
                // 2. Main Workspace Area (scrollable)
                // ============================================================
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // ---- Terminal: spans full width ----
                    item(span = { GridItemSpan(2) }) {
                        CommandBar(
                            state = commandBarState,
                            onSubmit = viewModel::onSubmit,
                            onToggleExpanded = viewModel::toggleExpanded,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // ---- Section header ----
                    item(span = { GridItemSpan(2) }) {
                        SectionHeader(title = "agents")
                    }

                    // ---- Messages card ----
                    item {
                        AgentCard(
                            title = "Messages",
                            subtitle = "WhatsApp & Teams",
                            icon = Icons.Default.ChatBubble,
                            accentColor = WhatsAppGreen,
                            onClick = onNavigateToMessages
                        ) {
                            AgentStatusText(
                                text = if (unreadMessages > 0) "$unreadMessages unread" else "All caught up",
                                isActive = unreadMessages > 0,
                                activeColor = WhatsAppGreen
                            )
                        }
                    }

                    // ---- Media card ----
                    item {
                        AgentCard(
                            title = "Media",
                            subtitle = "Spotify / YouTube",
                            icon = Icons.Default.Album,
                            accentColor = SpotifyGreen,
                            onClick = onNavigateToMedia
                        ) {
                            AgentStatusText(
                                text = nowPlaying,
                                isActive = nowPlaying != "Nothing playing",
                                activeColor = SpotifyGreen
                            )
                        }
                    }

                    // ---- Reminders card ----
                    item {
                        AgentCard(
                            title = "Reminders",
                            subtitle = "Calendar & tasks",
                            icon = Icons.Default.Notifications,
                            accentColor = TeamsBlue,
                            onClick = onNavigateToReminders
                        ) {
                            AgentStatusText(
                                text = nextReminder,
                                isActive = nextReminder != "No upcoming tasks",
                                activeColor = TeamsBlue
                            )
                        }
                    }

                    // ---- AI Assistant card ----
                    item {
                        AgentCard(
                            title = "AI Engine",
                            subtitle = "On-device LLM",
                            icon = Icons.Default.SmartToy,
                            accentColor = CastorPrimary,
                            onClick = { viewModel.toggleExpanded() }
                        ) {
                            AgentStatusText(
                                text = "LOCAL inference",
                                isActive = true,
                                activeColor = TerminalColors.Success
                            )
                        }
                    }

                    // Bottom spacing so content isn't hidden behind the dock
                    item(span = { GridItemSpan(2) }) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // ============================================================
                // 3. Quick Launch Bar (Ubuntu dock)
                // ============================================================
                QuickLaunchBar(
                    onMessages = onNavigateToMessages,
                    onMedia = onNavigateToMedia,
                    onReminders = onNavigateToReminders,
                    onAppDrawer = { isAppDrawerVisible = true },
                    onTerminal = { viewModel.toggleExpanded() },
                    unreadMessages = unreadMessages
                )
            }
        }

        // ====================================================================
        // Layer 2: App drawer overlay (GNOME Activities)
        // ====================================================================
        AppDrawer(
            isVisible = isAppDrawerVisible,
            onDismiss = { isAppDrawerVisible = false }
        )
    }
}

// ============================================================================
// Helper composables
// ============================================================================

/**
 * Monospace section header for separating content areas.
 * Styled like a terminal comment: `# agents`
 */
@Composable
private fun SectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Text(
            text = "# ",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )
        Text(
            text = title,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Timestamp
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(TerminalColors.Surface)
        )
    }
}

/**
 * Status line shown inside each AgentCard, with a small colored dot
 * indicator when the agent has active content.
 */
@Composable
private fun AgentStatusText(
    text: String,
    isActive: Boolean,
    activeColor: androidx.compose.ui.graphics.Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isActive) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Circle,
                contentDescription = null,
                tint = activeColor,
                modifier = Modifier.size(6.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
