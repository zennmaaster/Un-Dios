package com.castor.feature.media.session

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import com.castor.core.common.model.MediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State representing what is currently playing across all monitored media sessions.
 */
data class NowPlayingState(
    val isPlaying: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val albumArtUri: String? = null,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val source: MediaSource? = null,
    val packageName: String? = null,
    val sessionToken: MediaSession.Token? = null
)

/**
 * Information about a single active media session including its metadata
 * and transport controls handle.
 */
data class MediaSessionInfo(
    val packageName: String,
    val source: MediaSource?,
    val isActive: Boolean,
    val metadata: NowPlayingState,
    val transportControls: MediaController.TransportControls?
)

/**
 * Discovers and monitors active media sessions using [MediaSessionManager].
 *
 * Registers as an active-session listener and, for each session, attaches a
 * [MediaController.Callback] to track playback state changes, metadata updates,
 * and queue mutations. The highest-priority playing session is surfaced through
 * [nowPlaying]; every tracked session is available through [activeSessions].
 *
 * Package-name-to-[MediaSource] mapping:
 * - `com.spotify.music`            -> [MediaSource.SPOTIFY]
 * - `com.google.android.youtube`   -> [MediaSource.YOUTUBE]
 * - `com.audible.application`      -> [MediaSource.AUDIBLE]
 * - anything else                  -> `null`
 */
