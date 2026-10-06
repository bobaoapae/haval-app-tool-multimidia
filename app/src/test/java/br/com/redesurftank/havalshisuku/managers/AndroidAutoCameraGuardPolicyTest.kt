package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidAutoCameraGuardPolicyTest {
    @Test
    fun ordinaryWindowsKeepExistingGuardEligible() {
        val policy = AndroidAutoCameraGuardPolicy()
        val pending = policy.generation()
        policy.onWindowChanged(false)
        assertTrue(policy.canRun(pending))
    }

    @Test
    fun cameraWindowInvalidatesEveryPendingRetryBeforeTelemetryArrives() {
        val policy = AndroidAutoCameraGuardPolicy()
        val pending = policy.generation()
        policy.onWindowChanged(true)
        // Primary, verify, late verify and final verify share the original token.
        repeat(4) { assertFalse(policy.canRun(pending)) }
        assertFalse(policy.canRun(policy.generation()))
    }

    @Test
    fun telemetryOnlyCameraOpeningBlocksGuardsWithoutWindowEvent() {
        val policy = AndroidAutoCameraGuardPolicy()
        val pending = policy.generation()
        policy.onPreviewStatus("1")
        assertFalse(policy.canRun(pending))
        assertFalse(policy.canRun(policy.generation()))
    }

    @Test
    fun systemUiWindowCannotReleaseActiveCameraTelemetry() {
        val policy = AndroidAutoCameraGuardPolicy()
        policy.onWindowChanged(true)
        policy.onPreviewStatus("1")
        policy.onWindowChanged(false)
        assertFalse(policy.canRun(policy.generation()))
    }

    @Test
    fun cameraCloseAllowsNewWorkButNeverResurrectsPreCameraRetries() {
        val policy = AndroidAutoCameraGuardPolicy()
        val pending = policy.generation()
        policy.onPreviewStatus("1")
        policy.onPreviewStatus("0")
        assertFalse(policy.canRun(pending))
        assertTrue(policy.canRun(policy.generation()))
    }

    @Test
    fun cameraCloseInvalidatesWorkCapturedWhileOpen() {
        val policy = AndroidAutoCameraGuardPolicy()
        policy.onPreviewStatus("1")
        val duringCamera = policy.generation()
        policy.onPreviewStatus("0")
        assertFalse(policy.canRun(duringCamera))
    }

    @Test
    fun closeTelemetryReleasesWindowOnlyOwnershipWhenOpenSignalWasMissed() {
        val policy = AndroidAutoCameraGuardPolicy()
        policy.onWindowChanged(true)
        val pending = policy.generation()
        policy.onPreviewStatus("0")
        assertFalse(policy.canRun(pending))
        assertTrue(policy.canRun(policy.generation()))
    }

    @Test
    fun windowOnlyCameraClosesWithoutTelemetry() {
        val policy = AndroidAutoCameraGuardPolicy()
        val before = policy.generation()
        policy.onWindowChanged(true)
        policy.onWindowChanged(false)
        assertFalse(policy.canRun(before))
        assertTrue(policy.canRun(policy.generation()))
    }

    @Test
    fun projectionAndPassiveWindowEventsDoNotReleaseWindowOnlyCamera() {
        val policy = AndroidAutoCameraGuardPolicy()
        policy.onWindowChanged(true)
        policy.onWindowChanged(null)
        assertFalse(policy.canRun(policy.generation()))
        policy.onWindowChanged(false)
        assertTrue(policy.canRun(policy.generation()))
    }

    @Test
    fun duplicateCameraEventsKeepGuardBlocked() {
        val policy = AndroidAutoCameraGuardPolicy()
        policy.onWindowChanged(true)
        policy.onPreviewStatus("1")
        val pending = policy.generation()
        policy.onWindowChanged(true)
        policy.onPreviewStatus("1")
        assertEquals(pending, policy.generation())
        assertFalse(policy.canRun(pending))
    }

    @Test
    fun repeatedCameraCyclesNeverReuseOldGeneration() {
        val policy = AndroidAutoCameraGuardPolicy()
        val old = mutableListOf<Long>()
        repeat(5) {
            old.add(policy.generation())
            policy.onPreviewStatus("1")
            policy.onPreviewStatus("0")
            old.forEach { assertFalse(policy.canRun(it)) }
            assertTrue(policy.canRun(policy.generation()))
        }
    }

    @Test
    fun duplicateSignalsDoNotInvalidateNewPostCameraWork() {
        val policy = AndroidAutoCameraGuardPolicy()
        policy.onPreviewStatus("1")
        policy.onPreviewStatus("0")
        val pending = policy.generation()
        policy.onPreviewStatus("0")
        policy.onWindowChanged(false)
        assertEquals(pending, policy.generation())
        assertTrue(policy.canRun(pending))
    }

    @Test
    fun malformedTelemetryCannotReleaseCameraOwnership() {
        val policy = AndroidAutoCameraGuardPolicy()
        policy.onPreviewStatus("1")
        listOf("", "null", "unknown", "2").forEach {
            policy.onPreviewStatus(it)
            assertFalse(policy.canRun(policy.generation()))
        }
        policy.onPreviewStatus(" 0 ")
        assertTrue(policy.canRun(policy.generation()))
    }

    @Test
    fun cameraTransitionDuringSurfaceProbeInvalidatesRecoveryAndFocusFallback() {
        val policy = AndroidAutoCameraGuardPolicy()
        val pending = policy.generation()
        assertTrue(policy.canRun(pending)) // Before a blocking Surface/task query.
        policy.onPreviewStatus("1")
        policy.onPreviewStatus("0") // Even a whole open/close cycle during the query.
        assertFalse(policy.canRun(pending)) // Must re-check before force-stop or focus.
    }
}
