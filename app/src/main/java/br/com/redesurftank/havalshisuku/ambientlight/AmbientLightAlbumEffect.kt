package br.com.redesurftank.havalshisuku.ambientlight

enum class AmbientLightAlbumEffect(
    val label: String,
    val shortLabel: String,
    val ledLampModeId: Int?,
    val ledLampSource: String,
    internal val pattern: AmbientLightAlbumEffectPattern,
    internal val direction: AmbientLightAlbumEffectDirection,
    internal val anchor: AmbientLightAlbumEffectAnchor
) {
    FORWARD_6_BU(
        label = "13 Forward 6 Colors BU",
        shortLabel = "13 BU >",
        ledLampModeId = 13,
        ledLampSource = "ble_mode",
        pattern = AmbientLightAlbumEffectPattern.SIX_COLOR,
        direction = AmbientLightAlbumEffectDirection.FORWARD,
        anchor = AmbientLightAlbumEffectAnchor.BLUE
    ),
    BACKWARD_6_BU(
        label = "14 Backward 6 Colors BU",
        shortLabel = "14 BU <",
        ledLampModeId = 14,
        ledLampSource = "ble_mode",
        pattern = AmbientLightAlbumEffectPattern.SIX_COLOR,
        direction = AmbientLightAlbumEffectDirection.BACKWARD,
        anchor = AmbientLightAlbumEffectAnchor.BLUE
    ),
    FORWARD_6_GN(
        label = "11 Forward 6 Colors GN",
        shortLabel = "11 GN >",
        ledLampModeId = 11,
        ledLampSource = "ble_mode",
        pattern = AmbientLightAlbumEffectPattern.SIX_COLOR,
        direction = AmbientLightAlbumEffectDirection.FORWARD,
        anchor = AmbientLightAlbumEffectAnchor.GREEN
    ),
    BACKWARD_6_GN(
        label = "12 Backward 6 Colors GN",
        shortLabel = "12 GN <",
        ledLampModeId = 12,
        ledLampSource = "ble_mode",
        pattern = AmbientLightAlbumEffectPattern.SIX_COLOR,
        direction = AmbientLightAlbumEffectDirection.BACKWARD,
        anchor = AmbientLightAlbumEffectAnchor.GREEN
    ),
    FORWARD_6_CN(
        label = "15 Forward 6 Colors CN",
        shortLabel = "15 CN >",
        ledLampModeId = 15,
        ledLampSource = "ble_mode",
        pattern = AmbientLightAlbumEffectPattern.SIX_COLOR,
        direction = AmbientLightAlbumEffectDirection.FORWARD,
        anchor = AmbientLightAlbumEffectAnchor.CYAN
    ),
    BACKWARD_6_CN(
        label = "16 Backward 6 Colors CN",
        shortLabel = "16 CN <",
        ledLampModeId = 16,
        ledLampSource = "ble_mode",
        pattern = AmbientLightAlbumEffectPattern.SIX_COLOR,
        direction = AmbientLightAlbumEffectDirection.BACKWARD,
        anchor = AmbientLightAlbumEffectAnchor.CYAN
    ),
    DIGITAL_WAVE(
        label = "Onda digital",
        shortLabel = "Wave",
        ledLampModeId = null,
        ledLampSource = "local_album_palette",
        pattern = AmbientLightAlbumEffectPattern.DIGITAL_WAVE,
        direction = AmbientLightAlbumEffectDirection.FORWARD,
        anchor = AmbientLightAlbumEffectAnchor.ALBUM
    ),
    PULSE(
        label = "Pulse",
        shortLabel = "Pulse",
        ledLampModeId = null,
        ledLampSource = "Effect_Mode_Array: Pulse,12",
        pattern = AmbientLightAlbumEffectPattern.PULSE,
        direction = AmbientLightAlbumEffectDirection.FORWARD,
        anchor = AmbientLightAlbumEffectAnchor.ALBUM
    ),
    FORWARD_RUN_7(
        label = "95 Forward Run 7 Colors",
        shortLabel = "95 Run >",
        ledLampModeId = 95,
        ledLampSource = "dmx_mode",
        pattern = AmbientLightAlbumEffectPattern.RUN,
        direction = AmbientLightAlbumEffectDirection.FORWARD,
        anchor = AmbientLightAlbumEffectAnchor.ALBUM
    ),
    BACKWARD_RUN_7(
        label = "96 Backward Run 7 Colors",
        shortLabel = "96 Run <",
        ledLampModeId = 96,
        ledLampSource = "dmx_mode",
        pattern = AmbientLightAlbumEffectPattern.RUN,
        direction = AmbientLightAlbumEffectDirection.BACKWARD,
        anchor = AmbientLightAlbumEffectAnchor.ALBUM
    ),
    FORWARD_FLOW_7(
        label = "123 Forward Flow 7 Colors",
        shortLabel = "123 Flow >",
        ledLampModeId = 123,
        ledLampSource = "dmx_mode",
        pattern = AmbientLightAlbumEffectPattern.FLOW,
        direction = AmbientLightAlbumEffectDirection.FORWARD,
        anchor = AmbientLightAlbumEffectAnchor.ALBUM
    ),
    BACKWARD_FLOW_7(
        label = "124 Backward Flow 7 Colors",
        shortLabel = "124 Flow <",
        ledLampModeId = 124,
        ledLampSource = "dmx_mode",
        pattern = AmbientLightAlbumEffectPattern.FLOW,
        direction = AmbientLightAlbumEffectDirection.BACKWARD,
        anchor = AmbientLightAlbumEffectAnchor.ALBUM
    );

    companion object {
        val DEFAULT = FORWARD_6_BU

        fun fromStored(value: String?): AmbientLightAlbumEffect =
            values().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEFAULT
    }

    internal fun dmxModeIdForAlbumColor(color: LedColor): Int? {
        val family = DmxAlbumColorFamily.nearest(color)
        return when (pattern) {
            AmbientLightAlbumEffectPattern.SIX_COLOR -> dmxSixColorModeId(direction, family)
            AmbientLightAlbumEffectPattern.RUN -> dmxRunModeId(direction, family)
            AmbientLightAlbumEffectPattern.FLOW -> dmxFlowModeId(direction, family)
            else -> ledLampModeId
        }
    }
}

