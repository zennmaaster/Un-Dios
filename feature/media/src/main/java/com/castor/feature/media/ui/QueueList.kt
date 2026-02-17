package com.castor.feature.media.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.common.model.MediaSource
import com.castor.core.common.model.UnifiedMediaItem
import com.castor.core.ui.theme.AudibleOrange
import com.castor.core.ui.theme.SpotifyGreen
import com.castor.core.ui.theme.TerminalColors
import com.castor.core.ui.theme.YouTubeRed

// =============================================================================
// QueueList — Reusable composable for the unified media queue
// =============================================================================

/**
 * Displays the unified media queue as a scrollable list with drag-to-reorder
 * handles, source-colored badges, and remove buttons per item.
 *
 * Follows the Un-Dios terminal aesthetic: dark background, monospace metadata,
 * source-colored accents (Spotify green, YouTube red, Audible orange).
 *
 * @param queue The ordered list of items in the queue.
 * @param currentItemId The ID of the currently-playing item (highlighted).
 * @param onRemoveItem Callback when the user taps the remove button on an item.
 * @param onMoveItem Callback when a drag-to-reorder completes.
 * @param onItemClick Callback when the user taps an item (e.g., play immediately).
 * @param modifier Modifier for the root composable.
 */
@Composable
fun QueueList(
    queue: List<UnifiedMediaItem>,
    currentItemId: String?,
    onRemoveItem: (String) -> Unit,
    onMoveItem: (fromPosition: Int, toPosition: Int) -> Unit,
    onItemClick: (UnifiedMediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val monoStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        color = TerminalColors.Command
    )

    if (queue.isEmpty()) {
        QueueEmptyState(modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier
            .background(TerminalColors.Background)
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(
            items = queue,
            key = { _, item -> item.id }
        ) { index, item ->
            val isCurrent = item.id == currentItemId

            QueueItemRow(
                item = item,
                position = index,
                isCurrent = isCurrent,
                monoStyle = monoStyle,
                onRemove = { onRemoveItem(item.id) },
                onClick = { onItemClick(item) },
                modifier = Modifier.animateItem()
            )
        }

        // Bottom spacer so the last item isn't obscured.
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// =============================================================================
// QueueItemRow — Single queue entry
// =============================================================================

/**
 * A single row in the queue list displaying position, source badge, title,
 * artist, duration, drag handle, and remove button.
 */
@Composable
private fun QueueItemRow(
    item: UnifiedMediaItem,
    position: Int,
    isCurrent: Boolean,
    monoStyle: TextStyle,
    onRemove: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sourceColor = item.source.toAccentColor()

    val bgColor by animateColorAsState(
        targetValue = if (isCurrent) {
            sourceColor.copy(alpha = 0.12f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(300),
        label = "queueItemBg"
    )

    val elevation by animateDpAsState(
        targetValue = if (isCurrent) 2.dp else 0.dp,
        animationSpec = tween(200),
        label = "queueItemElevation"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Position number
        Text(
            text = String.format("%02d", position + 1),
            style = monoStyle.copy(
                fontSize = 11.sp,
                color = if (isCurrent) sourceColor else TerminalColors.Subtext,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
            ),
            modifier = Modifier.width(24.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Now-playing indicator or source badge
        if (isCurrent) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Now playing",
                tint = sourceColor,
                modifier = Modifier.size(16.dp)
            )
        } else {
            MediaSourceBadge(source = item.source)
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Title + Artist
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.title,
                style = monoStyle.copy(
                    fontSize = 13.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) TerminalColors.Command else TerminalColors.Output
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val artistName = item.artist
            if (artistName != null) {
                Text(
                    text = artistName,
                    style = monoStyle.copy(
                        fontSize = 11.sp,
                        color = if (isCurrent) sourceColor.copy(alpha = 0.8f)
                        else TerminalColors.Timestamp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Duration
        item.durationMs?.let { durationMs ->
            Text(
                text = formatDuration(durationMs),
                style = monoStyle.copy(
                    fontSize = 10.sp,
                    color = TerminalColors.Subtext
                ),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }

        // Drag handle
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = "Drag to reorder",
            tint = TerminalColors.Subtext.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )

        // Remove button
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove from queue",
                tint = TerminalColors.Subtext.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// =============================================================================
// QueueEmptyState
// =============================================================================

/**
 * Shown when the queue is empty. Terminal-styled empty state with a hint to add items.
 */
@Composable
private fun QueueEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.QueueMusic,
                contentDescription = null,
                tint = TerminalColors.Subtext.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp)
            )

            Text(
                text = "Queue empty",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Subtext
                )
            )

            Text(
                text = "Search or browse to add tracks.",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Subtext.copy(alpha = 0.7f)
                )
            )

            // Source hints
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SourceHintChip(label = "Spotify", color = SpotifyGreen)
                SourceHintChip(label = "YouTube", color = YouTubeRed)
                SourceHintChip(label = "Audible", color = AudibleOrange)
            }
        }
    }
}

/**
 * Small chip indicating a source that can be added from.
 */
@Composable
private fun SourceHintChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = color.copy(alpha = 0.7f)
            )
        )
    }
}

// =============================================================================
// MediaSourceBadge — Source indicator badge
// =============================================================================

/**
 * A small badge showing the media source name with source-colored styling.
 * Used throughout the media UI for identifying the origin of each item.
 */
@Composable
fun MediaSourceBadge(
    source: MediaSource,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (source) {
        MediaSource.SPOTIFY -> "SP" to SpotifyGreen
        MediaSource.YOUTUBE -> "YT" to YouTubeRed
        MediaSource.AUDIBLE -> "AU" to AudibleOrange
        else -> source.name.take(2) to TerminalColors.Accent
    }

    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
    }
}

/**
 * Full-name source badge variant (e.g., "Spotify" instead of "SP").
 */
@Composable
fun MediaSourceBadgeFull(
    source: MediaSource,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (source) {
        MediaSource.SPOTIFY -> "Spotify" to SpotifyGreen
        MediaSource.YOUTUBE -> "YouTube" to YouTubeRed
        MediaSource.AUDIBLE -> "Audible" to AudibleOrange
        else -> source.name to TerminalColors.Accent
    }

    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
    }
}

// =============================================================================
// Utility
// =============================================================================

/** Map a [MediaSource] to its accent color. */
@Composable
@ReadOnlyComposable
fun MediaSource.toAccentColor(): Color = when (this) {
    MediaSource.SPOTIFY -> SpotifyGreen
    MediaSource.YOUTUBE -> YouTubeRed
    MediaSource.AUDIBLE -> AudibleOrange
    else -> TerminalColors.Accent
}

/**
 * Format a duration in milliseconds to "m:ss" or "h:mm:ss".
 */
fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
