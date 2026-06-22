package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterCardSyncPolicyTest {
    @Test
    fun recentSyntheticMainMenuNavigationIgnoresDivergentNativeAcEcho() {
        assertTrue(
            ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                1,
                3,
                616L,
                1027,
                616L,
                1
            )
        )
    }

    @Test
    fun recentSyntheticNavigationAcceptsMatchingNativeEcho() {
        assertFalse(
            ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                1,
                1,
                616L,
                1027,
                616L,
                1
            )
        )
    }

    @Test
    fun staleNativeZeroWithoutRecentInputIsIgnored() {
        assertTrue(
            ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                1,
                0,
                -1L,
                -1,
                -1L,
                -1
            )
        )
    }

    @Test
    fun nativeCardChangeWithoutSyntheticNavigationIsAccepted() {
        assertFalse(
            ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                1,
                3,
                -1L,
                -1,
                -1L,
                -1
            )
        )
    }
}
