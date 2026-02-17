package com.castor.feature.messaging.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.core.common.model.CastorMessage
import com.castor.core.common.util.DateUtils
import com.castor.core.ui.components.SourceBadge
import com.castor.core.ui.theme.TerminalColors

/**
 * Full conversation thread view with Ubuntu/terminal-style aesthetics.
 *
 * Features:
 * - Message bubbles with sender differentiation (received left, sent right)
 * - Monospace font for message bodies
 * - Reply input bar at the bottom
 * - Smart reply suggestion chips (LLM-backed with shimmer loading)
 * - Thread summary with collapsible card: `$ tldr --thread`
 * - In-conversation search with match highlighting
 * - Long-press context menu with copy and metadata
 * - Auto-scroll to bottom on new messages
 *
 * @param sender The conversation contact name (resolved from nav args or passed directly)
 * @param groupName Optional group name
 * @param onBack Back navigation callback
 * @param isEmbedded When true, the screen is embedded in the split-pane (hides top bar nav icon)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    sender: String,
    groupName: String?,
    onBack: () -> Unit,
    isEmbedded: Boolean = false,
    viewModel: ConversationViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val filteredMessages by viewModel.filteredMessages.collectAsState()
    val replyText by viewModel.replyText.collectAsState()
    val smartReplies by viewModel.smartReplies.collectAsState()
    val isGeneratingReplies by viewModel.isGeneratingReplies.collectAsState()
    val isSummarizing by viewModel.isSummarizing.collectAsState()
    val threadSummary by viewModel.threadSummary.collectAsState()
    val searchQuery by viewModel.conversationSearchQuery.collectAsState()

    var showSearch by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Determine which messages to display: filtered or all
    val displayMessages = if (searchQuery.isBlank()) messages else filteredMessages

    // Mark conversation as read on entry
    LaunchedEffect(Unit) {
        viewModel.markConversationAsRead()
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && searchQuery.isBlank()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Determine the source badge from the first message
    val conversationSource = messages.firstOrNull()?.source

    Scaffold(
        topBar = {
            ConversationTopBar(
                sender = sender,
                groupName = groupName,
                messageCount = messages.size,
                conversationSource = conversationSource,
                isSummarizing = isSummarizing,
                isEmbedded = isEmbedded,
                showSearch = showSearch,
                searchQuery = searchQuery,
                onBack = onBack,
                onSummarize = { viewModel.summarizeThread() },
                onToggleSearch = {
                    showSearch = !showSearch
                    if (!showSearch) viewModel.clearConversationSearch()
                },
                onSearchQueryChange = { viewModel.setConversationSearchQuery(it) }
            )
        },
        bottomBar = {
            ReplyBar(
                replyText = replyText,
                smartReplies = smartReplies,
                isGeneratingReplies = isGeneratingReplies,
                onReplyTextChange = { viewModel.updateReplyText(it) },
                onSend = { viewModel.sendReply() },
                onSmartReply = { viewModel.sendSmartReply(it) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(TerminalColors.Background)
        ) {
            // Thread summary card (when available)
            AnimatedVisibility(
                visible = threadSummary != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                threadSummary?.let { summary ->
                    ThreadSummaryCard(
                        summary = summary,
                        onDismiss = { viewModel.dismissSummary() }
                    )
                }
            }

            // "Tap to generate summary" prompt when no summary exists
            AnimatedVisibility(
                visible = threadSummary == null && messages.isNotEmpty() && !isSummarizing,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                TldrPrompt(
                    isSummarizing = isSummarizing,
                    onTap = { viewModel.summarizeThread() }
                )
            }

            // Search results count when searching
            AnimatedVisibility(
                visible = searchQuery.isNotBlank(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Text(
                    text = "$ grep -c \"$searchQuery\" -> ${filteredMessages.size} match${if (filteredMessages.size != 1) "es" else ""}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = TerminalColors.Prompt,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Message list
            if (displayMessages.isEmpty() && searchQuery.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$ tail -f /conversation/$sender",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = TerminalColors.Subtext
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Waiting for messages...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = TerminalColors.Subtext.copy(alpha = 0.5f)
                        )
                    }
                }
            } else if (displayMessages.isEmpty() && searchQuery.isNotBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matches found for \"$searchQuery\"",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = TerminalColors.Subtext.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = displayMessages,
                        key = { it.id }
                    ) { message ->
                        MessageBubble(
                            message = message,
                            highlightQuery = searchQuery
                        )
                    }
                }
            }
        }
    }
}

// -- Conversation Top Bar ---------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationTopBar(
    sender: String,
    groupName: String?,
    messageCount: Int,
    conversationSource: com.castor.core.common.model.MessageSource?,
    isSummarizing: Boolean,
    isEmbedded: Boolean,
    showSearch: Boolean,
    searchQuery: String,
    onBack: () -> Unit,
    onSummarize: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit
) {
    TopAppBar(
        title = {
            if (showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            "grep conversation...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = TerminalColors.Subtext.copy(alpha = 0.5f)
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = TerminalColors.Command
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TerminalColors.Prompt,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = TerminalColors.Surface.copy(alpha = 0.5f),
                        unfocusedContainerColor = TerminalColors.Surface.copy(alpha = 0.3f),
                        cursorColor = TerminalColors.Cursor
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(TerminalColors.Accent.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sender.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = TerminalColors.Accent
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = sender,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = TerminalColors.Command,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (conversationSource != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                SourceBadge(source = conversationSource)
                            }
                        }
                        Row {
                            if (!groupName.isNullOrEmpty()) {
                                Text(
                                    text = "# $groupName",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = TerminalColors.Info
                                )
                                Text(
                                    text = "  |  ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TerminalColors.Subtext.copy(alpha = 0.4f)
                                )
                            }
                            Text(
                                text = "$messageCount msgs",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                ),
                                color = TerminalColors.Subtext.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        },
        navigationIcon = {
            if (!isEmbedded) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back [Esc]",
                        tint = TerminalColors.Command
                    )
                }
            }
        },
        actions = {
            // Search toggle
            IconButton(onClick = onToggleSearch) {
                Icon(
                    if (showSearch) Icons.Default.SearchOff else Icons.Default.Search,
                    contentDescription = if (showSearch) "Close search" else "Search [Ctrl+F]",
                    tint = TerminalColors.Prompt
                )
            }
            // Summarize thread button
            IconButton(
                onClick = onSummarize,
                enabled = !isSummarizing
            ) {
                if (isSummarizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = TerminalColors.Accent
                    )
                } else {
                    Icon(
                        Icons.Default.Summarize,
                        contentDescription = "Summarize thread [Ctrl+S]",
                        tint = TerminalColors.Accent
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = TerminalColors.Background
        )
    )
}

// -- TLDR Prompt (tap to generate summary) ----------------------------------------

@Composable
private fun TldrPrompt(
    isSummarizing: Boolean,
    onTap: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(6.dp),
        color = TerminalColors.Surface.copy(alpha = 0.6f),
        onClick = onTap
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$ tldr --thread",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = TerminalColors.Prompt
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isSummarizing) "Generating..." else "Tap to generate summary",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontStyle = FontStyle.Italic
                ),
                color = TerminalColors.Subtext.copy(alpha = 0.6f)
            )
        }
    }
}

// -- Thread Summary Card ----------------------------------------------------------

@Composable
private fun ThreadSummaryCard(
    summary: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = TerminalColors.Surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$ ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TerminalColors.Prompt
                )
                Text(
                    text = "tldr --thread",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = TerminalColors.Command,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(16.dp),
                        tint = TerminalColors.Subtext
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                ),
                color = TerminalColors.Output
            )
        }
    }
}

// -- Message Bubble (with long-press context menu) --------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: CastorMessage,
    highlightQuery: String = ""
) {
    val isSentByUser = message.sender == "You"
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val context = LocalContext.current
    var showContextMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isSentByUser) Alignment.End else Alignment.Start
    ) {
        // Sender label (only for received messages)
        if (!isSentByUser) {
            Text(
                text = message.sender,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = TerminalColors.Accent,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        // Bubble
        Box {
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (isSentByUser) 12.dp else 4.dp,
                    topEnd = if (isSentByUser) 4.dp else 12.dp,
                    bottomStart = 12.dp,
                    bottomEnd = 12.dp
                ),
                color = if (isSentByUser) {
                    TerminalColors.Accent.copy(alpha = 0.2f)
                } else {
                    TerminalColors.Surface
                },
                modifier = Modifier
                    .widthIn(max = screenWidth * 0.78f)
                    .combinedClickable(
                        onClick = { },
                        onLongClick = { showContextMenu = true }
                    )
            ) {
                // Highlight background if search matches this message
                val isHighlighted = highlightQuery.isNotBlank() &&
                    (message.content.lowercase().contains(highlightQuery.lowercase()) ||
                        message.sender.lowercase().contains(highlightQuery.lowercase()))

                val bubbleBackground = if (isHighlighted) {
                    TerminalColors.Warning.copy(alpha = 0.15f)
                } else {
                    Color.Transparent
                }

                Column(
                    modifier = Modifier
                        .background(bubbleBackground)
                        .padding(10.dp)
                ) {
                    // Message content in monospace, with search highlighting
                    if (highlightQuery.isNotBlank() && message.content.lowercase()
                            .contains(highlightQuery.lowercase())
                    ) {
                        HighlightedText(
                            text = message.content,
                            query = highlightQuery,
                            textColor = if (isSentByUser) TerminalColors.Command else TerminalColors.Output,
                            highlightColor = TerminalColors.Warning
                        )
                    } else {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp,
                                fontSize = 13.sp
                            ),
                            color = if (isSentByUser) TerminalColors.Command else TerminalColors.Output
                        )
                    }

                    // Timestamp + read indicator row
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isSentByUser) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = DateUtils.formatTime(message.timestamp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            ),
                            color = TerminalColors.Timestamp
                        )
                        if (!message.isRead && !isSentByUser) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(TerminalColors.Accent)
                            )
                        }
                    }
                }
            }

            // Long-press context menu
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false }
            ) {
                // Copy message
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = TerminalColors.Prompt
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "$ cp --msg",
                                fontFamily = FontFamily.Monospace,
                                color = TerminalColors.Command
                            )
                        }
                    },
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("message", message.content)
                        clipboard.setPrimaryClip(clip)
                        showContextMenu = false
                    }
                )

                // Message metadata
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = TerminalColors.Info
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "$ stat --msg",
                                fontFamily = FontFamily.Monospace,
                                color = TerminalColors.Command
                            )
                        }
                    },
                    onClick = { /* Metadata is shown below, keep menu open for viewing */ }
                )

                // Metadata details row
                HorizontalDivider(color = TerminalColors.Surface, thickness = 0.5.dp)
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    MetadataRow(label = "id", value = message.id)
                    MetadataRow(label = "source", value = message.source.name.lowercase())
                    MetadataRow(label = "sender", value = message.sender)
                    MetadataRow(label = "time", value = DateUtils.formatTime(message.timestamp))
                    MetadataRow(
                        label = "read",
                        value = if (message.isRead) "true" else "false"
                    )
                    if (message.notificationKey != null) {
                        MetadataRow(label = "nkey", value = message.notificationKey!!)
                    }
                }
            }
        }
    }
}

