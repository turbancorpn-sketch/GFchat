package com.gfchat.app.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Stage 1: sirf audio/video call ki UI (mute, speaker, camera, end, timer).
 * Asli call (WebRTC) Stage 3 me aayegi, isme abhi koi awaaz ya video nahi jaati.
 */
@Composable
fun CallScreen(partnerName: String, video: Boolean, onEnd: () -> Unit) {
    var seconds by remember { mutableIntStateOf(0) }
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(video) }
    var cameraOn by remember { mutableStateOf(video) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds++
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(48.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(partnerName.take(1).uppercase(), fontSize = 52.sp, color = Color.White)
            }
            Spacer(Modifier.height(16.dp))
            Text(partnerName, fontSize = 28.sp)
            Text(
                if (video) "Demo video call (abhi video/awaaz nahi jayegi)"
                else "Demo call (abhi awaaz nahi jayegi)",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Text("%02d:%02d".format(seconds / 60, seconds % 60), fontSize = 20.sp)
            if (video) {
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .size(width = 160.dp, height = 200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (cameraOn) "Aapka camera preview\n(Stage 3)" else "Camera off",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(
                color = if (muted) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                onClick = { muted = !muted },
            ) { Icon(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, "Mute") }
            if (video) {
                RoundButton(
                    color = if (!cameraOn) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                    onClick = { cameraOn = !cameraOn },
                ) { Icon(if (cameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff, "Camera") }
            }
            RoundButton(color = Color(0xFFD32F2F), onClick = onEnd) {
                Icon(Icons.Filled.CallEnd, "End call", tint = Color.White)
            }
            RoundButton(
                color = if (speaker) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                onClick = { speaker = !speaker },
            ) { Icon(Icons.Filled.VolumeUp, "Speaker") }
        }
    }
}

@Composable
private fun RoundButton(color: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
