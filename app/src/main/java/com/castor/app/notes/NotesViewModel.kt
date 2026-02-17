package com.castor.app.notes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.core.data.repository.Note
import com.castor.core.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Notes list screen (scratchpad).
 *
 * Exposes a reactive list of notes that updates whenever the underlying
 * Room table changes. Supports search filtering, sorting, creation,
 * deletion, duplication, pin toggling, and color updates.
 */
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    companion object {
        private const val TAG = "NotesViewModel"
    }

    // -------------------------------------------------------------------------------------
    // Search state
    // -------------------------------------------------------------------------------------

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    // -------------------------------------------------------------------------------------
    // Sort state
    // -------------------------------------------------------------------------------------

    private val _sortMode = MutableStateFlow(NoteSortMode.MODIFIED)
    val sortMode: StateFlow<NoteSortMode> = _sortMode.asStateFlow()

    // -------------------------------------------------------------------------------------
    // Tag filter
    // -------------------------------------------------------------------------------------

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    // -------------------------------------------------------------------------------------
    // Notes list (reactive, filtered by search query, sorted by mode)
    // -------------------------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawNotes: StateFlow<List<Note>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                noteRepository.getAllNotes()
            } else {
                noteRepository.searchNotes(query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * All notes after applying search, optional tag filter, and sort.
     */
    val notes: StateFlow<List<Note>> = combine(
        rawNotes,
        _sortMode,
        _selectedTag
    ) { notesList, sort, tag ->
        val filtered = if (tag != null) {
            notesList.filter { note -> tag in note.tags }
        } else {
            notesList
        }

        when (sort) {
            NoteSortMode.MODIFIED -> filtered.sortedWith(
                compareByDescending<Note> { it.isPinned }.thenByDescending { it.updatedAt }
            )
            NoteSortMode.CREATED -> filtered.sortedWith(
                compareByDescending<Note> { it.isPinned }.thenByDescending { it.createdAt }
            )
            NoteSortMode.NAME -> filtered.sortedWith(
                compareByDescending<Note> { it.isPinned }.thenBy { it.title.lowercase() }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    /**
     * Pinned notes subset, derived from the sorted notes flow.
     */
    val pinnedNotes: StateFlow<List<Note>> = notes
        .map { notesList -> notesList.filter { it.isPinned } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * Unpinned notes subset, derived from the sorted notes flow.
     */
    val unpinnedNotes: StateFlow<List<Note>> = notes
        .map { notesList -> notesList.filter { !it.isPinned } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // -------------------------------------------------------------------------------------
    // UI feedback
    // -------------------------------------------------------------------------------------

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // -------------------------------------------------------------------------------------
    // Search actions
    // -------------------------------------------------------------------------------------

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSearch() {
        _isSearchActive.value = !_isSearchActive.value
        if (!_isSearchActive.value) {
            _searchQuery.value = ""
        }
    }

    fun dismissSearch() {
        _isSearchActive.value = false
        _searchQuery.value = ""
    }

    // -------------------------------------------------------------------------------------
    // Sort actions
    // -------------------------------------------------------------------------------------

    fun setSortMode(mode: NoteSortMode) {
        _sortMode.value = mode
    }

    // -------------------------------------------------------------------------------------
    // Tag filter actions
    // -------------------------------------------------------------------------------------

    fun setTagFilter(tag: String?) {
        _selectedTag.value = tag
    }

    // -------------------------------------------------------------------------------------
    // Note CRUD actions
    // -------------------------------------------------------------------------------------

    /**
     * Creates a new blank note and returns its ID for immediate navigation
     * to the editor.
     */
    suspend fun createNote(): Long {
        return try {
            val now = System.currentTimeMillis()
            val note = Note(
                title = "",
                content = "",
                createdAt = now,
                updatedAt = now
            )
            val id = noteRepository.insertNote(note)
            Log.i(TAG, "Created new note with id=$id")
            id
        } catch (e: Exception) {
            Log.e(TAG, "Error creating note", e)
            _errorMessage.value = "Failed to create note."
            -1L
        }
    }

    /**
     * Permanently deletes a note by its database ID.
     */
    fun deleteNote(id: Long) {
        viewModelScope.launch {
            try {
                noteRepository.deleteNote(id)
                _successMessage.value = "Note deleted"
                Log.i(TAG, "Deleted note id=$id")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting note $id", e)
                _errorMessage.value = "Failed to delete note."
            }
        }
    }

    /**
     * Toggles the pinned state of a note.
     */
    fun togglePin(id: Long) {
        viewModelScope.launch {
            try {
                val allNotes = notes.value
                val note = allNotes.find { it.id == id } ?: return@launch
                noteRepository.updateNote(
                    note.copy(
                        isPinned = !note.isPinned,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                Log.i(TAG, "Toggled pin for note id=$id, now pinned=${!note.isPinned}")
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling pin for note $id", e)
                _errorMessage.value = "Failed to update note."
            }
        }
    }

    /**
     * Updates the accent color tag for a note.
     */
    fun updateNoteColor(id: Long, color: String) {
        viewModelScope.launch {
            try {
                val allNotes = notes.value
                val note = allNotes.find { it.id == id } ?: return@launch
                noteRepository.updateNote(
                    note.copy(
                        color = color,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error updating color for note $id", e)
                _errorMessage.value = "Failed to update note color."
            }
        }
    }

    /**
     * Duplicates an existing note with a " (copy)" title suffix.
     * The duplicate is unpinned and gets a fresh timestamp.
     */
    fun duplicateNote(id: Long) {
        viewModelScope.launch {
            try {
                val allNotes = notes.value
                val note = allNotes.find { it.id == id } ?: return@launch
                val now = System.currentTimeMillis()
                val copy = note.copy(
                    id = 0,
                    title = "${note.title} (copy)",
                    createdAt = now,
                    updatedAt = now,
                    isPinned = false
                )
                val newId = noteRepository.insertNote(copy)
                _successMessage.value = "Note duplicated"
                Log.i(TAG, "Duplicated note id=$id -> newId=$newId")
            } catch (e: Exception) {
                Log.e(TAG, "Error duplicating note $id", e)
                _errorMessage.value = "Failed to duplicate note."
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // UI state helpers
    // -------------------------------------------------------------------------------------

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun clearSuccessMessage() {
        _successMessage.value = null
    }
}
