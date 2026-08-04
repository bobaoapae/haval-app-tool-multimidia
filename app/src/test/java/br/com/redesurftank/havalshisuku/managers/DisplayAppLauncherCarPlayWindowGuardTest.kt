package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayAppLauncherCarPlayWindowGuardTest {
    private val impulsePackage = "br.com.redesurftank.havalshisuku"

    @Test
    fun normalDisplayZeroWindowIsVerifyOnlyForCarPlay() {
        assertEquals(
            "VERIFY_ONLY",
            DisplayAppLauncher.resolveCarPlayWindowFocusGuardActionForTest(
                packageName = "com.android.settings",
                selfPackageName = impulsePackage
            )
        )
    }

    @Test
    fun passiveDisplayZeroWindowsDoNotTriggerCarPlayFocusGuard() {
        listOf(
            "com.android.systemui",
            "com.beantechs.mediacenter",
            "com.beantechs.mediacenter.h5.core",
            "com.beantechs.vehiclecenter"
        ).forEach { packageName ->
            assertNull(
                DisplayAppLauncher.resolveCarPlayWindowFocusGuardActionForTest(
                    packageName = packageName,
                    selfPackageName = impulsePackage
                )
            )
        }
    }

    @Test
    fun selfDisplayZeroSurfaceReassertBackoffOnlyAppliesToWatchdogSelfPath() {
        assertTrue(
            DisplayAppLauncher.shouldSkipCarPlaySelfD0SurfaceReassertForTest(
                reason = "CARPLAY_CLUSTER_WATCHDOG_SELF_D0",
                now = 12_000L,
                lastFailedAt = 10_000L,
                backoffMs = 30_000L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldSkipCarPlaySelfD0SurfaceReassertForTest(
                reason = "AVM_PREVIEW_STATUS_1_CONTRACT_PRIMARY_STALE_SURFACE",
                now = 12_000L,
                lastFailedAt = 10_000L,
                backoffMs = 30_000L
            )
        )
    }

    @Test
    fun selfDisplayZeroSurfaceReassertBackoffExpires() {
        assertFalse(
            DisplayAppLauncher.shouldSkipCarPlaySelfD0SurfaceReassertForTest(
                reason = "CARPLAY_CLUSTER_WATCHDOG_SELF_D0",
                now = 45_000L,
                lastFailedAt = 10_000L,
                backoffMs = 30_000L
            )
        )
    }

    @Test
    fun impulseWindowIsVerifyOnlyForCarPlay() {
        assertEquals(
            "VERIFY_ONLY",
            DisplayAppLauncher.resolveCarPlayWindowFocusGuardActionForTest(
                packageName = impulsePackage,
                selfPackageName = impulsePackage
            )
        )
    }

    @Test
    fun projectionPackagesDoNotTriggerWindowFocusGuard() {
        assertNull(
            DisplayAppLauncher.resolveCarPlayWindowFocusGuardActionForTest(
                packageName = "com.ts.carplay.app",
                selfPackageName = impulsePackage
            )
        )
        assertNull(
            DisplayAppLauncher.resolveCarPlayWindowFocusGuardActionForTest(
                packageName = "com.ts.androidauto.app",
                selfPackageName = impulsePackage
            )
        )
    }

    @Test
    fun displayZeroNativePanelContractIsVerifyOnlyForCarPlay() {
        listOf(
            "AVM_PREVIEW_STATUS_1",
            "AVM_PREVIEW_STATUS_0",
            "HVAC_PANEL_DISPLAY_1",
            "SERVICE_OPEN_APP_com.android.settings",
            "OPEN_AVM_ONCE_OPEN",
            "LAUNCH_MAIN_AFTER_START_com.beantechs.settings"
        ).forEach { reason ->
            assertEquals(
                "VERIFY_ONLY",
                DisplayAppLauncher.resolveCarPlayContractGuardActionForTest(reason)
            )
        }
    }
}
