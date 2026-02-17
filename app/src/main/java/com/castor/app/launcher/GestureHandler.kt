package com.castor.app.launcher

import android.annotation.SuppressLint
import android.os.Build
import android.view.StatusBarManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Gesture detection wrapper for the home screen launcher.
 *
 * Wraps the provided [content] composable with pointer input handlers that detect
 * the following gestures, providing Ubuntu/GNOME-like desktop interactions:
 *
 * - **Swipe up** (100dp threshold): Opens the app drawer (Activities overview)
 * - **Swipe down** (100dp threshold, from top 1/4 of screen): Expands the notification shade
 * - **Double tap**: Locks the screen / puts display to sleep
 * - **Long press**: Opens launcher settings
 *
 * The gesture handling uses Compose's [pointerInput] with [detectDragGestures] for
 * swipe detection and [detectTapGestures] for tap/long-press detection. Both gesture
 * detectors run in parallel using separate pointerInput keys.
 *
 * Note: Notification shade expansion uses reflection on StatusBarManager's
 * expandNotificationsPanel() as there is no public API for this. This works on most
 * Android versions but may fail silently on heavily customized OEM ROMs.
 *
 * @param onSwipeUp Called when a vertical swipe-up gesture exceeds the threshold
 * @param onSwipeDown Called when a vertical swipe-down gesture from the top region exceeds threshold
 * @param onDoubleTap Called when the user double-taps the home screen
 * @param onLongPress Called when the user long-presses on the home screen
 * @param content The composable content to wrap with gesture detection
 */
@Composable
fun GestureHandler(
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    // Swipe threshold in pixels (100dp)
    val swipeThresholdPx = with(density) { 100.dp.toPx() }

    // Track whether the notification shade expansion should be used
    val expandNotificationShade: () -> Unit = remember {
        {
            expandNotifications(context)
        }
    }

    Box(
        modifier = Modifier
            // Drag/swipe detection
            .pointerInput(Unit) {
                var totalDragY = 0f
                var dragStartY = 0f
                var isTrackingDrag = false

                detectDragGestures(
                    onDragStart = { offset ->
                        totalDragY = 0f
                        dragStartY = offset.y
                        isTrackingDrag = true
                    },
                    onDragEnd = {
                        if (isTrackingDrag) {
                            val screenHeight = size.height.toFloat()
                            val startedFromTop = dragStartY < screenHeight * 0.25f

                            when {
                                // Swipe up: drag went upward past threshold
                                totalDragY < -swipeThresholdPx -> {
                                    onSwipeUp()
                                }
                                // Swipe down from top quarter: expand notifications
                                totalDragY > swipeThresholdPx && startedFromTop -> {
                                    expandNotificationShade()
                                    onSwipeDown()
                                }
                            }
                        }
                        totalDragY = 0f
                        isTrackingDrag = false
                    },
                    onDragCancel = {
                        totalDragY = 0f
                        isTrackingDrag = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragY += dragAmount.y
                    }
                )
            }
            // Tap and long-press detection
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onLongPress = { onLongPress() }
                )
            }
    ) {
        content()
    }
}

/**
 * Attempts to expand the system notification shade using reflection.
 *
 * On Android S+ (API 31+), tries the StatusBarManager system service.
 * On older versions, falls back to reflection on the "statusbar" service
 * calling the "expandNotificationsPanel" method.
 *
 * This is a best-effort approach: it may fail silently on devices where
 * the OEM has restricted this API or if security policies prevent it.
 */
@SuppressLint("WrongConstant", "PrivateApi")
private fun expandNotifications(context: android.content.Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Try StatusBarManager on Android 12+
            val statusBarManager = context.getSystemService(
                android.content.Context.STATUS_BAR_SERVICE
            )
            if (statusBarManager != null) {
                val method = statusBarManager.javaClass.getMethod("expandNotificationsPanel")
                method.invoke(statusBarManager)
                return
            }
        }

        // Fallback: reflection on the statusbar service (works on most Android versions)
        val service = context.getSystemService("statusbar")
        if (service != null) {
            val clazz = service.javaClass
            val method = clazz.getMethod("expandNotificationsPanel")
            method.invoke(service)
        }
    } catch (_: Exception) {
        // Silently fail — notification shade expansion is a convenience, not critical.
        // On some OEM ROMs or managed devices, this reflection call is blocked.
    }
}
