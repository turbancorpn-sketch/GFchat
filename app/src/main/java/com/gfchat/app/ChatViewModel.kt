package com.gfchat.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gfchat.app.data.ChatMessage
import com.gfchat.app.data.Outcome
import com.gfchat.app.data.SessionStore
import com.gfchat.app.data.SupabaseApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Har 3 second me naye messages check karta hai (asli push notification Stage 3 me aayegi). */
private const val POLL_MS = 3000L

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val api = SupabaseApi(SessionStore(app))

    val messages = mutableStateListOf<ChatMessage>()
    val signedUrls = mutableStateMapOf<Long, String>()
    var error by mutableStateOf<String?>(null)
        private set
    var uploading by mutableStateOf(false)
        private set
    private var lastId = 0L
    private var polling = false
    private var coupleId: String? = null
    private val urlsBeingFetched = mutableSetOf<Long>()

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

    /** Gallery se chuni photo/video private storage me upload karke partner ko bhejta hai. */
    fun sendMedia(uri: Uri, isVideo: Boolean) {
        viewModelScope.launch {
            uploading = true
            error = null
            val cid = coupleId ?: when (val r = api.myCoupleId()) {
                is Outcome.Ok -> r.value.also { coupleId = it }
                is Outcome.Err -> {
                    error = r.message; uploading = false; return@launch
                }
            }

            val resolver = getApplication<Application>().contentResolver
            val mime = resolver.getType(uri) ?: if (isVideo) "video/mp4" else "image/jpeg"
            val ext = mime.substringAfter('/', if (isVideo) "mp4" else "jpg")
            val bytes = withContext(Dispatchers.IO) {
                runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) {
                error = "File nahi padh paya."; uploading = false; return@launch
            }

            val path = "$cid/${UUID.randomUUID()}.$ext"
            when (val up = api.uploadMedia(path, bytes, mime)) {
                is Outcome.Err -> error = up.message
                is Outcome.Ok -> when (val sent = api.sendMessage(if (isVideo) "video" else "photo", path)) {
                    is Outcome.Ok -> {
                        messages.add(sent.value)
                        if (sent.value.id > lastId) lastId = sent.value.id
                    }
                    is Outcome.Err -> error = sent.message
                }
            }
            uploading = false
        }
    }

    /** Message ki photo/video dikhane ke liye signed URL (cache karke, dobara nahi maangta). */
    fun signedUrlFor(message: ChatMessage): String? {
        signedUrls[message.id]?.let { return it }
        val body = message.body
        if (body.isNullOrBlank() || !urlsBeingFetched.add(message.id)) return null
        viewModelScope.launch {
            when (val r = api.createSignedUrl(body)) {
                is Outcome.Ok -> signedUrls[message.id] = r.value
                is Outcome.Err -> Unit
            }
            urlsBeingFetched.remove(message.id)
        }
        return null
    }
}
