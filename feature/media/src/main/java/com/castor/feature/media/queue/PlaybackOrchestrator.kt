package com.castor.feature.media.queue

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.castor.core.common.model.MediaSource
import com.castor.core.common.model.UnifiedMediaItem
import com.castor.core.data.repository.MediaQueueRepository
import com.castor.feature.media.session.MediaSessionInfo
import com.castor.feature.media.session.MediaSessionMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages cross-source playback transitions for the Un-Dios unified media queue.
 *
 * The orchestrator acts as the central coordinator between multiple media sources
 * (Spotify, YouTube, Audible) and the unified queue. When the user presses "next"
 * it handles the complex dance of:
 *   1. Pausing/stopping the current source via its MediaSession transport controls
 *   2. Advancing the queue in [MediaQueueRepository]
 *   3. Starting the next source (which may be a completely different app)
 *   4. Requesting/abandoning audio focus appropriately
 *
 * It also publishes Un-Dios's own [MediaSession] so that lock screen controls,
 * Bluetooth AVRCP, and Android Auto can control the unified queue.
 *
 * Cross-source transition examples:
 * - Spotify track -> YouTube video: Pause Spotify, start YouTube via intent/adapter,
 *   request audio focus.
 * - YouTube video -> Audible audiobook: Stop YouTube playback, resume Audible via
 *   its MediaSession transport controls.
 * - Audible chapter -> Spotify track: Pause Audible, start Spotify via intent/adapter.
 */
