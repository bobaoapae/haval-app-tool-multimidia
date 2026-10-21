package br.com.redesurftank.havalshisuku.navigation

import br.com.redesurftank.havalshisuku.navigation.PreparedNavigationArbiter.Source.ANDROID_AUTO
import br.com.redesurftank.havalshisuku.navigation.PreparedNavigationArbiter.Source.CARPLAY
import org.junit.Assert.*
import org.junit.Test

/** All sessions, clocks and directions here are synthetic, not captures from a vehicle. */
class PreparedNavigationArbiterTest {
    private fun direction(distance: String = "100", road: String = "Synthetic Road") =
        PreparedDirections(active = true, navigationState = "ROUTE_SET", nextStreet = road, distanceText = distance)

    @Test fun initialSnapshotIsInactiveAndConnectingAlonePublishesNothing() {
        val arbiter = PreparedNavigationArbiter()
        assertFalse(arbiter.snapshot().directions.active)
        assertNull(arbiter.snapshot().source)
        assertNull(arbiter.connect(CARPLAY, 0).publication)
        assertNull(arbiter.connect(ANDROID_AUTO, 0).publication)
        assertNull(arbiter.nextFlushAtMs())
    }

    @Test fun aaWinsRegardlessOfArrivalOrder() {
        for (aaFirst in listOf(true, false)) {
            val arbiter = PreparedNavigationArbiter()
            val aa = arbiter.connect(ANDROID_AUTO, 0).session
            val cp = arbiter.connect(CARPLAY, 0).session
            if (aaFirst) {
                arbiter.onUpdate(aa, direction(road = "AA"), 0)
                assertNull(arbiter.onUpdate(cp, direction(road = "CP"), 1))
            } else {
                arbiter.onUpdate(cp, direction(road = "CP"), 0)
                assertEquals(ANDROID_AUTO, arbiter.onUpdate(aa, direction(road = "AA"), 1)?.source)
            }
            assertEquals(ANDROID_AUTO, arbiter.snapshot().source)
            assertEquals("AA", arbiter.snapshot().directions.nextStreet)
        }
    }

    @Test fun linkedButIdleAaDoesNotSuppressCarPlay() {
        val arbiter = PreparedNavigationArbiter()
        val aa = arbiter.connect(ANDROID_AUTO, 0).session
        val cp = arbiter.connect(CARPLAY, 0).session
        arbiter.onUpdate(aa, direction().copy(active = false), 0)
        assertEquals(CARPLAY, arbiter.onUpdate(cp, direction(), 1)?.source)
    }

    @Test fun nonOwnerDisconnectCannotClearOwnerOrItsTrailingUpdate() {
        val arbiter = PreparedNavigationArbiter()
        val aa = arbiter.connect(ANDROID_AUTO, 0).session
        val cp = arbiter.connect(CARPLAY, 0).session
        arbiter.onUpdate(aa, direction(), 0)
        arbiter.onUpdate(cp, direction(), 1)
        arbiter.onUpdate(aa, direction("90"), 2)
        assertNull(arbiter.disconnect(cp, 3))
        assertEquals(ANDROID_AUTO, arbiter.snapshot().source)
        assertEquals(1_000L, arbiter.nextFlushAtMs())
        assertEquals("90", arbiter.flush(1_000)?.directions?.distanceText)
    }

    @Test fun nonOwnerInactiveCannotClearOwner() {
        val arbiter = PreparedNavigationArbiter()
        val aa = arbiter.connect(ANDROID_AUTO, 0).session
        val cp = arbiter.connect(CARPLAY, 0).session
        arbiter.onUpdate(aa, direction(), 0)
        assertNull(arbiter.onUpdate(cp, direction().copy(active = false), 1))
        assertEquals(ANDROID_AUTO, arbiter.snapshot().source)
    }

    @Test fun ownerEndTransfersImmediatelyToLatestEligibleCarPlay() {
        val arbiter = PreparedNavigationArbiter()
        val aa = arbiter.connect(ANDROID_AUTO, 0).session
        val cp = arbiter.connect(CARPLAY, 0).session
        arbiter.onUpdate(aa, direction(), 0)
        arbiter.onUpdate(cp, direction("200"), 1)
        arbiter.onUpdate(cp, direction("150"), 2)
        val transfer = arbiter.onUpdate(aa, direction().copy(active = false), 3)!!
        assertEquals(CARPLAY, transfer.source)
        assertEquals("150", transfer.directions.distanceText)
        assertNull(arbiter.nextFlushAtMs())
    }

