package com.castor.agent.orchestrator.tools

import com.castor.agent.orchestrator.MediaAgent
import com.castor.core.inference.tool.ToolCall
import com.castor.core.inference.tool.ToolDefinition
import com.castor.core.inference.tool.ToolHandler
import com.castor.core.inference.tool.ToolParameters
import com.castor.core.inference.tool.ToolProperty
import com.castor.core.inference.tool.ToolResult
import javax.inject.Inject

/**
 * Tool handlers for media playback control.
 * Wraps the existing [MediaAgent] methods as structured tools the LLM can call.
 */

class PlayMediaTool @Inject constructor(
    private val mediaAgent: MediaAgent
) : ToolHandler {

    override val name = "play_media"
    override val toolset = "media"
    override val definition = ToolDefinition(
        name = "play_media",
        description = "Play music, a podcast, an audiobook, or a video. Use this when the user wants to listen to or watch something.",
        parameters = ToolParameters(
            properties = mapOf(
                "query" to ToolProperty("string", "What to play (song name, artist, genre, or description)"),
                "source" to ToolProperty("string", "Media source to use", listOf("spotify", "youtube", "audible"))
            ),
            required = listOf("query")
        )
    )

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val query = arguments["query"] ?: return ToolResult(
            toolName = name, callId = "", success = false, output = "", error = "Missing 'query'"
        )
        val source = arguments["source"]
        val input = if (source != null) "play $query on $source" else "play $query"

        return try {
            val result = mediaAgent.handleCommand(input)
            ToolResult(toolName = name, callId = "", success = true, output = result)
        } catch (e: Exception) {
            ToolResult(toolName = name, callId = "", success = false, output = "", error = e.message)
        }
    }
}

class PauseMediaTool @Inject constructor(
    private val mediaAgent: MediaAgent
) : ToolHandler {

    override val name = "pause_media"
    override val toolset = "media"
    override val definition = ToolDefinition(
        name = "pause_media",
        description = "Pause the currently playing media.",
        parameters = ToolParameters()
    )

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        return try {
            val result = mediaAgent.handleCommand("pause")
            ToolResult(toolName = name, callId = "", success = true, output = result)
        } catch (e: Exception) {
            ToolResult(toolName = name, callId = "", success = false, output = "", error = e.message)
        }
    }
}

class SkipTrackTool @Inject constructor(
    private val mediaAgent: MediaAgent
) : ToolHandler {

    override val name = "skip_track"
    override val toolset = "media"
    override val definition = ToolDefinition(
        name = "skip_track",
        description = "Skip to the next track in the current playlist or queue.",
        parameters = ToolParameters()
    )

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        return try {
            val result = mediaAgent.handleCommand("skip")
            ToolResult(toolName = name, callId = "", success = true, output = result)
        } catch (e: Exception) {
            ToolResult(toolName = name, callId = "", success = false, output = "", error = e.message)
        }
    }
}

class PreviousTrackTool @Inject constructor(
    private val mediaAgent: MediaAgent
) : ToolHandler {

    override val name = "previous_track"
    override val toolset = "media"
    override val definition = ToolDefinition(
        name = "previous_track",
        description = "Go back to the previous track.",
        parameters = ToolParameters()
    )

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        return try {
            val result = mediaAgent.handleCommand("previous")
            ToolResult(toolName = name, callId = "", success = true, output = result)
        } catch (e: Exception) {
            ToolResult(toolName = name, callId = "", success = false, output = "", error = e.message)
        }
    }
}

class NowPlayingTool @Inject constructor(
    private val mediaAgent: MediaAgent
) : ToolHandler {

    override val name = "now_playing"
    override val toolset = "media"
    override val definition = ToolDefinition(
        name = "now_playing",
        description = "Check what is currently playing.",
        parameters = ToolParameters()
    )

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        return try {
            val result = mediaAgent.handleCommand("what's playing")
            ToolResult(toolName = name, callId = "", success = true, output = result)
        } catch (e: Exception) {
            ToolResult(toolName = name, callId = "", success = false, output = "", error = e.message)
        }
    }
}

class QueueMediaTool @Inject constructor(
    private val mediaAgent: MediaAgent
) : ToolHandler {

    override val name = "queue_media"
    override val toolset = "media"
    override val definition = ToolDefinition(
        name = "queue_media",
        description = "Add media to the playback queue without playing it immediately.",
        parameters = ToolParameters(
            properties = mapOf(
                "query" to ToolProperty("string", "What to add to the queue"),
                "source" to ToolProperty("string", "Media source", listOf("spotify", "youtube", "audible"))
            ),
            required = listOf("query")
        )
    )

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val query = arguments["query"] ?: return ToolResult(
            toolName = name, callId = "", success = false, output = "", error = "Missing 'query'"
        )
        val source = arguments["source"]
        val input = if (source != null) "add to queue $query on $source" else "add to queue $query"

        return try {
            val result = mediaAgent.handleCommand(input)
            ToolResult(toolName = name, callId = "", success = true, output = result)
        } catch (e: Exception) {
            ToolResult(toolName = name, callId = "", success = false, output = "", error = e.message)
        }
    }
}
