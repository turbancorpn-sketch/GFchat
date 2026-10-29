package com.gfchat.app.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Secret code (PIN) ko kabhi plain text me save nahi karta.
 * Salt + PBKDF2 hash save hota hai. 5 galat try ke baad 30 second ka lockout.
 */
class PinStore(context: Context) {

    private val prefs = context.getSharedPreferences("gfchat_lock", Context.MODE_PRIVATE)

    val hasPin: Boolean get() = prefs.contains(KEY_HASH)

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIO, true)
        set(value) = prefs.edit().putBoolean(KEY_BIO, value).apply()

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_LOCK_UNTIL, 0L)
            .apply()
    }

    /** Remaining lockout in milliseconds (0 = not locked). */
    fun lockoutRemainingMs(now: Long = System.currentTimeMillis()): Long =
        (prefs.getLong(KEY_LOCK_UNTIL, 0L) - now).coerceAtLeast(0L)

    sealed interface Result {
        data object Success : Result
        data class Wrong(val triesLeft: Int) : Result
        data class LockedOut(val remainingMs: Long) : Result
    }

    fun verify(pin: String): Result {
        val remaining = lockoutRemainingMs()
        if (remaining > 0) return Result.LockedOut(remaining)

        val salt = Base64.decode(prefs.getString(KEY_SALT, null) ?: return Result.Wrong(0), Base64.NO_WRAP)
        val expected = Base64.decode(prefs.getString(KEY_HASH, null) ?: return Result.Wrong(0), Base64.NO_WRAP)

        return if (MessageDigest.isEqual(expected, hash(pin, salt))) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0L).apply()
            Result.Success
        } else {
            val fails = prefs.getInt(KEY_FAILS, 0) + 1
            if (fails >= MAX_TRIES) {
                prefs.edit()
                    .putInt(KEY_FAILS, 0)
                    .putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + LOCKOUT_MS)
                    .apply()
                Result.LockedOut(LOCKOUT_MS)
            } else {
                prefs.edit().putInt(KEY_FAILS, fails).apply()
                Result.Wrong(MAX_TRIES - fails)
            }
        }
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    companion object {
        const val PIN_LENGTH = 4
        private const val MAX_TRIES = 5
        private const val LOCKOUT_MS = 30_000L
        private const val KEY_SALT = "salt"
        private const val KEY_HASH = "hash"
        private const val KEY_FAILS = "fails"
        private const val KEY_LOCK_UNTIL = "lock_until"
        private const val KEY_BIO = "bio_enabled"
    }
}
