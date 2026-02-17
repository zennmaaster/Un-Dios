package com.castor.feature.commandbar

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.castor.core.common.model.PrivacyTier
import com.castor.core.ui.theme.TerminalColors

// =============================================================================
// Style constants
// =============================================================================

private val DialogMonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    color = Color(0xFFCDD6F4) // CatppuccinMocha command color
)

// =============================================================================
// Main composable
// =============================================================================

/**
 * Consent dialog that appears when a user request needs cloud processing.
 *
 * Shows the original and redacted versions of the request side-by-side so the
 * user can see exactly what data will be sent. Offers three actions:
 *  - "Allow once" — process this single request via cloud
 *  - "Always allow" — skip consent for this type of request in the future
 *  - "Keep local" — deny cloud processing, attempt local-only
 *
 * Styled with the terminal aesthetic (Catppuccin Mocha, monospace, dark).
 *
 * @param request The original user request text
 * @param redactedRequest The redacted version (PII stripped) that would be sent
 * @param tier The privacy tier that triggered this dialog
 * @param onApprove Called when the user approves cloud processing for this request
 * @param onDeny Called when the user denies cloud processing
 * @param onAlwaysAllow Called when the user wants to skip consent for this type
 */
@Composable
fun CloudConsentDialog(
    request: String,
    redactedRequest: String,
    tier: PrivacyTier,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onAlwaysAllow: () -> Unit
) {
    val wasRedacted = request != redactedRequest

    Dialog(
        onDismissRequest = onDeny,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TerminalColors.Background)
                .border(
                    width = 1.dp,
                    color = TerminalColors.Surface,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            // ---- Title bar (terminal style) ----
            DialogTitleBar(tier = tier)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // ---- Warning message ----
                WarningBanner()

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Request comparison ----
                if (wasRedacted) {
                    RequestComparison(
                        original = request,
                        redacted = redactedRequest
                    )
                } else {
                    SingleRequestView(request = request)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Privacy tier indicator ----
                TierExplanation(tier = tier, wasRedacted = wasRedacted)

                Spacer(modifier = Modifier.height(16.dp))

                // ---- Action buttons ----
                ActionButtons(
                    onApprove = onApprove,
                    onDeny = onDeny,
                    onAlwaysAllow = onAlwaysAllow
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ---- Fine print ----
                Text(
                    text = "Cloud calls go directly from your device to the provider. " +
                            "Un-Dios does not store or relay your queries.",
                    style = DialogMonoStyle.copy(
                        fontSize = 8.sp,
                        color = TerminalColors.Timestamp,
                        lineHeight = 12.sp
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

// =============================================================================
// Title bar
// =============================================================================

@Composable
private fun DialogTitleBar(tier: PrivacyTier) {
    val tierColor = when (tier) {
        PrivacyTier.LOCAL -> TerminalColors.PrivacyLocal
        PrivacyTier.ANONYMIZED -> TerminalColors.PrivacyAnonymized
        PrivacyTier.CLOUD -> TerminalColors.PrivacyCloud
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalColors.Surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Window dots
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(TerminalColors.Error.copy(alpha = 0.8f))
            )
            Spacer(modifier = Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(TerminalColors.Warning.copy(alpha = 0.8f))
            )
            Spacer(modifier = Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(TerminalColors.Success.copy(alpha = 0.8f))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "cloud consent",
                style = DialogMonoStyle.copy(
                    fontSize = 11.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }

        // Tier badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(tierColor.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = tier.name,
                style = DialogMonoStyle.copy(
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = tierColor
                )
            )
        }
    }
}

// =============================================================================
// Warning banner
// =============================================================================

@Composable
private fun WarningBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Warning.copy(alpha = 0.08f))
            .border(
                width = 0.5.dp,
                color = TerminalColors.Warning.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = TerminalColors.Warning,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Cloud Processing Request",
                    style = DialogMonoStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Warning
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "This request may benefit from cloud processing " +
                            "for a more accurate or complete response.",
                    style = DialogMonoStyle.copy(
                        fontSize = 9.sp,
                        color = TerminalColors.Output,
                        lineHeight = 13.sp
                    )
                )
            }
        }
    }
}

// =============================================================================
// Request comparison (original vs. redacted)
// =============================================================================

@Composable
private fun RequestComparison(
    original: String,
    redacted: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "# Your request vs. what gets sent",
            style = DialogMonoStyle.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Prompt
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Original request
        RequestBlock(
            label = "ORIGINAL (stays on device)",
            text = original,
            labelColor = TerminalColors.PrivacyLocal,
            borderColor = TerminalColors.PrivacyLocal,
            highlightRedactions = false
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Redacted request
        RequestBlock(
            label = "REDACTED (sent to cloud)",
            text = redacted,
            labelColor = TerminalColors.PrivacyAnonymized,
            borderColor = TerminalColors.PrivacyAnonymized,
            highlightRedactions = true
        )
    }
}

@Composable
private fun SingleRequestView(request: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "# Request to be sent",
            style = DialogMonoStyle.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Prompt
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        RequestBlock(
            label = "FULL REQUEST (sent to cloud)",
            text = request,
            labelColor = TerminalColors.PrivacyCloud,
            borderColor = TerminalColors.PrivacyCloud,
            highlightRedactions = false
        )
    }
}

