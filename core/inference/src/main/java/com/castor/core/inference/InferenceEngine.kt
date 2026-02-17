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

    suspend fun tokenize(text: String): List<Int>
    suspend fun getTokenCount(text: String): Int
}
