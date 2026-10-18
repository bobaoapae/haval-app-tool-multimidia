package br.com.redesurftank.havalshisuku.radar

import br.com.redesurftank.havalshisuku.radar.RadarAlertEngine.Event
import br.com.redesurftank.havalshisuku.radar.RadarAlertEngine.Fix
import br.com.redesurftank.havalshisuku.radar.RadarAlertEngine.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

/**
 * Cameras with a road (the normal case) and without one (straight-line fallback). Coordinates
 * are metres north/east of a base point near Rio.
 */
class RadarAlertEngineTest {
    private val baseLat = -22.9
    private val baseLon = -43.1
    private val config = RadarAlertEngine.Config(approachM = 500, nearM = 150)

    private fun at(north: Double, east: Double = 0.0): Pair<Double, Double> =
        (baseLat + north / 111_320.0) to (baseLon + east / (111_320.0 * cos(Math.toRadians(baseLat))))

    private fun camera(north: Double, east: Double = 0.0, limit: Int? = 60, bearing: Float? = null): RadarCamera {
        val (lat, lon) = at(north, east)
        return RadarCamera(lat, lon, limit, bearing, RadarCamera.Source.OSM)
    }

    /** A road through the given (north, east) points, drawn in that order. */
    private fun road(vararg pts: Pair<Double, Double>, oneway: Int = 0): RoadSnippet {
        val ll = pts.map { at(it.first, it.second) }
        return RoadSnippet(oneway, ll.map { it.first }.toDoubleArray(), ll.map { it.second }.toDoubleArray())
    }

    /** A north-south road at x = [east] from -2 km to +2 km (beyond the clip, as on the car the end extends). */
    private fun northRoad(east: Double = 0.0, oneway: Int = 0) = road(-300.0 to east, 1_300.0 to east, oneway = oneway)

    private fun index(cameras: List<RadarCamera>, roads: List<RoadSnippet>) = RadarIndex(cameras, RadarRoads.tie(cameras, roads))

    private class Run(val events: List<Pair<Double, Event>>, val alerts: List<Pair<Double, RadarAlertEngine.Alert?>>)

    /** Drives north along x = [east] from [from] to [to] metres in 15 m steps (1 s each at 54 km/h). */
    private fun drive(
        engine: RadarAlertEngine,
        index: RadarIndex,
        from: Double,
        to: Double,
        east: Double = 0.0,
        bearing: Float = 0f,
        carSpeed: Double = 54.0,
        startMs: Long = 0L
    ): Run {
        val events = ArrayList<Pair<Double, Event>>()
        val alerts = ArrayList<Pair<Double, RadarAlertEngine.Alert?>>()
        var y = from
        var t = startMs
        while (y <= to) {
            val (lat, lon) = at(y, east)
            val u = engine.onFix(Fix(lat, lon, bearing, 15f, t), carSpeed, config, index)
            u.event?.let { events.add(y to it) }
            alerts.add(y to u.alert)
            y += 15.0
            t += 1_000L
        }
        return Run(events, alerts)
    }

    // ---- on the camera's road ----

    @Test
    fun `on the camera's road it alerts at approach then near, then clears once passed`() {
        val index = index(listOf(camera(1_000.0)), listOf(northRoad()))
        val run = drive(RadarAlertEngine(), index, 0.0, 1_200.0)
        assertEquals(listOf(Event.APPROACH, Event.NEAR), run.events.map { it.second })
        val (approachAt, _) = run.events[0]
        val (nearAt, _) = run.events[1]
        // One fix to see it, the next to confirm it is closing in.
        assertTrue("approach within 500 m, got ${1_000 - approachAt}", 1_000 - approachAt in 470.0..500.0)
        assertTrue("near within 150 m, got ${1_000 - nearAt}", 1_000 - nearAt in 135.0..150.0)
        assertNull(run.alerts.last().second)
        val near = run.alerts.first { it.first == nearAt }.second
        assertNotNull(near)
        assertEquals(Phase.NEAR, near!!.phase)
        assertEquals(60, near.limitKmh)
    }

    @Test
    fun `parallel road 60 m away never alerts`() {
        // Camera on the road at x = 60; the car drives up the road at x = 0.
        val index = index(listOf(camera(800.0, east = 60.0)), listOf(northRoad(east = 60.0), northRoad()))
        val run = drive(RadarAlertEngine(), index, 0.0, 1_000.0)
        assertTrue(run.events.isEmpty())
    }

    @Test
    fun `camera on a cross street ahead never alerts`() {
        // Cross street running east-west at y = 500; camera on it just off our road.
        val cross = road(500.0 to -400.0, 500.0 to 400.0)
        val index = index(listOf(camera(500.0, east = 25.0)), listOf(cross, northRoad()))
        val run = drive(RadarAlertEngine(), index, 0.0, 700.0)
        assertTrue(run.events.isEmpty())
    }

    @Test
    fun `one-way road against us never alerts, with us does`() {
        // Drawn north to south and one-way: traffic goes south.
        val southbound = road(1_300.0 to 0.0, -300.0 to 0.0, oneway = 1)
        val against = drive(RadarAlertEngine(), index(listOf(camera(800.0)), listOf(southbound)), 0.0, 1_000.0)
        assertTrue(against.events.isEmpty())
        val northbound = road(-300.0 to 0.0, 1_300.0 to 0.0, oneway = 1)
        val with = drive(RadarAlertEngine(), index(listOf(camera(800.0)), listOf(northbound)), 0.0, 1_000.0)
        assertEquals(Event.APPROACH, with.events.first().second)
    }

