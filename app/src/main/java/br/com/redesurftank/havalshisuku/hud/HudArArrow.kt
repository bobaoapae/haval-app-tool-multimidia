package br.com.redesurftank.havalshisuku.hud

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * Geometry of the "Navegação AR" arrow, in HUD pixels and with no Android types (testable).
 * Keep the constants in step with the prototype in scripts/hud-simulator (draw3dArrow).
 *
 * Everything is a ribbon of one fixed width lying on the road (world x right, z ahead), and
 * the whole ribbon goes through one projection: the base, the arm and the head share one
 * vanishing point, so the arrow reads as flat on the road instead of rising at the top.
 * - Turns: straight ahead to the corner, then an arc into the maneuver and a tail with a head.
 *   Straight while far; the bend grows in between [BEND_START_M] and [BEND_FULL_M].
 * - Roundabouts: a grey ring on the road with grey stubs for the exits passed first, the route
 *   in blue round the ring to our exit with a head just past it. Inside the roundabout the
 *   camera drives round with the car ([roundabout] `progress`), so the scene turns as it does
 *   through the windshield.
 */
object HudArArrow {
    /** Centred on the native speed and the power/RPM arcs (one axis, owner's request). */
    const val CX = HudOverlayModel.ARC_CX
    // Viewport: the strip under the speed digits down to the bottom of the visible band.
    const val TOP = 126f
    const val BOTTOM = 200f
    /** Projection horizon (vanishing row) just above the viewport. */
    const val HORIZON = 118f
    /** Depth of the bottom edge. */
    const val Z_NEAR = 4f
    /** Lateral scale, px per world unit at depth 1. */
    const val F_X = 199f
    /** Ribbon width at the bottom edge (px): sets its width on the road. */
    const val BASE_W = 200f
    /** Half the ribbon's width on the road (world units). */
    const val HW = BASE_W / 2f * Z_NEAR / F_X
    /** Nothing nearer than this is projected (it is below the band anyway). */
    const val Z_CLIP = 2.5f
    /** Corner depth at 0 m. */
    const val Z_END = 5.5f
    /** Corner depth while far. */
    const val Z_CAP = 8f
    /** Depth per metre between Z_END and Z_CAP (~100 m of travel). */
    const val K = 0.025f
    /** Turn radius on the road; above [HW] so the inner edge never folds over. */
    const val TURN_R = 2.3f
    /** Tail after the corner. */
    const val TURN_LEN = 2.5f
    /** A tail coming back towards the car (sharp turns, U-turns) stops here, head still in view. */
    const val TAIL_MIN_Z = 5f
    /** Head: half its base width and its length, on the road. */
    const val HEAD_HW = 3.0f
    const val HEAD_L = 3.5f
    /** Straight -> full turn between these distances (owner: only within ~200 m). */
    const val BEND_START_M = 200f
    const val BEND_FULL_M = 40f
    /** Short fade at the top edge, so the clip there doesn't show as a hard line. */
    const val FADE_TOP_TO = 132f
    /** Fades out over the bottom of the visible band, so the optics' edge doesn't crop it hard. */
    const val FADE_BOTTOM_FROM = 180f
    /** Overall opacity over the road and the native HUD, near the maneuver. */
    const val OPACITY = 0.65f
    /** ...and while it is still far (straight arrow): more see-through. */
    const val OPACITY_FAR = 0.35f
    /** Within this distance the controller interpolates between AA updates for smooth motion. */
    const val SMOOTH_WITHIN_M = 200
    /**
     * How far the arm may reach left of [CX] (x ~10): it passes under the left column, whose
     * text is drawn on top of the translucent arrow.
     */
    const val ROOM_LEFT = 212f
    /** How far it may reach right of [CX] (x ~462). */
    const val ROOM_RIGHT = 240f

    // Roundabout, on the road (world units).
    /** Ring radius (centre of its lane). */
    const val RING_R = 3.5f
    /** Ring and passed-exit stubs: narrower than the route, which is drawn over them. */
    const val RING_HW = 1.5f
    const val STUB_LEN = 2.5f
    const val STUB_HW = 1.1f
    /** Gap from the corner (end of the approach) to the near side of the ring. */
    const val ENTRY_GAP = 1f
    /** The route joins the ring this far round, and leaves it this far before the exit. */
    const val ENTRY_DEG = 30f
    /** How far past the ring the route runs before its head (no road beyond the exit). */
    const val EXIT_OUT = 2.5f
    /** The camera looks at the route this far ahead, so it turns smoothly round the ring. */
    const val LOOK_AHEAD = 4f
    /** Real roundabout radius assumed for the progress estimate (m). A confirmar. */
    const val RB_RADIUS_M = 15f

