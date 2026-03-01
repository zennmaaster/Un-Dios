package com.castor.feature.media.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.castor.core.security.SecurePreferences
import com.castor.feature.media.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Manages Spotify OAuth 2.0 PKCE authorization flow using AppAuth.
 *
 * Handles the full lifecycle: building the auth intent, exchanging the
 * authorization code for tokens, persisting tokens in EncryptedSharedPreferences
 * via [SecurePreferences], refreshing expired access tokens, and logging out.
 */
@Singleton
class SpotifyAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePreferences: SecurePreferences
) {
    private val _isAuthenticated = MutableStateFlow(hasStoredTokens())
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse(AUTHORIZATION_ENDPOINT),
        Uri.parse(TOKEN_ENDPOINT)
    )

    private val authService: AuthorizationService by lazy {
        AuthorizationService(context)
    }

    private val configuredClientId: String = BuildConfig.SPOTIFY_CLIENT_ID.trim()
    private val configuredRedirectUri: String = BuildConfig.SPOTIFY_REDIRECT_URI.trim()

    // -----------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------

    /**
     * Returns true when OAuth has been configured with real credentials.
     */
    fun isConfigured(): Boolean {
        return configuredClientId.isNotBlank() &&
            configuredRedirectUri.isNotBlank() &&
            !configuredClientId.startsWith("YOUR_")
    }

    /**
     * Creates an [Intent] that launches the Spotify authorization page in a browser.
     * The caller should start this intent with an activity result launcher.
     * Uses PKCE (code_verifier / code_challenge) automatically via AppAuth.
     */
    fun createAuthIntent(): Intent? {
        if (!isConfigured()) return null

        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            configuredClientId,
            ResponseTypeValues.CODE,
            Uri.parse(configuredRedirectUri)
        )
            .setScopes(SCOPES)
            .build()

        return authService.getAuthorizationRequestIntent(authRequest)
    }

    /**
     * Handles the redirect intent returned after the user authorizes in the browser.
     * Extracts the authorization code, exchanges it for access + refresh tokens,
     * and persists them in secure storage.
     */
    fun handleAuthResponse(intent: Intent) {
        if (!isConfigured()) {
            _isAuthenticated.value = false
            return
        }

        val response = AuthorizationResponse.fromIntent(intent)
        val exception = net.openid.appauth.AuthorizationException.fromIntent(intent)

        if (response == null) {
            Log.e(TAG, "Authorization failed", exception)
            _isAuthenticated.value = false
            return
        }

        val tokenRequest = response.createTokenExchangeRequest()
        authService.performTokenRequest(tokenRequest) { tokenResponse, tokenException ->
            if (tokenResponse != null) {
                saveTokens(
                    accessToken = tokenResponse.accessToken.orEmpty(),
                    refreshToken = tokenResponse.refreshToken.orEmpty(),
                    expiresAtMillis = tokenResponse.accessTokenExpirationTime ?: 0L
                )
                _isAuthenticated.value = true
                Log.d(TAG, "Token exchange successful")
            } else {
                Log.e(TAG, "Token exchange failed", tokenException)
                _isAuthenticated.value = false
            }
        }
    }

    /**
     * Returns a valid Spotify access token. If the current token is expired,
     * a refresh is attempted automatically. Returns `null` when no tokens are
     * stored or the refresh fails.
     */
    suspend fun getAccessToken(): String? {
        if (!isConfigured()) return null

        val accessToken = securePreferences.getString(KEY_ACCESS_TOKEN)
        if (accessToken != null && !isTokenExpired()) {
            return accessToken
        }

        // Attempt a silent refresh
        return refreshToken()
    }

    /**
     * Clears all Spotify tokens from secure storage and resets the
     * authentication state.
     */
    fun logout() {
        securePreferences.remove(KEY_ACCESS_TOKEN)
        securePreferences.remove(KEY_REFRESH_TOKEN)
        securePreferences.remove(KEY_EXPIRES_AT)
        _isAuthenticated.value = false
        Log.d(TAG, "Spotify session cleared")
    }

    // -----------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------

    /**
     * Uses the stored refresh token to obtain a new access token.
     * Returns the new access token on success, or `null` on failure.
     */
    private suspend fun refreshToken(): String? {
        val refreshToken = securePreferences.getString(KEY_REFRESH_TOKEN)
            ?: return null

        val tokenRequest = TokenRequest.Builder(serviceConfig, configuredClientId)
            .setGrantType("refresh_token")
            .setRefreshToken(refreshToken)
            .setRedirectUri(Uri.parse(configuredRedirectUri))
            .build()

        return suspendCancellableCoroutine { continuation ->
            authService.performTokenRequest(tokenRequest) { response, exception ->
                if (response != null) {
                    saveTokens(
                        accessToken = response.accessToken.orEmpty(),
                        refreshToken = response.refreshToken ?: refreshToken,
                        expiresAtMillis = response.accessTokenExpirationTime ?: 0L
                    )
                    _isAuthenticated.value = true
                    continuation.resume(response.accessToken)
                } else {
                    Log.e(TAG, "Token refresh failed", exception)
                    _isAuthenticated.value = false
                    continuation.resume(null)
                }
            }
        }
    }

    private fun isTokenExpired(): Boolean {
        val expiresAt = securePreferences.getString(KEY_EXPIRES_AT)?.toLongOrNull() ?: return true
        // Consider the token expired 60 seconds early to avoid edge-case failures.
        return System.currentTimeMillis() >= (expiresAt - EXPIRY_BUFFER_MS)
    }

    private fun saveTokens(accessToken: String, refreshToken: String, expiresAtMillis: Long) {
        securePreferences.putString(KEY_ACCESS_TOKEN, accessToken)
        securePreferences.putString(KEY_REFRESH_TOKEN, refreshToken)
        securePreferences.putString(KEY_EXPIRES_AT, expiresAtMillis.toString())
    }

    private fun hasStoredTokens(): Boolean {
        return securePreferences.getString(KEY_ACCESS_TOKEN) != null
    }

    // -----------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------

    companion object {
        private const val TAG = "SpotifyAuthManager"

        /** Spotify OAuth endpoints */
        private const val AUTHORIZATION_ENDPOINT = "https://accounts.spotify.com/authorize"
        private const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"

        /** OAuth scopes required by Un-Dios for playback control and library access. */
        private val SCOPES = listOf(
            "user-read-playback-state",
            "user-modify-playback-state",
            "user-read-currently-playing",
            "playlist-read-private",
            "user-library-read",
            "streaming"
        )

        // Secure-prefs keys
        private const val KEY_ACCESS_TOKEN = "spotify_access_token"
        private const val KEY_REFRESH_TOKEN = "spotify_refresh_token"
        private const val KEY_EXPIRES_AT = "spotify_expires_at"

        /** Pre-expire tokens by this much to avoid race conditions. */
        private const val EXPIRY_BUFFER_MS = 60_000L
    }
}
