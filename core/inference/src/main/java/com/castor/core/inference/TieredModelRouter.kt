package com.castor.core.inference

import android.util.Log
import com.castor.core.inference.llama.LlamaCppEngine
import com.castor.core.inference.prompt.ModelFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which tier of model to use for a given request.
 *
 * - [FAST]: Small models (3B parameters or fewer). Low latency, good for
 *   simple tasks like media control, greetings, and short replies.
 * - [COMPLEX]: Larger models (7B+ parameters). Higher quality for
 *   multi-step reasoning, summarization, code generation, and analysis.
 */
enum class ModelTier {
    FAST,
    COMPLEX
}

/**
 * Coarse complexity classification for incoming user requests.
 *
 * Determined by keyword heuristics (not LLM inference) to avoid the
 * chicken-and-egg problem of needing a model loaded to decide which
 * model to load.
 *
 * - [SIMPLE]: Short commands — media control, reminders, greetings, quick answers.
 * - [MODERATE]: Mid-range tasks — summarization, message drafting, single-topic Q&A.
 * - [COMPLEX]: Heavy tasks — multi-step planning, code generation, comparative analysis,
 *   long-form reasoning.
 */
enum class TaskComplexity {
    SIMPLE,
    MODERATE,
    COMPLEX
}

/**
 * Routes inference requests to the appropriate model tier based on task complexity.
 *
 * The router maintains up to two model slots: a fast (small) model and a complex
 * (large) model. When both are available, simple tasks are routed to the fast model
 * for lower latency, while complex tasks use the larger model for higher quality.
 * If only one model is available on-device, it serves both tiers.
 *
 * Complexity classification uses keyword heuristics and input length — no LLM call
 * is needed to decide which model to invoke. This avoids circular dependencies and
 * keeps routing latency near zero.
 *
 * All model loading and inference happens on-device. No data leaves the phone.
 */
