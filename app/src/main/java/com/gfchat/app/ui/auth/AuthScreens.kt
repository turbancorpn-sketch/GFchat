package com.gfchat.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Closer", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
fun LoadingScreen() = Page { CircularProgressIndicator() }

@Composable
fun NotConfiguredScreen() = Page {
    Text(
        "Supabase set nahi hai.\n\nsecrets.properties file banao (secrets.properties.example dekho), " +
            "SUPABASE_URL aur SUPABASE_ANON_KEY daalo, phir app dobara build karo.",
        textAlign = TextAlign.Center,
    )
}

@Composable
fun UpdateRequiredScreen(onUpdate: () -> Unit) = Page {
    Icon(Icons.Filled.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
    Spacer(Modifier.height(16.dp))
    Text("Naya version aa gaya hai", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
        "Aage badhne ke liye pehle app update karo.",
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(20.dp))
    Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) { Text("Update karo") }
}

@Composable
fun LoadErrorScreen(message: String, onRetry: () -> Unit) = Page {
    Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Button(onClick = onRetry) { Text("Dobara try karo") }
}

@Composable
fun LoginScreen(
    busy: Boolean,
    error: String?,
    info: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onClear: () -> Unit,
) = Page {
    var signUpMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Text(if (signUpMode) "Naya account banao" else "Login karo", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password (kam se kam 8 character)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    if (info != null) Text(info, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { if (signUpMode) onSignUp(email, password) else onSignIn(email, password) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Text(if (signUpMode) "Account banao" else "Login")
    }
    TextButton(onClick = { signUpMode = !signUpMode; onClear() }) {
        Text(if (signUpMode) "Account hai? Login karo" else "Naya account banao")
    }
}

@Composable
fun PairingScreen(
    waitingCode: String?,
    busy: Boolean,
    error: String?,
    email: String?,
    onCreateCode: () -> Unit,
    onJoin: (String) -> Unit,
    onPoll: () -> Unit,
    onSignOut: () -> Unit,
) = Page {
    var code by remember { mutableStateOf("") }

    // Partner ke judne ka intezaar: har 5 second status check.
    LaunchedEffect(waitingCode) {
        if (waitingCode != null) {
            while (true) {
                delay(5000)
                onPoll()
            }
        }
    }

    Text("Partner se judo", style = MaterialTheme.typography.titleMedium)
    if (email != null) Text(email, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(16.dp))

    if (waitingCode != null) {
        Text("Ye code partner ko do (15 minute valid):", textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            waitingCode.chunked(4).joinToString(" "),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text("Partner ke judte hi app khud khul jayegi.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCreateCode, enabled = !busy) { Text("Naya code banao") }
    } else {
        Button(onClick = onCreateCode, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Mera pairing code banao")
        }
    }

    Spacer(Modifier.height(20.dp))
    HorizontalDivider()
    Spacer(Modifier.height(20.dp))

    Text("Ya partner ka code daalo:")
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.filter { c -> c.isLetterOrDigit() }.take(8).uppercase() },
        label = { Text("8 character ka code") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Button(onClick = { onJoin(code) }, enabled = !busy && code.length == 8, modifier = Modifier.fillMaxWidth()) {
        Text("Judo")
    }
    if (error != null) {
        Spacer(Modifier.height(8.dp))
        Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(16.dp))
    TextButton(onClick = onSignOut) { Text("Logout") }
}
