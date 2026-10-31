package com.gfchat.app.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Supabase Realtime (websocket) se "kuch badla hai" ka turant signal leta hai,
 * taaki naye message/tick poll ke agle turn (kuch second) ka wait na karna pade.
 *
 * Jaanboojh kar simple rakha hai: yeh sirf ek "jaago aur poll karo" trigger hai.
 * Asli data hamesha wahi verified fetch_messages() RPC se aata hai (ChatViewModel
 * ka poll()), isliye is socket ka koi bhi adhoora/galat payload bhi safe hai —
 * zyada se zyada ek extra poll ho jayega. Agar socket kabhi connect/reconnect na
 * ho paaye, normal polling pehle jaisa hi chalta rehta hai (koi functionality nahi
 * tootegi, bas update thoda der se aayenge).
 */
class RealtimeClient(
    private val wsUrl: String,
    private val anonKey: String,
    private val tokenProvider: suspend () -> String?,
    private val onChange: () -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val refCounter = AtomicInteger(1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private var heartbeatJob: Job? = null
    private var socket: WebSocket? = null
    private var running = false

    fun start() {
        if (running) return
        running = true
        loopJob = scope.launch { connectLoop() }
    }

    fun stop() {
        running = false
        loopJob?.cancel()
        heartbeatJob?.cancel()
        socket?.close(1000, null)
        socket = null
    }

    private suspend fun connectLoop() {
        while (running) {
            val token = runCatching { tokenProvider() }.getOrNull()
            if (token.isNullOrBlank()) {
                delay(5_000L)
                continue
            }

            val ended = CompletableDeferred<Unit>()
            val req = Request.Builder().url("$wsUrl?apikey=$anonKey&vsn=1.0.0").build()
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    sendJoin(webSocket, token)
                    startHeartbeat(webSocket)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("\"event\":\"postgres_changes\"")) onChange()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!ended.isCompleted) ended.complete(Unit)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!ended.isCompleted) ended.complete(Unit)
                }
            }
            socket = client.newWebSocket(req, listener)

            // Access token thodi der me expire ho sakta hai — har 10 minute me khud
            // reconnect karke naya (refreshed) token bhejte hain.
            val refreshJob = scope.launch {
                delay(10 * 60_000L)
                runCatching { socket?.close(1000, "refresh") }
            }
            ended.await()
            refreshJob.cancel()
            heartbeatJob?.cancel()
            socket = null
            if (running) delay(3_000L)
        }
    }

    private fun sendJoin(webSocket: WebSocket, token: String) {
        val join = JSONObject().apply {
            put("topic", "realtime:public:messages")
            put("event", "phx_join")
            put(
                "payload",
                JSONObject().apply {
                    put(
                        "config",
                        JSONObject().apply {
                            put(
                                "postgres_changes",
                                JSONArray().put(
                                    JSONObject().apply {
                                        put("event", "*")
                                        put("schema", "public")
                                        put("table", "messages")
                                    },
                                ),
                            )
                        },
                    )
                    put("access_token", token)
                },
            )
            put("ref", refCounter.getAndIncrement().toString())
        }
        webSocket.send(join.toString())
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(25_000L)
                val hb = JSONObject().apply {
                    put("topic", "phoenix")
                    put("event", "heartbeat")
                    put("payload", JSONObject())
                    put("ref", refCounter.getAndIncrement().toString())
                }
                runCatching { webSocket.send(hb.toString()) }
            }
        }
    }
}
