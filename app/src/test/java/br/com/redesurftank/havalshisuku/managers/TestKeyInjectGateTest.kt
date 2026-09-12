package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestKeyInjectGateTest {
    @Test
    fun acceptsFileModifiedAfterBoot() {
        val now = 1_700_000_000_000L
        val uptime = 60_000L // booted 60s ago
        val modified = now - 5_000L // written 5s ago
        assertTrue(TestKeyInjectGate.isTokenFileFromCurrentBoot(modified, now, uptime))
    }

    @Test
    fun rejectsFileFromPreviousBoot() {
        val now = 1_700_000_000_000L
        val uptime = 60_000L
        val bootWall = now - uptime
        val modified = bootWall - 60_000L // one minute before boot
        assertFalse(TestKeyInjectGate.isTokenFileFromCurrentBoot(modified, now, uptime))
    }
}
