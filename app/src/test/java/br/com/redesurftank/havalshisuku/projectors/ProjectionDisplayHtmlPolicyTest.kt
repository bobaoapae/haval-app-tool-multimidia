package br.com.redesurftank.havalshisuku.projectors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionDisplayHtmlPolicyTest {
    private val legacySportGaugeMotion =
        """<style>.g20-v2-needle{transition:x1 80ms linear,y1 80ms linear,x2 80ms linear,y2 80ms linear}.g20-v2-needle.power{transition:none}</style><script>eT=(t,e,i)=>t+(e-t)*(1-Math.exp(-i/180));function speed(){Math.abs(eU.speed-eK)>=30&&(eK=eU.speed),eQ()}function power(){Math.abs(eU.powerKw-ew)>=80&&(ew=eU.powerKw),eQ()}</script>"""
    private val supportedSportStructure =
        """<html><head><style>.display-analogico-v2 .cluster-mask-bg:after{box-shadow:0 0 0 9999px #000}.dashboard-top-center{}.g20-v2-bottom-bar{}.g20-v2-map-odometer{}.g20-v2-scale-comb{}.g20-v2-native-status-mock{}.g20-v2-speed-readout{}.g20-v2-power-readout{}</style><script>let projection="carplay-in-dash",cluster="g20-v2-cluster";</script></head><body></body></html>"""

    private val supportedSportBundle =
        supportedSportStructure.replace("</head>", "$legacySportGaugeMotion</head>")
    private val legacyHiddenSportCanvas =
        """<script>!function A(){let r,n;s.clearRect(0,0,500,500),draw(),f=requestAnimationFrame(A)}()</script>"""
    private val supportedSportRuntimeBundle =
        supportedSportBundle.replace("</head>", "$legacyHiddenSportCanvas</head>")

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

    @Test
    fun `injects approved static visual polish for known Sport bundle`() {
        val result =
            ProjectionDisplayHtmlPolicy.preserveUserDisplaySelection(
                supportedSportBundle,
                "SportRed"
            )

        assertTrue(result.injectedSportVisualPolish)
        assertTrue(result.html.contains("data-haval-sport-visual-polish=\"1\""))
        assertTrue(result.html.contains(".g20-v2-native-status-mock"))
        assertTrue(result.html.contains("top: 380px !important"))
        assertTrue(result.html.contains(".dashboard-top-center::before"))
        assertTrue(result.html.contains("width: 650px !important"))
        assertTrue(result.html.contains("gap: 72px !important"))
        assertTrue(
            result.html.contains(
                "file:///android_asset/sport_visual/sport-floating-header-glow.svg"
            )
        )
        assertTrue(result.html.contains("0 2px 8px rgba(0, 0, 0, 0.98)"))
        assertTrue(result.html.contains("#app.display-analogico-v2 .g20-v2-bottom-bar::before"))
        assertTrue(result.html.contains("left: 460px !important"))
        assertTrue(result.html.contains("width: 1000px !important"))
        assertTrue(
            result.html.contains(
                "file:///android_asset/sport_visual/sport-floating-data-glow.svg"
            )
        )
        assertTrue(result.html.contains("width: 320px !important"))
        assertTrue(result.html.contains("stroke-dasharray: 1.5 4.5 !important"))
        assertTrue(result.html.contains("stroke-dasharray: 3 8 !important"))
        assertFalse(result.html.contains("animation:"))
        assertFalse(result.html.contains("backdrop-filter:"))
        assertTrue(result.tunedSportGaugeMotion)
        assertTrue(result.html.contains("Math.exp(-i/100)"))
        assertTrue(result.html.contains("transition:x1 34ms linear"))
        assertFalse(result.html.contains("Math.exp(-i/180)"))
        assertFalse(result.html.contains("Math.abs(eU.speed-eK)>=30"))
        assertFalse(result.html.contains("Math.abs(eU.powerKw-ew)>=80"))
        assertTrue(
            result.html.indexOf("data-haval-sport-visual-polish") <
                result.html.indexOf("</head>")
        )
    }

    @Test
    fun `injects the same visual polish for SportRedLite`() {
        val result =
            ProjectionDisplayHtmlPolicy.preserveUserDisplaySelection(
                supportedSportBundle,
                "SportRedLite"
            )

        assertTrue(result.injectedSportVisualPolish)
        assertTrue(result.tunedSportGaugeMotion)
    }

    @Test
    fun `pauses hidden Normal canvas drawing for known Sport bundle`() {
        val result =
            ProjectionDisplayHtmlPolicy.preserveUserDisplaySelection(
                supportedSportRuntimeBundle,
                "SportRed"
            )

        assertTrue(result.pausedHiddenSportCanvas)
        assertTrue(
            result.html.contains(
                "if(!P.closest(\".display-normal\")){f=requestAnimationFrame(A);return}"
            )
        )
        assertFalse(
            result.html.contains(
                "!function A(){let r,n;s.clearRect(0,0,500,500),"
            )
        )
    }

    @Test
    fun `does not pause hidden canvas for another theme`() {
        val result =
            ProjectionDisplayHtmlPolicy.preserveUserDisplaySelection(
                supportedSportRuntimeBundle,
                "AnotherTheme"
            )

        assertFalse(result.pausedHiddenSportCanvas)
        assertTrue(
            result.html.contains(
                "!function A(){let r,n;s.clearRect(0,0,500,500),"
            )
        )
    }

    @Test
    fun `does not patch hidden Sport canvas twice`() {
        val first =
            ProjectionDisplayHtmlPolicy.preserveUserDisplaySelection(
                supportedSportRuntimeBundle,
                "SportRedLite"
            )
        val second =
            ProjectionDisplayHtmlPolicy.preserveUserDisplaySelection(
                first.html,
                "SportRedLite"
            )

        assertTrue(first.pausedHiddenSportCanvas)
        assertFalse(second.pausedHiddenSportCanvas)
        assertEquals(first.html, second.html)
    }

    @Test
    fun `does not inject Sport visual polish into another theme with matching selectors`() {
        val result =
            ProjectionDisplayHtmlPolicy.preserveUserDisplaySelection(
                supportedSportStructure,
                "AnotherTheme"
            )

        assertFalse(result.injectedSportVisualPolish)
        assertFalse(result.tunedSportGaugeMotion)
        assertFalse(result.pausedHiddenSportCanvas)
        assertFalse(result.html.contains("data-haval-sport-visual-polish"))
    }

    @Test
    fun `does not inject Sport visual polish twice`() {
        val first =
            ProjectionDisplayHtmlPolicy.preserveUserDisplaySelection(
                supportedSportBundle,
                "SportRed"
            )
        val second =
            ProjectionDisplayHtmlPolicy.preserveUserDisplaySelection(
                first.html,
                "SportRed"
            )

        assertTrue(first.injectedSportVisualPolish)
        assertTrue(first.tunedSportGaugeMotion)
        assertFalse(second.injectedSportVisualPolish)
        assertFalse(second.tunedSportGaugeMotion)
        assertEquals(
            1,
            Regex("data-haval-sport-visual-polish").findAll(second.html).count()
        )
    }

    @Test
    fun `fails closed when known Sport bundle structure is incomplete`() {
        val incompleteSportBundle =
            supportedSportStructure.replace(".g20-v2-native-status-mock{}", "")

        val result =
            ProjectionDisplayHtmlPolicy.preserveUserDisplaySelection(
                incompleteSportBundle,
                "SportRed"
            )

        assertFalse(result.injectedSportVisualPolish)
        assertFalse(result.tunedSportGaugeMotion)
        assertFalse(result.html.contains("data-haval-sport-visual-polish"))
    }

    @Test
    fun `fails closed when Sport gauge motion signature is incomplete`() {
        val incompleteMotion =
            supportedSportBundle.replace(
                "Math.abs(eU.powerKw-ew)>=80&&(ew=eU.powerKw),",
                ""
            )

        val result =
            ProjectionDisplayHtmlPolicy.preserveUserDisplaySelection(
                incompleteMotion,
                "SportRed"
            )

        assertFalse(result.tunedSportGaugeMotion)
        assertTrue(result.html.contains("Math.exp(-i/180)"))
        assertTrue(result.html.contains("Math.abs(eU.speed-eK)>=30"))
        assertTrue(result.html.contains("transition:x1 80ms linear"))
    }
}
