package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterCardSyncPolicyTest {
    @Test
    fun intentionalNavigationInputsReleaseSyntheticAirconOwnership() {
        assertTrue(ClusterCardSyncPolicy.shouldReleaseSyntheticAirconOwnershipForInput(1026))
        assertTrue(ClusterCardSyncPolicy.shouldReleaseSyntheticAirconOwnershipForInput(1027))
        assertTrue(ClusterCardSyncPolicy.shouldReleaseSyntheticAirconOwnershipForInput(1029))
        assertTrue(ClusterCardSyncPolicy.shouldReleaseSyntheticAirconOwnershipForInput(1030))
        assertFalse(ClusterCardSyncPolicy.shouldReleaseSyntheticAirconOwnershipForInput(1024))
        assertFalse(ClusterCardSyncPolicy.shouldReleaseSyntheticAirconOwnershipForInput(1025))
        assertFalse(ClusterCardSyncPolicy.shouldReleaseSyntheticAirconOwnershipForInput(1028))
    }

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
    fun recentSyntheticNavigationIgnoresMatchingNativeEcho() {
        assertTrue(
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

    @Test
    fun recentAirconDownCommandIgnoresNativeMainMenuEcho() {
        assertTrue(
            ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                3,
                1,
                50L,
                1025,
                4282L,
                3
            )
        )
    }

    @Test
    fun recentAirconUpCommandIgnoresNativeMainMenuEcho() {
        assertTrue(
            ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                3,
                1,
                282L,
                1024,
                4000L,
                3
            )
        )
    }

    @Test
    fun recentAirconEnterCommandIgnoresNativeHiddenEcho() {
        assertTrue(
            ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                3,
                0,
                1884L,
                1028,
                4282L,
                3
            )
        )
    }

    @Test
    fun backFromAirconCanAcceptNativeMainMenuChange() {
        assertFalse(
            ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                3,
                1,
                500L,
                1030,
                -1L,
                -1
            )
        )
    }

    @Test
    fun staleAirconControlEchoCanAcceptNativeMainMenuChange() {
        assertFalse(
            ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                3,
                1,
                4000L,
                1025,
                -1L,
                -1
            )
        )
    }

    @Test
    fun lateNativeMainMenuEchoAfterSyntheticAirconEntryIsIgnored() {
        assertTrue(
            ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                3,
                1,
                1527L,
                1027,
                1536L,
                3,
                false,
                true
            )
        )
    }

    @Test
    fun appOwnedAirconIgnoresNativeMainMenuEchoWithoutLaterExitInput() {
        assertTrue(
            ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                3,
                1,
                30_001L,
                1027,
                30_000L,
                3,
                false,
                true
            )
        )
    }

    @Test
    fun backAfterSyntheticAirconEntryCanExitImmediately() {
        assertFalse(
            ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                3,
                1,
                200L,
                1030,
                -1L,
                -1,
                false,
                false
            )
        )
    }

    @Test
    fun homeAfterSyntheticAirconEntryCanExitImmediately() {
        assertFalse(
            ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                3,
                0,
                200L,
                1029,
                -1L,
                -1,
                false,
                false
            )
        )
    }

    @Test
    fun recentBackWinsEvenIfSyntheticAirconOwnershipIsStillVisible() {
        assertFalse(
            ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                3,
                1,
                5L,
                1030,
                5000L,
                3,
                false,
                true
            )
        )
    }

    @Test
    fun recentHomeWinsEvenIfSyntheticAirconOwnershipIsStillVisible() {
        assertFalse(
            ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                3,
                0,
                5L,
                1029,
                5000L,
                3,
                false,
                true
            )
        )
    }

    @Test
    fun recentBackWinsWhenProjectionAndSyntheticProtectionsAreBothActive() {
        assertFalse(
            ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                3,
                1,
                5L,
                1030,
                5000L,
                3,
                true,
                true
            )
        )
    }

    @Test
    fun projectionTransitionIgnoresAirconMainMenuEchoWithoutInput() {
        assertTrue(
            ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                3,
                1,
                -1L,
                -1,
                -1L,
                -1,
                true
            )
        )
    }

    @Test
    fun projectionTransitionAcceptsAirconBackInput() {
        assertFalse(
            ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                3,
                1,
                500L,
                1030,
                -1L,
                -1,
                true
            )
        )
    }
}
