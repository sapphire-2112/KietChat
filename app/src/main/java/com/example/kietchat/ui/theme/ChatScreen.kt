package com.example.kietchat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(myNode: MeshNode) {
    val scope = rememberCoroutineScope()
    val messages by MeshNetwork.messages.collectAsState()
    val users = MeshNetwork.nodes

    var input by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<MeshNode?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("KIETchat - ${myNode.username} (${users.size} online)", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages) { msg ->
                val sender = users.find { it.unicastAddress == msg.src }?.username ?: "Unknown"
                val isMine = msg.src == myNode.unicastAddress
                ChatBubble(ChatMessage(sender, msg.text, isMine))
            }
        }

        Spacer(Modifier.height(8.dp))

        Row {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") }
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (input.isNotBlank()) {
                    scope.launch {
                        if (selectedUser == null) {
                            MeshNetwork.sendBroadcastMessage(input)
                        } else {
                            MeshNetwork.sendDirectMessage(input, selectedUser!!)
                        }
                        input = ""
                    }
                }
            }) {
                Text(if (selectedUser == null) "Send All" else "Send → ${selectedUser!!.username}")
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    Surface(
        color = if (message.isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "${message.sender}: ${message.text}",
            modifier = Modifier.padding(10.dp),
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

data class ChatMessage(val sender: String, val text: String, val isMine: Boolean)
