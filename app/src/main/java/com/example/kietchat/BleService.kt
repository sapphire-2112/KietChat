package com.example.kietchat.ble

import java.util.UUID

object BleService {
    val CHAT_SERVICE_UUID: UUID =
        UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
    val MESSAGE_CHAR_UUID: UUID =
        UUID.fromString("0000dcba-0000-1000-8000-00805f9b34fb")
}
