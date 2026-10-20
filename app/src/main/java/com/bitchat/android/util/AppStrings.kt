package com.bitchat.android.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate

/**
 * Localized string lookup for non-UI classes (system messages, command help).
 * Honors the per-app language chosen via AppCompatDelegate; falls back to the
 * provided English format when no context is available (e.g. unit tests).
 */
object AppStrings {
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun resources(): Resources? {
        val ctx = appContext ?: return null
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return ctx.resources
        val config = Configuration(ctx.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
        return ctx.createConfigurationContext(config).resources
    }

    fun get(@StringRes id: Int, fallback: String, vararg args: Any?): String {
        return try {
            val res = resources()
            if (res != null) res.getString(id, *args) else String.format(fallback, *args)
        } catch (_: Exception) {
            try { String.format(fallback, *args) } catch (_: Exception) { fallback }
        }
    }
}