@Composable
private fun RequestBlock(
    label: String,
    text: String,
    labelColor: androidx.compose.ui.graphics.Color,
    borderColor: androidx.compose.ui.graphics.Color,
    highlightRedactions: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Overlay)
            .border(
                width = 0.5.dp,
                color = borderColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(8.dp)
    ) {
        Text(
            text = label,
            style = DialogMonoStyle.copy(
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = labelColor,
                letterSpacing = 0.5.sp
            )
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (highlightRedactions) {
            // Highlight redaction tokens in a distinct color
            val annotatedText = buildAnnotatedString {
                val tokens = listOf("[EMAIL]", "[PHONE]", "[SSN]", "[CARD]", "[NAME]", "[ADDRESS]")
                var remaining = text
                while (remaining.isNotEmpty()) {
                    val nextToken = tokens
                        .map { token -> token to remaining.indexOf(token) }
                        .filter { it.second >= 0 }
                        .minByOrNull { it.second }

                    if (nextToken != null) {
                        val (token, index) = nextToken
                        // Text before the token
                        if (index > 0) {
                            withStyle(SpanStyle(color = TerminalColors.Output)) {
                                append(remaining.substring(0, index))
                            }
                        }
                        // The redaction token itself
                        withStyle(
                            SpanStyle(
                                color = TerminalColors.Warning,
                                fontWeight = FontWeight.Bold,
                                background = TerminalColors.Warning.copy(alpha = 0.1f)
                            )
                        ) {
                            append(token)
                        }
                        remaining = remaining.substring(index + token.length)
                    } else {
                        withStyle(SpanStyle(color = TerminalColors.Output)) {
                            append(remaining)
                        }
                        remaining = ""
                    }
                }
            }

            Text(
                text = annotatedText,
                style = DialogMonoStyle.copy(
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            )
        } else {
            Text(
                text = text,
                style = DialogMonoStyle.copy(
                    fontSize = 10.sp,
                    color = TerminalColors.Output,
                    lineHeight = 15.sp
                )
            )
        }
    }
}

// =============================================================================
// Tier explanation
// =============================================================================

@Composable
private fun TierExplanation(
    tier: PrivacyTier,
    wasRedacted: Boolean
) {
    val (icon, color, explanation) = when (tier) {
        PrivacyTier.LOCAL -> Triple(
            Icons.Default.Lock,
            TerminalColors.PrivacyLocal,
            "Processing will stay on-device. No data leaves your phone."
        )
        PrivacyTier.ANONYMIZED -> Triple(
            Icons.Default.Shield,
            TerminalColors.PrivacyAnonymized,
            if (wasRedacted)
                "Personal info has been stripped. Only the redacted version will be sent to the cloud provider."
            else
                "No personal info was detected. The full request will be sent to the cloud provider."
        )
        PrivacyTier.CLOUD -> Triple(
            Icons.Default.Cloud,
            TerminalColors.PrivacyCloud,
            "The full request will be sent to the cloud provider as-is. This may include personal information."
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.06f))
            .border(
                width = 0.5.dp,
                color = color.copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = explanation,
            style = DialogMonoStyle.copy(
                fontSize = 9.sp,
                color = TerminalColors.Output,
                lineHeight = 13.sp
            )
        )
    }
}

// =============================================================================
// Action buttons
// =============================================================================

@Composable
private fun ActionButtons(
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onAlwaysAllow: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Primary action: Allow once
        ConsentButton(
            text = "Allow once",
            description = "Process this request via cloud",
            color = TerminalColors.PrivacyCloud,
            isPrimary = true,
            onClick = onApprove
        )

        // Secondary action: Always allow
        ConsentButton(
            text = "Always allow for this type",
            description = "Skip consent for similar requests",
            color = TerminalColors.PrivacyAnonymized,
            isPrimary = false,
            onClick = onAlwaysAllow
        )

        HorizontalDivider(
            color = TerminalColors.Surface,
            modifier = Modifier.padding(vertical = 2.dp)
        )

        // Deny action: Keep local
        ConsentButton(
            text = "Keep local",
            description = "Process on-device only (may be limited)",
            color = TerminalColors.PrivacyLocal,
            isPrimary = false,
            onClick = onDeny
        )
    }
}

@Composable
private fun ConsentButton(
    text: String,
    description: String,
    color: androidx.compose.ui.graphics.Color,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isPrimary) color.copy(alpha = 0.12f)
                else TerminalColors.Surface.copy(alpha = 0.4f)
            )
            .border(
                width = if (isPrimary) 1.dp else 0.5.dp,
                color = if (isPrimary) color.copy(alpha = 0.4f)
                else TerminalColors.Subtext.copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                style = DialogMonoStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPrimary) color else TerminalColors.Command
                )
            )
            Text(
                text = description,
                style = DialogMonoStyle.copy(
                    fontSize = 8.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }

        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (isPrimary) 0.8f else 0.4f))
        )
    }
}
