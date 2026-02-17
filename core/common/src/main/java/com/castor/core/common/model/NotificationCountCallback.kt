package com.castor.core.common.model

/**
 * Callback interface for tracking the total count of active notifications.
 *
 * This interface lives in `:core:common` so that both `:feature:notifications`
 * (the producer -- CastorNotificationListener) and `:app` (the consumer --
 * SystemStatsProvider) can depend on it without creating a circular dependency.
 *
 * The `:app` module provides the concrete implementation ([NotificationCountHolder])
 * and binds it via Hilt.
 */
interface NotificationCountCallback {
    /** Set the notification count to an absolute value (e.g., on listener connect). */
    fun updateCount(count: Int)

    /** Increment the notification count by one (e.g., on notification posted). */
    fun increment()

    /** Decrement the notification count by one, floored at zero (e.g., on notification removed). */
    fun decrement()
}
