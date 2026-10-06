package br.com.redesurftank.havalshisuku.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteeringWheelClimateCommandTypeTest {
    @Test
    fun climateCommandsAreAvailableByStableKeys() {
        assertEquals(SteeringWheelClimateCommandType.TOGGLE_AC, SteeringWheelClimateCommandType.fromKey("toggle_ac"))
        assertEquals(SteeringWheelClimateCommandType.TOGGLE_AUTO, SteeringWheelClimateCommandType.fromKey("toggle_auto"))
        assertEquals(SteeringWheelClimateCommandType.TOGGLE_POWER, SteeringWheelClimateCommandType.fromKey("toggle_power"))
        assertEquals(SteeringWheelClimateCommandType.FRONT_DEFROST, SteeringWheelClimateCommandType.fromKey("front_defrost"))
    }

    @Test
    fun unknownClimateCommandKeyReturnsNull() {
        assertNull(SteeringWheelClimateCommandType.fromKey("unknown"))
    }

    @Test
    fun allRequestedClimateCommandsAreListedForSettingsDropdown() {
        assertTrue(SteeringWheelClimateCommandType.entries.contains(SteeringWheelClimateCommandType.TOGGLE_AC))
        assertTrue(SteeringWheelClimateCommandType.entries.contains(SteeringWheelClimateCommandType.TOGGLE_AUTO))
        assertTrue(SteeringWheelClimateCommandType.entries.contains(SteeringWheelClimateCommandType.TOGGLE_POWER))
        assertTrue(SteeringWheelClimateCommandType.entries.contains(SteeringWheelClimateCommandType.FRONT_DEFROST))
    }
}
