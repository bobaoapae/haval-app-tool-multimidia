package br.com.redesurftank.havalshisuku.radar

import br.com.redesurftank.havalshisuku.radar.RadarAlertEngine.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class RadarRoadsTest {
    /** Road running north from -22.9010 to -22.8990 at lon -43.1 (~222 m), plus an east-west one. */
    private val overpass = """
        {"version":0.6,"elements":[
          {"type":"way","id":10,"bounds":{},"nodes":[1,2,3],
           "geometry":[{"lat":-22.9010,"lon":-43.1000},{"lat":-22.9000,"lon":-43.1000},{"lat":-22.8990,"lon":-43.1000}]},
          {"type":"way","id":11,"nodes":[4,5],
           "geometry":[{"lat":-22.9000,"lon":-43.1010},{"lat":-22.9000,"lon":-43.0990}]},
          {"type":"way","id":10,"tags":{"oneway":"yes"}}
        ]}
    """.trimIndent()

    @Test
    fun `snippets keep each camera's nearest road, with its oneway`() {
        // Camera 5 m east of the north road, between the junction and the north end.
        val cam = RadarCamera(-22.8995, -43.09995, 60, null, RadarCamera.Source.OSM)
        val snippets = RadarRoads.buildSnippets({ StringReader(overpass) }, listOf(cam))
        assertEquals(1, snippets.size)
        assertEquals(1, snippets[0].oneway)
        assertEquals(-22.9010, snippets[0].lat.first(), 1e-9)
        // Tied: one-way drawn northwards = enforces northbound.
        val tied = RadarRoads.tie(listOf(cam), snippets)
        assertNotNull(tied[0])
        assertEquals(1, tied[0]!!.allowed)
        assertEquals(167.0, tied[0]!!.sCamera, 3.0)
    }

    @Test
    fun `a camera far from every road gets none`() {
        val cam = RadarCamera(-22.95, -43.15, 60, null, RadarCamera.Source.OSM)
        assertTrue(RadarRoads.buildSnippets({ StringReader(overpass) }, listOf(cam)).isEmpty())
        assertNull(RadarRoads.tie(listOf(cam), emptyList())[0])
    }

    @Test
    fun `two-way road takes the camera's own direction, else both ways`() {
        val road = RoadSnippet(0, doubleArrayOf(-22.9010, -22.8990), doubleArrayOf(-43.1, -43.1))
        val south = RadarCamera(-22.9, -43.1, 60, 180f, RadarCamera.Source.OSM)
        val none = RadarCamera(-22.9, -43.1, 60, null, RadarCamera.Source.OSM)
        val tied = RadarRoads.tie(listOf(south, none), listOf(road))
        assertEquals(-1, tied[0]!!.allowed)
        assertEquals(0, tied[1]!!.allowed)
    }

    @Test
    fun `projection extends past the ends of the stretch`() {
        val road = RoadSnippet(0, doubleArrayOf(-22.9010, -22.8990), doubleArrayOf(-43.1, -43.1))
        // 1 km south of the start, on the road's line.
        val p = road.project(-22.9010 - 1_000 / 111_320.0, -43.1)
        assertEquals(0.0, p.perpM, 0.5)
        assertEquals(-1_000.0, p.s, 1.0)
        assertEquals(1_000.0, p.beyondM, 1.0)
        assertEquals(0f, p.dirDeg, 0.5f)
    }

    @Test
    fun `roads file round-trips, gzipped or plain`() {
        val sn = RoadSnippet(-1, doubleArrayOf(-22.90001, -22.89901), doubleArrayOf(-43.10002, -43.10003))
        val bytes = RadarRoads.encode(RadarRoads.Contents("2026-10-18T00:00:00Z", listOf(sn)))
        for (input in listOf(bytes, java.util.zip.GZIPInputStream(bytes.inputStream()).readBytes())) {
            val back = RadarRoads.decode(input.inputStream())!!
            assertEquals("2026-10-18T00:00:00Z", back.updated)
            assertEquals(-1, back.snippets[0].oneway)
            assertEquals(-22.89901, back.snippets[0].lat[1], 1e-9)
        }
    }

    @Test
    fun `roads query asks around the extra points too`() {
        val q = RadarRoads.overpassQuery(listOf(-21.5 to -41.25))
        assertTrue(q.contains("way(around.c:25)"))
        assertTrue(q.contains("way(around:25,-21.500000,-41.250000)"))
        assertTrue(q.contains("out skel geom"))
    }

    // ---- turn-by-turn priority and the sound gate ----

    @Test
    fun `a maneuver before the camera keeps the slot, after it the camera takes it`() {
        val nav = RadarPriority.parseNav("""{"v":1,"active":true,"distance":"200 m","distance_m":200,"turn":"TURN_RIGHT"}""")
        assertTrue(RadarPriority.tbtFirst(nav, 450))
        assertFalse(RadarPriority.tbtFirst(nav, 150))
        assertFalse(RadarPriority.tbtFirst(RadarPriority.parseNav("""{"v":1,"active":false}"""), 450))
        // No distance from AA: can't tell, the camera shows.
        assertFalse(RadarPriority.tbtFirst(RadarPriority.parseNav("""{"active":true,"distance_m":null}"""), 450))
        assertFalse(RadarPriority.tbtFirst(RadarPriority.parseNav("garbage"), 450))
    }

    @Test
    fun `sound waits while the maneuver comes first, then plays the strongest once`() {
        val gate = RadarSoundGate()
        assertNull(gate.onUpdate(Event.APPROACH, targeted = true, shown = false))
        assertNull(gate.onUpdate(null, targeted = true, shown = false))
        assertNull(gate.onUpdate(Event.NEAR, targeted = true, shown = false))
        // The maneuver is done: the camera shows, the held near sound plays once.
        assertEquals(Event.NEAR, gate.onUpdate(null, targeted = true, shown = true))
        assertNull(gate.onUpdate(null, targeted = true, shown = true))
    }

    @Test
    fun `held sound is forgotten when the camera goes away (route turned off)`() {
        val gate = RadarSoundGate()
        assertNull(gate.onUpdate(Event.APPROACH, targeted = true, shown = false))
        assertNull(gate.onUpdate(null, targeted = false, shown = false))
        assertNull(gate.onUpdate(null, targeted = true, shown = true))
    }

    @Test
    fun `shown camera plays its events straight away`() {
        val gate = RadarSoundGate()
        assertEquals(Event.APPROACH, gate.onUpdate(Event.APPROACH, targeted = true, shown = true))
        assertEquals(Event.OVERSPEED, gate.onUpdate(Event.OVERSPEED, targeted = true, shown = true))
    }
}
