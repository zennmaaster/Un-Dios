package com.castor.core.inference

import android.util.Log
import com.castor.core.common.model.PrivacyTier
import com.castor.core.security.PrivacyClassifier
import com.castor.core.security.PrivacyPreferences
import com.castor.core.security.PrivacyPreferences.CloudProvider
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The hybrid inference engine that routes requests between the on-device local
 * LLM and cloud providers based on the user's privacy settings and the
 * classified privacy tier of each request.
 *
 * Decision flow:
 *  1. Classify the request's privacy tier (or use [forceTier] if provided)
 *  2. Apply user preference overrides (per-category tiers)
 *  3. Route to the appropriate engine:
 *     - LOCAL: always use on-device inference; if model not loaded, explain limitation
 *     - ANONYMIZED: redact PII, then send to cloud; fall back to local if cloud unavailable
 *     - CLOUD: send as-is to cloud; fall back to local if cloud unavailable
 *  4. Audit cloud responses for leaked PII
 *  5. Return the result with metadata about which engine/tier was used
 */
@Singleton
class HybridInferenceEngine @Inject constructor(
    private val localEngine: InferenceEngine,
    private val cloudEngine: CloudInferenceEngine,
    private val privacyClassifier: PrivacyClassifier,
    private val privacyPreferences: PrivacyPreferences
) {

    companion object {
        private const val TAG = "HybridInferenceEngine"
    }

    // =========================================================================
    // Result type
    // =========================================================================

    /**
     * The result of a hybrid inference call, including metadata about how
     * the request was processed.
     *
     * @param text The generated response text
     * @param tier The privacy tier that was actually used
     * @param wasRedacted Whether the prompt was redacted before sending to cloud
     * @param model Human-readable model identifier (e.g. "phi-3-mini (local)")
     * @param auditPassed Whether the cloud response passed PII audit (always true for local)
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
     * Generate a response by intelligently routing between local and cloud
     * inference based on privacy classification and user preferences.
     *
     * @param prompt The user's input prompt
     * @param systemPrompt Optional system-level instructions
     * @param maxTokens Maximum tokens for the response
     * @param forceTier If non-null, overrides the classifier's decision
     * @return An [InferenceResult] containing the response and routing metadata
     */
    suspend fun generate(
        prompt: String,
        systemPrompt: String? = null,
        maxTokens: Int = 512,
        forceTier: PrivacyTier? = null
    ): InferenceResult {
        // Step 1: Determine the effective privacy tier
        val classifiedTier = forceTier ?: classifyWithPreferences(prompt)

        Log.d(TAG, "Request classified as: $classifiedTier (forced=$forceTier)")

        return when (classifiedTier) {
            PrivacyTier.LOCAL -> generateLocal(prompt, systemPrompt, maxTokens)
            PrivacyTier.ANONYMIZED -> generateAnonymized(prompt, systemPrompt, maxTokens)
            PrivacyTier.CLOUD -> generateCloud(prompt, systemPrompt, maxTokens)
        }
    }

    /**
     * Check whether cloud processing is available (configured and enabled).
     */
    suspend fun isCloudAvailable(): Boolean {
        val enabled = privacyPreferences.cloudEnabled.first()
        return enabled && cloudEngine.isConfigured()
    }

    /**
     * Get the current effective privacy tier for a given prompt without
     * actually running inference. Useful for UI previews.
     */
    suspend fun previewTier(prompt: String): PrivacyTier {
        return classifyWithPreferences(prompt)
    }

    // =========================================================================
    // Tier-specific generation strategies
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
            // Model not loaded — inform user and suggest cloud
            InferenceResult(
                text = buildModelNotLoadedMessage(),
                tier = PrivacyTier.LOCAL,
                wasRedacted = false,
                model = "none (local)"
            )
        }
    }

    /**
     * ANONYMIZED tier: redact PII from the prompt, then send to cloud.
     * Falls back to local inference if cloud is unavailable.
     */
    private suspend fun generateAnonymized(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int
    ): InferenceResult {
        // Step 1: Redact the prompt
        val redactedPrompt = privacyClassifier.redact(prompt)
        val wasRedacted = redactedPrompt != prompt

        if (wasRedacted) {
            Log.d(TAG, "Prompt was redacted for ANONYMIZED tier")
        }

        // Step 2: Try cloud with redacted prompt
        if (cloudEngine.isConfigured()) {
            try {
                val provider = privacyPreferences.cloudProvider.first()
                val response = cloudEngine.generate(
                    prompt = redactedPrompt,
                    systemPrompt = systemPrompt,
                    maxTokens = maxTokens,
                    provider = provider
                )

                // Step 3: Audit the response for leaked PII
                val auditPassed = privacyClassifier.auditResponse(response)
                if (!auditPassed) {
                    Log.w(TAG, "Cloud response failed PII audit — returning with warning")
                }

                return InferenceResult(
                    text = if (auditPassed) response else wrapWithAuditWarning(response),
                    tier = PrivacyTier.ANONYMIZED,
                    wasRedacted = wasRedacted,
                    model = cloudEngine.getModelName(provider),
                    auditPassed = auditPassed
                )
            } catch (e: CloudInferenceException) {
                Log.w(TAG, "Cloud inference failed for ANONYMIZED tier, falling back to local", e)
                // Fall through to local fallback
            }
        }

        // Fallback: try local with the original (unredacted) prompt
        return generateLocalFallback(prompt, systemPrompt, maxTokens, PrivacyTier.ANONYMIZED)
    }

    /**
     * CLOUD tier: send the prompt as-is to the cloud provider.
     * Falls back to local inference if cloud is unavailable.
     */
    private suspend fun generateCloud(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int
    ): InferenceResult {
        if (cloudEngine.isConfigured()) {
            try {
                val provider = privacyPreferences.cloudProvider.first()
                val response = cloudEngine.generate(
                    prompt = prompt,
                    systemPrompt = systemPrompt,
                    maxTokens = maxTokens,
                    provider = provider
                )

                return InferenceResult(
                    text = response,
                    tier = PrivacyTier.CLOUD,
                    wasRedacted = false,
                    model = cloudEngine.getModelName(provider)
                )
            } catch (e: CloudInferenceException) {
                Log.w(TAG, "Cloud inference failed for CLOUD tier, falling back to local", e)
                // Fall through to local fallback
            }
        }

        // Fallback: try local
        return generateLocalFallback(prompt, systemPrompt, maxTokens, PrivacyTier.CLOUD)
    }

    /**
     * Attempt to use the local model as a fallback when cloud is unavailable.
     * Preserves the original requested tier in the result for transparency.
     */
    private suspend fun generateLocalFallback(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        requestedTier: PrivacyTier
    ): InferenceResult {
        return if (localEngine.isLoaded) {
            try {
                val response = localEngine.generate(
                    prompt = prompt,
                    systemPrompt = systemPrompt ?: "",
                    maxTokens = maxTokens
                )
                InferenceResult(
                    text = "[Processed locally — cloud unavailable]\n\n$response",
                    tier = PrivacyTier.LOCAL,
                    wasRedacted = false,
                    model = "${localEngine.modelName} (local fallback)"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Local fallback also failed", e)
                InferenceResult(
                    text = buildCloudUnavailableMessage(requestedTier),
                    tier = PrivacyTier.LOCAL,
                    wasRedacted = false,
                    model = "none"
                )
            }
        } else {
            InferenceResult(
                text = buildCloudUnavailableMessage(requestedTier),
                tier = PrivacyTier.LOCAL,
                wasRedacted = false,
                model = "none"
            )
        }
    }

    // =========================================================================
    // Classification with user preference overrides
    // =========================================================================

    /**
     * Classify the prompt and apply the user's per-category tier preferences.
     * The effective tier is the MORE restrictive of the classifier's decision
     * and the user's preference for that category.
     */
    private suspend fun classifyWithPreferences(prompt: String): PrivacyTier {
        val classifiedTier = privacyClassifier.classify(prompt)
        val lower = prompt.lowercase()

        // Determine the category-specific user preference
        val categoryTier = when {
            isMessagingRelated(lower) -> privacyPreferences.messagingTier.first()
            isMediaRelated(lower) -> privacyPreferences.mediaTier.first()
            else -> privacyPreferences.generalTier.first()
        }

        val defaultTier = privacyPreferences.defaultTier.first()

        // The effective tier is the MOST restrictive of:
        // 1. The classifier's decision
        // 2. The category-specific user preference
        // 3. The user's default tier
        // Restrictiveness: LOCAL > ANONYMIZED > CLOUD
        return mostRestrictive(classifiedTier, categoryTier, defaultTier)
    }

    /**
     * Returns the most restrictive (most private) of the given tiers.
     * LOCAL is most restrictive, CLOUD is least restrictive.
     */
    private fun mostRestrictive(vararg tiers: PrivacyTier): PrivacyTier {
        return tiers.minByOrNull { it.ordinal } ?: PrivacyTier.LOCAL
    }

    // =========================================================================
    // Category detection helpers
    // =========================================================================

    private fun isMessagingRelated(input: String): Boolean {
        val keywords = listOf(
            "message", "text", "sms", "reply", "send",
            "whatsapp", "telegram", "signal", "chat",
            "contact", "call", "phone"
        )
        return keywords.any { input.contains(it) }
    }

    private fun isMediaRelated(input: String): Boolean {
        val keywords = listOf(
            "play", "music", "song", "album", "artist",
            "spotify", "youtube", "podcast", "audiobook",
            "audible", "video", "stream", "playlist", "queue"
        )
        return keywords.any { input.contains(it) }
    }

    // =========================================================================
    // User-facing error messages
    // =========================================================================

    private fun buildModelNotLoadedMessage(): String = buildString {
        appendLine("No on-device model is currently loaded.")
        appendLine()
        appendLine("Options:")
        appendLine("  1. Download a GGUF model to enable local inference")
        appendLine("  2. Configure a cloud API key in Privacy Settings")
        appendLine("     to enable cloud-assisted processing")
        appendLine()
        appendLine("Your data stays on-device until you explicitly enable cloud.")
    }

    private fun buildLocalErrorMessage(error: Exception): String = buildString {
        appendLine("Local inference encountered an error:")
        appendLine("  ${error.message ?: "Unknown error"}")
        appendLine()
        appendLine("Try reloading the model or configuring cloud fallback")
        appendLine("in Privacy Settings.")
    }

    private fun buildCloudUnavailableMessage(requestedTier: PrivacyTier): String = buildString {
        appendLine("This request was classified as ${requestedTier.name}.")
        appendLine()
        if (requestedTier == PrivacyTier.CLOUD || requestedTier == PrivacyTier.ANONYMIZED) {
            appendLine("Cloud processing is not available because:")
            appendLine("  - No API key is configured, or")
            appendLine("  - The cloud provider is unreachable")
            appendLine()
            appendLine("The on-device model is also not loaded.")
            appendLine()
            appendLine("To resolve this:")
            appendLine("  1. Set a cloud API key in Privacy Settings")
            appendLine("  2. Or download a GGUF model for on-device inference")
        }
    }

    private fun wrapWithAuditWarning(response: String): String = buildString {
        appendLine("[PRIVACY NOTICE: The response below may contain personal")
        appendLine("information patterns. It was processed via ANONYMIZED tier")
        appendLine("but the cloud response includes data that resembles PII.]")
        appendLine()
        append(response)
    }
}
