package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayAppLauncherCarPlayWindowGuardTest {
    private val impulsePackage = "br.com.redesurftank.havalshisuku"

    @Test
    fun normalDisplayZeroWindowUsesExistingClusterLiteFocusOnly() {
        assertEquals(
            "EXISTING_CLUSTER_VIDEO_FOCUS_ONLY",
            DisplayAppLauncher.resolveCarPlayWindowFocusGuardActionForTest(
                packageName = "com.android.settings",
                selfPackageName = impulsePackage
            )
        )
    }

    @Test
    fun impulseWindowKeepsSurfaceStaleException() {
        assertEquals(
            "SURFACE_REASSERT_IF_STALE",
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
}
