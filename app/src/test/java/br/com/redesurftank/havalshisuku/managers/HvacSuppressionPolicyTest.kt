package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HvacSuppressionPolicyTest {

    // --- the lease --------------------------------------------------------

    @Test
    fun noHoldersMeansNoSuppression() {
        assertFalse(HvacSuppressionPolicy.leaseHeld(emptySet(), featureEnabled = true))
    }

    @Test
    fun aHolderSuppressesOnlyWhenTheUserAskedForIt() {
        assertTrue(HvacSuppressionPolicy.leaseHeld(setOf("viewer"), featureEnabled = true))
        assertFalse(HvacSuppressionPolicy.leaseHeld(setOf("viewer"), featureEnabled = false))
    }

    // --- the decision -----------------------------------------------------

    @Test
    fun takingTheLeaseDisablesTheOemApp() {
        assertEquals(
            HvacSuppressionPolicy.Action.DISABLE,
            HvacSuppressionPolicy.decide(leaseHeld = true, markerSet = false)
        )
    }

    @Test
    fun droppingTheLeaseGivesTheOemAppBack() {
        assertEquals(
            HvacSuppressionPolicy.Action.ENABLE,
            HvacSuppressionPolicy.decide(leaseHeld = false, markerSet = true)
        )
    }

    @Test
    fun aMatchingStateIsLeftAlone() {
        assertEquals(
            HvacSuppressionPolicy.Action.NONE,
            HvacSuppressionPolicy.decide(leaseHeld = true, markerSet = true)
        )
        assertEquals(
            HvacSuppressionPolicy.Action.NONE,
            HvacSuppressionPolicy.decide(leaseHeld = false, markerSet = false)
        )
    }

    /**
     * The case this whole object exists for: Impulse died holding the lease, or the car rebooted
     * with the OEM app disabled (`pm disable-user` persists). At the next reconcile nothing holds a
     * lease and the marker is still set, so the app must come back.
     */
    @Test
    fun aCrashOrRebootIsRecoveredOnReconcile() {
        val holdersAfterRestart = emptySet<String>()
        val held = HvacSuppressionPolicy.leaseHeld(holdersAfterRestart, featureEnabled = true)
        assertEquals(
            HvacSuppressionPolicy.Action.ENABLE,
            HvacSuppressionPolicy.decide(leaseHeld = held, markerSet = true)
        )
    }

    /** Turning the feature off must hand the car's popup back even while the viewer is bound. */
    @Test
    fun turningTheFeatureOffReleasesEvenWithAHolder() {
        val held = HvacSuppressionPolicy.leaseHeld(setOf("viewer"), featureEnabled = false)
        assertEquals(
            HvacSuppressionPolicy.Action.ENABLE,
            HvacSuppressionPolicy.decide(leaseHeld = held, markerSet = true)
        )
    }
}
