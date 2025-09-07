package com.example.kietchat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object MeshNetwork {
    private lateinit var selfNode: MeshNode

    private val _nodes = mutableListOf<MeshNode>()
    private val _messages = MutableStateFlow<List<MeshMessage>>(emptyList())

    val messages = _messages.asStateFlow()
    val nodes: List<MeshNode> get() = _nodes

    fun init(node: MeshNode) {
        selfNode = node
        if (!_nodes.contains(node)) {
            _nodes.add(node)
        }
    }

    fun sendBroadcastMessage(text: String) {
        val msg = MeshMessage(src = selfNode.unicastAddress, dst = 0xC000, text = text)
        deliver(msg)
    }

    fun sendDirectMessage(text: String, target: MeshNode) {
        val msg = MeshMessage(src = selfNode.unicastAddress, dst = target.unicastAddress, text = text)
        deliver(msg)
    }

    private fun deliver(msg: MeshMessage) {
        if (_messages.value.any { it.src == msg.src && it.seq == msg.seq }) return
        val newList = _messages.value.toMutableList()
        newList.add(msg)
        _messages.value = newList
    }
}

data class MeshMessage(
    val src: Int,
    val dst: Int,
    val text: String,
    val seq: Int = (0..999999).random()
)
