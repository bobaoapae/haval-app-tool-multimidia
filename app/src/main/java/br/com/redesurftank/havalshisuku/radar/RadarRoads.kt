package br.com.redesurftank.havalshisuku.radar

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.Reader
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot

/**
 * A stretch of the OSM road a camera sits on (±[RadarRoads.CLIP_M] along the road around it),
 * in the road's own drawing order. [oneway]: 1 = traffic only in drawing order, -1 = only
 * against it, 0 = both ways.
 */
class RoadSnippet(val oneway: Int, val lat: DoubleArray, val lon: DoubleArray) {
    /** Metres along the snippet at each point. */
    val s: DoubleArray = DoubleArray(lat.size).also { acc ->
        for (i in 1 until lat.size) acc[i] = acc[i - 1] + RadarGeo.distanceM(lat[i - 1], lon[i - 1], lat[i], lon[i])
    }

    val length: Double get() = s.last()

    /**
     * Where a point falls on the road: [perpM] metres off it, [s] metres along it, [dirDeg] the
     * road's drawing direction there. Past either end the road is taken to carry on straight
     * (the end segment extended), [beyondM] metres beyond that end; 0 within the snippet.
     */
    class Projection(val perpM: Double, val s: Double, val dirDeg: Float, val beyondM: Double)

    fun project(pLat: Double, pLon: Double): Projection {
        val k = cos(Math.toRadians(pLat)) * RadarGeo.METERS_PER_DEG
        var best: Projection? = null
        val last = lat.size - 2
        for (i in 0..last) {
            val ax = (lon[i] - pLon) * k
            val ay = (lat[i] - pLat) * RadarGeo.METERS_PER_DEG
            val bx = (lon[i + 1] - pLon) * k
            val by = (lat[i + 1] - pLat) * RadarGeo.METERS_PER_DEG
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            if (len2 < 1e-6) continue
            val len = kotlin.math.sqrt(len2)
            var t = -(ax * dx + ay * dy) / len2
            // Only the end segments extend past the snippet.
            val lo = if (i == 0) Double.NEGATIVE_INFINITY else 0.0
            val hi = if (i == last) Double.POSITIVE_INFINITY else 1.0
            t = t.coerceIn(lo, hi)
            val px = ax + t * dx
            val py = ay + t * dy
            val perp = hypot(px, py)
            if (best == null || perp < best.perpM) {
                val beyond = when {
                    t < 0 -> -t * len
                    t > 1 -> (t - 1) * len
                    else -> 0.0
                }
                val dir = RadarGeo.normalize(Math.toDegrees(atan2(dx, dy)).toFloat())
                best = Projection(perp, s[i] + t * len, dir, beyond)
            }
        }
        return best ?: Projection(Double.MAX_VALUE, 0.0, 0f, 0.0)
    }

    /** Drawing direction at [along] metres (the segment containing it). */
    fun directionAt(along: Double): Float {
        var i = 0
        while (i < s.size - 2 && s[i + 1] < along) i++
        return RadarGeo.bearingDeg(lat[i], lon[i], lat[i + 1], lon[i + 1])
    }
}

/**
 * A camera tied to its road: [sCamera] metres along [snippet], and the travel direction it
 * enforces relative to the snippet's drawing order ([allowed]: 1, -1, or 0 for both).
 */
class CameraRoad(val snippet: RoadSnippet, val sCamera: Double, val allowed: Int)

/**
 * Roads under the cameras: how they are fetched (Overpass, monthly), cut down, stored and tied
 * to cameras. Pure JVM (Gson's streaming reader, no Android), so the APK seed is built with the
 * same code on a PC.
 */
object RadarRoads {
    /** Road kept on each side of a camera, along the road. */
    const val CLIP_M = 200.0
    /** Fewer stretches than this means a broken answer (Brazil: ~9k, measured). */
    const val MIN_SNIPPETS = 3_000
    /** Roads change slowly and cost ~6 MB: refreshed at most this often. */
    const val COOLDOWN_MS = 30L * 24 * 60 * 60_000L
    /** A camera this close to a road is on it (OSM cameras: 99.9% within 25 m, measured). */
    const val MATCH_M = 30.0
    /** Camera direction vs road direction to count as "this way" / "the other way". */
    private const val DIRECTION_MATCH_DEG = 60f

    /** Car-carrying roads (no paths, service lanes or tracks). */
    private const val HIGHWAY_RE =
        "^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|road|" +
            "motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$"

