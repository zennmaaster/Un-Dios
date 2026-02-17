package com.castor.core.inference.download

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages downloading GGUF model files from HuggingFace to the device.
 *
 * All downloads are standard HTTPS GET requests — no authentication,
 * no telemetry, no data sent to any server. The only network traffic
 * is downloading the model weights file.
 *
 * Features:
 * - Progress tracking (bytes downloaded / total)
 * - Resume support for interrupted downloads (via HTTP Range header)
 * - SHA-256 integrity verification after download
 * - Saves to the app's private `filesDir/models/` directory
 * - Thread-safe download state management via [StateFlow]
 *
 * Uses OkHttp for the HTTP download, consistent with the rest of the codebase.
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val DOWNLOAD_BUFFER_SIZE = 8192
        private const val PARTIAL_SUFFIX = ".partial"
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

    val modelsDir: File get() = File(context.filesDir, "models").apply { mkdirs() }

    /**
     * Per-model download states, keyed by catalog entry ID.
     * The UI observes these flows to show progress bars and status.
     */
    private val _downloadStates = mutableMapOf<String, MutableStateFlow<DownloadState>>()

    /**
     * Get the download state flow for a specific catalog entry.
     * Creates a new [Idle] flow if one doesn't exist yet.
     */
    fun getDownloadState(catalogId: String): StateFlow<DownloadState> {
        return _downloadStates.getOrPut(catalogId) {
            MutableStateFlow(checkExistingDownload(catalogId))
        }
    }

    /**
     * Download a model from the catalog to local storage.
     *
     * This is a long-running suspending operation that should be called
     * from a coroutine scope (e.g. viewModelScope or a WorkManager worker).
     * Progress updates are emitted to the corresponding [StateFlow].
     *
     * Supports resume: if a partial download exists, it will continue
     * from where it left off using HTTP Range requests.
     *
     * @param entry The catalog entry describing the model to download
     * @throws CancellationException if the coroutine is cancelled
     */
    suspend fun downloadModel(entry: ModelCatalogEntry) = withContext(Dispatchers.IO) {
        val stateFlow = _downloadStates.getOrPut(entry.id) { MutableStateFlow(DownloadState.Idle) }
        val targetFile = File(modelsDir, entry.downloadUrl.substringAfterLast("/"))
        val partialFile = File(modelsDir, targetFile.name + PARTIAL_SUFFIX)

        // If the model is already fully downloaded, skip
        if (targetFile.exists() && targetFile.length() > 0) {
            stateFlow.value = DownloadState.Completed(targetFile.absolutePath)
            return@withContext
        }

        try {
            // Determine resume offset from partial file
            val resumeOffset = if (partialFile.exists()) partialFile.length() else 0L

            stateFlow.value = DownloadState.Downloading(
                progress = if (entry.fileSizeBytes > 0) resumeOffset.toFloat() / entry.fileSizeBytes else 0f,
                bytesDownloaded = resumeOffset,
                totalBytes = entry.fileSizeBytes
            )

            Log.d(TAG, "Starting download: ${entry.displayName} (resume from $resumeOffset bytes)")

            // Build request with optional Range header for resume
            val requestBuilder = Request.Builder().url(entry.downloadUrl)
            if (resumeOffset > 0) {
                requestBuilder.addHeader("Range", "bytes=$resumeOffset-")
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()

            if (!response.isSuccessful && response.code != 206) {
                stateFlow.value = DownloadState.Failed(
                    error = "Download failed: HTTP ${response.code}",
                    retryable = response.code in listOf(408, 429, 500, 502, 503, 504)
                )
                response.close()
                return@withContext
            }

            val body = response.body ?: run {
                stateFlow.value = DownloadState.Failed("Empty response body", retryable = true)
                response.close()
                return@withContext
            }

            // Determine total size from Content-Length or Content-Range
            val contentLength = body.contentLength()
            val totalBytes = if (resumeOffset > 0 && response.code == 206) {
                // Parse Content-Range: bytes 1234-5678/9999
                val rangeHeader = response.header("Content-Range")
                rangeHeader?.substringAfter("/")?.toLongOrNull() ?: (resumeOffset + contentLength)
            } else {
                if (contentLength > 0) contentLength else entry.fileSizeBytes
            }

            // Write to partial file (append if resuming)
            val outputStream = FileOutputStream(partialFile, resumeOffset > 0)
            val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
            var bytesDownloaded = resumeOffset

            body.byteStream().use { inputStream ->
                outputStream.use { output ->
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        val progress = if (totalBytes > 0) {
                            bytesDownloaded.toFloat() / totalBytes
                        } else {
                            0f
                        }

                        stateFlow.value = DownloadState.Downloading(
                            progress = progress.coerceIn(0f, 1f),
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = totalBytes
                        )
                    }
                }
            }

            response.close()

            // Verify integrity if checksum is provided
            if (entry.sha256.isNotBlank()) {
                Log.d(TAG, "Verifying SHA-256 checksum...")
                val actualHash = computeSha256(partialFile)
                if (!actualHash.equals(entry.sha256, ignoreCase = true)) {
                    partialFile.delete()
                    stateFlow.value = DownloadState.Failed(
                        error = "Checksum verification failed. Expected: ${entry.sha256}, got: $actualHash",
                        retryable = true
                    )
                    return@withContext
                }
                Log.d(TAG, "Checksum verified successfully")
            }

            // Rename partial file to final name
            if (partialFile.renameTo(targetFile)) {
                Log.d(TAG, "Download complete: ${targetFile.absolutePath}")
                stateFlow.value = DownloadState.Completed(targetFile.absolutePath)
            } else {
                stateFlow.value = DownloadState.Failed(
                    error = "Failed to finalize downloaded file",
                    retryable = false
                )
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Download cancelled: ${entry.displayName}")
            val downloaded = if (partialFile.exists()) partialFile.length() else 0L
            stateFlow.value = DownloadState.Paused(downloaded)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${entry.displayName}", e)
            stateFlow.value = DownloadState.Failed(
                error = e.message ?: "Unknown download error",
                retryable = true
            )
        }
    }

    /**
     * Delete a downloaded model from local storage.
     *
     * Also removes any partial download files and resets the download state.
     *
     * @param entry The catalog entry for the model to delete
     * @return true if the file was deleted (or didn't exist)
     */
    fun deleteModel(entry: ModelCatalogEntry): Boolean {
        val filename = entry.downloadUrl.substringAfterLast("/")
        val targetFile = File(modelsDir, filename)
        val partialFile = File(modelsDir, filename + PARTIAL_SUFFIX)

        partialFile.delete()
        val deleted = !targetFile.exists() || targetFile.delete()

        if (deleted) {
            _downloadStates[entry.id]?.value = DownloadState.Idle
        }

        return deleted
    }

    /**
     * Delete a model by its file reference.
     *
     * @param modelFile The model file to delete
     * @return true if the file was deleted
     */
    fun deleteModelFile(modelFile: File): Boolean {
        val deleted = modelFile.delete()
        // Reset any matching download state
        if (deleted) {
            val matchingEntry = ModelCatalog.entries.find {
                it.downloadUrl.substringAfterLast("/") == modelFile.name
            }
            matchingEntry?.let { _downloadStates[it.id]?.value = DownloadState.Idle }
        }
        return deleted
    }

    /**
     * Check if a model from the catalog is already downloaded.
     *
     * @param entry The catalog entry to check
     * @return true if the model file exists on device
     */
    fun isModelDownloaded(entry: ModelCatalogEntry): Boolean {
        val filename = entry.downloadUrl.substringAfterLast("/")
        val targetFile = File(modelsDir, filename)
        return targetFile.exists() && targetFile.length() > 0
    }

    /**
     * Get the local file path for a downloaded catalog model.
     *
     * @param entry The catalog entry
     * @return The file path, or null if not downloaded
     */
    fun getModelFilePath(entry: ModelCatalogEntry): String? {
        val filename = entry.downloadUrl.substringAfterLast("/")
        val targetFile = File(modelsDir, filename)
        return if (targetFile.exists()) targetFile.absolutePath else null
    }

    /**
     * Check existing download state for a catalog entry.
     * Called during initialization to detect already-downloaded models.
     */
    private fun checkExistingDownload(catalogId: String): DownloadState {
        val entry = ModelCatalog.findById(catalogId) ?: return DownloadState.Idle
        val filename = entry.downloadUrl.substringAfterLast("/")
        val targetFile = File(modelsDir, filename)
        val partialFile = File(modelsDir, filename + PARTIAL_SUFFIX)

        return when {
            targetFile.exists() && targetFile.length() > 0 ->
                DownloadState.Completed(targetFile.absolutePath)
            partialFile.exists() && partialFile.length() > 0 ->
                DownloadState.Paused(partialFile.length())
            else -> DownloadState.Idle
        }
    }

    /**
     * Compute SHA-256 hash of a file for integrity verification.
     */
    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)

        file.inputStream().use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