    @Test fun ownerDisconnectTransfersAndNeverEmitsIntermediateInactive() {
        val arbiter = PreparedNavigationArbiter()
        val aa = arbiter.connect(ANDROID_AUTO, 0).session
        val cp = arbiter.connect(CARPLAY, 0).session
        arbiter.onUpdate(cp, direction(), 0)
        arbiter.onUpdate(aa, direction(), 1)
        assertEquals(CARPLAY, arbiter.disconnect(aa, 2)?.source)
        assertTrue(arbiter.snapshot().directions.active)
    }

    @Test fun ownerDisconnectClearsImmediatelyAndCancelsTrailing() {
        val arbiter = PreparedNavigationArbiter()
        val cp = arbiter.connect(CARPLAY, 0).session
        arbiter.onUpdate(cp, direction(), 0)
        arbiter.onUpdate(cp, direction("90"), 1)
        val clear = arbiter.disconnect(cp, 2)!!
        assertFalse(clear.directions.active)
        assertNull(clear.source)
        assertEquals("", clear.directions.nextStreet)
        assertNull(arbiter.nextFlushAtMs())
        assertNull(arbiter.flush(2_000))
        assertNull(arbiter.disconnect(cp, 2_001))
    }

    @Test fun stoppedRouteCancelsTrailingAndPublishesInactiveOnce() {
        val arbiter = PreparedNavigationArbiter()
        val cp = arbiter.connect(CARPLAY, 0).session
        arbiter.onUpdate(cp, direction(), 0)
        arbiter.onUpdate(cp, direction("90"), 1)
        assertFalse(arbiter.onUpdate(cp, direction().copy(active = false), 2)!!.directions.active)
        assertNull(arbiter.onUpdate(cp, direction().copy(active = false), 3))
        assertNull(arbiter.flush(1_000))
    }

    @Test fun delayedPreviousSessionCannotOverwriteOrDisconnectNewSession() {
        val arbiter = PreparedNavigationArbiter()
        val old = arbiter.connect(CARPLAY, 0).session
        arbiter.onUpdate(old, direction(), 0)
        val rebind = arbiter.connect(CARPLAY, 1)
        assertFalse(rebind.publication!!.directions.active)
        assertTrue(rebind.session.generation > old.generation)
        arbiter.onUpdate(rebind.session, direction("80"), 2)
        assertNull(arbiter.onUpdate(old, direction("stale"), 3))
        assertNull(arbiter.disconnect(old, 4))
        assertEquals("80", arbiter.snapshot().directions.distanceText)
    }

    @Test fun resetInvalidatesAllSessionsAndClearsSnapshotAndPending() {
        val arbiter = PreparedNavigationArbiter()
        val aa = arbiter.connect(ANDROID_AUTO, 0).session
        val cp = arbiter.connect(CARPLAY, 0).session
        arbiter.onUpdate(aa, direction(), 0)
        arbiter.onUpdate(cp, direction(), 1)
        arbiter.onUpdate(aa, direction("90"), 2)
        assertFalse(arbiter.reset(3)!!.directions.active)
        assertNull(arbiter.nextFlushAtMs())
        assertNull(arbiter.onUpdate(aa, direction(), 4))
        assertNull(arbiter.onUpdate(cp, direction(), 4))
        assertNull(arbiter.flush(1_000))
        assertNull(arbiter.reset(1_000))
        val next = arbiter.connect(CARPLAY, 1_000).session
        assertTrue(next.generation > cp.generation)
        assertNotNull(arbiter.onUpdate(next, direction(), 1_000))
    }

    @Test fun tokensAreSpecificToAnArbiterEvenWithSameGeneration() {
        val first = PreparedNavigationArbiter()
        val second = PreparedNavigationArbiter()
        val foreign = first.connect(CARPLAY, 0).session
        val own = second.connect(CARPLAY, 0).session
        assertEquals(foreign.generation, own.generation)
        assertNull(second.onUpdate(foreign, direction(), 0))
        assertNull(second.disconnect(foreign, 0))
        assertNotNull(second.onUpdate(own, direction(), 0))
    }

