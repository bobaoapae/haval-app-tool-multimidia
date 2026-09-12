package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoNavigationTelemetryTest {
    @Test
    fun inactiveJsonClearsStaleStreet() {
        assertEquals("""{"active":false}""", AndroidAutoNavigationTelemetry.inactive().toJson())
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
}
