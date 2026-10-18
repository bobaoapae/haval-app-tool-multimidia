package br.com.redesurftank.havalshisuku.radar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger
import br.com.redesurftank.havalshisuku.hud.HudOverlayController
import br.com.redesurftank.havalshisuku.hud.HudOverlayModel
import br.com.redesurftank.havalshisuku.listeners.IDataChanged
import br.com.redesurftank.havalshisuku.managers.PowerFlowMapper
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.CarConstants
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Radar alert runtime: GPS fixes from the head unit's own receiver (1 Hz, with speed and
 * course) go through [RadarAlertEngine]; the result is published on [KEY] for the cluster
 * themes, handed to the HUD overlay and turned into sounds. Everything runs on one thread.
 *
 * Started by ServiceManager once the car services are up; switched on and off from the
 * settings ([applyConfig]). Off by default: nothing listens to the GPS until the owner enables it.
 */
object RadarManager {
    private const val TAG = "Radar"

    /** Theme key (contract v1.0, additive). See docs/architecture/themes-contract-v1.md. */
    const val KEY = "app.navigation.radar"
    const val INACTIVE_JSON = """{"v":1,"active":false}"""

    private const val GPS_INTERVAL_MS = 1_000L
    private val KEY_SPEED = CarConstants.CAR_BASIC_VEHICLE_SPEED.value

    private val thread = HandlerThread("Radar").apply { start() }
    private val handler = Handler(thread.looper)
    private val engine = RadarAlertEngine()

    @Volatile private var config: RadarConfig = RadarConfig()
    @Volatile private var carSpeedKmh: Double? = null
    @Volatile private var lastJson: String = INACTIVE_JSON
    private var started = false
    private var tracking = false
    private var lastAlert: RadarAlertEngine.Alert? = null

    private val context: Context get() = App.getDeviceProtectedContext()

