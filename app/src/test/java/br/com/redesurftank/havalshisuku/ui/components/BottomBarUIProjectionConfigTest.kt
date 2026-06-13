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
    fun resolveBottomBarEffectivePackage_prioritizesClusterProjectionOverStaleSelection() {
        val effectivePackage =
            resolveBottomBarEffectivePackage(
                projectionPackageOnMain = null,
                projectionPackageOnCluster = "com.ts.carplay.app",
                selectedPackage = "com.beantechs.applist",
                firstConfiguredPackage = "com.ts.androidauto.app"
            )

        assertEquals("com.ts.carplay.app", effectivePackage)
    }

    @Test
    fun resolveBottomBarEffectivePackage_keepsDisplayZeroProjectionFirst() {
        val effectivePackage =
            resolveBottomBarEffectivePackage(
                projectionPackageOnMain = "com.ts.androidauto.app",
                projectionPackageOnCluster = "com.ts.carplay.app",
                selectedPackage = "com.beantechs.applist",
                firstConfiguredPackage = "com.example.music"
            )

        assertEquals("com.ts.androidauto.app", effectivePackage)
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
