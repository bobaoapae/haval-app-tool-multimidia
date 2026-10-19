package br.com.redesurftank.havalshisuku.map

import android.os.SystemClock
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.BuildConfig
import br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

/**
 * OpenStreetMap road tiles on the car: memory, then disk (`files/osm-roads`, [TTL_MS]), then
 * Overpass. All the tiles missing round the car go out as ONE request for their box (a drive
 * first had them one by one, each failure adding a minute, and the map came 7 min late); one
 * request at a time on its own thread; after a failure on every server it waits [RETRY_MS].
 * Only tile boxes (~1.1 km squares) are ever sent, never the car's exact position.
 */
object OsmRoadStore {
    private const val TAG = "OsmRoadStore"
    /** Public Overpass servers, tried in turn: each is often busy (502/504/timeouts). */
    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    )
    private const val TTL_MS = 30L * 24 * 3600 * 1000
    private const val RETRY_MS = 30_000L
    private const val TIMEOUT_MS = 25_000

    /** The roads round a point and whether every tile there is loaded (else the view isn't whole). */
    class Near(val roads: List<OsmRoad>, val complete: Boolean)

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "OsmRoadStore") }
    private val tiles = ConcurrentHashMap<String, List<OsmRoad>>()
    private val queued = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var retryAfterMs = 0L

    /**
     * Roads of the tiles already loaded round the point (each road once); missing tiles are
     * queued together and [onLoaded] runs (on the store's thread) when they arrive.
     */
    fun roadsNear(lat: Double, lon: Double, radiusM: Double, onLoaded: () -> Unit): Near {
        val seen = HashSet<Long>()
        val out = ArrayList<OsmRoad>()
        val missing = ArrayList<OsmRoads.Tile>()
        for (tile in OsmRoads.tilesAround(lat, lon, radiusM)) {
            val roads = tiles[tile.key]
            if (roads == null) {
                if (queued.add(tile.key)) missing += tile
                continue
            }
            for (r in roads) if (seen.add(r.id)) out += r
        }
        if (missing.isNotEmpty()) io.execute { load(missing, onLoaded) }
        return Near(out, OsmRoads.tilesAround(lat, lon, radiusM).all { tiles.containsKey(it.key) })
    }

    private fun load(wanted: List<OsmRoads.Tile>, onLoaded: () -> Unit) {
        try {
            // Disk first; whatever is left goes out as one box.
            val toFetch = ArrayList<OsmRoads.Tile>()
            for (tile in wanted) {
                val file = file(tile)
                if (file.isFile && System.currentTimeMillis() - file.lastModified() < TTL_MS) {
                    tiles[tile.key] = OsmRoads.parse(file.readText())
                } else {
                    toFetch += tile
                }
            }
            if (toFetch.isNotEmpty() && System.currentTimeMillis() >= retryAfterMs) {
                val started = SystemClock.elapsedRealtime()
                val json = fetch(toFetch)
                if (json != null) {
                    val roads = OsmRoads.parse(json)
                    for (tile in toFetch) {
                        val own = OsmRoads.inTile(roads, tile)
                        tiles[tile.key] = own
                        file(tile).writeText(OsmRoads.toJson(own))
                    }
                    trace(true, toFetch.size, roads.size, SystemClock.elapsedRealtime() - started)
                } else {
                    trace(false, toFetch.size, 0, SystemClock.elapsedRealtime() - started)
                    // Stale copies on disk are better than nothing while the servers are down.
                    for (tile in toFetch) file(tile).takeIf { it.isFile }?.let { tiles[tile.key] = OsmRoads.parse(it.readText()) }
                }
            }
            // Only when something arrived: a call during the back-off must not re-queue at once.
            if (wanted.any { tiles.containsKey(it.key) }) onLoaded()
        } catch (t: Throwable) {
            Log.w(TAG, "tiles failed: ${t.message}")
        } finally {
            for (tile in wanted) queued.remove(tile.key)
        }
    }

    private fun trace(ok: Boolean, tileCount: Int, roads: Int, ms: Long) {
        Log.w(TAG, "fetch ok=$ok tiles=$tileCount roads=$roads ms=$ms")
        ClusterPersistentEventLogger.log("hud_map_tiles", mapOf("ok" to ok, "tiles" to tileCount, "roads" to roads, "ms" to ms))
    }

    /** The box of [tiles] from the first server that answers; null (and a back-off) when none does. */
    private fun fetch(tiles: List<OsmRoads.Tile>): String? {
        val query = OsmRoads.query(
            tiles.minOf { it.south }, tiles.minOf { it.west }, tiles.maxOf { it.north }, tiles.maxOf { it.east }
        )
        for (url in ENDPOINTS) fetch(url, query)?.let { return it }
        retryAfterMs = System.currentTimeMillis() + RETRY_MS
        return null
    }

    private fun fetch(url: String, query: String): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        // overpass-api.de takes TLS 1.3 only; Android 9 stops at 1.2 (same fix as the radar).
        if (conn is HttpsURLConnection) tls?.let { conn.sslSocketFactory = it }
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("User-Agent", "Impulse/${BuildConfig.VERSION_NAME} (HUD map; Android)")
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
        val code = conn.responseCode
        if (code != 200) {
            Log.w(TAG, "HTTP $code from ${URL(url).host}")
            null
        } else {
            conn.inputStream.bufferedReader().use { it.readText() }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "${t.message} from ${URL(url).host}")
        null
    }

    private val tls: SSLSocketFactory? by lazy {
        runCatching {
            val context = javax.net.ssl.SSLContext.getInstance("TLS", org.conscrypt.Conscrypt.newProvider())
            context.init(null, null, null)
            context.socketFactory
        }.getOrNull()
    }

    private fun file(tile: OsmRoads.Tile) = File(dir(), "${tile.key}.json")

    private fun dir(): File = File(App.getDeviceProtectedContext().filesDir, "osm-roads").apply { mkdirs() }
}