    /**
     * Roads within 25 m of every OSM camera in Brazil, plus around each extra point (ANTT
     * radars that are not OSM nodes). Geometry without tags, then the `oneway` tags on their
     * own: ~6 MB gzipped, ~2 min on the server (measured 2026-10-18), hence monthly.
     */
    fun overpassQuery(extraPoints: List<Pair<Double, Double>>): String {
        val sb = StringBuilder()
        sb.append("[out:json][timeout:600];\n")
        sb.append("node[\"highway\"=\"speed_camera\"](${RadarSources.BBOX})->.c;\n")
        sb.append("(\n  way(around.c:25)[highway~\"$HIGHWAY_RE\"];\n")
        for ((lat, lon) in extraPoints) {
            sb.append(String.format(Locale.US, "  way(around:25,%.6f,%.6f)[highway~\"%s\"];\n", lat, lon, HIGHWAY_RE))
        }
        sb.append(")->.near;\n.near out skel geom qt;\nway.near[oneway];\nout tags qt;\n")
        return sb.toString()
    }

    // ---- building snippets from the Overpass answer (two streaming passes over a file) ----

    private class Way(val id: Long, val lat: DoubleArray, val lon: DoubleArray)

    private class Best(var dist: Double = Double.MAX_VALUE, var way: Long = 0L, var s: Double = 0.0)

    /**
     * Cuts the Overpass answer down to the snippets the [cameras] need: each camera's nearest
     * road within [MATCH_M], ±[CLIP_M] around it. [open] reopens the answer for each pass, so the
     * ~30 MB of JSON is streamed, never held in memory.
     */
    fun buildSnippets(open: () -> Reader, cameras: List<RadarCamera>): List<RoadSnippet> {
        val grid = CameraGrid(cameras)
        val best = Array(cameras.size) { Best() }
        val oneway = HashMap<Long, Int>()
        // Pass 1: the nearest road of every camera, and the oneway tags.
        forEachWay(open(), onTags = { id, value -> parseOneway(value)?.let { oneway[id] = it } }) { way ->
            val cum = cumulative(way)
            for (i in 0 until way.lat.size - 1) {
                grid.forEachNearSegment(way.lat[i], way.lon[i], way.lat[i + 1], way.lon[i + 1], MATCH_M) { ci ->
                    val c = cameras[ci]
                    val (d, along) = segmentDistance(c.lat, c.lon, way, i, cum)
                    if (d < best[ci].dist) {
                        best[ci].dist = d
                        best[ci].way = way.id
                        best[ci].s = along
                    }
                }
            }
        }
        val wanted = HashMap<Long, MutableList<Double>>()
        best.forEach { b -> if (b.dist <= MATCH_M) wanted.getOrPut(b.way) { mutableListOf() }.add(b.s) }
        // Pass 2: cut each wanted road around its cameras, merging overlapping stretches.
        val snippets = ArrayList<RoadSnippet>()
        forEachWay(open(), onTags = { _, _ -> }) { way ->
            val at = wanted[way.id] ?: return@forEachWay
            val cum = cumulative(way)
            val ranges = at.map { (it - CLIP_M) to (it + CLIP_M) }.sortedBy { it.first }
            var cur: Pair<Double, Double>? = null
            val merged = ArrayList<Pair<Double, Double>>()
            for (r in ranges) {
                val c = cur
                cur = if (c != null && r.first <= c.second) c.first to maxOf(c.second, r.second) else { c?.let { merged.add(it) }; r }
            }
            cur?.let { merged.add(it) }
            for ((from, to) in merged) {
                // Whole segments: from the last point at or before [from] to the first at or after [to].
                var lo = 0
                while (lo < cum.size - 1 && cum[lo + 1] <= from) lo++
                var hi = cum.size - 1
                while (hi > 0 && cum[hi - 1] >= to) hi--
                if (hi - lo < 1) continue
                snippets.add(RoadSnippet(oneway[way.id] ?: 0, way.lat.copyOfRange(lo, hi + 1), way.lon.copyOfRange(lo, hi + 1)))
            }
        }
        return snippets
    }

    private fun parseOneway(value: String?): Int? = when (value?.trim()?.lowercase()) {
        "yes", "1", "true" -> 1
        "-1", "reverse" -> -1
        else -> null
    }

    private fun cumulative(way: Way): DoubleArray {
        val cum = DoubleArray(way.lat.size)
        for (i in 1 until way.lat.size) cum[i] = cum[i - 1] + RadarGeo.distanceM(way.lat[i - 1], way.lon[i - 1], way.lat[i], way.lon[i])
        return cum
    }