    @Test fun trailingCoalescesLatestAtFixedDeadlineWithoutStarvation() {
        val arbiter = PreparedNavigationArbiter()
        val cp = arbiter.connect(CARPLAY, 0).session
        arbiter.onUpdate(cp, direction("1000"), 0)
        for (time in 1L..999L) {
            assertNull(arbiter.onUpdate(cp, direction(time.toString()), time))
            assertEquals(1_000L, arbiter.nextFlushAtMs())
        }
        assertEquals("1000", arbiter.snapshot().directions.distanceText)
        assertNull(arbiter.flush(999))
        assertEquals("999", arbiter.flush(1_000)?.directions?.distanceText)
        assertNull(arbiter.flush(1_000))
        assertNull(arbiter.nextFlushAtMs())
    }

    @Test fun turnStreetAndStateChangesBypassThrottle() {
        val variants = listOf(
            direction().copy(turnId = 53), direction().copy(turn = "TURN_LEFT"),
            direction().copy(street = "Street"), direction(road = "Next"),
            direction().copy(navigationState = "REROUTING"), direction().copy(lanes = listOf("synthetic")),
            direction().copy(exitNumber = 2), direction().copy(turnAngle = 180),
            direction().copy(roundaboutClockwise = true)
        )
        for (changed in variants) {
            val arbiter = PreparedNavigationArbiter()
            val aa = arbiter.connect(ANDROID_AUTO, 0).session
            arbiter.onUpdate(aa, direction(), 0)
            arbiter.onUpdate(aa, direction("90"), 1)
            assertEquals(changed, arbiter.onUpdate(aa, changed, 2)?.directions)
            assertNull(arbiter.nextFlushAtMs())
            assertNull(arbiter.flush(1_000))
        }
    }

    @Test fun remainingAndDistanceFieldsAreThrottledTogether() {
        val arbiter = PreparedNavigationArbiter()
        val aa = arbiter.connect(ANDROID_AUTO, 0).session
        val original = direction()
        val changed = original.copy(distanceMeters = 90, remainingText = "2 km", remainingMeters = 2000,
            remainingSeconds = 120, eta = "23:00")
        arbiter.onUpdate(aa, original, 0)
        assertNull(arbiter.onUpdate(aa, changed, 1))
        assertEquals(original, arbiter.snapshot().directions)
        assertEquals(changed, arbiter.flush(1_000)?.directions)
    }

    @Test fun duplicateAndReturnToPublishedValueCancelTrailing() {
        val arbiter = PreparedNavigationArbiter()
        val cp = arbiter.connect(CARPLAY, 0).session
        arbiter.onUpdate(cp, direction(), 0)
        assertNull(arbiter.onUpdate(cp, direction(), 1))
        arbiter.onUpdate(cp, direction("90"), 2)
        assertNotNull(arbiter.nextFlushAtMs())
        assertNull(arbiter.onUpdate(cp, direction(), 3))
        assertNull(arbiter.nextFlushAtMs())
        assertNull(arbiter.flush(1_000))
    }

    @Test fun sourceSwitchDropsPreviousSourcesTrailingData() {
        val arbiter = PreparedNavigationArbiter()
        val aa = arbiter.connect(ANDROID_AUTO, 0).session
        val cp = arbiter.connect(CARPLAY, 0).session
        arbiter.onUpdate(cp, direction("100"), 0)
        arbiter.onUpdate(cp, direction("90"), 1)
        arbiter.onUpdate(aa, direction("200"), 2)
        assertNull(arbiter.nextFlushAtMs())
        assertNull(arbiter.flush(1_000))
        assertEquals("200", arbiter.snapshot().directions.distanceText)
        assertEquals("90", arbiter.disconnect(aa, 1_001)?.directions?.distanceText)
    }

