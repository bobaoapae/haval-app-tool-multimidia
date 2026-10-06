package br.com.redesurftank.havalshisuku.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarServiceUsbMediaTest {
    @Test
    fun usbPublishesOnlyForSourceTwoAndStartsOwnershipWhilePlaying() {
        assertTrue(
                BottomBarService.shouldPublishUsbMediaForTest(
                        currentSource = 2,
                        currentAudioSource = null,
                        currentMediaPackageName = null,
                        activeClusterProjectionPackage = null,
                        activePlayingSessionPackage = null,
                        androidAutoTransportReady = false,
                        usbIsPlaying = true
                )
        )
        assertTrue(
                BottomBarService.shouldPublishUsbMediaForTest(
                        currentSource = null,
                        currentAudioSource = 2,
                        currentMediaPackageName = "com.beantechs.mediacenter.usb",
                        activeClusterProjectionPackage = null,
                        activePlayingSessionPackage = null,
                        androidAutoTransportReady = false,
                        usbIsPlaying = false
                )
        )
        assertFalse(
                BottomBarService.shouldPublishUsbMediaForTest(
                        currentSource = 10,
                        currentAudioSource = 10,
                        currentMediaPackageName = null,
                        activeClusterProjectionPackage = null,
                        activePlayingSessionPackage = null,
                        androidAutoTransportReady = false,
                        usbIsPlaying = true
                )
        )
        assertFalse(
                BottomBarService.shouldPublishUsbMediaForTest(
                        currentSource = 2,
                        currentAudioSource = 12,
                        currentMediaPackageName = null,
                        activeClusterProjectionPackage = null,
                        activePlayingSessionPackage = null,
                        androidAutoTransportReady = false,
                        usbIsPlaying = true
                )
        )
        assertFalse(
                BottomBarService.shouldPublishUsbMediaForTest(
                        currentSource = 2,
                        currentAudioSource = 2,
                        currentMediaPackageName = null,
                        activeClusterProjectionPackage = "com.ts.androidauto.app",
                        activePlayingSessionPackage = null,
                        androidAutoTransportReady = true,
                        usbIsPlaying = true
                )
        )
        assertFalse(
                BottomBarService.shouldPublishUsbMediaForTest(
                        currentSource = 2,
                        currentAudioSource = 2,
                        currentMediaPackageName = null,
                        activeClusterProjectionPackage = null,
                        activePlayingSessionPackage = null,
                        androidAutoTransportReady = false,
                        usbIsPlaying = false
                )
        )
    }

    @Test
    fun usbNeverOverridesCarPlayAndroidAutoTransportOrPlayingSession() {
        assertFalse(
                BottomBarService.shouldPublishUsbMediaForTest(
                        currentSource = 2,
                        currentAudioSource = 2,
                        currentMediaPackageName = "com.ts.carplay",
                        activeClusterProjectionPackage = null,
                        activePlayingSessionPackage = null,
                        androidAutoTransportReady = false,
                        usbIsPlaying = true
                )
        )
        assertFalse(
                BottomBarService.shouldPublishUsbMediaForTest(
                        currentSource = 2,
                        currentAudioSource = 2,
                        currentMediaPackageName = null,
                        activeClusterProjectionPackage = "com.ts.carplay.app",
                        activePlayingSessionPackage = null,
                        androidAutoTransportReady = false,
                        usbIsPlaying = true
                )
        )
        assertFalse(
                BottomBarService.shouldPublishUsbMediaForTest(
                        currentSource = 2,
                        currentAudioSource = 2,
                        currentMediaPackageName = "com.ts.androidauto",
                        activeClusterProjectionPackage = null,
                        activePlayingSessionPackage = null,
                        androidAutoTransportReady = true,
                        usbIsPlaying = true
                )
        )
        assertFalse(
                BottomBarService.shouldPublishUsbMediaForTest(
                        currentSource = 2,
                        currentAudioSource = 2,
                        currentMediaPackageName = null,
                        activeClusterProjectionPackage = null,
                        activePlayingSessionPackage = "com.spotify.music",
                        androidAutoTransportReady = false,
                        usbIsPlaying = true
                )
        )
    }

    @Test
    fun usbOwnerIsPreservedAgainstPausedOrOemMirrorSessionsOnly() {
        assertTrue(
                BottomBarService.shouldPreserveUsbOwnerForCandidateForTest(
                        currentMediaPackageName = "com.beantechs.mediacenter.usb",
                        currentSource = 2,
                        currentAudioSource = 2,
                        candidatePackageName = "com.spotify.music",
                        candidateIsPlaying = false
                )
        )
        assertTrue(
                BottomBarService.shouldPreserveUsbOwnerForCandidateForTest(
                        currentMediaPackageName = "com.beantechs.mediacenter.usb",
                        currentSource = 2,
                        currentAudioSource = 2,
                        candidatePackageName = "com.beantechs.mediacenter",
                        candidateIsPlaying = true
                )
        )
        assertFalse(
                BottomBarService.shouldPreserveUsbOwnerForCandidateForTest(
                        currentMediaPackageName = "com.beantechs.mediacenter.usb",
                        currentSource = 2,
                        currentAudioSource = 2,
                        candidatePackageName = "com.ts.carplay",
                        candidateIsPlaying = false
                )
        )
        assertFalse(
                BottomBarService.shouldPreserveUsbOwnerForCandidateForTest(
                        currentMediaPackageName = "com.beantechs.mediacenter.usb",
                        currentSource = 2,
                        currentAudioSource = 2,
                        candidatePackageName = "com.spotify.music",
                        candidateIsPlaying = true
                )
        )
    }
}
