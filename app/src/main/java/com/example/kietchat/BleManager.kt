package com.example.kietchat.ble

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Suppress("MissingPermission")
class BleManager(private val context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
    private val advertiser: BluetoothLeAdvertiser? = bluetoothAdapter.bluetoothLeAdvertiser

    private val connectedDevices = ConcurrentHashMap<String, BluetoothDevice>()
    private val gattClients = ConcurrentHashMap<String, BluetoothGatt>()
    private val messageObservers = mutableListOf<(String) -> Unit>()
    private val peerNames = ConcurrentHashMap<String, String>()

    private lateinit var gattServer: BluetoothGattServer

    // ✅ Characteristic with Write + Notify
    private val messageCharacteristic = BluetoothGattCharacteristic(
        BleService.MESSAGE_CHAR_UUID,
        BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_WRITE
    )

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            device?.let {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connectedDevices[it.address] = it
                    if (!peerNames.containsKey(it.address)) {
                        peerNames[it.address] = "Peer${peerNames.size + 1}"
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connectedDevices.remove(it.address)
                    peerNames.remove(it.address)
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            value?.let {
                val msg = String(it, Charset.forName("UTF-8"))
                notifyMessageObservers("${getPeerName(device)}: $msg")

                // 🔥 Re-broadcast (mesh flooding)
                sendToAllClients(msg, excludeDevice = device)
            }
            if (responseNeeded && device != null) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    init {
        // Start GATT server with chat service
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        val chatService = BluetoothGattService(
            BleService.CHAT_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        chatService.addCharacteristic(messageCharacteristic)
        gattServer.addService(chatService)
    }

    fun start() {
        startAdvertising()
        startScanning()
    }

    private fun startAdvertising() {
        bluetoothAdapter.name = "KIETChat"

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(BleService.CHAT_SERVICE_UUID))
            .build()

        advertiser?.startAdvertising(settings, data, object : AdvertiseCallback() {})
    }

    private fun startScanning() {
        val scanner = bluetoothAdapter.bluetoothLeScanner
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    val uuids = result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
                    if (BleService.CHAT_SERVICE_UUID in uuids && !gattClients.containsKey(device.address)) {
                        connectToPeer(device)
                    }
                }
            }
        }
        scanner.startScan(scanCallback)
    }

    private fun connectToPeer(device: BluetoothDevice) {
        device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gattClients[device.address] = gatt
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gattClients.remove(device.address)
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val char = gatt.getService(BleService.CHAT_SERVICE_UUID)
                    ?.getCharacteristic(BleService.MESSAGE_CHAR_UUID)
                char?.let {
                    gatt.setCharacteristicNotification(it, true)
                    val descriptor = it.getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                    )
                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    if (descriptor != null) gatt.writeDescriptor(descriptor)
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                val msg = characteristic.value?.toString(Charset.forName("UTF-8")) ?: return
                notifyMessageObservers("${getPeerName(gatt.device)}: $msg")

                // 🔥 Re-broadcast
                sendToAllClients(msg, excludeDevice = gatt.device)
            }
        })
    }

    fun sendMessage(message: String) {
        notifyMessageObservers("Me: $message")
        sendToAllClients(message, null)
    }

    // Send to all connected clients except one (for rebroadcast loop prevention)
    private fun sendToAllClients(message: String, excludeDevice: BluetoothDevice?) {
        gattClients.values.forEach { gatt ->
            if (excludeDevice == null || gatt.device.address != excludeDevice.address) {
                val char = gatt.getService(BleService.CHAT_SERVICE_UUID)
                    ?.getCharacteristic(BleService.MESSAGE_CHAR_UUID)
                char?.let {
                    it.value = message.toByteArray(Charset.forName("UTF-8"))
                    gatt.writeCharacteristic(it)
                }
            }
        }
        // Notify connected devices on server
        connectedDevices.values.forEach { device ->
            if (excludeDevice == null || device.address != excludeDevice.address) {
                messageCharacteristic.value = message.toByteArray(Charset.forName("UTF-8"))
                gattServer.notifyCharacteristicChanged(device, messageCharacteristic, false)
            }
        }
    }

    fun observeMessages(observer: (String) -> Unit) {
        messageObservers.add(observer)
    }

    private fun notifyMessageObservers(message: String) {
        Handler(Looper.getMainLooper()).post {
            messageObservers.forEach { it(message) }
        }
    }

    fun getPeerName(device: BluetoothDevice?): String {
        return device?.let { peerNames[it.address] ?: it.name ?: "Unknown" } ?: "Unknown"
    }

    fun getConnectedDevices(): List<BluetoothDevice> =
        gattClients.keys.mapNotNull { gattClients[it]?.device }
}
