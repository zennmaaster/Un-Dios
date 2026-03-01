package com.castor.app.desktop.clipboard

import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class ClipboardEntry(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceApp: String? = null,
    val isPinned: Boolean = false
)

class ClipboardHistoryManager(context: Context) {

    private val _history = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    val history: StateFlow<List<ClipboardEntry>> = _history.asStateFlow()

    private val maxEntries = 50

    init {
        val systemClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        systemClipboard.addPrimaryClipChangedListener {
            val clip = systemClipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: return@addPrimaryClipChangedListener
                if (text.isBlank()) return@addPrimaryClipChangedListener
                // Avoid duplicating the most recent entry
                val current = _history.value
                if (current.isNotEmpty() && current.first().content == text) return@addPrimaryClipChangedListener
                addEntry(text)
            }
        }
    }

    private fun addEntry(content: String, sourceApp: String? = null) {
        val entry = ClipboardEntry(content = content, sourceApp = sourceApp)
        val current = _history.value.toMutableList()
        current.add(0, entry)
        // Trim to max, but keep all pinned
        while (current.size > maxEntries) {
            val lastUnpinned = current.indexOfLast { !it.isPinned }
            if (lastUnpinned >= 0) current.removeAt(lastUnpinned) else break
        }
        _history.value = current
    }

    fun pinEntry(id: String) {
        _history.value = _history.value.map {
            if (it.id == id) it.copy(isPinned = true) else it
        }
    }

    fun unpinEntry(id: String) {
        _history.value = _history.value.map {
            if (it.id == id) it.copy(isPinned = false) else it
        }
    }

    fun removeEntry(id: String) {
        _history.value = _history.value.filter { it.id != id }
    }

    fun clearHistory() {
        _history.value = _history.value.filter { it.isPinned }
    }
}
