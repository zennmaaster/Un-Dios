package com.castor.feature.commandbar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.theme.TerminalColors
import kotlinx.coroutines.delay

/**
 * VoiceInputButton is a microphone button for the command bar.
 *
 * When tapped, it starts voice recognition. While listening, it shows
 * a pulsing animation and displays the partial transcript. When a
 * result is received, it calls the onTranscript callback.
 *
 * @param isListening Whether voice recognition is currently active
 * @param partialTranscript The partial transcript being recognized
 * @param error Any error message from voice recognition
 * @param onStartListening Callback when the user taps the mic button
 * @param onStopListening Callback when the user taps to stop listening
 * @param onClearError Callback to clear the error state
 * @param modifier Modifier for the root composable
 */
@Composable
fun VoiceInputButton(
    isListening: Boolean,
    partialTranscript: String,
    error: String?,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pulsing animation when listening
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Auto-clear error after 3 seconds
    LaunchedEffect(error) {
        if (error != null) {
            delay(3000)
            onClearError()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable {
                    if (isListening) {
                        onStopListening()
                    } else {
                        onStartListening()
                    }
                }
        ) {
            // Pulsing background when listening
            if (isListening) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(TerminalColors.Accent.copy(alpha = 0.2f))
                )
            }

            // Static background
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isListening) {
                            TerminalColors.Accent.copy(alpha = 0.3f)
                        } else {
                            TerminalColors.Surface
                        }
                    )
            )

            // Icon
            Icon(
                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop listening" else "Start voice input",
                tint = if (isListening) TerminalColors.Accent else TerminalColors.Command,
                modifier = Modifier.size(20.dp)
            )
        }

        // Partial transcript display
        if (isListening && partialTranscript.isNotEmpty()) {
            Text(
                text = partialTranscript,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Info
                ),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Error display
        if (error != null && !isListening) {
            Text(
                text = error,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TerminalColors.Error
                ),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
