package br.com.redesurftank.havalshisuku.managers

import br.com.redesurftank.havalshisuku.models.CarConstants
import br.com.redesurftank.havalshisuku.models.PowerFlow

/**
 * Holds last ICE hysteresis and last packed flow. Call [ingest] after each
 * cache write; a non-null result means publish [PowerFlow.KEY_FLOW] /
 * [PowerFlow.KEY_ICE]. RPM ticks must not broadcast unless the mode changed.
 */
class PowerFlowTracker {
    private var iceOn = false
    private var lastPacked: String? = null

    fun ingest(key: String?, cache: Map<String, String>): PowerFlow? {
        if (key == null || !INPUT_KEYS.contains(key)) return null
        val rpm = PowerFlowMapper.parseNumber(cache[ENGINE_SPEED])
        iceOn = rpmHysteresis(iceOn, rpm)
        val charging = cache[CHARGING_STATE]?.trim() == "1"
        val speed = PowerFlowMapper.parseNumber(cache[VEHICLE_SPEED])
        val kw = PowerFlowMapper.packKw(cache[BATTERY_VOLTAGE], cache[CHARGE_CURRENT])
        val flow = PowerFlowMapper.resolve(
            cache[DRIVE_STATE],
            iceOn,
            speed,
            kw,
            charging,
        )
        val packed = flow.pack()
        if (packed == lastPacked) return null
        lastPacked = packed
        return flow
    }

    companion object {
        private val ENGINE_SPEED = CarConstants.CAR_BASIC_ENGINE_SPEED.value
        private val VEHICLE_SPEED = CarConstants.CAR_BASIC_VEHICLE_SPEED.value
        private val DRIVE_STATE = CarConstants.CAR_EV_INFO_ENERGY_DRIVE_STATE.value
        private val CHARGING_STATE = CarConstants.CAR_EV_INFO_CHARGING_STATE.value
        private val BATTERY_VOLTAGE = CarConstants.CAR_EV_INFO_POWER_BATTERY_VOLTAGE.value
        private val CHARGE_CURRENT = CarConstants.CAR_EV_INFO_CUR_CHARGE_CURRENT.value

        @JvmField
        val INPUT_KEYS: Set<String> = setOf(
            ENGINE_SPEED,
            VEHICLE_SPEED,
            DRIVE_STATE,
            CHARGING_STATE,
            BATTERY_VOLTAGE,
            CHARGE_CURRENT,
        )

        /** DHT idle is ~800–1100. Off reports 0. Hysteresis avoids cranking flicker. */
        @JvmStatic
        fun rpmHysteresis(wasOn: Boolean, rpm: Double): Boolean {
            if (!rpm.isFinite() || rpm < 0.0 || rpm > 8000.0) return wasOn
            val rounded = kotlin.math.round(rpm)
            if (rounded == 65535.0 || rounded == 1023.0 || rounded == 2047.0) return wasOn
            return if (wasOn) rpm >= 200.0 else rpm >= 400.0
        }
    }
}
