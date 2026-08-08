package br.com.redesurftank.havalshisuku.models

/**
 * Background sólido do cluster: uma cor RGB com vinheta opcional.
 *
 * Serializado em uma única string de preferência (CUSTOM_BACKGROUND_VALUE_D1) no formato
 * `#RRGGBB|V45`, onde `V` é a intensidade da vinheta em porcentagem (0 = sem vinheta).
 */
data class SolidBackgroundSpec(val color: Int, val vignette: Int) {

    fun encode(): String = "#%06X|V%d".format(color and 0x00FFFFFF, vignette.coerceIn(0, 100))

    companion object {
        const val TYPE = "COLOR"

        val DEFAULT = SolidBackgroundSpec(color = 0xFF101820.toInt(), vignette = 45)

        fun parse(value: String?): SolidBackgroundSpec? {
            if (value.isNullOrBlank()) return null
            val parts = value.trim().split("|")
            val hex = parts[0].removePrefix("#")
            if (hex.length != 6) return null
            val rgb = hex.toLongOrNull(16)?.toInt() ?: return null
            val vignette = parts.getOrNull(1)
                ?.removePrefix("V")
                ?.toIntOrNull()
                ?.coerceIn(0, 100)
                ?: 0
            return SolidBackgroundSpec(0xFF000000.toInt() or rgb, vignette)
        }
    }
}
