package Ir.co.tfs.farazaman.util

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    private val sharedPreferences: SharedPreferences
) {
    
    companion object {
        private const val ACCESS_TOKEN_KEY = "ACCESS_TOKEN"
        private const val REFRESH_TOKEN_KEY = "REFRESH_TOKEN"
        private const val ID_TOKEN_KEY = "ID_TOKEN"
        private const val TOKEN_TYPE_KEY = "TOKEN_TYPE"
        private const val TOKEN_EXPIRES_AT_KEY = "TOKEN_EXPIRES_AT"
        private const val TAG = "TokenManager"
    }
    
    private val refreshMutex = Mutex()
    
    /**
     * Get the current access token
     */
    fun getAccessToken(): String? {
        val token = sharedPreferences.getString(ACCESS_TOKEN_KEY, null)
        Log.d(TAG, "getAccessToken() called - token exists: ${!token.isNullOrEmpty()}")
        if (token != null) {
            Log.d(TAG, "Access token first 20 chars: ${token.take(20)}...")
        } else {
            Log.w(TAG, "Access token is null - no token stored")
        }
        return token
    }
    
    /**
     * Get the current refresh token
     */
    fun getRefreshToken(): String? {
        val token = sharedPreferences.getString(REFRESH_TOKEN_KEY, null)
        Log.d(TAG, "getRefreshToken() called - token exists: ${!token.isNullOrEmpty()}")
        if (token != null) {
            Log.d(TAG, "Refresh token first 20 chars: ${token.take(20)}...")
        } else {
            Log.w(TAG, "Refresh token is null - no token stored")
        }
        return token
    }

    fun getIdToken(): String? = sharedPreferences.getString(ID_TOKEN_KEY, null)

    fun saveIdToken(idToken: String?) {
        sharedPreferences.edit().apply {
            if (idToken.isNullOrEmpty()) remove(ID_TOKEN_KEY) else putString(ID_TOKEN_KEY, idToken)
        }.commit()
    }
    
    /**
     * Get the token type (usually "Bearer")
     */
    fun getTokenType(): String {
        return sharedPreferences.getString(TOKEN_TYPE_KEY, "Bearer") ?: "Bearer"
    }
    
    /**
     * Save tokens from login or refresh response
     */
    fun saveTokens(
        accessToken: String,
        refreshToken: String,
        tokenType: String = "Bearer",
        expiresIn: Int
    ) {
        Log.d(TAG, "=== SAVING TOKENS ===")
        Log.d(TAG, "Access token length: ${accessToken.length}")
        Log.d(TAG, "Access token : $accessToken")
        Log.d(TAG, "Refresh token length: ${refreshToken.length}")
        Log.d(TAG, "Refresh token first 20 chars: ${refreshToken.take(20)}...")
        Log.d(TAG, "Token type: $tokenType")
        Log.d(TAG, "Expires in: $expiresIn seconds")
        
        val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
        Log.d(TAG, "Calculated expiry timestamp: $expiresAt")
        
        val editor = sharedPreferences.edit()
        editor.putString(ACCESS_TOKEN_KEY, accessToken)
        editor.putString(REFRESH_TOKEN_KEY, refreshToken)
        editor.putString(TOKEN_TYPE_KEY, tokenType)
        editor.putLong(TOKEN_EXPIRES_AT_KEY, expiresAt)
        
        val success = editor.commit() // Use commit() instead of apply() to get immediate result
        Log.d(TAG, "SharedPreferences commit result: $success")
        
        // Verify tokens were saved
        val savedAccessToken = sharedPreferences.getString(ACCESS_TOKEN_KEY, null)
        val savedRefreshToken = sharedPreferences.getString(REFRESH_TOKEN_KEY, null)
        val savedExpiresAt = sharedPreferences.getLong(TOKEN_EXPIRES_AT_KEY, 0L)
        
        Log.d(TAG, "=== VERIFICATION ===")
        Log.d(TAG, "Saved access token exists: ${!savedAccessToken.isNullOrEmpty()}")
        Log.d(TAG, "Saved refresh token exists: ${!savedRefreshToken.isNullOrEmpty()}")
        Log.d(TAG, "Saved expiry timestamp: $savedExpiresAt")
        
        if (savedAccessToken != null && savedRefreshToken != null) {
            Log.d(TAG, "✅ Tokens saved and verified successfully!")
        } else {
            Log.e(TAG, "❌ Token save verification failed!")
            Log.e(TAG, "Access token saved: ${savedAccessToken != null}")
            Log.e(TAG, "Refresh token saved: ${savedRefreshToken != null}")
        }
        Log.d(TAG, "===================")
    }
    
    /**
     * Check if the access token is expired or about to expire (with 5 minute buffer)
     */
    fun isTokenExpired(): Boolean {
        val expiresAt = sharedPreferences.getLong(TOKEN_EXPIRES_AT_KEY, 0L)
        if (expiresAt == 0L) {
            Log.d(TAG, "No expiration time set, considering token expired")
            return true
        }
        
        val currentTime = System.currentTimeMillis()
        val bufferTime = 30 * 1000L // 30 seconds buffer (reduced from 5 minutes)
        val isExpired = currentTime >= (expiresAt - bufferTime)
        
        val timeUntilExpiry = expiresAt - currentTime
        val timeUntilExpiryMinutes = timeUntilExpiry / (60 * 1000L)
        
        Log.d(TAG, "Token expiration check:")
        Log.d(TAG, "  Current time: $currentTime")
        Log.d(TAG, "  Expires at: $expiresAt") 
        Log.d(TAG, "  Time until expiry: ${timeUntilExpiryMinutes} minutes")
        Log.d(TAG, "  Buffer time: ${bufferTime / 1000L} seconds")
        Log.d(TAG, "  Is expired (with buffer): $isExpired")
        
        return isExpired
    }
    
    /**
     * Check if refresh token is available
     */
    fun hasRefreshToken(): Boolean {
        return !getRefreshToken().isNullOrEmpty()
    }
    
    /**
     * Get token expiration timestamp
     */
    fun getTokenExpiresAt(): Long {
        return sharedPreferences.getLong(TOKEN_EXPIRES_AT_KEY, 0)
    }
    
    /**
     * Check if user is logged in (has valid tokens)
     */
    fun isLoggedIn(): Boolean {
        return !getAccessToken().isNullOrEmpty() && !getRefreshToken().isNullOrEmpty()
    }
    
    /**
     * Clear all tokens (logout)
     */
    fun clearTokens() {
        Log.w(TAG, "=== CLEARING ALL TOKENS ===")
        Log.w(TAG, "This will remove all stored authentication data")
        
        // Log current state before clearing
        val currentAccessToken = sharedPreferences.getString(ACCESS_TOKEN_KEY, null)
        val currentRefreshToken = sharedPreferences.getString(REFRESH_TOKEN_KEY, null)
        Log.w(TAG, "Before clear - Access token exists: ${!currentAccessToken.isNullOrEmpty()}")
        Log.w(TAG, "Before clear - Refresh token exists: ${!currentRefreshToken.isNullOrEmpty()}")
        
        val success = sharedPreferences.edit()
            .remove(ACCESS_TOKEN_KEY)
            .remove(REFRESH_TOKEN_KEY)
            .remove(ID_TOKEN_KEY)
            .remove(TOKEN_TYPE_KEY)
            .remove(TOKEN_EXPIRES_AT_KEY)
            .commit()
            
        Log.w(TAG, "Clear tokens commit result: $success")
        
        // Verify clearing worked
        val verifyAccessToken = sharedPreferences.getString(ACCESS_TOKEN_KEY, null)
        val verifyRefreshToken = sharedPreferences.getString(REFRESH_TOKEN_KEY, null)
        Log.w(TAG, "After clear - Access token exists: ${!verifyAccessToken.isNullOrEmpty()}")
        Log.w(TAG, "After clear - Refresh token exists: ${!verifyRefreshToken.isNullOrEmpty()}")
        
        if (verifyAccessToken == null && verifyRefreshToken == null) {
            Log.w(TAG, "✅ All tokens cleared successfully")
        } else {
            Log.e(TAG, "❌ Token clearing failed!")
        }
        Log.w(TAG, "===========================")
    }
    
    /**
     * Get authorization header string
     */
    fun getAuthorizationHeader(): String? {
        val token = getAccessToken()
        return if (token != null) {
            // Normalize scheme to capitalized "Bearer" to satisfy strict backends
            "Bearer $token"
        } else {
            null
        }
    }
    
    /**
     * Thread-safe token refresh operation
     */
    suspend fun withRefreshLock(operation: suspend () -> Unit) {
        refreshMutex.withLock {
            operation()
        }
    }
    
    /**
     * Get time until token expires in milliseconds
     */
    fun getTimeUntilExpiry(): Long {
        val expiresAt = sharedPreferences.getLong(TOKEN_EXPIRES_AT_KEY, 0L)
        return if (expiresAt > 0) {
            expiresAt - System.currentTimeMillis()
        } else {
            0L
        }
    }
}
