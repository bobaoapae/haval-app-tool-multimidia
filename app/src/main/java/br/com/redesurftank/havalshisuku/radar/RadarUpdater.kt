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
 * Refreshes the radar data straight from the sources at most once a day: on startup (and when
 * the feature is switched on) it checks the last successful update and, past [COOLDOWN_MS],
 * downloads OSM again and ANTT only when ANTT published a new file. Failures keep the current
 * data and retry a few times this boot ([RETRY_DELAYS_MS]); the cooldown starts on success.
 */
object RadarUpdater {
    private const val TAG = "Radar"
    const val COOLDOWN_MS = 24 * 60 * 60_000L
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

    /** Listeners (the settings screen) hear when an update starts or ends. Any thread. */
    var onStateChanged: (() -> Unit)? = null

    /** Starts an update if the cooldown passed (or [force]). Any thread. */
    fun maybeUpdate(context: Context, force: Boolean) {
        val app = context.applicationContext
        handler.post {
            if (running) return@post
            val last = RadarSettings.prefs().getLong(SharedPreferencesKeys.RADAR_LAST_UPDATE_MS.key, 0L)
            if (!force && System.currentTimeMillis() - last < COOLDOWN_MS) {
                Log.w(TAG, "update skipped: last success ${(System.currentTimeMillis() - last) / 60_000} min ago")
                return@post
            }
            handler.removeCallbacksAndMessages(null)
            attempt = 0
            networkPolls = 0
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
            update(context)
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

    /** One full attempt. True when OSM was refreshed; ANTT is best effort (it changes monthly). */
    private fun update(context: Context): Boolean {
        val started = System.currentTimeMillis()
        val downloader = RadarDownloader("Impulse/${BuildConfig.VERSION_NAME} (radar alerts; Android)", modernTls())
        val prefs = RadarSettings.prefs()
        val errors = ArrayList<String>()

        val osm = downloader.fetchOsm()
        osm.value?.let { RadarRepository.write(context, RadarFile.Contents(RadarCamera.Source.OSM, now(), it)) }
        osm.error?.let { errors.add(it) }

        var anttCount = -1
        val resource = downloader.fetchAnttResource()
        resource.error?.let { errors.add(it) }
        resource.value?.let { r ->
            val have = prefs.getString(SharedPreferencesKeys.RADAR_ANTT_RESOURCE.key, null)
            if (r.id != have || !RadarRepository.fileFor(context, RadarCamera.Source.ANTT).isFile) {
                val antt = downloader.fetchAntt(r)
                antt.value?.let {
                    RadarRepository.write(context, RadarFile.Contents(RadarCamera.Source.ANTT, r.modified, it))
                    prefs.edit().putString(SharedPreferencesKeys.RADAR_ANTT_RESOURCE.key, r.id).apply()
                    anttCount = it.size
                }
                antt.error?.let { errors.add(it) }
            }
        }

        if (osm.value != null || anttCount >= 0) RadarRepository.load(context)
        val ok = osm.value != null
        val edit = prefs.edit()
        if (ok) edit.putLong(SharedPreferencesKeys.RADAR_LAST_UPDATE_MS.key, System.currentTimeMillis())
        edit.putString(SharedPreferencesKeys.RADAR_LAST_ERROR.key, errors.joinToString(" | ")).apply()
        val tookS = (System.currentTimeMillis() - started) / 1000
        Log.w(TAG, "update ok=$ok osm=${osm.value?.size} antt=$anttCount took=${tookS}s errors=$errors")
        ClusterPersistentEventLogger.log(
            "radar_update",
            mapOf("ok" to ok, "osm" to (osm.value?.size ?: -1), "antt" to anttCount, "s" to tookS, "err" to errors.joinToString(" | ").take(200))
        )
        return ok
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