    private val speedListener = IDataChanged { key, value ->
        if (key == KEY_SPEED) carSpeedKmh = PowerFlowMapper.parseNumber(value).takeIf { it.isFinite() && it in 0.0..300.0 }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onLocation(location)
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    /** From ServiceManager once the car services are up. Any thread. */
    fun start() {
        handler.post {
            if (started) return@post
            started = true
            RadarRepository.load(context)
            apply(RadarSettings.load())
        }
    }

    /** Settings changed (already saved). Any thread. */
    fun applyConfig(newConfig: RadarConfig) {
        handler.post { apply(newConfig) }
    }

    /** Current theme JSON, for a theme subscribing after the last change. */
    fun snapshotJson(): String = lastJson

    private fun apply(newConfig: RadarConfig) {
        val wasEnabled = config.enabled && tracking
        config = newConfig
        HudOverlayController.setRadarHudWanted(newConfig.enabled && newConfig.showOnHud)
        if (newConfig.enabled && !wasEnabled) {
            startTracking()
            RadarUpdater.maybeUpdate(context, force = false)
        } else if (!newConfig.enabled && wasEnabled) {
            stopTracking()
        }
        // Re-publish so a toggled HUD / theme output takes effect at once.
        publish(if (newConfig.enabled) lastAlert else null)
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        if (tracking) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "no location permission; radar alert idle")
            ClusterPersistentEventLogger.log("radar_no_location_permission")
            return
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_INTERVAL_MS, 0f, locationListener, thread.looper)
        } catch (t: Throwable) {
            Log.w(TAG, "GPS updates refused: ${t.message}")
            return
        }
        ServiceManager.getInstance().addDataChangedListener(speedListener)
        speedListener.onDataChanged(KEY_SPEED, ServiceManager.getInstance().getData(KEY_SPEED))
        engine.reset()
        tracking = true
        Log.w(TAG, "tracking on (${RadarRepository.status?.total ?: 0} cameras)")
    }

    private fun stopTracking() {
        if (!tracking) return
        (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(locationListener)
        ServiceManager.getInstance().removeDataChangedListener(speedListener)
        engine.reset()
        tracking = false
        lastAlert = null
        Log.w(TAG, "tracking off")
    }

    private fun onLocation(location: Location) {
        if (demoRunning) return
        val fix = RadarAlertEngine.Fix(
            lat = location.latitude,
            lon = location.longitude,
            bearing = if (location.hasBearing()) location.bearing else null,
            speedMps = if (location.hasSpeed()) location.speed else null,
            timeMs = SystemClock.elapsedRealtime()
        )
        handle(engine.onFix(fix, carSpeedKmh, config.engineConfig, RadarRepository.index))
    }

    private fun handle(update: RadarAlertEngine.Update, demo: Boolean = false) {
        val alert = update.alert
        update.event?.let { event ->
            playFor(event, alert)
            ClusterPersistentEventLogger.log(
                "radar_alert",
                mapOf("event" to event.name, "m" to alert?.distanceM, "limit" to alert?.limitKmh, "over" to alert?.over, "src" to alert?.camera?.source?.code)
            )
        }
        lastAlert = alert
        // The demo shows on both outputs, even with the feature off, so it can be tried first.
        if (demo) publish(alert, showHud = true, showTheme = true) else publish(alert)
    }

    private fun playFor(event: RadarAlertEngine.Event, alert: RadarAlertEngine.Alert?) {
        val c = config
        val over = alert?.over == true
        if (c.soundOnlyOverLimit && !over) return
        when (event) {
            RadarAlertEngine.Event.APPROACH -> RadarSoundPlayer.play(context, c.approachSound, c.approachVolume)
            RadarAlertEngine.Event.NEAR, RadarAlertEngine.Event.OVERSPEED -> RadarSoundPlayer.play(context, c.nearSound, c.nearVolume)
        }
    }

    /** Theme key and HUD, each only when the owner shows the alert there. */
    private fun publish(
        alert: RadarAlertEngine.Alert?,
        showHud: Boolean = config.showOnHud,
        showTheme: Boolean = config.showOnTheme
    ) {
        HudOverlayController.onRadar(
            if (alert != null && showHud) HudOverlayModel.Radar(alert.distanceM, alert.limitKmh, alert.over) else null
        )
        val json = if (alert != null && showTheme) toJson(alert) else INACTIVE_JSON
        if (json == lastJson) return
        lastJson = json
        ServiceManager.getInstance().dispatchTelemetryOnly(KEY, json)
    }

    /** Distance in 10 m steps: the theme redraws on a visible change only. */
    fun toJson(alert: RadarAlertEngine.Alert): String = JSONObject().apply {
        put("v", 1)
        put("active", true)
        put("distance_m", (alert.distanceM / 10.0).roundToInt() * 10)
        put("limit_kmh", alert.limitKmh ?: JSONObject.NULL)
        put("over", alert.over)
        put("phase", alert.phase.name.lowercase())
    }.toString()

    // ---- demo: a camera 700 m ahead, driven past at 54 km/h, to check HUD/theme/sounds parked ----

    private const val DEMO_SPEED_MPS = 15.0
    private const val DEMO_LAT = -22.9
    private const val DEMO_LON = -43.1
    @Volatile private var demoRunning = false
    private var demoEngine: RadarAlertEngine? = null
    private var demoIndex: RadarIndex? = null
    private var demoMeters = 0.0
    private var demoOver = false
    private var demoTicks = 0

    /** Settings "Simular" button / debug intent. [over] drives at 72 km/h past a 60 camera. Any thread. */
    fun demo(over: Boolean) {
        handler.post {
            handler.removeCallbacks(demoTick)
            demoEngine = RadarAlertEngine()
            demoIndex = RadarIndex(listOf(RadarCamera(DEMO_LAT + 700.0 / 111_320.0, DEMO_LON, 60, null, RadarCamera.Source.OSM)))
            demoMeters = 0.0
            demoOver = over
            demoTicks = 0
            demoRunning = true
            HudOverlayController.setRadarHudWanted(true)
            demoTick.run()
        }
    }

    private val demoTick: Runnable = object : Runnable {
        override fun run() {
            val e = demoEngine ?: return
            val fix = RadarAlertEngine.Fix(DEMO_LAT + demoMeters / 111_320.0, DEMO_LON, 0f, DEMO_SPEED_MPS.toFloat(), demoTicks * 1_000L)
            handle(e.onFix(fix, if (demoOver) 72.0 else 50.0, config.engineConfig, demoIndex), demo = true)
            demoMeters += DEMO_SPEED_MPS
            demoTicks++
            if (demoMeters <= 820.0 && demoTicks <= 70) {
                handler.postDelayed(this, 1_000L)
                return
            }
            demoRunning = false
            demoEngine = null
            lastAlert = null
            publish(null, showHud = true, showTheme = true)
            HudOverlayController.setRadarHudWanted(config.enabled && config.showOnHud)
        }
    }
}
