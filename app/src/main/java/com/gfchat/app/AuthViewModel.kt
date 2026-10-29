package com.gfchat.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gfchat.app.BuildConfig
import com.gfchat.app.data.AppConfig
import com.gfchat.app.data.Outcome
import com.gfchat.app.data.PairState
import com.gfchat.app.data.SessionStore
import com.gfchat.app.data.SignUpResult
import com.gfchat.app.data.SupabaseApi
import kotlinx.coroutines.launch

sealed interface AppScreen {
    data object Loading : AppScreen
    data object NotConfigured : AppScreen
    data class UpdateRequired(val downloadUrl: String) : AppScreen
    data object SignedOut : AppScreen
    /** waitingCode != null: humne code banaya hai aur partner ke judne ka intezaar hai. */
    data class Pairing(val waitingCode: String?) : AppScreen
    data object Ready : AppScreen
    data class LoadError(val message: String) : AppScreen
}

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionStore(app)
    private val api = SupabaseApi(session)

    var screen by mutableStateOf<AppScreen>(AppScreen.Loading)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var info by mutableStateOf<String?>(null)
        private set
    var isAdmin by mutableStateOf(false)
        private set
    var adminConfig by mutableStateOf<AppConfig?>(null)
        private set

    val email: String? get() = session.email

    init {
        if (!api.configured) {
            screen = AppScreen.NotConfigured
        } else {
            viewModelScope.launch {
                // Force update check sabse pehle, login se bhi pehle.
                when (val r = api.fetchAppConfig()) {
                    is Outcome.Ok -> if (BuildConfig.VERSION_CODE < r.value.minVersionCode) {
                        screen = AppScreen.UpdateRequired(r.value.downloadUrl)
                        return@launch
                    }
                    is Outcome.Err -> Unit // Config check fail ho to app ko block mat karo.
                }
                if (!session.hasSession) screen = AppScreen.SignedOut else refreshStatus()
            }
        }
    }

    fun clearMessages() {
        error = null
        info = null
    }

    fun refreshStatus(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) screen = AppScreen.Loading
            when (val r = api.pairStatus()) {
                is Outcome.Ok -> screen = when (val s = r.value) {
                    is PairState.Paired -> {
                        isAdmin = s.isAdmin
                        AppScreen.Ready
                    }
                    is PairState.Waiting -> AppScreen.Pairing(s.code)
                    PairState.None -> AppScreen.Pairing(null)
                }
                is Outcome.Err -> when {
                    r.authExpired -> screen = AppScreen.SignedOut
                    // Offline: agar pehle paired the to chat khol do.
                    r.offline && session.pairedCached -> screen = AppScreen.Ready
                    silent -> Unit
                    else -> screen = AppScreen.LoadError(r.message)
                }
            }
        }
    }

    fun signIn(email: String, password: String) = authCall(email, password) {
        when (val r = api.signIn(email.trim(), password)) {
            is Outcome.Ok -> refreshStatus()
            is Outcome.Err -> error = r.message
        }
    }

    fun signUp(email: String, password: String) = authCall(email, password) {
        when (val r = api.signUp(email.trim(), password)) {
            is Outcome.Ok -> when (r.value) {
                SignUpResult.SignedIn -> refreshStatus()
                SignUpResult.ConfirmEmail ->
                    info = "Email par confirmation link gaya hai. Link khol ke wapas aao aur login karo."
            }
            is Outcome.Err -> error = r.message
        }
    }

    private fun authCall(email: String, password: String, block: suspend () -> Unit) {
        clearMessages()
        if (!email.contains("@") || email.trim().length < 5) {
            error = "Sahi email daalo."; return
        }
        if (password.length < 8) {
            error = "Password kam se kam 8 character ka rakho."; return
        }
        viewModelScope.launch {
            busy = true
            block()
            busy = false
        }
    }

    fun createCode() {
        clearMessages()
        viewModelScope.launch {
            busy = true
            when (val r = api.createPairCode()) {
                is Outcome.Ok -> screen = AppScreen.Pairing(r.value)
                is Outcome.Err -> if (r.authExpired) screen = AppScreen.SignedOut else error = r.message
            }
            busy = false
        }
    }

    fun joinWithCode(code: String) {
        clearMessages()
        if (code.trim().length < 8) {
            error = "8 character ka code daalo."; return
        }
        viewModelScope.launch {
            busy = true
            when (val r = api.joinPairCode(code)) {
                is Outcome.Ok -> screen = AppScreen.Ready
                is Outcome.Err -> if (r.authExpired) screen = AppScreen.SignedOut else error = r.message
            }
            busy = false
        }
    }

    fun signOut() {
        api.signOut()
        clearMessages()
        isAdmin = false
        screen = AppScreen.SignedOut
    }

    fun loadAdminConfig() {
        clearMessages()
        viewModelScope.launch {
            busy = true
            when (val r = api.fetchAppConfig()) {
                is Outcome.Ok -> adminConfig = r.value
                is Outcome.Err -> error = r.message
            }
            busy = false
        }
    }

    fun setMinVersion(minVersionCode: Int, downloadUrl: String?) {
        clearMessages()
        viewModelScope.launch {
            busy = true
            when (val r = api.adminSetMinVersion(minVersionCode, downloadUrl)) {
                is Outcome.Ok -> {
                    info = "Force update set ho gaya."
                    loadAdminConfig()
                }
                is Outcome.Err -> if (r.authExpired) screen = AppScreen.SignedOut else error = r.message
            }
            busy = false
        }
    }
}
