package Ir.co.tfs.farazaman.util

import android.content.Context
import android.widget.Toast
import okhttp3.Response
import retrofit2.Response as RetrofitResponse
import org.json.JSONObject

object ErrorHandler {
    
    /**
     * Handle HTTP response errors and show appropriate messages to user
     * @param response The HTTP response
     * @param context The application context
     * @param defaultErrorMessage Default error message if specific error cannot be extracted
     */
    fun handleHttpError(response: Response, context: Context, defaultErrorMessage: String) {
        val errorMessage = when (response.code) {
            400 -> {
                try {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val jsonError = JSONObject(responseBody)
                        // Try to extract error message from common response formats
                        jsonError.optString("error_description", "")
                            .takeIf { it.isNotEmpty() }
                            ?: jsonError.optString("message", "")
                            .takeIf { it.isNotEmpty() }
                            ?: jsonError.optString("error", "")
                            .takeIf { it.isNotEmpty() }
                            ?: jsonError.optString("errorMessage", "")
                            .takeIf { it.isNotEmpty() }
                            ?: "خطای 400: درخواست نامعتبر"
                    } else {
                        "خطای 400: درخواست نامعتبر"
                    }
                } catch (e: Exception) {
                    "خطای 400: درخواست نامعتبر"
                }
            }
            401 -> "خطای 401: عدم احراز هویت - لطفاً دوباره وارد شوید"
            403 -> "خطای 403: عدم دسترسی"
            404 -> "خطای 404: منبع مورد نظر یافت نشد"
            500 -> "خطای 500: خطای سرور"
            502 -> "خطای 502: سرور در دسترس نیست"
            503 -> "خطای 503: سرویس موقتاً در دسترس نیست"
            else -> defaultErrorMessage
        }
        
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
    }
    
    /**
     * Handle Retrofit HTTP response errors and show appropriate messages to user
     * @param response The Retrofit HTTP response
     * @param context The application context
     * @param defaultErrorMessage Default error message if specific error cannot be extracted
     */
    fun handleRetrofitHttpError(response: RetrofitResponse<*>, context: Context, defaultErrorMessage: String) {
        val errorMessage = when (response.code()) {
            400 -> {
                try {
                    val responseBody = response.errorBody()?.string()
                    if (responseBody != null) {
                        val jsonError = JSONObject(responseBody)
                        // Try to extract error message from common response formats
                        jsonError.optString("error_description", "")
                            .takeIf { it.isNotEmpty() }
                            ?: jsonError.optString("message", "")
                            .takeIf { it.isNotEmpty() }
                            ?: jsonError.optString("error", "")
                            .takeIf { it.isNotEmpty() }
                            ?: jsonError.optString("errorMessage", "")
                            .takeIf { it.isNotEmpty() }
                            ?: "خطای 400: درخواست نامعتبر"
                    } else {
                        "خطای 400: درخواست نامعتبر"
                    }
                } catch (e: Exception) {
                    "خطای 400: درخواست نامعتبر"
                }
            }
            401 -> "خطای 401: عدم احراز هویت - لطفاً دوباره وارد شوید"
            403 -> "خطای 403: عدم دسترسی"
            404 -> "خطای 404: منبع مورد نظر یافت نشد"
            500 -> "خطای 500: خطای سرور"
            502 -> "خطای 502: سرور در دسترس نیست"
            503 -> "خطای 503: سرویس موقتاً در دسترس نیست"
            else -> defaultErrorMessage
        }
        
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
    }
    
    /**
     * Handle network errors and show appropriate messages to user
     * @param exception The network exception
     * @param context The application context
     * @param defaultErrorMessage Default error message if specific error cannot be extracted
     */
    fun handleNetworkError(exception: Exception, context: Context, defaultErrorMessage: String) {
        val errorMessage = when (exception) {
            is java.net.UnknownHostException -> "خطا در اتصال به سرور - بررسی اتصال اینترنت"
            is java.net.SocketTimeoutException -> "خطا در اتصال به سرور - زمان انتظار به پایان رسید"
            is java.net.ConnectException -> "خطا در اتصال به سرور"
            else -> defaultErrorMessage
        }
        
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
    }
} 
