package com.gfchat.app.call

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gfchat.app.data.CallSignal
import com.gfchat.app.data.Outcome
import com.gfchat.app.data.RealtimeClient
import com.gfchat.app.data.SessionStore
import com.gfchat.app.data.SupabaseApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Abhi sirf audio call — video phase 2 me aayega (yahi state/signaling reuse hoga). */
sealed interface CallState {
    data object Idle : CallState
    data class Outgoing(val video: Boolean) : CallState
    data class Ringing(val video: Boolean) : CallState
    data class Connected(val video: Boolean, val startedAtMs: Long) : CallState
    data class Ended(val reason: String) : CallState
}

private const val CALL_POLL_MS = 1500L

class CallViewModel(app: Application) : AndroidViewModel(app) {

    private val api = SupabaseApi(SessionStore(app))

    var state by mutableStateOf<CallState>(CallState.Idle)
        private set
    var muted by mutableStateOf(false)
        private set
    var speakerOn by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var listening = false
    private var sinceTs: String? = null
    private var realtime: RealtimeClient? = null
    private var pollInFlight = false

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var offerSdp: String? = null
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false

    private val audioManager: AudioManager
        get() = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Chat screen khulte hi shuru hota hai — taaki partner call kare to ring ho jaaye. */
    fun startListening() {
        if (listening) return
        listening = true
        viewModelScope.launch {
            while (listening) {
                poll()
                delay(CALL_POLL_MS)
            }
        }
        realtime = RealtimeClient(
            wsUrl = api.realtimeWsUrl,
            anonKey = api.publicAnonKey,
            table = "call_signals",
            tokenProvider = { api.currentAccessToken() },
            onChange = { viewModelScope.launch { poll() } },
        )
        realtime?.start()
    }

    fun stopListening() {
        listening = false
        realtime?.stop()
    }

    private suspend fun poll() {
        if (pollInFlight) return
        pollInFlight = true
        try {
            when (val r = api.fetchCallSignals(sinceTs)) {
                is Outcome.Ok -> r.value.forEach { handleSignal(it) }
                is Outcome.Err -> Unit // call signaling best-effort hai, chat polling alag se error dikhayega
            }
        } finally {
            pollInFlight = false
        }
    }

    private fun handleSignal(signal: CallSignal) {
        if (sinceTs == null || signal.createdAt > sinceTs!!) sinceTs = signal.createdAt
        val payload = runCatching { JSONObject(signal.payload) }.getOrNull() ?: return
        when (signal.kind) {
            "offer" -> {
                if (state is CallState.Idle) {
                    offerSdp = payload.optString("sdp")
                    state = CallState.Ringing(payload.optBoolean("video", false))
                } else {
                    viewModelScope.launch { api.sendCallSignal("busy", "{}") }
                }
            }
            "answer" -> if (state is CallState.Outgoing) {
                viewModelScope.launch {
                    runCatching {
                        peerConnection?.setRemoteDescriptionSuspend(
                            SessionDescription(SessionDescription.Type.ANSWER, payload.optString("sdp")),
                        )
                        remoteDescriptionSet = true
                        flushPendingCandidates()
                        val video = (state as? CallState.Outgoing)?.video ?: false
                        state = CallState.Connected(video, System.currentTimeMillis())
                    }.onFailure { error = "Call connect nahi ho paayi." }
                }
            }
            "ice" -> {
                val candidate = IceCandidate(
                    payload.optString("sdpMid"),
                    payload.optInt("sdpMLineIndex"),
                    payload.optString("candidate"),
                )
                if (remoteDescriptionSet) peerConnection?.addIceCandidate(candidate)
                else pendingRemoteCandidates.add(candidate)
            }
            "end", "reject", "busy" -> if (state !is CallState.Idle) {
                val reason = when (signal.kind) {
                    "reject" -> "Call decline kar di gayi."
                    "busy" -> "Partner kisi aur call pe hai."
                    else -> "Call khatam ho gayi."
                }
                cleanup()
                state = CallState.Ended(reason)
            }
        }
    }

    fun startCall(video: Boolean = false) {
        if (state !is CallState.Idle) return
        state = CallState.Outgoing(video)
        viewModelScope.launch {
            runCatching {
                val pc = ensurePeerConnection()
                attachLocalAudio(pc)
                val offer = pc.createOfferSuspend(MediaConstraints())
                pc.setLocalDescriptionSuspend(offer)
                api.sendCallSignal(
                    "offer",
                    JSONObject().put("sdp", offer.description).put("video", video).toString(),
                )
            }.onFailure {
                error = "Call shuru nahi ho paayi."
                cleanup()
                state = CallState.Idle
            }
        }
    }

    fun acceptCall() {
        val ringing = state as? CallState.Ringing ?: return
        val offer = offerSdp ?: return
        viewModelScope.launch {
            runCatching {
                val pc = ensurePeerConnection()
                pc.setRemoteDescriptionSuspend(SessionDescription(SessionDescription.Type.OFFER, offer))
                remoteDescriptionSet = true
                flushPendingCandidates()
                attachLocalAudio(pc)
                val answer = pc.createAnswerSuspend(MediaConstraints())
                pc.setLocalDescriptionSuspend(answer)
                api.sendCallSignal("answer", JSONObject().put("sdp", answer.description).toString())
                state = CallState.Connected(ringing.video, System.currentTimeMillis())
            }.onFailure {
                error = "Call accept nahi ho paayi."
                cleanup()
                state = CallState.Idle
            }
        }
    }

