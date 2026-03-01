package com.castor.core.inference.download

import android.content.Context
import android.util.Log
import com.castor.core.inference.ModelManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sealed class representing the current state of a model download.
 *
 * Observed by the download screen UI to display progress bars,
 * completion status, or error messages.
 */
sealed class DownloadState {

    /** No download in progress for this model. */
    data object Idle : DownloadState()

    /**
     * Model is currently being downloaded.
     *
     * @param progress Download progress as a fraction (0.0 to 1.0)
     * @param bytesDownloaded Number of bytes downloaded so far
     * @param totalBytes Total expected file size in bytes
     */
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadState()

    /** Download completed, integrity verified. Awaiting rename to final path. */
    data object Verifying : DownloadState()

    /**
     * Download completed successfully.
     *
     * @param file The final GGUF file on device
     */
    data class Complete(val file: File) : DownloadState()

    /**
     * Download failed with an error.
     *
     * @param message Human-readable error description
     */
    data class Error(val message: String) : DownloadState()
}

/**
 * Manages downloading GGUF model files from HuggingFace to the device.
 *
 * All downloads are standard HTTPS GET requests -- no authentication,
 * no telemetry, no data sent to any server. The only network traffic
 * is downloading the model weights file.
 *
 * Features:
 * - Per-model progress tracking via [StateFlow]
 * - Resume support for interrupted downloads (HTTP Range header)
 * - SHA-256 integrity verification after download
 * - Downloads to `.part` file then renames to final path on success
 * - Thread-safe state management with [ConcurrentHashMap]
 * - Cancellation support for in-progress downloads
 *
 * Uses OkHttp for HTTP, consistent with the rest of the codebase.
 * Writes to [ModelManager.modelsDir] (app-private `filesDir/models/`).
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager
) {

    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val BUFFER_SIZE = 8192
        private const val PART_SUFFIX = ".part"
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 300L
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Per-model download states, keyed by [ModelCatalogEntry.id].
     *
     * The inner [MutableStateFlow] allows fine-grained updates per model
     * without recomposing the entire state map. The outer [StateFlow] exposes
     * the full map snapshot for the UI layer.
     */
    private val _perModelStates = ConcurrentHashMap<String, MutableStateFlow<DownloadState>>()

    private val _downloadState = MutableStateFlow<Map<String, DownloadState>>(emptyMap())

    /** Observable map of download states, keyed by catalog entry ID. */
    val downloadState: StateFlow<Map<String, DownloadState>> = _downloadState.asStateFlow()

    /** Active download jobs, keyed by entry ID. Used for cancellation. */
    private val activeJobs = ConcurrentHashMap<String, Job>()

    init {
        // Initialize state for all catalog entries based on what's on disk
        ModelCatalog.entries.forEach { entry ->
            val initialState = checkExistingFile(entry)
            _perModelStates[entry.id] = MutableStateFlow(initialState)
        }
        syncMapState()
    }

    /**
     * Download a model from the catalog to [ModelManager.modelsDir].
     *
     * This is a long-running suspending operation. Progress updates are
     * emitted to [downloadState]. Supports resume: if a `.part` file
     * exists, the download continues from where it left off.
     *
     * @param entry The catalog entry describing the model to download
     */
    suspend fun downloadModel(entry: ModelCatalogEntry) = withContext(Dispatchers.IO) {
        val stateFlow = getOrCreateStateFlow(entry.id)
        val targetFile = File(modelManager.modelsDir, entry.filename)
        val partFile = File(modelManager.modelsDir, entry.filename + PART_SUFFIX)

        // Already downloaded -- nothing to do
        if (targetFile.exists() && targetFile.length() > 0) {
            stateFlow.value = DownloadState.Complete(targetFile)
            syncMapState()
            return@withContext
        }

        try {
            // Determine resume offset from partial file
            val resumeOffset = if (partFile.exists()) partFile.length() else 0L

            stateFlow.value = DownloadState.Downloading(
                progress = if (entry.fileSizeBytes > 0) {
                    resumeOffset.toFloat() / entry.fileSizeBytes
                } else 0f,
                bytesDownloaded = resumeOffset,
                totalBytes = entry.fileSizeBytes
            )
            syncMapState()

            Log.d(TAG, "Downloading ${entry.displayName} (resume from $resumeOffset bytes)")

            // Build request with optional Range header for resume
            val requestBuilder = Request.Builder().url(entry.downloadUrl)
            if (resumeOffset > 0) {
                requestBuilder.addHeader("Range", "bytes=$resumeOffset-")
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()

            if (!response.isSuccessful && response.code != 206) {
                stateFlow.value = DownloadState.Error(
                    "HTTP ${response.code}: ${response.message}"
                )
                syncMapState()
                response.close()
                return@withContext
            }

            val body = response.body ?: run {
                stateFlow.value = DownloadState.Error("Empty response body")
                syncMapState()
                response.close()
                return@withContext
            }

            // If we requested a Range but got 200 (full response), the server
            // doesn't support resume. Start from scratch to avoid corruption.
            val effectiveResumeOffset = if (resumeOffset > 0 && response.code == 200) {
                Log.d(TAG, "Server returned 200 instead of 206; restarting download from scratch")
                partFile.delete()
                0L
            } else {
                resumeOffset
            }

            // Determine total size from Content-Length or Content-Range
            val contentLength = body.contentLength()
            val totalBytes = if (effectiveResumeOffset > 0 && response.code == 206) {
                val rangeHeader = response.header("Content-Range")
                rangeHeader?.substringAfter("/")?.toLongOrNull()
                    ?: (effectiveResumeOffset + contentLength)
            } else {
                if (contentLength > 0) contentLength else entry.fileSizeBytes
            }

            // Write to .part file (append if resuming)
            val outputStream = FileOutputStream(partFile, effectiveResumeOffset > 0)
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesDownloaded = effectiveResumeOffset

            body.byteStream().use { input ->
                outputStream.use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        val progress = if (totalBytes > 0) {
                            bytesDownloaded.toFloat() / totalBytes
                        } else 0f

                        stateFlow.value = DownloadState.Downloading(
                            progress = progress.coerceIn(0f, 1f),
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = totalBytes
                        )
                        syncMapState()
                    }
                }
            }

            response.close()

            // Verify integrity if checksum is not a placeholder
            stateFlow.value = DownloadState.Verifying
            syncMapState()

            if (entry.sha256.isNotBlank() &&
                !entry.sha256.startsWith("placeholder")
            ) {
                Log.d(TAG, "Verifying SHA-256 for ${entry.displayName}...")
                val actualHash = computeSha256(partFile)
                if (!actualHash.equals(entry.sha256, ignoreCase = true)) {
                    partFile.delete()
                    stateFlow.value = DownloadState.Error(
                        "Checksum mismatch. Expected: ${entry.sha256}, " +
                            "got: $actualHash"
                    )
                    syncMapState()
                    return@withContext
                }
                Log.d(TAG, "Checksum verified for ${entry.displayName}")
            }

            // Rename .part file to final name
            if (partFile.renameTo(targetFile)) {
                Log.d(TAG, "Download complete: ${targetFile.absolutePath}")
                stateFlow.value = DownloadState.Complete(targetFile)
            } else {
                stateFlow.value = DownloadState.Error(
                    "Failed to rename download file to final path"
                )
            }
            syncMapState()

        } catch (e: CancellationException) {
            Log.d(TAG, "Download cancelled: ${entry.displayName}")
            stateFlow.value = DownloadState.Idle
            syncMapState()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${entry.displayName}", e)
            stateFlow.value = DownloadState.Error(
                e.message ?: "Unknown download error"
            )
            syncMapState()
        }
    }

    /**
     * Cancel an in-progress download.
     *
     * Removes the active job and resets the entry state to [DownloadState.Idle].
     * The `.part` file is left on disk so the download can be resumed later.
     *
     * @param entryId The catalog entry ID to cancel
     */
    fun cancelDownload(entryId: String) {
        activeJobs[entryId]?.cancel()
        activeJobs.remove(entryId)
        _perModelStates[entryId]?.value = DownloadState.Idle
        syncMapState()
    }

    /**
     * Register an active download job for cancellation tracking.
     *
     * Called by the ViewModel when launching a download coroutine.
     *
     * @param entryId The catalog entry ID
     * @param job The coroutine [Job] running the download
     */
    fun registerJob(entryId: String, job: Job) {
        activeJobs[entryId] = job
    }

    /**
     * Get catalog entries that are already downloaded on device.
     *
     * Checks [ModelManager.modelsDir] for files matching catalog entry filenames.
     *
     * @return List of catalog entries whose GGUF files exist on device
     */
    fun getDownloadedModels(): List<ModelCatalogEntry> {
        return ModelCatalog.entries.filter { entry ->
            val file = File(modelManager.modelsDir, entry.filename)
            file.exists() && file.length() > 0
        }
    }

    /**
     * Delete a downloaded model file from local storage.
     *
     * Removes both the final file and any partial download, then resets
     * the download state to [DownloadState.Idle].
     *
     * @param entryId The catalog entry ID for the model to delete
     * @return true if the file was deleted (or never existed)
     */
    fun deleteModel(entryId: String): Boolean {
        val entry = ModelCatalog.findById(entryId) ?: return false
        val targetFile = File(modelManager.modelsDir, entry.filename)
        val partFile = File(modelManager.modelsDir, entry.filename + PART_SUFFIX)

        partFile.delete()
        val deleted = !targetFile.exists() || targetFile.delete()

        if (deleted) {
            _perModelStates[entryId]?.value = DownloadState.Idle
            syncMapState()
        }

        return deleted
    }

    /**
     * Check if a specific catalog entry is already downloaded.
     *
     * @param entryId The catalog entry ID
     * @return true if the model file exists and is non-empty
     */
    fun isDownloaded(entryId: String): Boolean {
        val entry = ModelCatalog.findById(entryId) ?: return false
        val file = File(modelManager.modelsDir, entry.filename)
        return file.exists() && file.length() > 0
    }

    /**
     * Get the local [File] for a downloaded catalog entry.
     *
     * @param entryId The catalog entry ID
     * @return The file if downloaded, null otherwise
     */
    fun getModelFile(entryId: String): File? {
        val entry = ModelCatalog.findById(entryId) ?: return null
        val file = File(modelManager.modelsDir, entry.filename)
        return if (file.exists() && file.length() > 0) file else null
    }

    // ---- Compatibility methods for ModelManagerViewModel ----

    /** Get the per-model state flow for UI observation. */
    fun getDownloadState(entryId: String): StateFlow<DownloadState> =
        getOrCreateStateFlow(entryId).asStateFlow()

    /** Check if a catalog entry is downloaded (by entry object). */
    fun isModelDownloaded(entry: ModelCatalogEntry): Boolean = isDownloaded(entry.id)

    /** Delete a model by catalog entry object. */
    fun deleteModel(entry: ModelCatalogEntry) { deleteModel(entry.id) }

    /** Delete a model by its file reference. */
    fun deleteModelFile(file: File) {
        file.delete()
        // Reset state if this matches a catalog entry
        ModelCatalog.entries.forEach { entry ->
            if (File(modelManager.modelsDir, entry.filename) == file) {
                _perModelStates[entry.id]?.value = DownloadState.Idle
                syncMapState()
            }
        }
    }

    /** Models directory reference for storage info. */
    val modelsDir: File get() = modelManager.modelsDir

    // ---- Internal helpers ----

    /**
     * Get or create a per-model state flow.
     */
    private fun getOrCreateStateFlow(entryId: String): MutableStateFlow<DownloadState> {
        return _perModelStates.getOrPut(entryId) {
            MutableStateFlow(DownloadState.Idle)
        }
    }

    /**
     * Check the filesystem to determine initial download state for an entry.
     */
    private fun checkExistingFile(entry: ModelCatalogEntry): DownloadState {
        val targetFile = File(modelManager.modelsDir, entry.filename)
        val partFile = File(modelManager.modelsDir, entry.filename + PART_SUFFIX)

        return when {
            targetFile.exists() && targetFile.length() > 0 ->
                DownloadState.Complete(targetFile)
            partFile.exists() && partFile.length() > 0 ->
                DownloadState.Idle // Partial file exists; user can resume
            else ->
                DownloadState.Idle
        }
    }

    /**
     * Synchronize the per-model state map into the single [_downloadState] flow.
     *
     * Called after every state mutation so that collectors of [downloadState]
     * receive the latest snapshot.
     */
    private fun syncMapState() {
        _downloadState.update {
            _perModelStates.mapValues { (_, flow) -> flow.value }
        }
    }

    /**
     * Compute SHA-256 hash of a file for integrity verification.
     */
    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)

        file.inputStream().use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
