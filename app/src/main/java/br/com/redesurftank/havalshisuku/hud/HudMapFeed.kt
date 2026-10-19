package br.com.redesurftank.havalshisuku.hud

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger
import br.com.redesurftank.havalshisuku.map.LocalRoad
import br.com.redesurftank.havalshisuku.map.OsmRoadStore
import br.com.redesurftank.havalshisuku.map.OsmRoads
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Streets round the car for the AR layout's map (beta): GPS position and heading, the
 * OpenStreetMap roads of the tiles round it ([OsmRoadStore]), turned into the car's frame
 * (metres, x right, y ahead). Runs only while [setEnabled] is on; its own GPS listener.
 *
 * After the first drive with it (2026-10-19, owner: "not precise, confusing"):
 * - [lines] stay empty until every tile round the car is loaded, so the HUD keeps the arrows
 *   instead of drawing half a street map;
 * - between the 1 Hz fixes the position runs on at the fix's speed ([FRAME_MS]), so the
 *   scene glides instead of jumping ~14 m a second at 50 km/h;
 * - while moving on a mapped street the heading is that street's direction when it agrees
 *   with GPS (GPS bearing lags and wobbles), so the drawn street lines up with the real one.
 */
object HudMapFeed {
    private const val TAG = "HudMapFeed"
    private const val GPS_INTERVAL_MS = 1_000L
    /** Tiles loaded round the car (and so required before drawing). */
    private const val LOAD_RADIUS_M = 900.0
    /** What is kept for drawing: ahead (the HUD's far end at the far map scale) and to the sides. */
    private const val AHEAD_M = 720.0
    private const val SIDE_M = 300.0
    private const val STEP_M = 6.0
    /** Below this speed GPS bearing is noise: keep the last good one. */
    private const val BEARING_MIN_MPS = 2f
    /** A moving fix's heading is trusted this long; then the road under the car gives it. */
    private const val BEARING_FRESH_MS = 20_000L
    /** Redraw period while moving (position extrapolated), and while standing. */
    private const val FRAME_MS = 200L
    private const val IDLE_MS = 5_000L
    /** Don't run on past this since the last fix (lost GPS). */
    private const val EXTRAPOLATE_MAX_MS = 2_000L
    /** Snap the heading to the street when it is this close to GPS's, and the street this near. */
    private const val SNAP_DEG = 20.0
    private const val SNAP_M = 15.0
    /**
     * Standing this long (parked, at home, at work): download the region round the car, so the
     * map works on the road without the servers (owner, 2026-10-19: 8 km).
     */
    private const val PREFETCH_AFTER_STILL_MS = 60_000L
    private const val PREFETCH_RADIUS_M = 8_000.0

    @Volatile var lines: List<LocalRoad> = emptyList()
        private set
    private var onChange: (() -> Unit)? = null
    private var thread: HandlerThread? = null
    private var handler: android.os.Handler? = null

    // Last fix (GPS thread; read by the frame loop on the same thread).
    private var lat = Double.NaN
    private var lon = Double.NaN
    private var speedMps = 0f
    private var fixAtMs = 0L
    private var bearing = 0.0
    /** When [bearing] last came from a moving fix (elapsed ms); 0 = never. */
    private var bearingAtMs = 0L
    private var ready = false
    /** Since when the car has stood still (elapsed ms); 0 = moving. */
    private var stillSinceMs = 0L
    private var prefetchedThisStop = false

    /** True while the map option is on but the area round the car isn't loaded yet. */
    @Volatile var pending: Boolean = false
        private set

