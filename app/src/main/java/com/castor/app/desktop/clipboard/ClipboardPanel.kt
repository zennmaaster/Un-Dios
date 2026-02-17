package com.castor.app.desktop.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.ui.theme.TerminalColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Clipboard history panel that shows recent clipboard entries.
 *
 * Provides a searchable, scrollable list of clipboard history entries with:
 * - Text preview, timestamp, and optional source app badge for each entry
 * - Click to paste (copies the entry back to the system clipboard)
 * - Long-press for options (delete, pin/unpin)
 * - Pinned items stay at the top and survive clear operations
 * - "Clear clipboard" button at the bottom
 * - Search/filter bar for finding specific entries
 *
 * The panel animates in with [slideInVertically] from the top.
 *
 * @param isVisible Whether the clipboard panel is currently shown
 * @param onDismiss Callback to close the panel
 * @param clipboardManager The singleton clipboard history manager instance
 */
@Composable
fun ClipboardPanel(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    clipboardManager: ClipboardHistoryManager
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it })
    ) {
        ClipboardPanelContent(
            clipboardManager = clipboardManager,
            onDismiss = onDismiss
        )
    }
}

/**
 * Internal content layout for the clipboard panel.
 *
 * @param clipboardManager The clipboard history manager
 * @param onDismiss Callback to close the panel
 */
@Composable
private fun ClipboardPanelContent(
    clipboardManager: ClipboardHistoryManager,
    onDismiss: () -> Unit
) {
    val history by clipboardManager.history.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    val filteredHistory = remember(history, searchQuery) {
        val sorted = history.sortedWith(
            compareByDescending<ClipboardEntry> { it.isPinned }
                .thenByDescending { it.timestamp }
        )
        if (searchQuery.isBlank()) {
            sorted
        } else {
            sorted.filter {
                it.content.contains(searchQuery, ignoreCase = true) ||
                    (it.sourceApp?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

    // Dismiss backdrop
    Box(
        modifier = Modifier
            .clickable(onClick = onDismiss)
            .background(TerminalColors.Overlay.copy(alpha = 0.5f))
    ) {
        // Panel content anchored to top-right
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopEnd
        ) {
            Column(
                modifier = Modifier
                    .width(340.dp)
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .background(TerminalColors.Surface)
                    .padding(16.dp)
            ) {
                // ---- Panel header ----
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Clipboard",
                        tint = TerminalColors.Accent,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "# clipboard-history",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Accent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${history.size}/50",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TerminalColors.Timestamp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Search bar ----
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "$ grep clipboard...",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TerminalColors.Timestamp
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TerminalColors.Accent,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TerminalColors.Command
                    ),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = TerminalColors.Background,
                        unfocusedContainerColor = TerminalColors.Background,
                        cursorColor = TerminalColors.Cursor,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ---- Separator ----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TerminalColors.Background)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ---- Clipboard history list ----
                if (filteredHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "$ clipboard is empty"
                            else "$ no matching entries",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TerminalColors.Timestamp
                            )
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        items(
                            items = filteredHistory,
                            key = { it.id }
                        ) { entry ->
                            ClipboardEntryRow(
                                entry = entry,
                                onPaste = {
                                    // Copy back to system clipboard
                                    val systemClipboard = context.getSystemService(
                                        Context.CLIPBOARD_SERVICE
                                    ) as ClipboardManager
                                    val clipData = ClipData.newPlainText("Castor Clipboard", entry.content)
                                    systemClipboard.setPrimaryClip(clipData)
                                },
                                onPin = {
                                    if (entry.isPinned) {
                                        clipboardManager.unpinEntry(entry.id)
                                    } else {
                                        clipboardManager.pinEntry(entry.id)
                                    }
                                },
                                onDelete = { clipboardManager.removeEntry(entry.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ---- Separator ----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TerminalColors.Background)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ---- Clear clipboard button ----
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = { clipboardManager.clearHistory() })
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear clipboard",
                        tint = TerminalColors.Error,
                        modifier = Modifier.size(14.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "$ clear-clipboard",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TerminalColors.Error
                        )
                    )
                }
            }
        }
    }
}

/**
 * A single clipboard history entry row.
 *
 * Shows a truncated text preview, timestamp, source app badge (if available),
 * and a pin indicator. Clicking pastes the content back to the system clipboard.
 * Long-pressing reveals pin/delete options.
 *
 * @param entry The clipboard entry data
 * @param onPaste Callback to paste this entry to the system clipboard
 * @param onPin Callback to toggle pin state
 * @param onDelete Callback to delete this entry
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipboardEntryRow(
    entry: ClipboardEntry,
    onPaste: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var showActions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (entry.isPinned) TerminalColors.Accent.copy(alpha = 0.05f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = onPaste,
                onLongClick = { showActions = !showActions }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        // Metadata row: timestamp + source app + pin indicator
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "[${dateFormat.format(Date(entry.timestamp))}]",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TerminalColors.Timestamp
                )
            )

            if (entry.sourceApp != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(TerminalColors.Accent.copy(alpha = 0.15f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = entry.sourceApp,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = TerminalColors.Accent
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (entry.isPinned) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "Pinned",
                    tint = TerminalColors.Accent,
                    modifier = Modifier.size(10.dp)
                )
            }
        }

        // Content preview
        Text(
            text = entry.content,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Command
            ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )

        // Action buttons (shown on long-press)
        if (showActions) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pin/unpin button
                IconButton(
                    onClick = {
                        onPin()
                        showActions = false
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = if (entry.isPinned) "Unpin" else "Pin",
                        tint = if (entry.isPinned) TerminalColors.Accent else TerminalColors.Subtext,
                        modifier = Modifier.size(12.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = if (entry.isPinned) "unpin" else "pin",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = TerminalColors.Timestamp
                    )
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Delete button
                IconButton(
                    onClick = {
                        onDelete()
                        showActions = false
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = TerminalColors.Error,
                        modifier = Modifier.size(12.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = "delete",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = TerminalColors.Error
                    )
                )
            }
        }

        // Separator
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TerminalColors.Background.copy(alpha = 0.5f))
        )
    }
}
