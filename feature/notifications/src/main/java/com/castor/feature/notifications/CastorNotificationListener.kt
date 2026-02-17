package com.castor.feature.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.castor.core.common.model.MessageSource
import com.castor.core.data.repository.MessageRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CastorNotificationListener : NotificationListenerService() {

    @Inject lateinit var messageRepository: MessageRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val monitoredPackages = setOf(
        MessageSource.WHATSAPP.packageName,
        MessageSource.TEAMS.packageName
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in monitoredPackages) return

        val source = MessageSource.fromPackageName(sbn.packageName) ?: return
        val notification = sbn.notification
        val extras = notification.extras

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: return

        // Determine sender and group
        val (sender, groupName) = when (source) {
            MessageSource.WHATSAPP -> parseWhatsAppNotification(title, text)
            MessageSource.TEAMS -> parseTeamsNotification(title, text)
        }

        scope.launch {
            messageRepository.addMessage(
                source = source,
                sender = sender,
                content = text,
                groupName = groupName,
                notificationKey = sbn.key
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Could mark messages as read when notification is dismissed
    }

    private fun parseWhatsAppNotification(title: String, text: String): Pair<String, String?> {
        // WhatsApp group format: "Group Name: Sender: Message"
        // WhatsApp DM format: "Sender: Message"
        return if (title.contains(":")) {
            val parts = title.split(":", limit = 2)
            parts[1].trim() to parts[0].trim()
        } else {
            title to null
        }
    }

    private fun parseTeamsNotification(title: String, text: String): Pair<String, String?> {
        // Teams format varies; title is usually the sender or channel
        return title to null
    }

    /**
     * Reply to a notification using RemoteInput.
     * Call this to reply directly from Castor without opening the original app.
     */
    fun replyToNotification(notificationKey: String, replyText: String): Boolean {
        val activeNotifications = activeNotifications ?: return false
        val notification = activeNotifications.find { it.key == notificationKey } ?: return false

        val replyAction = notification.notification.actions?.find { action ->
            action.remoteInputs?.isNotEmpty() == true
        } ?: return false

        val remoteInput = replyAction.remoteInputs?.firstOrNull() ?: return false

        val intent = android.content.Intent()
        val bundle = android.os.Bundle()
        bundle.putCharSequence(remoteInput.resultKey, replyText)
        android.app.RemoteInput.addResultsToIntent(replyAction.remoteInputs, intent, bundle)

        return try {
            replyAction.actionIntent.send(this, 0, intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
