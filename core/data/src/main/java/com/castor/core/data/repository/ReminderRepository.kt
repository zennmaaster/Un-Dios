package com.castor.core.data.repository

import com.castor.core.data.db.dao.ReminderDao
import com.castor.core.data.db.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain model representing a single reminder.
 *
 * This is the model that UI and feature layers work with. It is decoupled from
 * the Room [ReminderEntity] so that the persistence layer can evolve independently.
 */
data class Reminder(
    val id: Long = 0,
    val description: String,
    val triggerTimeMs: Long,
    val isRecurring: Boolean = false,
    val recurringIntervalMs: Long? = null,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Repository wrapping [ReminderDao] and exposing domain-level [Reminder] objects.
 *
 * All write operations are suspend functions; all read operations return observable
 * [Flow]s that emit whenever the underlying table changes.
 */
@Singleton
class ReminderRepository @Inject constructor(
    private val reminderDao: ReminderDao
) {

    // -------------------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------------------

    /**
     * Returns a [Flow] of all incomplete reminders ordered by trigger time ascending.
     */
    fun getActiveReminders(): Flow<List<Reminder>> =
        reminderDao.getActiveReminders().map { entities -> entities.map { it.toDomain() } }

    /**
     * Returns a [Flow] of the next 5 upcoming reminders that have not yet triggered.
     */
    fun getUpcomingReminders(): Flow<List<Reminder>> =
        reminderDao.getUpcomingReminders().map { entities -> entities.map { it.toDomain() } }

    /**
     * Returns a [Flow] of all future (not-yet-triggered) incomplete reminders.
     */
    fun getFutureReminders(): Flow<List<Reminder>> =
        reminderDao.getFutureReminders(System.currentTimeMillis())
            .map { entities -> entities.map { it.toDomain() } }

    /**
     * Returns a single reminder by its database ID, or null if not found.
     */
    suspend fun getReminderById(id: Long): Reminder? =
        reminderDao.getReminderById(id)?.toDomain()

    // -------------------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------------------

    /**
     * Creates a new reminder and returns its auto-generated database ID.
     *
     * @param description Human-readable description of what the reminder is for.
     * @param triggerTimeMs Epoch millis when the reminder should fire.
     * @param isRecurring Whether this reminder repeats on a schedule.
     * @param intervalMs Recurrence interval in milliseconds (required when [isRecurring] is true).
     * @return The auto-generated row ID of the new reminder.
     */
    suspend fun createReminder(
        description: String,
        triggerTimeMs: Long,
        isRecurring: Boolean = false,
        intervalMs: Long? = null
    ): Long {
        val entity = ReminderEntity(
            description = description,
            triggerTimeMs = triggerTimeMs,
            isRecurring = isRecurring,
            recurringIntervalMs = intervalMs,
            isCompleted = false,
            createdAt = System.currentTimeMillis()
        )
        return reminderDao.insertReminder(entity)
    }

    /**
     * Marks a reminder as completed so it no longer appears in the active list.
     */
    suspend fun completeReminder(id: Long) {
        reminderDao.markCompleted(id)
    }

    /**
     * Permanently deletes a reminder from the database.
     */
    suspend fun deleteReminder(id: Long) {
        reminderDao.deleteReminder(id)
    }

    /**
     * Updates all mutable fields of an existing reminder.
     */
    suspend fun updateReminder(reminder: Reminder) {
        reminderDao.updateReminder(reminder.toEntity())
    }

    /**
     * Deletes all completed reminders whose [ReminderEntity.createdAt] is older
     * than the given timestamp. Used by the periodic maintenance worker.
     */
    suspend fun cleanupOldCompleted(olderThan: Long) {
        reminderDao.cleanupOldCompleted(olderThan)
    }

    // -------------------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------------------

    private fun ReminderEntity.toDomain(): Reminder = Reminder(
        id = id,
        description = description,
        triggerTimeMs = triggerTimeMs,
        isRecurring = isRecurring,
        recurringIntervalMs = recurringIntervalMs,
        isCompleted = isCompleted,
        createdAt = createdAt
    )

    private fun Reminder.toEntity(): ReminderEntity = ReminderEntity(
        id = id,
        description = description,
        triggerTimeMs = triggerTimeMs,
        isRecurring = isRecurring,
        recurringIntervalMs = recurringIntervalMs,
        isCompleted = isCompleted,
        createdAt = createdAt
    )
}
