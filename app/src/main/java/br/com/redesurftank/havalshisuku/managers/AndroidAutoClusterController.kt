package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.projectors.AaClusterVideoHost
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns AA session telemetry, TBT publish, and the D3 CLUSTER Surface attach.
 *
 * Handshake constraint: CLUSTER video must be advertised before the AAP session
 * starts. Theme [setAaClusterMapEnabled] arrives after `session=active`, so the
 * Service patch advertises CLUSTER whenever it is mounted. This controller only
 * attaches or tears down the Impulse Surface. On disconnect the Surface is
 * released and the next connect starts over.
 */
object AndroidAutoClusterController {
    private const val TAG = "AaClusterCtrl"

    private val sessionDebouncer = AndroidAutoSessionTelemetry.Debouncer()
    private val directionsPublisher = AndroidAutoNavigationTelemetry.Publisher()
    private val started = AtomicBoolean(false)
    private val clusterRequested = AtomicBoolean(false)
    private val surfaceAttached = AtomicBoolean(false)
    private val clusterAdvertisedThisSession = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionPollRunnable = object : Runnable {
        override fun run() {
            val status = DisplayAppLauncher.readAndroidAutoLinkStatusIfAlreadyBound("AA_SESSION_POLL")
            if (status != null) {
                onLinkStatus(status)
            }
            mainHandler.postDelayed(this, 1_500L)
        }
    }

    @Volatile
    private var lastSessionValue: String = AndroidAutoTelemetryKeys.SESSION_STOPPED

    fun start() {
        if (!started.compareAndSet(false, true)) return
        Log.i(TAG, "Starting Android Auto cluster session monitor")
        publishSession(AndroidAutoTelemetryKeys.SESSION_STOPPED, force = true)
        publishDirections(AndroidAutoNavigationTelemetry.inactive(), SystemClock.elapsedRealtime(), force = true)
        mainHandler.removeCallbacks(sessionPollRunnable)
        mainHandler.post(sessionPollRunnable)
    }

    fun onLinkStatus(status: Int?, nowMs: Long = SystemClock.elapsedRealtime()) {
        val published = sessionDebouncer.onStatus(status, nowMs) ?: return
        publishSession(published, force = false)
    }

    fun currentSessionValue(): String = lastSessionValue

    fun isSessionActive(): Boolean {
        return lastSessionValue == AndroidAutoTelemetryKeys.SESSION_ACTIVE
    }

    fun isClusterRequested(): Boolean = clusterRequested.get()

    fun isSurfaceAttached(): Boolean = surfaceAttached.get()

    /**
     * Theme / external command. Does not persist across disconnects: a new
     * session starts over and the theme must ask again.
     */
    fun setClusterMapEnabled(enabled: Boolean, source: String) {
        Log.w(TAG, "setClusterMapEnabled enabled=$enabled source=$source session=$lastSessionValue")
        clusterRequested.set(enabled)
        if (!enabled) {
            detachSurface("disabled_by_$source")
            return
        }
        if (!isSessionActive()) {
            Log.i(TAG, "CLUSTER map requested with no AA session; will attach after session=active")
            return
        }
        attachIfSessionAllows(source)
    }

    fun onNavigationUpdate(update: AndroidAutoNavigationTelemetry.Directions, nowMs: Long = SystemClock.elapsedRealtime()) {
        publishDirections(update, nowMs, force = false)
        mainHandler.removeCallbacks(flushDirectionsRunnable)
        mainHandler.postDelayed(flushDirectionsRunnable, AndroidAutoNavigationTelemetry.THROTTLE_MS)
    }

    fun snapshotDirectionsJson(): String = directionsPublisher.lastJson()

    fun peekSurface(): Surface? = AaClusterVideoHost.peekSurface()

    private val flushDirectionsRunnable = Runnable {
        val json = directionsPublisher.flushPending(SystemClock.elapsedRealtime()) ?: return@Runnable
        ServiceManager.getInstance().dispatchTelemetryOnly(AndroidAutoTelemetryKeys.DIRECTIONS, json)
    }

    private fun publishSession(value: String, force: Boolean) {
        if (!force && value == lastSessionValue) return
        lastSessionValue = value
        ServiceManager.getInstance().dispatchTelemetryOnly(AndroidAutoTelemetryKeys.SESSION, value)
        if (value == AndroidAutoTelemetryKeys.SESSION_STOPPED) {
            onSessionStopped()
        } else if (clusterRequested.get()) {
            attachIfSessionAllows("session_active")
        }
    }

    private fun onSessionStopped() {
        clusterRequested.set(false)
        clusterAdvertisedThisSession.set(false)
        detachSurface("session_stopped")
        directionsPublisher.reset()
        ServiceManager.getInstance().dispatchTelemetryOnly(
            AndroidAutoTelemetryKeys.DIRECTIONS,
            AndroidAutoNavigationTelemetry.inactive().toJson()
        )
        sessionDebouncer.reset()
        lastSessionValue = AndroidAutoTelemetryKeys.SESSION_STOPPED
    }

    private fun publishDirections(
        update: AndroidAutoNavigationTelemetry.Directions,
        nowMs: Long,
        force: Boolean
    ) {
        val json = if (force) {
            directionsPublisher.reset()
            directionsPublisher.onUpdate(update, nowMs)
                ?: update.toJson()
        } else {
            directionsPublisher.onUpdate(update, nowMs)
        } ?: return
        ServiceManager.getInstance().dispatchTelemetryOnly(AndroidAutoTelemetryKeys.DIRECTIONS, json)
    }

    private fun attachIfSessionAllows(source: String) {
        // Mid-session enable without a CLUSTER handshake cannot invent video.
        // Service patch advertises CLUSTER on the next session start; a debug
        // Surface is still shown so the theme hole/mask path can be validated.
        val advertised = clusterAdvertisedThisSession.get() || AndroidAutoPatchManager.isServiceClusterPatchMounted()
        if (!advertised) {
            Log.w(
                TAG,
                "CLUSTER map requested mid-session without advertised CLUSTER ($source). " +
                    "Fail closed for live video; attaching debug Surface only if the Service patch is mounted. " +
                    "Wait for the next AA session."
            )
            if (!AndroidAutoPatchManager.isServiceClusterPatchMounted()) {
                return
            }
        }
        attachSurface(source)
    }

    private fun attachSurface(source: String) {
        if (surfaceAttached.get()) return
        val context: Context = App.getContext()
        mainHandler.post {
            val attached = AaClusterVideoHost.show(context)
            surfaceAttached.set(attached)
            Log.w(TAG, "CLUSTER Surface attached=$attached source=$source")
            notifyHostProjectionFlag(attached)
        }
    }

    private fun detachSurface(reason: String) {
        if (!surfaceAttached.getAndSet(false) && !AaClusterVideoHost.isShown()) {
            notifyHostProjectionFlag(false)
            return
        }
        mainHandler.post {
            AaClusterVideoHost.hide()
            Log.w(TAG, "CLUSTER Surface detached reason=$reason")
            notifyHostProjectionFlag(false)
        }
    }

    private fun notifyHostProjectionFlag(enabled: Boolean) {
        ServiceManager.getInstance().dispatchServiceManagerEvent(
            br.com.redesurftank.havalshisuku.models.ServiceManagerEventType.AA_CLUSTER_SURFACE,
            enabled
        )
    }

    internal fun markClusterAdvertisedForTest() {
        clusterAdvertisedThisSession.set(true)
    }
}
