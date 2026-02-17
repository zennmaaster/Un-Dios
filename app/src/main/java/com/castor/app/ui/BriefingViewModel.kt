package com.castor.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.agent.orchestrator.Briefing
import com.castor.agent.orchestrator.BriefingAgent
import com.castor.agent.orchestrator.ProactiveSuggestion
import com.castor.agent.orchestrator.QuickStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the briefing card and suggestions row on the home screen.
 *
 * Loads data from [BriefingAgent] on init and periodically refreshes every 15 minutes.
 * Exposes three observable state flows:
 * - [briefing] — the full morning briefing (null while loading)
 * - [suggestions] — proactive suggestions (empty while loading)
 * - [quickStatus] — compact status snapshot
 *
 * The refresh loop runs as long as the ViewModel is alive and can be manually
 * triggered via [refreshBriefing].
 */
@HiltViewModel
class BriefingViewModel @Inject constructor(
    private val briefingAgent: BriefingAgent
) : ViewModel() {

    companion object {
        /** How often to automatically refresh the briefing and suggestions. */
        private const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
    }

    private val _briefing = MutableStateFlow<Briefing?>(null)
    val briefing: StateFlow<Briefing?> = _briefing.asStateFlow()

    private val _suggestions = MutableStateFlow<List<ProactiveSuggestion>>(emptyList())
    val suggestions: StateFlow<List<ProactiveSuggestion>> = _suggestions.asStateFlow()

    private val _quickStatus = MutableStateFlow(QuickStatus())
    val quickStatus: StateFlow<QuickStatus> = _quickStatus.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var refreshJob: Job? = null

    init {
        // Initial load
        refreshBriefing()

        // Periodic refresh loop
        viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                loadAll()
            }
        }
    }

    /**
     * Manually trigger a full refresh of the briefing, suggestions, and quick status.
     * Safe to call multiple times — concurrent refresh requests are coalesced.
     */
    fun refreshBriefing() {
        // Cancel any in-flight refresh to avoid duplicate work
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loadAll()
        }
    }

    /**
     * Remove a suggestion from the list at the given [index].
     * This is a UI-only dismissal — the suggestion may reappear on the next refresh.
     */
    fun dismissSuggestion(index: Int) {
        _suggestions.update { current ->
            if (index in current.indices) {
                current.toMutableList().apply { removeAt(index) }
            } else {
                current
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Internal loading
    // -------------------------------------------------------------------------------------

    private suspend fun loadAll() {
        _isRefreshing.value = true
        try {
            // Load quick status first — it is cheap and gives immediate feedback
            val status = try {
                briefingAgent.getQuickStatus()
            } catch (_: Exception) {
                QuickStatus()
            }
            _quickStatus.value = status

            // Load full briefing
            val briefing = try {
                briefingAgent.generateMorningBriefing()
            } catch (_: Exception) {
                null
            }
            _briefing.value = briefing

            // Load suggestions
            val suggestions = try {
                briefingAgent.generateSuggestions()
            } catch (_: Exception) {
                emptyList()
            }
            _suggestions.value = suggestions
        } finally {
            _isRefreshing.value = false
        }
    }
}
