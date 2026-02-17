package com.castor.core.inference

data class InferenceConfig(
    val modelPath: String,
    val contextSize: Int = 4096,
    val batchSize: Int = 512,
    val threads: Int = 4,
    val gpuLayers: Int = 0,
    val useMmap: Boolean = true
)
