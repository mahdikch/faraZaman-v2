package Ir.co.tfs.farazaman.util

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import Ir.co.tfs.farazaman.R

class LoadingDialog(context: Context) : Dialog(context) {

    private lateinit var logoImageView: ImageView
    private lateinit var loadingTextView: TextView
    private lateinit var cancelButton: MaterialButton
    private var initialMessage: String = "در حال بارگذاری..."
    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null
    var onCancelListener: (() -> Unit)? = null
        set(value) {
            field = value
            if (::cancelButton.isInitialized) {
                applyCancelUi()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.loading_dialog)

        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setCanceledOnTouchOutside(false)

        logoImageView = findViewById(R.id.loading_logo)
        loadingTextView = findViewById(R.id.loading_text)
        cancelButton = findViewById(R.id.btnLoadingCancel)

        loadingTextView.text = initialMessage
        applyCancelUi()

        cancelButton.setOnClickListener {
            onCancelListener?.invoke()
            dismiss()
        }

        setOnCancelListener {
            onCancelListener?.invoke()
        }
    }

    private fun applyCancelUi() {
        val cancellable = onCancelListener != null
        if (::cancelButton.isInitialized) {
            cancelButton.visibility = if (cancellable) View.VISIBLE else View.GONE
        }
        setCancelable(cancellable)
    }

    fun setLoadingText(text: String) {
        if (::loadingTextView.isInitialized) {
            loadingTextView.text = text
        } else {
            initialMessage = text
        }
    }

    fun setTimeout(timeoutMs: Long) {
        cancelTimeout()

        timeoutHandler = Handler(Looper.getMainLooper())
        timeoutRunnable = Runnable {
            if (isShowing) {
                dismiss()
            }
        }
        timeoutHandler?.postDelayed(timeoutRunnable!!, timeoutMs)
    }

    fun cancelTimeout() {
        timeoutRunnable?.let { runnable ->
            timeoutHandler?.removeCallbacks(runnable)
        }
        timeoutHandler = null
        timeoutRunnable = null
    }

    override fun dismiss() {
        cancelTimeout()
        super.dismiss()
    }

    companion object {
        fun show(
            context: Context,
            message: String = "در حال بارگذاری...",
            onCancel: (() -> Unit)? = null,
        ): LoadingDialog {
            val dialog = LoadingDialog(context)
            dialog.initialMessage = message
            dialog.onCancelListener = onCancel
            dialog.show()
            return dialog
        }

        fun showWithTimeout(
            context: Context,
            message: String = "در حال بارگذاری...",
            timeoutMs: Long = 180000,
            onCancel: (() -> Unit)? = null,
        ): LoadingDialog {
            val dialog = LoadingDialog(context)
            dialog.initialMessage = message
            dialog.onCancelListener = onCancel
            dialog.show()
            dialog.setTimeout(timeoutMs)
            return dialog
        }
    }
}
