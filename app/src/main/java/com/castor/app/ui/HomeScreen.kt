package com.castor.app.ui

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
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.app.launcher.AppDrawer
import com.castor.app.launcher.GestureHandler
import com.castor.app.lockscreen.LockScreenOverlay
import com.castor.app.lockscreen.LockScreenViewModel
import com.castor.core.security.BiometricAuthManager
import com.castor.core.ui.components.AgentCard
import com.castor.core.ui.components.QuickLaunchBar
import com.castor.core.ui.components.SystemStatusBar
import com.castor.app.system.SystemStatsViewModel
import com.castor.core.ui.theme.CastorPrimary
import com.castor.core.ui.theme.SpotifyGreen
import com.castor.core.ui.theme.TeamsBlue
import com.castor.core.ui.theme.NetflixRed
import com.castor.core.ui.theme.TerminalColors
import com.castor.core.ui.theme.WhatsAppGreen
import com.castor.app.search.UniversalSearchOverlay
import com.castor.app.weather.WeatherCard
import com.castor.feature.commandbar.CommandBar
import com.castor.feature.commandbar.CommandBarViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The main home screen of Castor, styled as an Ubuntu/Linux desktop environment.
 *
 * Layout structure (top to bottom):
 * 1. SystemStatusBar -- persistent dark panel with system stats (CPU, RAM, battery, time)
 * 2. Main workspace area -- scrollable content with pull-to-refresh containing:
 *    a. CastorTerminal (via CommandBar) -- expanded by default, ~40% of screen
 *    b. WeatherCard -- full-width weather display (spans 2 columns)
 *    c. BriefingCard -- full-width briefing with real data (spans 2 columns)
 *    d. SuggestionsRow -- context-aware horizontal suggestion chips (spans 2 columns)
 *    e. Agent cards -- 2-column grid (Messages, Media, Reminders, AI)
 *       each showing live status info
 *    f. Last synced timestamp footer
 * 3. QuickLaunchBar -- Ubuntu-style dock at the bottom
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToMessages: () -> Unit,
    onNavigateToMedia: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToRecommendations: () -> Unit,
    onNavigateToUsageStats: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToNotificationCenter: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToWeather: () -> Unit = {},
    viewModel: CommandBarViewModel = hiltViewModel(),
    systemStatsViewModel: SystemStatsViewModel = hiltViewModel(),
    lockScreenViewModel: LockScreenViewModel = hiltViewModel(),
    briefingViewModel: BriefingViewModel = hiltViewModel()
) {
    val commandBarState by viewModel.uiState.collectAsState()

    // App drawer visibility state -- survives recomposition but not process death
    var isAppDrawerVisible by rememberSaveable { mutableStateOf(false) }

    // Universal search overlay visibility state
    var isSearchVisible by rememberSaveable { mutableStateOf(false) }

    // Real-time system stats from SystemStatsProvider (CPU, RAM, battery, WiFi, BT, time, etc.)
    val systemStats by systemStatsViewModel.stats.collectAsState()

    // Lock screen state
    val isLocked by lockScreenViewModel.isLocked.collectAsState()
    val showBootSequence by lockScreenViewModel.showBootSequence.collectAsState()
    val bootLines by lockScreenViewModel.bootLines.collectAsState()
    val showSuccessMessage by lockScreenViewModel.showSuccessMessage.collectAsState()
    val clockTime by lockScreenViewModel.clockTime.collectAsState()
    val clockDate by lockScreenViewModel.clockDate.collectAsState()
    val currentDate by lockScreenViewModel.currentDate.collectAsState()
    val weatherSummary by lockScreenViewModel.weatherSummary.collectAsState()
    val recentNotifications by lockScreenViewModel.recentNotifications.collectAsState()
    val notificationCount by lockScreenViewModel.notificationCount.collectAsState()
    val showNotificationsOnLock by lockScreenViewModel.showNotificationsOnLock.collectAsState()

    // Briefing state from BriefingViewModel (real data)
    val briefing by briefingViewModel.briefing.collectAsState()
    val briefingSuggestions by briefingViewModel.suggestions.collectAsState()
    val isRefreshing by briefingViewModel.isRefreshing.collectAsState()
    val briefingSummary by briefingViewModel.briefingSummary.collectAsState()
    val upcomingReminders by briefingViewModel.upcomingReminders.collectAsState()
    val lastUpdated by briefingViewModel.lastUpdated.collectAsState()
    val unreadMessages by briefingViewModel.unreadMessages.collectAsState()
    val quickStatus by briefingViewModel.quickStatus.collectAsState()
    val timeOfDay = briefingViewModel.getTimeOfDay()

    // Biometric authentication wiring
    val context = LocalContext.current
    val biometricAuthManager = androidx.compose.runtime.remember { BiometricAuthManager() }
    val activity = context as? FragmentActivity
    val canUseBiometric = activity?.let { biometricAuthManager.canAuthenticate(it) } ?: false

    // Live data from BriefingViewModel for agent cards
    val nowPlaying = quickStatus.nowPlaying ?: "Nothing playing"
    val nextReminder = if (upcomingReminders.isNotEmpty()) {
        val first = upcomingReminders.first()
        "${briefingViewModel.formatReminderTime(first.triggerTimeMs)} ${first.description}"
    } else {
        "No upcoming tasks"
    }

    // The entire home screen is layered: home content underneath, app drawer overlay on top,
    // lock screen overlay on the very top
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
                // 1. System Status Bar (Ubuntu panel -- always dark, always visible)
                // ============================================================
                SystemStatusBar(
                    stats = systemStats,
                    onNotificationClick = onNavigateToNotificationCenter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .clickable(onClick = onNavigateToUsageStats)
                )

                // ============================================================
                // 2. Main Workspace Area (scrollable with pull-to-refresh)
                // ============================================================
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { briefingViewModel.refreshBriefing() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
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

                        // ---- Weather card: spans full width, styled as $ curl wttr.in ----
                        item(span = { GridItemSpan(2) }) {
                            WeatherCard(
                                onClick = onNavigateToWeather,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // ---- Briefing card: spans full width, real data from BriefingViewModel ----
                        item(span = { GridItemSpan(2) }) {
                            BriefingCard(
                                briefing = briefing,
                                isRefreshing = isRefreshing,
                                onRefresh = { briefingViewModel.refreshBriefing() },
                                briefingSummary = briefingSummary,
                                upcomingReminders = upcomingReminders,
                                lastUpdatedMs = lastUpdated,
                                onViewMessages = onNavigateToMessages,
                                onViewReminders = onNavigateToReminders,
                                onViewNotifications = onNavigateToNotificationCenter,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // ---- Suggestions row: spans full width, context-aware by time of day ----
                        item(span = { GridItemSpan(2) }) {
                            SuggestionsRow(
                                suggestions = briefingSuggestions,
                                timeOfDay = timeOfDay,
                                onSuggestionClick = { suggestion ->
                                    when {
                                        suggestion.actionData.contains("messages") -> onNavigateToMessages()
                                        suggestion.actionData.contains("reminders") -> onNavigateToReminders()
                                        suggestion.actionData.contains("media") -> onNavigateToMedia()
                                        else -> { /* No-op for dismiss actions */ }
                                    }
                                },
                                onDismiss = { index -> briefingViewModel.dismissSuggestion(index) },
                                onCheckMessages = onNavigateToMessages,
                                onViewSchedule = onNavigateToReminders,
                                onPlayPlaylist = onNavigateToMedia,
                                onViewNotifications = onNavigateToNotificationCenter,
                                onViewReminders = onNavigateToReminders,
                                onScreenTime = onNavigateToUsageStats,
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

                        // ---- Contacts card ----
                        item {
                            AgentCard(
                                title = "Contacts",
                                subtitle = "People & groups",
                                icon = Icons.Default.Contacts,
                                accentColor = TerminalColors.Info,
                                onClick = onNavigateToContacts
                            ) {
                                AgentStatusText(
                                    text = "/etc/contacts",
                                    isActive = true,
                                    activeColor = TerminalColors.Info
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

                        // ---- Recommendations card ----
                        item {
                            AgentCard(
                                title = "Picks",
                                subtitle = "Watch recommendations",
                                icon = Icons.Default.Star,
                                accentColor = NetflixRed,
                                onClick = onNavigateToRecommendations
                            ) {
                                AgentStatusText(
                                    text = "On-device picks",
                                    isActive = true,
                                    activeColor = NetflixRed
                                )
                            }
                        }

                        // ---- Notes card ----
                        item {
                            AgentCard(
                                title = "Notes",
                                subtitle = "Scratchpad",
                                icon = Icons.Default.Edit,
                                accentColor = TerminalColors.Warning,
                                onClick = onNavigateToNotes
                            ) {
                                AgentStatusText(
                                    text = "~/notes/",
                                    isActive = true,
                                    activeColor = TerminalColors.Warning
                                )
                            }
                        }

                        // ---- Last synced timestamp footer ----
                        item(span = { GridItemSpan(2) }) {
                            LastSyncedFooter(lastUpdatedMs = lastUpdated)
                        }

                        // Bottom spacing so content isn't hidden behind the dock
                        item(span = { GridItemSpan(2) }) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
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
                    onSearch = { isSearchVisible = true },
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

        // ====================================================================
        // Layer 3: Universal search overlay (GNOME Spotlight)
        // ====================================================================
        UniversalSearchOverlay(
            isVisible = isSearchVisible,
            onDismiss = { isSearchVisible = false },
            onNavigate = { route ->
                when (route) {
                    "messages" -> onNavigateToMessages()
                    "media" -> onNavigateToMedia()
                    "reminders" -> onNavigateToReminders()
                    "recommendations" -> onNavigateToRecommendations()
                    "notes" -> onNavigateToNotes()
                    "settings" -> onNavigateToSettings()
                }
            },
            onOpenConversation = { sender, groupName ->
                // Navigate to messages -- the conversation screen will be opened
                // from the messaging screen's conversation click handler
                onNavigateToMessages()
            },
            onOpenAppDrawer = {
                isSearchVisible = false
                isAppDrawerVisible = true
            }
        )

        // ====================================================================
        // Layer 4: Lock screen overlay (topmost -- above everything)
        // ====================================================================
        LockScreenOverlay(
            isLocked = isLocked,
            showBootSequence = showBootSequence,
            bootLines = bootLines,
            showSuccessMessage = showSuccessMessage,
            clockTime = clockTime,
            clockDate = clockDate,
            currentDate = currentDate,
            weatherSummary = weatherSummary,
            batteryPercent = systemStats.batteryPercent,
            isCharging = systemStats.isCharging,
            notifications = recentNotifications,
            notificationCount = notificationCount,
            showNotifications = showNotificationsOnLock,
            canUseBiometric = canUseBiometric,
            onBiometricRequested = {
                activity?.let { fragmentActivity ->
                    biometricAuthManager.authenticate(
                        activity = fragmentActivity,
                        title = "Un-Dios Lock Screen",
                        subtitle = "$ authenticate --biometric",
                        onSuccess = { lockScreenViewModel.unlockScreen() },
                        onError = { _, _ -> /* Handled by system biometric UI */ }
                    )
                }
            },
            onSwipeUnlock = {
                // If biometric is available, trigger it on swipe; otherwise unlock directly
                if (canUseBiometric && activity != null) {
                    biometricAuthManager.authenticate(
                        activity = activity,
                        title = "Un-Dios Lock Screen",
                        subtitle = "$ authenticate --biometric",
                        onSuccess = { lockScreenViewModel.unlockScreen() },
                        onError = { _, _ -> /* Handled by system biometric UI */ }
                    )
                } else {
                    lockScreenViewModel.unlockScreen()
                }
            },
            onNotificationTap = {
                // Unlock the screen and navigate to the notification center
                lockScreenViewModel.unlockScreen()
                onNavigateToNotificationCenter()
            }
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

/**
 * "Last synced" timestamp footer at the bottom of the home screen grid.
 * Styled as a terminal comment: `# sync: 2026-02-17T14:30:00`
 */
@Composable
private fun LastSyncedFooter(lastUpdatedMs: Long?) {
    val syncText = if (lastUpdatedMs != null) {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        sdf.format(Date(lastUpdatedMs))
    } else {
        "never"
    }
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "# sync: $syncText",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}
