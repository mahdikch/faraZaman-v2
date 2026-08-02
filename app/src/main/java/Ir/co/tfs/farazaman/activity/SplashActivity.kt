package Ir.co.tfs.farazaman.activity

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import Ir.co.tfs.farazaman.OSMTracker
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.presentation.login.LoginActivity
import Ir.co.tfs.farazaman.util.TokenManager
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.imageView)
        val slogan = findViewById<TextView>(R.id.slogan_text)

        val logoAnim = AnimationUtils.loadAnimation(this, R.anim.splash_logo_fade_in)
        slogan.startAnimation(AnimationUtils.loadAnimation(this, R.anim.splash_text_slide_up))
        logo.startAnimation(logoAnim)

        logoAnim.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                navigateToNextScreen()
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
    }

    private fun navigateToNextScreen() {
        // First, check if Developer Options is enabled
//        if (isDeveloperOptionsEnabled()) {
//            // Developer Options is enabled, show warning screen
//            val intent = Intent(this, DeveloperOptionsWarningActivity::class.java)
//            startActivity(intent)
//            finish()
//            return
//        }

        // Use TokenManager to check authentication status
        val intent: Intent

        if (tokenManager.isLoggedIn()) {
            // User is logged in, check if token is expired
            if (tokenManager.isTokenExpired() && !tokenManager.hasRefreshToken()) {
                // Token expired and no refresh token, redirect to login
                intent = Intent(this, LoginActivity::class.java)
            } else {
                // User has valid tokens (or can refresh), go to RoleSelectionActivity
                intent = Intent(this, RoleSelectionActivity::class.java)
            }
        } else {
            // User is not logged in, go to LoginActivity
            intent = Intent(this, LoginActivity::class.java)
        }

        startActivity(intent)
        finish() // Clear this activity from memory
    }

    /**
     * Check if Developer Options is enabled on the device
     */
    private fun isDeveloperOptionsEnabled(): Boolean {
        return try {
            Settings.Secure.getInt(
                contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) != 0
        } catch (e: Exception) {
            // If we can't determine, assume it's disabled
            false
        }
    }
}
