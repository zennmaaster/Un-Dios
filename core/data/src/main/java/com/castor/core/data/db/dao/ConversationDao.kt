package com.castor.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.castor.core.data.db.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentConversations(limit: Int = 50): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE agentType = :agentType ORDER BY timestamp DESC LIMIT :limit")
    fun getConversationsByAgent(agentType: String, limit: Int = 20): Flow<List<ConversationEntity>>

    @Insert
    suspend fun insert(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}
