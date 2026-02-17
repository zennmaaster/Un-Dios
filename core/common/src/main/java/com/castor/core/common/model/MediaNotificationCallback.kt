package com.castor.core.common.model

import android.service.notification.StatusBarNotification

/**
 * Callback interface for forwarding media-related notifications from the
 * [NotificationListenerService] to the recommendation tracking pipeline.
 *
 * This interface lives in `:core:common` so that both `:feature:notifications`
 * (the producer) and `:feature:recommendations` (the consumer) can depend on
 * it without creating a circular dependency between feature modules.
 */
interface MediaNotificationCallback {
    /** Called when a notification from a media/streaming app is posted. */
    fun onMediaNotificationPosted(sbn: StatusBarNotification)

    /** Called when a notification from a media/streaming app is removed. */
    fun onMediaNotificationRemoved(sbn: StatusBarNotification)
}
