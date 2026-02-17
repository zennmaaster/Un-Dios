package com.castor.feature.reminders.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.core.data.repository.Reminder
import com.castor.core.data.repository.ReminderRepository
import com.castor.feature.reminders.engine.ReminderParser
import com.castor.feature.reminders.engine.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Reminders feature screen.
 *
 * Exposes two observable lists (active and upcoming reminders), creation via
 * natural language or explicit parameters, and actions for completing, deleting,
 * and snoozing reminders.
 */
@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val reminderParser: ReminderParser,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    companion object {
        private const val TAG = "RemindersViewModel"

        /** Default snooze duration: 10 minutes. */
        private const val DEFAULT_SNOOZE_MS = 10 * 60 * 1000L
    }

    // -------------------------------------------------------------------------------------
    // Observable state
    // -------------------------------------------------------------------------------------

    /**
     * All incomplete reminders ordered by trigger time.
     */
    val activeReminders: StateFlow<List<Reminder>> =
        reminderRepository.getActiveReminders()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    /**
     * The next 5 upcoming reminders (trigger time in the future).
     */
    val upcomingReminders: StateFlow<List<Reminder>> =
        reminderRepository.getUpcomingReminders()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    /**
     * Whether the ViewModel is currently processing a creation request.
     */
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    /**
     * Last error message from a failed operation, or null.
     * Consumed by the UI (cleared after display).
     */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Last success message after creating a reminder, or null.
     * Consumed by the UI (cleared after display).
     */
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // -------------------------------------------------------------------------------------
    // Natural language creation
    // -------------------------------------------------------------------------------------

    /**
     * Parses a natural-language string into a reminder, persists it, and schedules
     * an alarm.
     *
     * @param input User input such as "Remind me to call Mom at 5pm".
     * @return `true` if a reminder was successfully created.
     */
    suspend fun createFromNaturalLanguage(input: String): Boolean {
        _isProcessing.value = true
        _errorMessage.value = null

        return try {
            val parsed = reminderParser.parse(input)
            if (parsed == null) {
                _errorMessage.value = "Could not understand the reminder. Try something like: \"Remind me to call Mom at 5pm\""
                Log.w(TAG, "Failed to parse reminder from: $input")
                false
            } else {
                val id = reminderRepository.createReminder(
                    description = parsed.description,
                    triggerTimeMs = parsed.triggerTimeMs,
                    isRecurring = parsed.isRecurring,
                    intervalMs = parsed.recurringInterval
                )
                reminderScheduler.scheduleReminder(id, parsed.triggerTimeMs)

                val timeDesc = formatRelativeTime(parsed.triggerTimeMs)
                _successMessage.value = "Reminder set: \"${parsed.description}\" $timeDesc"
                Log.i(TAG, "Created reminder $id: ${parsed.description} at ${parsed.triggerTimeMs}")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating reminder from NL: $input", e)
            _errorMessage.value = "Failed to create reminder. Please try again."
            false
        } finally {
            _isProcessing.value = false
        }
    }

    // -------------------------------------------------------------------------------------
    // Direct creation
    // -------------------------------------------------------------------------------------

    /**
     * Creates a reminder with explicit parameters (no NL parsing needed).
     */
    suspend fun createReminder(
        description: String,
        triggerTimeMs: Long,
        recurring: Boolean = false,
        intervalMs: Long? = null
    ) {
        _isProcessing.value = true
        try {
            val id = reminderRepository.createReminder(
                description = description,
                triggerTimeMs = triggerTimeMs,
                isRecurring = recurring,
                intervalMs = intervalMs
            )
            reminderScheduler.scheduleReminder(id, triggerTimeMs)
            _successMessage.value = "Reminder set: \"$description\""
        } catch (e: Exception) {
            Log.e(TAG, "Error creating reminder", e)
            _errorMessage.value = "Failed to create reminder."
        } finally {
            _isProcessing.value = false
        }
    }

    // -------------------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------------------

    /**
     * Marks a reminder as completed and cancels its scheduled alarm.
     */
    fun completeReminder(id: Long) {
        viewModelScope.launch {
            try {
                reminderRepository.completeReminder(id)
                reminderScheduler.cancelReminder(id)
            } catch (e: Exception) {
                Log.e(TAG, "Error completing reminder $id", e)
                _errorMessage.value = "Failed to complete reminder."
            }
        }
    }

    /**
     * Permanently deletes a reminder and cancels its scheduled alarm.
     */
    fun deleteReminder(id: Long) {
        viewModelScope.launch {
            try {
                reminderScheduler.cancelReminder(id)
                reminderRepository.deleteReminder(id)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting reminder $id", e)
                _errorMessage.value = "Failed to delete reminder."
            }
        }
    }

    /**
     * Snoozes a reminder by rescheduling it for [durationMs] from now.
     *
     * @param id The reminder database ID.
     * @param durationMs How long to snooze in milliseconds (default 10 minutes).
     */
    fun snoozeReminder(id: Long, durationMs: Long = DEFAULT_SNOOZE_MS) {
        viewModelScope.launch {
            try {
                val reminder = reminderRepository.getReminderById(id) ?: return@launch
                val newTrigger = System.currentTimeMillis() + durationMs
                reminderRepository.updateReminder(
                    reminder.copy(triggerTimeMs = newTrigger)
                )
                reminderScheduler.scheduleReminder(id, newTrigger)
                _successMessage.value = "Snoozed for ${durationMs / 60_000} minutes"
            } catch (e: Exception) {
                Log.e(TAG, "Error snoozing reminder $id", e)
                _errorMessage.value = "Failed to snooze reminder."
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // UI state helpers
    // -------------------------------------------------------------------------------------

    /**
     * Clears the current error message after the UI has consumed it.
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    /**
     * Clears the current success message after the UI has consumed it.
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    // -------------------------------------------------------------------------------------
    // Formatting
    // -------------------------------------------------------------------------------------

    private fun formatRelativeTime(triggerTimeMs: Long): String {
        val diff = triggerTimeMs - System.currentTimeMillis()
        return when {
            diff < 60_000 -> "in less than a minute"
            diff < 3_600_000 -> "in ${diff / 60_000} minutes"
            diff < 86_400_000 -> "in ${diff / 3_600_000} hours"
            else -> "in ${diff / 86_400_000} days"
        }
    }
}
