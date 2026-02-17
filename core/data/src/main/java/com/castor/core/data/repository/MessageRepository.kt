package com.castor.core.data.repository

import com.castor.core.common.model.CastorMessage
import com.castor.core.common.model.MessageSource
import com.castor.core.data.db.dao.MessageDao
import com.castor.core.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val messageDao: MessageDao
) {

    // -------------------------------------------------------------------------------------
    // Read — all messages
    // -------------------------------------------------------------------------------------

    fun getAllMessages(): Flow<List<CastorMessage>> =
        messageDao.getAllMessages().map { entities -> entities.map { it.toDomain() } }

    fun getMessagesBySource(source: MessageSource): Flow<List<CastorMessage>> =
        messageDao.getMessagesBySource(source.name).map { entities -> entities.map { it.toDomain() } }

    fun getUnreadMessages(): Flow<List<CastorMessage>> =
        messageDao.getUnreadMessages().map { entities -> entities.map { it.toDomain() } }

    fun getUnreadCount(): Flow<Int> = messageDao.getUnreadCount()

    // -------------------------------------------------------------------------------------
    // Read — by sender / group
    // -------------------------------------------------------------------------------------

    fun getMessagesBySender(sender: String): Flow<List<CastorMessage>> =
        messageDao.getMessagesBySender(sender).map { entities -> entities.map { it.toDomain() } }

    fun getMessagesBySenderAndGroup(sender: String, groupName: String): Flow<List<CastorMessage>> =
        messageDao.getMessagesBySenderAndGroup(sender, groupName)
            .map { entities -> entities.map { it.toDomain() } }

    fun getMessagesByGroup(groupName: String): Flow<List<CastorMessage>> =
        messageDao.getMessagesByGroup(groupName).map { entities -> entities.map { it.toDomain() } }

    // -------------------------------------------------------------------------------------
    // Read — conversations & threading
    // -------------------------------------------------------------------------------------

    /**
     * Returns the full conversation thread for a sender within an optional group.
     * If [groupName] is null, returns only DM messages for that sender.
     * If [groupName] is provided, returns messages within that group for that sender.
     */
    fun getConversationMessages(sender: String, groupName: String?): Flow<List<CastorMessage>> =
        if (groupName.isNullOrEmpty()) {
            getMessagesBySender(sender)
        } else {
            getMessagesBySenderAndGroup(sender, groupName)
        }

    /**
     * Returns the full conversation thread for a sender + optional group using the dedicated
     * DAO query that handles NULL groupName comparisons correctly.
     */
    fun getConversationThread(sender: String, groupName: String?): Flow<List<CastorMessage>> =
        messageDao.getConversationThread(sender, groupName)
            .map { entities -> entities.map { it.toDomain() } }

    /**
     * Returns a [Flow] of the most recent conversations, each represented by its latest message.
     * Conversations are grouped by (sender, groupName) and ordered by most recent first.
     */
    fun getRecentConversations(): Flow<List<CastorMessage>> =
        messageDao.getRecentConversations().map { entities -> entities.map { it.toDomain() } }

    // -------------------------------------------------------------------------------------
    // Read — discovery
    // -------------------------------------------------------------------------------------

    /** Returns a [Flow] of distinct group names that have at least one message. */
    fun getActiveGroups(): Flow<List<String>> = messageDao.getActiveGroups()

    /** Returns a [Flow] of distinct sender names ordered by most recent message. */
    fun getActiveSenders(): Flow<List<String>> = messageDao.getActiveSenders()

    // -------------------------------------------------------------------------------------
    // Read — single message
    // -------------------------------------------------------------------------------------

    /** Returns a single message by its ID, or `null` if not found. */
    suspend fun getMessageById(id: String): CastorMessage? =
        messageDao.getMessageById(id)?.toDomain()

    // -------------------------------------------------------------------------------------
    // Search
    // -------------------------------------------------------------------------------------

    /**
     * Searches messages by content or sender using a LIKE query.
     * Returns a [Flow] of matching messages, limited to [limit] results.
     */
    fun searchMessages(query: String, limit: Int = 20): Flow<List<CastorMessage>> =
        messageDao.searchMessages(query, limit).map { entities -> entities.map { it.toDomain() } }

    // -------------------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------------------

    suspend fun addMessage(
        source: MessageSource,
        sender: String,
        content: String,
        groupName: String? = null,
        notificationKey: String? = null
    ) {
        messageDao.insertMessage(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                source = source.name,
                sender = sender,
                content = content,
                groupName = groupName,
                timestamp = System.currentTimeMillis(),
                notificationKey = notificationKey
            )
        )
    }

    // -------------------------------------------------------------------------------------
    // Update — read status
    // -------------------------------------------------------------------------------------

    suspend fun markAsRead(messageId: String) = messageDao.markAsRead(messageId)

    suspend fun markConversationAsRead(sender: String, groupName: String?) {
        if (groupName.isNullOrEmpty()) {
            messageDao.markConversationAsRead(sender)
        } else {
            messageDao.markConversationAsReadByGroup(sender, groupName)
        }
    }

    suspend fun markAllAsRead() = messageDao.markAllAsRead()

    // -------------------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------------------

    suspend fun deleteMessage(messageId: String) = messageDao.deleteMessage(messageId)

    /** Deletes all messages older than the given timestamp. */
    suspend fun deleteOlderThan(olderThan: Long) = messageDao.deleteOlderThan(olderThan)

    // -------------------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------------------

    private fun MessageEntity.toDomain() = CastorMessage(
        id = id,
        source = MessageSource.valueOf(source),
        sender = sender,
        content = content,
        groupName = groupName,
        timestamp = timestamp,
        isRead = isRead,
        notificationKey = notificationKey
    )
}
