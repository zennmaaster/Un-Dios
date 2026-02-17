package com.castor.app.lockscreen

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * A placeholder notification entry shown on the lock screen.
 *
 * @param source The originating app or channel (e.g. "WhatsApp", "Gmail").
 * @param content A one-line summary of the notification content.
 * @param timestamp Human-readable timestamp (e.g. "14:32").
 */
data class LockScreenNotification(
    val source: String,
    val content: String,
    val timestamp: String
)

/**
 * ViewModel managing the terminal-styled lock screen state.
 *
 * Responsibilities:
 * - Maintains lock/unlock state via [isLocked]
 * - Drives the boot-sequence animation via [showBootSequence] and [bootLines]
 * - Updates the clock every 60 seconds
 * - Reads user preferences from DataStore (lock enabled, timeout, boot animation, etc.)
 * - Provides a placeholder list of recent notifications for the lock screen preview
 * - Auto-locks the device after a configurable idle timeout
 *
 * Compose usage:
 * ```kotlin
 * val viewModel: LockScreenViewModel = hiltViewModel()
 * val isLocked by viewModel.isLocked.collectAsState()
 * val clockTime by viewModel.clockTime.collectAsState()
 * ```
 */
@HiltViewModel
class LockScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    // =========================================================================
    // DataStore
    // =========================================================================

    private val dataStore = context.lockScreenDataStore

    // =========================================================================
    // Lock state
    // =========================================================================

    /** Whether the lock screen is currently engaged. */
    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    /** Whether the boot-sequence animation is currently playing. */
    private val _showBootSequence = MutableStateFlow(false)
    val showBootSequence: StateFlow<Boolean> = _showBootSequence.asStateFlow()

    /** Boot sequence lines revealed one-by-one during the animation. */
    private val _bootLines = MutableStateFlow<List<String>>(emptyList())
    val bootLines: StateFlow<List<String>> = _bootLines.asStateFlow()

    /**
     * Whether the post-authentication success message is being shown
     * before the lock screen fades out.
     */
    private val _showSuccessMessage = MutableStateFlow(false)
    val showSuccessMessage: StateFlow<Boolean> = _showSuccessMessage.asStateFlow()

    // =========================================================================
    // Clock
    // =========================================================================

    /** Current time formatted as "HH:mm". */
    private val _clockTime = MutableStateFlow(formatTime())
    val clockTime: StateFlow<String> = _clockTime.asStateFlow()

    /** Current date formatted as "EEE, MMM d, yyyy". */
    private val _clockDate = MutableStateFlow(formatDate())
    val clockDate: StateFlow<String> = _clockDate.asStateFlow()

    // =========================================================================
    // Preferences
    // =========================================================================

    /** Master toggle: whether the lock screen feature is enabled. */
    private val _isLockEnabled = MutableStateFlow(true)
    val isLockEnabled: StateFlow<Boolean> = _isLockEnabled.asStateFlow()

    /** Auto-lock timeout in milliseconds. */
    private val _lockTimeoutMs = MutableStateFlow(60_000L)
    val lockTimeoutMs: StateFlow<Long> = _lockTimeoutMs.asStateFlow()

    /** Whether notification previews should be shown on the lock screen. */
    private val _showNotificationsOnLock = MutableStateFlow(true)
    val showNotificationsOnLock: StateFlow<Boolean> = _showNotificationsOnLock.asStateFlow()

    /** Whether the boot animation should play when the lock screen appears. */
    private val _showBootAnimation = MutableStateFlow(true)
    val showBootAnimation: StateFlow<Boolean> = _showBootAnimation.asStateFlow()

    // =========================================================================
    // Notifications (placeholder)
    // =========================================================================

    /** Recent notifications to preview on the lock screen. */
    private val _recentNotifications = MutableStateFlow(
        listOf(
            LockScreenNotification(
                source = "WhatsApp",
                content = "Mom: \"Are you coming for dinner?\"",
                timestamp = "14:32"
            ),
            LockScreenNotification(
                source = "Gmail",
                content = "Your order has been shipped",
                timestamp = "13:15"
            ),
            LockScreenNotification(
                source = "Calendar",
                content = "Team standup in 30 minutes",
                timestamp = "12:00"
            )
        )
    )
    val recentNotifications: StateFlow<List<LockScreenNotification>> =
        _recentNotifications.asStateFlow()

    // =========================================================================
    // Internal jobs
    // =========================================================================

    private var clockJob: Job? = null
    private var autoLockJob: Job? = null

    // =========================================================================
    // Boot sequence script
    // =========================================================================

    private val bootSequenceScript = listOf(
        "[OK] Starting Un-Dios kernel v0.1.0...",
        "[OK] Mounting /system/agents...",
        "[OK] Loading user profile...",
        "[OK] Initializing privacy sandbox...",
        "[OK] Starting notification listener...",
        "[OK] Initializing agents...",
        "[OK] System ready. Awaiting authentication."
    )

    // =========================================================================
    // Initialization
    // =========================================================================

    init {
        loadPreferences()
        startClockUpdater()
    }

    // =========================================================================
    // Preference loading
    // =========================================================================

    /**
     * Load lock screen preferences from DataStore and populate local state.
     */
    private fun loadPreferences() {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            _isLockEnabled.value =
                prefs[LockScreenPreferences.LOCK_ENABLED] ?: true
            _lockTimeoutMs.value =
                prefs[LockScreenPreferences.LOCK_TIMEOUT_MS] ?: 60_000L
            _showNotificationsOnLock.value =
                prefs[LockScreenPreferences.SHOW_NOTIFICATIONS_ON_LOCK] ?: true
            _showBootAnimation.value =
                prefs[LockScreenPreferences.SHOW_BOOT_ANIMATION] ?: true

            // If lock screen is disabled, start unlocked
            if (!_isLockEnabled.value) {
                _isLocked.value = false
            }
        }

        // Continue observing preference changes
        viewModelScope.launch {
            dataStore.data.map { prefs ->
                prefs[LockScreenPreferences.LOCK_ENABLED] ?: true
            }.collect { enabled ->
                _isLockEnabled.value = enabled
                if (!enabled) {
                    _isLocked.value = false
                }
            }
        }

        viewModelScope.launch {
            dataStore.data.map { prefs ->
                prefs[LockScreenPreferences.LOCK_TIMEOUT_MS] ?: 60_000L
            }.collect { timeout ->
                _lockTimeoutMs.value = timeout
            }
        }

        viewModelScope.launch {
            dataStore.data.map { prefs ->
                prefs[LockScreenPreferences.SHOW_NOTIFICATIONS_ON_LOCK] ?: true
            }.collect { show ->
                _showNotificationsOnLock.value = show
            }
        }

        viewModelScope.launch {
            dataStore.data.map { prefs ->
                prefs[LockScreenPreferences.SHOW_BOOT_ANIMATION] ?: true
            }.collect { show ->
                _showBootAnimation.value = show
            }
        }
    }

    // =========================================================================
    // Preference updates
    // =========================================================================

    /** Toggle the lock screen on or off and persist the choice. */
    fun setLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[LockScreenPreferences.LOCK_ENABLED] = enabled
            }
        }
    }

    /** Update the auto-lock timeout and persist the choice. */
    fun setLockTimeout(timeoutMs: Long) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[LockScreenPreferences.LOCK_TIMEOUT_MS] = timeoutMs
            }
        }
    }

    /** Toggle notification preview visibility on the lock screen. */
    fun setShowNotificationsOnLock(show: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[LockScreenPreferences.SHOW_NOTIFICATIONS_ON_LOCK] = show
            }
        }
    }

    /** Toggle the boot animation on or off. */
    fun setShowBootAnimation(show: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[LockScreenPreferences.SHOW_BOOT_ANIMATION] = show
            }
        }
    }

    // =========================================================================
    // Clock
    // =========================================================================

    /**
     * Start a coroutine that updates the clock every 60 seconds.
     * Runs for the lifetime of the ViewModel.
     */
    private fun startClockUpdater() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            while (isActive) {
                _clockTime.value = formatTime()
                _clockDate.value = formatDate()
                delay(60_000L)
            }
        }
    }

    private fun formatTime(): String {
        return try {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        } catch (_: Exception) {
            "--:--"
        }
    }

    private fun formatDate(): String {
        return try {
            SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date())
        } catch (_: Exception) {
            ""
        }
    }

    // =========================================================================
    // Lock / Unlock
    // =========================================================================

    /**
     * Engage the lock screen. Optionally plays the boot sequence animation
     * if the user has that preference enabled.
     */
    fun lockScreen() {
        if (!_isLockEnabled.value) return

        autoLockJob?.cancel()
        _showSuccessMessage.value = false
        _isLocked.value = true

        if (_showBootAnimation.value) {
            playBootSequence()
        }
    }

    /**
     * Dismiss the lock screen after a successful authentication.
     * Briefly shows a success message before fading out, then starts the
     * auto-lock idle timer.
     */
    fun unlockScreen() {
        viewModelScope.launch {
            _showSuccessMessage.value = true
            delay(1200L) // show success message briefly
            _showSuccessMessage.value = false
            _isLocked.value = false
            _showBootSequence.value = false
            _bootLines.value = emptyList()

            startAutoLockTimer()
        }
    }

    /**
     * Reset the auto-lock idle timer. Should be called on every user interaction
     * (tap, swipe, etc.) while the device is unlocked.
     */
    fun resetAutoLockTimer() {
        if (!_isLockEnabled.value) return
        if (_isLocked.value) return
        startAutoLockTimer()
    }

    /**
     * Start or restart the auto-lock idle countdown.
     * When the timeout elapses without [resetAutoLockTimer] being called,
     * the lock screen re-engages.
     */
    private fun startAutoLockTimer() {
        autoLockJob?.cancel()

        val timeout = _lockTimeoutMs.value
        // -1 or MAX_VALUE means "never auto-lock"
        if (timeout < 0 || timeout == Long.MAX_VALUE) return

        autoLockJob = viewModelScope.launch {
            delay(timeout)
            lockScreen()
        }
    }

    // =========================================================================
    // Boot animation
    // =========================================================================

    /**
     * Play the terminal boot sequence animation.
     * Lines appear one at a time with a staggered delay, simulating a
     * Linux kernel boot log.
     */
    private fun playBootSequence() {
        _showBootSequence.value = true
        _bootLines.value = emptyList()

        viewModelScope.launch {
            for (line in bootSequenceScript) {
                _bootLines.value = _bootLines.value + line
                delay(280L) // stagger each line ~280ms
            }
            // After full boot sequence, transition to main lock screen
            delay(400L)
            _showBootSequence.value = false
        }
    }

    // =========================================================================
    // Cleanup
    // =========================================================================

    override fun onCleared() {
        clockJob?.cancel()
        autoLockJob?.cancel()
        super.onCleared()
    }
}
