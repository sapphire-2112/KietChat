package com.example.kietchat

import android.util.Log

class BleService {
    fun sendData(data: String) {
        Log.d("BleService", "Sending data: $data")
    }

    fun receiveData(data: String) {
        Log.d("BleService", "Received: $data")
    }
}
