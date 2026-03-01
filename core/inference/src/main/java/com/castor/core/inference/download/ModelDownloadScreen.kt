package com.castor.core.inference.download

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.castor.core.inference.ModelManager
import com.castor.core.ui.theme.TerminalColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// =============================================================================
// ViewModel
// =============================================================================

/**
 * UI state for the model download screen.
 *
 * @param catalog All catalog entries available for download
 * @param downloadStates Per-entry download progress/state
 * @param downloadedIds Set of entry IDs that are fully downloaded on device
 * @param loadedModelName Filename of the currently loaded model, or null
 */
data class ModelDownloadUiState(
    val catalog: List<ModelCatalogEntry> = ModelCatalog.entries,
    val downloadStates: Map<String, DownloadState> = emptyMap(),
    val downloadedIds: Set<String> = emptySet(),
    val loadedModelName: String? = null
)

/**
 * ViewModel for the model download screen.
 *
 * Coordinates between [ModelDownloadManager] (network downloads) and
 * [ModelManager] (local model discovery and loading).
 *
 * All operations are local-first. Downloads are the only network
 * activity (HTTPS GET to HuggingFace).
 */
@HiltViewModel
class ModelDownloadViewModel @Inject constructor(
    private val downloadManager: ModelDownloadManager,
    private val modelManager: ModelManager
) : ViewModel() {

    private val _downloadedIds = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<ModelDownloadUiState> = combine(
        downloadManager.downloadState,
        _downloadedIds,
        modelManager.modelState
    ) { downloadStates, downloadedIds, modelState ->
        val loadedName = when (modelState) {
            is ModelManager.ModelState.Loaded -> modelState.modelName
            is ModelManager.ModelState.Loading -> modelState.modelName
            else -> null
        }

        ModelDownloadUiState(
            catalog = ModelCatalog.entries,
            downloadStates = downloadStates,
            downloadedIds = downloadedIds,
            loadedModelName = loadedName
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ModelDownloadUiState()
    )

    init {
        refreshDownloaded()
    }

    /** Refresh which models are downloaded on device. */
    fun refreshDownloaded() {
        _downloadedIds.value = downloadManager.getDownloadedModels()
            .map { it.id }
            .toSet()
    }

    /** Start downloading a catalog entry. */
    fun downloadModel(entry: ModelCatalogEntry) {
        val job = viewModelScope.launch {
            downloadManager.downloadModel(entry)
            refreshDownloaded()
        }
        downloadManager.registerJob(entry.id, job)
    }

    /** Cancel an in-progress download. */
    fun cancelDownload(entryId: String) {
        downloadManager.cancelDownload(entryId)
    }

    /** Delete a downloaded model from disk. */
    fun deleteModel(entryId: String) {
        downloadManager.deleteModel(entryId)
        refreshDownloaded()
    }

    /** Load a downloaded model as the active inference model. */
    fun loadModel(entryId: String) {
        val file = downloadManager.getModelFile(entryId) ?: return
        viewModelScope.launch {
            modelManager.loadModel(file)
        }
    }

    /** Check if a specific entry's file is the currently loaded model. */
    fun isLoaded(entry: ModelCatalogEntry): Boolean {
        val loadedName = (modelManager.modelState.value as? ModelManager.ModelState.Loaded)
            ?.modelName
        return loadedName == entry.filename
    }
}

// =============================================================================
// Screen Composable
// =============================================================================

/**
 * Model download screen with Ubuntu terminal aesthetic.
 *
 * Shows the curated catalog of GGUF models with:
 * - Model name, parameter count, quantization, file size, description
 * - Terminal-style progress bar during download (`[========>     ] 67%`)
 * - Checkmark and load button for downloaded models
 * - Delete button for downloaded models
 *
 * All text uses [FontFamily.Monospace] and colors from [TerminalColors]
 * (Catppuccin Mocha by default).
 */
@Composable
fun ModelDownloadScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ModelDownloadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        color = TerminalColors.Command
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
            .statusBarsPadding()
    ) {
        // ---- Top bar ----
        DownloadTopBar(onBack = onBack, mono = mono)

        // ---- Scrollable content ----
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ---- Terminal window header ----
            item {
                Spacer(modifier = Modifier.height(8.dp))
                TerminalWindowHeader(mono = mono)
            }

            // ---- Command prompt ----
            item {
                Text(
                    text = "$ apt search gguf --on-device",
                    style = mono.copy(
                        color = TerminalColors.Prompt,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "  Sorting... Done",
                    style = mono.copy(
                        color = TerminalColors.Timestamp,
                        fontSize = 11.sp
                    )
                )
                Text(
                    text = "  ${uiState.catalog.size} packages available",
                    style = mono.copy(
                        color = TerminalColors.Timestamp,
                        fontSize = 11.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ---- Model cards ----
            items(
                items = uiState.catalog,
                key = { it.id }
            ) { entry ->
                val downloadState = uiState.downloadStates[entry.id] ?: DownloadState.Idle
                val isDownloaded = uiState.downloadedIds.contains(entry.id)
                val isLoaded = uiState.loadedModelName == entry.filename

                ModelCard(
                    entry = entry,
                    downloadState = downloadState,
                    isDownloaded = isDownloaded,
                    isLoaded = isLoaded,
                    isRecommended = entry.recommended,
                    onDownload = { viewModel.downloadModel(entry) },
                    onCancel = { viewModel.cancelDownload(entry.id) },
                    onDelete = { viewModel.deleteModel(entry.id) },
                    onLoad = { viewModel.loadModel(entry.id) },
                    mono = mono
                )
            }

            // ---- Privacy footer ----
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "  All inference runs on-device. No data leaves your phone.",
                    style = mono.copy(
                        color = TerminalColors.PrivacyLocal,
                        fontSize = 11.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "  Downloads are plain HTTPS GET to huggingface.co.",
                    style = mono.copy(
                        color = TerminalColors.Timestamp,
                        fontSize = 10.sp
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// =============================================================================
// Top Bar
// =============================================================================

@Composable
private fun DownloadTopBar(onBack: () -> Unit, mono: TextStyle) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalColors.StatusBar)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TerminalColors.Command
            )
        }

        Icon(
            imageVector = Icons.Default.CloudDownload,
            contentDescription = null,
            tint = TerminalColors.Accent,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "/var/un-dios/downloads",
            style = mono.copy(
                color = TerminalColors.Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

// =============================================================================
// Terminal Window Header
// =============================================================================

@Composable
private fun TerminalWindowHeader(mono: TextStyle) {
    Column {
        // Window control dots
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(TerminalColors.Error.copy(alpha = 0.8f))
            )
            Spacer(modifier = Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(TerminalColors.Warning.copy(alpha = 0.8f))
            )
            Spacer(modifier = Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(TerminalColors.Success.copy(alpha = 0.8f))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "un-dios ~ model downloads",
                style = mono.copy(
                    color = TerminalColors.Timestamp,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Download Models",
            style = mono.copy(
                color = TerminalColors.Accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = "Curated Qwen2.5 GGUF models for on-device inference",
            style = mono.copy(
                color = TerminalColors.Timestamp,
                fontSize = 12.sp
            )
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

// =============================================================================
// Model Card
// =============================================================================

@Composable
private fun ModelCard(
    entry: ModelCatalogEntry,
    downloadState: DownloadState,
    isDownloaded: Boolean,
    isLoaded: Boolean,
    isRecommended: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onLoad: () -> Unit,
    mono: TextStyle
) {
    val borderColor = when {
        isLoaded -> TerminalColors.Success
        isRecommended -> TerminalColors.Accent.copy(alpha = 0.4f)
        else -> TerminalColors.Surface
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .padding(12.dp)
            .animateContentSize()
    ) {
        // ---- Title row: name + action button ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Recommended star
                if (isRecommended) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Recommended",
                        tint = TerminalColors.Accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Column {
                    Text(
                        text = entry.displayName,
                        style = mono.copy(
                            color = TerminalColors.Command,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "${entry.parameterCount} params | ${entry.quantization} | ${formatFileSize(entry.fileSizeBytes)}",
                        style = mono.copy(
                            color = TerminalColors.Timestamp,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            // Action button
            ModelActionButton(
                downloadState = downloadState,
                isDownloaded = isDownloaded,
                isLoaded = isLoaded,
                onDownload = onDownload,
                onCancel = onCancel,
                onDelete = onDelete,
                onLoad = onLoad
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ---- Description ----
        Text(
            text = entry.description,
            style = mono.copy(
                color = TerminalColors.Timestamp,
                fontSize = 10.sp
            )
        )

        // ---- Recommended badge ----
        if (isRecommended) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .background(
                        TerminalColors.Accent.copy(alpha = 0.12f),
                        RoundedCornerShape(3.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "RECOMMENDED",
                    style = mono.copy(
                        color = TerminalColors.Accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // ---- Download progress ----
        if (downloadState is DownloadState.Downloading) {
            Spacer(modifier = Modifier.height(8.dp))
            TerminalProgressBar(
                progress = downloadState.progress,
                bytesDownloaded = downloadState.bytesDownloaded,
                totalBytes = downloadState.totalBytes,
                mono = mono
            )
        }

        // ---- Verifying state ----
        if (downloadState is DownloadState.Verifying) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "  Verifying SHA-256 checksum...",
                style = mono.copy(
                    color = TerminalColors.Warning,
                    fontSize = 10.sp
                )
            )
        }

        // ---- Completed state ----
        if (downloadState is DownloadState.Complete || isDownloaded) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Downloaded",
                    tint = TerminalColors.Success,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isLoaded) "[LOADED] Ready for inference" else "[INSTALLED] ${entry.filename}",
                    style = mono.copy(
                        color = if (isLoaded) TerminalColors.Success else TerminalColors.Info,
                        fontSize = 10.sp
                    )
                )
            }
        }

        // ---- Error state ----
        if (downloadState is DownloadState.Error) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "  E: ${downloadState.message}",
                style = mono.copy(
                    color = TerminalColors.Error,
                    fontSize = 10.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "  Tap download to retry.",
                style = mono.copy(
                    color = TerminalColors.Timestamp,
                    fontSize = 10.sp
                ),
                modifier = Modifier.clickable { onDownload() }
            )
        }

        // ---- Apt-style hint for idle entries ----
        if (!isDownloaded && downloadState is DownloadState.Idle) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "  $ apt install ${entry.id}",
                style = mono.copy(
                    color = TerminalColors.Subtext,
                    fontSize = 9.sp
                ),
                modifier = Modifier.clickable { onDownload() }
            )
        }
    }
}

// =============================================================================
// Action Button (Download / Cancel / Load / Delete)
// =============================================================================

@Composable
private fun ModelActionButton(
    downloadState: DownloadState,
    isDownloaded: Boolean,
    isLoaded: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onLoad: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            // Currently downloading -- show cancel button
            downloadState is DownloadState.Downloading || downloadState is DownloadState.Verifying -> {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel download",
                        tint = TerminalColors.Error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Downloaded and loaded -- show loaded indicator
            isDownloaded && isLoaded -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Loaded",
                    tint = TerminalColors.Success,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete model",
                        tint = TerminalColors.Subtext,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Downloaded but not loaded -- show load + delete buttons
            isDownloaded -> {
                IconButton(
                    onClick = onLoad,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Load model",
                        tint = TerminalColors.Success,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete model",
                        tint = TerminalColors.Error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Not downloaded -- show download button
            else -> {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Download model",
                        tint = TerminalColors.Info,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// =============================================================================
// Terminal-style Progress Bar
// =============================================================================

/**
 * Renders a text-based terminal progress bar:
 * ```
 *   [=================>      ] 67%
 *   (1.3 GB / 2.0 GB)
 * ```
 * Plus a thin Material [LinearProgressIndicator] underneath for polish.
 */
@Composable
private fun TerminalProgressBar(
    progress: Float,
    bytesDownloaded: Long,
    totalBytes: Long,
    mono: TextStyle
) {
    Column {
        val percentInt = (progress * 100).toInt()
        val downloadedStr = formatFileSize(bytesDownloaded)
        val totalStr = formatFileSize(totalBytes)

        // Build the text-based bar: [=====>     ] 67%
        val barWidth = 24
        val filledWidth = (progress * barWidth).toInt()
        val bar = buildString {
            append("  [")
            repeat(filledWidth) { append('\u2588') } // Full block character
            if (filledWidth < barWidth) {
                append('\u2591') // Light shade for the "cursor"
                repeat(barWidth - filledWidth - 1) { append('\u2591') }
            }
            append("] $percentInt%")
        }

        Text(
            text = bar,
            style = mono.copy(
                color = TerminalColors.Accent,
                fontSize = 11.sp
            )
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "  ($downloadedStr / $totalStr)",
            style = mono.copy(
                color = TerminalColors.Info,
                fontSize = 10.sp
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Thin material progress indicator as secondary visual
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = TerminalColors.Accent,
            trackColor = TerminalColors.Background,
        )
    }
}

// =============================================================================
// Utility
// =============================================================================

/**
 * Format a byte count into a human-readable string.
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
