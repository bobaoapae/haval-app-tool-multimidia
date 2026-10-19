package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AaClusterStallPolicyTest {
    private val moving = 50f
    private val longAgo = Long.MAX_VALUE

    @Test
    fun freshFramesAreNotAStall() {
        assertFalse(AaClusterStallPolicy.isStalled(AaClusterStallPolicy.STALL_MS - 1))
        assertFalse(AaClusterStallPolicy.shouldRestart(1_000L, moving, longAgo))
    }

    @Test
    fun movingCarWithNoFramesRestarts() {
        assertTrue(AaClusterStallPolicy.shouldRestart(AaClusterStallPolicy.STALL_MS, moving, longAgo))
    }

    @Test
    fun parkedCarIsNeverRestarted() {
        assertTrue(AaClusterStallPolicy.isStalled(60_000L))
        assertFalse(AaClusterStallPolicy.shouldRestart(60_000L, 0f, longAgo))
        assertFalse(AaClusterStallPolicy.shouldRestart(60_000L, AaClusterStallPolicy.MIN_SPEED_KMH - 0.1f, longAgo))
    }

    @Test
    fun restartsAreBackedOff() {
        assertFalse(
            AaClusterStallPolicy.shouldRestart(
                60_000L, moving, AaClusterStallPolicy.RESTART_BACKOFF_MS - 1
            )
        )
        assertTrue(
            AaClusterStallPolicy.shouldRestart(60_000L, moving, AaClusterStallPolicy.RESTART_BACKOFF_MS)
        )
    }
}
