package br.com.redesurftank.havalshisuku.hud

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * Geometry of the "Navegação AR" arrow, in HUD pixels and with no Android types (testable).
 * Keep the constants in step with the prototype in scripts/hud-simulator (draw3dArrow).
 *
 * Sits in the middle of the HUD under the car's own speed readout, see-through. Two parts:
 * - the stem: a wide trapezoid on the road, [BASE_W] at the bottom, straight sides converging on
 *   [APEX]. Far from the maneuver it runs to the top and its taper is the arrow (a long wedge
 *   pointing ahead);
 * - the arm: as the car arrives (between [BEND_START_M] and [BEND_FULL_M]) the stem's top comes
 *   down to the corner and a narrower arm with a head grows out of it, bending from straight to
 *   the maneuver's angle, laid in perspective.
 */
object HudArArrow {
    /** Centred on the native speed and the power/RPM arcs (one axis, owner's request). */
    const val CX = HudOverlayModel.ARC_CX
    // Viewport: the strip under the speed digits down to the bottom of the visible band.
    const val TOP = 126f
    const val BOTTOM = 200f
    /** Where the stem's straight sides meet (just above the viewport, so its top is thin). */
    const val APEX = 118f
    /** Stem width at the bottom edge. */
    const val BASE_W = 200f
    /** Projection horizon for the arm (same vanishing row as the stem). */
    const val HORIZON = APEX
    const val Z_NEAR = 4f
    /** Corner depth at 0 m: mid-height, so the arm has room. */
    const val Z_END = 7f
    /** Corner depth when the bend starts. */
    const val Z_CAP = 15f
    /** Depth per metre between Z_END and Z_CAP (~100 m of travel). */
    const val K = 0.08f
    /** Lateral scale for the arm, px per world unit at depth 1. */
    const val F_X = 199f
    /** Arm thickness at the 0 m corner depth; thinner further away. */
    const val ARM_W = 30f
    const val TURN_R = 1.4f
    const val TURN_LEN = 2.0f
    /** Head size relative to the arm thickness at its end. */
    const val HEAD_W_RATIO = 2.0f
    const val HEAD_L_RATIO = 1.1f
    /** Straight -> full turn between these distances. */
    const val BEND_START_M = 180f
    const val BEND_FULL_M = 40f
    /** Short fade at the top edge, so the clip there doesn't show as a hard line. */
    const val FADE_TOP_TO = 132f
    /** Fades out over the bottom of the visible band, so the optics' edge doesn't crop it hard. */
    const val FADE_BOTTOM_FROM = 180f
    /** Overall opacity over the road and the native HUD (owner: less see-through than 30 %). */
    const val OPACITY = 0.65f
    /** Within this distance the controller interpolates between AA updates for smooth motion. */
    const val SMOOTH_WITHIN_M = 200
    /** How far the turn arm may reach left of [CX]: stops short of the left column (x ~162). */
    const val ROOM_LEFT = 60f
    /** How far it may reach right of [CX] (x ~368). */
    const val ROOM_RIGHT = 146f

    /** One point of the arm on screen: position, thickness. */
    data class Pt(val x: Float, val y: Float, val w: Float)

    /**
     * The arrow for one frame. [stemTop] is the row the stem reaches (its width there follows
     * [stemWidthAt]); [arm] and [head] are empty / null while the arrow is still straight;
     * [armAlpha] fades the arm in with the bend.
     */
    class Shape(val stemTop: Float, val arm: List<Pt>, val head: FloatArray?, val armAlpha: Float)

    /**
     * Bend angle in degrees (+ right) for a maneuver token (AndroidAutoNavigationTelemetry
     * .turnFromManeuver), or null when there is no clean shape for it (roundabouts, ferries):
     * the renderer then keeps the flat icon.
     */
    fun angleFor(turn: String?): Float? = when (turn.orEmpty().uppercase()) {
        "STRAIGHT", "NAME_CHANGE", "DEPART", "DESTINATION", "DESTINATION_LEFT", "DESTINATION_RIGHT" -> 0f
        "TURN_RIGHT" -> 90f
        "TURN_LEFT" -> -90f
        "SLIGHT_RIGHT" -> 45f
        "SLIGHT_LEFT" -> -45f
        "SHARP_RIGHT" -> 135f
        "SHARP_LEFT" -> -135f
        "KEEP_RIGHT", "FORK_RIGHT", "MERGE_RIGHT", "ON_RAMP_RIGHT" -> 25f
        "KEEP_LEFT", "FORK_LEFT", "MERGE_LEFT", "ON_RAMP_LEFT" -> -25f
        "OFF_RAMP_RIGHT" -> 35f
        "OFF_RAMP_LEFT" -> -35f
        "U_TURN_RIGHT" -> 180f
        "U_TURN_LEFT", "U_TURN" -> -180f
        else -> null
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)

