package com.castor.feature.media.sync

import android.util.Log
import com.castor.core.data.db.entity.BookSyncEntity
import com.castor.core.data.repository.BookSyncRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Matches books detected on Kindle with audiobooks detected on Audible.
 *
 * Because neither platform exposes a public API with canonical identifiers,
 * matching must rely on fuzzy comparison of titles and authors extracted
 * from notification text and MediaSession metadata.
 *
 * Matching strategy:
 * 1. **Exact normalized match**: after stripping punctuation, lowercasing,
 *    and removing common suffixes ("(Unabridged)", series tags).
 * 2. **Fuzzy match**: Levenshtein distance on normalized titles, accepting
 *    a match if the distance is below [MAX_EDIT_DISTANCE_RATIO] of the
 *    shorter title's length.
 * 3. **Author confirmation**: if two titles match fuzzily, the author names
 *    must also overlap to confirm the match.
 *
 * When a match is found, the two separate [BookSyncEntity] rows (one from
 * Kindle, one from Audible) are merged into a single row containing data
 * from both sources.
 */
@Singleton
class BookMatcher @Inject constructor(
    private val bookSyncRepository: BookSyncRepository
) {
    companion object {
        private const val TAG = "BookMatcher"

        /**
         * Maximum allowed edit distance as a fraction of the shorter title.
         * For example, 0.3 means we tolerate up to 30% character difference.
         */
        private const val MAX_EDIT_DISTANCE_RATIO = 0.30f

        // Suffixes / substrings commonly appended by Audible but not Kindle.
        private val NOISE_PATTERNS = listOf(
            Regex("""\s*\(unabridged\)""", RegexOption.IGNORE_CASE),
            Regex("""\s*\(abridged\)""", RegexOption.IGNORE_CASE),
            Regex("""\s*:\s*a novel""", RegexOption.IGNORE_CASE),
            Regex("""\s*\(.*?edition\)""", RegexOption.IGNORE_CASE),
            Regex("""\s*\[.*?]"""),  // Square-bracket annotations
            Regex("""\s*\(.*?book\s+\d+\)""", RegexOption.IGNORE_CASE), // "(Book 1)"
        )
    }

    // -------------------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------------------

    /**
     * Generate a deterministic ID for a book from its title and author.
     *
     * The ID is a hex-encoded hash of the normalised title + author,
     * giving stable identity even if the raw strings have minor variations.
     */
    fun generateBookId(title: String, author: String): String {
        val normalizedKey = "${normalizeTitle(title)}|${normalizeAuthor(author)}"
        val hash = normalizedKey.hashCode()
        return "bksync_${hash.toUInt().toString(16)}"
    }

    /**
     * Attempt to match a newly detected book (from either platform) against
     * existing entries in the database from the other platform.
     *
     * If a match is found, the two entries are merged and returned.
     * If no match is found, returns null.
     */
    suspend fun attemptMatch(
        title: String,
        author: String,
        source: BookSource
    ): BookSyncEntity? {
        val allBooks = bookSyncRepository.getAllBooks()

        // Find candidates from the *other* source.
        val candidates = allBooks.filter { book ->
            when (source) {
                BookSource.KINDLE -> book.audibleProgress != null && book.kindleProgress == null
                BookSource.AUDIBLE -> book.kindleProgress != null && book.audibleProgress == null
            }
        }

        val normalizedTitle = normalizeTitle(title)
        val normalizedAuthor = normalizeAuthor(author)

        for (candidate in candidates) {
            val candidateTitle = normalizeTitle(candidate.title)
            val candidateAuthor = normalizeAuthor(candidate.author)

            val titleMatch = titlesMatch(normalizedTitle, candidateTitle)
            val authorMatch = authorsMatch(normalizedAuthor, candidateAuthor)

            if (titleMatch && authorMatch) {
                Log.d(TAG, "Matched: '$title' <-> '${candidate.title}'")
                return candidate
            }
        }

        return null
    }

    /**
     * Merge two separate book entries (one Kindle, one Audible) into a
     * single consolidated entry.
     *
     * @param kindleEntry The entry with Kindle data.
     * @param audibleEntry The entry with Audible data.
     * @return The merged entry, ready for upserting.
     */
    fun mergeEntries(kindleEntry: BookSyncEntity, audibleEntry: BookSyncEntity): BookSyncEntity {
        // Use the entry with the most complete title/author as the base.
        val baseTitle = if (kindleEntry.title.length >= audibleEntry.title.length) {
            kindleEntry.title
        } else {
            audibleEntry.title
        }
        val baseAuthor = if (kindleEntry.author.isNotBlank()) {
            kindleEntry.author
        } else {
            audibleEntry.author
        }

        val mergedId = generateBookId(baseTitle, baseAuthor)

        return BookSyncEntity(
            id = mergedId,
            title = baseTitle,
            author = baseAuthor,
            kindleProgress = kindleEntry.kindleProgress,
            kindleLastPage = kindleEntry.kindleLastPage,
            kindleTotalPages = kindleEntry.kindleTotalPages,
            kindleChapter = kindleEntry.kindleChapter,
            kindleLastSync = kindleEntry.kindleLastSync,
            audibleProgress = audibleEntry.audibleProgress,
            audibleChapter = audibleEntry.audibleChapter,
            audiblePositionMs = audibleEntry.audiblePositionMs,
            audibleTotalMs = audibleEntry.audibleTotalMs,
            audibleLastSync = audibleEntry.audibleLastSync,
            coverUrl = audibleEntry.coverUrl ?: kindleEntry.coverUrl,
            lastUpdated = System.currentTimeMillis()
        )
    }

    // -------------------------------------------------------------------------------------
    // Normalization
    // -------------------------------------------------------------------------------------

    /**
     * Normalize a book title for comparison:
     * - Lowercase
     * - Strip noise suffixes (Unabridged, A Novel, etc.)
     * - Remove non-alphanumeric characters except spaces
     * - Collapse multiple spaces
     */
    fun normalizeTitle(title: String): String {
        var normalized = title.lowercase().trim()
        for (pattern in NOISE_PATTERNS) {
            normalized = pattern.replace(normalized, "")
        }
        normalized = normalized.replace(Regex("[^a-z0-9\\s]"), "")
        normalized = normalized.replace(Regex("\\s+"), " ").trim()
        return normalized
    }

    /**
     * Normalize an author name for comparison:
     * - Lowercase
     * - Remove non-alphanumeric except spaces
     * - Collapse spaces
     */
    private fun normalizeAuthor(author: String): String {
        return author.lowercase().trim()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // -------------------------------------------------------------------------------------
    // Matching heuristics
    // -------------------------------------------------------------------------------------

    /**
     * Check if two normalized titles match — either exactly or within
     * the allowed edit distance.
     */
    private fun titlesMatch(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.isBlank() || b.isBlank()) return false

        // One title contains the other (handles subtitle differences).
        if (a.contains(b) || b.contains(a)) return true

        // Levenshtein fuzzy match.
        val distance = levenshteinDistance(a, b)
        val shorter = minOf(a.length, b.length)
        if (shorter == 0) return false

        val ratio = distance.toFloat() / shorter.toFloat()
        return ratio <= MAX_EDIT_DISTANCE_RATIO
    }

    /**
     * Check if two normalized author names overlap.
     *
     * Authors are considered matching if:
     * - They are the same string
     * - One contains the other (handles "J.K. Rowling" vs "Rowling")
     * - They share at least one word of 4+ characters (last name)
     * - Either is blank (we don't penalise missing author data)
     */
    private fun authorsMatch(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return true // Be lenient with missing authors
        if (a == b) return true
        if (a.contains(b) || b.contains(a)) return true

        // Check for shared last name (any word >= 4 chars).
        val wordsA = a.split(" ").filter { it.length >= 4 }.toSet()
        val wordsB = b.split(" ").filter { it.length >= 4 }.toSet()
        return wordsA.intersect(wordsB).isNotEmpty()
    }

    // -------------------------------------------------------------------------------------
    // Levenshtein distance
    // -------------------------------------------------------------------------------------

    /**
     * Compute the Levenshtein (edit) distance between two strings.
     * Uses the classic dynamic programming approach with O(min(m,n)) space.
     */
    private fun levenshteinDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length

        // Optimise by making `a` the shorter string.
        if (m > n) return levenshteinDistance(b, a)

        var previousRow = IntArray(m + 1) { it }
        var currentRow = IntArray(m + 1)

        for (j in 1..n) {
            currentRow[0] = j
            for (i in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                currentRow[i] = minOf(
                    currentRow[i - 1] + 1,       // insertion
                    previousRow[i] + 1,           // deletion
                    previousRow[i - 1] + cost     // substitution
                )
            }
            val temp = previousRow
            previousRow = currentRow
            currentRow = temp
        }

        return previousRow[m]
    }
}

/**
 * Indicates which platform a book was detected on.
 */
enum class BookSource {
    KINDLE, AUDIBLE
}
