package br.com.redesurftank.havalshisuku.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeTelemetryKeysTest {
    @Test
    fun mapsPressurePayloadInVehicleWheelOrder() {
        val payload = "250,248,246,249"

        assertEquals("250", ThemeTelemetryKeys.tirePressureValue(payload, ThemeTelemetryKeys.TIRE_PRESSURE_FRONT_LEFT))
        assertEquals("248", ThemeTelemetryKeys.tirePressureValue(payload, ThemeTelemetryKeys.TIRE_PRESSURE_FRONT_RIGHT))
        assertEquals("246", ThemeTelemetryKeys.tirePressureValue(payload, ThemeTelemetryKeys.TIRE_PRESSURE_REAR_LEFT))
        assertEquals("249", ThemeTelemetryKeys.tirePressureValue(payload, ThemeTelemetryKeys.TIRE_PRESSURE_REAR_RIGHT))
    }

    @Test
    fun missingOrUnknownPressureDoesNotBecomeAReading() {
        assertNull(
            ThemeTelemetryKeys.tirePressureValue(
                "250,,246,249",
                ThemeTelemetryKeys.TIRE_PRESSURE_FRONT_RIGHT
            )
        )
        assertNull(ThemeTelemetryKeys.tirePressureValue(null, ThemeTelemetryKeys.TIRE_PRESSURE_FRONT_LEFT))
        assertNull(ThemeTelemetryKeys.tirePressureValue("250", ThemeTelemetryKeys.TIRE_PRESSURE_REAR_RIGHT))
        assertNull(ThemeTelemetryKeys.tirePressureValue("250,248,246,249", "unknown"))
    }
}
