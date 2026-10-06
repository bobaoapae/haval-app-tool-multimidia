package br.com.redesurftank.havalshisuku.projectors

import br.com.redesurftank.havalshisuku.models.CarConstants
import br.com.redesurftank.havalshisuku.models.screens.GraphicsScreen
import br.com.redesurftank.havalshisuku.models.screens.RegenScreen
import kotlin.math.max

/**
 * Pure policy for the high-frequency telemetry used by the immutable Sport 0.16.44 themes.
 *
 * The Android host can receive several CAN changes inside one display frame. Keeping only the
 * latest value for these gauges lets the projector deliver one JavaScript batch per frame instead
 * of queueing multiple evaluateJavascript calls ahead of steering-wheel input. Contract
 * subscriptions remain outside this policy and continue through the normal bridge path.
 */
internal object SportTelemetryBatchPolicy {
    const val FRAME_INTERVAL_MS = 33L

    private val batchableKeys =
        setOf(
            CarConstants.CAR_BASIC_VEHICLE_SPEED.value,
            CarConstants.CAR_BASIC_ENGINE_SPEED.value,
            CarConstants.CAR_EV_INFO_ENERGY_OUTPUT_PERCENTAGE.value,
            CarConstants.CAR_EV_INFO_POWER_BATTERY_VOLTAGE.value,
            CarConstants.CAR_EV_INFO_CUR_CHARGE_CURRENT.value
        )

    internal data class Result(
        val controlUpdates: Map<String, String>,
        val batteryVoltage: Float,
        val batteryCurrent: Float
    )

    fun isBatchable(key: String): Boolean = key in batchableKeys

    inline fun shouldBatch(
        isSportTheme: Boolean,
        key: String,
        hasThemeSubscription: () -> Boolean
    ): Boolean = isSportTheme && isBatchable(key) && !hasThemeSubscription()

    fun buildControlUpdates(
        values: Map<String, String>,
        previousBatteryVoltage: Float,
        previousBatteryCurrent: Float,
        adjustSpeed: (String) -> String
    ): Result {
        val updates = linkedMapOf<String, String>()

        values[CarConstants.CAR_BASIC_VEHICLE_SPEED.value]?.let { value ->
            updates["carSpeed"] = adjustSpeed(value)
        }
        values[CarConstants.CAR_BASIC_ENGINE_SPEED.value]?.let { value ->
            updates["engineRPM"] = value
        }
        values[CarConstants.CAR_EV_INFO_ENERGY_OUTPUT_PERCENTAGE.value]?.let { value ->
            val powerFactor = value.toFloatOrNull() ?: 0f
            updates[GraphicsScreen.GraphOptions.EV_POWER_FACTOR] = powerFactor.toString()
            updates[RegenScreen.RegenOptions.REGEN_GRAPH_STATE_NAME] =
                max(0f, -powerFactor).toString()
        }

        val voltageKey = CarConstants.CAR_EV_INFO_POWER_BATTERY_VOLTAGE.value
        val currentKey = CarConstants.CAR_EV_INFO_CUR_CHARGE_CURRENT.value
        val batteryVoltage =
            if (values.containsKey(voltageKey)) {
                values[voltageKey]?.toFloatOrNull() ?: 0f
            } else {
                previousBatteryVoltage
            }
        val batteryCurrent =
            if (values.containsKey(currentKey)) {
                values[currentKey]?.toFloatOrNull() ?: 0f
            } else {
                previousBatteryCurrent
            }
        if (values.containsKey(voltageKey) || values.containsKey(currentKey)) {
            updates[GraphicsScreen.GraphOptions.EV_POWER_KW] =
                (batteryVoltage * batteryCurrent / 1000f).toString()
        }

        return Result(updates, batteryVoltage, batteryCurrent)
    }
}
