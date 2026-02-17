package com.castor.feature.media.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.castor.feature.media.kindle.KindleAccessibilityParser
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Accessibility service that monitors Kindle and Audible app UIs for
 * reading/listening progress information.
 *
 * This service only watches specific packages (`com.amazon.kindle` and
 * `com.audible.application`) and only extracts progress-related data.
 * It never captures or stores the actual content being read or listened to.
 *
 * The user must explicitly enable this service in:
 * Settings > Accessibility > Un-Dios Media Sync
 *
 * Event types monitored:
 * - [AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED] — UI updates within the app
 * - [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED] — new screens/dialogs
 *
 * This service delegates all parsing to specialised parsers:
 * - [KindleAccessibilityParser] for Kindle progress extraction
 */
@AndroidEntryPoint
class MediaAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MediaAccessibilitySvc"

        /** Kindle app package name. */
        private const val KINDLE_PACKAGE = "com.amazon.kindle"

        /** Audible app package name. */
        private const val AUDIBLE_PACKAGE = "com.audible.application"

        /**
         * Singleton reference so that other components can check whether
         * the accessibility service is running.
         */
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    @Inject
    lateinit var kindleAccessibilityParser: KindleAccessibilityParser

    // -------------------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------------------

    override fun onServiceConnected() {
        super.onServiceConnected()

        // Configure the service programmatically (supplements the XML config).
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            packageNames = arrayOf(KINDLE_PACKAGE, AUDIBLE_PACKAGE)
            notificationTimeout = 500L // Debounce: at most one event per 500ms
        } ?: AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            packageNames = arrayOf(KINDLE_PACKAGE, AUDIBLE_PACKAGE)
            notificationTimeout = 500L
        }

        isRunning = true
        Log.i(TAG, "Media accessibility service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.i(TAG, "Media accessibility service destroyed")
    }

    // -------------------------------------------------------------------------------------
    // Event handling
    // -------------------------------------------------------------------------------------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.packageName?.toString()) {
            KINDLE_PACKAGE -> {
                try {
                    kindleAccessibilityParser.onAccessibilityEvent(event)
                } catch (e: Exception) {
                    Log.d(TAG, "Error processing Kindle accessibility event", e)
                }
            }
            AUDIBLE_PACKAGE -> {
                // Audible progress is primarily tracked via MediaSession (see
                // AudiblePositionTracker). The accessibility approach is reserved
                // for future enhancement if MediaSession data proves insufficient.
                Log.v(TAG, "Audible accessibility event (type=${event.eventType})")
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Media accessibility service interrupted")
    }
}
