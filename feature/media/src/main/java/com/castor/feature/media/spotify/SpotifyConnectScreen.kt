package com.castor.feature.media.spotify

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.castor.core.ui.theme.SpotifyGreen
import com.castor.core.ui.theme.TerminalColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// =============================================================================
// ViewModel
// =============================================================================

@HiltViewModel
class SpotifyConnectViewModel @Inject constructor(
    val authManager: SpotifyAuthManager
) : ViewModel() {
    val isConnected = authManager.isAuthenticated

    fun disconnect() {
        authManager.logout()
    }
}

// =============================================================================
// Compose Screen
// =============================================================================

/**
 * Full-screen Spotify connection page with terminal-style aesthetics.
 *
 * - Shows a "Connect Spotify" button with Spotify green branding when
 *   the user is not yet authenticated.
 * - Displays connection status and a disconnect option once linked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyConnectScreen(
    onBack: () -> Unit,
    viewModel: SpotifyConnectViewModel = hiltViewModel()
) {
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.authManager.handleAuthResponse(result.data!!)
        }
    }

    Scaffold(
        containerColor = TerminalColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "spotify :: connect",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Command
                        )
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(SpotifyGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Spotify Integration",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Command
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Control playback, search tracks, and manage\nyour queue directly from Un-Dios.",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TerminalColors.Timestamp,
                    textAlign = TextAlign.Center
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ----- Connection status indicator -----
            ConnectionStatusIndicator(isConnected = isConnected)

            Spacer(modifier = Modifier.height(32.dp))

            // ----- Action button -----
            AnimatedVisibility(
                visible = !isConnected,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Button(
                    onClick = {
                        val intent = viewModel.authManager.createAuthIntent()
                        authLauncher.launch(intent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpotifyGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Connect Spotify",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            AnimatedVisibility(
                visible = isConnected,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                OutlinedButton(
                    onClick = { viewModel.disconnect() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TerminalColors.Error
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = TerminalColors.Error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = "Disconnect Spotify",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Scopes info
            ScopesInfo()
        }
    }
}

// =============================================================================
// Sub-components
// =============================================================================

@Composable
private fun ConnectionStatusIndicator(isConnected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .border(
                width = 1.dp,
                color = if (isConnected) TerminalColors.Success.copy(alpha = 0.4f)
                else TerminalColors.Subtext.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                color = if (isConnected) TerminalColors.Success.copy(alpha = 0.06f)
                else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    if (isConnected) TerminalColors.Success
                    else TerminalColors.Subtext
                )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (isConnected) "connected" else "not connected",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isConnected) TerminalColors.Success else TerminalColors.Subtext
            )
        )
        if (isConnected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Connected",
                tint = TerminalColors.Success,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ScopesInfo() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                TerminalColors.Surface.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = "$ cat /etc/spotify/scopes",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Prompt
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        val scopes = listOf(
            "user-read-playback-state" to "Read current playback",
            "user-modify-playback-state" to "Control playback",
            "user-read-currently-playing" to "See now-playing track",
            "playlist-read-private" to "Access your playlists",
            "user-library-read" to "Browse saved library",
            "streaming" to "Stream audio content"
        )

        scopes.forEach { (scope, description) ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = scope,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Info
                    ),
                    modifier = Modifier.width(220.dp)
                )
                Text(
                    text = "# $description",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Timestamp
                    )
                )
            }
        }
    }
}

