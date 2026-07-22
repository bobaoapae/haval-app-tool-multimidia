package br.com.redesurftank.havalshisuku.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteeringWheelCustomActionTypeTest {
    @Test
    fun dashboardToggleActionIsAvailableByStableKey() {
        assertEquals(
                SteeringWheelCustomActionType.TOGGLE_IMPULSE_DASHBOARD,
                SteeringWheelCustomActionType.fromKey("toggle_impulse_dashboard")
        )
    }

    @Test
    fun dashboardToggleActionIsListedForSettingsDropdown() {
        assertTrue(
                SteeringWheelCustomActionType.entries.contains(
                        SteeringWheelCustomActionType.TOGGLE_IMPULSE_DASHBOARD
                )
        )
    }

    @Test
    fun climateCommandActionIsAvailableByStableKey() {
        assertEquals(
                SteeringWheelCustomActionType.CLIMATE_COMMAND,
                SteeringWheelCustomActionType.fromKey("climate_command")
        )
    }

    @Test
    fun climateCommandActionIsListedForSettingsDropdown() {
        assertTrue(
                SteeringWheelCustomActionType.entries.contains(
                        SteeringWheelCustomActionType.CLIMATE_COMMAND
                )
        )
    }
}
