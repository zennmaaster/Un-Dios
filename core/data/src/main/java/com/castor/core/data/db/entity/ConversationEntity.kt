package com.castor.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val role: String, // "user" or "assistant"
    val content: String,
    val agentType: String,
    val timestamp: Long = System.currentTimeMillis()
)
