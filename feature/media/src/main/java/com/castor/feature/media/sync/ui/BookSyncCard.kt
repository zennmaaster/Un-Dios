package com.castor.feature.media.sync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.castor.core.data.db.entity.BookSyncEntity
import com.castor.core.ui.theme.AudibleOrange
import com.castor.core.ui.theme.KindleBlue
import com.castor.core.ui.theme.TerminalColors
import com.castor.feature.media.sync.SyncDelta

// =============================================================================
// BookSyncCard — Compact card for embedding in other screens
// =============================================================================

/**
 * A compact card showing the most recently active synced book with
 * progress bars for both Kindle and Audible, plus quick-switch actions.
 *
 * Designed to be embedded in the MediaScreen or HomeScreen.
 *
 * @param book The synced book to display.
 * @param syncDelta The computed sync delta, or null if not available.
 * @param onOpenKindle Callback to open the Kindle app.
 * @param onOpenAudible Callback to open the Audible app.
 * @param onCardClick Callback when the card itself is tapped (navigate to full sync screen).
 * @param modifier Optional modifier.
 */
@Composable
fun BookSyncCard(
    book: BookSyncEntity,
    syncDelta: SyncDelta?,
    onOpenKindle: () -> Unit,
    onOpenAudible: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val kindleProgress = book.kindleProgress ?: 0f
    val audibleProgress = book.audibleProgress ?: 0f

    val deltaColor = when {
        syncDelta == null -> TerminalColors.Subtext
        syncDelta.isSynced -> TerminalColors.Success
        else -> TerminalColors.Warning
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.6f))
            .clickable(onClick = onCardClick)
            .padding(12.dp)
    ) {
        // Header row: icon + title + sync badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover art / fallback icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(TerminalColors.Selection),
                contentAlignment = Alignment.Center
            ) {
                if (book.coverUrl != null) {
                    AsyncImage(
                        model = book.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.SyncAlt,
                        contentDescription = null,
                        tint = TerminalColors.Accent.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
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
                if (book.author.isNotBlank()) {
                    Text(
                        text = book.author,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TerminalColors.Output
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Sync delta badge
            if (syncDelta != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(deltaColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (syncDelta.isSynced) "synced" else "${syncDelta.deltaPercent}%",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = deltaColor
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Mini progress bars side by side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Kindle
            MiniProgress(
                label = "kindle",
                progress = kindleProgress,
                color = KindleBlue,
                modifier = Modifier.weight(1f)
            )
            // Audible
            MiniProgress(
                label = "audible",
                progress = audibleProgress,
                color = AudibleOrange,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickAction(
                label = "Switch to Kindle",
                color = KindleBlue,
                icon = Icons.AutoMirrored.Filled.MenuBook,
                onClick = onOpenKindle,
                modifier = Modifier.weight(1f)
            )
            QuickAction(
                label = "Switch to Audible",
                color = AudibleOrange,
                icon = Icons.Default.Headphones,
                onClick = onOpenAudible,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// =============================================================================
// MiniProgress — Compact progress indicator
// =============================================================================

@Composable
private fun MiniProgress(
    label: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
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
            Text(
                text = "${(progress * 100).toInt()}%",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = color.copy(alpha = 0.7f)
                )
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp)),
            color = color,
            trackColor = TerminalColors.Selection,
        )
    }
}

// =============================================================================
// QuickAction — Compact action button
// =============================================================================

@Composable
private fun QuickAction(
    label: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(10.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = color
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
