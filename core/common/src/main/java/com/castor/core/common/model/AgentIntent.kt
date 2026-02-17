package com.castor.core.common.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface AgentIntent {
    @Serializable
    data class SendMessage(val recipient: String, val content: String, val source: MessageSource) : AgentIntent

    @Serializable
    data class PlayMedia(val query: String, val source: MediaSource? = null) : AgentIntent

    @Serializable
    data class QueueMedia(val query: String, val source: MediaSource? = null) : AgentIntent

    @Serializable
    data class SetReminder(val description: String, val timeDescription: String) : AgentIntent

    @Serializable
    data class Summarize(val context: String) : AgentIntent

    @Serializable
    data class GeneralQuery(val query: String) : AgentIntent
}
