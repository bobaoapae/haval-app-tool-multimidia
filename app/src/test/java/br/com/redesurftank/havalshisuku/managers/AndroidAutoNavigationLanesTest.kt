package br.com.redesurftank.havalshisuku.managers

import br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry.LaneDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoNavigationLanesTest {
    /** Replays a recorded sequence of Parcel reads. */
    private class FakeReader(vararg values: Any?) : AndroidAutoNavigationTelemetry.ParcelReader {
        private val queue = ArrayDeque(values.toList())
        override fun readInt(): Int = queue.removeFirst() as Int
        override fun readString(): String? = queue.removeFirst() as String?
        val remaining: Int get() = queue.size
    }

    private val straight = LaneDirection(1, false)
    private val rightUsed = LaneDirection(5, true)

    @Test
    fun readsTheCarMeasuredRightTurn() {
        // R. Nóbrega, 2026-10-13: event=8 lanes=[1 | 5*,1], one cue, one step.
        val reader = FakeReader(
            1, 1, "R. Nóbrega",          // cue count, list size, text
            2, 2,                        // lane count, list size
            1, 1, 1, 1, 1, 0,            // lane 0: flag, dirCount, size, [flag, STRAIGHT, off]
            1, 2, 2, 1, 5, 1, 1, 1, 0    // lane 1: flag, 2, 2, [NORMAL_RIGHT on], [STRAIGHT off]
        )
        val tail = AndroidAutoNavigationTelemetry.readStepTail(reader, stepListSize = 1)
        assertEquals(listOf(listOf(straight), listOf(rightUsed, straight)), tail.lanes)
        assertEquals(listOf("R. Nóbrega"), tail.cues)
        assertNull(tail.nextRoad)
        assertEquals(0, reader.remaining)
        assertEquals("1|5*,1", AndroidAutoNavigationTelemetry.describeLanes(tail.lanes))
    }

    @Test
    fun emptyListsReadOnlyTheirCounts() {
        val reader = FakeReader(0, 0)
        val tail = AndroidAutoNavigationTelemetry.readStepTail(reader, stepListSize = 1)
        assertTrue(tail.lanes.isEmpty())
        assertTrue(tail.cues.isEmpty())
        assertEquals(0, reader.remaining)
    }

    @Test
    fun readsSecondStepRoadAndEvent() {
        val reader = FakeReader(0, 0, 1, "Av. Roberto Silveira", 7)
        val tail = AndroidAutoNavigationTelemetry.readStepTail(reader, stepListSize = 2)
        assertEquals("Av. Roberto Silveira", tail.nextRoad)
        assertEquals(7, tail.nextEvent)
    }

    @Test(expected = IllegalStateException::class)
    fun implausibleLaneCountThrows() {
        AndroidAutoNavigationTelemetry.readStepTail(FakeReader(0, 1, 5000), stepListSize = 1)
    }

    @Test
    fun jsonCarriesLanesAndEmptyArrayWithout() {
        val base = AndroidAutoNavigationTelemetry.Directions(active = true, street = "A", turn = "TURN_RIGHT")
        assertTrue(base.toJson().contains("\"lanes\":[]"))
        val json = base.copy(lanes = listOf(listOf(straight), listOf(rightUsed, straight))).toJson()
        assertTrue(
            json,
            json.contains("\"lanes\":[[{\"s\":1,\"h\":false}],[{\"s\":5,\"h\":true},{\"s\":1,\"h\":false}]]")
        )
        assertEquals("""{"v":1,"active":false}""", AndroidAutoNavigationTelemetry.inactive().toJson())
    }

    @Test
    fun laneChangePublishesImmediately() {
        val publisher = AndroidAutoNavigationTelemetry.Publisher()
        val base = AndroidAutoNavigationTelemetry.Directions(active = true, street = "A", turn = "TURN_RIGHT")
        assertNotNull(publisher.onUpdate(base, 0L))
        val withLanes = base.copy(lanes = listOf(listOf(rightUsed)))
        assertNotNull("lanes must not wait for the 1 Hz throttle", publisher.onUpdate(withLanes, 50L))
    }

    @Test
    fun accumulatorKeepsLanesPerStepAndClearsOnReset() {
        val acc = AndroidAutoNavigationTelemetry.Accumulator()
        val lanes = listOf(listOf(straight), listOf(rightUsed, straight))
        assertEquals(lanes, acc.onRouteStep("R. Nóbrega", 8, hasRoute = true, stepLanes = lanes).lanes)
        assertTrue(acc.onRouteStep("R. Lopes Trovão", 8, hasRoute = true).lanes.isEmpty())
        acc.onRouteStep("R. Nóbrega", 8, hasRoute = true, stepLanes = lanes)
        acc.onNavigationState(2)
        assertTrue(acc.current().lanes.isEmpty())
    }
}
