package com.castor.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.core.ui.components.AgentCard
import com.castor.core.ui.theme.CastorPrimary
import com.castor.core.ui.theme.SpotifyGreen
import com.castor.core.ui.theme.TeamsBlue
import com.castor.core.ui.theme.WhatsAppGreen
import com.castor.feature.commandbar.CommandBar
import com.castor.feature.commandbar.CommandBarViewModel

@Composable
fun HomeScreen(
    onNavigateToMessages: () -> Unit,
    onNavigateToMedia: () -> Unit,
    onNavigateToReminders: () -> Unit,
    viewModel: CommandBarViewModel = hiltViewModel()
) {
    val commandBarState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Header
        Text(
            text = "Castor",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        // Command Bar
        CommandBar(
            state = commandBarState,
            onSubmit = viewModel::onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Agent Cards
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                AgentCard(
                    title = "Messages",
                    subtitle = "WhatsApp & Teams unified inbox",
                    icon = Icons.Default.ChatBubble,
                    accentColor = WhatsAppGreen,
                    onClick = onNavigateToMessages
                ) {
                    Text(
                        text = "Tap to view messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                AgentCard(
                    title = "Media",
                    subtitle = "Spotify, YouTube & Audible",
                    icon = Icons.Default.Album,
                    accentColor = SpotifyGreen,
                    onClick = onNavigateToMedia
                ) {
                    Text(
                        text = "Nothing playing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                AgentCard(
                    title = "Reminders",
                    subtitle = "Calendar & tasks",
                    icon = Icons.Default.Notifications,
                    accentColor = TeamsBlue,
                    onClick = onNavigateToReminders
                ) {
                    Text(
                        text = "No upcoming reminders",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                AgentCard(
                    title = "AI Assistant",
                    subtitle = "On-device intelligence",
                    icon = Icons.Default.SmartToy,
                    accentColor = CastorPrimary,
                    onClick = { /* Open AI chat */ }
                ) {
                    Text(
                        text = "Model: Loading...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
