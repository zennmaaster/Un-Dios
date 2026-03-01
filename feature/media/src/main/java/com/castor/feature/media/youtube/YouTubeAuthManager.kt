package com.castor.feature.media.youtube

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.castor.core.security.SecurePreferences
import com.castor.feature.media.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages Google OAuth 2.0 authentication for YouTube API access.
 *
 * Uses the AppAuth library with PKCE flow. Tokens are persisted in
 * [SecurePreferences] so the user does not need to re-authenticate on
 * every app launch.
 */
@Singleton
class YouTubeAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePreferences: SecurePreferences
) {
    private val configuredClientId: String = BuildConfig.YOUTUBE_CLIENT_ID.trim()
    private val configuredRedirectUriRaw: String = BuildConfig.YOUTUBE_REDIRECT_URI.trim()
    private val configuredRedirectUri: Uri = Uri.parse(configuredRedirectUriRaw)

    companion object {
        // Google OAuth 2.0 endpoints
        private val AUTHORIZATION_ENDPOINT =
            Uri.parse("https://accounts.google.com/o/oauth2/v2/auth")
        private val TOKEN_ENDPOINT =
            Uri.parse("https://oauth2.googleapis.com/token")

        // Scopes required for YouTube API
        private const val SCOPE_YOUTUBE_READONLY =
            "https://www.googleapis.com/auth/youtube.readonly"
        private const val SCOPE_YOUTUBE =
            "https://www.googleapis.com/auth/youtube"

        // SecurePreferences keys
        private const val KEY_AUTH_STATE = "youtube_auth_state"
        private const val KEY_ACCESS_TOKEN = "youtube_access_token"
        private const val KEY_REFRESH_TOKEN = "youtube_refresh_token"
    }

    private val serviceConfig = AuthorizationServiceConfiguration(
        AUTHORIZATION_ENDPOINT,
        TOKEN_ENDPOINT
    )

    private val authService: AuthorizationService by lazy {
        AuthorizationService(context)
    }

    private var authState: AuthState = restoreAuthState() ?: AuthState(serviceConfig)

    private val _isAuthenticated = MutableStateFlow(authState.isAuthorized)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    // -------------------------------------------------------------------------
    // Authorization flow
    // -------------------------------------------------------------------------

    fun isConfigured(): Boolean {
        return configuredClientId.isNotBlank() &&
            configuredRedirectUriRaw.isNotBlank() &&
            !configuredClientId.startsWith("YOUR_")
    }

    /**
     * Build the authorization [Intent] to launch the Google sign-in page.
     * The caller should launch this via an ActivityResultLauncher and forward
     * the result to [handleAuthorizationResponse].
     */
    fun buildAuthIntent(): Intent? {
        if (!isConfigured()) return null

        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            configuredClientId,
            ResponseTypeValues.CODE,
            configuredRedirectUri
        )
            .setScope("$SCOPE_YOUTUBE_READONLY $SCOPE_YOUTUBE")
            .setCodeVerifier(null) // AppAuth generates a PKCE verifier automatically
            .build()

        return authService.getAuthorizationRequestIntent(authRequest)
    }

    /**
     * Handle the result returned from the authorization activity.
     *
     * Exchanges the authorization code for tokens using PKCE and persists them.
     *
     * @param data The [Intent] data from the activity result.
     * @return true if authentication succeeded, false otherwise.
     */
    suspend fun handleAuthorizationResponse(data: Intent): Boolean {
        if (!isConfigured()) {
            _isAuthenticated.value = false
            return false
        }

        val response = AuthorizationResponse.fromIntent(data)
        val exception = AuthorizationException.fromIntent(data)

        authState.update(response, exception)

        if (response == null) {
            _isAuthenticated.value = false
            return false
        }

        return try {
            val tokenResponse = exchangeAuthorizationCode(response)
            authState.update(tokenResponse, null)
            persistAuthState()
            _isAuthenticated.value = true
            true
        } catch (e: Exception) {
            _isAuthenticated.value = false
            false
        }
    }

    /**
     * Exchange the authorization code for access and refresh tokens.
     */
    private suspend fun exchangeAuthorizationCode(
        authResponse: AuthorizationResponse
    ): TokenResponse = suspendCancellableCoroutine { continuation ->
        val tokenRequest = authResponse.createTokenExchangeRequest()
        authService.performTokenRequest(tokenRequest) { response, exception ->
            if (response != null) {
                continuation.resume(response)
            } else {
                continuation.resumeWithException(
                    exception ?: RuntimeException("Token exchange failed with no details")
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Token management
    // -------------------------------------------------------------------------

    /**
     * Return a valid access token, refreshing if necessary.
     * Returns null if the user is not authenticated.
     */
    suspend fun getValidAccessToken(): String? {
        if (!isConfigured()) return null
        if (!authState.isAuthorized) return null

        // If the current access token needs refreshing, do so
        if (authState.needsTokenRefresh) {
            return refreshAccessToken()
        }

        return authState.accessToken
    }

    /**
     * Refresh the access token using the stored refresh token.
     */
    private suspend fun refreshAccessToken(): String? = suspendCancellableCoroutine { continuation ->
        authState.performActionWithFreshTokens(authService) { accessToken, _, exception ->
            if (exception != null) {
                _isAuthenticated.value = false
                continuation.resume(null)
            } else {
                persistAuthState()
                _isAuthenticated.value = accessToken != null
                continuation.resume(accessToken)
            }
        }
    }

    /**
     * Disconnect the user by clearing all stored tokens and resetting auth state.
     */
    fun disconnect() {
        authState = AuthState(serviceConfig)
        securePreferences.remove(KEY_AUTH_STATE)
        securePreferences.removeToken(KEY_ACCESS_TOKEN)
        securePreferences.removeToken(KEY_REFRESH_TOKEN)
        _isAuthenticated.value = false
    }

    // -------------------------------------------------------------------------
    // Persistence helpers
    // -------------------------------------------------------------------------

    private fun persistAuthState() {
        securePreferences.putString(KEY_AUTH_STATE, authState.jsonSerializeString())
        authState.accessToken?.let { securePreferences.saveToken(KEY_ACCESS_TOKEN, it) }
        authState.refreshToken?.let { securePreferences.saveToken(KEY_REFRESH_TOKEN, it) }
    }

    private fun restoreAuthState(): AuthState? {
        val json = securePreferences.getString(KEY_AUTH_STATE) ?: return null
        return try {
            AuthState.jsonDeserialize(json)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Release resources held by the [AuthorizationService].
     * Should be called when the manager is no longer needed.
     */
    fun dispose() {
        authService.dispose()
    }
}
