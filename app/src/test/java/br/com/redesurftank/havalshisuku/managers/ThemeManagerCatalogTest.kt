package br.com.redesurftank.havalshisuku.managers

import br.com.redesurftank.havalshisuku.models.ThemeMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeManagerCatalogTest {
    @Test
    fun `default catalog uses upstream preview contract v1 release branch`() {
        assertEquals(
            "https://github.com/bobaoapae/haval-app-tool-multimidia/tree/" +
                "preview/cluster-widgets/Themes/v1.0",
            ThemeManager.THEME_REPO_URL
        )
    }

    @Test
    fun `legacy Sport catalog stays separate from contract v1 catalog`() {
        assertEquals(
            "https://github.com/bobaoapae/haval-app-tool-multimidia/tree/" +
                "preview/cluster-widgets/Themes",
            ThemeManager.TRUSTED_LEGACY_SPORT_REPO_URL
        )

        val sport = legacySportMetadata("SportRed", "Sport Colors")
        assertTrue(ThemeManager.isTrustedLegacySportCatalogEntry(sport))
        assertEquals(
            ThemeManager.TRUSTED_LEGACY_SPORT_REPO_URL,
            ThemeManager.defaultRepositoryUrlFor(sport)
        )
    }

    @Test
    fun `legacy Sport allowlist rejects renamed repackaged or unrelated themes`() {
        assertTrue(
            ThemeManager.isTrustedLegacySportCatalogEntry(
                legacySportMetadata("SportRedLite", "Sport Colors Leve")
            )
        )
        assertFalse(
            ThemeManager.isTrustedLegacySportCatalogEntry(
                legacySportMetadata("SportRed", "Sport Colors").copy(version = "0.16.45")
            )
        )
        assertFalse(
            ThemeManager.isTrustedLegacySportCatalogEntry(
                legacySportMetadata("SportRed", "Sport Colors").copy(contractVersion = "v1.0")
            )
        )
        assertFalse(
            ThemeManager.isTrustedLegacySportCatalogEntry(
                legacySportMetadata("OtherSport", "Sport Colors")
            )
        )
    }

    @Test
    fun `contract theme downloads remain on contract v1 repository`() {
        val minimalist = ThemeMetadata(
            name = "Minimalist",
            description = "",
            version = "1.0.1",
            thumbnailUrl = "thumbnail.png",
            mainFile = "app.html",
            folderName = "minimalist",
            contractVersion = "v1.0"
        )

        assertEquals(
            ThemeManager.THEME_REPO_URL,
            ThemeManager.defaultRepositoryUrlFor(minimalist)
        )
    }

    private fun legacySportMetadata(folderName: String, name: String) = ThemeMetadata(
        name = name,
        description = "",
        version = "0.16.44",
        thumbnailUrl = "thumbnail.png",
        mainFile = "index.html",
        folderName = folderName,
        contractVersion = "legacy"
    )
}
