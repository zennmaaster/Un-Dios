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
    fun getAllMessages(): Flow<List<CastorMessage>> =
        messageDao.getAllMessages().map { entities -> entities.map { it.toDomain() } }

    fun getMessagesBySource(source: MessageSource): Flow<List<CastorMessage>> =
        messageDao.getMessagesBySource(source.name).map { entities -> entities.map { it.toDomain() } }

    fun getUnreadCount(): Flow<Int> = messageDao.getUnreadCount()

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

    suspend fun markAsRead(messageId: String) = messageDao.markAsRead(messageId)

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
