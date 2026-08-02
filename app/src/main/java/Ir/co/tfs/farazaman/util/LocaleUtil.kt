package Ir.co.tfs.farazaman.util

import android.content.Context
import org.intellij.lang.annotations.Language
import java.util.Locale

object LocaleUtil {
    fun setLocale(contex:Context,language: String): Context? {
        val locale=Locale(language)
        Locale.setDefault(locale)
        val config=contex.resources.configuration
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return contex.createConfigurationContext(config)
    }
}
