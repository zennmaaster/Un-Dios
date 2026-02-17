package com.castor.core.data.repository

import com.castor.core.common.model.MediaSource
import com.castor.core.data.db.dao.WatchHistoryDao
import com.castor.core.data.db.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository facade for the watch history table.
 *
 * Provides a clean API for the tracking and recommendation layers to
 * read/write watch events without touching DAOs directly.
 */
@Singleton
class WatchHistoryRepository @Inject constructor(
    private val watchHistoryDao: WatchHistoryDao
) {

    // -------------------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------------------

    suspend fun logWatchEvent(
        source: MediaSource,
        title: String,
        genre: String? = null,
        contentType: String = "video",
        durationWatchedMs: Long = 0L,
        totalDurationMs: Long = 0L,
        metadata: String? = null
    ) {
        val completion = if (totalDurationMs > 0) {
            ((durationWatchedMs.toFloat() / totalDurationMs) * 100f).coerceIn(0f, 100f)
        } else 0f

        watchHistoryDao.insert(
            WatchHistoryEntity(
                source = source.name,
                title = title,
                genre = genre,
                contentType = contentType,
                durationWatchedMs = durationWatchedMs,
                totalDurationMs = totalDurationMs,
                completionPercent = completion,
                metadata = metadata
            )
        )
    }

    // -------------------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------------------

    fun getAll(): Flow<List<WatchHistoryEntity>> = watchHistoryDao.getAll()

    fun getRecent(limit: Int = 50): Flow<List<WatchHistoryEntity>> =
        watchHistoryDao.getRecent(limit)

    suspend fun getRecentSuspend(limit: Int = 50): List<WatchHistoryEntity> =
        watchHistoryDao.getRecentSuspend(limit)

    fun getBySource(source: MediaSource): Flow<List<WatchHistoryEntity>> =
        watchHistoryDao.getBySource(source.name)

    fun getByGenre(genre: String): Flow<List<WatchHistoryEntity>> =
        watchHistoryDao.getByGenre(genre)

    fun getByContentType(contentType: String): Flow<List<WatchHistoryEntity>> =
        watchHistoryDao.getByContentType(contentType)

    fun search(query: String): Flow<List<WatchHistoryEntity>> =
        watchHistoryDao.search(query)

    fun getTotalCount(): Flow<Int> = watchHistoryDao.getTotalCount()

    fun getTotalWatchTimeMs(): Flow<Long?> = watchHistoryDao.getTotalWatchTimeMs()

    suspend fun getAllGenres(): List<String> = watchHistoryDao.getAllGenres()

    suspend fun getTopGenres(limit: Int = 10) = watchHistoryDao.getTopGenres(limit)

    suspend fun getSourceCounts() = watchHistoryDao.getSourceCounts()

    // -------------------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------------------

    suspend fun deleteOlderThan(olderThan: Long) = watchHistoryDao.deleteOlderThan(olderThan)

    suspend fun deleteAll() = watchHistoryDao.deleteAll()
}
