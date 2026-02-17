package com.castor.core.data.repository

import com.castor.core.data.db.dao.NoteDao
import com.castor.core.data.db.entity.NoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain model representing a single note / scratchpad entry.
 *
 * This is the model that UI and feature layers work with. It is decoupled from
 * the Room [NoteEntity] so that the persistence layer can evolve independently.
 */
data class Note(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val color: String = "default",
    val tags: List<String> = emptyList()
)

/**
 * Repository wrapping [NoteDao] and exposing domain-level [Note] objects.
 *
 * All write operations are suspend functions; all read operations return observable
 * [Flow]s that emit whenever the underlying table changes.
 */
@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao
) {

    // -------------------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------------------

    fun getAllNotes(): Flow<List<Note>> =
        noteDao.getAllNotes().map { entities -> entities.map { it.toDomain() } }

    fun searchNotes(query: String): Flow<List<Note>> =
        noteDao.searchNotes(query).map { entities -> entities.map { it.toDomain() } }

    fun getNoteById(id: Long): Flow<Note?> =
        noteDao.getNoteById(id).map { it?.toDomain() }

    fun getPinnedNotes(): Flow<List<Note>> =
        noteDao.getPinnedNotes().map { entities -> entities.map { it.toDomain() } }

    fun getNotesByTag(tag: String): Flow<List<Note>> =
        noteDao.getNotesByTag(tag).map { entities -> entities.map { it.toDomain() } }

    // -------------------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------------------

    suspend fun insertNote(note: Note): Long =
        noteDao.insertNote(note.toEntity())

    suspend fun updateNote(note: Note) =
        noteDao.updateNote(note.toEntity())

    suspend fun deleteNote(id: Long) =
        noteDao.deleteNote(id)

    suspend fun deleteAllNotes() =
        noteDao.deleteAllNotes()

    // -------------------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------------------

    private fun NoteEntity.toDomain(): Note = Note(
        id = id,
        title = title,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isPinned = isPinned,
        color = color,
        tags = if (tags.isBlank()) emptyList() else tags.split(",").map { it.trim() }
    )

    private fun Note.toEntity(): NoteEntity = NoteEntity(
        id = id,
        title = title,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isPinned = isPinned,
        color = color.ifBlank { "default" },
        tags = tags.joinToString(",")
    )
}