    /** 0 = straight arrow, 1 = the full turn. */
    fun bendAmount(meters: Float): Float =
        smooth(((BEND_START_M - meters) / (BEND_START_M - BEND_FULL_M)).coerceIn(0f, 1f))

    fun cornerDepth(meters: Float): Float = min(Z_CAP, Z_END + K * meters.coerceAtLeast(0f))

    fun project(x: Float, z: Float): Pair<Float, Float> =
        CX + F_X * x / z to HORIZON + (BOTTOM - HORIZON) * Z_NEAR / z

    /** Stem width at row [y]: linear in y, so the sides are straight lines meeting at [APEX]. */
    fun stemWidthAt(y: Float): Float = BASE_W * ((y - APEX) / (BOTTOM - APEX)).coerceAtLeast(0f)

    /** Row fade: in over [TOP]..[FADE_TOP_TO], out over [FADE_BOTTOM_FROM]..[BOTTOM]. */
    fun rowAlpha(y: Float): Float = when {
        y <= TOP || y >= BOTTOM -> 0f
        y < FADE_TOP_TO -> (y - TOP) / (FADE_TOP_TO - TOP)
        y > FADE_BOTTOM_FROM -> (BOTTOM - y) / (BOTTOM - FADE_BOTTOM_FROM)
        else -> 1f
    }

    /** Gradient position for a row: 0 at the base (lighter blue) .. 1 at the top (deeper blue). */
    fun rowGradient(y: Float): Float = ((BOTTOM - y) / (BOTTOM - TOP)).coerceIn(0f, 1f)

    /** Arm centreline in world space (x, z): from the corner, an arc, then a tail. */
    fun armCenterline(meters: Float, angleDeg: Float): List<Pair<Float, Float>> {
        val zc = cornerDepth(meters)
        val pts = ArrayList<Pair<Float, Float>>(120)
        pts += 0f to zc
        var x = 0f
        var z = zc
        var h = 0f
        val target = (angleDeg * bendAmount(meters) * PI / 180).toFloat()
        val sgn = if (target == 0f) 1f else sign(target)
        val ds = 0.06f
        repeat((abs(target) * TURN_R / ds).roundToInt()) {
            h += sgn * ds / TURN_R
            x += sin(h) * ds
            z += cos(h) * ds
            pts += x to z
        }
        repeat((TURN_LEN / ds).roundToInt()) {
            x += sin(h) * ds
            z += cos(h) * ds
            pts += x to z
        }
        return pts
    }

    /** The arrow for [meters] to a maneuver bending by [angleDeg]. */
    fun shape(meters: Float, angleDeg: Float): Shape {
        val bend = bendAmount(meters)
        val zc = cornerDepth(meters)
        val cornerY = project(0f, zc).second
        // The stem runs to the top while straight and comes down to the corner as the arm grows.
        val stemTop = TOP + (cornerY - TOP) * bend
        if (bend <= 0.02f) return Shape(TOP, emptyList(), null, 0f)

        var arm = armCenterline(meters, angleDeg).filter { it.second > 0.4f }.map { (x, z) ->
            val (sx, sy) = project(x, z)
            Pt(sx, sy, min(ARM_W, ARM_W * Z_END / z))
        }
        val e = arm.last()
        val prev = arm[maxOf(0, arm.size - 4)]
        val len = hypot(e.x - prev.x, e.y - prev.y).takeIf { it > 0f } ?: 1f
        val dx = (e.x - prev.x) / len
        val dy = (e.y - prev.y) / len
        val hw = e.w * HEAD_W_RATIO
        val hl = e.w * HEAD_L_RATIO
        var head = floatArrayOf(
            e.x + dx * hl, e.y + dy * hl,          // tip
            e.x - dy * hw / 2, e.y + dx * hw / 2,  // base, one side
            e.x + dy * hw / 2, e.y - dx * hw / 2   // base, other side
        )
        // Sharp turns and U-turns near the corner would sweep past the room on their side (their
        // tail comes back towards the camera), and left turns would run under the left column:
        // squeeze the arm sideways just enough; thickness is kept.
        fun room(x: Float) = if (x < CX) ROOM_LEFT else ROOM_RIGHT
        var squeeze = 1f
        for (p in arm) {
            val dxc = abs(p.x - CX)
            if (dxc > 0.01f) squeeze = min(squeeze, ((room(p.x) - p.w / 2) / dxc).coerceAtLeast(0f))
        }
        for (k in 0 until 3) {
            val dxc = abs(head[2 * k] - CX)
            if (dxc > 0.01f) squeeze = min(squeeze, room(head[2 * k]) / dxc)
        }
        if (squeeze < 1f) {
            arm = arm.map { it.copy(x = CX + (it.x - CX) * squeeze) }
            head = head.copyOf().also { for (k in 0 until 3) it[2 * k] = CX + (it[2 * k] - CX) * squeeze }
        }
        return Shape(stemTop, arm, head, bend)
    }
}
