package com.castor.feature.media.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.core.common.model.MediaSource
import com.castor.core.common.model.UnifiedMediaItem
import com.castor.core.data.repository.MediaQueueRepository
import com.castor.feature.media.queue.PlaybackOrchestrator
import com.castor.feature.media.session.MediaSessionMonitor
import com.castor.feature.media.session.NowPlayingState
import com.castor.feature.media.session.UnifiedTransportControls
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Un-Dios media screen.
 *
 * A single data class that the Compose UI observes. All fields are immutable
 * and derived from the combination of [PlaybackOrchestrator] flows,
 * [MediaQueueRepository] flows, [MediaSessionMonitor] state, and local UI state.
 */
data class MediaScreenState(
    /** The item currently at the head of the queue (position 0). */
    val currentItem: UnifiedMediaItem? = null,
    /** Whether the current item is actively playing. */
    val isPlaying: Boolean = false,
    /** Playback position of the current item in milliseconds. */
    val playbackPositionMs: Long = 0L,
    /** The full ordered queue. */
    val queue: List<UnifiedMediaItem> = emptyList(),
    /** Number of items in the queue. */
    val queueSize: Int = 0,
    /** Currently selected tab in the source tabs bar. */
    val selectedTab: MediaTab = MediaTab.QUEUE,
    /** Current search query text. */
    val searchQuery: String = "",
    /** Search results across sources. */
    val searchResults: List<UnifiedMediaItem> = emptyList(),
    /** Whether a search is in progress. */
    val isSearching: Boolean = false,
    /** Active source filter chips for search. */
    val sourceFilter: Set<MediaSource> = emptySet(),
    /** Whether Spotify has an active MediaSession. */
    val spotifyConnected: Boolean = false,
    /** Whether YouTube has an active MediaSession. */
    val youtubeConnected: Boolean = false,
    /** Whether Audible has an active MediaSession. */
    val audibleConnected: Boolean = false,
    /** Raw now-playing state from the session monitor (for fallback display). */
    val nowPlaying: NowPlayingState = NowPlayingState()
)

/**
 * Tabs available in the media screen's source tab bar.
 */
enum class MediaTab(val label: String) {
    QUEUE("Queue"),
    SPOTIFY("Spotify"),
    YOUTUBE("YouTube"),
    AUDIBLE("Audible")
}

/**
 * ViewModel for the Un-Dios unified media screen.
 *
 * Bridges the [PlaybackOrchestrator] (cross-source playback transitions and
 * audio focus), [MediaQueueRepository] (persisted queue), and
 * [MediaSessionMonitor] (live system media sessions) into a single
 * [MediaScreenState] flow consumed by the Compose UI layer.
 */
