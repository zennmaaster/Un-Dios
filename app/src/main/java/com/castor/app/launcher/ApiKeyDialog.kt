package com.castor.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.castor.core.ui.theme.TerminalColors

/**
 * Supported AI provider types for cloud inference fallback.
 *
 * Each provider carries a display name for the selector UI and a hint
 * describing the expected API key format.
 */
enum class ApiProvider(val displayName: String, val keyHint: String) {
    OPENAI("OpenAI", "sk-..."),
    ANTHROPIC("Anthropic", "sk-ant-..."),
    CUSTOM("Custom endpoint", "https://your-endpoint.local/v1")
}

/**
 * Terminal-styled dialog for entering and managing cloud AI API keys.
 *
 * Renders as a simulated config-file editor with a provider selector,
 * a masked API key input field, and Save/Cancel actions styled as
 * terminal commands.
 *
 * The dialog intentionally does NOT store the key itself -- it delegates
 * persistence to the caller via the [onSave] callback, which should write
 * to [SecurePreferences] (encrypted storage). The provider name and key
 * are passed back together so the caller can route to the correct backend.
 *
 * @param currentProvider  The currently selected provider, if any.
 * @param currentKey       The current API key (masked for display), if any.
 * @param onSave           Called with `(provider, apiKey)` when the user taps Save.
 * @param onDelete         Called when the user clears the stored key.
 * @param onDismiss        Called when the dialog is dismissed without saving.
 */
@Composable
fun ApiKeyDialog(
    currentProvider: ApiProvider = ApiProvider.ANTHROPIC,
    currentKey: String = "",
    onSave: (provider: ApiProvider, apiKey: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedProvider by remember { mutableIntStateOf(ApiProvider.entries.indexOf(currentProvider)) }
    var apiKeyText by remember { mutableStateOf(currentKey) }
    var showKey by remember { mutableStateOf(false) }

    val provider = ApiProvider.entries[selectedProvider]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TerminalColors.Background)
                .padding(16.dp)
        ) {
            // ---- Header ----
            Text(
                text = "$ vim /etc/un-dios/api.conf",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Prompt
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "# Configure cloud inference provider",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Subtext
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Provider selector ----
            Text(
                text = "[provider]",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Info
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ApiProvider.entries.forEachIndexed { index, entry ->
                    val isSelected = index == selectedProvider
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isSelected) TerminalColors.Accent.copy(alpha = 0.2f)
                                else TerminalColors.Surface
                            )
                            .clickable { selectedProvider = index }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = entry.displayName,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TerminalColors.Accent else TerminalColors.Timestamp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- API key input ----
            Text(
                text = "[credentials]",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Info
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "api_key =",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Command
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Input field with terminal styling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(TerminalColors.Surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (apiKeyText.isEmpty()) {
                    Text(
                        text = provider.keyHint,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TerminalColors.Subtext.copy(alpha = 0.5f)
                        )
                    )
                }
                BasicTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalColors.Prompt
                    ),
                    cursorBrush = SolidColor(TerminalColors.Cursor),
                    visualTransformation = if (showKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation(mask = '*')
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Show/hide toggle
            Text(
                text = if (showKey) "# [hide key]" else "# [show key]",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Accent
                ),
                modifier = Modifier
                    .clickable { showKey = !showKey }
                    .padding(vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ---- Action buttons ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete / clear key
                if (currentKey.isNotEmpty()) {
                    Text(
                        text = "$ rm api_key",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Error
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                onDelete()
                                onDismiss()
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Cancel
                    Text(
                        text = ":q!",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Warning
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )

                    // Save
                    Text(
                        text = ":wq",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Success
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(TerminalColors.Success.copy(alpha = 0.15f))
                            .clickable {
                                if (apiKeyText.isNotBlank()) {
                                    onSave(provider, apiKeyText.trim())
                                    onDismiss()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
