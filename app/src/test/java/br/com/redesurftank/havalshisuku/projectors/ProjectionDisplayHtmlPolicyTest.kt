package br.com.redesurftank.havalshisuku.projectors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionDisplayHtmlPolicyTest {
    @Test
    fun `removes Sport projection override and preserves configured display getter`() {
        val legacyBundle =
            """before;function sJ(){if(sD()){let t=d("display");return"Mapa"===t||"Mapa Graduado"===t||"Mapa Limpo"===t?t:"Mapa Limpo"}return d("display")||"Normal"}after;"""

        val result = ProjectionDisplayHtmlPolicy.preserveUserDisplaySelection(legacyBundle)

        assertEquals(1, result.removedLegacyOverrides)
        assertTrue(result.html.contains("function sJ(){return d(\"display\")||\"Normal\"}"))
        assertFalse(result.html.contains("t:\"Mapa Limpo\""))
        assertFalse(result.injectedCarPlayFullBleedMask)
    }

    @Test
    fun `leaves already corrected bundle unchanged`() {
        val correctedBundle =
            """function sJ(){return d("display")||"Normal"}"""

        val result = ProjectionDisplayHtmlPolicy.preserveUserDisplaySelection(correctedBundle)

        assertEquals(0, result.removedLegacyOverrides)
        assertEquals(correctedBundle, result.html)
        assertFalse(result.injectedCarPlayFullBleedMask)
    }

    @Test
    fun `does not rewrite a structurally inconsistent function`() {
        val unknownBundle =
            """function sJ(){if(sD()){let t=d("display");return"Mapa"===x||"Mapa Graduado"===t||"Mapa Limpo"===t?t:"Mapa Limpo"}return d("display")||"Normal"}"""

        val result = ProjectionDisplayHtmlPolicy.preserveUserDisplaySelection(unknownBundle)

        assertEquals(0, result.removedLegacyOverrides)
        assertEquals(unknownBundle, result.html)
        assertFalse(result.injectedCarPlayFullBleedMask)
    }

    @Test
    fun `replaces Sport Analogico V2 projection cutout with linear edge fades`() {
        val sportBundle =
            """<html><head><style>.display-analogico-v2 .cluster-mask-bg:after{box-shadow:0 0 0 9999px #000}</style><script>let projection="carplay-in-dash",cluster="g20-v2-cluster";</script></head><body></body></html>"""

        val result = ProjectionDisplayHtmlPolicy.preserveUserDisplaySelection(sportBundle)

        assertTrue(result.injectedCarPlayFullBleedMask)
        assertTrue(result.html.contains("data-haval-projection-full-bleed-mask=\"1\""))
        assertTrue(
            result.html.contains(
                ".carplay-in-dash.display-analogico-v2 .cluster-mask-bg::after"
            )
        )
        assertEquals(2, Regex("linear-gradient\\(").findAll(result.html).count())
        assertTrue(result.html.contains("90deg,"))
        assertTrue(result.html.contains("270deg,"))
        assertTrue(result.html.contains("rgba(0, 0, 0, 0.98) 0%"))
        assertTrue(result.html.contains("rgba(0, 0, 0, 0.68) 28%"))
        assertTrue(result.html.contains("rgba(0, 0, 0, 0.06) 47%"))
        assertTrue(result.html.contains("transparent 50%"))
        assertTrue(result.html.contains("box-shadow: none !important"))
        assertTrue(
            result.html.contains(
                ".carplay-in-dash.display-analogico-v2 .mask-top-bar"
            )
        )
        assertTrue(result.html.contains("background-image: none !important"))
        assertFalse(result.html.contains("filter: blur"))
        assertTrue(
            result.html.indexOf("data-haval-projection-full-bleed-mask") <
                result.html.indexOf("</head>")
        )
    }

    @Test
    fun `does not inject projection mask twice`() {
        val alreadyPatchedBundle =
            """<html><head><style data-haval-projection-full-bleed-mask="1"></style><style>.display-analogico-v2 .cluster-mask-bg:after{box-shadow:0 0 0 9999px #000}</style><script>let projection="carplay-in-dash",cluster="g20-v2-cluster";</script></head></html>"""

        val result = ProjectionDisplayHtmlPolicy.preserveUserDisplaySelection(alreadyPatchedBundle)

        assertFalse(result.injectedCarPlayFullBleedMask)
        assertEquals(
            1,
            Regex("data-haval-projection-full-bleed-mask").findAll(result.html).count()
        )
    }

    @Test
    fun `appends projection style for minified Sport bundle without closing head`() {
        val minifiedSportBundle =
            """<!doctype html><html><style>.display-analogico-v2 .cluster-mask-bg:after{box-shadow:0 0 0 9999px #000}</style><script>let projection="carplay-in-dash",cluster="g20-v2-cluster";</script>"""

        val result = ProjectionDisplayHtmlPolicy.preserveUserDisplaySelection(minifiedSportBundle)

        assertTrue(result.injectedCarPlayFullBleedMask)
        assertTrue(result.html.startsWith(minifiedSportBundle))
        assertTrue(result.html.endsWith("</style>"))
    }

    @Test
    fun `does not inject full bleed mask for Android Auto only bundle`() {
        val androidAutoBundle =
            """<html><style>.display-analogico-v2 .cluster-mask-bg:after{box-shadow:0 0 0 9999px #000}</style><script>let projection="projection-mirror-in-dash",cluster="g20-v2-cluster";</script></html>"""

        val result = ProjectionDisplayHtmlPolicy.preserveUserDisplaySelection(androidAutoBundle)

        assertFalse(result.injectedCarPlayFullBleedMask)
        assertEquals(androidAutoBundle, result.html)
    }
}
