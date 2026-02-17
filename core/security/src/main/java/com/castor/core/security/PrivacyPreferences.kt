package com.castor.core.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.castor.core.common.model.PrivacyTier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Extension property that creates a single DataStore instance for privacy preferences. */
private val Context.privacyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "castor_privacy_preferences"
)

/**
 * Persistent user preferences for the privacy tier system.
 *
 * Stored via Jetpack DataStore (unencrypted preferences). Sensitive values
 * like API keys are stored separately in [SecurePreferences] and only
 * referenced by existence here.
 *
 * Default philosophy: everything is LOCAL unless the user explicitly opts in
 * to cloud processing.
 */
@Singleton
class PrivacyPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePreferences: SecurePreferences
) {

    private val dataStore: DataStore<Preferences>
        get() = context.privacyDataStore

    // =========================================================================
    // Preference keys
    // =========================================================================

    private object Keys {
        val DEFAULT_TIER = stringPreferencesKey("default_tier")
        val MESSAGING_TIER = stringPreferencesKey("messaging_tier")
        val MEDIA_TIER = stringPreferencesKey("media_tier")
        val GENERAL_TIER = stringPreferencesKey("general_tier")
        val CLOUD_PROVIDER = stringPreferencesKey("cloud_provider")
        val ALWAYS_ASK_FOR_CLOUD = booleanPreferencesKey("always_ask_for_cloud")
        val CLOUD_ENABLED = booleanPreferencesKey("cloud_enabled")
    }

    /** Key used in [SecurePreferences] for the cloud API key. */
    private object SecureKeys {
        const val CLOUD_API_KEY = "cloud_api_key"
    }

    // =========================================================================
    // Cloud providers
    // =========================================================================

    /**
     * Supported cloud LLM providers.
     */
    enum class CloudProvider(val displayName: String, val id: String) {
        ANTHROPIC("Anthropic (Claude)", "anthropic"),
        OPENAI("OpenAI (GPT)", "openai"),
        GOOGLE("Google (Gemini)", "google"),
        CUSTOM("Custom endpoint", "custom");

        companion object {
            fun fromId(id: String): CloudProvider =
                entries.firstOrNull { it.id == id } ?: ANTHROPIC
        }
    }

    // =========================================================================
    // Reactive flows — observe preference changes
    // =========================================================================

    /** The user's default privacy tier. Defaults to LOCAL. */
    val defaultTier: Flow<PrivacyTier> = dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_TIER]?.toPrivacyTier() ?: PrivacyTier.LOCAL
    }

    /** Privacy tier for messaging operations. Defaults to LOCAL (messages are sensitive). */
    val messagingTier: Flow<PrivacyTier> = dataStore.data.map { prefs ->
        prefs[Keys.MESSAGING_TIER]?.toPrivacyTier() ?: PrivacyTier.LOCAL
    }

    /** Privacy tier for media queries. Defaults to ANONYMIZED (media search is less sensitive). */
    val mediaTier: Flow<PrivacyTier> = dataStore.data.map { prefs ->
        prefs[Keys.MEDIA_TIER]?.toPrivacyTier() ?: PrivacyTier.ANONYMIZED
    }

    /** Privacy tier for general knowledge queries. Defaults to ANONYMIZED. */
    val generalTier: Flow<PrivacyTier> = dataStore.data.map { prefs ->
        prefs[Keys.GENERAL_TIER]?.toPrivacyTier() ?: PrivacyTier.ANONYMIZED
    }

    /** The selected cloud provider. Defaults to ANTHROPIC. */
    val cloudProvider: Flow<CloudProvider> = dataStore.data.map { prefs ->
        val id = prefs[Keys.CLOUD_PROVIDER] ?: CloudProvider.ANTHROPIC.id
        CloudProvider.fromId(id)
    }

    /**
     * The cloud API key, read from encrypted storage.
     * Emits null if no key has been configured.
     */
    val cloudApiKey: Flow<String?> = dataStore.data.map {
        // We trigger a read from SecurePreferences whenever DataStore emits.
        // The key itself is NOT in DataStore — only in encrypted storage.
        securePreferences.getString(SecureKeys.CLOUD_API_KEY)
    }

    /** Whether to prompt the user before every cloud request. Defaults to true. */
    val alwaysAskForCloud: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ALWAYS_ASK_FOR_CLOUD] ?: true
    }

    /** Master switch for cloud processing. Defaults to false (off). */
    val cloudEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.CLOUD_ENABLED] ?: false
    }

    // =========================================================================
    // Mutators
    // =========================================================================

    /** Set the default privacy tier applied to unclassified requests. */
    suspend fun setDefaultTier(tier: PrivacyTier) {
        dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_TIER] = tier.name
        }
    }

    /** Set the privacy tier for messaging-related queries. */
    suspend fun setMessagingTier(tier: PrivacyTier) {
        dataStore.edit { prefs ->
            prefs[Keys.MESSAGING_TIER] = tier.name
        }
    }

    /** Set the privacy tier for media-related queries. */
    suspend fun setMediaTier(tier: PrivacyTier) {
        dataStore.edit { prefs ->
            prefs[Keys.MEDIA_TIER] = tier.name
        }
    }

    /** Set the privacy tier for general-knowledge queries. */
    suspend fun setGeneralTier(tier: PrivacyTier) {
        dataStore.edit { prefs ->
            prefs[Keys.GENERAL_TIER] = tier.name
        }
    }

    /** Set the cloud LLM provider. */
    suspend fun setCloudProvider(provider: CloudProvider) {
        dataStore.edit { prefs ->
            prefs[Keys.CLOUD_PROVIDER] = provider.id
        }
    }

    /**
     * Store the cloud API key in encrypted storage.
     * The key is never written to DataStore — only to [SecurePreferences].
     */
    suspend fun setCloudApiKey(key: String) {
        securePreferences.putString(SecureKeys.CLOUD_API_KEY, key)
        // Touch DataStore so that `cloudApiKey` flow re-emits
        dataStore.edit { prefs ->
            prefs[Keys.CLOUD_ENABLED] = key.isNotBlank()
        }
    }

    /** Clear the stored cloud API key. */
    suspend fun clearCloudApiKey() {
        securePreferences.remove(SecureKeys.CLOUD_API_KEY)
        dataStore.edit { prefs ->
            prefs[Keys.CLOUD_ENABLED] = false
        }
    }

    /** Set whether the user should be prompted before each cloud request. */
    suspend fun setAlwaysAsk(ask: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.ALWAYS_ASK_FOR_CLOUD] = ask
        }
    }

    /** Enable or disable cloud processing globally. */
    suspend fun setCloudEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.CLOUD_ENABLED] = enabled
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Check synchronously (via SecurePreferences) whether a cloud API key
     * has been configured. Useful for quick checks without collecting a Flow.
     */
    fun hasCloudApiKey(): Boolean {
        return securePreferences.getString(SecureKeys.CLOUD_API_KEY)?.isNotBlank() == true
    }

    /** Convert a stored string to a [PrivacyTier], defaulting to LOCAL on unknown values. */
    private fun String.toPrivacyTier(): PrivacyTier {
        return try {
            PrivacyTier.valueOf(this)
        } catch (_: IllegalArgumentException) {
            PrivacyTier.LOCAL
        }
    }
}
