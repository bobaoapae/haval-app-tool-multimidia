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
        // No exit angle: still AR (the map stays), straight on, with the flat roundabout icon.
        val flat = HudOverlayModel.tbtFor(rb.copy(turnAngle = null), ar)!!
        assertEquals(HudOverlayModel.TbtStyle.AR, flat.style)
        assertEquals(0f, flat.arAngle)
        assertTrue(!flat.dynamicRoundabout)
    }

    @Test
    fun `arcs - circles from 140 to 345 degrees round the speed, clear of the column`() {
        val first = HudOverlayModel.arcTickDeg(0)
        val last = HudOverlayModel.arcTickDeg(HudOverlayModel.ARC_TICKS - 1)
        val slot = HudOverlayModel.ARC_SWEEP_DEG / HudOverlayModel.ARC_TICKS
        assertEquals(140f + slot / 2, first, 0.01f)
        assertEquals(345f - slot / 2, last, 0.01f)
        // The RPM line sits just inside the power ticks, clear of them.
        assertTrue(HudOverlayModel.ARC_RPM_R + HudOverlayModel.ARC_RPM_W / 2 < HudOverlayModel.ARC_POWER_R_IN - 1f)
        assertTrue(HudOverlayModel.ARC_CY - HudOverlayModel.ARC_POWER_R_OUT > 40f)
        // Nearly a circle (owner, 2026-10-26: "1:1", then a little wider), centred on the speed.
        assertTrue(HudOverlayModel.ARC_X_SCALE in 1f..1.15f)
        val (left, mid) = HudOverlayModel.arcPoint(180f, HudOverlayModel.ARC_POWER_R_OUT)
        assertEquals(HudOverlayModel.ARC_CX - HudOverlayModel.ARC_POWER_R_OUT * HudOverlayModel.ARC_X_SCALE, left, 0.01f)
        assertEquals(HudOverlayModel.ARC_CY, mid, 0.01f)
        assertEquals(HudOverlayModel.ARC_CY - HudOverlayModel.ARC_POWER_R_OUT, HudOverlayModel.arcPoint(270f, HudOverlayModel.ARC_POWER_R_OUT).second, 0.01f)
        assertEquals(HudOverlayModel.NATIVE_SPEED_CX, HudOverlayModel.ARC_CX, 0f)
        // The circle no longer reaches the street line's rows: it keeps the whole column.
        val right = HudOverlayModel.columnRightAt(HudOverlayModel.AR_STREET_BASELINE, HudOverlayModel.AR_STREET_SIZE)
        assertEquals(HudOverlayModel.BOX_RIGHT, right, 0f)
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
        // A roundabout without its exit stays in AR too (straight on, flat icon), so the map stays.
        assertEquals(HudOverlayModel.TbtStyle.AR, HudOverlayModel.tbtFor(roundabout, ar)!!.style)
        assertTrue(!HudOverlayModel.tbtFor(roundabout, ar)!!.dynamicRoundabout)
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
    fun `one indicators choice for both maneuver visuals`() {
        val turn = Directions(active = true, distance = "90 m", distanceM = 90, turn = "TURN_RIGHT")
        val arcs = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR, gauges = HudOverlayModel.Gauges.ARCS)
        assertTrue(HudOverlayModel.frameFor(arcs, turn, 30, 24).arcs)
        assertTrue(HudOverlayModel.frameFor(arcs.copy(layout = HudOverlayModel.Layout.NAVIGATION), turn, 30, 24).arcs)
        assertTrue(!HudOverlayModel.frameFor(arcs.copy(gauges = HudOverlayModel.Gauges.BARS), turn, 30, 24).arcs)
        // "Nenhum": no power, no RPM, whatever the two switches say.
        val none = HudOverlayModel.frameFor(arcs.copy(gauges = HudOverlayModel.Gauges.NONE), turn, 30, 24)
        assertNull(none.powerPercent)
        assertNull(none.rpmTenths)
    }

    @Test
    fun `styles - Mapa 3D always has the map, Navegação Básica the arrow, Discreto the least`() {
        val map = HudOverlayModel.Preset.MAP.config!!
        assertEquals(HudOverlayModel.Layout.AR, map.layout)
        assertTrue(map.mapStreets && map.arcs)
        val basic = HudOverlayModel.Preset.BASIC.config!!
        assertEquals(HudOverlayModel.Layout.NAVIGATION, basic.layout)
        assertEquals(HudOverlayModel.Gauges.BARS, basic.gauges)
        val discreet = HudOverlayModel.Preset.DISCREET.config!!
        assertEquals(HudOverlayModel.Gauges.NONE, discreet.gauges)
        assertEquals(HudOverlayModel.ColorMode.BLUE, discreet.colors.mode)
        // Distance and time come with navigation (owner, 2026-10-28), Discreet too.
        assertTrue(discreet.trip && discreet.showTurnFromM == 300)
        assertTrue(!HudOverlayModel.Preset.ORIGINAL.config!!.active)
        assertNull(HudOverlayModel.Preset.CUSTOM.config)
        assertEquals(HudOverlayModel.Preset.MAP, HudOverlayModel.Preset.fromKey("map"))
    }

    @Test
    fun `map tilt reshapes the far road, zoom scales the whole map`() {
        val top = HudArArrow.MAP_HORIZON_TOP_Y
        val plain = HudArArrow.MapLens(top)
        // 1, 1 is the original view.
        assertEquals(HudArArrow.mapProject(5f, 200f, top)!!.first, HudArArrow.mapProject(5f, 200f, plain)!!.first, 0.001f)
        // At the car nothing moves.
        val tilted = HudArArrow.MapLens(top, tilt = 4f)
        assertEquals(HudArArrow.MAP_NEAR_Y, HudArArrow.mapProject(0f, 0f, tilted)!!.second, 0.01f)
        assertEquals(HudArArrow.mapPxPerM(0f, plain), HudArArrow.mapPxPerM(0f, tilted), 0.01f)
        // Tilt up: 200 m ahead sits lower (more of the height for the road ahead); down: higher.
        val y = HudArArrow.mapProject(0f, 200f, plain)!!.second
        assertTrue(HudArArrow.mapProject(0f, 200f, HudArArrow.MapLens(top, tilt = 4f))!!.second > y + 10f)
        assertTrue(HudArArrow.mapProject(0f, 200f, HudArArrow.MapLens(top, tilt = 0.4f))!!.second < y - 5f)
        // Zoomed out: the map further away, 200 m ahead where 120 m was, the street narrower;
        // the car still at the bottom; the far end shows further on.
        val far = HudArArrow.MapLens(top, zoom = 0.6f)
        assertEquals(HudArArrow.mapProject(0f, 120f, plain)!!.second, HudArArrow.mapProject(0f, 200f, far)!!.second, 0.01f)
        assertEquals(HudArArrow.mapPxPerM(0f, plain) * 0.6f, HudArArrow.mapPxPerM(0f, far), 0.01f)
        assertEquals(HudArArrow.MAP_NEAR_Y, HudArArrow.mapProject(0f, 0f, far)!!.second, 0.01f)
        assertNull(HudArArrow.mapProject(0f, HudArArrow.MAP_FAR_M + 1f, plain))
        assertTrue(HudArArrow.mapProject(0f, HudArArrow.MAP_FAR_M + 1f, far) != null)
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
        assertTrue(onRing.tbt!!.routeMatched)
        assertEquals(1, onRing.chevrons!!.side)
        assertNull(HudOverlayModel.frameFor(mapped, rb, 0, null, streets = streets, speedKmh = 30.0).chevrons)
        // Band above the arcs, inside the optics.
        assertTrue(HudOverlayModel.CHEVRON_END_Y - HudOverlayModel.CHEVRON_HALF_H >= 40f)
    }

    @Test
    fun `chevrons - the lead travels alone, then a trail grows behind it, four in a row by 10 m`() {
        fun at(m: Float, sharp: Boolean = true, side: Int = 1) =
            HudOverlayModel.chevronStrip(side, sharp, 1f - m / HudOverlayModel.CHEVRON_ANIM_M)
        // 50 m: one, outside the arc's upper-right diagonal, pointing up-right.
        val s0 = at(50f)
        assertEquals(1, s0.size)
        val start = HudOverlayModel.chevronRingPoint(HudOverlayModel.CHEVRON_SHARP_START_DEG, wide = false)
        assertEquals(start.first, s0[0].x, 0.01f)
        assertEquals(start.second, s0[0].y, 0.01f)
        assertEquals(-45f, s0[0].dir, 2f)
        // First 30 m alone: it climbs and turns towards level.
        val s35 = at(35f)
        assertEquals(1, s35.size)
        assertTrue(s35[0].y < start.second && s35[0].dir > -45f && s35[0].dir < 0f)
        val s20 = at(20f)
        assertEquals(1, s20.size)
        assertEquals(HudOverlayModel.CHEVRON_END_Y, s20[0].y, 0.5f)
        assertEquals(0f, s20[0].dir, 0.5f)
        // Then the trail grows behind it, fading to the last one.
        assertEquals(listOf(1, 2, 3, 4), listOf(19.5f, 16f, 12.5f, 10f).map { at(it).size })
        val s15 = at(15f)
        assertTrue(s15.first().appear < s15.last().appear)
        // By 10 m: four, level, in a row on the top edge; at the turn the lead is out to the side.
        val s10 = at(10f)
        assertEquals(4, s10.size)
        assertTrue(s10.all { kotlin.math.abs(it.y - HudOverlayModel.CHEVRON_END_Y) < 0.01f && kotlin.math.abs(it.dir) < 0.01f })
        for (k in 1 until 4) assertEquals(HudOverlayModel.CHEVRON_SPACING, s10[k].x - s10[k - 1].x, 0.5f)
        val end = at(0f)
        assertEquals(4, end.size)
        assertTrue(end.last().x > s10.last().x && end.last().x > 380f)
        // Inside the optics all the way.
        for (m in listOf(50f, 40f, 30f, 20f, 10f, 0f)) for (sharp in listOf(true, false)) for (side in listOf(1, -1)) {
            assertTrue(at(m, sharp, side).all {
                // A chevron's half-height once turned to its direction.
                val rad = Math.toRadians(it.dir.toDouble())
                val half = HudOverlayModel.CHEVRON_HALF_H * (kotlin.math.abs(kotlin.math.sin(rad)) + kotlin.math.abs(kotlin.math.cos(rad))).toFloat()
                it.y - half >= 40f && it.x in 20f..460f
            })
        }
        // Soft: the diagonal all the way, the trail on it too.
        val soft = at(0f, sharp = false)
        assertEquals(4, soft.size)
        assertTrue(soft.all { kotlin.math.abs(it.dir + 45f) < 0.01f })
        assertTrue(soft.last().x > at(50f, sharp = false)[0].x)
        // Left mirrors it.
        assertEquals(180f, kotlin.math.abs(at(0f, side = -1).last().dir), 0.5f)
        assertTrue(at(0f, side = -1).all { it.x < HudOverlayModel.CHEVRON_RING_CX })
    }

    @Test
    fun `chevrons - sharp from 60 degrees, and the always option keeps one still arrow far away`() {
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        val d = Directions(active = true, distance = "40 m", distanceM = 40, turn = "TURN_RIGHT")
        assertTrue(HudOverlayModel.frameFor(ar, d, 0, null, arMetersOverride = 40f).chevrons!!.sharp)
        assertTrue(!HudOverlayModel.frameFor(ar, d.copy(turn = "SLIGHT_RIGHT"), 0, null, arMetersOverride = 40f).chevrons!!.sharp)
        val far = d.copy(distance = "800 m", distanceM = 800)
        assertNull(HudOverlayModel.frameFor(ar, far, 0, null).chevrons)
        val always = HudOverlayModel.frameFor(ar.copy(chevronsAlways = true), far, 0, null).chevrons!!
        assertEquals(1, always.side)
        assertTrue(!always.animate)
        // Still hidden when stopped, on straight-on steps, and while the turn itself is hidden.
        assertNull(HudOverlayModel.frameFor(ar.copy(chevronsAlways = true), far, 0, null, speedKmh = 0.0).chevrons)
        // A straight-on step: Sempre keeps the arrow upright along the road (no side to lean to).
        HudOverlayModel.frameFor(ar.copy(chevronsAlways = true), far.copy(turn = "STRAIGHT"), 0, null).chevrons!!.let { assertTrue(!it.animate && it.lean == 0f) }
        assertNull(HudOverlayModel.frameFor(ar, far.copy(turn = "STRAIGHT"), 0, null, arMetersOverride = 40f).chevrons)
        assertNull(HudOverlayModel.frameFor(ar.copy(chevronsAlways = true, showTurnFromM = 500), far, 0, null).chevrons)
        // "Sempre" (owner, 2026-10-26): shown even while the turn is hidden; needs chevronsAlways too.
        val hidden = HudOverlayModel.frameFor(ar.copy(chevronsAlways = true, chevronsEvenHidden = true, showTurnFromM = 500), far, 0, null).chevrons!!
        assertEquals(1, hidden.side)
        assertTrue(!hidden.animate)
        assertNull(HudOverlayModel.frameFor(ar.copy(chevronsEvenHidden = true, showTurnFromM = 500), far, 0, null).chevrons)
        // It follows the road until close: still straight at 200 m, leaning over from 130 m to 100 m.
        assertEquals(0f, always.lean, 0.001f)
        assertEquals(0f, HudOverlayModel.frameFor(ar.copy(chevronsAlways = true), d, 0, null, arMetersOverride = 200f).chevrons!!.lean, 0.001f)
        assertEquals(0.5f, HudOverlayModel.frameFor(ar.copy(chevronsAlways = true), d, 0, null, arMetersOverride = 115f).chevrons!!.lean, 0.01f)
        assertEquals(1f, HudOverlayModel.frameFor(ar.copy(chevronsAlways = true), d, 0, null, arMetersOverride = 90f).chevrons!!.lean, 0.001f)
        // It travels round the arc: on top pointing up, then to the turn's side, ending where the strip starts.
        val top = HudOverlayModel.chevronCompass(1, true, 0f)
        assertEquals(HudOverlayModel.CHEVRON_RING_CX, top.x, 0.01f) // on top of the arc, round the native speed
        assertEquals(-90f, top.dir, 0.01f)
        val mid = HudOverlayModel.chevronCompass(1, true, 0.5f)
        assertTrue(mid.x > top.x && mid.y > top.y)
        val near = HudOverlayModel.chevronCompass(1, true, 1f)
        val strip = HudOverlayModel.chevronStrip(1, true, 0f)[0]
        assertEquals(strip.x, near.x, 0.01f)
        assertEquals(strip.y, near.y, 0.01f)
        assertEquals(strip.dir, near.dir, 2f)
        assertTrue(HudOverlayModel.chevronCompass(-1, false, 0.5f).x < HudOverlayModel.CHEVRON_RING_CX)
        assertTrue(top.y - HudOverlayModel.CHEVRON_STROKE >= 40f)
    }

    @Test
    fun `Sempre arrow follows the road's bend ahead, then the turn`() {
        // Straight, then bending right: the arrow leans right on top of the arc.
        val straight = floatArrayOf(0f, 0f, 0f, 200f)
        assertEquals(0f, HudOverlayModel.roadBendAhead(straight)!!, 0.5f)
        // A 90 deg corner turns it the most (capped); a soft 15 deg bend shows a quarter of the
        // old 2x (owner, 2026-10-27: only the trend), 7.5.
        val bend = floatArrayOf(0f, 0f, 0f, 30f, 60f, 30f, 120f, 30f)
        assertEquals(HudOverlayModel.CHEVRON_ROAD_MAX_DEG, HudOverlayModel.roadBendAhead(bend)!!, 1f)
        assertEquals(-HudOverlayModel.CHEVRON_ROAD_MAX_DEG, HudOverlayModel.roadBendAhead(floatArrayOf(0f, 0f, 0f, 30f, -60f, 30f, -120f, 30f))!!, 1f)
        val dx = (kotlin.math.sin(Math.toRadians(15.0)) * 100).toFloat()
        val dy = (kotlin.math.cos(Math.toRadians(15.0)) * 100).toFloat()
        val soft = floatArrayOf(0f, 0f, 0f, 20f, dx, 20f + dy)
        assertEquals(15f * HudOverlayModel.CHEVRON_ROAD_GAIN, HudOverlayModel.roadBendAhead(soft)!!, 1f)
        assertNull(HudOverlayModel.roadBendAhead(floatArrayOf(0f, 0f, 0f, 40f)))
        // Never more than the cap.
        assertEquals(HudOverlayModel.CHEVRON_ROAD_MAX_DEG, HudOverlayModel.roadBendAhead(floatArrayOf(0f, 0f, 0f, 30f, 100f, 31f, 200f, 32f))!!, 0.01f)
        val far = HudOverlayModel.chevronCompass(1, true, 0f, roadDeg = 30f)
        assertEquals(-60f, far.dir, 0.01f)
        assertTrue(far.x > HudOverlayModel.CHEVRON_RING_CX)
        // Close to the turn the bend no longer matters: the turn's diagonal.
        val near = HudOverlayModel.chevronCompass(1, true, 1f, roadDeg = -40f)
        assertEquals(HudOverlayModel.chevronCompass(1, true, 1f).x, near.x, 0.01f)
        assertEquals(-45f, near.dir, 1f) // the strip's first chord on its curve
    }

    @Test
    fun `AR keeps its view for any maneuver, and gentle exits get the 45 degree icon`() {
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        val d = Directions(active = true, distance = "300 m", distanceM = 300, turn = "MERGE")
        // A merge with no side, a ferry, an unknown step: AR, straight on (the map stays).
        for (t in listOf("MERGE", "FERRY_BOAT", "")) {
            val tbt = HudOverlayModel.tbtFor(d.copy(turn = t), ar)!!
            assertEquals(t, HudOverlayModel.TbtStyle.AR, tbt.style)
            assertEquals(0f, tbt.arAngle)
        }
        // No distance yet (AA sends the step before its distance): still AR.
        assertEquals(HudOverlayModel.TbtStyle.AR, HudOverlayModel.tbtFor(d.copy(turn = "TURN_RIGHT", distance = "", distanceM = null), ar)!!.style)
        // Gentle maneuvers: the 45 degree arrow to their side, not the fork glyph or the 90 degree turn.
        for ((t, icon) in listOf(
            "SLIGHT_RIGHT" to "SLIGHT_RIGHT", "KEEP_LEFT" to "SLIGHT_LEFT", "FORK_RIGHT" to "SLIGHT_RIGHT",
            "OFF_RAMP_LEFT" to "SLIGHT_LEFT", "TURN_RIGHT" to "TURN_RIGHT", "ROUNDABOUT" to "ROUNDABOUT", "MERGE" to "MERGE"
        )) assertEquals(t, icon, HudOverlayModel.hudIcon(t))
    }

    @Test
    fun `soft turns animate at their own angle, read from the map when it has the turn`() {
        // The soft strip points at the turn's angle (from straight ahead), inside the band.
        for (deg in listOf(30f, 45f, 55f)) {
            val end = HudOverlayModel.chevronStrip(1, false, 1f, deg)
            assertTrue(end.all { kotlin.math.abs(it.dir - (-90f + deg)) < 0.5f })
            assertTrue(end.all { it.y >= HudOverlayModel.CHEVRON_SOFT_TOP_Y - 0.5f })
            assertEquals(4, end.size)
            // The still/Sempre arrow ends exactly where and how the strip starts.
            val start = HudOverlayModel.chevronStrip(1, false, 0f, deg)[0]
            val compass = HudOverlayModel.chevronCompass(1, false, 1f, turnDeg = deg)
            assertEquals(start.dir, compass.dir, 0.01f)
            assertEquals(start.x, compass.x, 0.01f)
        }
        // The map's turn: a route bending 40 deg right at 80 m.
        val rad = Math.toRadians(40.0)
        val pts = ArrayList<Float>()
        for (s in 0..80 step 10) { pts += 0f; pts += s.toFloat() }
        for (s in 10..140 step 10) { pts += (s * kotlin.math.sin(rad)).toFloat(); pts += 80f + (s * kotlin.math.cos(rad)).toFloat() }
        val route = pts.toFloatArray()
        assertEquals(40f, HudOverlayModel.routeTurnAt(route, 80f)!!, 1f)
        assertNull(HudOverlayModel.routeTurnAt(floatArrayOf(0f, 0f, 0f, 60f), 50f))
        // AA says TURN_RIGHT (90), the map says 40: a soft strip at 40.
        val tbt = HudOverlayModel.Tbt(icon = "TURN_RIGHT", distanceValue = "40", distanceUnit = "m", lanes = emptyList(),
            style = HudOverlayModel.TbtStyle.AR, trip = "", route = route, routeMatched = true, routeClear = true,
            guideMeters = 80f, guideAngle = 90f)
        val ch = HudOverlayModel.chevronsFor(tbt, 30.0)!!
        assertTrue(!ch.sharp)
        assertEquals(40f, ch.turnDeg, 1f)
        // The map on the other side, or not matched: AA's own angle.
        assertTrue(HudOverlayModel.chevronsFor(tbt.copy(guideAngle = -90f), 30.0)!!.sharp)
        assertTrue(HudOverlayModel.chevronsFor(tbt.copy(routeMatched = false), 30.0)!!.sharp)
    }

    @Test
    fun `chevrons on a roundabout - never entering it, only near the exit`() {
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR, chevronsAlways = true)
        val rb = Directions(active = true, distance = "120 m", distanceM = 120, turn = "ROUNDABOUT", exitNumber = 3, turnAngle = 270, roundaboutClockwise = false)
        // Approaching, and just on the ring: no side shown; with Sempre only the still arrow
        // following the road, upright (owner, 2026-10-27: it vanished there).
        fun upright(c: HudOverlayModel.Chevrons?) = c != null && !c.animate && c.lean == 0f
        assertTrue(upright(HudOverlayModel.frameFor(ar, rb, 0, null, arMetersOverride = 40f).chevrons))
        assertNull(HudOverlayModel.frameFor(ar.copy(chevronsAlways = false), rb, 0, null, arMetersOverride = 40f).chevrons)
        // On the ring the chevrons follow AA's own distance to the exit.
        fun left(m: Int) = rb.copy(distance = "$m m", distanceM = m)
        assertTrue(upright(HudOverlayModel.frameFor(ar, left(120), 0, null, arProgress = 0.1f).chevrons))
        // A small ring: within 50 m of the exit at once, but not before the first part of the lap.
        assertTrue(upright(HudOverlayModel.frameFor(ar, left(45), 0, null, arProgress = 0.1f).chevrons))
        // Further round, near the exit: they show the way out.
        val exit = HudOverlayModel.frameFor(ar, left(30), 0, null, arProgress = 0.5f).chevrons!!
        assertEquals(1, exit.side)
        assertTrue(exit.animate)
        assertTrue(upright(HudOverlayModel.frameFor(ar, left(80), 0, null, arProgress = 0.5f).chevrons))
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
        // No side from AA: the right curb, not the middle of the road.
        assertEquals(1, HudArArrow.destinationSide("DESTINATION"))
        assertNull(HudArArrow.destinationSide("TURN_RIGHT"))
        val leftShape = HudArArrow.destination(60f, -1)
        val rightShape = HudArArrow.destination(60f, 1)
        val leftFlag = leftShape.flag!!
        val rightFlag = rightShape.flag!!
        assertTrue(leftFlag.x < HudArArrow.CX && rightFlag.x > HudArArrow.CX)
        // No head on the last stretch: the route ends at the flag, which stands outside the road's
        // own edge, not on its centreline. Pennant flies away from it.
        assertTrue(leftShape.head.isEmpty() && rightShape.head.isEmpty())
        assertTrue(leftFlag.x < leftShape.blue.last().lx && leftFlag.x < leftShape.blue.last().rx)
        assertTrue(rightFlag.x > rightShape.blue.last().rx && rightFlag.x > rightShape.blue.last().lx)
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
    fun `roundabout - the 2026-10-22 drive replayed (approach to the entry, jump, lap, next leg)`() {
        val t = RoundaboutTracker()
        val step = HudOverlayModel.roundaboutIdentity(6, 315, false, 35)
        // AA's distance is to the entry while approaching: no lap yet, however close.
        for (m in listOf(100f, 82f, 59f, 38f, 16f, 7f, 2f)) {
            assertTrue(!t.onUpdate(step, m))
            assertEquals(0f, t.progress(m), 0f)
        }
        // On the ring: the 157 m lap (identity kept, the exit pair is held across ROUNDABOUT_EXIT).
        assertTrue(t.onUpdate(step, 157f))
        assertEquals(0.5f, t.progress(78.5f), 0.01f)
        assertEquals(1f, t.progress(5f), 0.05f)
        // AA's last sample of this step carries the next leg (437 m): the exit is done, not 0.
        t.onUpdate(step, 437f)
        assertEquals(1f, t.progress(437f), 0f)
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
    fun `three-digit speeds - wider arcs ending earlier on the right, with hysteresis`() {
        assertTrue(!HudOverlayModel.wideArcs(99.0, wasWide = false))
        assertTrue(HudOverlayModel.wideArcs(100.0, wasWide = false))
        assertTrue(HudOverlayModel.wideArcs(96.0, wasWide = true))   // stays wide down to 95
        assertTrue(!HudOverlayModel.wideArcs(94.0, wasWide = true))
        // A bigger circle (2026-10-26), its top at the HUD's top edge, ticks as long as before.
        val last = HudOverlayModel.ARC_TICKS - 1
        val o = HudOverlayModel.ARC_POWER_R_OUT
        val (topX, topY) = HudOverlayModel.arcPoint(270f, o, wide = true)
        assertEquals(HudOverlayModel.ARC_WIDE_CX, topX, 0.01f)
        assertEquals(40f, topY, 0.01f)
        val (lx, _) = HudOverlayModel.arcPoint(180f, o, wide = true)
        assertEquals(HudOverlayModel.ARC_WIDE_CX - HudOverlayModel.ARC_WIDE_R_OUT * HudOverlayModel.ARC_X_SCALE, lx, 0.01f)
        val tick = { w: Boolean -> HudOverlayModel.arcPoint(200f, HudOverlayModel.ARC_POWER_R_IN, w).let { (x, y) ->
            HudOverlayModel.arcPoint(200f, o, w).let { (x2, y2) -> kotlin.math.hypot(x2 - x, y2 - y) } } }
        assertEquals(tick(false), tick(true), 0.01f)
        // Its right end ends above the native "km/h" (its top ~y 121 on a 3-digit speed).
        val (_, endY) = HudOverlayModel.arcPoint(HudOverlayModel.arcTickDeg(last), o, wide = true)
        assertTrue("end y=$endY", endY < 110f)
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
    fun `carregando mapa - steady on the right, with its loading share`() {
        // On the right, inside the panel, the bar under the note and above the band's bottom.
        assertTrue(HudOverlayModel.MAP_PENDING_X > 300f && HudOverlayModel.MAP_PENDING_X < 470f)
        assertTrue(HudOverlayModel.MAP_PENDING_X + HudOverlayModel.MAP_PENDING_BAR_W / 2 < 480f)
        assertTrue(HudOverlayModel.MAP_PENDING_BAR_Y > HudOverlayModel.MAP_PENDING_Y && HudOverlayModel.MAP_PENDING_BAR_Y + 2f <= 200f)
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR, mapStreets = true)
        val turn = Directions(active = true, distance = "300 m", distanceM = 300, turn = "TURN_RIGHT")
        assertEquals(0.4f, HudOverlayModel.frameFor(ar, turn, 0, null, mapPending = true, mapProgress = 0.4f).tbt!!.mapProgress, 0f)
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
    fun `rpm arc - 7 segments of 1000 rpm, filling continuously, a sliver while turning`() {
        assertEquals(0f, HudOverlayModel.rpmArcLit(0), 0f)
        assertTrue(HudOverlayModel.rpmArcLit(1) >= 0.02f) // 100 rpm still shows
        assertEquals(0.5f, HudOverlayModel.rpmArcLit(35), 0.001f)
        assertEquals(1f, HudOverlayModel.rpmArcLit(75), 0f)
        for (wide in listOf(false, true)) {
            val all = HudOverlayModel.rpmSegments(wide)
            assertEquals(HudOverlayModel.ARC_RPM_SEGMENTS, all.size)
            assertEquals(HudOverlayModel.ARC_START_DEG, all.first().first, 0.01f)
            assertEquals(HudOverlayModel.ARC_START_DEG + HudOverlayModel.ARC_SWEEP_DEG, all.last().first + all.last().second, 0.01f)
            // The same gap in pixels on both circles.
            val gapDeg = all[1].first - (all[0].first + all[0].second)
            val r = HudOverlayModel.arcRadius(HudOverlayModel.ARC_RPM_R, wide)
            assertEquals(HudOverlayModel.ARC_RPM_GAP_PX, Math.toRadians(gapDeg.toDouble()).toFloat() * r, 0.01f)
            // 3500 rpm: three whole segments and half the fourth.
            val half = HudOverlayModel.rpmSegments(wide, HudOverlayModel.rpmArcLit(35))
            assertEquals(4, half.size)
            assertEquals(all[3].second / 2, half[3].second, 0.01f)
        }
    }

    @Test
    fun `colours - defaults are the old ones, Azul overrides every pick, frames carry them`() {
        val d = HudOverlayModel.Colors()
        assertEquals(HudOverlayModel.rgb(63, 169, 255), d.drive)
        assertEquals(HudOverlayModel.rgb(80, 230, 120), d.regen)
        assertEquals(HudOverlayModel.rgb(255, 64, 64), d.rpm)
        // The default route keeps the original light-to-deep blue and chevron blue.
        assertEquals(HudOverlayModel.rgb(120, 200, 255), HudOverlayModel.routeNear(d.route))
        assertEquals(HudOverlayModel.rgb(90, 180, 255), d.chevron)
        val picked = d.copy(drive = HudOverlayModel.rgb(255, 150, 50), text = HudOverlayModel.rgb(255, 214, 64))
        assertEquals(picked, picked.resolved())
        assertEquals(HudOverlayModel.BLUE_COLORS, picked.copy(mode = HudOverlayModel.ColorMode.BLUE).resolved())
        assertEquals(HudOverlayModel.rgb(127, 127, 127), HudOverlayModel.mixColor(HudOverlayModel.rgb(0, 0, 0), HudOverlayModel.rgb(255, 255, 255), 0.5f))
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR, colors = picked.copy(mode = HudOverlayModel.ColorMode.BLUE))
        assertEquals(HudOverlayModel.BLUE_COLORS, HudOverlayModel.frameFor(ar, null, 0, null).colors)
        // The swatches hold every default, so each part can go back to it.
        val swatches = HudOverlayModel.COLOR_SWATCHES.map { it.second }
        for (c in listOf(d.drive, d.regen, d.rpm, d.street, d.route, d.text, d.chevron)) assertTrue(swatches.any { HudOverlayModel.sameRgb(it, c) })
        // Opacity rides in the alpha byte: all solid by default but the streets (as they always looked).
        assertEquals(1f, HudOverlayModel.opacityOf(d.text), 0.01f)
        assertEquals(HudOverlayModel.STREET_DEFAULT_OPACITY, HudOverlayModel.opacityOf(d.street), 0.01f)
        assertEquals(0.1f, HudOverlayModel.opacityOf(HudOverlayModel.withOpacity(d.text, 0f)), 0.01f)
        // Azul changes the colour only; each part keeps its opacity.
        val faint = d.copy(text = HudOverlayModel.withOpacity(d.text, 0.5f), mode = HudOverlayModel.ColorMode.BLUE).resolved()
        assertEquals(0.5f, HudOverlayModel.opacityOf(faint.text), 0.01f)
        assertTrue(HudOverlayModel.sameRgb(faint.text, HudOverlayModel.COLOR_DRIVE))
    }

    @Test
    fun `map - the street under the route is as wide as the blue, other streets keep their class`() {
        val under = br.com.redesurftank.havalshisuku.map.LocalRoad(floatArrayOf(0f, -30f, 0f, 500f), 1.3f)
        val beside = br.com.redesurftank.havalshisuku.map.LocalRoad(floatArrayOf(60f, -30f, 60f, 500f), 1.3f)
        val shape = HudArArrow.mapScene(listOf(under, beside), floatArrayOf(0f, 0f, 0f, 400f), null)
        // Widths at the same screen row (the same depth).
        val row = shape.blue[shape.blue.size / 2].let { (it.ly + it.ry) / 2 }
        fun width(r: List<HudArArrow.Pt>) = r.minBy { kotlin.math.abs((it.ly + it.ry) / 2 - row) }.let { kotlin.math.hypot(it.rx - it.lx, it.ry - it.ly) }
        val blue = width(shape.blue)
        assertEquals(blue, width(shape.gray[0]), blue * 0.1f)
        assertTrue(width(shape.gray[1]) > blue * 1.15f)
    }

    @Test
    fun `a new step before its distance shows no number (not the last step's 0 m)`() {
        val d = br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry.Directions(
            active = true, distance = "0 m", distanceM = 0, turn = "TURN_LEFT", street = "R. Castilho França", remainingM = 3722, remainingS = 600
        )
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        val stale = HudOverlayModel.frameFor(ar, d, 0, null, arDistanceKnown = false).tbt!!
        assertEquals("", stale.distanceValue)
        assertTrue(!stale.turnHidden)
        assertEquals("0", HudOverlayModel.frameFor(ar, d, 0, null).tbt!!.distanceValue)
    }

    @Test
    fun `the trip's first step (depart, always 0 m) shows only the trip line`() {
        val d = br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry.Directions(
            active = true, distance = "0 m", distanceM = 0, turn = "DEPART", street = "em direção a Av. Roberto Silveira", remainingM = 5800, remainingS = 1080
        )
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        val tbt = HudOverlayModel.frameFor(ar, d, 0, null).tbt!!
        assertTrue(tbt.turnHidden)
        assertTrue(tbt.trip.isNotEmpty())
        assertTrue(!HudOverlayModel.frameFor(ar, d.copy(turn = "TURN_LEFT", distance = "60 m", distanceM = 60), 0, null).tbt!!.turnHidden)
    }

    @Test
    fun `map - a straight-on maneuver draws the road ahead, a destination still ends there`() {
        // São Francisco tunnel, 2026-10-26: a name change / merge 40 m ahead drew a 40 m stub.
        val road = listOf(br.com.redesurftank.havalshisuku.map.LocalRoad(floatArrayOf(0f, -30f, 0f, 700f), 1f))
        val map = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR, mapStreets = true)
        fun end(turn: String): Float {
            val d = br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry.Directions(
                active = true, distance = "40 m", distanceM = 40, turn = turn, street = "Túnel Roberto Silveira", remainingM = 4800, remainingS = 720
            )
            val route = HudOverlayModel.frameFor(map, d, 0, null, streets = road, speedKmh = 40.0).tbt!!.route!!
            return route[route.size - 1]
        }
        assertTrue(end("NAME_CHANGE") > 300f)
        assertTrue(end("MERGE") > 300f)
        assertEquals(40f, end("DESTINATION"), 2f)
    }

    @Test
    fun `three-digit chevrons go round the big circle and stay in the HUD`() {
        // "Sempre" far away: on top of the two-digit arc, at the top edge's limit on the big one.
        val small = HudOverlayModel.chevronCompass(1, false, 0f)
        assertEquals(HudOverlayModel.CHEVRON_RING_CX, small.x, 0.01f)
        val big = HudOverlayModel.chevronCompass(1, false, 0f, wide = true)
        assertEquals(HudOverlayModel.CHEVRON_RING_MIN_Y, big.y, 0.01f)
        assertTrue(big.x > HudOverlayModel.ARC_WIDE_CX)
        assertTrue(HudOverlayModel.chevronCompass(-1, false, 0f, wide = true).x < HudOverlayModel.ARC_WIDE_CX)
        // The strip starts outside the big circle.
        for (sharp in listOf(true, false)) {
            val m = HudOverlayModel.chevronStrip(1, sharp, 0f, wide = true)[0]
            val d = kotlin.math.hypot((m.x - HudOverlayModel.ARC_WIDE_CX) / HudOverlayModel.ARC_X_SCALE, m.y - HudOverlayModel.ARC_WIDE_CY)
            assertTrue("d=$d", d > HudOverlayModel.ARC_WIDE_R_OUT + HudOverlayModel.CHEVRON_HALF_H)
        }
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
