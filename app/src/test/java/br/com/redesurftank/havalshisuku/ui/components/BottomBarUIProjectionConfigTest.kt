package br.com.redesurftank.havalshisuku.ui.components

import br.com.redesurftank.havalshisuku.models.DisplayAppConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class BottomBarUIProjectionConfigTest {

    @Test
    fun mergeBottomBarProjectionConfigs_appendsMissingProjectionDefaults() {
        val savedConfigs =
            listOf(
                config("com.example.music"),
                config("com.ts.androidauto.app", "Android Auto")
            )
        val predefinedConfigs =
            listOf(
                config("com.ts.carplay.app", "Apple CarPlay"),
                config("com.ts.androidauto.app", "Android Auto"),
                config("com.android.settings", "Configurações")
            )

        val merged = mergeBottomBarProjectionConfigs(savedConfigs, predefinedConfigs)

        assertEquals(
            listOf("com.example.music", "com.ts.androidauto.app", "com.ts.carplay.app"),
            merged.map { it.packageName }
        )
    }

    @Test
    fun resolveBottomBarEffectivePackage_keepsDisplayZeroProjectionFirst() {
        val effectivePackage =
            resolveBottomBarEffectivePackage(
                projectionPackageOnMain = "com.ts.androidauto.app",
                selectedPackage = "com.beantechs.applist",
                firstConfiguredPackage = "com.example.music"
            )

        assertEquals("com.ts.androidauto.app", effectivePackage)
    }

    /**
     * The bar's icon names what display 0 is showing. While a projection runs on the cluster the
     * user is looking at some other app on display 0, and the icon has to say so.
     */
    @Test
    fun resolveBottomBarEffectivePackage_ignoresClusterProjectionAndFollowsDisplayZero() {
        val effectivePackage =
            resolveBottomBarEffectivePackage(
                projectionPackageOnMain = null,
                selectedPackage = "com.example.music",
                firstConfiguredPackage = "com.ts.androidauto.app"
            )

        assertEquals("com.example.music", effectivePackage)
    }

    @Test
    fun resolveBottomBarEffectivePackage_fallsBackToFirstConfigWhenNothingSelected() {
        val effectivePackage =
            resolveBottomBarEffectivePackage(
                projectionPackageOnMain = null,
                selectedPackage = "",
                firstConfiguredPackage = "com.example.music"
            )

        assertEquals("com.example.music", effectivePackage)
    }

    @Test
    fun resolveBottomBarTouchableLeftPx_keepsGutterWhenAndroidAutoOff() {
        assertEquals(128, resolveBottomBarTouchableLeftPx(128, androidAutoOnMainDisplay = false))
        assertEquals(0, resolveBottomBarTouchableLeftPx(0, androidAutoOnMainDisplay = false))
    }

    @Test
    fun resolveBottomBarTouchableLeftPx_usesAndroidAutoCutoutWhenOn() {
        assertEquals(
            ANDROID_AUTO_BOTTOM_BAR_LEFT_PASSTHROUGH_PX,
            resolveBottomBarTouchableLeftPx(0, androidAutoOnMainDisplay = true)
        )
        assertEquals(
            ANDROID_AUTO_BOTTOM_BAR_LEFT_PASSTHROUGH_PX,
            resolveBottomBarTouchableLeftPx(128, androidAutoOnMainDisplay = true)
        )
    }

    @Test
    fun resolveBottomBarRowStartPadPx_keepsContentAtGutterAfterSurfaceCutout() {
        assertEquals(128, resolveBottomBarRowStartPadPx(128, surfaceCutoutPx = 0))
        assertEquals(28, resolveBottomBarRowStartPadPx(128, surfaceCutoutPx = 100))
        assertEquals(0, resolveBottomBarRowStartPadPx(0, surfaceCutoutPx = 100))
    }

    @Test
    fun resolveDockApps_ordersBySessionRecentsAndPreservesTelasRemainder() {
        val telas = listOf(
            "app.telas.1",
            "app.telas.2",
            "app.telas.3",
            "app.telas.4",
            "app.telas.5"
        )
        // Session recents has app.telas.4 first, then non-telas app, then app.telas.2
        val sessionRecents = listOf("app.telas.4", "app.unrelated", "app.telas.2")

        val result = resolveDockApps(telas, sessionRecents, maxApps = 9)

        // app.telas.4 was most recent, then app.telas.2, then 1, 3, 5 follow in Telas order
        assertEquals(
            listOf("app.telas.4", "app.telas.2", "app.telas.1", "app.telas.3", "app.telas.5"),
            result
        )
    }

    @Test
    fun resolveDockApps_limitsToNineApps() {
        val telas = (1..15).map { "app.telas.$it" }
        val sessionRecents = listOf("app.telas.12", "app.telas.5")

        val result = resolveDockApps(telas, sessionRecents, maxApps = 9)

        assertEquals(9, result.size)
        assertEquals("app.telas.12", result[0])
        assertEquals("app.telas.5", result[1])
        assertEquals("app.telas.1", result[2])
    }

    @Test
    fun resolveDockApps_emptyTelasReturnsEmpty() {
        val result = resolveDockApps(emptyList(), listOf("app.any"), maxApps = 9)
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun shouldHideFromAllApps_filtersHavalH6IgnoredPackages() {
        org.junit.Assert.assertTrue(shouldHideFromAllApps("com.beantechs.hvac"))
        org.junit.Assert.assertTrue(shouldHideFromAllApps("com.beantechs.drivinganalysisservice"))
        org.junit.Assert.assertTrue(shouldHideFromAllApps("com.android.systemui"))
        org.junit.Assert.assertTrue(shouldHideFromAllApps("com.android.car.media"))
        org.junit.Assert.assertTrue(shouldHideFromAllApps("com.ts.androidauto.app"))
        org.junit.Assert.assertTrue(shouldHideFromAllApps(""))

        // Allows standard launchable and custom apps
        org.junit.Assert.assertFalse(shouldHideFromAllApps("com.android.chrome"))
        org.junit.Assert.assertFalse(shouldHideFromAllApps("com.google.android.apps.maps"))
        org.junit.Assert.assertFalse(shouldHideFromAllApps("com.spotify.music"))
        org.junit.Assert.assertFalse(shouldHideFromAllApps("com.waze"))
        org.junit.Assert.assertFalse(shouldHideFromAllApps("br.com.redesurftank.havalshisuku"))
    }

    @Test
    fun resolveBottomBarSwipeAction_horizontalDominant_returnsNone() {
        // When horizontal movement exceeds 15px and dominates over vertical, it must be ignored
        val decision = resolveBottomBarSwipeAction(
            totalDragX = 35f,
            totalDragY = -50f, // vertical is large, but horizontal is 35 > 15
            isHorizontalLocked = false,
            isChildConsumed = false
        )
        // Note: absX=35, absY=50 -> here absX < absY, so not dominated unless absX > absY
        // If absX > 15 and absX > absY:
        val decisionHorizontal = resolveBottomBarSwipeAction(
            totalDragX = 55f,
            totalDragY = -50f,
            isHorizontalLocked = false,
            isChildConsumed = false
        )
        assertEquals(BottomBarSwipeDecision.NONE, decisionHorizontal)
    }

    @Test
    fun resolveBottomBarSwipeAction_horizontalLocked_returnsNoneEvenWithVerticalDrift() {
        val decision = resolveBottomBarSwipeAction(
            totalDragX = 0f,
            totalDragY = -80f,
            isHorizontalLocked = true,
            isChildConsumed = false
        )
        assertEquals(BottomBarSwipeDecision.NONE, decision)
    }

    @Test
    fun resolveBottomBarSwipeAction_childConsumed_returnsNone() {
        val decision = resolveBottomBarSwipeAction(
            totalDragX = 0f,
            totalDragY = -80f,
            isHorizontalLocked = false,
            isChildConsumed = true
        )
        assertEquals(BottomBarSwipeDecision.NONE, decision)
    }

    @Test
    fun resolveBottomBarSwipeAction_pureSwipeUp_returnsExpand() {
        val decision = resolveBottomBarSwipeAction(
            totalDragX = 2f,
            totalDragY = -50f,
            isHorizontalLocked = false,
            isChildConsumed = false
        )
        assertEquals(BottomBarSwipeDecision.EXPAND, decision)
    }

    @Test
    fun resolveBottomBarSwipeAction_pureSwipeDown_returnsHide() {
        val decision = resolveBottomBarSwipeAction(
            totalDragX = 2f,
            totalDragY = 30f,
            isHorizontalLocked = false,
            isChildConsumed = false
        )
        assertEquals(BottomBarSwipeDecision.HIDE, decision)
    }

    @Test
    fun resolveBottomBarSwipeAction_smallDrag_returnsNone() {
        val decision = resolveBottomBarSwipeAction(
            totalDragX = 1f,
            totalDragY = -20f,
            isHorizontalLocked = false,
            isChildConsumed = false
        )
        assertEquals(BottomBarSwipeDecision.NONE, decision)
    }

    private fun config(packageName: String, customName: String? = null): DisplayAppConfig {
        return DisplayAppConfig(
            packageName = packageName,
            activityName = "$packageName.MainActivity",
            displayId = 3,
            x = 0,
            y = 0,
            width = 1920,
            height = 720,
            customName = customName
        )
    }
}
