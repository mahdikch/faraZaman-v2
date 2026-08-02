package Ir.co.tfs.farazaman.presentation.login

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ProgressBar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.auth.OidcAuthManager
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse

class OidcLoginBottomSheet : BottomSheetDialogFragment() {

    var onAuthSuccess: ((AuthorizationResponse) -> Unit)? = null
    var onAuthCancelled: (() -> Unit)? = null
    var onAuthError: ((AuthorizationException) -> Unit)? = null

    private var authRequest: AuthorizationRequest? = null
    private var authHandled = false
    private var cancelNotified = false

    companion object {
        private const val TAG = "OidcLoginBottomSheet"
        private const val ARG_AUTH_URL = "auth_url"
        private const val ARG_AUTH_REQUEST_JSON = "auth_request_json"

        fun newInstance(
            authUrl: String,
            authRequestJson: String,
        ): OidcLoginBottomSheet {
            return OidcLoginBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_AUTH_URL, authUrl)
                    putString(ARG_AUTH_REQUEST_JSON, authRequestJson)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.bottom_sheet_oidc_login, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val authUrl = arguments?.getString(ARG_AUTH_URL).orEmpty()
        authRequest = readAuthRequest()
        if (authUrl.isBlank() || authRequest == null) {
            Log.e(TAG, "Missing auth URL or request")
            dismissAllowingStateLoss()
            return
        }

        val webView = view.findViewById<WebView>(R.id.oidcWebView)
        val progressBar = view.findViewById<ProgressBar>(R.id.oidcLoadingProgress)
        val closeButton = view.findViewById<ImageButton>(R.id.oidcCloseButton)

        closeButton.setOnClickListener {
            notifyCancelled()
            dismiss()
        }

        setupWebView(webView, progressBar)
        clearWebSession {
            if (!isAdded) return@clearWebSession
            Log.d(TAG, "Loading OIDC login page")
            webView.loadUrl(authUrl)
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        dialog.setCanceledOnTouchOutside(false)
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        val sheetHeight = (resources.displayMetrics.heightPixels * 0.88f).toInt()
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = sheetHeight
        }
        BottomSheetBehavior.from(bottomSheet).apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isDraggable = false
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        notifyCancelled()
    }

    override fun onCancel(dialog: android.content.DialogInterface) {
        super.onCancel(dialog)
        notifyCancelled()
    }

    private fun notifyCancelled() {
        if (!authHandled && !cancelNotified) {
            cancelNotified = true
            onAuthCancelled?.invoke()
        }
    }

    private fun readAuthRequest(): AuthorizationRequest? {
        val json = arguments?.getString(ARG_AUTH_REQUEST_JSON).orEmpty()
        if (json.isBlank()) return null
        return try {
            AuthorizationRequest.jsonDeserialize(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize authorization request", e)
            null
        }
    }

    private fun clearWebSession(onComplete: () -> Unit) {
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            removeAllCookies { _ ->
                flush()
                onComplete()
            }
        }
    }

    private fun setupWebView(webView: WebView, progressBar: ProgressBar) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = buildBrowserUserAgent(userAgentString)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress in 1..99) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return handleRedirect(request.url)
            }

            @Deprecated("Deprecated in API 24")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleRedirect(Uri.parse(url))
            }
        }
    }

    private fun buildBrowserUserAgent(defaultUserAgent: String): String {
        return defaultUserAgent
            .replace("; wv", "")
            .replace(Regex("Version/\\d+\\.\\d+ "), "")
    }

    private fun handleRedirect(uri: Uri): Boolean {
        if (!isOidcRedirectUri(uri)) {
            return false
        }

        authHandled = true
        val request = authRequest
        if (request == null) {
            Log.e(TAG, "Redirect received but authorization request is missing")
            onAuthError?.invoke(
                AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW,
            )
            dismissAllowingStateLoss()
            return true
        }

        Log.d(TAG, "OIDC redirect received: $uri")

        if (uri.getQueryParameter(AuthorizationException.PARAM_ERROR) != null) {
            val exception = AuthorizationException.fromOAuthRedirect(uri)
            Log.e(TAG, "OIDC redirect error: ${exception.error} - ${exception.errorDescription}")
            onAuthError?.invoke(exception)
            dismissAllowingStateLoss()
            return true
        }

        val response = AuthorizationResponse.Builder(request)
            .fromUri(uri)
            .build()
        onAuthSuccess?.invoke(response)
        dismissAllowingStateLoss()
        return true
    }

    private fun isOidcRedirectUri(uri: Uri): Boolean {
        val redirect = OidcAuthManager.REDIRECT_URI
        return uri.scheme.equals(redirect.scheme, ignoreCase = true) &&
            uri.authority == redirect.authority
    }
}
