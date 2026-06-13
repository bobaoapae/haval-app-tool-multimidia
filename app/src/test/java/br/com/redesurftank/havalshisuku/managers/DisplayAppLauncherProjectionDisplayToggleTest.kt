package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayAppLauncherProjectionDisplayToggleTest {
    @Test
    fun mainCarPlayTargetsClusterDisplay() {
        assertEquals(
            "com.ts.carplay.app:0->3",
            DisplayAppLauncher.resolveProjectionDisplayToggleDecisionForTest(
                mainProjectionPackage = "com.ts.carplay.app",
                clusterProjectionPackage = null
            )
        )
    }

    @Test
    fun clusterCarPlayTargetsMainDisplay() {
        assertEquals(
            "com.ts.carplay.app:3->0",
            DisplayAppLauncher.resolveProjectionDisplayToggleDecisionForTest(
                mainProjectionPackage = null,
                clusterProjectionPackage = "com.ts.carplay.app"
            )
        )
    }

    @Test
    fun mainAndroidAutoTargetsClusterDisplay() {
        assertEquals(
            "com.ts.androidauto.app:0->3",
            DisplayAppLauncher.resolveProjectionDisplayToggleDecisionForTest(
                mainProjectionPackage = "com.ts.androidauto.app",
                clusterProjectionPackage = null
            )
        )
    }

    @Test
    fun clusterAndroidAutoTargetsMainDisplay() {
        assertEquals(
            "com.ts.androidauto.app:3->0",
            DisplayAppLauncher.resolveProjectionDisplayToggleDecisionForTest(
                mainProjectionPackage = null,
                clusterProjectionPackage = "com.ts.androidauto.app"
            )
        )
    }

    @Test
    fun ignoresWhenNoProjectionIsActive() {
        assertNull(
            DisplayAppLauncher.resolveProjectionDisplayToggleDecisionForTest(
                mainProjectionPackage = "com.android.settings",
                clusterProjectionPackage = null
            )
        )
    }

    @Test
    fun mainProjectionWinsWhenBothDisplaysReportProjection() {
        assertEquals(
            "com.ts.androidauto.app:0->3",
            DisplayAppLauncher.resolveProjectionDisplayToggleDecisionForTest(
                mainProjectionPackage = "com.ts.androidauto.app",
                clusterProjectionPackage = "com.ts.carplay.app"
            )
        )
    }

    @Test
    fun knownCarPlayPackageOnMainTargetsClusterEvenWhenNotVisible() {
        assertEquals(
            "com.ts.carplay.app:0->3",
            DisplayAppLauncher.resolveProjectionDisplayToggleDecisionFromKnownPackageForTest(
                packageName = "com.ts.carplay",
                sourceDisplayId = 0
            )
        )
    }

    @Test
    fun knownAndroidAutoPackageOnClusterTargetsMainEvenWhenNotVisible() {
        assertEquals(
            "com.ts.androidauto.app:3->0",
            DisplayAppLauncher.resolveProjectionDisplayToggleDecisionFromKnownPackageForTest(
                packageName = "com.ts.androidauto.projectionservice",
                sourceDisplayId = 3
            )
        )
    }

    @Test
    fun androidAutoToggleConnectionAcceptsActiveLinkStatuses() {
        assertEquals(true, DisplayAppLauncher.isAndroidAutoLinkActiveForToggleForTest(3))
        assertEquals(true, DisplayAppLauncher.isAndroidAutoLinkActiveForToggleForTest(7))
        assertEquals(true, DisplayAppLauncher.isAndroidAutoLinkActiveForToggleForTest(8))
        assertEquals(false, DisplayAppLauncher.isAndroidAutoLinkActiveForToggleForTest(1))
        assertEquals(false, DisplayAppLauncher.isAndroidAutoLinkActiveForToggleForTest(null))
    }
}
