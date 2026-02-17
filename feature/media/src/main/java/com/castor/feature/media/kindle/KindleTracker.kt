package com.castor.feature.media.kindle

import android.service.notification.StatusBarNotification
import android.util.Log
import com.castor.core.data.db.entity.BookSyncEntity
import com.castor.core.data.repository.BookSyncRepository
import com.castor.feature.media.sync.BookMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks Kindle reading position by monitoring notifications from the
 * `com.amazon.kindle` package.
 *
 * Kindle publishes an ongoing notification while the user is reading that
 * typically contains the book title and a progress indicator in one of
 * several observed formats:
 *
 * - `"Book Title — 42%"`
 * - `"42% · Book Title"`
 * - `"Reading: Book Title (Page 123 of 456)"`
 *
 * This tracker parses that information and persists it in the
 * [BookSyncRepository] so that the sync feature can compare Kindle and
 * Audible positions.
 */
@Singleton
class KindleTracker @Inject constructor(
    private val bookSyncRepository: BookSyncRepository,
    private val bookMatcher: BookMatcher
) {
    companion object {
        private const val TAG = "KindleTracker"

        /** Kindle's application package name. */
        const val KINDLE_PACKAGE = "com.amazon.kindle"

        // Regex patterns for extracting progress from Kindle notification text.

        /** Matches patterns like "42% · Some Book Title" or "42% - Some Book Title" */
        private val PERCENT_PREFIX_PATTERN = Regex("""^(\d{1,3})%\s*[·\-–—]\s*(.+)$""")

        /** Matches patterns like "Some Book Title — 42%" or "Some Book Title - 42%" */
        private val PERCENT_SUFFIX_PATTERN = Regex("""^(.+?)\s*[·\-–—]\s*(\d{1,3})%$""")

        /** Matches patterns like "Page 123 of 456" anywhere in the string */
        private val PAGE_PATTERN = Regex("""[Pp]age\s+(\d+)\s+of\s+(\d+)""")

        /** Matches patterns like "Reading: Book Title" */
        private val READING_PREFIX_PATTERN = Regex("""^Reading:\s*(.+)$""")

        /** Matches "Chapter X" or "Chapter: X" patterns */
        private val CHAPTER_PATTERN = Regex("""[Cc]hapter\s*:?\s*(.+)""")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Last known Kindle progress state for quick UI access. */
    private val _currentState = MutableStateFlow<KindleReadingState?>(null)
    val currentState: StateFlow<KindleReadingState?> = _currentState.asStateFlow()

    /**
     * The last notification content hash we processed — prevents redundant
     * DB writes when the notification is reposted without changes.
     */
    private var lastContentHash: Int = 0

    /**
     * Called when a notification is posted from the Kindle package.
     *
     * Extracts reading progress from the notification's text content and
     * extras, then persists the result in the book sync database.
     */
    fun onKindleNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != KINDLE_PACKAGE) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Extract all text fields from the notification.
        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()
        val subText = extras.getCharSequence("android.subText")?.toString()
        val bigText = extras.getCharSequence("android.bigText")?.toString()

        // Combine all available text for parsing.
        val allText = listOfNotNull(title, text, subText, bigText)
        if (allText.isEmpty()) return

        // Deduplicate: skip if the same content was already processed.
        val contentHash = allText.hashCode()
        if (contentHash == lastContentHash) return
        lastContentHash = contentHash

        // Parse the notification content.
        val parsed = parseKindleNotification(allText) ?: return

        Log.d(TAG, "Kindle progress detected: title='${parsed.bookTitle}', " +
            "progress=${parsed.progressPercent}, page=${parsed.currentPage}/${parsed.totalPages}")

        _currentState.value = parsed

        // Persist to the book sync database.
        scope.launch {
            persistKindleProgress(parsed)
        }
    }

    /**
     * Called when Kindle notification is removed — the user closed the book.
     */
    fun onKindleNotificationRemoved() {
        lastContentHash = 0
        // Don't clear _currentState — we want to remember last known position.
    }

    /**
     * Update Kindle progress from the accessibility service parser.
     * This provides higher-fidelity data (exact page numbers, chapter names)
     * than what the notification alone can offer.
     */
    fun onAccessibilityUpdate(
        bookTitle: String?,
        currentPage: Int?,
        totalPages: Int?,
        chapterName: String?,
        progressPercent: Float?
    ) {
        val title = bookTitle ?: _currentState.value?.bookTitle ?: return
        val progress = progressPercent
            ?: if (currentPage != null && totalPages != null && totalPages > 0) {
                currentPage.toFloat() / totalPages.toFloat()
            } else {
                _currentState.value?.progressPercent
            }
            ?: return

        val state = KindleReadingState(
            bookTitle = title,
            author = _currentState.value?.author,
            progressPercent = progress,
            currentPage = currentPage ?: _currentState.value?.currentPage,
            totalPages = totalPages ?: _currentState.value?.totalPages,
            chapterName = chapterName ?: _currentState.value?.chapterName
        )

        _currentState.value = state

        scope.launch {
            persistKindleProgress(state)
        }
    }

    // -------------------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------------------

    /**
     * Parse a list of notification text fields into a [KindleReadingState].
     *
     * Tries multiple regex patterns against each text field until one
     * produces a result with at least a title and progress percentage.
     */
    private fun parseKindleNotification(textFields: List<String>): KindleReadingState? {
        var bookTitle: String? = null
        var progressPercent: Float? = null
        var currentPage: Int? = null
        var totalPages: Int? = null
        var chapterName: String? = null

        for (text in textFields) {
            // Try "42% · Book Title" format
            PERCENT_PREFIX_PATTERN.find(text)?.let { match ->
                progressPercent = match.groupValues[1].toFloatOrNull()?.div(100f)
                bookTitle = match.groupValues[2].trim()
            }

            // Try "Book Title — 42%" format
            if (bookTitle == null) {
                PERCENT_SUFFIX_PATTERN.find(text)?.let { match ->
                    bookTitle = match.groupValues[1].trim()
                    progressPercent = match.groupValues[2].toFloatOrNull()?.div(100f)
                }
            }

            // Try "Reading: Book Title" format
            if (bookTitle == null) {
                READING_PREFIX_PATTERN.find(text)?.let { match ->
                    bookTitle = match.groupValues[1].trim()
                }
            }

            // Extract page numbers if present
            PAGE_PATTERN.find(text)?.let { match ->
                currentPage = match.groupValues[1].toIntOrNull()
                totalPages = match.groupValues[2].toIntOrNull()
                // Derive progress from pages if we don't have a percentage yet.
                if (progressPercent == null && currentPage != null && totalPages != null && totalPages!! > 0) {
                    progressPercent = currentPage!!.toFloat() / totalPages!!.toFloat()
                }
            }

            // Extract chapter name if present
            CHAPTER_PATTERN.find(text)?.let { match ->
                chapterName = match.groupValues[1].trim()
            }
        }

        // If we still don't have a title, use the first non-empty text field as
        // a fallback (the notification title is usually the book name).
        if (bookTitle == null) {
            bookTitle = textFields.firstOrNull { it.isNotBlank() }
        }

        if (bookTitle == null || progressPercent == null) return null

        return KindleReadingState(
            bookTitle = bookTitle!!,
            author = null, // Kindle notifications rarely include author name
            progressPercent = progressPercent!!.coerceIn(0f, 1f),
            currentPage = currentPage,
            totalPages = totalPages,
            chapterName = chapterName
        )
    }

    // -------------------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------------------

    /**
     * Persist a Kindle reading state to the book sync database.
     *
     * If a matching book already exists (by title), update its Kindle columns.
     * Otherwise, create a new [BookSyncEntity] for it.
     */
    private suspend fun persistKindleProgress(state: KindleReadingState) {
        try {
            val bookId = bookMatcher.generateBookId(state.bookTitle, state.author ?: "")

            val existing = bookSyncRepository.getBook(bookId)
                ?: bookSyncRepository.findByFuzzyMatch(state.bookTitle, state.author ?: "")

            if (existing != null) {
                bookSyncRepository.updateKindleProgress(
                    bookId = existing.id,
                    progress = state.progressPercent,
                    lastPage = state.currentPage,
                    totalPages = state.totalPages,
                    chapter = state.chapterName
                )
            } else {
                // Create a new entry for this book.
                bookSyncRepository.upsertBook(
                    BookSyncEntity(
                        id = bookId,
                        title = state.bookTitle,
                        author = state.author ?: "",
                        kindleProgress = state.progressPercent,
                        kindleLastPage = state.currentPage,
                        kindleTotalPages = state.totalPages,
                        kindleChapter = state.chapterName,
                        kindleLastSync = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist Kindle progress", e)
        }
    }
}

/**
 * Snapshot of the current Kindle reading state for a single book.
 */
data class KindleReadingState(
    val bookTitle: String,
    val author: String?,
    val progressPercent: Float,
    val currentPage: Int?,
    val totalPages: Int?,
    val chapterName: String?
)
