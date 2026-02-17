package com.castor.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.castor.app.launcher.LauncherPreferencesManager
import com.castor.app.launcher.LauncherSettingsScreen
import com.castor.app.settings.AboutScreen
import com.castor.app.settings.BatteryOptimizationScreen
import com.castor.app.settings.ThemeManager
import com.castor.app.settings.ThemeSelectorScreen
import com.castor.core.security.SecurePreferences
import com.castor.app.notes.NoteEditorScreen
import com.castor.app.notes.NotesScreen
import com.castor.app.onboarding.OnboardingPreferences
import com.castor.app.onboarding.OnboardingScreen
import com.castor.app.onboarding.onboardingDataStore
import com.castor.app.ui.HomeScreen
import com.castor.app.usage.UsageStatsScreen
import com.castor.app.weather.WeatherDetailScreen
import com.castor.feature.media.sync.ui.BookSyncScreen
import com.castor.feature.media.ui.MediaScreen
import com.castor.feature.messaging.contacts.ContactsScreen
import com.castor.feature.messaging.ui.ConversationScreen
import com.castor.feature.messaging.ui.MessagingScreen
import com.castor.feature.notifications.center.NotificationCenterScreen
import com.castor.feature.recommendations.ui.RecommendationsScreen
import com.castor.feature.recommendations.ui.WatchHistoryScreen
import com.castor.core.inference.ui.ModelManagerScreen
import com.castor.feature.reminders.ui.RemindersScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Root navigation host for the Castor application (phone mode).
 *
 * This NavHost is used exclusively in phone mode. In desktop mode,
 * navigation is handled by the [WindowManager] which opens each screen
 * as a separate window in the tiling workspace — no NavHost is needed
 * because windows can be opened/closed independently.
 *
 * On first launch, the user is directed to the onboarding wizard which
 * guides them through granting permissions required for full launcher
 * functionality. Once completed, onboarding is not shown again unless
 * the onboarding version is bumped.
 *
 * Routes:
 * - "onboarding"   — first-launch setup wizard (permissions, default launcher)
 * - "home"         — main dashboard (with gesture-driven app drawer overlay)
 * - "messages"     — unified messaging inbox (split-pane on tablets)
 * - "conversation" — individual conversation thread (phone-only; tablets use embedded pane)
 * - "contacts"     — contacts hub (terminal-styled contact browser with actions)
 * - "media"        — media aggregation
 * - "reminders"    — smart reminders
 * - "usage_stats"  — screen time / app usage stats dashboard
 * - "notification_center" — smart notification management (journalctl-style)
 * - "notes"        — quick notes / scratchpad (terminal-styled note list)
 * - "note_editor/{noteId}" — vim-styled note editor (-1 for new note)
 * - "weather"      — full weather detail screen (curl wttr.in --verbose)
 * - "settings"     — launcher settings (/etc/un-dios/config)
 * - "theme_selector" — terminal color theme picker (/etc/un-dios/themes.conf)
 * - "battery_optimization" — battery optimization guide (/etc/un-dios/battery-optimization.md)
 * - "model_manager" — download and manage on-device LLM models (/var/un-dios/models)
 * - "about"        — about screen (/etc/un-dios/about)
 *
 * Note: The app drawer is implemented as a full-screen overlay within HomeScreen
 * rather than as a separate navigation route, since it overlays the home content
 * and should animate smoothly without a navigation transition.
 *
 * Desktop mode note: When the device is connected to an external display,
 * [MainActivity] renders [DesktopHomeScreen] instead of this NavHost.
 * Each feature screen (Messages, Media, Reminders) is opened as a desktop
 * window via [WindowManager.openWindow] rather than via navigation routes.
 */
