package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
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
}
