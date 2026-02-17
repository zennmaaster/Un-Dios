package com.castor.core.inference

import android.content.Context
import com.castor.core.inference.prompt.ModelFamily
import com.castor.core.inference.prompt.PromptFormat
import com.castor.core.inference.prompt.PromptFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Metadata about a locally available GGUF model file.
 *
 * Provides human-readable information for the model selection UI,
 * including the detected model family, prompt format, and file size.
 *
 * @param file The GGUF file on device
 * @param name Human-readable model name derived from the filename
 * @param family Detected model family (Qwen2.5, Phi-3, etc.)
 * @param promptFormat The prompt format to use with this model
 * @param fileSizeBytes File size in bytes
 * @param quantization Detected quantization level (e.g. "Q4_K_M")
 * @param parameterCount Detected parameter count (e.g. "3B") or null if unknown
 */
data class LocalModelInfo(
    val file: File,
    val name: String,
    val family: ModelFamily,
    val promptFormat: PromptFormat,
    val fileSizeBytes: Long,
    val quantization: String?,
    val parameterCount: String?
)

/**
 * Manages on-device GGUF model files: discovery, loading, and metadata.
 *
 * The model manager scans `filesDir/models/` for GGUF files and exposes
 * metadata about each model to the UI. When loading, it prefers Qwen2.5
 * models over other families if multiple models are available.
 *
 * All model operations are local — no network access, no data leaves the device.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: InferenceEngine
) {
    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotLoaded)
    val modelState: StateFlow<ModelState> = _modelState

    val modelsDir: File get() = File(context.filesDir, "models").apply { mkdirs() }

    /**
     * Get list of raw GGUF files in the models directory.
     */
    fun getAvailableModels(): List<File> =
        modelsDir.listFiles { file -> file.extension == "gguf" }?.toList() ?: emptyList()

    /**
     * Get detailed metadata for all available local models.
     *
     * Metadata is derived from the filename using heuristic parsing.
     * Models are sorted with Qwen2.5 models first, then by file size descending.
     */
    fun getAvailableModelInfo(): List<LocalModelInfo> {
        return getAvailableModels().map { file ->
            buildModelInfo(file)
        }.sortedWith(
            compareByDescending<LocalModelInfo> { it.family == ModelFamily.QWEN25 }
                .thenByDescending { it.fileSizeBytes }
        )
    }

    /**
     * Get metadata for the currently loaded model, or null if no model is loaded.
     */
    fun getCurrentModelInfo(): LocalModelInfo? {
        val state = _modelState.value
        if (state !is ModelState.Loaded) return null
        val file = File(modelsDir, state.modelName)
        return if (file.exists()) buildModelInfo(file) else null
    }

    /**
     * Load a specific model file.
     */
    suspend fun loadModel(modelFile: File) {
        _modelState.value = ModelState.Loading(modelFile.name)
        try {
            engine.loadModel(modelFile.absolutePath)
            _modelState.value = ModelState.Loaded(modelFile.name)
        } catch (e: Exception) {
            _modelState.value = ModelState.Error(e.message ?: "Failed to load model")
        }
    }

    /**
     * Load the best available model, preferring Qwen2.5 models.
     *
     * Selection priority:
     * 1. Qwen2.5 3B models (any quantization)
     * 2. Qwen2.5 1.5B models
     * 3. Any other Qwen2.5 model
     * 4. Any other available model
     *
     * If no models are found, sets the state to [ModelState.Error].
     */
    suspend fun loadDefaultModel() {
        val models = getAvailableModelInfo()
        if (models.isEmpty()) {
            _modelState.value = ModelState.Error(
                "No model files found in ${modelsDir.absolutePath}. " +
                    "Download a model from the Model Manager screen."
            )
            return
        }

        // Prefer Qwen2.5 3B, then Qwen2.5 1.5B, then any Qwen2.5, then any model
        val preferred = models.firstOrNull {
            it.family == ModelFamily.QWEN25 && it.parameterCount == "3B"
        } ?: models.firstOrNull {
            it.family == ModelFamily.QWEN25 && it.parameterCount == "1.5B"
        } ?: models.firstOrNull {
            it.family == ModelFamily.QWEN25
        } ?: models.first()

        loadModel(preferred.file)
    }

    /**
     * Unload the current model and free resources.
     */
    suspend fun unloadModel() {
        engine.unloadModel()
        _modelState.value = ModelState.NotLoaded
    }

    /**
     * Build model metadata from a GGUF filename using heuristic parsing.
     *
     * Parses common naming conventions like:
     * - `qwen2.5-3b-instruct-q4_k_m.gguf`
     * - `Phi-3-mini-4k-instruct-q4.gguf`
     * - `Meta-Llama-3-8B-Instruct-Q5_K_M.gguf`
     */
    private fun buildModelInfo(file: File): LocalModelInfo {
        val filename = file.nameWithoutExtension
        val lower = filename.lowercase()
        val family = PromptFormatter.detectFamilyFromFilename(filename)
        val promptFormat = PromptFormatter.detectFromFilename(filename)

        // Extract quantization from filename (e.g. "q4_k_m", "q5_0", "q8_0")
        val quantRegex = Regex("""[qQ](\d+)([_-][kK]_?[mMsSlL])?""")
        val quantMatch = quantRegex.find(lower)
        val quantization = quantMatch?.value?.uppercase()

        // Extract parameter count (e.g. "3b", "1.5b", "7b")
        val paramRegex = Regex("""(\d+\.?\d*)[bB]""")
        val paramMatch = paramRegex.find(lower)
        val parameterCount = paramMatch?.let {
            val num = it.groupValues[1]
            "${num}B"
        }

        // Build a clean display name
        val displayName = when (family) {
            ModelFamily.QWEN25 -> "Qwen2.5 ${parameterCount ?: ""} ${quantization ?: ""}".trim()
            ModelFamily.PHI3 -> "Phi-3 ${parameterCount ?: ""} ${quantization ?: ""}".trim()
            ModelFamily.LLAMA3 -> "Llama 3 ${parameterCount ?: ""} ${quantization ?: ""}".trim()
            ModelFamily.GEMMA -> "Gemma ${parameterCount ?: ""} ${quantization ?: ""}".trim()
            ModelFamily.GENERIC -> filename
        }

        return LocalModelInfo(
            file = file,
            name = displayName,
            family = family,
            promptFormat = promptFormat,
            fileSizeBytes = file.length(),
            quantization = quantization,
            parameterCount = parameterCount
        )
    }

    /**
     * Format a byte count into a human-readable string (e.g. "2.1 GB").
     */
    companion object {
        fun formatFileSize(bytes: Long): String {
            return when {
                bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
                bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
                bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
                else -> "$bytes B"
            }
        }
    }

    sealed interface ModelState {
        data object NotLoaded : ModelState
        data class Loading(val modelName: String) : ModelState
        data class Loaded(val modelName: String) : ModelState
        data class Error(val message: String) : ModelState
    }
}
