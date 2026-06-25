package br.com.redesurftank.havalshisuku.ambientlight

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import br.com.redesurftank.havalshisuku.listeners.IDataChanged
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.CarConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AmbientLightService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var controller: AmbientLightBleController
    private lateinit var animationController: AmbientLightAnimationController
    private lateinit var musicVisualizerController: MusicVisualizerController
    private var driveModeListenerRegistered = false
    private var currentDriveMode = DriveMode.UNKNOWN

    private val driveModeListener =
        IDataChanged { key, value ->
            if (key == CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.value) {
                handleDriveModeChanged(value)
            }
        }

    override fun onCreate() {
        super.onCreate()
        controller = AmbientLightBleController.getInstance(this)
        animationController = AmbientLightAnimationController(controller, serviceScope)
        musicVisualizerController =
            MusicVisualizerController(
                context = this,
                controller = controller,
                scope = serviceScope,
                settingsProvider = { AmbientLightSettings.load() },
                baseColorProvider = { musicBaseColor() }
            )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_STOP) {
            stopAmbientLight()
            return START_NOT_STICKY
        }

        val settings = AmbientLightSettings.load()
        Log.i(TAG, "enabled=${settings.enabled}")
        if (!settings.enabled) {
            stopAmbientLight()
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_CONNECT -> connectSavedDevice(settings)
            ACTION_DISCONNECT -> disconnectLed()
            ACTION_SET_BRIGHTNESS -> handleSetBrightness(intent, settings)
            ACTION_TEST_COLOR -> handleTestColor(intent, settings)
            ACTION_SEND_HEX -> handleSendHex(intent, settings)
            ACTION_APPLY_DRIVE_MODE -> handleDriveModeChanged(intent?.getStringExtra(EXTRA_DRIVE_MODE))
            else -> {
                updateDriveModeListener(settings)
                cacheCurrentDriveMode(settings)
                connectSavedDevice(settings, applyModeAfterConnect = true)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        unregisterDriveModeListener()
        musicVisualizerController.stop()
        animationController.cancel()
        controller.disconnect()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connectSavedDevice(
        settings: AmbientLightConfig,
        applyModeAfterConnect: Boolean = false
    ) {
        val address = settings.deviceAddress
        if (address.isNullOrBlank()) {
            Log.i(TAG, "no saved LED device")
            musicVisualizerController.stop()
            return
        }
        serviceScope.launch {
            if (settings.musicAnimationEnabled) {
                animationController.cancel()
                musicVisualizerController.start()
            } else {
                musicVisualizerController.stop()
            }

            val wasConnected = controller.isConnectedTo(address)
            if (connectAndWait(address, settings.autoReconnect)) {
                controller.setBrightness(settings.brightnessPercent, settings.output)
                if (settings.musicAnimationEnabled) {
                    return@launch
                }
                if (!wasConnected && settings.animationsEnabled) {
                    animationController.welcomeAnimation(currentDriveMode)
                } else if (applyModeAfterConnect && settings.syncDriveMode) {
                    animationController.applyDriveMode(currentDriveMode)
                }
            }
        }
    }

    private fun disconnectLed() {
        serviceScope.launch {
            musicVisualizerController.stop()
            animationController.cancel()
            controller.disconnect()
        }
    }

    private fun handleSetBrightness(intent: Intent?, settings: AmbientLightConfig) {
        val address = settings.deviceAddress
        if (address.isNullOrBlank()) {
            Log.w(TAG, "brightness ignored: no saved LED device")
            return
        }
        val percent = intent?.getIntExtra(EXTRA_BRIGHTNESS, settings.brightnessPercent) ?: settings.brightnessPercent
        serviceScope.launch {
            if (connectAndWait(address, settings.autoReconnect)) {
                controller.setBrightness(percent, settings.output)
            }
        }
    }

    private fun handleTestColor(intent: Intent?, settings: AmbientLightConfig) {
        val address = settings.deviceAddress
        if (address.isNullOrBlank()) {
            Log.w(TAG, "test ignored: no saved LED device")
            return
        }
        val r = intent?.getIntExtra(EXTRA_R, 0) ?: 0
        val g = intent?.getIntExtra(EXTRA_G, 0) ?: 0
        val b = intent?.getIntExtra(EXTRA_B, 0) ?: 0
        serviceScope.launch {
            musicVisualizerController.stop()
            animationController.cancel()
            if (connectAndWait(address, settings.autoReconnect)) {
                controller.setRgb(r, g, b, settings.colorOrder, settings.bleColorOrder, settings.output)
            }
        }
    }

    private fun handleSendHex(intent: Intent?, settings: AmbientLightConfig) {
        val address = settings.deviceAddress
        val hex = intent?.getStringExtra(EXTRA_HEX)
        if (address.isNullOrBlank() || hex.isNullOrBlank()) {
            Log.w(TAG, "sendHex ignored: missing device or payload")
            return
        }
        serviceScope.launch {
            musicVisualizerController.stop()
            animationController.cancel()
            if (connectAndWait(address, settings.autoReconnect)) {
                controller.sendHex(hex)
            }
        }
    }

    private suspend fun connectAndWait(address: String, autoReconnect: Boolean): Boolean {
        if (controller.isConnectedTo(address)) return true
        controller.connect(address, reconnect = autoReconnect)
        return withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            controller.state
                .filter { it is AmbientLightConnectionState.Connected && it.address.equals(address, ignoreCase = true) }
                .first()
            true
        } ?: false
    }

    private fun updateDriveModeListener(settings: AmbientLightConfig) {
        if (settings.syncDriveMode && !driveModeListenerRegistered) {
            ServiceManager.getInstance().addDataChangedListener(driveModeListener)
            driveModeListenerRegistered = true
        } else if (!settings.syncDriveMode) {
            unregisterDriveModeListener()
        }
    }

    private fun unregisterDriveModeListener() {
        if (!driveModeListenerRegistered) return
        ServiceManager.getInstance().removeDataChangedListener(driveModeListener)
        driveModeListenerRegistered = false
    }

    private fun cacheCurrentDriveMode(settings: AmbientLightConfig) {
        if (!settings.syncDriveMode || settings.deviceAddress.isNullOrBlank()) return
        val raw = ServiceManager.getInstance().getData(CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.value)
        currentDriveMode = AmbientLightDriveModeMapper.fromRaw(raw)
    }

    private fun handleDriveModeChanged(rawValue: String?) {
        val settings = AmbientLightSettings.load()
        if (!settings.enabled || !settings.syncDriveMode || settings.deviceAddress.isNullOrBlank()) {
            return
        }
        val mode = AmbientLightDriveModeMapper.fromRaw(rawValue)
        if (mode == currentDriveMode) return
        currentDriveMode = mode
        if (settings.musicAnimationEnabled) {
            Log.i(TAG, "drive mode changed: ${mode.name} music_base_updated")
            return
        }
        animationController.applyDriveMode(mode)
    }

    private fun stopAmbientLight() {
        unregisterDriveModeListener()
        musicVisualizerController.stop()
        animationController.cancel()
        controller.disconnect()
        stopSelf()
    }

    private fun musicBaseColor(): LedColor =
        if (currentDriveMode == DriveMode.UNKNOWN) {
            AmbientLightProtocol.SOFT_BLUE
        } else {
            AmbientLightDriveModeMapper.colorForMode(currentDriveMode)
        }

    companion object {
        private const val TAG = "AmbientLight"
        private const val CONNECT_TIMEOUT_MS = 7_000L
        private const val ACTION_START = "br.com.redesurftank.havalshisuku.ambientlight.START"
        private const val ACTION_STOP = "br.com.redesurftank.havalshisuku.ambientlight.STOP"
        private const val ACTION_CONNECT = "br.com.redesurftank.havalshisuku.ambientlight.CONNECT"
        private const val ACTION_DISCONNECT = "br.com.redesurftank.havalshisuku.ambientlight.DISCONNECT"
        private const val ACTION_SET_BRIGHTNESS = "br.com.redesurftank.havalshisuku.ambientlight.SET_BRIGHTNESS"
        private const val ACTION_TEST_COLOR = "br.com.redesurftank.havalshisuku.ambientlight.TEST_COLOR"
        private const val ACTION_SEND_HEX = "br.com.redesurftank.havalshisuku.ambientlight.SEND_HEX"
        private const val ACTION_APPLY_DRIVE_MODE = "br.com.redesurftank.havalshisuku.ambientlight.APPLY_DRIVE_MODE"
        private const val EXTRA_R = "extra_r"
        private const val EXTRA_G = "extra_g"
        private const val EXTRA_B = "extra_b"
        private const val EXTRA_BRIGHTNESS = "extra_brightness"
        private const val EXTRA_HEX = "extra_hex"
        private const val EXTRA_DRIVE_MODE = "extra_drive_mode"

        @JvmStatic
        fun createStartIntent(context: Context): Intent =
            Intent(context, AmbientLightService::class.java).setAction(ACTION_START)

        @JvmStatic
        fun createStopIntent(context: Context): Intent =
            Intent(context, AmbientLightService::class.java).setAction(ACTION_STOP)

        @JvmStatic
        fun createConnectIntent(context: Context): Intent =
            Intent(context, AmbientLightService::class.java).setAction(ACTION_CONNECT)

        @JvmStatic
        fun createDisconnectIntent(context: Context): Intent =
            Intent(context, AmbientLightService::class.java).setAction(ACTION_DISCONNECT)

        @JvmStatic
        fun createBrightnessIntent(context: Context, percent: Int): Intent =
            Intent(context, AmbientLightService::class.java)
                .setAction(ACTION_SET_BRIGHTNESS)
                .putExtra(EXTRA_BRIGHTNESS, percent)

        @JvmStatic
        fun createTestColorIntent(context: Context, color: LedColor): Intent =
            Intent(context, AmbientLightService::class.java)
                .setAction(ACTION_TEST_COLOR)
                .putExtra(EXTRA_R, color.r)
                .putExtra(EXTRA_G, color.g)
                .putExtra(EXTRA_B, color.b)

        @JvmStatic
        fun createSendHexIntent(context: Context, hex: String): Intent =
            Intent(context, AmbientLightService::class.java)
                .setAction(ACTION_SEND_HEX)
                .putExtra(EXTRA_HEX, hex)

        @JvmStatic
        fun startIfEnabled(context: Context) {
            if (AmbientLightSettings.isEnabled()) {
                context.startService(createStartIntent(context))
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            context.startService(createStopIntent(context))
        }
    }
}
