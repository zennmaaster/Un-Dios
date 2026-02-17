package com.castor.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.castor.core.data.db.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    // -----------------------------------------------------------------------------------------
    // Insert
    // -----------------------------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WatchHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<WatchHistoryEntity>)

    // -----------------------------------------------------------------------------------------
    // Query — all / recent
    // -----------------------------------------------------------------------------------------

    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSuspend(limit: Int = 50): List<WatchHistoryEntity>

    // -----------------------------------------------------------------------------------------
    // Query — by source
    // -----------------------------------------------------------------------------------------

    @Query("SELECT * FROM watch_history WHERE source = :source ORDER BY timestamp DESC")
    fun getBySource(source: String): Flow<List<WatchHistoryEntity>>

    // -----------------------------------------------------------------------------------------
    // Query — by genre
    // -----------------------------------------------------------------------------------------

    @Query("SELECT * FROM watch_history WHERE genre = :genre ORDER BY timestamp DESC")
    fun getByGenre(genre: String): Flow<List<WatchHistoryEntity>>

    // -----------------------------------------------------------------------------------------
    // Query — by content type
    // -----------------------------------------------------------------------------------------

    @Query("SELECT * FROM watch_history WHERE contentType = :contentType ORDER BY timestamp DESC")
    fun getByContentType(contentType: String): Flow<List<WatchHistoryEntity>>

    // -----------------------------------------------------------------------------------------
    // Full-text search (title LIKE)
    // -----------------------------------------------------------------------------------------

    @Query("SELECT * FROM watch_history WHERE title LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun search(query: String): Flow<List<WatchHistoryEntity>>

    // -----------------------------------------------------------------------------------------
    // Aggregates
    // -----------------------------------------------------------------------------------------

    @Query("SELECT COUNT(*) FROM watch_history")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT SUM(durationWatchedMs) FROM watch_history")
    fun getTotalWatchTimeMs(): Flow<Long?>

    @Query("SELECT DISTINCT genre FROM watch_history WHERE genre IS NOT NULL ORDER BY genre ASC")
    suspend fun getAllGenres(): List<String>

    @Query("SELECT DISTINCT source FROM watch_history ORDER BY source ASC")
    suspend fun getAllSources(): List<String>

    @Query(
        """
        SELECT source, COUNT(*) AS cnt
        FROM watch_history
        GROUP BY source
        ORDER BY cnt DESC
        """
    )
    suspend fun getSourceCounts(): List<SourceCount>

    @Query(
        """
        SELECT genre, COUNT(*) AS cnt
        FROM watch_history
        WHERE genre IS NOT NULL
        GROUP BY genre
        ORDER BY cnt DESC
        LIMIT :limit
        """
    )
    suspend fun getTopGenres(limit: Int = 10): List<GenreCount>

    // -----------------------------------------------------------------------------------------
    // Delete
    // -----------------------------------------------------------------------------------------

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM watch_history WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)

    @Query("DELETE FROM watch_history")
    suspend fun deleteAll()
}

/** Lightweight projection for source -> count aggregation. */
data class SourceCount(val source: String, val cnt: Int)

/** Lightweight projection for genre -> count aggregation. */
data class GenreCount(val genre: String, val cnt: Int)
