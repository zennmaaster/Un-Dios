package com.castor.core.inference.tool

/**
 * Interface for tool implementations.
 *
 * Inspired by Hermes Agent's handler + check_fn pattern.
 * Each tool provides its schema, availability check, and execution logic.
 */
interface ToolHandler {

    /** Unique tool name matching the [ToolDefinition.name]. */
    val name: String

    /** The domain/toolset this tool belongs to (e.g. "media", "messaging", "system"). */
    val toolset: String

    /** JSON schema for this tool, used in system prompt injection. */
    val definition: ToolDefinition

    /**
     * Check if this tool is currently available.
     * Override to check preconditions (e.g. model loaded, service reachable).
     * Tools that return false are excluded from the LLM's tool list.
     */
    fun isAvailable(): Boolean = true

    /**
     * Execute the tool with parsed arguments.
     *
     * @param arguments Key-value pairs extracted from the LLM's tool call JSON.
     * @return A [ToolResult] with the outcome.
     */
    suspend fun execute(arguments: Map<String, String>): ToolResult
}
