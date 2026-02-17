package com.castor.feature.recommendations.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.data.db.entity.RecommendationEntity
import com.castor.core.ui.theme.ChromeYellow
import com.castor.core.ui.theme.NetflixRed
import com.castor.core.ui.theme.PrimeBlue
import com.castor.core.ui.theme.TerminalColors
import com.castor.core.ui.theme.YouTubeRed

/**
 * A single recommendation card with terminal aesthetic.
 *
 * Features:
 * - Genre tag with coloured badge
 * - Match score as a mini progress indicator
 * - "Watch on" row with platform icons
 * - Swipe-to-dismiss support
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecommendationCard(
    recommendation: RecommendationEntity,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            onDismiss(recommendation.id)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                    else -> TerminalColors.Error.copy(alpha = 0.3f)
                },
                label = "dismiss_bg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = TerminalColors.Error
                )
            }
        },
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = TerminalColors.Surface
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Row 1: Title + dismiss button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = recommendation.title,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Command
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onDismiss(recommendation.id) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = TerminalColors.Timestamp,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Row 2: Genre badge + match score
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GenreBadge(genre = recommendation.genre)
                    MatchScoreIndicator(score = recommendation.estimatedMatchScore)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row 3: Reason
                if (recommendation.reason.isNotBlank()) {
                    Text(
                        text = recommendation.reason,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TerminalColors.Output
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Row 4: "Watch on" platform badges
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$ watch --on ",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TerminalColors.Prompt
                        )
                    )
                    PlatformBadge(source = recommendation.source)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Sub-components
// -------------------------------------------------------------------------------------

@Composable
private fun GenreBadge(genre: String) {
    val color = genreColor(genre)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = genre.lowercase(),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = color
            )
        )
    }
}

@Composable
private fun MatchScoreIndicator(score: Float) {
    val percent = (score * 100).toInt()
    val color = when {
        percent >= 80 -> TerminalColors.Success
        percent >= 60 -> TerminalColors.Warning
        else -> TerminalColors.Info
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LinearProgressIndicator(
            progress = { score },
            modifier = Modifier
                .width(48.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = TerminalColors.Selection,
        )
        Text(
            text = "${percent}%",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
    }
}

@Composable
private fun PlatformBadge(source: String) {
    val (icon, color, label) = platformInfo(source)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = color
            )
        )
    }
}

// -------------------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------------------

@Composable
@ReadOnlyComposable
private fun genreColor(genre: String): Color {
    val lower = genre.lowercase()
    return when {
        "drama" in lower -> TerminalColors.Accent
        "comedy" in lower -> TerminalColors.Warning
        "thriller" in lower -> TerminalColors.Error
        "sci-fi" in lower || "science" in lower -> TerminalColors.Info
        "documentary" in lower -> TerminalColors.Success
        "action" in lower -> NetflixRed
        "horror" in lower -> TerminalColors.Error
        "anime" in lower -> TerminalColors.Accent
        "romance" in lower -> Color(0xFFF5C2E7) // Catppuccin Pink
        "fantasy" in lower -> TerminalColors.Accent
        else -> TerminalColors.Info
    }
}

private data class PlatformDisplayInfo(
    val icon: ImageVector,
    val color: Color,
    val label: String
)

@Composable
@ReadOnlyComposable
private fun platformInfo(source: String): PlatformDisplayInfo {
    val lower = source.lowercase()
    return when {
        "netflix" in lower -> PlatformDisplayInfo(Icons.Default.Tv, NetflixRed, "Netflix")
        "prime" in lower -> PlatformDisplayInfo(Icons.Default.Movie, PrimeBlue, "Prime")
        "youtube" in lower -> PlatformDisplayInfo(Icons.Default.PlayArrow, YouTubeRed, "YouTube")
        "chrome" in lower -> PlatformDisplayInfo(Icons.Default.Web, ChromeYellow, "Chrome")
        else -> PlatformDisplayInfo(Icons.Default.OndemandVideo, TerminalColors.Info, source)
    }
}
