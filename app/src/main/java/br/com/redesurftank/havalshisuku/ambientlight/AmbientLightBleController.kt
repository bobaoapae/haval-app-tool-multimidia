package br.com.redesurftank.havalshisuku.ambientlight

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class AmbientLightConnectionState(val label: String) {
    object Idle : AmbientLightConnectionState("Idle")
    data class Connecting(val address: String) : AmbientLightConnectionState("Connecting")
    data class Connected(val address: String, val name: String?) : AmbientLightConnectionState("Connected")
    data class Reconnecting(val attempt: Int, val delayMs: Long) : AmbientLightConnectionState("Reconnecting")
    data class Error(val message: String) : AmbientLightConnectionState("Error")
}

data class AmbientLightDebugState(
    val rssi: Int? = null,
    val serviceUuid: String = AmbientLightProtocol.SERVICE_UUID,
    val characteristicUuid: String = AmbientLightProtocol.CHARACTERISTIC_UUID,
    val lastPayloadHex: String? = null,
    val lastError: String? = null,
    val lastWriteElapsedMs: Long? = null,
    val musicVisualizerActive: Boolean = false,
    val musicBassLevel: Float = 0f,
    val musicLastBeatElapsedMs: Long? = null,
    val musicLastError: String? = null,
    val musicCaptureSource: String = "Nenhum"
)

