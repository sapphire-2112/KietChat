package com.example.kietchat.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class BleManager(private val context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val scope = CoroutineScope(Dispatchers.Main)

    private val connectedGatts = mutableListOf<BluetoothGatt>()
    private val deviceNames = mutableMapOf<BluetoothDevice, String>()

    private val _incomingMessages = MutableSharedFlow<String>()
    val incomingMessages = _incomingMessages

    private var deviceCallback: ((BluetoothDevice) -> Unit)? = null

    // ---------------- Scan ----------------
    @SuppressLint("MissingPermission")
    fun startScanning(callback: (BluetoothDevice) -> Unit) {
        deviceCallback = callback
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleService.CHAT_SERVICE_UUID))
            .build()

        bluetoothLeScanner?.startScan(listOf(filter), settings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                deviceCallback?.invoke(device)
                // connect if not already connected
                if (connectedGatts.none { it.device.address == device.address }) {
                    connect(device)
                }
            }
        }
    }

    // ---------------- Connect ----------------
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        val gatt = device.connectGatt(context, false, gattCallback)
        connectedGatts.add(gatt)
        getPeerName(device)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BLE", "Connected to ${gatt.device.address}")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE", "Disconnected: ${gatt.device.address}")
                connectedGatts.remove(gatt)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == BleService.MESSAGE_CHAR_UUID) {
                val msg = characteristic.getStringValue(0)
                val peerName = getPeerName(gatt.device)
                scope.launch { _incomingMessages.emit("$peerName: $msg") }
            }
        }
    }

    // ---------------- GATT Server ----------------
    @SuppressLint("MissingPermission")
    fun startGattServer() {
        val gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        val service = BluetoothGattService(
            BleService.CHAT_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        val messageChar = BluetoothGattCharacteristic(
            BleService.MESSAGE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(messageChar)
        gattServer.addService(service)
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            val msg = String(value)
            val peerName = getPeerName(device)
            scope.launch { _incomingMessages.emit("$peerName: $msg") }

            if (responseNeeded) {
                bluetoothManager.openGattServer(context, this)
                    ?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    // ---------------- Send Message to all connected peers ----------------
    @SuppressLint("MissingPermission")
    fun sendMessage(text: String) {
        connectedGatts.forEach { gatt ->
            val service = gatt.getService(BleService.CHAT_SERVICE_UUID)
            val char = service?.getCharacteristic(BleService.MESSAGE_CHAR_UUID)
            if (char != null) {
                char.value = text.toByteArray()
                gatt.writeCharacteristic(char)
            }
        }
    }

    fun observeMessages(callback: (String) -> Unit) {
        scope.launch {
            incomingMessages.collect { callback(it) }
        }
    }

    // ---------------- Advertising ----------------
    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(BleService.CHAT_SERVICE_UUID))
            .build()
        advertiser.startAdvertising(settings, data, object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d("BLE", "Advertising started")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e("BLE", "Advertising failed: $errorCode")
            }
        })
    }

    // ---------------- Random peer names ----------------
    fun getPeerName(device: BluetoothDevice): String {
        return deviceNames.getOrPut(device) {
            "Peer" + (10..99).random()
        }
    }
}
