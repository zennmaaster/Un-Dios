package com.castor.app.notes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.core.ui.theme.TerminalColors
import kotlinx.coroutines.launch

/**
 * Full-screen note editor styled as a vim/nano terminal editor.
 *
 * Features:
 * - vim-style top bar: `~/notes/<title>.md` (editable filename)
 * - INSERT / PREVIEW mode indicator at the bottom
 * - Monospace title and content fields with line numbers (code editor style)
 * - Bottom toolbar with tag input, color picker, pin toggle, word/char count
 * - 2-second debounced auto-save after typing stops
 * - Markdown preview toggle: switch between edit and rendered view
 * - Keyboard-aware layout (content resizes with soft keyboard)
 *
 * @param onBack Navigate back to the notes list
 * @param viewModel Hilt-injected ViewModel for the note editor
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
    onBack: () -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel()
) {
    val title by viewModel.title.collectAsState()
    val content by viewModel.content.collectAsState()
    val isPinned by viewModel.isPinned.collectAsState()
    val color by viewModel.color.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val isLoaded by viewModel.isLoaded.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isPreviewMode by viewModel.isPreviewMode.collectAsState()

    val scope = rememberCoroutineScope()
    val contentFocusRequester = remember { FocusRequester() }

    // Tag input state
    var isTagInputVisible by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }

    // Delete confirmation
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Word and character count
    val charCount = content.length
    val wordCount = if (content.isBlank()) 0 else content.trim().split("\\s+".toRegex()).size

    // Derive display title for the top bar
    val displayFileName = title.ifBlank { "untitled" }
        .lowercase()
        .replace("\\s+".toRegex(), "-")
        .take(30)

    // Auto-save on back press
    BackHandler {
        scope.launch {
            if (viewModel.hasUnsavedChanges) {
                viewModel.saveNote()
            }
            onBack()
        }
    }

    // Line numbers for content
    val lines = if (content.isEmpty()) listOf("") else content.split("\n")
    val lineNumberText = lines.indices.joinToString("\n") { i ->
        (i + 1).toString().padStart(3)
    }

    if (!isLoaded) {
        // Loading state
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TerminalColors.Background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Loading...",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
            .statusBarsPadding()
            .imePadding()
    ) {
        // ==================================================================
        // Top bar: ~/notes/<filename>.md
        // ==================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalColors.StatusBar)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                scope.launch {
                    if (viewModel.hasUnsavedChanges) {
                        viewModel.saveNote()
                    }
                    onBack()
                }
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TerminalColors.Command
                )
            }

            Text(
                text = "~/notes/",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TerminalColors.Prompt
                )
            )
            Text(
                text = "${displayFileName}.md",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Command
                ),
                modifier = Modifier.weight(1f)
            )

            // Markdown preview toggle
            IconButton(onClick = { viewModel.togglePreview() }) {
                Icon(
                    imageVector = Icons.Default.Preview,
                    contentDescription = if (isPreviewMode) "Edit mode" else "Preview",
                    tint = if (isPreviewMode) TerminalColors.Accent else TerminalColors.Timestamp
                )
            }

            // Save button
            IconButton(onClick = {
                scope.launch { viewModel.saveNote() }
            }) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save",
                    tint = if (viewModel.hasUnsavedChanges) TerminalColors.Warning else TerminalColors.Timestamp
                )
            }

            // Delete button
            if (!viewModel.isNewNote) {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = TerminalColors.Error
                    )
                }
            }
        }

        // ==================================================================
        // Title field
        // ==================================================================
        BasicTextField(
            value = title,
            onValueChange = { viewModel.updateTitle(it) },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Command
            ),
            cursorBrush = SolidColor(TerminalColors.Cursor),
            singleLine = true,
            readOnly = isPreviewMode,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TerminalColors.Background)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (title.isEmpty()) {
                        Text(
                            text = "# untitled",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TerminalColors.Subtext
                            )
                        )
                    }
                    innerTextField()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TerminalColors.Surface)
        )

        // ==================================================================
        // Content area: edit mode with line numbers OR markdown preview
        // ==================================================================
        AnimatedContent(
            targetState = isPreviewMode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "editor_preview_toggle",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { showPreview ->
            if (showPreview) {
                // Markdown preview
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    MarkdownPreview(content = content)
                }
            } else {
                // Edit mode with line numbers
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Line numbers column
                    Text(
                        text = lineNumberText,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = TerminalColors.Subtext
                        ),
                        modifier = Modifier
                            .background(TerminalColors.StatusBar)
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    )

                    // Vertical divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(
                                maxOf(
                                    (lines.size * 20).dp + 24.dp,
                                    200.dp
                                )
                            )
                            .background(TerminalColors.Surface)
                    )

                    // Content text field
                    BasicTextField(
                        value = content,
                        onValueChange = { viewModel.updateContent(it) },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = TerminalColors.Command
                        ),
                        cursorBrush = SolidColor(TerminalColors.Cursor),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                if (content.isEmpty()) {
                                    Text(
                                        text = "Start typing...",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            lineHeight = 20.sp,
                                            color = TerminalColors.Subtext
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(contentFocusRequester)
                    )
                }
            }
        }

        // ==================================================================
        // Tags row (if any tags exist)
        // ==================================================================
        if (tags.isNotEmpty() || isTagInputVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalColors.StatusBar)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "tags:",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TerminalColors.Subtext
                    )
                )

                tags.forEach { tag ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(TerminalColors.Selection)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { viewModel.removeTag(tag) }
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tag,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TerminalColors.Info
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove tag",
                                tint = TerminalColors.Subtext,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable { viewModel.removeTag(tag) }
                            )
                        }
                    }
                }
            }
        }

        // ==================================================================
        // Bottom toolbar
        // ==================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalColors.StatusBar)
                .navigationBarsPadding()
        ) {
            // Tag input row (shown when tag input is active)
            if (isTagInputVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$ tag add \"",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TerminalColors.Prompt
                        )
                    )

                    TextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        placeholder = {
                            Text(
                                text = "name",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = TerminalColors.Subtext
                                )
                            )
                        },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
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
                        text = "\"",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TerminalColors.Prompt
                        )
                    )

                    TextButton(onClick = {
                        if (tagInput.isNotBlank()) {
                            viewModel.addTag(tagInput)
                            tagInput = ""
                        }
                        isTagInputVisible = false
                    }) {
                        Text(
                            text = "add",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TerminalColors.Success
                            )
                        )
                    }

                    TextButton(onClick = {
                        tagInput = ""
                        isTagInputVisible = false
                    }) {
                        Text(
                            text = "esc",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TerminalColors.Timestamp
                            )
                        )
                    }
                }
            }

            // Main toolbar row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tag add button
                IconButton(
                    onClick = { isTagInputVisible = !isTagInputVisible },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = "Add tag",
                        tint = if (isTagInputVisible) TerminalColors.Info else TerminalColors.Timestamp,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Color picker dots
                NoteColor.entries.forEach { noteColor ->
                    val dotColor = noteColor.composeColor
                    val isSelected = color == noteColor.key
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (isSelected) 18.dp else 14.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .clickable { viewModel.updateColor(noteColor.key) }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Pin toggle
                IconButton(
                    onClick = { viewModel.togglePin() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = if (isPinned) "Unpin" else "Pin",
                        tint = if (isPinned) TerminalColors.Warning else TerminalColors.Timestamp,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Word/char count
                Text(
                    text = "$wordCount words | $charCount chars",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TerminalColors.Timestamp
                    ),
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            // Mode indicator (like vim) -- INSERT or PREVIEW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalColors.Overlay)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPreviewMode) "-- PREVIEW --" else "-- INSERT --",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPreviewMode) TerminalColors.Accent else TerminalColors.Success
                    )
                )

                Spacer(modifier = Modifier.weight(1f))

                // Current line / total lines indicator
                val totalLines = lines.size
                Text(
                    text = "$totalLines ln",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TerminalColors.Timestamp
                    )
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Save status
                Text(
                    text = when {
                        isSaving -> "saving..."
                        viewModel.hasUnsavedChanges -> "unsaved"
                        else -> "saved"
                    },
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isSaving -> TerminalColors.Warning
                            viewModel.hasUnsavedChanges -> TerminalColors.Warning
                            else -> TerminalColors.Success
                        }
                    )
                )
            }
        }
    }

    // ==================================================================
    // Delete confirmation dialog
    // ==================================================================
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = TerminalColors.Surface,
            title = {
                Text(
                    text = "$ rm ~/notes/$displayFileName",
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
                    viewModel.deleteNote()
                    showDeleteDialog = false
                    onBack()
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
                TextButton(onClick = { showDeleteDialog = false }) {
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
}

// ============================================================================
// Basic Markdown preview renderer
// ============================================================================

/**
 * Renders basic Markdown as styled text: headers (#, ##, ###), **bold**,
 * *italic*, `inline code`, code blocks (```), and unordered lists (-, *).
 *
 * This is a lightweight renderer -- no external library dependencies.
 */
