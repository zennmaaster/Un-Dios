package com.castor.app.lockscreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.theme.TerminalColors
import kotlin.math.roundToInt

// =============================================================================
// Constants
// =============================================================================

/** Vertical drag distance (in dp-equivalent pixels) to trigger swipe-to-unlock. */
private const val SWIPE_UNLOCK_THRESHOLD = -300f

// =============================================================================
// Public composable entry point
// =============================================================================

/**
 * Full-screen terminal-styled lock screen overlay.
 *
 * Renders on top of all other home screen layers when [isLocked] is true.
 * Features:
 * 1. A Linux boot-sequence animation that plays when the device wakes.
 * 2. A large monospace digital clock with the date beneath.
 * 3. A weather summary line styled as a `curl wttr.in` command.
 * 4. A terminal-formatted date display (`$ date -> Mon Feb 17 2026`).
 * 5. A battery status line styled as a `cat` command reading sysfs.
 * 6. Real notification previews from Room with tappable cards and count badge.
 * 7. A biometric authentication prompt (`$ authenticate --biometric`).
 * 8. Swipe-up-to-unlock with a pulsing arrow animation (InfiniteTransition).
 * 9. A brief "[OK] Authentication successful" message on unlock.
 *
 * @param isLocked Whether the lock screen is currently visible.
 * @param showBootSequence Whether the boot animation is currently playing.
 * @param bootLines The list of boot lines revealed so far.
 * @param showSuccessMessage Whether the post-auth success message is showing.
 * @param clockTime The current time string (HH:mm).
 * @param clockDate The current date string (EEE, MMM d, yyyy).
 * @param currentDate Terminal-formatted date string (e.g., "Mon Feb 17 2026").
 * @param weatherSummary One-line weather summary (e.g., "52F, Partly cloudy") or null.
 * @param batteryPercent Device battery level 0-100.
 * @param isCharging Whether the device is currently charging.
 * @param notifications Recent unread notifications from Room.
 * @param notificationCount Total unread notification count.
 * @param showNotifications Whether notification previews are enabled by the user.
 * @param canUseBiometric Whether biometric authentication is available.
 * @param onBiometricRequested Called when the user taps the fingerprint button.
 * @param onSwipeUnlock Called when the user completes a swipe-to-unlock gesture.
 * @param onNotificationTap Called when a notification card is tapped (unlock + navigate).
 */
@Composable
fun LockScreenOverlay(
    isLocked: Boolean,
    showBootSequence: Boolean,
    bootLines: List<String>,
    showSuccessMessage: Boolean,
    clockTime: String,
    clockDate: String,
    currentDate: String,
    weatherSummary: String?,
    batteryPercent: Int,
    isCharging: Boolean,
    notifications: List<LockScreenNotification>,
    notificationCount: Int,
    showNotifications: Boolean,
    canUseBiometric: Boolean,
    onBiometricRequested: () -> Unit,
    onSwipeUnlock: () -> Unit,
    onNotificationTap: () -> Unit
) {
    AnimatedVisibility(
        visible = isLocked,
        enter = fadeIn(animationSpec = tween(durationMillis = 400)),
        exit = fadeOut(animationSpec = tween(durationMillis = 500))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TerminalColors.Background)
        ) {
            when {
                // Phase 1: Boot sequence animation
                showBootSequence -> {
                    BootSequenceScreen(bootLines = bootLines)
                }
                // Phase 2: Success message (briefly after auth)
                showSuccessMessage -> {
                    AuthSuccessScreen()
                }
                // Phase 3: Main lock screen
                else -> {
                    MainLockScreen(
                        clockTime = clockTime,
                        clockDate = clockDate,
                        currentDate = currentDate,
                        weatherSummary = weatherSummary,
                        batteryPercent = batteryPercent,
                        isCharging = isCharging,
                        notifications = notifications,
                        notificationCount = notificationCount,
                        showNotifications = showNotifications,
                        canUseBiometric = canUseBiometric,
                        onBiometricRequested = onBiometricRequested,
                        onSwipeUnlock = onSwipeUnlock,
                        onNotificationTap = onNotificationTap
                    )
                }
            }
        }
    }
}

// =============================================================================
// Phase 1: Boot sequence
// =============================================================================

/**
 * Simulated Linux kernel boot log displayed during wake-up.
 *
 * Each line in [bootLines] fades in sequentially, creating the illusion of
 * a real system initialisation. Lines use color coding: `[OK]` markers are
 * shown in [TerminalColors.Success], while the rest of the text is in
 * [TerminalColors.Output].
 */
