package com.castor.core.inference

import android.util.Log
import com.castor.core.common.model.PrivacyTier
import com.castor.core.security.PrivacyClassifier
import com.castor.core.security.PrivacyPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The hybrid inference engine routes all requests to on-device local inference.
 *
 * CLOUD INFERENCE IS DISABLED. All data stays on-device at all times.
 * The cloud engine code is retained in [CloudInferenceEngine] for potential
 * future use, but this router NEVER calls it. The privacy classifier is
 * still used for metadata (logging, UI badges) but does not change routing.
 *
 * Decision flow:
 *  1. Classify the request's privacy tier (for logging/UI purposes only)
 *  2. Always route to on-device local inference
 *  3. Return the result with LOCAL tier metadata
 */
@Singleton
class HybridInferenceEngine @Inject constructor(
    private val localEngine: InferenceEngine,
    @Suppress("unused") private val cloudEngine: CloudInferenceEngine,
    private val privacyClassifier: PrivacyClassifier,
    @Suppress("unused") private val privacyPreferences: PrivacyPreferences
) {

    companion object {
        private const val TAG = "HybridInferenceEngine"
    }

    // =========================================================================
    // Result type
    // =========================================================================

    /**
     * The result of an inference call, including metadata about how
     * the request was processed.
     *
     * @param text The generated response text
     * @param tier The privacy tier used (always LOCAL in this configuration)
     * @param wasRedacted Always false — no redaction needed for local inference
     * @param model Human-readable model identifier (e.g. "qwen2.5-3b-instruct (local)")
     * @param auditPassed Always true for local inference
     */
    data class InferenceResult(
        val text: String,
        val tier: PrivacyTier,
        val wasRedacted: Boolean,
        val model: String,
        val auditPassed: Boolean = true
    )

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Generate a response using on-device local inference.
     *
     * Cloud inference is disabled — all requests are processed locally
     * regardless of the classified privacy tier or user preferences.
     * The [forceTier] parameter is accepted for API compatibility but
     * has no effect on routing.
     *
     * @param prompt The user's input prompt
     * @param systemPrompt Optional system-level instructions
     * @param maxTokens Maximum tokens for the response
     * @param forceTier Ignored — always routes to LOCAL
     * @return An [InferenceResult] containing the response and routing metadata
     */
    suspend fun generate(
        prompt: String,
        systemPrompt: String? = null,
        maxTokens: Int = 512,
        @Suppress("UNUSED_PARAMETER") forceTier: PrivacyTier? = null
    ): InferenceResult {
        // Classify for logging purposes only — does NOT change routing
        val classifiedTier = privacyClassifier.classify(prompt)
        Log.d(TAG, "Request classified as: $classifiedTier (routing to LOCAL — cloud disabled)")

        return generateLocal(prompt, systemPrompt, maxTokens)
    }

    /**
     * Cloud is permanently disabled in this configuration.
     * Always returns false.
     */
    @Suppress("unused")
    suspend fun isCloudAvailable(): Boolean = false

    /**
     * Get the classified privacy tier for a prompt (for UI display only).
     * Does not affect routing — everything goes to LOCAL.
     */
    suspend fun previewTier(prompt: String): PrivacyTier {
        return privacyClassifier.classify(prompt)
    }

    // =========================================================================
    // Local inference (the only code path)
    // =========================================================================

    /**
     * LOCAL tier: always use the on-device model.
     * If the model is not loaded, return a helpful message instead of failing.
     */
    private suspend fun generateLocal(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int
    ): InferenceResult {
        return if (localEngine.isLoaded) {
            try {
                val response = localEngine.generate(
                    prompt = prompt,
                    systemPrompt = systemPrompt ?: "",
                    maxTokens = maxTokens
                )
                InferenceResult(
                    text = response,
                    tier = PrivacyTier.LOCAL,
                    wasRedacted = false,
                    model = "${localEngine.modelName} (local)"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Local inference failed", e)
                InferenceResult(
                    text = buildLocalErrorMessage(e),
                    tier = PrivacyTier.LOCAL,
                    wasRedacted = false,
                    model = "${localEngine.modelName} (local)"
                )
            }
        } else {
            InferenceResult(
                text = buildModelNotLoadedMessage(),
                tier = PrivacyTier.LOCAL,
                wasRedacted = false,
                model = "none (local)"
            )
        }
    }

    // =========================================================================
    // User-facing messages
    // =========================================================================

    private fun buildModelNotLoadedMessage(): String = buildString {
        appendLine("No on-device model is currently loaded.")
        appendLine()
        appendLine("To get started:")
        appendLine("  1. Open the Model Manager from settings")
        appendLine("  2. Download Qwen2.5-3B-Instruct (recommended)")
        appendLine("  3. The model will load automatically")
        appendLine()
        appendLine("All processing happens on-device. No data leaves your phone.")
    }

    private fun buildLocalErrorMessage(error: Exception): String = buildString {
        appendLine("Local inference encountered an error:")
        appendLine("  ${error.message ?: "Unknown error"}")
        appendLine()
        appendLine("Try reloading the model from the Model Manager.")
    }
}
