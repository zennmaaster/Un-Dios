package com.castor.app.search

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.core.ui.theme.TerminalColors
import kotlinx.coroutines.delay

// ======================================================================================
// Main overlay composable
// ======================================================================================

/**
 * Full-screen GNOME Spotlight-style universal search overlay.
 *
 * Searches across installed apps, messages, reminders, files, and built-in
 * commands with a terminal-native aesthetic. The search bar is styled as a
 * `grep` command, results are grouped by category with Unix-style path headers,
 * and all text uses [FontFamily.Monospace] with [TerminalColors].
 *
 * Animation: The overlay fades in + slides up from the bottom when shown,
 * and reverses on dismiss. The search field auto-focuses when the overlay opens.
 *
 * @param isVisible Controls whether the overlay is shown (drives AnimatedVisibility)
 * @param onDismiss Callback to close the overlay
 * @param onNavigate Callback for navigation commands (route string), e.g. "messages", "reminders"
 * @param onOpenConversation Callback for opening a specific conversation (sender, groupName)
 * @param onOpenAppDrawer Callback to open the app drawer
 * @param viewModel The [UniversalSearchViewModel] providing search state and actions
 */
@Composable
fun UniversalSearchOverlay(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit = {},
    onOpenConversation: (sender: String, groupName: String?) -> Unit = { _, _ -> },
    onOpenAppDrawer: () -> Unit = {},
    initialQuery: String = "",
    viewModel: UniversalSearchViewModel = hiltViewModel()
) {
    val searchState by viewModel.searchState.collectAsState()
    val query by viewModel.searchQuery.collectAsState()

    // Clear search when overlay is hidden, or pre-fill with initialQuery when shown
    LaunchedEffect(isVisible, initialQuery) {
        if (!isVisible) {
            viewModel.clearSearch()
        } else if (initialQuery.isNotBlank()) {
            viewModel.onQueryChanged(initialQuery)
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(200)) +
            slideInVertically(
                animationSpec = tween(300),
                initialOffsetY = { -it / 4 }
            ),
        exit = fadeOut(animationSpec = tween(150)) +
            slideOutVertically(
                animationSpec = tween(250),
                targetOffsetY = { -it / 4 }
            )
    ) {
        SearchOverlayContent(
            query = query,
            searchState = searchState,
            onQueryChanged = viewModel::onQueryChanged,
            onClearSearch = viewModel::clearSearch,
            onDismiss = onDismiss,
            onAppClick = { packageName ->
                viewModel.launchApp(packageName)
                onDismiss()
            },
            onMessageClick = { sender, groupName ->
                onOpenConversation(sender, groupName)
                onDismiss()
            },
            onReminderClick = {
                onNavigate("reminders")
                onDismiss()
            },
            onFileClick = { filePath ->
                viewModel.openFile(filePath)
                onDismiss()
            },
            onCommandClick = { command ->
                val route = viewModel.executeCommand(command)
                if (command == "apps") {
                    onOpenAppDrawer()
                }
                if (route != null) {
                    onNavigate(route)
                }
                onDismiss()
            }
        )
    }
}

// ======================================================================================
// Overlay content layout
// ======================================================================================

/**
 * The full content layout of the search overlay: search bar at top, results below.
 * Separated from AnimatedVisibility wrapper for clarity and testability.
 */
