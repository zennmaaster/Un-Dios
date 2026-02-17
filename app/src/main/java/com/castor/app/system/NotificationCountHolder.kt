package com.castor.app.system

import com.castor.core.common.model.NotificationCountCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared, thread-safe holder for the current unread notification count.
 *
 * This is a singleton that bridges the [CastorNotificationListener] (which runs in
 * a system-managed [NotificationListenerService]) with the [SystemStatsProvider]
 * (which periodically reads the count to populate the status bar).
 *
 * Implements [NotificationCountCallback] (defined in `:core:common`) so that
 * `:feature:notifications` can call increment/decrement without depending on `:app`.
 *
 * The [SystemStatsProvider] reads [count] on each stats tick to include in
 * the [SystemStats.unreadNotifications] field.
 */
@Singleton
class NotificationCountHolder @Inject constructor() : NotificationCountCallback {

    private val _count = MutableStateFlow(0)

    /** Observable count of active (unread) notifications. */
    val count: StateFlow<Int> = _count.asStateFlow()

    /** Set the notification count to an absolute value (e.g., on listener connect). */
    override fun updateCount(count: Int) {
        _count.value = count
    }

    /** Increment the notification count by one (e.g., on notification posted). */
    override fun increment() {
        _count.update { it + 1 }
    }

    /** Decrement the notification count by one, floored at zero (e.g., on notification removed). */
    override fun decrement() {
        _count.update { maxOf(0, it - 1) }
    }
}
