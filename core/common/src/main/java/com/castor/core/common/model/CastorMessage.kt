package com.castor.core.common.model

data class CastorMessage(
    val id: String,
    val source: MessageSource,
    val sender: String,
    val content: String,
    val groupName: String? = null,
    val timestamp: Long,
    val isRead: Boolean = false,
    val notificationKey: String? = null
)