    fun declineCall() {
        if (state !is CallState.Ringing) return
        viewModelScope.launch { api.sendCallSignal("reject", "{}") }
        cleanup()
        state = CallState.Idle
    }

    fun endCall() {
        if (state is CallState.Idle) return
        viewModelScope.launch { api.sendCallSignal("end", "{}") }
        cleanup()
        state = CallState.Idle
    }

    /** "Call khatam" screen dikhane ke baad wapas chat pe aane ke liye. */
    fun dismissEnded() {
        if (state is CallState.Ended) state = CallState.Idle
    }

    fun toggleMute() {
        muted = !muted
        localAudioTrack?.setEnabled(!muted)
    }

    fun toggleSpeaker() {
        speakerOn = !speakerOn
        audioManager.isSpeakerphoneOn = speakerOn
    }

    private fun ensurePeerConnection(): PeerConnection {
        peerConnection?.let { return it }
        if (factory == null) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(getApplication<Application>().applicationContext)
                    .createInitializationOptions(),
            )
            factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        }
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            // Public demo TURN relay — abhi ke liye theek hai, baad me apna TURN server lagaya ja sakta hai.
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = speakerOn
        val pc = requireNotNull(factory).createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                viewModelScope.launch {
                    api.sendCallSignal(
                        "ice",
                        JSONObject()
                            .put("sdpMid", candidate.sdpMid)
                            .put("sdpMLineIndex", candidate.sdpMLineIndex)
                            .put("candidate", candidate.sdp)
                            .toString(),
                    )
                }
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                if (newState == PeerConnection.IceConnectionState.FAILED && state !is CallState.Idle) {
                    cleanup()
                    state = CallState.Ended("Connection toot gaya.")
                }
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>) {}
            override fun onAddStream(p0: MediaStream) {}
            override fun onRemoveStream(p0: MediaStream) {}
            override fun onDataChannel(p0: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver, p1: Array<out MediaStream>) {}
        })
        peerConnection = pc
        return requireNotNull(pc)
    }

    private fun attachLocalAudio(pc: PeerConnection) {
        if (localAudioTrack != null) return
        val f = requireNotNull(factory)
        val source = f.createAudioSource(MediaConstraints())
        val track = f.createAudioTrack("audio0", source)
        track.setEnabled(!muted)
        pc.addTrack(track, listOf("gfchat-call"))
        audioSource = source
        localAudioTrack = track
    }

    private fun flushPendingCandidates() {
        pendingRemoteCandidates.forEach { peerConnection?.addIceCandidate(it) }
        pendingRemoteCandidates.clear()
    }

    private fun cleanup() {
        runCatching { peerConnection?.close() }
        peerConnection = null
        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null
        runCatching { audioSource?.dispose() }
        audioSource = null
        offerSdp = null
        remoteDescriptionSet = false
        pendingRemoteCandidates.clear()
        muted = false
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
        cleanup()
        runCatching { factory?.dispose() }
        factory = null
    }
}

private suspend fun PeerConnection.createOfferSuspend(constraints: MediaConstraints): SessionDescription =
    suspendCancellableCoroutine { cont ->
        createOffer(
            object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    cont.resume(sdp)
                }
                override fun onCreateFailure(error: String?) {
                    cont.resumeWithException(IllegalStateException(error))
                }
                override fun onSetSuccess() {}
                override fun onSetFailure(error: String?) {}
            },
            constraints,
        )
    }

private suspend fun PeerConnection.createAnswerSuspend(constraints: MediaConstraints): SessionDescription =
    suspendCancellableCoroutine { cont ->
        createAnswer(
            object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    cont.resume(sdp)
                }
                override fun onCreateFailure(error: String?) {
                    cont.resumeWithException(IllegalStateException(error))
                }
                override fun onSetSuccess() {}
                override fun onSetFailure(error: String?) {}
            },
            constraints,
        )
    }

private suspend fun PeerConnection.setLocalDescriptionSuspend(sdp: SessionDescription) =
    suspendCancellableCoroutine<Unit> { cont ->
        setLocalDescription(
            object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {}
                override fun onCreateFailure(error: String?) {}
                override fun onSetSuccess() {
                    cont.resume(Unit)
                }
                override fun onSetFailure(error: String?) {
                    cont.resumeWithException(IllegalStateException(error))
                }
            },
            sdp,
        )
    }

private suspend fun PeerConnection.setRemoteDescriptionSuspend(sdp: SessionDescription) =
    suspendCancellableCoroutine<Unit> { cont ->
        setRemoteDescription(
            object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {}
                override fun onCreateFailure(error: String?) {}
                override fun onSetSuccess() {
                    cont.resume(Unit)
                }
                override fun onSetFailure(error: String?) {
                    cont.resumeWithException(IllegalStateException(error))
                }
            },
            sdp,
        )
    }
