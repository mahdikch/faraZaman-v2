package Ir.co.tfs.farazaman.presentation.login

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import Ir.co.tfs.farazaman.OSMTracker
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.activity.Intro
import Ir.co.tfs.farazaman.auth.OidcAuthManager
import Ir.co.tfs.farazaman.domain.repository.AuthRepository
import Ir.co.tfs.farazaman.domain.model.LoginResult
import Ir.co.tfs.farazaman.domain.usecase.LoginUseCase
import Ir.co.tfs.farazaman.supervisor.SupervisorMissionHelper
import Ir.co.tfs.farazaman.databinding.ActivityLoginBinding
import Ir.co.tfs.farazaman.util.LoadingDialog
import Ir.co.tfs.farazaman.util.UserManager
import Ir.co.tfs.farazaman.util.RolesManager
import Ir.co.tfs.farazaman.util.TokenManager
import Ir.co.tfs.farazaman.service.remote.UserService
import Ir.co.tfs.farazaman.service.remote.RolesService
import android.util.Log
import net.openid.appauth.AuthorizationResponse
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject
import Ir.co.tfs.farazaman.AppConstants
import java.io.IOException
import java.net.URI

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var prefs: SharedPreferences
    private var provinceList: List<ProvinceInstance> = emptyList()
    private var selectedProvince: ProvinceInstance? = null
    private var loadingDialog: LoadingDialog? = null
    private var currentBaseUrl: String = ""

    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var userManager: UserManager
    @Inject lateinit var rolesManager: RolesManager
    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var oidcAuthManager: OidcAuthManager
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var loginUseCase: LoginUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        currentBaseUrl = prefs.getString("BASE_URL", "No base URL set") ?: "No base URL set"
        Log.d("LoginActivity", "Activity started - Current base URL: $currentBaseUrl")

        if (hasValidSession()) {
            continueWithExistingSession(showLoginOnFailure = true)
        } else {
            setupLoginScreen()
        }
    }

    private fun setupLoginScreen() {
        binding.loginButton.isEnabled = false
        binding.citySelectedText.text = null

        fetchProvincesAndPopulateSpinner()
        setupListeners()
        setupCitySelector()
    }

    private fun hasValidSession(): Boolean {
        if (!tokenManager.isLoggedIn()) return false
        if (!tokenManager.isTokenExpired()) return true
        return tokenManager.hasRefreshToken()
    }

    private fun continueWithExistingSession(showLoginOnFailure: Boolean = false) {
        binding.loginButton.isEnabled = false
        loadingDialog = LoadingDialog.show(this, "در حال ورود...")

        lifecycleScope.launch {
            val sessionReady = ensureValidAccessToken()
            if (sessionReady) {
                Log.d("LoginActivity", "Resuming with existing session")
                fetchUserInfo()
                return@launch
            }

            loadingDialog?.dismiss()
            loadingDialog = null
            Log.d("LoginActivity", "Existing session is invalid, showing login screen")

            if (showLoginOnFailure) {
                setupLoginScreen()
            } else {
                binding.loginButton.isEnabled = selectedProvince != null
                Toast.makeText(
                    this@LoginActivity,
                    "نشست منقضی شده. لطفاً دوباره وارد شوید.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private suspend fun ensureValidAccessToken(): Boolean {
        if (!tokenManager.isLoggedIn()) return false
        if (!tokenManager.isTokenExpired()) return true
        if (!tokenManager.hasRefreshToken()) return false
        return try {
            authRepository.refreshToken()
            true
        } catch (e: Exception) {
            Log.e("LoginActivity", "Token refresh failed", e)
            false
        }
    }

    private fun canUseExistingSession(): Boolean {
        if (!hasValidSession()) return false
        val selected = selectedProvince ?: return true
        val savedBase = prefs.getString("BASE_URL", null)?.trimEnd('/') ?: return false
        return savedBase == selected.baseAddress.trimEnd('/')
    }

    override fun onDestroy() {
        loadingDialog?.dismiss()
        super.onDestroy()
    }

    private fun fetchUserInfo() {
        Log.d("LoginActivity", "Fetching user info...")
        
        // Get current base URL from SharedPreferences
        val currentBaseUrl = prefs.getString("BASE_URL", "https://app.tfs.co.ir") ?: "https://app.tfs.co.ir"
        val normalizedBaseUrl = if (currentBaseUrl.endsWith("/")) currentBaseUrl else "$currentBaseUrl/"
        
        Log.d("LoginActivity", "Creating UserService with base URL: $normalizedBaseUrl")
        
        // Create a new Retrofit instance with the current base URL
        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        val userService = retrofit.create(UserService::class.java)
        
        lifecycleScope.launch {
            try {
                val response = userService.getUserInfo()
                
                if (response.isSuccessful) {
                    val userInfoResponse = response.body()
                    if (userInfoResponse != null) {
                        Log.d("LoginActivity", "User info received successfully")
                        Log.d("LoginActivity", "UserName: ${userInfoResponse.user.userName}")
                        Log.d("LoginActivity", "DisplayName: ${userInfoResponse.user.displayName}")
                        Log.d("LoginActivity", "DefaultSubsystem: ${userInfoResponse.user.defaultSubsystem}")
                        Log.d("LoginActivity", "DefaultRoleName: ${userInfoResponse.user.defaultRoleName}")
                        
                        // Save user info to SharedPreferences
                        userManager.saveUserInfo(userInfoResponse.user)
                        
                        // Fetch user roles after saving user info
                        fetchUserRoles()
                    } else {
                        Log.e("LoginActivity", "User info response body is null")
                        loadingDialog?.dismiss()
                        loadingDialog = null
                        Toast.makeText(this@LoginActivity, "خطا در دریافت اطلاعات کاربر", Toast.LENGTH_SHORT).show()
                        decideNextActivity()
                    }
                } else {
                    Log.e("LoginActivity", "Failed to fetch user info: ${response.code()} - ${response.message()}")
                    loadingDialog?.dismiss()
                    loadingDialog = null
                    // Continue to next activity even if user info fetch fails
                    Toast.makeText(this@LoginActivity, "ورود موفقیت‌آمیز (خطا در دریافت اطلاعات کاربر)", Toast.LENGTH_SHORT).show()
                    decideNextActivity()
                }
            } catch (e: Exception) {
                Log.e("LoginActivity", "Error fetching user info", e)
                loadingDialog?.dismiss()
                loadingDialog = null
                // Continue to next activity even if user info fetch fails
                Toast.makeText(this@LoginActivity, "ورود موفقیت‌آمیز (خطا در دریافت اطلاعات کاربر)", Toast.LENGTH_SHORT).show()
                decideNextActivity()
            }
        }
    }
    
    private fun fetchUserRoles() {
        Log.d("LoginActivity", "Fetching user roles...")
        
        // Get current base URL from SharedPreferences
        val currentBaseUrl = prefs.getString("BASE_URL", "https://app.tfs.co.ir") ?: "https://app.tfs.co.ir"
        val normalizedBaseUrl = if (currentBaseUrl.endsWith("/")) currentBaseUrl else "$currentBaseUrl/"
        
        Log.d("LoginActivity", "Creating RolesService with base URL: $normalizedBaseUrl")
        
        // Create a new Retrofit instance with the current base URL
        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        val rolesService = retrofit.create(RolesService::class.java)
        
        lifecycleScope.launch {
            try {
                // Get access token
                val token = tokenManager.getAccessToken()
                if (token.isNullOrEmpty()) {
                    Log.e("LoginActivity", "Access token is missing for fetching user roles")
                    loadingDialog?.dismiss()
                    loadingDialog = null
                    Toast.makeText(this@LoginActivity, "خطا: توکن دسترسی برای دریافت نقش‌ها موجود نیست", Toast.LENGTH_LONG).show()
                    decideNextActivity()
                    return@launch
                }
                
                val response = rolesService.getUserRoles("Bearer $token")
                
                if (response.isSuccessful) {
                    val rolesResponse = response.body()
                    if (rolesResponse != null) {
                        Log.d("LoginActivity", "User roles received successfully")
                        Log.d("LoginActivity", "Roles: ${rolesResponse.roles}")
                        
                        // Save user roles to SharedPreferences
                        rolesManager.saveUserRoles(rolesResponse)
                        
                        loadingDialog?.dismiss()
                        loadingDialog = null
                        Toast.makeText(this@LoginActivity, "ورود موفقیت‌آمیز", Toast.LENGTH_SHORT).show()
                        decideNextActivity()
                    } else {
                        Log.e("LoginActivity", "User roles response body is null")
                        loadingDialog?.dismiss()
                        loadingDialog = null
                        Toast.makeText(this@LoginActivity, "خطا در دریافت نقش‌های کاربر", Toast.LENGTH_SHORT).show()
                        decideNextActivity()
                    }
                } else {
                    Log.e("LoginActivity", "Failed to fetch user roles: ${response.code()} - ${response.message()}")
                    loadingDialog?.dismiss()
                    loadingDialog = null
                    // Continue to next activity even if roles fetch fails
                    Toast.makeText(this@LoginActivity, "ورود موفقیت‌آمیز (خطا در دریافت نقش‌های کاربر)", Toast.LENGTH_SHORT).show()
                    decideNextActivity()
                }
            } catch (e: Exception) {
                Log.e("LoginActivity", "Error fetching user roles", e)
                loadingDialog?.dismiss()
                loadingDialog = null
                // Continue to next activity even if roles fetch fails
                Toast.makeText(this@LoginActivity, "ورود موفقیت‌آمیز (خطا در دریافت نقش‌های کاربر)", Toast.LENGTH_SHORT).show()
                decideNextActivity()
            }
        }
    }
    
    private fun decideNextActivity() {
        val showIntro = prefs.getBoolean(OSMTracker.Preferences.KEY_DISPLAY_APP_INTRO, OSMTracker.Preferences.VAL_DISPLAY_APP_INTRO)
        
        // Check if intro should be shown first
        if (showIntro) {
            startActivity(Intent(this, Intro::class.java))
            finish()
            return
        }
        
        // Get user access based on roles
        val userAccess = rolesManager.getUserAccess()
        
        Log.d("LoginActivity", "=== User Access Check ===")
        Log.d("LoginActivity", "Has Driver Access: ${userAccess.hasDriverAccess}")
        Log.d("LoginActivity", "Has Supervisor Access: ${userAccess.hasSupervisorAccess}")
        Log.d("LoginActivity", "Is Super Admin: ${userAccess.isSuperAdmin}")
        
        when {
            userAccess.canAccessOnlySupervisor() && !userAccess.isSuperAdmin -> {
                Log.d("LoginActivity", "User has only supervisor access, navigating to supervisor flow")
                SupervisorMissionHelper.launchSupervisor(this)
            }
            else -> {
                val intent = when {
                    userAccess.isSuperAdmin -> {
                        Log.d("LoginActivity", "User is SuperAdmin, navigating to RoleSelectionActivity")
                        Intent(this, Ir.co.tfs.farazaman.activity.RoleSelectionActivity::class.java)
                    }
                    userAccess.canAccessOnlyDriver() -> {
                        Log.d("LoginActivity", "User has only driver access, navigating to DriverActivity")
                        Intent(this, Ir.co.tfs.farazaman.activity.DriverActivity::class.java)
                    }
                    userAccess.canAccessBoth() -> {
                        Log.d("LoginActivity", "User has both accesses, navigating to RoleSelectionActivity")
                        Intent(this, Ir.co.tfs.farazaman.activity.RoleSelectionActivity::class.java)
                    }
                    else -> {
                        Log.w("LoginActivity", "User has no valid role, defaulting to RoleSelectionActivity")
                        Toast.makeText(this, "هیچ نقش معتبری یافت نشد", Toast.LENGTH_LONG).show()
                        Intent(this, Ir.co.tfs.farazaman.activity.RoleSelectionActivity::class.java)
                    }
                }
                startActivity(intent)
            }
        }

        Log.d("LoginActivity", "=== End User Access Check ===")
        finish()
    }

    private fun setupListeners() {
        binding.loginButton.setOnClickListener {
            if (selectedProvince == null || currentBaseUrl.isBlank()) {
                Toast.makeText(this, "لطفاً شهر را انتخاب کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startLogin()
        }
    }

    private fun startLogin() {
        if (canUseExistingSession()) {
            continueWithExistingSession()
            return
        }

        if (selectedProvince?.isWebLogin == true) {
            startWebOidcLogin()
        } else {
            startPasswordLogin()
        }
    }

    private fun startPasswordLogin() {
        val username = binding.usernameInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "لطفاً نام کاربری و رمز عبور را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }

        binding.loginButton.isEnabled = false
        showLoginLoading("در حال ورود...")

        lifecycleScope.launch {
            when (val result = loginUseCase(username, password)) {
                is LoginResult.Success -> {
                    showLoginLoading("در حال دریافت اطلاعات...")
                    fetchUserInfo()
                }
                is LoginResult.Error -> {
                    dismissLoginLoading()
                    binding.loginButton.isEnabled = selectedProvince != null
                    Toast.makeText(
                        this@LoginActivity,
                        result.message ?: "خطا در ورود",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun startWebOidcLogin() {
        if (supportFragmentManager.findFragmentByTag(OIDC_LOGIN_TAG) != null) {
            return
        }

        loadingDialog = LoadingDialog.show(this, "در حال بارگذاری صفحه ورود...")

        lifecycleScope.launch {
            try {
                val config = oidcAuthManager.ensureConfiguration()
                loadingDialog?.dismiss()
                loadingDialog = null

                val authRequest = oidcAuthManager.createAuthorizationRequest(config)
                (supportFragmentManager.findFragmentByTag(OIDC_LOGIN_TAG) as? OidcLoginBottomSheet)
                    ?.dismissAllowingStateLoss()

                val sheet = OidcLoginBottomSheet.newInstance(
                    authUrl = authRequest.toUri().toString(),
                    authRequestJson = authRequest.jsonSerializeString(),
                )
                sheet.onAuthSuccess = { response ->
                    oidcAuthManager.clearPendingAuthorizationRequest()
                    showLoginLoading("در حال ورود...")
                    handleOidcResponse(response)
                }
                sheet.onAuthCancelled = {
                    oidcAuthManager.clearPendingAuthorizationRequest()
                    setLoginButtonEnabled(selectedProvince != null)
                }
                sheet.onAuthError = { exception ->
                    oidcAuthManager.clearPendingAuthorizationRequest()
                    setLoginButtonEnabled(selectedProvince != null)
                    Log.e("LoginActivity", "OIDC auth error: ${exception.error} - ${exception.errorDescription}")
                    Toast.makeText(
                        this@LoginActivity,
                        "خطا در ورود: ${exception.errorDescription ?: exception.error ?: "نامشخص"}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                sheet.show(supportFragmentManager, OIDC_LOGIN_TAG)
            } catch (e: Exception) {
                Log.e("LoginActivity", "Failed to start OIDC login", e)
                loadingDialog?.dismiss()
                loadingDialog = null
                setLoginButtonEnabled(selectedProvince != null)
                Toast.makeText(
                    this@LoginActivity,
                    "خطا در اتصال به سرور احراز هویت",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun handleOidcResponse(response: AuthorizationResponse) {
        if (loadingDialog?.isShowing != true) {
            showLoginLoading("در حال ورود...")
        }
        lifecycleScope.launch {
            val result = oidcAuthManager.exchangeAuthorizationResponse(response)

            if (result.isSuccess) {
                Log.d("LoginActivity", "OIDC login successful")
                showLoginLoading("در حال دریافت اطلاعات...")
                fetchUserInfo()
            } else {
                dismissLoginLoading()
                setLoginButtonEnabled(selectedProvince != null)
                Log.e("LoginActivity", "OIDC token exchange failed", result.exceptionOrNull())
                Toast.makeText(
                    this@LoginActivity,
                    "خطا در دریافت توکن: ${result.exceptionOrNull()?.message ?: "نامشخص"}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun setupCitySelector() {
        val openSheet = { openCitySelectionSheet() }
        binding.citySelector.setOnClickListener { openSheet() }
        binding.citySelectedText.setOnClickListener { openSheet() }
    }

    private fun fetchProvincesAndPopulateSpinner() {
        provinceList = emptyList()
        binding.citySelectedText.text = null
        setLoginButtonEnabled(false)

        setCityLoading(true)
        val client = OkHttpClient.Builder()
            .connectTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        // Always load the full city list from the central registry (not the saved regional BASE_URL).
        val instancesUrl = "${AppConstants.BASE_URL.trimEnd('/')}/api/Application/Instances"
        val request = Request.Builder()
            .url(instancesUrl)
            .build()
        Log.d("LoginActivity", "Fetching instances from: $instancesUrl")
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    onProvincesFetchFailed()
                    Ir.co.tfs.farazaman.util.ErrorHandler.handleNetworkError(e, this@LoginActivity, "خطا در دریافت استان‌ها")
                }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null && populateProvincesFromJson(body)) {
                            saveProvincesCache(body)
                            setCityLoading(false)
                        } else {
                            onProvincesFetchFailed()
                            Toast.makeText(
                                this@LoginActivity,
                                "پاسخ نامعتبر از سرور دریافت شد",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } else {
                        onProvincesFetchFailed()
                        Ir.co.tfs.farazaman.util.ErrorHandler.handleHttpError(
                            response,
                            this@LoginActivity,
                            "خطا در دریافت استان‌ها",
                        )
                    }
                }
            }
        })
    }

    private fun openCitySelectionSheet() {
        val isLoading = binding.provinceProgressBar.visibility == View.VISIBLE
        CitySelectionBottomSheet.newInstance(provinceList, isLoading).apply {
            onCitySelected = { selected -> onProvinceSelected(selected) }
        }.show(supportFragmentManager, "city_selection")
    }

    private fun onProvinceSelected(selected: ProvinceInstance) {
        Log.d("LoginActivity", "=== PROVINCE SELECTION ===")
        Log.d("LoginActivity", "Province selected: ${selected.title}")
        Log.d("LoginActivity", "Province base URL: ${selected.baseAddress}")
        Log.d("LoginActivity", "Province clientId: ${selected.clientId}")
        Log.d("LoginActivity", "Province scope: ${selected.scope}")
        Log.d("LoginActivity", "Province loginMode: ${selected.loginMode}")

        selectedProvince = selected
        binding.citySelectedText.text = selected.title
        currentBaseUrl = selected.baseAddress
        prefs.edit().putString("BASE_URL", selected.baseAddress).apply()
        LoginModeHelper.saveCityLoginConfig(
            prefs = prefs,
            loginMode = selected.loginMode,
            scope = selected.scope,
        )
        oidcAuthManager.saveClientId(selected.clientId)
        oidcAuthManager.saveScope(selected.scope)
        updateLoginFormForCity(selected)
        setLoginButtonEnabled(true)
        if (selected.isWebLogin) {
            if (canUseExistingSession()) {
                continueWithExistingSession()
            } else {
                startWebOidcLogin()
            }
        }
        Log.d("LoginActivity", "=== END PROVINCE SELECTION ===")
    }

    private fun updateLoginFormForCity(city: ProvinceInstance) {
        val legacyVisibility = if (city.isWebLogin) View.GONE else View.VISIBLE
        binding.usernameLabel.visibility = legacyVisibility
        binding.usernameFieldContainer.visibility = legacyVisibility
        binding.passwordLabel.visibility = legacyVisibility
        binding.passwordFieldContainer.visibility = legacyVisibility
        if (city.isWebLogin) {
            binding.usernameInput.text?.clear()
            binding.passwordInput.text?.clear()
        }
    }

    private fun setCityLoading(loading: Boolean) {
        binding.provinceProgressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.citySelectedText.visibility = if (loading) View.INVISIBLE else View.VISIBLE
    }

    private fun saveProvincesCache(json: String) {
        prefs.edit().putString("CACHED_PROVINCES_JSON", json).apply()
        Log.d("LoginActivity", "Provinces cache saved (${json.length} bytes)")
    }

    private fun loadProvincesCache(): String? {
        return prefs.getString("CACHED_PROVINCES_JSON", null)
    }

    private fun onProvincesFetchFailed() {
        provinceList = emptyList()
        binding.citySelectedText.text = null
        setLoginButtonEnabled(false)
        setCityLoading(false)
        Log.d("LoginActivity", "Provinces fetch failed — city list cleared")
    }

    /** @return true if at least one city was parsed */
    private fun populateProvincesFromJson(json: String): Boolean {
        return try {
            val array = extractInstancesArray(json) ?: return false
            val tempList = mutableListOf<ProvinceInstance>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                parseProvinceInstance(obj)?.let { tempList.add(it) }
            }
            if (tempList.isEmpty()) {
                Log.w("LoginActivity", "Provinces response was empty after parsing")
                return false
            }
            provinceList = tempList.sortedBy { it.title }
            Log.d("LoginActivity", "Provinces populated (${tempList.size}) from server")
            true
        } catch (e: Exception) {
            Log.e("LoginActivity", "Failed to populate provinces: ${e.message}")
            false
        }
    }

    private fun extractInstancesArray(json: String): JSONArray? {
        val trimmed = json.trim()
        return when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                val keys = listOf("data", "result", "items", "instances", "Instances")
                for (key in keys) {
                    if (root.has(key) && !root.isNull(key)) {
                        return root.getJSONArray(key)
                    }
                }
                null
            }
            else -> null
        }
    }

    private fun parseProvinceInstance(obj: JSONObject): ProvinceInstance? {
        val uid = obj.optLong("uid", obj.optLong("Uid", -1L))
        if (uid < 0L) return null

        val baseAddress = obj.optStringField("baseAddress", "BaseAddress").trim()
        if (baseAddress.isEmpty()) return null

        val enabled = obj.optBoolean("enabled", obj.optBoolean("Enabled", true))
        if (!enabled) return null

        if (isCentralRegistry(baseAddress)) {
            Log.d("LoginActivity", "Skipping central registry instance: $baseAddress")
            return null
        }

        val rawTitle = obj.optStringField("title", "Title", "name", "Name")
        val displayTitle = resolveCityDisplayName(rawTitle, baseAddress)
        val clientId = obj.optStringField("clientId", "ClientId")
            .ifBlank { OidcAuthManager.DEFAULT_CLIENT_ID }
        val scope = obj.optStringField("scope", "Scope")
            .ifBlank { OidcAuthManager.DEFAULT_SCOPES }
        val loginMode = obj.optInt("loginMode", obj.optInt("LoginMode", LoginModeHelper.MODE_WEB))
        return ProvinceInstance(
            uid = uid,
            title = displayTitle,
            baseAddress = baseAddress,
            enabled = enabled,
            clientId = clientId,
            scope = scope,
            loginMode = loginMode,
        )
    }

    private fun JSONObject.optStringField(vararg keys: String): String {
        for (key in keys) {
            if (has(key) && !isNull(key)) {
                val value = optString(key).trim()
                if (value.isNotEmpty()) return value
            }
        }
        return ""
    }

    private fun isCentralRegistry(baseAddress: String): Boolean {
        val normalized = baseAddress.trim().trimEnd('/').lowercase()
        return normalized == "https://app.tfs.co.ir" ||
            normalized == "http://app.tfs.co.ir"
    }

    private fun resolveCityDisplayName(rawTitle: String, baseAddress: String): String {
        val title = rawTitle.trim()
        if (title.isNotEmpty() && !title.startsWith("http", ignoreCase = true)) {
            return title
        }
        return try {
            val host = URI(baseAddress).host.orEmpty()
            when {
                host.isEmpty() -> title.ifEmpty { baseAddress }
                host.startsWith("app.") -> title.ifEmpty { host }
                else -> host.substringBefore('.').replace('-', ' ')
            }
        } catch (_: Exception) {
            title.ifEmpty { baseAddress }
        }
    }

    data class ProvinceInstance(
        val uid: Long,
        val title: String,
        val baseAddress: String,
        val enabled: Boolean,
        val clientId: String = OidcAuthManager.DEFAULT_CLIENT_ID,
        val scope: String = OidcAuthManager.DEFAULT_SCOPES,
        val loginMode: Int = LoginModeHelper.MODE_WEB,
    ) {
        val isWebLogin: Boolean
            get() = loginMode == LoginModeHelper.MODE_WEB
    }
    private fun setLoginButtonEnabled(enabled: Boolean) {
        binding.loginButton.isEnabled = enabled
    }

    private fun showLoginLoading(message: String) {
        if (loadingDialog?.isShowing == true) {
            loadingDialog?.setLoadingText(message)
        } else {
            loadingDialog = LoadingDialog.show(this, message)
        }
    }

    private fun dismissLoginLoading() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    companion object {
        private const val OIDC_LOGIN_TAG = "oidc_login"
    }
}
