package com.castor.feature.recommendations.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.core.common.model.MediaSource
import com.castor.core.data.db.entity.WatchHistoryEntity
import com.castor.core.ui.theme.ChromeYellow
import com.castor.core.ui.theme.NetflixRed
import com.castor.core.ui.theme.PrimeBlue
import com.castor.core.ui.theme.TerminalColors
import com.castor.core.ui.theme.YouTubeRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Watch history timeline showing what the system has tracked.
 *
 * Features:
 * - Per-source filtering (Netflix, Prime, YouTube, Chrome)
 * - Stats: total watch time, favourite genres, most-used platform
 * - Timeline view of watched content
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatchHistoryScreen(
    onBack: () -> Unit,
    viewModel: WatchHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
            .statusBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalColors.StatusBar)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TerminalColors.Command
                )
            }
            Text(
                text = "$ history --media",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Prompt
                )
            )
        }

        // Stats row
        StatsRow(
            totalCount = state.totalCount,
            totalWatchTimeMs = state.totalWatchTimeMs,
            topGenres = state.topGenres,
            mostUsedPlatform = state.mostUsedPlatform
        )

        // Source filter chips
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val videoSources = listOf(
                MediaSource.NETFLIX, MediaSource.PRIME_VIDEO,
                MediaSource.YOUTUBE, MediaSource.CHROME_BROWSER
            )
            videoSources.forEach { source ->
                FilterChip(
                    selected = state.selectedSourceFilter == source,
                    onClick = { viewModel.onSelectSourceFilter(source) },
                    label = {
                        Text(
                            text = sourceDisplayName(source),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = TerminalColors.Surface,
                        labelColor = TerminalColors.Output,
                        selectedContainerColor = sourceColor(source).copy(alpha = 0.2f),
                        selectedLabelColor = sourceColor(source)
                    ),
                    shape = RoundedCornerShape(6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // History list
        if (state.history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$ echo \"No watch history yet\"",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = TerminalColors.Timestamp
                    )
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = state.history,
                    key = { it.id }
                ) { entry ->
                    WatchHistoryItem(entry = entry)
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Sub-components
// -------------------------------------------------------------------------------------

@Composable
private fun StatsRow(
    totalCount: Int,
    totalWatchTimeMs: Long,
    topGenres: List<String>,
    mostUsedPlatform: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = TerminalColors.Surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "# stats",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Accent
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            StatLine(label = "tracked", value = "$totalCount items")
            StatLine(label = "watch_time", value = formatDuration(totalWatchTimeMs))
            if (topGenres.isNotEmpty()) {
                StatLine(label = "top_genres", value = topGenres.take(3).joinToString(", "))
            }
            if (mostUsedPlatform != null) {
                StatLine(label = "top_source", value = mostUsedPlatform)
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = "$label=",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Timestamp
            )
        )
        Text(
            text = "\"$value\"",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Success
            )
        )
    }
}

@Composable
private fun WatchHistoryItem(entry: WatchHistoryEntity) {
    val color = sourceColor(entry.source)
    val dateFormatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateStr = dateFormatter.format(Date(entry.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TerminalColors.Surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Source icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = sourceIcon(entry.source),
                    contentDescription = entry.source,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TerminalColors.Command
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = entry.contentType,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TerminalColors.Info
                        )
                    )
                    val genreName = entry.genre
                    if (genreName != null) {
                        Text(
                            text = genreName,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TerminalColors.Accent
                            )
                        )
                    }
                }
            }

            // Timestamp
            Text(
                text = dateStr,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }
    }
}

// -------------------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------------------

private fun sourceDisplayName(source: MediaSource): String = when (source) {
    MediaSource.NETFLIX -> "Netflix"
    MediaSource.PRIME_VIDEO -> "Prime"
    MediaSource.YOUTUBE -> "YouTube"
    MediaSource.CHROME_BROWSER -> "Chrome"
    else -> source.name
}

@Composable
@ReadOnlyComposable
private fun sourceColor(source: MediaSource): Color = when (source) {
    MediaSource.NETFLIX -> NetflixRed
    MediaSource.PRIME_VIDEO -> PrimeBlue
    MediaSource.YOUTUBE -> YouTubeRed
    MediaSource.CHROME_BROWSER -> ChromeYellow
    else -> TerminalColors.Info
}

@Composable
@ReadOnlyComposable
private fun sourceColor(sourceName: String): Color {
    val source = MediaSource.entries.firstOrNull { it.name == sourceName }
    return if (source != null) sourceColor(source) else TerminalColors.Info
}

private fun sourceIcon(sourceName: String) = when {
    "NETFLIX" in sourceName -> Icons.Default.Tv
    "PRIME" in sourceName -> Icons.Default.Movie
    "YOUTUBE" in sourceName -> Icons.Default.PlayArrow
    "CHROME" in sourceName -> Icons.Default.Web
    else -> Icons.Default.OndemandVideo
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0m"
    val hours = ms / (1000 * 60 * 60)
    val minutes = (ms % (1000 * 60 * 60)) / (1000 * 60)
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
