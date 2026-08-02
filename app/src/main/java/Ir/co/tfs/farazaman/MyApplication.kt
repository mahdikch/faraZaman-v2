package Ir.co.tfs.farazaman

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import Ir.co.tfs.farazaman.util.LocaleUtil
import java.util.Locale

@HiltAndroidApp
class MyApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(LocaleUtil.setLocale(base!!, "fa"))
    }
    override fun onCreate() {
        super.onCreate()
        forceRTL()
    }
    private fun forceRTL() {
        val config = resources.configuration
        config.setLayoutDirection(Locale("fa")) // یا "ar" برای عربی
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
