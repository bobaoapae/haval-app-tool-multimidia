package br.com.redesurftank.havalshisuku.ambientlight

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AmbientLightAnimationController(
    private val controller: AmbientLightBleController,
    private val scope: CoroutineScope,
    private val settingsProvider: () -> AmbientLightConfig = { AmbientLightSettings.load() }
) {
    private var animationJob: Job? = null
    private var currentColor = AmbientLightProtocol.WHITE
    private var currentMode = DriveMode.UNKNOWN

    fun applyDriveMode(mode: DriveMode) {
        val settings = settingsProvider()
        if (!settings.enabled || !settings.syncDriveMode || settings.deviceAddress.isNullOrBlank()) {
            return
        }

        currentMode = mode
        val target = AmbientLightDriveModeMapper.colorForMode(mode)
        animationJob?.cancel()
        Log.i(TAG, "drive mode changed: ${mode.name}")

        animationJob =
            scope.launch {
                if (settings.animationsEnabled) {
                    when (mode) {
                        DriveMode.SPORT -> {
                            Log.i(TAG, "animation: SPORT_PULSE")
                            pulseSport()
                        }
                        DriveMode.ECO -> {
                            Log.i(TAG, "animation: ECO_BREATHING")
                            fadeToColor(currentColor, target)
                            ecoBreathing()
                        }
                        else -> {
                            Log.i(TAG, "animation: FADE")
                            fadeToColor(currentColor, target)
                        }
                    }
                } else {
                    Log.i(TAG, "animation: FIXED_COLOR")
                    sendColor(target)
                    currentColor = target
                }
            }
    }

    fun welcomeAnimation(mode: DriveMode) {
        val settings = settingsProvider()
        if (!settings.enabled || settings.deviceAddress.isNullOrBlank()) return

        animationJob?.cancel()
        animationJob =
            scope.launch {
                Log.i(TAG, "animation: WELCOME")
                sendColor(AmbientLightProtocol.SOFT_BLUE)
                delay(300)
                sendColor(AmbientLightProtocol.WHITE)
                delay(300)
                val target = AmbientLightDriveModeMapper.colorForMode(mode)
                fadeToColor(AmbientLightProtocol.WHITE, target)
            }
    }

    fun triggerAlertAnimation() {
        Log.i(TAG, "animation: ALERT_PLACEHOLDER")
    }

    fun cancel() {
        animationJob?.cancel()
        animationJob = null
    }

    suspend fun fadeToColor(from: LedColor, to: LedColor) {
        val start = from.coerce()
        val end = to.coerce()
        repeat(FADE_STEPS) { stepIndex ->
            val step = stepIndex + 1
            val ratio = step.toFloat() / FADE_STEPS.toFloat()
            val color =
                LedColor(
                    r = (start.r + ((end.r - start.r) * ratio)).toInt(),
                    g = (start.g + ((end.g - start.g) * ratio)).toInt(),
                    b = (start.b + ((end.b - start.b) * ratio)).toInt()
                )
            sendColor(color)
            delay(FADE_DELAY_MS)
        }
        currentColor = end
    }

    suspend fun pulseSport() {
        val red = AmbientLightProtocol.RED
        val lowRed = LedColor(120, 0, 0)
        val endAt = System.currentTimeMillis() + SPORT_PULSE_MS
        while (scope.coroutineContext.isActive && System.currentTimeMillis() < endAt) {
            fadeToColor(lowRed, red)
            fadeToColor(red, lowRed)
        }
        sendColor(red)
        currentColor = red
    }

    suspend fun ecoBreathing() {
        val low = LedColor(0, 90, 0)
        val high = AmbientLightProtocol.GREEN
        while (scope.coroutineContext.isActive && currentMode == DriveMode.ECO) {
            fadeToColor(low, high)
            delay(250)
            fadeToColor(high, low)
            delay(250)
        }
    }

    private suspend fun sendColor(color: LedColor): Boolean {
        val settings = settingsProvider()
        return controller.setRgb(
            color.r,
            color.g,
            color.b,
            settings.colorOrder,
            settings.bleColorOrder,
            settings.output
        )
    }

    companion object {
        private const val TAG = "AmbientLight"
        private const val FADE_STEPS = 20
        private const val FADE_DELAY_MS = 30L
        private const val SPORT_PULSE_MS = 3_000L
    }
}
