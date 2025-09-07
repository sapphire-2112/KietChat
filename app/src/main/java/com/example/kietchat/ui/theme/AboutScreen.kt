package com.example.kietchat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("About KIETchat", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "KIETchat is a peer-to-peer chat app using BLE Mesh technology.\n\n" +
                    "Features:\n" +
                    "• Random usernames when you connect\n" +
                    "• Broadcast messaging (send to all)\n" +
                    "• Direct messaging (send to a user)\n" +
                    "• Lightweight & offline"
        )
    }
}
