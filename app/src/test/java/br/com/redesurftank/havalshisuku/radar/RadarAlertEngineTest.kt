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

class RadarAlertEngineTest {
    private val baseLat = -22.9
    private val baseLon = -43.1
    private val config = RadarAlertEngine.Config(approachM = 500, nearM = 150)

    /** Point [north] / [east] metres from the base. */
    private fun at(north: Double, east: Double = 0.0): Pair<Double, Double> =
        (baseLat + north / 111_320.0) to (baseLon + east / (111_320.0 * cos(Math.toRadians(baseLat))))

    private fun camera(north: Double, east: Double = 0.0, limit: Int? = 60, bearing: Float? = null): RadarCamera {
        val (lat, lon) = at(north, east)
        return RadarCamera(lat, lon, limit, bearing, RadarCamera.Source.OSM)
    }

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

    @Test
    fun `camera straight ahead alerts at approach then near, then clears once passed`() {
        val index = RadarIndex(listOf(camera(1_000.0)))
        val engine = RadarAlertEngine()
        val run = drive(engine, index, 0.0, 1_200.0)
        assertEquals(listOf(Event.APPROACH, Event.NEAR), run.events.map { it.second })
        val (approachAt, _) = run.events[0]
        val (nearAt, _) = run.events[1]
        assertTrue("approach within 500 m, got ${1_000 - approachAt}", 1_000 - approachAt in 485.0..500.0)
        assertTrue("near within 150 m, got ${1_000 - nearAt}", 1_000 - nearAt in 135.0..150.0)
        // Past the camera by more than the growth margin: no alert any more.
        assertNull(run.alerts.last().second)
        val near = run.alerts.first { it.first == nearAt }.second
        assertNotNull(near)
        assertEquals(Phase.NEAR, near!!.phase)
        assertEquals(60, near.limitKmh)
    }

    @Test
    fun `a passed camera does not alert again on the same pass`() {
        val index = RadarIndex(listOf(camera(600.0)))
        val engine = RadarAlertEngine()
        drive(engine, index, 0.0, 700.0)
        // Keep heading north just past it: still nothing new.
        val again = drive(engine, index, 700.0, 900.0, startMs = 60_000L)
        assertTrue(again.events.isEmpty())
    }

    @Test
    fun `camera enforcing the other direction never alerts`() {
        val index = RadarIndex(listOf(camera(800.0, bearing = 180f)))
        val run = drive(RadarAlertEngine(), index, 0.0, 1_000.0)
        assertTrue(run.events.isEmpty())
    }

    @Test
    fun `camera enforcing our direction alerts`() {
        val index = RadarIndex(listOf(camera(800.0, bearing = 10f)))
        val run = drive(RadarAlertEngine(), index, 0.0, 1_000.0)
        assertEquals(Event.APPROACH, run.events.first().second)
    }

    @Test
    fun `camera on a parallel street 300 m away never alerts`() {
        val index = RadarIndex(listOf(camera(800.0, east = 300.0)))
        val run = drive(RadarAlertEngine(), index, 0.0, 1_200.0)
        assertTrue(run.events.isEmpty())
    }

    @Test
    fun `camera behind the car never alerts`() {
        val index = RadarIndex(listOf(camera(-200.0)))
        val run = drive(RadarAlertEngine(), index, 0.0, 300.0)
        assertTrue(run.events.isEmpty())
    }

    @Test
    fun `over the limit is flagged, and going over mid-approach fires OVERSPEED once`() {
        val index = RadarIndex(listOf(camera(1_000.0, limit = 60)))
        val engine = RadarAlertEngine()
        val slow = drive(engine, index, 0.0, 600.0, carSpeed = 50.0)
        assertEquals(listOf(Event.APPROACH), slow.events.map { it.second })
        assertTrue(slow.alerts.last().second!!.over.not())
        val fast = drive(engine, index, 615.0, 1_100.0, carSpeed = 75.0, startMs = 41_000L)
        assertEquals(listOf(Event.OVERSPEED, Event.NEAR), fast.events.map { it.second })
        assertTrue(fast.alerts.first().second!!.over)
    }

    @Test
    fun `unknown limit is never over`() {
        val index = RadarIndex(listOf(camera(700.0, limit = null)))
        val run = drive(RadarAlertEngine(), index, 0.0, 600.0, carSpeed = 150.0)
        assertTrue(run.events.none { it.second == Event.OVERSPEED })
        assertTrue(run.alerts.last().second!!.over.not())
    }

    @Test
    fun `turning away from the target drops it`() {
        val index = RadarIndex(listOf(camera(700.0)))
        val engine = RadarAlertEngine()
        val run = drive(engine, index, 0.0, 300.0)
        assertEquals(Event.APPROACH, run.events.single().second)
        // Turn east at y = 300 and drive away from it.
        var east = 0.0
        var t = 21_000L
        var last: RadarAlertEngine.Alert? = run.alerts.last().second
        repeat(6) {
            east += 15.0
            val (lat, lon) = at(300.0, east)
            last = engine.onFix(Fix(lat, lon, 90f, 15f, t), 54.0, config, index).alert
            t += 1_000L
        }
        assertNull(last)
    }

    @Test
    fun `no heading yet means no alert, then movement gives one`() {
        val index = RadarIndex(listOf(camera(400.0)))
        val engine = RadarAlertEngine()
        val (lat0, lon0) = at(0.0)
        // No GPS course and parked: nothing.
        assertNull(engine.onFix(Fix(lat0, lon0, null, 0f, 0L), 0.0, config, index).alert)
        // Moving without a course: heading from movement, then the alert.
        val (lat1, lon1) = at(20.0)
        val u = engine.onFix(Fix(lat1, lon1, null, 10f, 1_000L), 36.0, config, index)
        assertEquals(Event.APPROACH, u.event)
    }

    @Test
    fun `near distance inside approach on first sight alerts NEAR directly`() {
        val index = RadarIndex(listOf(camera(100.0)))
        val engine = RadarAlertEngine()
        val (lat, lon) = at(0.0)
        val u = engine.onFix(Fix(lat, lon, 0f, 15f, 0L), 54.0, config, index)
        assertEquals(Event.NEAR, u.event)
        assertEquals(Phase.NEAR, u.alert!!.phase)
    }
}
