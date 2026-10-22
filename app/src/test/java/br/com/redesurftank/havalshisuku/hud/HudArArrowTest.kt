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
    fun `one ribbon in true perspective - both edges run to the same vanishing point`() {
        val pts = HudArArrow.shape(1_200f, 0f).blue.filter { it.y in HudArArrow.TOP..HudArArrow.BOTTOM }
        assertTrue(pts.size > 10)
        // Straight ahead: each edge is a line through (CX, HORIZON), so no "rising" at the top.
        val slopes = pts.map { (it.rx - HudArArrow.CX) / (it.ry - HudArArrow.HORIZON) }
        for (k in slopes) assertEquals(slopes.first(), k, 0.01f)
        // BASE_W wide at the bottom edge.
        val bottom = pts.minByOrNull { kotlin.math.abs(it.y - HudArArrow.BOTTOM) }!!
        assertEquals(HudArArrow.BASE_W, bottom.w, 6f)
        // Turns stay one smooth ribbon: no jump in width from one cross-section to the next.
        for ((meters, angle) in listOf(90f to 90f, 0f to -135f, 0f to 180f)) {
            val blue = HudArArrow.shape(meters, angle).blue.filter { it.y in HudArArrow.TOP..HudArArrow.BOTTOM }
            for (i in 1 until blue.size) {
                val jump = kotlin.math.abs(blue[i].w - blue[i - 1].w)
                assertTrue("m=$meters a=$angle i=$i jump=$jump", jump < 0.1f * maxOf(blue[i].w, blue[i - 1].w) + 2f)
            }
        }
    }

    @Test
    fun `far away it is a straight arrow with its head inside the band`() {
        for (meters in listOf(1_000f, 400f, HudArArrow.BEND_START_M)) {
            val shape = HudArArrow.shape(meters, 90f)
            assertTrue("m=$meters", shape.blue.all { kotlin.math.abs(it.x - HudArArrow.CX) < 0.01f })
            assertEquals(6, shape.head.size)
            assertTrue("tip y=${shape.head[1]}", shape.head[1] > HudArArrow.TOP - 2f)
        }
        // Only inside ~200 m does the turn show.
        assertTrue(HudArArrow.shape(120f, 90f).blue.last().x > HudArArrow.CX + 5f)
    }

    @Test
    fun `the arrow and head stay on the panel at every angle and distance`() {
        val lo = HudArArrow.CX - HudArArrow.ROOM_LEFT - 1f
        val hi = HudArArrow.CX + HudArArrow.ROOM_RIGHT + 1f
        for (meters in listOf(0f, 20f, 60f, 120f)) for (angle in listOf(90f, -90f, 135f, -135f, -180f, 180f, 45f, 0f)) {
            val shape = HudArArrow.shape(meters, angle)
            for (p in shape.blue.filter { it.y in HudArArrow.TOP..HudArArrow.BOTTOM }) {
                assertTrue("m=$meters angle=$angle l=${p.lx} r=${p.rx}", minOf(p.lx, p.rx) >= lo && maxOf(p.lx, p.rx) <= hi)
            }
            assertEquals("m=$meters angle=$angle", 6, shape.head.size)
            for (k in 0 until 3) assertTrue("m=$meters angle=$angle head x=${shape.head[2 * k]}", shape.head[2 * k] in lo..hi)
        }
        assertEquals(HudOverlayModel.NATIVE_SPEED_CX, HudArArrow.CX, 0f)
        assertTrue(lo >= 0f && hi <= 480f)
        assertTrue(HudArArrow.shape(0f, 90f).blue.last().x > HudArArrow.CX + 100f)
        assertTrue(HudArArrow.shape(0f, -90f).blue.last().x < HudArArrow.CX - 100f)
    }

    @Test
    fun `U-turns curl to their own side`() {
        assertEquals(180f, HudArArrow.angleFor("U_TURN_RIGHT"))
        assertEquals(-180f, HudArArrow.angleFor("U_TURN_LEFT"))
        assertEquals(-180f, HudArArrow.angleFor("U_TURN")) // no side: left, right-hand traffic
        assertTrue(HudArArrow.shape(0f, 180f).head[0] > HudArArrow.CX)
        assertTrue(HudArArrow.shape(0f, -180f).head[0] < HudArArrow.CX)
    }

    @Test
    fun `roundabout - grey ring and passed exits, blue route to our exit`() {
        // 3rd exit, 270° round a counter-clockwise ring: two exits passed first.
        val passed = HudOverlayModel.passedExitAngles(270, 3)
        assertEquals(2, passed.size)
        val near = HudArArrow.roundabout(20f, 270f, clockwise = false, passedDeg = passed)
        assertEquals(1 + passed.size, near.gray.size) // ring + one stub per passed exit
        assertEquals(1f, near.grayAlpha, 0.001f)
        // Counter-clockwise for right-hand traffic: 270° comes out on the left.
        assertTrue(near.head[0] < HudArArrow.CX)
        val mirrored = HudArArrow.roundabout(20f, 270f, clockwise = true, passedDeg = passed)
        assertTrue(mirrored.head[0] > HudArArrow.CX)
        // Far away: no ring yet, just a straight arrow.
        val far = HudArArrow.roundabout(1_000f, 270f, clockwise = false, passedDeg = passed)
        assertTrue(far.gray.isEmpty())
        assertTrue(far.blue.all { kotlin.math.abs(it.x - HudArArrow.CX) < 0.01f })
    }

    @Test
    fun `inside the roundabout the exit stays where it was, in view`() {
        val passed = HudOverlayModel.passedExitAngles(270, 3)
        val before = HudArArrow.roundabout(0f, 270f, false, passed, progress = 0f)
        val half = HudArArrow.roundabout(0f, 270f, false, passed, progress = 0.5f)
        val late = HudArArrow.roundabout(0f, 270f, false, passed, progress = 0.9f)
        // What's been driven drops off the route; the exit itself does not swing away.
        assertTrue(half.blue.size < before.blue.size)
        assertTrue(late.blue.size < half.blue.size)
        assertTrue(half.gray.isNotEmpty())
        assertEquals(6, late.head.size)
        // A left exit stays on the left, inside the band, instead of sliding across the view.
        assertTrue(before.head[0] < HudArArrow.CX && late.head[0] < HudArArrow.CX)
        assertTrue(before.head[1] in HudArArrow.TOP..HudArArrow.BOTTOM)
        assertTrue(late.head[1] in HudArArrow.TOP..HudArArrow.BOTTOM)
        // Straight across stays ahead, not over on the left.
        val straight = HudArArrow.roundabout(0f, 180f, false, HudOverlayModel.passedExitAngles(180, 2), progress = 0.5f)
        assertEquals(HudArArrow.CX, straight.head[0], 30f)
        assertEquals(0f, HudArArrow.roundaboutProgress(0f, 180f), 0f)
        val way = (Math.PI * HudArArrow.RB_RADIUS_M).toFloat() // 180° on a 15 m ring
        assertEquals(0.5f, HudArArrow.roundaboutProgress(way / 2, 180f), 0.01f)
        assertEquals(1f, HudArArrow.roundaboutProgress(way * 3, 180f), 0f)
        // No jump at the entry: still approaching while the distance is longer than the ring,
        // then the left icon closes as the metres left to the exit fall.
        assertEquals(0f, HudArArrow.progressFromExitDistance(way + 40f, 180f), 0f)
        assertEquals(0f, HudArArrow.progressFromExitDistance(way, 180f), 0f)
        assertEquals(0.5f, HudArArrow.progressFromExitDistance(way / 2f, 180f), 0.02f)
        assertEquals(1f, HudArArrow.progressFromExitDistance(0f, 180f), 0f)
    }

    @Test
    fun `a new maneuver still carrying the last distance draws straight until its own arrives`() {
        val turn = Directions(active = true, distance = "0 m", distanceM = 0, turn = "TURN_RIGHT", street = "A")
        val next = turn.copy(turn = "TURN_LEFT", street = "B") // code 10 before its code 11
        assertTrue(HudOverlayModel.distanceStale(turn, next, wasStale = false))
        assertTrue(HudOverlayModel.distanceStale(next, next.copy(lanes = emptyList()), wasStale = true))
        val fresh = next.copy(distance = "850 m", distanceM = 850)
        assertTrue(!HudOverlayModel.distanceStale(next, fresh, wasStale = true))
        assertTrue(!HudOverlayModel.distanceStale(null, next, wasStale = false))
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        assertNull(HudOverlayModel.frameFor(ar, next, 0, null, arDistanceKnown = false).tbt!!.arMeters)
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
        // Stretched sideways for a three-digit speed, the height unchanged.
        val (left, mid) = HudOverlayModel.arcPoint(180f, HudOverlayModel.ARC_POWER_R_OUT)
        assertEquals(HudOverlayModel.ARC_CX - HudOverlayModel.ARC_POWER_R_OUT * HudOverlayModel.ARC_X_SCALE, left, 0.01f)
        assertEquals(HudOverlayModel.ARC_CY, mid, 0.01f)
        assertEquals(HudOverlayModel.ARC_CY - HudOverlayModel.ARC_POWER_R_OUT, HudOverlayModel.arcPoint(270f, HudOverlayModel.ARC_POWER_R_OUT).second, 0.01f)
        assertTrue(HudOverlayModel.ARC_X_SCALE > 1f)
        // 6 px left of the speed; the street line is narrowed to keep clear of the arc.
        // Two digits: 4 px left of the speed; three digits (wide): 6 px left, room for the "1".
        assertEquals(HudOverlayModel.NATIVE_SPEED_CX - 4f, HudOverlayModel.ARC_CX, 0f)
        assertEquals(HudOverlayModel.NATIVE_SPEED_CX - 6f, HudOverlayModel.ARC_WIDE_CX, 0f)
        val right = HudOverlayModel.columnRightAt(HudOverlayModel.AR_STREET_BASELINE, HudOverlayModel.AR_STREET_SIZE)
        assertTrue(right < HudOverlayModel.BOX_RIGHT && right > HudOverlayModel.BOX_CENTER_X + 40f)
        for (i in 0 until HudOverlayModel.ARC_TICKS) {
            val (x, y) = HudOverlayModel.arcPoint(HudOverlayModel.arcTickDeg(i), HudOverlayModel.ARC_POWER_R_OUT)
            if (y in HudOverlayModel.AR_STREET_BASELINE - HudOverlayModel.AR_STREET_SIZE..HudOverlayModel.AR_STREET_BASELINE) assertTrue("tick $i x=$x", x > right)
        }
        // Rows the arc doesn't reach keep the whole column.
        assertEquals(HudOverlayModel.BOX_RIGHT, HudOverlayModel.columnRightAt(190f, 14f), 0f)
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
    fun `each layout picks bars or arcs, Navegação bars and AR arcs by default`() {
        val turn = Directions(active = true, distance = "90 m", distanceM = 90, turn = "TURN_RIGHT")
        val nav = HudOverlayModel.Config(layout = HudOverlayModel.Layout.NAVIGATION)
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        assertTrue(!HudOverlayModel.frameFor(nav, turn, 30, 24).arcs)
        assertTrue(HudOverlayModel.frameFor(ar, turn, 30, 24).arcs)
        assertTrue(HudOverlayModel.frameFor(nav.copy(navGauges = HudOverlayModel.Gauges.ARCS), turn, 30, 24).arcs)
        assertTrue(!HudOverlayModel.frameFor(ar.copy(arGauges = HudOverlayModel.Gauges.BARS), turn, 30, 24).arcs)
        // One layout's choice doesn't touch the other's.
        assertTrue(HudOverlayModel.frameFor(ar.copy(navGauges = HudOverlayModel.Gauges.BARS), turn, 30, 24).arcs)
        assertTrue(!HudOverlayModel.frameFor(nav.copy(arGauges = HudOverlayModel.Gauges.BARS), turn, 30, 24).arcs)
        // "Nenhum": no power, no RPM, whatever the two switches say.
        val none = HudOverlayModel.frameFor(ar.copy(arGauges = HudOverlayModel.Gauges.NONE), turn, 30, 24)
        assertNull(none.powerPercent)
        assertNull(none.rpmTenths)
        assertEquals(HudOverlayModel.Gauges.ARCS, nav.withGauges(HudOverlayModel.Layout.NAVIGATION, HudOverlayModel.Gauges.ARCS).navGauges)
        assertEquals(HudOverlayModel.Gauges.ARCS, ar.withGauges(HudOverlayModel.Layout.NAVIGATION, HudOverlayModel.Gauges.ARCS).navGauges)
    }

    @Test
    fun `AR arrow is more see-through while far and the column knows it is far`() {
        assertEquals(HudArArrow.OPACITY_FAR, HudArArrow.opacityFor(1_000f), 0f)
        assertEquals(HudArArrow.OPACITY, HudArArrow.opacityFor(0f), 0f)
        assertTrue(HudArArrow.opacityFor(120f) in HudArArrow.OPACITY_FAR..HudArArrow.OPACITY)
        assertTrue(HudArArrow.OPACITY_FAR < HudArArrow.OPACITY)
        assertTrue(HudArArrow.isFar(null))
        assertTrue(HudArArrow.isFar(HudArArrow.BEND_START_M))
        assertTrue(!HudArArrow.isFar(150f))
    }

    @Test
    fun `chevrons - still from 100 m, animating from 50 m, hidden when stopped, both layouts`() {
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        val right = Directions(active = true, distance = "100 m", distanceM = 100, turn = "TURN_RIGHT")
        val far = HudOverlayModel.frameFor(ar, right.copy(distance = "300 m", distanceM = 300), 0, null)
        assertNull(far.chevrons)
        val held = HudOverlayModel.frameFor(ar, right, 0, null, arMetersOverride = 80f).chevrons!!
        assertEquals(1, held.side)
        assertEquals(0f, held.intensity, 0f)
        assertTrue(!held.animate)
        val near = HudOverlayModel.frameFor(ar, right, 0, null, arMetersOverride = 40f).chevrons!!
        assertEquals(1, near.side)
        assertTrue(near.animate)
        assertEquals(0.2f, near.intensity, 0.01f)
        val at = HudOverlayModel.frameFor(ar, right, 0, null, arMetersOverride = 0f).chevrons!!
        assertEquals(1f, at.intensity, 0f)
        val left = HudOverlayModel.frameFor(ar, right.copy(turn = "SHARP_LEFT"), 0, null, arMetersOverride = 30f).chevrons!!
        assertEquals(-1, left.side)
        // No side to point to: straight on, keep (slight), or switched off.
        assertNull(HudOverlayModel.frameFor(ar, right.copy(turn = "STRAIGHT"), 0, null, arMetersOverride = 30f).chevrons)
        assertNull(HudOverlayModel.frameFor(ar, right.copy(turn = "KEEP_LEFT"), 0, null, arMetersOverride = 30f).chevrons)
        assertNull(HudOverlayModel.frameFor(ar.copy(chevrons = false), right, 0, null, arMetersOverride = 30f).chevrons)
        // Navegação uses the same chevrons, and both layouts hide them when stopped.
        val nav = HudOverlayModel.frameFor(ar.copy(layout = HudOverlayModel.Layout.NAVIGATION), right, 0, null, 30f).chevrons!!
        assertEquals(1, nav.side)
        assertTrue(nav.animate)
        assertNull(HudOverlayModel.frameFor(ar, right, 0, null, arMetersOverride = 30f, speedKmh = 0.0).chevrons)
        assertNull(HudOverlayModel.frameFor(ar.copy(layout = HudOverlayModel.Layout.NAVIGATION), right, 0, null, 30f, speedKmh = 0.0).chevrons)
        assertEquals(1, HudOverlayModel.frameFor(ar, right, 0, null, arMetersOverride = 30f, speedKmh = 8.0).chevrons!!.side)
        // Roundabout: no chevrons while approaching. On the ring they point to the exit.
        val rb = Directions(active = true, distance = "50 m", distanceM = 50, turn = "ROUNDABOUT", exitNumber = 3, turnAngle = 270, roundaboutClockwise = false)
        assertNull(HudOverlayModel.frameFor(ar, rb, 0, null, arMetersOverride = 30f).chevrons)
        assertEquals(1, HudOverlayModel.frameFor(ar, rb, 0, null, arMetersOverride = 30f, arProgress = 0.4f).chevrons!!.side)
        assertEquals(-1, HudOverlayModel.frameFor(ar, rb.copy(roundaboutClockwise = true), 0, null, arMetersOverride = 30f, arProgress = 0.4f).chevrons!!.side)
        // Map matched, and the roundabout is not "the next side street": the exit chevron still shows.
        val streets = listOf(
            br.com.redesurftank.havalshisuku.map.LocalRoad(floatArrayOf(0f, -30f, 0f, 40f, 0f, 120f), 1f),
            br.com.redesurftank.havalshisuku.map.LocalRoad(floatArrayOf(0f, 40f, 40f, 40f), 1f)
        )
        val mapped = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR, mapStreets = true)
        val onRing = HudOverlayModel.frameFor(mapped, rb, 0, null, arProgress = 0.4f, streets = streets, speedKmh = 30.0)
        assertTrue(onRing.tbt!!.routeMatched && !onRing.tbt!!.routeClear)
        assertEquals(1, onRing.chevrons!!.side)
        assertNull(HudOverlayModel.frameFor(mapped, rb, 0, null, streets = streets, speedKmh = 30.0).chevrons)
        // Band above the arcs, inside the optics.
        assertTrue(HudOverlayModel.CHEVRON_END_Y - HudOverlayModel.CHEVRON_HALF_H >= 40f)
    }

    @Test
    fun `chevrons travel from the arc's diagonal to the top edge, turning, growing to 4`() {
        // Start (50 m): one, just outside the arc's upper-right diagonal, pointing up-right.
        val (p0, d0, n0) = HudOverlayModel.chevronPose(1, 0f)
        assertEquals(1, n0)
        assertEquals(-45f, d0, 0.01f)
        val diag = HudOverlayModel.arcPoint(HudOverlayModel.CHEVRON_START_DEG_RIGHT, HudOverlayModel.ARC_POWER_R_OUT + HudOverlayModel.CHEVRON_ARC_GAP)
        assertEquals(diag.first, p0.first, 0.01f)
        assertEquals(diag.second, p0.second, 0.01f)
        // At the turn: four, at the top edge out to the side, pointing fully right.
        val (p1, d1, n1) = HudOverlayModel.chevronPose(1, 1f)
        assertEquals(4, n1)
        assertEquals(0f, d1, 0.01f)
        assertEquals(HudOverlayModel.CHEVRON_END_Y, p1.second, 0.01f)
        assertEquals(HudOverlayModel.CHEVRON_CX + HudOverlayModel.CHEVRON_END_DX, p1.first, 0.01f)
        // Left: mirrored, pointing fully left at the end.
        assertEquals(-135f, HudOverlayModel.chevronPose(-1, 0f).second, 0.01f)
        assertEquals(-180f, HudOverlayModel.chevronPose(-1, 1f).second, 0.01f)
        assertTrue(HudOverlayModel.chevronPose(-1, 1f).first.first < HudOverlayModel.CHEVRON_CX)
        // Growing on the way: 1, 2, 3, 4.
        assertEquals(listOf(1, 2, 3, 4), listOf(0.1f, 0.3f, 0.6f, 0.9f).map { HudOverlayModel.chevronPose(1, it).third })
    }

    @Test
    fun `roundabout street line - the exit until on the ring, then the street`() {
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        val rb = Directions(active = true, distance = "80 m", distanceM = 80, turn = "ROUNDABOUT", street = "Av. Brasil", exitNumber = 3, turnAngle = 270, roundaboutClockwise = false)
        assertTrue(!HudOverlayModel.frameFor(ar, rb, 0, null).tbt!!.onRoundabout)
        assertTrue(HudOverlayModel.frameFor(ar, rb, 0, null, onRoundabout = true).tbt!!.onRoundabout)
        // Only roundabouts with an exit.
        assertTrue(!HudOverlayModel.frameFor(ar, rb.copy(turn = "TURN_RIGHT", exitNumber = null), 0, null, onRoundabout = true).tbt!!.onRoundabout)
    }

    @Test
    fun `right placement - same road with a higher horizon, between the lane-assist lines`() {
        val right = HudArArrow.Placement.RIGHT
        // The bottom centre lands between the lines (mock x 388..470); scale is about (CX, BOTTOM).
        assertEquals(right.cx to HudArArrow.BOTTOM, right.map(HudArArrow.CX, HudArArrow.BOTTOM))
        val (x, y) = right.map(HudArArrow.CX + 100f, 150f)
        assertEquals(right.cx + 100f * right.sx, x, 0.01f)
        assertEquals(HudArArrow.BOTTOM - 50f * right.sy, y, 0.01f)
        assertTrue(right.cx in 388f..470f)
        // Its band still starts inside the optics.
        assertTrue(right.map(HudArArrow.CX, right.top).second >= 40f)
        // Turns stay on the panel once mapped.
        for (angle in listOf(90f, -90f, 135f, -135f, 180f, -180f)) {
            val shape = HudArArrow.shape(0f, angle, right)
            for (p in shape.blue.filter { it.y in right.top..HudArArrow.BOTTOM }) {
                val (lx, _) = right.map(p.lx, p.ly)
                val (rx, _) = right.map(p.rx, p.ry)
                assertTrue("angle=$angle l=$lx r=$rx", minOf(lx, rx) >= 0f && maxOf(lx, rx) <= 480f)
            }
        }
        assertEquals(HudArArrow.Placement.CENTER, HudOverlayModel.Config().arPlacement)
        assertEquals(HudArArrow.Placement.RIGHT, HudArArrow.Placement.fromKey("right"))
    }

    @Test
    fun `destination - flag beside the arrow's end, on AA's side`() {
        assertEquals(-1, HudArArrow.destinationSide("DESTINATION_LEFT"))
        assertEquals(1, HudArArrow.destinationSide("DESTINATION_RIGHT"))
        assertEquals(0, HudArArrow.destinationSide("DESTINATION"))
        assertNull(HudArArrow.destinationSide("TURN_RIGHT"))
        val leftShape = HudArArrow.destination(60f, -1)
        val rightShape = HudArArrow.destination(60f, 1)
        val leftFlag = leftShape.flag!!
        val rightFlag = rightShape.flag!!
        assertTrue(leftFlag.x < HudArArrow.CX && rightFlag.x > HudArArrow.CX)
        // Outside the arrow head, not on the centreline of the road. Pennant flies away from it.
        assertTrue(leftFlag.x < leftShape.head[2])
        assertTrue(rightFlag.x > rightShape.head[4])
        assertEquals(-1, leftFlag.pennant)
        assertEquals(1, rightFlag.pennant)
        assertTrue(leftFlag.y in HudArArrow.TOP..HudArArrow.BOTTOM)
        // Closer means bigger and lower.
        val near = HudArArrow.destination(0f, 1).flag!!
        assertTrue(near.height > HudArArrow.destination(300f, 1).flag!!.height)
        assertTrue(near.y > HudArArrow.destination(300f, 1).flag!!.y)
        // The AR model carries the side only for destinations.
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        val arrive = Directions(active = true, distance = "80 m", distanceM = 80, turn = "DESTINATION_RIGHT")
        assertEquals(1, HudOverlayModel.tbtFor(arrive, ar)!!.arDestination)
        assertNull(HudOverlayModel.tbtFor(arrive.copy(turn = "TURN_RIGHT"), ar)!!.arDestination)
    }

    @Test
    fun `chevrons with the map - only when ours is the next street on the turn's side`() {
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR, mapStreets = true)
        val streets = listOf(
            br.com.redesurftank.havalshisuku.map.LocalRoad(floatArrayOf(0f, -30f, 0f, 60f, 0f, 110f, 0f, 300f), 1f),
            br.com.redesurftank.havalshisuku.map.LocalRoad(floatArrayOf(0f, 60f, 80f, 60f), 1f),   // first right, 60 m
            br.com.redesurftank.havalshisuku.map.LocalRoad(floatArrayOf(0f, 110f, 80f, 110f), 1f)  // second right, 110 m
        )
        val right = Directions(active = true, distance = "110 m", distanceM = 110, turn = "TURN_RIGHT")
        // Ours is the second street on the right: the first could be mistaken for it.
        val second = HudOverlayModel.frameFor(ar, right, 0, null, arMetersOverride = 100f, streets = streets)
        assertTrue(second.tbt!!.routeMatched)
        assertNull(second.chevrons)
        // Ours is the first on the right: chevrons from 50 m.
        val first = HudOverlayModel.frameFor(ar, right.copy(distance = "60 m", distanceM = 60), 0, null, arMetersOverride = 45f, streets = streets)
        assertTrue(first.tbt!!.routeClear)
        assertEquals(1, first.chevrons!!.side)
        // The map view is used: a route on the roads.
        assertTrue(first.tbt!!.route != null)
    }

    @Test
    fun `roundabout progress from AA's countdown to the exit`() {
        val t = RoundaboutTracker()
        val step = "35|Av. Brasil|3|270"
        // Approaching: distances fall to the entry, no progress yet.
        for (m in listOf(200f, 120f, 60f, 20f, 5f)) assertTrue(!t.onUpdate(step, m))
        assertTrue(!t.entered)
        // On the ring AA jumps to the way round to the exit: entered.
        assertTrue(t.onUpdate(step, 70f))
        assertEquals(70f, t.startM!!, 0f)
        assertEquals(0f, t.progress(70f), 0f)
        assertEquals(0.5f, t.progress(35f), 0.001f)
        assertEquals(1f, t.progress(0f), 0f)
        // The jump often happens before the last 20 m, and AA renames the street on that sample.
        val early = RoundaboutTracker()
        assertTrue(!early.onUpdate("approach", 180f))
        assertTrue(!early.onUpdate("approach", 40f))
        assertTrue(early.onUpdate("exit-road", 95f))
        assertEquals(95f, early.startM!!, 0f)
        assertEquals(0.5f, early.progress(47.5f), 0.02f)
        // A far next maneuver is not that lap.
        val next = RoundaboutTracker()
        assertTrue(!next.onUpdate("first", 30f))
        assertTrue(!next.onUpdate("second", 300f))
        assertTrue(!next.entered)
        // A new step starts over; other maneuvers never enter.
        assertTrue(!t.onUpdate("36|Next|0|0", 300f))
        assertTrue(!t.entered)
        val other = RoundaboutTracker()
        for (m in listOf(30f, 5f, 80f)) other.onUpdate(null, m)
        assertTrue(!other.entered)
        // A wobble while still far away is not an entry.
        val far = RoundaboutTracker()
        for (m in listOf(300f, 250f, 265f)) far.onUpdate(step, m)
        assertTrue(!far.entered)
        assertEquals(
            HudOverlayModel.roundaboutIdentity(3, 270, false, 35),
            HudOverlayModel.roundaboutIdentity(3, 270, false, 36)
        )
        assertTrue(
            HudOverlayModel.roundaboutIdentity(3, 270, false, 35) !=
                HudOverlayModel.roundaboutIdentity(2, 90, false, 35)
        )
        // Unknown/stale distances are skipped, not taken as 0.
        val stale = RoundaboutTracker()
        stale.onUpdate(step, null)
        assertTrue(!stale.onUpdate(step, 300f))
    }

    @Test
    fun `drive demo - on the roundabout the distance counts down to the exit, as AA does`() {
        val speed = 15f
        val roundaboutAt = 300f / speed + 270f / speed
        val atEntry = HudOverlayModel.DemoDrive.at(roundaboutAt + 13f, speed).directions.distanceM!!
        val onRing = HudOverlayModel.DemoDrive.at(roundaboutAt + 14f, speed).directions.distanceM!!
        assertTrue("entry=$atEntry ring=$onRing", atEntry <= 10 && onRing > 40)
        assertTrue(HudOverlayModel.DemoDrive.at(roundaboutAt + 17.9f, speed).directions.distanceM!! <= 10)
    }

    @Test
    fun `three-digit speeds - wider arcs starting later, with hysteresis`() {
        assertTrue(!HudOverlayModel.wideArcs(99.0, wasWide = false))
        assertTrue(HudOverlayModel.wideArcs(100.0, wasWide = false))
        assertTrue(HudOverlayModel.wideArcs(96.0, wasWide = true))   // stays wide down to 95
        assertTrue(!HudOverlayModel.wideArcs(94.0, wasWide = true))
        // Same 50 ticks and the same end; starts later at the bottom-left (where the "1" sits).
        val last = HudOverlayModel.ARC_TICKS - 1
        assertEquals(HudOverlayModel.arcTickDeg(last), HudOverlayModel.arcTickDeg(last, wide = true), 0.5f)
        assertTrue(HudOverlayModel.arcTickDeg(0, wide = true) > HudOverlayModel.arcTickDeg(0) + 10f)
        // Wider at the sides, same height.
        val (xn, _) = HudOverlayModel.arcPoint(180f, HudOverlayModel.ARC_RPM_R_IN)
        val (xw, _) = HudOverlayModel.arcPoint(180f, HudOverlayModel.ARC_RPM_R_IN, wide = true)
        assertTrue(xw < xn - 2f)
        assertEquals(HudOverlayModel.arcPoint(270f, 56f).second, HudOverlayModel.arcPoint(270f, 56f, wide = true).second, 0.01f)
        // The street line still clears it, a little narrower.
        val narrow = HudOverlayModel.columnRightAt(HudOverlayModel.AR_STREET_BASELINE, HudOverlayModel.AR_STREET_SIZE, wide = true)
        assertTrue(narrow > HudOverlayModel.BOX_CENTER_X + 30f)
        // Frames carry it.
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        assertTrue(HudOverlayModel.frameFor(ar, null, 0, null, wideArcs = true).wideArcs)
    }

    @Test
    fun `chevrons centred on the HUD, map horizon option at the top`() {
        assertEquals(240f, HudOverlayModel.CHEVRON_CX, 0f)
        // Default horizon under the speed; the option puts it at the top of the visible band.
        assertEquals(HudArArrow.MAP_HORIZON_Y, HudArArrow.mapProject(0f, HudArArrow.MAP_FAR_M)!!.second, 0.5f)
        assertEquals(HudArArrow.MAP_HORIZON_TOP_Y, HudArArrow.mapProject(0f, HudArArrow.MAP_FAR_M, HudArArrow.MAP_HORIZON_TOP_Y)!!.second, 0.5f)
        // A taller map: 100 m ahead sits much higher up the HUD.
        assertTrue(HudArArrow.mapProject(0f, 100f, HudArArrow.MAP_HORIZON_TOP_Y)!!.second < HudArArrow.mapProject(0f, 100f)!!.second - 30f)
        assertTrue(HudArArrow.MAP_HORIZON_TOP_Y >= 40f)
        // The map always uses the top horizon now (owner: default, no switch).
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        val turn = Directions(active = true, distance = "90 m", distanceM = 90, turn = "TURN_RIGHT")
        assertEquals(HudArArrow.MAP_HORIZON_TOP_Y, HudOverlayModel.frameFor(ar, turn, 0, null).tbt!!.mapHorizon, 0f)
    }

    @Test
    fun `map on but not loaded - a note instead of the arrow`() {
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR, mapStreets = true)
        val turn = Directions(active = true, distance = "90 m", distanceM = 90, turn = "TURN_RIGHT")
        assertTrue(HudOverlayModel.frameFor(ar, turn, 0, null, mapPending = true).tbt!!.mapPending)
        // Map off, or the feed has the area: no note.
        assertTrue(!HudOverlayModel.frameFor(ar.copy(mapStreets = false), turn, 0, null, mapPending = true).tbt!!.mapPending)
        assertTrue(!HudOverlayModel.frameFor(ar, turn, 0, null, mapPending = false).tbt!!.mapPending)
        // Roundabouts keep their own view.
        val rb = turn.copy(turn = "ROUNDABOUT", exitNumber = 2, turnAngle = 180, roundaboutClockwise = false)
        assertTrue(!HudOverlayModel.frameFor(ar, rb, 0, null, mapPending = true).tbt!!.mapPending)
    }

    @Test
    fun `carregando mapa - 5 s shown, 10 s hidden, round and round`() {
        assertTrue(HudOverlayModel.mapPendingVisible(0L))
        assertTrue(HudOverlayModel.mapPendingVisible(4_999L))
        assertTrue(!HudOverlayModel.mapPendingVisible(5_000L))
        assertTrue(!HudOverlayModel.mapPendingVisible(14_999L))
        assertTrue(HudOverlayModel.mapPendingVisible(15_000L))
        // On the right, inside the panel.
        assertTrue(HudOverlayModel.MAP_PENDING_X > 300f && HudOverlayModel.MAP_PENDING_X < 470f)
    }

    @Test
    fun `last section - no trip totals, in both themes`() {
        val arrive = Directions(active = true, distance = "200 m", distanceM = 200, turn = "DESTINATION_RIGHT", remainingM = 200, remainingS = 40)
        for (layout in listOf(HudOverlayModel.Layout.AR, HudOverlayModel.Layout.NAVIGATION)) {
            val cfg = HudOverlayModel.Config(layout = layout)
            assertEquals("", HudOverlayModel.tbtFor(arrive, cfg)!!.trip)
            assertTrue(HudOverlayModel.tbtFor(arrive.copy(turn = "TURN_RIGHT", remainingM = 4_800, remainingS = 700), cfg)!!.trip.isNotEmpty())
        }
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
        assertEquals(300, start.directions.distanceM)
        assertEquals(28, start.rpmTenths)
        val nearTurn = HudOverlayModel.DemoDrive.at(19f, 15f)
        assertEquals("TURN_RIGHT", nearTurn.directions.turn)
        assertEquals(15, nearTurn.directions.distanceM)
        assertTrue(nearTurn.powerPercent < 0) // regen into the turn, still on Augusta
        // 300 m at 15 m/s is 20 s: the next street starts then, Augusta does not sit at 0 m.
        val handedOff = HudOverlayModel.DemoDrive.at(20f, 15f)
        assertEquals("SHARP_LEFT", handedOff.directions.turn)
        assertEquals(270, handedOff.directions.distanceM)
        val left = HudOverlayModel.DemoDrive.at(25f, 15f)
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
