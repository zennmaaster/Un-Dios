package com.castor.core.inference.download

/**
 * Represents the current state of a model download operation.
 *
 * Used by the UI to display progress bars, completion status, or error
 * messages during model downloads from HuggingFace.
 */
sealed interface DownloadState {

    /** No download in progress for this model. */
    data object Idle : DownloadState

    /**
     * Model is currently being downloaded.
     *
     * @param progress Download progress as a fraction (0.0 to 1.0)
     * @param bytesDownloaded Number of bytes downloaded so far
     * @param totalBytes Total file size in bytes (-1 if unknown)
     */
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadState

    /**
     * Download completed successfully.
     *
     * @param filePath Absolute path to the downloaded GGUF file on device
     */
    data class Completed(val filePath: String) : DownloadState

    /**
     * Download failed with an error.
     *
     * @param error Human-readable error description
     * @param retryable Whether the download can be retried (e.g. network error vs. disk full)
     */
    data class Failed(
        val error: String,
        val retryable: Boolean = true
    ) : DownloadState

    /**
     * Download was paused and can be resumed.
     *
     * @param bytesDownloaded Number of bytes already downloaded
     */
    data class Paused(val bytesDownloaded: Long) : DownloadState
}
