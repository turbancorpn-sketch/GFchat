package com.gfchat.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Login session (tokens) encrypted storage me. */
class SessionStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "gfchat_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    val hasSession: Boolean get() = prefs.getString(KEY_REFRESH, null) != null
    val accessToken: String? get() = prefs.getString(KEY_ACCESS, null)
    val refreshToken: String? get() = prefs.getString(KEY_REFRESH, null)
    val expiresAtMs: Long get() = prefs.getLong(KEY_EXPIRES, 0L)
    val email: String? get() = prefs.getString(KEY_EMAIL, null)

    /** Last known: pairing ho chuka hai (offline start ke liye). */
    var pairedCached: Boolean
        get() = prefs.getBoolean(KEY_PAIRED, false)
        set(v) = prefs.edit().putBoolean(KEY_PAIRED, v).apply()

    fun save(access: String, refresh: String, expiresInSec: Long, email: String?) {
        val e = prefs.edit()
        e.putString(KEY_ACCESS, access)
        e.putString(KEY_REFRESH, refresh)
        e.putLong(KEY_EXPIRES, System.currentTimeMillis() + expiresInSec * 1000)
        if (!email.isNullOrBlank()) e.putString(KEY_EMAIL, email)
        e.apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_ACCESS = "access"
        const val KEY_REFRESH = "refresh"
        const val KEY_EXPIRES = "expires_at"
        const val KEY_EMAIL = "email"
        const val KEY_PAIRED = "paired"
    }
}