@Composable
fun CastorNavHost(
    themeManager: ThemeManager? = null,
    launcherPreferencesManager: LauncherPreferencesManager? = null,
    securePreferences: SecurePreferences? = null
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Determine start destination based on onboarding completion.
    // We read this synchronously before rendering, so the user never sees
    // a flash of the wrong screen.
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val isCompleted = context.onboardingDataStore.data
            .map { it[OnboardingPreferences.ONBOARDING_COMPLETED] ?: false }
            .first()
        startDestination = if (isCompleted) "home" else "onboarding"
    }

    // Wait until we know the start destination before rendering the NavHost.
    // This prevents a brief flash of the home screen on first launch.
    val destination = startDestination ?: return

    NavHost(navController = navController, startDestination = destination) {
        composable("onboarding") {
            OnboardingScreen(
                onComplete = {
                    navController.navigate("home") {
                        // Remove onboarding from the back stack so pressing
                        // back from home does not return to the wizard.
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(
                onNavigateToMessages = { navController.navigate("messages") },
                onNavigateToMedia = { navController.navigate("media") },
                onNavigateToContacts = { navController.navigate("contacts") },
                onNavigateToReminders = { navController.navigate("reminders") },
                onNavigateToRecommendations = { navController.navigate("recommendations") },
                onNavigateToUsageStats = { navController.navigate("usage_stats") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToNotificationCenter = { navController.navigate("notification_center") },
                onNavigateToNotes = { navController.navigate("notes") },
                onNavigateToWeather = { navController.navigate("weather") },
                onNavigateToRoute = { route -> navController.navigate(route) }
            )
        }

        composable("notification_center") {
            NotificationCenterScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("messages") {
            MessagingScreen(
                onBack = { navController.popBackStack() },
                onOpenConversation = { sender, groupName ->
                    val encodedSender = URLEncoder.encode(sender, StandardCharsets.UTF_8.toString())
                    val route = if (groupName != null) {
                        val encodedGroup = URLEncoder.encode(groupName, StandardCharsets.UTF_8.toString())
                        "conversation/$encodedSender?group=$encodedGroup"
                    } else {
                        "conversation/$encodedSender"
                    }
                    navController.navigate(route)
                }
            )
        }

        composable(
            route = "conversation/{sender}?group={groupName}",
            arguments = listOf(
                navArgument("sender") {
                    type = NavType.StringType
                },
                navArgument("groupName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val rawSender = backStackEntry.arguments?.getString("sender") ?: return@composable
            val rawGroup = backStackEntry.arguments?.getString("groupName")

            val sender = URLDecoder.decode(rawSender, StandardCharsets.UTF_8.toString())
            val groupName = rawGroup?.let {
                URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
            }

            ConversationScreen(
                sender = sender,
                groupName = groupName,
                onBack = { navController.popBackStack() }
            )
        }

        composable("contacts") {
            ContactsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("media") {
            MediaScreen(
                onBack = { navController.popBackStack() },
                onNavigateToBookSync = { navController.navigate("book_sync") }
            )
        }

        composable("book_sync") {
            BookSyncScreen(onBack = { navController.popBackStack() })
        }

        composable("reminders") {
            RemindersScreen(onBack = { navController.popBackStack() })
        }

        composable("recommendations") {
            RecommendationsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("watch_history") {
            WatchHistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("usage_stats") {
            UsageStatsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("notes") {
            NotesScreen(
                onBack = { navController.popBackStack() },
                onOpenNote = { noteId ->
                    navController.navigate("note_editor/$noteId")
                }
            )
        }

        composable(
            route = "note_editor/{noteId}",
            arguments = listOf(
                navArgument("noteId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) {
            NoteEditorScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("weather") {
            WeatherDetailScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            if (launcherPreferencesManager != null) {
                LauncherSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToUsageStats = { navController.navigate("usage_stats") },
                    onNavigateToThemeSelector = { navController.navigate("theme_selector") },
                    onNavigateToBatteryOptimization = { navController.navigate("battery_optimization") },
                    onNavigateToAbout = { navController.navigate("about") },
                    onNavigateToModelManager = { navController.navigate("model_manager") },
                    themeManager = themeManager,
                    launcherPreferencesManager = launcherPreferencesManager,
                    securePreferences = securePreferences
                )
            }
        }

        composable("model_manager") {
            ModelManagerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("theme_selector") {
            if (themeManager != null) {
                ThemeSelectorScreen(
                    themeManager = themeManager,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable("battery_optimization") {
            BatteryOptimizationScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("about") {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
