package com.castor.app.desktop.filemanager

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.castor.core.ui.theme.TerminalColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enumeration of sort orders available in the file manager.
 */
enum class SortOrder {
    Name,
    Size,
    Date,
    Type
}

/**
 * Enumeration of view modes for the file listing.
 */
enum class ViewMode {
    Grid,
    List
}

/**
 * Data class representing a single file or directory entry.
 *
 * @param name Display name of the file/directory
 * @param path Absolute path to the file/directory
 * @param isDirectory Whether this entry is a directory
 * @param size File size in bytes (0 for directories)
 * @param lastModified Last modified timestamp in milliseconds
 * @param extension File extension (empty string for directories)
 */
private data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String
)

/**
 * Terminal-styled file manager screen for browsing the device filesystem.
 *
 * Runs inside a desktop window and provides:
 * - Navigation breadcrumb trail showing the current path
 * - Grid/list toggle for file viewing
 * - File type detection with appropriate icons (images, videos, audio, documents, APKs)
 * - Sort options (name, size, date, type)
 * - Search bar for filtering files
 * - File opening via Android Intent system
 *
 * The file manager uses `java.io.File` API for browsing and relies on
 * Android's `Environment.getExternalStorageDirectory()` as the starting directory.
 *
 * @param modifier Modifier for the file manager container
 */
@Composable
fun FileManagerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val startPath = remember {
        Environment.getExternalStorageDirectory().absolutePath
    }

    var currentPath by remember { mutableStateOf(startPath) }
    val files = remember { mutableStateListOf<FileEntry>() }
    var sortOrder by remember { mutableStateOf(SortOrder.Name) }
    var viewMode by remember { mutableStateOf(ViewMode.List) }
    var searchQuery by remember { mutableStateOf("") }
    var isSortMenuVisible by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(true) }

    // Load files when path or sort order changes
    LaunchedEffect(currentPath, sortOrder) {
        scope.launch {
            val loadedFiles = withContext(Dispatchers.IO) {
                loadFiles(currentPath, sortOrder)
            }
            if (loadedFiles != null) {
                files.clear()
                files.addAll(loadedFiles)
                hasPermission = true
            } else {
                hasPermission = false
            }
        }
    }

    val filteredFiles = remember(files.toList(), searchQuery) {
        if (searchQuery.isBlank()) {
            files.toList()
        } else {
            files.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
    ) {
        // ---- Toolbar ----
        FileManagerToolbar(
            currentPath = currentPath,
            startPath = startPath,
            viewMode = viewMode,
            searchQuery = searchQuery,
            isSortMenuVisible = isSortMenuVisible,
            onNavigateUp = {
                val parentPath = File(currentPath).parent
                if (parentPath != null) {
                    currentPath = parentPath
                }
            },
            onNavigateHome = { currentPath = startPath },
            onRefresh = {
                scope.launch {
                    val loadedFiles = withContext(Dispatchers.IO) {
                        loadFiles(currentPath, sortOrder)
                    }
                    if (loadedFiles != null) {
                        files.clear()
                        files.addAll(loadedFiles)
                    }
                }
            },
            onToggleViewMode = {
                viewMode = if (viewMode == ViewMode.Grid) ViewMode.List else ViewMode.Grid
            },
            onSearchQueryChange = { searchQuery = it },
            onToggleSortMenu = { isSortMenuVisible = !isSortMenuVisible },
            onDismissSortMenu = { isSortMenuVisible = false },
            onSortOrderChange = { order ->
                sortOrder = order
                isSortMenuVisible = false
            },
            sortOrder = sortOrder
        )

        // ---- Breadcrumb trail ----
        BreadcrumbTrail(
            currentPath = currentPath,
            onNavigateTo = { path -> currentPath = path }
        )

        // ---- Content area ----
        if (!hasPermission) {
            // Storage permission not granted state
            PermissionRequiredState()
        } else if (filteredFiles.isEmpty()) {
            // Empty directory
            EmptyDirectoryState(searchQuery = searchQuery)
        } else {
            when (viewMode) {
                ViewMode.Grid -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(
                            items = filteredFiles,
                            key = { it.path }
                        ) { file ->
                            FileGridItem(
                                file = file,
                                onClick = {
                                    if (file.isDirectory) {
                                        currentPath = file.path
                                    } else {
                                        openFile(context, file)
                                    }
                                }
                            )
                        }
                    }
                }

                ViewMode.List -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(
                            items = filteredFiles,
                            key = { it.path }
                        ) { file ->
                            FileListItem(
                                file = file,
                                onClick = {
                                    if (file.isDirectory) {
                                        currentPath = file.path
                                    } else {
                                        openFile(context, file)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // ---- Status bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalColors.StatusBar)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${filteredFiles.size} items",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TerminalColors.Timestamp
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "sort: ${sortOrder.name.lowercase()} | view: ${viewMode.name.lowercase()}",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TerminalColors.Subtext
                )
            )
        }
    }
}

/**
 * File manager toolbar with navigation, search, view mode toggle, and sort controls.
 */
@Composable
private fun FileManagerToolbar(
    currentPath: String,
    startPath: String,
    viewMode: ViewMode,
    searchQuery: String,
    isSortMenuVisible: Boolean,
    sortOrder: SortOrder,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onRefresh: () -> Unit,
    onToggleViewMode: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSortMenu: () -> Unit,
    onDismissSortMenu: () -> Unit,
    onSortOrderChange: (SortOrder) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalColors.Surface)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Back button
        IconButton(
            onClick = onNavigateUp,
            enabled = currentPath != "/",
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Navigate up",
                tint = TerminalColors.Command,
                modifier = Modifier.size(16.dp)
            )
        }

        // Home button
        IconButton(
            onClick = onNavigateHome,
            enabled = currentPath != startPath,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "Home",
                tint = TerminalColors.Accent,
                modifier = Modifier.size(16.dp)
            )
        }

        // Refresh button
        IconButton(
            onClick = onRefresh,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = TerminalColors.Command,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Search bar
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = "$ grep...",
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
                .weight(1f)
                .height(36.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // View mode toggle
        IconButton(
            onClick = onToggleViewMode,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = if (viewMode == ViewMode.Grid)
                    Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                contentDescription = "Toggle view mode",
                tint = TerminalColors.Command,
                modifier = Modifier.size(16.dp)
            )
        }

        // Sort button with dropdown
        Box {
            IconButton(
                onClick = onToggleSortMenu,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "Sort",
                    tint = TerminalColors.Command,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = isSortMenuVisible,
                onDismissRequest = onDismissSortMenu,
                modifier = Modifier.background(TerminalColors.Surface)
            ) {
                SortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "$ sort --${order.name.lowercase()}",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = if (order == sortOrder) FontWeight.Bold
                                    else FontWeight.Normal,
                                    color = if (order == sortOrder) TerminalColors.Accent
                                    else TerminalColors.Command
                                )
                            )
                        },
                        onClick = { onSortOrderChange(order) }
                    )
                }
            }
        }
    }
}

