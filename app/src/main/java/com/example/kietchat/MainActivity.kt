package com.example.kietchat.ui.theme

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kietchat.ble.BleManager

class MainActivity : ComponentActivity() {
    private lateinit var bleManager: BleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
                val allGranted = perms.values.all { it }
                if (allGranted) initBle()
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                )
            )
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun initBle() {
        bleManager = BleManager(this)
        bleManager.startGattServer()
        bleManager.startAdvertising()
        bleManager.startScanning {}

        setContent {
            MaterialTheme { ChatScreen(bleManager) }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ChatScreen(bleManager: BleManager) {
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var connectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var chatMessages by remember { mutableStateOf<List<String>>(emptyList()) }
    var input by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        bleManager.startScanning { device ->
            if (devices.none { it.address == device.address }) devices = devices + device
        }
        bleManager.observeMessages { msg -> chatMessages = chatMessages + msg }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1976D2), Color(0xFF2196F3), Color(0xFF64B5F6))
                )
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "KIET Chat",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Devices List
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Available Devices", color = Color.White)
                LazyColumn {
                    items(devices) { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    bleManager.connect(device)
                                    connectedDevice = device
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = bleManager.getPeerName(device),
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            if (connectedDevice == device) {
                                Text("Connected", color = Color.Green)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Chat Window
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.weight(1f)
        ) {
            LazyColumn(modifier = Modifier.padding(12.dp)) {
                items(chatMessages) { msg ->
                    Text(text = msg, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input Row
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (input.isNotBlank()) {
                    bleManager.sendMessage(input)
                    chatMessages = chatMessages + "Me: $input"
                    input = ""
                }
            }) { Text("Send") }
        }
    }
}
