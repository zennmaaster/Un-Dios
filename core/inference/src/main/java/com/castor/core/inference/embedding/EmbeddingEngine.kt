package com.castor.core.inference.embedding

/**
 * Interface for on-device text embedding generation.
 *
 * Embeddings convert text into dense floating-point vectors that capture
 * semantic meaning. These vectors power the RAG pipeline by enabling
 * similarity search over local documents, notes, and conversation history.
 *
 * All computation happens on-device — no data is sent to any server.
 *
 * Usage:
 * ```
 * val engine: EmbeddingEngine = ...
 * engine.loadModel("/path/to/embedding-model.gguf")
 *
 * val queryVector = engine.embed("What is the weather?")
 * val docVectors = engine.embedBatch(listOf("doc1...", "doc2...", "doc3..."))
 *
 * val similarities = docVectors.map { engine.similarity(queryVector, it) }
 * val mostRelevantIndex = similarities.indexOf(similarities.max())
 * ```
 */
interface EmbeddingEngine {

    /** Whether an embedding model is currently loaded and ready. */
    val isLoaded: Boolean

    /** Dimensionality of the output vectors (e.g. 384 for MiniLM-L6-v2). */
    val dimensions: Int

    /**
     * Load an embedding model from disk.
     *
     * @param modelPath Absolute path to the GGUF model file
     * @throws IllegalStateException if loading fails
     */
    suspend fun loadModel(modelPath: String)

    /**
     * Unload the current model and free associated resources.
     */
    suspend fun unloadModel()

    /**
     * Generate an embedding vector for a single text input.
     *
     * @param text The text to embed
     * @return A [FloatArray] of size [dimensions] representing the text's semantic vector
     * @throws IllegalStateException if no model is loaded
     */
    suspend fun embed(text: String): FloatArray

    /**
     * Generate embedding vectors for a batch of texts.
     *
     * More efficient than calling [embed] repeatedly, as the model
     * can process multiple inputs in a single forward pass.
     *
     * @param texts The list of texts to embed
     * @return A list of [FloatArray] vectors, one per input text
     * @throws IllegalStateException if no model is loaded
     */
    suspend fun embedBatch(texts: List<String>): List<FloatArray>

    /**
     * Compute cosine similarity between two embedding vectors.
     *
     * @param a First embedding vector
     * @param b Second embedding vector
     * @return Similarity score in the range [-1.0, 1.0] where 1.0 = identical meaning
     * @throws IllegalArgumentException if vectors have different dimensions
     */
    suspend fun similarity(a: FloatArray, b: FloatArray): Float
}
