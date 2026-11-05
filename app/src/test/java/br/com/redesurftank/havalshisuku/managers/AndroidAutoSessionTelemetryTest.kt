package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoSessionTelemetryTest {
    @Test
    fun mapsLinkedStatusesToActive() {
        assertTrue(AndroidAutoSessionTelemetry.isLinkActive(3))
        assertTrue(AndroidAutoSessionTelemetry.isLinkActive(7))
        assertTrue(AndroidAutoSessionTelemetry.isLinkActive(8))
        assertEquals("active", AndroidAutoSessionTelemetry.sessionValue(3))
        assertEquals("active", AndroidAutoSessionTelemetry.sessionValue(7))
        assertEquals("active", AndroidAutoSessionTelemetry.sessionValue(8))
    }

    @Test
    fun mapsOtherStatusesToStopped() {
        assertFalse(AndroidAutoSessionTelemetry.isLinkActive(null))
        assertFalse(AndroidAutoSessionTelemetry.isLinkActive(0))
        assertFalse(AndroidAutoSessionTelemetry.isLinkActive(5))
        assertEquals("stopped", AndroidAutoSessionTelemetry.sessionValue(null))
        assertEquals("stopped", AndroidAutoSessionTelemetry.sessionValue(0))
    }

    @Test
    fun freshNavigationEvidenceOverridesTransientStoppedStatus() {
        assertEquals(
            AndroidAutoSessionTelemetry.LINK_STATUS_ACTIVATED,
            AndroidAutoSessionTelemetry.statusWithNavigationEvidence(
                linkStatus = 0,
                navigationEvidenceRecent = true
            )
        )
        assertEquals(
            AndroidAutoSessionTelemetry.LINK_STATUS_ACTIVATED,
            AndroidAutoSessionTelemetry.statusWithNavigationEvidence(
                linkStatus = null,
                navigationEvidenceRecent = true
            )
        )
    }

    @Test
    fun staleNavigationEvidenceAllowsStoppedStatus() {
        assertEquals(
            0,
            AndroidAutoSessionTelemetry.statusWithNavigationEvidence(
                linkStatus = 0,
                navigationEvidenceRecent = false
            )
        )
        assertTrue(
            AndroidAutoSessionTelemetry.hasRecentNavigationEvidence(
                lastActiveAtMs = 1_000L,
                nowMs = 1_000L + AndroidAutoSessionTelemetry.NAVIGATION_EVIDENCE_MAX_AGE_MS
            )
        )
        assertFalse(
            AndroidAutoSessionTelemetry.hasRecentNavigationEvidence(
                lastActiveAtMs = 1_000L,
                nowMs = 1_001L + AndroidAutoSessionTelemetry.NAVIGATION_EVIDENCE_MAX_AGE_MS
            )
        )
    }

    @Test
    fun parkedGuidanceGapKeepsNavigationEvidence() {
        // Parked on a route, Maps repeats the step only every 7–10 s.
        assertTrue(
            AndroidAutoSessionTelemetry.hasRecentNavigationEvidence(
                lastActiveAtMs = 1_000L,
                nowMs = 11_000L
            )
        )
    }

    @Test
    fun guidanceRevivesOnlyAStoppedSession() {
        assertTrue(
            AndroidAutoSessionTelemetry.shouldReviveSessionFromNavigation(
                navigationActive = true,
                sessionActive = false
            )
        )
        assertFalse(
            AndroidAutoSessionTelemetry.shouldReviveSessionFromNavigation(
                navigationActive = true,
                sessionActive = true
            )
        )
        assertFalse(
            AndroidAutoSessionTelemetry.shouldReviveSessionFromNavigation(
                navigationActive = false,
                sessionActive = false
            )
        )
    }

    @Test
    fun routeAfterStoppedSessionPublishesActive() {
        // Car 2026-11-04: session stopped when a route ended, link unreadable, then a new
        // route started. The revive feeds the unreadable link with fresh guidance.
        val debouncer = AndroidAutoSessionTelemetry.Debouncer()
        assertEquals("active", debouncer.onStatus(3, 0L))
        assertNull(debouncer.onStatus(0, 100L))
        assertEquals("stopped", debouncer.onStatus(0, 100L + AndroidAutoSessionTelemetry.DEBOUNCE_STOPPED_MS))
        val revived = AndroidAutoSessionTelemetry.statusWithNavigationEvidence(
            linkStatus = null,
            navigationEvidenceRecent = AndroidAutoSessionTelemetry.hasRecentNavigationEvidence(
                lastActiveAtMs = 22_000L,
                nowMs = 22_000L
            )
        )
        assertEquals("active", debouncer.onStatus(revived, 22_000L))
    }

    @Test
    fun pollKeepsWirelessSessionWhileDcmEvidenceIsRecent() {
        assertEquals(
            AndroidAutoSessionTelemetry.LINK_STATUS_ACTIVATED,
            AndroidAutoSessionTelemetry.statusForPoll(
                linkStatus = null,
                dcmEvidenceRecent = true,
                sessionActive = true
            )
        )
    }

    @Test
    fun pollReleasesSessionWhenWirelessEvidenceIsGone() {
        assertEquals(
            0,
            AndroidAutoSessionTelemetry.statusForPoll(
                linkStatus = null,
                dcmEvidenceRecent = false,
                sessionActive = true
            )
        )
    }

    @Test
    fun pollLeavesUnreadLinkAloneWhenNoSessionIsUp() {
        assertNull(
            AndroidAutoSessionTelemetry.statusForPoll(
                linkStatus = null,
                dcmEvidenceRecent = false,
                sessionActive = false
            )
        )
    }

    @Test
    fun sevenToEightDoesNotRepublish() {
        val debouncer = AndroidAutoSessionTelemetry.Debouncer()
        assertEquals("active", debouncer.onStatus(3, 0L))
        assertNull(debouncer.onStatus(7, 10L))
        assertNull(debouncer.onStatus(8, 20L))
        assertEquals("active", debouncer.publishedValue())
    }

    @Test
    fun stoppedIsDebounced() {
        val debouncer = AndroidAutoSessionTelemetry.Debouncer()
        assertEquals("active", debouncer.onStatus(7, 0L))
        assertNull(debouncer.onStatus(0, 100L))
        assertNull(debouncer.onStatus(0, 300L))
        assertEquals("stopped", debouncer.onStatus(0, 100 + AndroidAutoSessionTelemetry.DEBOUNCE_STOPPED_MS))
    }

    @Test
    fun activeIsImmediateAfterStoppedPending() {
        val debouncer = AndroidAutoSessionTelemetry.Debouncer()
        assertEquals("active", debouncer.onStatus(8, 0L))
        assertNull(debouncer.onStatus(0, 50L))
        // Pending stop is cancelled; last published is still active so no republish.
        assertNull(debouncer.onStatus(8, 80L))
        assertEquals("active", debouncer.publishedValue())
    }
}
