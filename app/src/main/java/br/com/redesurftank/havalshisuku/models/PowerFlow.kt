package br.com.redesurftank.havalshisuku.models

/**
 * Derived hybrid power-flow snapshot.
 *
 * Packed as `v1|{tone}|{ice}|{front}|{rear}|{label}` and published on
 * [KEY_FLOW] only when that string changes. ICE is RPM-gated, not
 * [CarConstants.CAR_BASIC_ENGINE_STATE] (that enum is ignition / screen,
 * not combustion).
 *
 * front/rear: 0 off, 1 drive, -1 regen (P2 / P4).
 */
data class PowerFlow(
    val tone: String,
    @get:JvmName("getIceOn")
    val iceOn: Boolean,
    val front: Int,
    val rear: Int,
    val label: String,
) {
    fun pack(): String {
        val ice = if (iceOn) 1 else 0
        return "v1|$tone|$ice|$front|$rear|$label"
    }

    companion object {
        const val KEY_FLOW = "haval.power.flow"
        const val KEY_ICE = "haval.power.ice"

        const val TONE_IDLE = "idle"
        const val TONE_EV = "ev"
        const val TONE_HYBRID = "hybrid"
        const val TONE_ICE = "ice"
        const val TONE_REGEN = "regen"
        const val TONE_CHARGE = "charge"

        @JvmStatic
        fun unpack(raw: String?): PowerFlow? {
            if (raw.isNullOrEmpty()) return null
            val parts = raw.split('|', limit = 6)
            if (parts.size < 6 || parts[0] != "v1") return null
            val ice = parts[2] == "1"
            val front = parts[3].toIntOrNull() ?: return null
            val rear = parts[4].toIntOrNull() ?: return null
            return PowerFlow(parts[1], ice, front, rear, parts[5])
        }
    }
}
