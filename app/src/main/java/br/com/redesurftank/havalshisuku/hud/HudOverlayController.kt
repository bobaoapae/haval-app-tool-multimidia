package br.com.redesurftank.havalshisuku.hud

import android.app.ActivityManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger
import br.com.redesurftank.havalshisuku.listeners.IDataChanged
import br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry
import br.com.redesurftank.havalshisuku.managers.PowerFlowMapper
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.CarConstants
import br.com.redesurftank.havalshisuku.models.PowerFlow
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns the HUD overlay window (display 4096) while the owner has a layout other than "original"
 * picked in the Telas tab ([HudOverlaySettings], default original = nothing drawn).
 *
 * The window stays up the whole time that layout is picked: the power/regen and RPM bars (if
 * on) are always drawn, and Android Auto turn-by-turn appears only while guidance runs. It is
 * launched once through Shizuku (the HUD is a private display) after a boot grace period, and
 * relaunched if it dies. State lives on the main thread; the Shizuku calls run on their own.
 */
object HudOverlayController {
    private const val TAG = "HudOverlay"
    /** Guidance drops for a moment while re-routing; keep the last cue up meanwhile. */
    private const val TBT_HIDE_DEBOUNCE_MS = 5_000L
    /** Stay off the shell during boot, where the AA early-mount race is tight. */
    private const val BOOT_GRACE_MS = 30_000L
    /** A launch that never produced an Activity is retried after this long. */
    private const val LAUNCH_TIMEOUT_MS = 5_000L
    /** After a failed launch, wait before trying again so a broken path doesn't spin. */
    private const val LAUNCH_BACKOFF_MS = 30_000L
    private const val COMPONENT = "br.com.redesurftank.havalshisuku/.hud.HudOverlayActivity"
    // NEW_TASK | NO_ANIMATION
    private const val LAUNCH_FLAGS = "0x10010000"

    private val KEY_VOLTAGE = CarConstants.CAR_EV_INFO_POWER_BATTERY_VOLTAGE.value
    private val KEY_CURRENT = CarConstants.CAR_EV_INFO_CUR_CHARGE_CURRENT.value
    private val KEY_RPM = CarConstants.CAR_BASIC_ENGINE_SPEED.value
    private val KEY_SPEED = CarConstants.CAR_BASIC_VEHICLE_SPEED.value
    /** AR arrow redraw period while near a maneuver (~10 fps). */
    private const val AR_TICK_MS = 100L
    /** Stop extrapolating if Android Auto goes quiet for this long. */
    private const val AR_EXTRAPOLATE_MAX_MS = 3_000L

    private val main = Handler(Looper.getMainLooper())
    private val shell = Executors.newSingleThreadExecutor { r -> Thread(r, "HudOverlayShell") }

    /** A debug demo frame clears itself after this long. */
    private const val DEMO_TIMEOUT_MS = 60_000L
    /** Time for one empty frame to reach the HUD before a window goes (it latches the last). */
    internal const val CLEAR_FRAME_DELAY_MS = 150L

    // Main thread.
    /** Latest active guidance, kept [TBT_HIDE_DEBOUNCE_MS] past its end; null = no route. */
    private var navDirections: AndroidAutoNavigationTelemetry.Directions? = null
    private var demoDirections: AndroidAutoNavigationTelemetry.Directions? = null
    /** Normal host: a window-manager overlay on the HUD display, no activity behind it. */
    private var overlay: HudOverlayView? = null
    private var overlayWm: WindowManager? = null
    /** Fallback host when the overlay can't be added. */
    private var activity: HudOverlayActivity? = null
    private var launchStartedMs = 0L
    private var launchBlockedUntilMs = 0L
    private var configCache: HudOverlayModel.Config? = null
    private var navReceivedAtMs = 0L
    private var demoSetAtMs = 0L
    private var arTickScheduled = false
    private var startedAtMs = 0L

    // Written from the CAN callback thread; only quantised changes reach the main thread.
    @Volatile private var voltage = Double.NaN
    @Volatile private var current = Double.NaN
    @Volatile private var rpm = Double.NaN
    @Volatile private var engineOn = false
    /** km/h; drives the AR arrow between Android Auto's ~1 Hz distance updates. */
    @Volatile private var speedKmh = 0.0
    private val powerPercent = AtomicInteger(0)
    private val rpmTenths = AtomicInteger(NO_RPM)
    private const val NO_RPM = Int.MIN_VALUE

    private var tbtHidePending = false
    private val hideTbtRunnable = Runnable {
        tbtHidePending = false
        navDirections = null
        publish()
    }

