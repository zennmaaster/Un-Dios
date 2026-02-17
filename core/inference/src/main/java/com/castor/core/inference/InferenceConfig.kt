package com.castor.core.inference

import com.castor.core.inference.prompt.ModelFamily
import com.castor.core.inference.prompt.PromptFormat

/**
 * Configuration for on-device LLM inference via llama.cpp.
 *
 * Sensible defaults are tuned for Qwen2.5-3B-Instruct running on
 * mid-range Android devices (6-8 GB RAM, Snapdragon 7-series or equivalent).
 *
 * @param modelPath Absolute path to the GGUF model file on device
 * @param contextSize Maximum context window in tokens (Qwen2.5 supports up to 32768)
 * @param batchSize Number of tokens processed per batch during prompt evaluation
 * @param threads Number of CPU threads for inference (usually physical core count)
 * @param gpuLayers Number of model layers offloaded to GPU (0 = CPU-only)
 * @param useMmap Whether to use memory-mapped I/O for model loading
 * @param promptFormat The prompt template format for this model
 * @param modelFamily The model family for model-specific optimizations
 * @param repeatPenalty Penalty applied to repeated tokens (1.0 = no penalty)
 * @param topP Nucleus sampling threshold (smaller = more focused)
 * @param topK Top-K sampling limit (smaller = more focused)
 * @param flashAttention Whether to enable flash attention (supported by Qwen2.5)
 */
data class InferenceConfig(
    val modelPath: String,
    val contextSize: Int = 4096,
    val batchSize: Int = 512,
    val threads: Int = 4,
    val gpuLayers: Int = 0,
    val useMmap: Boolean = true,
    val promptFormat: PromptFormat = PromptFormat.CHATML,
    val modelFamily: ModelFamily = ModelFamily.QWEN25,
    val repeatPenalty: Float = 1.1f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val flashAttention: Boolean = true
)
