package com.castor.feature.notifications

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cached information about a notification's reply action.
 *
 * @param action The [Notification.Action] that contains a [RemoteInput].
 * @param remoteInput The first [RemoteInput] attached to [action].
 * @param timestampCached The wall-clock time when this entry was cached.
 */
data class CachedReplyAction(
    val action: Notification.Action,
    val remoteInput: RemoteInput,
    val timestampCached: Long = System.currentTimeMillis()
)

/**
 * Manages sending replies back through notification [RemoteInput] actions.
 *
 * The [CastorNotificationListener] registers reply actions when notifications arrive, and this
 * manager provides a clean API for other components to send replies or check reply availability.
 *
 * Cached actions are automatically expired after [ACTION_TTL_MS] to avoid holding references to
 * stale [android.app.PendingIntent]s.
 */
@Singleton
class ReplyManager @Inject constructor() {

    companion object {
        private const val TAG = "ReplyManager"

        /** Time-to-live for cached reply actions (15 minutes). */
        private const val ACTION_TTL_MS = 15 * 60 * 1000L
    }

    /** Cached reply actions keyed by the notification key (StatusBarNotification.key). */
    private val replyActions = ConcurrentHashMap<String, CachedReplyAction>()

    // -------------------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------------------

    /**
     * Extracts and caches the reply action from a [StatusBarNotification], if present.
     *
     * Should be called from [CastorNotificationListener.onNotificationPosted].
     */
    fun cacheReplyAction(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val action = findReplyAction(notification) ?: return
        val remoteInput = action.remoteInputs?.firstOrNull() ?: return

        replyActions[sbn.key] = CachedReplyAction(
            action = action,
            remoteInput = remoteInput
        )
    }

    /**
     * Removes the cached reply action for the given notification key.
     *
     * Should be called from [CastorNotificationListener.onNotificationRemoved].
     */
    fun removeCachedAction(notificationKey: String) {
        replyActions.remove(notificationKey)
    }

    // -------------------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------------------

    /**
     * Returns `true` if there is a valid (non-expired) reply action cached for
     * [notificationKey].
     */
    fun canReply(notificationKey: String): Boolean {
        purgeExpired()
        return replyActions.containsKey(notificationKey)
    }

    /**
     * Returns the [CachedReplyAction] for the given key, or `null` if it does not exist or has
     * expired.
     */
    fun getReplyAction(notificationKey: String): CachedReplyAction? {
        purgeExpired()
        return replyActions[notificationKey]
    }

    // -------------------------------------------------------------------------------------
    // Reply
    // -------------------------------------------------------------------------------------

    /**
     * Sends a reply through the notification's [RemoteInput] mechanism.
     *
     * @param notificationKey The key of the notification to reply to.
     * @param replyText The text to send as a reply.
     * @return [Result.success] if the reply was sent, [Result.failure] with an appropriate
     *   exception otherwise.
     */
    fun replyToMessage(notificationKey: String, replyText: String): Result<Unit> {
        val cached = getReplyAction(notificationKey)
            ?: return Result.failure(
                IllegalStateException("No reply action cached for key: $notificationKey")
            )

        return try {
            val intent = Intent()
            val bundle = Bundle().apply {
                putCharSequence(cached.remoteInput.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(cached.action.remoteInputs, intent, bundle)
            cached.action.actionIntent.send(/* context = */ null, /* code = */ 0, intent)

            Log.d(TAG, "Reply sent successfully for key=$notificationKey")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send reply for key=$notificationKey", e)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------------------
    // Maintenance
    // -------------------------------------------------------------------------------------

    /**
     * Removes all cached actions that have exceeded the TTL.
     */
    fun purgeExpired() {
        val now = System.currentTimeMillis()
        val expiredKeys = replyActions.entries
            .filter { (_, cached) -> now - cached.timestampCached > ACTION_TTL_MS }
            .map { it.key }

        expiredKeys.forEach { key ->
            replyActions.remove(key)
            Log.d(TAG, "Purged expired reply action for key=$key")
        }
    }

    /**
     * Clears all cached reply actions. Useful when the notification listener is disconnected.
     */
    fun clearAll() {
        replyActions.clear()
        Log.d(TAG, "All cached reply actions cleared")
    }

    // -------------------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------------------

    /**
     * Finds the first [Notification.Action] on [notification] that has a [RemoteInput],
     * i.e., supports inline reply.
     */
    private fun findReplyAction(notification: Notification): Notification.Action? {
        return notification.actions?.firstOrNull { action ->
            action.remoteInputs?.isNotEmpty() == true
        }
    }
}
