package com.castor.feature.messaging.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.core.common.model.CastorMessage
import com.castor.core.common.model.MessageSource
import com.castor.core.common.util.DateUtils
import com.castor.core.ui.components.SourceBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagingScreen(
    onBack: () -> Unit,
    viewModel: MessagingViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    var selectedFilter by remember { mutableStateOf<MessageSource?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter chips
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("All") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = selectedFilter == MessageSource.WHATSAPP,
                    onClick = {
                        selectedFilter = if (selectedFilter == MessageSource.WHATSAPP) null
                        else MessageSource.WHATSAPP
                    },
                    label = { Text("WhatsApp") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = selectedFilter == MessageSource.TEAMS,
                    onClick = {
                        selectedFilter = if (selectedFilter == MessageSource.TEAMS) null
                        else MessageSource.TEAMS
                    },
                    label = { Text("Teams") }
                )
            }

            // Message list
            val filteredMessages = if (selectedFilter != null) {
                messages.filter { it.source == selectedFilter }
            } else messages

            if (filteredMessages.isEmpty()) {
                Text(
                    text = "No messages yet. Enable Notification Access in Settings to start receiving messages.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredMessages, key = { it.id }) { message ->
                        MessageItem(message = message)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageItem(message: CastorMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row {
            SourceBadge(source = message.source)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message.sender,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = DateUtils.formatRelativeTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (message.groupName != null) {
            Text(
                text = message.groupName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3
        )
    }
}
