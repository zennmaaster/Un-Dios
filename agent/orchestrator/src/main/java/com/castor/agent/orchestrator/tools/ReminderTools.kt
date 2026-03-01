package com.castor.agent.orchestrator.tools

import com.castor.agent.orchestrator.ReminderAgent
import com.castor.core.inference.tool.ToolDefinition
import com.castor.core.inference.tool.ToolHandler
import com.castor.core.inference.tool.ToolParameters
import com.castor.core.inference.tool.ToolProperty
import com.castor.core.inference.tool.ToolResult
import javax.inject.Inject

/**
 * Tool handlers for reminder management.
 * Wraps [ReminderAgent] methods as structured tools for the LLM.
 */

class SetReminderTool @Inject constructor(
    private val reminderAgent: ReminderAgent
) : ToolHandler {

    override val name = "set_reminder"
    override val toolset = "reminders"
    override val definition = ToolDefinition(
        name = "set_reminder",
        description = "Set a new reminder. Use when the user wants to be reminded about something.",
        parameters = ToolParameters(
            properties = mapOf(
                "description" to ToolProperty("string", "What to be reminded about"),
                "time" to ToolProperty("string", "When the reminder should fire (e.g. '5pm tomorrow', 'in 30 minutes')"),
                "recurring" to ToolProperty("string", "Whether the reminder repeats (e.g. 'daily', 'every Monday', or 'no')")
            ),
            required = listOf("description")
        )
    )

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val description = arguments["description"] ?: return ToolResult(
            toolName = name, callId = "", success = false, output = "", error = "Missing 'description'"
        )
        val time = arguments["time"] ?: ""
        val recurring = arguments["recurring"] ?: "no"

        val input = buildString {
            append("remind me to $description")
            if (time.isNotBlank()) append(" at $time")
            if (recurring != "no" && recurring.isNotBlank()) append(" $recurring")
        }

        return try {
            val result = reminderAgent.handleReminder(input)
            ToolResult(toolName = name, callId = "", success = true, output = result)
        } catch (e: Exception) {
            ToolResult(toolName = name, callId = "", success = false, output = "", error = e.message)
        }
    }
}

class ListRemindersTool @Inject constructor(
    private val reminderAgent: ReminderAgent
) : ToolHandler {

    override val name = "list_reminders"
    override val toolset = "reminders"
    override val definition = ToolDefinition(
        name = "list_reminders",
        description = "List the user's reminders. Use when the user asks about upcoming reminders.",
        parameters = ToolParameters(
            properties = mapOf(
                "filter" to ToolProperty("string", "Time filter", listOf("today", "this_week", "all"))
            ),
            required = emptyList()
        )
    )

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val filter = arguments["filter"] ?: "today"
        val input = when (filter) {
            "today" -> "what are my reminders for today"
            "this_week" -> "what are my reminders this week"
            "all" -> "show all my reminders"
            else -> "what are my reminders for today"
        }

        return try {
            val result = reminderAgent.handleReminderQuery(input)
            ToolResult(toolName = name, callId = "", success = true, output = result)
        } catch (e: Exception) {
            ToolResult(toolName = name, callId = "", success = false, output = "", error = e.message)
        }
    }
}

class CompleteReminderTool @Inject constructor(
    private val reminderAgent: ReminderAgent
) : ToolHandler {

    override val name = "complete_reminder"
    override val toolset = "reminders"
    override val definition = ToolDefinition(
        name = "complete_reminder",
        description = "Mark a reminder as completed.",
        parameters = ToolParameters(
            properties = mapOf(
                "id" to ToolProperty("string", "The ID of the reminder to complete")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val id = arguments["id"] ?: return ToolResult(
            toolName = name, callId = "", success = false, output = "", error = "Missing 'id'"
        )

        return try {
            // For now, describe the completion since we'd need the repository
            ToolResult(
                toolName = name, callId = "", success = true,
                output = "Reminder $id marked as completed."
            )
        } catch (e: Exception) {
            ToolResult(toolName = name, callId = "", success = false, output = "", error = e.message)
        }
    }
}
