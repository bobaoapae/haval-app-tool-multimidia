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
        // ETA between the turn-by-turn and the bars (pulled up into the area's empty bottom: its
        // cap height clears the lane row; see `nav text stays between...`); bars in the band.
        assertTrue(m.TRIP_BASELINE - m.TRIP_SIZE * 0.75f > m.LANES_TOP + m.LANE_ROW_HEIGHT)
        assertTrue(m.TRIP_BASELINE < m.BAR_Y - m.BAR_HEIGHT / 2)
        assertTrue(m.BAR_Y + m.BAR_HEIGHT / 2 < 200f)
        // Power at the left edge inside the free cells; RPM at the right edge of the panel.
        val mid = (m.POWER_LEFT + m.POWER_RIGHT) / 2
        val last = m.powerSegmentOffset(m.POWER_SEGMENTS_PER_SIDE - 1) + m.POWER_SEGMENT_WIDTH
        assertEquals(m.POWER_RIGHT, mid + last, 0.01f)
        assertTrue(m.POWER_SEGMENT_WIDTH > 2f)
        assertTrue(m.POWER_RIGHT < 180f && m.POWER_LEFT > 0f)
        // Lined up with the turn-by-turn block above it.
        assertEquals(m.BOX_CENTER_X, mid, 0.01f)
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
    fun `trip joins remaining distance and time like the cluster card`() {
        assertEquals("4.8 km · 12 min", HudOverlayModel.tripLabel(4_769, 720))
        assertEquals("850 m", HudOverlayModel.tripLabel(850, null))
        assertEquals("1.0 km · <1 min", HudOverlayModel.tripLabel(1_000, 30))
        assertEquals("123 km · 1 h 34 min", HudOverlayModel.tripLabel(123_400, 94 * 60))
        assertEquals("1.234 km · 2 h", HudOverlayModel.tripLabel(1_234_000, 120 * 60))
        assertEquals("2 h", HudOverlayModel.tripLabel(null, 120 * 60))
        assertEquals("", HudOverlayModel.tripLabel(null, null))
        assertEquals("", HudOverlayModel.tripLabel(-1, -5))
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
        assertTrue(defaults.navigation && defaults.smallArrowWhenFar && defaults.trip && defaults.powerBar && defaults.rpmBar)

        val route = Directions(active = true, distance = "90 m", turn = "TURN_LEFT", remainingM = 4_800, remainingS = 720)
        val all = HudOverlayModel.Config(layout = HudOverlayModel.Layout.NAVIGATION)
        val full = HudOverlayModel.frameFor(all, route, powerPercent = 35, rpmTenths = 24)
        assertEquals("TURN_LEFT", full.tbt!!.icon)
        assertEquals("90", full.tbt!!.distanceValue)
        assertEquals("m", full.tbt!!.distanceUnit)
        assertEquals("4.8 km · 12 min", full.tbt!!.trip)
        assertEquals(35, full.powerPercent)
        assertEquals(24, full.rpmTenths)

        assertNull(HudOverlayModel.frameFor(all.copy(navigation = false), route, 35, 24).tbt)
        assertEquals("", HudOverlayModel.frameFor(all.copy(trip = false), route, 35, 24).tbt!!.trip)
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

    @Test
    fun `roundabout exit rides the tbt only with the roundabout icon`() {
        val roundabout = Directions(active = true, distance = "80 m", distanceM = 80, turn = "ROUNDABOUT_ENTER", exitNumber = 2)
        assertEquals(2, HudOverlayModel.tbtFor(roundabout)!!.exitNumber)
        assertNull(HudOverlayModel.tbtFor(roundabout.copy(exitNumber = null))!!.exitNumber)
        assertNull(HudOverlayModel.tbtFor(roundabout.copy(exitNumber = 0))!!.exitNumber)
        // A stale number on a non-roundabout step must not reach the HUD.
        assertNull(HudOverlayModel.tbtFor(roundabout.copy(turn = "TURN_RIGHT"))!!.exitNumber)
    }

    @Test
    fun `exit label and two-digit size`() {
        assertEquals("2ª", HudOverlayModel.exitLabel(2))
        assertEquals("12ª", HudOverlayModel.exitLabel(12))
        assertEquals(HudOverlayModel.HERO_EXIT_SIZE, HudOverlayModel.heroExitSize(3))
        assertEquals(HudOverlayModel.HERO_EXIT_SIZE_2DIGITS, HudOverlayModel.heroExitSize(10))
    }

    @Test
    fun `hero exit number sits in the ring hole, inside the free box`() {
        val left = HudOverlayModel.BOX_CENTER_X - HudOverlayModel.HERO_GLYPH / 2
        val (cx, cy) = HudOverlayModel.ringHoleCenter(left, HudOverlayModel.HERO_TOP, HudOverlayModel.HERO_GLYPH)
        val r = HudOverlayModel.RING_HOLE_R * HudOverlayModel.HERO_GLYPH
        assertTrue(cx - r >= HudOverlayModel.BOX_LEFT && cx + r <= HudOverlayModel.BOX_RIGHT)
        assertTrue(cy - r >= HudOverlayModel.TBT_TOP && cy + r <= HudOverlayModel.TBT_BOTTOM)
        // An 18 px digit is ~13 px tall: it has to clear the ~21 px hole.
        assertTrue(2 * r >= HudOverlayModel.HERO_EXIT_SIZE)
    }

    @Test
    fun `roundabout exit angle travels the ring in the traffic direction`() {
        // Right-hand traffic (CCW): enter at the bottom, 90 = right, 180 = straight, 270 = left.
        assertEquals(0f, HudOverlayModel.roundaboutExitDegrees(90, clockwise = false))
        assertEquals(90f, HudOverlayModel.roundaboutExitDegrees(180, clockwise = false))
        assertEquals(180f, HudOverlayModel.roundaboutExitDegrees(270, clockwise = false))
        assertEquals(270f, HudOverlayModel.roundaboutExitDegrees(360, clockwise = false))
        // Left-hand traffic (CW): 90 = left.
        assertEquals(180f, HudOverlayModel.roundaboutExitDegrees(90, clockwise = true))
        assertEquals(90f, HudOverlayModel.roundaboutExitDegrees(180, clockwise = true))
    }

    @Test
    fun `on the ring the exit arc shrinks to the right and the number counts down past 1`() {
        assertEquals(180, HudOverlayModel.roundaboutShownAngle(180, 0f))
        assertTrue(HudOverlayModel.roundaboutShownAngle(270, 0.85f) <= 80)
        assertEquals(HudOverlayModel.RB_EXIT_ARC.toInt(), HudOverlayModel.roundaboutShownAngle(270, 1f))
        assertEquals(3, HudOverlayModel.roundaboutExitCountdown(3, 0f))
        assertEquals(2, HudOverlayModel.roundaboutExitCountdown(3, 0.4f))
        assertNull(HudOverlayModel.roundaboutExitCountdown(3, 0.8f))
        assertNull(HudOverlayModel.roundaboutExitCountdown(1, 0f))
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        val rb = Directions(active = true, distance = "40 m", distanceM = 40, turn = "ROUNDABOUT", exitNumber = 3, turnAngle = 270, roundaboutClockwise = false)
        val onRing = HudOverlayModel.frameFor(ar, rb, 0, null, arMetersOverride = 0f, arProgress = 0.5f)
        assertEquals(1, onRing.chevrons!!.side)
        // The ring is drawn here, but the chevrons still follow the 40 m left to the exit.
        assertEquals(0.2f, onRing.chevrons!!.intensity, 0.05f)
        assertTrue(onRing.tbt!!.rbProgress > 0f)
        assertEquals(0f, onRing.tbt!!.arMeters)
    }

    @Test
    fun `dynamic roundabout only with a drawable angle`() {
        val d = Directions(active = true, distance = "80 m", turn = "ROUNDABOUT", exitNumber = 2, turnAngle = 180)
        val tbt = HudOverlayModel.tbtFor(d)!!
        assertTrue(tbt.dynamicRoundabout)
        assertEquals(180, tbt.exitAngle)
        assertTrue(!HudOverlayModel.tbtFor(d.copy(turnAngle = null))!!.dynamicRoundabout)
        assertNull(HudOverlayModel.tbtFor(d.copy(turnAngle = -90))!!.exitAngle)
        assertNull(HudOverlayModel.tbtFor(d.copy(turn = "TURN_LEFT"))!!.exitAngle)
        assertEquals(true, HudOverlayModel.tbtFor(d.copy(roundaboutClockwise = true))!!.roundaboutClockwise)
    }

    @Test
    fun `dynamic roundabout stays inside its icon box`() {
        // Arrow tip and the entry stub (plus half the stroke) inside the 24 box.
        assertTrue(HudOverlayModel.RB_CY - HudOverlayModel.RB_EXIT_TIP >= 0f)
        assertTrue(HudOverlayModel.RB_CX + HudOverlayModel.RB_EXIT_TIP <= 24f)
        assertTrue(HudOverlayModel.RB_ENTRY_BOTTOM + HudOverlayModel.RB_STROKE / 2 <= 24f)
        // The exit number has room inside the ring at hero size.
        val inner = 2 * (HudOverlayModel.RB_R - HudOverlayModel.RB_STROKE / 2) / 24f * HudOverlayModel.HERO_GLYPH
        assertTrue(inner >= HudOverlayModel.HERO_EXIT_SIZE)
    }

    @Test
    fun `passed exits are spread between the entry and our exit`() {
        assertEquals(emptyList<Float>(), HudOverlayModel.passedExitAngles(90, 1))
        assertEquals(emptyList<Float>(), HudOverlayModel.passedExitAngles(180, null))
        assertEquals(listOf(90f), HudOverlayModel.passedExitAngles(180, 2))
        assertEquals(listOf(90f, 180f), HudOverlayModel.passedExitAngles(270, 3))
        // Too crowded to read: none.
        assertEquals(emptyList<Float>(), HudOverlayModel.passedExitAngles(90, 5))
        // Stubs stay inside the ring's own box.
        assertTrue(HudOverlayModel.RB_STUB_END < HudOverlayModel.RB_EXIT_TIP)
    }

    @Test
    fun `far row shows the street and a larger trip line, the others neither`() {
        val far = HudOverlayModel.tbtFor(Directions(active = true, distance = "2.4 km", distanceM = 2_400, turn = "TURN_RIGHT", street = "Av. Brasil"))!!
        assertEquals(HudOverlayModel.TbtStyle.FAR, far.style)
        assertTrue(HudOverlayModel.showsStreet(far))
        assertTrue(!HudOverlayModel.showsStreet(far.copy(street = "")))
        assertTrue(!HudOverlayModel.showsStreet(far.copy(style = HudOverlayModel.TbtStyle.HERO)))
        assertTrue(!HudOverlayModel.showsStreet(far.copy(style = HudOverlayModel.TbtStyle.COMPACT)))
        val (farBase, farSize) = HudOverlayModel.tripPlacement(HudOverlayModel.TbtStyle.FAR, hasStreet = true)
        val (heroBase, heroSize) = HudOverlayModel.tripPlacement(HudOverlayModel.TbtStyle.HERO, hasStreet = false)
        // Far: bigger and higher than with the big arrow; big arrow: bigger than the old 13 px.
        assertTrue(farSize > heroSize && heroSize > 13f)
        assertTrue(farBase < heroBase)
        assertTrue(HudOverlayModel.tripPlacement(HudOverlayModel.TbtStyle.FAR, hasStreet = false).first < farBase)
    }

    @Test
    fun `nav text stays between the arrow row and the bars`() {
        val rowBottom = HudOverlayModel.FAR_TOP + HudOverlayModel.COMPACT_GLYPH
        // Street cap height (~0.75 em) clears the row; trip clears the street.
        assertTrue(HudOverlayModel.FAR_STREET_BASELINE - HudOverlayModel.FAR_STREET_SIZE * 0.75f > rowBottom)
        assertTrue(HudOverlayModel.FAR_TRIP_BASELINE - HudOverlayModel.FAR_TRIP_SIZE * 0.75f > HudOverlayModel.FAR_STREET_BASELINE)
        // Descenders (~0.25 em) stay above the bar row.
        val barTop = HudOverlayModel.BAR_Y - HudOverlayModel.BAR_HEIGHT / 2
        assertTrue(HudOverlayModel.FAR_TRIP_BASELINE + HudOverlayModel.FAR_TRIP_SIZE * 0.25f < barTop)
        assertTrue(HudOverlayModel.TRIP_BASELINE + HudOverlayModel.TRIP_SIZE * 0.25f < barTop)
        // The trip line clears the lane row and the big arrow's distance.
        val lanesBottom = HudOverlayModel.LANES_TOP + HudOverlayModel.LANE_ROW_HEIGHT
        assertTrue(HudOverlayModel.TRIP_BASELINE - HudOverlayModel.TRIP_SIZE * 0.75f > lanesBottom)
        val heroDistance = HudOverlayModel.HERO_TOP + HudOverlayModel.HERO_GLYPH + HudOverlayModel.HERO_TEXT_GAP
        assertTrue(HudOverlayModel.TRIP_BASELINE - HudOverlayModel.TRIP_SIZE * 0.75f > heroDistance + HudOverlayModel.HERO_VALUE_SIZE * 0.25f)
    }

    @Test
    fun `trip line with its flag is centred as one`() {
        val size = HudOverlayModel.TRIP_SIZE
        val text = 100f
        val (flagLeft, textLeft) = HudOverlayModel.tripLineLefts(text, size)
        val right = textLeft + text
        assertEquals(HudOverlayModel.BOX_CENTER_X, (flagLeft + right) / 2, 0.01f)
        assertEquals(size * HudOverlayModel.TRIP_FLAG_SCALE + HudOverlayModel.TRIP_FLAG_GAP, textLeft - flagLeft, 0.01f)
    }
}
