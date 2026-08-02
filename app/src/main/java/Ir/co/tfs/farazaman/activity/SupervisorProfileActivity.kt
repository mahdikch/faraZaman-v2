package Ir.co.tfs.farazaman.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.databinding.ActivitySupervisorProfileBinding
import Ir.co.tfs.farazaman.presentation.login.LoginModeHelper
import Ir.co.tfs.farazaman.service.remote.RolesService
import Ir.co.tfs.farazaman.util.AuthHelper
import Ir.co.tfs.farazaman.util.RolesManager
import Ir.co.tfs.farazaman.util.TokenManager
import Ir.co.tfs.farazaman.util.UserManager
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject

@AndroidEntryPoint
class SupervisorProfileActivity : AppCompatActivity() {

    @Inject lateinit var authHelper: AuthHelper
    @Inject lateinit var userManager: UserManager
    @Inject lateinit var rolesManager: RolesManager
    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var okHttpClient: OkHttpClient

    private lateinit var binding: ActivitySupervisorProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySupervisorProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        bindUserInfo()
        setupChangeRoleButton()
        binding.btnLogout.setOnClickListener { confirmLogout() }
        refreshRolesIfNeeded()
    }

    private fun refreshRolesIfNeeded() {
        if (rolesManager.canSwitchRoles()) return
        lifecycleScope.launch {
            val token = tokenManager.getAccessToken() ?: return@launch
            val prefs = PreferenceManager.getDefaultSharedPreferences(this@SupervisorProfileActivity)
            val baseUrl = prefs.getString("BASE_URL", "https://app.tfs.co.ir") ?: "https://app.tfs.co.ir"
            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val rolesService = Retrofit.Builder()
                .baseUrl(normalizedBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(RolesService::class.java)

            try {
                val response = rolesService.getUserRoles("Bearer $token")
                if (response.isSuccessful) {
                    response.body()?.let { rolesManager.saveUserRoles(it) }
                    setupChangeRoleButton()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun setupChangeRoleButton() {
        val canChangeRole = rolesManager.canSwitchRoles()
        binding.btnChangeRole.visibility = if (canChangeRole) View.VISIBLE else View.GONE
        binding.btnChangeRole.setOnClickListener {
            startActivity(
                RoleSelectionActivity.createIntent(
                    context = this,
                    fromProfile = true,
                    currentRole = RoleSelectionActivity.ROLE_SUPERVISOR,
                ),
            )
        }
    }

    private fun bindUserInfo() {
        val userInfo = userManager.getUserInfo()
        binding.txtDisplayName.text =
            userInfo?.displayName?.takeIf { it.isNotBlank() }
                ?: userInfo?.userName
                ?: getString(R.string.supervisor_profile)
        binding.txtUserName.text = userInfo?.userName ?: "-"
        binding.txtRoleName.text = userInfo?.defaultRoleName?.takeIf { it.isNotBlank() } ?: "-"
        binding.txtUserName.visibility =
            if (userInfo?.userName.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.prefs_logout_dialog_title)
            .setMessage(R.string.prefs_logout_dialog_message)
            .setPositiveButton(R.string.prefs_logout_dialog_confirm) { _, _ ->
                performLogout()
            }
            .setNegativeButton(R.string.prefs_logout_dialog_cancel, null)
            .show()
    }

    private fun performLogout() {
        userManager.clearUserInfo()
        rolesManager.clearUserRoles()
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .remove("BASE_URL")
            .remove("CACHED_PROVINCES_JSON")
            .apply()
        LoginModeHelper.clearLoginConfig(PreferenceManager.getDefaultSharedPreferences(this))
        authHelper.logout(this)
        finish()
    }

    companion object {
        fun open(context: Context) {
            context.startActivity(Intent(context, SupervisorProfileActivity::class.java))
        }
    }
}
