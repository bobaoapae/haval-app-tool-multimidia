package br.com.redesurftank.havalshisuku.hud

import android.graphics.Matrix
import android.graphics.Path
import android.util.Log
import androidx.core.graphics.PathParser
import kotlin.math.cos
import kotlin.math.sin

/**
 * Turn and lane artwork for the HUD, copied from the cluster card so both screens draw the same
 * shapes. Keep in sync with cluster-widgets/source/v1.0/shared/tbt/tbtTurnIcons.js (TBT_TURN_SVGS,
 * TBT_TURN_GLYPHS) and tbtLanes.js (RIGHT_SHAPES, arrowHead).
 */
object HudTbtIcons {
    private const val TAG = "HudTbtIcons"

    /** A filled SVG icon: its viewBox side and path data. */
    class Svg(val viewBox: Float, vararg val paths: String)

    private val TURN_SVGS = mapOf(
        "TURN_RIGHT" to Svg(24f, "M20.72,6.46,15.64,2.23A1,1,0,0,0,14,3V5H7a5,5,0,0,0-5,5V20a2,2,0,0,0,2,2H6a2,2,0,0,0,2-2V11h6v2a1,1,0,0,0,1.64.77l5.08-4.23a2,2,0,0,0,0-3.08Z"),
        "TURN_LEFT" to Svg(24f, "M17,5H10V3a1,1,0,0,0-1.64-.77L3.28,6.46a2,2,0,0,0,0,3.08l5.08,4.23A1,1,0,0,0,10,13V11h6v9a2,2,0,0,0,2,2h2a2,2,0,0,0,2-2V10A5,5,0,0,0,17,5Z"),
        "STRAIGHT" to Svg(24f, "M17.77,8.36,13.54,3.28a2.08,2.08,0,0,0-3.08,0L6.23,8.36A1,1,0,0,0,7,10H9V21a1,1,0,0,0,1,1h4a1,1,0,0,0,1-1V10h2a1,1,0,0,0,.77-1.64Z"),
        "ROUNDABOUT" to Svg(
            486.039f,
            "M296.897,41.504c-67.547,0-124.165,47.687-137.954,111.191l-48.547-0.024l0.016-29.836L6.759,182.62l103.593,59.888l0.016-29.836l75.352,0.038c0.007,0,0.011,0,0.016,0l30-30.045c0-44.752,36.41-81.16,81.162-81.16c44.75,0,81.158,36.408,81.158,81.16s-36.408,81.16-81.158,81.16l-30,30v192.214h60V320.617c63.475-13.789,111.158-70.406,111.158-137.952C438.056,104.829,374.731,41.504,296.897,41.504z",
            "M450.11,152.439c1.926,9.782,2.945,19.887,2.945,30.226c0,10.035-0.98,20.007-2.889,29.774h29.113v-60H450.11z",
            "M326.843,29.397V0h-60.001v29.45c9.856-1.945,19.923-2.945,30.056-2.945C307.136,26.504,317.147,27.506,326.843,29.397z",
            "M218.898,200.726l-26.944,26.983l-28.753-0.014c14.085,41.608,47.045,74.504,88.696,88.495v-28.578l27.119-27.121C249.31,253.708,225.852,230.37,218.898,200.726z"
        ),
        "U_TURN" to Svg(24f, "M13.5,2A8.5,8.5,0,0,0,5,10.5V14H3a1,1,0,0,0-.77,1.64l5,6a1,1,0,0,0,1.54,0l5-6A1,1,0,0,0,13,14H11V10.5a2.5,2.5,0,0,1,5,0V21a1,1,0,0,0,1,1h4a1,1,0,0,0,1-1V10.5A8.51,8.51,0,0,0,13.5,2Z"),
        "MERGE" to Svg(20f, "M17.89 17.707L16.892 20c-3.137-1.366-5.496-3.152-6.892-5.275-1.396 2.123-3.755 3.91-6.892 5.275l-.998-2.293C5.14 16.389 8.55 14.102 8.55 10V7H5.5L10 0l4.5 7h-3.05v3c0 4.102 3.41 6.389 6.44 7.707z"),
        "FORK" to Svg(24f, "m9.78 11.16l-1.42 1.42a7.3 7.3 0 0 1-1.79-2.94l1.94-.49c.32.89.77 1.5 1.27 2.01M11 6L7 2L3 6h3.02c.02.81.08 1.54.19 2.17l1.94-.49C8.08 7.2 8.03 6.63 8.02 6zm10 0l-4-4l-4 4h2.99c-.1 3.68-1.28 4.75-2.54 5.88c-.5.44-1.01.92-1.45 1.55c-.34-.49-.73-.88-1.13-1.24L9.46 13.6c.93.85 1.54 1.54 1.54 3.4v5h2v-5c0-2.02.71-2.66 1.79-3.63c1.38-1.24 3.08-2.78 3.2-7.37z")
    )

