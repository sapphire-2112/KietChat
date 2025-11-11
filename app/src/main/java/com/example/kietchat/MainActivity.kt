package com.example.kietchat

import android.Manifest
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val REQUEST_PERMS = 100
    private val uuid: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Classic SPP

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var socket: BluetoothSocket? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var input: java.io.InputStream? = null
    private var output: java.io.OutputStream? = null

    private lateinit var statusView: TextView
    private lateinit var chatView: TextView
    private lateinit var messageInput: EditText
    private lateinit var sendBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.status)
        chatView = findViewById(R.id.chatView)
        messageInput = findViewById(R.id.messageInput)
        sendBtn = findViewById(R.id.sendBtn)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Ask permissions
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

        if (bluetoothAdapter == null) {
            statusView.text = "Bluetooth not supported"
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }

        // start accepting connections (server)
        startServer()

        // find bonded phones only
        val pairedPhones = bluetoothAdapter!!.bondedDevices.filter {
            it.bluetoothClass.deviceClass in listOf(
                BluetoothClass.Device.PHONE_SMART,
                BluetoothClass.Device.PHONE_CELLULAR
            )
        }

        if (pairedPhones.isNotEmpty()) {
            // try connect to first bonded phone
            connectToDevice(pairedPhones.first())
        } else {
            statusView.text = "No paired phones found"
        }

        sendBtn.setOnClickListener {
            val msg = messageInput.text.toString()
            if (msg.isNotEmpty()) {
                sendMessage(msg)
                chatView.append("\nYou: $msg")
                messageInput.text.clear()
            }
        }
    }

    private fun startServer() {
        thread {
            try {
                serverSocket =
                    bluetoothAdapter?.listenUsingRfcommWithServiceRecord("KIETChat", uuid)
                val socketTemp = serverSocket?.accept() // blocking
                socket = socketTemp
                runOnUiThread {
                    statusView.text = "Connected (server)"
                }
                input = socket?.inputStream
                output = socket?.outputStream
                listenForMessages()
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    statusView.text = "Server failed: ${e.message}"
                }
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
                runOnUiThread { statusView.text = "Connected to ${device.name}" }
                input = socket?.inputStream
                output = socket?.outputStream
                listenForMessages()
            } catch (e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    statusView.text = "Connection failed: ${e.message}"
                }
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
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
                    runOnUiThread {
                        chatView.append("\nPeer: $msg")
                    }
                } catch (e: Exception) {
                    runOnUiThread { statusView.text = "Disconnected" }
                    break
                }
            }
        }
    }

    private fun sendMessage(msg: String) {
        try {
            output?.write(msg.toByteArray())
        } catch (e: IOException) {
            runOnUiThread {
                statusView.text = "Send failed: ${e.message}"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            socket?.close()
            serverSocket?.close()
        } catch (_: Exception) {
        }
    }
}
