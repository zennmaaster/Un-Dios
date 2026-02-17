package com.castor.core.inference.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.core.inference.LocalModelInfo
import com.castor.core.inference.ModelManager
import com.castor.core.inference.download.DownloadState
import com.castor.core.inference.download.ModelCatalog
import com.castor.core.inference.download.ModelCatalogEntry
import com.castor.core.inference.download.ModelDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the model manager screen.
 *
 * @param localModels Models currently on device with metadata
 * @param catalogEntries Models available for download
 * @param downloadStates Per-model download progress states
 * @param currentModelName Name of the currently loaded model, or null
 * @param modelState Current model loading state
 * @param isRefreshing Whether the model list is being refreshed
 */
data class ModelManagerUiState(
    val localModels: List<LocalModelInfo> = emptyList(),
    val catalogEntries: List<ModelCatalogEntry> = ModelCatalog.entries,
    val downloadStates: Map<String, DownloadState> = emptyMap(),
    val currentModelName: String? = null,
    val modelState: ModelManager.ModelState = ModelManager.ModelState.NotLoaded,
    val isRefreshing: Boolean = false
)

/**
 * ViewModel for the model management screen.
 *
 * Coordinates between [ModelManager] (local model discovery and loading)
 * and [ModelDownloadManager] (downloading from HuggingFace).
 *
 * All operations are local-first. Model downloads are the only network
 * activity, and they are simple HTTPS GET requests to HuggingFace.
 */
@HiltViewModel
class ModelManagerViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val downloadManager: ModelDownloadManager
) : ViewModel() {

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<ModelManagerUiState> = combine(
        modelManager.modelState,
        _downloadStates,
        _isRefreshing
    ) { modelState, downloadStates, isRefreshing ->
        val currentModelName = when (modelState) {
            is ModelManager.ModelState.Loaded -> modelState.modelName
            is ModelManager.ModelState.Loading -> modelState.modelName
            else -> null
        }

        ModelManagerUiState(
            localModels = modelManager.getAvailableModelInfo(),
            catalogEntries = ModelCatalog.entries,
            downloadStates = downloadStates,
            currentModelName = currentModelName,
            modelState = modelState,
            isRefreshing = isRefreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ModelManagerUiState()
    )

    init {
        refreshModels()
        // Initialize download states from disk
        viewModelScope.launch {
            val states = ModelCatalog.entries.associate { entry ->
                entry.id to downloadManager.getDownloadState(entry.id).value
            }
            _downloadStates.value = states
        }
    }

    /**
     * Refresh the list of local models.
     */
    fun refreshModels() {
        _isRefreshing.value = true
        viewModelScope.launch {
            // Force re-read of model directory
            val states = ModelCatalog.entries.associate { entry ->
                entry.id to downloadManager.getDownloadState(entry.id).value
            }
            _downloadStates.value = states
            _isRefreshing.value = false
        }
    }

    /**
     * Download a model from the catalog.
     *
     * Launches a coroutine that downloads the model file and updates
     * the download state flow. The UI observes the state for progress.
     */
    fun downloadModel(entry: ModelCatalogEntry) {
        viewModelScope.launch {
            // Collect download state updates
            val stateFlow = downloadManager.getDownloadState(entry.id)
            launch {
                stateFlow.collect { state ->
                    _downloadStates.update { map ->
                        map + (entry.id to state)
                    }
                }
            }

            downloadManager.downloadModel(entry)

            // After download completes, refresh model list
            refreshModels()
        }
    }

    /**
     * Delete a downloaded model from the device.
     */
    fun deleteModel(entry: ModelCatalogEntry) {
        downloadManager.deleteModel(entry)
        _downloadStates.update { map ->
            map + (entry.id to DownloadState.Idle)
        }
        refreshModels()
    }

    /**
     * Delete a local model by its file info.
     */
    fun deleteLocalModel(modelInfo: LocalModelInfo) {
        downloadManager.deleteModelFile(modelInfo.file)
        refreshModels()
    }

    /**
     * Load a specific model as the active inference model.
     */
    fun loadModel(modelInfo: LocalModelInfo) {
        viewModelScope.launch {
            modelManager.loadModel(modelInfo.file)
        }
    }

    /**
     * Unload the current model.
     */
    fun unloadModel() {
        viewModelScope.launch {
            modelManager.unloadModel()
        }
    }

    /**
     * Check if a catalog model is already downloaded.
     */
    fun isModelDownloaded(entry: ModelCatalogEntry): Boolean {
        return downloadManager.isModelDownloaded(entry)
    }
}
