package br.com.redesurftank.havalshisuku.hud

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * Geometry of the "Navegação AR" turn arrow, in HUD pixels and with no Android types (testable).
 * Port of the prototype approved in scripts/hud-simulator (draw3dArrow); keep the constants in
 * step with it.
 *
 * A ribbon runs "down the road" from the bottom of the visible band towards a vanishing point
 * above the free box. It is always a straight arrow that morphs into the maneuver as the car
 * arrives: the bend eases in between [BEND_START_M] and [BEND_FULL_M] while the corner comes down
 * from a capped depth (far: straight arrow, head still visible) to mid-height at 0 m. The path is
 * in perspective; the thickness is laid in screen space (a ground-plane ribbon fanned into a road
 * at the base and flattened the turn arm into a sliver).
 */
object HudArArrow {
    // Viewport: from the top of the free box to the bottom of the visible band; the arrow runs
    // under the trip line and the power bar.
    const val TOP = 58f
    const val BOTTOM = 200f
    /** Camera pitched down: the horizon sits above the viewport. */
    const val HORIZON = 26f
    const val Z_NEAR = 4f
    /** Corner depth at 0 m: stops at mid-height so the turn has room. */
    const val Z_END = 7f
    /** Corner depth while far: a straight arrow whose head is still visible. */
    const val Z_CAP = 12.5f
    /** Depth per metre between Z_END and Z_CAP (~100 m of travel). */
    const val K = 0.055f
    /** Lateral scale, px per world unit at depth 1. */
    const val F_X = 117f
    /**
     * Thickness on screen: [BASE_W] px at the bottom edge, falling off as (Z_NEAR / z)^[WIDTH_EXP].
     * Steeper than plain perspective (exponent 1) so the base is wide while the far end stays as
     * thin as before (owner: "make the bottom bar of the arrow wider").
     */
    const val BASE_W = 100f
    const val WIDTH_EXP = 1.7f
    const val MAX_W = 110f
    /** Plain-perspective thickness in world units, used to size the head. */
    const val RIBBON_W = 1.5f
    const val TURN_R = 1.4f
    const val TURN_LEN = 2.0f
    const val HEAD_W = 2.4f
    const val HEAD_L = 1.0f
    /** Straight -> full turn between these distances. */
    const val BEND_START_M = 180f
    const val BEND_FULL_M = 40f
    /** Fully drawn below this row, fading out up to [TOP]. */
    const val FADE_FROM = 70f
    /** Within this distance the controller interpolates between AA updates for smooth motion. */
    const val SMOOTH_WITHIN_M = 200

    /** One point of the ribbon on screen: position, depth, thickness. */
    data class Pt(val x: Float, val y: Float, val z: Float, val w: Float)

    /** The ribbon's centreline on screen (bottom to tip) and its arrow head triangle. */
    class Shape(val ribbon: List<Pt>, val head: FloatArray)

    /**
     * Bend angle in degrees (+ right) for a maneuver token (AndroidAutoNavigationTelemetry
     * .turnFromManeuver), or null when the ribbon has no clean shape for it (roundabouts,
     * ferries): the renderer then keeps the flat icon.
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
        HudOverlayModel.BOX_CENTER_X + F_X * x / z to HORIZON + (BOTTOM - HORIZON) * Z_NEAR / z

    /** Row fade: 1 below [FADE_FROM], 0 at [TOP]. */
    fun rowAlpha(y: Float): Float =
        if (y >= FADE_FROM) 1f else ((y - TOP) / (FADE_FROM - TOP)).coerceIn(0f, 1f)

    /** Gradient position for a row: 0 at the base (deep blue) .. 1 at the top (light blue). */
    fun rowGradient(y: Float): Float = ((BOTTOM - y) / (BOTTOM - TOP)).coerceIn(0f, 1f)

