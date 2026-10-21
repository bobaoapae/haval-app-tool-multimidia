package br.com.redesurftank.havalshisuku.hud

import br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry.Directions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HudRadarTest {
    private val route = Directions(active = true, distance = "250 m", distanceM = 250, turn = "TURN_RIGHT")
    private val radar = HudOverlayModel.Radar(distanceM = 340, limitKmh = 60, over = false)
    private val navigation = HudOverlayModel.Config(layout = HudOverlayModel.Layout.NAVIGATION)

    @Test
    fun `radar takes the turn-by-turn box while alerted, bars stay`() {
        val frame = HudOverlayModel.frameFor(navigation, route, powerPercent = 30, rpmTenths = 20, radar = radar)
        assertNull(frame.tbt)
        assertEquals(radar, frame.radar)
        assertEquals(30, frame.powerPercent)
    }

    @Test
    fun `without a radar the turn-by-turn is back`() {
        val frame = HudOverlayModel.frameFor(navigation, route, powerPercent = 30, rpmTenths = 20)
        assertNotNull(frame.tbt)
        assertNull(frame.radar)
    }

    @Test
    fun `original layout draws only the radar`() {
        val original = HudOverlayModel.Config(layout = HudOverlayModel.Layout.ORIGINAL)
        val withRadar = HudOverlayModel.frameFor(original, route, powerPercent = 30, rpmTenths = 20, radar = radar)
        assertNull(withRadar.tbt)
        assertNull(withRadar.powerPercent)
        assertNull(withRadar.rpmTenths)
        assertEquals(radar, withRadar.radar)
        val without = HudOverlayModel.frameFor(original, route, powerPercent = 30, rpmTenths = 20)
        assertNull(without.tbt)
        assertNull(without.radar)
    }

    @Test
    fun `AR keeps the map while the radar card is up`() {
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR, mapStreets = true)
        val streets = listOf(br.com.redesurftank.havalshisuku.map.LocalRoad(floatArrayOf(0f, -20f, 0f, 200f), 1f))
        val frame = HudOverlayModel.frameFor(ar, route, powerPercent = 30, rpmTenths = 20, radar = radar, streets = streets)
        assertEquals(HudOverlayModel.TbtStyle.AR, frame.tbt!!.style)
        assertNotNull(frame.tbt!!.route)
        assertEquals(radar, frame.radar)
        assertEquals(30, frame.powerPercent)
    }

    @Test
    fun `HUD radar distance is 20 m closer, and never below zero`() {
        assertEquals(20, HudOverlayModel.RADAR_DISTANCE_LEAD_M)
        assertEquals(320, HudOverlayModel.radarShownMeters(340))
        assertEquals(0, HudOverlayModel.radarShownMeters(15))
        assertEquals(0, HudOverlayModel.radarShownMeters(0))
    }

    @Test
    fun `radar distance reads in 10 m steps, then km`() {
        assertEquals("340" to "m", HudOverlayModel.radarDistance(338))
        assertEquals("0" to "m", HudOverlayModel.radarDistance(-5))
        assertEquals("1.0" to "km", HudOverlayModel.radarDistance(1_000))
        assertEquals("1.3" to "km", HudOverlayModel.radarDistance(1_260))
    }

    @Test
    fun `radar card sits centred in the turn-by-turn area`() {
        val bottom = HudOverlayModel.RADAR_TOP + HudOverlayModel.RADAR_SIGN + HudOverlayModel.RADAR_TEXT_GAP
        // Same gap above the sign as below the distance baseline.
        assertEquals(HudOverlayModel.RADAR_TOP - HudOverlayModel.TBT_TOP, HudOverlayModel.TBT_BOTTOM - bottom, 0.01f)
    }
}
