package com.castor.core.inference.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.castor.core.inference.LocalModelInfo
import com.castor.core.inference.ModelManager
import com.castor.core.inference.download.DownloadState
import com.castor.core.inference.download.ModelCatalogEntry
import com.castor.core.ui.theme.TerminalColors

/**
 * Model Manager screen with terminal aesthetic.
 *
 * Displays:
 * - Currently loaded model status
 * - List of locally available models with metadata
 * - Download catalog with progress tracking
 *
 * All models run on-device. Downloads are the only network activity
 * (HTTPS GET to HuggingFace). No telemetry, no data sent anywhere.
 */
@Composable
fun ModelManagerScreen(
    modifier: Modifier = Modifier,
    viewModel: ModelManagerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        color = TerminalColors.Command
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ---- Header ----
        item {
            TerminalHeader(mono = mono)
        }

        // ---- Current Model Status ----
        item {
            CurrentModelStatus(
                modelState = uiState.modelState,
                currentModelName = uiState.currentModelName,
                mono = mono
            )
        }

        item {
            SectionDivider()
        }

        // ---- Local Models ----
        item {
            Text(
                text = "$ ls /var/un-dios/models/",
                style = mono.copy(
                    color = TerminalColors.Prompt,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (uiState.localModels.isEmpty()) {
            item {
                Text(
                    text = "  (no models found — download one below)",
                    style = mono.copy(
                        color = TerminalColors.Timestamp,
                        fontSize = 12.sp
                    )
                )
            }
        } else {
            items(
                items = uiState.localModels,
                key = { it.file.absolutePath }
            ) { modelInfo ->
                LocalModelCard(
                    modelInfo = modelInfo,
                    isCurrentModel = uiState.currentModelName == modelInfo.file.name,
                    isLoading = uiState.modelState is ModelManager.ModelState.Loading,
                    onLoad = { viewModel.loadModel(modelInfo) },
                    onDelete = { viewModel.deleteLocalModel(modelInfo) },
                    onUnload = { viewModel.unloadModel() },
                    mono = mono
                )
            }
        }

        item {
            SectionDivider()
        }

        // ---- Download Catalog ----
        item {
            Text(
                text = "$ cat /etc/un-dios/model-catalog",
                style = mono.copy(
                    color = TerminalColors.Prompt,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "  Available models (downloaded via HTTPS from HuggingFace):",
                style = mono.copy(
                    color = TerminalColors.Timestamp,
                    fontSize = 11.sp
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(
            items = uiState.catalogEntries,
            key = { it.id }
        ) { entry ->
            CatalogModelCard(
                entry = entry,
                downloadState = uiState.downloadStates[entry.id] ?: DownloadState.Idle,
                isDownloaded = viewModel.isModelDownloaded(entry),
                onDownload = { viewModel.downloadModel(entry) },
                onDelete = { viewModel.deleteModel(entry) },
                mono = mono
            )
        }

        // ---- Footer ----
        item {
            SectionDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "  All inference runs on-device. No data leaves your phone.",
                style = mono.copy(
                    color = TerminalColors.PrivacyLocal,
                    fontSize = 11.sp
                )
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ============================================================================
// Terminal Header
// ============================================================================

@Composable
private fun TerminalHeader(mono: TextStyle) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Window dots
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
                text = "un-dios ~ model manager",
                style = mono.copy(
                    color = TerminalColors.Timestamp,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Model Manager",
            style = mono.copy(
                color = TerminalColors.Accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = "Manage on-device LLM models for local inference",
            style = mono.copy(
                color = TerminalColors.Timestamp,
                fontSize = 12.sp
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

// ============================================================================
// Current Model Status
// ============================================================================

@Composable
private fun CurrentModelStatus(
    modelState: ModelManager.ModelState,
    currentModelName: String?,
    mono: TextStyle
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface)
            .padding(12.dp)
    ) {
        Text(
            text = "$ systemctl status un-dios-inference",
            style = mono.copy(
                color = TerminalColors.Prompt,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(6.dp))

        when (modelState) {
            is ModelManager.ModelState.NotLoaded -> {
                StatusLine(label = "Status", value = "inactive (no model)", color = TerminalColors.Warning, mono = mono)
                StatusLine(label = "Action", value = "Download and load a model below", color = TerminalColors.Timestamp, mono = mono)
            }
            is ModelManager.ModelState.Loading -> {
                StatusLine(label = "Status", value = "loading...", color = TerminalColors.Info, mono = mono)
                StatusLine(label = "Model", value = modelState.modelName, color = TerminalColors.Output, mono = mono)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = TerminalColors.Accent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Loading model into memory...",
                        style = mono.copy(color = TerminalColors.Accent, fontSize = 11.sp)
                    )
                }
            }
            is ModelManager.ModelState.Loaded -> {
                StatusLine(label = "Status", value = "active (running)", color = TerminalColors.Success, mono = mono)
                StatusLine(label = "Model", value = currentModelName ?: "unknown", color = TerminalColors.Output, mono = mono)
            }
            is ModelManager.ModelState.Error -> {
                StatusLine(label = "Status", value = "failed", color = TerminalColors.Error, mono = mono)
                StatusLine(label = "Error", value = modelState.message, color = TerminalColors.Error, mono = mono)
            }
        }
    }
}

@Composable
private fun StatusLine(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    mono: TextStyle
) {
    Row {
        Text(
            text = "  $label: ",
            style = mono.copy(color = TerminalColors.Timestamp, fontSize = 11.sp)
        )
        Text(
            text = value,
            style = mono.copy(color = color, fontSize = 11.sp)
        )
    }
}

// ============================================================================
// Local Model Card
// ============================================================================

@Composable
private fun LocalModelCard(
    modelInfo: LocalModelInfo,
    isCurrentModel: Boolean,
    isLoading: Boolean,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
    onUnload: () -> Unit,
    mono: TextStyle
) {
    val borderColor = if (isCurrentModel) TerminalColors.Success else TerminalColors.Surface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.6f))
            .padding(10.dp)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = if (isCurrentModel) TerminalColors.Success else TerminalColors.Info,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = modelInfo.name,
                        style = mono.copy(
                            color = TerminalColors.Command,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = buildString {
                            append(modelInfo.family.displayName)
                            append(" | ")
                            append(ModelManager.formatFileSize(modelInfo.fileSizeBytes))
                            modelInfo.quantization?.let { append(" | $it") }
                        },
                        style = mono.copy(
                            color = TerminalColors.Timestamp,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Row {
                if (isCurrentModel) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active model",
                        tint = TerminalColors.Success,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onUnload,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Unload model",
                            tint = TerminalColors.Warning,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onLoad,
                        enabled = !isLoading,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Load model",
                            tint = if (isLoading) TerminalColors.Subtext else TerminalColors.Success,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onDelete,
                    enabled = !isCurrentModel,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete model",
                        tint = if (isCurrentModel) TerminalColors.Subtext else TerminalColors.Error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (isCurrentModel) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "  [ACTIVE] Format: ${modelInfo.promptFormat.name}",
                style = mono.copy(
                    color = TerminalColors.Success,
                    fontSize = 10.sp
                )
            )
        }
    }
}

// ============================================================================
// Catalog Model Card
// ============================================================================

@Composable
private fun CatalogModelCard(
    entry: ModelCatalogEntry,
    downloadState: DownloadState,
    isDownloaded: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    mono: TextStyle
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.4f))
            .then(
                if (entry.recommended) {
                    Modifier.border(1.dp, TerminalColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                } else {
                    Modifier
                }
            )
            .padding(10.dp)
            .animateContentSize()
    ) {
        // Title row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.recommended) {
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "${entry.parameterCount} | ${entry.quantization} | " +
                            ModelManager.formatFileSize(entry.fileSizeBytes) +
                            " | ctx: ${entry.contextLength}",
                        style = mono.copy(
                            color = TerminalColors.Timestamp,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            // Action button
            when {
                isDownloaded && downloadState !is DownloadState.Downloading -> {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete downloaded model",
                            tint = TerminalColors.Error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                downloadState is DownloadState.Downloading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = TerminalColors.Accent
                    )
                }
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

        // Description
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = entry.description,
            style = mono.copy(
                color = TerminalColors.Timestamp,
                fontSize = 10.sp
            )
        )

        // Recommended badge
        if (entry.recommended) {
            Spacer(modifier = Modifier.height(4.dp))
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

        // Download progress bar
        if (downloadState is DownloadState.Downloading) {
            Spacer(modifier = Modifier.height(8.dp))
            TerminalProgressBar(
                progress = downloadState.progress,
                bytesDownloaded = downloadState.bytesDownloaded,
                totalBytes = downloadState.totalBytes,
                mono = mono
            )
        }

        // Download state messages
        when (downloadState) {
            is DownloadState.Completed -> {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "  [DOWNLOADED] ${downloadState.filePath.substringAfterLast("/")}",
                    style = mono.copy(
                        color = TerminalColors.Success,
                        fontSize = 10.sp
                    )
                )
            }
            is DownloadState.Failed -> {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "  [FAILED] ${downloadState.error}",
                    style = mono.copy(
                        color = TerminalColors.Error,
                        fontSize = 10.sp
                    )
                )
                if (downloadState.retryable) {
                    Text(
                        text = "  Tap download to retry.",
                        style = mono.copy(
                            color = TerminalColors.Timestamp,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.clickable { onDownload() }
                    )
                }
            }
            is DownloadState.Paused -> {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "  [PAUSED] ${ModelManager.formatFileSize(downloadState.bytesDownloaded)} downloaded. Tap to resume.",
                    style = mono.copy(
                        color = TerminalColors.Warning,
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.clickable { onDownload() }
                )
            }
            else -> { /* Idle or Downloading — handled above */ }
        }
    }
}

// ============================================================================
// Terminal-style Progress Bar
// ============================================================================

@Composable
private fun TerminalProgressBar(
    progress: Float,
    bytesDownloaded: Long,
    totalBytes: Long,
    mono: TextStyle
) {
    Column {
        // Text-based progress
        val percentStr = "${(progress * 100).toInt()}%"
        val downloadedStr = ModelManager.formatFileSize(bytesDownloaded)
        val totalStr = ModelManager.formatFileSize(totalBytes)

        Text(
            text = "  downloading... $downloadedStr / $totalStr ($percentStr)",
            style = mono.copy(
                color = TerminalColors.Info,
                fontSize = 10.sp
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Terminal-style progress bar: [=========>          ] 45%
        val barWidth = 30
        val filledWidth = (progress * barWidth).toInt()
        val bar = buildString {
            append("  [")
            repeat(filledWidth) { append("=") }
            if (filledWidth < barWidth) {
                append(">")
                repeat(barWidth - filledWidth - 1) { append(" ") }
            }
            append("] $percentStr")
        }

        Text(
            text = bar,
            style = mono.copy(
                color = TerminalColors.Accent,
                fontSize = 10.sp
            )
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Material progress indicator as secondary visual
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = TerminalColors.Accent,
            trackColor = TerminalColors.Surface,
        )
    }
}

// ============================================================================
// Helpers
// ============================================================================

@Composable
private fun SectionDivider() {
    Spacer(modifier = Modifier.height(4.dp))
    HorizontalDivider(
        color = TerminalColors.Surface,
        thickness = 1.dp
    )
    Spacer(modifier = Modifier.height(4.dp))
}
