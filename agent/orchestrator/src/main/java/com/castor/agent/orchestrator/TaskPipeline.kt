package com.castor.agent.orchestrator

import com.castor.core.common.model.AgentType
import com.castor.core.inference.InferenceEngine
import javax.inject.Inject
import javax.inject.Singleton

// =========================================================================================
// Data models
// =========================================================================================

/**
 * A single step in a multi-step task pipeline.
 *
 * @param agentType      Which agent should execute this step.
 * @param action         A human-readable description of what this step does.
 * @param inputData      Key-value pairs of input for the step.
 * @param dependsOnStep  Index (0-based) of a prior step whose output is required
 *                       as input to this step. Null if the step is independent.
 */
data class PipelineStep(
    val agentType: AgentType,
    val action: String,
    val inputData: Map<String, String>,
    val dependsOnStep: Int? = null
)

/**
 * The result of executing a single pipeline step.
 *
 * @param step    The step that was executed.
 * @param output  The textual output produced by the step.
 * @param success Whether the step completed without error.
 * @param error   An error message if the step failed.
 */
data class StepResult(
    val step: PipelineStep,
    val output: String,
    val success: Boolean,
    val error: String? = null
)

/**
 * The final result of an entire pipeline execution.
 *
 * @param success Whether all steps completed successfully.
 * @param steps   The ordered list of step results.
 * @param summary A user-facing summary of what the pipeline accomplished.
 */
data class PipelineResult(
    val success: Boolean,
    val steps: List<StepResult>,
    val summary: String
)

// =========================================================================================
// TaskPipeline
// =========================================================================================

/**
 * Multi-step task execution engine for compound commands.
 *
 * Some user requests naturally decompose into multiple agent actions that must execute
 * in sequence with data flowing between them. For example:
 *
 * - "Remind me about John's WhatsApp message tomorrow"
 *   1. MessagingAgent: find John's latest WhatsApp message
 *   2. ReminderAgent: create a reminder with the message content
 *
 * - "Play what I was listening to yesterday and set a timer for 30 minutes"
 *   1. MediaAgent: resume previous playback
 *   2. ReminderAgent: set a 30-minute timer
 *
 * The pipeline supports:
 * - Sequential step execution with dependency tracking
 * - Data passing between steps (output of step N becomes input to step N+1)
 * - Partial success: if a non-critical step fails, the pipeline continues
 * - LLM-powered decomposition for complex commands, with keyword fallback
 *
 * Usage:
 * - Call [isCompoundCommand] to check if an input needs pipeline execution.
 * - Call [decompose] to break an input into steps.
 * - Call [executePipeline] to run a pre-built set of steps.
 * - Call [decomposeAndExecute] for one-shot compound command handling.
 */
