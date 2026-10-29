package com.gfchat.app.ui.lock

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gfchat.app.security.PinStore
import kotlinx.coroutines.delay

private enum class Step { Enter, Create, Confirm }

/**
 * Pehli baar: secret code banao (2 baar daalna padta hai).
 * Uske baad: code se ya fingerprint se unlock.
 */
@Composable
fun LockScreen(
    pinStore: PinStore,
    biometricAvailable: Boolean,
    onFingerprintClick: () -> Unit,
    onUnlocked: () -> Unit,
    autoPromptFingerprint: Boolean = true,
) {
    var step by remember { mutableStateOf(if (pinStore.hasPin) Step.Enter else Step.Create) }
    var input by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var lockoutMs by remember { mutableLongStateOf(pinStore.lockoutRemainingMs()) }

    val showFingerprint = step == Step.Enter && biometricAvailable && pinStore.biometricEnabled

    LaunchedEffect(Unit) {
        if (showFingerprint && autoPromptFingerprint && lockoutMs == 0L) onFingerprintClick()
    }
    LaunchedEffect(lockoutMs) {
        while (lockoutMs > 0) {
            delay(500)
            lockoutMs = pinStore.lockoutRemainingMs()
        }
        if (lockoutMs == 0L && message?.startsWith("Bahut") == true) message = null
    }

    fun submit(pin: String) {
        when (step) {
            Step.Create -> {
                firstPin = pin; input = ""; message = null; step = Step.Confirm
            }
            Step.Confirm -> {
                if (pin == firstPin) {
                    pinStore.setPin(pin)
                    onUnlocked()
                } else {
                    input = ""; firstPin = ""; step = Step.Create
                    message = "Code match nahi hua. Dobara naya code banao."
                }
            }
            Step.Enter -> when (val r = pinStore.verify(pin)) {
                PinStore.Result.Success -> onUnlocked()
                is PinStore.Result.Wrong -> {
                    input = ""; message = "Galat code. ${r.triesLeft} try bache hain."
                }
                is PinStore.Result.LockedOut -> {
                    input = ""
                    lockoutMs = r.remainingMs
                    message = "Bahut zyada galat try. Thoda ruko."
                }
            }
        }
    }

    val title = when (step) {
        Step.Create -> "Apna secret code banao"
        Step.Confirm -> "Code dobara daalo"
        Step.Enter -> "Secret code daalo"
    }
    val locked = lockoutMs > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("GFchat", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(PinStore.PIN_LENGTH) { i ->
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < input.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                locked -> "Dobara try karo ${(lockoutMs + 999) / 1000} second baad"
                else -> message ?: " "
            },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { d ->
                    PadKey(enabled = !locked, onClick = {
                        if (input.length < PinStore.PIN_LENGTH) {
                            input += d
                            if (input.length == PinStore.PIN_LENGTH) submit(input)
                        }
                    }) { Text(d, fontSize = 26.sp) }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (showFingerprint) {
                PadKey(enabled = !locked, onClick = onFingerprintClick) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = "Fingerprint", modifier = Modifier.size(30.dp))
                }
            } else {
                Spacer(Modifier.size(72.dp))
            }
            PadKey(enabled = !locked, onClick = {
                if (input.length < PinStore.PIN_LENGTH) {
                    input += "0"
                    if (input.length == PinStore.PIN_LENGTH) submit(input)
                }
            }) { Text("0", fontSize = 26.sp) }
            PadKey(enabled = true, onClick = { if (input.isNotEmpty()) input = input.dropLast(1) }) {
                Icon(Icons.Filled.Backspace, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun PadKey(enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
