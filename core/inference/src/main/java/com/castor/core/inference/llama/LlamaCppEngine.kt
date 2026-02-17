package com.castor.core.inference.llama

import com.castor.core.inference.InferenceConfig
import com.castor.core.inference.InferenceEngine
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

    override suspend fun loadModel(modelPath: String) = withContext(Dispatchers.IO) {
        if (_isLoaded) unloadModel()
        config = InferenceConfig(modelPath = modelPath)
        // TODO: Replace with JNI call:
        // nativeHandle = nativeLoadModel(modelPath, config.contextSize, config.threads, config.gpuLayers)
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
        // return@withContext nativeGenerate(nativeHandle, fullPrompt, maxTokens, temperature)

        // Mock response for development
        "[Castor AI] I received your message: \"$prompt\". The on-device LLM will be connected once llama.cpp native library is compiled via NDK."
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
        // nativeGenerateStream(nativeHandle, fullPrompt, maxTokens, temperature) { token -> emit(token) }

        // Mock streaming for development
        val response = "[Castor] Processing your request..."
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

    private fun buildPrompt(systemPrompt: String, userPrompt: String): String {
        return if (systemPrompt.isNotBlank()) {
            "<|system|>\n$systemPrompt<|end|>\n<|user|>\n$userPrompt<|end|>\n<|assistant|>\n"
        } else {
            "<|user|>\n$userPrompt<|end|>\n<|assistant|>\n"
        }
    }

    // JNI native method declarations — uncomment when native lib is ready
    // private external fun nativeLoadModel(path: String, contextSize: Int, threads: Int, gpuLayers: Int): Long
    // private external fun nativeFreeModel(handle: Long)
    // private external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int, temperature: Float): String
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
