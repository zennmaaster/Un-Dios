package com.castor.feature.recommendations.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.core.data.db.dao.RecommendationDao
import com.castor.core.data.db.dao.WatchHistoryDao
import com.castor.core.data.db.entity.RecommendationEntity
import com.castor.core.data.db.entity.WatchHistoryEntity
import com.castor.feature.recommendations.engine.RecommendationEngine
import com.castor.feature.recommendations.engine.TasteProfileEngine
import com.castor.feature.recommendations.worker.RecommendationWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// -------------------------------------------------------------------------------------
// UI State
// -------------------------------------------------------------------------------------

data class RecommendationsScreenState(
    val recommendations: List<RecommendationEntity> = emptyList(),
    val isLoading: Boolean = false,
    val selectedFilter: ContentFilter = ContentFilter.ALL,
    val topGenres: List<String> = emptyList(),
    val lastRefreshed: Long = 0L
)

enum class ContentFilter(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    SERIES("Series"),
    DOCUMENTARIES("Docs"),
    VIDEOS("Videos")
}

// -------------------------------------------------------------------------------------
// ViewModel
// -------------------------------------------------------------------------------------

/**
 * ViewModel for the Recommendations screen.
 *
 * Observes the recommendations table via Room Flow, handles dismiss actions,
 * filter state, and triggers the recommendation engine for refreshes.
 */
@HiltViewModel
class RecommendationsViewModel @Inject constructor(
    private val recommendationDao: RecommendationDao,
    private val recommendationEngine: RecommendationEngine,
    private val tasteProfileEngine: TasteProfileEngine,
    private val workScheduler: RecommendationWorkScheduler,
    private val watchHistoryDao: WatchHistoryDao
) : ViewModel() {

    private val _localState = MutableStateFlow(LocalState())

    private data class LocalState(
        val isLoading: Boolean = false,
        val selectedFilter: ContentFilter = ContentFilter.ALL,
        val topGenres: List<String> = emptyList(),
        val lastRefreshed: Long = 0L
    )

    init {
        // Schedule daily background refresh on first load.
        workScheduler.scheduleDailyRefresh()

        // Load top genres for the header.
        viewModelScope.launch {
            val genres = tasteProfileEngine.getTopGenres(5).map { it.genre }
            _localState.update { it.copy(topGenres = genres) }
        }
    }

    val uiState: StateFlow<RecommendationsScreenState> = combine(
        recommendationDao.getUndismissed(),
        _localState
    ) { recommendations, local ->
        val filtered = filterRecommendations(recommendations, local.selectedFilter)
        RecommendationsScreenState(
            recommendations = filtered,
            isLoading = local.isLoading,
            selectedFilter = local.selectedFilter,
            topGenres = local.topGenres,
            lastRefreshed = local.lastRefreshed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecommendationsScreenState()
    )

    // -------------------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------------------

    fun onRefresh() {
        viewModelScope.launch {
            _localState.update { it.copy(isLoading = true) }
            try {
                val history = watchHistoryDao.getRecentSuspend(30)
                tasteProfileEngine.rebuildProfile()
                recommendationEngine.generateRecommendations(history)
                _localState.update {
                    it.copy(
                        isLoading = false,
                        lastRefreshed = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                _localState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onDismiss(id: Long) {
        viewModelScope.launch {
            recommendationEngine.dismiss(id)
        }
    }

    fun onSelectFilter(filter: ContentFilter) {
        _localState.update { it.copy(selectedFilter = filter) }
    }

    // -------------------------------------------------------------------------------------
    // Filtering
    // -------------------------------------------------------------------------------------

    private fun filterRecommendations(
        recommendations: List<RecommendationEntity>,
        filter: ContentFilter
    ): List<RecommendationEntity> {
        if (filter == ContentFilter.ALL) return recommendations

        val contentTypeKey = when (filter) {
            ContentFilter.MOVIES -> "movie"
            ContentFilter.SERIES -> "series"
            ContentFilter.DOCUMENTARIES -> "documentary"
            ContentFilter.VIDEOS -> "video"
            ContentFilter.ALL -> return recommendations
        }

        return recommendations.filter { rec ->
            // Match against genre or inferred content type from description.
            rec.genre.lowercase().contains(contentTypeKey) ||
                rec.description.lowercase().contains(contentTypeKey)
        }
    }
}
