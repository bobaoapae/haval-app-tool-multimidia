package br.com.redesurftank.havalshisuku.projectors

internal object ProjectionDisplayHtmlPolicy {
    internal data class Result(
        val html: String,
        val removedLegacyOverrides: Int,
        val injectedCarPlayFullBleedMask: Boolean,
        val injectedSportVisualPolish: Boolean,
        val tunedSportGaugeMotion: Boolean,
        val pausedHiddenSportCanvas: Boolean
    )

    private const val PROJECTION_FULL_BLEED_STYLE_MARKER =
        "data-haval-projection-full-bleed-mask"
    private const val SPORT_VISUAL_POLISH_STYLE_MARKER =
        "data-haval-sport-visual-polish"
    private const val SPORT_FLOATING_DATA_ASSET =
        "file:///android_asset/sport_visual/sport-floating-data-glow.svg"
    private const val SPORT_FLOATING_HEADER_ASSET =
        "file:///android_asset/sport_visual/sport-floating-header-glow.svg"
    private const val SPORT_ANALOG_V2_CUTOUT_SELECTOR =
        ".display-analogico-v2 .cluster-mask-bg:after{"
    private const val SPORT_ANALOG_V2_OPAQUE_CUTOUT =
        "box-shadow:0 0 0 9999px #000"
    private const val SPORT_GAUGE_OLD_EASING = "Math.exp(-i/180)"
    private const val SPORT_GAUGE_NEW_EASING = "Math.exp(-i/100)"
    private const val SPORT_GAUGE_SPEED_SNAP =
        "Math.abs(eU.speed-eK)>=30&&(eK=eU.speed),"
    private const val SPORT_GAUGE_POWER_SNAP =
        "Math.abs(eU.powerKw-ew)>=80&&(ew=eU.powerKw),"
    private const val SPORT_GAUGE_OLD_SPEED_TRANSITION =
        "transition:x1 80ms linear,y1 80ms linear,x2 80ms linear,y2 80ms linear"
    private const val SPORT_GAUGE_NEW_SPEED_TRANSITION =
        "transition:x1 34ms linear,y1 34ms linear,x2 34ms linear,y2 34ms linear"
    private const val SPORT_HIDDEN_CANVAS_LOOP_START =
        "!function A(){let r,n;s.clearRect(0,0,500,500),"
    private const val SPORT_VISIBLE_CANVAS_LOOP_START =
        "!function A(){let r,n;if(!P.closest(\".display-normal\")){f=requestAnimationFrame(A);return}s.clearRect(0,0,500,500),"

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
     * Keeps the approved Sport 0.16.44 layout adjustments identical to the Theme Lab preview
     * without rewriting the downloaded legacy packages. The selectors are static, scoped to the
     * two known Sport folders and injected only when the expected opaque-bundle structure is
     * present. There is no DOM mutation, listener, animation or runtime loop. The two small SVG
     * assets are bundled in the APK, rendered once and never fetched from the network.
     */
    private val sportVisualPolishStyle =
        """
        <style $SPORT_VISUAL_POLISH_STYLE_MARKER="1">
        .g20-v2-native-status-mock {
          display: none !important;
        }
        .g20-v2-speed-readout,
        .g20-v2-power-readout {
          top: 380px !important;
        }
        .dashboard-top-center {
          box-sizing: border-box !important;
          left: 50% !important;
          width: 650px !important;
          height: 70px !important;
          padding: 0 !important;
          align-items: center !important;
          justify-content: center !important;
          gap: 72px !important;
          border: 0 !important;
          border-radius: 0 !important;
          background: transparent !important;
          box-shadow: none !important;
          isolation: isolate !important;
          transform: translateX(-50%) !important;
          overflow: visible !important;
        }
        .dashboard-top-center::before {
          content: "" !important;
          display: block !important;
          position: absolute !important;
          top: -2px !important;
          right: auto !important;
          bottom: auto !important;
          left: 0 !important;
          width: 100% !important;
          height: 74px !important;
          border: 0 !important;
          border-radius: 0 !important;
          background-color: transparent !important;
          background-image: url("$SPORT_FLOATING_HEADER_ASSET") !important;
          background-position: center !important;
          background-repeat: no-repeat !important;
          background-size: 100% 100% !important;
          box-shadow: none !important;
          pointer-events: none !important;
          transform: none !important;
          transform-origin: center !important;
          z-index: 0 !important;
        }
        .dashboard-top-center::after {
          content: none !important;
          display: none !important;
        }
        .dashboard-top-center > .dashboard-clock,
        .dashboard-top-center > .dashboard-gear,
        .dashboard-top-center > .dashboard-ev-mode {
          position: relative !important;
          z-index: 1 !important;
          text-shadow:
            0 2px 8px rgba(0, 0, 0, 0.98),
            0 0 18px rgba(0, 0, 0, 0.78) !important;
        }
        #app.display-analogico-v2 .g20-v2-bottom-bar {
          isolation: isolate !important;
          overflow: visible !important;
        }
        #app.display-analogico-v2 .g20-v2-bottom-bar::before {
          content: "" !important;
          display: block !important;
          position: absolute !important;
          left: 460px !important;
          bottom: -8px !important;
          width: 1000px !important;
          height: 114px !important;
          border: 0 !important;
          background-color: transparent !important;
          background-image: url("$SPORT_FLOATING_DATA_ASSET") !important;
          background-position: center !important;
          background-repeat: no-repeat !important;
          background-size: 100% 100% !important;
          box-shadow: none !important;
          pointer-events: none !important;
          z-index: 0 !important;
        }
        #app.display-analogico-v2 .g20-v2-bottom-bar::after {
          content: none !important;
          display: none !important;
        }
        #app.display-analogico-v2 .external-temp-container,
        #app.display-analogico-v2 .internal-temp-container {
          width: 120px !important;
          height: 58px !important;
          min-height: 58px !important;
          bottom: 6px !important;
          padding: 7px 10px 6px !important;
          border: 0 !important;
          border-radius: 0 !important;
          background: transparent !important;
          box-shadow: none !important;
          transform: none !important;
        }
        #app.display-analogico-v2 .g20-v2-map-odometer {
          box-sizing: border-box !important;
          flex: 0 0 320px !important;
          width: 320px !important;
          height: 58px !important;
          min-height: 58px !important;
          margin: 0 !important;
          border: 0 !important;
          border-radius: 0 !important;
          background: transparent !important;
          box-shadow: none !important;
          overflow: visible !important;
          transform: none !important;
          z-index: 1 !important;
        }
        #app.display-analogico-v2 .external-temp-container::before,
        #app.display-analogico-v2 .internal-temp-container::before,
        #app.display-analogico-v2 .g20-v2-map-odometer::before,
        #app.display-analogico-v2 .external-temp-container::after,
        #app.display-analogico-v2 .internal-temp-container::after,
        #app.display-analogico-v2 .g20-v2-map-odometer::after {
          content: none !important;
          display: none !important;
        }
        #app.display-analogico-v2 .external-temp-container .temp-value,
        #app.display-analogico-v2 .internal-temp-container .temp-value {
          color: rgba(249, 252, 255, 0.98) !important;
          font-size: 27px !important;
          font-weight: 600 !important;
          font-variant-numeric: tabular-nums !important;
          letter-spacing: 0 !important;
          line-height: 1 !important;
          text-shadow: 0 1px 4px rgba(0, 0, 0, 0.85) !important;
        }
        #app.display-analogico-v2 .external-temp-container .temp-sub-label,
        #app.display-analogico-v2 .internal-temp-container .temp-sub-label {
          margin-top: 4px !important;
          color: rgba(235, 240, 248, 0.76) !important;
          font-size: 11px !important;
          font-weight: 600 !important;
          letter-spacing: 1.6px !important;
          line-height: 1 !important;
        }
        #app.display-analogico-v2 .g20-v2-map-odometer .odometer-text-wrapper,
        #app.display-analogico-v2 .g20-v2-map-odometer .odometer-text-wrapper.single-line,
        #app.display-analogico-v2 .g20-v2-map-odometer .odometer-text-wrapper.double-line {
          box-sizing: border-box !important;
          justify-content: center !important;
          gap: 2px !important;
          width: 100% !important;
          height: 100% !important;
          padding: 5px 18px 4px !important;
          transform: none !important;
        }
        #app.display-analogico-v2 .g20-v2-map-odometer .odometer-line {
          display: flex !important;
          align-items: baseline !important;
          justify-content: center !important;
          gap: 5px !important;
          margin: 0 !important;
          line-height: 1 !important;
          text-shadow: 0 1px 4px rgba(0, 0, 0, 0.85) !important;
        }
        #app.display-analogico-v2 .g20-v2-map-odometer .odometer-label {
          color: rgba(var(--perf-light-rgb), 0.90) !important;
          font-size: 12px !important;
          font-weight: 600 !important;
          letter-spacing: 1.1px !important;
          text-transform: uppercase !important;
        }
        #app.display-analogico-v2 .g20-v2-map-odometer .odometer-value {
          color: rgba(249, 252, 255, 0.98) !important;
          font-size: 27px !important;
          font-weight: 500 !important;
          font-variant-numeric: tabular-nums !important;
          letter-spacing: 1px !important;
        }
        #app.display-analogico-v2 .g20-v2-map-odometer .odometer-unit {
          color: rgba(235, 240, 248, 0.74) !important;
          font-size: 14px !important;
          font-weight: 400 !important;
          letter-spacing: 0.2px !important;
        }
        #app.display-analogico-v2 .g20-v2-map-odometer .revision-line {
          position: static !important;
          top: auto !important;
          width: 100% !important;
          margin: 0 !important;
          color: rgba(235, 240, 248, 0.82) !important;
          font-size: 13px !important;
          font-weight: 400 !important;
          letter-spacing: 0.15px !important;
          line-height: 1.1 !important;
          text-align: center !important;
          text-shadow: 0 1px 4px rgba(0, 0, 0, 0.85) !important;
          transform: none !important;
        }
        .display-analogico-v2 .g20-v2-scale-comb {
          stroke-dasharray: 1.5 4.5 !important;
          stroke-dashoffset: 0 !important;
        }
        .display-analogico-v2 .g20-v2-scale-comb.soft {
          stroke-dasharray: 3 8 !important;
          stroke-dashoffset: 0 !important;
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

    /**
     * The immutable Sport 0.16.44 bundle already has a bounded requestAnimationFrame interpolator
     * for both Analogico V2 needles. Its 180 ms exponential time constant takes roughly 540 ms to
     * visually settle, while speed also receives an extra 80 ms SVG transition. Large changes
     * bypass the interpolator entirely, which makes the same gauge alternate between lag and snap.
     *
     * Keep the existing 30 fps ceiling, but shorten the interpolator to 100 ms, remove the two
     * snap shortcuts and limit the speed-only SVG transition to one 30 fps frame. The rewrite is
     * atomic and fail-closed: an unknown or partially changed bundle is returned untouched.
     */
    private fun tuneLegacySportGaugeMotion(html: String): Pair<String, Boolean> {
        val expectedFragments =
            listOf(
                SPORT_GAUGE_OLD_EASING,
                SPORT_GAUGE_SPEED_SNAP,
                SPORT_GAUGE_POWER_SNAP,
                SPORT_GAUGE_OLD_SPEED_TRANSITION
            )
        if (expectedFragments.any { fragment -> html.countOccurrences(fragment) != 1 }) {
            return html to false
        }

        val tunedHtml =
            html
                .replace(SPORT_GAUGE_OLD_EASING, SPORT_GAUGE_NEW_EASING)
                .replace(SPORT_GAUGE_SPEED_SNAP, "")
                .replace(SPORT_GAUGE_POWER_SNAP, "")
                .replace(
                    SPORT_GAUGE_OLD_SPEED_TRANSITION,
                    SPORT_GAUGE_NEW_SPEED_TRANSITION
                )
        return tunedHtml to true
    }

    /**
     * The Normal-mode speedometer canvas is created for every Sport display, but 0.16.44 keeps
     * drawing its gradients and shadows on every animation frame even while Analogico V2 hides
     * the whole widget with display:none. Skip that drawing work until Normal is selected. The
     * requestAnimationFrame heartbeat stays alive, so changing display resumes the original
     * renderer immediately and cleanup can still cancel the same frame handle.
     */
    private fun pauseLegacySportCanvasWhenHidden(html: String): Pair<String, Boolean> {
        if (html.countOccurrences(SPORT_HIDDEN_CANVAS_LOOP_START) != 1 ||
            html.contains(SPORT_VISIBLE_CANVAS_LOOP_START)
        ) {
            return html to false
        }
        return html.replace(SPORT_HIDDEN_CANVAS_LOOP_START, SPORT_VISIBLE_CANVAS_LOOP_START) to true
    }

    fun preserveUserDisplaySelection(html: String, themeName: String? = null): Result {
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

        val isKnownSportTheme =
            themeName.equals("SportRed", ignoreCase = true) ||
                themeName.equals("SportRedLite", ignoreCase = true)
        val hasKnownSportStructure =
            projectionPatchedHtml.contains(SPORT_ANALOG_V2_CUTOUT_SELECTOR) &&
                projectionPatchedHtml.contains(SPORT_ANALOG_V2_OPAQUE_CUTOUT) &&
                projectionPatchedHtml.contains(".dashboard-top-center") &&
                projectionPatchedHtml.contains(".g20-v2-bottom-bar") &&
                projectionPatchedHtml.contains(".g20-v2-map-odometer") &&
                projectionPatchedHtml.contains(".g20-v2-scale-comb") &&
                projectionPatchedHtml.contains(".g20-v2-native-status-mock") &&
                projectionPatchedHtml.contains(".g20-v2-speed-readout") &&
                projectionPatchedHtml.contains(".g20-v2-power-readout") &&
                projectionPatchedHtml.contains("g20-v2-cluster")
        val (motionPatchedHtml, tunedSportGaugeMotion) =
            if (isKnownSportTheme && hasKnownSportStructure) {
                tuneLegacySportGaugeMotion(projectionPatchedHtml)
            } else {
                projectionPatchedHtml to false
            }
        val (runtimePatchedHtml, pausedHiddenSportCanvas) =
            if (isKnownSportTheme && hasKnownSportStructure) {
                pauseLegacySportCanvasWhenHidden(motionPatchedHtml)
            } else {
                motionPatchedHtml to false
            }
        val shouldInjectSportVisualPolish =
            isKnownSportTheme &&
                hasKnownSportStructure &&
                !runtimePatchedHtml.contains(SPORT_VISUAL_POLISH_STYLE_MARKER)
        val fullyPatchedHtml =
            if (shouldInjectSportVisualPolish) {
                injectBeforeClosingHead(runtimePatchedHtml, sportVisualPolishStyle)
            } else {
                runtimePatchedHtml
            }

        return Result(
            html = fullyPatchedHtml,
            removedLegacyOverrides = removedOverrides,
            injectedCarPlayFullBleedMask = shouldInjectCarPlayFullBleedMask,
            injectedSportVisualPolish = shouldInjectSportVisualPolish,
            tunedSportGaugeMotion = tunedSportGaugeMotion,
            pausedHiddenSportCanvas = pausedHiddenSportCanvas
        )
    }

    private fun String.countOccurrences(fragment: String): Int {
        if (fragment.isEmpty()) return 0
        var count = 0
        var index = indexOf(fragment)
        while (index >= 0) {
            count += 1
            index = indexOf(fragment, index + fragment.length)
        }
        return count
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
