package com.gfchat.app.ui.theme

import android.content.Context

enum class ThemeMode { System, Light, Dark }

/** Theme preference har phone pe alag save hoti hai (dusre phone se koi lena-dena nahi). */
class ThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences("gfchat_theme", Context.MODE_PRIVATE)

    var mode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString(KEY, null) ?: "") }.getOrDefault(ThemeMode.System)
        set(value) = prefs.edit().putString(KEY, value.name).apply()

    fun next(): ThemeMode {
        val n = when (mode) {
            ThemeMode.System -> ThemeMode.Light
            ThemeMode.Light -> ThemeMode.Dark
            ThemeMode.Dark -> ThemeMode.System
        }
        mode = n
        return n
    }

    private companion object {
        const val KEY = "mode"
    }
}
