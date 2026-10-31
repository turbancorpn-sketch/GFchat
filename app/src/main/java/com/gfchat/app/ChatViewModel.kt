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

/** Jitna chhota, utni jaldi dono phones sync honge (asli push notification abhi nahi hai). */
private const val POLL_MS = 1200L

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val api = SupabaseApi(SessionStore(app))

    val messages = mutableStateListOf<ChatMessage>()
    val signedUrls = mutableStateMapOf<Long, String>()
    var error by mutableStateOf<String?>(null)
        private set
    var uploading by mutableStateOf(false)
        private set
    private var lastSyncedAt: String? = null
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
        when (val r = api.fetchMessages(lastSyncedAt)) {
            is Outcome.Ok -> {
                r.value.forEach { incoming ->
                    val idx = messages.indexOfFirst { it.id == incoming.id }
                    if (idx >= 0) messages[idx] = incoming else messages.add(incoming)
                }
                r.value.maxOfOrNull { it.updatedAt }?.let { latest ->
                    if (lastSyncedAt == null || latest > lastSyncedAt!!) lastSyncedAt = latest
                }
                if (messages.any { !it.fromMe && !it.deleted }) api.markRead()
                error = null
            }
            is Outcome.Err -> if (!r.offline) error = r.message
        }
    }

    private fun track(m: ChatMessage) {
        val idx = messages.indexOfFirst { it.id == m.id }
        if (idx >= 0) messages[idx] = m else messages.add(m)
        if (lastSyncedAt == null || m.updatedAt > lastSyncedAt!!) lastSyncedAt = m.updatedAt
    }

    fun send(text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        viewModelScope.launch {
            when (val r = api.sendMessage("text", body)) {
                is Outcome.Ok -> track(r.value)
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
                    is Outcome.Ok -> track(sent.value)
                    is Outcome.Err -> error = sent.message
                }
            }
            uploading = false
        }
    }

    /** "Delete for everyone" — apna hi message, dono phones se turant hat jaata hai. */
    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            when (val r = api.deleteMessage(id)) {
                is Outcome.Ok -> {
                    val idx = messages.indexOfFirst { it.id == id }
                    if (idx >= 0) messages[idx] = messages[idx].copy(body = null, deleted = true)
                }
                is Outcome.Err -> error = r.message
            }
        }
    }

    /** Poori chat dono phones se clear kar deta hai. */
    fun clearChat() {
        viewModelScope.launch {
            when (val r = api.clearChat()) {
                is Outcome.Ok -> {
                    messages.clear()
                    signedUrls.clear()
                    lastSyncedAt = null
                }
                is Outcome.Err -> error = r.message
            }
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
