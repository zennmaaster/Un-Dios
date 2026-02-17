package com.castor.feature.media.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.castor.core.common.model.MediaSource
import com.castor.core.common.model.UnifiedMediaItem
import com.castor.core.ui.theme.AudibleOrange
import com.castor.core.ui.theme.KindleBlue
import com.castor.core.ui.theme.SpotifyGreen
import com.castor.core.ui.theme.TerminalColors
import com.castor.core.ui.theme.YouTubeRed

// =============================================================================
// MediaScreen — Un-Dios Ubuntu-style Media Center
// =============================================================================

/**
 * The unified media screen for Un-Dios. Provides a terminal-aesthetic media center
 * with cross-source playback control, a unified queue, and source browsing tabs.
 *
 * Layout:
 * - **Title bar**: Ubuntu-style window header with close/minimize/maximize dots
 * - **Search bar**: Monospace terminal-style search across connected sources
 * - **Now Playing section**: Album art, metadata, progress bar, transport controls
 * - **Source tabs**: Queue | Spotify | YouTube | Audible
 * - **Tab content**: Queue list, source-specific content, or connection CTAs
 *
 * @param onBack Navigation callback to return to the previous screen.
 * @param viewModel The [MediaViewModel] providing state and actions.
 */
@Composable
fun MediaScreen(
    onBack: () -> Unit,
    onNavigateToBookSync: () -> Unit = {},
    viewModel: MediaViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
    ) {
        // Title bar
        MediaTitleBar(onBack = onBack)

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Search bar
            MediaSearchBar(
                query = state.searchQuery,
                onQueryChanged = viewModel::onSearchQueryChanged,
                onClearSearch = viewModel::onClearSearch,
                results = state.searchResults,
                isSearching = state.isSearching,
                sourceFilter = state.sourceFilter,
                onToggleSourceFilter = viewModel::onToggleSourceFilter,
                onAddToQueue = viewModel::onAddToQueue,
                onPlayNow = viewModel::onPlayNow
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Now Playing section
            NowPlayingSection(
                currentItem = state.currentItem,
                isPlaying = state.isPlaying,
                positionMs = state.playbackPositionMs,
                nowPlayingTitle = state.nowPlaying.title,
                nowPlayingArtist = state.nowPlaying.artist,
                nowPlayingArtUri = state.nowPlaying.albumArtUri,
                nowPlayingDurationMs = state.nowPlaying.durationMs,
                nowPlayingSource = state.nowPlaying.source,
                onPlayPause = viewModel::onPlayPause,
                onSkipNext = viewModel::onSkipNext,
                onSkipPrevious = viewModel::onSkipPrevious,
                onSeekTo = viewModel::onSeekTo
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Library Sync section — Kindle/Audible cross-device sync
            LibrarySyncSection(onNavigateToBookSync = onNavigateToBookSync)

            Spacer(modifier = Modifier.height(16.dp))

            // Source tabs
            SourceTabBar(
                selectedTab = state.selectedTab,
                onSelectTab = viewModel::onSelectTab,
                spotifyConnected = state.spotifyConnected,
                youtubeConnected = state.youtubeConnected,
                audibleConnected = state.audibleConnected
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tab content
            AnimatedContent(
                targetState = state.selectedTab,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "tabContent"
            ) { tab ->
                when (tab) {
                    MediaTab.QUEUE -> QueueTabContent(
                        queue = state.queue,
                        currentItemId = state.currentItem?.id,
                        queueSize = state.queueSize,
                        onRemoveItem = viewModel::onRemoveFromQueue,
                        onMoveItem = viewModel::onMoveQueueItem,
                        onItemClick = viewModel::onPlayNow,
                        onClearQueue = viewModel::onClearQueue
                    )
                    MediaTab.SPOTIFY -> SourceTabContent(
                        source = MediaSource.SPOTIFY,
                        isConnected = state.spotifyConnected,
                        sourceColor = SpotifyGreen
                    )
                    MediaTab.YOUTUBE -> SourceTabContent(
                        source = MediaSource.YOUTUBE,
                        isConnected = state.youtubeConnected,
                        sourceColor = YouTubeRed
                    )
                    MediaTab.AUDIBLE -> SourceTabContent(
                        source = MediaSource.AUDIBLE,
                        isConnected = state.audibleConnected,
                        sourceColor = AudibleOrange
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// =============================================================================
// LibrarySyncSection — Kindle/Audible sync entry point
// =============================================================================

/**
 * A compact banner on the media screen that links to the full Book Sync screen.
 * Shows a brief description and a "View Library Sync" call-to-action.
 */
@Composable
private fun LibrarySyncSection(onNavigateToBookSync: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .clickable(onClick = onNavigateToBookSync)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(TerminalColors.Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = TerminalColors.Accent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Library Sync",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Command
                    )
                )
                Text(
                    text = "Kindle + Audible position tracking",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Subtext
                    )
                )
            }

            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Open library sync",
                tint = TerminalColors.Subtext,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// =============================================================================
// MediaTitleBar — Ubuntu-style window header
// =============================================================================

/**
 * Terminal-style title bar with window control dots and title.
 */
@Composable
private fun MediaTitleBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalColors.Surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Window control dots
            WindowDot(color = TerminalColors.Error, onClick = onBack)
            Spacer(modifier = Modifier.width(5.dp))
            WindowDot(color = TerminalColors.Warning)
            Spacer(modifier = Modifier.width(5.dp))
            WindowDot(color = TerminalColors.Success)
            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "un-dios ~ media",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TerminalColors.Timestamp
                )
            )
        }

        // Back arrow for accessibility
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TerminalColors.Timestamp,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * A small circular dot replicating Ubuntu/GNOME window controls.
 */
@Composable
private fun WindowDot(
    color: Color,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.8f))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
    )
}

