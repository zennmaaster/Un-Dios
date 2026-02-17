package com.castor.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.castor.core.data.db.entity.HabitCompletionEntity
import com.castor.core.data.db.entity.HabitEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the `habits` and `habit_completions` tables.
 *
 * All read operations return reactive [Flow]s that emit whenever the
 * underlying data changes. Write operations are suspend functions.
 *
 * Active habits are ordered by creation time (oldest first), so the
 * user's longest-running habits appear at the top of the list.
 */
@Dao
interface HabitDao {

    @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY createdAt ASC")
    fun getActiveHabits(): Flow<List<HabitEntity>>

    @Query(
        """
        SELECT * FROM habit_completions
        WHERE habitId = :habitId AND date BETWEEN :startDate AND :endDate
        """
    )
    fun getCompletionsInRange(
        habitId: String,
        startDate: String,
        endDate: String
    ): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habit_completions WHERE date = :date")
    fun getCompletionsForDate(date: String): Flow<List<HabitCompletionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: HabitCompletionEntity)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND date = :date")
    suspend fun removeCompletion(habitId: String, date: String)

    @Query("UPDATE habits SET isArchived = 1 WHERE id = :habitId")
    suspend fun archiveHabit(habitId: String)

    @Delete
    suspend fun deleteHabit(habit: HabitEntity)
}
