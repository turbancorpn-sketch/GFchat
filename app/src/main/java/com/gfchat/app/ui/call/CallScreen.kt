package com.gfchat.app.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gfchat.app.call.CallState
import com.gfchat.app.call.CallViewModel
import kotlinx.coroutines.delay

@Composable
fun CallScreen(vm: CallViewModel, partnerName: String) {
    val state = vm.state

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

    Surface(Modifier.fillMaxSize(), color = Color(0xFF121212)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))
            Box(
                modifier = Modifier.size(120.dp).background(Color(0xFF3A3A3A), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(partnerName, color = Color.White, fontSize = 26.sp)
            Spacer(Modifier.height(8.dp))
            Text(statusLabel(state, elapsedSec), color = Color(0xFFBBBBBB), fontSize = 16.sp)

            Spacer(Modifier.weight(1f))

            when (state) {
                is CallState.Ringing -> IncomingCallControls(vm)
                is CallState.Outgoing, is CallState.Connected -> InCallControls(vm)
                else -> Unit
            }
            Spacer(Modifier.height(32.dp))
        }
    }
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        RoundButton(color = Color(0xFFE53935), onClick = vm::declineCall) {
            Icon(Icons.Filled.CallEnd, contentDescription = "Decline", tint = Color.White)
        }
        RoundButton(color = Color(0xFF43A047), onClick = vm::acceptCall) {
            Icon(Icons.Filled.Call, contentDescription = "Accept", tint = Color.White)
        }
    }
}

@Composable
private fun InCallControls(vm: CallViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        RoundButton(color = if (vm.muted) Color.White else Color(0xFF3A3A3A), onClick = vm::toggleMute) {
            Icon(
                if (vm.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = "Mute",
                tint = if (vm.muted) Color.Black else Color.White,
            )
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
