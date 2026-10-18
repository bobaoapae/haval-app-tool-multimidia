package br.com.redesurftank.havalshisuku.map

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin

/** A road in the car's frame (metres, x right, y ahead; x, y pairs) and its drawn width factor. */
class LocalRoad(val pts: FloatArray, val width: Float) {
    /** Driveways, parking aisles: not counted as side streets. */
    val minor: Boolean get() = width < MINOR_WIDTH

    companion object {
        const val MINOR_WIDTH = 0.7f
    }
}

/**
 * The route ahead: [pts] (x, y pairs, metres, car frame), whether it was [matched] to the real
 * roads, and whether our street is the [nextOnSide]: no other street leaves on the turn's side
 * between the car and it (chevrons only then, so they can't point at the wrong street).
 */
class Route(val pts: FloatArray, val matched: Boolean, val nextOnSide: Boolean)

/**
 * The route ahead on the real roads, in the car's frame (metres): the road under the car,
 * followed for Android Auto's distance to the next maneuver, then the street at that junction
 * that best matches the maneuver's turn, followed a little further. AA only sends the next
 * maneuver, so nothing past it is known. Pure (no Android) so it can be tested.
 */
object RoutePath {
    /** The car must be this close to a road (GPS error is ~5-10 m). */
    const val MATCH_M = 25f
    /** ...and roughly along it. */
    const val MATCH_DEG = 50f
    /** The maneuver's junction is looked for this far either side of AA's distance. */
    const val JUNCTION_M = 40f
    /** An exit is accepted when within this of the maneuver's turn angle. */
    const val EXIT_TOLERANCE_DEG = 55f
    /** Past the maneuver, the street is followed this far. */
    const val TAIL_M = 120f
    /** Nothing further than this ahead. */
    const val AHEAD_M = 600f
    /** Keep following the road while its bends stay under this (a sharper one is a junction we don't take). */
    const val STRAIGHT_ON_DEG = 60f
    /** Fallback shape: corner radius and tail. */
    const val FALLBACK_R = 8f
    const val FALLBACK_TAIL_M = 60f

    private class Node(val x: Float, val y: Float) {
        val next = ArrayList<Node>(3)
        /** Parallel to [next]: the way there is a minor road. */
        val minor = ArrayList<Boolean>(3)

        fun link(n: Node, isMinor: Boolean) {
            val i = next.indexOf(n)
            if (i < 0) { next += n; minor += isMinor } else if (!isMinor) minor[i] = false
        }
    }

    private fun key(x: Float, y: Float): Long =
        ((x * 10f).roundToLong() shl 32) xor ((y * 10f).roundToLong() and 0xffffffffL)

    /** Roads into a graph: shared points (junctions) join them. */
    private fun graph(roads: List<LocalRoad>): Collection<Node> {
        val nodes = HashMap<Long, Node>()
        fun node(x: Float, y: Float) = nodes.getOrPut(key(x, y)) { Node(x, y) }
        for (road in roads) {
            var prev: Node? = null
            for (k in 0 until road.pts.size / 2) {
                val n = node(road.pts[2 * k], road.pts[2 * k + 1])
                if (prev != null && prev !== n) {
                    prev.link(n, road.minor)
                    n.link(prev, road.minor)
                }
                prev = n
            }
        }
        return nodes.values
    }

    /** Heading of a step in degrees, 0 = ahead, + right. */
    private fun heading(dx: Float, dy: Float) = (atan2(dx, dy) * 180 / PI).toFloat()

    private fun turn(fromDeg: Float, toDeg: Float): Float {
        var d = toDeg - fromDeg
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }

    /** Straightest way on from [cur] (arrived from [prev]), or null at a dead end or a sharp bend only. */
    private fun straightOn(prev: Node, cur: Node): Node? {
        val inDeg = heading(cur.x - prev.x, cur.y - prev.y)
        return cur.next.filter { it !== prev }
            .minByOrNull { abs(turn(inDeg, heading(it.x - cur.x, it.y - cur.y))) }
            ?.takeIf { abs(turn(inDeg, heading(it.x - cur.x, it.y - cur.y))) <= STRAIGHT_ON_DEG }
    }

