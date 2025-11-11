package com.example.kietchat

import android.util.Log

data class MeshNode(val name: String) {
    fun receiveMessage(message: String) {
        Log.d("MeshNode", "Node [$name] received: $message")
    }
}
