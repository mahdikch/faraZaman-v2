package Ir.co.tfs.farazaman.util

import android.content.Context
import android.preference.PreferenceManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import Ir.co.tfs.farazaman.service.remote.AuthService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RefreshTokenTestHelper @Inject constructor(
    private val tokenManager: TokenManager,
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "RefreshTokenTest"
    }
    
    /**
     * Test the current token status and log detailed information
     */
    fun logTokenStatus() {
        Log.d(TAG, "=== TOKEN STATUS DEBUG ===")
        
        val accessToken = tokenManager.getAccessToken()
        val refreshToken = tokenManager.getRefreshToken()
        val isLoggedIn = tokenManager.isLoggedIn()
        val isExpired = tokenManager.isTokenExpired()
        val hasRefresh = tokenManager.hasRefreshToken()
        val timeUntilExpiry = tokenManager.getTimeUntilExpiry()
        
        Log.d(TAG, "Access Token: ${accessToken?.take(20)}... (length: ${accessToken?.length ?: 0})")
        Log.d(TAG, "Refresh Token: ${refreshToken?.take(20)}... (length: ${refreshToken?.length ?: 0})")
        Log.d(TAG, "Is Logged In: $isLoggedIn")
        Log.d(TAG, "Is Token Expired: $isExpired")
        Log.d(TAG, "Has Refresh Token: $hasRefresh")
        Log.d(TAG, "Time Until Expiry: ${timeUntilExpiry / 1000} seconds")
        
        if (timeUntilExpiry > 0) {
            Log.d(TAG, "Time Until Expiry (minutes): ${timeUntilExpiry / 60000}")
        }
        
        Log.d(TAG, "=== END TOKEN STATUS ===")
    }
    
    /**
     * Force clear tokens for testing
     */
    fun clearTokensForTesting() {
        Log.d(TAG, "Clearing tokens for testing...")
        tokenManager.clearTokens()
        Log.d(TAG, "Tokens cleared")
    }
    
    /**
     * Check if the AuthInterceptor is working by examining the current setup
     */
    fun checkInterceptorSetup() {
        Log.d(TAG, "=== INTERCEPTOR SETUP CHECK ===")
        
        // Check if tokens exist
        val hasTokens = tokenManager.isLoggedIn()
        Log.d(TAG, "Has valid tokens: $hasTokens")
        
        if (hasTokens) {
            val authHeader = tokenManager.getAuthorizationHeader()
            Log.d(TAG, "Authorization header: ${authHeader?.take(30)}...")
        }
        
        Log.d(TAG, "=== END INTERCEPTOR CHECK ===")
    }
    
    /**
     * Test refresh token manually
     */
    suspend fun testRefreshToken(): TestResult {
        return try {
            Log.d(TAG, "=== STARTING REFRESH TOKEN TEST ===")
            
            // Check if refresh token exists
            val refreshToken = tokenManager.getRefreshToken()
            if (refreshToken.isNullOrEmpty()) {
                Log.e(TAG, "No refresh token available for testing")
                return TestResult.Error("No refresh token available")
            }
            
            Log.d(TAG, "Refresh token exists: ${refreshToken.take(20)}...")
            Log.d(TAG, "Current access token: ${tokenManager.getAccessToken()?.take(20)}...")
            Log.d(TAG, "Token expired: ${tokenManager.isTokenExpired()}")
            
            // Get base URL
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val baseUrl = prefs.getString("BASE_URL", "https://app.tfs.co.ir/") ?: "https://app.tfs.co.ir/"
            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            
            Log.d(TAG, "Using base URL: $normalizedBaseUrl")
            
            // Create basic retrofit instance without interceptors
            val basicClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedBaseUrl)
                .client(basicClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            
            val authService = retrofit.create(AuthService::class.java)
            
            // Test refresh
            val clientId = "b1aa497a-8778-4351-8c91-9719e2b0362f"
            val clientSecret = "i4uCgAvIAw7BuAn7dDxDW0jHznYaqIhA"
            val authHeader = "Basic " + android.util.Base64.encodeToString(
                "$clientId:$clientSecret".toByteArray(), android.util.Base64.NO_WRAP
            )
            
            Log.d(TAG, "Auth header: ${authHeader.take(50)}...")
            Log.d(TAG, "Making refresh request to: ${normalizedBaseUrl}token")
            val response = authService.refreshToken(authHeader, refreshToken)
            
            Log.d(TAG, "=== REFRESH RESPONSE ===")
            Log.d(TAG, "Response code: ${response.code()}")
            Log.d(TAG, "Response successful: ${response.isSuccessful}")
            Log.d(TAG, "Response message: ${response.message()}")
            
            if (response.isSuccessful) {
                val tokenResponse = response.body()
                if (tokenResponse != null) {
                    Log.d(TAG, "✅ Refresh successful!")
                    Log.d(TAG, "New access token: ${tokenResponse.access_token.take(20)}...")
                    Log.d(TAG, "New refresh token: ${tokenResponse.refresh_token.take(20)}...")
                    Log.d(TAG, "Token type: ${tokenResponse.token_type}")
                    Log.d(TAG, "Expires in: ${tokenResponse.expires_in} seconds")
                    
                    // Save new tokens
                    tokenManager.saveTokens(
                        accessToken = tokenResponse.access_token,
                        refreshToken = tokenResponse.refresh_token,
                        tokenType = tokenResponse.token_type,
                        expiresIn = tokenResponse.expires_in
                    )
                    
                    Log.d(TAG, "New tokens saved successfully")
                    TestResult.Success("Refresh token test successful - new tokens saved")
                } else {
                    Log.e(TAG, "❌ Response body is null")
                    TestResult.Error("Response body is null")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "❌ Refresh failed: HTTP ${response.code()}")
                Log.e(TAG, "Error body: $errorBody")
                
                if (response.code() == 400) {
                    Log.e(TAG, "400 Bad Request - This usually means:")
                    Log.e(TAG, "  1. Refresh token is invalid or expired")
                    Log.e(TAG, "  2. Client credentials are wrong")
                    Log.e(TAG, "  3. Request format is incorrect")
                } else if (response.code() == 401) {
                    Log.e(TAG, "401 Unauthorized - Refresh token is invalid or expired")
                }
                
                TestResult.Error("HTTP ${response.code()}: $errorBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during refresh test: ${e.message}", e)
            TestResult.Error("Exception: ${e.message}")
        }
    }
    
    /**
     * Force expire the current token and test automatic refresh
     */
    fun testAutomaticRefresh(): String {
        Log.d(TAG, "=== TESTING AUTOMATIC REFRESH ===")
        
        if (!tokenManager.hasRefreshToken()) {
            return "❌ No refresh token available for testing"
        }
        
        // Force token to appear expired by setting expiry to past
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putLong("TOKEN_EXPIRES_AT", System.currentTimeMillis() - 60000) // 1 minute ago
            .apply()
        
        Log.d(TAG, "✅ Token marked as expired for testing")
        Log.d(TAG, "Next API call should trigger automatic refresh via AuthInterceptor")
        Log.d(TAG, "Token expired: ${tokenManager.isTokenExpired()}")
        
        return "✅ Token marked as expired. Next API call will test automatic refresh."
    }
    
    /**
     * Test result sealed class
     */
    sealed class TestResult {
        data class Success(val message: String) : TestResult()
        data class Error(val message: String) : TestResult()
    }
}