enum class AmbientLightAlbumEffectPattern {
    DIGITAL_WAVE,
    PULSE,
    SIX_COLOR,
    RUN,
    FLOW
}

enum class AmbientLightAlbumEffectDirection(val multiplier: Float) {
    FORWARD(1f),
    BACKWARD(-1f)
}

enum class AmbientLightAlbumEffectAnchor {
    ALBUM,
    GREEN,
    BLUE,
    CYAN
}

private enum class DmxAlbumColorFamily(val reference: LedColor) {
    RED(LedColor(255, 0, 0)),
    GREEN(LedColor(0, 255, 0)),
    BLUE(LedColor(0, 0, 255)),
    YELLOW(LedColor(255, 255, 0)),
    CYAN(LedColor(0, 255, 255)),
    VIOLET(LedColor(255, 0, 255)),
    WHITE(LedColor(255, 255, 255));

    companion object {
        fun nearest(color: LedColor): DmxAlbumColorFamily {
            val safe = color.coerce()
            return values().minByOrNull { family ->
                squaredDistance(safe, family.reference)
            } ?: BLUE
        }

        private fun squaredDistance(left: LedColor, right: LedColor): Int {
            val dr = left.r - right.r
            val dg = left.g - right.g
            val db = left.b - right.b
            return (dr * dr) + (dg * dg) + (db * db)
        }
    }
}

private fun dmxSixColorModeId(
    direction: AmbientLightAlbumEffectDirection,
    family: DmxAlbumColorFamily
): Int =
    when (family) {
        DmxAlbumColorFamily.RED -> direction.pickDmxMode(forward = 9, backward = 10)
        DmxAlbumColorFamily.GREEN -> direction.pickDmxMode(forward = 11, backward = 12)
        DmxAlbumColorFamily.BLUE -> direction.pickDmxMode(forward = 13, backward = 14)
        DmxAlbumColorFamily.CYAN -> direction.pickDmxMode(forward = 15, backward = 16)
        DmxAlbumColorFamily.YELLOW -> direction.pickDmxMode(forward = 17, backward = 18)
        DmxAlbumColorFamily.VIOLET -> direction.pickDmxMode(forward = 19, backward = 20)
        DmxAlbumColorFamily.WHITE -> direction.pickDmxMode(forward = 21, backward = 22)
    }

private fun dmxRunModeId(
    direction: AmbientLightAlbumEffectDirection,
    family: DmxAlbumColorFamily
): Int =
    when (family) {
        DmxAlbumColorFamily.RED -> direction.pickDmxMode(forward = 97, backward = 98)
        DmxAlbumColorFamily.GREEN -> direction.pickDmxMode(forward = 99, backward = 100)
        DmxAlbumColorFamily.BLUE -> direction.pickDmxMode(forward = 101, backward = 102)
        DmxAlbumColorFamily.YELLOW -> direction.pickDmxMode(forward = 103, backward = 104)
        DmxAlbumColorFamily.CYAN -> direction.pickDmxMode(forward = 105, backward = 106)
        DmxAlbumColorFamily.VIOLET -> direction.pickDmxMode(forward = 107, backward = 108)
        DmxAlbumColorFamily.WHITE -> direction.pickDmxMode(forward = 109, backward = 110)
    }

private fun dmxFlowModeId(
    direction: AmbientLightAlbumEffectDirection,
    family: DmxAlbumColorFamily
): Int =
    when (family) {
        DmxAlbumColorFamily.RED,
        DmxAlbumColorFamily.GREEN -> direction.pickDmxMode(forward = 125, backward = 126)
        DmxAlbumColorFamily.BLUE -> direction.pickDmxMode(forward = 127, backward = 128)
        DmxAlbumColorFamily.YELLOW -> direction.pickDmxMode(forward = 129, backward = 130)
        DmxAlbumColorFamily.CYAN -> direction.pickDmxMode(forward = 131, backward = 132)
        DmxAlbumColorFamily.VIOLET -> direction.pickDmxMode(forward = 133, backward = 134)
        DmxAlbumColorFamily.WHITE -> direction.pickDmxMode(forward = 135, backward = 136)
    }

private fun AmbientLightAlbumEffectDirection.pickDmxMode(forward: Int, backward: Int): Int =
    when (this) {
        AmbientLightAlbumEffectDirection.FORWARD -> forward
        AmbientLightAlbumEffectDirection.BACKWARD -> backward
    }
