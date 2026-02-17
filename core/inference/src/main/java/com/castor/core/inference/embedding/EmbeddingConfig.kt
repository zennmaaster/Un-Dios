package com.castor.core.inference.embedding

/**
 * Configuration for the on-device embedding model.
 *
 * Embeddings are used for RAG (Retrieval-Augmented Generation) to find
 * relevant context from local documents, notes, and conversation history
 * before passing them to the main LLM for generation.
 *
 * @param modelPath Absolute path to the GGUF embedding model file
 * @param dimensions Output embedding vector dimensionality (384 for MiniLM, 1024 for Qwen2.5)
 * @param batchSize Maximum number of texts to embed in a single batch
 * @param normalize Whether to L2-normalize output vectors (required for cosine similarity)
 */
data class EmbeddingConfig(
    val modelPath: String,
    val dimensions: Int = 384,
    val batchSize: Int = 32,
    val normalize: Boolean = true
)
