package br.com.redesurftank.havalshisuku.map

import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** A drivable road from OpenStreetMap: its points as lat, lon pairs. */
class OsmRoad(val id: Long, val name: String, val highway: String, val oneway: Boolean, val latLon: DoubleArray)

/**
 * OpenStreetMap roads for the AR HUD: fixed tiles, the Overpass query for one, its parser, and
 * the projection of roads into the car's frame. Pure (no Android) so it can be tested.
 */
object OsmRoads {
    /** Tile side in degrees: ~1.1 km of latitude. Fixed tiles keep queries coarse and cacheable. */
    const val TILE_DEG = 0.01
    private const val M_PER_DEG_LAT = 110_540.0
    private const val M_PER_DEG_LON_EQ = 111_320.0

    /** Road kinds a car drives on (no footways, cycleways, tracks or paths). */
    val DRIVABLE = listOf(
        "motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential",
        "living_street", "service", "motorway_link", "trunk_link", "primary_link",
        "secondary_link", "tertiary_link"
    )

    data class Tile(val x: Int, val y: Int) {
        val key: String get() = "${y}_$x"
        val south: Double get() = y * TILE_DEG
        val west: Double get() = x * TILE_DEG
        val north: Double get() = (y + 1) * TILE_DEG
        val east: Double get() = (x + 1) * TILE_DEG
    }

    fun tileOf(lat: Double, lon: Double) = Tile(floor(lon / TILE_DEG).toInt(), floor(lat / TILE_DEG).toInt())

    /** Tiles touching a square of ±[radiusM] round the point. */
    fun tilesAround(lat: Double, lon: Double, radiusM: Double): List<Tile> {
        val dLat = radiusM / M_PER_DEG_LAT
        val dLon = radiusM / (M_PER_DEG_LON_EQ * cos(lat * PI / 180))
        val y0 = floor((lat - dLat) / TILE_DEG).toInt()
        val y1 = floor((lat + dLat) / TILE_DEG).toInt()
        val x0 = floor((lon - dLon) / TILE_DEG).toInt()
        val x1 = floor((lon + dLon) / TILE_DEG).toInt()
        return (y0..y1).flatMap { y -> (x0..x1).map { x -> Tile(x, y) } }
    }

    /** Overpass QL for the drivable roads of [tile], with their geometry inline. */
    fun query(tile: Tile): String = query(tile.south, tile.west, tile.north, tile.east)

    /** Overpass QL for the drivable roads in a box (several tiles at once). */
    fun query(south: Double, west: Double, north: Double, east: Double): String {
        val bbox = "%.4f,%.4f,%.4f,%.4f".format(java.util.Locale.US, south, west, north, east)
        // The server-side limit: at 25 s a busy overpass-api.de answered "200" with no roads.
        return "[out:json][timeout:90];way[\"highway\"~\"^(${DRIVABLE.joinToString("|")})$\"]($bbox);out tags geom;"
    }

    /** The roads of a box answer that touch [tile] (their extent overlaps it; a road can be in several). */
    fun inTile(roads: List<OsmRoad>, tile: Tile): List<OsmRoad> = roads.filter { r ->
        var s = Double.MAX_VALUE; var n = -Double.MAX_VALUE; var w = Double.MAX_VALUE; var e = -Double.MAX_VALUE
        for (k in 0 until r.latLon.size / 2) {
            val la = r.latLon[2 * k]; val lo = r.latLon[2 * k + 1]
            if (la < s) s = la; if (la > n) n = la; if (lo < w) w = lo; if (lo > e) e = lo
        }
        n >= tile.south && s <= tile.north && e >= tile.west && w <= tile.east
    }

    /**
     * Whether an Overpass answer is a real one: a busy server answers "200" with a "remark" such
     * as "runtime error: Query timed out" and no elements (2026-10-19, cached as empty tiles).
     */
    fun answerOk(json: String): Boolean = runCatching {
        val remark = JSONObject(json).optString("remark")
        !(remark.contains("error", ignoreCase = true) || remark.contains("timed out", ignoreCase = true))
    }.getOrDefault(false)

    /**
     * Whether a tile's cache file can be trusted: one written from a checked answer carries
     * [COMPLETE_MARK]; an older raw answer is fine unless it holds an error remark.
     */
    fun cacheOk(json: String): Boolean = runCatching {
        val o = JSONObject(json)
        o.optBoolean(COMPLETE_MARK) || (o.has("osm3s") && answerOk(json))
    }.getOrDefault(false)

    const val COMPLETE_MARK = "impulse_complete"

    /** [roads] back as an Overpass-shaped answer, for one tile's cache file ([parse] reads it). */
    fun toJson(roads: List<OsmRoad>): String {
        val elements = org.json.JSONArray()
        for (r in roads) {
            val geometry = org.json.JSONArray()
            for (k in 0 until r.latLon.size / 2) geometry.put(JSONObject().put("lat", r.latLon[2 * k]).put("lon", r.latLon[2 * k + 1]))
            val tags = JSONObject().put("highway", r.highway)
            if (r.name.isNotEmpty()) tags.put("name", r.name)
            if (r.oneway) tags.put("oneway", "yes")
            elements.put(JSONObject().put("type", "way").put("id", r.id).put("tags", tags).put("geometry", geometry))
        }
        return JSONObject().put(COMPLETE_MARK, true).put("elements", elements).toString()
    }

