package Ir.co.tfs.farazaman.presentation.login

import android.content.SharedPreferences

object LoginModeHelper {
    const val MODE_PASSWORD = 0
    const val MODE_WEB = 1

    private const val PREF_LOGIN_MODE = "LOGIN_MODE"
    private const val PREF_OIDC_SCOPE = "OIDC_SCOPE"

    fun saveCityLoginConfig(
        prefs: SharedPreferences,
        loginMode: Int,
        scope: String,
    ) {
        prefs.edit()
            .putInt(PREF_LOGIN_MODE, loginMode)
            .putString(PREF_OIDC_SCOPE, scope)
            .apply()
    }

    fun getLoginMode(prefs: SharedPreferences): Int {
        return prefs.getInt(PREF_LOGIN_MODE, MODE_WEB)
    }

    fun isWebLogin(prefs: SharedPreferences): Boolean {
        return getLoginMode(prefs) == MODE_WEB
    }

    fun getOidcScope(prefs: SharedPreferences): String {
        return prefs.getString(PREF_OIDC_SCOPE, null).orEmpty()
    }

    fun clearLoginConfig(prefs: SharedPreferences) {
        prefs.edit()
            .remove(PREF_LOGIN_MODE)
            .remove(PREF_OIDC_SCOPE)
            .apply()
    }
}
