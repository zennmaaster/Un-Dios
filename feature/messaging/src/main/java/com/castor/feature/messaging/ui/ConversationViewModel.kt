package com.castor.feature.messaging.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.core.common.model.CastorMessage
import com.castor.core.common.model.MessageSource
import com.castor.core.data.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the conversation thread view.
 *
 * Navigation args (extracted from SavedStateHandle or passed directly):
 * - "sender"    — the conversation contact name
 * - "groupName" — optional group/channel name
 */
@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Sender resolved from navigation args. */
    val sender: String = savedStateHandle.get<String>("sender") ?: ""

    /** Optional group name from navigation args. */
    val groupName: String? = savedStateHandle.get<String>("groupName")

    /** All messages in this conversation thread, ordered chronologically (ASC). */
    val messages: StateFlow<List<CastorMessage>> =
        messageRepository.getConversationThread(sender, groupName)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Placeholder smart reply suggestions. Will be wired to the LLM agent later. */
    private val _smartReplies = MutableStateFlow(
        listOf(
            "Got it, thanks!",
            "Let me check and get back to you.",
            "Sounds good."
        )
    )
    val smartReplies: StateFlow<List<String>> = _smartReplies.asStateFlow()

    /** Current text in the reply input field. */
    private val _replyText = MutableStateFlow("")
    val replyText: StateFlow<String> = _replyText.asStateFlow()

    /** Whether a thread summary is currently being generated (placeholder). */
    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing.asStateFlow()

    /** Placeholder thread summary. Will be wired to the LLM summarizer agent later. */
    private val _threadSummary = MutableStateFlow<String?>(null)
    val threadSummary: StateFlow<String?> = _threadSummary.asStateFlow()

    // ---- Actions ----

    fun updateReplyText(text: String) {
        _replyText.value = text
    }

    /**
     * Sends a reply message to this conversation.
     *
     * Currently stores the reply locally in the database. The actual cross-app reply
     * mechanism (e.g., replying via notification action) will be connected through
     * the ReplyManager in a future phase.
     */
    fun sendReply() {
        val text = _replyText.value.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            // Determine the source from existing messages, or default to WhatsApp
            val source = messages.value.firstOrNull()?.source ?: MessageSource.WHATSAPP

            messageRepository.addMessage(
                source = source,
                sender = "You",
                content = text,
                groupName = groupName,
                notificationKey = null
            )
            _replyText.value = ""
        }
    }

    /**
     * Uses a smart reply chip to populate and send a reply.
     */
    fun sendSmartReply(reply: String) {
        _replyText.value = reply
        sendReply()
    }

    /**
     * Triggers a thread summarization.
     *
     * Placeholder: generates a mock summary. Will be connected to the on-device
     * LLM summarizer agent (via :core:inference) in a later phase.
     */
    fun summarizeThread() {
        viewModelScope.launch {
            _isSummarizing.value = true
            // Placeholder — simulate summarization delay
            kotlinx.coroutines.delay(800)
            val msgCount = messages.value.size
            val participants = messages.value.map { it.sender }.distinct()
            _threadSummary.value = buildString {
                append("Thread summary ($msgCount messages")
                if (participants.size > 1) {
                    append(", ${participants.size} participants")
                }
                append("): ")
                append("This conversation covers recent exchanges between ")
                append(participants.joinToString(", "))
                append(". Key topics discussed include the latest messages in the thread.")
            }
            _isSummarizing.value = false
        }
    }

    /** Dismisses the thread summary. */
    fun dismissSummary() {
        _threadSummary.value = null
    }

    /** Marks all messages in this conversation as read. */
    fun markConversationAsRead() {
        viewModelScope.launch {
            messageRepository.markConversationAsRead(sender, groupName)
        }
    }
}
