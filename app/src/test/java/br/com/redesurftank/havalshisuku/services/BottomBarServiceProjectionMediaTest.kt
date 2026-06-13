package br.com.redesurftank.havalshisuku.services

import org.junit.Assert.assertFalse
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
    fun androidAutoFallbackMetadataIsAcceptedWhileProjectionSessionIsActive() {
        assertTrue(
                BottomBarService.shouldUseAndroidAutoProjectionFallbackMediaPackageForTest(
                        candidatePackageName = "com.android.bluetooth",
                        candidateHasMetadata = true,
                        currentMediaPackageName = "com.ts.androidauto",
                        activeClusterProjectionPackage = "",
                        androidAutoSessionReady = false
                )
        )
        assertTrue(
                BottomBarService.shouldUseAndroidAutoProjectionFallbackMediaPackageForTest(
                        candidatePackageName = "com.beantechs.mediacenter",
                        candidateHasMetadata = true,
                        currentMediaPackageName = null,
                        activeClusterProjectionPackage = "",
                        androidAutoSessionReady = true
                )
        )
    }

    @Test
    fun androidAutoFallbackMetadataDoesNotOverrideCarPlayOrUnknownSessions() {
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
                        candidatePackageName = "com.android.bluetooth",
                        candidateHasMetadata = false,
                        currentMediaPackageName = "com.ts.androidauto",
                        activeClusterProjectionPackage = "",
                        androidAutoSessionReady = false
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
}
