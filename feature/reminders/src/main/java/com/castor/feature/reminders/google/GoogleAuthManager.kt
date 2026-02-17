package com.castor.feature.reminders.google

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.castor.core.security.SecurePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Manages Google OAuth 2.0 PKCE authentication for Calendar and Tasks APIs.
 *
 * Uses AppAuth library for standards-compliant OAuth flow with PKCE code challenge.
 * Tokens are stored securely via [SecurePreferences] using EncryptedSharedPreferences.
 */
@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePreferences: SecurePreferences
) {
    companion object {
        private const val TAG = "GoogleAuthManager"

        // OAuth endpoints
        private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"

        // Redirect URI — must match AndroidManifest intent-filter
        private const val REDIRECT_URI = "com.castor.app://google-callback"

        // Scopes for Calendar and Tasks access
        private val SCOPES = listOf(
            "https://www.googleapis.com/auth/calendar.readonly",
            "https://www.googleapis.com/auth/calendar.events",
            "https://www.googleapis.com/auth/tasks.readonly",
            "https://www.googleapis.com/auth/tasks"
        )

        // SecurePreferences keys
        private const val KEY_AUTH_STATE = "google_auth_state"
        private const val KEY_ACCESS_TOKEN = "google_access_token"
        private const val KEY_REFRESH_TOKEN = "google_refresh_token"
        private const val KEY_TOKEN_EXPIRY = "google_token_expiry"
        private const val KEY_CLIENT_ID = "google_client_id"
    }

    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse(AUTH_ENDPOINT),
        Uri.parse(TOKEN_ENDPOINT)
    )

    private val _isAuthenticated = MutableStateFlow(false)

    /** Emits true when a valid Google auth session exists. */
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)

    /** Emits the most recent authentication error message, or null when cleared. */
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private var authState: AuthState? = null

    init {
        restoreAuthState()
    }

    /**
     * Sets the Google OAuth client ID at runtime.
     * This should be called during app initialization before any auth flow.
     *
     * @param clientId The OAuth 2.0 client ID from Google Cloud Console
     */
    fun setClientId(clientId: String) {
        securePreferences.putString(KEY_CLIENT_ID, clientId)
    }

    /**
     * Creates an [Intent] to launch the Google OAuth authorization flow.
     *
     * The intent opens the system browser / Chrome Custom Tab with the Google
     * consent screen. The PKCE code verifier is generated automatically by AppAuth.
     *
     * @return An [Intent] to start with startActivityForResult, or null if client ID is not set
     */
    fun createAuthIntent(): Intent? {
        val clientId = securePreferences.getString(KEY_CLIENT_ID)
        if (clientId.isNullOrBlank()) {
            Log.e(TAG, "Client ID not set. Call setClientId() before starting auth flow.")
            _authError.value = "Google OAuth client ID not configured"
            return null
        }

        val authService = AuthorizationService(context)

        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI)
        )
            .setScopes(SCOPES)
            .setCodeVerifier(null) // AppAuth generates PKCE code verifier automatically
            .build()

        return authService.getAuthorizationRequestIntent(authRequest)
    }

    /**
     * Handles the OAuth callback after the user completes (or cancels) the consent screen.
     *
     * Exchanges the authorization code for access + refresh tokens using PKCE.
     *
     * @param intent The result intent from the authorization activity
     * @return true if authentication succeeded, false otherwise
     */
    suspend fun handleAuthResponse(intent: Intent): Boolean {
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)

        if (exception != null) {
            Log.e(TAG, "Authorization failed: ${exception.errorDescription}", exception)
            _authError.value = exception.errorDescription ?: "Authorization failed"
            _isAuthenticated.value = false
            return false
        }

        if (response == null) {
            Log.e(TAG, "Authorization response is null — user may have cancelled")
            _authError.value = "Authorization was cancelled"
            _isAuthenticated.value = false
            return false
        }

        // Exchange authorization code for tokens
        return try {
            val tokenResponse = exchangeAuthorizationCode(response)
            handleTokenResponse(tokenResponse)
            _authError.value = null
            true
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange failed", e)
            _authError.value = "Token exchange failed: ${e.message}"
            _isAuthenticated.value = false
            false
        }
    }

    /**
     * Returns a valid access token, refreshing if necessary.
     *
     * @return A Bearer-ready access token string, or null if not authenticated
     */
    suspend fun getAccessToken(): String? {
        val currentToken = securePreferences.getString(KEY_ACCESS_TOKEN)
        val expiryStr = securePreferences.getString(KEY_TOKEN_EXPIRY)
        val expiry = expiryStr?.toLongOrNull() ?: 0L

        // If token exists and is not expired (with 5 min buffer), return it
        if (currentToken != null && System.currentTimeMillis() < expiry - 300_000) {
            return currentToken
        }

        // Attempt refresh
        val refreshToken = securePreferences.getString(KEY_REFRESH_TOKEN)
        if (refreshToken == null) {
            Log.w(TAG, "No refresh token available — user must re-authenticate")
            _isAuthenticated.value = false
            return null
        }

        return try {
            refreshAccessToken(refreshToken)
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed", e)
            _isAuthenticated.value = false
            null
        }
    }

    /**
     * Returns the access token formatted as a Bearer authorization header value.
     *
     * @return "Bearer <token>" or null if not authenticated
     */
    suspend fun getAuthorizationHeader(): String? {
        val token = getAccessToken() ?: return null
        return "Bearer $token"
    }

    /**
     * Clears all stored auth state and tokens. Signs the user out of Google integration.
     */
    fun logout() {
        securePreferences.remove(KEY_AUTH_STATE)
        securePreferences.remove(KEY_ACCESS_TOKEN)
        securePreferences.remove(KEY_REFRESH_TOKEN)
        securePreferences.remove(KEY_TOKEN_EXPIRY)
        authState = null
        _isAuthenticated.value = false
        _authError.value = null
        Log.i(TAG, "Google auth session cleared")
    }

    /**
     * Clears the current error state.
     */
    fun clearError() {
        _authError.value = null
    }

    // ---- Private helpers ----

    private fun restoreAuthState() {
        val stateJson = securePreferences.getString(KEY_AUTH_STATE)
        if (stateJson != null) {
            try {
                authState = AuthState.jsonDeserialize(stateJson)
                val hasToken = securePreferences.getString(KEY_ACCESS_TOKEN) != null
                val hasRefresh = securePreferences.getString(KEY_REFRESH_TOKEN) != null
                _isAuthenticated.value = hasToken && hasRefresh
                Log.d(TAG, "Restored auth state: authenticated=${_isAuthenticated.value}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore auth state", e)
                logout()
            }
        }
    }

    private fun persistAuthState() {
        authState?.let { state ->
            securePreferences.putString(KEY_AUTH_STATE, state.jsonSerializeString())
        }
    }

    private suspend fun exchangeAuthorizationCode(
        authResponse: AuthorizationResponse
    ): TokenResponse = suspendCoroutine { continuation ->
        val authService = AuthorizationService(context)
        val tokenRequest = authResponse.createTokenExchangeRequest()

        authService.performTokenRequest(tokenRequest) { response, exception ->
            authService.dispose()
            if (exception != null) {
                continuation.resumeWithException(
                    RuntimeException("Token exchange failed: ${exception.errorDescription}", exception)
                )
            } else if (response != null) {
                continuation.resume(response)
            } else {
                continuation.resumeWithException(
                    RuntimeException("Token exchange returned null response")
                )
            }
        }
    }

    private fun handleTokenResponse(tokenResponse: TokenResponse) {
        // Update AuthState
        authState = AuthState(serviceConfig).apply {
            update(tokenResponse, null)
        }

        // Persist tokens securely
        tokenResponse.accessToken?.let { token ->
            securePreferences.putString(KEY_ACCESS_TOKEN, token)
        }
        tokenResponse.refreshToken?.let { token ->
            securePreferences.putString(KEY_REFRESH_TOKEN, token)
        }
        tokenResponse.accessTokenExpirationTime?.let { expiry ->
            securePreferences.putString(KEY_TOKEN_EXPIRY, expiry.toString())
        }

        persistAuthState()
        _isAuthenticated.value = true
        Log.i(TAG, "Google authentication successful")
    }

    private suspend fun refreshAccessToken(refreshToken: String): String? =
        suspendCoroutine { continuation ->
            val clientId = securePreferences.getString(KEY_CLIENT_ID)
            if (clientId == null) {
                continuation.resume(null)
                return@suspendCoroutine
            }

            val authService = AuthorizationService(context)
            val tokenRequest = TokenRequest.Builder(serviceConfig, clientId)
                .setGrantType("refresh_token")
                .setRefreshToken(refreshToken)
                .build()

            authService.performTokenRequest(tokenRequest) { response, exception ->
                authService.dispose()
                if (exception != null) {
                    Log.e(TAG, "Token refresh failed: ${exception.errorDescription}", exception)
                    continuation.resume(null)
                } else if (response != null) {
                    handleTokenResponse(response)
                    continuation.resume(response.accessToken)
                } else {
                    continuation.resume(null)
                }
            }
        }
}
