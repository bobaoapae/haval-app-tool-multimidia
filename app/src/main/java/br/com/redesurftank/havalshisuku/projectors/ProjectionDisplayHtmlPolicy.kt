package br.com.redesurftank.havalshisuku.projectors

internal object ProjectionDisplayHtmlPolicy {
    internal data class Result(
        val html: String,
        val removedLegacyOverrides: Int,
        val injectedCarPlayFullBleedMask: Boolean
    )

    private const val PROJECTION_FULL_BLEED_STYLE_MARKER =
        "data-haval-projection-full-bleed-mask"
    private const val SPORT_ANALOG_V2_CUTOUT_SELECTOR =
        ".display-analogico-v2 .cluster-mask-bg:after{"
    private const val SPORT_ANALOG_V2_OPAQUE_CUTOUT =
        "box-shadow:0 0 0 9999px #000"

    /**
     * Sport 0.16.44 uses a large black box-shadow to leave only a narrow map window between the
     * Analogico V2 gauges. While CarPlay is on D3 its video Surface already fills 1920x720, so
     * replace that opaque cutout with two static linear edge gradients. Each edge stays nearly
     * opaque below its gauge and fades continuously toward the clear central navigation area.
     * The theme's full-width top mask also becomes transparent while the independent center
     * status capsule remains intact. These rules avoid filters/blur to keep the WebView compositor
     * cost bounded. The `carplay-in-dash` selector deliberately keeps Android Auto outside this
     * compatibility.
     */
    private val projectionFullBleedMaskStyle =
        """
        <style $PROJECTION_FULL_BLEED_STYLE_MARKER="1">
        .carplay-in-dash.display-analogico-v2 .cluster-mask-bg::after {
          content: "" !important;
          display: block !important;
          position: absolute !important;
          inset: 0 !important;
          top: 0 !important;
          right: 0 !important;
          bottom: 0 !important;
          left: 0 !important;
          border-radius: 0 !important;
          box-shadow: none !important;
          background-color: transparent !important;
          background-image:
            linear-gradient(
              90deg,
              rgba(0, 0, 0, 0.98) 0%,
              rgba(0, 0, 0, 0.96) 10%,
              rgba(0, 0, 0, 0.88) 20%,
              rgba(0, 0, 0, 0.68) 28%,
              rgba(0, 0, 0, 0.42) 36%,
              rgba(0, 0, 0, 0.18) 43%,
              rgba(0, 0, 0, 0.06) 47%,
              transparent 50%
            ),
            linear-gradient(
              270deg,
              rgba(0, 0, 0, 0.98) 0%,
              rgba(0, 0, 0, 0.96) 10%,
              rgba(0, 0, 0, 0.88) 20%,
              rgba(0, 0, 0, 0.68) 28%,
              rgba(0, 0, 0, 0.42) 36%,
              rgba(0, 0, 0, 0.18) 43%,
              rgba(0, 0, 0, 0.06) 47%,
              transparent 50%
            ) !important;
          pointer-events: none !important;
        }
        .carplay-in-dash.display-analogico-v2 .mask-top-bar {
          background: transparent !important;
          background-image: none !important;
          box-shadow: none !important;
        }
        </style>
        """.trimIndent()

    /**
     * SportRed 0.16.44 and SportRedLite 0.16.44 were distributed only as minified HTML and
     * replace every non-map display with Mapa Limpo while a projection is active. Keep the
     * projection transparency classes, but make the effective display come from the user setting.
     *
     * This compatibility pass runs in memory before WebView loading. It does not rewrite the
     * downloaded theme and becomes a no-op as soon as the theme bundle removes the legacy rule.
     */
    private val legacySportProjectionDisplayOverride = Regex(
        """function ([A-Za-z_][A-Za-z0-9_]*)\(\)\{if\(([A-Za-z_][A-Za-z0-9_]*)\(\)\)\{let ([A-Za-z_][A-Za-z0-9_]*)=([A-Za-z_][A-Za-z0-9_]*)\("display"\);return"Mapa"===([A-Za-z_][A-Za-z0-9_]*)\|\|"Mapa Graduado"===([A-Za-z_][A-Za-z0-9_]*)\|\|"Mapa Limpo"===([A-Za-z_][A-Za-z0-9_]*)\?([A-Za-z_][A-Za-z0-9_]*):"Mapa Limpo"\}return ([A-Za-z_][A-Za-z0-9_]*)\("display"\)\|\|"Normal"\}"""
    )

    fun preserveUserDisplaySelection(html: String): Result {
        var removedOverrides = 0
        val displayPatchedHtml = legacySportProjectionDisplayOverride.replace(html) { match ->
            val displayVariable = match.groupValues[3]
            val stateGetter = match.groupValues[4]
            val usesSameDisplayVariable =
                match.groupValues.slice(5..8).all { it == displayVariable }
            val usesSameStateGetter = match.groupValues[9] == stateGetter

            if (!usesSameDisplayVariable || !usesSameStateGetter) {
                match.value
            } else {
                removedOverrides += 1
                val effectiveDisplayFunction = match.groupValues[1]
                """function $effectiveDisplayFunction(){return $stateGetter("display")||"Normal"}"""
            }
        }

        val shouldInjectCarPlayFullBleedMask =
            !displayPatchedHtml.contains(PROJECTION_FULL_BLEED_STYLE_MARKER) &&
                displayPatchedHtml.contains(SPORT_ANALOG_V2_CUTOUT_SELECTOR) &&
                displayPatchedHtml.contains(SPORT_ANALOG_V2_OPAQUE_CUTOUT) &&
                displayPatchedHtml.contains("carplay-in-dash") &&
                displayPatchedHtml.contains("g20-v2-cluster")
        val projectionPatchedHtml =
            if (shouldInjectCarPlayFullBleedMask) {
                injectBeforeClosingHead(displayPatchedHtml, projectionFullBleedMaskStyle)
            } else {
                displayPatchedHtml
            }

        return Result(
            html = projectionPatchedHtml,
            removedLegacyOverrides = removedOverrides,
            injectedCarPlayFullBleedMask = shouldInjectCarPlayFullBleedMask
        )
    }

    private fun injectBeforeClosingHead(html: String, style: String): String {
        val closingHeadIndex = html.lastIndexOf("</head>", ignoreCase = true)
        return if (closingHeadIndex >= 0) {
            html.substring(0, closingHeadIndex) + style + html.substring(closingHeadIndex)
        } else {
            html + style
        }
    }
}
