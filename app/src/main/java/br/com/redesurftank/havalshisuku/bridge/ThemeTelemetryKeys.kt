package br.com.redesurftank.havalshisuku.bridge

internal object ThemeTelemetryKeys {
    const val TIRE_PRESSURE_FRONT_LEFT = "car.basic.tire_pressure_front_left"
    const val TIRE_PRESSURE_FRONT_RIGHT = "car.basic.tire_pressure_front_right"
    const val TIRE_PRESSURE_REAR_LEFT = "car.basic.tire_pressure_rear_left"
    const val TIRE_PRESSURE_REAR_RIGHT = "car.basic.tire_pressure_rear_right"

    val tirePressureKeys =
        listOf(
            TIRE_PRESSURE_FRONT_LEFT,
            TIRE_PRESSURE_FRONT_RIGHT,
            TIRE_PRESSURE_REAR_LEFT,
            TIRE_PRESSURE_REAR_RIGHT
        )

    fun tirePressureValue(payload: String?, key: String): String? {
        val index = tirePressureKeys.indexOf(key)
        if (index < 0 || payload == null) return null
        return payload.split(',', limit = tirePressureKeys.size).getOrNull(index)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
