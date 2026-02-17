package com.castor.core.inference

import android.content.Context
import android.util.Log
import com.castor.core.security.PrivacyPreferences.CloudProvider
import com.castor.core.security.SecurePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Cloud-based inference engine that calls external LLM APIs as a fallback
 * when on-device inference is unavailable or insufficient.
 *
 * Supports:
 * - Anthropic (Claude) via Messages API
 * - OpenAI (GPT) via Chat Completions API
 *
 * Uses OkHttp for direct HTTP calls — simpler and more flexible than Retrofit
 * when supporting multiple providers with different API shapes.
 *
 * API keys are stored in [SecurePreferences] (AES-256 encrypted).
 */
@Singleton
class CloudInferenceEngine @Inject constructor(
    private val securePreferences: SecurePreferences,
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "CloudInferenceEngine"

        // --- Anthropic ---
        private const val ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_MODEL = "claude-sonnet-4-5-20250929"
        private const val ANTHROPIC_API_VERSION = "2023-06-01"

        // --- OpenAI ---
        private const val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
        private const val OPENAI_MODEL = "gpt-4o"

        // --- Secure preference keys ---
        private const val KEY_CLOUD_API = "cloud_api_key"

        // --- Timeouts ---
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 60L
        private const val WRITE_TIMEOUT_SECONDS = 30L

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Generate a complete response from a cloud LLM provider.
     *
     * @param prompt The user prompt to send
     * @param systemPrompt Optional system-level instructions
     * @param maxTokens Maximum tokens in the response
     * @param temperature Sampling temperature (0.0 = deterministic, 1.0 = creative)
     * @param provider Which cloud provider to use
     * @return The generated text response
     * @throws CloudInferenceException if the request fails
     */
    suspend fun generate(
        prompt: String,
        systemPrompt: String? = null,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        provider: CloudProvider = CloudProvider.ANTHROPIC
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
            ?: throw CloudInferenceException("No API key configured. Set one in Privacy Settings.")

        when (provider) {
            CloudProvider.ANTHROPIC -> callAnthropic(
                apiKey = apiKey,
                prompt = prompt,
                systemPrompt = systemPrompt,
                maxTokens = maxTokens,
                temperature = temperature
            )
            CloudProvider.OPENAI -> callOpenAI(
                apiKey = apiKey,
                prompt = prompt,
                systemPrompt = systemPrompt,
                maxTokens = maxTokens,
                temperature = temperature
            )
            CloudProvider.GOOGLE -> callOpenAI(
                // Google's Gemini API is compatible with OpenAI-style calls via proxy
                // For now, fall back to OpenAI-compatible endpoint
                apiKey = apiKey,
                prompt = prompt,
                systemPrompt = systemPrompt,
                maxTokens = maxTokens,
                temperature = temperature
            )
            CloudProvider.CUSTOM -> callOpenAI(
                // Custom endpoints expected to be OpenAI-compatible
                apiKey = apiKey,
                prompt = prompt,
                systemPrompt = systemPrompt,
                maxTokens = maxTokens,
                temperature = temperature
            )
        }
    }

    /**
     * Stream tokens from a cloud LLM provider.
     *
     * Emits individual text chunks as they arrive via Server-Sent Events (SSE).
     *
     * @param prompt The user prompt to send
     * @param systemPrompt Optional system-level instructions
     * @param provider Which cloud provider to use
     * @return A [Flow] of text chunks
     */
    fun generateStream(
        prompt: String,
        systemPrompt: String? = null,
        provider: CloudProvider = CloudProvider.ANTHROPIC
    ): Flow<String> = flow {
        val apiKey = getApiKey()
            ?: throw CloudInferenceException("No API key configured. Set one in Privacy Settings.")

        when (provider) {
            CloudProvider.ANTHROPIC -> {
                val chunks = streamAnthropic(apiKey, prompt, systemPrompt)
                chunks.forEach { chunk -> emit(chunk) }
            }
            CloudProvider.OPENAI,
            CloudProvider.GOOGLE,
            CloudProvider.CUSTOM -> {
                val chunks = streamOpenAI(apiKey, prompt, systemPrompt)
                chunks.forEach { chunk -> emit(chunk) }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Check whether cloud inference is properly configured (API key exists).
     */
    fun isConfigured(): Boolean {
        val key = securePreferences.getString(KEY_CLOUD_API)
        return !key.isNullOrBlank()
    }

    /**
     * Returns the display name of the model for a given provider.
     */
    fun getModelName(provider: CloudProvider): String = when (provider) {
        CloudProvider.ANTHROPIC -> "claude-sonnet (cloud)"
        CloudProvider.OPENAI -> "gpt-4o (cloud)"
        CloudProvider.GOOGLE -> "gemini (cloud)"
        CloudProvider.CUSTOM -> "custom (cloud)"
    }

    // =========================================================================
    // Anthropic Messages API
    // =========================================================================

    /**
     * Call the Anthropic Messages API for a complete (non-streaming) response.
     *
     * POST https://api.anthropic.com/v1/messages
     * Headers: x-api-key, anthropic-version, content-type
     */
    private suspend fun callAnthropic(
        apiKey: String,
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        temperature: Float
    ): String {
        val body = JSONObject().apply {
            put("model", ANTHROPIC_MODEL)
            put("max_tokens", maxTokens)
            put("temperature", temperature.toDouble())
            if (!systemPrompt.isNullOrBlank()) {
                put("system", systemPrompt)
            }
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val request = Request.Builder()
            .url(ANTHROPIC_API_URL)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", ANTHROPIC_API_VERSION)
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val responseBody = executeRequest(request)
        return parseAnthropicResponse(responseBody)
    }

    /**
     * Stream from the Anthropic Messages API using Server-Sent Events.
     */
    private suspend fun streamAnthropic(
        apiKey: String,
        prompt: String,
        systemPrompt: String?
    ): List<String> {
        val body = JSONObject().apply {
            put("model", ANTHROPIC_MODEL)
            put("max_tokens", 1024)
            put("stream", true)
            if (!systemPrompt.isNullOrBlank()) {
                put("system", systemPrompt)
            }
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val request = Request.Builder()
            .url(ANTHROPIC_API_URL)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", ANTHROPIC_API_VERSION)
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return collectSSE(request) { event ->
            // Anthropic SSE format: event type "content_block_delta" with delta.text
            try {
                val json = JSONObject(event)
                val type = json.optString("type")
                if (type == "content_block_delta") {
                    val delta = json.optJSONObject("delta")
                    delta?.optString("text")
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse Anthropic SSE chunk: $event", e)
                null
            }
        }
    }

    /**
     * Parse a non-streaming Anthropic Messages API response.
     */
    private fun parseAnthropicResponse(body: String): String {
        return try {
            val json = JSONObject(body)

            // Check for API errors
            val errorObj = json.optJSONObject("error")
            if (errorObj != null) {
                val errorMessage = errorObj.optString("message", "Unknown Anthropic API error")
                throw CloudInferenceException("Anthropic API error: $errorMessage")
            }

            val content = json.getJSONArray("content")
            val textBlocks = (0 until content.length())
                .map { content.getJSONObject(it) }
                .filter { it.getString("type") == "text" }
                .joinToString("") { it.getString("text") }

            if (textBlocks.isEmpty()) {
                throw CloudInferenceException("Anthropic returned empty response")
            }

            textBlocks
        } catch (e: CloudInferenceException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Anthropic response: $body", e)
            throw CloudInferenceException("Failed to parse Anthropic response: ${e.message}")
        }
    }

    // =========================================================================
    // OpenAI Chat Completions API
    // =========================================================================

    /**
     * Call the OpenAI Chat Completions API for a complete (non-streaming) response.
     *
     * POST https://api.openai.com/v1/chat/completions
     * Headers: Authorization: Bearer <key>, content-type
     */
    private suspend fun callOpenAI(
        apiKey: String,
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        temperature: Float
    ): String {
        val messages = JSONArray().apply {
            if (!systemPrompt.isNullOrBlank()) {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val body = JSONObject().apply {
            put("model", OPENAI_MODEL)
            put("messages", messages)
            put("max_tokens", maxTokens)
            put("temperature", temperature.toDouble())
        }

        val request = Request.Builder()
            .url(OPENAI_API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val responseBody = executeRequest(request)
        return parseOpenAIResponse(responseBody)
    }

    /**
     * Stream from the OpenAI Chat Completions API using Server-Sent Events.
     */
    private suspend fun streamOpenAI(
        apiKey: String,
        prompt: String,
        systemPrompt: String?
    ): List<String> {
        val messages = JSONArray().apply {
            if (!systemPrompt.isNullOrBlank()) {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val body = JSONObject().apply {
            put("model", OPENAI_MODEL)
            put("messages", messages)
            put("max_tokens", 1024)
            put("stream", true)
        }

        val request = Request.Builder()
            .url(OPENAI_API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return collectSSE(request) { event ->
            // OpenAI SSE format: data lines with JSON, delta.content field
            try {
                if (event.trim() == "[DONE]") return@collectSSE null
                val json = JSONObject(event)
                val choices = json.optJSONArray("choices") ?: return@collectSSE null
                if (choices.length() == 0) return@collectSSE null
                val delta = choices.getJSONObject(0).optJSONObject("delta")
                delta?.optString("content")?.takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse OpenAI SSE chunk: $event", e)
                null
            }
        }
    }

    /**
     * Parse a non-streaming OpenAI Chat Completions response.
     */
    private fun parseOpenAIResponse(body: String): String {
        return try {
            val json = JSONObject(body)

            // Check for API errors
            val errorObj = json.optJSONObject("error")
            if (errorObj != null) {
                val errorMessage = errorObj.optString("message", "Unknown OpenAI API error")
                throw CloudInferenceException("OpenAI API error: $errorMessage")
            }

            val choices = json.getJSONArray("choices")
            if (choices.length() == 0) {
                throw CloudInferenceException("OpenAI returned no choices")
            }

            val message = choices.getJSONObject(0).getJSONObject("message")
            val content = message.getString("content")

            if (content.isBlank()) {
                throw CloudInferenceException("OpenAI returned empty content")
            }

            content
        } catch (e: CloudInferenceException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse OpenAI response: $body", e)
            throw CloudInferenceException("Failed to parse OpenAI response: ${e.message}")
        }
    }

    // =========================================================================
    // HTTP helpers
    // =========================================================================

    /**
     * Execute a synchronous HTTP request on the IO dispatcher and return the
     * response body as a string. Throws [CloudInferenceException] on failure.
     */
    private suspend fun executeRequest(request: Request): String =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)

            continuation.invokeOnCancellation {
                call.cancel()
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            CloudInferenceException("Network error: ${e.message}", e)
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        val body = resp.body?.string() ?: ""

                        if (!resp.isSuccessful) {
                            val errorDetail = try {
                                val json = JSONObject(body)
                                json.optJSONObject("error")?.optString("message") ?: body
                            } catch (_: Exception) {
                                body.take(500)
                            }

                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    CloudInferenceException(
                                        "API request failed (HTTP ${resp.code}): $errorDetail"
                                    )
                                )
                            }
                            return
                        }

                        if (continuation.isActive) {
                            continuation.resume(body)
                        }
                    }
                }
            })
        }

    /**
     * Collect Server-Sent Events from an HTTP request.
     * Parses each SSE data line using the provided [parseChunk] function.
     *
     * Returns all parsed non-null chunks as a list. For true streaming to
     * a Flow, use [generateStream] which wraps this.
     */
    private suspend fun collectSSE(
        request: Request,
        parseChunk: (String) -> String?
    ): List<String> = suspendCancellableCoroutine { continuation ->
        val chunks = mutableListOf<String>()

        val eventSourceFactory = EventSources.createFactory(httpClient)
        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                val parsed = parseChunk(data)
                if (parsed != null) {
                    synchronized(chunks) {
                        chunks.add(parsed)
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                if (continuation.isActive) {
                    continuation.resume(chunks)
                }
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                if (continuation.isActive) {
                    // If we have some chunks, return what we got
                    if (chunks.isNotEmpty()) {
                        continuation.resume(chunks)
                    } else {
                        continuation.resumeWithException(
                            CloudInferenceException(
                                "Stream failed: ${t?.message ?: "Unknown error"}",
                                t
                            )
                        )
                    }
                }
            }
        }

        val eventSource = eventSourceFactory.newEventSource(request, listener)

        continuation.invokeOnCancellation {
            eventSource.cancel()
        }
    }

    /**
     * Retrieve the stored API key from encrypted preferences.
     */
    private fun getApiKey(): String? {
        return securePreferences.getString(KEY_CLOUD_API)
    }
}

/**
 * Exception type for all cloud inference errors. Wraps network failures,
 * API errors, parsing errors, and configuration issues.
 */
class CloudInferenceException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
