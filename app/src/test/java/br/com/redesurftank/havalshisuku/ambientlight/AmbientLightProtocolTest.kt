package br.com.redesurftank.havalshisuku.ambientlight

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AmbientLightProtocolTest {
    @Test
    fun redPayloadMatchesValidatedCommand() {
        assertEquals("7B0007FF000000FFBF", AmbientLightProtocol.setRgbHex(255, 0, 0))
    }

    @Test
    fun greenAndBluePayloadsMatchLedcar01Format() {
        assertEquals("7B00070000FF00FFBF", AmbientLightProtocol.setRgbHex(0, 255, 0))
        assertEquals("7B000700FF0000FFBF", AmbientLightProtocol.setRgbHex(0, 0, 255))
    }

    @Test
    fun rgbOrderRemainsAvailableForOtherControllers() {
        assertEquals("7B000700FF0000FFBF", AmbientLightProtocol.setRgbHex(0, 255, 0, ColorOrderMapper.RGB))
        assertEquals("7B00070000FF00FFBF", AmbientLightProtocol.setRgbHex(0, 0, 255, ColorOrderMapper.RGB))
    }

    @Test
    fun rgbValuesAreClampedToByteRange() {
        assertEquals("7B0007FF000000FFBF", AmbientLightProtocol.setRgbHex(300, -1, 0))
    }

    @Test
    fun parseHexIgnoresWhitespace() {
        assertArrayEquals(
            AmbientLightProtocol.setRgbPayload(255, 0, 0),
            AmbientLightProtocol.parseHex("7B 00 07 FF 00 00 00 FF BF")
        )
    }

    @Test
    fun brightnessPayloadMatchesLedcar01DmxFormat() {
        assertEquals("7BFF01206400FFFFBF", AmbientLightProtocol.setBrightnessHex(100))
        assertEquals("7BFF01000000FFFFBF", AmbientLightProtocol.setBrightnessHex(-1))
    }

    @Test
    fun bothOutputUsesIndependentDmxAndBleColorOrders() {
        val payloads =
            AmbientLightProtocol.rgbPayloads(
                0,
                255,
                0,
                ColorOrderMapper.RBG,
                ColorOrderMapper.RGB,
                AmbientLightOutput.BOTH
            ).map { AmbientLightProtocol.bytesToHex(it) }

        assertEquals(listOf("7B00070000FF00FFBF", "7EFF050300FF00FFEF"), payloads)
    }

    @Test
    fun bleOutputDefaultsToRgbOrder() {
        assertEquals("7EFF050300FF00FFEF", AmbientLightProtocol.bytesToHex(AmbientLightProtocol.setBleRgbPayload(0, 255, 0)))
        assertEquals("7EFF05030000FFFFEF", AmbientLightProtocol.bytesToHex(AmbientLightProtocol.setBleRgbPayload(0, 0, 255)))
    }

    @Test
    fun ledLampModePayloadsMatchCar01BleAndDmxFrames() {
        val payloads =
            AmbientLightProtocol.ledLampModePayloads(
                modeId = 13,
                output = AmbientLightOutput.BOTH
            ).map { AmbientLightProtocol.bytesToHex(it) }

        assertEquals(listOf("7BFF030DFFFFFFFFBF", "7EFF030D03FFFFFFEF"), payloads)
    }

    @Test
    fun ledLampSpeedPayloadsMatchCar01BleAndDmxFrames() {
        val payloads =
            AmbientLightProtocol.ledLampSpeedPayloads(
                speed = 50,
                output = AmbientLightOutput.BOTH
            ).map { AmbientLightProtocol.bytesToHex(it) }

        assertEquals(listOf("7BFF0232FF00FFFFBF", "7EFF023200FFFFFFEF"), payloads)
    }

    @Test
    fun dmxNativeEffectPayloadsUseOriginalLedLampModeThenSpeedFrames() {
        val payloads =
            AmbientLightProtocol.ledLampNativeEffectPayloads(
                modeId = 13,
                speed = 50,
                output = AmbientLightOutput.DMX
            ).map { AmbientLightProtocol.bytesToHex(it) }

        assertEquals(listOf("7BFF030DFFFFFFFFBF", "7BFF0232FF00FFFFBF"), payloads)
    }

    @Test
    fun dmxCustomEffectPayloadCarriesAlbumColorModeAndSpeed() {
        assertEquals(
            "7B00070A1E140D32BF",
            AmbientLightProtocol.bytesToHex(
                AmbientLightProtocol.setDmxLedLampCustomEffectPayload(
                    r = 10,
                    g = 20,
                    b = 30,
                    modeId = 13,
                    speed = 50,
                    colorOrder = ColorOrderMapper.RBG
                )
            )
        )
    }
}
