package br.com.redesurftank.havalshisuku.services

import br.com.redesurftank.havalshisuku.models.CarConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarServiceProjectionMediaTest {
    @Test
    fun usbReadyAcceptsConfiguredAndConnectedStates() {
        assertTrue(BottomBarService.isProjectionUsbReadyForMedia("CONFIGURED"))
        assertTrue(BottomBarService.isProjectionUsbReadyForMedia("CONNECTED"))
        assertTrue(BottomBarService.isProjectionUsbReadyForMedia("DISCONNECTED\nCONFIGURED"))
    }

    @Test
    fun usbReadyRejectsDisconnectedOrUnknownStates() {
        assertFalse(BottomBarService.isProjectionUsbReadyForMedia("DISCONNECTED"))
        assertFalse(BottomBarService.isProjectionUsbReadyForMedia(""))
        assertFalse(BottomBarService.isProjectionUsbReadyForMedia("SUSPENDED"))
    }

    @Test
    fun clearsProjectionMediaWhenUsbDisconnects() {
        assertTrue(
                BottomBarService.shouldClearCarPlayMediaOnUsbState(
                        packageName = "com.ts.carplay",
                        rawState = "DISCONNECTED"
                )
        )
        assertTrue(
                BottomBarService.shouldClearCarPlayMediaOnUsbState(
                        packageName = "com.ts.carplay.app",
                        rawState = "DISCONNECTED"
                )
        )
        assertFalse(
                BottomBarService.shouldClearCarPlayMediaOnUsbState(
                        packageName = "com.ts.androidauto",
                        rawState = "DISCONNECTED"
                )
        )
        assertTrue(
                BottomBarService.shouldClearAndroidAutoMediaOnUsbState(
                        packageName = "com.ts.androidauto",
                        rawState = "DISCONNECTED"
                )
        )
        assertTrue(
                BottomBarService.shouldClearAndroidAutoMediaOnUsbState(
                        packageName = "com.ts.androidauto.app",
                        rawState = "DISCONNECTED"
                )
        )
        assertFalse(
                BottomBarService.shouldClearAndroidAutoMediaOnUsbState(
                        packageName = "com.ts.androidauto",
                        rawState = "DISCONNECTED",
                        androidAutoSessionReady = true
                )
        )
        assertFalse(
                BottomBarService.shouldClearAndroidAutoMediaOnUsbState(
                        packageName = "com.ts.carplay",
                        rawState = "DISCONNECTED"
                )
        )
        assertFalse(
                BottomBarService.shouldClearCarPlayMediaOnUsbState(
                        packageName = "com.ts.carplay",
                        rawState = "CONFIGURED"
                )
        )
        assertFalse(
                BottomBarService.shouldClearAndroidAutoMediaOnUsbState(
                        packageName = "com.ts.androidauto",
                        rawState = "CONFIGURED"
                )
        )
    }

    @Test
    fun androidAutoFallbackMetadataIsAcceptedFromPhoneSourcesWhileProjectionSessionIsActive() {
        assertTrue(
                BottomBarService.shouldUseAndroidAutoProjectionFallbackMediaPackageForTest(
                        candidatePackageName = "com.android.bluetooth",
                        candidateHasMetadata = true,
                        currentMediaPackageName = "com.ts.androidauto",
                        activeClusterProjectionPackage = "",
                        androidAutoSessionReady = true
                )
        )
        assertTrue(
                BottomBarService.shouldUseAndroidAutoProjectionFallbackMediaPackageForTest(
                        candidatePackageName = "com.onecar.onlinemusic",
                        candidateHasMetadata = true,
                        currentMediaPackageName = null,
                        activeClusterProjectionPackage = "com.ts.androidauto",
                        androidAutoSessionReady = true
                )
        )
    }

    @Test
    fun androidAutoBluetoothFallbackIsRejectedWhenProjectionSessionIsUnavailable() {
        assertFalse(
                BottomBarService.shouldUseAndroidAutoProjectionFallbackMediaPackageForTest(
                        candidatePackageName = "com.android.bluetooth",
                        candidateHasMetadata = true,
                        currentMediaPackageName = "com.ts.androidauto",
                        activeClusterProjectionPackage = "",
                        androidAutoSessionReady = false,
                        nativeAndroidAutoMediaCenterActive = true
                )
        )
    }

    @Test
    fun androidAutoProjectionStateIsNotPreservedForBluetoothWhenSessionIsUnavailable() {
        assertFalse(
                BottomBarService.shouldKeepProjectionMediaStateForTest(
                        currentMediaPackageName = "com.ts.androidauto",
                        candidatePackageName = "com.android.bluetooth",
                        candidateHasMetadata = true,
                        androidAutoSessionReady = false
                )
        )
        assertTrue(
                BottomBarService.shouldKeepProjectionMediaStateForTest(
                        currentMediaPackageName = "com.ts.androidauto",
                        candidatePackageName = "com.android.bluetooth",
                        candidateHasMetadata = true,
                        androidAutoSessionReady = true
                )
        )
    }

    @Test
    fun androidAutoFallbackMetadataDoesNotOverrideCarPlayNativeRadioOrUnknownSessions() {
        assertFalse(
                BottomBarService.shouldUseAndroidAutoProjectionFallbackMediaPackageForTest(
                        candidatePackageName = "com.android.bluetooth",
                        candidateHasMetadata = true,
                        currentMediaPackageName = "com.ts.carplay",
                        activeClusterProjectionPackage = "",
                        androidAutoSessionReady = true
                )
        )
        assertFalse(
                BottomBarService.shouldUseAndroidAutoProjectionFallbackMediaPackageForTest(
                        candidatePackageName = "com.android.bluetooth",
                        candidateHasMetadata = true,
                        currentMediaPackageName = null,
                        activeClusterProjectionPackage = "",
                        androidAutoSessionReady = false
                )
        )
        assertFalse(
                BottomBarService.shouldUseAndroidAutoProjectionFallbackMediaPackageForTest(
                        candidatePackageName = "com.beantechs.mediacenter",
                        candidateHasMetadata = true,
                        currentMediaPackageName = "com.ts.androidauto",
                        activeClusterProjectionPackage = "",
                        androidAutoSessionReady = true,
                        nativeAndroidAutoMediaCenterActive = false
                )
        )
        assertFalse(
                BottomBarService.shouldUseAndroidAutoProjectionFallbackMediaPackageForTest(
                        candidatePackageName = "com.beantechs.mediacenter.h5.core",
                        candidateHasMetadata = true,
                        currentMediaPackageName = "com.ts.androidauto",
                        activeClusterProjectionPackage = "",
                        androidAutoSessionReady = true,
                        nativeAndroidAutoMediaCenterActive = false
                )
        )
        assertFalse(
                BottomBarService.shouldUseAndroidAutoProjectionFallbackMediaPackageForTest(
                        candidatePackageName = "com.beantechs.mediacenter",
                        candidateHasMetadata = true,
                        currentMediaPackageName = null,
                        activeClusterProjectionPackage = "",
                        androidAutoSessionReady = true,
                        nativeAndroidAutoMediaCenterActive = false
                )
        )
        assertFalse(
                BottomBarService.shouldUseAndroidAutoProjectionFallbackMediaPackageForTest(
                        candidatePackageName = "com.android.bluetooth",
                        candidateHasMetadata = false,
                        currentMediaPackageName = "com.ts.androidauto",
                        activeClusterProjectionPackage = "",
                        androidAutoSessionReady = false
                )
        )
    }

    @Test
    fun androidAutoNativeMediaCenterFallbackIsAcceptedOnlyWhenNativeSourceIsActive() {
        assertTrue(
                BottomBarService.shouldUseAndroidAutoProjectionFallbackMediaPackageForTest(
                        candidatePackageName = "com.beantechs.mediacenter",
                        candidateHasMetadata = true,
                        currentMediaPackageName = null,
                        activeClusterProjectionPackage = "",
                        androidAutoSessionReady = false,
                        nativeAndroidAutoMediaCenterActive = true
                )
        )
        assertTrue(
                BottomBarService.shouldUseAndroidAutoProjectionFallbackMediaPackageForTest(
                        candidatePackageName = "com.beantechs.mediacenter.h5.core",
                        candidateHasMetadata = true,
                        currentMediaPackageName = "com.ts.androidauto",
                        activeClusterProjectionPackage = "",
                        androidAutoSessionReady = false,
                        nativeAndroidAutoMediaCenterActive = true
                )
        )
        assertFalse(
                BottomBarService.shouldUseAndroidAutoProjectionFallbackMediaPackageForTest(
                        candidatePackageName = "com.beantechs.mediacenter",
                        candidateHasMetadata = true,
                        currentMediaPackageName = null,
                        activeClusterProjectionPackage = "",
                        androidAutoSessionReady = false,
                        nativeAndroidAutoMediaCenterActive = false
                )
        )
    }

    @Test
    fun androidAutoFallbackPlaybackCannotDemoteCurrentNativePlayback() {
        assertFalse(
                BottomBarService.shouldApplyAndroidAutoProjectionFallbackPlaybackStateForTest(
                        currentMediaPackageName = "com.ts.androidauto",
                        fallbackIsPlaying = false
                )
        )
        assertTrue(
                BottomBarService.shouldApplyAndroidAutoProjectionFallbackPlaybackStateForTest(
                        currentMediaPackageName = "com.ts.androidauto",
                        fallbackIsPlaying = true
                )
        )
        assertTrue(
                BottomBarService.shouldApplyAndroidAutoProjectionFallbackPlaybackStateForTest(
                        currentMediaPackageName = "com.beantechs.mediacenter",
                        fallbackIsPlaying = false
                )
        )
    }

    @Test
    fun activeAndroidAutoProjectionClearDoesNotDemotePlaybackState() {
        assertFalse(
                BottomBarService.shouldApplyAndroidAutoNowPlayingClearForTest(
                        projectionSessionReady = true
                )
        )
        assertTrue(
                BottomBarService.shouldApplyAndroidAutoNowPlayingClearForTest(
                        projectionSessionReady = false
                )
        )
        assertTrue(
                BottomBarService.shouldApplyAndroidAutoNowPlayingClearForTest(
                        projectionSessionReady = true,
                        clearReason = "Android Auto link inactive"
                )
        )
        assertTrue(
                BottomBarService.isForcedAndroidAutoNowPlayingClearReasonForTest(
                        "service disconnected"
                )
        )
        assertFalse(
                BottomBarService.isForcedAndroidAutoNowPlayingClearReasonForTest(
                        "empty metadata callback"
                )
        )
    }

    @Test
    fun androidAutoMediaCommandsAreDebounced() {
        assertTrue(
                BottomBarService.shouldAcceptAndroidAutoMediaCommandForTest(
                        nowMs = 1_000L,
                        lastCommandAtMs = 0L
                )
        )
        assertFalse(
                BottomBarService.shouldAcceptAndroidAutoMediaCommandForTest(
                        nowMs = 1_300L,
                        lastCommandAtMs = 1_000L
                )
        )
        assertTrue(
                BottomBarService.shouldAcceptAndroidAutoMediaCommandForTest(
                        nowMs = 1_800L,
                        lastCommandAtMs = 1_000L
                )
        )
    }

    @Test
    fun androidAutoToggleCommandsUseLongerDebounceWindow() {
        assertFalse(
                BottomBarService.shouldAcceptAndroidAutoMediaCommandForTest(
                        nowMs = 2_500L,
                        lastCommandAtMs = 1_000L,
                        cooldownMs = 2_000L
                )
        )
        assertTrue(
                BottomBarService.shouldAcceptAndroidAutoMediaCommandForTest(
                        nowMs = 3_200L,
                        lastCommandAtMs = 1_000L,
                        cooldownMs = 2_000L
                )
        )
    }

    @Test
    fun androidAutoPlaybackDirectFallbackOnlyWhenMonitorUnavailable() {
        assertFalse(
                BottomBarService.shouldUseAndroidAutoPlaybackDirectFallbackForTest(
                        monitorAvailable = true
                )
        )
        assertTrue(
                BottomBarService.shouldUseAndroidAutoPlaybackDirectFallbackForTest(
                        monitorAvailable = false
                )
        )
    }

    @Test
    fun androidAutoPlaybackCommandStateUsesNativePlayingOverVisualPaused() {
        assertTrue(
                BottomBarService.resolveAndroidAutoPlaybackCommandStateForTest(
                        visualIsPlaying = false,
                        nativeIsPlaying = true,
                        musicStatus = null
                )
        )
    }

    @Test
    fun androidAutoPlaybackCommandStateUsesActiveAudioOverStaleNativePaused() {
        assertTrue(
                BottomBarService.resolveAndroidAutoPlaybackCommandStateForTest(
                        visualIsPlaying = false,
                        nativeIsPlaying = false,
                        musicStatus = 0,
                        audioPlaybackActive = true
                )
        )
    }

    @Test
    fun androidAutoPlaybackCommandStateIgnoresStoppedAudioWhenStatusIsNotStart() {
        assertTrue(
                BottomBarService.resolveAndroidAutoPlaybackCommandStateForTest(
                        visualIsPlaying = true,
                        nativeIsPlaying = true,
                        musicStatus = 0,
                        audioPlaybackActive = false
                )
        )
    }

    @Test
    fun androidAutoPlaybackCommandStateUsesMusicPlayingOverNativePaused() {
        assertTrue(
                BottomBarService.resolveAndroidAutoPlaybackCommandStateForTest(
                        visualIsPlaying = false,
                        nativeIsPlaying = false,
                        musicStatus = 1
                )
        )
    }

    @Test
    fun androidAutoPlaybackCommandStateUsesNativePausedWhenStatusIsKnownNotPlaying() {
        assertFalse(
                BottomBarService.resolveAndroidAutoPlaybackCommandStateForTest(
                        visualIsPlaying = true,
                        nativeIsPlaying = false,
                        musicStatus = 2
                )
        )
    }

    @Test
    fun androidAutoPlaybackCommandStateIgnoresNativePausedWhenStatusIsNotStart() {
        assertTrue(
                BottomBarService.resolveAndroidAutoPlaybackCommandStateForTest(
                        visualIsPlaying = true,
                        nativeIsPlaying = false,
                        musicStatus = 0,
                        audioPlaybackActive = false
                )
        )
    }

    @Test
    fun androidAutoPlaybackCommandStateUsesNativeRouteGuessWhenStateIsInconclusive() {
        assertTrue(
                BottomBarService.resolveAndroidAutoPlaybackCommandStateForTest(
                        visualIsPlaying = false,
                        nativeIsPlaying = null,
                        musicStatus = 0,
                        audioPlaybackActive = false,
                        nativeMediaCenterRouteActive = true,
                        nativePlaybackGuess = true
                )
        )
        assertFalse(
                BottomBarService.resolveAndroidAutoPlaybackCommandStateForTest(
                        visualIsPlaying = false,
                        nativeIsPlaying = null,
                        musicStatus = 0,
                        audioPlaybackActive = false,
                        nativeMediaCenterRouteActive = true,
                        nativePlaybackGuess = false
                )
        )
    }

    @Test
    fun androidAutoPlaybackToggleRouteIsNotUsedForDashboardCommands() {
        assertFalse(
                BottomBarService.shouldUseAndroidAutoPlaybackToggleForInconclusiveStateForTest(
                        nativeMediaCenterRouteActive = true,
                        musicStatus = 0,
                        audioPlaybackActive = false
                )
        )
        assertFalse(
                BottomBarService.shouldUseAndroidAutoPlaybackToggleForInconclusiveStateForTest(
                        nativeMediaCenterRouteActive = true,
                        musicStatus = 1,
                        audioPlaybackActive = false
                )
        )
        assertFalse(
                BottomBarService.shouldUseAndroidAutoPlaybackToggleForInconclusiveStateForTest(
                        nativeMediaCenterRouteActive = true,
                        musicStatus = 2,
                        audioPlaybackActive = false
                )
        )
        assertFalse(
                BottomBarService.shouldUseAndroidAutoPlaybackToggleForInconclusiveStateForTest(
                        nativeMediaCenterRouteActive = false,
                        musicStatus = 0,
                        audioPlaybackActive = false
                )
        )
    }

    @Test
    fun androidAutoPauseOemAudioFocusIsReleasedOnlyForPlayTarget() {
        assertFalse(
                BottomBarService.shouldReleaseAndroidAutoPauseOemAudioFocusForTest(
                        targetPlaying = false
                )
        )
        assertTrue(
                BottomBarService.shouldReleaseAndroidAutoPauseOemAudioFocusForTest(
                        targetPlaying = true
                )
        )
    }

    @Test
    fun androidAutoPauseDoesNotRequireSustainedVerificationInRollbackPath() {
        assertFalse(
                BottomBarService.shouldRequireAndroidAutoPauseSustainedVerificationForTest(
                        targetPlaying = false
                )
        )
        assertFalse(
                BottomBarService.shouldRequireAndroidAutoPauseSustainedVerificationForTest(
                        targetPlaying = true
                )
        )
    }

    @Test
    fun androidAutoGenericMediaButtonFallbackIsAllowedForPlaybackTargetsInRollbackPath() {
        assertTrue(
                BottomBarService.shouldUseAndroidAutoMediaButtonFallbackForPlaybackForTest(
                        targetPlaying = false
                )
        )
        assertTrue(
                BottomBarService.shouldUseAndroidAutoMediaButtonFallbackForPlaybackForTest(
                        targetPlaying = true
                )
        )
    }

    @Test
    fun androidAutoAapPauseFallbackIsAllowedAfterDirectPauseWasSentInRollbackPath() {
        assertFalse(
                BottomBarService.shouldSkipAndroidAutoAapPauseFallbackAfterDirectRouteForTest(
                        targetPlaying = false,
                        directHandled = true
                )
        )
        assertFalse(
                BottomBarService.shouldSkipAndroidAutoAapPauseFallbackAfterDirectRouteForTest(
                        targetPlaying = false,
                        directHandled = false
                )
        )
        assertFalse(
                BottomBarService.shouldSkipAndroidAutoAapPauseFallbackAfterDirectRouteForTest(
                        targetPlaying = true,
                        directHandled = true
                )
        )
    }

    @Test
    fun androidAutoPauseVerificationRejectsPausedStatusWhenAudioStillActive() {
        assertFalse(
                BottomBarService.shouldVerifyAndroidAutoPlaybackTargetForTest(
                        targetPlaying = false,
                        musicStatus = 2,
                        audioPlaybackActive = true
                )
        )
    }

    @Test
    fun androidAutoPauseVerificationAcceptsWhenAudioStopped() {
        assertTrue(
                BottomBarService.shouldVerifyAndroidAutoPlaybackTargetForTest(
                        targetPlaying = false,
                        musicStatus = 2,
                        audioPlaybackActive = false
                )
        )
    }

    @Test
    fun androidAutoPauseVerificationRejectsNotStartStatus() {
        assertFalse(
                BottomBarService.shouldVerifyAndroidAutoPlaybackTargetForTest(
                        targetPlaying = false,
                        musicStatus = 0,
                        audioPlaybackActive = false
                )
        )
    }

    @Test
    fun androidAutoPauseVerificationAcceptsStoppedAudioAfterKnownActivePlayback() {
        assertTrue(
                BottomBarService.shouldVerifyAndroidAutoPlaybackTargetForTest(
                        targetPlaying = false,
                        musicStatus = 0,
                        audioPlaybackActive = false,
                        allowStoppedAudioAsPause = true
                )
        )
    }

    @Test
    fun androidAutoPauseVerificationRejectsStoppedAudioFallbackWhenAudioStillActive() {
        assertFalse(
                BottomBarService.shouldVerifyAndroidAutoPlaybackTargetForTest(
                        targetPlaying = false,
                        musicStatus = 0,
                        audioPlaybackActive = true,
                        allowStoppedAudioAsPause = true
                )
        )
    }

    @Test
    fun androidAutoPlayVerificationAcceptsAudioEvidence() {
        assertTrue(
                BottomBarService.shouldVerifyAndroidAutoPlaybackTargetForTest(
                        targetPlaying = true,
                        musicStatus = 0,
                        audioPlaybackActive = true
                )
        )
    }

    @Test
    fun nativeAndroidAutoPlaybackCommandTargetHoldsAgainstImmediateStaleOppositeState() {
        assertTrue(
                BottomBarService.shouldHoldNativeAndroidAutoPlaybackCommandTargetForTest(
                        targetPlaying = false,
                        targetAtMs = 1_000L,
                        targetElapsedMs = 60_000L,
                        incomingIsPlaying = true,
                        incomingElapsedMs = 60_200L,
                        nowMs = 2_500L
                )
        )
    }

    @Test
    fun nativeAndroidAutoPlaybackCommandTargetStaysStableWhenNativeStateMatches() {
        assertTrue(
                BottomBarService.shouldHoldNativeAndroidAutoPlaybackCommandTargetForTest(
                        targetPlaying = false,
                        targetAtMs = 1_000L,
                        targetElapsedMs = 60_000L,
                        incomingIsPlaying = false,
                        incomingElapsedMs = 60_000L,
                        nowMs = 2_500L
                )
        )
    }

    @Test
    fun nativeAndroidAutoPlaybackCommandTargetClearsAfterExternalProgress() {
        assertFalse(
                BottomBarService.shouldHoldNativeAndroidAutoPlaybackCommandTargetForTest(
                        targetPlaying = false,
                        targetAtMs = 1_000L,
                        targetElapsedMs = 60_000L,
                        incomingIsPlaying = true,
                        incomingElapsedMs = 64_500L,
                        nowMs = 10_000L
                )
        )
    }

    @Test
    fun nativeAndroidAutoPlaybackCommandTargetExpires() {
        assertFalse(
                BottomBarService.shouldHoldNativeAndroidAutoPlaybackCommandTargetForTest(
                        targetPlaying = false,
                        targetAtMs = 1_000L,
                        targetElapsedMs = 60_000L,
                        incomingIsPlaying = true,
                        incomingElapsedMs = 60_000L,
                        nowMs = 50_000L
                )
        )
    }

    @Test
    fun nativeAndroidAutoPlayStateAcceptsRequestedSourceAndZeroSource() {
        assertTrue(
                BottomBarService.shouldAcceptNativeAndroidAutoPlayStateForTest(
                        playStateMediaSource = 402,
                        mediaInfoSource = null
                )
        )
        assertTrue(
                BottomBarService.shouldAcceptNativeAndroidAutoPlayStateForTest(
                        playStateMediaSource = 0,
                        mediaInfoSource = null
                )
        )
        assertTrue(
                BottomBarService.shouldAcceptNativeAndroidAutoPlayStateForTest(
                        playStateMediaSource = 0,
                        mediaInfoSource = 402
                )
        )
        assertFalse(
                BottomBarService.shouldAcceptNativeAndroidAutoPlayStateForTest(
                        playStateMediaSource = 403,
                        mediaInfoSource = 402
                )
        )
    }

    @Test
    fun mediaProgressEstimateAdvancesBetweenNativeMetadataPolls() {
        assertEquals(
                55_500L,
                BottomBarService.resolveNativeAndroidAutoProgressElapsedForTest(
                        previousElapsedMs = 54_000L,
                        durationMs = 180_000L,
                        progressUpdatedAtMs = 1_000L,
                        nowMs = 2_500L,
                        isPlaying = true,
                        trackChanged = false,
                        nativeElapsedMs = null
                )
        )
    }

    @Test
    fun mediaProgressEstimateDoesNotAdvanceWhenPaused() {
        assertEquals(
                54_000L,
                BottomBarService.resolveNativeAndroidAutoProgressElapsedForTest(
                        previousElapsedMs = 54_000L,
                        durationMs = 180_000L,
                        progressUpdatedAtMs = 1_000L,
                        nowMs = 2_500L,
                        isPlaying = false,
                        trackChanged = false,
                        nativeElapsedMs = null
                )
        )
    }

    @Test
    fun nativeElapsedWinsForNativeAndroidAutoProgress() {
        assertEquals(
                61_000L,
                BottomBarService.resolveNativeAndroidAutoProgressElapsedForTest(
                        previousElapsedMs = 54_000L,
                        durationMs = 180_000L,
                        progressUpdatedAtMs = 1_000L,
                        nowMs = 2_500L,
                        isPlaying = true,
                        trackChanged = false,
                        nativeElapsedMs = 61_000L
                )
        )
    }

    @Test
    fun nativeAndroidAutoProgressIgnoresTransientZeroRegression() {
        assertEquals(
                55_500L,
                BottomBarService.resolveNativeAndroidAutoProgressElapsedForTest(
                        previousElapsedMs = 54_000L,
                        durationMs = 180_000L,
                        progressUpdatedAtMs = 1_000L,
                        nowMs = 2_500L,
                        isPlaying = true,
                        trackChanged = false,
                        nativeElapsedMs = 0L
                )
        )
    }

    @Test
    fun nativeAndroidAutoProgressIgnoresTransientZeroRegressionWhenStatusIsFalse() {
        assertEquals(
                54_000L,
                BottomBarService.resolveNativeAndroidAutoProgressElapsedForTest(
                        previousElapsedMs = 54_000L,
                        durationMs = 180_000L,
                        progressUpdatedAtMs = 1_000L,
                        nowMs = 2_500L,
                        isPlaying = false,
                        trackChanged = false,
                        nativeElapsedMs = 0L
                )
        )
    }

    @Test
    fun nativeAndroidAutoProgressAllowsZeroOnTrackChange() {
        assertEquals(
                0L,
                BottomBarService.resolveNativeAndroidAutoProgressElapsedForTest(
                        previousElapsedMs = 54_000L,
                        durationMs = 180_000L,
                        progressUpdatedAtMs = 1_000L,
                        nowMs = 2_500L,
                        isPlaying = true,
                        trackChanged = true,
                        nativeElapsedMs = 0L
                )
        )
    }

    @Test
    fun nativeMetadataTrackChangeWithoutElapsedResetsProgress() {
        assertEquals(
                0L,
                BottomBarService.resolveNativeAndroidAutoProgressElapsedForTest(
                        previousElapsedMs = 54_000L,
                        durationMs = 180_000L,
                        progressUpdatedAtMs = 1_000L,
                        nowMs = 2_500L,
                        isPlaying = true,
                        trackChanged = true,
                        nativeElapsedMs = null
                )
        )
    }

    @Test
    fun mediaProgressEstimateClampsToDuration() {
        assertEquals(
                180_000L,
                BottomBarService.resolveNativeAndroidAutoProgressElapsedForTest(
                        previousElapsedMs = 179_500L,
                        durationMs = 180_000L,
                        progressUpdatedAtMs = 1_000L,
                        nowMs = 4_000L,
                        isPlaying = true,
                        trackChanged = false,
                        nativeElapsedMs = null
                )
        )
    }

    @Test
    fun parsesAudioMuteStateFromCentralValues() {
        assertTrue(BottomBarService.parseAudioMuteState("1")!!)
        assertTrue(BottomBarService.parseAudioMuteState("true")!!)
        assertTrue(BottomBarService.parseAudioMuteState("muted")!!)

        assertFalse(BottomBarService.parseAudioMuteState("0")!!)
        assertFalse(BottomBarService.parseAudioMuteState("false")!!)
        assertFalse(BottomBarService.parseAudioMuteState("unmuted")!!)

        assertNull(BottomBarService.parseAudioMuteState(""))
        assertNull(BottomBarService.parseAudioMuteState("unknown"))
        assertNull(BottomBarService.parseAudioMuteState(null))
    }

    @Test
    fun audioMuteTargetResolutionTreatsAnyDivergentStateAsMismatch() {
        assertFalse(
                BottomBarService.resolveAudioMuteStateForTargetForTest(
                        states = listOf(true, false),
                        targetMuted = true
                )!!
        )
        assertTrue(
                BottomBarService.resolveAudioMuteStateForTargetForTest(
                        states = listOf(false, true),
                        targetMuted = false
                )!!
        )
        assertTrue(
                BottomBarService.resolveAudioMuteStateForTargetForTest(
                        states = listOf(true, true),
                        targetMuted = true
                )!!
        )
        assertFalse(
                BottomBarService.resolveAudioMuteStateForTargetForTest(
                        states = listOf(false, false),
                        targetMuted = false
                )!!
        )
        assertNull(
                BottomBarService.resolveAudioMuteStateForTargetForTest(
                        states = emptyList(),
                        targetMuted = true
                )
        )
    }

    @Test
    fun audioMuteStateTreatsMediaOrSystemMuteAsActive() {
        assertTrue(
                BottomBarService.resolveAudioMuteStateWithSystemMuteFallbackForTest(
                        mediaStates = listOf(true),
                        systemStates = listOf(false),
                        targetMuted = true
                )!!
        )
        assertTrue(
                BottomBarService.resolveAudioMuteStateWithSystemMuteFallbackForTest(
                        mediaStates = listOf(false),
                        systemStates = listOf(true),
                        targetMuted = true
                )!!
        )
        assertTrue(
                BottomBarService.resolveAudioMuteStateWithSystemMuteFallbackForTest(
                        mediaStates = listOf(false),
                        systemStates = listOf(true),
                        targetMuted = false
                )!!
        )
        assertFalse(
                BottomBarService.resolveAudioMuteStateWithSystemMuteFallbackForTest(
                        mediaStates = listOf(false),
                        systemStates = listOf(false),
                        targetMuted = false
                )!!
        )
    }

    @Test
    fun nativeAudioMuteStateValueMatchesCentralMediaMuteContract() {
        assertEquals("1", BottomBarService.resolveNativeAudioMuteStateValueForTest(true))
        assertEquals("0", BottomBarService.resolveNativeAudioMuteStateValueForTest(false))
    }

    @Test
    fun audioMuteStateTreatsZeroMediaVolumeAsMuted() {
        assertTrue(
                BottomBarService.resolveAudioMuteStateWithVolumeForTest(
                        mutedState = false,
                        mediaVolume = 0,
                        targetMuted = true
                )!!
        )
        assertFalse(
                BottomBarService.resolveAudioMuteStateWithVolumeForTest(
                        mutedState = true,
                        mediaVolume = 8,
                        targetMuted = false
                )!!
        )
        assertTrue(
                BottomBarService.resolveAudioMuteStateWithVolumeForTest(
                        mutedState = true,
                        mediaVolume = null,
                        targetMuted = null
                )!!
        )
    }

    @Test
    fun nativeRadioSignalsProtectAndroidAutoMediaRoute() {
        assertTrue(
                BottomBarService.shouldProtectNativeRadioForTest(
                        key = CarConstants.SYS_RADIO_PLAY_STATE.getValue(),
                        value = "1",
                        fromInitialSnapshot = true
                )
        )
        assertTrue(
                BottomBarService.shouldProtectNativeRadioForTest(
                        key = CarConstants.SYS_BASIC_AUDIO_SOURCE_APP.getValue(),
                        value = "com.beantechs.radio",
                        fromInitialSnapshot = true
                )
        )
        assertTrue(
                BottomBarService.shouldProtectNativeRadioForTest(
                        key = CarConstants.SYS_RADIO_CUR_CHANNEL_INFO.getValue(),
                        value = "{84300,0,1,0}",
                        fromInitialSnapshot = false
                )
        )

        assertFalse(
                BottomBarService.shouldProtectNativeRadioForTest(
                        key = CarConstants.SYS_RADIO_PLAY_STATE.getValue(),
                        value = "0",
                        fromInitialSnapshot = true
                )
        )
        assertFalse(
                BottomBarService.shouldProtectNativeRadioForTest(
                        key = CarConstants.SYS_RADIO_CUR_CHANNEL_INFO.getValue(),
                        value = "{84300,0,1,0}",
                        fromInitialSnapshot = true
                )
        )
        assertFalse(
                BottomBarService.shouldProtectNativeRadioForTest(
                        key = CarConstants.SYS_BASIC_AUDIO_SOURCE_APP.getValue(),
                        value = "com.ts.androidauto.projectionservice",
                        fromInitialSnapshot = true
                )
        )
    }

    @Test
    fun nativeMediaCenterRadioSourceProtectsAndroidAutoMediaRoute() {
        assertTrue(
                BottomBarService.shouldProtectNativeMediaCenterSourceForTest(
                        currentSource = 12,
                        currentAudioSource = 402
                )
        )
        assertTrue(
                BottomBarService.shouldProtectNativeMediaCenterSourceForTest(
                        currentSource = 402,
                        currentAudioSource = 10
                )
        )

        assertFalse(
                BottomBarService.shouldProtectNativeMediaCenterSourceForTest(
                        currentSource = 402,
                        currentAudioSource = 402
                )
        )
        assertFalse(
                BottomBarService.shouldProtectNativeMediaCenterSourceForTest(
                        currentSource = 403,
                        currentAudioSource = 4
                )
        )
        assertFalse(
                BottomBarService.shouldProtectNativeMediaCenterSourceForTest(
                        currentSource = null,
                        currentAudioSource = null
                )
        )
    }

    @Test
    fun nativeRadioProtectionIsIgnoredOnlyForClearAndroidAutoMediaCenterSource() {
        assertTrue(
                BottomBarService.shouldIgnoreNativeRadioProtectionForAndroidAutoSourceForTest(
                        currentSource = 402,
                        currentAudioSource = 402
                )
        )
        assertFalse(
                BottomBarService.shouldIgnoreNativeRadioProtectionForAndroidAutoSourceForTest(
                        currentSource = 12,
                        currentAudioSource = 402
                )
        )
        assertFalse(
                BottomBarService.shouldIgnoreNativeRadioProtectionForAndroidAutoSourceForTest(
                        currentSource = 402,
                        currentAudioSource = 10
                )
        )
        assertFalse(
                BottomBarService.shouldIgnoreNativeRadioProtectionForAndroidAutoSourceForTest(
                        currentSource = null,
                        currentAudioSource = null
                )
        )
    }

    @Test
    fun nativeMediaCenterAndroidAutoSourceEnablesMetadataRoute() {
        assertTrue(
                BottomBarService.shouldUseNativeAndroidAutoMediaCenterMetadataForTest(
                        currentSource = 402,
                        currentAudioSource = null
                )
        )
        assertTrue(
                BottomBarService.shouldUseNativeAndroidAutoMediaCenterMetadataForTest(
                        currentSource = null,
                        currentAudioSource = 402
                )
        )
        assertFalse(
                BottomBarService.shouldUseNativeAndroidAutoMediaCenterMetadataForTest(
                        currentSource = 12,
                        currentAudioSource = 10
                )
        )
    }

    @Test
    fun externalFocusRestoresBarOnlyWhenBottomBarWasActive() {
        assertTrue(
                BottomBarService.shouldRestoreBarAfterExternalFocusForTest(
                        packageName = "com.beantechs.hvac",
                        ownPackageName = "br.com.redesurftank.havalshisuku",
                        isVisible = false,
                        isDashboardExpanded = true
                )
        )
        assertTrue(
                BottomBarService.shouldRestoreBarAfterExternalFocusForTest(
                        packageName = "com.beantechs.applist",
                        ownPackageName = "br.com.redesurftank.havalshisuku",
                        isVisible = true,
                        isDashboardExpanded = false
                )
        )
        assertFalse(
                BottomBarService.shouldRestoreBarAfterExternalFocusForTest(
                        packageName = "br.com.redesurftank.havalshisuku",
                        ownPackageName = "br.com.redesurftank.havalshisuku",
                        isVisible = true,
                        isDashboardExpanded = true
                )
        )
        assertFalse(
                BottomBarService.shouldRestoreBarAfterExternalFocusForTest(
                        packageName = "com.beantechs.hvac",
                        ownPackageName = "br.com.redesurftank.havalshisuku",
                        isVisible = false,
                        isDashboardExpanded = false
                )
        )
    }

    @Test
    fun passiveExternalFocusDoesNotCollapseExpandedDashboard() {
        listOf(
                        "com.android.systemui",
                        "com.beantechs.launcher",
                        "com.beantechs.mediacenter",
                        "com.beantechs.mediacenter.h5.core",
                        "com.beantechs.vehiclecenter",
                        "com.ts.androidauto",
                        "com.ts.androidauto.app",
                        "com.ts.androidauto.projectionservice",
                        "com.ts.carplay",
                        "com.ts.carplay.app"
                )
                .forEach { packageName ->
                    assertFalse(
                            packageName,
                            BottomBarService.shouldRestoreBarAfterExternalFocusForTest(
                                    packageName = packageName,
                                    ownPackageName = "br.com.redesurftank.havalshisuku",
                                    isVisible = true,
                                    isDashboardExpanded = true
                            )
                    )
                }
    }

    @Test
    fun suppressedDashboardControlFocusDoesNotCollapseExpandedDashboard() {
        assertFalse(
                BottomBarService.shouldRestoreBarAfterExternalFocusForTest(
                        packageName = "com.beantechs.settings",
                        ownPackageName = "br.com.redesurftank.havalshisuku",
                        isVisible = true,
                        isDashboardExpanded = true,
                        dashboardControlFocusRestoreSuppressed = true
                )
        )
        assertTrue(
                BottomBarService.shouldRestoreBarAfterExternalFocusForTest(
                        packageName = "com.beantechs.settings",
                        ownPackageName = "br.com.redesurftank.havalshisuku",
                        isVisible = true,
                        isDashboardExpanded = true,
                        dashboardControlFocusRestoreSuppressed = false
                )
        )
    }

    @Test
    fun dashboardControlFocusSuppressionRequiresExpandedDashboardAndActiveWindow() {
        assertTrue(
                BottomBarService.shouldSuppressDashboardFocusRestoreForControlForTest(
                        nowMs = 1_000L,
                        suppressedUntilMs = 1_500L,
                        isDashboardExpanded = true
                )
        )
        assertFalse(
                BottomBarService.shouldSuppressDashboardFocusRestoreForControlForTest(
                        nowMs = 1_501L,
                        suppressedUntilMs = 1_500L,
                        isDashboardExpanded = true
                )
        )
        assertFalse(
                BottomBarService.shouldSuppressDashboardFocusRestoreForControlForTest(
                        nowMs = 1_000L,
                        suppressedUntilMs = 1_500L,
                        isDashboardExpanded = false
                )
        )
    }

    @Test
    fun projectionHandoffRestoresDashboardOnlyWhenBottomBarWasActive() {
        assertTrue(
                BottomBarService.shouldAutoOpenDashboardAfterProjectionHandoffForTest(
                        isVisible = true,
                        isDashboardExpanded = false
                )
        )
        assertTrue(
                BottomBarService.shouldAutoOpenDashboardAfterProjectionHandoffForTest(
                        isVisible = false,
                        isDashboardExpanded = true
                )
        )
        assertFalse(
                BottomBarService.shouldAutoOpenDashboardAfterProjectionHandoffForTest(
                        isVisible = false,
                        isDashboardExpanded = false
                )
        )
    }
}
