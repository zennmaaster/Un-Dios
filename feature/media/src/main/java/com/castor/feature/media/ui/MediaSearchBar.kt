package com.castor.feature.media.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
// MediaSearchBar — Terminal-styled search across connected sources
// =============================================================================

/**
 * A terminal-styled search bar for searching media across all connected sources
 * (Spotify, YouTube, Audible).
 *
 * Features:
 * - Monospace input with `$ search media...` placeholder
 * - Source filter chips (Spotify/YouTube/Audible) — tap to toggle
 * - Grouped results by source with "Add to queue" and "Play now" actions
 * - Loading spinner during search
 *
 * @param query The current search query text.
 * @param onQueryChanged Callback when the search text changes.
 * @param onClearSearch Callback to clear the search.
 * @param results The search results to display.
 * @param isSearching Whether a search is in progress.
 * @param sourceFilter The set of currently active source filters.
 * @param onToggleSourceFilter Callback to toggle a source filter chip.
 * @param onAddToQueue Callback when the user taps "Add to queue" on a result.
 * @param onPlayNow Callback when the user taps "Play now" on a result.
 * @param modifier Modifier for the root composable.
 */
@Composable
fun MediaSearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    results: List<UnifiedMediaItem>,
    isSearching: Boolean,
    sourceFilter: Set<MediaSource>,
    onToggleSourceFilter: (MediaSource) -> Unit,
    onAddToQueue: (UnifiedMediaItem) -> Unit,
    onPlayNow: (UnifiedMediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val monoStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        color = TerminalColors.Command
    )

    Column(modifier = modifier) {
        // Search input field
        SearchInputField(
            query = query,
            onQueryChanged = onQueryChanged,
            onClearSearch = onClearSearch,
            isSearching = isSearching,
            monoStyle = monoStyle
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Source filter chips
        SourceFilterChips(
            sourceFilter = sourceFilter,
            onToggleSourceFilter = onToggleSourceFilter
        )

        // Results dropdown
        AnimatedVisibility(
            visible = query.isNotBlank(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                SearchResultsList(
                    results = results,
                    isSearching = isSearching,
                    query = query,
                    monoStyle = monoStyle,
                    onAddToQueue = onAddToQueue,
                    onPlayNow = onPlayNow
                )
            }
        }
    }
}

// =============================================================================
// SearchInputField
// =============================================================================

/**
 * The terminal-styled search input with monospace font and `$ search media...` placeholder.
 */
@Composable
private fun SearchInputField(
    query: String,
    onQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    isSearching: Boolean,
    monoStyle: TextStyle
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Search icon or prompt
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = TerminalColors.Accent,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Terminal prompt prefix
        Text(
            text = "$ ",
            style = monoStyle.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )

        // Text input
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                textStyle = monoStyle.copy(
                    fontSize = 13.sp,
                    color = TerminalColors.Command
                ),
                cursorBrush = SolidColor(TerminalColors.Cursor),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { /* Search is triggered on text change */ }
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "search media...",
                                style = monoStyle.copy(
                                    fontSize = 13.sp,
                                    color = TerminalColors.Subtext
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        // Clear button or loading spinner
        if (isSearching) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = TerminalColors.Accent
            )
        } else if (query.isNotEmpty()) {
            IconButton(
                onClick = onClearSearch,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear search",
                    tint = TerminalColors.Subtext,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// =============================================================================
// SourceFilterChips
// =============================================================================

/**
 * Horizontal row of source filter chips. Tapping a chip toggles it on/off.
 * Active chips are highlighted in their source color.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceFilterChips(
    sourceFilter: Set<MediaSource>,
    onToggleSourceFilter: (MediaSource) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MediaSource.entries.forEach { source ->
            val isActive = source in sourceFilter
            val color = source.toAccentColor()

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isActive) color.copy(alpha = 0.20f)
                        else TerminalColors.Surface.copy(alpha = 0.5f)
                    )
                    .clickable { onToggleSourceFilter(source) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = source.name.lowercase(),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) color else TerminalColors.Subtext
                    )
                )
            }
        }
    }
}

// =============================================================================
// SearchResultsList
// =============================================================================

/**
 * Displays search results grouped by source, or an empty/loading state.
 */
@Composable
private fun SearchResultsList(
    results: List<UnifiedMediaItem>,
    isSearching: Boolean,
    query: String,
    monoStyle: TextStyle,
    onAddToQueue: (UnifiedMediaItem) -> Unit,
    onPlayNow: (UnifiedMediaItem) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .heightIn(max = 300.dp)
    ) {
        when {
            isSearching -> {
                // Loading state
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = TerminalColors.Accent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "searching...",
                        style = monoStyle.copy(
                            fontSize = 12.sp,
                            color = TerminalColors.Accent
                        )
                    )
                }
            }
            results.isEmpty() && query.isNotBlank() -> {
                // No results
                Text(
                    text = "  No results for \"$query\"",
                    style = monoStyle.copy(
                        fontSize = 12.sp,
                        color = TerminalColors.Subtext
                    ),
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> {
                // Group results by source
                val grouped = results.groupBy { it.source }

                LazyColumn(
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    grouped.forEach { (source, items) ->
                        // Source group header
                        item(key = "header_${source.name}") {
                            SearchGroupHeader(source = source)
                        }

                        items(
                            items = items,
                            key = { "result_${it.id}" }
                        ) { item ->
                            SearchResultRow(
                                item = item,
                                monoStyle = monoStyle,
                                onAddToQueue = { onAddToQueue(item) },
                                onPlayNow = { onPlayNow(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Header for a group of search results from a single source.
 */
@Composable
private fun SearchGroupHeader(source: MediaSource) {
    val color = source.toAccentColor()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = source.name.lowercase(),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    letterSpacing = 1.sp
                )
            )
        }
        HorizontalDivider(
            color = TerminalColors.Selection.copy(alpha = 0.3f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

/**
 * A single search result row with title, artist, and action buttons.
 */
@Composable
private fun SearchResultRow(
    item: UnifiedMediaItem,
    monoStyle: TextStyle,
    onAddToQueue: () -> Unit,
    onPlayNow: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Source badge
        MediaSourceBadge(source = item.source)

        Spacer(modifier = Modifier.width(10.dp))

        // Title + artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = monoStyle.copy(fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.artist != null) {
                Text(
                    text = item.artist,
                    style = monoStyle.copy(
                        fontSize = 10.sp,
                        color = TerminalColors.Timestamp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Duration
        item.durationMs?.let { ms ->
            Text(
                text = formatDuration(ms),
                style = monoStyle.copy(
                    fontSize = 10.sp,
                    color = TerminalColors.Subtext
                ),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // "Add to queue" button
        IconButton(
            onClick = onAddToQueue,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add to queue",
                tint = TerminalColors.Accent,
                modifier = Modifier.size(16.dp)
            )
        }

        // "Play now" button
        IconButton(
            onClick = onPlayNow,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play now",
                tint = TerminalColors.Success,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