@Singleton
class MediaSessionMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MediaSessionMonitor"

        /** Map well-known package names to the unified [MediaSource] enum. */
        private val PACKAGE_SOURCE_MAP: Map<String, MediaSource> = mapOf(
            "com.spotify.music" to MediaSource.SPOTIFY,
            "com.google.android.youtube" to MediaSource.YOUTUBE,
            "com.audible.application" to MediaSource.AUDIBLE
        )

        /** Resolve a package name to a [MediaSource], or null for unknown apps. */
        fun resolveSource(packageName: String): MediaSource? =
            PACKAGE_SOURCE_MAP[packageName]
    }

    // ---------------------------------------------------------------------------
    // Public state
    // ---------------------------------------------------------------------------

    private val _nowPlaying = MutableStateFlow(NowPlayingState())

    /** The single highest-priority now-playing state (the session that is actively playing). */
    val nowPlaying: StateFlow<NowPlayingState> = _nowPlaying.asStateFlow()

    private val _activeSessions = MutableStateFlow<List<MediaSessionInfo>>(emptyList())

    /** All currently tracked media sessions. */
    val activeSessions: StateFlow<List<MediaSessionInfo>> = _activeSessions.asStateFlow()

    // ---------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------

    private var monitorScope: CoroutineScope? = null
    private var mediaSessionManager: MediaSessionManager? = null
    private var isMonitoring = false

    /**
     * Per-session callback registrations so we can clean up when sessions disappear.
     * Key: [MediaSession.Token], Value: controller + its callback.
     */
    private val controllerCallbacks =
        mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()

    /**
     * Listener that the system invokes whenever the set of active sessions changes
     * (e.g. a new media app starts playing, or one is killed).
     */
    private val activeSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            controllers?.let { onActiveSessionsChanged(it) }
        }

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    /**
     * Begin monitoring media sessions. Safe to call multiple times; subsequent
     * calls are no-ops while monitoring is active.
     */
    fun startMonitoring() {
        if (isMonitoring) return

        monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
                as? MediaSessionManager
            if (manager == null) {
                Log.e(TAG, "MediaSessionManager not available on this device.")
                return
            }
            mediaSessionManager = manager

            // The component name of *our* NotificationListenerService — required to
            // call getActiveSessions() and register the listener.
            val listenerComponent = ComponentName(
                context,
                CastorNotificationListenerStub::class.java
            )

            // Seed with the currently active sessions.
            val currentSessions = manager.getActiveSessions(listenerComponent)
            onActiveSessionsChanged(currentSessions)

            // Subscribe to future changes.
            manager.addOnActiveSessionsChangedListener(
                activeSessionsListener,
                listenerComponent
            )
            isMonitoring = true
            Log.d(TAG, "Media session monitoring started.")
        } catch (e: SecurityException) {
            Log.e(TAG, "NotificationListener permission not granted.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start media session monitoring.", e)
        }
    }

    /**
     * Stop monitoring and release all resources.
     */
    fun stopMonitoring() {
        if (!isMonitoring) return

        mediaSessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener)
        unregisterAllCallbacks()
        monitorScope?.cancel()
        monitorScope = null
        isMonitoring = false

        _nowPlaying.value = NowPlayingState()
        _activeSessions.value = emptyList()

        Log.d(TAG, "Media session monitoring stopped.")
    }

    // ---------------------------------------------------------------------------
    // Active-session wiring
    // ---------------------------------------------------------------------------

    /**
     * Called when the system reports a new list of active [MediaController]s.
     * We diff against our existing registrations, unregister stale callbacks,
     * and register new ones.
     */
    private fun onActiveSessionsChanged(controllers: List<MediaController>) {
        val newTokens = controllers.mapNotNull { it.sessionToken }.toSet()
        val existingTokens = controllerCallbacks.keys.toSet()

        // Remove callbacks for sessions that are no longer active.
        val removedTokens = existingTokens - newTokens
        for (token in removedTokens) {
            val (controller, callback) = controllerCallbacks.remove(token) ?: continue
            controller.unregisterCallback(callback)
        }

        // Register callbacks for new sessions.
        for (controller in controllers) {
            val token = controller.sessionToken ?: continue
            if (token in controllerCallbacks) continue
            registerControllerCallback(controller)
        }

        // Rebuild the public state.
        rebuildState(controllers)
    }

    /**
     * Wire up a [MediaController.Callback] for the given controller so we can
     * react to metadata / playback-state changes in real time.
     */
    private fun registerControllerCallback(controller: MediaController) {
        val token = controller.sessionToken ?: return

        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                monitorScope?.launch { refreshState() }
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                monitorScope?.launch { refreshState() }
            }

            override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) {
                monitorScope?.launch { refreshState() }
            }

            override fun onSessionDestroyed() {
                controllerCallbacks.remove(token)?.let { (ctrl, cb) ->
                    ctrl.unregisterCallback(cb)
                }
                monitorScope?.launch { refreshState() }
            }
        }

        controller.registerCallback(callback)
        controllerCallbacks[token] = controller to callback
    }

    /** Remove and unregister every tracked callback. */
    private fun unregisterAllCallbacks() {
        for ((_, pair) in controllerCallbacks) {
            val (controller, callback) = pair
            try {
                controller.unregisterCallback(callback)
            } catch (_: Exception) {
                // Controller may already be dead — ignore.
            }
        }
        controllerCallbacks.clear()
    }

    // ---------------------------------------------------------------------------
    // State computation
    // ---------------------------------------------------------------------------

    /** Re-query every tracked controller and rebuild [_activeSessions] and [_nowPlaying]. */
    private fun refreshState() {
        val controllers = controllerCallbacks.values.map { it.first }
        rebuildState(controllers)
    }

    private fun rebuildState(controllers: List<MediaController>) {
        val sessions = controllers.map { controller ->
            val pkg = controller.packageName ?: "unknown"
            val source = resolveSource(pkg)
            val meta = extractNowPlaying(controller, pkg, source)
            val isActive = controller.playbackState?.state.let { state ->
                state == PlaybackState.STATE_PLAYING ||
                    state == PlaybackState.STATE_BUFFERING
            }

            MediaSessionInfo(
                packageName = pkg,
                source = source,
                isActive = isActive,
                metadata = meta,
                transportControls = controller.transportControls
            )
        }

        _activeSessions.update { sessions }

        // The "now playing" entry is the first session that is actively playing,
        // or the first session overall if nothing is playing.
        val primary = sessions.firstOrNull { it.isActive } ?: sessions.firstOrNull()
        _nowPlaying.update { primary?.metadata ?: NowPlayingState() }
    }

    /**
     * Extract [NowPlayingState] from a [MediaController]'s current metadata and
     * playback state.
     */
    private fun extractNowPlaying(
        controller: MediaController,
        packageName: String,
        source: MediaSource?
    ): NowPlayingState {
        val metadata = controller.metadata
        val playbackState = controller.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val albumArtUri = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        val durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
        val positionMs = playbackState?.position ?: 0L

        return NowPlayingState(
            isPlaying = isPlaying,
            title = title,
            artist = artist,
            albumArtUri = albumArtUri,
            durationMs = if (durationMs > 0) durationMs else 0L,
            positionMs = if (positionMs > 0) positionMs else 0L,
            source = source,
            packageName = packageName,
            sessionToken = controller.sessionToken
        )
    }
}

/**
 * Stub [NotificationListenerService] that the system binds to grant us
 * permission to read active media sessions via [MediaSessionManager].
 *
 * The app manifest must declare this service with the appropriate
 * `<intent-filter>` and the user must enable it in Settings > Notifications >
 * Notification access.
 *
 * (The actual NotificationListenerService is expected to already be declared
 * in the app module's manifest. This stub class exists purely so that the
 * feature module can reference a concrete [ComponentName] when calling
 * [MediaSessionManager.getActiveSessions].)
 */
class CastorNotificationListenerStub : NotificationListenerService()
