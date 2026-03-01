package com.castor.core.inference.tool

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * JSON schema describing a tool the LLM can invoke.
 * Follows the OpenAI/ChatML function calling format that Qwen2.5 was trained on.
 */
@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: ToolParameters
)

@Serializable
data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, ToolProperty> = emptyMap(),
    val required: List<String> = emptyList()
)

@Serializable
data class ToolProperty(
    val type: String,
    val description: String,
    val enum: List<String>? = null
)

/**
 * A tool call parsed from the LLM's `<tool_call>` output.
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject
)

/**
 * Result of executing a tool.
 */
data class ToolResult(
    val toolName: String,
    val callId: String,
    val success: Boolean,
    val output: String,
    val error: String? = null
)
