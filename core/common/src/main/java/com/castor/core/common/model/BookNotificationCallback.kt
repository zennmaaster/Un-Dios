package com.castor.core.common.model

import android.service.notification.StatusBarNotification

/**
 * Callback interface for forwarding book-related notifications (Kindle)
 * from the [NotificationListenerService] to the book sync tracking pipeline.
 *
 * This interface lives in `:core:common` so that both `:feature:notifications`
 * (the producer) and `:feature:media` (the consumer) can depend on it
 * without creating a circular dependency between feature modules.
 */
interface BookNotificationCallback {
    /** Called when a notification from Kindle is posted. */
    fun onBookNotificationPosted(sbn: StatusBarNotification)

    /** Called when a notification from Kindle is removed. */
    fun onBookNotificationRemoved(sbn: StatusBarNotification)
}
