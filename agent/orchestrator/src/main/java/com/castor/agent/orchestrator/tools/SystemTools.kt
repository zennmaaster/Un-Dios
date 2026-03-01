package com.castor.agent.orchestrator.tools

import com.castor.agent.orchestrator.AgentHealthMonitor
import com.castor.agent.orchestrator.MemoryManager
import com.castor.core.inference.tool.ToolDefinition
import com.castor.core.inference.tool.ToolHandler
import com.castor.core.inference.tool.ToolParameters
import com.castor.core.inference.tool.ToolProperty
import com.castor.core.inference.tool.ToolResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * System-level tool handlers: time, status, and persistent memory.
 */

class GetTimeTool @Inject constructor() : ToolHandler {

    override val name = "get_time"
    override val toolset = "system"
    override val definition = ToolDefinition(
        name = "get_time",
        description = "Get the current date and time. Use when the user asks what time or day it is.",
        parameters = ToolParameters()
    )

    private val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm:ss a z", Locale.US)

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        return ToolResult(
            toolName = name, callId = "", success = true,
            output = dateFormat.format(Date())
        )
    }
}

class GetStatusTool @Inject constructor(
    private val healthMonitor: AgentHealthMonitor
) : ToolHandler {

    override val name = "get_status"
    override val toolset = "system"
    override val definition = ToolDefinition(
        name = "get_status",
        description = "Get the system status including agent health and model info.",
        parameters = ToolParameters()
    )

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        return try {
            val report = healthMonitor.getStatusReport()
            ToolResult(toolName = name, callId = "", success = true, output = report)
        } catch (e: Exception) {
            ToolResult(toolName = name, callId = "", success = false, output = "", error = e.message)
        }
    }
}

class SaveMemoryTool @Inject constructor(
    private val memoryManager: MemoryManager
) : ToolHandler {

    override val name = "save_memory"
    override val toolset = "system"
    override val definition = ToolDefinition(
        name = "save_memory",
        description = "Save a fact or preference to persistent memory for recall in future sessions. Use when you learn something important about the user.",
        parameters = ToolParameters(
            properties = mapOf(
                "category" to ToolProperty("string", "Memory category", listOf("user_profile", "agent_note")),
                "key" to ToolProperty("string", "A short key describing the memory (e.g. 'favorite_music', 'work_schedule')"),
                "value" to ToolProperty("string", "The value to remember")
            ),
            required = listOf("category", "key", "value")
        )
    )

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val category = arguments["category"] ?: return ToolResult(
            toolName = name, callId = "", success = false, output = "", error = "Missing 'category'"
        )
        val key = arguments["key"] ?: return ToolResult(
            toolName = name, callId = "", success = false, output = "", error = "Missing 'key'"
        )
        val value = arguments["value"] ?: return ToolResult(
            toolName = name, callId = "", success = false, output = "", error = "Missing 'value'"
        )

        return try {
            memoryManager.saveMemory(category, key, value)
            ToolResult(
                toolName = name, callId = "", success = true,
                output = "Saved to memory: [$category] $key = $value"
            )
        } catch (e: Exception) {
            ToolResult(toolName = name, callId = "", success = false, output = "", error = e.message)
        }
    }
}

class RecallMemoryTool @Inject constructor(
    private val memoryManager: MemoryManager
) : ToolHandler {

    override val name = "recall_memory"
    override val toolset = "system"
    override val definition = ToolDefinition(
        name = "recall_memory",
        description = "Recall previously saved memories. Use to look up user preferences or agent notes from past sessions.",
        parameters = ToolParameters(
            properties = mapOf(
                "category" to ToolProperty("string", "Category to search in", listOf("user_profile", "agent_note")),
                "search" to ToolProperty("string", "Optional search term to filter memories")
            ),
            required = emptyList()
        )
    )

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val category = arguments["category"]
        val search = arguments["search"]

        return try {
            val memories = memoryManager.recallMemory(category, search)
            if (memories.isEmpty()) {
                ToolResult(
                    toolName = name, callId = "", success = true,
                    output = "No memories found${category?.let { " in category '$it'" } ?: ""}${search?.let { " matching '$it'" } ?: ""}."
                )
            } else {
                val output = memories.joinToString("\n") { m ->
                    "[${m.category}] ${m.key}: ${m.value}"
                }
                ToolResult(toolName = name, callId = "", success = true, output = output)
            }
        } catch (e: Exception) {
            ToolResult(toolName = name, callId = "", success = false, output = "", error = e.message)
        }
    }
}