    /** Text fallbacks for tokens with no SVG, as TBT_TURN_GLYPHS. */
    private val TURN_GLYPHS = mapOf(
        "EXIT" to "↗",
        "DESTINATION" to "◎"
    )
    const val FALLBACK_GLYPH = "➤"

    private val pathCache = HashMap<String, Path?>()

    /** Turn icon scaled into a [size] square at the origin, or null when it is a text glyph. */
    fun turnPath(icon: String, size: Float): Path? {
        val key = "$icon@$size"
        if (pathCache.containsKey(key)) return pathCache[key]
        val svg = TURN_SVGS[icon]
        val path = svg?.let { build(it, size) }
        pathCache[key] = path
        return path
    }

    fun turnGlyph(icon: String): String = TURN_GLYPHS[icon] ?: FALLBACK_GLYPH

    private fun build(svg: Svg, size: Float): Path? = try {
        val out = Path()
        for (d in svg.paths) out.addPath(PathParser.createPathFromPathData(d))
        out.transform(Matrix().apply { setScale(size / svg.viewBox, size / svg.viewBox) })
        out
    } catch (t: Throwable) {
        Log.w(TAG, "Bad turn icon path", t)
        null
    }

    // Lane arrows: 24x24 box, car enters bottom-centre, right-hand shapes only (left = mirrored).
    private const val HEAD_LENGTH = 5.5f
    private const val HEAD_SPREAD_DEG = 40.0

    private class LaneSpec(val stem: String, val tipX: Float, val tipY: Float, val angleDeg: Double)

    private val LANE_SPECS = mapOf(
        HudOverlayModel.LaneShape.STRAIGHT to LaneSpec("M12 22V3", 12f, 3f, 0.0),
        HudOverlayModel.LaneShape.SLIGHT to LaneSpec("M12 22V14L19 7", 19f, 7f, 45.0),
        HudOverlayModel.LaneShape.NORMAL to LaneSpec("M12 22V13Q12 8 17 8H21", 21f, 8f, 90.0),
        HudOverlayModel.LaneShape.SHARP to LaneSpec("M12 22V9L19 16", 19f, 16f, 135.0),
        HudOverlayModel.LaneShape.UTURN to LaneSpec("M8 22V10A4 4 0 0 1 16 10V17", 16f, 17f, 180.0)
    )

    private val laneCache = HashMap<HudOverlayModel.LaneShape, Path?>()

    /** Stroked lane arrow (stem + open head) in the 24x24 box. Mirror for left-hand shapes. */
    fun lanePath(shape: HudOverlayModel.LaneShape): Path? = laneCache.getOrPut(shape) {
        val spec = LANE_SPECS.getValue(shape)
        try {
            PathParser.createPathFromPathData(spec.stem).apply { addPath(arrowHead(spec)) }
        } catch (t: Throwable) {
            Log.w(TAG, "Bad lane path $shape", t)
            null
        }
    }

    /** Two wings swept back from the tip, as arrowHead() in tbtLanes.js. */
    private fun arrowHead(spec: LaneSpec): Path {
        val rad = Math.toRadians(spec.angleDeg)
        val bx = -sin(rad)
        val by = cos(rad)
        fun wing(spreadDeg: Double): Pair<Float, Float> {
            val t = Math.toRadians(spreadDeg)
            val wx = bx * cos(t) - by * sin(t)
            val wy = bx * sin(t) + by * cos(t)
            return (spec.tipX + wx * HEAD_LENGTH).toFloat() to (spec.tipY + wy * HEAD_LENGTH).toFloat()
        }
        val (ax, ay) = wing(HEAD_SPREAD_DEG)
        val (cx, cy) = wing(-HEAD_SPREAD_DEG)
        return Path().apply {
            moveTo(ax, ay)
            lineTo(spec.tipX, spec.tipY)
            lineTo(cx, cy)
        }
    }
}