    /** World-space centreline (x, z): straight to the corner, an arc, a short tail. */
    fun centerline(meters: Float, angleDeg: Float): List<Pair<Float, Float>> {
        val zc = cornerDepth(meters)
        val pts = ArrayList<Pair<Float, Float>>(160)
        val zStart = Z_NEAR * 0.8f
        val n = 40
        for (i in 0..n) pts += 0f to zStart + (zc - zStart) * i / n
        var x = 0f
        var z = zc
        var h = 0f
        val bend = angleDeg * bendAmount(meters)
        val target = (bend * PI / 180).toFloat()
        val sgn = if (target == 0f) 1f else sign(target)
        val ds = 0.06f
        val arcSteps = (abs(target) * TURN_R / ds).roundToInt()
        repeat(arcSteps) {
            h += sgn * ds / TURN_R
            x += sin(h) * ds
            z += cos(h) * ds
            pts += x to z
        }
        val tailSteps = (TURN_LEN / ds).roundToInt()
        repeat(tailSteps) {
            x += sin(h) * ds
            z += cos(h) * ds
            pts += x to z
        }
        return pts
    }

    /** On-screen thickness at depth [z]. */
    fun ribbonWidth(z: Float): Float = min(MAX_W, BASE_W * (Z_NEAR / z).pow(WIDTH_EXP))

    /** Room either side of the centre line, so the arrow never leaves the free box. */
    private const val HALF_ROOM = HudOverlayModel.BOX_RIGHT - HudOverlayModel.BOX_CENTER_X - 1f

    /**
     * The arrow on screen for [meters] to a maneuver bending by [angleDeg]. Sharp turns and
     * U-turns near the corner would sweep past the free box (their tail comes back towards the
     * camera), so when any visible part would leave it the arrow is squeezed sideways just enough
     * to fit; thickness is kept.
     */
    fun shape(meters: Float, angleDeg: Float): Shape {
        val raw = shapeUnfit(meters, angleDeg)
        val cx = HudOverlayModel.BOX_CENTER_X
        var squeeze = 1f
        for (p in raw.ribbon) {
            if (p.y < TOP || p.y > BOTTOM) continue
            val dx = abs(p.x - cx)
            if (dx > 0.01f) squeeze = min(squeeze, ((HALF_ROOM - p.w / 2) / dx).coerceAtLeast(0f))
        }
        for (k in 0 until 3) {
            val dx = abs(raw.head[2 * k] - cx)
            if (dx > 0.01f) squeeze = min(squeeze, HALF_ROOM / dx)
        }
        if (squeeze >= 1f) return raw
        val ribbon = raw.ribbon.map { it.copy(x = cx + (it.x - cx) * squeeze) }
        val head = raw.head.copyOf()
        for (k in 0 until 3) head[2 * k] = cx + (head[2 * k] - cx) * squeeze
        return Shape(ribbon, head)
    }

    private fun shapeUnfit(meters: Float, angleDeg: Float): Shape {
        val ribbon = centerline(meters, angleDeg).filter { it.second > 0.4f }.map { (x, z) ->
            val (sx, sy) = project(x, z)
            Pt(sx, sy, z, ribbonWidth(z))
        }
        val e = ribbon.last()
        val prev = ribbon[maxOf(0, ribbon.size - 4)]
        val len = hypot(e.x - prev.x, e.y - prev.y).takeIf { it > 0f } ?: 1f
        val dx = (e.x - prev.x) / len
        val dy = (e.y - prev.y) / len
        val scale = F_X / e.z
        val hl = HEAD_L * scale
        val hw = min(MAX_W * 2, HEAD_W * scale)
        val head = floatArrayOf(
            e.x + dx * hl, e.y + dy * hl,          // tip
            e.x - dy * hw / 2, e.y + dx * hw / 2,  // base, one side
            e.x + dy * hw / 2, e.y - dx * hw / 2   // base, other side
        )
        return Shape(ribbon, head)
    }
}
