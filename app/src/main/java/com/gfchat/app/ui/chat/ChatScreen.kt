package com.gfchat.app.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.gfchat.app.ChatViewModel
import com.gfchat.app.data.ChatMessage
import com.gfchat.app.data.TickStatus
import com.gfchat.app.ui.theme.ThemeMode
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private fun timeLabel(isoUtc: String): String = try {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    val date = parser.parse(isoUtc.take(19))
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date!!)
} catch (e: Exception) {
    ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    partnerName: String,
    isAdmin: Boolean,
    themeMode: ThemeMode,
    onCall: (video: Boolean) -> Unit,
    onAdmin: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    val vm: ChatViewModel = viewModel()
    DisposableEffect(Unit) {
        vm.startPolling()
        onDispose { vm.stopPolling() }
    }

    var draft by remember { mutableStateOf("") }
    var attachMenu by remember { mutableStateOf(false) }
    var overflowMenu by remember { mutableStateOf(false) }
    var confirmClearChat by remember { mutableStateOf(false) }
    var deleteCandidateId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.sendMedia(uri, isVideo = false)
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.sendMedia(uri, isVideo = true)
    }

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.lastIndex)
    }

    if (confirmClearChat) {
        AlertDialog(
            onDismissRequest = { confirmClearChat = false },
            title = { Text("Poori chat clear karo?") },
            text = { Text("Sare messages dono phones se hamesha ke liye mit jaayenge.") },
            confirmButton = {
                TextButton(onClick = { confirmClearChat = false; vm.clearChat() }) {
                    Text("Clear karo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClearChat = false }) { Text("Cancel") } },
        )
    }
    deleteCandidateId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteCandidateId = null },
            title = { Text("Message delete karo?") },
            text = { Text("Ye dono phones se hat jaayega (delete for everyone).") },
            confirmButton = {
                TextButton(onClick = { vm.deleteMessage(id); deleteCandidateId = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteCandidateId = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(partnerName) },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            when (themeMode) {
                                ThemeMode.System -> Icons.Filled.Brightness4
                                ThemeMode.Light -> Icons.Filled.LightMode
                                ThemeMode.Dark -> Icons.Filled.DarkMode
                            },
                            contentDescription = "Theme: $themeMode",
                        )
                    }
                    IconButton(onClick = { onCall(true) }) {
                        Icon(Icons.Filled.Videocam, contentDescription = "Video call")
                    }
                    IconButton(onClick = { onCall(false) }) {
                        Icon(Icons.Filled.Call, contentDescription = "Audio call")
                    }
                    if (isAdmin) {
                        IconButton(onClick = onAdmin) {
                            Icon(Icons.Filled.Settings, contentDescription = "Admin")
                        }
                    }
                    Box {
                        IconButton(onClick = { overflowMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Aur options")
                        }
                        DropdownMenu(expanded = overflowMenu, onDismissRequest = { overflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Chat clear karo") },
                                leadingIcon = { Icon(Icons.Filled.DeleteSweep, null) },
                                onClick = { overflowMenu = false; confirmClearChat = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
        bottomBar = {
            Column {
                if (vm.error != null) {
                    Text(
                        vm.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        IconButton(onClick = { attachMenu = true }, enabled = !vm.uploading) {
                            if (vm.uploading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Filled.AttachFile, contentDescription = "Attach")
                        }
                        DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Photo") },
                                leadingIcon = { Icon(Icons.Filled.Image, null) },
                                onClick = {
                                    attachMenu = false
                                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Video") },
                                leadingIcon = { Icon(Icons.Filled.Videocam, null) },
                                onClick = {
                                    attachMenu = false
                                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message likho...") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                    )
                    IconButton(
                        onClick = {
                            if (draft.isNotBlank()) {
                                vm.send(draft)
                                draft = ""
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(vm.messages, key = { it.id }) { msg ->
                val onLongPress: () -> Unit = { if (msg.fromMe && !msg.deleted) deleteCandidateId = msg.id }
                when {
                    msg.deleted -> DeletedBubble(msg)
                    msg.kind == "photo" || msg.kind == "video" ->
                        MediaBubble(msg, signedUrl = vm.signedUrlFor(msg), onLongPress = onLongPress)
                    else -> TextBubble(msg, onLongPress = onLongPress)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextBubble(m: ChatMessage, onLongPress: () -> Unit) {
    val mine = m.fromMe
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape(mine))
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                )
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(10.dp),
        ) {
            Text(m.body.orEmpty())
            Spacer(Modifier.height(2.dp))
            BubbleFooter(m)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaBubble(m: ChatMessage, signedUrl: String?, onLongPress: () -> Unit) {
    val mine = m.fromMe
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape(mine))
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                )
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(10.dp),
        ) {
            val mediaModifier = Modifier
                .size(width = 240.dp, height = if (m.kind == "photo") 240.dp else 140.dp)
                .clip(RoundedCornerShape(12.dp))

            when {
                signedUrl == null -> Box(
                    mediaModifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp) }

                m.kind == "photo" -> AsyncImage(
                    model = signedUrl,
                    contentDescription = "Photo",
                    contentScale = ContentScale.Crop,
                    modifier = mediaModifier,
                )

                else -> Box(
                    mediaModifier
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                        .clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(signedUrl)))
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.PlayCircle, contentDescription = "Video chalao", modifier = Modifier.size(56.dp)) }
            }
            Spacer(Modifier.height(2.dp))
            BubbleFooter(m)
        }
    }
}

@Composable
private fun DeletedBubble(m: ChatMessage) {
    val mine = m.fromMe
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape(mine))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(10.dp),
        ) {
            Text(
                "Ye message delete ho chuka hai",
                fontStyle = FontStyle.Italic,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            BubbleFooter(m)
        }
    }
}

@Composable
private fun BubbleFooter(m: ChatMessage) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        Text(timeLabel(m.createdAt), style = MaterialTheme.typography.labelSmall)
        if (m.fromMe) {
            Spacer(Modifier.width(4.dp))
            val (icon, tint) = when (m.status) {
                TickStatus.Sent -> Icons.Filled.Check to MaterialTheme.colorScheme.onSurfaceVariant
                TickStatus.Delivered -> Icons.Filled.DoneAll to MaterialTheme.colorScheme.onSurfaceVariant
                TickStatus.Read -> Icons.Filled.DoneAll to MaterialTheme.colorScheme.primary
            }
            Icon(icon, contentDescription = m.status.name, tint = tint, modifier = Modifier.size(14.dp))
        }
    }
}

private fun bubbleShape(mine: Boolean) = RoundedCornerShape(
    topStart = 16.dp, topEnd = 16.dp,
    bottomStart = if (mine) 16.dp else 4.dp,
    bottomEnd = if (mine) 4.dp else 16.dp,
)
