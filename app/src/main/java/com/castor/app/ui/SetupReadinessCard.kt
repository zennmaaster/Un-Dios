package com.castor.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.app.onboarding.SetupReadinessState
import com.castor.core.ui.theme.TerminalColors

@Composable
fun SetupReadinessCard(
    readiness: SetupReadinessState,
    onRunCheck: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWalkthrough: () -> Unit,
    modifier: Modifier = Modifier
) {
    val missingCritical = readiness.missingCritical
    val missingOptional = readiness.missingOptional

    val hasIssues = missingCritical.isNotEmpty() || missingOptional.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = 1.dp,
                color = if (hasIssues) TerminalColors.Warning.copy(alpha = 0.5f)
                else TerminalColors.Success.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp)
            )
            .background(TerminalColors.Surface.copy(alpha = 0.65f))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (hasIssues) Icons.Default.WarningAmber else Icons.Default.BuildCircle,
                contentDescription = null,
                tint = if (hasIssues) TerminalColors.Warning else TerminalColors.Success
            )

            Text(
                text = if (hasIssues) " setup-check: action needed" else " setup-check: all good",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hasIssues) TerminalColors.Warning else TerminalColors.Success
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (missingCritical.isEmpty()) {
            Text(
                text = "All critical setup requirements are ready.",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Output,
                    lineHeight = 17.sp
                )
            )
        } else {
            Text(
                text = "Finish these setup items:",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Command,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            missingCritical.take(4).forEach { item ->
                Text(
                    text = "- $item",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TerminalColors.Output,
                        lineHeight = 16.sp
                    )
                )
            }
        }

        if (missingOptional.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Optional:",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Timestamp,
                    fontWeight = FontWeight.Bold
                )
            )
            missingOptional.take(2).forEach { item ->
                Text(
                    text = "- $item",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Timestamp,
                        lineHeight = 15.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SetupActionChip(
                text = "Open settings",
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f)
            )
            SetupActionChip(
                text = "Walkthrough",
                onClick = onOpenWalkthrough,
                modifier = Modifier.weight(1f)
            )
            SetupActionChip(
                text = "Run check",
                onClick = onRunCheck,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SetupActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Background.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Accent,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
