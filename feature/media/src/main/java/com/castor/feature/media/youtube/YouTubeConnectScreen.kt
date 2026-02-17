package com.castor.feature.media.youtube

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.theme.TerminalColors
import kotlinx.coroutines.launch

/**
 * Screen for connecting / disconnecting the user's Google (YouTube) account.
 *
 * Uses the terminal-inspired aesthetic consistent with Un-Dios. The flow:
 * 1. User taps "Connect YouTube" -- launches the Google OAuth consent screen
 *    via AppAuth.
 * 2. On success, the screen transitions to a "connected" state showing the
 *    account status and a disconnect option.
 *
 * @param authManager The [YouTubeAuthManager] handling OAuth.
 * @param onBack Navigation callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeConnectScreen(
    authManager: YouTubeAuthManager,
    onBack: () -> Unit
) {
    val isAuthenticated by authManager.isAuthenticated.collectAsState()
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Activity result launcher for the OAuth flow
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            isLoading = true
            errorMessage = null
            scope.launch {
                val success = authManager.handleAuthorizationResponse(data)
                isLoading = false
                if (!success) {
                    errorMessage = "Authentication failed. Please try again."
                }
            }
        } else {
            errorMessage = "Sign-in was cancelled."
        }
    }

    Scaffold(
        containerColor = TerminalColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "YouTube",
                        fontFamily = FontFamily.Monospace,
                        color = TerminalColors.Command
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TerminalColors.Command
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TerminalColors.StatusBar
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // -- Terminal header -----------------------------------------------
            Text(
                text = "castor@media:~$ youtube --connect",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = TerminalColors.Prompt,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isAuthenticated) {
                // -- Connected state ------------------------------------------
                ConnectedContent(
                    onDisconnect = {
                        authManager.disconnect()
                    }
                )
            } else {
                // -- Disconnected state ---------------------------------------
                DisconnectedContent(
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onConnect = {
                        errorMessage = null
                        val intent = authManager.buildAuthIntent()
                        authLauncher.launch(intent)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -- Permissions info ---------------------------------------------
            PermissionsInfoCard()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// =============================================================================
// Connected sub-component
// =============================================================================

@Composable
private fun ConnectedContent(
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = TerminalColors.Surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Connected",
                tint = TerminalColors.Success,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "> YouTube connected",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = TerminalColors.Success
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your Google account is linked. You can search YouTube, browse playlists, and queue videos in Un-Dios.",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Output,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Status details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = TerminalColors.Overlay,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                StatusRow(label = "STATUS", value = "CONNECTED", valueColor = TerminalColors.Success)
                Spacer(modifier = Modifier.height(4.dp))
                StatusRow(label = "SERVICE", value = "YouTube Data API v3")
                Spacer(modifier = Modifier.height(4.dp))
                StatusRow(label = "SCOPES", value = "youtube, youtube.readonly")
                Spacer(modifier = Modifier.height(4.dp))
                StatusRow(label = "AUTH", value = "OAuth 2.0 + PKCE")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Disconnect button
            OutlinedButton(
                onClick = onDisconnect,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TerminalColors.Error
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Disconnect YouTube",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// =============================================================================
// Disconnected sub-component
// =============================================================================

@Composable
private fun DisconnectedContent(
    isLoading: Boolean,
    errorMessage: String?,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = TerminalColors.Surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = "YouTube",
                tint = TerminalColors.Error, // Red for YouTube brand
                modifier = Modifier.size(56.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Connect YouTube",
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Command
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Link your Google account to search YouTube, browse your playlists, and add videos to your Un-Dios queue.",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Output,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Error message
            if (errorMessage != null) {
                Text(
                    text = "> ERROR: $errorMessage",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = TerminalColors.Error.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Connect button
            Button(
                onClick = onConnect,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TerminalColors.Error, // Red for YouTube
                    contentColor = TerminalColors.Background
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = TerminalColors.Background,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Authenticating...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sign in with Google",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// =============================================================================
// Permissions info card
// =============================================================================

@Composable
private fun PermissionsInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = TerminalColors.Surface.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "# Permissions requested:",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Timestamp
            )
            Spacer(modifier = Modifier.height(8.dp))
            PermissionRow(
                scope = "youtube.readonly",
                description = "View your YouTube activity (playlists, likes)"
            )
            Spacer(modifier = Modifier.height(4.dp))
            PermissionRow(
                scope = "youtube",
                description = "Manage your YouTube account"
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "# Tokens are stored locally in encrypted storage.\n# You can disconnect at any time.",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Timestamp,
                lineHeight = 16.sp
            )
        }
    }
}

// =============================================================================
// Small reusable composables
// =============================================================================

@Composable
private fun StatusRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = TerminalColors.Output
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label:",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = TerminalColors.Timestamp,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = valueColor
        )
    }
}

@Composable
private fun PermissionRow(
    scope: String,
    description: String
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "  - ",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = TerminalColors.Timestamp
        )
        Column {
            Text(
                text = scope,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Info
            )
            Text(
                text = description,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Timestamp,
                lineHeight = 15.sp
            )
        }
    }
}
