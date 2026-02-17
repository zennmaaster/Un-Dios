package com.castor.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.castor.core.data.db.entity.TasteProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TasteProfileDao {

    // -----------------------------------------------------------------------------------------
    // Upsert
    // -----------------------------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: TasteProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(profiles: List<TasteProfileEntity>)

    // -----------------------------------------------------------------------------------------
    // Query
    // -----------------------------------------------------------------------------------------

    @Query("SELECT * FROM taste_profile ORDER BY score DESC")
    fun getAll(): Flow<List<TasteProfileEntity>>

    @Query("SELECT * FROM taste_profile ORDER BY score DESC")
    suspend fun getAllSuspend(): List<TasteProfileEntity>

    @Query("SELECT * FROM taste_profile ORDER BY score DESC LIMIT :limit")
    fun getTopGenres(limit: Int = 5): Flow<List<TasteProfileEntity>>

    @Query("SELECT * FROM taste_profile ORDER BY score DESC LIMIT :limit")
    suspend fun getTopGenresSuspend(limit: Int = 5): List<TasteProfileEntity>

    @Query("SELECT * FROM taste_profile WHERE genre = :genre LIMIT 1")
    suspend fun getByGenre(genre: String): TasteProfileEntity?

    // -----------------------------------------------------------------------------------------
    // Delete
    // -----------------------------------------------------------------------------------------

    @Query("DELETE FROM taste_profile")
    suspend fun deleteAll()

    @Query("DELETE FROM taste_profile WHERE genre = :genre")
    suspend fun deleteByGenre(genre: String)
}
