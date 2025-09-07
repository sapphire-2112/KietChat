package com.example.kietchat.ui.theme

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.example.kietchat.model.ChatMessage

class ChatViewModel : ViewModel() {

    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> get() = _messages

    fun sendMessage(text: String) {
        _messages.add(ChatMessage(text, true))
    }

    fun receiveMessage(text: String) {
        _messages.add(ChatMessage(text, false))
    }
}
