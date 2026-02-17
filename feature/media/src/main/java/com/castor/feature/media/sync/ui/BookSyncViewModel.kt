package com.castor.feature.media.sync.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.core.data.db.entity.BookSyncEntity
import com.castor.core.data.repository.BookSyncRepository
import com.castor.feature.media.accessibility.MediaAccessibilityService
import com.castor.feature.media.sync.BookMatcher
import com.castor.feature.media.sync.SyncDelta
import com.castor.feature.media.sync.SyncPositionCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the book sync screen.
 */
data class BookSyncScreenState(
    /** All synced books with both Kindle and Audible data. */
    val matchedBooks: List<BookSyncUiModel> = emptyList(),
    /** Books detected on only one platform (need manual matching or auto-discovery). */
    val unmatchedBooks: List<BookSyncEntity> = emptyList(),
    /** Total number of tracked books. */
    val totalBooks: Int = 0,
    /** Whether the accessibility service is enabled. */
    val accessibilityEnabled: Boolean = false,
    /** Whether the list is still loading. */
    val isLoading: Boolean = true
)

/**
 * A book with computed sync delta and resume information for the UI.
 */
data class BookSyncUiModel(
    val book: BookSyncEntity,
    val syncDelta: SyncDelta?,
    val kindleProgressPercent: Int,
    val audibleProgressPercent: Int
)

/**
 * ViewModel for the Kindle-Audible book sync screen.
 *
 * Observes the [BookSyncRepository] for book data, computes sync deltas
 * using [SyncPositionCalculator], and provides actions for launching
 * Kindle/Audible and managing book matches.
 */
@HiltViewModel
class BookSyncViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val bookSyncRepository: BookSyncRepository,
    private val syncCalculator: SyncPositionCalculator,
    private val bookMatcher: BookMatcher
) : ViewModel() {

    private val _localState = MutableStateFlow(LocalState())

    private data class LocalState(
        val isLoading: Boolean = true
    )

    val uiState: StateFlow<BookSyncScreenState> = combine(
        bookSyncRepository.observeAllBooks(),
        bookSyncRepository.observeUnmatched(),
        bookSyncRepository.observeBookCount(),
        _localState
    ) { allBooks, unmatched, count, local ->
        val matched = allBooks
            .filter { it.kindleProgress != null && it.audibleProgress != null }
            .map { book ->
                BookSyncUiModel(
                    book = book,
                    syncDelta = syncCalculator.computeSyncDelta(book),
                    kindleProgressPercent = ((book.kindleProgress ?: 0f) * 100).toInt(),
                    audibleProgressPercent = ((book.audibleProgress ?: 0f) * 100).toInt()
                )
            }

        BookSyncScreenState(
            matchedBooks = matched,
            unmatchedBooks = unmatched,
            totalBooks = count,
            accessibilityEnabled = MediaAccessibilityService.isRunning,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BookSyncScreenState()
    )

    init {
        // Mark loading as false after initial collection.
        viewModelScope.launch {
            _localState.update { it.copy(isLoading = false) }
        }
    }

    // -------------------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------------------

    /**
     * Launch the Kindle app to continue reading.
     */
    fun openKindle(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage("com.amazon.kindle")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * Launch the Audible app to continue listening.
     */
    fun openAudible(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage("com.audible.application")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * Open Android's accessibility settings so the user can enable
     * the MediaAccessibilityService.
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Manually match two books from different platforms.
     */
    fun manuallyMatchBooks(kindleBookId: String, audibleBookId: String) {
        viewModelScope.launch {
            val kindleEntry = bookSyncRepository.getBook(kindleBookId) ?: return@launch
            val audibleEntry = bookSyncRepository.getBook(audibleBookId) ?: return@launch

            val merged = bookMatcher.mergeEntries(kindleEntry, audibleEntry)
            bookSyncRepository.upsertBook(merged)

            // Clean up the old separate entries if they have different IDs.
            if (kindleEntry.id != merged.id) {
                bookSyncRepository.deleteBook(kindleEntry.id)
            }
            if (audibleEntry.id != merged.id) {
                bookSyncRepository.deleteBook(audibleEntry.id)
            }
        }
    }

    /**
     * Remove a book from sync tracking.
     */
    fun removeBook(bookId: String) {
        viewModelScope.launch {
            bookSyncRepository.deleteBook(bookId)
        }
    }

    /**
     * Get the estimated Audible resume point description for a book.
     */
    fun getAudibleResumeDescription(book: BookSyncEntity): String? {
        return syncCalculator.estimateAudiblePosition(book)?.description
    }

    /**
     * Get the estimated Kindle resume point description for a book.
     */
    fun getKindleResumeDescription(book: BookSyncEntity): String? {
        return syncCalculator.estimateKindlePosition(book)?.description
    }
}
