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
