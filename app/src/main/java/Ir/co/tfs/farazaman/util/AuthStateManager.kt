package Ir.co.tfs.farazaman.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthStateManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "AuthStateManager"
        const val ACTION_AUTH_STATE_CHANGED = "Ir.co.tfs.farazaman.AUTH_STATE_CHANGED"
        const val EXTRA_IS_LOGGED_IN = "is_logged_in"
        const val EXTRA_REASON = "reason"
        
        const val REASON_TOKEN_CLEARED = "token_cleared"
        const val REASON_REFRESH_FAILED = "refresh_failed"
        const val REASON_LOGOUT = "logout"
    }
    
    /**
     * Notify that authentication state has changed
     */
    fun notifyAuthStateChanged(isLoggedIn: Boolean, reason: String) {
        Log.d(TAG, "Notifying auth state change: isLoggedIn=$isLoggedIn, reason=$reason")
        
        val intent = Intent(ACTION_AUTH_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_LOGGED_IN, isLoggedIn)
            putExtra(EXTRA_REASON, reason)
        }
        
        context.sendBroadcast(intent)
    }
    
    /**
     * Register a receiver for authentication state changes
     */
    fun registerAuthStateReceiver(receiver: BroadcastReceiver) {
        val filter = IntentFilter(ACTION_AUTH_STATE_CHANGED)
        context.registerReceiver(receiver, filter)
    }
    
    /**
     * Unregister a receiver for authentication state changes
     */
    fun unregisterAuthStateReceiver(receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receiver: ${e.message}")
        }
    }
}
