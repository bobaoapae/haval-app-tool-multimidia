package br.com.redesurftank.havalshisuku.ambientlight

enum class DriveMode {
    ECO,
    NORMAL,
    SPORT,
    SNOW,
    OFFROAD,
    UNKNOWN
}

object AmbientLightDriveModeMapper {
    fun fromRaw(value: String?): DriveMode {
        return when (value?.trim()?.uppercase()) {
            "2", "ECO" -> DriveMode.ECO
            "0", "NORMAL" -> DriveMode.NORMAL
            "1", "SPORT" -> DriveMode.SPORT
            "3", "SNOW", "NEVE" -> DriveMode.SNOW
            "4", "5", "OFFROAD", "AREIA", "LAMA" -> DriveMode.OFFROAD
            null, "" -> DriveMode.UNKNOWN
            else -> DriveMode.UNKNOWN
        }
    }

    fun colorForMode(mode: DriveMode): LedColor =
        when (mode) {
            DriveMode.ECO -> AmbientLightProtocol.GREEN
            DriveMode.NORMAL -> AmbientLightProtocol.SOFT_BLUE
            DriveMode.SPORT -> AmbientLightProtocol.RED
            DriveMode.SNOW -> AmbientLightProtocol.ICE_BLUE
            DriveMode.OFFROAD -> AmbientLightProtocol.ORANGE
            DriveMode.UNKNOWN -> AmbientLightProtocol.WHITE
        }
}
