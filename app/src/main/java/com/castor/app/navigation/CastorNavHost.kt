package com.castor.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.castor.app.launcher.LauncherSettingsScreen
import com.castor.app.ui.HomeScreen
import com.castor.feature.media.sync.ui.BookSyncScreen
import com.castor.feature.media.ui.MediaScreen
import com.castor.feature.messaging.ui.ConversationScreen
import com.castor.feature.messaging.ui.MessagingScreen
import com.castor.feature.recommendations.ui.RecommendationsScreen
import com.castor.feature.recommendations.ui.WatchHistoryScreen
import com.castor.feature.reminders.ui.RemindersScreen
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
 * Routes:
 * - "home"         — main dashboard (with gesture-driven app drawer overlay)
 * - "messages"     — unified messaging inbox (split-pane on tablets)
 * - "conversation" — individual conversation thread (phone-only; tablets use embedded pane)
 * - "media"        — media aggregation
 * - "reminders"    — smart reminders
 * - "settings"     — launcher settings (/etc/un-dios/config)
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
fun CastorNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToMessages = { navController.navigate("messages") },
                onNavigateToMedia = { navController.navigate("media") },
                onNavigateToReminders = { navController.navigate("reminders") },
                onNavigateToRecommendations = { navController.navigate("recommendations") },
                onNavigateToSettings = { navController.navigate("settings") }
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

        composable("settings") {
            LauncherSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
