package com.example

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("rootit_tv_settings", Context.MODE_PRIVATE)

    var adBlockEnabled: Boolean
        get() = prefs.getBoolean("ad_block_enabled", true)
        set(value) = prefs.edit().putBoolean("ad_block_enabled", value).apply()

    var desktopModeEnabled: Boolean
        get() = prefs.getBoolean("desktop_mode_enabled", false)
        set(value) = prefs.edit().putBoolean("desktop_mode_enabled", value).apply()

    var customBlockedDomains: Set<String>
        get() = prefs.getStringSet("custom_blocked_domains", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("custom_blocked_domains", value).apply()

    var themeAccentIndex: Int
        get() = prefs.getInt("theme_accent_index", 0)
        set(value) = prefs.edit().putInt("theme_accent_index", value).apply()

    var fullscreenModeEnabled: Boolean
        get() = prefs.getBoolean("fullscreen_mode_enabled", false)
        set(value) = prefs.edit().putBoolean("fullscreen_mode_enabled", value).apply()

    var rootAccessEnabled: Boolean
        get() = prefs.getBoolean("root_access_enabled", false)
        set(value) = prefs.edit().putBoolean("root_access_enabled", value).apply()

    var rootCloakEnabled: Boolean
        get() = prefs.getBoolean("root_cloak_enabled", true)
        set(value) = prefs.edit().putBoolean("root_cloak_enabled", value).apply()

    fun addCustomDomain(domain: String) {
        val current = customBlockedDomains.toMutableSet()
        current.add(domain.trim().lowercase())
        customBlockedDomains = current
    }

    fun removeCustomDomain(domain: String) {
        val current = customBlockedDomains.toMutableSet()
        current.remove(domain.trim().lowercase())
        customBlockedDomains = current
    }
}
