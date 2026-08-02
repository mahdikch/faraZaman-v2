package Ir.co.tfs.farazaman.presentation.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import Ir.co.tfs.farazaman.presentation.login.LoginActivity
import Ir.co.tfs.farazaman.util.AuthStateManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
abstract class BaseActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "BaseActivity"
    }
    
    @Inject
    lateinit var authStateManager: AuthStateManager
    
    private val authStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AuthStateManager.ACTION_AUTH_STATE_CHANGED) {
                val isLoggedIn = intent.getBooleanExtra(AuthStateManager.EXTRA_IS_LOGGED_IN, true)
                val reason = intent.getStringExtra(AuthStateManager.EXTRA_REASON) ?: "unknown"
                
                Log.d(TAG, "Auth state changed: isLoggedIn=$isLoggedIn, reason=$reason")
                
                if (!isLoggedIn && shouldNavigateToLogin(reason)) {
                    navigateToLogin(reason)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerAuthStateReceiver()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterAuthStateReceiver()
    }
    
    private fun registerAuthStateReceiver() {
        try {
            authStateManager.registerAuthStateReceiver(authStateReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error registering auth state receiver: ${e.message}")
        }
    }
    
    private fun unregisterAuthStateReceiver() {
        try {
            authStateManager.unregisterAuthStateReceiver(authStateReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering auth state receiver: ${e.message}")
        }
    }
    
    /**
     * Determine if this activity should navigate to login based on the reason
     * Override in subclasses to customize behavior
     */
    protected open fun shouldNavigateToLogin(reason: String): Boolean {
        // Default behavior: navigate to login for all auth failures
        return reason == AuthStateManager.REASON_TOKEN_CLEARED || 
               reason == AuthStateManager.REASON_REFRESH_FAILED
    }
    
    /**
     * Navigate to login activity
     * Override in subclasses to customize navigation behavior
     */
    protected open fun navigateToLogin(reason: String) {
        Log.d(TAG, "Navigating to login due to: $reason")
        
        // Clear any existing tasks and start fresh
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("auth_failure_reason", reason)
        }
        
        startActivity(intent)
        finish()
    }
}
