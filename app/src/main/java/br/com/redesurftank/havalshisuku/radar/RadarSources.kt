package br.com.redesurftank.havalshisuku.radar

import org.json.JSONArray
import org.json.JSONObject

/**
 * Where the radar data comes from and how each source is read. Pure: no Android, no network
 * (RadarUpdater does the fetching), so the parsing is unit-tested on real samples.
 */
object RadarSources {
    /** Brazil plus the border strips of its neighbours (cameras there help near the border). */
    const val BBOX = "-33.8,-74.0,5.3,-34.7"

    /**
     * Public Overpass servers, tried in order. The main one needs TLS 1.3 (RadarUpdater brings
     * it to Android 9); maps.mail.ru also answers over TLS 1.2. The query is always all of
     * Brazil, so no server learns where the car is.
     */
    val OVERPASS_URLS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
    )

    /**
     * Every camera in [BBOX], then the roads under the ones tagged `direction=forward|backward`
     * (their direction is relative to the road, so the road's geometry is needed). About 10k
     * cameras + 600 roads, ~500 KB gzipped (measured 2026-10-17).
     */
    val OVERPASS_QUERY = """
        [out:json][timeout:240];
        node["highway"="speed_camera"]($BBOX)->.c;
        .c out qt;
        node.c["direction"~"^(forward|backward)$"]->.d;
        way(bn.d)["highway"];
        out geom qt;
    """.trimIndent()

    /** CKAN metadata of ANTT's "Dados dos Radares": a new CSV resource is added each month. */
    const val ANTT_PACKAGE_URL = "https://dados.antt.gov.br/api/3/action/package_show?id=radar"

    /** Fewer than this many cameras means a broken download, not a real change. */
    const val MIN_OSM_CAMERAS = 5_000
    const val MIN_ANTT_CAMERAS = 300

    // ---- OpenStreetMap (Overpass JSON) ----

    fun parseOverpass(json: String): List<RadarCamera> {
        val elements = JSONObject(json).getJSONArray("elements")
        // Road id -> (node ids, coordinates) for resolving forward/backward.
        val ways = HashMap<Long, Pair<LongArray, List<DoubleArray>>>()
        val nodeWays = HashMap<Long, MutableList<Long>>()
        for (i in 0 until elements.length()) {
            val e = elements.getJSONObject(i)
            if (e.optString("type") != "way") continue
            val ids = e.optJSONArray("nodes") ?: continue
            val geometry = e.optJSONArray("geometry") ?: continue
            if (ids.length() != geometry.length() || ids.length() < 2) continue
            val nodeIds = LongArray(ids.length()) { ids.getLong(it) }
            val coords = (0 until geometry.length()).map {
                val p = geometry.getJSONObject(it)
                doubleArrayOf(p.getDouble("lat"), p.getDouble("lon"))
            }
            val wayId = e.getLong("id")
            ways[wayId] = nodeIds to coords
            nodeIds.forEach { nodeWays.getOrPut(it) { mutableListOf() }.add(wayId) }
        }
        val cameras = ArrayList<RadarCamera>()
        for (i in 0 until elements.length()) {
            val e = elements.getJSONObject(i)
            if (e.optString("type") != "node") continue
            val lat = e.optDouble("lat", Double.NaN)
            val lon = e.optDouble("lon", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite()) continue
            val tags = e.optJSONObject("tags") ?: JSONObject()
            val direction = tags.optString("direction", "").trim().lowercase()
            val bearing = when (direction) {
                "forward", "backward" -> roadBearing(e.getLong("id"), nodeWays, ways)
                    ?.let { if (direction == "backward") RadarGeo.normalize(it + 180f) else it }
                else -> RadarGeo.parseCompass(direction)
            }
            cameras.add(RadarCamera(lat, lon, RadarGeo.parseLimit(tags.optString("maxspeed", "")), bearing, RadarCamera.Source.OSM))
        }
        return cameras
    }

    /** The road's own direction at [nodeId] (from the node before to the node after it). */
    private fun roadBearing(
        nodeId: Long,
        nodeWays: Map<Long, List<Long>>,
        ways: Map<Long, Pair<LongArray, List<DoubleArray>>>
    ): Float? {
        // A camera on a junction of several roads is ambiguous: leave it to the heading.
        val wayId = nodeWays[nodeId]?.singleOrNull() ?: return null
        val (ids, coords) = ways[wayId] ?: return null
        val i = ids.indexOf(nodeId)
        if (i < 0) return null
        val a = coords[(i - 1).coerceAtLeast(0)]
        val b = coords[(i + 1).coerceAtMost(coords.size - 1)]
        if (a === b) return null
        return RadarGeo.bearingDeg(a[0], a[1], b[0], b[1])
    }

    // ---- ANTT ----

    /** The CSV resource to use: the newest "Dados dos Radares" CSV in the package metadata. */
    data class AnttResource(val id: String, val url: String, val modified: String)

    fun latestAnttCsv(packageJson: String): AnttResource? {
        val result = JSONObject(packageJson).optJSONObject("result") ?: return null
        val resources: JSONArray = result.optJSONArray("resources") ?: return null
        var best: AnttResource? = null
        for (i in 0 until resources.length()) {
            val r = resources.getJSONObject(i)
            val name = r.optString("name")
            if (!r.optString("format").equals("CSV", ignoreCase = true)) continue
            if (!name.contains("Radares", ignoreCase = true) || name.contains("Dicion", ignoreCase = true)) continue
            val url = r.optString("url").takeIf { it.startsWith("https://") } ?: continue
            val modified = r.optString("last_modified").takeIf { it.isNotEmpty() && it != "null" } ?: r.optString("created")
            if (best == null || modified > best.modified) best = AnttResource(r.optString("id"), url, modified)
        }
        return best
    }

    private class AnttRow(
        val road: String,
        val km: Double,
        val lat: Double,
        val lon: Double,
        val limit: Int?,
        /** 1 = towards increasing km ("Crescente"), -1 = decreasing, 0 = both. */
        val sense: Int
    )

    /**
     * ANTT CSV (ISO-8859-1, `;`, decimal comma). Columns used: rodovia, uf, km_m, sentido,
     * situacao, latitude, longitude, velocidade_leve. "Crescente" means traffic towards
     * increasing km; the compass bearing of that comes from the next ANTT radar on the same road
     * (see [kmBearing]), since the file has no road geometry.
     */
    fun parseAnttCsv(text: String): List<RadarCamera> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.iterator()
        if (!lines.hasNext()) return emptyList()
        val sep = if (text.substringBefore('\n').count { it == ';' } > 0) ';' else ','
        val header = lines.next().trimStart('﻿').split(sep).map { it.trim().lowercase() }
        fun col(name: String) = header.indexOf(name)
        val iRoad = col("rodovia"); val iUf = col("uf"); val iKm = col("km_m"); val iSense = col("sentido")
        val iState = col("situacao"); val iLat = col("latitude"); val iLon = col("longitude"); val iLimit = col("velocidade_leve")
        if (iLat < 0 || iLon < 0) return emptyList()
        fun num(s: String?) = s?.trim()?.replace(',', '.')?.toDoubleOrNull()
        val rows = ArrayList<AnttRow>()
        for (line in lines) {
            val p = line.split(sep)
            fun at(i: Int) = if (i in p.indices) p[i] else null
            if (iState >= 0 && !at(iState).orEmpty().trim().equals("Ativo", ignoreCase = true)) continue
            val lat = num(at(iLat)) ?: continue
            val lon = num(at(iLon)) ?: continue
            // Swapped or zeroed coordinates show up as points outside the region.
            if (lat !in -34.0..6.0 || lon !in -75.0..-34.0) continue
            val sense = at(iSense).orEmpty().trim().lowercase().let {
                when {
                    it.contains("/") -> 0
                    it.startsWith("cres") -> 1
                    it.startsWith("decres") -> -1
                    else -> 0
                }
            }
            rows.add(AnttRow("${at(iRoad).orEmpty().trim()}|${at(iUf).orEmpty().trim()}", num(at(iKm)) ?: Double.NaN, lat, lon, RadarGeo.parseLimit(at(iLimit)), sense))
        }
        val byRoad = rows.groupBy { it.road }
        return rows.map { r ->
            val bearing = if (r.sense == 0) null else kmBearing(r, byRoad[r.road].orEmpty())?.let {
                if (r.sense > 0) it else RadarGeo.normalize(it + 180f)
            }
            RadarCamera(r.lat, r.lon, r.limit, bearing, RadarCamera.Source.ANTT)
        }
    }

    /**
     * Compass bearing of increasing km at [r]: towards the closest-by-km radar on the same road
     * 0.5..40 km away. Skipped when the straight line is far shorter than the km difference
     * (a winding stretch or bad data), since the bearing would then be meaningless.
     */
    private fun kmBearing(r: AnttRow, road: List<AnttRow>): Float? {
        if (!r.km.isFinite()) return null
        val other = road
            .filter { it.km.isFinite() && kotlin.math.abs(it.km - r.km) in 0.5..40.0 }
            .minByOrNull { kotlin.math.abs(it.km - r.km) } ?: return null
        val dkm = kotlin.math.abs(other.km - r.km)
        val straight = RadarGeo.distanceM(r.lat, r.lon, other.lat, other.lon)
        if (straight < dkm * 1000 * 0.6 || straight > dkm * 1000 * 1.5) return null
        return if (other.km > r.km) RadarGeo.bearingDeg(r.lat, r.lon, other.lat, other.lon)
        else RadarGeo.bearingDeg(other.lat, other.lon, r.lat, r.lon)
    }

    /** ANTT radars with no OSM camera nearby: their roads must be asked for by position. */
    fun anttOnly(osm: List<RadarCamera>, antt: List<RadarCamera>): List<RadarCamera> {
        val index = RadarIndex(osm)
        return antt.filter { a ->
            var near = false
            index.forEachNear(a.lat, a.lon, MERGE_RADIUS_M) { _, _, _ -> near = true }
            !near
        }
    }

    // ---- merge ----

    /** An ANTT radar and an OSM camera this close (and pointing the same way) are one camera. */
    const val MERGE_RADIUS_M = 60.0
    private const val MERGE_MAX_ANGLE = 60f

    /**
     * OSM + ANTT without duplicates. ANTT is the operator's own record, so it wins on the
     * limit and direction when both have them; OSM fills what ANTT lacks. The OSM position is
     * kept: mappers put the node on the road, which is what ties the camera to its road.
     */
    fun merge(osm: List<RadarCamera>, antt: List<RadarCamera>): List<RadarCamera> {
        if (antt.isEmpty()) return osm
        val index = RadarIndex(osm)
        val used = BooleanArray(osm.size)
        val merged = ArrayList<RadarCamera>(osm.size + antt.size)
        for (a in antt) {
            var best = -1
            var bestD = Double.MAX_VALUE
            index.forEachNear(a.lat, a.lon, MERGE_RADIUS_M) { i, cam, d ->
                if (used[i] || d >= bestD) return@forEachNear
                if (a.bearing != null && cam.bearing != null && RadarGeo.angleDiff(a.bearing, cam.bearing) > MERGE_MAX_ANGLE) return@forEachNear
                best = i
                bestD = d
            }
            if (best < 0) {
                merged.add(a)
                continue
            }
            used[best] = true
            val o = osm[best]
            merged.add(a.copy(lat = o.lat, lon = o.lon, limitKmh = a.limitKmh ?: o.limitKmh, bearing = a.bearing ?: o.bearing))
        }
        osm.forEachIndexed { i, cam -> if (!used[i]) merged.add(cam) }
        return merged
    }
}
