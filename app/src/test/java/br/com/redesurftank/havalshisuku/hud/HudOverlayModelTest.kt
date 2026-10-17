package br.com.redesurftank.havalshisuku.hud

import br.com.redesurftank.havalshisuku.hud.HudOverlayModel.LaneArrow
import br.com.redesurftank.havalshisuku.hud.HudOverlayModel.LaneShape
import br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry.Directions
import br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry.LaneDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HudOverlayModelTest {
    @Test
    fun `turn tokens fold like the cluster card`() {
        assertEquals("TURN_RIGHT", HudOverlayModel.resolveTurnIcon("turn_right"))
        assertEquals("TURN_LEFT", HudOverlayModel.resolveTurnIcon("SHARP_LEFT"))
        assertEquals("TURN_RIGHT", HudOverlayModel.resolveTurnIcon("SLIGHT_RIGHT"))
        assertEquals("TURN_LEFT", HudOverlayModel.resolveTurnIcon("KEEP_LEFT"))
        assertEquals("STRAIGHT", HudOverlayModel.resolveTurnIcon("NAME_CHANGE"))
        assertEquals("STRAIGHT", HudOverlayModel.resolveTurnIcon("DEPART"))
        assertEquals("STRAIGHT", HudOverlayModel.resolveTurnIcon("FERRY_BOAT"))
        assertEquals("U_TURN", HudOverlayModel.resolveTurnIcon("U_TURN_LEFT"))
        assertEquals("ROUNDABOUT", HudOverlayModel.resolveTurnIcon("ROUNDABOUT_EXIT"))
        assertEquals("DESTINATION", HudOverlayModel.resolveTurnIcon("DESTINATION_RIGHT"))
        assertEquals("FORK", HudOverlayModel.resolveTurnIcon("FORK_LEFT"))
        assertEquals("MERGE", HudOverlayModel.resolveTurnIcon("MERGE_RIGHT"))
        assertEquals("MERGE", HudOverlayModel.resolveTurnIcon("ON_RAMP_LEFT"))
        assertEquals("EXIT", HudOverlayModel.resolveTurnIcon("OFF_RAMP_RIGHT"))
        assertEquals("", HudOverlayModel.resolveTurnIcon(null))
    }

    @Test
    fun `lane shapes map and mirror like tbtLanes`() {
        assertEquals(LaneArrow(LaneShape.STRAIGHT, false, true), HudOverlayModel.laneArrow(1, true))
        assertEquals(LaneArrow(LaneShape.NORMAL, true, false), HudOverlayModel.laneArrow(4, false))
        assertEquals(LaneArrow(LaneShape.NORMAL, false, false), HudOverlayModel.laneArrow(5, false))
        assertEquals(LaneArrow(LaneShape.UTURN, false, false), HudOverlayModel.laneArrow(9, false))
        assertNull(HudOverlayModel.laneArrow(0, true))
    }

    @Test
    fun `route arrow is drawn last and empty lanes keep their slot`() {
        val arrows = HudOverlayModel.laneArrows(listOf(LaneDirection(5, true), LaneDirection(1, false)))
        assertEquals(listOf(LaneShape.STRAIGHT, LaneShape.NORMAL), arrows.map { it.shape })
        assertTrue(arrows.last().highlighted)

        val unknown = HudOverlayModel.laneArrows(listOf(LaneDirection(0, false)))
        assertEquals(listOf(LaneArrow(LaneShape.STRAIGHT, false, false)), unknown)
    }

    @Test
    fun `lane cells shrink to keep every lane inside the free box`() {
        val width = HudOverlayModel.BOX_RIGHT - HudOverlayModel.BOX_LEFT
        assertEquals(HudOverlayModel.LANE_CELL_MAX, HudOverlayModel.laneCellWidth(3), 0f)
        for (n in 1..10) assertTrue(HudOverlayModel.laneCellWidth(n) * n <= width + 0.01f)
        assertEquals(0f, HudOverlayModel.laneCellWidth(0), 0f)
    }

    @Test
    fun `distance splits into value and unit`() {
        assertEquals("250" to "m", HudOverlayModel.splitDistance("250 m", 250))
        assertEquals("1.2" to "km", HudOverlayModel.splitDistance("1.2 km", 1200))
        assertEquals("80" to "m", HudOverlayModel.splitDistance("", 80))
        assertEquals("" to "", HudOverlayModel.splitDistance("", null))
    }

    @Test
    fun `top task of D0 comes from the first D0 stack`() {
        // Trimmed from `am stack list` on the car: D0 stacks are listed top first.
        val list = """
            Stack id=16 bounds=[0,0][480,240] displayId=4096 userId=0
              taskId=24384: br.com.redesurftank.havalshisuku/br.com.redesurftank.havalshisuku.hud.HudOverlayActivity bounds=[0,0][480,240] userId=0 visible=true
            Stack id=8 bounds=[0,0][1920,660] displayId=0 userId=0
             configuration={1.0 ?mcc?mnc [pt_BR] ldltr sw720dp}
              taskId=24375: com.ts.androidauto.app/com.ts.androidauto.app.display.AapActivity bounds=[0,0][1920,660] userId=0 visible=true
            Stack id=7 bounds=[0,0][1920,720] displayId=0 userId=0
              taskId=24374: app.revanced.android.youtube/app.revanced.android.youtube.revanced_rounded_1 bounds=[0,0][1920,720] userId=0 visible=false
        """.trimIndent()
        assertEquals(24375, HudOverlayModel.topTaskOnDisplay(list, 0))
        assertEquals(24384, HudOverlayModel.topTaskOnDisplay(list, 4096))
        assertEquals(-1, HudOverlayModel.topTaskOnDisplay(list, 3))
    }

    @Test
    fun `layout stays inside the free HUD cells`() {
        // Free cells A1..C4 = x 0..180, y 40..200 (measured on the car).
        val m = HudOverlayModel
        assertTrue(m.BOX_LEFT > 0f && m.BOX_RIGHT < 180f)
        assertTrue(m.TBT_TOP > 40f)
        // Compact / far rows and the lanes inside the turn-by-turn area.
        assertTrue(m.COMPACT_TOP >= m.TBT_TOP)
        assertTrue(m.COMPACT_TOP + m.COMPACT_GLYPH < m.LANES_TOP)
        assertTrue(m.LANES_TOP + m.LANE_ROW_HEIGHT <= m.TBT_BOTTOM)
        assertTrue(m.FAR_TOP >= m.TBT_TOP && m.FAR_TOP + m.COMPACT_GLYPH <= m.TBT_BOTTOM)
        // Hero: big arrow and its text.
        assertTrue(m.HERO_TOP >= m.TBT_TOP)
        assertTrue(m.HERO_TOP + m.HERO_GLYPH + m.HERO_TEXT_GAP <= m.TBT_BOTTOM)
        assertTrue(m.HERO_GLYPH > m.COMPACT_GLYPH)
        // ETA between the turn-by-turn and the bars; bars inside the visible band.
        assertTrue(m.ETA_BASELINE - m.ETA_SIZE >= m.TBT_BOTTOM)
        assertTrue(m.ETA_BASELINE < m.BAR_Y - m.BAR_HEIGHT / 2)
        assertTrue(m.BAR_Y + m.BAR_HEIGHT / 2 < 200f)
        // Power at the left edge inside the free cells; RPM at the right edge of the panel.
        val mid = (m.POWER_LEFT + m.POWER_RIGHT) / 2
        val last = m.powerSegmentOffset(m.POWER_SEGMENTS_PER_SIDE - 1) + m.POWER_SEGMENT_WIDTH
        assertEquals(m.POWER_RIGHT, mid + last, 0.01f)
        assertTrue(m.POWER_SEGMENT_WIDTH > 2f)
        assertTrue(m.POWER_RIGHT < 180f)
        assertTrue(m.RPM_LEFT > 180f && m.RPM_RIGHT < 480f)
    }

    @Test
    fun `power percent and one segment per 10 percent, nothing lit at zero`() {
        assertEquals(0, HudOverlayModel.powerPercent(0.2))
        assertEquals(0, HudOverlayModel.powerPercent(-0.2))
        assertEquals(0, HudOverlayModel.powerPercent(Double.NaN))
        assertEquals(35, HudOverlayModel.powerPercent(HudOverlayModel.DRIVE_MAX_KW * 0.35))
        assertEquals(-56, HudOverlayModel.powerPercent(-HudOverlayModel.REGEN_MAX_KW * 0.56))
        assertEquals(100, HudOverlayModel.powerPercent(1_000.0))
        assertEquals(-100, HudOverlayModel.powerPercent(-1_000.0))

        assertEquals(0, HudOverlayModel.powerSegments(0))
        assertEquals(1, HudOverlayModel.powerSegments(1))
        assertEquals(-1, HudOverlayModel.powerSegments(-4))
        assertEquals(4, HudOverlayModel.powerSegments(35))
        assertEquals(-6, HudOverlayModel.powerSegments(-56))
        assertEquals(10, HudOverlayModel.powerSegments(100))
    }

    @Test
    fun `rpm shows only while the engine runs, one segment per 1000 rpm`() {
        assertNull(HudOverlayModel.rpmTenths(1_500.0, engineOn = false))
        assertNull(HudOverlayModel.rpmTenths(65_535.0, engineOn = true))
        assertNull(HudOverlayModel.rpmTenths(Double.NaN, engineOn = true))
        assertEquals(32, HudOverlayModel.rpmTenths(3_249.0, engineOn = true))

        assertEquals(0, HudOverlayModel.rpmSegments(0))
        assertEquals(1, HudOverlayModel.rpmSegments(8))   // idle still lights one
        assertEquals(3, HudOverlayModel.rpmSegments(32))
        assertEquals(6, HudOverlayModel.rpmSegments(56))
        assertEquals(6, HudOverlayModel.rpmSegments(75))
        assertTrue(HudOverlayModel.rpmSegmentIsRed(5))
        assertTrue(!HudOverlayModel.rpmSegmentIsRed(4))
    }

    @Test
    fun `eta joins arrival clock and time left like the cluster card`() {
        assertEquals("10:40 · 12 min", HudOverlayModel.etaLabel("10:40", 720))
        assertEquals("09:05", HudOverlayModel.etaLabel("9:05", null))
        assertEquals("<1 min", HudOverlayModel.etaLabel("", 30))
        assertEquals("1 h 34 min", HudOverlayModel.etaLabel("bad", 94 * 60))
        assertEquals("2 h", HudOverlayModel.etaLabel("", 120 * 60))
        assertEquals("", HudOverlayModel.etaLabel("25:00", null))
    }

    @Test
    fun `distance in metres falls back to the display text`() {
        assertEquals(250, HudOverlayModel.distanceMeters("250 m", 250))
        assertEquals(1_200, HudOverlayModel.distanceMeters("1.2 km", null))
        assertEquals(1_500, HudOverlayModel.distanceMeters("1,5 km", null))
        assertEquals(90, HudOverlayModel.distanceMeters("90 m", null))
        assertNull(HudOverlayModel.distanceMeters("", null))
    }

    @Test
    fun `turn style - lanes compact, far small only when enabled, else hero`() {
        val near = Directions(active = true, distance = "250 m", distanceM = 250, turn = "TURN_LEFT")
        val far = near.copy(distance = "1.8 km", distanceM = 1_800)
        val lanes = near.copy(lanes = listOf(listOf(LaneDirection(1, true))))
        val on = HudOverlayModel.Config(layout = HudOverlayModel.Layout.NAVIGATION)
        val off = on.copy(smallArrowWhenFar = false)

        assertEquals(HudOverlayModel.TbtStyle.HERO, HudOverlayModel.tbtFor(near, on)!!.style)
        assertEquals(HudOverlayModel.TbtStyle.FAR, HudOverlayModel.tbtFor(far, on)!!.style)
        assertEquals(HudOverlayModel.TbtStyle.HERO, HudOverlayModel.tbtFor(far, off)!!.style)
        assertEquals(HudOverlayModel.TbtStyle.COMPACT, HudOverlayModel.tbtFor(lanes, on)!!.style)
        // Exactly 1 km is still "far"; 999 m gets the big arrow.
        assertEquals(HudOverlayModel.TbtStyle.FAR, HudOverlayModel.tbtFor(near.copy(distanceM = 1_000), on)!!.style)
        assertEquals(HudOverlayModel.TbtStyle.HERO, HudOverlayModel.tbtFor(near.copy(distanceM = 999), on)!!.style)
    }

    @Test
    fun `config switches each part off, everything on by default`() {
        val defaults = HudOverlayModel.Config()
        assertEquals(HudOverlayModel.Layout.ORIGINAL, defaults.layout)
        assertTrue(!defaults.active)
        assertTrue(defaults.navigation && defaults.smallArrowWhenFar && defaults.eta && defaults.powerBar && defaults.rpmBar)

        val route = Directions(active = true, distance = "90 m", turn = "TURN_LEFT", eta = "10:40", remainingS = 720)
        val all = HudOverlayModel.Config(layout = HudOverlayModel.Layout.NAVIGATION)
        val full = HudOverlayModel.frameFor(all, route, powerPercent = 35, rpmTenths = 24)
        assertEquals("TURN_LEFT", full.tbt!!.icon)
        assertEquals("90", full.tbt!!.distanceValue)
        assertEquals("m", full.tbt!!.distanceUnit)
        assertEquals("10:40 · 12 min", full.tbt!!.eta)
        assertEquals(35, full.powerPercent)
        assertEquals(24, full.rpmTenths)

        assertNull(HudOverlayModel.frameFor(all.copy(navigation = false), route, 35, 24).tbt)
        assertEquals("", HudOverlayModel.frameFor(all.copy(eta = false), route, 35, 24).tbt!!.eta)
        assertNull(HudOverlayModel.frameFor(all.copy(powerBar = false), route, 35, 24).powerPercent)
        assertNull(HudOverlayModel.frameFor(all.copy(rpmBar = false), route, 35, 24).rpmTenths)
        assertNull(HudOverlayModel.frameFor(all, Directions(active = false), 0, null).tbt)
        assertNull(HudOverlayModel.frameFor(all, null, 0, null).tbt)
    }

    @Test
    fun `layout keys round-trip`() {
        for (layout in HudOverlayModel.Layout.entries) assertEquals(layout, HudOverlayModel.Layout.fromKey(layout.key))
        assertNull(HudOverlayModel.Layout.fromKey("nope"))
    }
}
