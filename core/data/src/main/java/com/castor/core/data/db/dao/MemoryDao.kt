package com.castor.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.castor.core.data.db.entity.MemoryEntity

/**
 * Data Access Object for the `memories` table.
 *
 * Provides CRUD operations for the agent's persistent memory system.
 * Unlike most DAOs in the app, read operations here return suspend values
 * (not Flows) because memory is read synchronously during prompt building.
 */
@Dao
interface MemoryDao {

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY updatedAt DESC")
    suspend fun getByCategory(category: String): List<MemoryEntity>

    @Query(
        """
        SELECT * FROM memories
        WHERE category = :category
          AND (key LIKE '%' || :search || '%' OR value LIKE '%' || :search || '%')
        ORDER BY updatedAt DESC
        """
    )
    suspend fun search(category: String, search: String): List<MemoryEntity>

    @Query(
        """
        SELECT * FROM memories
        WHERE key LIKE '%' || :search || '%' OR value LIKE '%' || :search || '%'
        ORDER BY updatedAt DESC
        """
    )
    suspend fun searchAll(search: String): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getById(id: Long): MemoryEntity?

    @Query("SELECT * FROM memories WHERE category = :category AND key = :key LIMIT 1")
    suspend fun getByKey(category: String, key: String): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity): Long

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM memories WHERE category = :category")
    suspend fun deleteByCategory(category: String)

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun count(): Int
}