/**
 * Breadcrumb trail showing the current navigation path.
 * Each segment is clickable to navigate directly to that directory.
 *
 * @param currentPath The current absolute path
 * @param onNavigateTo Callback to navigate to a specific path
 */
@Composable
private fun BreadcrumbTrail(
    currentPath: String,
    onNavigateTo: (String) -> Unit
) {
    val segments = remember(currentPath) {
        val parts = currentPath.split("/").filter { it.isNotBlank() }
        val paths = mutableListOf<Pair<String, String>>()
        var accumulated = ""
        parts.forEach { part ->
            accumulated = "$accumulated/$part"
            paths.add(part to accumulated)
        }
        paths
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalColors.Background)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "/",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Accent
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(2.dp))
                .clickable { onNavigateTo("/") }
                .padding(horizontal = 2.dp)
        )

        segments.forEach { (name, path) ->
            Text(
                text = "/",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Subtext
                )
            )

            Text(
                text = name,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = if (path == currentPath) FontWeight.Bold else FontWeight.Normal,
                    color = if (path == currentPath) TerminalColors.Command
                    else TerminalColors.Accent
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .clickable { onNavigateTo(path) }
                    .padding(horizontal = 2.dp)
            )
        }
    }
}

/**
 * A single file/folder item rendered in grid view mode.
 *
 * @param file The file entry to display
 * @param onClick Callback when the item is clicked
 */
@Composable
private fun FileGridItem(
    file: FileEntry,
    onClick: () -> Unit
) {
    val (icon, tint) = getFileIconAndTint(file)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(32.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = file.name,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = if (file.isDirectory) FontWeight.Bold else FontWeight.Normal,
                color = TerminalColors.Command
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (!file.isDirectory) {
            Text(
                text = formatFileSize(file.size),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }
    }
}

/**
 * A single file/folder item rendered in list view mode.
 *
 * @param file The file entry to display
 * @param onClick Callback when the item is clicked
 */
@Composable
private fun FileListItem(
    file: FileEntry,
    onClick: () -> Unit
) {
    val (icon, tint) = getFileIconAndTint(file)
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Name
        Text(
            text = file.name,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = if (file.isDirectory) FontWeight.Bold else FontWeight.Normal,
                color = if (file.isDirectory) TerminalColors.Warning else TerminalColors.Command
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Size
        if (!file.isDirectory) {
            Text(
                text = formatFileSize(file.size),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TerminalColors.Timestamp
                ),
                modifier = Modifier.width(60.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(60.dp))
        }

        // Modified date
        Text(
            text = dateFormat.format(Date(file.lastModified)),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = TerminalColors.Subtext
            ),
            modifier = Modifier.width(110.dp)
        )
    }

    // Separator
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(TerminalColors.Surface.copy(alpha = 0.3f))
    )
}

