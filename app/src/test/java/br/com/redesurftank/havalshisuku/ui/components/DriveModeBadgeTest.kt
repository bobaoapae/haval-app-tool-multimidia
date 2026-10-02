package br.com.redesurftank.havalshisuku.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveModeBadgeTest {

    @Test
    fun normalAndUnknownValues_haveNoBadge() {
        assertNull(resolveDriveModeBadge("0"))
        assertNull(resolveDriveModeBadge(null))
        assertNull(resolveDriveModeBadge(""))
        assertNull(resolveDriveModeBadge("9"))
    }

    @Test
    fun driveModeValues_mapToTheSettingsMenuModes() {
        assertEquals(DriveModeBadge.SPORT, resolveDriveModeBadge("1"))
        assertEquals(DriveModeBadge.ECO, resolveDriveModeBadge("2"))
        assertEquals(DriveModeBadge.SNOW, resolveDriveModeBadge("3"))
        assertEquals(DriveModeBadge.SAND, resolveDriveModeBadge("4"))
        assertEquals(DriveModeBadge.MUD, resolveDriveModeBadge("5"))
    }

    @Test
    fun description_namesTheActiveModeOrJustTheButton() {
        assertEquals("Modos do veículo", driveModeDescription("0"))
        assertEquals("Modos do veículo: Sport", driveModeDescription("1"))
        assertEquals("Modos do veículo: Neve", driveModeDescription("3"))
    }
}
