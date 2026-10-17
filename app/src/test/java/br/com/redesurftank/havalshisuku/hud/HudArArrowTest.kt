package br.com.redesurftank.havalshisuku.hud

import br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry.Directions
import br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry.LaneDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
    fun `far arrow is straight with its head inside the visible band`() {
        val shape = HudArArrow.shape(1_200f, 90f)
        assertTrue(shape.ribbon.all { abs(it.x - HudOverlayModel.BOX_CENTER_X) < 0.01f })
        val tipY = shape.head[1]
        assertTrue("tip y=$tipY", tipY > HudArArrow.TOP && HudArArrow.rowAlpha(shape.ribbon.last().y) > 0.3f)
    }

    @Test
    fun `at the turn the whole arrow and head stay inside the free box`() {
        for (angle in listOf(90f, -90f, 135f, -135f, -180f, 45f)) {
            val shape = HudArArrow.shape(0f, angle)
            val visible = shape.ribbon.filter { it.y in HudArArrow.TOP..HudArArrow.BOTTOM }
            for (p in visible) {
                assertTrue("angle=$angle x=${p.x}", p.x - p.w / 2 >= HudOverlayModel.BOX_LEFT - 1f)
                assertTrue("angle=$angle x=${p.x}", p.x + p.w / 2 <= HudOverlayModel.BOX_RIGHT + 1f)
            }
            for (k in 0 until 3) {
                val x = shape.head[2 * k]
                assertTrue("angle=$angle head x=$x", x in HudOverlayModel.BOX_LEFT - 1f..HudOverlayModel.BOX_RIGHT + 1f)
            }
        }
    }

    @Test
    fun `thick at the base, thin far away`() {
        val shape = HudArArrow.shape(1_200f, 0f)
        val base = shape.ribbon.first { it.y <= HudArArrow.BOTTOM }
        val far = shape.ribbon.last()
        assertTrue(base.w > 3 * far.w)
        assertTrue(base.w <= HudArArrow.MAX_W)
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
