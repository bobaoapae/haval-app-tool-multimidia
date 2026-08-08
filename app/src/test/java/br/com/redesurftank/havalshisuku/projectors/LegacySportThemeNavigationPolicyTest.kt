package br.com.redesurftank.havalshisuku.projectors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacySportThemeNavigationPolicyTest {
    @Test
    fun `menu focus updates legacy state before moving visual focus`() {
        val javascript =
            LegacySportThemeNavigationPolicy.buildMenuFocusJavascript("option_8")

        assertTrue(javascript.contains("window.control('menuNav',\"option_8\")"))
        assertTrue(javascript.contains("window.focus(\"option_8\")"))
        assertTrue(
            javascript.indexOf("window.control('menuNav'") <
                javascript.indexOf("window.focus(")
        )
    }

    @Test
    fun `screen focus and control use the legacy Sport bridge functions`() {
        assertTrue(
            LegacySportThemeNavigationPolicy.buildShowScreenJavascript("color_selection")
                .contains("window.showScreen(\"color_selection\")")
        )
        assertTrue(
            LegacySportThemeNavigationPolicy.buildFocusJavascript("temp")
                .contains("window.focus(\"temp\")")
        )
        assertTrue(
            LegacySportThemeNavigationPolicy.buildControlJavascript("currentGraph", "power")
                .contains("window.control(\"currentGraph\",\"power\")")
        )
    }

    @Test
    fun `dynamic values are quoted instead of becoming executable javascript`() {
        val javascript =
            LegacySportThemeNavigationPolicy.buildMenuFocusJavascript(
                "option_8\");window.evil=true;//"
            )

        assertTrue(javascript.contains("\\\");window.evil=true;//"))
        assertFalse(javascript.contains("window.focus(\"option_8\");window.evil=true"))
    }
}
