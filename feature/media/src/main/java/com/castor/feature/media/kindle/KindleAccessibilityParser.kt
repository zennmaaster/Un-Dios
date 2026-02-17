package com.castor.feature.media.kindle

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses reading progress from the Kindle app's UI tree using the
 * Android AccessibilityService framework.
 *
 * When the Kindle app is in the foreground, the [MediaAccessibilityService]
 * forwards [AccessibilityEvent]s to this parser. We traverse the node tree
 * looking for identifiable UI elements:
 *
 * - **Page indicator text**: "Page X of Y" or "Loc X of Y"
 * - **Progress bar nodes**: Seek-bar or progress-bar with `RangeInfo`
 * - **Chapter title text**: Usually a larger text near the top of the screen
 *
 * All heuristics here are best-effort — Kindle's view hierarchy may change
 * between app versions. If parsing fails, the tracker falls back to the
 * notification-based approach.
 *
 * **Privacy**: We only inspect nodes when the foreground app is
 * `com.amazon.kindle`. We never capture the actual reading content.
 */
@Singleton
class KindleAccessibilityParser @Inject constructor(
    private val kindleTracker: KindleTracker
) {
    companion object {
        private const val TAG = "KindleAccessParser"
        const val KINDLE_PACKAGE = "com.amazon.kindle"

        /** Matches "Page X of Y" or "Loc X of Y" or "Location X of Y" */
        private val PAGE_LOC_PATTERN = Regex(
            """(?:Page|Loc(?:ation)?)\s+(\d[\d,]*)\s+of\s+(\d[\d,]*)""",
            RegexOption.IGNORE_CASE
        )

        /** Matches "X% complete" or similar */
        private val PERCENT_COMPLETE_PATTERN = Regex(
            """(\d{1,3})%\s*(?:complete|done)?""",
            RegexOption.IGNORE_CASE
        )

        /** Matches "Chapter X: ..." or "Chapter X" */
        private val CHAPTER_PATTERN = Regex(
            """Chapter\s+(\d+)(?:\s*[:.\-]\s*(.+))?""",
            RegexOption.IGNORE_CASE
        )
    }

    /**
     * Called by the accessibility service when a window content change or
     * window state change event occurs in the Kindle app.
     */
    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() != KINDLE_PACKAGE) return

        val rootNode = event.source ?: return

        try {
            val result = parseNodeTree(rootNode)
            if (result != null) {
                kindleTracker.onAccessibilityUpdate(
                    bookTitle = result.bookTitle,
                    currentPage = result.currentPage,
                    totalPages = result.totalPages,
                    chapterName = result.chapterName,
                    progressPercent = result.progressPercent
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to parse Kindle accessibility tree", e)
        } finally {
            rootNode.recycle()
        }
    }

    // -------------------------------------------------------------------------------------
    // Tree traversal
    // -------------------------------------------------------------------------------------

    /**
     * Traverse the entire accessible node tree, collecting page info,
     * progress, and chapter data from matching nodes.
     */
    private fun parseNodeTree(root: AccessibilityNodeInfo): KindleAccessibilityResult? {
        var bookTitle: String? = null
        var currentPage: Int? = null
        var totalPages: Int? = null
        var progressPercent: Float? = null
        var chapterName: String? = null

        traverseNodes(root) { node ->
            // Check for progress/seek bar nodes.
            node.rangeInfo?.let { range ->
                if (range.max > 0) {
                    progressPercent = (range.current / range.max).coerceIn(0f, 1f)
                }
            }

            // Check text nodes for page / progress / chapter info.
            val text = node.text?.toString()
            if (!text.isNullOrBlank()) {
                // Page or Location pattern
                PAGE_LOC_PATTERN.find(text)?.let { match ->
                    currentPage = match.groupValues[1].replace(",", "").toIntOrNull()
                    totalPages = match.groupValues[2].replace(",", "").toIntOrNull()
                }

                // Percentage complete pattern
                PERCENT_COMPLETE_PATTERN.find(text)?.let { match ->
                    progressPercent = match.groupValues[1].toFloatOrNull()?.div(100f)
                }

                // Chapter pattern
                CHAPTER_PATTERN.find(text)?.let { match ->
                    val chapterNum = match.groupValues[1]
                    val chapterTitle = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
                    chapterName = if (chapterTitle != null) {
                        "Chapter $chapterNum: $chapterTitle"
                    } else {
                        "Chapter $chapterNum"
                    }
                }
            }

            // Content description may also carry page info.
            val contentDesc = node.contentDescription?.toString()
            if (!contentDesc.isNullOrBlank()) {
                PAGE_LOC_PATTERN.find(contentDesc)?.let { match ->
                    currentPage = match.groupValues[1].replace(",", "").toIntOrNull()
                    totalPages = match.groupValues[2].replace(",", "").toIntOrNull()
                }

                PERCENT_COMPLETE_PATTERN.find(contentDesc)?.let { match ->
                    progressPercent = match.groupValues[1].toFloatOrNull()?.div(100f)
                }
            }
        }

        // We need at least a progress percentage to be useful.
        if (progressPercent == null && currentPage == null) return null

        // Derive progress from pages if only pages are available.
        if (progressPercent == null && currentPage != null && totalPages != null && totalPages!! > 0) {
            progressPercent = currentPage!!.toFloat() / totalPages!!.toFloat()
        }

        return KindleAccessibilityResult(
            bookTitle = bookTitle,
            currentPage = currentPage,
            totalPages = totalPages,
            progressPercent = progressPercent,
            chapterName = chapterName
        )
    }

    /**
     * Depth-first traversal of the accessibility node tree.
     * Invokes [action] on each node.
     */
    private fun traverseNodes(
        node: AccessibilityNodeInfo,
        action: (AccessibilityNodeInfo) -> Unit
    ) {
        action(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                traverseNodes(child, action)
            } finally {
                child.recycle()
            }
        }
    }
}

/**
 * Result of parsing the Kindle accessibility node tree.
 */
data class KindleAccessibilityResult(
    val bookTitle: String?,
    val currentPage: Int?,
    val totalPages: Int?,
    val progressPercent: Float?,
    val chapterName: String?
)
