package com.castor.app.desktop.clipboard

import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing a single entry in the clipboard history.
 *
 * @param id Unique identifier for this entry (UUID)
 * @param content The text content that was copied
 * @param timestamp Timestamp in milliseconds when this entry was recorded
 * @param sourceApp Optional package name or app name of the source application
 * @param isPinned Whether this entry is pinned (survives clear operations)
 */
data class ClipboardEntry(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val timestamp: Long,
    val sourceApp: String? = null,
    val isPinned: Boolean = false
)

/**
 * Clipboard history manager that monitors the system clipboard and maintains
 * a history of copied text entries.
 *
 * This manager is a Hilt [Singleton] that:
 * - Listens to Android's [ClipboardManager] for clipboard changes via
 *   [ClipboardManager.OnPrimaryClipChangedListener]
 * - Maintains a [MutableStateFlow] of clipboard history (max 50 entries)
 * - Supports pinning entries so they survive clear operations
 * - Provides functions to add, remove, pin, unpin, and clear entries
 *
 * The history is held in memory only and does not persist across app restarts.
 * A future enhancement could add Room or DataStore persistence.
 *
 * @param context Application context, injected via Hilt's [ApplicationContext]
 */
@Singleton
class ClipboardHistoryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Maximum number of entries to retain in the clipboard history. */
    private companion object {
        const val MAX_HISTORY_SIZE = 50
    }

    private val _history = MutableStateFlow<List<ClipboardEntry>>(emptyList())

    /** Observable clipboard history, newest entries first. */
    val history: StateFlow<List<ClipboardEntry>> = _history.asStateFlow()

    private val systemClipboard: ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    init {
        // Register listener for clipboard changes
        systemClipboard.addPrimaryClipChangedListener {
            val clip = systemClipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(context).toString()
                if (text.isNotBlank()) {
                    addEntry(text = text, sourceApp = clip.description?.label?.toString())
                }
            }
        }
    }

    /**
     * Adds a new text entry to the clipboard history.
     *
     * If the text is identical to the most recent entry, the duplicate
     * is not added. The history is capped at [MAX_HISTORY_SIZE] entries;
     * when the cap is reached, the oldest non-pinned entry is removed.
     *
     * @param text The clipboard text content
     * @param sourceApp Optional source application name or package
     */
    fun addEntry(text: String, sourceApp: String? = null) {
        _history.update { currentHistory ->
            // Don't add duplicate of the most recent entry
            if (currentHistory.isNotEmpty() && currentHistory.first().content == text) {
                return@update currentHistory
            }

            val newEntry = ClipboardEntry(
                content = text,
                timestamp = System.currentTimeMillis(),
                sourceApp = sourceApp
            )

            val updatedHistory = listOf(newEntry) + currentHistory

            // Trim to max size, preserving pinned items
            if (updatedHistory.size > MAX_HISTORY_SIZE) {
                val pinned = updatedHistory.filter { it.isPinned }
                val unpinned = updatedHistory.filter { !it.isPinned }
                val maxUnpinned = MAX_HISTORY_SIZE - pinned.size
                pinned + unpinned.take(maxUnpinned.coerceAtLeast(0))
            } else {
                updatedHistory
            }
        }
    }

    /**
     * Removes a specific entry from the clipboard history by its ID.
     *
     * @param id The unique identifier of the entry to remove
     */
    fun removeEntry(id: String) {
        _history.update { currentHistory ->
            currentHistory.filter { it.id != id }
        }
    }

    /**
     * Pins an entry so it stays at the top and survives clear operations.
     *
     * @param id The unique identifier of the entry to pin
     */
    fun pinEntry(id: String) {
        _history.update { currentHistory ->
            currentHistory.map { entry ->
                if (entry.id == id) entry.copy(isPinned = true) else entry
            }
        }
    }

    /**
     * Unpins a previously pinned entry.
     *
     * @param id The unique identifier of the entry to unpin
     */
    fun unpinEntry(id: String) {
        _history.update { currentHistory ->
            currentHistory.map { entry ->
                if (entry.id == id) entry.copy(isPinned = false) else entry
            }
        }
    }

    /**
     * Clears all non-pinned entries from the clipboard history.
     * Pinned entries are preserved.
     */
    fun clearHistory() {
        _history.update { currentHistory ->
            currentHistory.filter { it.isPinned }
        }
    }
}
