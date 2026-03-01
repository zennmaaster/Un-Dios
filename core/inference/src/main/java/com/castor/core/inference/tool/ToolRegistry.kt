package com.castor.core.inference.tool

import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central registry for all tools the on-device LLM can call.
 *
 * Inspired by Hermes Agent's `tools/registry.py`. Key responsibilities:
 * - Store tool handlers indexed by name
 * - Filter by availability at prompt-build time
 * - Dispatch tool calls by name
 * - Generate the `<tools>` XML block for Qwen2.5 ChatML function calling
 *
 * Thread-safe: uses [ConcurrentHashMap] for storage.
 */
@Singleton
class ToolRegistry @Inject constructor() {

    companion object {
        private const val TAG = "ToolRegistry"
    }

    private val tools = ConcurrentHashMap<String, ToolHandler>()
    private val json = Json { prettyPrint = false; encodeDefaults = true }

    /**
     * Register a tool handler. Replaces any existing handler with the same name.
     */
    fun register(handler: ToolHandler) {
        tools[handler.name] = handler
        Log.d(TAG, "Registered tool: ${handler.name} (toolset=${handler.toolset})")
    }

    /**
     * Unregister a tool by name.
     */
    fun unregister(name: String) {
        tools.remove(name)
    }

    /**
     * Get all registered tools that are currently available.
     */
    fun getAvailableTools(): List<ToolHandler> =
        tools.values.filter {
            try {
                it.isAvailable()
            } catch (e: Exception) {
                Log.w(TAG, "Tool ${it.name} availability check failed: ${e.message}")
                false
            }
        }

    /**
     * Get available tools in a specific toolset.
     */
    fun getAvailableToolsByToolset(toolset: String): List<ToolHandler> =
        getAvailableTools().filter { it.toolset == toolset }

    /**
     * Look up a tool by name (regardless of availability).
     */
    fun getTool(name: String): ToolHandler? = tools[name]

    /**
     * Get the count of all registered tools.
     */
    fun registeredCount(): Int = tools.size

    /**
     * Dispatch a tool call to the appropriate handler.
     */
    suspend fun dispatch(call: ToolCall): ToolResult {
        val handler = tools[call.name]
            ?: return ToolResult(
                toolName = call.name,
                callId = call.id,
                success = false,
                output = "",
                error = "Unknown tool: ${call.name}"
            )

        if (!handler.isAvailable()) {
            return ToolResult(
                toolName = call.name,
                callId = call.id,
                success = false,
                output = "",
                error = "Tool '${call.name}' is currently unavailable"
            )
        }

        val args = call.arguments.mapValues { (_, v) ->
            val str = v.toString()
            if (str.startsWith("\"") && str.endsWith("\"")) {
                str.substring(1, str.length - 1)
            } else {
                str
            }
        }

        return try {
            Log.d(TAG, "Dispatching tool: ${call.name} with args: $args")
            handler.execute(args)
        } catch (e: Exception) {
            Log.e(TAG, "Tool ${call.name} execution failed", e)
            ToolResult(
                toolName = call.name,
                callId = call.id,
                success = false,
                output = "",
                error = "Tool execution failed: ${e.message}"
            )
        }
    }

    /**
     * Generate the tools prompt block for Qwen2.5 ChatML function calling.
     *
     * Produces the format Qwen2.5-Instruct was trained on:
     * ```
     * # Tools
     *
     * You may call one or more functions to assist with the user query.
     *
     * You are provided with function signatures within <tools></tools> XML tags:
     * <tools>
     * [{"type":"function","function":{"name":"...","description":"...","parameters":{...}}}]
     * </tools>
     *
     * For each function call, return a json object with function name and arguments
     * within <tool_call></tool_call> XML tags:
     * <tool_call>
     * {"name": "function_name", "arguments": {"arg1": "value1"}}
     * </tool_call>
     * ```
     */
    fun generateToolsPromptBlock(): String {
        val available = getAvailableTools()
        if (available.isEmpty()) return ""

        val toolSchemas = available.map { handler ->
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to handler.definition.name,
                    "description" to handler.definition.description,
                    "parameters" to mapOf(
                        "type" to handler.definition.parameters.type,
                        "properties" to handler.definition.parameters.properties.mapValues { (_, prop) ->
                            buildMap {
                                put("type", prop.type)
                                put("description", prop.description)
                                if (prop.enum != null) put("enum", prop.enum)
                            }
                        },
                        "required" to handler.definition.parameters.required
                    )
                )
            )
        }

        val toolsJson = json.encodeToString(toolSchemas)

        return buildString {
            appendLine()
            appendLine("# Tools")
            appendLine()
            appendLine("You may call one or more functions to assist with the user query.")
            appendLine()
            appendLine("You are provided with function signatures within <tools></tools> XML tags:")
            appendLine("<tools>")
            appendLine(toolsJson)
            appendLine("</tools>")
            appendLine()
            appendLine("For each function call, return a json object with function name and arguments within <tool_call></tool_call> XML tags:")
            appendLine("<tool_call>")
            appendLine("{\"name\": \"function_name\", \"arguments\": {\"arg1\": \"value1\"}}")
            appendLine("</tool_call>")
        }
    }
}
