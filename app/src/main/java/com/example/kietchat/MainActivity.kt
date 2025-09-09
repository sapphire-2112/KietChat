package com.example.kietchat.ui.theme

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kietchat.ble.BleManager

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BleManager
    private var chatMessages by mutableStateOf<List<String>>(emptyList())

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            if (perms.values.all { it }) enableBluetoothAndInit()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBlePermissions()
    }

    private fun requestBlePermissions() {
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

    @SuppressLint("MissingPermission")
    private fun enableBluetoothAndInit() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!adapter.isEnabled) {
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1)
        } else initBle()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) initBle()
    }

    private fun initBle() {
        bleManager = BleManager(this)
        bleManager.start()
        bleManager.observeMessages { msg -> chatMessages = chatMessages + msg }

        setContent {
            MaterialTheme {
                ChatUIWithDevices(bleManager, chatMessages)
            }
        }
    }
}

@Composable
fun ChatUIWithDevices(bleManager: BleManager, chatMessages: List<String>) {
    var input by remember { mutableStateOf("") }
    var availableDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    // Update devices every second
    LaunchedEffect(Unit) {
        while (true) {
            availableDevices = bleManager.getConnectedDevices()
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1976D2), Color(0xFF2196F3), Color(0xFF64B5F6))))
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "KIETChat",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Available Devices
        Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Available Devices", color = Color.White, fontWeight = FontWeight.SemiBold)
                LazyColumn {
                    items(availableDevices) { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {  }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(bleManager.getPeerName(device), color = Color.White, modifier = Modifier.weight(1f))
                            Text("Connected", color = Color.Green)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Chat messages
        Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)), modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(modifier = Modifier.padding(12.dp)) {
                items(chatMessages) { msg ->
                    Text(msg, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input + Send
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
            Button(onClick = { if (input.isNotBlank()) { bleManager.sendMessage(input); input = "" } }) {
                Text("Send")
            }
        }
    }
}