    @Test fun callerCannotMutatePublishedOrPendingLanes() {
        val arbiter = PreparedNavigationArbiter()
        val aa = arbiter.connect(ANDROID_AUTO, 0).session
        val lanes = mutableListOf("synthetic-lane")
        arbiter.onUpdate(aa, direction().copy(lanes = lanes), 0)
        lanes.clear()
        assertEquals(listOf("synthetic-lane"), arbiter.snapshot().directions.lanes)
        try {
            (arbiter.snapshot().directions.lanes as MutableList<String>).clear()
            fail("Snapshot lanes must not be externally mutable")
        } catch (_: UnsupportedOperationException) { }
        val pendingLanes = mutableListOf("synthetic-lane")
        assertNull(arbiter.onUpdate(aa, direction("90").copy(lanes = pendingLanes), 1))
        pendingLanes.clear()
        assertEquals(listOf("synthetic-lane"), arbiter.flush(1_000)?.directions?.lanes)
    }

    @Test fun deadlineDoesNotOverflowOrFlushTooEarlyAtLongBoundary() {
        val arbiter = PreparedNavigationArbiter()
        val cp = arbiter.connect(CARPLAY, Long.MAX_VALUE - 10).session
        arbiter.onUpdate(cp, direction(), Long.MAX_VALUE - 10)
        arbiter.onUpdate(cp, direction("90"), Long.MAX_VALUE - 1)
        assertEquals(Long.MAX_VALUE, arbiter.nextFlushAtMs())
        assertNull(arbiter.flush(Long.MAX_VALUE))
    }

    @Test(expected = IllegalArgumentException::class) fun negativeClockRejected() {
        PreparedNavigationArbiter().connect(CARPLAY, -1)
    }

    @Test(expected = IllegalArgumentException::class) fun backwardClockRejected() {
        val arbiter = PreparedNavigationArbiter()
        val cp = arbiter.connect(CARPLAY, 100).session
        arbiter.onUpdate(cp, direction(), 99)
    }

    @Test fun normalizedCarPlayStatePolicyControlsOwnershipWithoutGuessingManeuvers() {
        val arbiter = PreparedNavigationArbiter()
        val cp = arbiter.connect(CARPLAY, 0).session
        val raw = RawCarPlayGuidance(6, "1,234", 0, "Synthetic Road", "2.345", 0,
            Long.MAX_VALUE, 1, 1, 0, 90)
        var time = 0L
        for (state in listOf(1, 0, 5, 2, 6, 3, 1, 4, 5, 999)) {
            val prepared = CarPlayGuidanceNormalizer.normalize(raw.copy(mRouteGuidanceState = state))
            arbiter.onUpdate(cp, prepared, time++)
            assertEquals(state in listOf(1, 5, 6), arbiter.snapshot().directions.active)
            assertEquals("UNKNOWN", arbiter.snapshot().directions.turn)
            assertNull(arbiter.snapshot().directions.turnAngle)
            assertNull(arbiter.snapshot().directions.remainingSeconds)
        }
        arbiter.onUpdate(cp, CarPlayGuidanceNormalizer.normalize(raw), time++)
        assertFalse(arbiter.onUpdate(cp, CarPlayGuidanceNormalizer.normalize(null), time)!!.directions.active)
    }

    @Test fun syntheticEventSequencesAlwaysChooseHighestPriorityEligibleSession() {
        val random = java.util.Random(24L)
        repeat(30) {
            val arbiter = PreparedNavigationArbiter()
            val sessions = mutableMapOf<PreparedNavigationArbiter.Source, PreparedNavigationArbiter.Session>()
            val active = mutableMapOf<PreparedNavigationArbiter.Source, Boolean>()
            var clock = 0L
            repeat(200) {
                val source = if (random.nextBoolean()) ANDROID_AUTO else CARPLAY
                when (random.nextInt(4)) {
                    0 -> { sessions[source] = arbiter.connect(source, clock).session; active[source] = false }
                    1 -> sessions[source]?.let { token ->
                        val value = random.nextBoolean()
                        active[source] = value
                        arbiter.onUpdate(token, direction().copy(active = value), clock)
                    }
                    2 -> sessions.remove(source)?.let { token ->
                        arbiter.disconnect(token, clock); active.remove(source)
                    }
                    3 -> { arbiter.reset(clock); sessions.clear(); active.clear() }
                }
                val expected = when {
                    active[ANDROID_AUTO] == true -> ANDROID_AUTO
                    active[CARPLAY] == true -> CARPLAY
                    else -> null
                }
                assertEquals(expected, arbiter.snapshot().source)
                assertEquals(expected != null, arbiter.snapshot().directions.active)
                clock++
            }
        }
    }
}
