package com.gfchat.app

import android.content.Intent
import android.net.Uri
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gfchat.app.ui.auth.LoadErrorScreen
import com.gfchat.app.ui.auth.LoadingScreen
import com.gfchat.app.ui.auth.LoginScreen
import com.gfchat.app.ui.auth.NotConfiguredScreen
import com.gfchat.app.ui.auth.PairingScreen
import com.gfchat.app.ui.auth.UpdateRequiredScreen
import com.gfchat.app.ui.admin.AdminScreen
import com.gfchat.app.ui.call.CallScreen
import com.gfchat.app.ui.chat.ChatScreen
import com.gfchat.app.ui.lock.LockScreen
import com.gfchat.app.ui.theme.GFchatTheme
import com.gfchat.app.ui.theme.ThemeMode
import com.gfchat.app.ui.theme.ThemeStore

/** Is naam ko apni girlfriend ke naam se badal do. */
private const val PARTNER_NAME = "My Love"

/** App background se itni der baad wapas aaye to dobara lock. (Photo picker ke liye chhoti grace.) */
private const val RELOCK_AFTER_MS = 15_000L

class MainActivity : FragmentActivity() {

    private lateinit var pinStore: PinStore
    private lateinit var themeStore: ThemeStore
    private var locked by mutableStateOf(true)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Screenshot aur recent-apps preview band.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        pinStore = PinStore(applicationContext)
        themeStore = ThemeStore(applicationContext)

        setContent {
            var themeMode by remember { mutableStateOf(themeStore.mode) }
            GFchatTheme(mode = themeMode) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GFchatApp(
                        themeMode = themeMode,
                        onToggleTheme = { themeMode = themeStore.next() },
                    )
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
    private fun GFchatApp(themeMode: ThemeMode, onToggleTheme: () -> Unit) {
        // null = call nahi chal rahi, true = video call, false = audio call
        var callIsVideo by remember { mutableStateOf<Boolean?>(null) }
        var showAdmin by remember { mutableStateOf(false) }
        val vm: AuthViewModel = viewModel()

        Box(Modifier.fillMaxSize()) {
            // Neeche ki screen hamesha compose rehti hai taaki lock hone par bhi state na jaaye;
            // lock screen uske upar pura dhak deti hai.
            when (val s = vm.screen) {
                AppScreen.Loading -> LoadingScreen()
                AppScreen.NotConfigured -> NotConfiguredScreen()
                is AppScreen.UpdateRequired -> UpdateRequiredScreen(onUpdate = {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.downloadUrl)))
                })
                is AppScreen.LoadError -> LoadErrorScreen(s.message, onRetry = { vm.refreshStatus() })
                AppScreen.SignedOut -> LoginScreen(
                    busy = vm.busy,
                    error = vm.error,
                    info = vm.info,
                    onSignIn = vm::signIn,
                    onSignUp = vm::signUp,
                    onClear = vm::clearMessages,
                )
                is AppScreen.Pairing -> PairingScreen(
                    waitingCode = s.waitingCode,
                    busy = vm.busy,
                    error = vm.error,
                    email = vm.email,
                    onCreateCode = vm::createCode,
                    onJoin = vm::joinWithCode,
                    onPoll = { vm.refreshStatus(silent = true) },
                    onSignOut = vm::signOut,
                )
                AppScreen.Ready -> {
                    val video = callIsVideo
                    when {
                        showAdmin -> AdminScreen(
                            config = vm.adminConfig,
                            busy = vm.busy,
                            error = vm.error,
                            info = vm.info,
                            onLoad = vm::loadAdminConfig,
                            onSetMinVersion = vm::setMinVersion,
                            onBack = { showAdmin = false; vm.clearMessages() },
                        )
                        video == null -> ChatScreen(
                            partnerName = PARTNER_NAME,
                            isAdmin = vm.isAdmin,
                            themeMode = themeMode,
                            onCall = { callIsVideo = it },
                            onAdmin = { showAdmin = true },
                            onToggleTheme = onToggleTheme,
                        )
                        else -> CallScreen(partnerName = PARTNER_NAME, video = video, onEnd = { callIsVideo = null })
                    }
                }
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
