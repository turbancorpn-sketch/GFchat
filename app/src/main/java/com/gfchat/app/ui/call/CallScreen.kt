package com.gfchat.app.ui.call

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.gfchat.app.call.CallState
import com.gfchat.app.call.CallViewModel
import com.gfchat.app.util.CallRingtone
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import kotlin.math.roundToInt

@Composable
fun CallScreen(vm: CallViewModel, partnerName: String) {
    val state = vm.state
    val context = LocalContext.current

    LaunchedEffect(state is CallState.Ringing) {
        if (state is CallState.Ringing) CallRingtone.start(context) else CallRingtone.stop()
    }
    DisposableEffect(Unit) { onDispose { CallRingtone.stop() } }

    LaunchedEffect(state) {
        if (state is CallState.Ended) {
            delay(2000)
            vm.dismissEnded()
        }
    }

    var elapsedSec by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state) {
        val connected = state as? CallState.Connected ?: return@LaunchedEffect
        while (true) {
            elapsedSec = (System.currentTimeMillis() - connected.startedAtMs) / 1000
            delay(1000)
        }
    }

    val showVideo = when (state) {
        is CallState.Connected -> state.video
        is CallState.Outgoing -> state.video
        is CallState.Ringing -> state.video
        else -> false
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF121212)) {
        Box(Modifier.fillMaxSize()) {
            if (showVideo) {
                vm.remoteVideoTrack?.let { track ->
                    VideoRenderer(track = track, eglBaseContext = vm.eglBaseContext, modifier = Modifier.fillMaxSize())
                }
                vm.localVideoTrack?.let { track ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(width = 110.dp, height = 150.dp)
                            .background(Color(0xFF2A2A2A), RoundedCornerShape(12.dp)),
                    ) {
                        VideoRenderer(track = track, eglBaseContext = vm.eglBaseContext, modifier = Modifier.fillMaxSize())
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(64.dp))
                if (!showVideo || vm.remoteVideoTrack == null) {
                    Box(
                        modifier = Modifier.size(120.dp).background(Color(0xFF3A3A3A), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                }
                Text(partnerName, color = Color.White, fontSize = 26.sp)
                Spacer(Modifier.height(8.dp))
                Text(statusLabel(state, elapsedSec), color = Color(0xFFBBBBBB), fontSize = 16.sp)

                Spacer(Modifier.weight(1f))

                when (state) {
                    is CallState.Ringing -> IncomingCallControls(vm)
                    is CallState.Outgoing, is CallState.Connected -> InCallControls(vm, video = showVideo)
                    else -> Unit
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun VideoRenderer(track: VideoTrack, eglBaseContext: org.webrtc.EglBase.Context, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                init(eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                track.addSink(this)
            }
        },
        onRelease = { renderer ->
            track.removeSink(renderer)
            renderer.release()
        },
    )
}

private fun statusLabel(state: CallState, elapsedSec: Long): String = when (state) {
    CallState.Idle -> ""
    is CallState.Outgoing -> "Ring ho raha hai…"
    is CallState.Ringing -> "Incoming call…"
    is CallState.Connected -> "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60)
    is CallState.Ended -> state.reason
}

@Composable
private fun IncomingCallControls(vm: CallViewModel) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundButton(color = Color(0xFFE53935), onClick = vm::declineCall) {
                Icon(Icons.Filled.CallEnd, contentDescription = "Decline", tint = Color.White)
            }
        }
        Spacer(Modifier.height(24.dp))
        SlideToAnswer(onAnswer = vm::acceptCall)
    }
}

@Composable
private fun SlideToAnswer(onAnswer: () -> Unit) {
    var dragPx by remember { mutableFloatStateOf(0f) }
    var answered by remember { mutableFloatStateOf(0f) } // 0 = not yet, 1 = fired
    val thumbSize = 56.dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color(0xFF2E7D32).copy(alpha = 0.35f), RoundedCornerShape(32.dp)),
        contentAlignment = Alignment.CenterStart,
    ) {
        val maxPx = with(androidx.compose.ui.platform.LocalDensity.current) { (maxWidth - thumbSize).toPx() }.coerceAtLeast(1f)
        Text(
            "Slide karke answer karo →",
            color = Color.White.copy(alpha = (1f - dragPx / maxPx).coerceIn(0.2f, 1f)),
            modifier = Modifier.align(Alignment.Center),
        )
        Box(
            modifier = Modifier
                .padding(4.dp)
                .size(thumbSize)
                .align(Alignment.CenterStart)
                .offset(dragPx)
                .background(Color(0xFF43A047), CircleShape)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragPx >= maxPx * 0.8f && answered == 0f) {
                                answered = 1f
                                onAnswer()
                            } else {
                                dragPx = 0f
                            }
                        },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            dragPx = (dragPx + delta).coerceIn(0f, maxPx)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Call, contentDescription = "Accept", tint = Color.White)
        }
    }
}

private fun Modifier.offset(px: Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(px.roundToInt(), 0)
        }
    },
)

@Composable
private fun InCallControls(vm: CallViewModel, video: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        RoundButton(color = if (vm.muted) Color.White else Color(0xFF3A3A3A), onClick = vm::toggleMute) {
            Icon(
                if (vm.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = "Mute",
                tint = if (vm.muted) Color.Black else Color.White,
            )
        }
        if (video) {
            RoundButton(color = if (!vm.cameraOn) Color.White else Color(0xFF3A3A3A), onClick = vm::toggleCamera) {
                Icon(
                    if (vm.cameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                    contentDescription = "Camera",
                    tint = if (!vm.cameraOn) Color.Black else Color.White,
                )
            }
        }
        RoundButton(color = Color(0xFFE53935), onClick = vm::endCall) {
            Icon(Icons.Filled.CallEnd, contentDescription = "End call", tint = Color.White)
        }
        RoundButton(color = if (vm.speakerOn) Color.White else Color(0xFF3A3A3A), onClick = vm::toggleSpeaker) {
            Icon(
                Icons.Filled.VolumeUp,
                contentDescription = "Speaker",
                tint = if (vm.speakerOn) Color.Black else Color.White,
            )
        }
    }
}

@Composable
private fun RoundButton(color: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(64.dp).background(color, CircleShape),
    ) { content() }
}
