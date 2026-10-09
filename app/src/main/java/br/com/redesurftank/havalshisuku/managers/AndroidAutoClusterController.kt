package br.com.redesurftank.havalshisuku.managers

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.api.AaClusterProtocol
import br.com.redesurftank.havalshisuku.projectors.AaClusterVideoHost
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns AA telemetry and demand for the independent D3 CLUSTER output.
 * The exact-profile Service registers CLUSTER before discovery with automatic
 * projection disabled. Readiness means a current authenticated rendered frame,
 * never a mounted APK, a local focus callback or merely an allocated Surface.
 */
object AndroidAutoClusterController {
    private const val TAG = "AaClusterCtrl"

    private val sessionDebouncer = AndroidAutoSessionTelemetry.Debouncer()
    private val directionsPublisher = AndroidAutoNavigationTelemetry.Publisher()
    private val started = AtomicBoolean(false)
    private val clusterRequested = AtomicBoolean(false)
    private val demandEpoch = AtomicLong()
    private val surfaceAttached = AtomicBoolean(false)
    private var deliveredGeneration = -1L // main-thread Surface lifecycle token
    private var deliveredDemandEpoch = -1L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client by lazy {
        AndroidAutoClusterClient(App.getContext()) { state, reason ->
            val live = state == AaClusterProtocol.LIVE && clusterRequested.get() &&
                isSessionActive() && AaClusterVideoHost.peekSurface()?.isValid == true
            surfaceAttached.set(live)
            notifyHostProjectionFlag(live)
            Log.w(TAG, "CLUSTER state=$state live=$live reason=$reason")
        }
    }
    private val sessionPollRunnable = object : Runnable {
        override fun run() {
            val status = DisplayAppLauncher.readAndroidAutoLinkStatusIfAlreadyBound("AA_SESSION_POLL")
            if (status != null) {
                onLinkStatus(status)
            } else if (DisplayAppLauncher.hasRecentAndroidAutoDcmProjectionActiveEvidenceForSession()) {
                // Wireless AA never binds the Autolink command service, so the
                // read above is null for the whole session and this poll could
                // not flip to active no matter how long a route ran. DCM knows,
                // and the stale-cleanup sweep is already asking it every ~10 s.
                onLinkStatus(AndroidAutoSessionTelemetry.LINK_STATUS_ACTIVATED)
            }
            // A null status with no DCM evidence is still left alone rather than
            // published as stopped: that was the behaviour before this fallback
            // and an unreadable link is not the same as a disconnected phone.
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
        if (clusterRequested.getAndSet(enabled) != enabled) demandEpoch.incrementAndGet()
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
        if (clusterRequested.getAndSet(false)) demandEpoch.incrementAndGet()
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
        mainHandler.post { reconcileOutput(source) }
    }

    private fun reconcileOutput(source: String) {
        // Re-read demand on the main thread: an older posted enable must not
        // recreate output after a rapid disable or session disconnect.
        if (!clusterRequested.get() || !isSessionActive()) {
            surfaceAttached.set(false)
            notifyHostProjectionFlag(false)
            if (deliveredGeneration != -1L) client.setOutput(null)
            deliveredGeneration = -1L
            AaClusterVideoHost.hide()
            return
        }
        if (!AaClusterVideoHost.isShown()) {
            surfaceAttached.set(false)
            notifyHostProjectionFlag(false)
            if (!AaClusterVideoHost.show(App.getContext())) {
                Log.w(TAG, "CLUSTER awaits Presentation ($source)")
                return
            }
        }
        AaClusterVideoHost.peekOutput()?.let {
            onSurfaceAvailable(it, AaClusterVideoHost.surfaceGeneration())
        }
    }

    /** The client borrows the owned consumer until an authenticated terminal release. */
    internal fun onSurfaceAvailable(surface: ClusterSurfaceOutput, generation: Long) {
        val epoch = demandEpoch.get()
        if (clusterRequested.get() && isSessionActive() && surface.isAvailable && deliveredGeneration == generation && deliveredDemandEpoch == epoch) return
        surfaceAttached.set(false)
        notifyHostProjectionFlag(false)
        if (clusterRequested.get() && isSessionActive() && surface.isAvailable) {
            deliveredGeneration = generation
            deliveredDemandEpoch = epoch
            client.setOutput(surface)
        } else {
            deliveredGeneration = -1L
            client.setOutput(null)
        }
    }

    internal fun onSurfaceDestroyed() {
        deliveredGeneration = -1L
        surfaceAttached.set(false)
        notifyHostProjectionFlag(false)
        client.setOutput(null)
    }

    internal fun onHostAvailable() {
        mainHandler.post { reconcileOutput("presentation_available") }
    }

    private fun detachSurface(reason: String) {
        surfaceAttached.set(false)
        notifyHostProjectionFlag(false)
        mainHandler.post {
            reconcileOutput(reason)
        }
    }

    private fun notifyHostProjectionFlag(enabled: Boolean) {
        ServiceManager.getInstance().dispatchServiceManagerEvent(
            br.com.redesurftank.havalshisuku.models.ServiceManagerEventType.AA_CLUSTER_SURFACE,
            enabled
        )
    }

}
