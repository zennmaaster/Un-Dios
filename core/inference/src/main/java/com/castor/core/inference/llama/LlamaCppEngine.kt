package com.castor.core.inference.llama

import android.content.Context
import com.castor.core.inference.InferenceConfig
import com.castor.core.inference.InferenceEngine
import com.castor.core.inference.prompt.ModelFamily
import com.castor.core.inference.prompt.PromptFormat
import com.castor.core.inference.prompt.PromptFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LLM inference engine backed by llama.cpp via JNI.
 *
 * Loads GGUF models and runs inference entirely on-device.
 * Supports multiple prompt formats via [PromptFormatter], with ChatML (Qwen2.5)
 * as the default.
 *
 * Falls back to mock responses if the native library is unavailable.
 */
@Singleton
class LlamaCppEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : InferenceEngine {

    @Volatile private var nativeHandle: Long = 0L
    @Volatile private var config: InferenceConfig? = null
    @Volatile private var _isLoaded = false
    @Volatile private var nativeAvailable = false

    /** Mutex to serialize native JNI calls (only one load/unload/generate at a time). */
    private val nativeMutex = Mutex()

    override val isLoaded: Boolean get() = _isLoaded
    override val modelName: String get() = config?.modelPath?.substringAfterLast("/") ?: "none"

    val promptFormat: PromptFormat get() = config?.promptFormat ?: PromptFormat.CHATML
    val modelFamily: ModelFamily get() = config?.modelFamily ?: ModelFamily.QWEN25

    init {
        try {
            System.loadLibrary("undios-llama")
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            nativeInit(nativeLibDir)
            nativeAvailable = true
        } catch (e: UnsatisfiedLinkError) {
            nativeAvailable = false
        }
    }

    override suspend fun loadModel(modelPath: String) = withContext(Dispatchers.IO) {
        nativeMutex.withLock {
            if (_isLoaded) unloadModelInternal()

            val detectedFormat = PromptFormatter.detectFromFilename(modelPath)
            val detectedFamily = PromptFormatter.detectFamilyFromFilename(modelPath)

            config = InferenceConfig(
                modelPath = modelPath,
                promptFormat = detectedFormat,
                modelFamily = detectedFamily,
                contextSize = detectedFamily.defaultContextLength.coerceAtMost(4096),
                flashAttention = detectedFamily == ModelFamily.QWEN25
            )

            if (nativeAvailable) {
                val cfg = config!!
                nativeHandle = nativeLoadModel(
                    cfg.modelPath, cfg.contextSize, cfg.threads,
                    cfg.gpuLayers, cfg.useMmap, cfg.flashAttention
                )
                _isLoaded = nativeHandle != 0L
                if (!_isLoaded) {
                    throw RuntimeException("Failed to load model: $modelPath")
                }
            } else {
                _isLoaded = true // Mock mode
            }
        }
    }

    suspend fun loadModelWithConfig(inferenceConfig: InferenceConfig) = withContext(Dispatchers.IO) {
        nativeMutex.withLock {
            if (_isLoaded) unloadModelInternal()
            config = inferenceConfig

            if (nativeAvailable) {
                nativeHandle = nativeLoadModel(
                    inferenceConfig.modelPath, inferenceConfig.contextSize,
                    inferenceConfig.threads, inferenceConfig.gpuLayers,
                    inferenceConfig.useMmap, inferenceConfig.flashAttention
                )
                _isLoaded = nativeHandle != 0L
            } else {
                _isLoaded = true
            }
        }
    }

    override suspend fun unloadModel() = withContext(Dispatchers.IO) {
        nativeMutex.withLock {
            unloadModelInternal()
        }
    }

