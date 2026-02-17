package com.castor.feature.media.audible

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.castor.core.ui.theme.TerminalColors

/**
 * UI state representing the current Audible playback, sourced from the
 * [AudibleMediaAdapter] reading Audible's MediaSession.
 */
data class AudibleStatusUiState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val bookTitle: String? = null,
    val author: String? = null,
    val chapterInfo: String? = null,
    val coverArtUrl: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

/**
 * Compose card displaying the current Audible audiobook status.
 *
 * Uses the terminal-style monospace aesthetic consistent with Un-Dios.
 * All data comes from Audible's MediaSession -- the card includes a note
 * reminding the user that library browsing must happen in the Audible app.
 *
 * @param state Current playback state from [AudibleMediaAdapter].
 * @param onPlay Callback to resume playback.
 * @param onPause Callback to pause playback.
 * @param onSkipNext Callback to skip to the next chapter.
 * @param onSkipPrevious Callback to skip to the previous chapter.
 * @param modifier Optional modifier for the card.
 */
@Composable
fun AudibleStatusCard(
    state: AudibleStatusUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = TerminalColors.Surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // -- Header -------------------------------------------------------
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = "Audible",
                    tint = TerminalColors.Warning, // Orange for Audible branding
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "castor@media:~$ audible --status",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TerminalColors.Prompt
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!state.isConnected) {
                // -- No active session ----------------------------------------
                Text(
                    text = "> Audible session not detected",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Timestamp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "> Start playing an audiobook in Audible to see it here.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Timestamp
                )
            } else if (state.bookTitle == null) {
                // -- Connected but nothing playing ----------------------------
                Text(
                    text = "> Audible is open but not playing.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Output
                )
            } else {
                // -- Now playing ----------------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Cover art
                    if (state.coverArtUrl != null) {
                        AsyncImage(
                            model = state.coverArtUrl,
                            contentDescription = "Audiobook cover",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    // Metadata
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = state.bookTitle,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = TerminalColors.Command,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.author != null) {
                            Text(
                                text = "by ${state.author}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TerminalColors.Output,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (state.chapterInfo != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.chapterInfo,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TerminalColors.Info,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // -- Progress bar ---------------------------------------------
                if (state.durationMs > 0) {
                    val progress = (state.positionMs.toFloat() / state.durationMs.toFloat())
                        .coerceIn(0f, 1f)

                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = TerminalColors.Warning,
                            trackColor = TerminalColors.Selection,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(state.positionMs),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TerminalColors.Timestamp
                            )
                            Text(
                                text = formatDuration(state.durationMs),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TerminalColors.Timestamp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // -- Transport controls ---------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onSkipPrevious) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous chapter",
                            tint = TerminalColors.Command,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Play/Pause button with highlight
                    IconButton(
                        onClick = { if (state.isPlaying) onPause() else onPlay() },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = TerminalColors.Warning.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(24.dp)
                            )
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = TerminalColors.Warning,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(onClick = onSkipNext) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next chapter",
                            tint = TerminalColors.Command,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // -- Footer note --------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = TerminalColors.Overlay,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "# Audible is controlled via its notification.\n# Open Audible to browse your library.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Timestamp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// =============================================================================
// Helpers
// =============================================================================

/**
 * Format a duration in milliseconds to "MM:SS" or "H:MM:SS".
 */
private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
