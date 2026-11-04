package br.com.redesurftank.havalshisuku.map

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * A road in the car's frame (metres, x right, y ahead; x, y pairs) and its drawn width factor.
 * [oneway]: travel only in the order of [pts] (OSM's way direction). [ring]: a roundabout's ring.
 */
class LocalRoad(val pts: FloatArray, val width: Float, val name: String = "", val oneway: Boolean = false, val ring: Boolean = false) {
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
    /** Past the maneuver, the street is followed this far, or to its next real junction... */
    const val TAIL_M = 120f
    /** ...but at least this far, so a junction right at the maneuver doesn't cut it to nothing. */
    const val TAIL_MIN_M = 5f
    /** Nothing further than this ahead. */
    const val AHEAD_M = 600f
    /** Keep following the road while its bends stay under this (a sharper one is a junction we don't take). */
    const val STRAIGHT_ON_DEG = 60f
    /** A single continuation kinkier than that (a roundabout ring, a broken way) is still the road. */
    const val CONTINUE_DEG = 120f
    /** Way ends this close are the same junction. OSM often splits a road into ways that don't share a node. */
    const val SNAP_M = 14f
    /** Fallback shape: corner radius and tail. */
    const val FALLBACK_R = 8f
    const val FALLBACK_TAIL_M = 60f

    private class Node(val x: Float, val y: Float) {
        val next = ArrayList<Node>(3)
        /** Parallel to [next]: the way there is a minor road. */
        val minor = ArrayList<Boolean>(3)
        /** Parallel to [next]: the way there runs against a one-way street (no way out there). */
        val against = ArrayList<Boolean>(3)
        /** Parallel to [next]: the way there is a roundabout's ring. */
        val ring = ArrayList<Boolean>(3)
        /** Parallel to [next]: the street's name. */
        val names = ArrayList<String>(3)

        fun link(n: Node, isMinor: Boolean, name: String, isAgainst: Boolean = false, isRing: Boolean = false) {
            val i = next.indexOf(n)
            if (i < 0) {
                next += n; minor += isMinor; names += name; against += isAgainst; ring += isRing
            } else {
                if (!isMinor) minor[i] = false
                if (!isAgainst) against[i] = false
                if (isRing) ring[i] = true
            }
        }
    }

    private fun key(x: Float, y: Float): Long =
        ((x * 10f).roundToLong() shl 32) xor ((y * 10f).roundToLong() and 0xffffffffL)

    /** Roads into a graph: shared points (junctions) join them. Way ends within [SNAP_M] join too. */
    private fun graph(roads: List<LocalRoad>): Collection<Node> {
        val nodes = HashMap<Long, Node>()
        val ends = ArrayList<Node>()
        fun vertex(x: Float, y: Float, end: Boolean): Node {
            nodes[key(x, y)]?.let { return it }
            if (end) {
                for (n in ends) if (hypot(n.x - x, n.y - y) <= SNAP_M) return n
            }
            val n = Node(x, y)
            nodes[key(x, y)] = n
            if (end) ends += n
            return n
        }
        for (road in roads) {
            val nPts = road.pts.size / 2
            if (nPts < 2) continue
            var prev: Node? = null
            for (k in 0 until nPts) {
                val n = vertex(road.pts[2 * k], road.pts[2 * k + 1], end = k == 0 || k == nPts - 1)
                if (prev != null && prev !== n) {
                    prev.link(n, road.minor, road.name, isRing = road.ring)
                    n.link(prev, road.minor, road.name, isAgainst = road.oneway, isRing = road.ring)
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

    /** Straightest way on from [cur] (arrived from [prev]), or null at a dead end or a real fork. */
    private fun straightOn(prev: Node, cur: Node): Node? {
        val inDeg = heading(cur.x - prev.x, cur.y - prev.y)
        fun ang(n: Node) = abs(turn(inDeg, heading(n.x - cur.x, n.y - cur.y)))
        val options = cur.next.filter { it !== prev }
        if (options.isEmpty()) return null
        val best = options.minBy { ang(it) }
        if (ang(best) <= STRAIGHT_ON_DEG) return best
        // One kinked continuation (a ring, a way that didn't share its node): still the road.
        val open = options.filter { ang(it) <= CONTINUE_DEG }
        if (open.size == 1) return open[0]
        if (open.size >= 2) {
            val sorted = open.sortedBy { ang(it) }
            if (ang(sorted[1]) - ang(sorted[0]) >= 25f) return sorted[0]
        }
        return null
    }

    /**
     * The route as x, y pairs, from the car, or null when the car isn't on a mapped road.
     * [meters] is AA's distance to the maneuver (null = unknown: just the road ahead),
     * [turnDeg] its bend (+ right; null or 0 = straight on).
     */
    fun route(roads: List<LocalRoad>, meters: Float?, turnDeg: Float?): Route? {
        val nodes = graph(roads)
        val (from, to, start) = matchStart(nodes) ?: return null
        return routeFrom(from, to, start, meters, turnDeg)
    }

    /** The road under the car: the nearest segment running roughly ahead, and the car's foot on it. */
    private fun matchStart(nodes: Collection<Node>): Triple<Node, Node, Pair<Float, Float>>? {
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
        return Triple(from, to, start)
    }

    private fun routeFrom(from: Node, to: Node, start: Pair<Float, Float>, meters: Float?, turnDeg: Float?): Route {
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
        if (!wantsTurn) {
            val out = ArrayList<Float>()
            out += trim(pts, cum, target).toList()
            // The mapped way ended before AA's distance: keep the line going on the same heading
            // for a short bridge, so a joint doesn't cut the route off.
            if (cum.last() + 1f < target && out.size >= 4) {
                val n = out.size / 2
                val dir = heading(out[2 * n - 2] - out[2 * n - 4], out[2 * n - 1] - out[2 * n - 3])
                val rest = (target - cum.last()).coerceAtMost(60f)
                val rad = dir * PI.toFloat() / 180f
                out += out[2 * n - 2] + sin(rad) * rest
                out += out[2 * n - 1] + cos(rad) * rest
            }
            return Route(out.toFloatArray(), matched = true, nextOnSide = false)
        }
        // The junction: a node with more than two ways nearest AA's distance.
        var j = -1
        for (i in 1 until path.size) {
            if (path[i].next.size < 3 || abs(cum[i] - target) > JUNCTION_M) continue
            if (j < 0 || abs(cum[i] - target) < abs(cum[j] - target)) j = i
        }
        val turnTo = turnDeg!!
        if (j > 0) {
            val inDeg = heading(path[j].x - path[j - 1].x, path[j].y - path[j - 1].y)
            // Not into a one-way street against its traffic (a road that only comes in).
            val exit = path[j].next.indices.filter { path[j].next[it] !== path[j - 1] && !path[j].against[it] }
                .map { path[j].next[it] }
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
                    // AA sends one maneuver only (steps=1 on this car): past the next real
                    // junction we can't know the way, so the tail stops there instead of drawing
                    // straight on (owner, 2026-10-25: a right then a left 11 m later read as
                    // straight on).
                    if (tail >= TAIL_MIN_M && isJunction(prev, cur)) break
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

    /** How far round a ring is followed before giving up (a big roundabout is ~250 m round). */
    const val RING_MAX_M = 400f
    /** A way off the ring counts as an exit when it bends this much further out than the ring itself. */
    const val RING_EXIT_SPREAD_DEG = 8f
    /** AA's exit (by number) is taken when its net turn is within this of AA's exit angle... */
    const val RING_COUNT_TOLERANCE_DEG = 60f
    /** ...else the exit closest to that angle, within this. */
    const val RING_EXIT_TOLERANCE_DEG = 45f
    /** An exit's direction is read this far along it. */
    const val RING_EXIT_LOOK_M = 25f
    /** On the ring: the exit nearest AA's distance, within this of it. */
    const val RING_GUIDE_M = 60f

    /** An exit off the ring: the ring drawn up to it ([upTo] floats), the node and the way out, the net turn, the way round. */
    private class Exit(val upTo: Int, val node: Node, val k: Int, val net: Float, val ringM: Float)

    /**
     * Round the ring from [prev] -> [first] (the most inward way at every node), appending the
     * ring to [out], listing its exits: per node, the ways that bend out further than the ring
     * (one per node, the one closest to [wantNet] against [approach]), not driveways, not roads
     * that only come in. [dir] +1 counter-clockwise, -1 clockwise.
     */
    private fun walkRing(prev0: Node, first: Node, dir: Float, approach: Float, wantNet: Float, out: ArrayList<Float>, stopAt: Node?): List<Exit> {
        fun turnTo(a: Node, b: Node, from: Float) = turn(from, heading(b.x - a.x, b.y - a.y))
        val exits = ArrayList<Exit>()
        var prev = prev0
        var cur = first
        var ringM = 0f
        while (ringM < RING_MAX_M) {
            ringM += hypot(cur.x - prev.x, cur.y - prev.y)
            out += cur.x; out += cur.y
            if (cur === stopAt) break // all the way round
            val inDeg = heading(cur.x - prev.x, cur.y - prev.y)
            val options = cur.next.indices.filter { cur.next[it] !== prev }
            if (options.isEmpty()) break
            // On the ring: its own ways when the map marks them (an exit can bend in further than
            // the ring where it leaves), else the most inward way (left round a counter-clockwise ring).
            val ringWays = options.filter { cur.ring[it] && !cur.against[it] }
            val onRing = (ringWays.ifEmpty { options }).minBy { turnTo(cur, cur.next[it], inDeg) * dir }
            val ringTurn = turnTo(cur, cur.next[onRing], inDeg) * dir
            options.filter { k ->
                k != onRing && !cur.minor[k] && !cur.against[k] && !cur.ring[k] &&
                    turnTo(cur, cur.next[k], inDeg) * dir - ringTurn >= RING_EXIT_SPREAD_DEG
            }.minByOrNull { k -> abs(turn(wantNet, turn(approach, outHeading(cur, k)))) }?.let { k ->
                exits += Exit(out.size, cur, k, turn(approach, outHeading(cur, k)), ringM)
            }
            prev = cur
            cur = cur.next[onRing]
        }
        return exits
    }

    /**
     * Where a way off the ring heads: [RING_EXIT_LOOK_M] along it, not its first step (an exit
     * that leaves at a tangent and then turns read as a right turn when it was nearly a U-turn).
     */
    private fun outHeading(cur: Node, k: Int): Float {
        var p = cur
        var c = cur.next[k]
        var d = hypot(c.x - p.x, c.y - p.y)
        while (d < RING_EXIT_LOOK_M) {
            val n = straightOn(p, c) ?: break
            d += hypot(n.x - c.x, n.y - c.y)
            p = c
            c = n
        }
        return heading(c.x - cur.x, c.y - cur.y)
    }

    /** [route] (the ring up to [e]) on along exit [e]'s road, as in [routeFrom]'s tail. */
    private fun exitRoute(out: List<Float>, e: Exit): Route {
        val route = ArrayList<Float>(out.subList(0, e.upTo))
        var p = e.node
        var c: Node = e.node.next[e.k]
        var tail = 0f
        while (true) {
            tail += hypot(c.x - p.x, c.y - p.y)
            route += c.x; route += c.y
            if (tail >= TAIL_M) break
            if (tail >= TAIL_MIN_M && isJunction(p, c)) break
            val n = straightOn(p, c) ?: break
            p = c
            c = n
        }
        return Route(route.toFloatArray(), matched = true, nextOnSide = false)
    }

    /**
     * Approaching a roundabout (owner, 2026-10-26: the map only showed the arrow): the road to
     * the entry at AA's distance, round the ring in the direction of travel ([clockwise] false =
     * counter-clockwise, right-hand traffic), out of AA's exit, then along that road. Null when
     * the map has no ring there (the caller falls back to the plain route).
     *
     * Which exit (2026-10-26 drive, Charitas: AA said 6th exit at 315 degrees; the map has 3,
     * two of them leaving the ring at a tangent, and the one taken is nearly back the way it
     * came): AA's [exitNumber] when its net turn agrees with [exitAngleDeg] (degrees round the
     * ring, 180 = straight across, so the net turn is 180 - angle, mirrored when clockwise); a
     * number past the map's exits means the last one; else the exit closest to the angle.
     */
    fun roundabout(roads: List<LocalRoad>, meters: Float?, exitAngleDeg: Float, clockwise: Boolean, exitNumber: Int? = null): Route? {
        if (meters == null || meters > AHEAD_M) return null
        val nodes = graph(roads)
        val (from, to, start) = matchStart(nodes) ?: return null
        val path = arrayListOf(from, to)
        val pts = arrayListOf(start.first, start.second, to.x, to.y)
        val cum = arrayListOf(0f, hypot(to.x - start.first, to.y - start.second))
        while (cum.last() < meters + JUNCTION_M) {
            val next = straightOn(path[path.size - 2], path.last()) ?: break
            cum += cum.last() + hypot(next.x - path.last().x, next.y - path.last().y)
            path += next
            pts += next.x; pts += next.y
        }
        // The entry: the junction nearest AA's distance.
        var e = -1
        for (i in 1 until path.size) {
            if (path[i].next.size < 3 || abs(cum[i] - meters) > JUNCTION_M) continue
            if (e < 0 || abs(cum[i] - meters) < abs(cum[e] - meters)) e = i
        }
        if (e < 1) return null
        val entry = path[e]
        val approach = heading(entry.x - path[e - 1].x, entry.y - path[e - 1].y)
        // + = the way round: right then left round a counter-clockwise ring, mirrored clockwise.
        val dir = if (clockwise) -1f else 1f
        fun turnTo(a: Node, b: Node, from: Float) = turn(from, heading(b.x - a.x, b.y - a.y))
        val ways = entry.next.indices.filter { entry.next[it] !== path[e - 1] && turnTo(entry, entry.next[it], approach) * dir in 10f..160f }
        // Onto the ring's own way when the map marks it.
        val ring0 = (ways.filter { entry.ring[it] }.ifEmpty { ways }).map { entry.next[it] }
            .minByOrNull { abs(turnTo(entry, it, approach) * dir - 70f) } ?: return null
        val wantNet = dir * (180f - exitAngleDeg)
        val out = ArrayList<Float>(pts.subList(0, 2 * (e + 1)))
        val exits = walkRing(entry, ring0, dir, approach, wantNet, out, stopAt = entry)
        fun off(x: Exit) = abs(turn(wantNet, x.net))
        // A number past the map's exits: AA counts more (roads in, lanes), the exit is the last
        // one the map has; AA's angle didn't fit it there (Charitas: 315 for nearly a U-turn).
        val pastTheMap = exitNumber != null && exitNumber > exits.size && exits.size >= 2
        val chosen = (if (pastTheMap) exits.last() else null)
            ?: exitNumber?.let { exits.getOrNull(it - 1) }?.takeIf { off(it) <= RING_COUNT_TOLERANCE_DEG }
            ?: exits.minByOrNull { off(it) }?.takeIf { off(it) <= RING_EXIT_TOLERANCE_DEG }
            ?: return null
        return exitRoute(out, chosen)
    }

    /**
     * Already on the ring: round it to the exit nearest AA's distance ([meters], to the exit),
     * within [RING_GUIDE_M]. Null when the car isn't on a mapped road or no exit is near.
     */
    fun onRing(roads: List<LocalRoad>, meters: Float?, clockwise: Boolean): Route? {
        if (meters == null || meters > RING_MAX_M) return null
        val nodes = graph(roads)
        val (from, to, start) = matchStart(nodes) ?: return null
        val dir = if (clockwise) -1f else 1f
        val out = arrayListOf(start.first, start.second)
        // Once round at most (back where the car is).
        val exits = walkRing(from, to, dir, heading(to.x - from.x, to.y - from.y), 0f, out, stopAt = from)
        val lead = hypot(to.x - start.first, to.y - start.second) - hypot(to.x - from.x, to.y - from.y)
        val chosen = exits.minByOrNull { abs(it.ringM + lead - meters) }?.takeIf { abs(it.ringM + lead - meters) <= RING_GUIDE_M }
            ?: return null
        return exitRoute(out, chosen)
    }

    /** [cur] (reached from [prev]) is a real junction: two or more onward ways that aren't driveways. */
    private fun isJunction(prev: Node, cur: Node): Boolean =
        cur.next.indices.count { k -> cur.next[k] !== prev && !cur.minor[k] } >= 2

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

    /**
     * A drive for the map demo (debug), from the street under the car: its centreline in the
     * car's frame through a real junction 80..400 m ahead (or the T where the street ends) and
     * into the side street there whose
     * turn is closest to 90°, a little past it. [junctionM] is along [pts]; [street] is the
     * side street's name. Without such a junction: just the street ahead ([turnDeg] 0).
     */
    class DemoPlan(val pts: FloatArray, val junctionM: Float, val turnDeg: Float, val street: String)

    fun demoPlan(roads: List<LocalRoad>): DemoPlan? {
        val nodes = graph(roads)
        val (from, to, start) = matchStart(nodes) ?: return null
        val path = arrayListOf(from, to)
        val cum = arrayListOf(0f, hypot(to.x - start.first, to.y - start.second))
        while (cum.last() < 450f) {
            val next = straightOn(path[path.size - 2], path.last()) ?: break
            cum += cum.last() + hypot(next.x - path.last().x, next.y - path.last().y)
            path += next
        }
        val pts = ArrayList<Float>()
        pts += start.first; pts += start.second
        for (i in 1 until path.size) { pts += path[i].x; pts += path[i].y }
        // A junction 80..400 m ahead, or where the street ends in a T (its last node).
        for (i in 1 until path.size) {
            val last = i == path.size - 1
            if (cum[i] !in 80f..400f || path[i].next.size < (if (last) 2 else 3)) continue
            val cur = path[i]
            val inDeg = heading(cur.x - path[i - 1].x, cur.y - path[i - 1].y)
            val k = cur.next.indices
                .filter { cur.next[it] !== path[i - 1] && (last || cur.next[it] !== path[i + 1]) && !cur.minor[it] }
                .minByOrNull { abs(abs(turn(inDeg, heading(cur.next[it].x - cur.x, cur.next[it].y - cur.y))) - 90f) }
                ?: continue
            val exit = cur.next[k]
            val turnDeg = turn(inDeg, heading(exit.x - cur.x, exit.y - cur.y))
            if (abs(turnDeg) < 30f) continue
            val out = ArrayList<Float>(pts.subList(0, 2 * (i + 1)))
            var prev = cur
            var c = exit
            var tail = 0f
            while (true) {
                tail += hypot(c.x - prev.x, c.y - prev.y)
                out += c.x; out += c.y
                if (tail >= TAIL_M) break
                val n = straightOn(prev, c) ?: break
                prev = c
                c = n
            }
            return DemoPlan(out.toFloatArray(), cum[i], turnDeg, cur.names[k])
        }
        return DemoPlan(pts.toFloatArray(), cum.last(), 0f, "")
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