    /**
     * The route as x, y pairs, from the car, or null when the car isn't on a mapped road.
     * [meters] is AA's distance to the maneuver (null = unknown: just the road ahead),
     * [turnDeg] its bend (+ right; null or 0 = straight on).
     */
    fun route(roads: List<LocalRoad>, meters: Float?, turnDeg: Float?): Route? {
        val nodes = graph(roads)
        // The road under the car: nearest segment that runs roughly ahead.
        var best: Pair<Node, Node>? = null
        var bestScore = Float.MAX_VALUE
        var start = 0f to 0f
        for (a in nodes) for (b in a.next) {
            if (b.y - a.y <= 0f) continue // each segment once, oriented ahead
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len2 = dx * dx + dy * dy
            if (len2 <= 0f) continue
            val t = ((-a.x) * dx + (-a.y) * dy) / len2
            val px = a.x + dx * t.coerceIn(0f, 1f)
            val py = a.y + dy * t.coerceIn(0f, 1f)
            val dist = hypot(px, py)
            val angle = abs(heading(dx, dy))
            if (dist > MATCH_M || angle > MATCH_DEG) continue
            val score = dist + angle * 0.2f
            if (score < bestScore) {
                bestScore = score
                best = a to b
                start = px to py
            }
        }
        val (from, to) = best ?: return null
        val path = ArrayList<Node>()
        path += from
        path += to
        val pts = ArrayList<Float>()
        pts += start.first; pts += start.second
        pts += to.x; pts += to.y
        val cum = ArrayList<Float>()
        cum += 0f
        cum += hypot(to.x - start.first, to.y - start.second)
        val target = min(meters ?: AHEAD_M, AHEAD_M)
        val wantsTurn = meters != null && meters <= AHEAD_M && turnDeg != null && abs(turnDeg) >= 20f
        // Walk the road ahead, past the maneuver by the junction search radius.
        val walkTo = if (wantsTurn) target + JUNCTION_M else target
        while (cum.last() < walkTo) {
            val next = straightOn(path[path.size - 2], path.last()) ?: break
            cum += cum.last() + hypot(next.x - path.last().x, next.y - path.last().y)
            path += next
            pts += next.x; pts += next.y
        }
        if (!wantsTurn) return Route(trim(pts, cum, target), matched = true, nextOnSide = false)
        // The junction: a node with more than two ways nearest AA's distance.
        var j = -1
        for (i in 1 until path.size) {
            if (path[i].next.size < 3 || abs(cum[i] - target) > JUNCTION_M) continue
            if (j < 0 || abs(cum[i] - target) < abs(cum[j] - target)) j = i
        }
        val turnTo = turnDeg!!
        if (j > 0) {
            val inDeg = heading(path[j].x - path[j - 1].x, path[j].y - path[j - 1].y)
            val exit = path[j].next.filter { it !== path[j - 1] }
                .minByOrNull { abs(turn(inDeg, heading(it.x - path[j].x, it.y - path[j].y)) - turnTo) }
            if (exit != null && abs(turn(inDeg, heading(exit.x - path[j].x, exit.y - path[j].y)) - turnTo) <= EXIT_TOLERANCE_DEG) {
                val out = ArrayList<Float>(pts.subList(0, 2 * (j + 1)))
                val clear = (1 until j).none { i -> sideStreet(path[i - 1], path[i], path[i + 1], turnTo) }
                var prev = path[j]
                var cur: Node = exit
                var tail = 0f
                while (true) {
                    tail += hypot(cur.x - prev.x, cur.y - prev.y)
                    out += cur.x; out += cur.y
                    if (tail >= TAIL_M) break
                    val n = straightOn(prev, cur) ?: break
                    prev = cur
                    cur = n
                }
                return Route(out.toFloatArray(), matched = true, nextOnSide = clear)
            }
        }
        // No junction that fits: the road up to AA's distance (straight on where the mapped road
        // ends early), then a plain turn.
        var head = trim(pts, cum, target)
        var n = head.size / 2
        val dirDeg = if (n >= 2) heading(head[2 * n - 2] - head[2 * n - 4], head[2 * n - 1] - head[2 * n - 3]) else 0f
        if (cum.last() < target) {
            val rest = target - cum.last()
            val rad = dirDeg * PI.toFloat() / 180f
            head += floatArrayOf(head[2 * n - 2] + sin(rad) * rest, head[2 * n - 1] + cos(rad) * rest)
            n += 1
        }
        return Route(head + arc(head[2 * n - 2], head[2 * n - 1], dirDeg, turnTo), matched = true, nextOnSide = false)
    }

    /** A street (not a driveway) leaving [cur] on the same side as [turnTo], other than the way on. */
    private fun sideStreet(prev: Node, cur: Node, on: Node, turnTo: Float): Boolean {
        if (cur.next.size < 3) return false
        val inDeg = heading(cur.x - prev.x, cur.y - prev.y)
        return cur.next.indices.any { k ->
            val n = cur.next[k]
            if (n === prev || n === on || cur.minor[k]) return@any false
            val t = turn(inDeg, heading(n.x - cur.x, n.y - cur.y))
            abs(t) >= 30f && (t > 0f) == (turnTo > 0f)
        }
    }

    /** No road under the car: straight for [meters] (or the whole view), then a plain turn. */
    fun synthetic(meters: Float?, turnDeg: Float?): Route {
        val d = min(meters ?: AHEAD_M, AHEAD_M)
        val straight = floatArrayOf(0f, 0f, 0f, d)
        val t = turnDeg ?: 0f
        return Route(if (meters == null || abs(t) < 20f) straight else straight + arc(0f, d, 0f, t), matched = false, nextOnSide = false)
    }

    /** A turn of [turnDeg] with radius [FALLBACK_R] from (x, y) heading [dirDeg], then a tail. */
    private fun arc(x0: Float, y0: Float, dirDeg: Float, turnDeg: Float): FloatArray {
        val out = ArrayList<Float>()
        var x = x0
        var y = y0
        var h = dirDeg * PI.toFloat() / 180f
        val steps = (abs(turnDeg) / 10f).toInt().coerceAtLeast(1)
        val dh = turnDeg * PI.toFloat() / 180f / steps
        val ds = abs(dh) * FALLBACK_R
        repeat(steps) {
            h += dh
            x += sin(h) * ds
            y += cos(h) * ds
            out += x; out += y
        }
        x += sin(h) * FALLBACK_TAIL_M
        y += cos(h) * FALLBACK_TAIL_M
        out += x; out += y
        return out.toFloatArray()
    }

    /** [pts] cut at [length] along it ([cum] = distance at each point). */
    private fun trim(pts: List<Float>, cum: List<Float>, length: Float): FloatArray {
        val out = ArrayList<Float>()
        for (i in cum.indices) {
            if (cum[i] <= length) {
                out += pts[2 * i]; out += pts[2 * i + 1]
            } else {
                val prev = cum[i - 1]
                val t = (length - prev) / (cum[i] - prev)
                out += pts[2 * i - 2] + (pts[2 * i] - pts[2 * i - 2]) * t
                out += pts[2 * i - 1] + (pts[2 * i + 1] - pts[2 * i - 1]) * t
                break
            }
        }
        return out.toFloatArray()
    }
}
