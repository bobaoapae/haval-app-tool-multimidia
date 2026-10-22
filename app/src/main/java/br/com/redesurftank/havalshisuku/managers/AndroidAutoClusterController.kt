package br.com.redesurftank.havalshisuku.managers

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.api.AaClusterProtocol
import br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger
import br.com.redesurftank.havalshisuku.models.CarConstants
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
    /** Guidance can drop for a moment while re-routing; don't tear the map down for that. */
    private const val NAVIGATION_HIDE_DEBOUNCE_MS = 5_000L
    private const val STALL_CHECK_MS = 1_000L
    /** Same gap idea as Rafael's manual stop/start of navigation, just shorter. */
    private const val STALL_RESTART_GAP_MS = 1_500L

    private val sessionDebouncer = AndroidAutoSessionTelemetry.Debouncer()
    private val directionsPublisher = AndroidAutoNavigationTelemetry.Publisher()
    private val started = AtomicBoolean(false)
    private val clusterRequested = AtomicBoolean(false)
    private val navigationActive = AtomicBoolean(false)
    private val lastNavigationActiveAtMs = AtomicLong(0L)
    private val sessionStopSuppressed = AtomicBoolean(false)
    /** An app sent to D1/D3 gets the cluster; the map steps aside until it leaves. */
    private val secondaryAppActive = AtomicBoolean(false)
    private val demandEpoch = AtomicLong()
    private val surfaceAttached = AtomicBoolean(false)
    private var deliveredGeneration = -1L // main-thread Surface lifecycle token
    private var deliveredDemandEpoch = -1L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client by lazy {
        AndroidAutoClusterClient(App.getContext()) { state, reason ->
            val live = state == AaClusterProtocol.LIVE && wantsOutput() &&
                AaClusterVideoHost.peekSurface()?.isValid == true
            surfaceAttached.set(live)
            AaClusterVideoHost.setLive(live)
            notifyHostProjectionFlag(live)
            mainHandler.post { onLiveChanged(live) }
            Log.w(TAG, "CLUSTER state=$state live=$live reason=$reason")
        }
    }

    // Stall watchdog, main thread only. See AaClusterStallPolicy.
    private var liveSinceMs = 0L
    private var stallSinceMs = 0L
    private var lastStallRestartMs = 0L
    private val stallWatchdog = object : Runnable {
        override fun run() {
            checkStall()
            if (surfaceAttached.get()) mainHandler.postDelayed(this, STALL_CHECK_MS)
        }
    }
    private val sessionPollRunnable = object : Runnable {
        override fun run() {
            val status = DisplayAppLauncher.readAndroidAutoLinkStatusIfAlreadyBound("AA_SESSION_POLL")
            val dcmEvidenceRecent =
                DisplayAppLauncher.hasRecentAndroidAutoDcmProjectionActiveEvidenceForSession()
            val next = AndroidAutoSessionTelemetry.statusForPoll(
                linkStatus = status,
                dcmEvidenceRecent = dcmEvidenceRecent,
                sessionActive = isSessionActive()
            )
            if (next != null) {
                onLinkStatus(next)
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
        val navigationEvidenceRecent =
            AndroidAutoSessionTelemetry.hasRecentNavigationEvidence(
                lastActiveAtMs = lastNavigationActiveAtMs.get(),
                nowMs = nowMs
            )
        val effectiveStatus = AndroidAutoSessionTelemetry.statusWithNavigationEvidence(
            linkStatus = status,
            navigationEvidenceRecent = navigationEvidenceRecent
        )
        if (effectiveStatus != status && sessionStopSuppressed.compareAndSet(false, true)) {
            val navigationAgeMs = nowMs - lastNavigationActiveAtMs.get()
            Log.w(
                TAG,
                "Ignoring transient AA stopped status while navigation evidence is fresh " +
                    "status=$status navigationAgeMs=$navigationAgeMs"
            )
            ClusterPersistentEventLogger.log(
                "aa_cluster_session_stop_suppressed",
                mapOf("link_status" to status, "navigation_age_ms" to navigationAgeMs)
            )
        } else if (effectiveStatus == status) {
            sessionStopSuppressed.set(false)
        }
        val published = sessionDebouncer.onStatus(effectiveStatus, nowMs) ?: return
        publishSession(published, force = false)
    }

    fun currentSessionValue(): String = lastSessionValue

    fun isSessionActive(): Boolean {
        return lastSessionValue == AndroidAutoTelemetryKeys.SESSION_ACTIVE
    }

    fun isClusterRequested(): Boolean = clusterRequested.get()

    /**
     * The map shows only while the theme asked for it, AA is linked, Maps is guiding and no app
     * is on D1/D3. The map is drawn inside D3's Presentation, above anything on D1/D3, so it
     * would otherwise cover the app.
     */
    private fun wantsOutput(): Boolean =
        clusterRequested.get() && isSessionActive() && navigationActive.get() &&
            !secondaryAppActive.get()

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

    /** D3 projector: whether any app is on D1 or D3 right now. Any thread. */
    fun setSecondaryDisplayAppActive(active: Boolean, source: String) {
        if (secondaryAppActive.getAndSet(active) == active) return
        Log.w(TAG, "CLUSTER secondary-display app active=$active source=$source")
        attachIfSessionAllows(if (active) "secondary_app_arrived" else "secondary_app_left")
    }

    fun onNavigationUpdate(update: AndroidAutoNavigationTelemetry.Directions, nowMs: Long = SystemClock.elapsedRealtime()) {
        onNavigationActive(update.active, nowMs)
        br.com.redesurftank.havalshisuku.hud.HudOverlayController.onDirections(update)
        publishDirections(update, nowMs, force = false)
        mainHandler.removeCallbacks(flushDirectionsRunnable)
        mainHandler.postDelayed(flushDirectionsRunnable, AndroidAutoNavigationTelemetry.THROTTLE_MS)
    }

    fun snapshotDirectionsJson(): String = directionsPublisher.lastJson()

    fun peekSurface(): Surface? = AaClusterVideoHost.peekSurface()

    private val navigationEndedRunnable = Runnable {
        if (navigationActive.getAndSet(false)) {
            Log.w(TAG, "CLUSTER navigation ended; hiding map")
            attachIfSessionAllows("navigation_ended")
        }
    }

    private fun onNavigationActive(active: Boolean, nowMs: Long) {
        if (active) {
            lastNavigationActiveAtMs.set(nowMs)
            mainHandler.removeCallbacks(navigationEndedRunnable)
            if (!navigationActive.getAndSet(true)) {
                Log.w(TAG, "CLUSTER navigation started")
                if (clusterRequested.get()) attachIfSessionAllows("navigation_started")
            }
        } else if (navigationActive.get()) {
            mainHandler.removeCallbacks(navigationEndedRunnable)
            mainHandler.postDelayed(navigationEndedRunnable, NAVIGATION_HIDE_DEBOUNCE_MS)
        }
    }

    private val flushDirectionsRunnable = Runnable {
        val json = directionsPublisher.flushPending(SystemClock.elapsedRealtime()) ?: return@Runnable
        ServiceManager.getInstance().dispatchTelemetryOnly(AndroidAutoTelemetryKeys.DIRECTIONS, json)
    }

    private fun publishSession(value: String, force: Boolean) {
        if (!force && value == lastSessionValue) return
        lastSessionValue = value
        br.com.redesurftank.havalshisuku.models.BottomBarState.publishAndroidAutoLinked(
            value == AndroidAutoTelemetryKeys.SESSION_ACTIVE
        )
        ServiceManager.getInstance().dispatchTelemetryOnly(AndroidAutoTelemetryKeys.SESSION, value)
        if (value == AndroidAutoTelemetryKeys.SESSION_STOPPED) {
            onSessionStopped()
        } else if (clusterRequested.get()) {
            attachIfSessionAllows("session_active")
        }
    }

    private fun onSessionStopped() {
        if (clusterRequested.getAndSet(false)) demandEpoch.incrementAndGet()
        mainHandler.removeCallbacks(navigationEndedRunnable)
        navigationActive.set(false)
        lastNavigationActiveAtMs.set(0L)
        sessionStopSuppressed.set(false)
        detachSurface("session_stopped")
        br.com.redesurftank.havalshisuku.hud.HudOverlayController.onDirections(AndroidAutoNavigationTelemetry.inactive())
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
        if (!wantsOutput()) {
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
        if (wantsOutput() && surface.isAvailable && deliveredGeneration == generation && deliveredDemandEpoch == epoch) return
        surfaceAttached.set(false)
        notifyHostProjectionFlag(false)
        if (wantsOutput() && surface.isAvailable) {
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

    private fun onLiveChanged(live: Boolean) {
        mainHandler.removeCallbacks(stallWatchdog)
        if (!live) return
        liveSinceMs = SystemClock.elapsedRealtime()
        mainHandler.postDelayed(stallWatchdog, STALL_CHECK_MS)
    }

    private fun checkStall() {
        if (!surfaceAttached.get() || !AaClusterVideoHost.isShown()) return
        val now = SystemClock.elapsedRealtime()
        val ageMs = now - maxOf(AaClusterVideoHost.lastFrameAtMs(), liveSinceMs)
        if (!AaClusterStallPolicy.isStalled(ageMs)) {
            if (stallSinceMs != 0L) {
                ClusterPersistentEventLogger.log(
                    "aa_cluster_stall_resumed",
                    mapOf(
                        "gapMs" to (now - stallSinceMs),
                        "restarted" to (lastStallRestartMs >= stallSinceMs)
                    )
                )
                stallSinceMs = 0L
            }
            return
        }
        val speedKmh = ServiceManager.getInstance()
            .getData(CarConstants.CAR_BASIC_VEHICLE_SPEED.value)?.toFloatOrNull() ?: 0f
        if (stallSinceMs == 0L) {
            stallSinceMs = now - ageMs
            ClusterPersistentEventLogger.log(
                "aa_cluster_stall",
                mapOf("frameAgeMs" to ageMs, "speedKmh" to speedKmh)
            )
        }
        if (!AaClusterStallPolicy.shouldRestart(ageMs, speedKmh, now - lastStallRestartMs)) return
        lastStallRestartMs = now
        ClusterPersistentEventLogger.log(
            "aa_cluster_stall_restart",
            mapOf("frameAgeMs" to ageMs, "speedKmh" to speedKmh)
        )
        Log.w(TAG, "CLUSTER stalled ${ageMs}ms at ${speedKmh}km/h; restarting the stream")
        // Drop the request and ask again, as stopping/restarting navigation does: the Service
        // releases this decoder and the phone starts a fresh stream from a keyframe. The map
        // keeps its last frame on screen meanwhile (setLive(false) does not fade it out).
        deliveredGeneration = -1L
        surfaceAttached.set(false)
        client.setOutput(null)
        mainHandler.postDelayed({ reconcileOutput("stall_restart") }, STALL_RESTART_GAP_MS)
    }

    private fun notifyHostProjectionFlag(enabled: Boolean) {
        ServiceManager.getInstance().dispatchServiceManagerEvent(
            br.com.redesurftank.havalshisuku.models.ServiceManagerEventType.AA_CLUSTER_SURFACE,
            enabled
        )
    }

}
