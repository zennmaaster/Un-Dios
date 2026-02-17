package com.castor.app.desktop.filemanager

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Data class representing a single file or directory item in the file manager.
 *
 * @param name Display name of the file or directory
 * @param path Absolute filesystem path
 * @param isDirectory Whether this item is a directory
 * @param size File size in bytes (0 for directories)
 * @param lastModified Last modified timestamp in milliseconds since epoch
 * @param mimeType MIME type of the file (empty for directories)
 */
data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val mimeType: String
)

/**
 * Enumeration of how files can be sorted in the file manager.
 */
enum class FileSortOrder {
    ByName,
    BySize,
    ByDate,
    ByType
}

/**
 * Enumeration of file listing display modes.
 */
enum class FileViewMode {
    Grid,
    List
}

/**
 * Immutable UI state for the file manager.
 *
 * @param currentPath The currently browsed directory path
 * @param files List of file items in the current directory
 * @param sortOrder Current sort order for the file listing
 * @param viewMode Current view mode (grid or list)
 * @param searchQuery Active search/filter query
 * @param isLoading Whether a file operation is in progress
 * @param error Error message, if any
 */
data class FileManagerUiState(
    val currentPath: String = Environment.getExternalStorageDirectory().absolutePath,
    val files: List<FileItem> = emptyList(),
    val sortOrder: FileSortOrder = FileSortOrder.ByName,
    val viewMode: FileViewMode = FileViewMode.List,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for the desktop file manager.
 *
 * Manages the file browsing state including navigation, sorting, searching,
 * and file operations. All file I/O is performed on [Dispatchers.IO] to
 * keep the UI thread free.
 *
 * The starting directory is the device's external storage root
 * (`Environment.getExternalStorageDirectory()`).
 *
 * File operations provided:
 * - [navigateTo]: Navigate to a specific directory path
 * - [navigateUp]: Navigate to the parent directory
 * - [refresh]: Reload the current directory listing
 * - [search]: Filter files by name
 * - [sortBy]: Change the sort order
 * - [setViewMode]: Switch between grid and list views
 * - [openFile]: Placeholder for opening a file
 * - [copyFile]: Placeholder for copying a file
 * - [deleteFile]: Delete a file or directory
 */
@HiltViewModel
class FileManagerViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(FileManagerUiState())

    /** Observable UI state for the file manager. */
    val state: StateFlow<FileManagerUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * Navigates to the specified directory path and loads its contents.
     *
     * @param path Absolute path of the directory to navigate to
     */
    fun navigateTo(path: String) {
        _state.update { it.copy(currentPath = path, searchQuery = "") }
        loadFiles()
    }

    /**
     * Navigates to the parent directory of the current path.
     * Does nothing if already at the filesystem root.
     */
    fun navigateUp() {
        val parentPath = File(_state.value.currentPath).parent
        if (parentPath != null) {
            navigateTo(parentPath)
        }
    }

    /**
     * Reloads the file listing for the current directory.
     */
    fun refresh() {
        loadFiles()
    }

    /**
     * Sets the search query to filter files by name.
     *
     * @param query The search string to filter by
     */
    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    /**
     * Changes the sort order and reloads the file listing.
     *
     * @param order The new sort order to apply
     */
    fun sortBy(order: FileSortOrder) {
        _state.update { it.copy(sortOrder = order) }
        loadFiles()
    }

    /**
     * Sets the view mode (grid or list).
     *
     * @param mode The new view mode
     */
    fun setViewMode(mode: FileViewMode) {
        _state.update { it.copy(viewMode = mode) }
    }

    /**
     * Placeholder for opening a file with an appropriate viewer/handler.
     * In production, this would use Android's Intent system.
     *
     * @param path Absolute path of the file to open
     */
    fun openFile(path: String) {
        // Placeholder — real implementation would launch an Intent
        // via a side-effect channel (SharedFlow or similar)
    }

    /**
     * Placeholder for copying a file to the clipboard or a destination.
     *
     * @param path Absolute path of the file to copy
     */
    fun copyFile(path: String) {
        // Placeholder — real implementation would use file copy operations
    }

    /**
     * Deletes a file or directory at the specified path.
     * Refreshes the directory listing after deletion.
     *
     * @param path Absolute path of the file or directory to delete
     */
    fun deleteFile(path: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val file = File(path)
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
            }
            loadFiles()
        }
    }

    /**
     * Internal function to load and sort files from the current directory.
     * Runs file I/O on [Dispatchers.IO] and updates the UI state.
     */
    private fun loadFiles() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                val currentState = _state.value
                val directory = File(currentState.currentPath)

                if (!directory.exists() || !directory.isDirectory) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "directory not found: ${currentState.currentPath}",
                            files = emptyList()
                        )
                    }
                    return@launch
                }

                val files = withContext(Dispatchers.IO) {
                    val rawFiles = directory.listFiles()?.map { file ->
                        FileItem(
                            name = file.name,
                            path = file.absolutePath,
                            isDirectory = file.isDirectory,
                            size = if (file.isFile) file.length() else 0L,
                            lastModified = file.lastModified(),
                            mimeType = if (file.isFile) {
                                guessMimeType(file.extension)
                            } else {
                                ""
                            }
                        )
                    } ?: emptyList()

                    sortFiles(rawFiles, currentState.sortOrder)
                }

                _state.update {
                    it.copy(
                        files = files,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: SecurityException) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "permission denied: ${e.message}",
                        files = emptyList()
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "error: ${e.message}",
                        files = emptyList()
                    )
                }
            }
        }
    }

    /**
     * Sorts a list of [FileItem] entries by the specified [FileSortOrder].
     * Directories are always sorted before files.
     *
     * @param files The unsorted list of file items
     * @param order The sort order to apply
     * @return A new sorted list
     */
    private fun sortFiles(files: List<FileItem>, order: FileSortOrder): List<FileItem> {
        return when (order) {
            FileSortOrder.ByName -> files.sortedWith(
                compareByDescending<FileItem> { it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )

            FileSortOrder.BySize -> files.sortedWith(
                compareByDescending<FileItem> { it.isDirectory }
                    .thenByDescending { it.size }
            )

            FileSortOrder.ByDate -> files.sortedWith(
                compareByDescending<FileItem> { it.isDirectory }
                    .thenByDescending { it.lastModified }
            )

            FileSortOrder.ByType -> files.sortedWith(
                compareByDescending<FileItem> { it.isDirectory }
                    .thenBy { it.mimeType }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
        }
    }

    /**
     * Guesses the MIME type of a file based on its extension.
     *
     * @param extension The file extension (without the dot)
     * @return Best-guess MIME type string
     */
    private fun guessMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "txt" -> "text/plain"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "json" -> "application/json"
            "xml" -> "application/xml"
            else -> "application/octet-stream"
        }
    }
}
