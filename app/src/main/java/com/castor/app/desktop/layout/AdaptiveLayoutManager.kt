package com.castor.app.desktop.layout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.castor.app.desktop.DisplayMode
import com.castor.app.desktop.DisplayModeViewModel

/**
 * Adaptive layout manager that switches between phone and desktop layouts
 * based on the current [DisplayMode].
 *
 * Uses [AnimatedContent] to provide a smooth crossfade + scale transition
 * when the display mode changes (e.g., plugging in an external display
 * or entering Samsung DeX mode).
 *
 * The phone layout and desktop layout are provided as lambda parameters,
 * allowing the caller to define the exact composables for each mode.
 * This keeps the layout switching logic separate from the actual UI content.
 *
 * Transition animation:
 * - Phone -> Desktop: Fade in + slight scale up (expanding to larger screen)
 * - Desktop -> Phone: Fade out + slight scale down (contracting to phone)
 * - Duration: 400ms for smooth but not sluggish transition
 *
 * @param displayModeViewModel ViewModel providing the current display mode
 * @param phoneContent Composable to render in phone mode (existing HomeScreen)
 * @param desktopContent Composable to render in desktop mode (DesktopHomeScreen)
 * @param modifier Modifier for the animated content container
 */
@Composable
fun AdaptiveLayoutManager(
    displayModeViewModel: DisplayModeViewModel,
    phoneContent: @Composable () -> Unit,
    desktopContent: @Composable (DisplayMode.Desktop) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayMode by displayModeViewModel.displayMode.collectAsState()

    AnimatedContent(
        targetState = displayMode,
        modifier = modifier,
        transitionSpec = {
            modeTransitionSpec(
                initialMode = initialState,
                targetMode = targetState
            )
        },
        contentKey = { mode ->
            when (mode) {
                is DisplayMode.Phone -> "phone"
                is DisplayMode.Desktop -> "desktop"
            }
        },
        label = "DisplayModeTransition"
    ) { mode ->
        when (mode) {
            is DisplayMode.Phone -> phoneContent()
            is DisplayMode.Desktop -> desktopContent(mode)
        }
    }
}

/**
 * Defines the transition animation between phone and desktop modes.
 *
 * Phone -> Desktop: Fade in with slight scale-up (0.95 -> 1.0)
 * Desktop -> Phone: Fade out with slight scale-down (1.0 -> 0.95)
 *
 * The slight scaling provides a visual cue of "expanding" to a larger
 * workspace or "contracting" back to the phone layout.
 */
private fun modeTransitionSpec(
    initialMode: DisplayMode,
    targetMode: DisplayMode
): ContentTransform {
    val transitionDuration = 400

    return when {
        // Phone -> Desktop: expand
        initialMode is DisplayMode.Phone && targetMode is DisplayMode.Desktop -> {
            (fadeIn(animationSpec = tween(transitionDuration)) +
                scaleIn(
                    initialScale = 0.95f,
                    animationSpec = tween(transitionDuration)
                )).togetherWith(
                fadeOut(animationSpec = tween(transitionDuration / 2)) +
                    scaleOut(
                        targetScale = 1.05f,
                        animationSpec = tween(transitionDuration / 2)
                    )
            )
        }

        // Desktop -> Phone: contract
        initialMode is DisplayMode.Desktop && targetMode is DisplayMode.Phone -> {
            (fadeIn(animationSpec = tween(transitionDuration)) +
                scaleIn(
                    initialScale = 1.05f,
                    animationSpec = tween(transitionDuration)
                )).togetherWith(
                fadeOut(animationSpec = tween(transitionDuration / 2)) +
                    scaleOut(
                        targetScale = 0.95f,
                        animationSpec = tween(transitionDuration / 2)
                    )
            )
        }

        // Fallback: simple crossfade
        else -> {
            fadeIn(animationSpec = tween(transitionDuration))
                .togetherWith(fadeOut(animationSpec = tween(transitionDuration)))
        }
    }
}
