package br.com.redesurftank.havalshisuku.map

import br.com.redesurftank.havalshisuku.hud.HudArArrow
import org.junit.Assert.assertEquals
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
        assertEquals(HudArArrow.BOTTOM, HudArArrow.mapProject(0f, 0f)!!.second, 0.01f)
        val band = HudArArrow.BOTTOM - HudArArrow.MAP_HORIZON_Y
        assertEquals(0.43f, 1f - HudArArrow.mapOffset(100f) / band, 0.02f)
        assertEquals(0.82f, 1f - HudArArrow.mapOffset(400f) / band, 0.03f)
        assertTrue(HudArArrow.mapProject(0f, HudArArrow.MAP_FAR_M + 1f) == null)
        // A straight road ahead stays a straight line to the vanishing point.
        val (x1, y1) = HudArArrow.mapProject(5f, 50f)!!
        val (x2, y2) = HudArArrow.mapProject(5f, 300f)!!
        assertEquals((x1 - HudArArrow.CX) / (y1 - HudArArrow.MAP_HORIZON_Y), (x2 - HudArArrow.CX) / (y2 - HudArArrow.MAP_HORIZON_Y), 0.001f)
        // Arriving on the right: a flag right of the route's end.
        val arrive = RoutePath.route(roads, 250f, 0f)!!
        val flag = HudArArrow.mapScene(roads, arrive.pts, 1).flag!!
        assertTrue(flag.x > HudArArrow.CX)
    }
}
