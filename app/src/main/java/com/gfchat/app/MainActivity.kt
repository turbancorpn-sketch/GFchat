package com.gfchat.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.gfchat.app.security.BiometricHelper
import com.gfchat.app.security.PinStore
import com.gfchat.app.ui.call.CallScreen
import com.gfchat.app.ui.chat.ChatScreen
import com.gfchat.app.ui.lock.LockScreen
import com.gfchat.app.ui.theme.GFchatTheme

/** Is naam ko apni girlfriend ke naam se badal do. */
private const val PARTNER_NAME = "My Love"

/** App background se itni der baad wapas aaye to dobara lock. (Photo picker ke liye chhoti grace.) */
private const val RELOCK_AFTER_MS = 15_000L

class MainActivity : FragmentActivity() {

    private lateinit var pinStore: PinStore
    private var locked by mutableStateOf(true)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Screenshot aur recent-apps preview band.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        pinStore = PinStore(applicationContext)

        setContent {
            GFchatTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GFchatApp()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt != 0L && System.currentTimeMillis() - stoppedAt > RELOCK_AFTER_MS) {
            locked = true
        }
    }

    @Composable
    private fun GFchatApp() {
        // null = call nahi chal rahi, true = video call, false = audio call
        var callIsVideo by remember { mutableStateOf<Boolean?>(null) }

        Box(Modifier.fillMaxSize()) {
            // Chat hamesha compose rehti hai taaki lock hone par bhi state na jaaye;
            // lock screen uske upar pura dhak deti hai.
            val video = callIsVideo
            if (video == null) {
                ChatScreen(partnerName = PARTNER_NAME, onCall = { callIsVideo = it })
            } else {
                CallScreen(partnerName = PARTNER_NAME, video = video, onEnd = { callIsVideo = null })
            }

            if (locked) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LockScreen(
                        pinStore = pinStore,
                        biometricAvailable = BiometricHelper.isAvailable(this@MainActivity),
                        onFingerprintClick = {
                            BiometricHelper.prompt(this@MainActivity, onSuccess = { locked = false })
                        },
                        onUnlocked = { locked = false },
                    )
                }
            }
        }
    }
}
