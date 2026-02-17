package com.castor.agent.orchestrator

import com.castor.core.common.model.MediaSource
import com.castor.core.common.model.MediaType
import com.castor.core.data.repository.MediaQueueRepository
import com.castor.core.inference.InferenceEngine
import javax.inject.Inject
import javax.inject.Singleton

// =========================================================================================
// Data models
// =========================================================================================

/**
 * Actions the media agent can perform.
 */
enum class MediaAction {
    PLAY,
    PAUSE,
    SKIP_NEXT,
    SKIP_PREVIOUS,
    QUEUE,
    SEARCH,
    WHAT_IS_PLAYING
}

/**
 * Parsed media command extracted from natural language input.
 *
 * @param action       The media action to perform.
 * @param query        The search query or media description (e.g. "workout playlist", "jazz music").
 * @param source       The preferred media source, if detected.
 * @param playlistName An explicit playlist name, if mentioned.
 * @param mediaType    The type of media being requested.
 */
data class MediaCommand(
    val action: MediaAction,
    val query: String?,
    val source: MediaSource?,
    val playlistName: String?,
    val mediaType: MediaType = MediaType.MUSIC
)

// =========================================================================================
// MediaAgent
// =========================================================================================

/**
 * Dedicated agent for handling all media-related intents: play, pause, skip, queue, and
 * "what is playing" queries.
 *
 * When the on-device LLM is loaded, the agent uses it to extract structured fields from
 * free-form input. When the LLM is unavailable, it falls back to keyword/regex-based
 * parsing so that basic media commands still work offline.
 *
 * Usage:
 * - Call [parseMediaCommand] to convert user input into a [MediaCommand].
 * - Call [describeAction] to generate a user-facing natural language response.
 * - Call [handleCommand] for a one-shot parse + describe + optional queue operation.
 */
