package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the mapping from the vendor LinkCallback nav transactions onto the
 * published [AndroidAutoNavigationTelemetry.Directions].
 *
 * This car sends state (code 10) + position (code 11); codes 7/8/9 never fire
 * on it, so these cover the path that is actually live.
 */
class AndroidAutoNavigationAccumulatorTest {
    @Test
    fun maneuverSevenIsANormalLeftTurn() {
        // Verified against the AA screen on 2026-09-12: the car sent event=7
        // while D0 drew a left arrow. The Google proto enum calls 7 ON_RAMP,
        // which is why the vendor table is the one to map against.
        assertEquals(
            "TURN_LEFT",
            AndroidAutoNavigationTelemetry.turnFromManeuver(
                AndroidAutoNavigationTelemetry.MANEUVER_TURN_NORMAL_LEFT
            )
        )
        assertEquals(
            "TURN_RIGHT",
            AndroidAutoNavigationTelemetry.turnFromManeuver(
                AndroidAutoNavigationTelemetry.MANEUVER_TURN_NORMAL_RIGHT
            )
        )
        assertEquals(
            "DEPART",
            AndroidAutoNavigationTelemetry.turnFromManeuver(
                AndroidAutoNavigationTelemetry.MANEUVER_DEPART
            )
        )
    }

    @Test
    fun sideIsBakedIntoTheManeuverValue() {
        assertEquals(
            "SLIGHT_LEFT",
            AndroidAutoNavigationTelemetry.turnFromManeuver(
                AndroidAutoNavigationTelemetry.MANEUVER_TURN_SLIGHT_LEFT
            )
        )
        assertEquals(
            "ON_RAMP_RIGHT",
            AndroidAutoNavigationTelemetry.turnFromManeuver(
                AndroidAutoNavigationTelemetry.MANEUVER_ON_RAMP_SHARP_RIGHT
            )
        )
        assertEquals(
            "ROUNDABOUT_ENTER",
            AndroidAutoNavigationTelemetry.turnFromManeuver(
                AndroidAutoNavigationTelemetry.MANEUVER_ROUNDABOUT_ENTER_CCW
            )
        )
        assertEquals(
            "",
            AndroidAutoNavigationTelemetry.turnFromManeuver(
                AndroidAutoNavigationTelemetry.MANEUVER_UNKNOWN
            )
        )
    }

    @Test
    fun unitSuffixJoinsTheBareDisplayValue() {
        assertEquals("m", AndroidAutoNavigationTelemetry.unitSuffix(AndroidAutoNavigationTelemetry.UNIT_METERS))
        assertEquals("km", AndroidAutoNavigationTelemetry.unitSuffix(AndroidAutoNavigationTelemetry.UNIT_KILOMETERS_P1))
        assertEquals("", AndroidAutoNavigationTelemetry.unitSuffix(AndroidAutoNavigationTelemetry.UNIT_UNKNOWN))
    }

    @Test
    fun routeStepAndPositionMerge() {
        val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
        val step = accumulator.onRouteStep(
            "Rua Dom Bosco",
            AndroidAutoNavigationTelemetry.MANEUVER_TURN_NORMAL_LEFT,
            hasRoute = true
        )
        assertTrue(step.active)
        assertEquals("Rua Dom Bosco", step.street)
        assertEquals("TURN_LEFT", step.turn)

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
    fun tripTotalsAndEtaRideAlong() {
        val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
        accumulator.onRouteStep(
            "Rua Dom Bosco",
            AndroidAutoNavigationTelemetry.MANEUVER_TURN_NORMAL_LEFT,
            hasRoute = true
        )
        val update = accumulator.onPosition(
            153,
            "150",
            AndroidAutoNavigationTelemetry.UNIT_METERS,
            remainingMeters = 4800,
            remainingSeconds = 600,
            estimatedTime = "10:35"
        )
        assertEquals(4800, update.remainingM)
        assertEquals(600, update.remainingS)
        assertEquals("10:35", update.eta)
        val json = update.toJson()
        assertTrue(json.contains("\"remaining_m\":4800"))
        assertTrue(json.contains("\"eta\":\"10:35\""))
    }

    @Test
    fun positionWithoutTripTotalsKeepsLastEta() {
        // Waze streams manoeuvre distance without DestDistanceData on many
        // ticks; clearing remaining/eta there made the viewer's ETA column
        // vanish while Maps (which always fills the list) looked fine.
        val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
        accumulator.onRouteStep(
            "Rua Dom Bosco",
            AndroidAutoNavigationTelemetry.MANEUVER_TURN_NORMAL_LEFT,
            hasRoute = true
        )
        accumulator.onPosition(
            153,
            "150",
            AndroidAutoNavigationTelemetry.UNIT_METERS,
            remainingMeters = 4800,
            remainingSeconds = 600,
            estimatedTime = "10:35"
        )
        val partial = accumulator.onPosition(
            120,
            "120",
            AndroidAutoNavigationTelemetry.UNIT_METERS
        )
        assertEquals(120, partial.distanceM)
        assertEquals(4800, partial.remainingM)
        assertEquals(600, partial.remainingS)
        assertEquals("10:35", partial.eta)
    }

    @Test
    fun wazeZeroSecondsDerivesRemainingFromEtaClock() {
        // Car log 2026-09-24: remainingM=4000 remainingS=0 eta=20:38
        val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
        accumulator.onRouteStep(
            "Rua Dom Bosco",
            AndroidAutoNavigationTelemetry.MANEUVER_TURN_NORMAL_LEFT,
            hasRoute = true
        )
        val now = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 20)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val update = accumulator.onPosition(
            80,
            "80",
            AndroidAutoNavigationTelemetry.UNIT_METERS,
            remainingMeters = 4000,
            remainingSeconds = 0,
            estimatedTime = "20:38",
            nowMs = now
        )
        assertEquals(4000, update.remainingM)
        assertEquals("20:38", update.eta)
        assertEquals(38 * 60, update.remainingS)
    }