    private val dataListener = IDataChanged { key, value -> onCarData(key, value) }

    /** From ServiceManager once the car services are up. */
    fun start() {
        main.post {
            if (startedAtMs != 0L) return@post
            startedAtMs = SystemClock.elapsedRealtime()
            ServiceManager.getInstance().addDataChangedListener(dataListener)
            seedFromCache()
            // First launch once the boot grace is over, even if no data arrives.
            main.postDelayed({ publish() }, BOOT_GRACE_MS)
        }
    }

    /** Telas tab: layout or one of its options changed (already saved). Any thread. */
    fun setConfig(config: HudOverlayModel.Config) {
        main.post {
            configCache = config
            // The owner's pick wins: a debug demo must not keep the HUD up past "original".
            endDemo()
            if (config.active) publish() else closeHosts()
        }
    }

    /** Android Auto guidance, from AndroidAutoClusterController. Any thread. */
    fun onDirections(update: AndroidAutoNavigationTelemetry.Directions) {
        main.post {
            if (!update.active) {
                // Arm once; repeated inactive updates must not keep pushing the hide out.
                if (navDirections != null && !tbtHidePending) {
                    tbtHidePending = true
                    main.postDelayed(hideTbtRunnable, TBT_HIDE_DEBOUNCE_MS)
                }
                return@post
            }
            tbtHidePending = false
            main.removeCallbacks(hideTbtRunnable)
            val previous = navDirections
            navDirections = update
            navReceivedAtMs = SystemClock.elapsedRealtime()
            // ~1 Hz feed: only redraw when what the HUD shows changes (turn, distance, trip line).
            val config = config()
            if (previous == null || HudOverlayModel.tbtFor(previous, config) != HudOverlayModel.tbtFor(update, config)) {
                publish()
            }
        }
    }

    private fun config(): HudOverlayModel.Config =
        configCache ?: HudOverlaySettings.load().also { configCache = it }

    private fun wanted(): Boolean = config().active || demoDirections != null

    // ---- car data ----

    private fun seedFromCache() {
        val sm = ServiceManager.getInstance()
        sm.getData(KEY_VOLTAGE)?.let { onCarData(KEY_VOLTAGE, it) }
        sm.getData(KEY_CURRENT)?.let { onCarData(KEY_CURRENT, it) }
        sm.getData(PowerFlow.KEY_ICE)?.let { onCarData(PowerFlow.KEY_ICE, it) }
        sm.getData(KEY_RPM)?.let { onCarData(KEY_RPM, it) }
        sm.getData(KEY_SPEED)?.let { onCarData(KEY_SPEED, it) }
    }

    /** CAN callback thread. Cheap: posts to main only when a shown value (%, 0.1k rpm) changes. */
    private fun onCarData(key: String?, value: String?) {
        when (key) {
            KEY_VOLTAGE -> voltage = PowerFlowMapper.parseNumber(value)
            KEY_CURRENT -> current = PowerFlowMapper.parseNumber(value)
            KEY_RPM -> rpm = PowerFlowMapper.parseNumber(value)
            PowerFlow.KEY_ICE -> engineOn = value?.trim() == "1"
            KEY_SPEED -> {
                // Only feeds the AR extrapolation, which redraws on its own tick.
                speedKmh = PowerFlowMapper.parseNumber(value).takeIf { it.isFinite() && it in 0.0..300.0 } ?: 0.0
                return
            }
            else -> return
        }
        val changed = if (key == KEY_VOLTAGE || key == KEY_CURRENT) {
            val kw = if (voltage.isFinite() && current.isFinite()) voltage * current / 1000.0 else 0.0
            val percent = HudOverlayModel.powerPercent(kw)
            powerPercent.getAndSet(percent) != percent
        } else {
            val tenths = HudOverlayModel.rpmTenths(rpm, engineOn) ?: NO_RPM
            rpmTenths.getAndSet(tenths) != tenths
        }
        if (changed) main.post { publish() }
    }

    // ---- window ----

    private fun currentFrame() = HudOverlayModel.frameFor(
        config = config(),
        directions = demoDirections ?: navDirections,
        powerPercent = powerPercent.get(),
        rpmTenths = rpmTenths.get().takeIf { it != NO_RPM },
        arMetersOverride = arMetersEstimate()
    )

