package com.castor.agent.orchestrator

import com.castor.core.inference.tool.ToolRegistry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles the multi-layer system prompt for the agent loop.
 *
 * Inspired by Hermes Agent's `prompt_builder.py`. The system prompt is
 * composed of several layers injected in order:
 *
 * 1. **Identity**: Who Un-Dios is and its core principles
 * 2. **Date/Time**: Current date/time for temporal context
 * 3. **Memory**: Persistent memories from previous sessions
 * 4. **Tools**: The `<tools>` block from [ToolRegistry] (injected separately
 *    via [PromptFormatter.formatMultiTurnWithTools])
 * 5. **Behavioral instructions**: When to use tools vs. answer directly
 */
@Singleton
class PromptBuilder @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val memoryManager: MemoryManager
) {

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.US)

        private const val IDENTITY = """You are Un-Dios, an AI assistant running entirely on the user's Android phone.
All computation and data stays on-device — nothing is sent to the cloud.
You help the user with messaging, media playback, reminders, and general questions."""

        private const val BEHAVIORAL_INSTRUCTIONS = """
# Instructions
- Use tools when the user asks you to take an action (play music, send a message, set a reminder, save something to memory).
- Answer directly WITHOUT tools when the user asks a question you can answer from knowledge or conversation context.
- When you use a tool, wait for the result before responding to the user.
- If you learn something important about the user (preferences, habits, names), use save_memory to remember it.
- Be concise. One or two sentences is usually enough.
- Never fabricate tool results. If a tool fails, tell the user honestly."""
    }

    /**
     * Build the system prompt content (without tools block — that's injected
     * by [PromptFormatter.formatMultiTurnWithTools]).
     */
    suspend fun buildSystemPrompt(): String = buildString {
        // Layer 1: Identity
        append(IDENTITY)
        appendLine()

        // Layer 2: Date/Time
        appendLine()
        appendLine("Current date and time: ${DATE_FORMAT.format(Date())}")

        // Layer 3: Memory
        val memoryBlock = memoryManager.buildMemoryPromptBlock()
        if (memoryBlock.isNotBlank()) {
            append(memoryBlock)
        }

        // Layer 4: Behavioral instructions
        append(BEHAVIORAL_INSTRUCTIONS)
    }

    /**
     * Get the tools prompt block for injection into the system turn.
     * Returns empty string if no tools are available.
     */
    fun getToolsBlock(): String {
        return toolRegistry.generateToolsPromptBlock()
    }
}
