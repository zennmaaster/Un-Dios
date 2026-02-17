package com.castor.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.castor.core.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE source = :source ORDER BY timestamp DESC")
    fun getMessagesBySource(source: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sender = :sender AND (groupName IS NULL OR groupName = '') ORDER BY timestamp ASC")
    fun getMessagesBySender(sender: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sender = :sender AND groupName = :groupName ORDER BY timestamp ASC")
    fun getMessagesBySenderAndGroup(sender: String, groupName: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isRead = 0 ORDER BY timestamp DESC")
    fun getUnreadMessages(): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET isRead = 1 WHERE id = :messageId")
    suspend fun markAsRead(messageId: String)

    @Query("UPDATE messages SET isRead = 1 WHERE sender = :sender AND (groupName IS NULL OR groupName = '')")
    suspend fun markConversationAsRead(sender: String)

    @Query("UPDATE messages SET isRead = 1 WHERE sender = :sender AND groupName = :groupName")
    suspend fun markConversationAsReadByGroup(sender: String, groupName: String)

    @Query("UPDATE messages SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    // --- Phase 2.1: Enhanced notification queries ---

    @Query("SELECT * FROM messages WHERE groupName = :groupName ORDER BY timestamp DESC")
    fun getMessagesByGroup(groupName: String): Flow<List<MessageEntity>>

    @Query("SELECT DISTINCT groupName FROM messages WHERE groupName IS NOT NULL ORDER BY timestamp DESC")
    fun getActiveGroups(): Flow<List<String>>

    @Query("SELECT DISTINCT sender FROM messages ORDER BY timestamp DESC")
    fun getActiveSenders(): Flow<List<String>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: String): MessageEntity?

    @Query("DELETE FROM messages WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)

    @Query(
        """
        SELECT * FROM messages
        WHERE sender = :sender
        AND ((:groupName IS NULL AND groupName IS NULL) OR groupName = :groupName)
        ORDER BY timestamp ASC
        """
    )
    fun getConversationThread(sender: String, groupName: String?): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT m.* FROM messages m
        INNER JOIN (
            SELECT
                sender,
                COALESCE(groupName, '') AS grp,
                MAX(timestamp) AS maxTs
            FROM messages
            GROUP BY sender, COALESCE(groupName, '')
        ) latest
        ON m.sender = latest.sender
        AND COALESCE(m.groupName, '') = latest.grp
        AND m.timestamp = latest.maxTs
        ORDER BY m.timestamp DESC
        """
    )
    fun getRecentConversations(): Flow<List<MessageEntity>>
}
