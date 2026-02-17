package com.castor.app.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.core.data.db.entity.HabitEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/**
 * Data class representing a habit with its completion status and streak.
 */
data class HabitWithStatus(
    val habit: HabitEntity,
    val isCompletedToday: Boolean,
    val currentStreak: Int,
    val weeklyCompletions: List<Boolean> // Last 7 days (oldest to newest)
)

/**
 * ViewModel for the Habit Tracker screen.
 *
 * Manages the state of habits, completions, date selection, and dialog visibility.
 * All state is exposed as [StateFlow]s to ensure reactive UI updates.
 */
@HiltViewModel
class HabitViewModel @Inject constructor(
    private val repository: HabitRepository
) : ViewModel() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // Selected date (defaults to today)
    private val _selectedDate = MutableStateFlow(getTodayString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Show create habit dialog
    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    // Habits with their status for the selected date
    val habits: StateFlow<List<HabitWithStatus>> = combine(
        repository.getActiveHabits(),
        _selectedDate
    ) { habitList, date ->
        habitList.map { habit ->
            val isCompleted = isHabitCompleted(habit.id, date)
            val streak = repository.getStreakForHabit(habit.id)
            val weeklyCompletions = getWeeklyCompletions(habit.id)

            HabitWithStatus(
                habit = habit,
                isCompletedToday = isCompleted,
                currentStreak = streak,
                weeklyCompletions = weeklyCompletions
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /**
     * Toggles the completion status of a habit for the selected date.
     */
    fun toggleHabit(habitId: String) {
        viewModelScope.launch {
            repository.toggleCompletion(habitId, _selectedDate.value)
        }
    }

    /**
     * Creates a new habit with the given name and target days per week.
     */
    fun createHabit(name: String, targetDaysPerWeek: Int) {
        if (name.isBlank()) return

        viewModelScope.launch {
            repository.createHabit(name.trim(), targetDaysPerWeek)
            _showCreateDialog.value = false
        }
    }

    /**
     * Archives a habit by ID.
     */
    fun archiveHabit(habitId: String) {
        viewModelScope.launch {
            repository.archiveHabit(habitId)
        }
    }

    /**
     * Selects a specific date.
     */
    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    /**
     * Shows the create habit dialog.
     */
    fun showCreateDialog() {
        _showCreateDialog.value = true
    }

    /**
     * Dismisses the create habit dialog.
     */
    fun dismissCreateDialog() {
        _showCreateDialog.value = false
    }

    /**
     * Checks if a habit is completed for the given date.
     */
    private suspend fun isHabitCompleted(habitId: String, date: String): Boolean {
        val completions = repository.getCompletionsForDate(date)
        return completions.first().any { it.habitId == habitId }
    }

    /**
     * Returns a list of booleans representing whether the habit was completed
     * for each of the last 7 days (oldest to newest).
     */
    private suspend fun getWeeklyCompletions(habitId: String): List<Boolean> {
        val calendar = Calendar.getInstance()
        val result = mutableListOf<Boolean>()

        // Get completions for the last 7 days (oldest to newest)
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = dateFormat.format(cal.time)
            val completions = repository.getCompletionsForDate(dateStr)
            val isCompleted = completions.first().any { it.habitId == habitId }
            result.add(isCompleted)
        }

        return result
    }

    /**
     * Returns today's date in "yyyy-MM-dd" format.
     */
    private fun getTodayString(): String {
        return dateFormat.format(Calendar.getInstance().time)
    }

    /**
     * Returns a list of the last 7 days (oldest to newest) as date strings.
     */
    fun getLast7Days(): List<String> {
        val result = mutableListOf<String>()
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            result.add(dateFormat.format(cal.time))
        }
        return result
    }
}
