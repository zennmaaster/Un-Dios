package com.castor.agent.orchestrator

import com.castor.core.common.model.AgentType
import com.castor.core.data.db.dao.ConversationDao
import com.castor.core.data.db.entity.ConversationEntity
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// =========================================================================================
// Data model
// =========================================================================================

/**
 * A single turn in a conversation between the user and the assistant.
 *
 * @param role      Either "user" or "assistant".
 * @param content   The text content of the turn.
 * @param agentType Which agent handled (or should handle) this turn.
 * @param timestamp Epoch millis when the turn was created.
 */
data class ConversationTurn(
    val role: String,
    val content: String,
    val agentType: AgentType,
    val timestamp: Long
)

// =========================================================================================
// ConversationManager
// =========================================================================================

/**
 * Manages multi-turn conversation context by persisting turns to the Room database and
 * providing methods to retrieve, format, and clear conversation history.
 *
 * The conversation history is used to:
 * 1. Give the LLM context about prior turns so it can handle follow-up questions.
 * 2. Track which agent handled each turn for analytics and routing improvements.
 * 3. Build a context prompt string that can be prepended to LLM calls.
 *
 * Usage:
 * - Call [addTurn] after every user input and every assistant response.
 * - Call [getRecentContext] to retrieve recent conversation turns for display.
 * - Call [buildContextPrompt] to create a text block suitable for LLM context injection.
 * - Call [clearHistory] to reset the conversation (e.g. on user request or session end).
 * - Call [pruneOldHistory] periodically to remove stale conversation data.
 */
@Singleton
class ConversationManager @Inject constructor(
    private val conversationDao: ConversationDao
) {

    companion object {
        /** Maximum number of turns to include in an LLM context prompt. */
        private const val DEFAULT_CONTEXT_LIMIT = 10

        /** Maximum age of conversation turns to keep (7 days). */
        private val MAX_HISTORY_AGE_MS = TimeUnit.DAYS.toMillis(7)

        /** Roles used in conversation turns. */
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_SYSTEM = "system"
    }

    // -------------------------------------------------------------------------------------
    // Public API — write
    // -------------------------------------------------------------------------------------

    /**
     * Add a new conversation turn to the persistent history.
     *
     * @param role      The speaker role: [ROLE_USER], [ROLE_ASSISTANT], or [ROLE_SYSTEM].
     * @param content   The text content of the turn.
     * @param agentType The agent that handled or will handle this turn.
     */
    suspend fun addTurn(role: String, content: String, agentType: AgentType) {
        val entity = ConversationEntity(
            id = UUID.randomUUID().toString(),
            role = role,
            content = content,
            agentType = agentType.name,
            timestamp = System.currentTimeMillis()
        )
        conversationDao.insert(entity)
    }

    /**
     * Convenience: record a user turn.
     */
    suspend fun addUserTurn(content: String, agentType: AgentType = AgentType.ROUTER) {
        addTurn(ROLE_USER, content, agentType)
    }

    /**
     * Convenience: record an assistant turn.
     */
    suspend fun addAssistantTurn(content: String, agentType: AgentType) {
        addTurn(ROLE_ASSISTANT, content, agentType)
    }

    // -------------------------------------------------------------------------------------
    // Public API — read
    // -------------------------------------------------------------------------------------

    /**
     * Retrieve the most recent conversation turns, ordered oldest-first so they read
     * naturally top-to-bottom.
     *
     * @param limit Maximum number of turns to return.
     * @return List of [ConversationTurn] in chronological order.
     */
    suspend fun getRecentContext(limit: Int = DEFAULT_CONTEXT_LIMIT): List<ConversationTurn> {
        return try {
            val entities = conversationDao.getRecentConversations(limit).first()
            // The DAO returns newest-first; reverse to chronological order.
            entities.reversed().map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Retrieve recent turns handled by a specific agent type.
     *
     * @param agentType The agent to filter by.
     * @param limit     Maximum number of turns to return.
     * @return List of [ConversationTurn] in chronological order.
     */
    suspend fun getContextByAgent(
        agentType: AgentType,
        limit: Int = DEFAULT_CONTEXT_LIMIT
    ): List<ConversationTurn> {
        return try {
            val entities = conversationDao.getConversationsByAgent(agentType.name, limit).first()
            entities.reversed().map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Build a formatted context string suitable for prepending to an LLM prompt.
     *
     * The format is:
     * ```
     * [Previous conversation]
     * User: How is the weather?
     * Assistant: It looks sunny today with a high of 75F.
     * User: What about tomorrow?
     * ```
     *
     * @param limit Maximum number of turns to include.
     * @return A formatted context string, or an empty string if there is no history.
     */
    suspend fun buildContextPrompt(limit: Int = 5): String {
        val turns = getRecentContext(limit)
        if (turns.isEmpty()) return ""

        val lines = turns.joinToString("\n") { turn ->
            val roleLabel = when (turn.role) {
                ROLE_USER -> "User"
                ROLE_ASSISTANT -> "Assistant"
                ROLE_SYSTEM -> "System"
                else -> turn.role.replaceFirstChar { it.uppercase() }
            }
            "$roleLabel: ${turn.content}"
        }

        return "[Previous conversation]\n$lines\n"
    }

    /**
     * Get the last user message, if any. Useful for detecting follow-up context.
     */
    suspend fun getLastUserMessage(): ConversationTurn? {
        val recent = getRecentContext(limit = 5)
        return recent.lastOrNull { it.role == ROLE_USER }
    }

    /**
     * Get the last assistant response, if any. Useful for context when the user
     * references something the assistant just said.
     */
    suspend fun getLastAssistantResponse(): ConversationTurn? {
        val recent = getRecentContext(limit = 5)
        return recent.lastOrNull { it.role == ROLE_ASSISTANT }
    }

    /**
     * Check whether there is any recent conversation context available.
     */
    suspend fun hasContext(): Boolean {
        return try {
            val entities = conversationDao.getRecentConversations(1).first()
            entities.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    // -------------------------------------------------------------------------------------
    // Public API — maintenance
    // -------------------------------------------------------------------------------------

    /**
     * Clear all conversation history. Useful when the user explicitly requests it
     * or on session boundaries.
     */
    suspend fun clearHistory() {
        try {
            conversationDao.deleteOlderThan(Long.MAX_VALUE)
        } catch (e: Exception) {
            // Database error — silently ignore; history will self-prune anyway.
        }
    }

    /**
     * Remove conversation turns older than [MAX_HISTORY_AGE_MS]. Should be called
     * periodically (e.g. from a WorkManager task) to keep the database lean.
     */
    suspend fun pruneOldHistory() {
        try {
            val cutoff = System.currentTimeMillis() - MAX_HISTORY_AGE_MS
            conversationDao.deleteOlderThan(cutoff)
        } catch (e: Exception) {
            // Silently ignore pruning failures.
        }
    }

    // -------------------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------------------

    private fun ConversationEntity.toDomain(): ConversationTurn = ConversationTurn(
        role = role,
        content = content,
        agentType = try {
            AgentType.valueOf(agentType)
        } catch (e: IllegalArgumentException) {
            AgentType.GENERAL
        },
        timestamp = timestamp
    )
}
