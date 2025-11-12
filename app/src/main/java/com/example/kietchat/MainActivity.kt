package com.example.kietchat

import android.Manifest
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val REQUEST_PERMS = 100
    private val uuid: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // SPP UUID

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var socket: BluetoothSocket? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var input: java.io.InputStream? = null
    private var output: java.io.OutputStream? = null

    private lateinit var status: TextView
    private lateinit var chatContainer: LinearLayout
    private lateinit var messageInput: EditText
    private lateinit var sendBtn: Button

    private lateinit var deviceListDialog: AlertDialog
    private val foundDevices = mutableListOf<BluetoothDevice>()
    private lateinit var receiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        chatContainer = findViewById(R.id.chatContainer)
        messageInput = findViewById(R.id.messageInput)
        sendBtn = findViewById(R.id.sendBtn)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            status.text = "❌ Bluetooth not supported"
            return
        }

        // Request Bluetooth permissions
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                REQUEST_PERMS
            )
        }

        // Enable Bluetooth if off
        if (!bluetoothAdapter!!.isEnabled) {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }

        // Make this device discoverable
        val discoverIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        discoverIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        startActivity(discoverIntent)
        status.text = "📡 Discoverable for 5 mins"

        // Start server to accept connections
        startServer()

        // Start discovering other devices
        startDiscovery()

        sendBtn.setOnClickListener {
            val msg = messageInput.text.toString().trim()
            if (msg.isNotEmpty()) {
                sendMessage(msg)
                addMessage("You", msg)
                messageInput.text.clear()
            }
        }
    }

    private fun startDiscovery() {
        foundDevices.clear()

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                if (BluetoothDevice.ACTION_FOUND == action) {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && device.bluetoothClass.deviceClass in listOf(
                            BluetoothClass.Device.PHONE_SMART,
                            BluetoothClass.Device.PHONE_CELLULAR
                        )
                    ) {
                        if (!foundDevices.any { it.address == device.address }) {
                            foundDevices.add(device)
                        }
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                    showDeviceList()
                }
            }
        }

        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(receiver, filter)

        bluetoothAdapter?.startDiscovery()
        status.text = "🔍 Scanning for nearby phones..."
    }

    private fun showDeviceList() {
        if (foundDevices.isEmpty()) {
            status.text = "❌ No nearby phones found"
            return
        }

        val deviceNames = foundDevices.map { it.name ?: it.address }.toTypedArray()

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select a device to connect")
        builder.setItems(deviceNames) { _, which ->
            val device = foundDevices[which]
            status.text = "🔗 Connecting to ${device.name}"
            connectToDevice(device)
        }
        builder.setCancelable(false)

        deviceListDialog = builder.create()
        deviceListDialog.show()
    }

    private fun startServer() {
        thread {
            try {
                serverSocket =
                    bluetoothAdapter?.listenUsingRfcommWithServiceRecord("KietChat", uuid)
                val socketTemp = serverSocket?.accept()
                socket = socketTemp
                runOnUiThread {
                    status.text = "✅ Connected (Server)"
                }
                input = socket?.inputStream
                output = socket?.outputStream
                listenForMessages()
            } catch (e: Exception) {
                runOnUiThread { status.text = "Server error: ${e.message}" }
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        thread {
            try {
                bluetoothAdapter?.cancelDiscovery()
                val sock = device.createRfcommSocketToServiceRecord(uuid)
                sock.connect()
                socket = sock
                runOnUiThread { status.text = "✅ Connected to ${device.name}" }
                input = socket?.inputStream
                output = socket?.outputStream
                listenForMessages()
            } catch (e: IOException) {
                runOnUiThread { status.text = "❌ Connection failed: ${e.message}" }
            }
        }
    }

    private fun listenForMessages() {
        thread {
            val buffer = ByteArray(1024)
            while (true) {
                try {
                    val bytes = input?.read(buffer) ?: break
                    val msg = String(buffer, 0, bytes)
                    runOnUiThread { addMessage("Peer", msg) }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "⚠️ Disconnected" }
                    break
                }
            }
        }
    }

    private fun sendMessage(msg: String) {
        try {
            output?.write(msg.toByteArray())
        } catch (e: IOException) {
            runOnUiThread { status.text = "Send failed: ${e.message}" }
        }
    }

    private fun addMessage(sender: String, msg: String) {
        val textView = TextView(this)
        textView.text = "$sender: $msg"
        textView.textSize = 16f
        textView.setTextColor(
            if (sender == "You") getColor(android.R.color.holo_blue_dark)
            else getColor(android.R.color.black)
        )
        chatContainer.addView(textView)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
            socket?.close()
            serverSocket?.close()
        } catch (_: Exception) {
        }
    }
}