// =============================================================================
// NowPlayingSection — Large album art, metadata, transport controls
// =============================================================================

/**
 * The "Now Playing" section at the top of the media screen.
 *
 * Shows album art, title, artist, source badge, a seek bar with timestamps,
 * and transport controls (previous / play-pause / next).
 *
 * Falls back to the [MediaSessionMonitor]'s now-playing state when there is
 * no queue item (e.g., user is playing directly from Spotify without the queue).
 */
@Composable
private fun NowPlayingSection(
    currentItem: UnifiedMediaItem?,
    isPlaying: Boolean,
    positionMs: Long,
    nowPlayingTitle: String?,
    nowPlayingArtist: String?,
    nowPlayingArtUri: String?,
    nowPlayingDurationMs: Long,
    nowPlayingSource: MediaSource?,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    // Use queue item if available, otherwise fall back to session monitor state.
    val title = currentItem?.title ?: nowPlayingTitle ?: "Nothing Playing"
    val artist = currentItem?.artist ?: nowPlayingArtist
    val artUrl = currentItem?.albumArtUrl ?: nowPlayingArtUri
    val durationMs = currentItem?.durationMs ?: nowPlayingDurationMs.takeIf { it > 0 }
    val source = currentItem?.source ?: nowPlayingSource
    val hasContent = currentItem != null || nowPlayingTitle != null

    val sourceColor = source?.toAccentColor() ?: TerminalColors.Accent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        sourceColor.copy(alpha = 0.08f),
                        TerminalColors.Surface.copy(alpha = 0.6f)
                    )
                )
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Album art
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(TerminalColors.Selection),
            contentAlignment = Alignment.Center
        ) {
            if (artUrl != null) {
                AsyncImage(
                    model = artUrl,
                    contentDescription = "Album art for $title",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Placeholder icon
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = TerminalColors.Subtext.copy(alpha = 0.3f),
                    modifier = Modifier.size(56.dp)
                )
            }

            // Playing indicator overlay
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(sourceColor.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    PlayingBarsAnimation(color = TerminalColors.Background)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Source badge
        if (source != null) {
            MediaSourceBadgeFull(source = source)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Title
        Text(
            text = title,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Command
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Artist
        if (artist != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = artist,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = sourceColor.copy(alpha = 0.8f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!hasContent) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Play something from Spotify, YouTube, or Audible",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Subtext
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Seek bar with timestamps
        if (durationMs != null && durationMs > 0) {
            SeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                accentColor = sourceColor,
                onSeekTo = onSeekTo
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Transport controls
        TransportControls(
            isPlaying = isPlaying,
            hasContent = hasContent,
            accentColor = sourceColor,
            onPlayPause = onPlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious
        )
    }
}

// =============================================================================
// SeekBar — Progress bar with timestamps
// =============================================================================

/**
 * A seek bar showing current position and total duration with a draggable slider.
 */
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    accentColor: Color,
    onSeekTo: (Long) -> Unit
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }

    val progress = if (durationMs > 0) {
        if (isSeeking) seekPosition
        else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = progress,
            onValueChange = { value ->
                isSeeking = true
                seekPosition = value
            },
            onValueChangeFinished = {
                isSeeking = false
                onSeekTo((seekPosition * durationMs).toLong())
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = TerminalColors.Selection
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayPosition = if (isSeeking) {
                (seekPosition * durationMs).toLong()
            } else {
                positionMs
            }
            Text(
                text = formatDuration(displayPosition),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Subtext
                )
            )
            Text(
                text = formatDuration(durationMs),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Subtext
                )
            )
        }
    }
}

