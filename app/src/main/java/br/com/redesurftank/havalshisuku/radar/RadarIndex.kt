package br.com.redesurftank.havalshisuku.radar

import kotlin.math.cos
import kotlin.math.floor

/**
 * Grid over the cameras (cells of [CELL_DEG], ~2.2 km) so a GPS fix only looks at the few
 * cameras around it instead of all ~11k, with each camera's road when it has one ([roads],
 * aligned with [cameras]; see RadarRoads.tie). Immutable; build a new one when the data changes.
 */
class RadarIndex(val cameras: List<RadarCamera>, private val roads: Array<CameraRoad?> = arrayOfNulls(cameras.size)) {
    private companion object {
        const val CELL_DEG = 0.02
        const val METERS_PER_DEG = 111_320.0
    }

    private val cells: Map<Long, IntArray> = run {
        val lists = HashMap<Long, MutableList<Int>>()
        cameras.forEachIndexed { i, c -> lists.getOrPut(key(cell(c.lat), cell(c.lon))) { mutableListOf() }.add(i) }
        lists.mapValues { it.value.toIntArray() }
    }

    val size: Int get() = cameras.size

    /** The road camera [i] sits on, or null (no OSM road near it, or no road data yet). */
    fun roadOf(i: Int): CameraRoad? = roads.getOrNull(i)

    val withRoad: Int get() = roads.count { it != null }

    private fun cell(deg: Double): Int = floor(deg / CELL_DEG).toInt()
    private fun key(row: Int, col: Int): Long = (row.toLong() shl 32) xor (col.toLong() and 0xffffffffL)

    /** Calls [block] with (index, camera, metres) for every camera within [radiusM]. */
    inline fun forEachNear(lat: Double, lon: Double, radiusM: Double, block: (Int, RadarCamera, Double) -> Unit) {
        for (i in candidates(lat, lon, radiusM)) {
            val c = cameras[i]
            val d = RadarGeo.distanceM(lat, lon, c.lat, c.lon)
            if (d <= radiusM) block(i, c, d)
        }
    }

    /** Indices in the cells covering [radiusM] around the point (a superset of the real hits). */
    fun candidates(lat: Double, lon: Double, radiusM: Double): List<Int> {
        val dLat = radiusM / METERS_PER_DEG
        val dLon = radiusM / (METERS_PER_DEG * cos(Math.toRadians(lat)).coerceAtLeast(0.1))
        val out = ArrayList<Int>()
        for (row in cell(lat - dLat)..cell(lat + dLat)) {
            for (col in cell(lon - dLon)..cell(lon + dLon)) {
                cells[key(row, col)]?.let { ids -> ids.forEach { out.add(it) } }
            }
        }
        return out
    }
}
