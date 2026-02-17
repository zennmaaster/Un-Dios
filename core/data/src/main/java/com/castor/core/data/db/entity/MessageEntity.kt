package com.castor.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val source: String,
    val sender: String,
    val content: String,
    val groupName: String?,
    val timestamp: Long,
    val isRead: Boolean = false,
    val notificationKey: String?
)