@Composable
private fun BootSequenceScreen(bootLines: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // Header
        Text(
            text = "Un-Dios Boot Sequence",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "kernel: un-dios 0.1.0-castor (aarch64)",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Timestamp
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Boot lines with staggered fade-in
        bootLines.forEachIndexed { index, line ->
            BootLine(line = line, index = index)
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

/**
 * A single boot-log line with a fade-in animation.
 * The `[OK]` prefix is highlighted in green; the rest in neutral output color.
 */
@Composable
private fun BootLine(line: String, index: Int) {
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(index) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300, easing = LinearEasing)
        )
    }

    Row(modifier = Modifier.alpha(alpha.value)) {
        if (line.startsWith("[OK]")) {
            Text(
                text = "[OK]",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Success
                )
            )
            Text(
                text = line.removePrefix("[OK]"),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Output
                )
            )
        } else if (line.startsWith("[WARN]")) {
            Text(
                text = "[WARN]",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Warning
                )
            )
            Text(
                text = line.removePrefix("[WARN]"),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Output
                )
            )
        } else if (line.startsWith("[FAIL]")) {
            Text(
                text = "[FAIL]",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Error
                )
            )
            Text(
                text = line.removePrefix("[FAIL]"),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Output
                )
            )
        } else {
            Text(
                text = line,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Output
                )
            )
        }
    }
}

// =============================================================================
// Phase 2: Authentication success
// =============================================================================

/**
 * Brief success screen shown after authentication before the lock screen
 * fades out. Displays a terminal-style confirmation message.
 */
@Composable
private fun AuthSuccessScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = TerminalColors.Success,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row {
                Text(
                    text = "[OK]",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Success
                    )
                )
                Text(
                    text = " Authentication successful.",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = TerminalColors.Output
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Welcome back.",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TerminalColors.Accent
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Blinking cursor effect
            BlinkingCursor()
        }
    }
}

/**
 * A simple blinking block cursor (underscore character) to reinforce the
 * terminal aesthetic during the success screen.
 */
@Composable
private fun BlinkingCursor() {
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        while (true) {
            alpha.animateTo(0f, animationSpec = tween(500))
            alpha.animateTo(1f, animationSpec = tween(500))
        }
    }

    Text(
        text = "_",
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TerminalColors.Cursor
        ),
        modifier = Modifier.alpha(alpha.value)
    )
}

// =============================================================================
// Phase 3: Main lock screen
// =============================================================================

/**
 * The primary lock screen UI shown after the boot sequence completes.
 *
 * Layout (top to bottom):
 * 1. Terminal header with hostname
 * 2. Large monospace clock (HH:MM)
 * 3. Terminal date display (`$ date -> Mon Feb 17 2026`)
 * 4. Weather summary line (`$ curl wttr.in -> 52F, Partly cloudy`)
 * 5. Battery status as a `cat` sysfs command with icon
 * 6. Notification count badge
 * 7. Real notification previews (up to 5 tappable cards)
 * 8. Biometric prompt / fingerprint button
 * 9. Pulsing swipe-up hint with arrow animation
 */
@Composable
private fun MainLockScreen(
    clockTime: String,
    clockDate: String,
    currentDate: String,
    weatherSummary: String?,
    batteryPercent: Int,
    isCharging: Boolean,
    notifications: List<LockScreenNotification>,
    notificationCount: Int,
    showNotifications: Boolean,
    canUseBiometric: Boolean,
    onBiometricRequested: () -> Unit,
    onSwipeUnlock: () -> Unit,
    onNotificationTap: () -> Unit
) {
    // Swipe-up tracking
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffset < SWIPE_UNLOCK_THRESHOLD) {
                            onSwipeUnlock()
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = {
                        dragOffset = 0f
                    },
                    onVerticalDrag = { _, dragAmount ->
                        dragOffset += dragAmount
                    }
                )
            }
            .offset { IntOffset(0, (dragOffset * 0.3f).roundToInt().coerceAtMost(0)) }
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // ---- Terminal header ----
        TerminalHeader()

        Spacer(modifier = Modifier.height(48.dp))

        // ---- Clock ----
        ClockDisplay(time = clockTime, date = clockDate)

        Spacer(modifier = Modifier.height(8.dp))

        // ---- Terminal date display ----
        if (currentDate.isNotBlank()) {
            TerminalDateDisplay(date = currentDate)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ---- Weather summary ----
        if (weatherSummary != null) {
            WeatherSummaryLine(summary = weatherSummary)
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ---- Battery status ----
        BatteryStatusLine(percent = batteryPercent, isCharging = isCharging)

        Spacer(modifier = Modifier.height(16.dp))

        // ---- Notification count badge ----
        NotificationCountBadge(count = notificationCount)

        Spacer(modifier = Modifier.height(8.dp))

        // ---- Notification preview ----
        if (showNotifications && notifications.isNotEmpty()) {
            LockScreenNotificationSection(
                notifications = notifications,
                totalCount = notificationCount,
                onNotificationTap = onNotificationTap
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        // ---- Biometric prompt ----
        BiometricPromptSection(
            canUseBiometric = canUseBiometric,
            onBiometricRequested = onBiometricRequested
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ---- Swipe hint with pulsing arrow ----
        SwipeHint(canUseBiometric = canUseBiometric)

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// =============================================================================
// Sub-components
// =============================================================================

/**
 * Terminal header showing the hostname and login context.
 */
@Composable
private fun TerminalHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(TerminalColors.Success)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "un-dios@localhost",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Prompt
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "$ /usr/bin/lockscreen --display",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

/**
 * Large monospace clock with the date displayed below it.
 * The time is rendered in a large monospace font, center-aligned, with the
 * date in a smaller dim font beneath.
 */
@Composable
private fun ClockDisplay(time: String, date: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = time,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Command,
                letterSpacing = 4.sp
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = date,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

/**
 * Terminal-style date display formatted as a `date` command output.
 * Example: `$ date -> Mon Feb 17 2026`
 */
@Composable
private fun TerminalDateDisplay(date: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "$ date ",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Timestamp
            )
        )
        Text(
            text = "-> ",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Prompt
            )
        )
        Text(
            text = date,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Output
            )
        )
    }
}

/**
 * Weather summary line styled as a `curl wttr.in` command.
 * Example: `$ curl wttr.in -> 52F, Partly cloudy`
 */
@Composable
private fun WeatherSummaryLine(summary: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "$ curl wttr.in",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Timestamp
                )
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row {
                Text(
                    text = "-> ",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalColors.Prompt
                    )
                )
                Text(
                    text = summary,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Info
                    )
                )
            }
        }
    }
}

