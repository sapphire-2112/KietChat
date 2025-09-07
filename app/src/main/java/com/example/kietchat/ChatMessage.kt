package com.example.kietchat.model

import java.text.SimpleDateFormat
import java.util.*

data class ChatMessage(
    val text: String,
    val isSentByMe: Boolean,
    val timestamp: String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
)
