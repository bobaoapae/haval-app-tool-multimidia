package br.com.redesurftank.havalshisuku.map

import br.com.redesurftank.havalshisuku.hud.HudArArrow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OsmRoadsTest {
    @Test
    fun `fixed 0,01 degree tiles round the car`() {
        val tile = OsmRoads.tileOf(-22.906848, -43.110673)
        assertEquals(-2291, tile.y)
        assertEquals(-4312, tile.x)
        assertTrue(-22.906848 in tile.south..tile.north && -43.110673 in tile.west..tile.east)
        val around = OsmRoads.tilesAround(-22.906848, -43.110673, 700.0)
        assertTrue(tile in around)
        assertTrue(around.size in 1..9)
        // The query asks for the tile's box only (no exact position) and drivable roads.
        val q = OsmRoads.query(tile)
        assertTrue(q, q.contains("(-22.9100,-43.1200,-22.9000,-43.1100)"))
        assertTrue(q.contains("residential") && !q.contains("footway"))
    }

    @Test
    fun `one box request split back into tiles, cached and read back`() {
        val a = OsmRoads.Tile(-4311, -2291)
        val b = OsmRoads.Tile(-4310, -2291)
        // One road inside a, one crossing from a into b.
        val inA = OsmRoad(1, "A", "residential", false, doubleArrayOf(a.south + 0.002, a.west + 0.002, a.south + 0.003, a.west + 0.003))
        val across = OsmRoad(2, "X", "primary", true, doubleArrayOf(a.south + 0.005, a.east - 0.001, a.south + 0.005, a.east + 0.001))
        val roads = listOf(inA, across)
        assertEquals(listOf(1L, 2L), OsmRoads.inTile(roads, a).map { it.id })
        assertEquals(listOf(2L), OsmRoads.inTile(roads, b).map { it.id })
        // The cache file holds the tile's roads as an Overpass-shaped answer.
        val back = OsmRoads.parse(OsmRoads.toJson(OsmRoads.inTile(roads, a)))
        assertEquals(2, back.size)
        assertEquals("X", back[1].name)
        assertTrue(back[1].oneway)
        assertEquals("primary", back[1].highway)
        assertEquals(across.latLon.toList(), back[1].latLon.toList())
        // The box query covers both tiles.
        val q = OsmRoads.query(a.south, a.west, b.north, b.east)
        assertTrue(q, q.contains("(-22.9100,-43.1100,-22.9000,-43.0900)"))
    }

    @Test
    fun `a busy server's empty 200 is not an answer, and not cached`() {
        val busy = """{"version":0.6,"osm3s":{},"remark":"runtime error: Query timed out in \"query\" at line 1 after 26 seconds.","elements":[]}"""
        assertTrue(!OsmRoads.answerOk(busy))
        assertTrue(!OsmRoads.cacheOk(busy))
        val good = """{"version":0.6,"osm3s":{},"elements":[]}"""
        assertTrue(OsmRoads.answerOk(good))
        assertTrue(OsmRoads.cacheOk(good)) // an old raw answer without errors (sea: no roads is fine)
        // Our own cache files carry the mark; one without it (written from an unchecked answer) is stale.
        assertTrue(OsmRoads.cacheOk(OsmRoads.toJson(emptyList())))
        assertTrue(!OsmRoads.cacheOk("""{"elements":[]}"""))
        assertTrue(OsmRoads.query(OsmRoads.Tile(0, 0)).contains("[timeout:90]"))
    }

    @Test
    fun `map demo plan - the street ahead to a real junction, then its side street`() {
        val roads = listOf(
            LocalRoad(floatArrayOf(0f, -30f, 0f, 100f, 0f, 220f, 0f, 500f), 1f, "Rua A"),
            LocalRoad(floatArrayOf(0f, 100f, -60f, 100f), 0.55f, "Garagem"),           // a driveway: not taken
            LocalRoad(floatArrayOf(0f, 220f, 150f, 220f), 1f, "Rua B")                 // the side street
        )
        val plan = RoutePath.demoPlan(roads)!!
        assertEquals(220f, plan.junctionM, 1f)
        assertEquals(90f, plan.turnDeg, 1f)
        assertEquals("Rua B", plan.street)
        assertTrue(plan.pts[plan.pts.size - 2] > 50f) // ends along Rua B
        // The street ends in a T at 160 m: the demo turns there.
        val t = RoutePath.demoPlan(listOf(
            LocalRoad(floatArrayOf(0f, -30f, 0f, 160f), 1f, "Rua A"),
            LocalRoad(floatArrayOf(-200f, 160f, 0f, 160f, 200f, 160f), 1f, "Rua T")
        ))!!
        assertEquals(160f, t.junctionM, 1f)
        assertEquals(90f, kotlin.math.abs(t.turnDeg), 1f)
        assertEquals("Rua T", t.street)
        // No junction in range: just the street ahead.
        val straight = RoutePath.demoPlan(listOf(LocalRoad(floatArrayOf(0f, -30f, 0f, 500f), 1f, "Rua A")))!!
        assertEquals(0f, straight.turnDeg, 0f)
    }

    @Test
    fun `parses an Overpass out geom answer`() {
        val json = """
            {"elements":[
              {"type":"way","id":1,"tags":{"highway":"residential","name":"R. A","oneway":"yes"},
               "geometry":[{"lat":-22.9,"lon":-43.1},{"lat":-22.901,"lon":-43.1}]},
              {"type":"way","id":2,"tags":{"highway":"primary"},"geometry":[{"lat":-22.9,"lon":-43.1}]},
              {"type":"node","id":3}
            ]}
        """.trimIndent()
        val roads = OsmRoads.parse(json)
        assertEquals(1, roads.size) // one-point way and the node dropped
        assertEquals("R. A", roads[0].name)
        assertTrue(roads[0].oneway)
        assertEquals(4, roads[0].latLon.size)
    }

    @Test
    fun `roads turn into the car's frame - ahead, right, split, windowed`() {
        val lat = -22.9
        val lon = -43.1
        val dLat = 100.0 / 110_540.0 // 100 m north
        // A road straight north of the car, 0..100 m.
        val north = OsmRoad(1, "N", "residential", false, doubleArrayOf(lat, lon, lat + dLat, lon))
        val headingNorth = OsmRoads.toLocal(listOf(north), lat, lon, 0.0, 900.0, 400.0, 12.0)
        assertEquals(1, headingNorth.size)
        val line = headingNorth[0].pts
        assertEquals(1f, headingNorth[0].width, 0f)
        assertEquals(0f, line[0], 0.01f)
        assertEquals(100f, line[line.size - 1], 0.5f) // ends 100 m ahead
        assertTrue(line.size / 2 >= 9) // split into <= 12 m pieces
        // Heading east, the same road runs off to the left.
        val headingEast = OsmRoads.toLocal(listOf(north), lat, lon, 90.0, 900.0, 400.0, 12.0)[0].pts
        assertEquals(-100f, headingEast[headingEast.size - 2], 0.5f)
        assertEquals(0f, headingEast[headingEast.size - 1], 0.5f)
        // Outside the window: dropped.
        val far = OsmRoad(2, "F", "residential", false, doubleArrayOf(lat + 20 * dLat, lon, lat + 21 * dLat, lon))
        assertTrue(OsmRoads.toLocal(listOf(far), lat, lon, 0.0, 900.0, 400.0, 12.0).isEmpty())
    }

    @Test
    fun `standing still - heading taken along the street under the car`() {
        val lat = -22.9
        val lon = -43.1
        val dLon = 100.0 / (111_320.0 * kotlin.math.cos(Math.toRadians(lat)))
        // An east-west street 5 m north of the car.
        val dLat = 5.0 / 110_540.0
        val street = OsmRoad(1, "E", "residential", false, doubleArrayOf(lat + dLat, lon - dLon, lat + dLat, lon + dLon))
        assertEquals(90.0, OsmRoads.roadBearing(listOf(street), lat, lon, 80.0)!!, 0.5)
        assertEquals(270.0, OsmRoads.roadBearing(listOf(street), lat, lon, 250.0)!!, 0.5)
        // No street close enough: no heading from the map.
        assertTrue(OsmRoads.roadBearing(listOf(street), lat - 0.01, lon, 0.0) == null)
    }

    /** A street grid in the car frame: our road north, cross streets at 100 m (right only) and 200 m. */
    private fun grid(): List<LocalRoad> {
        fun road(vararg xy: Float, w: Float = 1f) = LocalRoad(xy, w)
        return listOf(
            road(0f, -30f, 0f, 100f, 0f, 200f, 0f, 300f),
            road(0f, 100f, 80f, 100f),               // side street to the right at 100 m
            road(-120f, 200f, 0f, 200f, 120f, 200f), // crossroads at 200 m
            road(0f, 150f, -40f, 150f, w = 0.55f)    // a driveway on the left at 150 m
        )
    }

    /** A ring of radius 15 at y 115 entered from below at (0, 100), exits right, ahead and left. */
    private fun ring(): List<LocalRoad> {
        fun road(vararg xy: Float) = LocalRoad(xy, 1f)
        val pts = ArrayList<Float>()
        for (k in 0..24) {
            val th = Math.toRadians(-90.0 + 15.0 * k)
            pts += (15 * kotlin.math.cos(th)).toFloat(); pts += (115 + 15 * kotlin.math.sin(th)).toFloat()
        }
        return listOf(
            road(0f, -30f, 0f, 100f),
            LocalRoad(pts.toFloatArray(), 1f),
            road(15f, 115f, 140f, 115f),   // right (90 deg round a counter-clockwise ring)
            road(0f, 130f, 0f, 260f),      // ahead (180)
            road(-15f, 115f, -140f, 115f)  // left (270)
        )
    }

    @Test
    fun `roundabout - round the ring in the direction of travel and out of AA's exit`() {
        val ahead = RoutePath.roundabout(ring(), 100f, 180f, clockwise = false)!!
        val n = ahead.pts.size / 2
        assertEquals(0f, ahead.pts[2 * n - 2], 1f)
        assertTrue(ahead.pts[2 * n - 1] > 200f)
        // Counter-clockwise (right-hand traffic): round the right-hand side to get there.
        assertTrue((0 until n).any { ahead.pts[2 * it] > 10f })
        assertTrue((0 until n).none { ahead.pts[2 * it] < -10f && ahead.pts[2 * it + 1] in 100f..130f })
        val right = RoutePath.roundabout(ring(), 100f, 90f, clockwise = false)!!
        assertTrue(right.pts[right.pts.size - 2] > 100f)
        val left = RoutePath.roundabout(ring(), 100f, 270f, clockwise = false)!!
        assertTrue(left.pts[left.pts.size - 2] < -100f)
        // Clockwise: the left exit is the first, reached round the left-hand side.
        val cwLeft = RoutePath.roundabout(ring(), 100f, 90f, clockwise = true)!!
        assertTrue(cwLeft.pts[cwLeft.pts.size - 2] < -100f)
        // No ring there: null (the plain route is used).
        assertNull(RoutePath.roundabout(listOf(LocalRoad(floatArrayOf(0f, -30f, 0f, 300f), 1f)), 100f, 180f, false))
    }

    @Test
    fun `roundabout - AA's exit number, the last exit past the map's count, roads that only come in skipped`() {
        // Exits counter-clockwise: right (1st), ahead (2nd), left (3rd).
        val second = RoutePath.roundabout(ring(), 100f, 180f, clockwise = false, exitNumber = 2)!!
        assertEquals(0f, second.pts[second.pts.size - 2], 1f)
        // Charitas, 2026-10-26: AA's number past the map's exits (and its angle off): the last one.
        val past = RoutePath.roundabout(ring(), 100f, 90f, clockwise = false, exitNumber = 9)!!
        assertTrue(past.pts[past.pts.size - 2] < -100f)
        // The right-hand road only comes into the ring: not an exit, so the 1st exit is ahead.
        val roads = ring().toMutableList().also { it[2] = LocalRoad(floatArrayOf(140f, 115f, 15f, 115f), 1f, oneway = true) }
        val first = RoutePath.roundabout(roads, 100f, 180f, clockwise = false, exitNumber = 1)!!
        assertEquals(0f, first.pts[first.pts.size - 2], 1f)
        assertTrue((0 until first.pts.size / 2).none { first.pts[2 * it] > 30f })
    }

    @Test
    fun `on the ring - out of the exit at AA's distance round it`() {
        // A counter-clockwise ring with the car on it heading ahead (centre 15 m to the left).
        val pts = ArrayList<Float>()
        for (k in 0..24) {
            val th = Math.toRadians(15.0 * k)
            pts += (-15 + 15 * kotlin.math.cos(th)).toFloat(); pts += (15 * kotlin.math.sin(th)).toFloat()
        }
        val roads = listOf(
            LocalRoad(pts.toFloatArray(), 1f, ring = true),
            LocalRoad(floatArrayOf(-15f, 15f, -15f, 140f), 1f),   // a quarter round (~24 m)
            LocalRoad(floatArrayOf(-30f, 0f, -150f, 0f), 1f)      // half round (~47 m)
        )
        val top = RoutePath.onRing(roads, 24f, clockwise = false)!!
        assertEquals(-15f, top.pts[top.pts.size - 2], 1f)
        assertTrue(top.pts[top.pts.size - 1] > 100f)
        val left = RoutePath.onRing(roads, 47f, clockwise = false)!!
        assertTrue(left.pts[left.pts.size - 2] < -100f)
        // Nothing near AA's distance: null (the plain route is used).
        assertNull(RoutePath.onRing(roads, 200f, clockwise = false))
    }

    @Test
    fun `route - never out into a one-way street against its traffic`() {
        val roads = listOf(
            LocalRoad(floatArrayOf(0f, -30f, 0f, 100f, 0f, 300f), 1f),
            LocalRoad(floatArrayOf(120f, 100f, 0f, 100f), 1f, oneway = true) // comes in from the right
        )
        val r = RoutePath.route(roads, 100f, 90f)!!
        assertTrue((0 until r.pts.size / 2).none { kotlin.math.abs(r.pts[2 * it + 1] - 100f) < 1f && r.pts[2 * it] > 60f })
        // The same street two-way: taken.
        val two = RoutePath.route(listOf(roads[0], LocalRoad(floatArrayOf(120f, 100f, 0f, 100f), 1f)), 100f, 90f)!!
        assertEquals(120f, two.pts[two.pts.size - 2], 1f)
    }

    @Test
    fun `route - after the turn, the tail stops at the next junction (the way on is unknown)`() {
        fun road(vararg xy: Float, w: Float = 1f) = LocalRoad(xy, w)
        // Right at 100 m onto a street with a cross street 11 m in (Lorde Byron, 2026-10-25).
        val roads = listOf(
            road(0f, -30f, 0f, 100f, 0f, 300f),
            road(0f, 100f, 11f, 100f, 120f, 100f),
            road(11f, 40f, 11f, 100f, 11f, 160f)
        )
        val r = RoutePath.route(roads, 100f, 90f)!!
        val n = r.pts.size / 2
        assertEquals(11f, r.pts[2 * n - 2], 0.5f)
        assertEquals(100f, r.pts[2 * n - 1], 0.5f)
        // No junction after it: the street is followed on as before.
        val open = RoutePath.route(roads.take(2), 100f, 90f)!!
        assertTrue(open.pts[open.pts.size - 2] > 100f)
        // A driveway isn't a junction.
        val drive = RoutePath.route(listOf(roads[0], roads[1], road(11f, 100f, 11f, 140f, w = 0.55f)), 100f, 90f)!!
        assertTrue(drive.pts[drive.pts.size - 2] > 100f)
    }

    @Test
    fun `route - the road under the car to AA's distance, then the matching street`() {
        val r = RoutePath.route(grid(), 200f, 90f)!!
        assertTrue(r.matched)
        val n = r.pts.size / 2
        // Ends on the right-hand cross street at 200 m, past the junction.
        assertEquals(200f, r.pts[2 * n - 1], 0.5f)
        assertTrue(r.pts[2 * n - 2] > 50f)
        // A side street on the right comes first (100 m): not "next on the side" -> no chevrons.
        assertTrue(!r.nextOnSide)
        // Turning left at 200 m: nothing leaves on the left before it (the driveway doesn't count).
        val left = RoutePath.route(grid(), 200f, -90f)!!
        assertTrue(left.pts[left.pts.size - 2] < -50f)
        assertTrue(left.nextOnSide)
        // Right at 100 m: ours is the first on the right.
        assertTrue(RoutePath.route(grid(), 100f, 90f)!!.nextOnSide)
        // No turn: just the road ahead, as far as AA's distance.
        val ahead = RoutePath.route(grid(), 250f, 0f)!!
        assertEquals(250f, ahead.pts[ahead.pts.size - 1], 0.5f)
        // Two ways of one street that don't share a node: the route still crosses the joint.
        val gapped = listOf(
            LocalRoad(floatArrayOf(0f, -20f, 0f, 100f), 1f),
            LocalRoad(floatArrayOf(0f, 112f, 0f, 300f), 1f)
        )
        val across = RoutePath.route(gapped, 250f, 0f)!!
        assertTrue(across.pts[across.pts.size - 1] > 200f)
        // A side-shifted street is drawn leaving the car, straight up the HUD.
        val shifted = HudArArrow.mapScene(emptyList(), floatArrayOf(8f, 0f, 8f, 80f), null)
        val near = shifted.blue.minBy { kotlin.math.abs(it.y - HudArArrow.MAP_NEAR_Y) }
        assertEquals(HudArArrow.CX, near.x, 12f)
        // Off the map: null, and the fallback goes straight then turns.
        assertTrue(RoutePath.route(listOf(LocalRoad(floatArrayOf(300f, 0f, 400f, 0f), 1f)), 100f, 90f) == null)
        val fallback = RoutePath.synthetic(100f, 90f)
        assertTrue(!fallback.matched && fallback.pts[fallback.pts.size - 2] > 50f)
    }

    @Test
    fun `map view - roads as wide as the route, route on top, horizon under the speed`() {
        val roads = grid()
        val route = RoutePath.route(roads, 100f, 90f)!!
        val scene = HudArArrow.mapScene(roads, route.pts, null)
        assertTrue(scene.gray.isNotEmpty() && scene.blue.isNotEmpty())
        assertEquals(6, scene.head.size)
        // A street as wide as the route: 10 m = 120 px at the bottom edge.
        val bottom = scene.blue.minByOrNull { kotlin.math.abs(it.y - HudArArrow.BOTTOM) }!!
        assertEquals(2 * HudArArrow.MAP_HALF_M * HudArArrow.MAP_PX_PER_M, bottom.w, 15f)
        // The horizon lands just under the native speed (baseline ~133); the far path keeps room:
        // 100 m ahead is ~43 % of the way up, 400 m ~82 %.
        assertTrue(HudArArrow.MAP_HORIZON_Y in 134f..140f)
        assertEquals(HudArArrow.MAP_NEAR_Y, HudArArrow.mapProject(0f, 0f)!!.second, 0.01f)
        assertTrue(HudArArrow.MAP_NEAR_Y < HudArArrow.BOTTOM)
        val band = HudArArrow.MAP_NEAR_Y - HudArArrow.MAP_HORIZON_Y
        assertEquals(0.43f, 1f - HudArArrow.mapOffset(100f) / band, 0.02f)
        assertEquals(0.82f, 1f - HudArArrow.mapOffset(400f) / band, 0.03f)
        assertTrue(HudArArrow.mapProject(0f, HudArArrow.MAP_FAR_M + 1f) == null)
        // A straight road ahead stays a straight line to the vanishing point.
        val (x1, y1) = HudArArrow.mapProject(5f, 50f)!!
        val (x2, y2) = HudArArrow.mapProject(5f, 300f)!!
        assertEquals((x1 - HudArArrow.CX) / (y1 - HudArArrow.MAP_HORIZON_Y), (x2 - HudArArrow.CX) / (y2 - HudArArrow.MAP_HORIZON_Y), 0.001f)
        // Arriving on the right: no head (the route ends at the flag), and the pole is outside the
        // drawn street, not in its middle (far away the street is widened).
        val arrive = RoutePath.route(roads, 250f, 0f)!!
        val sceneR = HudArArrow.mapScene(roads, arrive.pts, 1)
        val flag = sceneR.flag!!
        assertTrue(sceneR.head.isEmpty())
        assertTrue(flag.x > sceneR.blue.last().rx)
        assertEquals(1, flag.pennant)
        val sceneL = HudArArrow.mapScene(roads, arrive.pts, -1)
        val left = sceneL.flag!!
        assertTrue(left.x < sceneL.blue.last().lx)
        assertEquals(-1, left.pennant)
        val farRoad = listOf(LocalRoad(floatArrayOf(0f, -20f, 0f, 650f), 1f))
        val farRoute = RoutePath.route(farRoad, 550f, 0f)!!
        val far = HudArArrow.mapScene(farRoad, farRoute.pts, 1, HudArArrow.MAP_HORIZON_TOP_Y)
        assertTrue(far.flag!!.x > far.blue.last().rx)
    }
}
