package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoNavigationTelemetryTest {
    @Test
    fun inactiveJsonClearsStaleStreet() {
        assertEquals("""{"v":1,"active":false}""", AndroidAutoNavigationTelemetry.inactive().toJson())
    }

    @Test
    fun keepsThemeLabFields() {
        val json = AndroidAutoNavigationTelemetry.Directions(
            active = true,
            street = "Av. Paulista",
            distance = "200 m",
            distanceM = 200,
            turn = "TURN_RIGHT",
            turnId = 103,
            remainingM = 12300,
            remainingS = 840
        ).toJson()
        assertTrue(json.contains("\"street\":\"Av. Paulista\""))
        assertTrue(json.contains("\"distance\":\"200 m\""))
        assertTrue(json.contains("\"turn\":\"TURN_RIGHT\""))
        assertTrue(json.contains("\"distance_m\":200"))
        assertTrue(json.contains("\"remaining_m\":12300"))
        assertTrue(json.contains("\"remaining_s\":840"))
    }

    @Test
    fun turnAndStreetPublishImmediately() {
        val publisher = AndroidAutoNavigationTelemetry.Publisher()
        val first = AndroidAutoNavigationTelemetry.Directions(
            active = true,
            street = "A",
            distance = "300 m",
            distanceM = 300,
            turn = "TURN_LEFT"
        )
        assertTrue(publisher.onUpdate(first, 0L)!!.contains("TURN_LEFT"))
        val turned = first.copy(turn = "TURN_RIGHT", distanceM = 280, distance = "280 m")
        val json = publisher.onUpdate(turned, 50L)
        assertTrue(json!!.contains("TURN_RIGHT"))
    }

    @Test
    fun distanceIsThrottledWithTrailingCommit() {
        val publisher = AndroidAutoNavigationTelemetry.Publisher()
        val base = AndroidAutoNavigationTelemetry.Directions(
            active = true,
            street = "A",
            distance = "300 m",
            distanceM = 300,
            turn = "STRAIGHT"
        )
        publisher.onUpdate(base, 0L)
        assertNull(publisher.onUpdate(base.copy(distanceM = 290, distance = "290 m"), 200L))
        assertNull(publisher.onUpdate(base.copy(distanceM = 280, distance = "280 m"), 400L))
        val flushed = publisher.flushPending(400L)
        assertTrue(flushed!!.contains("\"distance_m\":280"))
        val later = publisher.onUpdate(base.copy(distanceM = 200, distance = "200 m"), 400L + AndroidAutoNavigationTelemetry.THROTTLE_MS)
        assertTrue(later!!.contains("\"distance_m\":200"))
    }

    @Test
    fun mapsUnknownTurnIdToEmptyAndKnownIdsToThemeLabStrings() {
        assertEquals("TURN_RIGHT", AndroidAutoNavigationTelemetry.fromTurnEnum("turn right", null))
        assertEquals("TURN_LEFT", AndroidAutoNavigationTelemetry.fromTurnEnum(null, 1))
        assertEquals("", AndroidAutoNavigationTelemetry.fromTurnEnum(null, 99))
    }

    @Test
    fun roundaboutExitIsPublishedAdditively() {
        val acc = AndroidAutoNavigationTelemetry.Accumulator()
        val step = acc.onRouteStep(
            "Pça. da Sé",
            AndroidAutoNavigationTelemetry.MANEUVER_ROUNDABOUT_ENTER_AND_EXIT_CCW_WITH_ANGLE,
            hasRoute = true,
            angle = 90,
            number = 2
        )
        assertEquals(2, step.exitNumber)
        assertEquals(90, step.turnAngle)
        val json = step.toJson()
        assertTrue(json.contains("\"v\":1,"))
        assertTrue(json.contains("\"exit_number\":2"))
        assertTrue(json.contains("\"turn_angle\":90"))
        assertTrue(json.contains("\"roundabout_cw\":false"))
        // Every field the v1.0 themes already read is still there.
        assertTrue(json.contains("\"turn\":\"ROUNDABOUT\""))
        assertTrue(json.contains("\"lanes\":[]"))
    }

    @Test
    fun roundaboutExitIsNullOffRoundaboutsAndWhenZero() {
        val acc = AndroidAutoNavigationTelemetry.Accumulator()
        val turn = acc.onRouteStep("A", AndroidAutoNavigationTelemetry.MANEUVER_TURN_NORMAL_RIGHT, true, angle = 90, number = 2)
        assertNull(turn.exitNumber)
        assertNull(turn.turnAngle)
        assertTrue(turn.toJson().contains("\"exit_number\":null,\"turn_angle\":null,\"roundabout_cw\":null"))
        // Fresh roundabout with no prior exit data stays null.
        val zero = acc.onRouteStep("B", AndroidAutoNavigationTelemetry.MANEUVER_ROUNDABOUT_ENTER_CW, true)
        assertNull(zero.exitNumber)
        assertNull(zero.turnAngle)
        assertEquals(true, zero.roundaboutClockwise)
        assertEquals(null to null, AndroidAutoNavigationTelemetry.roundaboutExit(
            AndroidAutoNavigationTelemetry.MANEUVER_ROUNDABOUT_EXIT_CW, 999, 9999))
        // Code 8 (other builds) carries the same pair.
        assertEquals(3, acc.onNextTurn("C", AndroidAutoNavigationTelemetry.MANEUVER_ROUNDABOUT_EXIT_CCW, 0, angle = -45, number = 3).exitNumber)
        acc.reset()
        assertNull(acc.current().exitNumber)
    }

    @Test
    fun roundaboutExitKeepsApproachPairWhenExitStepZerosAngle() {
        val acc = AndroidAutoNavigationTelemetry.Accumulator()
        val approach = acc.onRouteStep(
            "Av. Pref. Silvio Picanço",
            AndroidAutoNavigationTelemetry.MANEUVER_ROUNDABOUT_ENTER_AND_EXIT_CCW_WITH_ANGLE,
            hasRoute = true,
            angle = 315,
            number = 6
        )
        assertEquals(6, approach.exitNumber)
        assertEquals(315, approach.turnAngle)
        assertEquals(false, approach.roundaboutClockwise)
        // Same ring: AA's ROUNDABOUT_EXIT often arrives with turn_number/angle = 0.
        val onRing = acc.onRouteStep(
            "Av. Pref. Silvio Picanço",
            AndroidAutoNavigationTelemetry.MANEUVER_ROUNDABOUT_EXIT_CCW,
            hasRoute = true,
            angle = 0,
            number = 0
        )
        assertEquals("ROUNDABOUT_EXIT", onRing.turn)
        assertEquals(6, onRing.exitNumber)
        assertEquals(315, onRing.turnAngle)
        assertEquals(false, onRing.roundaboutClockwise)
        // Leaving the roundabout clears the pair.
        val next = acc.onRouteStep("R. Dr. Armando Lopes", AndroidAutoNavigationTelemetry.MANEUVER_TURN_NORMAL_RIGHT, true)
        assertNull(next.exitNumber)
        assertNull(next.turnAngle)
    }

    @Test
    fun newRoundaboutExitPublishesImmediately() {
        val publisher = AndroidAutoNavigationTelemetry.Publisher()
        val first = AndroidAutoNavigationTelemetry.Directions(
            active = true, street = "A", distance = "300 m", distanceM = 300, turn = "ROUNDABOUT", exitNumber = 1
        )
        publisher.onUpdate(first, 0L)
        val json = publisher.onUpdate(first.copy(exitNumber = 2), 10L)
        assertTrue(json!!.contains("\"exit_number\":2"))
    }
}
