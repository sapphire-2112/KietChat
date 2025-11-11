package com.example.kietchat

import android.Manifest
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.view.Gravity
import android.view.View
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
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Classic SPP UUID

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var socket: BluetoothSocket? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var input: java.io.InputStream? = null
    private var output: java.io.OutputStream? = null

    private lateinit var statusView: TextView
    private lateinit var messageInput: EditText
    private lateinit var sendBtn: Button
    private lateinit var chatContainer: LinearLayout
    private lateinit var chatScroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.status)
        messageInput = findViewById(R.id.messageInput)
        sendBtn = findViewById(R.id.sendBtn)
        chatContainer = findViewById(R.id.chatContainer)
        chatScroll = findViewById(R.id.chatScroll)

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

        // Start accepting connections (server)
        startServer()

        // Find bonded phones only
        val pairedPhones = bluetoothAdapter!!.bondedDevices.filter {
            it.bluetoothClass.deviceClass in listOf(
                BluetoothClass.Device.PHONE_SMART,
                BluetoothClass.Device.PHONE_CELLULAR
            )
        }

        if (pairedPhones.isNotEmpty()) {
            // Try connect to first bonded phone
            connectToDevice(pairedPhones.first())
        } else {
            statusView.text = "No paired phones found"
        }

        sendBtn.setOnClickListener {
            val msg = messageInput.text.toString()
            if (msg.isNotEmpty()) {
                sendMessage(msg)
                addMessage("You: $msg", true)
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
                        addMessage("Peer: $msg", false)
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

    // Chat bubble UI helper
    private fun addMessage(text: String, isSent: Boolean) {
        val messageView = TextView(this)
        messageView.text = text
        messageView.textSize = 15f
        messageView.setPadding(20, 12, 20, 12)
        messageView.maxWidth = 800
        messageView.background = ContextCompat.getDrawable(
            this,
            if (isSent) R.drawable.bubble_sent else R.drawable.bubble_received
        )
        messageView.setTextColor(ContextCompat.getColor(this, android.R.color.black))

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(12, 8, 12, 8)
        params.gravity = if (isSent) Gravity.END else Gravity.START
        messageView.layoutParams = params

        chatContainer.addView(messageView)

        chatScroll.post {
            chatScroll.fullScroll(View.FOCUS_DOWN)
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
