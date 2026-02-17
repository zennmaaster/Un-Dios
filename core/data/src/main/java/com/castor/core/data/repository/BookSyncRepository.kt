package com.castor.core.data.repository

import com.castor.core.data.db.dao.BookSyncDao
import com.castor.core.data.db.entity.BookSyncEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository layer for Kindle-Audible book sync data.
 *
 * Wraps [BookSyncDao] and provides a clean API for the feature layer.
 * All writes go through the encrypted Room database — no cloud.
 */
@Singleton
class BookSyncRepository @Inject constructor(
    private val bookSyncDao: BookSyncDao
) {

    // -----------------------------------------------------------------------------------------
    // Observe
    // -----------------------------------------------------------------------------------------

    /** Observe all synced books, ordered by most recently updated. */
    fun observeAllBooks(): Flow<List<BookSyncEntity>> = bookSyncDao.observeAll()

    /** Observe books where Kindle and Audible are out of sync. */
    fun observeOutOfSync(threshold: Float = 0.05f): Flow<List<BookSyncEntity>> =
        bookSyncDao.observeOutOfSync(threshold)

    /** Observe books that only appear on one platform (unmatched). */
    fun observeUnmatched(): Flow<List<BookSyncEntity>> = bookSyncDao.observeUnmatched()

    /** Observe a single book by its ID. */
    fun observeBook(bookId: String): Flow<BookSyncEntity?> = bookSyncDao.observeById(bookId)

    /** Observe the total number of tracked books. */
    fun observeBookCount(): Flow<Int> = bookSyncDao.observeCount()

    // -----------------------------------------------------------------------------------------
    // Read (suspend)
    // -----------------------------------------------------------------------------------------

    /** Get all synced books. */
    suspend fun getAllBooks(): List<BookSyncEntity> = bookSyncDao.getAll()

    /** Get a single book by ID. */
    suspend fun getBook(bookId: String): BookSyncEntity? = bookSyncDao.getById(bookId)

    /** Fuzzy-match a book by title and author fragments. */
    suspend fun findByFuzzyMatch(title: String, author: String): BookSyncEntity? {
        val titlePattern = "%${title.lowercase().trim()}%"
        val authorPattern = "%${author.lowercase().trim()}%"
        return bookSyncDao.findByFuzzyMatch(titlePattern, authorPattern)
    }

    // -----------------------------------------------------------------------------------------
    // Write
    // -----------------------------------------------------------------------------------------

    /** Insert or replace a book sync entry. */
    suspend fun upsertBook(book: BookSyncEntity) = bookSyncDao.upsert(book)

    /** Update Kindle progress for a specific book. */
    suspend fun updateKindleProgress(
        bookId: String,
        progress: Float,
        lastPage: Int? = null,
        totalPages: Int? = null,
        chapter: String? = null
    ) {
        bookSyncDao.updateKindleProgress(
            bookId = bookId,
            progress = progress,
            lastPage = lastPage,
            totalPages = totalPages,
            chapter = chapter
        )
    }

    /** Update Audible progress for a specific book. */
    suspend fun updateAudibleProgress(
        bookId: String,
        progress: Float,
        chapter: String? = null,
        positionMs: Long = 0L,
        totalMs: Long = 0L
    ) {
        bookSyncDao.updateAudibleProgress(
            bookId = bookId,
            progress = progress,
            chapter = chapter,
            positionMs = positionMs,
            totalMs = totalMs
        )
    }

    // -----------------------------------------------------------------------------------------
    // Delete
    // -----------------------------------------------------------------------------------------

    /** Remove a book from sync tracking. */
    suspend fun deleteBook(bookId: String) = bookSyncDao.deleteById(bookId)

    /** Clear all sync data. */
    suspend fun deleteAll() = bookSyncDao.deleteAll()
}
