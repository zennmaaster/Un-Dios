package com.castor.app.desktop.integration

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.theme.TerminalColors

/**
 * A dropdown-style popup panel for switching between app profiles.
 *
 * Displays all available profiles in a terminal-styled card. Each profile
 * row shows an icon, the profile name, the number of apps in the profile,
 * and a green dot indicator for the currently active profile.
 *
 * Clicking a profile row switches to that profile. A "$ new-profile" button
 * at the bottom allows creating new custom profiles.
 *
 * The panel slides in from the top with a fade animation when [isVisible]
 * becomes true, and slides out when dismissed.
 *
 * @param isVisible Whether the profile switcher panel is currently shown
 * @param onDismiss Callback to close the panel
 * @param profileManager The [ProfileManager] singleton to observe and control profiles
 * @param onCreateProfile Callback to initiate profile creation flow
 * @param modifier Modifier for the outer container
 */
@Composable
fun ProfileSwitcher(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    profileManager: ProfileManager,
    onCreateProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val profiles by profileManager.profiles.collectAsState()
    val activeProfile by profileManager.activeProfile.collectAsState()

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(200)) +
            slideInVertically(
                animationSpec = tween(250),
                initialOffsetY = { -it / 4 }
            ),
        exit = fadeOut(animationSpec = tween(150)) +
            slideOutVertically(
                animationSpec = tween(200),
                targetOffsetY = { -it / 4 }
            )
    ) {
        // Dismiss backdrop
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(TerminalColors.Background.copy(alpha = 0.5f))
                .clickable { onDismiss() }
        ) {
            // Profile switcher card
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .width(320.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TerminalColors.Surface)
                    .clickable(enabled = false) {} // Prevent click-through
                    .padding(16.dp)
            ) {
                // Header
                Text(
                    text = "# profiles",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Accent
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "active: ${activeProfile.name.lowercase()}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TerminalColors.Timestamp
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Separator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TerminalColors.Selection)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Profile list
                profiles.forEach { profile ->
                    ProfileRow(
                        profile = profile,
                        isActive = profile.id == activeProfile.id,
                        onClick = {
                            profileManager.switchProfile(profile.id)
                            onDismiss()
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Separator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TerminalColors.Selection)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // New profile button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onCreateProfile()
                            onDismiss()
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create new profile",
                        tint = TerminalColors.Accent,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "$ new-profile",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TerminalColors.Accent
                        )
                    )
                }
            }
        }
    }
}

/**
 * A single row in the profile switcher showing profile info and active state.
 *
 * Displays the profile icon, name, app count, and a green dot indicator
 * for the currently active profile. The row is clickable to switch to
 * that profile.
 *
 * @param profile The profile data to display
 * @param isActive Whether this profile is currently the active one
 * @param onClick Called when the row is tapped
 */
@Composable
private fun ProfileRow(
    profile: AppProfile,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val rowBackground = if (isActive) {
        TerminalColors.Selection
    } else {
        TerminalColors.Surface
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(rowBackground)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile icon
            Icon(
                imageVector = resolveProfileIcon(profile.icon),
                contentDescription = profile.name,
                tint = if (isActive) TerminalColors.Accent else TerminalColors.Command,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Profile name and app count
            Column {
                Text(
                    text = profile.name.lowercase(),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) TerminalColors.Command else TerminalColors.Output
                    )
                )

                Text(
                    text = "${profile.apps.size} apps",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Timestamp
                    )
                )
            }
        }

        // Active indicator dot
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(TerminalColors.Success)
            )
        }
    }
}

/**
 * Resolves a profile icon name string to a Material [ImageVector].
 *
 * Maps well-known icon name strings (from [AppProfile.icon]) to their
 * corresponding Material Icons. Falls back to [Icons.Default.Person]
 * for unrecognized icon names.
 *
 * @param iconName The icon name string from the profile data
 * @return The corresponding Material icon vector
 */
private fun resolveProfileIcon(iconName: String): ImageVector {
    return when (iconName.lowercase()) {
        "work" -> Icons.Default.Work
        "person", "personal" -> Icons.Default.Person
        "games", "gaming" -> Icons.Default.Games
        "school", "education" -> Icons.Default.School
        else -> Icons.Default.Person
    }
}