/**
 * Battery status rendered as a terminal command reading from sysfs.
 * Example: `$ cat /sys/class/power_supply/battery/capacity -> 85% [charging]`
 *
 * Includes a battery icon that changes color based on charge level:
 * - Red for <= 15%
 * - Yellow/warning for <= 30%
 * - Green for > 30%
 */
@Composable
private fun BatteryStatusLine(percent: Int, isCharging: Boolean) {
    val chargingLabel = if (isCharging) " [charging]" else ""
    val batteryIcon = if (isCharging) Icons.Default.BatteryChargingFull
        else Icons.Default.BatteryFull
    val batteryColor = when {
        percent <= 15 -> TerminalColors.Error
        percent <= 30 -> TerminalColors.Warning
        else -> TerminalColors.Success
    }

    // Visual battery bar: [========  ] style
    val filledBlocks = (percent / 10).coerceIn(0, 10)
    val emptyBlocks = 10 - filledBlocks
    val batteryBar = "[" + "=".repeat(filledBlocks) + " ".repeat(emptyBlocks) + "]"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = batteryIcon,
            contentDescription = "Battery",
            tint = batteryColor,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = "$ cat /sys/class/power_supply/battery/capacity",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Timestamp
                )
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row {
                Text(
                    text = "-> ",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalColors.Prompt
                    )
                )
                Text(
                    text = "$percent%$chargingLabel ",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = batteryColor
                    )
                )
                Text(
                    text = batteryBar,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = batteryColor.copy(alpha = 0.7f)
                    )
                )
            }
        }
    }
}

/**
 * Biometric authentication prompt section.
 *
 * When biometric is available, shows a terminal command prompt
 * `$ authenticate --biometric` with a tappable fingerprint icon.
 * When biometric is unavailable, shows a fallback swipe prompt.
 */
@Composable
private fun BiometricPromptSection(
    canUseBiometric: Boolean,
    onBiometricRequested: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (canUseBiometric) {
            Text(
                text = "$ authenticate --biometric",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Prompt
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            IconButton(
                onClick = onBiometricRequested,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = TerminalColors.Accent.copy(alpha = 0.15f),
                    contentColor = TerminalColors.Accent
                ),
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Authenticate with biometrics",
                    modifier = Modifier.size(36.dp)
                )
            }
        } else {
            Text(
                text = "$ authenticate --swipe",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Prompt
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "# biometric unavailable, swipe up to unlock",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Subtext
                )
            )
        }
    }
}

/**
 * Swipe-up hint at the bottom of the lock screen with a pulsing arrow animation.
 *
 * Uses [rememberInfiniteTransition] to create a smooth, continuous vertical
 * bounce on the arrow icon and a synchronized alpha pulse on the text. This
 * creates a subtle "breathing" effect that draws the user's attention to the
 * swipe-up gesture without being distracting.
 */
@Composable
private fun SwipeHint(canUseBiometric: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "swipe_hint")

    // Pulsing alpha: 0.3 -> 0.9 -> 0.3
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Vertical bounce offset for the arrow: 0dp -> -6dp -> 0dp
    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrow_bounce"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(pulseAlpha)
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = "Swipe up",
            tint = TerminalColors.Subtext,
            modifier = Modifier
                .size(24.dp)
                .offset { IntOffset(0, arrowOffset.roundToInt()) }
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = if (canUseBiometric) "^ swipe to unlock" else "^ swipe up to unlock",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Subtext
            )
        )
    }
}
