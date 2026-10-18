package br.com.redesurftank.havalshisuku.hud

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.HandlerThread
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.map.OsmRoadStore
import br.com.redesurftank.havalshisuku.map.OsmRoads

/**
 * Streets round the car for the AR layout's map (beta): GPS position and heading, the
 * OpenStreetMap roads of the tiles round it ([OsmRoadStore]), turned into the car's frame
 * (metres, x right, y ahead). Runs only while [setEnabled] is on; its own GPS listener, 1 Hz.
 */
object HudMapFeed {
    private const val TAG = "HudMapFeed"
    private const val GPS_INTERVAL_MS = 1_000L
    /** Tiles loaded round the car. */
    private const val LOAD_RADIUS_M = 700.0
    /** What is kept for drawing: ahead (the HUD's far end at the far map scale) and to the sides. */
    private const val AHEAD_M = 900.0
    private const val SIDE_M = 400.0
    private const val STEP_M = 12.0
    /** Below this speed GPS bearing is noise: keep the last good one. */
    private const val BEARING_MIN_MPS = 2f

    @Volatile var lines: List<FloatArray> = emptyList()
        private set
    private var onChange: (() -> Unit)? = null
    private var thread: HandlerThread? = null
    @Volatile private var lat = Double.NaN
    @Volatile private var lon = Double.NaN
    @Volatile private var bearing = 0.0

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
            try {
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onLocation(it) }
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_INTERVAL_MS, 0f, listener, t.looper)
                Log.w(TAG, "map on")
            } catch (e: SecurityException) {
                Log.w(TAG, "no location permission: ${e.message}")
            }
        } else {
            runCatching { lm.removeUpdates(listener) }
            thread?.quitSafely()
            thread = null
            lines = emptyList()
            Log.w(TAG, "map off")
        }
    }

    private fun onLocation(location: Location) {
        lat = location.latitude
        lon = location.longitude
        if (location.hasBearing() && location.hasSpeed() && location.speed >= BEARING_MIN_MPS) bearing = location.bearing.toDouble()
        rebuild()
    }

    private fun rebuild() {
        if (lat.isNaN()) return
        val roads = OsmRoadStore.roadsNear(lat, lon, LOAD_RADIUS_M) { rebuild() }
        lines = OsmRoads.toLocal(roads, lat, lon, bearing, AHEAD_M, SIDE_M, STEP_M)
        onChange?.invoke()
    }
}
