package com.castor.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.castor.core.data.db.entity.MediaQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaQueueDao {
    @Query("SELECT * FROM media_queue ORDER BY queuePosition ASC")
    fun getQueue(): Flow<List<MediaQueueEntity>>

    @Query("SELECT * FROM media_queue WHERE queuePosition = (SELECT MIN(queuePosition) FROM media_queue)")
    suspend fun getCurrentItem(): MediaQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: MediaQueueEntity)

    @Query("DELETE FROM media_queue WHERE id = :itemId")
    suspend fun removeItem(itemId: String)

    @Query("DELETE FROM media_queue")
    suspend fun clearQueue()

    @Query("SELECT COALESCE(MAX(queuePosition), -1) + 1 FROM media_queue")
    suspend fun getNextPosition(): Int

    @Query("UPDATE media_queue SET queuePosition = :newPosition WHERE id = :itemId")
    suspend fun updatePosition(itemId: String, newPosition: Int)

    @Query("SELECT COUNT(*) FROM media_queue")
    fun getQueueSize(): Flow<Int>

    @Query("SELECT * FROM media_queue WHERE id = :itemId")
    suspend fun getItemById(itemId: String): MediaQueueEntity?

    @Query("UPDATE media_queue SET queuePosition = queuePosition - 1 WHERE queuePosition > :position")
    suspend fun shiftPositionsDown(position: Int)

    @Query("UPDATE media_queue SET queuePosition = queuePosition + 1 WHERE queuePosition >= :position")
    suspend fun shiftPositionsUp(position: Int)

    @Query("SELECT * FROM media_queue ORDER BY queuePosition ASC")
    suspend fun getCurrentQueue(): List<MediaQueueEntity>
}
