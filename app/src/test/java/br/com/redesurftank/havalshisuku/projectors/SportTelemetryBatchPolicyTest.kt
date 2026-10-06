package br.com.redesurftank.havalshisuku.projectors

import br.com.redesurftank.havalshisuku.models.CarConstants
import br.com.redesurftank.havalshisuku.models.screens.GraphicsScreen
import br.com.redesurftank.havalshisuku.models.screens.RegenScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportTelemetryBatchPolicyTest {
    @Test
    fun `batches only high frequency Sport gauge telemetry`() {
        assertTrue(
            SportTelemetryBatchPolicy.isBatchable(
                CarConstants.CAR_BASIC_VEHICLE_SPEED.value
            )
        )
        assertTrue(
            SportTelemetryBatchPolicy.isBatchable(
                CarConstants.CAR_EV_INFO_ENERGY_OUTPUT_PERCENTAGE.value
            )
        )
        assertFalse(
            SportTelemetryBatchPolicy.isBatchable(
                CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.value
            )
        )
    }

    @Test
    fun `preserves original bridge path for subscribed keys and other themes`() {
        val speedKey = CarConstants.CAR_BASIC_VEHICLE_SPEED.value
        var subscriptionChecks = 0

        assertTrue(
            SportTelemetryBatchPolicy.shouldBatch(
                isSportTheme = true,
                key = speedKey,
                hasThemeSubscription = { false }
            )
        )
        assertFalse(
            SportTelemetryBatchPolicy.shouldBatch(
                isSportTheme = true,
                key = speedKey,
                hasThemeSubscription = { true }
            )
        )
        assertFalse(
            SportTelemetryBatchPolicy.shouldBatch(
                isSportTheme = false,
                key = speedKey,
                hasThemeSubscription = {
                    subscriptionChecks += 1
                    false
                }
            )
        )
        assertFalse(
            SportTelemetryBatchPolicy.shouldBatch(
                isSportTheme = true,
                key = CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.value,
                hasThemeSubscription = {
                    subscriptionChecks += 1
                    false
                }
            )
        )
        assertEquals(0, subscriptionChecks)
    }

    @Test
    fun `coalesces speed power and regen into one control update map`() {
        val result =
            SportTelemetryBatchPolicy.buildControlUpdates(
                values =
                    mapOf(
                        CarConstants.CAR_BASIC_VEHICLE_SPEED.value to "57.4",
                        CarConstants.CAR_BASIC_ENGINE_SPEED.value to "1830",
                        CarConstants.CAR_EV_INFO_ENERGY_OUTPUT_PERCENTAGE.value to "-18.5"
                    ),
                previousBatteryVoltage = 350f,
                previousBatteryCurrent = 10f,
                adjustSpeed = { "58" }
            )

        assertEquals("58", result.controlUpdates["carSpeed"])
        assertEquals("1830", result.controlUpdates["engineRPM"])
        assertEquals(
            "-18.5",
            result.controlUpdates[GraphicsScreen.GraphOptions.EV_POWER_FACTOR]
        )
        assertEquals(
            "18.5",
            result.controlUpdates[RegenScreen.RegenOptions.REGEN_GRAPH_STATE_NAME]
        )
        assertEquals(4, result.controlUpdates.size)
    }

    @Test
    fun `computes battery power once from latest voltage and current in the frame`() {
        val result =
            SportTelemetryBatchPolicy.buildControlUpdates(
                values =
                    mapOf(
                        CarConstants.CAR_EV_INFO_POWER_BATTERY_VOLTAGE.value to "360",
                        CarConstants.CAR_EV_INFO_CUR_CHARGE_CURRENT.value to "-20"
                    ),
                previousBatteryVoltage = 300f,
                previousBatteryCurrent = 5f,
                adjustSpeed = { it }
            )

        assertEquals(-7.2f, result.controlUpdates[GraphicsScreen.GraphOptions.EV_POWER_KW]!!.toFloat())
        assertEquals(360f, result.batteryVoltage)
        assertEquals(-20f, result.batteryCurrent)
        assertEquals(1, result.controlUpdates.size)
    }

    @Test
    fun `reuses the last counterpart when only one battery signal changes`() {
        val result =
            SportTelemetryBatchPolicy.buildControlUpdates(
                values =
                    mapOf(
                        CarConstants.CAR_EV_INFO_CUR_CHARGE_CURRENT.value to "12"
                    ),
                previousBatteryVoltage = 350f,
                previousBatteryCurrent = 1f,
                adjustSpeed = { it }
            )

        assertEquals(4.2f, result.controlUpdates[GraphicsScreen.GraphOptions.EV_POWER_KW]!!.toFloat())
        assertEquals(350f, result.batteryVoltage)
        assertEquals(12f, result.batteryCurrent)
    }
}
