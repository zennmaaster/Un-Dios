package com.castor.app.system

import androidx.lifecycle.ViewModel
import com.castor.core.ui.components.SystemStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ViewModel exposing real-time [SystemStats] to the Compose UI layer.
 *
 * Starts monitoring when created (typically when the first composable collecting
 * [stats] enters composition) and stops when the ViewModel is cleared (e.g., when
 * the Activity is destroyed), ensuring system reads are only performed while the
 * UI is actually observing.
 *
 * Usage in Compose:
 * ```kotlin
 * val viewModel: SystemStatsViewModel = hiltViewModel()
 * val stats by viewModel.stats.collectAsState()
 * SystemStatusBar(stats = stats)
 * ```
 */
@HiltViewModel
class SystemStatsViewModel @Inject constructor(
    private val statsProvider: SystemStatsProvider
) : ViewModel() {

    /**
     * Observable stream of system statistics, updated every 3 seconds.
     * Compose UI should collect this with `collectAsState()` or
     * `collectAsStateWithLifecycle()`.
     */
    val stats: StateFlow<SystemStats> = statsProvider.stats

    init {
        statsProvider.startMonitoring()
    }

    override fun onCleared() {
        statsProvider.stopMonitoring()
        super.onCleared()
    }
}
