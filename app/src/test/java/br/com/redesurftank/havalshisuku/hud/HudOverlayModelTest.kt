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
    fun `layout stays inside the free HUD cells`() {
        // Free cells A1..C4 = x 0..180, y 40..200 (measured on the car).
        val m = HudOverlayModel
        assertTrue(m.BOX_LEFT > 0f && m.BOX_RIGHT < 180f)
        assertTrue(m.TBT_TOP > 40f)
        // Compact: row, then lanes, all above the bar labels.
        assertTrue(m.COMPACT_TOP >= m.TBT_TOP)
        assertTrue(m.COMPACT_TOP + m.COMPACT_GLYPH < m.LANES_TOP)
        assertTrue(m.LANES_TOP + m.LANE_ROW_HEIGHT <= m.TBT_BOTTOM)
        // Hero: big arrow and its text above the bar labels.
        assertTrue(m.HERO_TOP >= m.TBT_TOP)
        assertTrue(m.HERO_TOP + m.HERO_GLYPH + m.HERO_TEXT_GAP <= m.TBT_BOTTOM)
        assertTrue(m.HERO_GLYPH > m.COMPACT_GLYPH)
        // Bars: labels clear of the turn-by-turn, dots inside the visible band.
        assertTrue(m.TBT_BOTTOM < m.BAR_LABEL_BASELINE - m.BAR_LABEL_SIZE)
        assertTrue(m.BAR_LABEL_BASELINE < m.BAR_Y - m.POWER_DOT_RADIUS)
        assertTrue(m.BAR_Y + m.RPM_SEGMENT_HEIGHT < 200f)
        // Power at the left edge, inside the free cells; RPM at the right edge of the panel.
        val mid = (m.POWER_LEFT + m.POWER_RIGHT) / 2
        assertTrue(mid - m.powerDotOffset(m.POWER_DOTS_PER_SIDE - 1) - m.POWER_DOT_RADIUS >= m.POWER_LEFT - 0.01f)
        assertTrue(mid + m.powerDotOffset(m.POWER_DOTS_PER_SIDE - 1) + m.POWER_DOT_RADIUS <= m.POWER_RIGHT + 0.01f)
        assertTrue(m.powerDotOffset(0) - m.POWER_DOT_RADIUS > 0f)
        assertTrue(m.POWER_RIGHT < 180f)
        assertTrue(m.RPM_LEFT > 180f && m.RPM_RIGHT < 480f)
    }

    @Test
    fun `power percent and one dot per 10 percent, nothing lit at zero`() {
        assertEquals(0, HudOverlayModel.powerPercent(0.2))
        assertEquals(0, HudOverlayModel.powerPercent(-0.2))
        assertEquals(0, HudOverlayModel.powerPercent(Double.NaN))
        assertEquals(35, HudOverlayModel.powerPercent(HudOverlayModel.DRIVE_MAX_KW * 0.35))
        assertEquals(-56, HudOverlayModel.powerPercent(-HudOverlayModel.REGEN_MAX_KW * 0.56))
        assertEquals(100, HudOverlayModel.powerPercent(1_000.0))
        assertEquals(-100, HudOverlayModel.powerPercent(-1_000.0))

        assertEquals(0, HudOverlayModel.powerDots(0))
        assertEquals(1, HudOverlayModel.powerDots(1))
        assertEquals(-1, HudOverlayModel.powerDots(-4))
        assertEquals(4, HudOverlayModel.powerDots(35))
        assertEquals(-6, HudOverlayModel.powerDots(-56))
        assertEquals(10, HudOverlayModel.powerDots(100))
    }

    @Test
    fun `rpm shows only while the engine runs, one segment per 1000 rpm`() {
        assertNull(HudOverlayModel.rpmTenths(1_500.0, engineOn = false))
        assertNull(HudOverlayModel.rpmTenths(65_535.0, engineOn = true))
        assertNull(HudOverlayModel.rpmTenths(Double.NaN, engineOn = true))
        assertEquals(32, HudOverlayModel.rpmTenths(3_249.0, engineOn = true))
        assertEquals("3.2 kRPM", HudOverlayModel.rpmLabel(32))
        assertEquals("0.8 kRPM", HudOverlayModel.rpmLabel(8))

        assertEquals(0, HudOverlayModel.rpmSegments(0))
        assertEquals(1, HudOverlayModel.rpmSegments(8))   // idle still lights one
        assertEquals(1, HudOverlayModel.rpmSegments(11))
        assertEquals(3, HudOverlayModel.rpmSegments(32))
        assertEquals(6, HudOverlayModel.rpmSegments(56))
        assertEquals(6, HudOverlayModel.rpmSegments(75))
        assertTrue(HudOverlayModel.rpmSegmentIsRed(5))
        assertTrue(!HudOverlayModel.rpmSegmentIsRed(4))
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
    fun `inactive guidance has no turn-by-turn`() {
        assertNull(HudOverlayModel.tbtFor(Directions(active = false)))
        val tbt = HudOverlayModel.tbtFor(Directions(active = true, distance = "90 m", turn = "TURN_LEFT"))!!
        assertEquals("TURN_LEFT", tbt.icon)
        assertEquals("90", tbt.distanceValue)
        assertEquals("m", tbt.distanceUnit)
        assertTrue(tbt.lanes.isEmpty())
    }
}
