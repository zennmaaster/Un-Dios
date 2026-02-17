package com.castor.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.castor.core.data.db.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY triggerTimeMs ASC")
    fun getActiveReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE triggerTimeMs > :now AND isCompleted = 0 ORDER BY triggerTimeMs ASC LIMIT 5")
    fun getUpcomingReminders(now: Long = System.currentTimeMillis()): Flow<List<ReminderEntity>>

    @Insert
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Query("UPDATE reminders SET isCompleted = 1 WHERE id = :reminderId")
    suspend fun markCompleted(reminderId: Long)

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): ReminderEntity?

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: Long)

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 AND triggerTimeMs > :now ORDER BY triggerTimeMs ASC")
    fun getFutureReminders(now: Long): Flow<List<ReminderEntity>>

    @Query("DELETE FROM reminders WHERE isCompleted = 1 AND createdAt < :olderThan")
    suspend fun cleanupOldCompleted(olderThan: Long)
}
