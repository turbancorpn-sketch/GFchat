package com.gfchat.app.util

import android.content.Context
import android.net.Uri

private const val PREFS_NAME = "notification_prefs"
private const val KEY_TONE_URI = "tone_uri"

/** User ne jo custom notification tone chuni hai usko yaad rakhta hai (device pe hi, plain prefs). */
object NotificationPrefs {

    fun getToneUri(context: Context): Uri? {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TONE_URI, null)
        return saved?.let { runCatching { Uri.parse(it) }.getOrNull() }
    }

    fun setToneUri(context: Context, uri: Uri?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_TONE_URI, uri?.toString())
            .apply()
    }
}