    /** Distance from a point to segment [i] of [way], and the metres along the way of the foot. */
    private fun segmentDistance(pLat: Double, pLon: Double, way: Way, i: Int, cum: DoubleArray): Pair<Double, Double> {
        val k = cos(Math.toRadians(pLat)) * RadarGeo.METERS_PER_DEG
        val ax = (way.lon[i] - pLon) * k
        val ay = (way.lat[i] - pLat) * RadarGeo.METERS_PER_DEG
        val dx = (way.lon[i + 1] - way.lon[i]) * k
        val dy = (way.lat[i + 1] - way.lat[i]) * RadarGeo.METERS_PER_DEG
        val len2 = dx * dx + dy * dy
        val t = if (len2 < 1e-6) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val d = hypot(ax + t * dx, ay + t * dy)
        return d to cum[i] + t * (cum[i + 1] - cum[i])
    }

    /**
     * Streams the Overpass JSON: [onWay] for each way with geometry, [onTags] with each way's
     * `oneway` value (the tags come in their own section after the geometry).
     */
    private fun forEachWay(reader: Reader, onTags: (Long, String?) -> Unit, onWay: (Way) -> Unit) {
        JsonReader(reader).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                if (r.nextName() != "elements") {
                    r.skipValue()
                    continue
                }
                r.beginArray()
                while (r.hasNext()) readElement(r, onTags, onWay)
                r.endArray()
            }
            r.endObject()
        }
    }

    private fun readElement(r: JsonReader, onTags: (Long, String?) -> Unit, onWay: (Way) -> Unit) {
        var type = ""
        var id = 0L
        var lats: DoubleArray? = null
        var lons: DoubleArray? = null
        var oneway: String? = null
        var hasTags = false
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "type" -> type = r.nextString()
                "id" -> id = r.nextLong()
                "geometry" -> {
                    val la = ArrayList<Double>()
                    val lo = ArrayList<Double>()
                    r.beginArray()
                    while (r.hasNext()) {
                        if (r.peek() == JsonToken.NULL) { r.nextNull(); continue }
                        var pLat = Double.NaN
                        var pLon = Double.NaN
                        r.beginObject()
                        while (r.hasNext()) when (r.nextName()) {
                            "lat" -> pLat = r.nextDouble()
                            "lon" -> pLon = r.nextDouble()
                            else -> r.skipValue()
                        }
                        r.endObject()
                        if (pLat.isFinite() && pLon.isFinite()) { la.add(pLat); lo.add(pLon) }
                    }
                    r.endArray()
                    lats = la.toDoubleArray()
                    lons = lo.toDoubleArray()
                }
                "tags" -> {
                    hasTags = true
                    r.beginObject()
                    while (r.hasNext()) if (r.nextName() == "oneway") oneway = r.nextString() else r.skipValue()
                    r.endObject()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        if (type != "way") return
        if (hasTags) onTags(id, oneway)
        val la = lats
        val lo = lons
        if (la != null && lo != null && la.size >= 2) onWay(Way(id, la, lo))
    }

    /** Cameras by grid cell, to find the ones near a road segment quickly. */
    private class CameraGrid(private val cameras: List<RadarCamera>) {
        private val cell = 0.005
        private val cells = HashMap<Long, MutableList<Int>>()

        init {
            cameras.forEachIndexed { i, c -> cells.getOrPut(key(idx(c.lat), idx(c.lon))) { mutableListOf() }.add(i) }
        }

        private fun idx(v: Double) = floor(v / cell).toInt()
        private fun key(a: Int, b: Int) = (a.toLong() shl 32) xor (b.toLong() and 0xffffffffL)

        fun forEachNearSegment(lat1: Double, lon1: Double, lat2: Double, lon2: Double, padM: Double, block: (Int) -> Unit) {
            val padLat = padM / RadarGeo.METERS_PER_DEG
            val padLon = padM / (RadarGeo.METERS_PER_DEG * cos(Math.toRadians(lat1)).coerceAtLeast(0.1))
            val r0 = idx(minOf(lat1, lat2) - padLat)
            val r1 = idx(maxOf(lat1, lat2) + padLat)
            val c0 = idx(minOf(lon1, lon2) - padLon)
            val c1 = idx(maxOf(lon1, lon2) + padLon)
            // A very long straight segment would span thousands of cells; such roads are rare
            // near cameras, and the camera's own nearer segments still match.
            if ((r1 - r0 + 1).toLong() * (c1 - c0 + 1) > 400) return
            for (r in r0..r1) for (c in c0..c1) cells[key(r, c)]?.forEach(block)
        }
    }

    // ---- tying cameras to snippets (at load) ----

    /**
     * For each camera, its road: the nearest snippet within [MATCH_M], where along it the camera
     * sits, and which way it enforces. A one-way road decides the direction (a camera can only
     * face its traffic); otherwise the camera's own direction, if it has one; else both ways.
     */
    fun tie(cameras: List<RadarCamera>, snippets: List<RoadSnippet>): Array<CameraRoad?> {
        val grid = SnippetGrid(snippets)
        return Array(cameras.size) { i ->
            val c = cameras[i]
            var bestSnippet: RoadSnippet? = null
            var bestProj: RoadSnippet.Projection? = null
            for (sn in grid.near(c.lat, c.lon)) {
                val p = sn.project(c.lat, c.lon)
                if (p.beyondM > 0.0 || p.perpM > MATCH_M) continue
                if (bestProj == null || p.perpM < bestProj.perpM) {
                    bestSnippet = sn
                    bestProj = p
                }
            }
            val sn = bestSnippet ?: return@Array null
            val p = bestProj!!
            val allowed = when {
                sn.oneway != 0 -> sn.oneway
                c.bearing != null && RadarGeo.angleDiff(c.bearing, p.dirDeg) <= DIRECTION_MATCH_DEG -> 1
                c.bearing != null && RadarGeo.angleDiff(c.bearing, p.dirDeg + 180f) <= DIRECTION_MATCH_DEG -> -1
                else -> 0
            }
            CameraRoad(sn, p.s, allowed)
        }
    }

    private class SnippetGrid(snippets: List<RoadSnippet>) {
        private val cell = 0.01
        private val cells = HashMap<Long, MutableList<RoadSnippet>>()

        init {
            for (sn in snippets) {
                val keys = HashSet<Long>()
                for (i in sn.lat.indices) keys.add(key(idx(sn.lat[i]), idx(sn.lon[i])))
                keys.forEach { cells.getOrPut(it) { mutableListOf() }.add(sn) }
            }
        }

        private fun idx(v: Double) = floor(v / cell).toInt()
        private fun key(a: Int, b: Int) = (a.toLong() shl 32) xor (b.toLong() and 0xffffffffL)

        fun near(lat: Double, lon: Double): Set<RoadSnippet> {
            val out = LinkedHashSet<RoadSnippet>()
            val r = idx(lat)
            val c = idx(lon)
            for (dr in -1..1) for (dc in -1..1) cells[key(r + dr, c + dc)]?.let { out.addAll(it) }
            return out
        }
    }

    // ---- file ----

    const val VERSION = "v1"

    data class Contents(val updated: String, val snippets: List<RoadSnippet>)

    /**
     * `# impulse-radar-roads v1 updated=...` then one snippet per line:
     * `oneway|lat,lon;lat,lon;...` (5 decimals, ~1 m).
     */
    fun encode(contents: Contents): ByteArray {
        val sb = StringBuilder(contents.snippets.size * 120)
        sb.append("# impulse-radar-roads ").append(VERSION).append(" updated=").append(contents.updated).append('\n')
        for (sn in contents.snippets) {
            sb.append(sn.oneway).append('|')
            for (i in sn.lat.indices) {
                if (i > 0) sb.append(';')
                sb.append(String.format(Locale.US, "%.5f,%.5f", sn.lat[i], sn.lon[i]))
            }
            sb.append('\n')
        }
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    /** Gzipped or plain (see RadarFile.decode); null when not ours. */
    fun decode(input: InputStream): Contents? = runCatching {
        val buffered = java.io.BufferedInputStream(input)
        buffered.mark(2)
        val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
        buffered.reset()
        val stream = if (gzip) GZIPInputStream(buffered) else buffered
        val lines = stream.bufferedReader(Charsets.UTF_8).readLines()
        val header = lines.firstOrNull() ?: return@runCatching null
        if (!header.startsWith("# impulse-radar-roads $VERSION")) return@runCatching null
        val updated = header.substringAfter("updated=", "").trim()
        val snippets = ArrayList<RoadSnippet>(lines.size)
        for (line in lines.drop(1)) {
            val bar = line.indexOf('|')
            if (bar < 0) continue
            val oneway = line.substring(0, bar).toIntOrNull() ?: continue
            val pts = line.substring(bar + 1).split(';')
            if (pts.size < 2) continue
            val la = DoubleArray(pts.size)
            val lo = DoubleArray(pts.size)
            var ok = true
            for ((i, p) in pts.withIndex()) {
                val comma = p.indexOf(',')
                val a = p.substring(0, comma.coerceAtLeast(0)).toDoubleOrNull()
                val b = p.substring(comma + 1).toDoubleOrNull()
                if (comma < 0 || a == null || b == null) { ok = false; break }
                la[i] = a
                lo[i] = b
            }
            if (ok) snippets.add(RoadSnippet(oneway.coerceIn(-1, 1), la, lo))
        }
        Contents(updated, snippets)
    }.getOrNull()
}
