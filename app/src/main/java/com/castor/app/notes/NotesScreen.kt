package com.castor.app.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.core.data.repository.Note
import com.castor.core.ui.theme.TerminalColors
import kotlinx.coroutines.launch

// -------------------------------------------------------------------------------------
// Color palette options for note accent tags
// -------------------------------------------------------------------------------------

private val noteColorOptions: List<Pair<String, @Composable () -> Color>>
    @Composable get() = listOf(
        "red" to { TerminalColors.Error },
        "orange" to { TerminalColors.Warning },
        "yellow" to { TerminalColors.Cursor },
        "green" to { TerminalColors.Success },
        "blue" to { TerminalColors.Info },
        "default" to { TerminalColors.Accent },
    )

/**
 * Full notes list screen styled as `$ ls ~/notes/`.
 *
 * Displays all notes in a staggered 2-column grid with pinned/unpinned sections,
 * search filtering, sort dropdown, swipe-to-delete, and long-press context menus.
 * Uses the terminal/vim aesthetic consistent with the Castor launcher.
 *
 * @param onBack Navigate back to the home screen
 * @param onOpenNote Navigate to the note editor for a specific note ID
 * @param viewModel Hilt-injected ViewModel for the notes list
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NotesScreen(
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    viewModel: NotesViewModel = hiltViewModel()
) {
    val pinnedNotes by viewModel.pinnedNotes.collectAsState()
    val unpinnedNotes by viewModel.unpinnedNotes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current

    // Delete confirmation dialog state
    var noteToDelete by remember { mutableStateOf<Note?>(null) }

    // Color picker dialog state
    var noteForColorPicker by remember { mutableStateOf<Note?>(null) }

    // Sort dropdown state
    var showSortMenu by remember { mutableStateOf(false) }

    // Observe error / success messages
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        containerColor = TerminalColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        val id = viewModel.createNote()
                        if (id != -1L) {
                            onOpenNote(id)
                        }
                    }
                },
                containerColor = TerminalColors.Surface,
                contentColor = TerminalColors.Accent,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New note")
                    Text(
                        text = "touch",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = TerminalColors.Accent
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
        ) {
            // ==================================================================
            // Top bar
            // ==================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalColors.StatusBar)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TerminalColors.Command
                    )
                }

                Text(
                    text = "# scratchpad",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Accent
                    ),
                    modifier = Modifier.weight(1f)
                )

                // Sort dropdown toggle
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
                            tint = TerminalColors.Command
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        containerColor = TerminalColors.Surface
                    ) {
                        NoteSortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = mode.flag,
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            fontWeight = if (mode == sortMode) FontWeight.Bold else FontWeight.Normal,
                                            color = if (mode == sortMode) TerminalColors.Accent else TerminalColors.Command
                                        )
                                    )
                                },
                                onClick = {
                                    viewModel.setSortMode(mode)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = { viewModel.toggleSearch() }) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (isSearchActive) "Close search" else "Search",
                        tint = TerminalColors.Command
                    )
                }
            }

            // ==================================================================
            // Search bar (animated visibility)
            // ==================================================================
            AnimatedVisibility(
                visible = isSearchActive,
                enter = slideInVertically(tween(200)) { -it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(200))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TerminalColors.Surface)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$ grep -i \"",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = TerminalColors.Prompt
                        )
                    )

                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = {
                            Text(
                                text = "query",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = TerminalColors.Subtext
                                )
                            )
                        },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = TerminalColors.Command
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = TerminalColors.Cursor,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "\" ~/notes/*",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = TerminalColors.Prompt
                        )
                    )
                }
            }

            // ==================================================================
            // Notes staggered grid
            // ==================================================================
            if (pinnedNotes.isEmpty() && unpinnedNotes.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$ ls ~/notes/",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = TerminalColors.Prompt
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "(empty directory)",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = TerminalColors.Timestamp
                            )
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "$ touch new-note.md",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TerminalColors.Subtext
                            )
                        )
                        Text(
                            text = "tap + to create your first note",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TerminalColors.Subtext
                            )
                        )
                    }
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = 88.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // ---- Pinned section ----
                    if (pinnedNotes.isNotEmpty()) {
                        item(
                            key = "header_pinned",
                            span = StaggeredGridItemSpan.FullLine
                        ) {
                            NoteSectionHeader(title = "pinned")
                        }

                        items(
                            items = pinnedNotes,
                            key = { it.id }
                        ) { note ->
                            NoteCard(
                                note = note,
                                onClick = { onOpenNote(note.id) },
                                onLongClick = { },
                                onDelete = { noteToDelete = note },
                                onTogglePin = { viewModel.togglePin(note.id) },
                                onChangeColor = { noteForColorPicker = note },
                                onCopy = {
                                    val text = buildString {
                                        if (note.title.isNotBlank()) appendLine(note.title)
                                        append(note.content)
                                    }
                                    clipboardManager.setText(AnnotatedString(text))
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Copied to clipboard")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // ---- Recent section ----
                    if (unpinnedNotes.isNotEmpty()) {
                        item(
                            key = "header_recent",
                            span = StaggeredGridItemSpan.FullLine
                        ) {
                            NoteSectionHeader(title = "recent")
                        }

                        items(
                            items = unpinnedNotes,
                            key = { it.id }
                        ) { note ->
                            NoteCard(
                                note = note,
                                onClick = { onOpenNote(note.id) },
                                onLongClick = { },
                                onDelete = { noteToDelete = note },
                                onTogglePin = { viewModel.togglePin(note.id) },
                                onChangeColor = { noteForColorPicker = note },
                                onCopy = {
                                    val text = buildString {
                                        if (note.title.isNotBlank()) appendLine(note.title)
                                        append(note.content)
                                    }
                                    clipboardManager.setText(AnnotatedString(text))
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Copied to clipboard")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    // ==================================================================
    // Delete confirmation dialog
    // ==================================================================
    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            containerColor = TerminalColors.Surface,
            title = {
                Text(
                    text = "$ rm ~/notes/${note.title.take(20).ifBlank { "untitled" }}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Error
                    )
                )
            },
            text = {
                Text(
                    text = "This action cannot be undone. Delete this note permanently?",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalColors.Command
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteNote(note.id)
                    noteToDelete = null
                }) {
                    Text(
                        text = "rm -f",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Error
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text(
                        text = "cancel",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = TerminalColors.Timestamp
                        )
                    )
                }
            }
        )
    }

    // ==================================================================
    // Color picker dialog
    // ==================================================================
    noteForColorPicker?.let { note ->
        AlertDialog(
            onDismissRequest = { noteForColorPicker = null },
            containerColor = TerminalColors.Surface,
            title = {
                Text(
                    text = "$ chattr --color",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Accent
                    )
                )
            },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    // "None" option
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(TerminalColors.Subtext)
                            .combinedClickable(
                                onClick = {
                                    viewModel.updateNoteColor(note.id, "default")
                                    noteForColorPicker = null
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "x",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TerminalColors.Background
                            )
                        )
                    }

                    NoteColor.entries.filter { it != NoteColor.DEFAULT }.forEach { noteColor ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(noteColor.composeColor)
                                .combinedClickable(
                                    onClick = {
                                        viewModel.updateNoteColor(note.id, noteColor.key)
                                        noteForColorPicker = null
                                    }
                                )
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }
}

// ============================================================================
// Section header
// ============================================================================

@Composable
private fun NoteSectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Text(
            text = "# ",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )
        Text(
            text = title,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Timestamp
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(TerminalColors.Surface)
        )
    }
}

// ============================================================================
// Note card with context menu (for staggered grid)
// ============================================================================

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onChangeColor: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showContextMenu by remember { mutableStateOf(false) }

    val accentColor = NoteColor.fromKey(note.color).let { noteColor ->
        if (noteColor != NoteColor.DEFAULT) noteColor.composeColor else null
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(TerminalColors.Surface)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showContextMenu = true }
                )
                .height(IntrinsicSize.Min)
        ) {
            // Optional left accent border
            if (accentColor != null) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxSize()
                        .background(accentColor)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                // Title row with pin indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = note.title.ifBlank { "# untitled" },
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (note.title.isBlank()) TerminalColors.Subtext else TerminalColors.Command
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (note.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = TerminalColors.Warning,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(start = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Content preview (3 lines max)
                if (note.content.isNotBlank()) {
                    Text(
                        text = note.content,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TerminalColors.Timestamp
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Tags row
                if (note.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        note.tags.take(3).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TerminalColors.Selection)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = tag,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = accentColor ?: TerminalColors.Info
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                        if (note.tags.size > 3) {
                            Text(
                                text = "+${note.tags.size - 3}",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = TerminalColors.Subtext
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Timestamp
                Text(
                    text = formatRelativeTime(note.updatedAt),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Subtext
                    )
                )
            }
        }

        // Context menu
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = DpOffset(8.dp, 0.dp),
            containerColor = TerminalColors.Surface
        ) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            tint = TerminalColors.Warning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (note.isPinned) "$ unpin" else "$ pin",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = TerminalColors.Command
                            )
                        )
                    }
                },
                onClick = {
                    onTogglePin()
                    showContextMenu = false
                }
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = TerminalColors.Accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$ chattr --color",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = TerminalColors.Command
                            )
                        )
                    }
                },
                onClick = {
                    onChangeColor()
                    showContextMenu = false
                }
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = TerminalColors.Info,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$ xclip -sel clip",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = TerminalColors.Command
                            )
                        )
                    }
                },
                onClick = {
                    onCopy()
                    showContextMenu = false
                }
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = TerminalColors.Error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$ rm -f",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = TerminalColors.Error
                            )
                        )
                    }
                },
                onClick = {
                    onDelete()
                    showContextMenu = false
                }
            )
        }
    }
}

// ============================================================================
// Relative time formatting
// ============================================================================

private fun formatRelativeTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMs

    return when {
        diff < 60_000 -> "modified just now"
        diff < 3_600_000 -> "modified ${diff / 60_000}m ago"
        diff < 86_400_000 -> "modified ${diff / 3_600_000}h ago"
        diff < 604_800_000 -> "modified ${diff / 86_400_000}d ago"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            "modified ${sdf.format(java.util.Date(timestampMs))}"
        }
    }
}
