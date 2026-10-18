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
 * One continuous ribbon on the road, centred on the native speed and the arcs:
 * - the stem, [BASE_W] wide at the bottom with straight sides, narrowing to exactly the arm's
 *   width at the corner through a short rounded fillet (no step between stem and arm);
 * - the arm, laid in perspective from the corner: straight ahead while far, bending into the
 *   maneuver between [BEND_START_M] and [BEND_FULL_M], with a head at its end;
 * - for roundabouts, a grey ring on the road with grey stubs for the exits passed first, and the
 *   route in blue round the ring to our exit. Inside the roundabout the part already driven turns
 *   grey ([Shape.gray]), from an estimated progress.
 */
object HudArArrow {
    /** Centred on the native speed and the power/RPM arcs (one axis, owner's request). */
    const val CX = HudOverlayModel.ARC_CX
    // Viewport: the strip under the speed digits down to the bottom of the visible band.
    const val TOP = 126f
    const val BOTTOM = 200f
    /** Stem width at the bottom edge. */
    const val BASE_W = 200f
    /** Fraction of the stem's height (from the corner) that rounds into the arm's width. */
    const val FILLET = 0.22f
    /** Projection horizon (vanishing row) just above the viewport. */
    const val HORIZON = 118f
    const val Z_NEAR = 4f
    /** Corner depth at 0 m: mid-height, so the arm has room. */
    const val Z_END = 7f
    /** Corner depth while far: a straight arrow whose head is still inside the band. */
    const val Z_CAP = 10f
    /** Depth per metre between Z_END and Z_CAP (~100 m of travel). */
    const val K = 0.03f
    /** Lateral scale for the arm, px per world unit at depth 1. */
    const val F_X = 199f
    /** Arm thickness at the 0 m corner depth; thinner further away. */
    const val ARM_W = 30f
    const val TURN_R = 1.4f
    /** Tail after the corner: long, so a turn sweeps across the HUD instead of a stub. */
    const val TURN_LEN = 5.0f
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
    /** Overall opacity over the road and the native HUD. */
    const val OPACITY = 0.65f
    /** Within this distance the controller interpolates between AA updates for smooth motion. */
    const val SMOOTH_WITHIN_M = 200
    /**
     * How far the arm may reach left of [CX] (x ~10): it passes under the left column, whose
     * text is drawn on top of the translucent arrow.
     */
    const val ROOM_LEFT = 212f
    /** How far it may reach right of [CX] (x ~462). */
    const val ROOM_RIGHT = 240f

    // Roundabout, laid in screen space: true perspective squashes the ring to ~14 px, unreadable.
    /** Ring centre row and radii at full size: a flattened ellipse under the native speed. */
    const val RB_CY = 162f
    const val RB_RX = 58f
    const val RB_RY = 16f
    /** Ring size when it first appears; grows to full as the bend comes in. */
    const val RB_MIN_SCALE = 0.35f
    /** Route thickness at the bottom edge .. at the top edge. */
    const val RB_W_NEAR = 22f
    const val RB_W_FAR = 9f
    /** Ring and passed-exit stubs: thinner than the route. */
    const val RING_W_RATIO = 0.5f
    const val STUB_PX = 12f
    const val RB_TAIL_PX = 70f
    /** Real roundabout radius assumed for the progress estimate (m). A confirmar. */
    const val RB_RADIUS_M = 15f

    /** One point of a ribbon on screen: position, thickness. */
    data class Pt(val x: Float, val y: Float, val w: Float)

    /**
     * The arrow for one frame: [blue] the route (stem, arm / way round the ring, tail), [head]
     * at its end, [gray] the ring, passed-exit stubs and any part already driven.
     */
    class Shape(val blue: List<Pt>, val head: FloatArray, val gray: List<List<Pt>> = emptyList())

    /**
     * Bend angle in degrees (+ right) for a maneuver token (AndroidAutoNavigationTelemetry
     * .turnFromManeuver), or null when there is no clean shape for it (ferries; roundabouts
     * are drawn by [roundabout] when the exit angle is known).
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

    /** Arm thickness at depth [z]. */
    fun armWidth(z: Float): Float = min(ARM_W, ARM_W * Z_END / z)

