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
    fun `arrow fades in at the top and out over the bottom of the band`() {
        assertEquals(0f, HudArArrow.rowAlpha(HudArArrow.TOP), 0f)
        assertEquals(1f, HudArArrow.rowAlpha(HudArArrow.FADE_TOP_TO), 0.001f)
        assertEquals(1f, HudArArrow.rowAlpha(160f), 0f)
        assertEquals(1f, HudArArrow.rowAlpha(HudArArrow.FADE_BOTTOM_FROM), 0.001f)
        assertEquals(0.5f, HudArArrow.rowAlpha((HudArArrow.FADE_BOTTOM_FROM + HudArArrow.BOTTOM) / 2), 0.01f)
        assertEquals(0f, HudArArrow.rowAlpha(HudArArrow.BOTTOM), 0f)
    }

    @Test
    fun `one continuous ribbon - straight-sided stem rounding exactly into the arm`() {
        for ((meters, angle) in listOf(1_200f to 0f, 90f to 90f, 0f to -135f)) {
            val shape = HudArArrow.shape(meters, angle)
            val pts = shape.blue
            // No step anywhere: neighbouring widths never jump.
            for (i in 1 until pts.size) {
                val jump = kotlin.math.abs(pts[i].w - pts[i - 1].w)
                assertTrue("m=$meters a=$angle i=$i jump=$jump", jump < 8f)
            }
            // Wide at the bottom, the arm's width at the corner.
            assertTrue(pts.first().w > 180f)
        }
        // Straight sides away from the fillet: width linear in t.
        val cw = 20f
        val a = HudArArrow.stemWidth(0.4f, cw)
        val b = HudArArrow.stemWidth(0.7f, cw)
        val c = HudArArrow.stemWidth(1.0f, cw)
        assertEquals(b - a, c - b, 0.01f)
        assertEquals(cw, HudArArrow.stemWidth(0f, cw), 0.001f)
        assertEquals(HudArArrow.BASE_W, HudArArrow.stemWidth(1f, cw), 0.01f)
    }

    @Test
    fun `far away it is a straight arrow with its head inside the band`() {
        val shape = HudArArrow.shape(1_200f, 90f)
        assertTrue(shape.blue.all { kotlin.math.abs(it.x - HudArArrow.CX) < 0.01f })
        assertTrue("tip y=${shape.head[1]}", shape.head[1] > HudArArrow.TOP - 2f)
    }

    @Test
    fun `the arrow and head stay on the panel at every angle and distance`() {
        val lo = HudArArrow.CX - HudArArrow.ROOM_LEFT - 1f
        val hi = HudArArrow.CX + HudArArrow.ROOM_RIGHT + 1f
        for (meters in listOf(0f, 20f, 60f, 120f)) for (angle in listOf(90f, -90f, 135f, -135f, -180f, 45f, 0f)) {
            val shape = HudArArrow.shape(meters, angle)
            for (p in shape.blue.filter { it.y in HudArArrow.TOP..HudArArrow.BOTTOM && kotlin.math.abs(it.x - HudArArrow.CX) > 1f }) {
                assertTrue("m=$meters angle=$angle x=${p.x} w=${p.w}", p.x - p.w / 2 >= lo && p.x + p.w / 2 <= hi)
            }
            for (k in 0 until 3) assertTrue("m=$meters angle=$angle head x=${shape.head[2 * k]}", shape.head[2 * k] in lo..hi)
        }
        assertEquals(HudOverlayModel.ARC_CX, HudArArrow.CX, 0f)
        assertTrue(lo >= 0f && hi <= 480f)
        assertTrue(HudArArrow.shape(0f, 90f).blue.last().x > HudArArrow.CX + 150f)
        assertTrue(HudArArrow.shape(0f, -90f).blue.last().x < HudArArrow.CX - 150f)
    }

    @Test
    fun `roundabout - grey ring and passed exits, blue route to our exit`() {
        // 3rd exit, 270° round a counter-clockwise ring: two exits passed first.
        val passed = HudOverlayModel.passedExitAngles(270, 3)
        assertEquals(2, passed.size)
        val near = HudArArrow.roundabout(20f, 270f, clockwise = false, passedDeg = passed)
        assertEquals(1 + passed.size, near.gray.size) // ring + one stub per passed exit
        // Counter-clockwise for right-hand traffic: 270° comes out on the left.
        assertTrue(near.blue.last().x < HudArArrow.CX)
        val mirrored = HudArArrow.roundabout(20f, 270f, clockwise = true, passedDeg = passed)
        assertTrue(mirrored.blue.last().x > HudArArrow.CX)
        // Far away: no ring yet, just a straight arrow.
        val far = HudArArrow.roundabout(1_000f, 270f, clockwise = false, passedDeg = passed)
        assertTrue(far.gray.isEmpty())
        assertTrue(far.blue.all { kotlin.math.abs(it.x - HudArArrow.CX) < 0.01f })
    }

    @Test
    fun `roundabout progress greys the part already driven`() {
        val passed = HudOverlayModel.passedExitAngles(180, 2)
        val before = HudArArrow.roundabout(0f, 180f, false, passed, progress = 0f)
        val half = HudArArrow.roundabout(0f, 180f, false, passed, progress = 0.5f)
        assertTrue(half.gray.size > before.gray.size) // driven part added in grey
        assertTrue(half.blue.size < before.blue.size)
        assertEquals(0f, HudArArrow.roundaboutProgress(0f, 180f), 0f)
        val way = (Math.PI * HudArArrow.RB_RADIUS_M).toFloat() // 180° on a 15 m ring
        assertEquals(0.5f, HudArArrow.roundaboutProgress(way / 2, 180f), 0.01f)
        assertEquals(1f, HudArArrow.roundaboutProgress(way * 3, 180f), 0f)
    }

    @Test
    fun `AR layout draws roundabouts with a known exit in 3D`() {
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        val rb = Directions(
            active = true, distance = "80 m", distanceM = 80, turn = "ROUNDABOUT",
            exitNumber = 2, turnAngle = 180, roundaboutClockwise = false
        )
        val tbt = HudOverlayModel.tbtFor(rb, ar)!!
        assertEquals(HudOverlayModel.TbtStyle.AR, tbt.style)
        assertTrue(tbt.dynamicRoundabout)
        assertEquals(2, tbt.exitNumber)
        val frame = HudOverlayModel.frameFor(ar, rb, 0, null, arMetersOverride = 0f, arProgress = 0.4f)
        assertEquals(0.4f, frame.tbt!!.arProgress, 0f)
        // Without the exit angle it stays the flat icon.
        assertEquals(HudOverlayModel.TbtStyle.HERO, HudOverlayModel.tbtFor(rb.copy(turnAngle = null), ar)!!.style)
    }

    @Test
    fun `arcs span 195 degrees, 15 shorter at each end, clear of the column`() {
        val first = HudOverlayModel.arcTickDeg(0)
        val last = HudOverlayModel.arcTickDeg(HudOverlayModel.ARC_TICKS - 1)
        val slot = HudOverlayModel.ARC_SWEEP_DEG / HudOverlayModel.ARC_TICKS
        assertEquals(150f + slot / 2, first, 0.01f)
        assertEquals(345f - slot / 2, last, 0.01f)
        assertTrue(HudOverlayModel.ARC_POWER_R_OUT - HudOverlayModel.ARC_POWER_R_IN > HudOverlayModel.ARC_RPM_R_OUT - HudOverlayModel.ARC_RPM_R_IN)
        assertTrue(HudOverlayModel.ARC_POWER_TICK_W > HudOverlayModel.ARC_RPM_TICK_W)
        assertTrue(HudOverlayModel.ARC_CY - HudOverlayModel.ARC_POWER_R_OUT > 40f)
        assertTrue(HudOverlayModel.ARC_CX - HudOverlayModel.ARC_POWER_R_OUT > HudOverlayModel.BOX_RIGHT)
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
        val roundabout = turn.copy(turn = "ROUNDABOUT") // no exit angle: flat icon
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
    fun `power arc - one tick per 2 percent from the left, regen too`() {
        assertEquals(0, HudOverlayModel.arcPowerTicks(0))
        assertEquals(1, HudOverlayModel.arcPowerTicks(1)) // anything lights one
        assertEquals(18, HudOverlayModel.arcPowerTicks(35))
        assertEquals(50, HudOverlayModel.arcPowerTicks(100))
        assertEquals(20, HudOverlayModel.arcPowerTicks(-40)) // regen fills from the left as well
        assertEquals(50, HudOverlayModel.arcPowerTicks(-250))
    }

    @Test
    fun `rpm arc - 50 ticks over 0 to 7000 rpm, at least one while turning`() {
        assertEquals(0, HudOverlayModel.arcRpmTicks(0))
        assertEquals(1, HudOverlayModel.arcRpmTicks(1)) // 100 rpm still lights one
        assertEquals(25, HudOverlayModel.arcRpmTicks(35))
        assertEquals(40, HudOverlayModel.arcRpmTicks(56))
        assertEquals(50, HudOverlayModel.arcRpmTicks(75))
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
        assertTrue(left.rpmTenths!! * 100 > HudOverlayModel.RPM_RED) // into the red zone
        // Every step is something the AR theme draws with its centre arrow.
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        for (t in 0 until 48) {
            val step = HudOverlayModel.DemoDrive.at(t.toFloat(), 15f)
            assertEquals(HudOverlayModel.TbtStyle.AR, HudOverlayModel.tbtFor(step.directions, ar)!!.style)
        }
    }
}