@Composable
private fun SearchOverlayContent(
    query: String,
    searchState: UniversalSearchState,
    onQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onDismiss: () -> Unit,
    onAppClick: (packageName: String) -> Unit,
    onMessageClick: (sender: String, groupName: String?) -> Unit,
    onReminderClick: (reminderId: Long) -> Unit,
    onFileClick: (filePath: String) -> Unit,
    onCommandClick: (command: String) -> Unit
) {
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background.copy(alpha = 0.97f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ---- Top bar: back button + title ----
            SearchTopBar(onDismiss = onDismiss)

            // ---- Search input field ----
            SearchInputBar(
                query = query,
                onQueryChanged = onQueryChanged,
                onClear = onClearSearch
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ---- Results area ----
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = navBarPadding.calculateBottomPadding())
            ) {
                when {
                    // Initial state: show help hint
                    !searchState.hasSearched && query.isBlank() -> {
                        SearchHintState()
                    }
                    // Loading state
                    searchState.isSearching -> {
                        SearchLoadingState()
                    }
                    // No results
                    searchState.hasSearched && searchState.sections.isEmpty() -> {
                        SearchEmptyState(query = searchState.query)
                    }
                    // Results
                    else -> {
                        SearchResultsList(
                            sections = searchState.sections,
                            onAppClick = onAppClick,
                            onMessageClick = onMessageClick,
                            onReminderClick = onReminderClick,
                            onFileClick = onFileClick,
                            onCommandClick = onCommandClick
                        )
                    }
                }
            }
        }
    }
}

// ======================================================================================
// Top bar
// ======================================================================================

/**
 * Compact top bar with a back/close button and "universal search" title,
 * styled as a terminal window title bar.
 */
@Composable
private fun SearchTopBar(onDismiss: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Close search",
                tint = TerminalColors.Command
            )
        }

        Text(
            text = "universal-search",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "ESC to close",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Timestamp
            )
        )

        Spacer(modifier = Modifier.width(12.dp))
    }
}

// ======================================================================================
// Search input bar
// ======================================================================================

/**
 * Terminal-styled search input bar that resembles a `grep` command.
 * Displays: `$ grep -ri "query" /system/*` with the user's input replacing "query".
 * Auto-focuses when first composed.
 */
@Composable
private fun SearchInputBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    // Auto-focus the search field when the overlay opens
    LaunchedEffect(Unit) {
        delay(100) // Small delay for animation to start
        focusRequester.requestFocus()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        // Prompt prefix
        Text(
            text = "$ grep -ri \"",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = TerminalColors.Prompt
            )
        )

        // Input field
        BasicTextField(
            value = query,
            onValueChange = onQueryChanged,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = TerminalColors.Command
            ),
            cursorBrush = SolidColor(TerminalColors.Cursor),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        // Blinking cursor placeholder
                        BlinkingPlaceholder()
                    }
                    innerTextField()
                }
            }
        )

        // Suffix
        Text(
            text = "\" /system/*",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = TerminalColors.Prompt
            )
        )

        // Clear button
        if (query.isNotEmpty()) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear search",
                    tint = TerminalColors.Timestamp,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * A blinking underscore cursor animation for the empty search field placeholder.
 * Mimics a real terminal cursor blinking at ~1Hz.
 */
@Composable
private fun BlinkingPlaceholder() {
    val infiniteTransition = rememberInfiniteTransition(label = "cursorBlink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 530, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    Text(
        text = "_",
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TerminalColors.Cursor
        ),
        modifier = Modifier.alpha(alpha)
    )
}

// ======================================================================================
// State composables (hint, loading, empty)
// ======================================================================================

/**
 * Initial hint state shown before the user has typed anything.
 * Displays a brief guide on what can be searched.
 */
@Composable
private fun SearchHintState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "# search across your device",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        val hints = listOf(
            SearchCategory.APPS.path to "Installed applications",
            SearchCategory.MESSAGES.path to "WhatsApp & Teams messages",
            SearchCategory.REMINDERS.path to "Scheduled reminders",
            SearchCategory.FILES.path to "Downloads, documents, media",
            SearchCategory.COMMANDS.path to "Built-in launcher commands"
        )

        hints.forEach { (path, description) ->
            Row(
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = path,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalColors.Prompt
                    ),
                    modifier = Modifier.width(120.dp)
                )
                Text(
                    text = "-- $description",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalColors.Timestamp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "$ type to begin searching...",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Subtext
            )
        )
    }
}

/**
 * Loading state with a spinning indicator and a terminal-style "searching..." label.
 */