    /** Row fade: in over [TOP]..[FADE_TOP_TO], out over [FADE_BOTTOM_FROM]..[BOTTOM]. */
    fun rowAlpha(y: Float): Float = when {
        y <= TOP || y >= BOTTOM -> 0f
        y < FADE_TOP_TO -> (y - TOP) / (FADE_TOP_TO - TOP)
        y > FADE_BOTTOM_FROM -> (BOTTOM - y) / (BOTTOM - FADE_BOTTOM_FROM)
        else -> 1f
    }

    /** Gradient position for a row: 0 at the base (lighter blue) .. 1 at the top (deeper blue). */
    fun rowGradient(y: Float): Float = ((BOTTOM - y) / (BOTTOM - TOP)).coerceIn(0f, 1f)

    /**
     * Stem width at [t] = 0 (the corner) .. 1 (the bottom edge): straight sides from [BASE_W]
     * down to [cornerW], rounding into the arm over the last [FILLET] so the outline has no kink.
     */
    fun stemWidth(t: Float, cornerW: Float): Float {
        val slope = (BASE_W - cornerW) / (1f - FILLET / 2f)
        val tt = t.coerceIn(0f, 1f)
        return if (tt >= FILLET) cornerW + slope * (tt - FILLET / 2f) else cornerW + slope * tt * tt / (2f * FILLET)
    }

    /** Stem on screen: from below the bottom edge up to the corner row, widths from [stemWidth]. */
    private fun stem(cornerY: Float, cornerW: Float): List<Pt> {
        val yStart = BOTTOM + 4f
        val n = 36
        return (0..n).map { i ->
            val y = yStart + (cornerY - yStart) * i / n
            Pt(CX, y, stemWidth((y - cornerY) / (BOTTOM - cornerY), cornerW))
        }
    }

    private fun toScreen(world: List<Pair<Float, Float>>, widthRatio: Float = 1f): List<Pt> =
        world.filter { it.second > 0.4f }.map { (x, z) ->
            val (sx, sy) = project(x, z)
            Pt(sx, sy, armWidth(z) * widthRatio)
        }

    private fun head(pts: List<Pt>): FloatArray {
        val e = pts.last()
        val prev = pts[maxOf(0, pts.size - 4)]
        val len = hypot(e.x - prev.x, e.y - prev.y).takeIf { it > 0f } ?: 1f
        val dx = (e.x - prev.x) / len
        val dy = (e.y - prev.y) / len
        val hw = e.w * HEAD_W_RATIO
        val hl = e.w * HEAD_L_RATIO
        return floatArrayOf(
            e.x + dx * hl, e.y + dy * hl,          // tip
            e.x - dy * hw / 2, e.y + dx * hw / 2,  // base, one side
            e.x + dy * hw / 2, e.y - dx * hw / 2   // base, other side
        )
    }

