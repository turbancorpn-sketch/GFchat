package com.gfchat.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gfchat.app.data.ChatMessage
import com.gfchat.app.data.Outcome
import com.gfchat.app.data.SessionStore
import com.gfchat.app.data.SupabaseApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Har 3 second me naye messages check karta hai (asli push notification Stage 3 me aayegi). */
private const val POLL_MS = 3000L

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val api = SupabaseApi(SessionStore(app))

    val messages = mutableStateListOf<ChatMessage>()
    var error by mutableStateOf<String?>(null)
        private set
    private var lastId = 0L
    private var polling = false

    fun startPolling() {
        if (polling) return
        polling = true
        viewModelScope.launch {
            while (polling) {
                poll()
                delay(POLL_MS)
            }
        }
    }

    fun stopPolling() {
        polling = false
    }

    private suspend fun poll() {
        when (val r = api.fetchMessages(lastId)) {
            is Outcome.Ok -> {
                if (r.value.isNotEmpty()) {
                    // Status (ticks) update ho sakta hai purane messages ka bhi, isliye merge karo.
                    r.value.forEach { incoming ->
                        val idx = messages.indexOfFirst { it.id == incoming.id }
                        if (idx >= 0) messages[idx] = incoming else messages.add(incoming)
                    }
                    lastId = r.value.maxOf { it.id }
                }
                if (messages.any { !it.fromMe }) api.markRead()
                error = null
            }
            is Outcome.Err -> if (!r.offline) error = r.message
        }
    }

    fun send(text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        viewModelScope.launch {
            when (val r = api.sendMessage("text", body)) {
                is Outcome.Ok -> {
                    messages.add(r.value)
                    if (r.value.id > lastId) lastId = r.value.id
                }
                is Outcome.Err -> error = r.message
            }
        }
    }
}
