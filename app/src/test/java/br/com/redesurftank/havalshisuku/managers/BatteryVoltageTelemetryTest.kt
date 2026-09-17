package br.com.redesurftank.havalshisuku.managers

import br.com.redesurftank.havalshisuku.models.CarConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryVoltageTelemetryTest {

    @Test
    fun batteryVoltageRoundsToOneDecimalPlace() {
        val key = CarConstants.CAR_BASIC_BATTERY_VOLTAGE.value

        assertEquals("14.2", BatteryVoltageFilter.normalize(key, "14.23"))
        assertEquals("14.3", BatteryVoltageFilter.normalize(key, "14.28"))
        assertEquals("14.2", BatteryVoltageFilter.normalize(key, "14.19"))
        assertEquals("14.1", BatteryVoltageFilter.normalize(key, "14.10"))
        assertEquals("14.1", BatteryVoltageFilter.normalize(key, "14.1"))
        assertEquals("12.0", BatteryVoltageFilter.normalize(key, "12"))
        assertEquals("12.0", BatteryVoltageFilter.normalize(key, "12.000"))
    }

    @Test
    fun batteryVoltageHandlesCommaLocaleAndWhitespace() {
        val key = CarConstants.CAR_BASIC_BATTERY_VOLTAGE.value

        assertEquals("14.2", BatteryVoltageFilter.normalize(key, " 14,24 "))
        assertEquals("13.9", BatteryVoltageFilter.normalize(key, "13,88"))
    }

    @Test
    fun batteryVoltagePreservesNonNumericOrInvalid() {
        val key = CarConstants.CAR_BASIC_BATTERY_VOLTAGE.value

        assertEquals("invalid", BatteryVoltageFilter.normalize(key, "invalid"))
        assertEquals("", BatteryVoltageFilter.normalize(key, ""))
        assertNull(BatteryVoltageFilter.normalize(key, null))
    }

    @Test
    fun otherKeysAreNotAffectedByBatteryRounding() {
        val speedKey = CarConstants.CAR_BASIC_VEHICLE_SPEED.value
        assertEquals("14.23", BatteryVoltageFilter.normalize(speedKey, "14.23"))
        assertEquals("80", BatteryVoltageFilter.normalize(speedKey, "80"))

        val powerKey = CarConstants.CAR_BASIC_BATTERY_POWER_LEVEL.value
        assertEquals("95", BatteryVoltageFilter.normalize(powerKey, "95"))
    }

    @Test
    fun suppressionSuppressesOnlyIdenticalNormalizedVoltage() {
        val key = CarConstants.CAR_BASIC_BATTERY_VOLTAGE.value

        // Same rounded value -> suppress
        assertTrue(BatteryVoltageFilter.shouldSuppress(key, "14.2", "14.2"))

        // Different rounded value -> do not suppress
        assertFalse(BatteryVoltageFilter.shouldSuppress(key, "14.1", "14.2"))

        // First event (no cache yet) -> do not suppress
        assertFalse(BatteryVoltageFilter.shouldSuppress(key, "14.2", null))

        // Other keys are never suppressed by this policy
        val speedKey = CarConstants.CAR_BASIC_VEHICLE_SPEED.value
        assertFalse(BatteryVoltageFilter.shouldSuppress(speedKey, "80", "80"))
    }
}