@HiltViewModel
class MediaViewModel @Inject constructor(
    private val orchestrator: PlaybackOrchestrator,
    private val queueRepository: MediaQueueRepository,
    private val mediaSessionMonitor: MediaSessionMonitor,
    private val transportControls: UnifiedTransportControls
) : ViewModel() {

    // -------------------------------------------------------------------------------------
    // Local UI state (not backed by any repository)
    // -------------------------------------------------------------------------------------

    private val _localState = MutableStateFlow(LocalUiState())

    private data class LocalUiState(
        val selectedTab: MediaTab = MediaTab.QUEUE,
        val searchQuery: String = "",
        val searchResults: List<UnifiedMediaItem> = emptyList(),
        val isSearching: Boolean = false,
        val sourceFilter: Set<MediaSource> = emptySet()
    )

    // -------------------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------------------

    init {
        mediaSessionMonitor.startMonitoring()
    }

    override fun onCleared() {
        super.onCleared()
        mediaSessionMonitor.stopMonitoring()
    }

    // -------------------------------------------------------------------------------------
    // Combined UI state — single source of truth for the screen
    // -------------------------------------------------------------------------------------

    val uiState: StateFlow<MediaScreenState> = combine(
        orchestrator.currentItem,
        orchestrator.isPlaying,
        orchestrator.playbackPosition,
        queueRepository.getQueue(),
        queueRepository.getQueueSize(),
        _localState,
        mediaSessionMonitor.nowPlaying
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val currentItem = values[0] as UnifiedMediaItem?
        val isPlaying = values[1] as Boolean
        val positionMs = values[2] as Long
        val queue = values[3] as List<UnifiedMediaItem>
        val queueSize = values[4] as Int
        val local = values[5] as LocalUiState
        val nowPlaying = values[6] as NowPlayingState

        // Determine which sources have active sessions.
        val activeSources = mediaSessionMonitor.activeSessions.value
            .mapNotNull { it.source }
            .toSet()

        MediaScreenState(
            currentItem = currentItem,
            isPlaying = isPlaying,
            playbackPositionMs = positionMs,
            queue = queue,
            queueSize = queueSize,
            selectedTab = local.selectedTab,
            searchQuery = local.searchQuery,
            searchResults = local.searchResults,
            isSearching = local.isSearching,
            sourceFilter = local.sourceFilter,
            spotifyConnected = MediaSource.SPOTIFY in activeSources,
            youtubeConnected = MediaSource.YOUTUBE in activeSources,
            audibleConnected = MediaSource.AUDIBLE in activeSources,
            nowPlaying = nowPlaying
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MediaScreenState()
    )

    // -------------------------------------------------------------------------------------
    // Playback controls — delegate to orchestrator, fall back to transport controls
    // -------------------------------------------------------------------------------------

    /**
     * Toggle play / pause. Uses the orchestrator when there is a queue item;
     * falls back to [UnifiedTransportControls] to control whatever system media
     * session is active when the queue is empty.
     */
    fun onPlayPause() {
        viewModelScope.launch {
            if (uiState.value.currentItem != null) {
                orchestrator.playPause()
            } else {
                transportControls.playPause()
            }
        }
    }

    /**
     * Skip to the next item in the queue (cross-source transition).
     * Falls back to [UnifiedTransportControls] when no queue item is present.
     */
    fun onSkipNext() {
        viewModelScope.launch {
            if (uiState.value.currentItem != null) {
                orchestrator.skipNext()
            } else {
                transportControls.skipNext()
            }
        }
    }

    /**
     * Restart the current item (or seek to beginning).
     * Falls back to [UnifiedTransportControls] when no queue item is present.
     */
    fun onSkipPrevious() {
        viewModelScope.launch {
            if (uiState.value.currentItem != null) {
                orchestrator.skipPrevious()
            } else {
                transportControls.skipPrevious()
            }
        }
    }

    /**
     * Seek to an absolute position in the current item.
     * Falls back to [UnifiedTransportControls] when no queue item is present.
     */
    fun onSeekTo(positionMs: Long) {
        viewModelScope.launch {
            if (uiState.value.currentItem != null) {
                orchestrator.seekTo(positionMs)
            } else {
                transportControls.seekTo(positionMs)
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Queue management — delegate to repository
    // -------------------------------------------------------------------------------------

    /** Append an item to the end of the queue. */
    fun onAddToQueue(item: UnifiedMediaItem) {
        viewModelScope.launch { queueRepository.addToQueue(item) }
    }

    /** Insert an item at position 1 (play next). */
    fun onPlayNext(item: UnifiedMediaItem) {
        viewModelScope.launch { queueRepository.addToQueueNext(item) }
    }

    /** Insert an item at position 1 and immediately skip to it. */
    fun onPlayNow(item: UnifiedMediaItem) {
        viewModelScope.launch {
            queueRepository.addToQueueNext(item)
            orchestrator.skipNext()
        }
    }

    /** Remove a single item from the queue. */
    fun onRemoveFromQueue(itemId: String) {
        viewModelScope.launch { queueRepository.removeFromQueue(itemId) }
    }

    /** Clear the entire queue. */
    fun onClearQueue() {
        viewModelScope.launch { queueRepository.clearQueue() }
    }

    /** Move an item from one position to another (drag-to-reorder). */
    fun onMoveQueueItem(fromPosition: Int, toPosition: Int) {
        viewModelScope.launch { queueRepository.moveItem(fromPosition, toPosition) }
    }

    // -------------------------------------------------------------------------------------
    // Tab selection
    // -------------------------------------------------------------------------------------

    /** Select a tab in the source tabs bar. */
    fun onSelectTab(tab: MediaTab) {
        _localState.update { it.copy(selectedTab = tab) }
    }

    // -------------------------------------------------------------------------------------
    // Search
    // -------------------------------------------------------------------------------------

    /** Update the search query and trigger a search. */
    fun onSearchQueryChanged(query: String) {
        _localState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _localState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        performSearch(query)
    }

    /** Toggle a source filter chip on/off and re-run the search. */
    fun onToggleSourceFilter(source: MediaSource) {
        _localState.update { state ->
            val newFilter = state.sourceFilter.toMutableSet()
            if (source in newFilter) newFilter.remove(source) else newFilter.add(source)
            state.copy(sourceFilter = newFilter)
        }
        val currentQuery = _localState.value.searchQuery
        if (currentQuery.isNotBlank()) {
            performSearch(currentQuery)
        }
    }

    /** Clear the search query and results. */
    fun onClearSearch() {
        _localState.update {
            it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false)
        }
    }

    /**
     * Perform a search across connected sources.
     *
     * In this phase we do a local search over the current queue. Actual source-
     * specific API searches (Spotify Web API, YouTube Data API, Audible catalog)
     * will be integrated when the respective adapter modules are complete.
     */
    private fun performSearch(query: String) {
        viewModelScope.launch {
            _localState.update { it.copy(isSearching = true) }

            val activeFilters = _localState.value.sourceFilter
            val lowerQuery = query.lowercase()

            // Search existing queue items as a baseline.
            val queueMatches = uiState.value.queue.filter { item ->
                val matchesQuery = item.title.lowercase().contains(lowerQuery) ||
                    (item.artist?.lowercase()?.contains(lowerQuery) == true)
                val matchesFilter = activeFilters.isEmpty() || item.source in activeFilters
                matchesQuery && matchesFilter
            }

            _localState.update {
                it.copy(searchResults = queueMatches, isSearching = false)
            }
        }
    }
}
