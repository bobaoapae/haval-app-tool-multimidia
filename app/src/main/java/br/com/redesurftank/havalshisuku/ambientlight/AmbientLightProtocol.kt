package br.com.redesurftank.havalshisuku.ambientlight

import java.util.Locale

data class LedColor(val r: Int, val g: Int, val b: Int) {
    fun coerce(): LedColor = LedColor(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
}

object AmbientLightProtocol {
    const val SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb"
    const val CHARACTERISTIC_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb"
    const val MIN_WRITE_INTERVAL_MS = 50L

    val RED = LedColor(255, 0, 0)
    val GREEN = LedColor(0, 255, 0)
    val BLUE = LedColor(0, 0, 255)
    val WHITE = LedColor(255, 255, 255)
    val SOFT_BLUE = LedColor(80, 120, 255)
    val ICE_BLUE = LedColor(0, 180, 255)
    val ORANGE = LedColor(255, 120, 0)
    val YELLOW = LedColor(255, 255, 0)
    val PURPLE = LedColor(255, 0, 255)

    fun setRgbPayload(
        r: Int,
        g: Int,
        b: Int,
        colorOrder: ColorOrderMapper = ColorOrderMapper.DEFAULT
    ): ByteArray = setDmxRgbPayload(r, g, b, colorOrder)

    fun setDmxRgbPayload(
        r: Int,
        g: Int,
        b: Int,
        colorOrder: ColorOrderMapper = ColorOrderMapper.DEFAULT
    ): ByteArray {
        val color = colorOrder.apply(LedColor(r, g, b))
        return byteArrayOf(
            0x7B.toByte(),
            0x00.toByte(),
            0x07.toByte(),
            color.r.toByte(),
            color.g.toByte(),
            color.b.toByte(),
            0x00.toByte(),
            0xFF.toByte(),
            0xBF.toByte()
        )
    }

    fun setBleRgbPayload(
        r: Int,
        g: Int,
        b: Int,
        colorOrder: ColorOrderMapper = ColorOrderMapper.DEFAULT_BLE
    ): ByteArray {
        val color = colorOrder.apply(LedColor(r, g, b))
        return byteArrayOf(
            0x7E.toByte(),
            0xFF.toByte(),
            0x05.toByte(),
            0x03.toByte(),
            color.r.toByte(),
            color.g.toByte(),
            color.b.toByte(),
            0xFF.toByte(),
            0xEF.toByte()
        )
    }

    fun setBrightnessPayload(percent: Int): ByteArray = setDmxBrightnessPayload(percent)

    fun setDmxBrightnessPayload(percent: Int): ByteArray {
        val brightness = percent.coerceIn(0, 100)
        val scaled = (brightness * 32) / 100
        return byteArrayOf(
            0x7B.toByte(),
            0xFF.toByte(),
            0x01.toByte(),
            scaled.toByte(),
            brightness.toByte(),
            0x00.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xBF.toByte()
        )
    }

    fun setBleBrightnessPayload(percent: Int): ByteArray {
        val brightness = percent.coerceIn(0, 100)
        return byteArrayOf(
            0x7E.toByte(),
            0xFF.toByte(),
            0x01.toByte(),
            brightness.toByte(),
            0x00.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xEF.toByte()
        )
    }

    fun rgbPayloads(
        r: Int,
        g: Int,
        b: Int,
        dmxColorOrder: ColorOrderMapper,
        bleColorOrder: ColorOrderMapper,
        output: AmbientLightOutput
    ): List<ByteArray> =
        when (output) {
            AmbientLightOutput.BLE -> listOf(setBleRgbPayload(r, g, b, bleColorOrder))
            AmbientLightOutput.DMX -> listOf(setDmxRgbPayload(r, g, b, dmxColorOrder))
            AmbientLightOutput.BOTH ->
                listOf(
                    setDmxRgbPayload(r, g, b, dmxColorOrder),
                    setBleRgbPayload(r, g, b, bleColorOrder)
                )
        }

    fun rgbPayloads(
        r: Int,
        g: Int,
        b: Int,
        colorOrder: ColorOrderMapper,
        output: AmbientLightOutput
    ): List<ByteArray> = rgbPayloads(r, g, b, colorOrder, colorOrder, output)

    fun brightnessPayloads(percent: Int, output: AmbientLightOutput): List<ByteArray> =
        when (output) {
            AmbientLightOutput.BLE -> listOf(setBleBrightnessPayload(percent))
            AmbientLightOutput.DMX -> listOf(setDmxBrightnessPayload(percent))
            AmbientLightOutput.BOTH -> listOf(setDmxBrightnessPayload(percent), setBleBrightnessPayload(percent))
        }

    fun setRgbHex(
        r: Int,
        g: Int,
        b: Int,
        colorOrder: ColorOrderMapper = ColorOrderMapper.DEFAULT
    ): String = bytesToHex(setRgbPayload(r, g, b, colorOrder))

    fun setBrightnessHex(percent: Int): String = bytesToHex(setBrightnessPayload(percent))

    fun parseHex(hex: String): ByteArray {
        val clean = hex.replace(" ", "").replace("\n", "").replace("\t", "")
            .uppercase(Locale.US)
        require(clean.isNotEmpty()) { "Payload HEX vazio" }
        require(clean.length % 2 == 0) { "Payload HEX deve ter quantidade par de caracteres" }
        require(clean.all { it in '0'..'9' || it in 'A'..'F' }) { "Payload HEX invalido" }
        return ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString(separator = "") { "%02X".format(Locale.US, it.toInt() and 0xFF) }
}
