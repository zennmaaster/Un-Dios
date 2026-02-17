package com.castor.app.system

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ViewModel exposing [SystemStats] to the UI layer.
 *
 * Starts monitoring when created and stops when the ViewModel is cleared,
 * ensuring system stats are only collected while the UI is active.
 */
@HiltViewModel
class SystemStatsViewModel @Inject constructor(
    private val statsProvider: SystemStatsProvider
) : ViewModel() {

    /**
     * Observable stream of system statistics, updated every 2 seconds.
     * Compose UI can collect this with `collectAsStateWithLifecycle()`.
     */
    val systemStats: StateFlow<SystemStats> = statsProvider.systemStats

    init {
        statsProvider.startMonitoring()
    }

    override fun onCleared() {
        statsProvider.stopMonitoring()
        super.onCleared()
    }
}
