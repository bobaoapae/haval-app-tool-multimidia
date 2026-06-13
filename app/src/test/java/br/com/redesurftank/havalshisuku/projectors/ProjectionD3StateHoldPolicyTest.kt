package br.com.redesurftank.havalshisuku.projectors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionD3StateHoldPolicyTest {
    @Test
    fun holdsWhenDesiredClusterAndRecentHealthyD3SampleExists() {
        assertTrue(
                ProjectionD3StateHoldPolicy.shouldHoldCarPlayInDash(
                        lastHealthyD3AtMs = 1_000L,
                        nowMs = 7_000L,
                        desiredCluster = true,
                        preparingD3 = false,
                        holdMs = 12_000L
                )
        )
    }

    @Test
    fun holdsDuringPreparingD3EvenBeforeDesiredTargetIsPersisted() {
        assertTrue(
                ProjectionD3StateHoldPolicy.shouldHoldCarPlayInDash(
                        lastHealthyD3AtMs = 1_000L,
                        nowMs = 7_000L,
                        desiredCluster = false,
                        preparingD3 = true,
                        holdMs = 12_000L
                )
        )
    }

    @Test
    fun doesNotHoldWhenUserTargetIsNoLongerCluster() {
        assertFalse(
                ProjectionD3StateHoldPolicy.shouldHoldCarPlayInDash(
                        lastHealthyD3AtMs = 1_000L,
                        nowMs = 7_000L,
                        desiredCluster = false,
                        preparingD3 = false,
                        holdMs = 12_000L
                )
        )
    }

    @Test
    fun doesNotHoldAfterWindowExpires() {
        assertFalse(
                ProjectionD3StateHoldPolicy.shouldHoldCarPlayInDash(
                        lastHealthyD3AtMs = 1_000L,
                        nowMs = 14_001L,
                        desiredCluster = true,
                        preparingD3 = false,
                        holdMs = 12_000L
                )
        )
    }

    @Test
    fun doesNotHoldWithoutPreviousHealthyD3Sample() {
        assertFalse(
                ProjectionD3StateHoldPolicy.shouldHoldCarPlayInDash(
                        lastHealthyD3AtMs = 0L,
                        nowMs = 7_000L,
                        desiredCluster = true,
                        preparingD3 = false,
                        holdMs = 12_000L
                )
        )
    }
}
