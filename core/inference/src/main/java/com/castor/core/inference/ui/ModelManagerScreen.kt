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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
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
 * - Storage usage summary (`$ df -h /models`)
 * - Installed models with metadata, load/unload/delete actions
 * - Available models catalog with download progress tracking
 *
 * Organized into two tab sections styled as terminal commands:
 * - Installed: `$ apt list --installed`
 * - Available: `$ apt search llm`
 *
 * All models run on-device. Downloads are the only network activity
 * (HTTPS GET to HuggingFace). No telemetry, no data sent anywhere.
 */
@Composable
fun ModelManagerScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ModelManagerViewModel = hiltViewModel()
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
        // ---- Top bar with back navigation ----
        ModelManagerTopBar(onBack = onBack, mono = mono)

        // ---- Scrollable content ----
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ---- Terminal Header ----
            item {
                Spacer(modifier = Modifier.height(8.dp))
                TerminalHeader(mono = mono)
            }

            // ---- Storage Usage Summary ----
            item {
                StorageUsageSummary(
                    storageInfo = uiState.storageInfo,
                    mono = mono
                )
            }

            item {
                SectionDivider()
            }

            // ---- Tab Selector ----
            item {
                TabSelector(
                    selectedTab = uiState.selectedTab,
                    installedCount = uiState.localModels.size,
                    availableCount = uiState.catalogEntries.size,
                    onSelectTab = { viewModel.selectTab(it) },
                    mono = mono
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // ---- Tab Content ----
            when (uiState.selectedTab) {
                0 -> {
                    // Installed Models Tab
                    item {
                        Text(
                            text = "$ apt list --installed",
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
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(TerminalColors.Surface.copy(alpha = 0.4f))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "  0 packages installed.",
                                    style = mono.copy(
                                        color = TerminalColors.Timestamp,
                                        fontSize = 12.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "  Run \$ apt install <model> from the Available tab.",
                                    style = mono.copy(
                                        color = TerminalColors.Timestamp,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    } else {
                        items(
                            items = uiState.localModels,
                            key = { it.file.absolutePath }
                        ) { modelInfo ->
                            InstalledModelCard(
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
                }

                1 -> {
                    // Available Models Tab
                    item {
                        Text(
                            text = "$ apt search llm",
                            style = mono.copy(
                                color = TerminalColors.Prompt,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "  Sorting... Done",
                            style = mono.copy(
                                color = TerminalColors.Timestamp,
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text = "  Full Text Search... Done",
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
                        AvailableModelCard(
                            entry = entry,
                            downloadState = uiState.downloadStates[entry.id]
                                ?: DownloadState.Idle,
                            isDownloaded = viewModel.isModelDownloaded(entry),
                            onDownload = { viewModel.downloadModel(entry) },
                            onCancel = { viewModel.cancelDownload(entry) },
                            onDelete = { viewModel.deleteModel(entry) },
                            mono = mono
                        )
                    }
                }
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
}

// ============================================================================
// Top Bar with Back Navigation
// ============================================================================

@Composable
private fun ModelManagerTopBar(onBack: () -> Unit, mono: TextStyle) {
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
            imageVector = Icons.Default.Memory,
            contentDescription = null,
            tint = TerminalColors.Accent,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "/var/un-dios/models",
            style = mono.copy(
                color = TerminalColors.Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )
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
            text = "Download and manage on-device LLM models",
            style = mono.copy(
                color = TerminalColors.Timestamp,
                fontSize = 12.sp
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

// ============================================================================
// Storage Usage Summary
// ============================================================================

@Composable
private fun StorageUsageSummary(
    storageInfo: StorageInfo,
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
            text = "$ df -h /models",
            style = mono.copy(
                color = TerminalColors.Prompt,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(6.dp))

        val usedStr = ModelManager.formatFileSize(storageInfo.usedBytes)
        val availStr = ModelManager.formatFileSize(storageInfo.availableBytes)

        Row {
            Text(
                text = "  Used: ",
                style = mono.copy(color = TerminalColors.Timestamp, fontSize = 11.sp)
            )
            Text(
                text = usedStr,
                style = mono.copy(
                    color = if (storageInfo.usedBytes > 0) TerminalColors.Warning else TerminalColors.Success,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "  /  Available: ",
                style = mono.copy(color = TerminalColors.Timestamp, fontSize = 11.sp)
            )
            Text(
                text = availStr,
                style = mono.copy(
                    color = TerminalColors.Success,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        // Visual bar for storage usage
        if (storageInfo.availableBytes > 0 || storageInfo.usedBytes > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            val totalSpace = storageInfo.usedBytes + storageInfo.availableBytes
            val usageRatio = if (totalSpace > 0) {
                (storageInfo.usedBytes.toFloat() / totalSpace).coerceIn(0f, 1f)
            } else 0f

            LinearProgressIndicator(
                progress = { usageRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (usageRatio > 0.8f) TerminalColors.Error else TerminalColors.Accent,
                trackColor = TerminalColors.Background,
            )
        }
    }
}

// ============================================================================
// Tab Selector
// ============================================================================

@Composable
private fun TabSelector(
    selectedTab: Int,
    installedCount: Int,
    availableCount: Int,
    onSelectTab: (Int) -> Unit,
    mono: TextStyle
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TabChip(
            label = "Installed ($installedCount)",
            isSelected = selectedTab == 0,
            onClick = { onSelectTab(0) },
            mono = mono
        )
        TabChip(
            label = "Available ($availableCount)",
            isSelected = selectedTab == 1,
            onClick = { onSelectTab(1) },
            mono = mono
        )
    }
}

@Composable
private fun TabChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    mono: TextStyle
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isSelected) TerminalColors.Accent.copy(alpha = 0.2f)
                else TerminalColors.Surface.copy(alpha = 0.5f)
            )
            .then(
                if (isSelected) {
                    Modifier.border(
                        1.dp,
                        TerminalColors.Accent.copy(alpha = 0.5f),
                        RoundedCornerShape(4.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = mono.copy(
                color = if (isSelected) TerminalColors.Accent else TerminalColors.Timestamp,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        )
    }
}

// ============================================================================
// Installed Model Card
// ============================================================================

@Composable
private fun InstalledModelCard(
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
        // Title row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
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
                            modelInfo.parameterCount?.let { append(" | ${it} params") }
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

        // Disk usage detail
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "  disk: ${ModelManager.formatFileSize(modelInfo.fileSizeBytes)}  |  " +
                "format: ${modelInfo.promptFormat.name}",
            style = mono.copy(
                color = TerminalColors.Timestamp,
                fontSize = 10.sp
            )
        )

        if (isCurrentModel) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "  [ACTIVE] Ready for inference",
                style = mono.copy(
                    color = TerminalColors.Success,
                    fontSize = 10.sp
                )
            )
        }

        // Delete hint styled as apt remove
        if (!isCurrentModel) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "  $ apt remove ${modelInfo.file.nameWithoutExtension}",
                style = mono.copy(
                    color = TerminalColors.Subtext,
                    fontSize = 9.sp
                ),
                modifier = Modifier.clickable { onDelete() }
            )
        }
    }
}

// ============================================================================
// Available Model Card
// ============================================================================

@Composable
private fun AvailableModelCard(
    entry: ModelCatalogEntry,
    downloadState: DownloadState,
    isDownloaded: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
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
                    Modifier.border(
                        1.dp,
                        TerminalColors.Accent.copy(alpha = 0.4f),
                        RoundedCornerShape(6.dp)
                    )
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
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
                        text = "${entry.parameterCount} params | ${entry.quantization} | " +
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
                    // Cancel button while downloading
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
            TerminalDownloadProgress(
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
                    text = "  [INSTALLED] ${downloadState.filePath.substringAfterLast("/")}",
                    style = mono.copy(
                        color = TerminalColors.Success,
                        fontSize = 10.sp
                    )
                )
            }
            is DownloadState.Failed -> {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "  E: ${downloadState.error}",
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
            else -> { /* Idle or Downloading -- handled above */ }
        }

        // Apt-style install hint
        if (!isDownloaded && downloadState is DownloadState.Idle) {
            Spacer(modifier = Modifier.height(4.dp))
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

// ============================================================================
// Terminal-style Download Progress
// ============================================================================

@Composable
private fun TerminalDownloadProgress(
    progress: Float,
    bytesDownloaded: Long,
    totalBytes: Long,
    mono: TextStyle
) {
    Column {
        val percentStr = "${(progress * 100).toInt()}%"
        val downloadedStr = ModelManager.formatFileSize(bytesDownloaded)
        val totalStr = ModelManager.formatFileSize(totalBytes)

        // Estimate ETA (simple estimation based on progress)
        val etaStr = if (progress > 0.01f && progress < 1f) {
            // Rough ETA: assume linear progress
            val remainingFraction = 1f - progress
            val elapsedRatio = progress
            val etaSeconds = ((remainingFraction / elapsedRatio) * 60).toInt()
            when {
                etaSeconds >= 3600 -> {
                    val h = etaSeconds / 3600
                    val m = (etaSeconds % 3600) / 60
                    "ETA: ${h}h ${m}m"
                }
                etaSeconds >= 60 -> {
                    val m = etaSeconds / 60
                    val s = etaSeconds % 60
                    "ETA: ${m}m ${s}s"
                }
                else -> "ETA: ${etaSeconds}s"
            }
        } else if (progress >= 1f) {
            "Verifying..."
        } else {
            "ETA: calculating..."
        }

        // Terminal-style progress bar: [=====>     ] 45% (1.2GB / 2.5GB) ETA: 3m 20s
        val barWidth = 20
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

        Text(
            text = "  ($downloadedStr / $totalStr) $etaStr",
            style = mono.copy(
                color = TerminalColors.Info,
                fontSize = 10.sp
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

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
