package com.castor.feature.recommendations.tracking

import android.service.notification.StatusBarNotification
import android.util.Log
import com.castor.core.common.model.MediaSource
import com.castor.core.data.repository.WatchHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton tracker that receives media notification events from streaming
 * apps and logs them as watch history entries via [WatchHistoryRepository].
 *
 * Designed to be called from the existing [CastorNotificationListener] when
 * a notification arrives from a monitored media package. The tracker:
 *
 * 1. Delegates parsing to [NotificationMediaExtractor].
 * 2. Deduplicates rapidly-updating notifications (e.g. Netflix progress).
 * 3. Persists watch events to the encrypted Room database.
 * 4. Emits events on a [SharedFlow] for real-time consumers.
 *
 * All processing is on-device; no data leaves the device.
 */
@Singleton
class MediaWatchTracker @Inject constructor(
    private val extractor: NotificationMediaExtractor,
    private val watchHistoryRepository: WatchHistoryRepository
) {
    companion object {
        private const val TAG = "MediaWatchTracker"

        /** Package names of streaming apps we track watch events for. */
        val MONITORED_MEDIA_PACKAGES = setOf(
            "com.netflix.mediaclient",
            "com.amazon.avod",
            "com.google.android.youtube",
            "com.android.chrome"
        )

        /**
         * Minimum interval (ms) between persisting events for the same title
         * from the same source, to prevent duplicate writes from notification
         * updates that fire every few seconds.
         */
        private const val DEDUP_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Tracks the last time we persisted an event for a given (source, title)
     * pair to avoid duplicate writes from rapid notification updates.
     */
    private val lastEventTimestamps = ConcurrentHashMap<String, Long>()

    private val _watchEvents = MutableSharedFlow<ExtractedMediaEvent>(extraBufferCapacity = 64)

    /** Public flow of newly tracked watch events. */
    val watchEvents: SharedFlow<ExtractedMediaEvent> = _watchEvents.asSharedFlow()

    // -------------------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------------------

    /**
     * Called when a notification is posted from a monitored media app.
     *
     * Extracts the media event, deduplicates, persists, and emits.
     */
    fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in MONITORED_MEDIA_PACKAGES) return

        val event = extractor.extract(sbn) ?: return

        // Deduplication: skip if we recently logged the same title from the same source.
        val dedupKey = "${event.source.name}::${event.title}"
        val now = System.currentTimeMillis()
        val lastTime = lastEventTimestamps[dedupKey]
        if (lastTime != null && (now - lastTime) < DEDUP_INTERVAL_MS) {
            Log.d(TAG, "Dedup skip: $dedupKey (last=${now - lastTime}ms ago)")
            return
        }
        lastEventTimestamps[dedupKey] = now

        // Persist and emit.
        scope.launch {
            try {
                watchHistoryRepository.logWatchEvent(
                    source = event.source,
                    title = event.title,
                    contentType = event.contentType,
                    metadata = buildMetadataJson(event)
                )
                _watchEvents.tryEmit(event)
                Log.d(TAG, "Watch event logged: source=${event.source}, title=${event.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log watch event: ${event.title}", e)
            }
        }
    }

    /**
     * Called when a notification is removed from a monitored media app.
     * Can be used to estimate watch duration based on how long the
     * notification was active.
     */
    fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName !in MONITORED_MEDIA_PACKAGES) return
        // Future enhancement: calculate approximate watch duration from
        // notification post time to removal time.
        Log.d(TAG, "Media notification removed: pkg=${sbn.packageName}")
    }

    /**
     * Manually log a watch event (e.g. from AccessibilityService content parsing).
     */
    fun logManualEvent(
        source: MediaSource,
        title: String,
        contentType: String = "video",
        genre: String? = null,
        durationWatchedMs: Long = 0L,
        totalDurationMs: Long = 0L
    ) {
        scope.launch {
            try {
                watchHistoryRepository.logWatchEvent(
                    source = source,
                    title = title,
                    genre = genre,
                    contentType = contentType,
                    durationWatchedMs = durationWatchedMs,
                    totalDurationMs = totalDurationMs
                )
                Log.d(TAG, "Manual watch event logged: source=$source, title=$title")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log manual watch event: $title", e)
            }
        }
    }

    /**
     * Clears deduplication state. Useful when the notification listener
     * reconnects or when testing.
     */
    fun clearDedupState() {
        lastEventTimestamps.clear()
    }

    // -------------------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------------------

    private fun buildMetadataJson(event: ExtractedMediaEvent): String {
        val parts = mutableListOf<String>()
        event.subtitle?.let { parts.add("\"subtitle\":\"${escapeJson(it)}\"") }
        event.additionalInfo?.let { parts.add("\"additionalInfo\":\"${escapeJson(it)}\"") }
        parts.add("\"packageName\":\"${event.packageName}\"")
        return "{${parts.joinToString(",")}}"
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
