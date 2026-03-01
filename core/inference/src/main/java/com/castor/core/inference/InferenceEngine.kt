package com.castor.core.inference

import kotlinx.coroutines.flow.Flow

interface InferenceEngine {
    val isLoaded: Boolean
    val modelName: String

    suspend fun loadModel(modelPath: String)
    suspend fun unloadModel()

    suspend fun generate(
        prompt: String,
        systemPrompt: String = "",
        maxTokens: Int = 512,
        temperature: Float = 0.7f
    ): String

    fun generateStream(
        prompt: String,
        systemPrompt: String = "",
        maxTokens: Int = 512,
        temperature: Float = 0.7f
    ): Flow<String>

    /**
     * Generate a response from a pre-formatted prompt string.
     *
     * Unlike [generate], this method does NOT apply prompt formatting internally.
     * The caller is responsible for building the full ChatML/prompt template string,
     * including all special tokens. Used by the AgentLoop which constructs multi-turn
     * prompts with tool calls externally.
     *
     * @param formattedPrompt The fully formatted prompt string (with special tokens)
     * @param maxTokens Maximum tokens to generate
     * @param temperature Sampling temperature
     * @return The generated text
     */
    suspend fun generateRaw(
        formattedPrompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f
    ): String

    suspend fun tokenize(text: String): List<Int>
    suspend fun getTokenCount(text: String): Int
}
