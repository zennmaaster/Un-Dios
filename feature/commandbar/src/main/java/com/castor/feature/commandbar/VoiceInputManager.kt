package com.castor.feature.commandbar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VoiceInputManager handles speech recognition for the command bar.
 *
 * Uses Android's SpeechRecognizer API to convert speech to text in real-time.
 * Provides StateFlows for observing the listening state, transcript, and errors.
 *
 * IMPORTANT: The RECORD_AUDIO permission must be declared in AndroidManifest.xml
 * and should be requested at runtime before calling startListening(). If permission
 * is not granted, an error will be emitted to the error flow.
 *
 * Usage:
 * ```
 * voiceInputManager.startListening()
 * voiceInputManager.transcript.collect { text ->
 *     // Handle transcript
 * }
 * voiceInputManager.stopListening()
 * ```
 */
@Singleton
class VoiceInputManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Checks if speech recognition is available on this device.
     */
    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Starts listening for voice input.
     *
     * @param locale The locale to use for recognition. Defaults to device default.
     */
    fun startListening(locale: Locale = Locale.getDefault()) {
        // Check if recognition is available
        if (!isRecognitionAvailable()) {
            _error.value = "Speech recognition is not available on this device"
            return
        }

        // Clean up any existing recognizer
        stopListening()

        // Reset state
        _transcript.value = ""
        _partialTranscript.value = ""
        _error.value = null

        // Create new recognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(recognitionListener)
        }

        // Create recognition intent
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Request on-device recognition if available for privacy
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        // Start listening
        speechRecognizer?.startListening(intent)
    }

    /**
     * Stops listening and cleans up the recognizer.
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _isListening.value = false
    }

    /**
     * Clears any error state.
     */
    fun clearError() {
        _error.value = null
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _isListening.value = true
            _error.value = null
        }

        override fun onBeginningOfSpeech() {
            // User started speaking
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Audio level changed - could be used for visualization
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Audio buffer received
        }

        override fun onEndOfSpeech() {
            _isListening.value = false
        }

        override fun onError(error: Int) {
            _isListening.value = false
            _error.value = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Try again."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service is busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Timeout."
                else -> "Recognition error: $error"
            }
        }

        override fun onResults(results: Bundle?) {
            _isListening.value = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (matches != null && matches.isNotEmpty()) {
                _transcript.value = matches[0]
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (matches != null && matches.isNotEmpty()) {
                _partialTranscript.value = matches[0]
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Additional events
        }
    }
}
