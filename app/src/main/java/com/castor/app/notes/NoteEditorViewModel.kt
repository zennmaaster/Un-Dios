package com.castor.app.notes

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.core.data.repository.Note
import com.castor.core.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the note editor screen (vim-styled full-screen editor).
 *
 * Loads an existing note by ID from [SavedStateHandle], or prepares a blank
 * note for creation when noteId == -1. Tracks unsaved changes so the UI can
 * display a save indicator, and auto-saves after a 2-second debounce whenever
 * the user stops typing.
 */
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "NoteEditorViewModel"
        private const val AUTO_SAVE_DELAY_MS = 2_000L
    }

    private val noteId: Long = savedStateHandle.get<Long>("noteId") ?: -1L

    /**
     * Tracks the actual persisted ID. For new notes this starts at 0 and is
     * assigned after the first insert. For existing notes it is the noteId.
     */
    private var _savedNoteId: Long = if (noteId == -1L) 0L else noteId

    val isNewNote: Boolean get() = _savedNoteId == 0L

    // -------------------------------------------------------------------------------------
    // Editable state
    // -------------------------------------------------------------------------------------

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _isPinned = MutableStateFlow(false)
    val isPinned: StateFlow<Boolean> = _isPinned.asStateFlow()

    private val _color = MutableStateFlow("default")
    val color: StateFlow<String> = _color.asStateFlow()

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    // -------------------------------------------------------------------------------------
    // Markdown preview toggle
    // -------------------------------------------------------------------------------------

    private val _isPreviewMode = MutableStateFlow(false)
    val isPreviewMode: StateFlow<Boolean> = _isPreviewMode.asStateFlow()

    fun togglePreview() {
        _isPreviewMode.value = !_isPreviewMode.value
    }

    // -------------------------------------------------------------------------------------
    // Internal tracking
    // -------------------------------------------------------------------------------------

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /** Snapshot of the note as loaded -- used to detect unsaved changes. */
    private var originalTitle = ""
    private var originalContent = ""
    private var originalIsPinned = false
    private var originalColor: String = "default"
    private var originalTags: List<String> = emptyList()

    /** The createdAt timestamp for existing notes; set on load. */
    private var createdAt: Long = System.currentTimeMillis()

    /** Auto-save debounce job. Cancelled and re-launched on each edit. */
    private var autoSaveJob: Job? = null

    init {
        if (noteId != -1L) {
            loadNote(noteId)
        } else {
            _isLoaded.value = true
        }
    }

    // -------------------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------------------

    private fun loadNote(id: Long) {
        viewModelScope.launch {
            try {
                val note = noteRepository.getNoteById(id).first()
                if (note != null) {
                    _title.value = note.title
                    _content.value = note.content
                    _isPinned.value = note.isPinned
                    _color.value = note.color
                    _tags.value = note.tags

                    originalTitle = note.title
                    originalContent = note.content
                    originalIsPinned = note.isPinned
                    originalColor = note.color
                    originalTags = note.tags
                    createdAt = note.createdAt
                }
                _isLoaded.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Error loading note id=$id", e)
                _isLoaded.value = true
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Editable field updates (each triggers auto-save debounce)
    // -------------------------------------------------------------------------------------

    fun updateTitle(value: String) {
        _title.value = value
        scheduleAutoSave()
    }

    fun updateContent(value: String) {
        _content.value = value
        scheduleAutoSave()
    }

    fun togglePin() {
        _isPinned.value = !_isPinned.value
        scheduleAutoSave()
    }

    fun updateColor(value: String) {
        _color.value = value
        scheduleAutoSave()
    }

    fun addTag(tag: String) {
        val trimmed = tag.trim().lowercase()
        if (trimmed.isNotEmpty() && trimmed !in _tags.value) {
            _tags.value = _tags.value + trimmed
            scheduleAutoSave()
        }
    }

    fun removeTag(tag: String) {
        _tags.value = _tags.value.filter { it != tag }
        scheduleAutoSave()
    }

    // -------------------------------------------------------------------------------------
    // Auto-save debounce
    // -------------------------------------------------------------------------------------

    /**
     * Cancels any pending auto-save and schedules a new one after
     * [AUTO_SAVE_DELAY_MS]. This ensures we only write to the database
     * once the user pauses typing for 2 seconds.
     */
    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DELAY_MS)
            if (hasUnsavedChanges) {
                saveNote()
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------------------

    /**
     * Whether any fields have been modified since the note was loaded.
     */
    val hasUnsavedChanges: Boolean
        get() = _title.value != originalTitle ||
                _content.value != originalContent ||
                _isPinned.value != originalIsPinned ||
                _color.value != originalColor ||
                _tags.value != originalTags

    /**
     * Persists the note to the database. Inserts a new note if [isNewNote] is true
     * and the note has meaningful content; otherwise updates the existing row.
     *
     * Returns the saved note's ID, or -1 if nothing was saved.
     */
    suspend fun saveNote(): Long {
        // Don't save completely empty new notes
        if (isNewNote && _title.value.isBlank() && _content.value.isBlank()) {
            return -1L
        }

        _isSaving.value = true
        return try {
            val now = System.currentTimeMillis()
            val note = Note(
                id = _savedNoteId,
                title = _title.value,
                content = _content.value,
                createdAt = if (isNewNote) now else createdAt,
                updatedAt = now,
                isPinned = _isPinned.value,
                color = _color.value,
                tags = _tags.value
            )

            val savedId = if (isNewNote) {
                val id = noteRepository.insertNote(note)
                _savedNoteId = id
                createdAt = now
                id
            } else {
                noteRepository.updateNote(note)
                _savedNoteId
            }

            // Update original snapshots after successful save
            originalTitle = _title.value
            originalContent = _content.value
            originalIsPinned = _isPinned.value
            originalColor = _color.value
            originalTags = _tags.value

            Log.i(TAG, "Saved note id=$savedId")
            savedId
        } catch (e: Exception) {
            Log.e(TAG, "Error saving note", e)
            -1L
        } finally {
            _isSaving.value = false
        }
    }

    /**
     * Permanently deletes the current note.
     */
    fun deleteNote() {
        if (_savedNoteId == 0L) return
        autoSaveJob?.cancel()
        viewModelScope.launch {
            try {
                noteRepository.deleteNote(_savedNoteId)
                Log.i(TAG, "Deleted note id=$_savedNoteId")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting note $_savedNoteId", e)
            }
        }
    }
}
