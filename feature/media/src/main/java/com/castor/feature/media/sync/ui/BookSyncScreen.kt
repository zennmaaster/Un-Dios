package com.castor.feature.media.sync.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.castor.core.data.db.entity.BookSyncEntity
import com.castor.core.ui.theme.AudibleOrange
import com.castor.core.ui.theme.KindleBlue
import com.castor.core.ui.theme.TerminalColors

// =============================================================================
// BookSyncScreen — Terminal-style Kindle/Audible sync view
// =============================================================================

/**
 * The book sync screen displays all tracked books and their synchronisation
 * state between Kindle (reading) and Audible (listening).
 *
 * Terminal aesthetic: monospace font, dark background, Catppuccin colors.
 *
 * @param onBack Navigation callback to return to the previous screen.
 * @param viewModel The [BookSyncViewModel] providing state and actions.
 */
@Composable
fun BookSyncScreen(
    onBack: () -> Unit,
    viewModel: BookSyncViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
    ) {
        // Title bar
        SyncTitleBar(onBack = onBack)

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Terminal header
            Text(
                text = "$ cat /var/un-dios/library/sync",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Prompt
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tracking ${state.totalBooks} book(s) across Kindle and Audible",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Subtext
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Accessibility service banner (if not enabled)
            if (!state.accessibilityEnabled) {
                AccessibilityBanner(
                    onEnable = { viewModel.openAccessibilitySettings(context) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Matched books section
            if (state.matchedBooks.isNotEmpty()) {
                SectionHeader(title = "synced", count = state.matchedBooks.size)
                Spacer(modifier = Modifier.height(8.dp))

                state.matchedBooks.forEach { model ->
                    BookSyncItemCard(
                        model = model,
                        onOpenKindle = { viewModel.openKindle(context) },
                        onOpenAudible = { viewModel.openAudible(context) },
                        onRemove = { viewModel.removeBook(model.book.id) },
                        kindleResumeDesc = viewModel.getKindleResumeDescription(model.book),
                        audibleResumeDesc = viewModel.getAudibleResumeDescription(model.book)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Unmatched books section
            if (state.unmatchedBooks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(title = "unmatched", count = state.unmatchedBooks.size)
                Spacer(modifier = Modifier.height(8.dp))

                state.unmatchedBooks.forEach { book ->
                    UnmatchedBookCard(book = book)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Empty state
            if (state.matchedBooks.isEmpty() && state.unmatchedBooks.isEmpty() && !state.isLoading) {
                EmptyState()
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// =============================================================================
// SyncTitleBar
// =============================================================================

@Composable
private fun SyncTitleBar(onBack: () -> Unit) {
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
                text = "un-dios ~ library sync",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TerminalColors.Timestamp
                )
            )
        }

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
// Accessibility Banner
// =============================================================================

@Composable
private fun AccessibilityBanner(onEnable: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Warning.copy(alpha = 0.10f))
            .clickable(onClick = onEnable)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Accessibility,
                contentDescription = null,
                tint = TerminalColors.Warning,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Accessibility service required",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Warning
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Enable \"Un-Dios Media Sync\" in Settings > Accessibility for accurate Kindle page tracking.",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Subtext
                    )
                )
            }
        }
    }
}

// =============================================================================
// Section Header
// =============================================================================

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.SyncAlt,
            contentDescription = null,
            tint = TerminalColors.Accent,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "($count)",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Subtext
            )
        )
    }

    HorizontalDivider(
        color = TerminalColors.Selection.copy(alpha = 0.3f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(top = 4.dp)
    )
}

// =============================================================================
// BookSyncItemCard — a matched book with both Kindle and Audible data
// =============================================================================