@Composable
private fun MarkdownPreview(content: String) {
    val markdownLines = content.split("\n")
    var inCodeBlock = false

    markdownLines.forEach { line ->
        when {
            // Toggle code block
            line.trimStart().startsWith("```") -> {
                inCodeBlock = !inCodeBlock
                if (inCodeBlock) {
                    Spacer(modifier = Modifier.height(4.dp))
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Inside a code block
            inCodeBlock -> {
                Text(
                    text = line,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = TerminalColors.Prompt,
                        background = TerminalColors.Surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TerminalColors.Surface)
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                )
            }

            // H1
            line.startsWith("# ") -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = line.removePrefix("# "),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Accent
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // H2
            line.startsWith("## ") -> {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = line.removePrefix("## "),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Info
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))
            }

            // H3
            line.startsWith("### ") -> {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = line.removePrefix("### "),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Warning
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Unordered list items
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                val indent = line.length - line.trimStart().length
                val bulletContent = line.trimStart().drop(2)
                Row(modifier = Modifier.padding(start = (indent * 8).dp)) {
                    Text(
                        text = " - ",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = TerminalColors.Accent
                        )
                    )
                    Text(
                        text = renderInlineMarkdown(bulletContent),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = TerminalColors.Command
                        )
                    )
                }
            }

            // Empty line
            line.isBlank() -> {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Regular paragraph with inline formatting
            else -> {
                Text(
                    text = renderInlineMarkdown(line),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = TerminalColors.Command
                    )
                )
            }
        }
    }
}

/**
 * Parses inline Markdown formatting: **bold**, *italic*, and `code`.
 * Returns an [AnnotatedString] with appropriate [SpanStyle]s applied.
 */
@Composable
private fun renderInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // **bold**
                i + 1 < text.length && text[i] == '*' && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TerminalColors.Command)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // *italic*
                text[i] == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = TerminalColors.Timestamp)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // `inline code`
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                color = TerminalColors.Prompt,
                                background = TerminalColors.Surface
                            )
                        ) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }

                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
