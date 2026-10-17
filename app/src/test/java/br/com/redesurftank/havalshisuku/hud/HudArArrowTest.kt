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
    fun `AR layout uses the 3D arrow only without lanes and for shaped maneuvers`() {
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        val nav = HudOverlayModel.Config(layout = HudOverlayModel.Layout.NAVIGATION)
        val turn = Directions(active = true, distance = "90 m", distanceM = 90, turn = "TURN_RIGHT")

        val tbt = HudOverlayModel.tbtFor(turn, ar)!!
        assertEquals(HudOverlayModel.TbtStyle.AR, tbt.style)
        assertEquals(90f, tbt.arAngle)
        assertEquals(90f, tbt.arMeters)

        assertEquals(HudOverlayModel.TbtStyle.HERO, HudOverlayModel.tbtFor(turn, nav)!!.style)
        assertNull(HudOverlayModel.tbtFor(turn, nav)!!.arAngle)
        val withLanes = turn.copy(lanes = listOf(listOf(LaneDirection(5, true))))
        assertEquals(HudOverlayModel.TbtStyle.COMPACT, HudOverlayModel.tbtFor(withLanes, ar)!!.style)
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
}
