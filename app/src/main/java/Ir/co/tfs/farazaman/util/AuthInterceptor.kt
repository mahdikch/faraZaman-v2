package Ir.co.tfs.farazaman.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import Ir.co.tfs.farazaman.auth.OidcAuthManager
import Ir.co.tfs.farazaman.domain.repository.AuthRepository
import Ir.co.tfs.farazaman.util.AuthStateManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
    private val context: Context,
    private val authStateManager: AuthStateManager,
    private val authRepository: AuthRepository,
) : Interceptor {

    companion object {
        private const val TAG = "AuthInterceptor"
        private const val AUTHORIZATION_HEADER = "Authorization"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url.toString()
        
        Log.d(TAG, "=== INTERCEPTING REQUEST ===")
        Log.d(TAG, "URL: $url")
        Log.d(TAG, "Method: ${originalRequest.method}")
        Log.d(TAG, "Current access token: ${tokenManager.getAccessToken()?.take(20)}...")
        Log.d(TAG, "Current refresh token: ${tokenManager.getRefreshToken()?.take(20)}...")
        Log.d(TAG, "Token expired: ${tokenManager.isTokenExpired()}")
        Log.d(TAG, "Has refresh token: ${tokenManager.hasRefreshToken()}")
        
        // Add detailed expiry analysis
        val expiresAt = tokenManager.getTokenExpiresAt()
        val currentTime = System.currentTimeMillis()
        val timeRemaining = expiresAt - currentTime
        Log.d(TAG, "Token expires at: $expiresAt")
        Log.d(TAG, "Current time: $currentTime") 
        Log.d(TAG, "Time remaining: ${timeRemaining}ms (${timeRemaining/1000}s)")
        Log.d(TAG, "Time since login: ${currentTime - 1756488569871}ms")
        
        // Skip token logic for login/refresh endpoints
        if (isAuthEndpoint(originalRequest)) {
            Log.d(TAG, "Skipping auth for endpoint: $url")
            return chain.proceed(originalRequest)
        }

        // Check if token is expired before making the request
        if (tokenManager.isTokenExpired() && tokenManager.hasRefreshToken()) {
            Log.d(TAG, "Token is expired, refreshing before request")
            synchronized(this) {
                // Double-check after acquiring lock
                if (tokenManager.isTokenExpired() && tokenManager.hasRefreshToken()) {
                    val refreshResult = runBlocking {
                        refreshTokenSync()
                    }
                    if (!refreshResult) {
                        Log.e(TAG, "Preemptive token refresh failed")
                        tokenManager.clearTokens()
                        authStateManager.notifyAuthStateChanged(false, AuthStateManager.REASON_REFRESH_FAILED)
                    }
                }
            }
        }

        // Add authorization header if available
        val requestWithAuth = addAuthHeader(originalRequest)
        Log.d(TAG, "Making request with auth header: ${requestWithAuth.header(AUTHORIZATION_HEADER)?.take(20)}...")
        
        var response = chain.proceed(requestWithAuth)
        Log.d(TAG, "Received response code: ${response.code}")

        // If we get 401 Unauthorized, try to refresh the token
        if (response.code == 401 && tokenManager.hasRefreshToken()) {
            Log.w(TAG, "=== 401 UNAUTHORIZED DETECTED ===")
            Log.w(TAG, "Response message: ${response.message}")
            Log.w(TAG, "Request URL: $url")
            Log.w(TAG, "Request method: ${originalRequest.method}")
            Log.w(TAG, "Auth header used: ${requestWithAuth.header(AUTHORIZATION_HEADER)?.take(30)}...")
            Log.w(TAG, "Attempting token refresh...")
            
            val errorBody = response.peekBody(1024).string()
            if (errorBody.isNotEmpty()) {
                Log.w(TAG, "401 error body: $errorBody")
            }
            
            synchronized(this) {
                // Double-check if token was already refreshed by another thread
                val newToken = tokenManager.getAccessToken()
                if (newToken != null && newToken != getTokenFromRequest(requestWithAuth)) {
                    Log.d(TAG, "Token was already refreshed by another thread")
                    response.close()
                    val newRequest = addAuthHeader(originalRequest)
                    return chain.proceed(newRequest)
                }

                // Attempt token refresh
                val refreshResult = runBlocking {
                    refreshTokenSync()
                }

                if (refreshResult) {
                    Log.d(TAG, "Token refresh successful, retrying original request")
                    response.close()
                    val newRequest = addAuthHeader(originalRequest)
                    Log.d(TAG, "Retrying with new auth header: ${newRequest.header(AUTHORIZATION_HEADER)?.take(30)}...")
                    val retryResponse = chain.proceed(newRequest)
                    Log.d(TAG, "Retry response code: ${retryResponse.code}")
                    if (retryResponse.code == 401) {
                        Log.e(TAG, "Still getting 401 after token refresh - refresh token might be invalid")
                        val retryErrorBody = retryResponse.peekBody(1024).string()
                        Log.e(TAG, "Retry 401 error body: $retryErrorBody")
                        tokenManager.clearTokens()
                        authStateManager.notifyAuthStateChanged(false, AuthStateManager.REASON_REFRESH_FAILED)
                    }
                    return retryResponse
                } else {
                    Log.e(TAG, "Token refresh failed, clearing tokens")
                    tokenManager.clearTokens()
                    authStateManager.notifyAuthStateChanged(false, AuthStateManager.REASON_REFRESH_FAILED)
                    // You might want to redirect to login screen here
                    // For now, just return the original 401 response
                }
            }
        }

        return response
    }

    private fun isAuthEndpoint(request: Request): Boolean {
        val url = request.url.toString()
        return url.contains("identity.tfs.co.ir", ignoreCase = true) ||
            (url.contains("/token") &&
                request.body?.contentType()?.toString()?.contains("application/x-www-form-urlencoded") == true)
    }

    private fun addAuthHeader(request: Request): Request {
        val authHeader = tokenManager.getAuthorizationHeader()
        return if (authHeader != null) {
            request.newBuilder()
                .header(AUTHORIZATION_HEADER, authHeader)
                .build()
        } else {
            request
        }
    }

    private fun getTokenFromRequest(request: Request): String? {
        val authHeader = request.header(AUTHORIZATION_HEADER)
        return authHeader?.removePrefix("Bearer ")?.trim()
    }

    private suspend fun refreshTokenSync(): Boolean {
        return try {
            tokenManager.withRefreshLock {
                performTokenRefresh()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed: ${e.message}")
            false
        }
    }

    private suspend fun performTokenRefresh() {
        val refreshToken = tokenManager.getRefreshToken()
        if (refreshToken.isNullOrEmpty()) {
            Log.e(TAG, "No refresh token available")
            throw IllegalStateException("No refresh token available")
        }

        Log.d(TAG, "Performing token refresh...")
        authRepository.refreshToken()
        Log.d(TAG, "Token refresh successful")
    }
}
