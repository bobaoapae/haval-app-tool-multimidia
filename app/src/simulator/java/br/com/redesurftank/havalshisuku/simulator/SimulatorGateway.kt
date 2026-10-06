package br.com.redesurftank.havalshisuku.simulator

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.CarConstants
import java.util.concurrent.atomic.AtomicBoolean

class SimulatorGateway(private val serviceManager: ServiceManager) {
    private val TAG = "SimulatorGateway"

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private val isRunning = AtomicBoolean(false)

    private var currentSpeed = 0f
    private var simulationPhase = "accelerating"
    private var steadyTimeCounter = 0
    private var animationTimeCounter = 0
    private var fuelBatteryPhase = "decreasing"

    private val SIMULATION_INTERVAL = 100L
    private val MAX_FUEL_RANGE = 700f
    private val MAX_BATTERY_RANGE = 170f
    private val DECREASE_TIME_MS = 30000
    private val INCREASE_TIME_MS = 5000

    private var lastValue = 0.0
    private val smoothingFactor = 0.05
    private var simulatedOdo = 11450.5

    fun start() {
        if (isRunning.getAndSet(true)) return

        Log.w(TAG, "Starting SimulatorGateway telemetry loop")

        handlerThread = HandlerThread("SimulatorGatewayThread").apply { start() }
        handler = Handler(handlerThread!!.looper)

        // Initial HVAC state
        serviceManager.updateData(CarConstants.CAR_HVAC_FAN_SPEED.value, "3")
        serviceManager.updateData(CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.value, "22.0")
        serviceManager.updateData(CarConstants.CAR_HVAC_POWER_MODE.value, "1")

        // Initial Basic state
        serviceManager.updateData(CarConstants.CAR_BASIC_INSIDE_TEMP.value, "24.0")
        serviceManager.updateData(CarConstants.CAR_BASIC_OUTSIDE_TEMP.value, "28.0")

        handler?.post(simulationRunnable)
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        Log.w(TAG, "Stopping SimulatorGateway")
        handler?.removeCallbacksAndMessages(null)
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
    }

    fun injectValue(key: String, value: String) {
        serviceManager.updateData(key, value)
    }

    private val simulationRunnable = object : Runnable {
        override fun run() {
            if (!isRunning.get()) return

            // Speed Profile (similar to testing-utils.js)
            when (simulationPhase) {
                "accelerating" -> {
                    if (currentSpeed < 150f) currentSpeed += 2.0f
                    else {
                        currentSpeed = 150f
                        simulationPhase = "decelerating"
                    }
                }
                "decelerating" -> {
                    if (currentSpeed > 20f) currentSpeed -= 5f
                    else {
                        currentSpeed = 20f
                        simulationPhase = "steady"
                        steadyTimeCounter = 0
                    }
                }
                "steady" -> {
                    if (steadyTimeCounter * SIMULATION_INTERVAL < 1000) steadyTimeCounter++
                    else simulationPhase = "stopping"
                }
                "stopping" -> {
                    if (currentSpeed > 0f) currentSpeed -= 1f
                    else {
                        currentSpeed = 0f
                        simulationPhase = "idle"
                        handler?.postDelayed({ simulationPhase = "accelerating" }, 5000)
                    }
                }
            }

            serviceManager.updateData(CarConstants.CAR_BASIC_VEHICLE_SPEED.value, String.format(java.util.Locale.US, "%.1f", Math.max(0f, currentSpeed)))

            // Random smoothing for other values
            val randomTarget = Math.floor(Math.random() * 101)
            lastValue = (lastValue * (1 - smoothingFactor)) + (randomTarget * smoothingFactor)

            // RPM and Gear
            val playsRPM = currentSpeed > 0f
            val simulatedRPM = if (playsRPM) 1000 + (currentSpeed * 40) + (Math.random() * 500) else 0.0
            serviceManager.updateData(CarConstants.CAR_BASIC_ENGINE_SPEED.value, Math.round(simulatedRPM).toString())

            val gear = when {
                currentSpeed == 0f -> "P"
                currentSpeed < 20f -> "N"
                else -> "D"
            }
            serviceManager.updateData(CarConstants.CAR_BASIC_GEAR_STATUS.value, gear)

            // Fuel and Battery Animation
            animationTimeCounter += SIMULATION_INTERVAL.toInt()
            var percent = 100f
            if (fuelBatteryPhase == "decreasing") {
                percent = 100f - (animationTimeCounter.toFloat() / DECREASE_TIME_MS) * 100f
                if (animationTimeCounter >= DECREASE_TIME_MS) {
                    percent = 0f
                    fuelBatteryPhase = "increasing"
                    animationTimeCounter = 0
                }
            } else {
                percent = (animationTimeCounter.toFloat() / INCREASE_TIME_MS) * 100f
                if (animationTimeCounter >= INCREASE_TIME_MS) {
                    percent = 100f
                    fuelBatteryPhase = "decreasing"
                    animationTimeCounter = 0
                }
            }

            val clampedPercent = Math.max(0f, Math.min(100f, percent)).toInt()
            serviceManager.updateData(CarConstants.CAR_BASIC_REMAIN_FUEL_PERCENTAGE.value, clampedPercent.toString())
            serviceManager.updateData(CarConstants.CAR_EV_INFO_BATTERY_POWER_PERCENTAGE.value, clampedPercent.toString())

            val fuelRange = Math.round((clampedPercent / 100f) * MAX_FUEL_RANGE)
            serviceManager.updateData(CarConstants.CAR_BASIC_REMAIN_ODOMETER.value, fuelRange.toString())

            // Odometer
            if (currentSpeed > 0f) {
                val delta = (currentSpeed * SIMULATION_INTERVAL) / 3600000f
                simulatedOdo += delta
            }
            serviceManager.updateData(CarConstants.CAR_BASIC_ACCUMULATED_ODOMETER.value, Math.floor(simulatedOdo).toInt().toString())

            handler?.postDelayed(this, SIMULATION_INTERVAL)
        }
    }
}
