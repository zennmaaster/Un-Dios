package com.castor.core.inference.tool

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Parses `<tool_call>` blocks from LLM output.
 *
 * Qwen2.5-Instruct emits tool calls in this format:
 * ```
 * <tool_call>
 * {"name": "play_media", "arguments": {"query": "jazz", "source": "spotify"}}
 * </tool_call>
 * ```
 *
 * This parser extracts all such blocks from a response and converts them
 * into [ToolCall] objects for dispatch via the [ToolRegistry].
 */
object ToolCallParser {

    private const val TAG = "ToolCallParser"

    private val TOOL_CALL_REGEX = Regex(
        """<tool_call>\s*(\{.*?\})\s*</tool_call>""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Parse all `<tool_call>` blocks from the LLM output.
     *
     * @return List of parsed [ToolCall]s, or empty list if none found.
     */
    fun parse(llmOutput: String): List<ToolCall> {
        val matches = TOOL_CALL_REGEX.findAll(llmOutput)
        return matches.mapNotNull { match ->
            try {
                val jsonStr = match.groupValues[1].trim()
                val obj = json.parseToJsonElement(jsonStr).jsonObject
                val name = obj["name"]?.toString()?.removeSurrounding("\"")
                    ?: return@mapNotNull null
                val args = obj["arguments"]?.jsonObject ?: JsonObject(emptyMap())
                ToolCall(
                    id = "call_${System.nanoTime()}",
                    name = name,
                    arguments = args
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse tool call: ${e.message}")
                null
            }
        }.toList()
    }

    /**
     * Quick check whether the output contains any tool calls.
     */
    fun hasToolCalls(llmOutput: String): Boolean =
        TOOL_CALL_REGEX.containsMatchIn(llmOutput)

    /**
     * Strip all `<tool_call>` blocks from the output, returning only plain text.
     */
    fun stripToolCalls(llmOutput: String): String =
        TOOL_CALL_REGEX.replace(llmOutput, "").trim()

    /**
     * Format a tool result for injection back into the conversation.
     *
     * Produces the `<tool_response>` format Qwen2.5 expects:
     * ```
     * <tool_response>
     * {"name": "play_media", "content": "Now playing jazz on Spotify."}
     * </tool_response>
     * ```
     */
    fun formatToolResponse(call: ToolCall, result: ToolResult): String {
        val content = if (result.success) result.output else (result.error ?: "Tool failed")
        return buildString {
            appendLine("<tool_response>")
            appendLine("{\"name\": \"${call.name}\", \"content\": ${json.encodeToString(kotlinx.serialization.serializer<String>(), content)}}")
            appendLine("</tool_response>")
        }
    }
}