    // Destination flag, on the road beside the arrow's end.
    /** From the ribbon's edge to the pole (world units). */
    const val FLAG_GAP = 0.9f
    /** Pole height (world units, drawn at the lateral scale of its depth). */
    const val FLAG_H = 1.8f

    /**
     * Where the arrow sits on the HUD. CENTER: under the native speed, as computed. RIGHT:
     * between the native lane-assist lines (x ~388..470, mock measured from the owner's photo,
     * a confirmar): the same picture scaled about (CX, BOTTOM) by [sx] sideways and [sy]
     * upwards, which is exactly the same road seen with a higher horizon, so it narrows like
     * those lines. [top] is the band's top in the unscaled picture (RIGHT reaches y ~56 on
     * screen); [roomLeft] / [roomRight] limit the sideways reach in it.
     */
    enum class Placement(
        val key: String,
        val cx: Float,
        val sx: Float,
        val sy: Float,
        val top: Float,
        val roomLeft: Float,
        val roomRight: Float
    ) {
        CENTER("center", CX, 1f, 1f, TOP, ROOM_LEFT, ROOM_RIGHT),
        RIGHT("right", 429f, 0.4f, 2.4f, 140f, 320f, 115f);

        /** Screen point for a point of the unscaled picture. */
        fun map(x: Float, y: Float): Pair<Float, Float> = cx + (x - CX) * sx to BOTTOM - (BOTTOM - y) * sy

        companion object {
            fun fromKey(key: String?): Placement? = entries.firstOrNull { it.key == key }
        }
    }

    /** Destination flag: pole foot ([x], [y]) on the road, [height] of the pole, unscaled picture. */
    data class Flag(val x: Float, val y: Float, val height: Float)

    /** Side of the destination for a maneuver token: -1 left, +1 right, 0 unknown, null not one. */
    fun destinationSide(turn: String?): Int? = when (turn.orEmpty().uppercase()) {
        "DESTINATION_LEFT" -> -1
        "DESTINATION_RIGHT" -> 1
        "DESTINATION" -> 0
        else -> null
    }

    /**
     * One cross-section of a ribbon on screen: its left and right edge points. [x], [y] are the
     * middle, [w] the on-screen width.
     */
    data class Pt(val lx: Float, val ly: Float, val rx: Float, val ry: Float) {
        val x: Float get() = (lx + rx) / 2f
        val y: Float get() = (ly + ry) / 2f
        val w: Float get() = hypot(rx - lx, ry - ly)
    }

