package com.castor.feature.messaging.ui

import androidx.lifecycle.ViewModel
import com.castor.core.common.model.CastorMessage
import com.castor.core.data.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class MessagingViewModel @Inject constructor(
    messageRepository: MessageRepository
) : ViewModel() {
    val messages: Flow<List<CastorMessage>> = messageRepository.getAllMessages()
}
