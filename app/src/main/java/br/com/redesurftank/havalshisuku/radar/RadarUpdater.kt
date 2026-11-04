package br.com.redesurftank.havalshisuku.radar

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import br.com.redesurftank.havalshisuku.BuildConfig
import br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Refreshes each source on its own successful-check/write clock. Partial failures keep valid
 * caches and retry only overdue sources at the existing bounded boot retry intervals.
 */
object RadarUpdater {
    private const val TAG = "Radar"
    const val COOLDOWN_MS = RadarRefresh.DAY_MS
    /** Wait for the car to get online (phone hotspot) before the first try. */
    private const val NETWORK_POLL_MS = 60_000L
    private const val NETWORK_POLL_MAX = 30
    private val RETRY_DELAYS_MS = longArrayOf(5 * 60_000L, 15 * 60_000L, 60 * 60_000L)

    private val thread = HandlerThread("RadarUpdater").apply { start() }
    private val handler = Handler(thread.looper)

    @Volatile
    var running = false
        private set
    private var attempt = 0
    private var networkPolls = 0
    private var retryOsm = false
    private var retryAntt = false

    /** Listeners (the settings screen) hear when an update starts or ends. Any thread. */
    var onStateChanged: (() -> Unit)? = null

    /** Starts an update if the cooldown passed (or [force]). Any thread. */
    fun maybeUpdate(force: Boolean) {
        // Always device-protected storage, where RadarRepository reads at startup (and the only
        // storage there before the user unlocks). `context.applicationContext` would turn a DE
        // context back into credential storage: on 2026-10-17 the download landed in
        // /data/data/.../files/radar and every restart fell back to the APK copy.
        val app = br.com.redesurftank.App.getDeviceProtectedContext()
        handler.post {
            if (running) return@post
            if (!force && !refresh(app).needed(readState())) {
                Log.w(TAG, "update skipped: all sources fresh")
                return@post
            }
            handler.removeCallbacksAndMessages(null)
            attempt = 0
            networkPolls = 0
            retryOsm = false
            retryAntt = false
            run(app, force)
        }
    }

    private fun run(context: Context, force: Boolean) {
        if (!force && !RadarSettings.load().enabled) return
        if (!isOnline(context)) {
            if (++networkPolls <= NETWORK_POLL_MAX) handler.postDelayed({ run(context, force) }, NETWORK_POLL_MS)
            else saveError("Sem internet")
            return
        }
        running = true
        onStateChanged?.invoke()
        val ok = try {
            update(context, force && attempt == 0)
        } catch (t: Throwable) {
            Log.w(TAG, "update crashed", t)
            saveError(t.message ?: t.javaClass.simpleName)
            false
        } finally {
            running = false
        }
        onStateChanged?.invoke()
        if (!ok && attempt < RETRY_DELAYS_MS.size) {
            handler.postDelayed({ run(context, force) }, RETRY_DELAYS_MS[attempt++])
        }
    }

    /** A forced user request refreshes cameras once; retries still skip successful sources. */
    private fun update(context: Context, force: Boolean): Boolean {
        val started = System.currentTimeMillis()
        val result = refresh(context).run(readState(), force, retryOsm, retryAntt, ::now)
        retryOsm = result.osm == RadarRefresh.Outcome.FAILED
        retryAntt = result.antt == RadarRefresh.Outcome.FAILED
        val edit = RadarSettings.prefs().edit()
        if (result.ok) edit.putLong(SharedPreferencesKeys.RADAR_LAST_UPDATE_MS.key, System.currentTimeMillis())
        edit.putString(SharedPreferencesKeys.RADAR_LAST_ERROR.key, result.errors.joinToString(" | ")).apply()
        val tookS = (System.currentTimeMillis() - started) / 1000
        Log.w(TAG, "update ok=${result.ok} osm=${result.osm} antt=${result.antt} roads=${result.roads} took=${tookS}s errors=${result.errors}")
        ClusterPersistentEventLogger.log(
            "radar_update",
            mapOf(
                "ok" to result.ok, "osm" to (RadarRepository.status?.osm ?: -1),
                "antt" to (RadarRepository.status?.antt ?: -1), "with_road" to (RadarRepository.status?.withRoad ?: -1),
                "osm_state" to result.osm.name,
                "antt_state" to result.antt.name, "roads_state" to result.roads.name,
                "s" to tookS, "err" to result.errors.joinToString(" | ").take(200)
            )
        )
        return result.ok
    }

