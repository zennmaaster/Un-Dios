package com.castor.app.lockscreen

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.app.weather.WeatherRepository
import com.castor.core.data.db.dao.NotificationDao
import com.castor.core.data.db.entity.NotificationEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// =============================================================================
// Lock screen notification UI model
// =============================================================================

/**
 * UI model representing a single notification preview on the lock screen.
 *
 * Mapped from [NotificationEntity] in the Room database. Unlike the old
 * placeholder version, this carries the full metadata needed for the
 * [LockScreenNotificationCard] composable.
 *
 * @param id Unique notification ID (from Room).
 * @param appName Display name of the originating app (e.g. "WhatsApp", "Gmail").
 * @param title Notification title (sender name or subject line).
 * @param content One-line body preview of the notification.
 * @param timestamp Unix epoch millis when the notification was posted.
 * @param formattedTime Human-readable time string (e.g. "14:32").
 * @param priority Priority level string ("high", "normal", "low").
 * @param category Category string ("social", "work", "media", "sys", "other").
 */
data class LockScreenNotification(
    val id: String,
    val appName: String,
    val title: String,
    val content: String,
    val timestamp: Long,
    val formattedTime: String,
    val priority: String,
    val category: String
)

// =============================================================================
// ViewModel
// =============================================================================

/**
 * ViewModel managing the terminal-styled lock screen state.
 *
 * Responsibilities:
 * - Maintains lock/unlock state via [isLocked]
 * - Drives the boot-sequence animation via [showBootSequence] and [bootLines]
 * - Updates the clock every 60 seconds
 * - Reads user preferences from DataStore (lock enabled, timeout, boot animation, etc.)
 * - Queries real notifications from Room via [NotificationDao]
 * - Exposes a weather summary from [WeatherRepository]
 * - Provides formatted date, notification count, and weather for the lock screen UI
 * - Auto-locks the device after a configurable idle timeout
 *
 * Compose usage:
 * ```kotlin
 * val viewModel: LockScreenViewModel = hiltViewModel()
 * val isLocked by viewModel.isLocked.collectAsState()
 * val clockTime by viewModel.clockTime.collectAsState()
 * val notifications by viewModel.recentNotifications.collectAsState()
 * ```
 */
@HiltViewModel
class LockScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationDao: NotificationDao,
    private val weatherRepository: WeatherRepository
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
    // Terminal-formatted date (e.g. "Mon Feb 17 2026")
    // =========================================================================

    /** Current date in terminal `date` command format: "Mon Feb 17 2026". */
    private val _currentDate = MutableStateFlow(formatTerminalDate())
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()

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
    // Notifications (real data from Room)
    // =========================================================================

    /** Time formatter for notification timestamps. */
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    /**
     * Recent unread notifications mapped to lock-screen UI models.
     * Observes Room's reactive Flow and limits to 5 most recent entries.
     */
    val recentNotifications: StateFlow<List<LockScreenNotification>> =
        notificationDao.getRecentUnread(limit = 5)
            .map { entities -> entities.map { it.toLockScreenNotification() } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    /** Total count of unread, non-dismissed notifications. */
    val notificationCount: StateFlow<Int> =
        notificationDao.getUnreadCount()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0
            )

    // =========================================================================
    // Weather summary
    // =========================================================================

    /**
     * One-line weather summary for the lock screen.
     * Format: "52F, Partly cloudy" or null if weather data is unavailable.
     * Refreshed when the lock screen becomes visible and periodically.
     */
    private val _weatherSummary = MutableStateFlow<String?>(null)
    val weatherSummary: StateFlow<String?> = _weatherSummary.asStateFlow()

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
        loadWeatherSummary()
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
                _currentDate.value = formatTerminalDate()
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

    /**
     * Format the current date in a terminal `date` command style.
     * Example: "Mon Feb 17 2026"
     */
    private fun formatTerminalDate(): String {
        return try {
            SimpleDateFormat("EEE MMM d yyyy", Locale.US).format(Date())
        } catch (_: Exception) {
            ""
        }
    }

    // =========================================================================
    // Weather
    // =========================================================================

    /**
     * Load weather summary from the repository's cached data.
     * Attempts to read cached weather first, falling back to a fresh fetch.
     */
    private fun loadWeatherSummary() {
        viewModelScope.launch {
            try {
                val lat = weatherRepository.getSavedLatitude()
                val lon = weatherRepository.getSavedLongitude()
                val cityName = weatherRepository.getSavedCityName()
                val country = weatherRepository.getSavedCountry()

                // Use cache if available (forceRefresh = false)
                val response = weatherRepository.fetchWeather(lat, lon, forceRefresh = false)
                val weatherData = weatherRepository.toWeatherData(response, cityName, country)

                if (weatherData != null) {
                    val tempF = (weatherData.temperature * 9.0 / 5.0 + 32).toInt()
                    _weatherSummary.value = "${tempF}F, ${weatherData.description}"
                }
            } catch (_: Exception) {
                // Weather is a nice-to-have on the lock screen; fail silently
                _weatherSummary.value = null
            }
        }
    }

    /** Refresh the weather summary. Called when the lock screen becomes visible. */
    fun refreshWeatherSummary() {
        loadWeatherSummary()
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

        // Refresh weather when locking (user will see updated data on wake)
        refreshWeatherSummary()

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
    // Entity mapping
    // =========================================================================

    /**
     * Map a [NotificationEntity] from Room into a [LockScreenNotification] UI model.
     */
    private fun NotificationEntity.toLockScreenNotification(): LockScreenNotification {
        return LockScreenNotification(
            id = id,
            appName = appName,
            title = title,
            content = content,
            timestamp = timestamp,
            formattedTime = try {
                timeFormatter.format(Date(timestamp))
            } catch (_: Exception) {
                "--:--"
            },
            priority = priority,
            category = category
        )
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
