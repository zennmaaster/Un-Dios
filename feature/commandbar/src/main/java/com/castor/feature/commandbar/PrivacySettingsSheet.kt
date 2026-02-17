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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.common.model.PrivacyTier
import com.castor.core.security.PrivacyPreferences
import com.castor.core.security.PrivacyPreferences.CloudProvider
import com.castor.core.ui.theme.TerminalColors
import kotlinx.coroutines.launch

// =============================================================================
// Style constants
// =============================================================================

private val MonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    color = Color(0xFFCDD6F4) // CatppuccinMocha command color
)

// =============================================================================
// Main composable
// =============================================================================

/**
 * Bottom sheet that presents the full privacy settings interface.
 *
 * Styled as a terminal-aesthetic settings panel with monospace fonts,
 * the Catppuccin Mocha palette, and colored tier indicators matching
 * the terminal prompt colors.
 *
 * @param privacyPreferences The injected [PrivacyPreferences] instance
 * @param onDismiss Called when the sheet is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsSheet(
    privacyPreferences: PrivacyPreferences,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Collect current values
    val defaultTier by privacyPreferences.defaultTier.collectAsState(initial = PrivacyTier.LOCAL)
    val messagingTier by privacyPreferences.messagingTier.collectAsState(initial = PrivacyTier.LOCAL)
    val mediaTier by privacyPreferences.mediaTier.collectAsState(initial = PrivacyTier.ANONYMIZED)
    val generalTier by privacyPreferences.generalTier.collectAsState(initial = PrivacyTier.ANONYMIZED)
    val cloudProvider by privacyPreferences.cloudProvider.collectAsState(initial = CloudProvider.ANTHROPIC)
    val alwaysAsk by privacyPreferences.alwaysAskForCloud.collectAsState(initial = true)
    val cloudApiKey by privacyPreferences.cloudApiKey.collectAsState(initial = null)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TerminalColors.Background,
        contentColor = TerminalColors.Command,
        dragHandle = {
            // Custom terminal-style drag handle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 32.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TerminalColors.Subtext)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ---- Header ----
            SheetHeader()

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Current Privacy Status ----
            CurrentPrivacyStatus(tier = defaultTier)

            Spacer(modifier = Modifier.height(20.dp))
            SectionDivider()

            // ---- Default Tier Selector ----
            SectionTitle(text = "Default Privacy Tier")
            Spacer(modifier = Modifier.height(8.dp))
            TierSelector(
                selectedTier = defaultTier,
                onTierSelected = { tier ->
                    scope.launch { privacyPreferences.setDefaultTier(tier) }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))
            SectionDivider()

            // ---- Per-Category Overrides ----
            SectionTitle(text = "Category Overrides")
            Spacer(modifier = Modifier.height(8.dp))

            CategoryTierRow(
                category = "Messaging",
                description = "SMS, WhatsApp, Telegram",
                tier = messagingTier,
                onTierSelected = { tier ->
                    scope.launch { privacyPreferences.setMessagingTier(tier) }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            CategoryTierRow(
                category = "Media",
                description = "Spotify, YouTube, Audible",
                tier = mediaTier,
                onTierSelected = { tier ->
                    scope.launch { privacyPreferences.setMediaTier(tier) }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            CategoryTierRow(
                category = "General",
                description = "Knowledge, code, factual",
                tier = generalTier,
                onTierSelected = { tier ->
                    scope.launch { privacyPreferences.setGeneralTier(tier) }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))
            SectionDivider()

            // ---- Cloud Configuration ----
            SectionTitle(text = "Cloud Configuration")
            Spacer(modifier = Modifier.height(8.dp))

            // Always ask toggle
            ToggleRow(
                label = "Ask before cloud requests",
                description = "Prompt for consent before each cloud call",
                checked = alwaysAsk,
                onCheckedChange = { ask ->
                    scope.launch { privacyPreferences.setAlwaysAsk(ask) }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Cloud provider selector
            CloudProviderSelector(
                selectedProvider = cloudProvider,
                onProviderSelected = { provider ->
                    scope.launch { privacyPreferences.setCloudProvider(provider) }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // API key input
            ApiKeyInput(
                currentKey = cloudApiKey,
                onKeySubmit = { key ->
                    scope.launch { privacyPreferences.setCloudApiKey(key) }
                },
                onKeyClear = {
                    scope.launch { privacyPreferences.clearCloudApiKey() }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))
            SectionDivider()

            // ---- Privacy Policy Summary ----
            PrivacyPolicySummary()

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// =============================================================================
// Header
// =============================================================================

@Composable
private fun SheetHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            tint = TerminalColors.Accent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Privacy Settings",
            style = MonoStyle.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Control how Un-Dios processes your data",
        style = MonoStyle.copy(
            fontSize = 11.sp,
            color = TerminalColors.Timestamp
        )
    )
}

// =============================================================================
// Current privacy status display
// =============================================================================

@Composable
private fun CurrentPrivacyStatus(tier: PrivacyTier) {
    val (color, icon, label) = when (tier) {
        PrivacyTier.LOCAL -> Triple(
            TerminalColors.PrivacyLocal,
            Icons.Default.Lock,
            "LOCAL - All processing on-device"
        )
        PrivacyTier.ANONYMIZED -> Triple(
            TerminalColors.PrivacyAnonymized,
            Icons.Default.Shield,
            "ANONYMIZED - PII stripped before cloud"
        )
        PrivacyTier.CLOUD -> Triple(
            TerminalColors.PrivacyCloud,
            Icons.Default.Cloud,
            "CLOUD - Full cloud processing allowed"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Glowing dot indicator
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Current Status",
                    style = MonoStyle.copy(
                        fontSize = 9.sp,
                        color = TerminalColors.Timestamp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = label,
                    style = MonoStyle.copy(
                        fontSize = 12.sp,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

// =============================================================================
// Tier selector (LOCAL / ANONYMIZED / CLOUD)
// =============================================================================

@Composable
private fun TierSelector(
    selectedTier: PrivacyTier,
    onTierSelected: (PrivacyTier) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PrivacyTier.entries.forEach { tier ->
            TierChip(
                tier = tier,
                isSelected = tier == selectedTier,
                onClick = { onTierSelected(tier) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TierChip(
    tier: PrivacyTier,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = when (tier) {
        PrivacyTier.LOCAL -> TerminalColors.PrivacyLocal
        PrivacyTier.ANONYMIZED -> TerminalColors.PrivacyAnonymized
        PrivacyTier.CLOUD -> TerminalColors.PrivacyCloud
    }

    val label = when (tier) {
        PrivacyTier.LOCAL -> "LOCAL"
        PrivacyTier.ANONYMIZED -> "ANON"
        PrivacyTier.CLOUD -> "CLOUD"
    }

    val icon = when (tier) {
        PrivacyTier.LOCAL -> Icons.Default.Lock
        PrivacyTier.ANONYMIZED -> Icons.Default.Shield
        PrivacyTier.CLOUD -> Icons.Default.Cloud
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSelected) color.copy(alpha = 0.15f)
                else TerminalColors.Surface.copy(alpha = 0.5f)
            )
            .border(
                width = if (isSelected) 1.dp else 0.5.dp,
                color = if (isSelected) color.copy(alpha = 0.6f)
                else TerminalColors.Subtext.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) color else TerminalColors.Subtext,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MonoStyle.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) color else TerminalColors.Subtext
                )
            )
        }
    }
}

// =============================================================================
// Category tier overrides
// =============================================================================

@Composable
private fun CategoryTierRow(
    category: String,
    description: String,
    tier: PrivacyTier,
    onTierSelected: (PrivacyTier) -> Unit
) {
    val tierColor = when (tier) {
        PrivacyTier.LOCAL -> TerminalColors.PrivacyLocal
        PrivacyTier.ANONYMIZED -> TerminalColors.PrivacyAnonymized
        PrivacyTier.CLOUD -> TerminalColors.PrivacyCloud
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.4f))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category,
                    style = MonoStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Command
                    )
                )
                Text(
                    text = description,
                    style = MonoStyle.copy(
                        fontSize = 9.sp,
                        color = TerminalColors.Timestamp
                    )
                )
            }

            // Current tier indicator (tap to cycle)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(tierColor.copy(alpha = 0.15f))
                    .border(
                        width = 1.dp,
                        color = tierColor.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Cycle: LOCAL -> ANONYMIZED -> CLOUD -> LOCAL
                        val next = when (tier) {
                            PrivacyTier.LOCAL -> PrivacyTier.ANONYMIZED
                            PrivacyTier.ANONYMIZED -> PrivacyTier.CLOUD
                            PrivacyTier.CLOUD -> PrivacyTier.LOCAL
                        }
                        onTierSelected(next)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tier.name,
                    style = MonoStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = tierColor
                    )
                )
            }
        }
    }
}

// =============================================================================
// Toggle row (reusable)
// =============================================================================

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.4f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MonoStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = description,
                style = MonoStyle.copy(
                    fontSize = 9.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TerminalColors.PrivacyLocal,
                checkedTrackColor = TerminalColors.PrivacyLocal.copy(alpha = 0.3f),
                uncheckedThumbColor = TerminalColors.Subtext,
                uncheckedTrackColor = TerminalColors.Surface
            )
        )
    }
}

// =============================================================================
// Cloud provider selector
// =============================================================================

@Composable
private fun CloudProviderSelector(
    selectedProvider: CloudProvider,
    onProviderSelected: (CloudProvider) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.4f))
            .padding(10.dp)
    ) {
        Text(
            text = "Cloud Provider",
            style = MonoStyle.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        CloudProvider.entries.forEach { provider ->
            ProviderOption(
                provider = provider,
                isSelected = provider == selectedProvider,
                onClick = { onProviderSelected(provider) }
            )
            if (provider != CloudProvider.entries.last()) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ProviderOption(
    provider: CloudProvider,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isSelected) TerminalColors.Accent.copy(alpha = 0.1f)
                else TerminalColors.Background.copy(alpha = 0.3f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Radio-style indicator
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .border(
                    width = 1.5.dp,
                    color = if (isSelected) TerminalColors.Accent else TerminalColors.Subtext,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(TerminalColors.Accent)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = provider.displayName,
            style = MonoStyle.copy(
                fontSize = 11.sp,
                color = if (isSelected) TerminalColors.Command else TerminalColors.Subtext
            )
        )
    }
}

// =============================================================================
// API key input
// =============================================================================

@Composable
private fun ApiKeyInput(
    currentKey: String?,
    onKeySubmit: (String) -> Unit,
    onKeyClear: () -> Unit
) {
    var keyText by remember { mutableStateOf("") }
    var isVisible by remember { mutableStateOf(false) }
    val hasKey = !currentKey.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.4f))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                tint = if (hasKey) TerminalColors.Success else TerminalColors.Subtext,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "API Key",
                style = MonoStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.weight(1f))
            if (hasKey) {
                Text(
                    text = "configured",
                    style = MonoStyle.copy(
                        fontSize = 9.sp,
                        color = TerminalColors.Success,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input field
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(TerminalColors.Background)
                    .border(
                        width = 0.5.dp,
                        color = TerminalColors.Subtext.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                BasicTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MonoStyle.copy(
                        fontSize = 11.sp,
                        color = TerminalColors.Command
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(TerminalColors.Cursor),
                    visualTransformation = if (isVisible) VisualTransformation.None
                    else PasswordVisualTransformation(mask = '*'),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    decorationBox = { innerTextField ->
                        Box {
                            if (keyText.isEmpty()) {
                                Text(
                                    text = if (hasKey) "enter new key to replace..."
                                    else "paste your API key here...",
                                    style = MonoStyle.copy(
                                        fontSize = 11.sp,
                                        color = TerminalColors.Subtext
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Visibility toggle
            IconButton(
                onClick = { isVisible = !isVisible },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isVisible) Icons.Default.VisibilityOff
                    else Icons.Default.Visibility,
                    contentDescription = if (isVisible) "Hide key" else "Show key",
                    tint = TerminalColors.Subtext,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Submit button
            IconButton(
                onClick = {
                    if (keyText.isNotBlank()) {
                        onKeySubmit(keyText.trim())
                        keyText = ""
                    }
                },
                modifier = Modifier.size(32.dp),
                enabled = keyText.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Save key",
                    tint = if (keyText.isNotBlank()) TerminalColors.Success
                    else TerminalColors.Subtext.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Clear key option
        if (hasKey) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "[clear stored key]",
                style = MonoStyle.copy(
                    fontSize = 9.sp,
                    color = TerminalColors.Error.copy(alpha = 0.7f)
                ),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onKeyClear
                )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Keys are stored in AES-256 encrypted storage on-device.",
            style = MonoStyle.copy(
                fontSize = 8.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

// =============================================================================
// Privacy policy summary
// =============================================================================

@Composable
private fun PrivacyPolicySummary() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.3f))
            .padding(12.dp)
    ) {
        Text(
            text = "Privacy Policy",
            style = MonoStyle.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        PolicyItem(
            icon = Icons.Default.Lock,
            color = TerminalColors.PrivacyLocal,
            text = "LOCAL: All processing happens on your device. No data " +
                    "leaves your phone. This is the default for all personal " +
                    "data including messages, contacts, and calendar."
        )

        Spacer(modifier = Modifier.height(6.dp))

        PolicyItem(
            icon = Icons.Default.Shield,
            color = TerminalColors.PrivacyAnonymized,
            text = "ANONYMIZED: Personal identifiers (names, emails, phone " +
                    "numbers) are stripped before sending to cloud. Only the " +
                    "redacted query is transmitted. Used for general knowledge."
        )

        Spacer(modifier = Modifier.height(6.dp))

        PolicyItem(
            icon = Icons.Default.Cloud,
            color = TerminalColors.PrivacyCloud,
            text = "CLOUD: Full query sent to cloud provider. Only used when " +
                    "you explicitly request it (web search, real-time data). " +
                    "You can require per-request consent via the toggle above."
        )

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = TerminalColors.Subtext.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "API keys are stored locally in AES-256 encrypted " +
                    "storage and never transmitted to Un-Dios servers. " +
                    "Cloud calls go directly from your device to the " +
                    "selected provider.",
            style = MonoStyle.copy(
                fontSize = 9.sp,
                color = TerminalColors.Timestamp,
                lineHeight = 14.sp
            )
        )
    }
}

@Composable
private fun PolicyItem(
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    text: String
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(12.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MonoStyle.copy(
                fontSize = 9.sp,
                color = TerminalColors.Output,
                lineHeight = 14.sp
            )
        )
    }
}

// =============================================================================
// Shared UI elements
// =============================================================================

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = "# $text",
        style = MonoStyle.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TerminalColors.Prompt
        )
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        color = TerminalColors.Surface,
        thickness = 1.dp,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