    private fun readState(): RadarRefresh.State {
        val prefs = RadarSettings.prefs()
        return RadarRefresh.State(
            // The legacy timestamp represented OSM success only. Never migrate it to ANTT.
            osmSuccess = prefs.getLong(SharedPreferencesKeys.RADAR_OSM_LAST_UPDATE_MS.key,
                prefs.getLong(SharedPreferencesKeys.RADAR_LAST_UPDATE_MS.key, 0L)),
            anttChecked = prefs.getLong(SharedPreferencesKeys.RADAR_ANTT_LAST_CHECK_MS.key, 0L),
            anttDownloaded = prefs.getLong(SharedPreferencesKeys.RADAR_ANTT_LAST_DOWNLOAD_MS.key, 0L),
            anttResource = prefs.getString(SharedPreferencesKeys.RADAR_ANTT_VERSION.key, "").orEmpty(),
            roadsSuccess = prefs.getLong(SharedPreferencesKeys.RADAR_ROADS_LAST_UPDATE_MS.key, 0L),
            roadsGeometry = prefs.getString(SharedPreferencesKeys.RADAR_ROADS_GEOMETRY.key, "").orEmpty()
        )
    }

    private fun saveState(state: RadarRefresh.State) {
        RadarSettings.prefs().edit()
            .putLong(SharedPreferencesKeys.RADAR_OSM_LAST_UPDATE_MS.key, state.osmSuccess)
            .putLong(SharedPreferencesKeys.RADAR_ANTT_LAST_CHECK_MS.key, state.anttChecked)
            .putLong(SharedPreferencesKeys.RADAR_ANTT_LAST_DOWNLOAD_MS.key, state.anttDownloaded)
            .putString(SharedPreferencesKeys.RADAR_ANTT_VERSION.key, state.anttResource)
            .putLong(SharedPreferencesKeys.RADAR_ROADS_LAST_UPDATE_MS.key, state.roadsSuccess)
            .putString(SharedPreferencesKeys.RADAR_ROADS_GEOMETRY.key, state.roadsGeometry)
            .apply()
    }

    private fun refresh(context: Context): RadarRefresh {
        val downloader = RadarDownloader("Impulse/${BuildConfig.VERSION_NAME} (radar alerts; Android)", modernTls())
        val source = object : RadarRefresh.Source {
            override fun osm() = downloader.fetchOsm()
            override fun anttResource() = downloader.fetchAnttResource()
            override fun antt(resource: RadarSources.AnttResource) = downloader.fetchAntt(resource)
            override fun roads(cameras: List<RadarCamera>, extra: List<RadarCamera>) =
                downloader.fetchRoads(cameras, extra, java.io.File(context.cacheDir, "radar-roads.json"))
        }
        val cache = object : RadarRefresh.Cache {
            override fun hasSource(source: RadarCamera.Source) = RadarRepository.hasDownloadedSource(context, source)
            override fun save(contents: RadarFile.Contents) = RadarRepository.write(context, contents)
            override fun saveRoads(contents: RadarRoads.Contents) = RadarRepository.writeRoads(context, contents)
            override fun reload() = RadarRepository.load(context)
            override val cameras get() = RadarRepository.index?.cameras.orEmpty()
            override val osmCameras get() = RadarRepository.osmCameras
            override val anttCameras get() = RadarRepository.anttCameras
            override val roadsUpdated get() = parseIso(RadarRepository.status?.roadsUpdated.orEmpty())
        }
        return RadarRefresh(source, cache, System::currentTimeMillis, ::saveState)
    }

    /**
     * TLS 1.3 through a private Conscrypt instance, used only by these downloads (the app's
     * other connections keep the platform stack). Null if it can't load: then TLS 1.2 servers
     * (ANTT, the mirrors) still work.
     */
    private fun modernTls(): javax.net.ssl.SSLSocketFactory? = runCatching {
        val context = javax.net.ssl.SSLContext.getInstance("TLS", org.conscrypt.Conscrypt.newProvider())
        context.init(null, null, null)
        context.socketFactory
    }.onFailure { Log.w(TAG, "Conscrypt unavailable: ${it.message}") }.getOrNull()

    private fun parseIso(value: String): Long = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(value)!!.time
    }.getOrDefault(0L)

    private fun saveError(message: String) {
        RadarSettings.prefs().edit().putString(SharedPreferencesKeys.RADAR_LAST_ERROR.key, message).apply()
    }

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun now(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date())
}
