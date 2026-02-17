package com.castor.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.castor.core.data.db.entity.RecommendationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecommendationDao {

    // -----------------------------------------------------------------------------------------
    // Insert
    // -----------------------------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recommendations: List<RecommendationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recommendation: RecommendationEntity): Long

    // -----------------------------------------------------------------------------------------
    // Query
    // -----------------------------------------------------------------------------------------

    @Query("SELECT * FROM recommendations WHERE dismissed = 0 ORDER BY estimatedMatchScore DESC, createdAt DESC")
    fun getUndismissed(): Flow<List<RecommendationEntity>>

    @Query("SELECT * FROM recommendations WHERE dismissed = 0 ORDER BY estimatedMatchScore DESC, createdAt DESC")
    suspend fun getUndismissedSuspend(): List<RecommendationEntity>

    @Query("SELECT * FROM recommendations ORDER BY createdAt DESC")
    fun getAll(): Flow<List<RecommendationEntity>>

    @Query("SELECT * FROM recommendations WHERE dismissed = 0 AND genre = :genre ORDER BY estimatedMatchScore DESC")
    fun getByGenre(genre: String): Flow<List<RecommendationEntity>>

    @Query("SELECT * FROM recommendations WHERE dismissed = 0 AND source = :source ORDER BY estimatedMatchScore DESC")
    fun getBySource(source: String): Flow<List<RecommendationEntity>>

    @Query("SELECT COUNT(*) FROM recommendations WHERE dismissed = 0")
    fun getUndismissedCount(): Flow<Int>

    // -----------------------------------------------------------------------------------------
    // Update
    // -----------------------------------------------------------------------------------------

    @Query("UPDATE recommendations SET dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    // -----------------------------------------------------------------------------------------
    // Delete
    // -----------------------------------------------------------------------------------------

    @Query("DELETE FROM recommendations WHERE createdAt < :olderThan")
    suspend fun clearOld(olderThan: Long)

    @Query("DELETE FROM recommendations")
    suspend fun deleteAll()
}