@Singleton
class TieredModelRouter @Inject constructor(
    private val modelManager: ModelManager,
    private val engine: LlamaCppEngine
) {

    companion object {
        private const val TAG = "TieredModelRouter"

        /**
         * Input length threshold: inputs longer than this are considered COMPLEX
         * unless overridden by specific keywords.
         */
        private const val LONG_INPUT_THRESHOLD = 200

        /**
         * Parameter count threshold in billions: models at or above this are
         * assigned to the COMPLEX tier; below goes to FAST.
         */
        private const val COMPLEX_MODEL_PARAM_THRESHOLD = 5.0

        // ---------------------------------------------------------------------------------
        // Keyword lists for heuristic complexity classification
        // ---------------------------------------------------------------------------------

        /** Keywords that strongly signal a COMPLEX task. */
        private val COMPLEX_KEYWORDS = listOf(
            "analyze", "analyse", "analysis",
            "compare", "comparison", "contrast",
            "explain in detail", "explain why", "explain how",
            "step by step", "step-by-step",
            "write code", "write a script", "code generation", "implement",
            "debug", "refactor",
            "plan", "planning", "strategy", "strategize",
            "pros and cons", "advantages and disadvantages",
            "essay", "long-form", "in depth", "in-depth",
            "reason about", "reasoning", "think through",
            "evaluate", "assessment", "critique",
            "multi-step", "multistep",
            "translate and explain",
            "research", "investigate"
        )

        /** Keywords that signal a MODERATE task. */
        private val MODERATE_KEYWORDS = listOf(
            "summarize", "summary", "summarise",
            "draft", "compose", "write a message", "write a reply",
            "rephrase", "rewrite", "paraphrase",
            "describe", "elaborate",
            "what do you think", "your opinion",
            "recommend", "recommendation", "suggest",
            "translate",
            "list", "outline",
            "tldr", "tl;dr",
            "recap", "catch me up",
            "how to", "how do i",
            "what is", "what are", "define"
        )

        /** Keywords that signal a SIMPLE task (checked first for fast-path). */
        private val SIMPLE_KEYWORDS = listOf(
            "play", "pause", "stop", "skip", "next", "previous",
            "volume", "mute", "unmute",
            "remind me", "set alarm", "set timer", "set a reminder",
            "hello", "hi", "hey", "good morning", "good night",
            "thanks", "thank you", "ok", "okay", "yes", "no",
            "what time", "what's the time", "what day",
            "open", "launch", "start",
            "send", "text", "reply", "message",
            "call", "dial"
        )
    }

    // -------------------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------------------

    /** The model currently assigned to the FAST tier, or null if none. */
    private var fastModel: LocalModelInfo? = null

    /** The model currently assigned to the COMPLEX tier, or null if none. */
    private var complexModel: LocalModelInfo? = null

    /** Mutex to serialize model loading operations (only one load at a time). */
    private val loadMutex = Mutex()

    private val _currentTier = MutableStateFlow<ModelTier?>(null)

    /**
     * The tier of the model that is currently loaded in the inference engine,
     * or null if no model is loaded.
     */
    val currentTier: StateFlow<ModelTier?> = _currentTier.asStateFlow()

    private val _availableTiers = MutableStateFlow<Map<ModelTier, LocalModelInfo?>>(emptyMap())

    /**
     * Map of each tier to the model assigned to it (or null if no suitable model
     * was found for that tier). Updated whenever [refreshAvailableModels] is called
     * or when [ensureModelForTier] discovers new models.
     */
    val availableTiers: StateFlow<Map<ModelTier, LocalModelInfo?>> = _availableTiers.asStateFlow()

    // -------------------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------------------

    /**
     * Re-scan the on-device models directory and assign models to tiers.
     *
     * Call this after downloading a new model or at app startup to ensure the
     * tier assignments reflect what is actually on disk.
     */
    fun refreshAvailableModels() {
        val allModels = modelManager.getAvailableModelInfo()
        assignModelsToTiers(allModels)
        Log.d(TAG, "Refreshed models: FAST=${fastModel?.name}, COMPLEX=${complexModel?.name}")
    }

    /**
     * Classify the complexity of a user input using keyword heuristics and length.
     *
     * This method is intentionally LLM-free so it can be called before any model
     * is loaded. The classification drives tier selection but is not exposed to
     * the user — it is an internal routing signal.
     *
     * Heuristic priority:
     * 1. Long inputs (>[LONG_INPUT_THRESHOLD] chars) -> [TaskComplexity.COMPLEX]
     * 2. Contains a COMPLEX keyword -> [TaskComplexity.COMPLEX]
     * 3. Contains a MODERATE keyword -> [TaskComplexity.MODERATE]
     * 4. Contains a SIMPLE keyword -> [TaskComplexity.SIMPLE]
     * 5. Default -> [TaskComplexity.SIMPLE]
     */
    fun classifyComplexity(input: String): TaskComplexity {
        val lowered = input.lowercase().trim()

        // Rule 1: Long inputs are likely complex
        if (lowered.length > LONG_INPUT_THRESHOLD) {
            // Even long inputs can be simple if they match simple keywords exactly at the start
            val startsSimple = SIMPLE_KEYWORDS.any { keyword ->
                lowered.startsWith(keyword)
            }
            if (!startsSimple) {
                Log.d(TAG, "Classified as COMPLEX (length=${lowered.length})")
                return TaskComplexity.COMPLEX
            }
        }

        // Rule 2: Check for COMPLEX keywords (word-boundary matching)
        if (COMPLEX_KEYWORDS.any { keyword -> containsWord(lowered, keyword) }) {
            Log.d(TAG, "Classified as COMPLEX (keyword match)")
            return TaskComplexity.COMPLEX
        }

        // Rule 3: Check for MODERATE keywords
        if (MODERATE_KEYWORDS.any { keyword -> containsWord(lowered, keyword) }) {
            Log.d(TAG, "Classified as MODERATE (keyword match)")
            return TaskComplexity.MODERATE
        }

        // Rule 4: Check for SIMPLE keywords (explicit confirmation)
        if (SIMPLE_KEYWORDS.any { keyword -> containsWord(lowered, keyword) }) {
            Log.d(TAG, "Classified as SIMPLE (keyword match)")
            return TaskComplexity.SIMPLE
        }

        // Rule 5: Default to SIMPLE for unrecognized short inputs
        Log.d(TAG, "Classified as SIMPLE (default)")
        return TaskComplexity.SIMPLE
    }

    /**
     * Map a [TaskComplexity] to the [ModelTier] that should handle it.
     *
     * - SIMPLE -> FAST (low latency is more important than quality)
     * - MODERATE -> FAST (prefer speed; 3B models handle these adequately)
     * - COMPLEX -> COMPLEX (quality matters; use 7B+ if available, else FAST)
     */
    fun selectTier(complexity: TaskComplexity): ModelTier {
        return when (complexity) {
            TaskComplexity.SIMPLE -> ModelTier.FAST
            TaskComplexity.MODERATE -> ModelTier.FAST
            TaskComplexity.COMPLEX -> {
                // Use the COMPLEX tier if a model is assigned to it; otherwise fall back to FAST
                if (complexModel != null) ModelTier.COMPLEX else ModelTier.FAST
            }
        }
    }

    /**
     * Ensure the inference engine has the correct model loaded for the requested [tier].
     *
     * If the engine already has the right model loaded, this is a no-op. Otherwise it
     * unloads the current model and loads the one assigned to the requested tier.
     *
     * If no model is assigned to the requested tier, falls back:
     * - COMPLEX requested but no complex model -> use fast model
     * - FAST requested but no fast model -> use complex model
     * - Neither assigned -> attempt [refreshAvailableModels] and retry once
     *
     * @throws IllegalStateException if no models are available at all.
     */
    suspend fun ensureModelForTier(tier: ModelTier) = loadMutex.withLock {
        // Refresh if we have no tier assignments yet
        if (fastModel == null && complexModel == null) {
            refreshAvailableModels()
        }

        val targetModel = resolveModelForTier(tier)
            ?: throw IllegalStateException(
                "No models available for tier $tier. " +
                    "Download a model from the Model Manager screen."
            )

        // Check if the engine already has this model loaded
        val currentModelName = if (engine.isLoaded) engine.modelName else null
        val targetFileName = targetModel.file.name

        if (currentModelName == targetFileName) {
            // Already loaded — just update the tier state
            _currentTier.value = tier
            Log.d(TAG, "Model $targetFileName already loaded for tier $tier")
            return@withLock
        }

        // Need to swap models
        Log.d(TAG, "Loading model $targetFileName for tier $tier")
        if (engine.isLoaded) {
            engine.unloadModel()
        }
        engine.loadModel(targetModel.file.absolutePath)
        _currentTier.value = tier
        Log.d(TAG, "Model $targetFileName loaded successfully for tier $tier")
    }

    /**
     * End-to-end convenience method: classify input, select tier, ensure the right
     * model is loaded, and generate a response.
     *
     * This is the primary entry point for code that wants tiered inference without
     * manually managing model loading. The [AgentOrchestrator] or UI layer can call
     * this directly.
     *
     * @param prompt The user's input prompt
     * @param systemPrompt Optional system-level instructions
     * @param maxTokens Maximum number of tokens to generate
     * @param temperature Sampling temperature (higher = more creative)
     * @return The generated response text
     */
    suspend fun routeAndGenerate(
        prompt: String,
        systemPrompt: String = "",
        maxTokens: Int = 512,
        temperature: Float = 0.7f
    ): String {
        val complexity = classifyComplexity(prompt)
        val tier = selectTier(complexity)

        Log.d(TAG, "Routing: complexity=$complexity, tier=$tier")

        ensureModelForTier(tier)

        return engine.generate(
            prompt = prompt,
            systemPrompt = systemPrompt,
            maxTokens = maxTokens,
            temperature = temperature
        )
    }

    /**
     * Like [routeAndGenerate] but returns the selected tier along with the response,
     * useful for UI badges or logging.
     */
    suspend fun routeAndGenerateWithMetadata(
        prompt: String,
        systemPrompt: String = "",
        maxTokens: Int = 512,
        temperature: Float = 0.7f
    ): Pair<ModelTier, String> {
        val complexity = classifyComplexity(prompt)
        val tier = selectTier(complexity)

        Log.d(TAG, "Routing (with metadata): complexity=$complexity, tier=$tier")

        ensureModelForTier(tier)

        val response = engine.generate(
            prompt = prompt,
            systemPrompt = systemPrompt,
            maxTokens = maxTokens,
            temperature = temperature
        )

        return tier to response
    }

    /**
     * Select the appropriate tier for a prompt and ensure the model is loaded,
     * but do NOT generate. Useful when the caller wants to use [InferenceEngine]
     * directly (e.g. for streaming).
     *
     * @return The selected [ModelTier] after the model is loaded.
     */
    suspend fun routeAndPrepare(prompt: String): ModelTier {
        val complexity = classifyComplexity(prompt)
        val tier = selectTier(complexity)
        ensureModelForTier(tier)
        return tier
    }

    // -------------------------------------------------------------------------------------
    // Internal: tier assignment logic
    // -------------------------------------------------------------------------------------

    /**
     * Assign available models to tiers based on their parameter count.
     *
     * Strategy:
     * - Models with parameter count < [COMPLEX_MODEL_PARAM_THRESHOLD]B -> FAST candidates
     * - Models with parameter count >= [COMPLEX_MODEL_PARAM_THRESHOLD]B -> COMPLEX candidates
     * - Within each tier, prefer Qwen2.5 family, then largest parameter count
     * - If only small models exist, the best one serves both tiers (COMPLEX = null)
     * - If only large models exist, the best one serves both tiers (FAST = large model)
     */
    private fun assignModelsToTiers(models: List<LocalModelInfo>) {
        if (models.isEmpty()) {
            fastModel = null
            complexModel = null
            _availableTiers.value = emptyMap()
            return
        }

        val (largeModels, smallModels) = models.partition { model ->
            parseParamCount(model.parameterCount) >= COMPLEX_MODEL_PARAM_THRESHOLD
        }

        // Assign FAST tier: prefer smallest Qwen2.5, then smallest overall
        fastModel = smallModels.sortedWith(
            compareByDescending<LocalModelInfo> { it.family == ModelFamily.QWEN25 }
                .thenBy { parseParamCount(it.parameterCount) }
        ).firstOrNull()

        // Assign COMPLEX tier: prefer largest Qwen2.5, then largest overall
        complexModel = largeModels.sortedWith(
            compareByDescending<LocalModelInfo> { it.family == ModelFamily.QWEN25 }
                .thenByDescending { parseParamCount(it.parameterCount) }
        ).firstOrNull()

        // If no small models, use the large model for FAST too
        if (fastModel == null && complexModel != null) {
            fastModel = complexModel
        }

        // If no large models but we have small ones, COMPLEX stays null
        // (selectTier will fall back to FAST)

        _availableTiers.value = mapOf(
            ModelTier.FAST to fastModel,
            ModelTier.COMPLEX to complexModel
        )
    }

    /**
     * Resolve which [LocalModelInfo] to use for a given tier, with fallback.
     */
    private fun resolveModelForTier(tier: ModelTier): LocalModelInfo? {
        return when (tier) {
            ModelTier.FAST -> fastModel ?: complexModel
            ModelTier.COMPLEX -> complexModel ?: fastModel
        }
    }

    /**
     * Check if a keyword appears in the input as a whole word or phrase,
     * not merely as a substring of a longer word. Multi-word keywords
     * (e.g. "step by step") use plain contains since they are already specific.
     */
    private fun containsWord(input: String, keyword: String): Boolean {
        if (keyword.contains(' ') || keyword.contains('-')) {
            // Multi-word phrases are specific enough
            return input.contains(keyword)
        }
        // Single-word: check word boundaries
        val idx = input.indexOf(keyword)
        if (idx < 0) return false
        val before = if (idx > 0) input[idx - 1] else ' '
        val after = if (idx + keyword.length < input.length) input[idx + keyword.length] else ' '
        return !before.isLetterOrDigit() && !after.isLetterOrDigit()
    }

    /**
     * Parse a parameter count string like "3B", "1.5B", "7B" into a numeric
     * value in billions. Returns 0.0 if the string is null or unparseable.
     */
    private fun parseParamCount(paramCount: String?): Double {
        if (paramCount == null) return 0.0
        val cleaned = paramCount.uppercase().removeSuffix("B").trim()
        return cleaned.toDoubleOrNull() ?: 0.0
    }
}