    @Test
    fun remainingSecondsFromEtaClockRollsOvernight() {
        val now = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 50)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        // 00:20 is 30 minutes after 23:50 → tomorrow.
        assertEquals(
            30 * 60,
            AndroidAutoNavigationTelemetry.remainingSecondsFromEtaClock("00:20", now)
        )
    }

    @Test
    fun positiveRemainingSecondsStillWinsOverEtaClock() {
        val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
        accumulator.onRouteStep(
            "Rua Dom Bosco",
            AndroidAutoNavigationTelemetry.MANEUVER_TURN_NORMAL_LEFT,
            hasRoute = true
        )
        val now = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 20)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val update = accumulator.onPosition(
            80,
            "80",
            AndroidAutoNavigationTelemetry.UNIT_METERS,
            remainingMeters = 4000,
            remainingSeconds = 600,
            estimatedTime = "20:38",
            nowMs = now
        )
        assertEquals(600, update.remainingS)
    }

    @Test
    fun routeWithNoStepsClearsGuidance() {
        val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
        accumulator.onRouteStep(
            "Rua Dom Bosco",
            AndroidAutoNavigationTelemetry.MANEUVER_TURN_NORMAL_LEFT,
            hasRoute = true
        )
        val cleared = accumulator.onRouteStep(null, 0, hasRoute = false)
        assertFalse(cleared.active)
        assertEquals("""{"v":1,"active":false}""", cleared.toJson())
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
    fun resetDropsStaleRouteBeforeNextSession() {
        val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
        accumulator.onRouteStep(
            "Rua Augusta",
            AndroidAutoNavigationTelemetry.MANEUVER_TURN_NORMAL_LEFT,
            hasRoute = true
        )
        accumulator.reset()
        assertFalse(accumulator.current().active)
    }

    @Test
    fun negativeDistanceIsNotPublishedAsAMeasurement() {
        val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
        accumulator.onRouteStep(
            "Rua Augusta",
            AndroidAutoNavigationTelemetry.MANEUVER_STRAIGHT,
            hasRoute = true
        )
        val update = accumulator.onPosition(
            -1,
            "",
            AndroidAutoNavigationTelemetry.UNIT_METERS
        )
        assertNull(update.distanceM)
        assertEquals("", update.distance)
    }

    @Test
    fun inactiveNavigationStatusClearsTheCard() {
        // On the car the card stayed frozen on the arrival frame after guidance
        // ended: code 7 carries NavigationStatus, where INACTIVE is 2, and only
        // 0 used to clear.
        val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
        accumulator.onRouteStep(
            "Colégio pH",
            AndroidAutoNavigationTelemetry.MANEUVER_DESTINATION,
            hasRoute = true
        )
        val ended = accumulator.onNavigationState(AndroidAutoNavigationTelemetry.NAV_STATUS_INACTIVE)
        assertFalse(ended.active)
        assertEquals("""{"v":1,"active":false}""", ended.toJson())
    }

    @Test
    fun deviceLostStatusClearsTheCard() {
        val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
        accumulator.onRouteStep(
            "Rua Dom Bosco",
            AndroidAutoNavigationTelemetry.MANEUVER_TURN_NORMAL_LEFT,
            hasRoute = true
        )
        assertFalse(accumulator.onNavigationState(0).active)
    }

    @Test
    fun activeOrReroutingStatusNeverInventsACard() {
        val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
        // ACTIVE before any route must not raise an empty card.
        assertFalse(accumulator.onNavigationState(AndroidAutoNavigationTelemetry.NAV_STATUS_ACTIVE).active)
        accumulator.onRouteStep(
            "Rua Dom Bosco",
            AndroidAutoNavigationTelemetry.MANEUVER_TURN_NORMAL_LEFT,
            hasRoute = true
        )
        // REROUTING mid-route keeps the current manoeuvre.
        val rerouting = accumulator.onNavigationState(AndroidAutoNavigationTelemetry.NAV_STATUS_REROUTING)
        assertTrue(rerouting.active)
        assertEquals("Rua Dom Bosco", rerouting.street)
    }
}