    private val frame = object : Runnable {
        override fun run() {
            rebuild()
            handler?.postDelayed(this, if (moving()) FRAME_MS else IDLE_MS)
        }
    }

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onLocation(location)
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    /** Main thread. [changed] runs (any thread) when the lines change. */
    @SuppressLint("MissingPermission")
    fun setEnabled(enabled: Boolean, changed: () -> Unit) {
        onChange = changed
        if (enabled == (thread != null)) return
        val lm = App.getDeviceProtectedContext().getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        if (enabled) {
            val t = HandlerThread("HudMapFeed").also { it.start() }
            thread = t
            val h = android.os.Handler(t.looper)
            handler = h
            h.post {
                try {
                    lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onLocation(it) }
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_INTERVAL_MS, 0f, listener, t.looper)
                    Log.w(TAG, "map on")
                } catch (e: SecurityException) {
                    Log.w(TAG, "no location permission: ${e.message}")
                }
                h.postDelayed(frame, FRAME_MS)
            }
        } else {
            runCatching { lm.removeUpdates(listener) }
            handler?.removeCallbacksAndMessages(null)
            handler = null
            thread?.quitSafely()
            thread = null
            lines = emptyList()
            ready = false
            pending = false
            Log.w(TAG, "map off")
        }
    }

    private fun moving() = speedMps >= BEARING_MIN_MPS && SystemClock.elapsedRealtime() - fixAtMs < EXTRAPOLATE_MAX_MS

    private fun onLocation(location: Location) {
        lat = location.latitude
        lon = location.longitude
        speedMps = if (location.hasSpeed()) location.speed else 0f
        fixAtMs = SystemClock.elapsedRealtime()
        if (speedMps >= BEARING_MIN_MPS) {
            stillSinceMs = 0L
            prefetchedThisStop = false
        } else if (stillSinceMs == 0L) {
            stillSinceMs = fixAtMs
        }
        if (location.hasBearing() && location.hasSpeed() && location.speed >= BEARING_MIN_MPS) {
            bearing = location.bearing.toDouble()
            bearingAtMs = fixAtMs
        }
        rebuild()
    }

    private fun rebuild() {
        if (lat.isNaN()) return
        val now = SystemClock.elapsedRealtime()
        if (!prefetchedThisStop && stillSinceMs != 0L && now - stillSinceMs >= PREFETCH_AFTER_STILL_MS) {
            prefetchedThisStop = true
            OsmRoadStore.prefetch(lat, lon, PREFETCH_RADIUS_M)
        }
        val near = OsmRoadStore.roadsNear(lat, lon, LOAD_RADIUS_M) { handler?.post { rebuild() } }
        if (near.complete != ready) {
            ready = near.complete
            ClusterPersistentEventLogger.log("hud_map_ready", mapOf("ready" to ready, "roads" to near.roads.size))
        }
        if (!near.complete) {
            // Part of the area is missing: a note on the HUD, not half a street map.
            val changed = lines.isNotEmpty() || !pending
            lines = emptyList()
            pending = true
            if (changed) onChange?.invoke()
            return
        }
        pending = false
        // Where the car is now: the last fix run on at its speed along its heading.
        var heading = bearing
        val fresh = bearingAtMs != 0L && now - bearingAtMs <= BEARING_FRESH_MS
        if (!fresh) {
            // Standing still (no heading for a while): face along the road the car is on.
            heading = OsmRoads.roadBearing(near.roads, lat, lon, bearing) ?: bearing
        }
        var pLat = lat
        var pLon = lon
        if (moving()) {
            val d = speedMps * (now - fixAtMs).coerceAtMost(EXTRAPOLATE_MAX_MS) / 1000.0
            val rad = Math.toRadians(heading)
            pLat += d * cos(rad) / 110_540.0
            pLon += d * sin(rad) / (111_320.0 * cos(Math.toRadians(lat)))
            // On a mapped street that agrees with GPS: its direction, steadier than GPS's.
            OsmRoads.roadBearing(near.roads, pLat, pLon, heading, SNAP_M)?.let { street ->
                if (abs(((street - heading) % 360 + 540) % 360 - 180) <= SNAP_DEG) heading = street
            }
        }
        lines = OsmRoads.toLocal(near.roads, pLat, pLon, heading, AHEAD_M, SIDE_M, STEP_M)
        onChange?.invoke()
    }
}
