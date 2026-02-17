package com.castor.feature.messaging.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.core.common.model.CastorMessage
import com.castor.core.common.model.MessageSource
import com.castor.core.data.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Summary of a conversation thread, grouping messages by sender + groupName.
 * Used to render the conversation list in the split-pane messaging UI.
 */
data class ConversationSummary(
    val sender: String,
    val source: MessageSource,
    val groupName: String?,
    val lastMessage: String,
    val lastTimestamp: Long,
    val unreadCount: Int,
    val totalCount: Int,
    val notificationKey: String?,
    val isPinned: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MessagingViewModel @Inject constructor(
    private val messageRepository: MessageRepository
) : ViewModel() {

    /** All raw messages flowing from the database. */
    val messages: StateFlow<List<CastorMessage>> = messageRepository.getAllMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Global unread badge count. */
    val unreadCount: StateFlow<Int> = messageRepository.getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Currently active source filter (null = All). */
    private val _selectedFilter = MutableStateFlow<MessageSource?>(null)
    val selectedFilter: StateFlow<MessageSource?> = _selectedFilter.asStateFlow()

    /** Current search/grep query. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Set of pinned conversation keys (sender|groupName). */
    private val _pinnedConversations = MutableStateFlow<Set<String>>(emptySet())

    /** Currently selected conversation for the split-pane detail view. */
    private val _selectedConversation = MutableStateFlow<ConversationSummary?>(null)
    val selectedConversation: StateFlow<ConversationSummary?> = _selectedConversation.asStateFlow()

    /**
     * Conversations derived from messages + filter + search, grouped by sender+groupName.
     * Sorted: pinned first, then by most recent timestamp descending.
     */
    val conversations: StateFlow<List<ConversationSummary>> = combine(
        messages,
        _selectedFilter,
        _searchQuery,
        _pinnedConversations
    ) { allMessages, filter, query, pinned ->
        allMessages
            .let { msgs ->
                if (filter != null) msgs.filter { it.source == filter } else msgs
            }
            .let { msgs ->
                if (query.isNotBlank()) {
                    val q = query.lowercase()
                    msgs.filter {
                        it.sender.lowercase().contains(q) ||
                                it.content.lowercase().contains(q) ||
                                (it.groupName?.lowercase()?.contains(q) == true)
                    }
                } else msgs
            }
            .groupBy { conversationKey(it.sender, it.groupName) }
            .map { (key, msgs) ->
                val latest = msgs.maxByOrNull { it.timestamp } ?: msgs.first()
                ConversationSummary(
                    sender = latest.sender,
                    source = latest.source,
                    groupName = latest.groupName,
                    lastMessage = latest.content,
                    lastTimestamp = latest.timestamp,
                    unreadCount = msgs.count { !it.isRead },
                    totalCount = msgs.size,
                    notificationKey = latest.notificationKey,
                    isPinned = pinned.contains(key)
                )
            }
            .sortedWith(
                compareByDescending<ConversationSummary> { it.isPinned }
                    .thenByDescending { it.lastTimestamp }
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Actions ----

    fun setFilter(source: MessageSource?) {
        _selectedFilter.value = source
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectConversation(conversation: ConversationSummary?) {
        _selectedConversation.value = conversation
    }

    fun markAsRead(messageId: String) {
        viewModelScope.launch {
            messageRepository.markAsRead(messageId)
        }
    }

    fun markConversationAsRead(sender: String, groupName: String?) {
        viewModelScope.launch {
            messageRepository.markConversationAsRead(sender, groupName)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            messageRepository.markAllAsRead()
        }
    }

    fun togglePin(conversation: ConversationSummary) {
        val key = conversationKey(conversation.sender, conversation.groupName)
        _pinnedConversations.value = _pinnedConversations.value.let { current ->
            if (current.contains(key)) current - key else current + key
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            messageRepository.deleteMessage(messageId)
        }
    }

    /** Creates a stable key for grouping messages into conversations. */
    private fun conversationKey(sender: String, groupName: String?): String =
        if (groupName.isNullOrEmpty()) sender else "$sender|$groupName"
}
