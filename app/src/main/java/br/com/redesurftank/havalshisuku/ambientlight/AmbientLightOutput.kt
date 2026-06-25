package br.com.redesurftank.havalshisuku.ambientlight

enum class AmbientLightOutput(val label: String) {
    BLE("BLE"),
    DMX("DMX"),
    BOTH("BLE + DMX");

    companion object {
        val DEFAULT = DMX

        fun fromStored(value: String?): AmbientLightOutput =
            values().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEFAULT
    }
}
