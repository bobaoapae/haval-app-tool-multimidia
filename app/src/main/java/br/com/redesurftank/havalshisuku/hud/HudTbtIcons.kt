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
    /** Path data on a [viewBox] square whose top-left corner is ([originX], [originY]). */
    class Svg(
        val viewBox: Float,
        vararg val paths: String,
        val originX: Float = 0f,
        val originY: Float = 0f,
        /** Merge the paths into one outline (overlapping parts would otherwise cancel out). */
        val union: Boolean = false
    )

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

    // Google's icons (owner, 2026-10-25): Material Symbols Rounded, weight 700, filled, the set
    // Google Maps uses (Apache 2.0, fonts.google.com/icons). Drawn on a 960 grid with y from
    // -960; the glyphs sit inside 80..880, so that box is scaled to the icon square.
    private const val MATERIAL_BOX = 800f
    private const val MATERIAL_ORIGIN_X = 80f
    private const val MATERIAL_ORIGIN_Y = -880f
    // Fork (owner, 2026-10-25): the slight turn plus the road going on straight, no arrowhead
    // on that one (the library's fork glyph read wrong). The bar is the stem's own width.
    private const val FORK_ON_RIGHT = "M343-440V-753Q343-772.5 356.75-786.25Q370.5-800 390-800Q409.5-800 423.25-786.25Q437-772.5 437-753V-440Z"
    private const val FORK_ON_LEFT = "M529-440V-753Q529-772.5 542.75-786.25Q556.5-800 576-800Q595.5-800 609.25-786.25Q623-772.5 623-753V-440Z"
    private val MATERIAL_SVGS = mapOf(
        "fork_right" to Svg(MATERIAL_BOX, "M356.5-148.63Q343-162.25 343-182v-266q0-18.09 7.5-35.54Q358-501 371-514l216-217h-79q-20.75 0-34.37-13.68Q460-758.35 460-778.18 460-798 473.63-812q13.62-14 34.37-14h193q19.75 0 33.88 14.12Q749-797.75 749-778v193q0 20.75-14.18 34.37-14.17 13.63-34 13.63-19.82 0-33.32-13.63Q654-564.25 654-585v-79L437-448v266q0 19.75-13.68 33.37Q409.65-135 389.82-135q-19.82 0-33.32-13.63Z", FORK_ON_RIGHT, originX = MATERIAL_ORIGIN_X, originY = MATERIAL_ORIGIN_Y, union = true),
        "fork_left" to Svg(MATERIAL_BOX, "M542.5-148.63Q529-162.25 529-182v-266L312-664v79q0 20.75-13.68 34.37Q284.65-537 264.82-537q-19.82 0-33.32-13.63Q218-564.25 218-585v-193q0-19.75 13.63-33.88Q245.25-826 265-826h194q19.75 0 33.38 14.18 13.62 14.17 13.62 34 0 19.82-13.62 33.32Q478.75-731 459-731h-80l216 217q13 13 20.5 30.46Q623-466.09 623-448v266q0 19.75-13.68 33.37Q595.65-135 575.82-135q-19.82 0-33.32-13.63Z", FORK_ON_LEFT, originX = MATERIAL_ORIGIN_X, originY = MATERIAL_ORIGIN_Y, union = true),
        "turn_right" to Svg(MATERIAL_BOX, "M263-182v-329q0-39 27.5-66.5T357-605h328l-56-57q-15-14-15-32.5t14.5-33Q643-742 662-742t33 14l137 136q14 14 14 34t-14 34L695-388q-14 15-32.5 14.5t-33-15Q615-403 615-422t14-33l56-56H357v329q0 20-13.5 33.5T310-135q-20 0-33.5-13.5T263-182Z", originX = MATERIAL_ORIGIN_X, originY = MATERIAL_ORIGIN_Y),
        "turn_left" to Svg(MATERIAL_BOX, "m275-511 57 56q14 15 13.5 34t-15 33.5Q316-373 297.5-373T264-388L128-524q-14-14-14-34t14-34l137-136q14-15 33-15t33.5 14.5Q346-714 346-695t-14 33l-57 57h328q39 0 66.5 27.5T697-511v329q0 20-13.5 33.5T650-135q-20 0-33.5-13.5T603-182v-329H275Z", originX = MATERIAL_ORIGIN_X, originY = MATERIAL_ORIGIN_Y),
        "turn_slight_right" to Svg(MATERIAL_BOX, "M356.5-148.63Q343-162.25 343-182v-266q0-18.09 7.5-35.54Q358-501 371-514l216-217h-79q-20.75 0-34.37-13.68Q460-758.35 460-778.18 460-798 473.63-812q13.62-14 34.37-14h193q19.75 0 33.88 14.12Q749-797.75 749-778v193q0 20.75-14.18 34.37-14.17 13.63-34 13.63-19.82 0-33.32-13.63Q654-564.25 654-585v-79L437-448v266q0 19.75-13.68 33.37Q409.65-135 389.82-135q-19.82 0-33.32-13.63Z", originX = MATERIAL_ORIGIN_X, originY = MATERIAL_ORIGIN_Y),
        "turn_slight_left" to Svg(MATERIAL_BOX, "M542.5-148.63Q529-162.25 529-182v-266L312-664v79q0 20.75-13.68 34.37Q284.65-537 264.82-537q-19.82 0-33.32-13.63Q218-564.25 218-585v-193q0-19.75 13.63-33.88Q245.25-826 265-826h194q19.75 0 33.38 14.18 13.62 14.17 13.62 34 0 19.82-13.62 33.32Q478.75-731 459-731h-80l216 217q13 13 20.5 30.46Q623-466.09 623-448v266q0 19.75-13.68 33.37Q595.65-135 575.82-135q-19.82 0-33.32-13.63Z", originX = MATERIAL_ORIGIN_X, originY = MATERIAL_ORIGIN_Y),
        "straight" to Svg(MATERIAL_BOX, "m433-686-57 57q-14 14-33 14t-33-14q-14-14-14-33t14-33l136-137q14-14 34-14t34 14l136 137q14 14 14 33t-14 33q-14 14-33 14.5T584-629l-57-57v544q0 20-13.5 33.5T480-95q-20 0-33.5-13.5T433-142v-544Z", originX = MATERIAL_ORIGIN_X, originY = MATERIAL_ORIGIN_Y),
        "u_turn_left" to Svg(MATERIAL_BOX, "M249.5-277q-8.5-4-15.5-11L98-424q-15-14-15-33t15-33q14-14 32.5-14t33.5 14l57 57v-172q0-108 76-184.5T481.5-866q108.5 0 185 76.5T743-605v434q0 20-14 33.5T695-124q-20 0-33.5-13.5T648-171v-434q0-69-48.5-117.5t-118-48.5q-69.5 0-118 48.5T315-605v172l59-58q14-14 31.5-13.5T437-490q15 15 15 33.5T438-424L302-288q-7 7-16 11t-18.5 4q-9.5 0-18-4Z", originX = MATERIAL_ORIGIN_X, originY = MATERIAL_ORIGIN_Y),
        "u_turn_right" to Svg(MATERIAL_BOX, "M234.5-137.5Q221-151 221-171v-434q0-108 76-184.5T481.5-866q108.5 0 185 76.5T743-605v172l57-57q14-14 32.5-14t33.5 14q14 14 14 33t-14 33L729-288q-7 7-15.5 11t-18 4q-9.5 0-18.5-4t-15-11L525-424q-14-14-14-32.5t15-33.5q14-14 31.5-14.5T590-491l58 58v-172q0-69-48.5-117.5t-118-48.5q-69.5 0-118 48.5T315-605v434q0 20-13.5 33.5T268-124q-20 0-33.5-13.5Z", originX = MATERIAL_ORIGIN_X, originY = MATERIAL_ORIGIN_Y),
        "ramp_right" to Svg(MATERIAL_BOX, "M444.5-108.5Q431-122 431-142v-189q-25 31-61 61.5T289-212q-19 13-40.5 11T211-218q-14-14-8.5-33t23.5-32q121-79 163-141.5T431-565v-121l-57 57q-14 15-33 14.5T308-629q-14-14-14-33t14-33l136-137q7-7 16-11t18-4q9 0 18 4t16 11l136 137q14 14 14 33t-14 33q-14 14-33 14t-33-14l-57-57v544q0 20-13.5 33.5T478-95q-20 0-33.5-13.5Z", originX = MATERIAL_ORIGIN_X, originY = MATERIAL_ORIGIN_Y),
        "ramp_left" to Svg(MATERIAL_BOX, "M435-142v-544l-57 57q-14 14-33 14t-33-14q-14-14-14-33t14-33l136-137q7-7 16-11t18-4q9 0 18 4t16 11l136 137q14 14 14 33t-14 33q-14 14-33 14.5T586-629l-57-57v121q0 78 42 140.5T735-283q17 13 22.5 32t-8.5 33q-16 15-37.5 17T671-212q-45-27-81-57.5T529-331v189q0 20-13.5 33.5T482-95q-20 0-33.5-13.5T435-142Z", originX = MATERIAL_ORIGIN_X, originY = MATERIAL_ORIGIN_Y),
        "merge" to Svg(MATERIAL_BOX, "M480-334 274-128q-14 15-33 14.5T208-128q-14-14-14-32.5t14-33.5l190-190q21-21 28-38t7-53v-211l-57 57q-14 15-33 15t-33-15q-14-14-14-32.5t14-33.5l136-136q14-14 34-14t34 14l136 136q14 15 14 33.5T650-629q-14 15-33 15t-33-14l-57-58v211q0 36 7 53t28 38l190 190q14 15 14 34t-14 32q-14 15-33 15t-33-15L480-334Z", originX = MATERIAL_ORIGIN_X, originY = MATERIAL_ORIGIN_Y),
        "roundabout_right" to Svg(MATERIAL_BOX, "M265-172v-185q-91-16-150.5-87T55-608q0-107 75-182.5T313-866q93 0 164 59.5T564-656h163l-57-57q-14-14-14-33t13-33q14-14 33-14t33 14l137 137q7 7 11 15.5t4 18q0 9.5-4 18.5t-11 15L735-438q-14 14-33 14t-33-14q-13-14-13-33t13-32l58-58H552q-29 0-51.5-17.5T474-624q-7-63-52.5-105T313-771q-68 0-115.5 47.5T150-608q0 62 41.5 107.5T296-448q28 5 46 27t18 51v198q0 20-13.5 33.5t-34 13.5q-20.5 0-34-13.5T265-172Z", originX = MATERIAL_ORIGIN_X, originY = MATERIAL_ORIGIN_Y),
        "roundabout_left" to Svg(MATERIAL_BOX, "M601-172v-198q0-29 17.58-51.1T665-448q63.14-7.28 104.57-52.67Q811-546.06 811-607.94q0-68.06-47.26-115.56Q716.47-771 648-771q-62.42 0-108.71 42Q493-687 487-624q-4.68 25.84-27.77 44.42Q436.14-561 409-561H234l57 57q13 14 13.5 33T292-438q-14 14-33.5 14T226-438L89-575q-7-6-11-15t-4-18.5q0-9.5 4.05-18.1Q82.09-635.2 89-642l137-137q14.25-14.17 33.13-13.58Q278-792 292-779q14 14.53 14 33.27Q306-727 292-713l-58 57h163q15-91 87-150.5T648.09-866q106.88 0 182.39 75.52Q906-714.97 906-608.09 906-516 846.5-444T696-357v185q0 20-14.09 33.5t-34 13.5q-19.91 0-33.41-13.5Q601-152 601-172Z", originX = MATERIAL_ORIGIN_X, originY = MATERIAL_ORIGIN_Y)
    )

    /** A Material Symbols maneuver icon ([HudOverlayModel.googleIcon]) scaled into a [size] square, or null. */
    fun googlePath(name: String, size: Float): Path? {
        val key = "g:$name@$size"
        if (pathCache.containsKey(key)) return pathCache[key]
        val path = MATERIAL_SVGS[name]?.let { build(it, size) }
        pathCache[key] = path
        return path
    }

    private fun build(svg: Svg, size: Float): Path? = try {
        val out = Path()
        for (d in svg.paths) {
            val p = PathParser.createPathFromPathData(d)
            if (svg.union && !out.isEmpty) out.op(p, Path.Op.UNION) else out.addPath(p)
        }
        out.transform(Matrix().apply {
            setTranslate(-svg.originX, -svg.originY)
            postScale(size / svg.viewBox, size / svg.viewBox)
        })
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
