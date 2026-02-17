package com.castor.feature.recommendations.tracking

import android.service.notification.StatusBarNotification
import com.castor.core.common.model.MediaNotificationCallback
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [MediaNotificationCallback] that bridges the
 * NotificationListenerService events to the [MediaWatchTracker].
 *
 * Provided via Hilt and injected into the CastorNotificationListener.
 */
@Singleton
class MediaNotificationCallbackImpl @Inject constructor(
    private val mediaWatchTracker: MediaWatchTracker
) : MediaNotificationCallback {

    override fun onMediaNotificationPosted(sbn: StatusBarNotification) {
        mediaWatchTracker.onNotificationPosted(sbn)
    }

    override fun onMediaNotificationRemoved(sbn: StatusBarNotification) {
        mediaWatchTracker.onNotificationRemoved(sbn)
    }
}
