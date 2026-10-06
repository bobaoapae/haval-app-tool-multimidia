package br.com.redesurftank.havalshisuku.ambientlight

enum class ColorOrderMapper(val label: String) {
    RGB("RGB"),
    RBG("RBG"),
    GRB("GRB"),
    GBR("GBR"),
    BRG("BRG"),
    BGR("BGR");

    fun apply(color: LedColor): LedColor {
        val coerced = color.coerce()
        return when (this) {
            RGB -> LedColor(coerced.r, coerced.g, coerced.b)
            RBG -> LedColor(coerced.r, coerced.b, coerced.g)
            GRB -> LedColor(coerced.g, coerced.r, coerced.b)
            GBR -> LedColor(coerced.g, coerced.b, coerced.r)
            BRG -> LedColor(coerced.b, coerced.r, coerced.g)
            BGR -> LedColor(coerced.b, coerced.g, coerced.r)
        }
    }

    companion object {
        val DEFAULT_DMX = RBG
        val DEFAULT_BLE = RGB
        val DEFAULT = DEFAULT_DMX

        fun fromStored(value: String?, fallback: ColorOrderMapper = DEFAULT): ColorOrderMapper =
            values().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: fallback
    }
}
