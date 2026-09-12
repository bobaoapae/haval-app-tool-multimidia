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
