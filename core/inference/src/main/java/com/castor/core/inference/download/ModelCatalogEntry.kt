package com.castor.core.inference.download

import com.castor.core.inference.prompt.ModelFamily
import com.castor.core.inference.prompt.PromptFormat

/**
 * A model available for download from the model catalog.
 *
 * All models are GGUF-format files hosted on HuggingFace and downloaded
 * over HTTPS. No data is sent to any server — only a standard HTTP GET
 * to download the model weights.
 *
 * @param id Unique identifier for this catalog entry (e.g. "qwen25-3b-q4km")
 * @param displayName Human-readable name shown in the UI
 * @param family The model family for inference configuration
 * @param parameterCount Human-readable param count (e.g. "3B", "1.5B")
 * @param quantization Quantization method (e.g. "Q4_K_M", "Q5_K_M")
 * @param fileSizeBytes Expected file size in bytes for progress tracking
 * @param downloadUrl Direct HTTPS URL to the GGUF file
 * @param sha256 SHA-256 checksum for integrity verification after download
 * @param promptFormat The prompt format required by this model
 * @param contextLength Maximum context window in tokens
 * @param recommended Whether this is the recommended model for new users
 * @param description Short description of the model's capabilities and tradeoffs
 */
data class ModelCatalogEntry(
    val id: String,
    val displayName: String,
    val family: ModelFamily,
    val parameterCount: String,
    val quantization: String,
    val fileSizeBytes: Long,
    val downloadUrl: String,
    val sha256: String,
    val promptFormat: PromptFormat,
    val contextLength: Int,
    val recommended: Boolean = false,
    val description: String
) {
    /** Filename derived from the download URL for local storage. */
    val filename: String get() = downloadUrl.substringAfterLast("/")
}

/**
 * Pre-configured catalog of recommended on-device models.
 *
 * These are curated GGUF quantizations that balance quality, speed, and
 * RAM usage on typical Android devices. Qwen2.5-3B-Instruct is the
 * primary recommendation due to its strong instruction-following ability
 * at a reasonable size.
 *
 * All downloads go to HuggingFace over HTTPS. No telemetry, no tracking,
 * no data sent anywhere.
 */
object ModelCatalog {

    val entries: List<ModelCatalogEntry> = listOf(
        ModelCatalogEntry(
            id = "qwen25-0.5b-q4km",
            displayName = "Qwen2.5 0.5B Instruct",
            family = ModelFamily.QWEN25,
            parameterCount = "0.5B",
            quantization = "Q4_K_M",
            fileSizeBytes = 397_557_760L, // ~400 MB
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            sha256 = "", // TODO: Fill with actual checksum once verified
            promptFormat = PromptFormat.CHATML,
            contextLength = 32768,
            recommended = false,
            description = "Ultra-lightweight model for quick responses. Ideal for low-RAM " +
                "devices or fast on-device tasks. Reduced quality vs larger models."
        ),
        ModelCatalogEntry(
            id = "qwen25-1.5b-q4km",
            displayName = "Qwen2.5 1.5B Instruct",
            family = ModelFamily.QWEN25,
            parameterCount = "1.5B",
            quantization = "Q4_K_M",
            fileSizeBytes = 1_073_741_824L, // ~1.0 GB
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            sha256 = "", // TODO: Fill with actual checksum once verified
            promptFormat = PromptFormat.CHATML,
            contextLength = 32768,
            recommended = false,
            description = "Balanced model for devices with 4+ GB RAM. Good instruction " +
                "following with reasonable speed. Best value for most tasks."
        ),
        ModelCatalogEntry(
            id = "qwen25-3b-q4km",
            displayName = "Qwen2.5 3B Instruct",
            family = ModelFamily.QWEN25,
            parameterCount = "3B",
            quantization = "Q4_K_M",
            fileSizeBytes = 2_147_483_648L, // ~2.0 GB
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            sha256 = "", // TODO: Fill with actual checksum once verified
            promptFormat = PromptFormat.CHATML,
            contextLength = 32768,
            recommended = true,
            description = "Best balance of quality and speed. Strong instruction following, " +
                "multilingual support. Recommended for devices with 8+ GB RAM."
        ),
        ModelCatalogEntry(
            id = "qwen25-7b-q4km",
            displayName = "Qwen2.5 7B Instruct",
            family = ModelFamily.QWEN25,
            parameterCount = "7B",
            quantization = "Q4_K_M",
            fileSizeBytes = 4_831_838_208L, // ~4.5 GB
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m.gguf",
            sha256 = "",
            promptFormat = PromptFormat.CHATML,
            contextLength = 32768,
            recommended = false,
            description = "High-quality model for complex tasks (reasoning, analysis, planning). " +
                "Requires 8+ GB RAM. Best used as the COMPLEX tier model."
        ),
        ModelCatalogEntry(
            id = "phi3-mini-q4",
            displayName = "Phi-3 Mini 4K Instruct",
            family = ModelFamily.PHI3,
            parameterCount = "3.8B",
            quantization = "Q4_0",
            fileSizeBytes = 2_684_354_560L, // ~2.5 GB
            downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
            sha256 = "", // TODO: Fill with actual checksum once verified
            promptFormat = PromptFormat.PHI3,
            contextLength = 4096,
            recommended = false,
            description = "Microsoft Phi-3 mini. Good reasoning ability. Legacy option " +
                "for users familiar with Phi-3. Larger file than Qwen2.5 3B."
        )
    )

    /**
     * Get the primary recommended model (Qwen2.5 3B).
     */
    fun getRecommended(): ModelCatalogEntry = entries.first { it.recommended }

    /**
     * Find a catalog entry by its unique ID.
     */
    fun findById(id: String): ModelCatalogEntry? = entries.find { it.id == id }
}
