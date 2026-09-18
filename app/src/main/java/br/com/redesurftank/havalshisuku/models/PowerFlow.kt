package br.com.redesurftank.havalshisuku.models

/**
 * Derived hybrid power-flow snapshot.
 *
 * Packed as `v1|{state}|{ice}|{front}|{rear}` and published on [KEY_FLOW]
 * only when that string changes. ICE is RPM-gated, not
 * [CarConstants.CAR_BASIC_ENGINE_STATE] (that enum is ignition / screen,
 * not combustion).
 *
 * state: idle | ev | hybrid | ice | regen | charge
 * front/rear: 0 off, 1 drive, -1 regen (P2 / P4).
 */
data class PowerFlow(
    val state: String,
    @get:JvmName("getIceOn")
    val iceOn: Boolean,
    val front: Int,
    val rear: Int,
) {
    fun pack(): String {
        val ice = if (iceOn) 1 else 0
        return "v1|$state|$ice|$front|$rear"
    }

    companion object {
        const val KEY_FLOW = "haval.power.flow"
        const val KEY_ICE = "haval.power.ice"

        const val STATE_IDLE = "idle"
        const val STATE_EV = "ev"
        const val STATE_HYBRID = "hybrid"
        const val STATE_ICE = "ice"
        const val STATE_REGEN = "regen"
        const val STATE_CHARGE = "charge"

        @JvmStatic
        fun unpack(raw: String?): PowerFlow? {
            if (raw.isNullOrEmpty()) return null
            val parts = raw.split('|')
            if (parts.size < 5 || parts[0] != "v1") return null
            val ice = parts[2] == "1"
            val front = parts[3].toIntOrNull() ?: return null
            val rear = parts[4].toIntOrNull() ?: return null
            return PowerFlow(parts[1], ice, front, rear)
        }
    }
}
