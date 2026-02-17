package com.castor.feature.media.kindle

import android.service.notification.StatusBarNotification
import com.castor.core.common.model.BookNotificationCallback
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [BookNotificationCallback] that bridges the
 * NotificationListenerService events to the [KindleTracker].
 *
 * Provided via Hilt and injected into the CastorNotificationListener.
 */
@Singleton
class BookNotificationCallbackImpl @Inject constructor(
    private val kindleTracker: KindleTracker
) : BookNotificationCallback {

    override fun onBookNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == KindleTracker.KINDLE_PACKAGE) {
            kindleTracker.onKindleNotificationPosted(sbn)
        }
    }

    override fun onBookNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == KindleTracker.KINDLE_PACKAGE) {
            kindleTracker.onKindleNotificationRemoved()
        }
    }
}