    @Test
    fun `two-way road, camera facing the other way never alerts`() {
        val index = index(listOf(camera(800.0, bearing = 180f)), listOf(northRoad()))
        assertTrue(drive(RadarAlertEngine(), index, 0.0, 1_000.0).events.isEmpty())
    }

    @Test
    fun `distance is along the road round a bend`() {
        // Road north 300 m, then east 300 m; camera at the far end.
        val bend = road(-200.0 to 0.0, 300.0 to 0.0, 300.0 to 300.0, 300.0 to 600.0)
        val cam = camera(300.0, east = 300.0)
        val engine = RadarAlertEngine()
        val run = drive(engine, index(listOf(cam), listOf(bend)), 0.0, 120.0)
        val alert = run.alerts.last().second
        assertNotNull(alert)
        // Car at y = 120: 180 m to the corner + 300 m east = 480 m, not the 350 m straight line.
        assertEquals(480.0, alert!!.distanceM.toDouble(), 3.0)
    }

    @Test
    fun `turning off the camera's road drops it`() {
        val index = index(listOf(camera(700.0)), listOf(northRoad()))
        val engine = RadarAlertEngine()
        val run = drive(engine, index, 0.0, 300.0)
        assertEquals(Event.APPROACH, run.events.single().second)
        var east = 0.0
        var t = 21_000L
        var last: RadarAlertEngine.Alert? = run.alerts.last().second
        repeat(5) {
            east += 15.0
            val (lat, lon) = at(300.0, east)
            last = engine.onFix(Fix(lat, lon, 90f, 15f, t), 54.0, config, index).alert
            t += 1_000L
        }
        assertNull(last)
    }

    @Test
    fun `a passed camera does not alert again on the same pass`() {
        val index = index(listOf(camera(600.0)), listOf(northRoad()))
        val engine = RadarAlertEngine()
        drive(engine, index, 0.0, 700.0)
        val again = drive(engine, index, 700.0, 900.0, startMs = 60_000L)
        assertTrue(again.events.isEmpty())
    }

    @Test
    fun `over the limit is flagged, and going over mid-approach fires OVERSPEED once`() {
        val index = index(listOf(camera(1_000.0, limit = 60)), listOf(northRoad()))
        val engine = RadarAlertEngine()
        val slow = drive(engine, index, 0.0, 600.0, carSpeed = 50.0)
        assertEquals(listOf(Event.APPROACH), slow.events.map { it.second })
        assertTrue(slow.alerts.last().second!!.over.not())
        val fast = drive(engine, index, 615.0, 1_100.0, carSpeed = 75.0, startMs = 41_000L)
        assertEquals(listOf(Event.OVERSPEED, Event.NEAR), fast.events.map { it.second })
        assertTrue(fast.alerts.first().second!!.over)
    }

    @Test
    fun `parked on the road nothing new alerts`() {
        val index = index(listOf(camera(300.0)), listOf(northRoad()))
        val engine = RadarAlertEngine()
        val (lat, lon) = at(0.0)
        repeat(5) { assertNull(engine.onFix(Fix(lat, lon, 0f, 0f, it * 1_000L), 0.0, config, index).alert) }
    }

    // ---- no road: straight-line fallback ----

    @Test
    fun `without a road, a camera on our line still alerts`() {
        val index = RadarIndex(listOf(camera(800.0)))
        val run = drive(RadarAlertEngine(), index, 0.0, 1_000.0)
        assertEquals(listOf(Event.APPROACH, Event.NEAR), run.events.map { it.second })
    }

    @Test
    fun `without a road, a camera 40 m to the side never alerts`() {
        val index = RadarIndex(listOf(camera(800.0, east = 40.0)))
        assertTrue(drive(RadarAlertEngine(), index, 0.0, 1_000.0).events.isEmpty())
    }

    @Test
    fun `without a road, a camera behind never alerts`() {
        val index = RadarIndex(listOf(camera(-200.0)))
        assertTrue(drive(RadarAlertEngine(), index, 0.0, 300.0).events.isEmpty())
    }

    @Test
    fun `unknown limit is never over`() {
        val index = index(listOf(camera(700.0, limit = null)), listOf(northRoad()))
        val run = drive(RadarAlertEngine(), index, 0.0, 600.0, carSpeed = 150.0)
        assertTrue(run.events.none { it.second == Event.OVERSPEED })
        assertTrue(run.alerts.last().second!!.over.not())
    }

    @Test
    fun `no GPS course - heading from movement, then the alert after closing in`() {
        val index = index(listOf(camera(400.0)), listOf(northRoad()))
        val engine = RadarAlertEngine()
        val (lat0, lon0) = at(0.0)
        assertNull(engine.onFix(Fix(lat0, lon0, null, 0f, 0L), 0.0, config, index).alert)
        val (lat1, lon1) = at(20.0)
        assertNull(engine.onFix(Fix(lat1, lon1, null, 10f, 1_000L), 36.0, config, index).event)
        val (lat2, lon2) = at(40.0)
        assertEquals(Event.APPROACH, engine.onFix(Fix(lat2, lon2, null, 10f, 2_000L), 36.0, config, index).event)
    }

    @Test
    fun `inside the near distance on first sight alerts NEAR directly`() {
        val index = index(listOf(camera(130.0)), listOf(northRoad()))
        val engine = RadarAlertEngine()
        val (lat0, lon0) = at(0.0)
        engine.onFix(Fix(lat0, lon0, 0f, 15f, 0L), 54.0, config, index)
        val (lat1, lon1) = at(15.0)
        val u = engine.onFix(Fix(lat1, lon1, 0f, 15f, 1_000L), 54.0, config, index)
        assertEquals(Event.NEAR, u.event)
        assertEquals(Phase.NEAR, u.alert!!.phase)
    }
}