    /** Roads in an Overpass `out geom` answer. */
    fun parse(json: String): List<OsmRoad> {
        val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val roads = ArrayList<OsmRoad>(elements.length())
        for (i in 0 until elements.length()) {
            val e = elements.optJSONObject(i) ?: continue
            if (e.optString("type") != "way") continue
            val geometry = e.optJSONArray("geometry") ?: continue
            if (geometry.length() < 2) continue
            val tags = e.optJSONObject("tags")
            val pts = DoubleArray(geometry.length() * 2)
            for (k in 0 until geometry.length()) {
                val g = geometry.getJSONObject(k)
                pts[2 * k] = g.getDouble("lat")
                pts[2 * k + 1] = g.getDouble("lon")
            }
            roads += OsmRoad(
                id = e.optLong("id"),
                name = tags?.optString("name").orEmpty(),
                highway = tags?.optString("highway").orEmpty(),
                oneway = tags?.optString("oneway") == "yes",
                latLon = pts
            )
        }
        return roads
    }

    /**
     * [roads] in the car's frame, in metres: x to the right, y ahead, for a car at [lat], [lon]
     * heading [bearingDeg] (0 = north). Only what lies within [aheadM] ahead (and a little
     * behind) and [sideM] to each side; long segments are split to at most [stepM] so a later
     * perspective projection can clip them near the car.
     */
    fun toLocal(
        roads: List<OsmRoad>,
        lat: Double,
        lon: Double,
        bearingDeg: Double,
        aheadM: Double,
        sideM: Double,
        stepM: Double
    ): List<LocalRoad> {
        val mLat = M_PER_DEG_LAT
        val mLon = M_PER_DEG_LON_EQ * cos(lat * PI / 180)
        val b = bearingDeg * PI / 180
        val sb = sin(b)
        val cb = cos(b)
        val behindM = 30.0
        val out = ArrayList<LocalRoad>()
        for (road in roads) {
            val width = widthOf(road.highway)
            val n = road.latLon.size / 2
            val run = ArrayList<Float>()
            var px = 0.0
            var py = 0.0
            for (k in 0 until n) {
                val east = (road.latLon[2 * k + 1] - lon) * mLon
                val north = (road.latLon[2 * k] - lat) * mLat
                val x = east * cb - north * sb
                val y = east * sb + north * cb
                if (k > 0) {
                    // Split the segment from the previous point.
                    val pieces = max(1, ceil(hypot(x - px, y - py) / stepM).toInt())
                    for (j in 1..pieces) {
                        val t = j.toDouble() / pieces
                        val sx = px + (x - px) * t
                        val sy = py + (y - py) * t
                        if (sy < -behindM || sy > aheadM || abs(sx) > sideM) {
                            if (run.size >= 4) out += LocalRoad(run.toFloatArray(), width, road.name)
                            run.clear()
                        } else {
                            run += sx.toFloat(); run += sy.toFloat()
                        }
                    }
                } else if (y >= -behindM && y <= aheadM && abs(x) <= sideM) {
                    run += x.toFloat(); run += y.toFloat()
                }
                px = x
                py = y
            }
            if (run.size >= 4) out += LocalRoad(run.toFloatArray(), width, road.name)
        }
        return out
    }

    /**
     * Bearing (degrees, 0 = north) along the nearest street within [maxM] of the point, of its
     * two directions the one closer to [prefer]; null when none is that close. For a car that
     * stands still, so GPS gives no heading.
     */
    fun roadBearing(roads: List<OsmRoad>, lat: Double, lon: Double, prefer: Double, maxM: Double = 30.0): Double? {
        val local = toLocal(roads, lat, lon, 0.0, 1_000.0, 1_000.0, 10.0)
        var best = Double.MAX_VALUE
        var bearing: Double? = null
        for (road in local) {
            if (road.minor) continue
            for (k in 0 until road.pts.size / 2 - 1) {
                val ax = road.pts[2 * k].toDouble(); val ay = road.pts[2 * k + 1].toDouble()
                val bx = road.pts[2 * k + 2].toDouble(); val by = road.pts[2 * k + 3].toDouble()
                val dx = bx - ax; val dy = by - ay
                val len2 = dx * dx + dy * dy
                if (len2 <= 0.0) continue
                val t = (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
                val d = hypot(ax + dx * t, ay + dy * t)
                if (d < best && d <= maxM) {
                    best = d
                    bearing = Math.toDegrees(kotlin.math.atan2(dx, dy))
                }
            }
        }
        val b = bearing ?: return null
        // The two directions along the street: the one nearer [prefer].
        fun diff(x: Double) = abs(((x - prefer) % 360 + 540) % 360 - 180)
        val other = (b + 180) % 360
        return if (diff(b) <= diff(other)) (b + 360) % 360 else other
    }

    /** Drawn width relative to the route's: avenues wider, driveways narrower. */
    fun widthOf(highway: String): Float = when (highway) {
        "motorway", "trunk" -> 1.5f
        "primary" -> 1.3f
        "secondary", "tertiary" -> 1.15f
        "service" -> 0.55f
        else -> if (highway.endsWith("_link")) 0.8f else 1f
    }
}
