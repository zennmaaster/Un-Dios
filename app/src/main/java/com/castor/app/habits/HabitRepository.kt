package com.castor.app.habits

import com.castor.core.data.db.dao.HabitDao
import com.castor.core.data.db.entity.HabitCompletionEntity
import com.castor.core.data.db.entity.HabitEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing habits and habit completions.
 *
 * Provides high-level operations on the habit database:
 * - Fetching active habits
 * - Checking completion status for a given date
 * - Toggling habit completion (on/off)
 * - Creating new habits
 * - Archiving habits
 * - Calculating streaks (consecutive days completed)
 */
@Singleton
class HabitRepository @Inject constructor(
    private val habitDao: HabitDao
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Returns a Flow of all active (non-archived) habits,
     * ordered by creation time (oldest first).
     */
    fun getActiveHabits(): Flow<List<HabitEntity>> {
        return habitDao.getActiveHabits()
    }

    /**
     * Returns a Flow of all habit completions for the given date.
     *
     * @param date The date string in "yyyy-MM-dd" format (e.g., "2026-02-17")
     */
    fun getCompletionsForDate(date: String): Flow<List<HabitCompletionEntity>> {
        return habitDao.getCompletionsForDate(date)
    }

    /**
     * Toggles the completion status of a habit for the given date.
     * If the habit is already marked complete for that day, it is unchecked.
     * If not, it is marked complete.
     *
     * @param habitId The habit ID
     * @param date The date string in "yyyy-MM-dd" format
     */
    suspend fun toggleCompletion(habitId: String, date: String) {
        val existing = habitDao.getCompletionsForDate(date).first()
        val completion = existing.find { it.habitId == habitId }

        if (completion != null) {
            // Already completed — remove it
            habitDao.removeCompletion(habitId, date)
        } else {
            // Not completed — add it
            habitDao.insertCompletion(
                HabitCompletionEntity(
                    habitId = habitId,
                    date = date
                )
            )
        }
    }

    /**
     * Creates a new habit with the given name and target days per week.
     *
     * @param name The habit name (e.g., "Exercise")
     * @param targetDaysPerWeek The target number of days per week (1-7)
     */
    suspend fun createHabit(name: String, targetDaysPerWeek: Int) {
        habitDao.insertHabit(
            HabitEntity(
                name = name,
                targetDaysPerWeek = targetDaysPerWeek
            )
        )
    }

    /**
     * Archives a habit by ID.
     * Archived habits are hidden from the main list but their data is retained.
     *
     * @param habitId The habit ID
     */
    suspend fun archiveHabit(habitId: String) {
        habitDao.archiveHabit(habitId)
    }

    /**
     * Calculates the current streak for a habit: the number of consecutive
     * days (going backward from today) that the habit was completed.
     *
     * @param habitId The habit ID
     * @return The streak count (0 if not completed today)
     */
    suspend fun getStreakForHabit(habitId: String): Int {
        val calendar = Calendar.getInstance()
        var streak = 0

        // Start from today and count backward
        for (i in 0..365) { // Max 1 year lookback
            val dateStr = dateFormat.format(calendar.time)
            val completions = habitDao.getCompletionsForDate(dateStr).first()

            if (completions.any { it.habitId == habitId }) {
                streak++
            } else {
                // Streak broken
                break
            }

            // Move back one day
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        return streak
    }

    /**
     * Returns a Flow of completions for a given habit within a date range.
     *
     * @param habitId The habit ID
     * @param startDate Start date in "yyyy-MM-dd" format
     * @param endDate End date in "yyyy-MM-dd" format
     */
    fun getCompletionsInRange(
        habitId: String,
        startDate: String,
        endDate: String
    ): Flow<List<HabitCompletionEntity>> {
        return habitDao.getCompletionsInRange(habitId, startDate, endDate)
    }

    /**
     * Formats a Calendar date to "yyyy-MM-dd" format.
     */
    fun formatDate(calendar: Calendar): String {
        return dateFormat.format(calendar.time)
    }
}
