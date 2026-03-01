package com.castor.core.inference

import android.util.Log
import com.castor.core.inference.llama.LlamaCppEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An [InferenceEngine] implementation that wraps [LlamaCppEngine] with
 * automatic tiered model routing via [TieredModelRouter].
 *
 * Instead of assuming a single model is always loaded, this engine:
 * 1. Classifies the complexity of each incoming request.
 * 2. Ensures the appropriate model tier (FAST or COMPLEX) is loaded.
 * 3. Delegates the actual inference to the underlying [LlamaCppEngine].
 *
 * This is transparent to callers — they use the standard [InferenceEngine]
 * interface and tiered routing happens automatically behind the scenes.
 *
 * **Important:** This engine wraps the concrete [LlamaCppEngine], not the
 * [InferenceEngine] interface, to avoid circular Hilt injection. The DI
 * module should bind [TieredInferenceEngine] as a separate named provider
 * or use it directly where tiered routing is desired.
 *
 * All inference happens on-device. No data leaves the phone.
 */
@Singleton
class TieredInferenceEngine @Inject constructor(
    private val llamaEngine: LlamaCppEngine,
    private val router: TieredModelRouter
) : InferenceEngine {

    companion object {
        private const val TAG = "TieredInferenceEngine"
    }

    // -------------------------------------------------------------------------------------
    // InferenceEngine — delegation properties
    // -------------------------------------------------------------------------------------

    /**
     * Whether the underlying llama.cpp engine has a model loaded.
     */
    override val isLoaded: Boolean
        get() = llamaEngine.isLoaded

    /**
     * The name of the currently loaded model in the underlying engine.
     */
    override val modelName: String
        get() = llamaEngine.modelName

    // -------------------------------------------------------------------------------------
    // InferenceEngine — model lifecycle (pass-through)
    // -------------------------------------------------------------------------------------

    /**
     * Load a model by path. This bypasses tiered routing and loads a specific
     * model directly. Prefer using [generate] or [generateStream] which handle
     * model selection automatically.
     */
    override suspend fun loadModel(modelPath: String) {
        Log.d(TAG, "Direct loadModel called: $modelPath")
        llamaEngine.loadModel(modelPath)
    }

    /**
     * Unload the currently loaded model and free native resources.
     */
    override suspend fun unloadModel() {
        Log.d(TAG, "Unloading model")
        llamaEngine.unloadModel()
    }

    // -------------------------------------------------------------------------------------
    // InferenceEngine — tiered generation
    // -------------------------------------------------------------------------------------

    /**
     * Generate a response with automatic tiered model routing.
     *
     * Flow:
     * 1. The [TieredModelRouter] classifies the prompt's complexity.
     * 2. The router selects the appropriate tier (FAST or COMPLEX).
     * 3. The router ensures the correct model is loaded (swapping if needed).
     * 4. The underlying [LlamaCppEngine] performs the actual generation.
     *
     * If the router cannot load any model (e.g. no models on device), this
     * falls back to calling the engine directly, which will throw an
     * [IllegalStateException] if no model is loaded.
     *
     * @param prompt The user's input prompt
     * @param systemPrompt Optional system-level instructions
     * @param maxTokens Maximum number of tokens to generate
     * @param temperature Sampling temperature (higher = more creative)
     * @return The generated response text
     */
    override suspend fun generate(
        prompt: String,
        systemPrompt: String,
        maxTokens: Int,
        temperature: Float
    ): String {
        return try {
            // Let the router handle complexity classification, tier selection,
            // model loading, and generation
            router.routeAndGenerate(
                prompt = prompt,
                systemPrompt = systemPrompt,
                maxTokens = maxTokens,
                temperature = temperature
            )
        } catch (e: IllegalStateException) {
            // Router could not find any models — fall through to engine
            // which will produce its own error or mock response
            Log.w(TAG, "Router failed to find models, falling back to direct engine: ${e.message}")
            llamaEngine.generate(
                prompt = prompt,
                systemPrompt = systemPrompt,
                maxTokens = maxTokens,
                temperature = temperature
            )
        }
    }

    /**
     * Stream a response token-by-token with automatic tiered model routing.
     *
     * The router prepares the correct model tier before streaming begins.
     * Once the model is loaded, tokens are streamed directly from the
     * underlying [LlamaCppEngine] for minimum latency.
     *
     * @param prompt The user's input prompt
     * @param systemPrompt Optional system-level instructions
     * @param maxTokens Maximum number of tokens to generate
     * @param temperature Sampling temperature (higher = more creative)
     * @return A [Flow] emitting individual tokens as they are generated
     */
    override fun generateStream(
        prompt: String,
        systemPrompt: String,
        maxTokens: Int,
        temperature: Float
    ): Flow<String> = flow {
        // Route and prepare the model before streaming starts.
        // This is a suspend call wrapped in a flow builder so that the caller
        // gets a cold Flow that does tier selection on collection.
        try {
            val tier = router.routeAndPrepare(prompt)
            Log.d(TAG, "Streaming with tier: $tier")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Router failed to prepare model for stream, using current: ${e.message}")
            // Proceed with whatever model is currently loaded (or let engine throw)
        }

        // Delegate streaming to the underlying engine
        llamaEngine.generateStream(
            prompt = prompt,
            systemPrompt = systemPrompt,
            maxTokens = maxTokens,
            temperature = temperature
        ).collect { token ->
            emit(token)
        }
    }

    // -------------------------------------------------------------------------------------
    // InferenceEngine — raw generation (pass-through, no tier routing)
    // -------------------------------------------------------------------------------------

    /**
     * Generate from a pre-formatted prompt. Bypasses tiered routing and prompt
     * formatting — the caller has already built the full ChatML string with
     * tool calls and multi-turn history. Used by the AgentLoop.
     */
    override suspend fun generateRaw(
        formattedPrompt: String,
        maxTokens: Int,
        temperature: Float
    ): String {
        return llamaEngine.generateRaw(formattedPrompt, maxTokens, temperature)
    }

    // -------------------------------------------------------------------------------------
    // InferenceEngine — tokenization (pass-through, no tier needed)
    // -------------------------------------------------------------------------------------

    /**
     * Tokenize text using the currently loaded model's tokenizer.
     *
     * Tokenization does not require tier selection — it uses whatever model
     * is currently loaded. If no model is loaded, the engine will throw or
     * return a mock result.
     */
    override suspend fun tokenize(text: String): List<Int> {
        return llamaEngine.tokenize(text)
    }

    /**
     * Get the token count for the given text using the current model's tokenizer.
     */
    override suspend fun getTokenCount(text: String): Int {
        return llamaEngine.getTokenCount(text)
    }
}
