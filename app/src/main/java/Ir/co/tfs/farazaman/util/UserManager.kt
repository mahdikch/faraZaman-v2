package Ir.co.tfs.farazaman.util

import android.content.SharedPreferences
import android.util.Log
import Ir.co.tfs.farazaman.data.model.UserInfo

/**
 * Manager for user information stored in SharedPreferences
 */
class UserManager(private val sharedPreferences: SharedPreferences) {
    
    companion object {
        private const val TAG = "UserManager"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_DEFAULT_SUBSYSTEM = "default_subsystem"
        private const val KEY_DEFAULT_ROLE_NAME = "default_role_name"
    }
    
    /**
     * Save user information to SharedPreferences
     */
    fun saveUserInfo(userInfo: UserInfo) {
        Log.d(TAG, "Saving user info: ${userInfo.userName}")
        sharedPreferences.edit()
            .putString(KEY_USER_NAME, userInfo.userName)
            .putString(KEY_DISPLAY_NAME, userInfo.displayName)
            .putInt(KEY_DEFAULT_SUBSYSTEM, userInfo.defaultSubsystem)
            .putString(KEY_DEFAULT_ROLE_NAME, userInfo.defaultRoleName)
            .apply()
        Log.d(TAG, "User info saved successfully")
    }
    
    /**
     * Get user information from SharedPreferences
     */
    fun getUserInfo(): UserInfo? {
        val userName = sharedPreferences.getString(KEY_USER_NAME, null)
        if (userName == null) {
            Log.d(TAG, "No user info found in SharedPreferences")
            return null
        }
        
        return UserInfo(
            userName = userName,
            displayName = sharedPreferences.getString(KEY_DISPLAY_NAME, "") ?: "",
            defaultSubsystem = sharedPreferences.getInt(KEY_DEFAULT_SUBSYSTEM, 0),
            defaultRoleName = sharedPreferences.getString(KEY_DEFAULT_ROLE_NAME, "") ?: ""
        )
    }
    
    /**
     * Get user name
     */
    fun getUserName(): String? {
        return sharedPreferences.getString(KEY_USER_NAME, null)
    }
    
    /**
     * Get display name
     */
    fun getDisplayName(): String? {
        return sharedPreferences.getString(KEY_DISPLAY_NAME, null)
    }
    
    /**
     * Get default subsystem
     */
    fun getDefaultSubsystem(): Int {
        return sharedPreferences.getInt(KEY_DEFAULT_SUBSYSTEM, 0)
    }
    
    /**
     * Get default role name
     */
    fun getDefaultRoleName(): String? {
        return sharedPreferences.getString(KEY_DEFAULT_ROLE_NAME, null)
    }
    
    /**
     * Check if user info exists
     */
    fun hasUserInfo(): Boolean {
        return sharedPreferences.contains(KEY_USER_NAME)
    }
    
    /**
     * Clear user information (e.g., on logout)
     */
    fun clearUserInfo() {
        Log.d(TAG, "Clearing user info")
        sharedPreferences.edit()
            .remove(KEY_USER_NAME)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_DEFAULT_SUBSYSTEM)
            .remove(KEY_DEFAULT_ROLE_NAME)
            .apply()
        Log.d(TAG, "User info cleared")
    }
}

