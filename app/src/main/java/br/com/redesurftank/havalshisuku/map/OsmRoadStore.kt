package br.com.redesurftank.havalshisuku.map

import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.BuildConfig
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
 * Overpass. One fetch at a time on its own thread; after a failure it waits [RETRY_MS].
 * Only the tile (~1.1 km square) is ever sent, never the car's exact position.
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
    private const val RETRY_MS = 60_000L
    private const val TIMEOUT_MS = 20_000

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "OsmRoadStore") }
    private val tiles = ConcurrentHashMap<String, List<OsmRoad>>()
    private val queued = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var retryAfterMs = 0L

    /**
     * Roads of the tiles already loaded round the point; missing tiles are queued and
     * [onLoaded] runs (on the store's thread) when one arrives.
     */
    fun roadsNear(lat: Double, lon: Double, radiusM: Double, onLoaded: () -> Unit): List<OsmRoad> {
        val out = ArrayList<OsmRoad>()
        for (tile in OsmRoads.tilesAround(lat, lon, radiusM)) {
            val roads = tiles[tile.key]
            if (roads != null) out += roads else if (queued.add(tile.key)) io.execute { load(tile, onLoaded) }
        }
        return out
    }

    private fun load(tile: OsmRoads.Tile, onLoaded: () -> Unit) {
        try {
            val file = File(dir(), "${tile.key}.json")
            val fresh = file.isFile && System.currentTimeMillis() - file.lastModified() < TTL_MS
            val json = when {
                fresh -> file.readText()
                System.currentTimeMillis() < retryAfterMs -> return
                else -> fetch(tile)?.also { file.writeText(it) } ?: (if (file.isFile) file.readText() else return)
            }
            tiles[tile.key] = OsmRoads.parse(json)
            Log.w(TAG, "tile ${tile.key}: ${tiles[tile.key]?.size} roads${if (fresh) " (cache)" else ""}")
            onLoaded()
        } catch (t: Throwable) {
            Log.w(TAG, "tile ${tile.key} failed: ${t.message}")
        } finally {
            queued.remove(tile.key)
        }
    }

    /** The tile from the first server that answers; null (and a back-off) when none does. */
    private fun fetch(tile: OsmRoads.Tile): String? {
        for (url in ENDPOINTS) fetch(url, tile)?.let { return it }
        retryAfterMs = System.currentTimeMillis() + RETRY_MS
        return null
    }

    private fun fetch(url: String, tile: OsmRoads.Tile): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        // overpass-api.de takes TLS 1.3 only; Android 9 stops at 1.2 (same fix as the radar).
        if (conn is HttpsURLConnection) tls?.let { conn.sslSocketFactory = it }
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("User-Agent", "Impulse/${BuildConfig.VERSION_NAME} (HUD map; Android)")
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.outputStream.use { it.write(("data=" + URLEncoder.encode(OsmRoads.query(tile), "UTF-8")).toByteArray()) }
        val code = conn.responseCode
        if (code != 200) {
            Log.w(TAG, "tile ${tile.key}: HTTP $code from ${URL(url).host}")
            null
        } else {
            conn.inputStream.bufferedReader().use { it.readText() }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "tile ${tile.key}: ${t.message} from ${URL(url).host}")
        null
    }

    private val tls: SSLSocketFactory? by lazy {
        runCatching {
            val context = javax.net.ssl.SSLContext.getInstance("TLS", org.conscrypt.Conscrypt.newProvider())
            context.init(null, null, null)
            context.socketFactory
        }.getOrNull()
    }

    private fun dir(): File = File(App.getDeviceProtectedContext().filesDir, "osm-roads").apply { mkdirs() }
}
