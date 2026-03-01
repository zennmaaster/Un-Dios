package com.castor.agent.orchestrator

import android.util.Log
import com.castor.core.data.db.dao.MemoryDao
import com.castor.core.data.db.entity.MemoryEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages persistent agent memory backed by the encrypted Room database.
 *
 * The LLM can save and recall facts across sessions using `save_memory` /
 * `recall_memory` tools dispatched through this manager. Memories are
 * categorized for efficient retrieval:
 * - `"agent_note"`: Observations and context the agent wants to remember
 * - `"user_profile"`: Facts about the user (preferences, habits, names)
 *
 * All data stays on-device.
 */
@Singleton
class MemoryManager @Inject constructor(
    private val memoryDao: MemoryDao
) {

    companion object {
        private const val TAG = "MemoryManager"

        /** Max memories to include in the system prompt. */
        private const val MAX_PROMPT_MEMORIES = 20

        /** Max characters for the memory block in the system prompt. */
        private const val MAX_PROMPT_CHARS = 1500
    }

    /**
     * Save a memory entry. If a memory with the same category+key exists,
     * it is updated (upsert).
     */
    suspend fun saveMemory(category: String, key: String, value: String): Long {
        val existing = memoryDao.getByKey(category, key)
        val entity = if (existing != null) {
            existing.copy(
                value = value,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            MemoryEntity(
                category = category,
                key = key,
                value = value
            )
        }
        val id = memoryDao.upsert(entity)
        Log.d(TAG, "Saved memory: [$category] $key = $value (id=$id)")
        return id
    }

    /**
     * Recall memories by category, optionally filtering by a search term.
     */
    suspend fun recallMemory(category: String? = null, search: String? = null): List<MemoryEntity> {
        return when {
            category != null && search != null -> memoryDao.search(category, search)
            category != null -> memoryDao.getByCategory(category)
            search != null -> memoryDao.searchAll(search)
            else -> memoryDao.getRecent(MAX_PROMPT_MEMORIES)
        }
    }

    /**
     * Delete a memory by ID.
     */
    suspend fun deleteMemory(id: Long) {
        memoryDao.delete(id)
        Log.d(TAG, "Deleted memory id=$id")
    }

    /**
     * Build a memory block for injection into the system prompt.
     *
     * Format:
     * ```
     * # Memory
     * You have the following memories from previous sessions:
     * - [user_profile] favorite_music: jazz and lo-fi
     * - [agent_note] last_briefing: User prefers morning briefings at 8am
     * ```
     */
    suspend fun buildMemoryPromptBlock(): String {
        val memories = memoryDao.getRecent(MAX_PROMPT_MEMORIES)
        if (memories.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("# Memory")
        sb.appendLine("You have the following memories from previous sessions:")

        var totalChars = sb.length
        for (memory in memories) {
            val line = "- [${memory.category}] ${memory.key}: ${memory.value}"
            if (totalChars + line.length > MAX_PROMPT_CHARS) break
            sb.appendLine(line)
            totalChars += line.length
        }

        return sb.toString()
    }
}
