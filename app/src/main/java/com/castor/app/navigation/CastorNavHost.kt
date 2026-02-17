package com.castor.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.castor.app.ui.HomeScreen
import com.castor.feature.media.ui.MediaScreen
import com.castor.feature.messaging.ui.ConversationScreen
import com.castor.feature.messaging.ui.MessagingScreen
import com.castor.feature.reminders.ui.RemindersScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Root navigation host for the Castor application.
 *
 * Routes:
 * - "home"         — main dashboard
 * - "messages"     — unified messaging inbox (split-pane on tablets)
 * - "conversation" — individual conversation thread (phone-only; tablets use embedded pane)
 * - "media"        — media aggregation
 * - "reminders"    — smart reminders
 */
@Composable
fun CastorNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToMessages = { navController.navigate("messages") },
                onNavigateToMedia = { navController.navigate("media") },
                onNavigateToReminders = { navController.navigate("reminders") }
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
            MediaScreen(onBack = { navController.popBackStack() })
        }

        composable("reminders") {
            RemindersScreen(onBack = { navController.popBackStack() })
        }
    }
}
