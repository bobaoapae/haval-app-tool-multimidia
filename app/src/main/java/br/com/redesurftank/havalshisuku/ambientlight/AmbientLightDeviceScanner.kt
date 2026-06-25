package br.com.redesurftank.havalshisuku.ambientlight

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

data class AmbientLightScanResult(
    val name: String?,
    val address: String,
    val rssi: Int,
    val isLikelyLedLamp: Boolean
)

class AmbientLightDeviceScanner(private val context: Context) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val seenAddresses = linkedSetOf<String>()
    private var scanCallback: ScanCallback? = null

    private val bluetoothAdapter: BluetoothAdapter?
        get() {
            val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            return manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        }

    fun startScan(
        timeoutMs: Long = DEFAULT_SCAN_TIMEOUT_MS,
        onResult: (AmbientLightScanResult) -> Unit,
        onError: (String) -> Unit,
        onFinished: () -> Unit
    ) {
        stopScan()

        if (!hasScanPermission(appContext)) {
            onError("Permissao de Bluetooth/localizacao ausente")
            onFinished()
            return
        }

        val adapter = bluetoothAdapter
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || scanner == null) {
            onError("Bluetooth desligado ou scanner BLE indisponivel")
            onFinished()
            return
        }

        seenAddresses.clear()
        scanCallback =
            object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    handleResult(result, onResult)
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { handleResult(it, onResult) }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "scan failed: $errorCode")
                    onError("Falha no scan BLE: $errorCode")
                    stopScan()
                    onFinished()
                }
            }

        runCatching {
            @SuppressLint("MissingPermission")
            scanner.startScan(scanCallback)
            Log.i(TAG, "scan started")
        }.onFailure {
            onError("Nao foi possivel iniciar scan BLE: ${it.message}")
            scanCallback = null
            onFinished()
            return
        }

        handler.postDelayed(
            {
                stopScan()
                onFinished()
            },
            timeoutMs
        )
    }

    fun stopScan() {
        val callback = scanCallback ?: return
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        runCatching {
            if (hasScanPermission(appContext)) {
                @SuppressLint("MissingPermission")
                scanner?.stopScan(callback)
            }
        }
        handler.removeCallbacksAndMessages(null)
        scanCallback = null
        Log.i(TAG, "scan stopped")
    }

    private fun handleResult(result: ScanResult, onResult: (AmbientLightScanResult) -> Unit) {
        val device = result.device ?: return
        val address = device.address ?: return
        if (!seenAddresses.add(address)) return

        val name = result.scanRecord?.deviceName ?: safeDeviceName(device)
        val likelyLedLamp =
            name?.contains("LEDCAR", ignoreCase = true) == true ||
                name?.contains("LEDDMX", ignoreCase = true) == true ||
                name?.contains("LED", ignoreCase = true) == true

        onResult(
            AmbientLightScanResult(
                name = name,
                address = address,
                rssi = result.rssi,
                isLikelyLedLamp = likelyLedLamp
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return runCatching { device.name }.getOrNull()
    }

    companion object {
        private const val TAG = "AmbientLight"
        private const val DEFAULT_SCAN_TIMEOUT_MS = 12_000L

        fun requiredPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        fun hasScanPermission(context: Context): Boolean =
            requiredPermissions().all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
    }
}
