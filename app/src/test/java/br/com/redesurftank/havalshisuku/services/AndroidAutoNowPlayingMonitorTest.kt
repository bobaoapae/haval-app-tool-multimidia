package br.com.redesurftank.havalshisuku.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoNowPlayingMonitorTest {
    @Test
    fun ignoresLowProgressRegressionWhilePlayingSameTrack() {
        assertTrue(
            AndroidAutoNowPlayingMonitor.shouldIgnoreStaleLowProgressSample(
                previousProgressSeconds = 42,
                newProgressSeconds = 0,
                durationSeconds = 240,
                isPlaying = true,
                hasMetadata = true,
                forceAcceptProgressReset = false
            )
        )
        assertTrue(
            AndroidAutoNowPlayingMonitor.shouldIgnoreStaleLowProgressSample(
                previousProgressSeconds = 42,
                newProgressSeconds = 1,
                durationSeconds = 240,
                isPlaying = true,
                hasMetadata = true,
                forceAcceptProgressReset = false
            )
        )
    }

    @Test
    fun acceptsExplicitZeroResetFromSeekOrTrackChange() {
        assertFalse(
            AndroidAutoNowPlayingMonitor.shouldIgnoreStaleLowProgressSample(
                previousProgressSeconds = 42,
                newProgressSeconds = 0,
                durationSeconds = 240,
                isPlaying = true,
                hasMetadata = true,
                forceAcceptProgressReset = true
            )
        )
    }

    @Test
    fun acceptsNonLowProgressRegression() {
        assertFalse(
            AndroidAutoNowPlayingMonitor.shouldIgnoreStaleLowProgressSample(
                previousProgressSeconds = 42,
                newProgressSeconds = 12,
                durationSeconds = 240,
                isPlaying = true,
                hasMetadata = true,
                forceAcceptProgressReset = false
            )
        )
    }

    @Test
    fun acceptsZeroNearTrackEndForRepeatOrNextTrack() {
        assertFalse(
            AndroidAutoNowPlayingMonitor.shouldIgnoreStaleLowProgressSample(
                previousProgressSeconds = 239,
                newProgressSeconds = 0,
                durationSeconds = 240,
                isPlaying = true,
                hasMetadata = true,
                forceAcceptProgressReset = false
            )
        )
    }

    @Test
    fun acceptsZeroWhenNotPlayingOrWithoutMetadata() {
        assertFalse(
            AndroidAutoNowPlayingMonitor.shouldIgnoreStaleLowProgressSample(
                previousProgressSeconds = 42,
                newProgressSeconds = 0,
                durationSeconds = 240,
                isPlaying = false,
                hasMetadata = true,
                forceAcceptProgressReset = false
            )
        )
        assertFalse(
            AndroidAutoNowPlayingMonitor.shouldIgnoreStaleLowProgressSample(
                previousProgressSeconds = 42,
                newProgressSeconds = 0,
                durationSeconds = 240,
                isPlaying = true,
                hasMetadata = false,
                forceAcceptProgressReset = false
            )
        )
    }

    @Test
    fun ignoresTransientNonPlayingStatusWhileProgressIsRecent() {
        assertTrue(
            AndroidAutoNowPlayingMonitor.shouldIgnoreTransientNonPlayingStatus(
                previousStatus = 1,
                newStatus = 0,
                hasMetadata = true,
                lastProgressSeconds = 42,
                lastAcceptedProgressAtMs = 10_000L,
                nowMs = 12_000L
            )
        )
        assertTrue(
            AndroidAutoNowPlayingMonitor.shouldIgnoreTransientNonPlayingStatus(
                previousStatus = 1,
                newStatus = 2,
                hasMetadata = true,
                lastProgressSeconds = 42,
                lastAcceptedProgressAtMs = 10_000L,
                nowMs = 12_000L
            )
        )
    }

    @Test
    fun acceptsNonPlayingStatusAfterGraceWindow() {
        assertFalse(
            AndroidAutoNowPlayingMonitor.shouldIgnoreTransientNonPlayingStatus(
                previousStatus = 1,
                newStatus = 0,
                hasMetadata = true,
                lastProgressSeconds = 42,
                lastAcceptedProgressAtMs = 10_000L,
                nowMs = 16_000L
            )
        )
    }

    @Test
    fun acceptsNonPlayingStatusWithoutMetadataOrPreviousPlaying() {
        assertFalse(
            AndroidAutoNowPlayingMonitor.shouldIgnoreTransientNonPlayingStatus(
                previousStatus = 1,
                newStatus = 0,
                hasMetadata = false,
                lastProgressSeconds = 42,
                lastAcceptedProgressAtMs = 10_000L,
                nowMs = 12_000L
            )
        )
        assertFalse(
            AndroidAutoNowPlayingMonitor.shouldIgnoreTransientNonPlayingStatus(
                previousStatus = 0,
                newStatus = 0,
                hasMetadata = true,
                lastProgressSeconds = 42,
                lastAcceptedProgressAtMs = 10_000L,
                nowMs = 12_000L
            )
        )
    }

    @Test
    fun resyncsCallbackWhenLinkIsActiveAndPlayingWithoutMetadata() {
        assertTrue(
            AndroidAutoNowPlayingMonitor.shouldResyncCallbackForMissingMetadata(
                isLinkActive = true,
                musicStatus = 1,
                hasMetadata = false,
                progressSeconds = 0,
                nowMs = 20_000L,
                lastResyncAtMs = 0L
            )
        )
        assertTrue(
            AndroidAutoNowPlayingMonitor.shouldResyncCallbackForMissingMetadata(
                isLinkActive = true,
                musicStatus = 0,
                hasMetadata = false,
                progressSeconds = 12,
                nowMs = 20_000L,
                lastResyncAtMs = 0L
            )
        )
    }

    @Test
    fun resyncsCallbackDuringInitialBootstrapEvenBeforeStatusArrives() {
        assertTrue(
            AndroidAutoNowPlayingMonitor.shouldResyncCallbackForMissingMetadata(
                isLinkActive = true,
                musicStatus = 0,
                hasMetadata = false,
                progressSeconds = 0,
                nowMs = 20_000L,
                lastResyncAtMs = 0L,
                isInBootstrapWindow = true
            )
        )
    }

    @Test
    fun skipsMetadataResyncWhenInactiveOrWithinCooldown() {
        assertFalse(
            AndroidAutoNowPlayingMonitor.shouldResyncCallbackForMissingMetadata(
                isLinkActive = false,
                musicStatus = 1,
                hasMetadata = false,
                progressSeconds = 0,
                nowMs = 20_000L,
                lastResyncAtMs = 0L
            )
        )
        assertFalse(
            AndroidAutoNowPlayingMonitor.shouldResyncCallbackForMissingMetadata(
                isLinkActive = true,
                musicStatus = 1,
                hasMetadata = true,
                progressSeconds = 0,
                nowMs = 20_000L,
                lastResyncAtMs = 0L
            )
        )
        assertFalse(
            AndroidAutoNowPlayingMonitor.shouldResyncCallbackForMissingMetadata(
                isLinkActive = true,
                musicStatus = 1,
                hasMetadata = false,
                progressSeconds = 0,
                nowMs = 20_000L,
                lastResyncAtMs = 15_000L
            )
        )
    }

    @Test
    fun holdsMissingMetadataStateDuringActiveAndroidAutoBootstrap() {
        assertTrue(
            AndroidAutoNowPlayingMonitor.shouldHoldMissingMetadataAndroidAutoState(
                isLinkActive = true,
                previousStatus = 0,
                lastProgressSeconds = 0,
                lastPlaybackEvidenceAtMs = 0L,
                lastServiceConnectedAtMs = 10_000L,
                nowMs = 12_000L
            )
        )
        assertTrue(
            AndroidAutoNowPlayingMonitor.shouldHoldMissingMetadataAndroidAutoState(
                isLinkActive = true,
                previousStatus = 1,
                lastProgressSeconds = 0,
                lastPlaybackEvidenceAtMs = 0L,
                lastServiceConnectedAtMs = 0L,
                nowMs = 40_000L
            )
        )
    }

    @Test
    fun doesNotHoldMissingMetadataStateWhenLinkInactiveAndNoRecentEvidence() {
        assertFalse(
            AndroidAutoNowPlayingMonitor.shouldHoldMissingMetadataAndroidAutoState(
                isLinkActive = false,
                previousStatus = 1,
                lastProgressSeconds = 30,
                lastPlaybackEvidenceAtMs = 20_000L,
                lastServiceConnectedAtMs = 10_000L,
                nowMs = 21_000L
            )
        )
        assertFalse(
            AndroidAutoNowPlayingMonitor.shouldHoldMissingMetadataAndroidAutoState(
                isLinkActive = true,
                previousStatus = 0,
                lastProgressSeconds = 0,
                lastPlaybackEvidenceAtMs = 10_000L,
                lastServiceConnectedAtMs = 1_000L,
                nowMs = 40_000L
            )
        )
    }
}
