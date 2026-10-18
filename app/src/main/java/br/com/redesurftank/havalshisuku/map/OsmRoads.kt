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
    fun query(tile: Tile): String {
        val bbox = "%.4f,%.4f,%.4f,%.4f".format(java.util.Locale.US, tile.south, tile.west, tile.north, tile.east)
        return "[out:json][timeout:25];way[\"highway\"~\"^(${DRIVABLE.joinToString("|")})$\"]($bbox);out tags geom;"
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
     * perspective projection can clip them near the car. Each polyline is x, y pairs.
     */
    fun toLocal(
        roads: List<OsmRoad>,
        lat: Double,
        lon: Double,
        bearingDeg: Double,
        aheadM: Double,
        sideM: Double,
        stepM: Double
    ): List<FloatArray> {
        val mLat = M_PER_DEG_LAT
        val mLon = M_PER_DEG_LON_EQ * cos(lat * PI / 180)
        val b = bearingDeg * PI / 180
        val sb = sin(b)
        val cb = cos(b)
        val behindM = 30.0
        val out = ArrayList<FloatArray>()
        for (road in roads) {
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
                            if (run.size >= 4) out += run.toFloatArray()
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
            if (run.size >= 4) out += run.toFloatArray()
        }
        return out
    }
}