// =============================================================================
// TransportControls — Prev / Play-Pause / Next
// =============================================================================

/**
 * The main transport control buttons: skip previous, play/pause, skip next.
 */
@Composable
private fun TransportControls(
    isPlaying: Boolean,
    hasContent: Boolean,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Skip previous
        IconButton(
            onClick = onSkipPrevious,
            enabled = hasContent,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                tint = if (hasContent) TerminalColors.Output else TerminalColors.Subtext,
                modifier = Modifier.size(28.dp)
            )
        }

        // Play / Pause — large central button
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    if (hasContent) accentColor.copy(alpha = 0.2f)
                    else TerminalColors.Selection
                )
                .clickable(enabled = hasContent, onClick = onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = if (hasContent) accentColor else TerminalColors.Subtext,
                modifier = Modifier.size(36.dp)
            )
        }

        // Skip next
        IconButton(
            onClick = onSkipNext,
            enabled = hasContent,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = if (hasContent) TerminalColors.Output else TerminalColors.Subtext,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// =============================================================================
// PlayingBarsAnimation — Small animated bars overlay
// =============================================================================

/**
 * Three animated vertical bars indicating active playback, similar to
 * a music equalizer visualization.
 */
@Composable
private fun PlayingBarsAnimation(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "playingBars")

    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(14.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(bar1Height.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(bar2Height.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(bar3Height.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
    }
}

// =============================================================================
// SourceTabBar — Queue | Spotify | YouTube | Audible
// =============================================================================

/**
 * Horizontal tab bar for switching between the queue and source-specific views.
 * Each tab shows a connection status indicator dot.
 */
@Composable
private fun SourceTabBar(
    selectedTab: MediaTab,
    onSelectTab: (MediaTab) -> Unit,
    spotifyConnected: Boolean,
    youtubeConnected: Boolean,
    audibleConnected: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        MediaTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            val tabColor = when (tab) {
                MediaTab.QUEUE -> TerminalColors.Accent
                MediaTab.SPOTIFY -> SpotifyGreen
                MediaTab.YOUTUBE -> YouTubeRed
                MediaTab.AUDIBLE -> AudibleOrange
            }
            val isConnected = when (tab) {
                MediaTab.QUEUE -> true
                MediaTab.SPOTIFY -> spotifyConnected
                MediaTab.YOUTUBE -> youtubeConnected
                MediaTab.AUDIBLE -> audibleConnected
            }

            val bgColor by animateColorAsState(
                targetValue = if (isSelected) tabColor.copy(alpha = 0.15f) else Color.Transparent,
                animationSpec = tween(200),
                label = "tabBg_${tab.name}"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(bgColor)
                    .clickable { onSelectTab(tab) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Connection status dot (not for Queue tab)
                    if (tab != MediaTab.QUEUE) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isConnected) tabColor.copy(alpha = 0.8f)
                                    else TerminalColors.Subtext.copy(alpha = 0.3f)
                                )
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }

                    Text(
                        text = tab.label,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) tabColor else TerminalColors.Subtext
                        )
                    )
                }
            }
        }
    }
}

// =============================================================================
// QueueTabContent — The unified queue view
// =============================================================================