@Composable
private fun SearchLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = TerminalColors.Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "$ searching...",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }
    }
}

/**
 * No-results state styled as a grep failure message.
 * Shows: `grep: no matches for "query" in /system/*`
 */
@Composable
private fun SearchEmptyState(query: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = "$ grep -ri \"$query\" /system/*",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Prompt
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "grep: no matches for \"$query\" in /system/*",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Error
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Try a different query or check your spelling.",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }
    }
}

// ======================================================================================
// Results list
// ======================================================================================

/**
 * The main results list, a [LazyColumn] rendering categorized sections.
 * Each section has a terminal-style path header and up to 5 result rows.
 */
@Composable
private fun SearchResultsList(
    sections: List<SearchResultSection>,
    onAppClick: (packageName: String) -> Unit,
    onMessageClick: (sender: String, groupName: String?) -> Unit,
    onReminderClick: (reminderId: Long) -> Unit,
    onFileClick: (filePath: String) -> Unit,
    onCommandClick: (command: String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 4.dp,
            bottom = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        sections.forEach { section ->
            // Section header
            item(key = "header_${section.category.name}") {
                SectionPathHeader(
                    path = section.categoryPath,
                    resultCount = section.totalCount
                )
            }

            // Result rows
            items(
                items = section.results,
                key = { it.id }
            ) { result ->
                SearchResultRow(
                    result = result,
                    onAppClick = onAppClick,
                    onMessageClick = onMessageClick,
                    onReminderClick = onReminderClick,
                    onFileClick = onFileClick,
                    onCommandClick = onCommandClick
                )
            }

            // "show all (N)" link if there are more results than shown
            if (section.totalCount > section.results.size) {
                item(key = "more_${section.category.name}") {
                    ShowAllLink(
                        category = section.category,
                        totalCount = section.totalCount
                    )
                }
            }

            // Spacing between sections
            item(key = "spacer_${section.category.name}") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// ======================================================================================
// Section header
// ======================================================================================

/**
 * Terminal-style section header displaying the category as a filesystem path.
 * Format: `--- /usr/bin/ (3 results) ---`
 */
@Composable
private fun SectionPathHeader(
    path: String,
    resultCount: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        // Left divider
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(1.dp)
                .background(TerminalColors.Selection)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Path text
        Text(
            text = path,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Prompt
            )
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Result count
        Text(
            text = "($resultCount ${if (resultCount == 1) "result" else "results"})",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Timestamp
            )
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Right divider
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(TerminalColors.Selection)
        )
    }
}

// ======================================================================================
// Result row
// ======================================================================================

/**
 * A single search result row. Dispatches the click event to the appropriate
 * callback based on the result type. Displays: icon + title + subtitle + category badge.
 */
@Composable
private fun SearchResultRow(
    result: SearchResult,
    onAppClick: (packageName: String) -> Unit,
    onMessageClick: (sender: String, groupName: String?) -> Unit,
    onReminderClick: (reminderId: Long) -> Unit,
    onFileClick: (filePath: String) -> Unit,
    onCommandClick: (command: String) -> Unit
) {
    val onClick: () -> Unit = when (result) {
        is SearchResult.AppResult -> ({ onAppClick(result.packageName) })
        is SearchResult.MessageResult -> ({ onMessageClick(result.sender, result.groupName) })
        is SearchResult.ReminderResult -> ({ onReminderClick(result.reminderId) })
        is SearchResult.FileResult -> ({ onFileClick(result.filePath) })
        is SearchResult.CommandResult -> ({ onCommandClick(result.command) })
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Category icon
        ResultIcon(result = result)

        Spacer(modifier = Modifier.width(12.dp))

        // Title + subtitle
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = result.title,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TerminalColors.Command
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = result.subtitle,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Timestamp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Source badge
        CategoryBadge(category = result.category)
    }
}

// ======================================================================================
// Result icon
// ======================================================================================

/**
 * Renders the appropriate icon for a search result based on its type.
 * - Apps: actual app icon drawable, or Android fallback
 * - Messages: chat bubble icon
 * - Reminders: notification/clock icon
 * - Files: file type icon based on extension
 * - Commands: terminal icon
 */
@Composable
private fun ResultIcon(result: SearchResult) {
    when (result) {
        is SearchResult.AppResult -> {
            AppResultIcon(icon = result.icon)
        }
        is SearchResult.MessageResult -> {
            IconBox(
                icon = Icons.Default.ChatBubble,
                tint = TerminalColors.Success,
                backgroundColor = TerminalColors.Success.copy(alpha = 0.12f)
            )
        }
        is SearchResult.ReminderResult -> {
            IconBox(
                icon = if (result.isCompleted) Icons.Default.Notifications else Icons.Default.AccessTime,
                tint = TerminalColors.Warning,
                backgroundColor = TerminalColors.Warning.copy(alpha = 0.12f)
            )
        }
        is SearchResult.FileResult -> {
            val fileIcon = getFileIcon(result.filePath)
            IconBox(
                icon = fileIcon,
                tint = TerminalColors.Info,
                backgroundColor = TerminalColors.Info.copy(alpha = 0.12f)
            )
        }
        is SearchResult.CommandResult -> {
            IconBox(
                icon = Icons.Default.Terminal,
                tint = TerminalColors.Accent,
                backgroundColor = TerminalColors.Accent.copy(alpha = 0.12f)
            )
        }
    }
}

/**
 * Renders an app icon from its [Drawable], or a fallback Android icon.
 */
@Composable
private fun AppResultIcon(icon: Drawable?) {
    if (icon != null) {
        val bitmap = remember(icon) {
            try {
                icon.toBitmap(96, 96).asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            return
        }
    }

    // Fallback
    IconBox(
        icon = Icons.Default.Android,
        tint = TerminalColors.Prompt,
        backgroundColor = TerminalColors.Prompt.copy(alpha = 0.12f)
    )
}

/**
 * A square icon container with a tinted background and centered icon.
 */
@Composable
private fun IconBox(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    backgroundColor: androidx.compose.ui.graphics.Color
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Returns an appropriate Material icon for a file based on its extension.
 */
private fun getFileIcon(filePath: String): ImageVector {
    val ext = filePath.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp" -> Icons.Default.Image
        "mp4", "mkv", "avi", "mov", "webm" -> Icons.Default.VideoFile
        "mp3", "wav", "flac", "aac", "ogg" -> Icons.Default.MusicNote
        "pdf", "doc", "docx", "txt", "rtf" -> Icons.Default.Description
        "zip", "rar", "7z", "tar", "gz" -> Icons.Default.AttachFile
        "apk" -> Icons.Default.Android
        else -> Icons.Default.InsertDriveFile
    }
}

// ======================================================================================
// Category badge
// ======================================================================================

/**
 * Small colored badge showing the search result category.
 * Styled as a compact rounded chip with monospace text.
 */
@Composable
private fun CategoryBadge(category: SearchCategory) {
    val (badgeColor, badgeText) = when (category) {
        SearchCategory.APPS -> TerminalColors.Prompt to "app"
        SearchCategory.MESSAGES -> TerminalColors.Success to "msg"
        SearchCategory.REMINDERS -> TerminalColors.Warning to "cron"
        SearchCategory.FILES -> TerminalColors.Info to "file"
        SearchCategory.COMMANDS -> TerminalColors.Accent to "cmd"
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(badgeColor.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = badgeText,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )
        )
    }
}

// ======================================================================================
// "Show all" link
// ======================================================================================

/**
 * "show all (N)" link shown when a category has more results than the preview limit.
 * Styled as a terminal-command-style clickable text.
 */
@Composable
private fun ShowAllLink(
    category: SearchCategory,
    totalCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Text(
            text = "$ ls ${category.path} --all  # show all ($totalCount)",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Accent
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { /* TODO: Expand to show all results for this category */ }
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}
