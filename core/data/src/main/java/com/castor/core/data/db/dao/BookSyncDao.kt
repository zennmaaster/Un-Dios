package com.castor.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.castor.core.data.db.entity.BookSyncEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the `book_sync` table — tracks Kindle reading and Audible listening
 * positions for seamless cross-platform book synchronisation.
 */
@Dao
interface BookSyncDao {

    // -----------------------------------------------------------------------------------------
    // Insert / Upsert
    // -----------------------------------------------------------------------------------------

    /** Insert a new book or replace an existing one with the same primary key. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookSyncEntity)

    /** Full entity update. */
    @Update
    suspend fun update(book: BookSyncEntity)

    // -----------------------------------------------------------------------------------------
    // Kindle progress
    // -----------------------------------------------------------------------------------------

    /**
     * Update only the Kindle-related columns for the given book.
     */
    @Query(
        """
        UPDATE book_sync
        SET kindleProgress   = :progress,
            kindleLastPage   = :lastPage,
            kindleTotalPages = :totalPages,
            kindleChapter    = :chapter,
            kindleLastSync   = :syncTimestamp,
            lastUpdated      = :syncTimestamp
        WHERE id = :bookId
        """
    )
    suspend fun updateKindleProgress(
        bookId: String,
        progress: Float,
        lastPage: Int?,
        totalPages: Int?,
        chapter: String?,
        syncTimestamp: Long = System.currentTimeMillis()
    )

    // -----------------------------------------------------------------------------------------
    // Audible progress
    // -----------------------------------------------------------------------------------------

    /**
     * Update only the Audible-related columns for the given book.
     */
    @Query(
        """
        UPDATE book_sync
        SET audibleProgress   = :progress,
            audibleChapter    = :chapter,
            audiblePositionMs = :positionMs,
            audibleTotalMs    = :totalMs,
            audibleLastSync   = :syncTimestamp,
            lastUpdated       = :syncTimestamp
        WHERE id = :bookId
        """
    )
    suspend fun updateAudibleProgress(
        bookId: String,
        progress: Float,
        chapter: String?,
        positionMs: Long,
        totalMs: Long,
        syncTimestamp: Long = System.currentTimeMillis()
    )

    // -----------------------------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------------------------

    /** Observe all synced books ordered by most recently updated. */
    @Query("SELECT * FROM book_sync ORDER BY lastUpdated DESC")
    fun observeAll(): Flow<List<BookSyncEntity>>

    /** Get all synced books (suspend, non-reactive). */
    @Query("SELECT * FROM book_sync ORDER BY lastUpdated DESC")
    suspend fun getAll(): List<BookSyncEntity>

    /** Retrieve a single book by its deterministic ID. */
    @Query("SELECT * FROM book_sync WHERE id = :bookId")
    suspend fun getById(bookId: String): BookSyncEntity?

    /** Observe a single book by its deterministic ID. */
    @Query("SELECT * FROM book_sync WHERE id = :bookId")
    fun observeById(bookId: String): Flow<BookSyncEntity?>

    /**
     * Fuzzy title + author match using SQL LIKE.
     * The caller should pass `%lowercaseTitle%` and `%lowercaseAuthor%`.
     */
    @Query(
        """
        SELECT * FROM book_sync
        WHERE LOWER(title) LIKE :titlePattern
          AND LOWER(author) LIKE :authorPattern
        LIMIT 1
        """
    )
    suspend fun findByFuzzyMatch(titlePattern: String, authorPattern: String): BookSyncEntity?

    /**
     * Books where both Kindle and Audible progress exist and the absolute
     * difference exceeds [threshold] (default 5%).
     */
    @Query(
        """
        SELECT * FROM book_sync
        WHERE kindleProgress IS NOT NULL
          AND audibleProgress IS NOT NULL
          AND ABS(kindleProgress - audibleProgress) > :threshold
        ORDER BY lastUpdated DESC
        """
    )
    fun observeOutOfSync(threshold: Float = 0.05f): Flow<List<BookSyncEntity>>

    /**
     * Books that only have data from one platform (unmatched).
     */
    @Query(
        """
        SELECT * FROM book_sync
        WHERE (kindleProgress IS NULL AND audibleProgress IS NOT NULL)
           OR (kindleProgress IS NOT NULL AND audibleProgress IS NULL)
        ORDER BY lastUpdated DESC
        """
    )
    fun observeUnmatched(): Flow<List<BookSyncEntity>>

    /** Count of all tracked books. */
    @Query("SELECT COUNT(*) FROM book_sync")
    fun observeCount(): Flow<Int>

    // -----------------------------------------------------------------------------------------
    // Delete
    // -----------------------------------------------------------------------------------------

    @Query("DELETE FROM book_sync WHERE id = :bookId")
    suspend fun deleteById(bookId: String)

    @Query("DELETE FROM book_sync")
    suspend fun deleteAll()
}
