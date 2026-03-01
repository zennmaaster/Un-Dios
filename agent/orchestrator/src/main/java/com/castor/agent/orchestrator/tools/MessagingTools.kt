package com.castor.agent.orchestrator.tools

import com.castor.agent.orchestrator.MessagingAgent
import com.castor.core.inference.tool.ToolDefinition
import com.castor.core.inference.tool.ToolHandler
import com.castor.core.inference.tool.ToolParameters
import com.castor.core.inference.tool.ToolProperty
import com.castor.core.inference.tool.ToolResult
import javax.inject.Inject

/**
 * Tool handlers for messaging operations.
 * Wraps [MessagingAgent] methods as structured tools for the LLM.
 */

class SendMessageTool @Inject constructor(
    private val messagingAgent: MessagingAgent
) : ToolHandler {

    override val name = "send_message"
    override val toolset = "messaging"
    override val definition = ToolDefinition(
        name = "send_message",
        description = "Compose and draft a message to a contact. Use when the user wants to text, reply to, or message someone.",
        parameters = ToolParameters(
            properties = mapOf(
                "recipient" to ToolProperty("string", "The name of the person to message"),
                "content" to ToolProperty("string", "What the user wants to say (natural language instruction)"),
                "platform" to ToolProperty("string", "Messaging platform", listOf("whatsapp", "teams"))
            ),
            required = listOf("recipient", "content")
        )
    )

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val recipient = arguments["recipient"] ?: return ToolResult(
            toolName = name, callId = "", success = false, output = "", error = "Missing 'recipient'"
        )
        val content = arguments["content"] ?: return ToolResult(
            toolName = name, callId = "", success = false, output = "", error = "Missing 'content'"
        )
        val platform = arguments["platform"] ?: "whatsapp"

        return try {
            val instruction = "tell $recipient on $platform: $content"
            val composed = messagingAgent.composeMessage(instruction)
            ToolResult(
                toolName = name, callId = "", success = true,
                output = "Draft message for $recipient ($platform): \"$composed\""
            )
        } catch (e: Exception) {
            ToolResult(toolName = name, callId = "", success = false, output = "", error = e.message)
        }
    }
}

class SummarizeMessagesTool @Inject constructor(
    private val messagingAgent: MessagingAgent
) : ToolHandler {

    override val name = "summarize_messages"
    override val toolset = "messaging"
    override val definition = ToolDefinition(
        name = "summarize_messages",
        description = "Summarize recent messages or a conversation. Use when the user asks what they missed or wants a recap.",
        parameters = ToolParameters(
            properties = mapOf(
                "conversation" to ToolProperty("string", "Which conversation or contact to summarize (or 'all' for all recent)")
            ),
            required = emptyList()
        )
    )

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        return try {
            // Use summarizeThread with empty list to get general summary
            // In a real implementation, this would fetch messages from the repository
            val result = messagingAgent.summarizeThread(emptyList())
            ToolResult(toolName = name, callId = "", success = true, output = result)
        } catch (e: Exception) {
            ToolResult(toolName = name, callId = "", success = false, output = "", error = e.message)
        }
    }
}

class SmartReplyTool @Inject constructor(
    private val messagingAgent: MessagingAgent
) : ToolHandler {

    override val name = "smart_reply"
    override val toolset = "messaging"
    override val definition = ToolDefinition(
        name = "smart_reply",
        description = "Generate smart reply suggestions for a conversation.",
        parameters = ToolParameters(
            properties = mapOf(
                "conversation" to ToolProperty("string", "Which conversation to generate replies for")
            ),
            required = listOf("conversation")
        )
    )

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        return try {
            val replies = messagingAgent.generateSmartReplies(emptyList())
            val output = replies.mapIndexed { i, reply -> "${i + 1}. $reply" }.joinToString("\n")
            ToolResult(toolName = name, callId = "", success = true, output = output)
        } catch (e: Exception) {
            ToolResult(toolName = name, callId = "", success = false, output = "", error = e.message)
        }
    }
}