@Singleton
class PlaybackOrchestrator @Inject constructor(
    private val queueRepository: MediaQueueRepository,
    private val mediaSessionMonitor: MediaSessionMonitor,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PlaybackOrchestrator"
        private const val SESSION_TAG = "UnDiosMediaSession"

        /** Interval for polling playback position from the active session (ms). */
        private const val POSITION_POLL_INTERVAL_MS = 500L
    }

    // -------------------------------------------------------------------------------------
    // Coroutine scope — lives as long as the singleton
    // -------------------------------------------------------------------------------------

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // -------------------------------------------------------------------------------------
    // Public state
    // -------------------------------------------------------------------------------------

    private val _currentItem = MutableStateFlow<UnifiedMediaItem?>(null)

    /** The item at the head of the queue (position 0). */
    val currentItem: StateFlow<UnifiedMediaItem?> = _currentItem.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)

    /** Whether the current item is actively playing. */
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)

    /** Current playback position in milliseconds for the active item. */
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    // -------------------------------------------------------------------------------------
    // Audio focus
    // -------------------------------------------------------------------------------------

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus lost — pausing.")
                scope.launch { pause() }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost transiently — pausing.")
                scope.launch { pause() }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Ducking: we let the source app handle volume reduction itself.
                Log.d(TAG, "Audio focus: ducking requested.")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus regained — resuming.")
                scope.launch { play() }
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Un-Dios MediaSession — exposed to lock screen, Bluetooth, Android Auto
    // -------------------------------------------------------------------------------------

    private var mediaSession: MediaSession? = null

    // -------------------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------------------

    init {
        // Observe the queue so we always know the current head item.
        scope.launch {
            queueRepository.getQueue().collectLatest { queue ->
                val head = queue.firstOrNull()
                _currentItem.value = head
                if (head != null) {
                    updateMediaSessionMetadata(head)
                }
            }
        }

        // Mirror the MediaSessionMonitor's now-playing state into our flows.
        scope.launch {
            mediaSessionMonitor.nowPlaying.collectLatest { nowPlaying ->
                _isPlaying.value = nowPlaying.isPlaying
                _playbackPosition.value = nowPlaying.positionMs
            }
        }

        // Poll position while playing (some sources don't push position updates).
        scope.launch {
            while (true) {
                delay(POSITION_POLL_INTERVAL_MS)
                if (_isPlaying.value) {
                    val nowPlaying = mediaSessionMonitor.nowPlaying.value
                    if (nowPlaying.positionMs > 0) {
                        _playbackPosition.value = nowPlaying.positionMs
                    }
                }
            }
        }

        // Initialize the Un-Dios MediaSession.
        publishMediaSession()
    }

    // -------------------------------------------------------------------------------------
    // Playback control — public API
    // -------------------------------------------------------------------------------------

    /**
     * Resume playback of the current item by sending play to the source app's
     * MediaSession transport controls.
     */
    suspend fun play() {
        val item = _currentItem.value ?: return
        if (!requestAudioFocus()) {
            Log.w(TAG, "Could not acquire audio focus for play().")
            return
        }

        val controls = findTransportControlsForSource(item.source)
        if (controls != null) {
            controls.play()
            _isPlaying.value = true
            updatePlaybackState(PlaybackState.STATE_PLAYING)
            Log.d(TAG, "play() -> ${item.source} via transport controls")
        } else {
            // No active session for this source — attempt to launch the source app.
            launchSourceApp(item)
            Log.d(TAG, "play() -> launched source app for ${item.source}")
        }
    }

    /**
     * Pause the current item by sending pause to the source app's transport controls.
     */
    suspend fun pause() {
        val item = _currentItem.value ?: return
        val controls = findTransportControlsForSource(item.source)
        controls?.pause()
        _isPlaying.value = false
        updatePlaybackState(PlaybackState.STATE_PAUSED)
        Log.d(TAG, "pause() -> ${item.source}")
    }

    /**
     * Toggle play/pause.
     */
    suspend fun playPause() {
        if (_isPlaying.value) pause() else play()
    }

    /**
     * Skip to the next item in the queue.
     *
     * This is the core cross-source transition:
     *   1. Determine the current source and the next item's source.
     *   2. Stop/pause the current source.
     *   3. Advance the queue (removes position 0, shifts everything down).
     *   4. Start the next source.
     */
    suspend fun skipNext() {
        val currentSource = _currentItem.value?.source

        // Stop/pause the currently playing source.
        if (currentSource != null) {
            stopSource(currentSource)
        }

        // Advance the queue — the old head is removed.
        queueRepository.advanceQueue()

        // The new head is now the "current" item (updated via the Flow observer).
        val nextItem = queueRepository.getCurrentItem()
        if (nextItem != null) {
            Log.d(TAG, "skipNext(): transitioning from $currentSource -> ${nextItem.source}")
            // Brief delay to let the previous source release resources.
            if (currentSource != null && currentSource != nextItem.source) {
                delay(300)
            }
            _currentItem.value = nextItem
            startSource(nextItem)
        } else {
            Log.d(TAG, "skipNext(): queue exhausted.")
            _isPlaying.value = false
            _playbackPosition.value = 0L
            abandonAudioFocus()
            updatePlaybackState(PlaybackState.STATE_STOPPED)
        }
    }

    /**
     * Skip to the previous item. Since our queue is forward-only (consumed items
     * are removed), "previous" restarts the current track by seeking to 0.
     * If playback position is < 3 seconds, this is a no-op (we don't maintain
     * a backwards history in this phase).
     */
    suspend fun skipPrevious() {
        val item = _currentItem.value ?: return
        // Restart current track.
        seekTo(0L)
        Log.d(TAG, "skipPrevious(): restarting ${item.title}")
    }

    /**
     * Seek to a specific position in the current track.
     */
    suspend fun seekTo(positionMs: Long) {
        val item = _currentItem.value ?: return
        val controls = findTransportControlsForSource(item.source)
        controls?.seekTo(positionMs)
        _playbackPosition.value = positionMs
        Log.d(TAG, "seekTo($positionMs) -> ${item.source}")
    }

    // -------------------------------------------------------------------------------------
    // Cross-source transition helpers
    // -------------------------------------------------------------------------------------

    /**
     * Stop or pause playback for a given source by finding its active MediaSession
     * and issuing a stop command.
     */
    private fun stopSource(source: MediaSource) {
        val controls = findTransportControlsForSource(source)
        if (controls != null) {
            when (source) {
                MediaSource.YOUTUBE -> {
                    // YouTube: send stop to fully release the video player.
                    controls.stop()
                }
                MediaSource.SPOTIFY, MediaSource.AUDIBLE -> {
                    // Spotify/Audible: pause is more appropriate — the user may want to
                    // resume later and these apps handle pause gracefully.
                    controls.pause()
                }
            }
            Log.d(TAG, "stopSource($source): command sent.")
        } else {
            Log.w(TAG, "stopSource($source): no active session found.")
        }
    }

    /**
     * Start playback for a given item. First attempts to use existing MediaSession
     * transport controls; falls back to launching the source app.
     */
    private suspend fun startSource(item: UnifiedMediaItem) {
        if (!requestAudioFocus()) {
            Log.w(TAG, "startSource(${item.source}): audio focus denied.")
            return
        }

        val controls = findTransportControlsForSource(item.source)
        if (controls != null) {
            controls.play()
            _isPlaying.value = true
            updatePlaybackState(PlaybackState.STATE_PLAYING)
            updateMediaSessionMetadata(item)
            Log.d(TAG, "startSource(${item.source}): via transport controls.")
        } else {
            // Source app doesn't have an active session — launch it.
            launchSourceApp(item)
            _isPlaying.value = true
            updatePlaybackState(PlaybackState.STATE_PLAYING)
            updateMediaSessionMetadata(item)
            Log.d(TAG, "startSource(${item.source}): launched app.")
        }
    }

    /**
     * Launch the source app for a given item. This sends an intent that the
     * source app can handle (e.g., Spotify deep link, YouTube video URI).
     *
     * In a full implementation each source adapter would handle this; for now
     * we use a simple intent-based approach.
     */
    private fun launchSourceApp(item: UnifiedMediaItem) {
        try {
            val intent = when (item.source) {
                MediaSource.SPOTIFY -> {
                    // Spotify URI: spotify:track:xxxx -> open in Spotify app.
                    android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse(item.sourceUri)
                        setPackage("com.spotify.music")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                MediaSource.YOUTUBE -> {
                    // YouTube URI: https://youtube.com/watch?v=xxxx or vnd.youtube:xxxx
                    android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse(item.sourceUri)
                        setPackage("com.google.android.youtube")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                MediaSource.AUDIBLE -> {
                    // Audible: launch the app; deep links are limited.
                    context.packageManager.getLaunchIntentForPackage("com.audible.application")
                        ?.apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        } ?: return
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch source app for ${item.source}: ${e.message}")
        }
    }

    /**
     * Find the [android.media.session.MediaController.TransportControls] for a given
     * [MediaSource] by looking through the [MediaSessionMonitor]'s active sessions.
     */
    private fun findTransportControlsForSource(
        source: MediaSource
    ): android.media.session.MediaController.TransportControls? {
        val sessions = mediaSessionMonitor.activeSessions.value
        val match: MediaSessionInfo? = sessions.firstOrNull { it.source == source && it.isActive }
            ?: sessions.firstOrNull { it.source == source }
        return match?.transportControls
    }

    // -------------------------------------------------------------------------------------
    // Audio focus management
    // -------------------------------------------------------------------------------------

    /**
     * Request audio focus for media playback.
     * @return true if focus was granted.
     */
    private fun requestAudioFocus(): Boolean {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()

        audioFocusRequest = request

        val result = audioManager.requestAudioFocus(request)
        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(TAG, "requestAudioFocus() -> ${if (granted) "GRANTED" else "DENIED"}")
        return granted
    }

    /**
     * Abandon audio focus when we're no longer playing.
     */
    private fun abandonAudioFocus() {
        audioFocusRequest?.let { request ->
            audioManager.abandonAudioFocusRequest(request)
            audioFocusRequest = null
            Log.d(TAG, "abandonAudioFocus()")
        }
    }

    // -------------------------------------------------------------------------------------
    // Un-Dios MediaSession — lock screen, Bluetooth, Android Auto
    // -------------------------------------------------------------------------------------

    /**
     * Create and activate Un-Dios's own MediaSession so that external controllers
     * (lock screen, Bluetooth headsets, Android Auto) route commands to us rather
     * than directly to individual source apps.
     */
    private fun publishMediaSession() {
        val session = MediaSession(context, SESSION_TAG).apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    scope.launch { play() }
                }

                override fun onPause() {
                    scope.launch { pause() }
                }

                override fun onSkipToNext() {
                    scope.launch { skipNext() }
                }

                override fun onSkipToPrevious() {
                    scope.launch { skipPrevious() }
                }

                override fun onSeekTo(pos: Long) {
                    scope.launch { seekTo(pos) }
                }

                override fun onStop() {
                    scope.launch {
                        pause()
                        abandonAudioFocus()
                    }
                }
            })
            isActive = true
        }
        mediaSession = session
        updatePlaybackState(PlaybackState.STATE_NONE)
        Log.d(TAG, "Un-Dios MediaSession published.")
    }

    /**
     * Update the metadata on our published MediaSession to reflect the given item.
     * This drives the lock screen and notification artwork/title/artist.
     */
    private fun updateMediaSessionMetadata(item: UnifiedMediaItem) {
        val session = mediaSession ?: return
        val builder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, item.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, item.artist ?: "Unknown Artist")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Un-Dios Queue")

        item.durationMs?.let { duration ->
            builder.putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
        }

        item.albumArtUrl?.let { artUrl ->
            builder.putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, artUrl)
            builder.putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, artUrl)
        }

        // Encode the source in the display subtitle so external UIs can show it.
        builder.putString(
            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
            "${item.artist ?: "Unknown"} | ${item.source.name}"
        )

        session.setMetadata(builder.build())
    }

    /**
     * Update the playback state on our published MediaSession.
     */
    private fun updatePlaybackState(state: Int) {
        val session = mediaSession ?: return
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SEEK_TO or
            PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_PLAY_PAUSE

        val builder = PlaybackState.Builder()
            .setActions(actions)
            .setState(state, _playbackPosition.value, 1.0f, SystemClock.elapsedRealtime())

        session.setPlaybackState(builder.build())
    }
}