@Singleton
class TaskPipeline @Inject constructor(
    private val engine: InferenceEngine,
    private val messagingAgent: MessagingAgent,
    private val mediaAgent: MediaAgent,
    private val reminderAgent: ReminderAgent
) {

    companion object {
        private const val DECOMPOSE_MAX_TOKENS = 384

        private const val DECOMPOSE_SYSTEM_PROMPT = """You are a task decomposer for an Android assistant called Un-Dios.
Given a compound user command, break it into sequential steps. Each step must be handled by one agent.

Available agents:
- MESSAGING: Read, search, summarize, or send messages (WhatsApp, Teams)
- MEDIA: Play, pause, skip, queue music/podcasts/audiobooks (Spotify, YouTube, Audible)
- REMINDER: Set, query, or manage reminders and timers
- GENERAL: Answer questions, provide information, general conversation

Respond in EXACTLY this format (one step per line, NO extra text):

STEP 1: AGENT=<agent>, ACTION=<what to do>, INPUT=<key input for this step>
STEP 2: AGENT=<agent>, ACTION=<what to do>, INPUT=<key input or PREV_OUTPUT>
...

Rules:
- Use PREV_OUTPUT in the INPUT field when a step needs the output of the previous step
- Keep it to 2-4 steps maximum
- Each step should be a single, clear action
- If the command is not compound (just one action), output a single step

Examples:
"Remind me about John's latest message tomorrow"
STEP 1: AGENT=MESSAGING, ACTION=find latest message from John, INPUT=John's latest message
STEP 2: AGENT=REMINDER, ACTION=set reminder with message content, INPUT=PREV_OUTPUT tomorrow

"Play my workout playlist and set a 30 minute timer"
STEP 1: AGENT=MEDIA, ACTION=play workout playlist, INPUT=workout playlist
STEP 2: AGENT=REMINDER, ACTION=set timer for 30 minutes, INPUT=30 minutes

"Summarize my unread messages and text Mom the highlights"
STEP 1: AGENT=MESSAGING, ACTION=summarize unread messages, INPUT=unread messages
STEP 2: AGENT=MESSAGING, ACTION=compose message to Mom with summary, INPUT=PREV_OUTPUT"""

        // Compound command indicators: keywords/patterns suggesting multi-step tasks
        private val COMPOUND_INDICATORS = listOf(
            // Conjunctions joining distinct actions
            Regex("""\b(?:and then|then|and also|also|after that|afterwards)\b""", RegexOption.IGNORE_CASE),
            // Two distinct action verbs separated by "and"
            Regex("""(?:play|send|remind|set|summarize|text|message|queue)\b.*\band\b.*(?:play|send|remind|set|summarize|text|message|queue)\b""", RegexOption.IGNORE_CASE),
            // Cross-agent references like "remind me about X's message"
            Regex("""remind\s+me\s+(?:about|of)\s+.+(?:message|text|chat)""", RegexOption.IGNORE_CASE),
            // "... and set a ..." pattern
            Regex("""\band\s+set\s+(?:a|an)\b""", RegexOption.IGNORE_CASE),
            // "... and play ..."
            Regex("""\band\s+play\b""", RegexOption.IGNORE_CASE)
        )
    }

    // -------------------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------------------

    /**
     * Determine if a user input is a compound command that should be decomposed into
     * multiple pipeline steps rather than handled by a single agent.
     */
    fun isCompoundCommand(input: String): Boolean {
        return COMPOUND_INDICATORS.any { it.containsMatchIn(input) }
    }

    /**
     * Decompose a compound user command into a list of [PipelineStep]s.
     *
     * Uses the LLM for decomposition when available, with a keyword-based fallback.
     */
    suspend fun decompose(input: String): List<PipelineStep> {
        return if (engine.isLoaded) {
            decomposeWithLlm(input)
        } else {
            decomposeWithKeywords(input)
        }
    }

    /**
     * Execute a pre-built pipeline of steps sequentially.
     *
     * Steps with [PipelineStep.dependsOnStep] set will receive the output of the
     * referenced step injected into their input data under the key "previousOutput".
     *
     * @param steps The ordered list of steps to execute.
     * @return A [PipelineResult] with the outcome of each step and an overall summary.
     */
    suspend fun executePipeline(steps: List<PipelineStep>): PipelineResult {
        if (steps.isEmpty()) {
            return PipelineResult(
                success = false,
                steps = emptyList(),
                summary = "No steps to execute."
            )
        }

        val results = mutableListOf<StepResult>()
        var allSucceeded = true

        for ((index, step) in steps.withIndex()) {
            // Resolve dependencies: inject output from a prior step if needed
            val resolvedInput = if (step.dependsOnStep != null) {
                val depIndex = step.dependsOnStep
                if (depIndex in results.indices && results[depIndex].success) {
                    step.inputData + ("previousOutput" to results[depIndex].output)
                } else {
                    // Dependency failed — skip this step
                    val failResult = StepResult(
                        step = step,
                        output = "",
                        success = false,
                        error = "Dependency on step ${depIndex + 1} failed or is out of range."
                    )
                    results.add(failResult)
                    allSucceeded = false
                    continue
                }
            } else {
                step.inputData
            }

            val result = executeStep(step.copy(inputData = resolvedInput))
            results.add(result)
            if (!result.success) {
                allSucceeded = false
            }
        }

        val summary = buildPipelineSummary(results, allSucceeded)
        return PipelineResult(
            success = allSucceeded,
            steps = results,
            summary = summary
        )
    }

    /**
     * One-shot convenience: decompose a compound command and execute the resulting pipeline.
     */
    suspend fun decomposeAndExecute(input: String): PipelineResult {
        val steps = decompose(input)
        return executePipeline(steps)
    }

    // -------------------------------------------------------------------------------------
    // Step execution
    // -------------------------------------------------------------------------------------

    /**
     * Execute a single pipeline step by routing to the appropriate agent.
     */
    private suspend fun executeStep(step: PipelineStep): StepResult {
        return try {
            val input = buildStepInput(step)
            val output = when (step.agentType) {
                AgentType.MESSAGING -> executeMessagingStep(step, input)
                AgentType.MEDIA -> executeMediaStep(input)
                AgentType.REMINDER -> executeReminderStep(step, input)
                AgentType.GENERAL -> executeGeneralStep(input)
                AgentType.ROUTER -> executeGeneralStep(input)
            }
            StepResult(step = step, output = output, success = true)
        } catch (e: Exception) {
            StepResult(
                step = step,
                output = "",
                success = false,
                error = e.message ?: "Unknown error executing step."
            )
        }
    }

    /**
     * Build the input string for a step from its input data map.
     */
    private fun buildStepInput(step: PipelineStep): String {
        val previousOutput = step.inputData["previousOutput"]
        val rawInput = step.inputData["input"] ?: step.action

        return if (previousOutput != null) {
            "$rawInput\n\nContext from previous step: $previousOutput"
        } else {
            rawInput
        }
    }

    private suspend fun executeMessagingStep(step: PipelineStep, input: String): String {
        val action = step.action.lowercase()
        return when {
            action.contains("summarize") || action.contains("summary") -> {
                // Use the engine to summarize since we do not have conversation context
                if (engine.isLoaded) {
                    engine.generate(
                        prompt = "Summarize: $input",
                        systemPrompt = "You are a helpful assistant. Provide a brief summary.",
                        maxTokens = 256,
                        temperature = 0.5f
                    ).trim()
                } else {
                    "Summary of messages requested. Model not loaded for summarization."
                }
            }
            action.contains("find") || action.contains("search") || action.contains("latest") -> {
                // Describe the search action; actual message retrieval would need MessageRepository
                "Searching for messages matching: $input"
            }
            action.contains("compose") || action.contains("send") || action.contains("text") -> {
                messagingAgent.composeMessage(input)
            }
            else -> {
                messagingAgent.composeMessage(input)
            }
        }
    }

    private suspend fun executeMediaStep(input: String): String {
        return mediaAgent.handleCommand(input)
    }

    private suspend fun executeReminderStep(step: PipelineStep, input: String): String {
        val action = step.action.lowercase()
        return when {
            action.contains("query") || action.contains("list") || action.contains("show") || action.contains("what") -> {
                reminderAgent.handleReminderQuery(input)
            }
            else -> {
                reminderAgent.handleReminder(input)
            }
        }
    }

    private suspend fun executeGeneralStep(input: String): String {
        return if (engine.isLoaded) {
            engine.generate(
                prompt = input,
                systemPrompt = "You are Un-Dios, a helpful AI assistant. Answer concisely.",
                maxTokens = 256,
                temperature = 0.7f
            ).trim()
        } else {
            "I understand your request, but the language model is not currently loaded."
        }
    }

    // -------------------------------------------------------------------------------------
    // LLM-based decomposition
    // -------------------------------------------------------------------------------------

    private suspend fun decomposeWithLlm(input: String): List<PipelineStep> {
        return try {
            val response = engine.generate(
                prompt = input,
                systemPrompt = DECOMPOSE_SYSTEM_PROMPT,
                maxTokens = DECOMPOSE_MAX_TOKENS,
                temperature = 0.2f
            )
            parseLlmDecomposition(response)
        } catch (e: Exception) {
            decomposeWithKeywords(input)
        }
    }

    /**
     * Parse the LLM decomposition response into a list of [PipelineStep]s.
     */
    private fun parseLlmDecomposition(response: String): List<PipelineStep> {
        val stepPattern = Regex(
            """STEP\s+\d+:\s*AGENT=(\w+),\s*ACTION=(.+?),\s*INPUT=(.+?)(?:\s*$)""",
            RegexOption.MULTILINE
        )

        val steps = mutableListOf<PipelineStep>()

        for (match in stepPattern.findAll(response)) {
            val agentStr = match.groupValues[1].uppercase().trim()
            val action = match.groupValues[2].trim()
            val inputStr = match.groupValues[3].trim()

            val agentType = when (agentStr) {
                "MESSAGING" -> AgentType.MESSAGING
                "MEDIA" -> AgentType.MEDIA
                "REMINDER" -> AgentType.REMINDER
                "GENERAL" -> AgentType.GENERAL
                else -> AgentType.GENERAL
            }

            val hasDependency = inputStr.contains("PREV_OUTPUT", ignoreCase = true)
            val cleanedInput = inputStr.replace("PREV_OUTPUT", "", ignoreCase = true).trim()
            val dependsOn = if (hasDependency && steps.isNotEmpty()) steps.size - 1 else null

            steps.add(
                PipelineStep(
                    agentType = agentType,
                    action = action,
                    inputData = mapOf("input" to cleanedInput),
                    dependsOnStep = dependsOn
                )
            )
        }

        return steps.ifEmpty {
            // If parsing fails completely, return a single general step
            listOf(
                PipelineStep(
                    agentType = AgentType.GENERAL,
                    action = "process request",
                    inputData = mapOf("input" to response)
                )
            )
        }
    }

    // -------------------------------------------------------------------------------------
    // Keyword-based fallback decomposition
    // -------------------------------------------------------------------------------------

    /**
     * Decompose a compound command using keyword analysis when the LLM is unavailable.
     *
     * Splits on "and then" / "then" / "and also" / "and", identifies each segment's
     * agent type via keyword matching, and builds pipeline steps accordingly.
     */
    private fun decomposeWithKeywords(input: String): List<PipelineStep> {
        // Split on conjunction boundaries
        val segments = input.split(
            Regex("""\s+(?:and then|then|and also|also|after that|afterwards)\s+""", RegexOption.IGNORE_CASE)
        ).flatMap { segment ->
            // Further split on " and " only if both sides look like distinct commands
            val andParts = segment.split(Regex("""\s+and\s+""", RegexOption.IGNORE_CASE))
            if (andParts.size == 2 && andParts.all { it.split(" ").size >= 3 }) {
                andParts
            } else {
                listOf(segment)
            }
        }.map { it.trim() }.filter { it.isNotBlank() }

        if (segments.size <= 1) {
            // Not really compound — return a single step
            val agentType = detectAgentType(input)
            return listOf(
                PipelineStep(
                    agentType = agentType,
                    action = input,
                    inputData = mapOf("input" to input)
                )
            )
        }

        // Check for cross-agent reference pattern: "remind me about X's message"
        val crossAgentMatch = Regex(
            """remind\s+me\s+(?:about|of)\s+(.+?)(?:'s|'s)?\s*(?:message|text|chat)(.*)""",
            RegexOption.IGNORE_CASE
        ).find(input)

        if (crossAgentMatch != null) {
            val contactName = crossAgentMatch.groupValues[1].trim()
            val timePart = crossAgentMatch.groupValues[2].trim()
            return listOf(
                PipelineStep(
                    agentType = AgentType.MESSAGING,
                    action = "find latest message from $contactName",
                    inputData = mapOf("input" to "$contactName latest message")
                ),
                PipelineStep(
                    agentType = AgentType.REMINDER,
                    action = "set reminder with message content",
                    inputData = mapOf("input" to "reminder about message $timePart"),
                    dependsOnStep = 0
                )
            )
        }

        // Build steps from segments
        val steps = mutableListOf<PipelineStep>()
        for ((index, segment) in segments.withIndex()) {
            val agentType = detectAgentType(segment)
            val dependsOn = if (index > 0 && segmentReferencesPrevious(segment)) index - 1 else null
            steps.add(
                PipelineStep(
                    agentType = agentType,
                    action = segment,
                    inputData = mapOf("input" to segment),
                    dependsOnStep = dependsOn
                )
            )
        }

        return steps
    }

    /**
     * Detect which agent should handle a given text segment based on keywords.
     */
    private fun detectAgentType(text: String): AgentType {
        val lowered = text.lowercase()
        return when {
            // Reminder keywords
            lowered.containsAny("remind", "reminder", "alarm", "timer", "schedule", "alert me", "notify me") ->
                AgentType.REMINDER
            // Media keywords
            lowered.containsAny("play", "pause", "skip", "queue", "music", "song", "podcast", "spotify", "youtube", "audible", "listen") ->
                AgentType.MEDIA
            // Messaging keywords
            lowered.containsAny("message", "text", "send", "reply", "whatsapp", "teams", "chat", "dm", "summarize", "summary", "unread") ->
                AgentType.MESSAGING
            else -> AgentType.GENERAL
        }
    }

    /**
     * Check if a segment references the output of a previous step
     * (e.g. "the highlights", "that", "it").
     */
    private fun segmentReferencesPrevious(text: String): Boolean {
        val lowered = text.lowercase()
        return lowered.containsAny("the highlights", "that info", "the summary", "those", "that", "it", "the result")
    }

    // -------------------------------------------------------------------------------------
    // Summary generation
    // -------------------------------------------------------------------------------------

    /**
     * Build a user-facing summary of the pipeline execution.
     */
    private suspend fun buildPipelineSummary(
        results: List<StepResult>,
        allSucceeded: Boolean
    ): String {
        if (results.isEmpty()) return "No steps were executed."

        // If only one step, just return its output
        if (results.size == 1) {
            val result = results.first()
            return if (result.success) {
                result.output
            } else {
                result.error ?: "The step failed."
            }
        }

        // Try LLM-powered summary first
        if (engine.isLoaded && allSucceeded) {
            try {
                val stepsDescription = results.mapIndexed { index, result ->
                    "Step ${index + 1} (${result.step.agentType.name}): ${result.output}"
                }.joinToString("\n")

                return engine.generate(
                    prompt = "Summarize these completed actions in a single friendly response to the user:\n$stepsDescription",
                    systemPrompt = "You are Un-Dios. Combine the step results into one concise, natural response. No preamble, no markdown.",
                    maxTokens = 192,
                    temperature = 0.5f
                ).trim()
            } catch (e: Exception) {
                // Fall through to template summary
            }
        }

        // Template-based summary
        val successCount = results.count { it.success }
        val failCount = results.size - successCount

        val builder = StringBuilder()

        if (allSucceeded) {
            builder.append("Completed all ${results.size} steps:\n")
        } else {
            builder.append("Completed $successCount of ${results.size} steps")
            if (failCount > 0) builder.append(" ($failCount failed)")
            builder.append(":\n")
        }

        for ((index, result) in results.withIndex()) {
            val status = if (result.success) "Done" else "Failed"
            val detail = if (result.success) result.output else (result.error ?: "Unknown error")
            val truncatedDetail = if (detail.length > 100) detail.take(97) + "..." else detail
            builder.append("  ${index + 1}. [$status] ${result.step.action}: $truncatedDetail\n")
        }

        return builder.toString().trim()
    }

    // -------------------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------------------

    /**
     * Extension function: check if a string contains any of the given substrings.
     */
    private fun String.containsAny(vararg terms: String): Boolean {
        return terms.any { this.contains(it) }
    }
}
