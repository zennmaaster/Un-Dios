package com.castor.app.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private data class GuideStep(
    val title: String,
    val whatItDoes: String,
    val howToUse: String,
    val openFrom: String,
    val icon: ImageVector
)

@Composable
fun FeatureGuideScreen(
    onBack: () -> Unit
) {
    val steps = guideSteps()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
            .statusBarsPadding()
    ) {
        GuideTopBar(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "# first-time-walkthrough",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Command
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Step-by-step guide for new users. Follow these in order.",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Output,
                    lineHeight = 18.sp
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            steps.forEachIndexed { index, step ->
                GuideStepCard(
                    stepNumber = index + 1,
                    step = step
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun FeatureWalkthroughStep(
    onContinue: () -> Unit
) {
    val steps = guideSteps()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "# feature-walkthrough",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Command
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Before you start, here is what each core feature does and where to find it.",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Output,
                lineHeight = 18.sp
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        steps.forEachIndexed { index, step ->
            GuideStepCard(
                stepNumber = index + 1,
                step = step
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(
                containerColor = TerminalColors.Accent,
                contentColor = TerminalColors.Background
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "$ continue-setup",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun GuideTopBar(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalColors.StatusBar)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TerminalColors.Command
            )
        }

        Text(
            text = "walkthrough",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )
    }
}

@Composable
private fun GuideStepCard(
    stepNumber: Int,
    step: GuideStep
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(TerminalColors.Accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber.toString(),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Accent
                    )
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Icon(
                imageVector = step.icon,
                contentDescription = step.title,
                tint = TerminalColors.Prompt,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = step.title,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Command
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        GuideField(label = "What it does", value = step.whatItDoes)
        Spacer(modifier = Modifier.height(6.dp))
        GuideField(label = "How to use", value = step.howToUse)
        Spacer(modifier = Modifier.height(6.dp))
        GuideField(label = "Open from", value = step.openFrom)
    }
}

@Composable
private fun GuideField(
    label: String,
    value: String
) {
    Text(
        text = "$label:",
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = TerminalColors.Timestamp,
            fontWeight = FontWeight.Bold
        )
    )

    Spacer(modifier = Modifier.height(2.dp))

    Text(
        text = value,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = TerminalColors.Output,
            lineHeight = 18.sp
        )
    )
}

private fun guideSteps(): List<GuideStep> {
    return listOf(
        GuideStep(
            title = "Home Dashboard",
            whatItDoes = "Shows your day at a glance: weather, briefings, suggestions, and quick-launch actions.",
            howToUse = "Start here every time. Swipe up for the app drawer and long-press the home area to open settings.",
            openFrom = "Main home screen",
            icon = Icons.Default.Dashboard
        ),
        GuideStep(
            title = "Messages Hub",
            whatItDoes = "Collects notifications and conversations into one inbox for easier follow-up.",
            howToUse = "Open Messages, pick a conversation, and read or reply from one place.",
            openFrom = "Home -> Messages",
            icon = Icons.Default.ChatBubble
        ),
        GuideStep(
            title = "Notification Center",
            whatItDoes = "Gives a cleaner view of important alerts so nothing urgent is missed.",
            howToUse = "Review alerts by priority and clear noise quickly.",
            openFrom = "Home -> Notification Center",
            icon = Icons.Default.Notifications
        ),
        GuideStep(
            title = "Reminders & Calendar",
            whatItDoes = "Tracks tasks and schedules so daily plans stay visible and actionable.",
            howToUse = "Add reminders, then check upcoming items throughout the day.",
            openFrom = "Home -> Reminders",
            icon = Icons.Default.CalendarMonth
        ),
        GuideStep(
            title = "Media & Sync",
            whatItDoes = "Keeps playback and media context in one place across supported sources.",
            howToUse = "Use Media to see what is playing, queue items, and manage sync features.",
            openFrom = "Home -> Media",
            icon = Icons.Default.Album
        ),
        GuideStep(
            title = "AI Command Bar",
            whatItDoes = "Lets you run commands, shortcuts, and assistant actions from one terminal-style input.",
            howToUse = "Type natural requests or quick slash commands to jump directly to features.",
            openFrom = "Home command bar",
            icon = Icons.Default.SmartToy
        ),
        GuideStep(
            title = "Settings & Privacy",
            whatItDoes = "Controls themes, permissions, API keys, and privacy behavior.",
            howToUse = "Open settings to tune your experience and revisit this walkthrough anytime.",
            openFrom = "Home long-press -> Settings",
            icon = Icons.Default.Settings
        )
    )
}
