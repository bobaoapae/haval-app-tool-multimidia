package br.com.redesurftank.havalshisuku.bridge

object BridgeContractTranslator {
    private val legacyToCanonical = mapOf(
        "car.basic.speed" to "car.basic.vehicle_speed",
        "car.basic.gear" to "car.basic.gear_status",
        "car.basic.temp_inside" to "car.basic.inside_temp",
        "car.basic.temp_outside" to "car.basic.outside_temp"
    )

    private val canonicalToLegacy = legacyToCanonical.entries.associate { it.value to it.key }

    fun translateThemeKeyToCanonical(themeKey: String): String {
        return legacyToCanonical[themeKey] ?: themeKey
    }

    fun translateCanonicalToThemeKey(canonicalKey: String): String {
        return canonicalToLegacy[canonicalKey] ?: canonicalKey
    }
}