@Composable
private fun BookSyncItemCard(
    model: BookSyncUiModel,
    onOpenKindle: () -> Unit,
    onOpenAudible: () -> Unit,
    onRemove: () -> Unit,
    kindleResumeDesc: String?,
    audibleResumeDesc: String?
) {
    val book = model.book
    val syncDelta = model.syncDelta

    val deltaColor = when {
        syncDelta == null -> TerminalColors.Subtext
        syncDelta.isSynced -> TerminalColors.Success
        else -> TerminalColors.Warning
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.6f))
            .padding(12.dp)
    ) {
        // Header: cover art + title + author + delete
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Cover art
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(TerminalColors.Selection),
                contentAlignment = Alignment.Center
            ) {
                if (book.coverUrl != null) {
                    AsyncImage(
                        model = book.coverUrl,
                        contentDescription = "Cover art for ${book.title}",
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = TerminalColors.Subtext.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Command
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.author.isNotBlank()) {
                    Text(
                        text = book.author,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TerminalColors.Output
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove book",
                    tint = TerminalColors.Subtext.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Kindle progress bar
        ProgressRow(
            label = "kindle",
            progress = book.kindleProgress ?: 0f,
            percentText = "${model.kindleProgressPercent}%",
            color = KindleBlue,
            lastSync = book.kindleLastSync,
            chapter = book.kindleChapter
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Audible progress bar
        ProgressRow(
            label = "audible",
            progress = book.audibleProgress ?: 0f,
            percentText = "${model.audibleProgressPercent}%",
            color = AudibleOrange,
            lastSync = book.audibleLastSync,
            chapter = book.audibleChapter
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Sync delta indicator
        if (syncDelta != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(deltaColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = syncDelta.description,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = deltaColor
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Continue on Kindle
            ActionChip(
                label = "Continue on Kindle",
                subtitle = kindleResumeDesc,
                color = KindleBlue,
                icon = Icons.AutoMirrored.Filled.MenuBook,
                onClick = onOpenKindle,
                modifier = Modifier.weight(1f)
            )

            // Continue on Audible
            ActionChip(
                label = "Continue on Audible",
                subtitle = audibleResumeDesc,
                color = AudibleOrange,
                icon = Icons.Default.Headphones,
                onClick = onOpenAudible,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// =============================================================================
// ProgressRow — Kindle or Audible progress bar
// =============================================================================

@Composable
private fun ProgressRow(
    label: String,
    progress: Float,
    percentText: String,
    color: Color,
    lastSync: Long?,
    chapter: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            )
            Text(
                text = percentText,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = color.copy(alpha = 0.8f)
                )
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = TerminalColors.Selection,
        )

        // Chapter and sync time
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = chapter ?: "",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TerminalColors.Subtext
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (lastSync != null) {
                Text(
                    text = formatRelativeTime(lastSync),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = TerminalColors.Subtext
                    )
                )
            }
        }
    }
}

// =============================================================================
// ActionChip — "Continue on Kindle/Audible" button
// =============================================================================

@Composable
private fun ActionChip(
    label: String,
    subtitle: String?,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = color.copy(alpha = 0.7f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// =============================================================================
// UnmatchedBookCard — books only on one platform
// =============================================================================

@Composable
private fun UnmatchedBookCard(book: BookSyncEntity) {
    val isKindle = book.kindleProgress != null
    val color = if (isKindle) KindleBlue else AudibleOrange
    val platformLabel = if (isKindle) "kindle" else "audible"
    val progress = if (isKindle) book.kindleProgress ?: 0f else book.audibleProgress ?: 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.4f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Platform indicator
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isKindle) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.Headphones,
                contentDescription = platformLabel,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Command
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$platformLabel only",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = color
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Subtext
                    )
                )
            }
        }

        // Manual match icon (placeholder for future matching UI)
        Icon(
            imageVector = Icons.Default.Link,
            contentDescription = "Match manually",
            tint = TerminalColors.Subtext.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )
    }
}

// =============================================================================
// Empty State
// =============================================================================

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.SyncAlt,
            contentDescription = null,
            tint = TerminalColors.Subtext.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp)
        )

        Text(
            text = "$ sync --status",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Prompt
            )
        )

        Text(
            text = "No books detected yet.",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Subtext
            )
        )

        Text(
            text = "Open a book in Kindle or start listening in Audible.\nUn-Dios will detect progress automatically.",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Subtext.copy(alpha = 0.7f)
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

// =============================================================================
// Utility
// =============================================================================

/**
 * Format a timestamp into a relative time string like "2m ago", "3h ago".
 */
private fun formatRelativeTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val deltaMs = now - timestampMs
    val deltaSeconds = deltaMs / 1000
    val deltaMinutes = deltaSeconds / 60
    val deltaHours = deltaMinutes / 60
    val deltaDays = deltaHours / 24

    return when {
        deltaSeconds < 60 -> "just now"
        deltaMinutes < 60 -> "${deltaMinutes}m ago"
        deltaHours < 24 -> "${deltaHours}h ago"
        deltaDays < 7 -> "${deltaDays}d ago"
        else -> "${deltaDays / 7}w ago"
    }
}
