package com.castor.feature.recommendations.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.core.common.model.MediaSource
import com.castor.core.data.db.dao.WatchHistoryDao
import com.castor.core.data.db.entity.WatchHistoryEntity
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

data class WatchHistoryScreenState(
    val history: List<WatchHistoryEntity> = emptyList(),
    val selectedSourceFilter: MediaSource? = null,
    val totalWatchTimeMs: Long = 0L,
    val totalCount: Int = 0,
    val topGenres: List<String> = emptyList(),
    val mostUsedPlatform: String? = null
)

// -------------------------------------------------------------------------------------
// ViewModel
// -------------------------------------------------------------------------------------

@HiltViewModel
class WatchHistoryViewModel @Inject constructor(
    private val watchHistoryDao: WatchHistoryDao
) : ViewModel() {

    private val _sourceFilter = MutableStateFlow<MediaSource?>(null)
    private val _stats = MutableStateFlow(StatsState())

    private data class StatsState(
        val totalWatchTimeMs: Long = 0L,
        val topGenres: List<String> = emptyList(),
        val mostUsedPlatform: String? = null
    )

    init {
        loadStats()
    }

    val uiState: StateFlow<WatchHistoryScreenState> = combine(
        watchHistoryDao.getAll(),
        watchHistoryDao.getTotalCount(),
        _sourceFilter,
        _stats
    ) { allHistory, count, sourceFilter, stats ->
        val filtered = if (sourceFilter != null) {
            allHistory.filter { it.source == sourceFilter.name }
        } else {
            allHistory
        }

        WatchHistoryScreenState(
            history = filtered,
            selectedSourceFilter = sourceFilter,
            totalWatchTimeMs = stats.totalWatchTimeMs,
            totalCount = count,
            topGenres = stats.topGenres,
            mostUsedPlatform = stats.mostUsedPlatform
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WatchHistoryScreenState()
    )

    fun onSelectSourceFilter(source: MediaSource?) {
        _sourceFilter.update { if (it == source) null else source }
    }

    private fun loadStats() {
        viewModelScope.launch {
            val genres = watchHistoryDao.getTopGenres(5).map { it.genre }
            val sourceCounts = watchHistoryDao.getSourceCounts()
            val topSource = sourceCounts.maxByOrNull { it.cnt }?.source

            _stats.update {
                it.copy(
                    topGenres = genres,
                    mostUsedPlatform = topSource
                )
            }
        }

        viewModelScope.launch {
            watchHistoryDao.getTotalWatchTimeMs().collect { totalMs ->
                _stats.update { it.copy(totalWatchTimeMs = totalMs ?: 0L) }
            }
        }
    }
}
