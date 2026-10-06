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
        val tempKey = CarConstants.CAR_BASIC_INSIDE_TEMP.value
        assertEquals("23.45", BatteryVoltageFilter.normalize(tempKey, "23.45"))
        assertEquals("22", BatteryVoltageFilter.normalize(tempKey, "22"))

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
        val tempKey = CarConstants.CAR_BASIC_INSIDE_TEMP.value
        assertFalse(BatteryVoltageFilter.shouldSuppress(tempKey, "22", "22"))
    }

    @Test
    fun suppressionTrackerFlushesOnlyAfterInterval() {
        var currentTime = 1_000_000L
        val logs = mutableListOf<String>()
        val tracker = BatteryVoltageFilter.SuppressionTracker(
            windowMs = 10 * 60 * 1000L, // 10 minutes
            clock = { currentTime },
            logger = { logs.add(it) }
        )

        val key = CarConstants.CAR_BASIC_BATTERY_VOLTAGE.value

        // Record 5 events at t=1_000_000
        repeat(5) { tracker.recordSuppressed(key) }
        assertEquals(5L, tracker.getTotalSuppressed())
        assertEquals(0, logs.size) // Not flushed yet

        // Advance 9 minutes (540,000 ms) -> still inside 10-minute window
        currentTime += 9 * 60 * 1000L
        tracker.recordSuppressed(key)
        assertEquals(6L, tracker.getTotalSuppressed())
        assertEquals(0, logs.size) // Still not flushed

        // Advance 1 more minute (total 10 minutes elapsed) and record an event
        currentTime += 1 * 60 * 1000L
        tracker.recordSuppressed(key)

        // Must have flushed!
        assertEquals(1, logs.size)
        assertTrue(logs[0].contains("Suppressed 7 telemetry events in the last 10.0 min"))
        assertTrue(logs[0].contains("car.basic.battery_voltage: 7"))

        // Counter reset after flush
        assertEquals(0L, tracker.getTotalSuppressed())
    }

    @Test
    fun steeringWheelAngleRoundsToIntegerDegrees() {
        val key = CarConstants.CAR_BASIC_STEERING_WHEEL_ANGLE.value

        assertEquals("14", BatteryVoltageFilter.normalize(key, "14.32"))
        assertEquals("15", BatteryVoltageFilter.normalize(key, "14.8"))
        assertEquals("-14", BatteryVoltageFilter.normalize(key, "-14.2"))
        assertEquals("-15", BatteryVoltageFilter.normalize(key, "-14.9"))
        assertEquals("0", BatteryVoltageFilter.normalize(key, "0.0"))
        assertEquals("0", BatteryVoltageFilter.normalize(key, " 0,25 "))
        assertEquals("invalid", BatteryVoltageFilter.normalize(key, "invalid"))
    }

    @Test
    fun steeringWheelAngleSuppressesIdenticalIntegerDegrees() {
        val key = CarConstants.CAR_BASIC_STEERING_WHEEL_ANGLE.value

        // Same degree -> suppress
        assertTrue(BatteryVoltageFilter.shouldSuppress(key, "14", "14"))

        // Different degree -> do not suppress
        assertFalse(BatteryVoltageFilter.shouldSuppress(key, "15", "14"))

        // First event (no cache) -> do not suppress
        assertFalse(BatteryVoltageFilter.shouldSuppress(key, "14", null))
    }

    @Test
    fun vehicleSpeedNormalizesToIntegerKmH() {
        val key = CarConstants.CAR_BASIC_VEHICLE_SPEED.value

        assertEquals("0", BatteryVoltageFilter.normalize(key, "0"))
        assertEquals("0", BatteryVoltageFilter.normalize(key, "0.0"))
        assertEquals("0", BatteryVoltageFilter.normalize(key, " 0,2 "))
        assertEquals("80", BatteryVoltageFilter.normalize(key, "80.4"))
        assertEquals("81", BatteryVoltageFilter.normalize(key, "80.6"))
        assertEquals("0", BatteryVoltageFilter.normalize(key, "-5"))
        assertEquals("invalid", BatteryVoltageFilter.normalize(key, "invalid"))
    }

    @Test
    fun vehicleSpeedSuppressesIdenticalConsecutiveValues() {
        val key = CarConstants.CAR_BASIC_VEHICLE_SPEED.value

        // Steady cruise or stopped at light -> suppress
        assertTrue(BatteryVoltageFilter.shouldSuppress(key, "0", "0"))
        assertTrue(BatteryVoltageFilter.shouldSuppress(key, "80", "80"))

        // Acceleration or braking -> dispatch
        assertFalse(BatteryVoltageFilter.shouldSuppress(key, "1", "0"))
        assertFalse(BatteryVoltageFilter.shouldSuppress(key, "81", "80"))

        // Boot / first event -> dispatch
        assertFalse(BatteryVoltageFilter.shouldSuppress(key, "0", null))
    }

    @Test
    fun tpmsStatusRoundsToSingleDecimal() {
        val key = CarConstants.CAR_BASIC_TPMS_STATUS.value

        val raw = "{2.48922,25.0,2.48922,24.0,2.2559,24.0,2.48922,24.0}"
        val expected = "{2.5,25.0,2.5,24.0,2.3,24.0,2.5,24.0}"
        assertEquals(expected, BatteryVoltageFilter.normalize(key, raw))

        // Raw array with slight micro-noise in 4th/5th decimal yields identical normalized string
        val rawJitter = "{2.48918,25.0,2.48925,24.0,2.2561,24.0,2.48920,24.0}"
        assertEquals(expected, BatteryVoltageFilter.normalize(key, rawJitter))
    }

    @Test
    fun tpmsStatusSuppressesIdenticalNormalizedValues() {
        val key = CarConstants.CAR_BASIC_TPMS_STATUS.value
        val normalized = "{2.5,25.0,2.5,24.0,2.3,24.0,2.5,24.0}"

        assertTrue(BatteryVoltageFilter.shouldSuppress(key, normalized, normalized))
        assertFalse(BatteryVoltageFilter.shouldSuppress(key, "{2.4,25.0,2.5,24.0,2.3,24.0,2.5,24.0}", normalized))
        assertFalse(BatteryVoltageFilter.shouldSuppress(key, normalized, null))
    }
}




