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
        val line = headingNorth[0]
        assertEquals(0f, line[0], 0.01f)
        assertEquals(100f, line[line.size - 1], 0.5f) // ends 100 m ahead
        assertTrue(line.size / 2 >= 9) // split into <= 12 m pieces
        // Heading east, the same road runs off to the left.
        val headingEast = OsmRoads.toLocal(listOf(north), lat, lon, 90.0, 900.0, 400.0, 12.0)[0]
        assertEquals(-100f, headingEast[headingEast.size - 2], 0.5f)
        assertEquals(0f, headingEast[headingEast.size - 1], 0.5f)
        // Outside the window: dropped.
        val far = OsmRoad(2, "F", "residential", false, doubleArrayOf(lat + 20 * dLat, lon, lat + 21 * dLat, lon))
        assertTrue(OsmRoads.toLocal(listOf(far), lat, lon, 0.0, 900.0, 400.0, 12.0).isEmpty())
    }

    @Test
    fun `map scale puts the real cross street under the arrow's turn`() {
        for (m in listOf(150f, 100f, 50f, 20f)) {
            assertEquals(HudArArrow.cornerDepth(m), m * HudArArrow.mapScale(m), 0.001f)
        }
        assertEquals(HudArArrow.Z_CAP / HudArArrow.BEND_START_M, HudArArrow.mapScale(null), 0f)
        assertEquals(HudArArrow.Z_CAP / HudArArrow.BEND_START_M, HudArArrow.mapScale(900f), 0f)
        assertEquals(1f, HudArArrow.mapScale(0f), 0f) // real scale right at the turn
        // A cross street 60 m ahead, drawn as a thin ribbon in the band.
        val cross = floatArrayOf(-80f, 60f, 0f, 60f, 80f, 60f)
        val ribbons = HudArArrow.streets(listOf(cross), 60f)
        assertEquals(1, ribbons.size)
        assertTrue(ribbons[0].all { it.y in HudArArrow.TOP..HudArArrow.BOTTOM })
    }
}
