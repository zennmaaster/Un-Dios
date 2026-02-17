package com.castor.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.castor.app.ui.HomeScreen
import com.castor.feature.messaging.ui.MessagingScreen
import com.castor.feature.media.ui.MediaScreen
import com.castor.feature.reminders.ui.RemindersScreen

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
            MessagingScreen(onBack = { navController.popBackStack() })
        }
        composable("media") {
            MediaScreen(onBack = { navController.popBackStack() })
        }
        composable("reminders") {
            RemindersScreen(onBack = { navController.popBackStack() })
        }
    }
}