/**
 * State shown when storage permission has not been granted.
 */
@Composable
private fun PermissionRequiredState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Permission required",
                tint = TerminalColors.Warning,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "permission denied: storage",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Error
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(TerminalColors.Accent.copy(alpha = 0.2f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "$ grant-permission",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Accent
                    )
                )
            }
        }
    }
}

/**
 * State shown when the current directory is empty.
 *
 * @param searchQuery Current search query (to differentiate "no results" from "empty directory")
 */
@Composable
private fun EmptyDirectoryState(searchQuery: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$ ls",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Accent
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (searchQuery.isBlank()) "directory is empty"
                else "no files matching '$searchQuery'",
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
 * Returns the appropriate icon and tint color for a file entry based on its type.
 *
 * @param file The file entry to get the icon for
 * @return A pair of (ImageVector, Color) for the icon and its tint
 */
private fun getFileIconAndTint(file: FileEntry): Pair<ImageVector, Color> {
    if (file.isDirectory) {
        return Icons.Default.Folder to TerminalColors.Warning
    }

    return when (file.extension.lowercase()) {
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg" ->
            Icons.Default.Image to TerminalColors.Info

        "mp4", "mkv", "avi", "mov", "webm", "flv" ->
            Icons.Default.VideoFile to TerminalColors.Error

        "mp3", "flac", "aac", "ogg", "wav", "m4a" ->
            Icons.Default.AudioFile to TerminalColors.Success

        "pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx" ->
            Icons.Default.Description to TerminalColors.Accent

        "apk" -> Icons.Default.Android to TerminalColors.Success

        else -> Icons.AutoMirrored.Filled.InsertDriveFile to TerminalColors.Subtext
    }
}

/**
 * Formats a file size in bytes to a human-readable string.
 *
 * @param sizeBytes File size in bytes
 * @return Formatted string (e.g., "1.2 MB", "340 KB")
 */
private fun formatFileSize(sizeBytes: Long): String {
    return when {
        sizeBytes < 1024 -> "$sizeBytes B"
        sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
        sizeBytes < 1024 * 1024 * 1024 -> "${String.format("%.1f", sizeBytes / (1024.0 * 1024.0))} MB"
        else -> "${String.format("%.2f", sizeBytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}

/**
 * Loads and sorts files from the specified directory path.
 *
 * @param path Absolute path of the directory to list
 * @param sortOrder The desired sort order for the results
 * @return Sorted list of [FileEntry] items, or null if the directory cannot be read
 */
private fun loadFiles(path: String, sortOrder: SortOrder): List<FileEntry>? {
    val directory = File(path)
    if (!directory.exists() || !directory.isDirectory || !directory.canRead()) {
        return null
    }

    val fileList = directory.listFiles()?.map { file ->
        FileEntry(
            name = file.name,
            path = file.absolutePath,
            isDirectory = file.isDirectory,
            size = if (file.isFile) file.length() else 0L,
            lastModified = file.lastModified(),
            extension = file.extension
        )
    } ?: return emptyList()

    // Directories first, then sort by the selected order
    val sorted = when (sortOrder) {
        SortOrder.Name -> fileList.sortedWith(
            compareByDescending<FileEntry> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
        SortOrder.Size -> fileList.sortedWith(
            compareByDescending<FileEntry> { it.isDirectory }.thenByDescending { it.size }
        )
        SortOrder.Date -> fileList.sortedWith(
            compareByDescending<FileEntry> { it.isDirectory }.thenByDescending { it.lastModified }
        )
        SortOrder.Type -> fileList.sortedWith(
            compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.extension.lowercase() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
    }

    return sorted
}

/**
 * Opens a file using Android's Intent system with ACTION_VIEW.
 *
 * @param context Android context for starting the activity
 * @param file The file entry to open
 */
private fun openFile(context: Context, file: FileEntry) {
    try {
        val javaFile = File(file.path)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            javaFile
        )
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        // Silently fail — in production this would show a toast
    }
}
