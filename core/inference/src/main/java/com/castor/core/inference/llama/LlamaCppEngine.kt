package com.castor.core.inference.llama

import com.castor.core.inference.InferenceConfig
import com.castor.core.inference.InferenceEngine
import com.castor.core.inference.prompt.ModelFamily
import com.castor.core.inference.prompt.PromptFormat
import com.castor.core.inference.prompt.PromptFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LLM inference engine backed by llama.cpp via JNI.
 *
 * The native library (libllama.so) must be compiled from the llama.cpp source
 * using the Android NDK and included in the app's jniLibs directory.
 *
 * Supports multiple prompt formats via [PromptFormatter], with ChatML (Qwen2.5)
 * as the default. The prompt format is determined from the [InferenceConfig]
 * or auto-detected from the model filename.
 *
 * For initial development, this class provides a mock implementation.
 * Replace the method bodies with actual JNI calls once the native lib is built.
 */
@Singleton
class LlamaCppEngine @Inject constructor() : InferenceEngine {

    private var nativeHandle: Long = 0L
    private var config: InferenceConfig? = null
    private var _isLoaded = false

    override val isLoaded: Boolean get() = _isLoaded
    override val modelName: String get() = config?.modelPath?.substringAfterLast("/") ?: "none"

    /**
     * The currently active prompt format. Defaults to ChatML for Qwen2.5 models.
     * Updated when a model is loaded based on the config or filename detection.
     */
    val promptFormat: PromptFormat get() = config?.promptFormat ?: PromptFormat.CHATML

    /**
     * The currently active model family.
     */
    val modelFamily: ModelFamily get() = config?.modelFamily ?: ModelFamily.QWEN25

    override suspend fun loadModel(modelPath: String) = withContext(Dispatchers.IO) {
        if (_isLoaded) unloadModel()

        // Auto-detect prompt format and model family from filename
        val detectedFormat = PromptFormatter.detectFromFilename(modelPath)
        val detectedFamily = PromptFormatter.detectFamilyFromFilename(modelPath)

        config = InferenceConfig(
            modelPath = modelPath,
            promptFormat = detectedFormat,
            modelFamily = detectedFamily,
            contextSize = detectedFamily.defaultContextLength.coerceAtMost(4096),
            flashAttention = detectedFamily == ModelFamily.QWEN25
        )

        // TODO: Replace with JNI call:
        // nativeHandle = nativeLoadModel(
        //     modelPath,
        //     config!!.contextSize,
        //     config!!.threads,
        //     config!!.gpuLayers,
        //     config!!.useMmap,
        //     config!!.flashAttention
        // )
        _isLoaded = true
    }

    /**
     * Load model with an explicit configuration.
     * Allows callers to override auto-detected settings.
     */
    suspend fun loadModelWithConfig(inferenceConfig: InferenceConfig) = withContext(Dispatchers.IO) {
        if (_isLoaded) unloadModel()
        config = inferenceConfig

        // TODO: Replace with JNI call:
        // nativeHandle = nativeLoadModel(
        //     inferenceConfig.modelPath,
        //     inferenceConfig.contextSize,
        //     inferenceConfig.threads,
        //     inferenceConfig.gpuLayers,
        //     inferenceConfig.useMmap,
        //     inferenceConfig.flashAttention
        // )
        _isLoaded = true
    }

    override suspend fun unloadModel() = withContext(Dispatchers.IO) {
        if (nativeHandle != 0L) {
            // TODO: nativeFreeModel(nativeHandle)
            nativeHandle = 0L
        }
        _isLoaded = false
    }

    override suspend fun generate(
        prompt: String,
        systemPrompt: String,
        maxTokens: Int,
        temperature: Float
    ): String = withContext(Dispatchers.IO) {
        check(_isLoaded) { "Model not loaded. Call loadModel() first." }

        val fullPrompt = buildPrompt(systemPrompt, prompt)
        // TODO: Replace with JNI call:
        // return@withContext nativeGenerate(
        //     nativeHandle, fullPrompt, maxTokens, temperature,
        //     config!!.topP, config!!.topK, config!!.repeatPenalty
        // )

        // Mock response for development
        val modelInfo = config?.let { "${it.modelFamily.displayName} (${it.promptFormat.name})" } ?: "unknown"
        "[Castor AI | $modelInfo] I received your message: \"$prompt\". " +
            "The on-device LLM will be connected once llama.cpp native library is compiled via NDK."
    }

    override fun generateStream(
        prompt: String,
        systemPrompt: String,
        maxTokens: Int,
        temperature: Float
    ): Flow<String> = flow {
        check(_isLoaded) { "Model not loaded. Call loadModel() first." }

        val fullPrompt = buildPrompt(systemPrompt, prompt)
        // TODO: Replace with streaming JNI callback:
        // nativeGenerateStream(
        //     nativeHandle, fullPrompt, maxTokens, temperature,
        //     config!!.topP, config!!.topK, config!!.repeatPenalty
        // ) { token -> emit(token) }

        // Mock streaming for development
        val modelInfo = config?.let { "${it.modelFamily.displayName}" } ?: "local"
        val response = "[Castor | $modelInfo] Processing your request locally..."
        for (word in response.split(" ")) {
            emit("$word ")
            kotlinx.coroutines.delay(50)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun tokenize(text: String): List<Int> = withContext(Dispatchers.IO) {
        // TODO: nativeTokenize(nativeHandle, text)
        text.split(" ").mapIndexed { i, _ -> i }
    }

    override suspend fun getTokenCount(text: String): Int = tokenize(text).size

    /**
     * Build the full prompt string using the configured [PromptFormat].
     *
     * Delegates to [PromptFormatter] which handles the specific token
     * wrapping for each model family (ChatML for Qwen2.5, Phi-3 tags, etc.).
     */
    private fun buildPrompt(systemPrompt: String, userPrompt: String): String {
        val format = config?.promptFormat ?: PromptFormat.CHATML
        return PromptFormatter.formatPrompt(format, systemPrompt, userPrompt)
    }

    // JNI native method declarations — uncomment when native lib is ready
    // private external fun nativeLoadModel(
    //     path: String, contextSize: Int, threads: Int,
    //     gpuLayers: Int, useMmap: Boolean, flashAttention: Boolean
    // ): Long
    // private external fun nativeFreeModel(handle: Long)
    // private external fun nativeGenerate(
    //     handle: Long, prompt: String, maxTokens: Int, temperature: Float,
    //     topP: Float, topK: Int, repeatPenalty: Float
    // ): String
    // private external fun nativeGenerateStream(
    //     handle: Long, prompt: String, maxTokens: Int, temperature: Float,
    //     topP: Float, topK: Int, repeatPenalty: Float, callback: (String) -> Unit
    // )
    // private external fun nativeTokenize(handle: Long, text: String): IntArray

    companion object {
        init {
            try {
                System.loadLibrary("llama")
            } catch (e: UnsatisfiedLinkError) {
                // Native library not yet available — mock mode
            }
        }
    }
}