    /** Internal unload without acquiring the mutex (caller must hold it). */
    private fun unloadModelInternal() {
        if (nativeHandle != 0L && nativeAvailable) {
            nativeFreeModel(nativeHandle)
        }
        nativeHandle = 0L
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

        if (nativeAvailable && nativeHandle != 0L) {
            nativeMutex.withLock {
                val cfg = config!!
                nativeGenerate(
                    nativeHandle, fullPrompt, maxTokens, temperature,
                    cfg.topP, cfg.topK, cfg.repeatPenalty
                )
            }
        } else {
            val modelInfo = config?.let { "${it.modelFamily.displayName} (${it.promptFormat.name})" } ?: "unknown"
            "[Un-Dios AI | $modelInfo] I received your message: \"$prompt\". " +
                "Native library not available — running in mock mode."
        }
    }

    override fun generateStream(
        prompt: String,
        systemPrompt: String,
        maxTokens: Int,
        temperature: Float
    ): Flow<String> = callbackFlow {
        check(_isLoaded) { "Model not loaded. Call loadModel() first." }

        val fullPrompt = buildPrompt(systemPrompt, prompt)

        if (nativeAvailable && nativeHandle != 0L) {
            nativeMutex.withLock {
                val cfg = config!!
                val callback = object : LlamaStreamCallback {
                    override fun onToken(token: String) {
                        trySend(token)
                    }
                }
                nativeGenerateStream(
                    nativeHandle, fullPrompt, maxTokens, temperature,
                    cfg.topP, cfg.topK, cfg.repeatPenalty, callback
                )
            }
        } else {
            val response = "[Un-Dios | mock] Processing locally..."
            for (word in response.split(" ")) {
                send("$word ")
                kotlinx.coroutines.delay(50)
            }
        }
        close()
        awaitClose()
    }.flowOn(Dispatchers.IO)

    override suspend fun generateRaw(
        formattedPrompt: String,
        maxTokens: Int,
        temperature: Float
    ): String = withContext(Dispatchers.IO) {
        check(_isLoaded) { "Model not loaded. Call loadModel() first." }

        if (nativeAvailable && nativeHandle != 0L) {
            nativeMutex.withLock {
                val cfg = config!!
                nativeGenerate(
                    nativeHandle, formattedPrompt, maxTokens, temperature,
                    cfg.topP, cfg.topK, cfg.repeatPenalty
                )
            }
        } else {
            val modelInfo = config?.let { "${it.modelFamily.displayName} (${it.promptFormat.name})" } ?: "unknown"
            "[Un-Dios AI | $modelInfo] Raw prompt received (${formattedPrompt.length} chars). " +
                "Native library not available — running in mock mode."
        }
    }

    override suspend fun tokenize(text: String): List<Int> = withContext(Dispatchers.IO) {
        if (nativeAvailable && nativeHandle != 0L) {
            nativeTokenize(nativeHandle, text).toList()
        } else {
            text.split(" ").mapIndexed { i, _ -> i }
        }
    }

    override suspend fun getTokenCount(text: String): Int = tokenize(text).size

    private fun buildPrompt(systemPrompt: String, userPrompt: String): String {
        val format = config?.promptFormat ?: PromptFormat.CHATML
        return PromptFormatter.formatPrompt(format, systemPrompt, userPrompt)
    }

    // JNI native methods
    private external fun nativeInit(nativeLibDir: String)
    private external fun nativeLoadModel(
        path: String, contextSize: Int, threads: Int,
        gpuLayers: Int, useMmap: Boolean, flashAttention: Boolean
    ): Long
    private external fun nativeFreeModel(handle: Long)
    private external fun nativeGenerate(
        handle: Long, prompt: String, maxTokens: Int, temperature: Float,
        topP: Float, topK: Int, repeatPenalty: Float
    ): String
    private external fun nativeGenerateStream(
        handle: Long, prompt: String, maxTokens: Int, temperature: Float,
        topP: Float, topK: Int, repeatPenalty: Float, callback: LlamaStreamCallback
    )
    private external fun nativeTokenize(handle: Long, text: String): IntArray
    private external fun nativeShutdown()

    /**
     * Shut down the llama.cpp backend. Call once at application exit.
     * After this call, no further native operations are valid.
     */
    fun shutdown() {
        if (nativeAvailable) {
            nativeShutdown()
        }
    }
}