    /** Arm centreline in world space (x, z): from the corner, an arc, then the tail. */
    fun armCenterline(meters: Float, angleDeg: Float): List<Pair<Float, Float>> {
        val zc = cornerDepth(meters)
        val pts = ArrayList<Pair<Float, Float>>(160)
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

    /** The arrow for [meters] to a maneuver bending by [angleDeg]: stem and arm as one ribbon. */
    fun shape(meters: Float, angleDeg: Float): Shape {
        val arm = toScreen(armCenterline(meters, angleDeg))
        val blue = stem(arm.first().y, arm.first().w) + arm.drop(1)
        return fit(Shape(blue, head(blue)))
    }

    /** Roundabout ribbon thickness for a row: thinner further up (further away). */
    fun rbWidth(y: Float): Float = RB_W_FAR + (RB_W_NEAR - RB_W_FAR) * ((y - TOP) / (BOTTOM - TOP)).coerceIn(0f, 1f)

    /**
     * A roundabout [meters] ahead whose exit is [travelDeg] round the ring ([clockwise] for
     * left-hand traffic), with [passedDeg] the exits passed first and [progress] (0..1) how much
     * of the way round has already been driven.
     */
    fun roundabout(
        meters: Float,
        travelDeg: Float,
        clockwise: Boolean,
        passedDeg: List<Float>,
        progress: Float = 0f
    ): Shape {
        val bend = bendAmount(meters)
        // Far away: a plain straight arrow, like any other maneuver.
        if (bend <= 0.02f) return shape(meters, 0f)
        val scale = RB_MIN_SCALE + (1f - RB_MIN_SCALE) * bend
        val rx = RB_RX * scale
        val ry = RB_RY * scale
        val side = if (clockwise) -1f else 1f
        // 0° is the entry (near side, bottom of the ellipse), going round towards [side].
        fun ringXY(deg: Float): Pair<Float, Float> {
            val psi = (deg * PI / 180).toFloat()
            return CX + side * rx * sin(psi) to RB_CY + ry * cos(psi)
        }
        fun outward(deg: Float): Pair<Float, Float> {
            val psi = (deg * PI / 180).toFloat()
            val dx = side * sin(psi)
            val dy = cos(psi) * 0.6f
            val len = hypot(dx, dy).takeIf { it > 0f } ?: 1f
            return dx / len to dy / len
        }
        fun pt(xy: Pair<Float, Float>, ratio: Float = 1f) = Pt(xy.first, xy.second, rbWidth(xy.second) * ratio)
        // Grey ring, and grey stubs for the exits passed before ours.
        val gray = ArrayList<List<Pt>>()
        gray += (0..72).map { pt(ringXY(it * 5f), RING_W_RATIO) }
        for (deg in passedDeg) {
            val (x0, y0) = ringXY(deg)
            val (ox, oy) = outward(deg)
            gray += (0..6).map { k -> val d = STUB_PX * scale * k / 6; pt(x0 + ox * d to y0 + oy * d, RING_W_RATIO) }
        }
        // Route: half way round (straight through) curling on to the real exit as the bend comes in.
        val shown = 180f + (travelDeg - 180f) * bend
        val steps = maxOf(2, (shown / 3f).roundToInt())
        val around = (0..steps).map { pt(ringXY(shown * it / steps)) }
        val exit = around.last()
        val (dx, dy) = outward(shown)
        // Upward tails stop short of the top fade so the head stays visible.
        val roomUp = if (dy < 0f) (exit.y - (FADE_TOP_TO + exit.w * HEAD_L_RATIO)) / -dy else Float.MAX_VALUE
        val tailLen = min(RB_TAIL_PX, roomUp.coerceAtLeast(4f))
        val tail = (1..20).map { k -> val d = tailLen * k / 20; pt(exit.x + dx * d to exit.y + dy * d) }
        val route = around + tail
        val entry = route.first()
        val done = progress.coerceIn(0f, 1f) * steps
        val blue = if (done >= 1f) {
            // Inside the roundabout: the stem and the way round driven so far turn grey.
            val n = done.roundToInt().coerceIn(1, steps)
            gray += stem(entry.y, entry.w) + route.take(n + 1).drop(1)
            route.drop(n)
        } else {
            stem(entry.y, entry.w) + route.drop(1)
        }
        return fit(Shape(blue, head(blue), gray))
    }

    /**
     * Sharp turns, U-turns and roundabout exits near the corner could sweep past the room on
     * their side: squeeze everything sideways just enough; thickness is kept.
     */
    private fun fit(shape: Shape): Shape {
        fun room(x: Float) = if (x < CX) ROOM_LEFT else ROOM_RIGHT
        var squeeze = 1f
        for (p in shape.blue + shape.gray.flatten()) {
            val dxc = abs(p.x - CX)
            if (dxc > 0.01f) squeeze = min(squeeze, ((room(p.x) - p.w / 2) / dxc).coerceAtLeast(0f))
        }
        for (k in 0 until 3) {
            val dxc = abs(shape.head[2 * k] - CX)
            if (dxc > 0.01f) squeeze = min(squeeze, room(shape.head[2 * k]) / dxc)
        }
        if (squeeze >= 1f) return shape
        fun sq(list: List<Pt>) = list.map { it.copy(x = CX + (it.x - CX) * squeeze) }
        val head = shape.head.copyOf().also { for (k in 0 until 3) it[2 * k] = CX + (it[2 * k] - CX) * squeeze }
        return Shape(sq(shape.blue), head, shape.gray.map { sq(it) })
    }

    /**
     * Progress round a roundabout once the distance hit 0: [metersDriven] since then over the
     * way round to the exit on a ring of [RB_RADIUS_M]. An estimate; AA doesn't send it.
     */
    fun roundaboutProgress(metersDriven: Float, travelDeg: Float): Float {
        val way = (travelDeg * PI / 180).toFloat() * RB_RADIUS_M
        return if (way <= 0f) 0f else (metersDriven / way).coerceIn(0f, 1f)
    }
}
