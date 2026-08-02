package Ir.co.tfs.farazaman.util

import android.content.Context
import android.content.Intent
import Ir.co.tfs.farazaman.domain.repository.AuthRepository
import Ir.co.tfs.farazaman.presentation.login.LoginActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for common authentication operations
 */
@Singleton
class AuthHelper @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager
) {
    
    /**
     * Perform logout operation and redirect to login screen
     */
    fun logout(context: Context) {
        // Clear all tokens
        authRepository.logout()
        
        // Redirect to login screen
        val intent = Intent(context, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
    }
    
    /**
     * Check if user is properly authenticated
     */
    fun isAuthenticated(): Boolean {
        return tokenManager.isLoggedIn() && 
               (!tokenManager.isTokenExpired() || tokenManager.hasRefreshToken())
    }
    
    /**
     * Get current user's token status for debugging
     */
    fun getTokenStatus(): String {
        return when {
            !tokenManager.isLoggedIn() -> "Not logged in"
            tokenManager.isTokenExpired() && !tokenManager.hasRefreshToken() -> "Token expired, no refresh token"
            tokenManager.isTokenExpired() && tokenManager.hasRefreshToken() -> "Token expired, refresh available"
            else -> "Token valid (expires in ${tokenManager.getTimeUntilExpiry() / 1000 / 60} minutes)"
        }
    }
}
