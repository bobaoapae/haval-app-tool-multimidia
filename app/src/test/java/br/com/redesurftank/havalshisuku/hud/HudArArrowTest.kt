package br.com.redesurftank.havalshisuku.hud

import br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry.Directions
import br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry.LaneDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HudArArrowTest {
    @Test
    fun `maneuvers map to bend angles, roundabouts keep the flat icon`() {
        assertEquals(90f, HudArArrow.angleFor("TURN_RIGHT"))
        assertEquals(-90f, HudArArrow.angleFor("turn_left"))
        assertEquals(-135f, HudArArrow.angleFor("SHARP_LEFT"))
        assertEquals(45f, HudArArrow.angleFor("SLIGHT_RIGHT"))
        assertEquals(-25f, HudArArrow.angleFor("KEEP_LEFT"))
        assertEquals(35f, HudArArrow.angleFor("OFF_RAMP_RIGHT"))
        assertEquals(-180f, HudArArrow.angleFor("U_TURN_LEFT"))
        assertEquals(0f, HudArArrow.angleFor("STRAIGHT"))
        assertNull(HudArArrow.angleFor("ROUNDABOUT"))
        assertNull(HudArArrow.angleFor("ROUNDABOUT_EXIT"))
        assertNull(HudArArrow.angleFor(null))
    }

    @Test
    fun `straight while far, full turn when close, eased in between`() {
        assertEquals(0f, HudArArrow.bendAmount(1_200f), 0f)
        assertEquals(0f, HudArArrow.bendAmount(HudArArrow.BEND_START_M), 0f)
        assertEquals(1f, HudArArrow.bendAmount(HudArArrow.BEND_FULL_M), 0f)
        assertEquals(1f, HudArArrow.bendAmount(0f), 0f)
        val mid = HudArArrow.bendAmount((HudArArrow.BEND_START_M + HudArArrow.BEND_FULL_M) / 2)
        assertEquals(0.5f, mid, 0.01f)
    }

    @Test
    fun `corner comes down as the car closes in, capped while far`() {
        assertEquals(HudArArrow.Z_CAP, HudArArrow.cornerDepth(5_000f), 0f)
        assertEquals(HudArArrow.Z_END, HudArArrow.cornerDepth(0f), 0f)
        assertTrue(HudArArrow.cornerDepth(50f) < HudArArrow.cornerDepth(100f))
    }

    @Test
    fun `far away it is only the wedge, running to the top`() {
        val shape = HudArArrow.shape(1_200f, 90f)
        assertEquals(HudArArrow.TOP, shape.stemTop, 0f)
        assertTrue(shape.arm.isEmpty())
        assertNull(shape.head)
        assertEquals(0f, shape.armAlpha, 0f)
    }

    @Test
    fun `stem is 200 px at the base with straight sides meeting at the apex`() {
        assertEquals(HudArArrow.BASE_W, HudArArrow.stemWidthAt(HudArArrow.BOTTOM), 0.01f)
        assertEquals(0f, HudArArrow.stemWidthAt(HudArArrow.APEX), 0.01f)
        // Linear in y = straight edges.
        val a = HudArArrow.stemWidthAt(150f)
        val b = HudArArrow.stemWidthAt(175f)
        val c = HudArArrow.stemWidthAt(200f)
        assertEquals(b - a, c - b, 0.01f)
        assertTrue(HudArArrow.stemWidthAt(HudArArrow.TOP) < 25f) // thin far end
    }

    @Test
    fun `as the car arrives the stem comes down to the corner and the arm grows`() {
        val near = HudArArrow.shape(0f, 90f)
        val cornerY = HudArArrow.project(0f, HudArArrow.Z_END).second
        assertEquals(cornerY, near.stemTop, 0.01f)
        assertEquals(1f, near.armAlpha, 0f)
        assertTrue(near.arm.isNotEmpty())
        assertTrue(near.arm.all { it.w <= HudArArrow.ARM_W + 0.01f })
        // Right turn: the arm ends to the right of the stem.
        assertTrue(near.arm.last().x > HudArArrow.CX + 30f)

        val bending = HudArArrow.shape(110f, 90f)
        assertTrue(bending.armAlpha in 0.1f..0.9f)
        assertTrue(bending.stemTop in HudArArrow.TOP..cornerY)
    }

    @Test
    fun `the arm and head stay inside the middle strip at every angle`() {
        val lo = HudArArrow.CX - HudArArrow.HALF_ROOM - 1f
        val hi = HudArArrow.CX + HudArArrow.HALF_ROOM + 1f
        for (meters in listOf(0f, 20f, 60f, 120f)) for (angle in listOf(90f, -90f, 135f, -135f, -180f, 45f, 0f)) {
            val shape = HudArArrow.shape(meters, angle)
            for (p in shape.arm) {
                assertTrue("m=$meters angle=$angle x=${p.x} w=${p.w}", p.x - p.w / 2 >= lo && p.x + p.w / 2 <= hi)
            }
            shape.head?.let { h ->
                for (k in 0 until 3) assertTrue("m=$meters angle=$angle head x=${h[2 * k]}", h[2 * k] in lo..hi)
            }
        }
        // The wide stem and the strip stay clear of the left box's bars and the RPM bar.
        val barBottom = HudOverlayModel.BAR_Y + HudOverlayModel.BAR_HEIGHT / 2
        assertTrue(HudArArrow.CX - HudArArrow.stemWidthAt(barBottom) / 2 > HudOverlayModel.POWER_RIGHT)
        assertTrue(hi < HudOverlayModel.RPM_LEFT)
    }

    @Test
    fun `AR layout keeps the centre arrow with lanes, roundabouts keep the flat icon`() {
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        val nav = HudOverlayModel.Config(layout = HudOverlayModel.Layout.NAVIGATION)
        val turn = Directions(active = true, distance = "90 m", distanceM = 90, turn = "TURN_RIGHT")

        val tbt = HudOverlayModel.tbtFor(turn, ar)!!
        assertEquals(HudOverlayModel.TbtStyle.AR, tbt.style)
        assertEquals(90f, tbt.arAngle)
        assertEquals(90f, tbt.arMeters)

        assertEquals(HudOverlayModel.TbtStyle.HERO, HudOverlayModel.tbtFor(turn, nav)!!.style)
        assertNull(HudOverlayModel.tbtFor(turn, nav)!!.arAngle)
        val withLanes = turn.copy(lanes = listOf(listOf(LaneDirection(5, true))), street = " R. Augusta ")
        val laned = HudOverlayModel.tbtFor(withLanes, ar)!!
        assertEquals(HudOverlayModel.TbtStyle.AR, laned.style)
        assertEquals(1, laned.lanes.size)
        assertEquals("R. Augusta", laned.street)
        assertEquals(HudOverlayModel.TbtStyle.COMPACT, HudOverlayModel.tbtFor(withLanes, nav)!!.style)
        val roundabout = turn.copy(turn = "ROUNDABOUT")
        assertEquals(HudOverlayModel.TbtStyle.HERO, HudOverlayModel.tbtFor(roundabout, ar)!!.style)
    }

    @Test
    fun `the controller's estimate replaces the AR distance only`() {
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        val turn = Directions(active = true, distance = "90 m", distanceM = 90, turn = "TURN_RIGHT")
        val frame = HudOverlayModel.frameFor(ar, turn, 0, null, arMetersOverride = 72.5f)
        assertEquals(72.5f, frame.tbt!!.arMeters)
        assertEquals("90", frame.tbt!!.distanceValue) // the text stays Android Auto's
        val navFrame = HudOverlayModel.frameFor(ar.copy(layout = HudOverlayModel.Layout.NAVIGATION), turn, 0, null, 72.5f)
        assertNull(navFrame.tbt!!.arMeters)
    }

    @Test
    fun `AR frames use arcs, Navegação keeps the bars`() {
        val turn = Directions(active = true, distance = "90 m", distanceM = 90, turn = "TURN_RIGHT")
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        assertTrue(HudOverlayModel.frameFor(ar, turn, 30, 24).arcs)
        assertTrue(!HudOverlayModel.frameFor(ar.copy(layout = HudOverlayModel.Layout.NAVIGATION), turn, 30, 24).arcs)
    }

    @Test
    fun `power arc - drive fills from the left, regen grows from the top centre`() {
        assertTrue(HudOverlayModel.arcPowerLit(0).isEmpty())
        assertEquals(0 until 4, HudOverlayModel.arcPowerLit(35))
        assertEquals(0 until 10, HudOverlayModel.arcPowerLit(100))
        assertEquals(0 until 1, HudOverlayModel.arcPowerLit(2))
        // Regen: symmetric round the top (between segments 4 and 5), one per side per 20 %.
        assertEquals(4 until 6, HudOverlayModel.arcPowerLit(-10))
        assertEquals(3 until 7, HudOverlayModel.arcPowerLit(-35))
        assertEquals(0 until 10, HudOverlayModel.arcPowerLit(-100))
    }

    @Test
    fun `rpm arc has 7 steps of 1000 rpm, red past 5k`() {
        assertEquals(0, HudOverlayModel.arcRpmLit(0))
        assertEquals(1, HudOverlayModel.arcRpmLit(8))
        assertEquals(3, HudOverlayModel.arcRpmLit(32))
        assertEquals(7, HudOverlayModel.arcRpmLit(75))
        assertTrue(HudOverlayModel.rpmSegmentIsRed(5) && HudOverlayModel.rpmSegmentIsRed(6))
        assertTrue(!HudOverlayModel.rpmSegmentIsRed(4))
    }

    @Test
    fun `arcs span 270 degrees open at the bottom, top centre between the middle segments`() {
        val (firstStart, _) = HudOverlayModel.arcSegment(0, 10)
        val (lastStart, lastSweep) = HudOverlayModel.arcSegment(9, 10)
        assertEquals(HudOverlayModel.ARC_START_DEG + HudOverlayModel.ARC_GAP_DEG / 2, firstStart, 0.01f)
        assertEquals(HudOverlayModel.ARC_START_DEG + 270f - HudOverlayModel.ARC_GAP_DEG / 2, lastStart + lastSweep, 0.01f)
        // Top of the arc is 270° in canvas angles: the boundary between segments 4 and 5.
        val (s5, _) = HudOverlayModel.arcSegment(5, 10)
        assertEquals(270f, s5 - HudOverlayModel.ARC_GAP_DEG / 2, 0.01f)
        // Inside the visible band.
        assertTrue(HudOverlayModel.ARC_CY - HudOverlayModel.ARC_POWER_R - HudOverlayModel.ARC_THICKNESS > 40f)
    }

    @Test
    fun `drive demo - right turn approaching, then a sharp left with lanes into the red zone`() {
        val start = HudOverlayModel.DemoDrive.at(0f, 15f)
        assertEquals("TURN_RIGHT", start.directions.turn)
        assertEquals(450, start.directions.distanceM)
        assertEquals(28, start.rpmTenths)
        val nearTurn = HudOverlayModel.DemoDrive.at(29f, 15f)
        assertEquals(15, nearTurn.directions.distanceM)
        assertTrue(nearTurn.powerPercent < 0) // regen into the turn
        val left = HudOverlayModel.DemoDrive.at(35f, 15f)
        assertEquals("SHARP_LEFT", left.directions.turn)
        assertEquals(3, left.directions.lanes.size)
        assertTrue(HudOverlayModel.rpmSegmentIsRed(HudOverlayModel.arcRpmLit(left.rpmTenths!!) - 1))
        // Every step is something the AR theme draws with its centre arrow.
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        for (t in 0 until 48) {
            val step = HudOverlayModel.DemoDrive.at(t.toFloat(), 15f)
            assertEquals(HudOverlayModel.TbtStyle.AR, HudOverlayModel.tbtFor(step.directions, ar)!!.style)
        }
    }
}