@Singleton
class MediaAgent @Inject constructor(
    private val engine: InferenceEngine,
    private val mediaQueueRepository: MediaQueueRepository
) {

    companion object {
        private const val PARSE_MAX_TOKENS = 192
        private const val DESCRIBE_MAX_TOKENS = 128

        private const val PARSE_SYSTEM_PROMPT = """You are a media command parser for an Android assistant called Un-Dios.
Given user input, extract the media command fields and respond in EXACTLY this format (one field per line, no extra text):

ACTION: <PLAY|PAUSE|SKIP_NEXT|SKIP_PREVIOUS|QUEUE|SEARCH|WHAT_IS_PLAYING>
QUERY: <search query or media description, or NONE>
SOURCE: <SPOTIFY|YOUTUBE|AUDIBLE|NONE>
PLAYLIST: <playlist name, or NONE>
MEDIA_TYPE: <MUSIC|VIDEO|AUDIOBOOK|PODCAST>

Rules:
- If the user says "play my workout playlist on Spotify", ACTION=PLAY, QUERY=workout, SOURCE=SPOTIFY, PLAYLIST=workout playlist, MEDIA_TYPE=MUSIC
- If the user says "pause", ACTION=PAUSE, QUERY=NONE, SOURCE=NONE, PLAYLIST=NONE, MEDIA_TYPE=MUSIC
- If the user says "skip", "next song", "next track", ACTION=SKIP_NEXT
- If the user says "previous", "go back", ACTION=SKIP_PREVIOUS
- If the user says "add to queue" or "queue up", ACTION=QUEUE
- If the user says "what's playing", "what song is this", ACTION=WHAT_IS_PLAYING
- Always infer MEDIA_TYPE from context: podcasts, audiobooks, videos, or default to MUSIC"""

        private const val DESCRIBE_SYSTEM_PROMPT = """You are Un-Dios, a helpful AI assistant on the user's Android phone.
Describe the media action you are about to take in a single concise, friendly sentence.
Do not use markdown. Do not add any preamble."""

        // Keyword sets for fallback parsing
        private val PAUSE_KEYWORDS = listOf("pause", "stop playing", "stop music", "stop the music")
        private val SKIP_NEXT_KEYWORDS = listOf(
            "skip", "next song", "next track", "skip this", "play next", "next"
        )
        private val SKIP_PREV_KEYWORDS = listOf(
            "previous", "go back", "last song", "previous track", "prev"
        )
        private val QUEUE_KEYWORDS = listOf(
            "add to queue", "queue up", "queue", "add to my queue", "play after this"
        )
        private val WHAT_PLAYING_KEYWORDS = listOf(
            "what's playing", "what is playing", "what song is this",
            "what am i listening to", "now playing", "current song", "current track"
        )
        private val PODCAST_KEYWORDS = listOf("podcast", "episode", "show")
        private val AUDIOBOOK_KEYWORDS = listOf("audiobook", "audio book", "audible", "book")
        private val VIDEO_KEYWORDS = listOf("video", "watch", "youtube")
    }

    // -------------------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------------------

    /**
     * Parse a natural language media command into a structured [MediaCommand].
     *
     * Uses the LLM when loaded; falls back to keyword-based parsing otherwise.
     */
    suspend fun parseMediaCommand(input: String): MediaCommand {
        return if (engine.isLoaded) {
            parseWithLlm(input)
        } else {
            parseWithKeywords(input)
        }
    }

    /**
     * Generate a natural language description of the action represented by [command].
     *
     * When the LLM is unavailable, a template-based description is returned.
     */
    suspend fun describeAction(command: MediaCommand): String {
        if (!engine.isLoaded) {
            return describeWithTemplate(command)
        }

        val prompt = buildString {
            append("Describe this media action: ${command.action.name.lowercase().replace('_', ' ')}")
            if (command.query != null) append(", query: \"${command.query}\"")
            if (command.source != null) append(", on ${command.source.name}")
            if (command.playlistName != null) append(", playlist: \"${command.playlistName}\"")
        }

        return try {
            engine.generate(
                prompt = prompt,
                systemPrompt = DESCRIBE_SYSTEM_PROMPT,
                maxTokens = DESCRIBE_MAX_TOKENS,
                temperature = 0.6f
            ).trim()
        } catch (e: Exception) {
            describeWithTemplate(command)
        }
    }

    /**
     * One-shot convenience: parse the input, describe the action, and return the response.
     * If the command is WHAT_IS_PLAYING, queries the media queue for the current item.
     */
    suspend fun handleCommand(input: String): String {
        val command = parseMediaCommand(input)

        return when (command.action) {
            MediaAction.WHAT_IS_PLAYING -> {
                val currentItem = try {
                    mediaQueueRepository.getCurrentItem()
                } catch (e: Exception) {
                    null
                }
                if (currentItem != null) {
                    val artistText = if (currentItem.artist != null) " by ${currentItem.artist}" else ""
                    "Now playing: ${currentItem.title}$artistText (${currentItem.source.name.lowercase()})"
                } else {
                    "Nothing is currently playing."
                }
            }
            else -> describeAction(command)
        }
    }

    // -------------------------------------------------------------------------------------
    // LLM-based parsing
    // -------------------------------------------------------------------------------------

    private suspend fun parseWithLlm(input: String): MediaCommand {
        return try {
            val response = engine.generate(
                prompt = input,
                systemPrompt = PARSE_SYSTEM_PROMPT,
                maxTokens = PARSE_MAX_TOKENS,
                temperature = 0.1f
            )
            parseLlmResponse(response, input)
        } catch (e: Exception) {
            parseWithKeywords(input)
        }
    }

    /**
     * Parse the structured LLM response into a [MediaCommand].
     * Falls back to keyword parsing if the response is malformed.
     */
    private fun parseLlmResponse(response: String, originalInput: String): MediaCommand {
        val lines = response.lines().associate { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                parts[0].trim().uppercase() to parts[1].trim()
            } else {
                "" to ""
            }
        }

        val actionStr = lines["ACTION"]?.uppercase() ?: return parseWithKeywords(originalInput)
        val action = try {
            MediaAction.valueOf(actionStr)
        } catch (e: IllegalArgumentException) {
            return parseWithKeywords(originalInput)
        }

        val query = lines["QUERY"]?.takeIf { it.uppercase() != "NONE" && it.isNotBlank() }
        val sourceStr = lines["SOURCE"]?.takeIf { it.uppercase() != "NONE" && it.isNotBlank() }
        val source = sourceStr?.let {
            try {
                MediaSource.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                guessMediaSource(originalInput)
            }
        } ?: guessMediaSource(originalInput)
        val playlist = lines["PLAYLIST"]?.takeIf { it.uppercase() != "NONE" && it.isNotBlank() }
        val mediaTypeStr = lines["MEDIA_TYPE"]?.takeIf { it.uppercase() != "NONE" && it.isNotBlank() }
        val mediaType = mediaTypeStr?.let {
            try {
                MediaType.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                guessMediaType(originalInput)
            }
        } ?: guessMediaType(originalInput)

        return MediaCommand(
            action = action,
            query = query,
            source = source,
            playlistName = playlist,
            mediaType = mediaType
        )
    }

    // -------------------------------------------------------------------------------------
    // Keyword-based fallback parsing
    // -------------------------------------------------------------------------------------

    private fun parseWithKeywords(input: String): MediaCommand {
        val lowered = input.lowercase()

        // Detect action — order matters
        val action = when {
            WHAT_PLAYING_KEYWORDS.any { lowered.contains(it) } -> MediaAction.WHAT_IS_PLAYING
            PAUSE_KEYWORDS.any { lowered.contains(it) } -> MediaAction.PAUSE
            SKIP_PREV_KEYWORDS.any { lowered.contains(it) } -> MediaAction.SKIP_PREVIOUS
            SKIP_NEXT_KEYWORDS.any { lowered.contains(it) } -> MediaAction.SKIP_NEXT
            QUEUE_KEYWORDS.any { lowered.contains(it) } -> MediaAction.QUEUE
            else -> MediaAction.PLAY
        }

        // Extract query: strip the action keywords and source keywords
        val query = if (action == MediaAction.PAUSE || action == MediaAction.SKIP_NEXT ||
            action == MediaAction.SKIP_PREVIOUS || action == MediaAction.WHAT_IS_PLAYING
        ) {
            null
        } else {
            extractMediaQuery(input)
        }

        // Extract playlist name
        val playlistName = extractPlaylistName(input)

        return MediaCommand(
            action = action,
            query = query,
            source = guessMediaSource(input),
            playlistName = playlistName,
            mediaType = guessMediaType(input)
        )
    }

    // -------------------------------------------------------------------------------------
    // Template-based description
    // -------------------------------------------------------------------------------------

    private fun describeWithTemplate(command: MediaCommand): String {
        val sourceText = command.source?.let { " on ${it.name.lowercase()}" } ?: ""
        return when (command.action) {
            MediaAction.PLAY -> {
                if (command.playlistName != null) {
                    "Playing playlist \"${command.playlistName}\"$sourceText."
                } else if (command.query != null) {
                    "Playing ${command.query}$sourceText."
                } else {
                    "Resuming playback$sourceText."
                }
            }
            MediaAction.PAUSE -> "Pausing playback."
            MediaAction.SKIP_NEXT -> "Skipping to the next track."
            MediaAction.SKIP_PREVIOUS -> "Going back to the previous track."
            MediaAction.QUEUE -> {
                if (command.query != null) {
                    "Adding ${command.query} to the queue$sourceText."
                } else {
                    "Adding to the queue$sourceText."
                }
            }
            MediaAction.SEARCH -> {
                if (command.query != null) {
                    "Searching for ${command.query}$sourceText."
                } else {
                    "Opening search$sourceText."
                }
            }
            MediaAction.WHAT_IS_PLAYING -> "Checking what is currently playing."
        }
    }

    // -------------------------------------------------------------------------------------
    // Extraction helpers
    // -------------------------------------------------------------------------------------

    /**
     * Guess which media source the user intends based on keywords.
     */
    private fun guessMediaSource(input: String): MediaSource? {
        val lowered = input.lowercase()
        return when {
            lowered.contains("spotify") -> MediaSource.SPOTIFY
            lowered.contains("youtube") -> MediaSource.YOUTUBE
            lowered.contains("audible") || lowered.contains("audiobook") -> MediaSource.AUDIBLE
            else -> null
        }
    }

    /**
     * Guess the media type from input keywords.
     */
    private fun guessMediaType(input: String): MediaType {
        val lowered = input.lowercase()
        return when {
            PODCAST_KEYWORDS.any { lowered.contains(it) } -> MediaType.PODCAST
            AUDIOBOOK_KEYWORDS.any { lowered.contains(it) } -> MediaType.AUDIOBOOK
            VIDEO_KEYWORDS.any { lowered.contains(it) } -> MediaType.VIDEO
            else -> MediaType.MUSIC
        }
    }

    /**
     * Extract the media query by stripping known action/source keywords from the input.
     */
    private fun extractMediaQuery(input: String): String? {
        val stopWords = listOf(
            "play", "queue", "queue up", "add to queue", "listen to", "put on",
            "on spotify", "on youtube", "on audible", "from spotify", "from youtube",
            "my", "some", "the", "a", "please", "can you", "could you"
        )
        var cleaned = input
        for (word in stopWords) {
            cleaned = cleaned.replace(word, "", ignoreCase = true)
        }
        cleaned = cleaned.trim()
        return cleaned.takeIf { it.isNotBlank() }
    }

    /**
     * Extract a playlist name from patterns like "my workout playlist" or "playlist called X".
     */
    private fun extractPlaylistName(input: String): String? {
        val patterns = listOf(
            Regex("""(?:my\s+)?(\w[\w\s]*?)\s+playlist""", RegexOption.IGNORE_CASE),
            Regex("""playlist\s+(?:called|named)\s+(.+?)(?:\s+on|\s*$)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(input)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return null
    }
}
