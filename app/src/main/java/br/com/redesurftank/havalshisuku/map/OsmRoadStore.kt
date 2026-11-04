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
    /**
     * Public Overpass servers, tried in turn, each with its own read timeout. 2026-10-19:
     * private.coffee answered a 3x3-tile box in 2 s. 2026-10-24 drive (owner: no map on the
     * highway): private.coffee 0 of 143 (timeouts), kumi 0 of 68, overpass-api.de 75 answers
     * (~30-55 s), 42 x 502; from the PC the same day overpass-api.de took 53 s, the others
     * timed out. So overpass-api.de goes first, and a server that times out is skipped for
     * [DEAD_MS] instead of costing 30 s on every request.
     */
    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter" to 100_000,
        "https://overpass.private.coffee/api/interpreter" to 30_000,
        "https://overpass.kumi.systems/api/interpreter" to 30_000
    )
    /** A server that timed out or dropped the connection is skipped this long (not on a 5xx: that is load). */
    private const val DEAD_MS = 15 * 60_000L
    private val deadUntil = ConcurrentHashMap<String, Long>()
    /** Tiles a background download is fetching now: the view waits for them instead of asking again. */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private const val TTL_MS = 30L * 24 * 3600 * 1000
    private const val RETRY_MS = 30_000L
    /** A server that answered 429 (too many requests) is skipped this long. */
    private const val RATE_LIMIT_MS = 5 * 60_000L
    private const val CONNECT_TIMEOUT_MS = 15_000
    private val limitedUntil = ConcurrentHashMap<String, Long>()
    /** Per server, what the last request got (code or error), for the durable trace. */
    @Volatile private var lastOutcome = ""

    /** The roads round a point, whether every tile there is loaded (else the view isn't whole), and how many are. */
    class Near(val roads: List<OsmRoad>, val complete: Boolean, val loaded: Int = 0, val total: Int = 0)

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "OsmRoadStore") }
    /** The region download runs apart, so it never holds up the tiles the view needs now. */
    private val background = Executors.newSingleThreadExecutor { r -> Thread(r, "OsmRoadPrefetch") }
    @Volatile private var prefetching = false
    /** Pause between region blocks: the public servers are shared. */
    private const val PREFETCH_PAUSE_MS = 3_000L
    private val tiles = ConcurrentHashMap<String, List<OsmRoad>>()

    // ---- retention (owner, 2026-10-19): by last use, not by area ----
    /** A tile not used (shown or downloaded) for this long is deleted. */
    private const val UNUSED_MS = 30L * 24 * 3600 * 1000
    /** Past this, the least recently used tiles go first. */
    private const val MAX_BYTES = 100L * 1024 * 1024
    /** When each tile was last used (epoch ms), kept in [USE_FILE]. */
    private val lastUse = ConcurrentHashMap<String, Long>()
    private const val USE_FILE = "last-use.txt"
    @Volatile private var useLoaded = false
    @Volatile private var useSavedAtMs = 0L
    private const val USE_SAVE_EVERY_MS = 5 * 60_000L
    @Volatile private var trimmedThisRun = false
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
        val now = System.currentTimeMillis()
        for (tile in OsmRoads.tilesAround(lat, lon, radiusM)) {
            val roads = tiles[tile.key]
            if (roads == null) {
                if (queued.add(tile.key)) missing += tile
                continue
            }
            lastUse[tile.key] = now
            for (r in roads) if (seen.add(r.id)) out += r
        }
        if (now - useSavedAtMs > USE_SAVE_EVERY_MS) {
            useSavedAtMs = now
            background.execute { runCatching { saveUse() } }
        }
        if (missing.isNotEmpty()) io.execute { load(missing, onLoaded) }
        val around = OsmRoads.tilesAround(lat, lon, radiusM)
        val loaded = around.count { tiles.containsKey(it.key) }
        return Near(out, loaded == around.size, loaded, around.size)
    }

    private fun load(wanted: List<OsmRoads.Tile>, onLoaded: () -> Unit) {
        try {
            // Disk first; whatever is left goes out as one box.
            val toFetch = ArrayList<OsmRoads.Tile>()
            for (tile in wanted) {
                val file = file(tile)
                val cached = file.takeIf { it.isFile && System.currentTimeMillis() - it.lastModified() < TTL_MS }?.readText()
                if (cached != null && OsmRoads.cacheOk(cached)) {
                    tiles[tile.key] = OsmRoads.parse(cached)
                } else if (tile.key !in inFlight) {
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
                        lastUse[tile.key] = System.currentTimeMillis()
                    }
                    trace(true, toFetch.size, roads.size, SystemClock.elapsedRealtime() - started)
                } else {
                    trace(false, toFetch.size, 0, SystemClock.elapsedRealtime() - started)
                    // Stale copies on disk are better than nothing while the servers are down.
                    for (tile in toFetch) file(tile).takeIf { it.isFile }?.readText()?.takeIf { OsmRoads.cacheOk(it) }?.let { tiles[tile.key] = OsmRoads.parse(it) }
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

    /**
     * Downloads, in the background, every tile within [radiusM] of the point that isn't on disk
     * (or is older than [TTL_MS]), in 3x3-tile boxes, so the map is there while driving without
     * the servers (they failed for a whole drive on 2026-10-19). Only what is missing goes out;
     * when everything is there it costs a directory check. One region at a time: false when
     * one is still running (this one is not queued).
     */
    fun prefetch(lat: Double, lon: Double, radiusM: Double, oneBox: Boolean = false): Boolean {
        if (prefetching) return false
        prefetching = true
        background.execute {
            try {
                prefetchRegion(lat, lon, radiusM, oneBox)
            } catch (t: Throwable) {
                Log.w(TAG, "prefetch failed: ${t.message}")
            } finally {
                prefetching = false
            }
        }
        return true
    }

    /** [oneBox]: all missing tiles in one request (the small area ahead while driving), else 3x3 blocks. */
    private fun prefetchRegion(lat: Double, lon: Double, radiusM: Double, oneBox: Boolean) {
        trim()
        val now = System.currentTimeMillis()
        val missing = OsmRoads.tilesAround(lat, lon, radiusM).filter { t ->
            val f = file(t)
            !(f.isFile && now - f.lastModified() < TTL_MS && OsmRoads.cacheOk(f.readText()))
        }
        if (missing.isEmpty()) return
        val blocks = if (oneBox) listOf(missing) else missing.groupBy { Math.floorDiv(it.x, 3) to Math.floorDiv(it.y, 3) }.values
        ClusterPersistentEventLogger.log("hud_map_prefetch", mapOf("state" to "start", "tiles" to missing.size, "blocks" to blocks.size, "ahead" to oneBox))
        var done = 0
        var failed = 0
        for (block in blocks) {
            if (System.currentTimeMillis() < retryAfterMs) Thread.sleep((retryAfterMs - System.currentTimeMillis()).coerceIn(0L, RETRY_MS))
            val started = SystemClock.elapsedRealtime()
            block.forEach { inFlight += it.key }
            val json = try { fetch(block) } finally { block.forEach { inFlight -= it.key } }
            if (json == null) {
                failed++
                trace(false, block.size, 0, SystemClock.elapsedRealtime() - started)
                // Several failures in a row: the servers are down; try again at the next stop.
                if (failed >= 3 && done == 0) break
                continue
            }
            val roads = OsmRoads.parse(json)
            for (tile in block) {
                val own = OsmRoads.inTile(roads, tile)
                file(tile).writeText(OsmRoads.toJson(own))
                lastUse[tile.key] = System.currentTimeMillis()
                // The area ahead goes straight into memory (the view may be waiting on it, see
                // [inFlight]); a parked region only refreshes what is already shown.
                if (oneBox || tiles.containsKey(tile.key)) tiles[tile.key] = own
            }
            done++
            trace(true, block.size, roads.size, SystemClock.elapsedRealtime() - started)
            Thread.sleep(PREFETCH_PAUSE_MS)
        }
        ClusterPersistentEventLogger.log("hud_map_prefetch", mapOf("state" to "end", "blocks_ok" to done, "blocks_failed" to failed))
        saveUse()
    }

    /**
     * Deletes tiles unused for [UNUSED_MS] (a tile with no record counts from its file date),
     * then, past [MAX_BYTES], the least recently used ones. Background thread.
     */
    private fun trim() {
        loadUse()
        val files = dir().listFiles { f -> f.name.endsWith(".json") }?.toList().orEmpty()
        val now = System.currentTimeMillis()
        fun used(f: File) = lastUse[f.name.removeSuffix(".json")] ?: f.lastModified()
        var deleted = 0
        val kept = ArrayList<File>()
        for (f in files) {
            if (now - used(f) > UNUSED_MS && f.delete()) {
                lastUse.remove(f.name.removeSuffix(".json")); deleted++
            } else kept += f
        }
        var total = kept.sumOf { it.length() }
        for (f in kept.sortedBy { used(it) }) {
            if (total <= MAX_BYTES) break
            val size = f.length()
            if (f.delete()) {
                total -= size
                lastUse.remove(f.name.removeSuffix(".json"))
                tiles.remove(f.name.removeSuffix(".json"))
                deleted++
            }
        }
        if (deleted > 0 || !trimmedThisRun) {
            ClusterPersistentEventLogger.log("hud_map_cache", mapOf("files" to (files.size - deleted), "deleted" to deleted, "mb" to total / (1024 * 1024)))
        }
        trimmedThisRun = true
        saveUse()
    }

    private fun loadUse() {
        if (useLoaded) return
        useLoaded = true
        runCatching {
            File(dir(), USE_FILE).takeIf { it.isFile }?.readLines()?.forEach { line ->
                val key = line.substringBefore('=')
                line.substringAfter('=').toLongOrNull()?.let { t -> lastUse.merge(key, t) { a, b -> maxOf(a, b) } }
            }
        }
    }

    private fun saveUse() {
        loadUse()
        File(dir(), USE_FILE).writeText(lastUse.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }

    private fun trace(ok: Boolean, tileCount: Int, roads: Int, ms: Long) {
        Log.w(TAG, "fetch ok=$ok tiles=$tileCount roads=$roads ms=$ms servers=$lastOutcome")
        ClusterPersistentEventLogger.log(
            "hud_map_tiles",
            mapOf("ok" to ok, "tiles" to tileCount, "roads" to roads, "ms" to ms, "servers" to lastOutcome)
        )
    }

    /** The box of [tiles] from the first server that answers; null (and a back-off) when none does. */
    private fun fetch(tiles: List<OsmRoads.Tile>): String? {
        val query = OsmRoads.query(
            tiles.minOf { it.south }, tiles.minOf { it.west }, tiles.maxOf { it.north }, tiles.maxOf { it.east }
        )
        val outcomes = ArrayList<String>()
        try {
            val now = System.currentTimeMillis()
            // All skipped (dead or rate-limited): try them anyway rather than give up.
            val skipAny = ENDPOINTS.any { (url, _) -> now >= (limitedUntil[url] ?: 0L) && now >= (deadUntil[url] ?: 0L) }
            for ((url, timeout) in ENDPOINTS) {
                val host = URL(url).host.substringBefore('.').let { if (it == "overpass") URL(url).host.split('.')[1] else it }
                if (skipAny && System.currentTimeMillis() < (limitedUntil[url] ?: 0L)) {
                    outcomes += "$host:limited"
                    continue
                }
                if (skipAny && System.currentTimeMillis() < (deadUntil[url] ?: 0L)) {
                    outcomes += "$host:skipped"
                    continue
                }
                val (json, outcome) = fetch(url, query, timeout)
                outcomes += "$host:$outcome"
                if (outcome == "429") limitedUntil[url] = System.currentTimeMillis() + RATE_LIMIT_MS
                if (outcome in DEAD_OUTCOMES) deadUntil[url] = System.currentTimeMillis() + DEAD_MS
                if (json != null) {
                    deadUntil.remove(url)
                    return json
                }
            }
            retryAfterMs = System.currentTimeMillis() + RETRY_MS
            return null
        } finally {
            lastOutcome = outcomes.joinToString(",")
        }
    }

    /** Outcomes that mean the server is not answering at all (no internet is UnknownHostException: not its fault). */
    private val DEAD_OUTCOMES = setOf("SocketTimeoutException", "SocketException", "ConnectException", "SSLHandshakeException")

    /** The answer and what happened ("200", an HTTP code, or the error's class). */
    private fun fetch(url: String, query: String, timeout: Int): Pair<String?, String> = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        // overpass-api.de takes TLS 1.3 only; Android 9 stops at 1.2 (same fix as the radar).
        if (conn is HttpsURLConnection) tls?.let { conn.sslSocketFactory = it }
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = timeout
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("User-Agent", "Impulse/${BuildConfig.VERSION_NAME} (HUD map; Android)")
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
        val code = conn.responseCode
        if (code != 200) {
            Log.w(TAG, "HTTP $code from ${URL(url).host}")
            null to code.toString()
        } else {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            if (OsmRoads.answerOk(body)) body to "200" else null to "remark"
        }
    } catch (t: Throwable) {
        Log.w(TAG, "${t.message} from ${URL(url).host}")
        null to (t.javaClass.simpleName.ifEmpty { "error" })
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
