package com.castor.core.inference.embedding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * On-device embedding engine backed by llama.cpp's embedding mode.
 *
 * Uses the same llama.cpp JNI bridge as [LlamaCppEngine] but with the
 * model loaded in embedding mode (embedding=true). This produces dense
 * float vectors suitable for semantic similarity search in the RAG pipeline.
 *
 * Supports:
 * - all-MiniLM-L6-v2 GGUF (384 dimensions, ~23MB) — lightweight and fast
 * - Qwen2.5 in embedding mode (1024 dimensions) — higher quality, larger
 *
 * For initial development, this class provides a mock implementation.
 * Replace the method bodies with actual JNI calls once the native lib is built.
 *
 * All computation is on-device — no data leaves the device.
 */
@Singleton
class LlamaEmbeddingEngine @Inject constructor() : EmbeddingEngine {

    private var nativeHandle: Long = 0L
    private var config: EmbeddingConfig? = null
    private var _isLoaded = false

    override val isLoaded: Boolean get() = _isLoaded
    override val dimensions: Int get() = config?.dimensions ?: 384

    override suspend fun loadModel(modelPath: String) = withContext(Dispatchers.IO) {
        if (_isLoaded) unloadModel()

        // Detect embedding dimensions from filename
        val detectedDimensions = detectDimensions(modelPath)
        config = EmbeddingConfig(
            modelPath = modelPath,
            dimensions = detectedDimensions
        )

        // TODO: Replace with JNI call:
        // nativeHandle = nativeLoadEmbeddingModel(modelPath, detectedDimensions)
        _isLoaded = true
    }

    /**
     * Load the embedding model with explicit configuration.
     */
    suspend fun loadModelWithConfig(embeddingConfig: EmbeddingConfig) = withContext(Dispatchers.IO) {
        if (_isLoaded) unloadModel()
        config = embeddingConfig

        // TODO: Replace with JNI call:
        // nativeHandle = nativeLoadEmbeddingModel(
        //     embeddingConfig.modelPath,
        //     embeddingConfig.dimensions
        // )
        _isLoaded = true
    }

    override suspend fun unloadModel() = withContext(Dispatchers.IO) {
        if (nativeHandle != 0L) {
            // TODO: nativeFreeEmbeddingModel(nativeHandle)
            nativeHandle = 0L
        }
        _isLoaded = false
        config = null
    }

    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.IO) {
        check(_isLoaded) { "Embedding model not loaded. Call loadModel() first." }

        // TODO: Replace with JNI call:
        // return@withContext nativeEmbed(nativeHandle, text, config!!.normalize)

        // Mock: return a deterministic pseudo-embedding based on text hash
        val dim = config?.dimensions ?: 384
        mockEmbedding(text, dim)
    }

    override suspend fun embedBatch(texts: List<String>): List<FloatArray> = withContext(Dispatchers.IO) {
        check(_isLoaded) { "Embedding model not loaded. Call loadModel() first." }

        // TODO: Replace with JNI batch call for efficiency:
        // return@withContext nativeEmbedBatch(nativeHandle, texts.toTypedArray(), config!!.normalize)

        // Mock: embed each text individually
        texts.map { text ->
            val dim = config?.dimensions ?: 384
            mockEmbedding(text, dim)
        }
    }

    override suspend fun similarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) {
            "Embedding dimensions must match: ${a.size} != ${b.size}"
        }

        return cosineSimilarity(a, b)
    }

    /**
     * Compute cosine similarity between two vectors.
     *
     * cosine_similarity = (A . B) / (||A|| * ||B||)
     *
     * If vectors are already L2-normalized (as they are when config.normalize=true),
     * this simplifies to just the dot product.
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0f) dotProduct / denominator else 0f
    }

    /**
     * Detect embedding dimensions from model filename.
     *
     * MiniLM variants typically produce 384-d vectors, while larger
     * models like Qwen2.5 produce 1024-d or 1536-d vectors.
     */
    private fun detectDimensions(modelPath: String): Int {
        val lower = modelPath.lowercase()
        return when {
            "minilm" in lower -> 384
            "e5-small" in lower -> 384
            "e5-base" in lower -> 768
            "e5-large" in lower -> 1024
            "qwen" in lower -> 1024
            "bge-small" in lower -> 384
            "bge-base" in lower -> 768
            "bge-large" in lower -> 1024
            else -> 384
        }
    }

    /**
     * Generate a deterministic mock embedding for development.
     * Uses a simple hash-based approach to produce consistent vectors.
     */
    private fun mockEmbedding(text: String, dimensions: Int): FloatArray {
        val hash = text.hashCode().toLong()
        val vector = FloatArray(dimensions) { i ->
            val seed = hash xor i.toLong()
            ((seed * 2654435761L) % 1000).toFloat() / 1000f
        }

        // L2 normalize
        val norm = sqrt(vector.fold(0f) { acc, v -> acc + v * v })
        if (norm > 0f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }

        return vector
    }

    // JNI native method declarations — uncomment when native lib is ready
    // private external fun nativeLoadEmbeddingModel(path: String, dimensions: Int): Long
    // private external fun nativeFreeEmbeddingModel(handle: Long)
    // private external fun nativeEmbed(handle: Long, text: String, normalize: Boolean): FloatArray
    // private external fun nativeEmbedBatch(handle: Long, texts: Array<String>, normalize: Boolean): Array<FloatArray>

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
