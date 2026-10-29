package com.gfchat.app.ui.chat

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MessageKind { Text, Photo, Video }

data class Message(
    val id: Long,
    val fromMe: Boolean,
    val kind: MessageKind,
    val text: String = "",
    val uri: Uri? = null,
    val time: String = nowLabel(),
)

fun nowLabel(): String = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

/** Sirf demo ke liye. Real messages Stage 2 me backend se aayenge. */
fun demoMessages(): List<Message> = listOf(
    Message(1, fromMe = false, kind = MessageKind.Text, text = "Good morning! Uth gaye? ☀️", time = "08:02 AM"),
    Message(2, fromMe = true, kind = MessageKind.Text, text = "Haan, abhi abhi. Tum chai pi rahi ho?", time = "08:05 AM"),
    Message(3, fromMe = false, kind = MessageKind.Text, text = "Haan ☕ Aaj shaam ko call karna", time = "08:06 AM"),
    Message(4, fromMe = true, kind = MessageKind.Text, text = "Pakka. 8 baje audio call karta hoon 📞", time = "08:07 AM"),
)