// -- Metadata Row (for long-press info) -------------------------------------------

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            ),
            color = TerminalColors.Prompt
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            ),
            color = TerminalColors.Output,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -- Highlighted Text (search match highlighting) ---------------------------------

@Composable
private fun HighlightedText(
    text: String,
    query: String,
    textColor: Color,
    highlightColor: Color
) {
    val annotatedString = buildAnnotatedString {
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        var startIndex = 0

        while (true) {
            val matchIndex = lowerText.indexOf(lowerQuery, startIndex)
            if (matchIndex == -1) {
                // Append remaining text
                withStyle(SpanStyle(color = textColor)) {
                    append(text.substring(startIndex))
                }
                break
            }
            // Append text before the match
            if (matchIndex > startIndex) {
                withStyle(SpanStyle(color = textColor)) {
                    append(text.substring(startIndex, matchIndex))
                }
            }
            // Append the matched text with highlight
            withStyle(
                SpanStyle(
                    color = TerminalColors.Background,
                    background = highlightColor
                )
            ) {
                append(text.substring(matchIndex, matchIndex + query.length))
            }
            startIndex = matchIndex + query.length
        }
    }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp,
            fontSize = 13.sp
        )
    )
}

// -- Reply Bar --------------------------------------------------------------------

@Composable
private fun ReplyBar(
    replyText: String,
    smartReplies: List<String>,
    isGeneratingReplies: Boolean,
    onReplyTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onSmartReply: (String) -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        color = TerminalColors.Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
        ) {
            HorizontalDivider(
                color = TerminalColors.Surface,
                thickness = 0.5.dp
            )

            // Smart reply chips (or shimmer loading state)
            if (replyText.isEmpty()) {
                if (isGeneratingReplies) {
                    // Shimmer loading animation for smart replies
                    SmartReplyShimmer()
                } else if (smartReplies.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(smartReplies) { reply ->
                            SuggestionChip(
                                onClick = { onSmartReply(reply) },
                                label = {
                                    Text(
                                        text = "> $reply",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = TerminalColors.Prompt,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = TerminalColors.Surface.copy(alpha = 0.7f)
                                ),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = TerminalColors.Prompt.copy(alpha = 0.3f),
                                    borderWidth = 0.5.dp
                                ),
                                shape = RoundedCornerShape(4.dp)
                            )
                        }
                    }
                }
            }

            // Input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = onReplyTextChange,
                    placeholder = {
                        Text(
                            "$ reply...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = TerminalColors.Subtext.copy(alpha = 0.5f)
                        )
                    },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = TerminalColors.Command
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TerminalColors.Prompt,
                        unfocusedBorderColor = TerminalColors.Surface,
                        focusedContainerColor = TerminalColors.Surface.copy(alpha = 0.3f),
                        unfocusedContainerColor = TerminalColors.Surface.copy(alpha = 0.2f),
                        cursorColor = TerminalColors.Cursor
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Send button
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = if (replyText.isNotBlank()) {
                        TerminalColors.Prompt
                    } else {
                        TerminalColors.Surface
                    },
                    onClick = {
                        if (replyText.isNotBlank()) onSend()
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send [Enter]",
                            modifier = Modifier.size(20.dp),
                            tint = if (replyText.isNotBlank()) {
                                TerminalColors.Background
                            } else {
                                TerminalColors.Subtext.copy(alpha = 0.4f)
                            }
                        )
                    }
                }
            }

            // Keyboard shortcut hint
            Text(
                text = "Enter to send  |  Ctrl+F to search  |  Ctrl+S to summarize",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontStyle = FontStyle.Italic
                ),
                color = TerminalColors.Subtext.copy(alpha = 0.3f),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp)
            )
        }
    }
}

// -- Smart Reply Shimmer Loading --------------------------------------------------

@Composable
private fun SmartReplyShimmer() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            TerminalColors.Surface.copy(alpha = 0.3f),
            TerminalColors.Prompt.copy(alpha = 0.15f),
            TerminalColors.Surface.copy(alpha = 0.3f)
        ),
        start = Offset(shimmerOffset - 200f, 0f),
        end = Offset(shimmerOffset, 0f)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush)
            )
        }
    }
}
