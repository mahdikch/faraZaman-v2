package Ir.co.tfs.farazaman.auth

import android.content.Context
import android.net.Uri
import android.preference.PreferenceManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import Ir.co.tfs.farazaman.util.TokenManager
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
class OidcAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager,
) {
    @Volatile
    private var serviceConfig: AuthorizationServiceConfiguration? = null

    @Volatile
    private var pendingAuthorizationRequest: AuthorizationRequest? = null

    fun getClientId(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(PREF_CLIENT_ID, DEFAULT_CLIENT_ID) ?: DEFAULT_CLIENT_ID
    }

    fun getScope(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val savedScope = prefs.getString(PREF_OIDC_SCOPE, null)
        return savedScope?.takeIf { it.isNotBlank() } ?: DEFAULT_SCOPES
    }

    fun saveClientId(clientId: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_CLIENT_ID, clientId)
            .apply()
    }

    fun saveScope(scope: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_OIDC_SCOPE, scope)
            .apply()
    }

    suspend fun ensureConfiguration(): AuthorizationServiceConfiguration {
        serviceConfig?.let { return it }
        return suspendCoroutine { continuation ->
            AuthorizationServiceConfiguration.fetchFromIssuer(ISSUER_URI) { config, ex ->
                if (ex != null) {
                    Log.e(TAG, "Failed to fetch OIDC configuration", ex)
                    continuation.resumeWith(Result.failure(ex))
                    return@fetchFromIssuer
                }
                if (config == null) {
                    continuation.resumeWith(
                        Result.failure(IllegalStateException("OIDC configuration is null")),
                    )
                    return@fetchFromIssuer
                }
                serviceConfig = config
                continuation.resume(config)
            }
        }
    }

    fun createAuthorizationRequest(
        config: AuthorizationServiceConfiguration,
        forceLogin: Boolean = false,
    ): AuthorizationRequest {
        val builder = AuthorizationRequest.Builder(
            config,
            getClientId(),
            ResponseTypeValues.CODE,
            REDIRECT_URI,
        ).setScope(getScope())

        if (forceLogin) {
            builder.setPrompt(AuthorizationRequest.Prompt.LOGIN)
        }

        return builder.build().also { pendingAuthorizationRequest = it }
    }

    fun clearPendingAuthorizationRequest() {
        pendingAuthorizationRequest = null
    }

    fun peekPendingAuthorizationRequest(): AuthorizationRequest? = pendingAuthorizationRequest

    suspend fun exchangeAuthorizationResponse(response: AuthorizationResponse): Result<Unit> {
        return suspendCoroutine { continuation ->
            val authService = AuthorizationService(context)
            val tokenRequest = response.createTokenExchangeRequest()
            authService.performTokenRequest(tokenRequest) { tokenResponse, ex ->
                authService.dispose()
                when {
                    ex != null -> continuation.resume(Result.failure(ex))
                    tokenResponse == null -> continuation.resume(
                        Result.failure(IllegalStateException("Empty token response")),
                    )
                    else -> {
                        saveTokenResponse(tokenResponse)
                        continuation.resume(Result.success(Unit))
                    }
                }
            }
        }
    }

    suspend fun refreshAccessToken(): Boolean {
        val refreshToken = tokenManager.getRefreshToken()
        if (refreshToken.isNullOrEmpty()) {
            Log.e(TAG, "No refresh token available for OIDC refresh")
            return false
        }

        return try {
            val config = ensureConfiguration()
            suspendCoroutine { continuation ->
                val authService = AuthorizationService(context)
                val request = TokenRequest.Builder(config, getClientId())
                    .setGrantType(GrantTypeValues.REFRESH_TOKEN)
                    .setRefreshToken(refreshToken)
                    .setScope(getScope())
                    .build()

                authService.performTokenRequest(request) { tokenResponse, ex ->
                    authService.dispose()
                    when {
                        ex != null -> {
                            Log.e(TAG, "OIDC refresh failed", ex)
                            continuation.resume(false)
                        }
                        tokenResponse == null -> continuation.resume(false)
                        else -> {
                            saveTokenResponse(tokenResponse)
                            continuation.resume(true)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OIDC refresh error", e)
            false
        }
    }

    private fun saveTokenResponse(tokenResponse: TokenResponse) {
        val accessToken = tokenResponse.accessToken
        if (accessToken.isNullOrEmpty()) {
            throw IllegalStateException("Access token missing in OIDC response")
        }

        val refreshToken = tokenResponse.refreshToken ?: tokenManager.getRefreshToken().orEmpty()
        val expiresIn = tokenResponse.accessTokenExpirationTime?.let { expiryMs ->
            ((expiryMs - System.currentTimeMillis()) / 1000L).toInt()
        }?.coerceAtLeast(60) ?: DEFAULT_EXPIRES_IN_SECONDS

        tokenManager.saveTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = tokenResponse.tokenType ?: "Bearer",
            expiresIn = expiresIn,
        )
        tokenManager.saveIdToken(tokenResponse.idToken)
    }

    companion object {
        private const val TAG = "OidcAuthManager"
        private const val PREF_CLIENT_ID = "OIDC_CLIENT_ID"
        private const val PREF_OIDC_SCOPE = "OIDC_SCOPE"
        const val DEFAULT_CLIENT_ID = "TFS_MunicipalServices_Mobile_Application"
        const val DEFAULT_SCOPES = "openid profile offline_access TFS_MunicipalServices_Api"
        private const val DEFAULT_EXPIRES_IN_SECONDS = 3600

        val ISSUER_URI: Uri = Uri.parse("https://identity.tfs.co.ir")
        val REDIRECT_URI: Uri = Uri.parse("ir.co.tfs.farazaman://signin-oidc")
    }
}