    /**
     * AR layout, within [HudArArrow.SMOOTH_WITHIN_M] of a maneuver: Android Auto's last distance
     * minus what the car covered since, so the corner glides instead of jumping once a second.
     * Null when not applicable (the reported distance is used as is).
     */
    private fun arMetersEstimate(): Float? {
        if (config().layout != HudOverlayModel.Layout.AR) return null
        val dirs = (demoDirections ?: navDirections)?.takeIf { it.active } ?: return null
        val base = HudOverlayModel.distanceMeters(dirs.distance, dirs.distanceM) ?: return null
        if (base > HudArArrow.SMOOTH_WITHIN_M) return null
        val since = if (demoDirections != null) demoSetAtMs else navReceivedAtMs
        val elapsedMs = (SystemClock.elapsedRealtime() - since).coerceIn(0L, AR_EXTRAPOLATE_MAX_MS)
        return (base - speedKmh / 3.6 * elapsedMs / 1000.0).coerceAtLeast(0.0).toFloat()
    }

    private val arTick = Runnable {
        arTickScheduled = false
        publish()
    }

    /** Keep redrawing while the AR arrow is near a maneuver and the car is moving. */
    private fun scheduleArTick(frame: HudOverlayModel.Frame) {
        val near = frame.tbt?.style == HudOverlayModel.TbtStyle.AR &&
            (frame.tbt?.arMeters ?: Float.MAX_VALUE) <= HudArArrow.SMOOTH_WITHIN_M
        if (near && speedKmh > 0.5 && !arTickScheduled) {
            arTickScheduled = true
            main.postDelayed(arTick, AR_TICK_MS)
        }
    }

    /** Main thread. Push the frame, or bring the window up if it should exist and doesn't. */
    private fun publish() {
        if (!wanted()) {
            closeHosts()
            return
        }
        overlay?.let {
            val frame = currentFrame()
            it.show(frame)
            scheduleArTick(frame)
            return
        }
        activity?.let {
            val frame = currentFrame()
            it.show(frame)
            scheduleArTick(frame)
            return
        }
        if (startedAtMs == 0L || SystemClock.elapsedRealtime() - startedAtMs < BOOT_GRACE_MS) {
            if (demoDirections == null) return
        }
        requestLaunch()
    }

    private fun requestLaunch() {
        val now = SystemClock.elapsedRealtime()
        if (now < launchBlockedUntilMs) return
        if (launchStartedMs != 0L && now - launchStartedMs < LAUNCH_TIMEOUT_MS) return
        launchStartedMs = now
        shell.execute {
            val ok = launch()
            main.post {
                if (!ok) {
                    launchStartedMs = 0L
                    launchBlockedUntilMs = SystemClock.elapsedRealtime() + LAUNCH_BACKOFF_MS
                }
            }
        }
    }

    private fun launch(): Boolean {
        if (!ShizukuUtils.isShizukuAvailable()) {
            Log.w(TAG, "launch skipped: Shizuku unavailable")
            ClusterPersistentEventLogger.log("hud_overlay_launch", mapOf("ok" to false, "why" to "no_shizuku"))
            return false
        }
        val output = ShizukuUtils.runCommandAndGetOutput(
            arrayOf(
                "sh", "-c",
                "am start --display ${HudOverlayModel.DISPLAY_ID} -f $LAUNCH_FLAGS -n $COMPONENT 2>&1"
            )
        ).orEmpty()
        val ok = !output.contains("Error") && !output.contains("Exception")
        Log.w(TAG, "launch ok=$ok output=${output.trim().take(200)}")
        ClusterPersistentEventLogger.log(
            "hud_overlay_launch",
            mapOf("ok" to ok, "output" to output.trim().take(200))
        )
        return ok
    }

