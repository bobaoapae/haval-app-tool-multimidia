package br.com.redesurftank.havalshisuku.managers

import br.com.redesurftank.havalshisuku.models.CarConstants
import br.com.redesurftank.havalshisuku.models.PowerFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerFlowMapperTest {
    @Test
    fun mappedEvDoesNotLightIceWithoutRpm() {
        val flow = PowerFlowMapper.resolve("14", iceOn = false, speedKmh = 40.0, packKw = 12.0, charging = false)
        assertEquals(PowerFlow.TONE_EV, flow.tone)
        assertFalse(flow.iceOn)
        assertEquals(1, flow.front)
        assertEquals(0, flow.rear)
        assertEquals("Elétrico", flow.label)
    }

    @Test
    fun mappedHybridLightsIceOnlyWhenRpmSaysSo() {
        val off = PowerFlowMapper.resolve("16", iceOn = false, speedKmh = 80.0, packKw = 18.0, charging = false)
        assertEquals(PowerFlow.TONE_HYBRID, off.tone)
        assertFalse(off.iceOn)
        assertEquals(1, off.front)

        val on = PowerFlowMapper.resolve("16", iceOn = true, speedKmh = 80.0, packKw = 18.0, charging = false)
        assertTrue(on.iceOn)
        assertEquals("Híbrido", on.label)
    }

    @Test
    fun unmappedAwdWithPackPowerAndIceOffIsEvBothMotors() {
        val flow = PowerFlowMapper.resolve("0", iceOn = false, speedKmh = 50.0, packKw = 8.0, charging = false)
        assertEquals(PowerFlow.TONE_EV, flow.tone)
        assertFalse(flow.iceOn)
        assertEquals(1, flow.front)
        assertEquals(1, flow.rear)
        assertEquals("P2 + P4", flow.label)
    }

    @Test
    fun unmappedAwdWithPackPowerAndIceOnIsHybrid() {
        val flow = PowerFlowMapper.resolve("", iceOn = true, speedKmh = 50.0, packKw = 8.0, charging = false)
        assertEquals(PowerFlow.TONE_HYBRID, flow.tone)
        assertTrue(flow.iceOn)
        assertEquals(1, flow.front)
        assertEquals(1, flow.rear)
    }

    @Test
    fun parkedIdleDoesNotInferAwd() {
        val flow = PowerFlowMapper.resolve("0", iceOn = true, speedKmh = 0.0, packKw = 0.4, charging = false)
        assertEquals(PowerFlow.TONE_IDLE, flow.tone)
        assertTrue(flow.iceOn)
        assertEquals(0, flow.front)
        assertEquals("Parado", flow.label)
    }

    @Test
    fun chargingOverridesDriveStateAndIce() {
        val flow = PowerFlowMapper.resolve("16", iceOn = true, speedKmh = 0.0, packKw = -3.0, charging = true)
        assertEquals(PowerFlow.TONE_CHARGE, flow.tone)
        assertFalse(flow.iceOn)
        assertEquals(0, flow.front)
        assertEquals(0, flow.rear)
    }

    @Test
    fun dualMotorEvEnum() {
        val flow = PowerFlowMapper.resolve("40", iceOn = false, speedKmh = 60.0, packKw = 20.0, charging = false)
        assertEquals(PowerFlow.TONE_EV, flow.tone)
        assertEquals(1, flow.front)
        assertEquals(1, flow.rear)
        assertFalse(flow.iceOn)
    }

    @Test
    fun packRoundTrip() {
        val original = PowerFlow(PowerFlow.TONE_HYBRID, true, 1, -1, "P4, carga P2")
        val packed = original.pack()
        assertEquals("v1|hybrid|1|1|-1|P4, carga P2", packed)
        assertEquals(original, PowerFlow.unpack(packed))
        assertNull(PowerFlow.unpack("nope"))
        assertNull(PowerFlow.unpack(null))
    }
}

class PowerFlowTrackerTest {
    private fun cache(vararg pairs: Pair<String, String>): MutableMap<String, String> {
        return mutableMapOf(*pairs)
    }

    @Test
    fun rpmBelowIdleDoesNotLightIceOnEvDrive() {
        val tracker = PowerFlowTracker()
        val c = cache(
            CarConstants.CAR_EV_INFO_ENERGY_DRIVE_STATE.value to "14",
            CarConstants.CAR_BASIC_ENGINE_SPEED.value to "0",
            CarConstants.CAR_BASIC_VEHICLE_SPEED.value to "40",
            CarConstants.CAR_EV_INFO_POWER_BATTERY_VOLTAGE.value to "360",
            CarConstants.CAR_EV_INFO_CUR_CHARGE_CURRENT.value to "30",
        )
        val flow = tracker.ingest(CarConstants.CAR_BASIC_ENGINE_SPEED.value, c)
        assertNotNull(flow)
        assertFalse(flow!!.iceOn)
        assertEquals(PowerFlow.TONE_EV, flow.tone)
    }

    @Test
    fun rpmIdleLightsIce() {
        val tracker = PowerFlowTracker()
        val c = cache(
            CarConstants.CAR_EV_INFO_ENERGY_DRIVE_STATE.value to "16",
            CarConstants.CAR_BASIC_ENGINE_SPEED.value to "900",
            CarConstants.CAR_BASIC_VEHICLE_SPEED.value to "80",
        )
        val flow = tracker.ingest(CarConstants.CAR_BASIC_ENGINE_SPEED.value, c)
        assertNotNull(flow)
        assertTrue(flow!!.iceOn)
    }

    @Test
    fun rpmTicksDoNotRepublishSameMode() {
        val tracker = PowerFlowTracker()
        val c = cache(CarConstants.CAR_BASIC_ENGINE_SPEED.value to "900")
        assertNotNull(tracker.ingest(CarConstants.CAR_BASIC_ENGINE_SPEED.value, c))
        c[CarConstants.CAR_BASIC_ENGINE_SPEED.value] = "910"
        assertNull(tracker.ingest(CarConstants.CAR_BASIC_ENGINE_SPEED.value, c))
        c[CarConstants.CAR_BASIC_ENGINE_SPEED.value] = "0"
        val off = tracker.ingest(CarConstants.CAR_BASIC_ENGINE_SPEED.value, c)
        assertNotNull(off)
        assertFalse(off!!.iceOn)
    }

    @Test
    fun hysteresisHoldsThroughCrankDip() {
        assertFalse(PowerFlowTracker.rpmHysteresis(false, 0.0))
        assertFalse(PowerFlowTracker.rpmHysteresis(false, 300.0))
        assertTrue(PowerFlowTracker.rpmHysteresis(false, 400.0))
        assertTrue(PowerFlowTracker.rpmHysteresis(true, 250.0))
        assertFalse(PowerFlowTracker.rpmHysteresis(true, 100.0))
        assertTrue(PowerFlowTracker.rpmHysteresis(true, 65535.0))
    }

    @Test
    fun ignoresUnrelatedKeys() {
        val tracker = PowerFlowTracker()
        assertNull(tracker.ingest(CarConstants.CAR_BASIC_DOOR_STATUS.value, cache()))
        assertNull(tracker.ingest(PowerFlow.KEY_FLOW, cache()))
    }
}
