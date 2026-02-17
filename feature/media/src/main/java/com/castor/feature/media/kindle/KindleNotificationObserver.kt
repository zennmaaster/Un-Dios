package com.castor.feature.media.kindle

import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight event bus for forwarding Kindle notifications from the
 * [CastorNotificationListener] (in the `:feature:notifications` module)
 * to the [KindleTracker] (in the `:feature:media` module).
 *
 * This class avoids a direct dependency between the two feature modules.
 * The notification listener calls [onNotificationPosted] / [onNotificationRemoved],
 * and the [KindleTracker] collects the resulting flows.
 *
 * Usage from CastorNotificationListener:
 * ```kotlin
 * @Inject lateinit var kindleNotificationObserver: KindleNotificationObserver
 *
 * override fun onNotificationPosted(sbn: StatusBarNotification) {
 *     if (sbn.packageName == "com.amazon.kindle") {
 *         kindleNotificationObserver.onNotificationPosted(sbn)
 *     }
 *     // ... existing messaging logic
 * }
 * ```
 */
@Singleton
class KindleNotificationObserver @Inject constructor() {

    companion object {
        const val KINDLE_PACKAGE = "com.amazon.kindle"
    }

    private val _posted = MutableSharedFlow<StatusBarNotification>(extraBufferCapacity = 16)
    /** Emits when a Kindle notification is posted. */
    val posted: SharedFlow<StatusBarNotification> = _posted.asSharedFlow()

    private val _removed = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    /** Emits when a Kindle notification is removed. */
    val removed: SharedFlow<Unit> = _removed.asSharedFlow()

    /**
     * Call from the [NotificationListenerService] when a notification from
     * the Kindle package is posted.
     */
    fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == KINDLE_PACKAGE) {
            _posted.tryEmit(sbn)
        }
    }

    /**
     * Call from the [NotificationListenerService] when a notification from
     * the Kindle package is removed.
     */
    fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == KINDLE_PACKAGE) {
            _removed.tryEmit(Unit)
        }
    }
}
