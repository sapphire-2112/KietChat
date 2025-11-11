package com.example.kietchat

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.widget.Toast

class BleManager(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    @SuppressLint("MissingPermission")
    fun listPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    fun showPairedDevices() {
        val devices = listPairedDevices()
        if (devices.isEmpty()) {
            Toast.makeText(context, "No paired devices found", Toast.LENGTH_SHORT).show()
        } else {
            val names = devices.joinToString(", ") { it.name ?: "Unknown" }
            Toast.makeText(context, "Paired: $names", Toast.LENGTH_LONG).show()
        }
    }
}
