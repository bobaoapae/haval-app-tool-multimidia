package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the mapping from the vendor LinkCallback nav transactions (codes 7/8/9)
 * onto the published [AndroidAutoNavigationTelemetry.Directions].
 */
class AndroidAutoNavigationAccumulatorTest {
    @Test
    fun turnAndSideBecomeThemeToken() {
        assertEquals(
            "TURN_RIGHT",
            AndroidAutoNavigationTelemetry.turnFromEvent(
                AndroidAutoNavigationTelemetry.EVENT_TURN,
                AndroidAutoNavigationTelemetry.SIDE_RIGHT
            )
        )
        assertEquals(
            "SLIGHT_LEFT",
            AndroidAutoNavigationTelemetry.turnFromEvent(
                AndroidAutoNavigationTelemetry.EVENT_SLIGHT_TURN,
                AndroidAutoNavigationTelemetry.SIDE_LEFT
            )
        )
    }

    @Test
    fun sidelessEventsDoNotGainASuffix() {
        assertEquals(
            "U_TURN",
            AndroidAutoNavigationTelemetry.turnFromEvent(
                AndroidAutoNavigationTelemetry.EVENT_U_TURN,
                AndroidAutoNavigationTelemetry.SIDE_LEFT
            )
        )
        assertEquals(
            "DESTINATION",
            AndroidAutoNavigationTelemetry.turnFromEvent(
                AndroidAutoNavigationTelemetry.EVENT_DESTINATION,
                AndroidAutoNavigationTelemetry.SIDE_UNSPECIFIED
            )
        )
        assertEquals(
            "",
            AndroidAutoNavigationTelemetry.turnFromEvent(
                AndroidAutoNavigationTelemetry.EVENT_UNKNOWN,
                AndroidAutoNavigationTelemetry.SIDE_UNSPECIFIED
            )
        )
    }

    @Test
    fun displayDistanceIsScaledByAThousand() {
        assertEquals(
            "200 m",
            AndroidAutoNavigationTelemetry.formatDistance(
                200_000,
                AndroidAutoNavigationTelemetry.UNIT_METERS
            )
        )
        assertEquals(
            "1.2 km",
            AndroidAutoNavigationTelemetry.formatDistance(
                1_200,
                AndroidAutoNavigationTelemetry.UNIT_KILOMETERS_P1
            )
        )
        assertEquals(
            "3 km",
            AndroidAutoNavigationTelemetry.formatDistance(
                3_000,
                AndroidAutoNavigationTelemetry.UNIT_KILOMETERS
            )
        )
    }

    @Test
    fun unknownUnitYieldsNoDistanceText() {
        assertEquals(
            "",
            AndroidAutoNavigationTelemetry.formatDistance(
                500,
                AndroidAutoNavigationTelemetry.UNIT_UNKNOWN
            )
        )
    }

    @Test
    fun turnAndDistanceArriveSeparatelyAndMerge() {
        val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
        val afterTurn = accumulator.onNextTurn(
            "Av. Paulista",
            AndroidAutoNavigationTelemetry.EVENT_TURN,
            AndroidAutoNavigationTelemetry.SIDE_RIGHT
        )
        assertTrue(afterTurn.active)
        assertEquals("Av. Paulista", afterTurn.street)
        assertEquals("TURN_RIGHT", afterTurn.turn)
        assertNull(afterTurn.distanceM)

        val afterDistance = accumulator.onNextTurnDistance(
            200,
            200_000,
            AndroidAutoNavigationTelemetry.UNIT_METERS
        )
        // The street from the earlier transaction must survive the merge.
        assertEquals("Av. Paulista", afterDistance.street)
        assertEquals("TURN_RIGHT", afterDistance.turn)
        assertEquals(200, afterDistance.distanceM)
        assertEquals("200 m", afterDistance.distance)
    }

    @Test
    fun guidanceOffClearsTheStreet() {
        val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
        accumulator.onNextTurn(
            "Av. Paulista",
            AndroidAutoNavigationTelemetry.EVENT_TURN,
            AndroidAutoNavigationTelemetry.SIDE_RIGHT
        )
        val stopped = accumulator.onNavigationState(0)
        assertFalse(stopped.active)
        assertEquals("""{"active":false}""", stopped.toJson())
        assertEquals("", stopped.street)
    }

    @Test
    fun resetDropsStaleRouteBeforeNextSession() {
        val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
        accumulator.onNextTurn(
            "Rua Augusta",
            AndroidAutoNavigationTelemetry.EVENT_TURN,
            AndroidAutoNavigationTelemetry.SIDE_LEFT
        )
        accumulator.reset()
        assertFalse(accumulator.current().active)

        // A bare distance callback on a fresh route must not resurrect the old
        // street from the previous session.
        val next = accumulator.onNextTurnDistance(
            50,
            50_000,
            AndroidAutoNavigationTelemetry.UNIT_METERS
        )
        assertTrue(next.active)
        assertEquals("", next.street)
        assertEquals(50, next.distanceM)
    }

    // The car sends state (code 10) + position (code 11); codes 7/8/9 never fire
    // on this head unit, so these cover the path that is actually live.

    @Test
    fun routeStepAndPositionMerge() {
        val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
        val step = accumulator.onRouteStep(
            "Rua Dom Bosco",
            AndroidAutoNavigationTelemetry.EVENT_ON_RAMP,
            0,
            hasRoute = true
        )
        assertTrue(step.active)
        assertEquals("Rua Dom Bosco", step.street)
        assertEquals("ON_RAMP", step.turn)

        val moving = accumulator.onPosition(
            153,
            "150",
            AndroidAutoNavigationTelemetry.UNIT_METERS
        )
        assertEquals("Rua Dom Bosco", moving.street)
        assertEquals(153, moving.distanceM)
        // The car sends a bare "150" with the unit in its own field.
        assertEquals("150 m", moving.distance)
    }

    @Test
    fun routeWithNoStepsClearsGuidance() {
        val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
        accumulator.onRouteStep(
            "Rua Dom Bosco",
            AndroidAutoNavigationTelemetry.EVENT_ON_RAMP,
            0,
            hasRoute = true
        )
        val cleared = accumulator.onRouteStep(null, 0, 0, hasRoute = false)
        assertFalse(cleared.active)
        assertEquals("""{"active":false}""", cleared.toJson())
    }

    @Test
    fun positionBeforeAnyRouteIsIgnored() {
        val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
        val update = accumulator.onPosition(
            153,
            "150",
            AndroidAutoNavigationTelemetry.UNIT_METERS
        )
        assertFalse(update.active)
        assertNull(update.distanceM)
    }

    @Test
    fun turnSideComesFromTheSignedAngle() {
        val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
        val left = accumulator.onRouteStep(
            "Av. Brasil",
            AndroidAutoNavigationTelemetry.EVENT_TURN,
            -90,
            hasRoute = true
        )
        assertEquals("TURN_LEFT", left.turn)

        val right = accumulator.onRouteStep(
            "Av. Brasil",
            AndroidAutoNavigationTelemetry.EVENT_TURN,
            90,
            hasRoute = true
        )
        assertEquals("TURN_RIGHT", right.turn)
    }

    @Test
    fun negativeDistanceIsNotPublishedAsAMeasurement() {
        val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
        val update = accumulator.onNextTurnDistance(
            -1,
            0,
            AndroidAutoNavigationTelemetry.UNIT_METERS
        )
        assertNull(update.distanceM)
        assertEquals("", update.distance)
    }
}
