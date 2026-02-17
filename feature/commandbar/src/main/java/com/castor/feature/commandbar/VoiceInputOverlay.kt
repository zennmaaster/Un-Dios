package com.castor.feature.commandbar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.theme.TerminalColors
import kotlin.math.sin

/**
 * VoiceInputOverlay is a full-screen overlay that appears when voice input is active.
 *
 * Features:
 * - Semi-transparent background with terminal aesthetic
 * - Terminal prompt: `$ voice --listen`
 * - Large pulsing microphone icon with concentric circles
 * - Real-time partial transcript display
 * - Simple waveform visualization
 * - "Tap anywhere to cancel" hint
 *
 * @param partialTranscript The partial transcript being recognized in real-time
 * @param onCancel Callback when the user taps to cancel voice input
 * @param modifier Modifier for the root composable
 */
@Composable
fun VoiceInputOverlay(
    partialTranscript: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pulsing animation for the microphone
    val infiniteTransition = rememberInfiniteTransition(label = "voiceOverlay")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalColors.Overlay.copy(alpha = 0.95f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onCancel()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Terminal prompt
            Text(
                text = "$ voice --listen",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Prompt
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Concentric pulsing circles + microphone icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(200.dp)
            ) {
                // Outer circle
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(TerminalColors.Accent.copy(alpha = 0.1f))
                )

                // Middle circle
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(1f + (pulseScale - 1f) * 0.7f)
                        .clip(CircleShape)
                        .background(TerminalColors.Accent.copy(alpha = 0.15f))
                )

                // Inner circle
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(1f + (pulseScale - 1f) * 0.4f)
                        .clip(CircleShape)
                        .background(TerminalColors.Accent.copy(alpha = 0.25f))
                )

                // Microphone icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(TerminalColors.Accent.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Listening",
                        tint = TerminalColors.Accent,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Partial transcript display
            Box(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                if (partialTranscript.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "> ",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = TerminalColors.Prompt
                            )
                        )
                        Text(
                            text = partialTranscript,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = TerminalColors.Command
                            )
                        )
                    }
                } else {
                    Text(
                        text = "Listening...",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            color = TerminalColors.Timestamp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Simple waveform visualization
            WaveformVisualization(phase = wavePhase)

            Spacer(modifier = Modifier.height(40.dp))

            // Cancel hint
            Text(
                text = "Tap anywhere to cancel",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Subtext
                )
            )
        }
    }
}

/**
 * Simple waveform visualization using animated bars.
 */
@Composable
private fun WaveformVisualization(
    phase: Float,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        // Generate 15 bars with animated heights
        repeat(15) { index ->
            val offset = (index - 7) * 24f
            val phaseRadians = Math.toRadians((phase + offset).toDouble())
            val heightFactor = (sin(phaseRadians) * 0.5 + 0.5).toFloat()
            val barHeight = 4.dp + (20.dp * heightFactor)

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .background(
                        color = TerminalColors.Accent.copy(alpha = 0.6f + heightFactor * 0.4f)
                    )
            )
        }
    }
}