/**
 * Content for the "Queue" tab: header with count and clear button, then the queue list.
 */
@Composable
private fun QueueTabContent(
    queue: List<UnifiedMediaItem>,
    currentItemId: String?,
    queueSize: Int,
    onRemoveItem: (String) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onItemClick: (UnifiedMediaItem) -> Unit,
    onClearQueue: () -> Unit
) {
    Column {
        // Queue header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = TerminalColors.Accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "queue",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Accent
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "($queueSize ${if (queueSize == 1) "track" else "tracks"})",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TerminalColors.Subtext
                    )
                )
            }

            if (queue.isNotEmpty()) {
                IconButton(
                    onClick = onClearQueue,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear queue",
                        tint = TerminalColors.Error.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        HorizontalDivider(
            color = TerminalColors.Selection.copy(alpha = 0.3f),
            thickness = 0.5.dp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // The queue list (renders its own empty state if empty)
        QueueList(
            queue = queue,
            currentItemId = currentItemId,
            onRemoveItem = onRemoveItem,
            onMoveItem = onMoveItem,
            onItemClick = onItemClick,
            modifier = Modifier.height(
                if (queue.isEmpty()) 200.dp
                else (queue.size.coerceAtMost(8) * 56 + 16).dp
            )
        )
    }
}

// =============================================================================
// SourceTabContent — Source-specific views (Spotify, YouTube, Audible)
// =============================================================================

/**
 * Content for a source-specific tab. Shows a connection CTA if disconnected,
 * or a placeholder for connected source browsing.
 */
@Composable
private fun SourceTabContent(
    source: MediaSource,
    isConnected: Boolean,
    sourceColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isConnected) {
            // Connected state
            ConnectedSourceView(source = source, sourceColor = sourceColor)
        } else {
            // Disconnected state — connection CTA
            DisconnectedSourceCta(source = source, sourceColor = sourceColor)
        }
    }
}

/**
 * View shown when a source is connected — shows available playlists/content.
 * (Placeholder until source adapters are fully integrated.)
 */
@Composable
private fun ConnectedSourceView(source: MediaSource, sourceColor: Color) {
    val (label, description) = when (source) {
        MediaSource.SPOTIFY -> "Spotify" to "Connected. Your playlists and library are available."
        MediaSource.YOUTUBE -> "YouTube" to "Connected. Your playlists and subscriptions are available."
        MediaSource.AUDIBLE -> "Audible" to "Connected. Your audiobook library is available."
        else -> source.name to "Connected."
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(sourceColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (source) {
                    MediaSource.SPOTIFY -> Icons.Default.MusicNote
                    MediaSource.YOUTUBE -> Icons.Default.PlayArrow
                    MediaSource.AUDIBLE -> Icons.Default.Headphones
                    else -> Icons.Default.MusicNote
                },
                contentDescription = label,
                tint = sourceColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = sourceColor
            )
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(TerminalColors.Success)
            )
            Text(
                text = "active session",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Success
                )
            )
        }

        Text(
            text = description,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Subtext
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // "Browse in app" button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(sourceColor.copy(alpha = 0.12f))
                .clickable { /* Open source app */ }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Open $label",
                    tint = sourceColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Open $label",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = sourceColor
                    )
                )
            }
        }
    }
}

/**
 * CTA shown when a source is not connected. Prompts the user to connect.
 */
@Composable
private fun DisconnectedSourceCta(source: MediaSource, sourceColor: Color) {
    val (label, instruction) = when (source) {
        MediaSource.SPOTIFY -> "Spotify" to "Open Spotify to enable media control."
        MediaSource.YOUTUBE -> "YouTube" to "Open YouTube to enable media control."
        MediaSource.AUDIBLE -> "Audible" to "Open Audible to enable audiobook control."
        else -> source.name to "Open the app to enable media control."
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(sourceColor.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = "Connect $label",
                tint = sourceColor.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = "Connect $label",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = sourceColor.copy(alpha = 0.7f)
            )
        )

        Text(
            text = instruction,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Subtext
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Un-Dios will detect active media sessions automatically.",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Subtext.copy(alpha = 0.6f)
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
