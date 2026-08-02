package Ir.co.tfs.farazaman.data.repository

import android.content.Context
import android.preference.PreferenceManager
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import Ir.co.tfs.farazaman.auth.OidcAuthManager
import Ir.co.tfs.farazaman.data.model.res.LoginResponse
import Ir.co.tfs.farazaman.domain.repository.AuthRepository
import Ir.co.tfs.farazaman.presentation.login.LoginModeHelper
import Ir.co.tfs.farazaman.util.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager,
    private val oidcAuthManager: OidcAuthManager,
) : AuthRepository {

    override suspend fun login(username: String, password: String): LoginResponse {
        Log.d(TAG, "Password login request for username: $username")

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val baseUrl = normalizeBaseUrl(prefs.getString("BASE_URL", "https://app.tfs.co.ir/")!!)
        val scope = LoginModeHelper.getOidcScope(prefs)
        val tokenResponse = requestPasswordToken(baseUrl, username, password, scope)

        tokenManager.saveTokens(
            accessToken = tokenResponse.accessToken,
            refreshToken = tokenResponse.refreshToken,
            tokenType = tokenResponse.tokenType,
            expiresIn = tokenResponse.expiresIn,
        )

        return LoginResponse(
            access_token = tokenResponse.accessToken,
            token_type = tokenResponse.tokenType,
            expires_in = tokenResponse.expiresIn,
            refresh_token = tokenResponse.refreshToken,
        )
    }

    override suspend fun refreshToken(): LoginResponse {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return if (LoginModeHelper.isWebLogin(prefs)) {
            refreshOidcToken()
        } else {
            refreshPasswordGrantToken()
        }
    }

    override fun logout() {
        Log.d(TAG, "Logout called - clearing all tokens")
        tokenManager.clearTokens()
    }

    private suspend fun refreshOidcToken(): LoginResponse {
        Log.d(TAG, "OIDC refresh token request initiated")
        val refreshed = oidcAuthManager.refreshAccessToken()
        if (!refreshed) {
            tokenManager.clearTokens()
            throw Exception("Token refresh failed")
        }

        val accessToken = tokenManager.getAccessToken()
            ?: throw IllegalStateException("Access token missing after refresh")
        return LoginResponse(
            access_token = accessToken,
            token_type = tokenManager.getTokenType(),
            expires_in = (tokenManager.getTimeUntilExpiry() / 1000L).toInt().coerceAtLeast(60),
            refresh_token = tokenManager.getRefreshToken().orEmpty(),
        )
    }

    private suspend fun refreshPasswordGrantToken(): LoginResponse {
        Log.d(TAG, "Password-grant refresh token request initiated")

        val refreshToken = tokenManager.getRefreshToken()
        if (refreshToken.isNullOrEmpty()) {
            throw IllegalStateException("No refresh token available")
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val baseUrl = normalizeBaseUrl(prefs.getString("BASE_URL", "https://app.tfs.co.ir/")!!)
        val tokenResponse = requestRefreshToken(baseUrl, refreshToken)

        tokenManager.saveTokens(
            accessToken = tokenResponse.accessToken,
            refreshToken = tokenResponse.refreshToken,
            tokenType = tokenResponse.tokenType,
            expiresIn = tokenResponse.expiresIn,
        )

        return LoginResponse(
            access_token = tokenResponse.accessToken,
            token_type = tokenResponse.tokenType,
            expires_in = tokenResponse.expiresIn,
            refresh_token = tokenResponse.refreshToken,
        )
    }

    private suspend fun requestPasswordToken(
        baseUrl: String,
        username: String,
        password: String,
        scope: String,
    ): ParsedTokenResponse = withContext(Dispatchers.IO) {
        requestToken(
            baseUrl = baseUrl,
            modernFormBody = buildModernPasswordForm(username, password, scope),
            legacyFormBody = FormBody.Builder()
                .add("grant_type", "password")
                .add("username", username)
                .add("password", password)
                .build(),
        )
    }

    private fun buildModernPasswordForm(
        username: String,
        password: String,
        scope: String,
    ): FormBody {
        val formBuilder = FormBody.Builder()
            .add("grant_type", "password")
            .add("username", username)
            .add("password", password)
            .add("client_id", LEGACY_CLIENT_ID)
        if (scope.isNotBlank()) {
            formBuilder.add("scope", scope)
        }
        return formBuilder.build()
    }

    private suspend fun requestRefreshToken(
        baseUrl: String,
        refreshToken: String,
    ): ParsedTokenResponse = withContext(Dispatchers.IO) {
        try {
            requestToken(
                baseUrl = baseUrl,
                modernFormBody = FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .add("client_id", LEGACY_CLIENT_ID)
                    .build(),
                legacyFormBody = FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .build(),
            )
        } catch (e: Exception) {
            tokenManager.clearTokens()
            throw e
        }
    }

    private fun requestToken(
        baseUrl: String,
        modernFormBody: FormBody,
        legacyFormBody: FormBody,
    ): ParsedTokenResponse {
        val client = createBasicOkHttpClient()
        val authHeader = buildBasicAuthHeader()
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        var lastError: Exception? = null

        val attempts = listOf(
            TokenAttempt("token", legacyFormBody),
            TokenAttempt("api/token", modernFormBody),
        )

        for (attempt in attempts) {
            val url = normalizedBaseUrl + attempt.path
            Log.d(TAG, "Trying token endpoint: $url")

            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .post(attempt.formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                Log.d(
                    TAG,
                    "Token response from $url: HTTP ${response.code}, prefix=${bodyString.take(120)}",
                )

                if (bodyString.isBlank()) {
                    lastError = Exception("پاسخ خالی از سرور ورود")
                    return@use
                }

                if (!bodyString.trimStart().startsWith("{")) {
                    lastError = Exception(
                        "سرور $normalizedBaseUrl پاسخ نامعتبر برگرداند. endpoint ورود در دسترس نیست.",
                    )
                    return@use
                }

                val json = JSONObject(bodyString)
                if (!response.isSuccessful) {
                    val error = parseTokenError(json, response.code)
                    if (json.has("error") || json.has("error_description")) {
                        throw error
                    }
                    lastError = error
                    return@use
                }

                return parseTokenResponse(json)
            }
        }

        throw lastError ?: Exception("خطا در اتصال به سرور ورود")
    }

    private fun parseTokenResponse(json: JSONObject): ParsedTokenResponse {
        val accessToken = json.optString("access_token")
        if (accessToken.isBlank()) {
            throw IllegalStateException("توکن دسترسی در پاسخ سرور یافت نشد")
        }

        return ParsedTokenResponse(
            accessToken = accessToken,
            refreshToken = json.optString("refresh_token"),
            tokenType = json.optString("token_type", "Bearer"),
            expiresIn = json.optInt("expires_in", 3600),
        )
    }

    private fun parseTokenError(json: JSONObject, httpCode: Int): Exception {
        val description = json.optString("error_description")
            .ifBlank { json.optString("error") }
        if (description.isNotBlank()) {
            return Exception(description)
        }
        return Exception("خطا در ورود: $httpCode")
    }

    private fun createBasicOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        return if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    }

    private fun buildBasicAuthHeader(): String {
        val credentials = "$LEGACY_CLIENT_ID:$LEGACY_CLIENT_SECRET"
        return "Basic ${Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)}"
    }

    private data class ParsedTokenResponse(
        val accessToken: String,
        val refreshToken: String,
        val tokenType: String,
        val expiresIn: Int,
    )

    private data class TokenAttempt(
        val path: String,
        val formBody: FormBody,
    )

    companion object {
        private const val TAG = "AuthRepository"
        private const val LEGACY_CLIENT_ID = "b1aa497a-8778-4351-8c91-9719e2b0362f"
        private const val LEGACY_CLIENT_SECRET = "i4uCgAvIAw7BuAn7dDxDW0jHznYaqIhA"
    }
}
