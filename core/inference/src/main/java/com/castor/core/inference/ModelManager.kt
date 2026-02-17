package com.castor.core.inference

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: InferenceEngine
) {
    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotLoaded)
    val modelState: StateFlow<ModelState> = _modelState

    val modelsDir: File get() = File(context.filesDir, "models").apply { mkdirs() }

    fun getAvailableModels(): List<File> =
        modelsDir.listFiles { file -> file.extension == "gguf" }?.toList() ?: emptyList()

    suspend fun loadModel(modelFile: File) {
        _modelState.value = ModelState.Loading(modelFile.name)
        try {
            engine.loadModel(modelFile.absolutePath)
            _modelState.value = ModelState.Loaded(modelFile.name)
        } catch (e: Exception) {
            _modelState.value = ModelState.Error(e.message ?: "Failed to load model")
        }
    }

    suspend fun loadDefaultModel() {
        val models = getAvailableModels()
        if (models.isNotEmpty()) {
            loadModel(models.first())
        } else {
            _modelState.value = ModelState.Error("No model files found in ${modelsDir.absolutePath}")
        }
    }

    suspend fun unloadModel() {
        engine.unloadModel()
        _modelState.value = ModelState.NotLoaded
    }

    sealed interface ModelState {
        data object NotLoaded : ModelState
        data class Loading(val modelName: String) : ModelState
        data class Loaded(val modelName: String) : ModelState
        data class Error(val message: String) : ModelState
    }
}
