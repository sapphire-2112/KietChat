package com.example.kietchat

class MeshNetwork {
    private val nodes = mutableListOf<MeshNode>()

    fun addNode(node: MeshNode) {
        if (nodes.none { it.name == node.name }) {
            nodes.add(node)
        }
    }

    fun broadcastMessage(message: String) {
        nodes.forEach { node ->
            node.receiveMessage(message)
        }
    }
}
