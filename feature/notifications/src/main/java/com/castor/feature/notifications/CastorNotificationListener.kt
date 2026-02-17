package com.castor.feature.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.castor.core.common.model.CastorMessage
import com.castor.core.common.model.MessageSource
import com.castor.core.data.repository.MessageRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Listens for incoming notifications from monitored messaging apps (WhatsApp, Teams), parses
 * them via [NotificationParser], persists new messages through [MessageRepository], and caches
 * reply actions in [ReplyManager] so Castor can reply without opening the source app.
 *
 * Key capabilities:
 * - Delegates all parsing logic to [NotificationParser].
 * - Deduplicates messages that arrive via notification updates (same key, same content).
 * - Tracks active notification keys in a [ConcurrentHashMap] for efficient reply lookup.
 * - Emits new messages on a [SharedFlow] for real-time UI updates.
 * - Cleans up cached reply actions when notifications are removed.
 */
@AndroidEntryPoint
class CastorNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "CastorNotifListener"

        /**
         * Singleton reference so that other components (e.g., ReplyManager, ViewModels) can
         * access the live instance. Set in [onListenerConnected], cleared in
         * [onListenerDisconnected].
         */
        @Volatile
        var instance: CastorNotificationListener? = null
            private set
    }

    @Inject lateinit var messageRepository: MessageRepository
    @Inject lateinit var notificationParser: NotificationParser
    @Inject lateinit var replyManager: ReplyManager

    /** Coroutine scope tied to this service's lifecycle. Cancelled in [onListenerDisconnected]. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Package names of apps we monitor. */
    private val monitoredPackages = setOf(
        MessageSource.WHATSAPP.packageName,
        MessageSource.TEAMS.packageName
    )

    /**
     * Tracks the content hash of the last processed message per notification key.
     * Used for deduplication when a notification is updated (same key) with the same content.
     */
    private val lastContentHashByKey = ConcurrentHashMap<String, Int>()

    /**
     * Maps notification keys to their corresponding [MessageSource]. Used for quick reverse
     * lookups when a notification is removed.
     */
    private val keyToSource = ConcurrentHashMap<String, MessageSource>()

    /** Emits newly persisted [CastorMessage]s for real-time UI consumption. */
    private val _newMessages = MutableSharedFlow<CastorMessage>(extraBufferCapacity = 64)

    /** Public read-only flow of new messages. */
    val newMessages: SharedFlow<CastorMessage> = _newMessages.asSharedFlow()

    // -------------------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------------------

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        replyManager.clearAll()
        lastContentHashByKey.clear()
        keyToSource.clear()
        scope.cancel()
        Log.i(TAG, "Notification listener disconnected")
    }

    // -------------------------------------------------------------------------------------
    // Notification events
    // -------------------------------------------------------------------------------------

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Only process notifications from monitored packages.
        if (sbn.packageName !in monitoredPackages) return

        val source = MessageSource.fromPackageName(sbn.packageName) ?: return

        // Always cache the reply action regardless of whether we persist the message.
        replyManager.cacheReplyAction(sbn)

        // Parse the notification.
        val parsed = notificationParser.parse(sbn) ?: return

        // Deduplication: skip if the same key delivered the same content.
        val contentHash = computeContentHash(parsed)
        val previousHash = lastContentHashByKey.put(sbn.key, contentHash)
        if (previousHash == contentHash) {
            Log.d(TAG, "Duplicate notification skipped: key=${sbn.key}")
            return
        }

        // Track the source for this key.
        keyToSource[sbn.key] = source

        // Persist and emit.
        scope.launch {
            try {
                val messageId = UUID.randomUUID().toString()
                messageRepository.addMessage(
                    source = source,
                    sender = parsed.sender,
                    content = parsed.content,
                    groupName = parsed.groupName,
                    notificationKey = sbn.key
                )

                val message = CastorMessage(
                    id = messageId,
                    source = source,
                    sender = parsed.sender,
                    content = parsed.content,
                    groupName = parsed.groupName,
                    timestamp = System.currentTimeMillis(),
                    isRead = false,
                    notificationKey = sbn.key
                )

                _newMessages.tryEmit(message)
                Log.d(TAG, "Message persisted: sender=${parsed.sender}, group=${parsed.groupName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist message from key=${sbn.key}", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName !in monitoredPackages) return

        // Clean up cached reply action.
        replyManager.removeCachedAction(sbn.key)

        // Clean up deduplication and source tracking maps.
        lastContentHashByKey.remove(sbn.key)
        keyToSource.remove(sbn.key)

        Log.d(TAG, "Notification removed: key=${sbn.key}")
    }

    // -------------------------------------------------------------------------------------
    // Public API for replying
    // -------------------------------------------------------------------------------------

    /**
     * Replies to a notification identified by its key.
     *
     * Delegates to [ReplyManager.replyToMessage] which uses the cached [android.app.RemoteInput]
     * action.
     *
     * @param notificationKey The notification key (as stored in [CastorMessage.notificationKey]).
     * @param replyText The text to send.
     * @return A [Result] indicating success or failure.
     */
    fun replyToNotification(notificationKey: String, replyText: String): Result<Unit> {
        return replyManager.replyToMessage(notificationKey, replyText)
    }

    /**
     * Returns the cached reply action for a notification, or `null` if no reply action is
     * available (expired, removed, or never had one).
     */
    fun getReplyAction(notificationKey: String): CachedReplyAction? {
        return replyManager.getReplyAction(notificationKey)
    }

    /**
     * Returns `true` if there is a valid reply action for the given notification key.
     */
    fun canReply(notificationKey: String): Boolean {
        return replyManager.canReply(notificationKey)
    }

    // -------------------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------------------

    /**
     * Computes a simple hash from the parsed notification content. Used for deduplication.
     */
    private fun computeContentHash(parsed: ParsedNotification): Int {
        var result = parsed.sender.hashCode()
        result = 31 * result + parsed.content.hashCode()
        result = 31 * result + (parsed.groupName?.hashCode() ?: 0)
        return result
    }
}
