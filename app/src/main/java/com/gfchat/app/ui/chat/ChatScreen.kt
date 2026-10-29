package com.gfchat.app.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import com.gfchat.app.ui.theme.ThemeMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

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
    val messages = remember { mutableStateListOf<Message>().apply { addAll(demoMessages()) } }
    var draft by remember { mutableStateOf("") }
    var attachMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun add(kind: MessageKind, text: String = "", uri: android.net.Uri? = null) {
        messages.add(Message(id = System.nanoTime(), fromMe = true, kind = kind, text = text, uri = uri))
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) add(MessageKind.Photo, uri = uri)
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) add(MessageKind.Video, uri = uri)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    IconButton(onClick = { attachMenu = true }) {
                        Icon(Icons.Filled.AttachFile, contentDescription = "Attach")
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
                        val t = draft.trim()
                        if (t.isNotEmpty()) {
                            add(MessageKind.Text, text = t)
                            draft = ""
                        }
                    },
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
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
            items(messages, key = { it.id }) { Bubble(it) }
        }
    }
}

@Composable
private fun Bubble(m: Message) {
    val mine = m.fromMe
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (mine) 16.dp else 4.dp,
                        bottomEnd = if (mine) 4.dp else 16.dp,
                    )
                )
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                )
                .padding(10.dp),
        ) {
            when (m.kind) {
                MessageKind.Text -> Text(m.text)
                MessageKind.Photo -> AsyncImage(
                    model = m.uri,
                    contentDescription = "Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 240.dp, height = 240.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                MessageKind.Video -> Box(
                    Modifier
                        .size(width = 240.dp, height = 140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayCircle, contentDescription = "Video", modifier = Modifier.size(56.dp))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                m.time,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}
