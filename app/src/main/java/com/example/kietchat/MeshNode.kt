package com.example.kietchat

data class MeshNode(
    val username: String,
    val unicastAddress: Int = (10000..99999).random() // unique random ID
)