    /**
     * Android 9 resumes one activity at a time, and starting ours on 4096 moves focus there
     * (seen on the car: AA on D0 went paused). Give it back to whatever D0 shows on top.
     * Main thread; the stack lookup runs on the shell thread.
     */
    private fun restoreFocus(host: HudOverlayActivity) {
        val am = host.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        shell.execute {
            val taskId = HudOverlayModel.topTaskOnDisplay(
                ShizukuUtils.runCommandAndGetOutput(arrayOf("sh", "-c", "am stack list 2>&1")).orEmpty(),
                displayId = 0
            )
            if (taskId <= 0) return@execute
            val ok = try {
                am.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_NO_USER_ACTION)
                true
            } catch (t: Throwable) {
                Log.w(TAG, "focus restore failed task=$taskId", t)
                false
            }
            Log.w(TAG, "focus restored task=$taskId ok=$ok")
            ClusterPersistentEventLogger.log("hud_overlay_focus_restore", mapOf("task" to taskId, "ok" to ok))
        }
    }

    /**
     * Debug-only layout check, from HudOverlayActivity's `hudDemo` extra: `on` = right turn at
     * 250 m with three lanes (the R. Nóbrega shape measured on the car), `nolanes` = the same
     * turn without lanes, `far` = keep left in 1.8 km, `off` = back to live data. All with
     * 4.8 km / 12 min to go. Shows even on the "original" layout, so it ends after [DEMO_TIMEOUT_MS] or as soon
     * as the layout is touched.
     */
    internal fun demo(mode: String) {
        val threeLanes = listOf(
            listOf(AndroidAutoNavigationTelemetry.LaneDirection(1, false)),
            listOf(AndroidAutoNavigationTelemetry.LaneDirection(1, false)),
            listOf(
                AndroidAutoNavigationTelemetry.LaneDirection(5, true),
                AndroidAutoNavigationTelemetry.LaneDirection(1, false)
            )
        )
        val demo = AndroidAutoNavigationTelemetry.Directions(
            active = true, distance = "250 m", distanceM = 250, turn = "TURN_RIGHT",
            remainingM = 4_800, remainingS = 720
        )
        main.removeCallbacks(demoExpiry)
        demoSetAtMs = SystemClock.elapsedRealtime()
        demoDirections = when (mode) {
            "on" -> demo.copy(lanes = threeLanes)
            "nolanes" -> demo
            "far" -> demo.copy(distance = "1.8 km", distanceM = 1_800, turn = "KEEP_LEFT")
            else -> null
        }
        // A demo left up looks like a live route with the HUD off (owner hit this): expire it.
        if (demoDirections != null) main.postDelayed(demoExpiry, DEMO_TIMEOUT_MS)
    }

    private val demoExpiry = Runnable {
        if (demoDirections == null) return@Runnable
        demoDirections = null
        publish()
    }

    private fun endDemo() {
        main.removeCallbacks(demoExpiry)
        demoDirections = null
    }

    /**
     * The door activity is up on the HUD display: add our overlay window there. While one of
     * our activities is on a private display the WindowManager accepts windows from our uid on
     * it, and the overlay outlives the activity, which then finishes. Returns false when the
     * overlay can't be added, so the activity hosts the view itself. Main thread.
     */
    internal fun adoptDisplay(host: HudOverlayActivity): Boolean {
        launchStartedMs = 0L
        if (!wanted()) {
            // Nothing to show (e.g. demo off with the toggle off): close what's up, shut the door.
            closeHosts()
            return true
        }
        overlay?.let {
            it.show(currentFrame())
            return true
        }
        return try {
            val display = host.windowManager.defaultDisplay
            val ctx = host.applicationContext.createDisplayContext(display)
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = HudOverlayView(ctx)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { title = "ImpulseHudOverlay" }
            wm.addView(view, params)
            overlay = view
            overlayWm = wm
            view.show(currentFrame())
            Log.w(TAG, "overlay added on display=${display.displayId}")
            ClusterPersistentEventLogger.log("hud_overlay_window", mapOf("ok" to true, "display" to display.displayId))
            true
        } catch (t: Throwable) {
            Log.w(TAG, "overlay add failed; activity hosts the view", t)
            ClusterPersistentEventLogger.log(
                "hud_overlay_window",
                mapOf("ok" to false, "error" to (t.message ?: t.javaClass.simpleName).take(160))
            )
            false
        }
    }

    /** Toggle off (or nothing wanted): clear the HUD, then remove whatever hosts the view. */
    private fun closeHosts() {
        val v = overlay
        val wm = overlayWm
        overlay = null
        overlayWm = null
        if (v != null && wm != null) {
            v.show(null)
            // The HUD latches its last frame, so let the empty one land before the window goes.
            v.postOnAnimation {
                v.postDelayed({ runCatching { wm.removeViewImmediate(v) } }, CLEAR_FRAME_DELAY_MS)
            }
            ClusterPersistentEventLogger.log("hud_overlay_closed", mapOf("host" to "overlay"))
        }
        activity?.dismiss()
    }

    /** Fallback host activity created (or re-intented). Main thread. */
    internal fun attach(host: HudOverlayActivity) {
        launchStartedMs = 0L
        activity = host
        if (!wanted()) {
            // Toggle went off while the launch was in flight, or someone else started us.
            host.dismiss()
            return
        }
        host.show(currentFrame())
        // Every start, new window or not: a repeat `am start` brings our task to the front and
        // takes focus again (seen on the car).
        restoreFocus(host)
    }

    /** Activity destroyed. Main thread. */
    internal fun detach(host: HudOverlayActivity) {
        if (activity !== host) return
        activity = null
        ClusterPersistentEventLogger.log("hud_overlay_closed", mapOf("wanted" to wanted()))
        // Died while it should be up (killed, display reset): bring it back.
        if (wanted()) requestLaunch()
    }
}