    /**
     * The arrow for one frame: [blue] the route, [head] at its end (tip, base corner, base corner;
     * empty when out of view), [gray] the ring and passed-exit stubs, drawn at [grayAlpha].
     */
    class Shape(
        val blue: List<Pt>,
        val head: FloatArray,
        val gray: List<List<Pt>> = emptyList(),
        val grayAlpha: Float = 1f,
        val flag: Flag? = null
    )

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
        // No side given: left, as in right-hand traffic.
        "U_TURN_LEFT", "U_TURN" -> -180f
        else -> null
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)

    /** 0 = straight arrow, 1 = the full turn. */
    fun bendAmount(meters: Float): Float =
        smooth(((BEND_START_M - meters) / (BEND_START_M - BEND_FULL_M)).coerceIn(0f, 1f))

    /** Far -> [OPACITY_FAR], rising with the bend to [OPACITY] near the maneuver. */
    fun opacityFor(meters: Float): Float = OPACITY_FAR + (OPACITY - OPACITY_FAR) * bendAmount(meters)

    /** True while the maneuver is too far to show its turn (no or unknown distance included). */
    fun isFar(meters: Float?): Boolean = meters == null || meters >= BEND_START_M

    fun cornerDepth(meters: Float): Float = min(Z_CAP, Z_END + K * meters.coerceAtLeast(0f))

    fun project(x: Float, z: Float): Pair<Float, Float> =
        CX + F_X * x / z to HORIZON + (BOTTOM - HORIZON) * Z_NEAR / z

    /** Row fade: in over [top]..[top] + 6, out over [FADE_BOTTOM_FROM]..[BOTTOM]. */
    fun rowAlpha(y: Float, top: Float = TOP): Float {
        val fadeTo = top + (FADE_TOP_TO - TOP)
        return when {
            y <= top || y >= BOTTOM -> 0f
            y < fadeTo -> (y - top) / (fadeTo - top)
            y > FADE_BOTTOM_FROM -> (BOTTOM - y) / (BOTTOM - FADE_BOTTOM_FROM)
            else -> 1f
        }
    }

    /** Gradient position for a row: 0 at the base (lighter blue) .. 1 at the top (deeper blue). */
    fun rowGradient(y: Float): Float = ((BOTTOM - y) / (BOTTOM - TOP)).coerceIn(0f, 1f)

    /** A point on the road. */
    data class W(val x: Float, val z: Float)

    /** Where the camera stands on the road and its heading (radians, + right). */
    private class Camera(val x: Float, val z: Float, val h: Float) {
        private val c = cos(h)
        private val s = sin(h)

        /** Screen point for a road point, or null when it is behind [Z_CLIP]. */
        fun screen(px: Float, pz: Float): Pair<Float, Float>? {
            val dx = px - x
            val dz = pz - z
            val lx = dx * c - dz * s
            val lz = dx * s + dz * c
            return if (lz < Z_CLIP) null else project(lx, lz)
        }
    }

    private val AHEAD = Camera(0f, 0f, 0f)

    private fun unit(a: W, b: W): W {
        val dx = b.x - a.x
        val dz = b.z - a.z
        val len = hypot(dx, dz).takeIf { it > 0f } ?: return W(0f, 1f)
        return W(dx / len, dz / len)
    }

    /**
     * A road centreline [half] wide on each side, on screen. Splits into runs where it goes out
     * of view (behind the camera).
     */
    private fun ribbon(line: List<W>, half: Float, cam: Camera): List<List<Pt>> {
        val runs = ArrayList<List<Pt>>()
        var run = ArrayList<Pt>()
        for (i in line.indices) {
            val d = unit(line[maxOf(0, i - 1)], line[minOf(line.size - 1, i + 1)])
            val p = line[i]
            // Right of the heading (dx, dz) is (dz, -dx).
            val l = cam.screen(p.x - d.z * half, p.z + d.x * half)
            val r = cam.screen(p.x + d.z * half, p.z - d.x * half)
            if (l == null || r == null) {
                if (run.size > 1) runs += run
                run = ArrayList()
                continue
            }
            run += Pt(l.first, l.second, r.first, r.second)
        }
        if (run.size > 1) runs += run
        return runs
    }

    /** Head on the road at the end of [line], pointing along its last stretch. */
    private fun head(line: List<W>, cam: Camera): FloatArray {
        val end = line.last()
        val d = unit(line[maxOf(0, line.size - 4)], end)
        // Pointing back towards the car: shortened so the tip stays in view.
        val ez = end.x * sin(cam.h) + end.z * cos(cam.h) - (cam.x * sin(cam.h) + cam.z * cos(cam.h))
        val dz = d.x * sin(cam.h) + d.z * cos(cam.h)
        val len = if (dz < 0f) min(HEAD_L, max(1f, (ez - Z_CLIP - 0.3f) / -dz)) else HEAD_L
        val tip = cam.screen(end.x + d.x * len, end.z + d.z * len)
        val l = cam.screen(end.x - d.z * HEAD_HW, end.z + d.x * HEAD_HW)
        val r = cam.screen(end.x + d.z * HEAD_HW, end.z - d.x * HEAD_HW)
        if (tip == null || l == null || r == null) return FloatArray(0)
        return floatArrayOf(tip.first, tip.second, l.first, l.second, r.first, r.second)
    }

    /** Turn centreline on the road: straight to the corner, an arc, then the tail. */
    fun armCenterline(meters: Float, angleDeg: Float): List<W> {
        val zc = cornerDepth(meters)
        val ds = 0.1f
        val pts = ArrayList<W>(200)
        var x = 0f
        var z = Z_CLIP - ds
        while (z < zc) {
            pts += W(x, z)
            z += ds
        }
        z = zc
        pts += W(x, z)
        var h = 0f
        val target = (angleDeg * bendAmount(meters) * PI / 180).toFloat()
        val sgn = if (target == 0f) 1f else sign(target)
        repeat((abs(target) * TURN_R / ds).roundToInt()) {
            h += sgn * ds / TURN_R
            x += sin(h) * ds
            z += cos(h) * ds
            pts += W(x, z)
        }
        repeat((TURN_LEN / ds).roundToInt()) {
            val nz = z + cos(h) * ds
            // Coming back towards the car: stop while the head is still in view.
            if (cos(h) < 0f && nz < TAIL_MIN_Z) return pts
            x += sin(h) * ds
            z = nz
            pts += W(x, z)
        }
        return pts
    }

    /** The arrow for [meters] to a maneuver bending by [angleDeg]. */
    fun shape(meters: Float, angleDeg: Float, placement: Placement = Placement.CENTER): Shape {
        val line = armCenterline(meters, angleDeg)
        val blue = ribbon(line, HW, AHEAD).lastOrNull().orEmpty()
        return fit(Shape(blue, head(line, AHEAD)), placement)
    }

    /**
     * Arriving: a straight arrow up to the destination's depth and a flag on the road beside it,
     * on [side] (-1 left, +1 right; 0 unknown: just past the head, on the road).
     */
    fun destination(meters: Float, side: Int, placement: Placement = Placement.CENTER): Shape {
        val zc = cornerDepth(meters)
        val line = ArrayList<W>()
        var z = Z_CLIP - 0.1f
        while (z < zc) {
            line += W(0f, z)
            z += 0.1f
        }
        line += W(0f, zc)
        val blue = ribbon(line, HW, AHEAD).lastOrNull().orEmpty()
        val fx = if (side == 0) 0f else side * (HW + FLAG_GAP)
        val fz = if (side == 0) zc + HEAD_L + 0.6f else zc
        val (sx, sy) = project(fx, fz)
        return fit(Shape(blue, head(line, AHEAD), flag = Flag(sx, sy, FLAG_H * F_X / fz)), placement)
    }

    /** Cubic from [p0] heading [d0] to [p3] heading [d3], without [p0]. */
    private fun curve(p0: W, d0: W, p3: W, d3: W, n: Int = 24): List<W> {
        val k = hypot(p3.x - p0.x, p3.z - p0.z) * 0.45f
        val p1 = W(p0.x + d0.x * k, p0.z + d0.z * k)
        val p2 = W(p3.x - d3.x * k, p3.z - d3.z * k)
        return (1..n).map { i ->
            val t = i.toFloat() / n
            val u = 1 - t
            W(
                u * u * u * p0.x + 3 * u * u * t * p1.x + 3 * u * t * t * p2.x + t * t * t * p3.x,
                u * u * u * p0.z + 3 * u * u * t * p1.z + 3 * u * t * t * p2.z + t * t * t * p3.z
            )
        }
    }

    /** Point at arc length [s] along [line] (cumulative lengths in [acc]). */
    private fun at(line: List<W>, acc: FloatArray, s: Float): W {
        if (s <= 0f) return line.first()
        for (i in 1 until line.size) {
            if (acc[i] >= s) {
                val seg = (acc[i] - acc[i - 1]).takeIf { it > 0f } ?: return line[i]
                val t = (s - acc[i - 1]) / seg
                return W(line[i - 1].x + (line[i].x - line[i - 1].x) * t, line[i - 1].z + (line[i].z - line[i - 1].z) * t)
            }
        }
        return line.last()
    }

    /**
     * A roundabout [meters] ahead whose exit is [travelDeg] round the ring ([clockwise] for
     * left-hand traffic), with [passedDeg] the exits passed first and [progress] (0..1) how far
     * the car has got from the approach to the exit.
     */
    fun roundabout(
        meters: Float,
        travelDeg: Float,
        clockwise: Boolean,
        passedDeg: List<Float>,
        progress: Float = 0f,
        placement: Placement = Placement.CENTER
    ): Shape {
        val bend = bendAmount(meters)
        val side = if (clockwise) -1f else 1f
        val zc = cornerDepth(meters)
        val centre = W(0f, zc + ENTRY_GAP + RING_R)
        fun rad(deg: Float) = (deg * PI / 180).toFloat()
        // 0° is the near side of the ring; angles grow in the direction of travel.
        fun ring(deg: Float, r: Float = RING_R) =
            W(centre.x + side * r * sin(rad(deg)), centre.z - r * cos(rad(deg)))
        fun along(deg: Float) = W(side * cos(rad(deg)), sin(rad(deg)))
        fun outward(deg: Float) = W(side * sin(rad(deg)), -cos(rad(deg)))

        // Route on the road: approach, merge into the ring, round it, out to just past our exit.
        val aOut = max(ENTRY_DEG + 10f, travelDeg - ENTRY_DEG)
        val route = ArrayList<W>(300)
        var z = 0f
        while (z < zc) {
            route += W(0f, z)
            z += 0.1f
        }
        val approachEnd = W(0f, zc)
        route += approachEnd
        route += curve(approachEnd, W(0f, 1f), ring(ENTRY_DEG), along(ENTRY_DEG))
        var deg = ENTRY_DEG + 2f
        while (deg < aOut) {
            route += ring(deg)
            deg += 2f
        }
        route += ring(aOut)
        route += curve(ring(aOut), along(aOut), ring(travelDeg, RING_R + EXIT_OUT), outward(travelDeg))
        val acc = FloatArray(route.size)
        for (i in 1 until route.size) acc[i] = acc[i - 1] + hypot(route[i].x - route[i - 1].x, route[i].z - route[i - 1].z)

        // While far it is a straight arrow of the same length, curling into the route.
        val shown = route.mapIndexed { i, p -> W(p.x * bend, acc[i] + (p.z - acc[i]) * bend) }

        // Inside the roundabout the camera rides the route, looking a little ahead.
        val total = acc.last()
        val sCam = progress.coerceIn(0f, 1f) * (total - EXIT_OUT)
        val cam = if (sCam <= 0f) AHEAD else {
            val here = at(shown, acc, sCam)
            val ahead = at(shown, acc, min(total, sCam + LOOK_AHEAD))
            val d = unit(here, ahead)
            Camera(here.x, here.z, kotlin.math.atan2(d.x, d.z))
        }
        val remaining = if (sCam <= 0f) shown else shown.filterIndexed { i, _ -> acc[i] >= sCam }
        val blue = ribbon(remaining, HW, cam).lastOrNull().orEmpty()

        val gray = ArrayList<List<Pt>>()
        if (bend > 0.02f) {
            gray += ribbon((0..90).map { ring(it * 4f) }, RING_HW, cam)
            for (p in passedDeg) {
                gray += ribbon((0..8).map { k -> ring(p, RING_R + STUB_LEN * k / 8) }, STUB_HW, cam)
            }
        }
        return fit(Shape(blue, head(remaining, cam), gray, grayAlpha = bend), placement)
    }

    /**
     * Sharp turns, U-turns and roundabout exits near the corner could sweep past the room on
     * their side: squeeze everything sideways just enough. Measured on the route and its head.
     */
    private fun fit(shape: Shape, placement: Placement): Shape {
        fun room(x: Float) = if (x < CX) placement.roomLeft else placement.roomRight
        var squeeze = 1f
        fun take(x: Float) {
            val dxc = abs(x - CX)
            if (dxc > 0.01f) squeeze = min(squeeze, room(x) / dxc)
        }
        for (p in shape.blue) if (p.y in placement.top..BOTTOM) { take(p.lx); take(p.rx) }
        for (k in 0 until shape.head.size / 2) take(shape.head[2 * k])
        shape.flag?.let { take(it.x) }
        if (squeeze >= 1f) return shape
        fun sx(x: Float) = CX + (x - CX) * squeeze
        fun sq(list: List<Pt>) = list.map { it.copy(lx = sx(it.lx), rx = sx(it.rx)) }
        val head = shape.head.copyOf().also { for (k in 0 until it.size / 2) it[2 * k] = sx(it[2 * k]) }
        return Shape(sq(shape.blue), head, shape.gray.map { sq(it) }, shape.grayAlpha, shape.flag?.let { it.copy(x = sx(it.x)) })
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
