package br.com.redesurftank.havalshisuku.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class CarPlayNowPlayingMonitorTest {
    @Test
    fun transientBlankWaitsOnlyForRemainderOfGraceWindow() {
        assertEquals(
                2_500L,
                CarPlayNowPlayingMonitor.blankClearDelayForTest(
                        nowMs = 11_500L,
                        lastValidNowPlayingAtMs = 10_000L
                )
        )
    }

    @Test
    fun terminalBlankClearsAfterGraceAndFirstBlankClearsImmediately() {
        assertEquals(
                0L,
                CarPlayNowPlayingMonitor.blankClearDelayForTest(
                        nowMs = 14_000L,
                        lastValidNowPlayingAtMs = 10_000L
                )
        )
        assertEquals(
                0L,
                CarPlayNowPlayingMonitor.blankClearDelayForTest(
                        nowMs = 14_000L,
                        lastValidNowPlayingAtMs = 0L
                )
        )
    }

    @Test
    fun monotonicClockRollbackDoesNotClearEarly() {
        assertEquals(
                4_000L,
                CarPlayNowPlayingMonitor.blankClearDelayForTest(
                        nowMs = 9_000L,
                        lastValidNowPlayingAtMs = 10_000L
                )
        )
    }

    @Test
    fun carPlayServiceBindDoesNotRetryWhenBinderIsAlive() {
        assertFalse(
                CarPlayNowPlayingMonitor.shouldRetryCarPlayServiceBindForTest(
                        nowMs = 30_000L,
                        lastServiceBindAttemptAtMs = 10_000L,
                        binderAlive = true
                )
        )
    }

    @Test
    fun carPlayServiceBindRetriesWhenNoAttemptWasMade() {
        assertTrue(
                CarPlayNowPlayingMonitor.shouldRetryCarPlayServiceBindForTest(
                        nowMs = 1_000L,
                        lastServiceBindAttemptAtMs = 0L,
                        binderAlive = false
                )
        )
    }

    @Test
    fun carPlayServiceBindRetriesOnlyAfterRetryWindow() {
        assertFalse(
                CarPlayNowPlayingMonitor.shouldRetryCarPlayServiceBindForTest(
                        nowMs = 13_000L,
                        lastServiceBindAttemptAtMs = 10_000L,
                        binderAlive = false
                )
        )
        assertTrue(
                CarPlayNowPlayingMonitor.shouldRetryCarPlayServiceBindForTest(
                        nowMs = 16_000L,
                        lastServiceBindAttemptAtMs = 10_000L,
                        binderAlive = false
                )
        )
    }

    @Test
    fun nowPlayingSubscriptionDoesNotRefreshWhenBinderIsDead() {
        assertFalse(
                CarPlayNowPlayingMonitor.shouldRefreshNowPlayingSubscriptionForTest(
                        nowMs = 30_000L,
                        lastSubscriptionAttemptAtMs = 0L,
                        lastUpdateAtMs = 0L,
                        callbackRegistered = false,
                        binderAlive = false
                )
        )
    }

    @Test
    fun nowPlayingSubscriptionRefreshesMissingCallbackAfterRetryWindow() {
        assertTrue(
                CarPlayNowPlayingMonitor.shouldRefreshNowPlayingSubscriptionForTest(
                        nowMs = 30_000L,
                        lastSubscriptionAttemptAtMs = 10_000L,
                        lastUpdateAtMs = 0L,
                        callbackRegistered = false,
                        binderAlive = true
                )
        )
    }

    @Test
    fun nowPlayingSubscriptionDoesNotRefreshTooSoonAfterAttempt() {
        assertFalse(
                CarPlayNowPlayingMonitor.shouldRefreshNowPlayingSubscriptionForTest(
                        nowMs = 20_000L,
                        lastSubscriptionAttemptAtMs = 10_000L,
                        lastUpdateAtMs = 0L,
                        callbackRegistered = false,
                        binderAlive = true
                )
        )
    }

    @Test
    fun nowPlayingSubscriptionRefreshesRegisteredCallbackOnlyWhenUpdatesAreStale() {
        assertFalse(
                CarPlayNowPlayingMonitor.shouldRefreshNowPlayingSubscriptionForTest(
                        nowMs = 25_000L,
                        lastSubscriptionAttemptAtMs = 5_000L,
                        lastUpdateAtMs = 12_000L,
                        callbackRegistered = true,
                        binderAlive = true
                )
        )

        assertTrue(
                CarPlayNowPlayingMonitor.shouldRefreshNowPlayingSubscriptionForTest(
                        nowMs = 35_000L,
                        lastSubscriptionAttemptAtMs = 5_000L,
                        lastUpdateAtMs = 12_000L,
                        callbackRegistered = true,
                        binderAlive = true
                )
        )
    }
}
