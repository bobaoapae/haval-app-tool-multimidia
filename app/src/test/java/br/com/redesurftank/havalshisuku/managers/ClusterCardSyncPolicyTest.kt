package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterCardSyncPolicyTest {
    @Test
    fun recentSyntheticNavigationAcceptsDivergentNativeReportAsCorrection() {
        // Prediction said card 1, the car reports card 3. The car wins: dropping this
        // left the app on card 1 while the cluster showed card 3, with no later report
        // to recover from (subsequent echoes match the stale card and get deduped).
        assertFalse(
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
    fun homeKeyReportToCardZeroIsAcceptedAfterRecentPrediction() {
        // Regression, captured on-car: HOME was pressed on card 1, the car reported
        // card 0 (msgId=133 intValue=0) 635ms later, but a LEFT/RIGHT prediction to
        // card 1 had happened 1242ms earlier — inside the echo window. The report was
        // discarded, so HOME appeared dead and card 0 rendered blank.
        assertFalse(
            ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                1,
                0,
                635L,
                1029,
                1242L,
                1
            )
        )
    }

    @Test
    fun nativeZeroWithoutRecentNavigationInputIsAccepted() {
        // The car can move to card 0 without a LEFT/RIGHT press (HOME, or its own
        // logic). Requiring recent wheel input to believe it discarded those reports.
        assertFalse(
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