class AmbientLightBleController private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectMutex = Mutex()
    private val writeMutex = Mutex()
    private val serviceUuid = UUID.fromString(AmbientLightProtocol.SERVICE_UUID)
    private val characteristicUuid = UUID.fromString(AmbientLightProtocol.CHARACTERISTIC_UUID)

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var reconnectJob: Job? = null
    private var targetAddress: String? = null
    private var reconnectEnabled = false
    private var manualDisconnect = false
    private var reconnectAttempt = 0
    private var lastWriteAt = 0L

    private val _state = MutableStateFlow<AmbientLightConnectionState>(AmbientLightConnectionState.Idle)
    val state: StateFlow<AmbientLightConnectionState> = _state

    private val _debugState = MutableStateFlow(AmbientLightDebugState())
    val debugState: StateFlow<AmbientLightDebugState> = _debugState

    private val bluetoothAdapter: BluetoothAdapter?
        get() {
            val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            return manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        }

    private val gattCallback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                val address = gatt.device?.address.orEmpty()
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "connected to ${safeDeviceName(gatt.device) ?: address}")
                    reconnectAttempt = 0
                    if (!hasConnectPermission()) {
                        setError("Permissao BLUETOOTH_CONNECT ausente")
                        closeGatt(gatt)
                        return
                    }
                    runCatching {
                        @SuppressLint("MissingPermission")
                        gatt.discoverServices()
                    }.onFailure {
                        setError("Falha ao descobrir servicos: ${it.message}")
                        closeGatt(gatt)
                        scheduleReconnect()
                    }
                    return
                }

                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w(TAG, "disconnected from $address status=$status")
                    writeCharacteristic = null
                    closeGatt(gatt)
                    if (!manualDisconnect) {
                        scheduleReconnect()
                    } else {
                        _state.value = AmbientLightConnectionState.Idle
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    setError("Falha ao descobrir servicos BLE: $status")
                    closeGatt(gatt)
                    scheduleReconnect()
                    return
                }

                val service: BluetoothGattService? = gatt.getService(serviceUuid)
                val characteristic = service?.getCharacteristic(characteristicUuid)
                if (characteristic == null) {
                    setError("Caracteristica FFE1 nao encontrada")
                    closeGatt(gatt)
                    scheduleReconnect()
                    return
                }

                val supportsWriteNoResponse =
                    characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
                characteristic.writeType =
                    if (supportsWriteNoResponse) {
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    } else {
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    }
                writeCharacteristic = characteristic
                _state.value =
                    AmbientLightConnectionState.Connected(
                        gatt.device.address,
                        safeDeviceName(gatt.device)
                    )
                readRemoteRssi(gatt)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    setError("Falha ao escrever BLE: $status")
                }
            }

            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    _debugState.value = _debugState.value.copy(rssi = rssi)
                }
            }
        }

    fun connect(address: String, reconnect: Boolean = true) {
        scope.launch { connectInternal(address, reconnect) }
    }

    fun disconnect() {
        manualDisconnect = true
        reconnectEnabled = false
        reconnectJob?.cancel()
        scope.launch {
            connectMutex.withLock {
                closeGatt(bluetoothGatt)
                bluetoothGatt = null
                writeCharacteristic = null
                _state.value = AmbientLightConnectionState.Idle
            }
        }
    }

    suspend fun setRgb(
        r: Int,
        g: Int,
        b: Int,
        dmxColorOrder: ColorOrderMapper = ColorOrderMapper.DEFAULT_DMX,
        bleColorOrder: ColorOrderMapper = ColorOrderMapper.DEFAULT_BLE,
        output: AmbientLightOutput = AmbientLightOutput.DEFAULT
    ): Boolean =
        writePayloads(AmbientLightProtocol.rgbPayloads(r, g, b, dmxColorOrder, bleColorOrder, output))

    suspend fun setBrightness(
        percent: Int,
        output: AmbientLightOutput = AmbientLightOutput.DEFAULT
    ): Boolean =
        writePayloads(AmbientLightProtocol.brightnessPayloads(percent, output))

    suspend fun sendHex(hex: String): Boolean =
        runCatching { writePayload(AmbientLightProtocol.parseHex(hex)) }
            .getOrElse {
                _state.value = AmbientLightConnectionState.Error(it.message ?: "Payload HEX invalido")
                false
            }

    fun setRgbAsync(
        r: Int,
        g: Int,
        b: Int,
        dmxColorOrder: ColorOrderMapper = ColorOrderMapper.DEFAULT_DMX,
        bleColorOrder: ColorOrderMapper = ColorOrderMapper.DEFAULT_BLE,
        output: AmbientLightOutput = AmbientLightOutput.DEFAULT
    ) {
        scope.launch { setRgb(r, g, b, dmxColorOrder, bleColorOrder, output) }
    }

    fun sendHexAsync(hex: String) {
        scope.launch { sendHex(hex) }
    }

    fun isConnected(): Boolean = state.value is AmbientLightConnectionState.Connected

    fun isConnectedTo(address: String): Boolean =
        (state.value as? AmbientLightConnectionState.Connected)?.address.equals(address, ignoreCase = true)

    fun updateMusicDebug(
        active: Boolean,
        bassLevel: Float = _debugState.value.musicBassLevel,
        lastBeatElapsedMs: Long? = _debugState.value.musicLastBeatElapsedMs,
        error: String? = null,
        captureSource: String = _debugState.value.musicCaptureSource
    ) {
        _debugState.value =
            _debugState.value.copy(
                musicVisualizerActive = active,
                musicBassLevel = bassLevel.coerceIn(0f, 1f),
                musicLastBeatElapsedMs = lastBeatElapsedMs,
                musicLastError = error,
                musicCaptureSource = if (active) captureSource else "Nenhum"
            )
    }

    private suspend fun connectInternal(address: String, reconnect: Boolean) {
        connectMutex.withLock {
            targetAddress = address
            reconnectEnabled = reconnect
            manualDisconnect = false
            reconnectJob?.cancel()
            writeCharacteristic = null
            closeGatt(bluetoothGatt)
            bluetoothGatt = null

            if (!hasConnectPermission()) {
                setError("Permissao Bluetooth ausente")
                return
            }

            val adapter = bluetoothAdapter
            if (adapter == null || !adapter.isEnabled) {
                setError("Bluetooth desligado ou indisponivel")
                return
            }

            val device =
                runCatching { adapter.getRemoteDevice(address) }
                    .getOrElse {
                        setError("MAC BLE invalido: $address")
                        return
                    }

            _state.value = AmbientLightConnectionState.Connecting(address)
            runCatching {
                @SuppressLint("MissingPermission")
                bluetoothGatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            }.onFailure {
                setError("Falha ao conectar BLE: ${it.message}")
                scheduleReconnect()
            }
        }
    }

    private suspend fun writePayloads(payloads: List<ByteArray>): Boolean {
        var sentAny = false
        payloads.forEach { payload ->
            sentAny = writePayload(payload) || sentAny
        }
        return sentAny
    }

    private suspend fun writePayload(payload: ByteArray): Boolean {
        return writeMutex.withLock {
            val gatt = bluetoothGatt
            val characteristic = writeCharacteristic
            if (gatt == null || characteristic == null || !isConnected()) {
                setError("BLE nao conectado")
                false
            } else if (!hasConnectPermission()) {
                setError("Permissao Bluetooth ausente")
                false
            } else {
                val elapsed = SystemClock.elapsedRealtime() - lastWriteAt
                if (elapsed < AmbientLightProtocol.MIN_WRITE_INTERVAL_MS) {
                    delay(AmbientLightProtocol.MIN_WRITE_INTERVAL_MS - elapsed)
                }

                val hex = AmbientLightProtocol.bytesToHex(payload)
                Log.i(TAG, "send: $hex")
                characteristic.value = payload
                @SuppressLint("MissingPermission")
                val sent = gatt.writeCharacteristic(characteristic)
                if (sent) {
                    lastWriteAt = SystemClock.elapsedRealtime()
                    _debugState.value =
                        _debugState.value.copy(
                            lastPayloadHex = hex,
                            lastError = null,
                            lastWriteElapsedMs = lastWriteAt
                        )
                } else {
                    setError("writeCharacteristic retornou false")
                }
                sent
            }
        }
    }

    private fun scheduleReconnect() {
        val address = targetAddress ?: return
        if (!reconnectEnabled || manualDisconnect) return
        if (reconnectJob?.isActive == true) return

        reconnectAttempt += 1
        val delayMs = when {
            reconnectAttempt <= 1 -> 1_000L
            reconnectAttempt == 2 -> 2_000L
            reconnectAttempt == 3 -> 5_000L
            else -> 10_000L
        }
        _state.value = AmbientLightConnectionState.Reconnecting(reconnectAttempt, delayMs)
        reconnectJob =
            scope.launch {
                delay(delayMs)
                connectInternal(address, reconnect = true)
            }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    private fun readRemoteRssi(gatt: BluetoothGatt) {
        if (!hasConnectPermission()) return
        runCatching { gatt.readRemoteRssi() }
    }

    private fun setError(message: String) {
        _state.value = AmbientLightConnectionState.Error(message)
        _debugState.value = _debugState.value.copy(lastError = message)
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice?): String? {
        if (device == null || !hasConnectPermission()) return null
        return runCatching { device.name }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(gatt: BluetoothGatt?) {
        if (gatt == null) return
        runCatching {
            if (hasConnectPermission()) {
                gatt.disconnect()
            }
        }
        runCatching { gatt.close() }
        if (bluetoothGatt == gatt) {
            bluetoothGatt = null
        }
    }

    companion object {
        private const val TAG = "AmbientLight"

        @Volatile
        private var INSTANCE: AmbientLightBleController? = null

        fun getInstance(context: Context): AmbientLightBleController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AmbientLightBleController(context).also { INSTANCE = it }
            }
        }
    }
}
