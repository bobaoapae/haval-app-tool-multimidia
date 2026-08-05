package br.com.redesurftank.havalshisuku.projectors

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.R
import br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.CarConstants
import br.com.redesurftank.havalshisuku.models.ClusterKey
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType
import br.com.redesurftank.havalshisuku.models.SteeringWheelAcControlType
import br.com.redesurftank.havalshisuku.bridge.IBridgeContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.get
import kotlinx.coroutines.*

class InstrumentProjector2(private val outerContext: Context, display: Display) :
        BaseProjector(outerContext, display), IBridgeContext {

    private val TAG = "InstrumentProjector2"
    private val CURRENT_BRIDGE_VERSION = 1
    private val DEBUG_EXTERNAL_APP_HTML = "/data/local/tmp/app.html"
    private val FORCE_MAP_DISPLAY_AS_DEFAULT_FOR_TESTS = false
    private val MAP_DISPLAY_TEST_VALUE = "Mapa"
    private val PROJECTION_NATIVE_PANEL_RESTORE_HOLD_MS = 1200L
    private val PROJECTION_PROJECTOR_WARMUP_BYPASS_MS = 1600L
    private val PROJECTION_D3_FALSE_NEGATIVE_HOLD_MS = 12_000L
    private val PERF_HEARTBEAT_INTERVAL_MS = 60_000L
    /** How long a warning must have been standing before BACK is allowed to dismiss it, so
     *  a BACK press already in flight cannot swallow a warning the driver never saw. */
    private val WARNING_DISMISS_LOCKOUT_MS = 2500L
    /** Grace period before the layout follows a warning that cleared by itself, so it does
     *  not snap back while the car's own popup is still finishing its own close. */
    private val WARNING_CLEAR_HOLDOFF_MS = 2000L
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val clockRunnable =
            object : Runnable {
                override fun run() {
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    evaluateJsIfReady(webView, "control('clockTime', '$time')")
                    handler.postDelayed(this, 30000) // Update every 30s
                }
            }
    override val preferences: SharedPreferences =
            App.getDeviceProtectedContext()
                    .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    override var webView: WebView? = null
    private val webViewsLoaded = mutableMapOf<WebView, Boolean>()
    private val pendingJsQueues = mutableMapOf<WebView, MutableList<String>>()
    private lateinit var root: FrameLayout

    // Dedup cache: skip a JS push when the same key:value pair was the most
    // recently pushed one. Car CAN bus often re-emits identical values back
    // to back; pushing them all the way through to the WebView causes wasted
    // DOM mutations + Chrome compositor renders. Cleared on full bootstrap
    // syncs where we intentionally re-establish the whole JS state.
    private val lastSentValues = java.util.concurrent.ConcurrentHashMap<String, String>()
    override val subscribedKeys: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // Cached EV power values for kW calculation
    private var batteryVoltage = 0f
    private var batteryCurrent = 0f
    private var isAnyAppOnDisplay3 = false
    private var isAnyAppOnDisplay1 = false
    // Seeded from the host-side cache rather than assumed to be 0. ServiceManager tracks
    // the card the car last reported and outlives this projector, so a restart or a
    // projector re-creation while the cluster sits on card 1/3 starts from the real card
    // instead of believing card 0 until the next msgId=133 happens to arrive.
    private var currentCard = ServiceManager.getInstance().clusterCardView
    override var isWarningActive = false
    private var testDefaultDisplayOverrideActive = FORCE_MAP_DISPLAY_AS_DEFAULT_FOR_TESTS
    private val dismissedWarnings = java.util.concurrent.ConcurrentHashMap<String, String>()
    override var isWarningDismissed = false
    private var lastWarningActiveTime = 0L
    /** Last value seen per monitored warning key, so a re-emission of an unchanged value
     *  can be told apart from a genuinely new warning. */
    private val lastWarningValues = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** When each currently-active warning started. BACK dismisses the newest one and the
     *  lockout is measured against that warning, not against whichever fired first. */
    private val warningOnsetTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /** Pending hold-off before the layout follows a warning that cleared on its own. */
    private var warningClearRunnable: Runnable? = null
    private var projectionOverlayBypassActive: Boolean? = null
    private var hvacNativePanelActive = false
    private var avmNativePreviewActive = false
    private var nativePanelBypassHoldUntilMs = 0L
    private var projectorWarmupBypassUntilMs = 0L
    private var projectionBypassRestoreScheduledUntilMs = 0L
    private var nativeCardPassThroughActive: Boolean? = null

    private fun isWarningValueActive(value: String?): Boolean {
        return ClusterWarningPolicy.isWarningValueActive(value)
    }

    private var hasAutoLaunched = false
    private val lastAppliedConfigs =
            mutableMapOf<String, br.com.redesurftank.havalshisuku.models.DisplayAppConfig>()

    private var activeThemeMetadata: br.com.redesurftank.havalshisuku.models.ThemeMetadata? = null
    private var nativeMaskContainer: FrameLayout? = null
    private var fuelMaskView: android.widget.ImageView? = null
    private var batteryMaskView: android.widget.ImageView? = null
    private var speedMaskView: android.widget.ImageView? = null
    private var infoMaskView: android.widget.ImageView? = null
    private var topCenterMaskView: android.widget.ImageView? = null
    private var globalMaskView: android.widget.ImageView? = null
    private val maskVisibilityOverrides = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private var themeBridge: br.com.redesurftank.havalshisuku.bridge.ThemeBridgeImpl? = null
    private var bridgePrefsListener: br.com.redesurftank.havalshisuku.bridge.PreferencePushListener? = null

    private var lastHeartbeatTime = System.currentTimeMillis()
    private var lastCarPlayInDash: Boolean? = null
    private var lastProjectionMirrorInDash: Boolean? = null
    private var lastProjectionPreparingD3: Boolean? = null
    private var lastHealthyCarPlayD3AtMs = 0L
    private var lastCarPlayD3HoldLogAtMs = 0L
    private var lastProjectionVisibilityLog = ""
    private var lastProjectionDomDiagnosticAt = 0L
    private var lastPerfHeartbeatLogAtMs = 0L
    private var lastMenuNavigationPerfLogAtMs = 0L
    private var currentClusterScreenName = "unknown"
    private var currentGraphName = "unknown"

    private data class ProjectionSnapshot(
            val carPlayInDash: Boolean,
            val projectionMirrorInDash: Boolean,
            val projectionPreparingD3: Boolean,
            val usedFastPath: Boolean
    ) {
        val active: Boolean
            get() = carPlayInDash || projectionMirrorInDash || projectionPreparingD3
    }
    private val watchdogRunnable =
            object : Runnable {
                override fun run() {
                    val now = System.currentTimeMillis()
                    // If no heartbeat for 15 seconds, and the projector should be visible, reload
                    if (now - lastHeartbeatTime > 15000 &&
                                    shouldShowProjector() &&
                                    ::root.isInitialized &&
                                    root.isVisible
                    ) {
                        Log.e(
                                TAG,
                                "WebView watchdog triggered: No heartbeat for ${now - lastHeartbeatTime}ms. Reloading..."
                        )
                        ClusterPersistentEventLogger.log(
                                "webview_watchdog_reload",
                                mapOf(
                                        "missedHeartbeatMs" to (now - lastHeartbeatTime),
                                        "card" to currentCard,
                                        "screen" to currentClusterScreenName,
                                        "rootVisible" to (::root.isInitialized && root.isVisible)
                                )
                        )
                        ensureUi {
                            webView?.let { wv ->
                                markWebViewLoading(wv, "WATCHDOG_RELOAD")
                                wv.reload()
                            }
                        }
                        lastHeartbeatTime =
                                System.currentTimeMillis() // Reset to avoid immediate re-trigger
                    }
                    refreshProjectionStateFromDisplay("WATCHDOG")
                    handler.postDelayed(this, 5000) // Check every 5s
                }
            }

    val monitoredWarningKeys =
            setOf(
                    CarConstants.CAR_BASIC_COOLANT_TEMP_WARNING.value,
                    CarConstants.CAR_BASIC_ENGINE_OIL_LOW_PRESSURE_WARNING.value,
                    CarConstants.CAR_BASIC_FATIGUE_WARNING.value,
                    CarConstants.CAR_BASIC_MAINTENANCE_WARNING.value,
                    CarConstants.CAR_BASIC_OIL_LOW_WARNING.value,
                    CarConstants.CAR_BASIC_SEAT_BELT_WARNING.value,
                    CarConstants.CAR_BASIC_TIREPRESS_WARNING.value,
                    CarConstants.CAR_BASIC_TIRETEMP_WARNING.value,
                    CarConstants.CAR_BASIC_TPMS_WARNING.value,
                    CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQLEFT.value,
                    CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQRIGHT.value,
                    CarConstants.CAR_IPK_INFO_DOW_WARNING_REQLEFT.value,
                    CarConstants.CAR_IPK_INFO_DOW_WARNING_REQRIGHT.value,
                    CarConstants.CAR_IPK_INFO_FCTA_WARNING.value,
                    CarConstants.CAR_IPK_INFO_FCW_WARNING.value,
                    CarConstants.CAR_IPK_INFO_WARNING_TTS_NOTIFY.value,
                    CarConstants.CAR_IPK_LIGHT_DOOR_WARNING.value,
                    CarConstants.CAR_IPK_LIGHT_ENGINE_OIL_LOW_PRESSURE_WARNING.value,
                    CarConstants.CAR_IPK_LIGHT_SEAT_BELT_WARNING_INDICATOR.value,
                    CarConstants.CAR_IPK_LIGHT_TPMS_WARNING.value,
                    CarConstants.CAR_IPK_LIGHT_FUEL_LOW.value
            )

    private val prefsListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key in
                                listOf(
                                        SharedPreferencesKeys
                                                .ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION
                                                .key,
                                        SharedPreferencesKeys.ENABLE_INSTRUMENT_PROJECTOR.key,
                                        SharedPreferencesKeys.ENABLE_VIRTUAL_CLUSTER.key,
                                        SharedPreferencesKeys.VIRTUAL_CLUSTER_DISPLAY_ID.key,
                                        SharedPreferencesKeys.ACTIVE_CUSTOM_THEME.key,
                                        SharedPreferencesKeys.THEME_RELOAD_NONCE.key,
                                        SharedPreferencesKeys.VIRTUAL_CLUSTER_THEME.key,
                                        SharedPreferencesKeys.CLUSTER_FUEL_DISPLAY_UNIT.key,
                                        // Background keys: the theme needs to know
                                        // whether anything opaque is painted behind
                                        // it, so re-push clusterBackground via the
                                        // PREFS_CHANGED visibility pass below.
                                        SharedPreferencesKeys.ENABLE_CUSTOM_BACKGROUND_D1.key,
                                        SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key,
                                        SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key,
                                        SharedPreferencesKeys.TRIP_CONSISTENCY_CLUSTER_ACTIVE.key,
                                        SharedPreferencesKeys.TRIP_CONSISTENCY_CLUSTER_SCORE.key,
                                        SharedPreferencesKeys
                                                .ENABLE_INSTRUMENT_ODOMETER_AND_REVISION
                                                .key
                                )
                ) {
                    ensureUi {

                        if (key == SharedPreferencesKeys.ENABLE_INSTRUMENT_ODOMETER_AND_REVISION.key
                        ) {
                            val enabled = preferences.getBoolean(key, true)
                            val nextKm =
                                    preferences.getInt(
                                            SharedPreferencesKeys.INSTRUMENT_REVISION_KM.key,
                                            0
                                    )
                            evaluateJsIfReady(webView, "control('enableOdometer', $enabled)")
                            evaluateJsIfReady(
                                    webView,
                                    "control('enableRevisionWarning', ${enabled && nextKm > 0})"
                            )
                        }
                        if (key == SharedPreferencesKeys.ACTIVE_CUSTOM_THEME.key || key == SharedPreferencesKeys.THEME_RELOAD_NONCE.key) {
                            // ACTIVE_CUSTOM_THEME is the sole source of truth for what
                            // content loads (see getActiveCustomThemeName()); it's also
                            // the only key some flows (e.g. download-then-activate) write.
                            // VIRTUAL_CLUSTER_THEME is UI-display bookkeeping only (Telas'
                            // selectedTheme) and is always written in the same transaction
                            // as ACTIVE_CUSTOM_THEME when switching via the theme picker, so
                            // reacting to it too only double-fired this reload per switch.
                            Log.w(TAG, "Theme changed, reloading WebView (key=$key)")
                            webView?.let { wv ->
                                markWebViewLoading(wv, "THEME_CHANGED")
                                wv.loadDataWithBaseURL(
                                        getThemeBaseUrl(),
                                        readAppContent(outerContext),
                                        "text/html",
                                        "UTF-8",
                                        null
                                )
                            }
                        }
                        if (key == SharedPreferencesKeys.CLUSTER_FUEL_DISPLAY_UNIT.key) {
                            val unit = getClusterFuelDisplayUnit()
                            Log.d(TAG, "[HavalDev] Cluster fuel display unit changed: $unit")
                            evaluateJsIfReady(webView, "control('fuelDisplayUnit', '$unit')")
                        }
                        if (key == SharedPreferencesKeys.ENABLE_CUSTOM_BACKGROUND_D1.key ||
                            key == SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key ||
                            key == SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key) {
                            updateNativeMaskViews()
                        }
                        if (key == SharedPreferencesKeys.TRIP_CONSISTENCY_CLUSTER_ACTIVE.key) {
                            val active = preferences.getBoolean(key, false)
                            evaluateJsIfReady(webView, "control('tripAnalysisActive', $active)")
                        }
                        if (key == SharedPreferencesKeys.TRIP_CONSISTENCY_CLUSTER_SCORE.key) {
                            val score =
                                    preferences.getInt(
                                            SharedPreferencesKeys.TRIP_CONSISTENCY_CLUSTER_SCORE
                                                    .key,
                                            -1
                                    )
                            evaluateJsIfReady(
                                    webView,
                                    "control('tripAnalysisScore', ${if (score >= 0) score else "null"})"
                            )
                        }
                        root.isVisible =
                                shouldShowProjector() &&
                                        ServiceManager.getInstance().isMainScreenOn
                        updateVirtualClusterVisibility(reason = "PREFS_CHANGED")
                    }
                } else if (key == SharedPreferencesKeys.INSTRUMENT_REVISION_KM.key) {
                    val nextRevisionKm = preferences.getInt(key, 0)
                    val enabled =
                            preferences.getBoolean(
                                    SharedPreferencesKeys.ENABLE_INSTRUMENT_ODOMETER_AND_REVISION
                                            .key,
                                    true
                            )
                    ensureUi {
                        evaluateJsIfReady(webView, "control('nextRevisionKm', $nextRevisionKm)")
                        evaluateJsIfReady(
                                webView,
                                "control('enableRevisionWarning', ${enabled && nextRevisionKm > 0})"
                        )
                    }
                } else if (key == SharedPreferencesKeys.INSTRUMENT_REVISION_NEXT_DATE.key) {
                    val nextRevisionDate = preferences.getLong(key, 0L)
                    ensureUi {
                        evaluateJsIfReady(webView, "control('nextRevisionDate', $nextRevisionDate)")
                    }
                }
            }

    private fun shouldShowProjector(): Boolean {
        return preferences.getBoolean(
                SharedPreferencesKeys.ENABLE_INSTRUMENT_PROJECTOR.key,
                false
        ) &&
                preferences.getBoolean(
                        SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.key,
                        false
                )
    }

    /**
     * Whether InstrumentProjector actually paints something opaque behind this
     * WebView. Mirrors the branches of InstrumentProjector.applyCustomBackground:
     * PRESET/FILE/IMAGE_URL/COLOR always end up covering (black at worst), while
     * THEME only covers when the active theme package declares a wallpaper — the
     * Default theme never does, so it stays transparent and the car's native dash
     * shows through.
     *
     * The theme uses this to decide whether the no-app masks may shrink with the
     * dials in reduzido, or must stay full size to keep the native dash hidden.
     */
    private fun isClusterBackgroundApplied(): Boolean {
        val enabled =
                preferences.getBoolean(
                        SharedPreferencesKeys.ENABLE_CUSTOM_BACKGROUND_D1.key,
                        true
                )
        if (!enabled) return false

        val type =
                preferences.getString(
                        SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key,
                        "THEME"
                )
                        ?: "THEME"
        val value =
                preferences.getString(SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key, "")
                        ?: ""

        return when (type) {
            "THEME" ->
                    br.com.redesurftank.havalshisuku.managers.ThemeManager
                            .getInstance(outerContext)
                            .getActiveThemeBackgroundFile(value.ifBlank { null }) != null
            "PRESET",
            "FILE",
            "IMAGE_URL",
            br.com.redesurftank.havalshisuku.models.SolidBackgroundSpec.TYPE -> true
            "WEB_URL" -> value.isNotEmpty()
            else -> false
        }
    }

    private fun isCarPlayInDash(): Boolean {
        val rawCarPlayOnD3 =
                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.isCarPlayOnDisplay(3)
        val now = SystemClock.elapsedRealtime()
        if (rawCarPlayOnD3) {
            lastHealthyCarPlayD3AtMs = now
            return true
        }

        return shouldHoldCarPlayD3State(now)
    }

    private fun isProjectionMirrorInDash(): Boolean {
        val rawProjectionOnD3 =
                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                        .isProjectionMirrorOnDisplay(3)
        if (rawProjectionOnD3) return true

        return shouldHoldCarPlayD3State(SystemClock.elapsedRealtime())
    }

    private fun isProjectionPreparingD3(): Boolean {
        return br.com.redesurftank.havalshisuku.managers.CarPlayDisplayOrchestrator.isPreparingD3()
    }

    private fun shouldHoldCarPlayD3State(now: Long): Boolean {
        val desiredCluster =
                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                        .isCarPlayDesiredOnCluster()
        val preparingD3 = isProjectionPreparingD3()
        val shouldHold =
                ProjectionD3StateHoldPolicy.shouldHoldCarPlayInDash(
                        lastHealthyCarPlayD3AtMs,
                        now,
                        desiredCluster,
                        preparingD3,
                        PROJECTION_D3_FALSE_NEGATIVE_HOLD_MS
                )
        if (shouldHold && now - lastCarPlayD3HoldLogAtMs > 2_000L) {
            Log.w(
                    TAG,
                    "[PROJECTION_D3_STATE_HOLD] Keeping Mapa active after transient D3 proof loss; " +
                            "lastHealthyAgoMs=${now - lastHealthyCarPlayD3AtMs} " +
                            "desiredCluster=$desiredCluster preparingD3=$preparingD3"
            )
            lastCarPlayD3HoldLogAtMs = now
        }
        return shouldHold
    }

    private fun isNativeProjectionPanelKey(key: String): Boolean {
        return key == CarConstants.CAR_HVAC_PANEL_DISPLAY_NOTIFY.value ||
                key == CarConstants.SYS_AVM_PREVIEW_STATUS.value
    }

    private fun isNativePanelValueActive(value: String?): Boolean {
        val normalized = value?.trim()?.lowercase(Locale.ROOT) ?: return false
        return normalized.isNotEmpty() &&
                normalized != "0" &&
                normalized != "false" &&
                normalized != "off" &&
                normalized != "null" &&
                normalized != "{0,0,0,0}" &&
                normalized != "{0,0,0,0,0}"
    }

    private fun isNativeProjectionPanelActive(): Boolean {
        return hvacNativePanelActive || avmNativePreviewActive
    }

    private fun updateNativeProjectionPanelStateFromSignal(key: String, value: String): Boolean {
        val active = isNativePanelValueActive(value)
        val changed =
                when (key) {
                    CarConstants.CAR_HVAC_PANEL_DISPLAY_NOTIFY.value -> {
                        if (hvacNativePanelActive == active) {
                            false
                        } else {
                            hvacNativePanelActive = active
                            true
                        }
                    }
                    CarConstants.SYS_AVM_PREVIEW_STATUS.value -> {
                        if (avmNativePreviewActive == active) {
                            false
                        } else {
                            avmNativePreviewActive = active
                            true
                        }
                    }
                    else -> false
                }

        if (changed) {
            if (!active) {
                nativePanelBypassHoldUntilMs =
                        SystemClock.uptimeMillis() + PROJECTION_NATIVE_PANEL_RESTORE_HOLD_MS
            }
            Log.w(
                    TAG,
                    "Native projection panel state changed: key=$key value=$value active=$active hvac=$hvacNativePanelActive avm=$avmNativePreviewActive"
            )
        }
        return changed
    }

    private fun refreshNativeProjectionPanelStateFromCache(): Boolean {
        val sm = ServiceManager.getInstance()
        val hvacActive =
                isNativePanelValueActive(sm.getData(CarConstants.CAR_HVAC_PANEL_DISPLAY_NOTIFY.value))
        val avmActive =
                isNativePanelValueActive(sm.getData(CarConstants.SYS_AVM_PREVIEW_STATUS.value))
        val changed = hvacNativePanelActive != hvacActive || avmNativePreviewActive != avmActive
        if (changed) {
            hvacNativePanelActive = hvacActive
            avmNativePreviewActive = avmActive
            Log.w(
                    TAG,
                    "Native projection panel state refreshed from cache: hvac=$hvacNativePanelActive avm=$avmNativePreviewActive"
            )
        }
        return changed
    }

    private fun scheduleProjectionBypassRestore(untilMs: Long) {
        val now = SystemClock.uptimeMillis()
        if (untilMs <= now || projectionBypassRestoreScheduledUntilMs >= untilMs) return

        projectionBypassRestoreScheduledUntilMs = untilMs
        handler.postDelayed(
                {
                    projectionBypassRestoreScheduledUntilMs = 0L
                    updateVirtualClusterVisibility(reason = "PROJECTION_BYPASS_RESTORE")
                },
                untilMs - now + 80L
        )
    }

    private fun isProjectionOverlayBypassActive(
            carPlayInDash: Boolean,
            androidAutoInDash: Boolean
    ): Boolean {
        if (androidAutoInDash) return false
        if (!carPlayInDash) return false

        // Camera/AVM/HVAC no longer hide the cluster Presentation. The native
        // CarPlay patch keeps the video route alive, and hiding this WebView
        // removes the protected Mapa overlay while the projection is healthy
        // on display 3.
        return false
    }

    private fun applyProjectionOverlayBypass(active: Boolean) {
        if (projectionOverlayBypassActive == active) return

        projectionOverlayBypassActive = active
        val alpha = if (active) 0f else 1f
        window?.let { win ->
            val attrs = win.attributes
            attrs.alpha = alpha
            win.attributes = attrs
        }
        Log.w(
                TAG,
                "Projection overlay bypass active=$active windowAlpha=$alpha hvac=$hvacNativePanelActive avm=$avmNativePreviewActive"
        )
    }

    private fun applyProjectorViewVisibility(
            visible: Boolean,
            bypassActive: Boolean,
            projectionActive: Boolean = isCachedProjectionActive()
    ) {
        if (!::root.isInitialized) return

        val nativeCardPassThrough =
                ClusterCardFlowPolicy.shouldUseNativeCardPassThrough(
                        currentCard,
                        isWarningActive,
                        projectionActive
                )
        val hidden = bypassActive || nativeCardPassThrough
        val alpha = if (hidden) 0f else 1f
        root.alpha = alpha
        root.isVisible = visible && !hidden
        webView?.alpha = alpha
        webView?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        if (nativeCardPassThroughActive != nativeCardPassThrough) {
            nativeCardPassThroughActive = nativeCardPassThrough
            Log.w(
                    TAG,
                    "Native card pass-through active=$nativeCardPassThrough currentCard=$currentCard warningActive=$isWarningActive projectionActive=$projectionActive"
            )
        }
    }

    private fun isCachedProjectionActive(): Boolean {
        return lastCarPlayInDash == true ||
                lastProjectionMirrorInDash == true ||
                lastProjectionPreparingD3 == true ||
                isProjectionPreparingD3()
    }

    private fun resetProjectionStateCache() {
        lastCarPlayInDash = null
        lastProjectionMirrorInDash = null
        lastProjectionPreparingD3 = null
    }

    private fun isProjectionActive(
            carPlayInDash: Boolean = isCarPlayInDash(),
            projectionMirrorInDash: Boolean = isProjectionMirrorInDash(),
            projectionPreparingD3: Boolean = isProjectionPreparingD3()
    ): Boolean {
        return carPlayInDash || projectionMirrorInDash || projectionPreparingD3
    }

    private fun updateKnownScreenForCard(cardId: Int) {
        currentClusterScreenName =
                when (cardId) {
                    ClusterCardIds.MAIN_MENU_CARD -> "main_menu"
                    ClusterCardIds.AIRCON_CARD -> "aircon"
                    ClusterCardIds.NATIVE_CARD -> "hidden"
                    else -> currentClusterScreenName
                }
    }

    private fun logClusterPerfEvent(event: String, details: Map<String, Any?> = emptyMap()) {
        val commonDetails =
                mapOf(
                        "card" to currentCard,
                        "screen" to currentClusterScreenName,
                        "graph" to currentGraphName,
                        "warningActive" to isWarningActive,
                        "display1Active" to isAnyAppOnDisplay1,
                        "display3Active" to isAnyAppOnDisplay3
                ) + details
        ClusterPersistentEventLogger.log("cluster_$event", commonDetails)
        ClusterPerfEventLogger.log(
                event,
                commonDetails
        )
    }

    private fun logPerfHeartbeatIfNeeded() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPerfHeartbeatLogAtMs < PERF_HEARTBEAT_INTERVAL_MS) return
        lastPerfHeartbeatLogAtMs = now
        logClusterPerfEvent(
                "webview_heartbeat",
                mapOf(
                        "webViewLoaded" to (webView?.let { webViewsLoaded.getOrDefault(it, false) } ?: false),
                        "rootVisible" to (::root.isInitialized && root.isVisible)
                )
        )
    }

    private fun logMenuNavigationPerfEventIfNeeded(targetItem: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastMenuNavigationPerfLogAtMs < 2_000L) return
        lastMenuNavigationPerfLogAtMs = now
        logClusterPerfEvent("menu_item_navigation", mapOf("targetItem" to targetItem))
    }

    private fun readProjectionSnapshotForCardChange(): ProjectionSnapshot {
        val projectionPreparingD3 = isProjectionPreparingD3()
        val heldCarPlayD3 = shouldHoldCarPlayD3State(SystemClock.elapsedRealtime())
        val cachedProjectionActive =
                lastCarPlayInDash == true ||
                        lastProjectionMirrorInDash == true ||
                        lastProjectionPreparingD3 == true

        if (!isAnyAppOnDisplay3 &&
                        !projectionPreparingD3 &&
                        !heldCarPlayD3 &&
                        !cachedProjectionActive
        ) {
            return ProjectionSnapshot(
                    carPlayInDash = false,
                    projectionMirrorInDash = false,
                    projectionPreparingD3 = false,
                    usedFastPath = true
            )
        }

        val carPlayInDash = isCarPlayInDash()
        val projectionMirrorInDash = isProjectionMirrorInDash()
        return ProjectionSnapshot(
                carPlayInDash = carPlayInDash,
                projectionMirrorInDash = projectionMirrorInDash,
                projectionPreparingD3 = projectionPreparingD3,
                usedFastPath = false
        )
    }

    private fun isProjectionStateMayBeStale(snapshot: ProjectionSnapshot): Boolean {
        return (lastCarPlayInDash == true && !snapshot.carPlayInDash) ||
                (lastProjectionMirrorInDash == true && !snapshot.projectionMirrorInDash) ||
                (lastProjectionPreparingD3 == true && !snapshot.projectionPreparingD3)
    }

    private fun shouldInspectConfigForDisplay(
            config: br.com.redesurftank.havalshisuku.models.DisplayAppConfig,
            displayId: Int
    ): Boolean {
        if (config.displayId != displayId) return false
        if (br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                        .isProjectionMirrorPackage(config.packageName)
        ) {
            return false
        }
        return config.packageName != outerContext.packageName || displayId != 1
    }

    private fun getManagedSecondaryDisplayConfigs(
            displayIds: Set<Int>
    ): List<br.com.redesurftank.havalshisuku.models.DisplayAppConfig> {
        if (displayIds.isEmpty()) return emptyList()
        return br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.getAllConfigs()
                .filter { config ->
                    displayIds.any { displayId ->
                        isDisplayKnownActive(displayId) && shouldInspectConfigForDisplay(config, displayId)
                    }
                }
    }

    private fun hasManagedSecondaryDisplayWork(displayId: Int): Boolean {
        if (!isDisplayKnownActive(displayId)) return false
        return getManagedSecondaryDisplayConfigs(setOf(displayId)).isNotEmpty()
    }

    private fun hasManagedSecondaryDisplayWork(): Boolean {
        if (!isAnyAppOnDisplay1 && !isAnyAppOnDisplay3) return false
        return getManagedSecondaryDisplayConfigs(setOf(1, 3)).isNotEmpty()
    }

    private fun isDisplayKnownActive(displayId: Int): Boolean {
        return when (displayId) {
            1 -> isAnyAppOnDisplay1
            3 -> isAnyAppOnDisplay3
            else -> false
        }
    }

    private fun logClusterInputKey(keyName: String, keyCode: Int, action: Int) {
        Log.d(
                TAG,
                "[CLUSTER_INPUT_KEY] key=$keyName($keyCode) action=$action"
        )
        ClusterPersistentEventLogger.log(
                "cluster_input_key_projector",
                mapOf(
                        "key" to keyName,
                        "keyCode" to keyCode,
                        "action" to action,
                        "card" to currentCard,
                        "screen" to currentClusterScreenName
                )
        )
    }

    private fun pushProjectionStateToWebView(
            carPlayInDash: Boolean,
            projectionMirrorInDash: Boolean,
            projectionPreparingD3: Boolean = isProjectionPreparingD3(),
            force: Boolean = false
    ) {
        val sendCarPlay = force || lastCarPlayInDash != carPlayInDash
        val sendProjectionMirror = force || lastProjectionMirrorInDash != projectionMirrorInDash
        val sendProjectionPreparing = force || lastProjectionPreparingD3 != projectionPreparingD3
        if (sendCarPlay || sendProjectionMirror || sendProjectionPreparing) {
            Log.w(
                    TAG,
                    "[PROJECTION_STATE_PUSH] force=$force carPlayInDash=$carPlayInDash projectionMirrorInDash=$projectionMirrorInDash projectionPreparingD3=$projectionPreparingD3 loaded=${
                        webView?.let { webViewsLoaded.getOrDefault(it, false) } ?: false
                    } lastCarPlayInDash=$lastCarPlayInDash lastProjectionMirrorInDash=$lastProjectionMirrorInDash lastProjectionPreparingD3=$lastProjectionPreparingD3"
            )
            ClusterPersistentEventLogger.log(
                    "projection_state_push",
                    mapOf(
                            "force" to force,
                            "carPlayInDash" to carPlayInDash,
                            "projectionMirrorInDash" to projectionMirrorInDash,
                            "projectionPreparingD3" to projectionPreparingD3,
                            "loaded" to
                                    (webView?.let {
                                        webViewsLoaded.getOrDefault(it, false)
                                    }
                                            ?: false),
                            "lastCarPlayInDash" to lastCarPlayInDash,
                            "lastProjectionMirrorInDash" to lastProjectionMirrorInDash,
                            "lastProjectionPreparingD3" to lastProjectionPreparingD3,
                            "card" to currentCard,
                            "screen" to currentClusterScreenName
                    )
            )
        }
        if (sendCarPlay) {
            evaluateJsIfReady(webView, "control('carPlayInDash', $carPlayInDash)")
            lastCarPlayInDash = carPlayInDash
        }
        if (sendProjectionMirror) {
            evaluateJsIfReady(webView, "control('projectionMirrorInDash', $projectionMirrorInDash)")
            lastProjectionMirrorInDash = projectionMirrorInDash
        }
        if (sendProjectionPreparing) {
            evaluateJsIfReady(webView, "control('projectionPreparingD3', $projectionPreparingD3)")
            lastProjectionPreparingD3 = projectionPreparingD3
        }
        if (carPlayInDash || projectionMirrorInDash || projectionPreparingD3 || force) {
            scheduleProjectionDomDiagnostic("PROJECTION_STATE_PUSH")
        }
    }

    private fun refreshProjectionStateFromDisplay(reason: String) {
        val carPlayInDash = isCarPlayInDash()
        val projectionMirrorInDash = isProjectionMirrorInDash()
        val projectionPreparingD3 = isProjectionPreparingD3()
        if (
                lastCarPlayInDash != carPlayInDash ||
                        lastProjectionMirrorInDash != projectionMirrorInDash ||
                        lastProjectionPreparingD3 != projectionPreparingD3
        ) {
            Log.w(
                    TAG,
                    "[$reason] Projection state changed: carPlayInDash=$carPlayInDash projectionMirrorInDash=$projectionMirrorInDash projectionPreparingD3=$projectionPreparingD3"
            )
            ClusterPersistentEventLogger.log(
                    "projection_state_changed",
                    mapOf(
                            "reason" to reason,
                            "carPlayInDash" to carPlayInDash,
                            "projectionMirrorInDash" to projectionMirrorInDash,
                            "projectionPreparingD3" to projectionPreparingD3,
                            "lastCarPlayInDash" to lastCarPlayInDash,
                            "lastProjectionMirrorInDash" to lastProjectionMirrorInDash,
                            "lastProjectionPreparingD3" to lastProjectionPreparingD3,
                            "card" to currentCard,
                            "screen" to currentClusterScreenName
                    )
            )
            updateVirtualClusterVisibility(
                    carPlayInDash,
                    projectionMirrorInDash,
                    reason,
                    projectionPreparingD3
            )
        }
    }

    private fun handleClusterCardChanged(nextCard: Int) {
        val startedAt = SystemClock.uptimeMillis()
        val previousCard = currentCard
        currentCard = nextCard
        updateNativeMaskViews()
        updateKnownScreenForCard(nextCard)

        val snapshot = readProjectionSnapshotForCardChange()
        val projectionStateMayBeStale = isProjectionStateMayBeStale(snapshot)
        val hasManagedSecondaryDisplayWork = hasManagedSecondaryDisplayWork()
        val cardCanAffectManagedAppBounds =
                ClusterCardFlowPolicy.cardCanAffectManagedAppBounds(previousCard, nextCard)
        val decision =
                ClusterCardFlowPolicy.decideCardChange(
                        nextCard = nextCard,
                        projectionActive = snapshot.active,
                        projectionStateMayBeStale = projectionStateMayBeStale,
                        hasManagedSecondaryDisplayWork = hasManagedSecondaryDisplayWork,
                        cardCanAffectManagedAppBounds = cardCanAffectManagedAppBounds
                )

        if (decision.clearAppliedAppConfigCache) {
            lastAppliedConfigs.clear()
        }

        if (decision.pushProjectionStateBeforeCard) {
            // During projection, projection classes must reach JS before
            // cardId to avoid a one-frame opaque AC/main menu repaint.
            pushProjectionStateToWebView(
                    snapshot.carPlayInDash,
                    snapshot.projectionMirrorInDash,
                    snapshot.projectionPreparingD3,
                    force = decision.forceProjectionStateBeforeCard
            )
        }

        applyProjectorViewVisibility(
                shouldShowProjector() && ServiceManager.getInstance().isMainScreenOn,
                projectionOverlayBypassActive == true,
                snapshot.active
        )

        // Contract (THEME_GUIDE): backend owns the active card and informs the frontend
        // via window.onCardChanged(cardId) — the single canonical channel. No legacy
        // control('cardId', ...) push: two channels means two code paths and ambiguity
        // about which one a theme is actually honouring.
        //
        // The call is feature-detected, so a theme that predates the contract would
        // silently no-op and sit on whatever card it last rendered (observed on-car with
        // a stale installed theme: card 0 never painted). Fail loudly instead — the fix
        // for that case is redeploying the theme, not a second channel.
        pushActiveCardToTheme(currentCard)

        if (decision.updateVirtualClusterVisibility) {
            updateVirtualClusterVisibility(
                    snapshot.carPlayInDash,
                    snapshot.projectionMirrorInDash,
                    "CLUSTER_CARD_CHANGED",
                    snapshot.projectionPreparingD3
            )
        }

        if (decision.syncSecondaryDisplayApps) {
            syncSecondaryDisplayApps(3)
        }

        if (ClusterCardFlowPolicy.isCardBackedMenu(currentCard)) {
            isWarningDismissed = false
        }
        if (decision.syncVisibleCardValues) {
            updateCardEntryValuesWebView(currentCard)
        }

        val elapsedMs = SystemClock.uptimeMillis() - startedAt
        if (elapsedMs > 80L || !snapshot.usedFastPath || decision.updateVirtualClusterVisibility) {
            Log.w(
                    TAG,
                    "[CARD_FLOW] card=$previousCard->$currentCard elapsedMs=$elapsedMs fastPath=${snapshot.usedFastPath} " +
                            "projectionActive=${snapshot.active} projectionStale=$projectionStateMayBeStale " +
                            "managedSecondary=$hasManagedSecondaryDisplayWork boundsMayChange=$cardCanAffectManagedAppBounds decision=$decision"
            )
        }
        logClusterPerfEvent(
                "card_change",
                mapOf(
                        "from" to previousCard,
                        "to" to currentCard,
                        "elapsedMs" to elapsedMs,
                        "fastPath" to snapshot.usedFastPath,
                        "projectionActive" to snapshot.active,
                        "projectionStale" to projectionStateMayBeStale,
                        "managedSecondary" to hasManagedSecondaryDisplayWork,
                        "boundsMayChange" to cardCanAffectManagedAppBounds,
                        "visibilityWork" to decision.updateVirtualClusterVisibility,
                        "syncApps" to decision.syncSecondaryDisplayApps
                )
        )
    }

    private val eventListener =
            br.com.redesurftank.havalshisuku.listeners.IServiceManagerEvent { event, args ->
                ensureUi {
                    when (event) {
                        ServiceManagerEventType.CLUSTER_CARD_CHANGED -> {
                            handleClusterCardChanged(args[0] as Int)
                        }
                        ServiceManagerEventType.CLUSTER_INPUT_KEY -> {
                            val keyName = args.getOrNull(0) as? String ?: "UNKNOWN"
                            val keyCode = args.getOrNull(1) as? Int ?: -1
                            val action = args.getOrNull(2) as? Int ?: -1
                            logClusterInputKey(keyName, keyCode, action)
                        }
                        ServiceManagerEventType.STEERING_WHEEL_AC_CONTROL -> {
                            val action = args[0]
                            ClusterPersistentEventLogger.log(
                                    "steering_wheel_ac_control",
                                    mapOf(
                                            "action" to action,
                                            "card" to currentCard,
                                            "screen" to currentClusterScreenName
                                    )
                            )
                            // Focus injections removed. Frontend is the single source of truth for navigation.
                            br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                                    .preserveCarPlayClusterContract("STEERING_WHEEL_AC_CONTROL")
                            br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                                    .preserveAndroidAutoNativePanelContract("STEERING_WHEEL_AC_CONTROL")
                        }
                        ServiceManagerEventType.GRAPH_SCREEN_NAVIGATION -> {
                            val screen = args[0]
                            if (screen is String) {
                                currentGraphName = screen
                                logClusterPerfEvent("graph_navigation", mapOf("targetGraph" to screen))
                                // control injection removed. Frontend is the single source of truth for navigation.
                            }
                        }
                        ServiceManagerEventType.UPDATE_SCREEN -> {
                            val arg0 = args[0]
                            if (arg0 is String) {
                                currentClusterScreenName = arg0
                                logClusterPerfEvent("screen_update", mapOf("targetScreen" to arg0))
                                // showScreen injection removed. Frontend is the single source of truth for navigation.
                                if (arg0 == "aircon") {
                                    br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                                            .preserveCarPlayClusterContract("UPDATE_SCREEN_AIRCON")
                                    br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                                            .preserveAndroidAutoNativePanelContract("UPDATE_SCREEN_AIRCON")
                                }
                            } else {
                                evaluateJsIfReady(webView, "control('updateScreen', true)")
                            }
                        }
                        ServiceManagerEventType.MENU_ITEM_NAVIGATION -> {
                            val menuNav = args[0] as String
                            logMenuNavigationPerfEventIfNeeded(menuNav)
                            // focus injection removed. Frontend is the single source of truth for navigation.
                        }
                        ServiceManagerEventType.MAX_AUTO_AC_STATUS_CHANGED -> {
                            val status = args[0]
                            val intStatus =
                                    when (status) {
                                        is Int -> status
                                        is Boolean -> if (status) 1 else 0
                                        else -> 0
                                    }
                            evaluateJsIfReady(webView, "control('maxauto', $intStatus)")
                        }
                        ServiceManagerEventType.DISPLAY_SCREEN_SELECTION -> {
                            val arg0 = args[0] as String
                            logClusterPerfEvent("display_screen_selection", mapOf("scriptOrUrl" to arg0.take(80)))
                            if (args.size > 1 && args[1] == 3) {
                                webView?.loadUrl(arg0)
                            } else {
                                evaluateJsIfReady(webView, arg0)
                            }
                        }
                        ServiceManagerEventType.DISPLAY_3_APP_STATE_CHANGED -> {
                            isAnyAppOnDisplay3 = args[0] as Boolean
                            Log.w(
                                    TAG,
                                    "Display 3 app state changed in cluster projector: $isAnyAppOnDisplay3"
                            )
                            logClusterPerfEvent(
                                    "display3_app_state",
                                    mapOf("active" to isAnyAppOnDisplay3)
                            )
                            if (!isAnyAppOnDisplay3) {
                                lastAppliedConfigs.clear()
                            }
                            updateVirtualClusterVisibility(reason = "DISPLAY_3_APP_STATE_CHANGED")
                            syncSecondaryDisplayApps(3)
                            pushVirtualDisplayState(3)
                        }
                        ServiceManagerEventType.DISPLAY_1_APP_STATE_CHANGED -> {
                            isAnyAppOnDisplay1 = args[0] as Boolean
                            Log.w(
                                    TAG,
                                    "Display 1 app state changed in cluster projector: $isAnyAppOnDisplay1"
                            )
                            logClusterPerfEvent(
                                    "display1_app_state",
                                    mapOf("active" to isAnyAppOnDisplay1)
                            )
                            updateVirtualClusterVisibility(reason = "DISPLAY_1_APP_STATE_CHANGED")
                            pushVirtualDisplayState(1)
                        }
                        ServiceManagerEventType.DISMISS_WARNING -> {
                            dismissWarnings()
                        }
                        ServiceManagerEventType.APP_GEOMETRY_CHANGED -> {
                            logClusterPerfEvent("app_geometry_changed")
                            updateVirtualClusterVisibility(reason = "APP_GEOMETRY_CHANGED")
                            syncSecondaryDisplayApps(3)
                        }
                        ServiceManagerEventType.RAW_KEY_EVENT -> {
                            val key = args[0] as br.com.redesurftank.havalshisuku.models.ClusterKey
                            // LEFT/RIGHT are the cluster card-navigation keys. Card changes are
                            // backend-driven (native msgId=133 -> onCardChanged), so we do NOT forward
                            // them to the theme; doing so lets a theme consume them and the card
                            // change gets lost. Matches the THEME_GUIDE contract (UP/DOWN/ENTER/BACK...).
                            if (key != br.com.redesurftank.havalshisuku.models.ClusterKey.LEFT &&
                                    key != br.com.redesurftank.havalshisuku.models.ClusterKey.RIGHT) {
                                evaluateJsIfReady(webView, "if (window.onKeyEvent) window.onKeyEvent('${key.name}');")
                            }
                        }
                        else -> {}
                    }
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handler.post(clockRunnable)
        handler.post(watchdogRunnable)
        preferences.registerOnSharedPreferenceChangeListener(prefsListener)
        bridgePrefsListener = br.com.redesurftank.havalshisuku.bridge.PreferencePushListener(this@InstrumentProjector2) { virtualKey, value ->
            themeBridge?.pushOnDataChanged(virtualKey, value)
        }
        preferences.registerOnSharedPreferenceChangeListener(bridgePrefsListener)
        ServiceManager.getInstance().addServiceManagerEventListener(eventListener)
        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        window?.addFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
        )
        projectorWarmupBypassUntilMs =
                SystemClock.uptimeMillis() + PROJECTION_PROJECTOR_WARMUP_BYPASS_MS

        root = FrameLayout(outerContext).apply { setBackgroundColor(Color.TRANSPARENT) }
        setContentView(root)
        setupControlView(root)
        isAnyAppOnDisplay3 =
                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.isAnyAppOnDisplay(3)
        isAnyAppOnDisplay1 =
                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.isAnyAppOnDisplay(1)
        updateValuesWebView() // Queue initial values for state sync
        syncInitialWarnings() // Fresh JS state on init — warnings need to be primed
        refreshNativeProjectionPanelStateFromCache()
        updateVirtualClusterVisibility(reason = "ON_CREATE")
        setupDataListeners()
    }

    override fun onStop() {
        Log.w(TAG, "onStop: Cleaning up resources")
        logClusterPerfEvent("projector_on_stop")
        handler.removeCallbacks(clockRunnable)
        handler.removeCallbacks(watchdogRunnable)
        cancelPendingWarningClear()
        scope.cancel()
        preferences.unregisterOnSharedPreferenceChangeListener(prefsListener)
        bridgePrefsListener?.let {
            preferences.unregisterOnSharedPreferenceChangeListener(it)
        }
        ServiceManager.getInstance().removeServiceManagerEventListener(eventListener)

        // Hardening: Explicitly destroy WebView to prevent leaks and broken channels
        webView?.let { wv: WebView ->
            Log.w(TAG, "Destroying WebView")
            root.removeView(wv)
            wv.stopLoading()
            wv.clearHistory()
            wv.clearCache(true)
            wv.loadUrl("about:blank")
            wv.onPause()
            wv.removeAllViews()
            wv.destroy()
            webView = null
        }

        super.onStop()
    }

    private fun setupDataListeners() {
        ServiceManager.getInstance().addDataChangedListener { key, value ->
            if (value == null) return@addDataChangedListener

            if (isNativeProjectionPanelKey(key)) {
                ensureUi {
                    if (updateNativeProjectionPanelStateFromSignal(key, value.toString())) {
                        updateVirtualClusterVisibility(reason = "NATIVE_PANEL_SIGNAL")
                    }
                }
            }

            // Same-value dedup. Cars commonly re-emit identical values
            // back-to-back (e.g. unchanged HVAC settings, sticky CAN
            // signals). Skipping them avoids round-tripping a no-op DOM
            // mutation through the WebView and Chrome compositor — that
            // was a big chunk of the ~30%+ sandbox-process CPU we saw.
            // Cache is cleared in updateValuesWebView() (init / card change
            // / page-finished) so the next telemetry burst is pushed
            // through even when values match the post-bootstrap snapshot.
            // NOTE: We intentionally do NOT gate on shouldShowProjector()
            // or isMainScreenOn here — even when the projector is briefly
            // hidden or the main screen is "off", we still want the
            // WebView's internal state to stay current so it's correct
            // the moment visibility returns.
            val previous = lastSentValues[key]
            if (previous == value) return@addDataChangedListener
            lastSentValues[key] = value

            ensureUi {
                // Symmetrical bridge push
                val themeBridge = this.themeBridge
                if (themeBridge != null) {
                    val themeKey = br.com.redesurftank.havalshisuku.bridge.BridgeContractTranslator.translateCanonicalToThemeKey(key)
                    if (subscribedKeys.contains(themeKey)) {
                        themeBridge.pushOnDataChanged(themeKey, value)
                    } else if (subscribedKeys.contains(key)) {
                        themeBridge.pushOnDataChanged(key, value)
                    }
                }

                // --- Warning Management Logic ---
                if (key in monitoredWarningKeys) {
                    val currentValue = value?.toString() ?: "0"
                    val isNewOnset = trackWarningOnset(key, currentValue)

                    // A new warning re-opens the car's own warning popup, and that popup
                    // lists *every* warning currently active — so the car has just put the
                    // driver's previously dismissed warnings back on screen. Their
                    // acknowledgements no longer describe what is being displayed, and
                    // holding on to them would collapse our layout out from under a popup
                    // that is still up (dismiss the belt, open a door, close it again, and
                    // the belt warning is still sitting there on the car's popup).
                    if (isNewOnset &&
                            ClusterWarningPolicy.raisesWarningBadge(key, currentValue) &&
                            dismissedWarnings.isNotEmpty()) {
                        Log.d(TAG, "New warning $key voids ${dismissedWarnings.size} prior acknowledgement(s)")
                        dismissedWarnings.clear()
                    }

                    if (dismissedWarnings[key] != currentValue) {
                        dismissedWarnings.remove(key)
                        // Informational only — the theme renders from the warningActive
                        // boolean below, not from this. Kept so a theme that wants to list
                        // individual warnings has the raw material.
                        evaluateJsIfReady(webView, "updateWarning('$key', '$currentValue')")
                    }
                    if (key in ClusterWarningPolicy.bsdIndicatorKeys) {
                        pushBsdIndicatorsToTheme()
                    }
                    recomputeWarningState("TELEMETRY:$key")
                }
            }
        }
    }

    private fun triggerAutoLaunch() {
        ensureUi {
            val defaultPackage =
                    preferences.getString(SharedPreferencesKeys.DEFAULT_DISPLAY_APP_PACKAGE.key, "")
                            ?: ""
            if (defaultPackage.isNotEmpty()) {
                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.getAllConfigs()
                        .find { it.packageName == defaultPackage }
                        ?.let { storedConfig ->
                            // Startup auto-launch is the one path the
                            // "move projection to cluster" setting gates; an explicit
                            // send-to-display from the UI stays honoured.
                            val config =
                                    br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                                            .resolveAutoLaunchConfig(storedConfig)
                            Log.d(
                                    TAG,
                                    "Auto-launching default app: $defaultPackage on display ${config.displayId}"
                            )
                            scope.launch {
                                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                                        .launchApp(config)
                            }
                        }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupControlView(parent: FrameLayout) {
        initNativeMaskViews(parent)
        if (webView == null) {
            webView =
                    WebView(this@InstrumentProjector2.context).apply {
                        layoutParams =
                                FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT
                                )
                        setBackgroundColor(Color.TRANSPARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowContentAccess = true
                        webViewClient =
                                object : WebViewClient() {
                                    override fun onPageStarted(
                                            view: WebView?,
                                            url: String?,
                                            favicon: android.graphics.Bitmap?
                                    ) {
                                        super.onPageStarted(view, url, favicon)
                                        view?.let { markWebViewLoading(it, "PAGE_STARTED", url) }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        view?.let { wv: android.webkit.WebView ->
                                            Log.w(
                                                    TAG,
                                                    "WebView finished loading (PID: ${android.os.Process.myPid()}): $url"
                                            )
                                            logClusterPerfEvent(
                                                    "webview_page_finished",
                                                    mapOf("url" to (url ?: "null"))
                                            )

                                            // Mark the WebView as fully loaded first so that any new incoming
                                            // telemetry events are processed instantly instead of being queued.
                                            lastHeartbeatTime = System.currentTimeMillis()
                                            webViewsLoaded[wv] = true

                                            // Replay any JS queued while the page was loading instead of
                                            // discarding it silently — it may include one-off, authoritative
                                            // calls (e.g. window.onCardChanged) that have no other delivery
                                            // path. Stale telemetry values it also contains get overwritten
                                            // by the full-state sync below, so replaying first is harmless.
                                            pendingJsQueues.remove(wv)?.forEach { queuedJs ->
                                                wv.evaluateJavascript(queuedJs, null)
                                            }

                                            // Perform a single, consolidated, full-state synchronization
                                            // using the latest car metrics to guarantee perfect UI consistency.
                                            updateValuesWebView()
                                            updateNativeMaskViews()

                                            // Prime the warning state once so the UI reflects the current car warnings.
                                            syncInitialWarnings()

                                            // Pending JS is intentionally dropped on load; re-send projection
                                            // state immediately so CarPlay/AA display overrides never stay stale.
                                            resetProjectionStateCache()
                                            updateVirtualClusterVisibility(reason = "WEBVIEW_PAGE_FINISHED")

                                            // Inject Heartbeat
                                            wv.evaluateJavascript(
                                                    "setInterval(() => { if (window.Android && window.Android.heartbeat) window.Android.heartbeat(); }, 2000);",
                                                    null
                                            )
                                            // Inject polyfills if necessary
                                            br.com.redesurftank.havalshisuku.bridge.CompatTranslationLayer.injectPolyfillsIfNecessary(wv, activeThemeMetadata)
                                        }
                                    }
                                }
                        webChromeClient =
                                object : WebChromeClient() {
                                    override fun onConsoleMessage(
                                            consoleMessage: ConsoleMessage?
                                    ): Boolean {
                                        if (consoleMessage != null) {
                                            ClusterPersistentEventLogger.log(
                                                    "webview_console",
                                                    mapOf(
                                                            "level" to
                                                                    consoleMessage.messageLevel()
                                                                            ?.name,
                                                            "line" to
                                                                    consoleMessage.lineNumber(),
                                                            "source" to
                                                                    consoleMessage
                                                                            .sourceId()
                                                                            .orEmpty()
                                                                            .takeLast(120),
                                                            "message" to
                                                                    consoleMessage
                                                                            .message()
                                                                            .orEmpty()
                                                    )
                                            )
                                        }
                                        return super.onConsoleMessage(consoleMessage)
                                    }
                                }
                        // addJavascriptInterface MUST run before the page load starts: WebView
                        // only guarantees window.Android is visible to a page's own scripts
                        // (including deferred <script type="module">) if the interface is
                        // registered before loadDataWithBaseURL kicks off loading. Registering
                        // after is a race — themes' bridge.init()/Android.subscribe() calls can
                        // silently no-op if they run before the interface actually attaches.
                        themeBridge = br.com.redesurftank.havalshisuku.bridge.ThemeBridgeImpl(this@InstrumentProjector2)
                        addJavascriptInterface(themeBridge!!, "Android")
                        loadDataWithBaseURL(
                                getThemeBaseUrl(),
                                readAppContent(outerContext),
                                "text/html",
                                "UTF-8",
                                null
                        )
                    }
            parent.addView(webView)
        }
    }



    private fun updateValuesWebView() {
        val sm = ServiceManager.getInstance()
        val webView = this.webView
        if (webView == null) return

        val updates = mutableMapOf<String, String>()

        val carPlayInDash = isCarPlayInDash()
        val projectionMirrorInDash = isProjectionMirrorInDash()
        val projectionPreparingD3 = isProjectionPreparingD3()
        // Projection state must be established before card/screen state reaches
        // JS. Otherwise a stale card such as Display can render an opaque menu
        // over the native CarPlay Surface before mirror classes are active.
        updates["carPlayInDash"] = carPlayInDash.toString()
        updates["projectionMirrorInDash"] = projectionMirrorInDash.toString()
        updates["projectionPreparingD3"] = projectionPreparingD3.toString()
        updates["warningActive"] = isWarningActive.toString()
        updates["warningDismissed"] = isWarningDismissed.toString()
        // cardId is deliberately NOT in this map: batchEvaluateJs would emit it as
        // control('cardId', ...), the legacy channel. The card goes out via
        // window.onCardChanged below so the contract has exactly one channel on both the
        // per-change and the page-load path.
        updates["display"] = getSavedClusterDisplay()
        Log.w(
                TAG,
                "[WEBVIEW_STATE_SYNC] carPlayInDash=$carPlayInDash projectionMirrorInDash=$projectionMirrorInDash projectionPreparingD3=$projectionPreparingD3 cardId=$currentCard display=${updates["display"]} loaded=${
                    webViewsLoaded.getOrDefault(webView, false)
                }"
        )
        ClusterPersistentEventLogger.log(
                "webview_state_sync",
                mapOf(
                        "carPlayInDash" to carPlayInDash,
                        "projectionMirrorInDash" to projectionMirrorInDash,
                        "projectionPreparingD3" to projectionPreparingD3,
                        "cardId" to currentCard,
                        "display" to updates["display"],
                        "loaded" to webViewsLoaded.getOrDefault(webView, false),
                        "screen" to currentClusterScreenName
                )
        )

        // Gears
        updates["gearState"] = getGearLabel(sm.getData(CarConstants.CAR_BASIC_GEAR_STATUS.value))

        // AC and Core info
        updates["fan"] = sm.getData(CarConstants.CAR_HVAC_FAN_SPEED.value) ?: "0"
        updates["temp"] = sm.getData(CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.value) ?: "22"
        updates["power"] = sm.getData(CarConstants.CAR_HVAC_POWER_MODE.value) ?: "0"
        updates["recycle"] = sm.getData(CarConstants.CAR_HVAC_CYCLE_MODE.value) ?: "0"
        updates["auto"] = sm.getData(CarConstants.CAR_HVAC_AUTO_ENABLE.value) ?: "0"
        updates["aion"] = sm.getData(CarConstants.CAR_HVAC_ANION_ENABLE.value) ?: "0"

        val tempUnit = sm.getData(CarConstants.CAR_CONFIGURE_DEFAULT_TEMP_UNIT.value)
        updates["tempUnit"] = if (tempUnit == "1") "°F" else "°C"

        updates["outside_temp"] = formatTemp(sm.getData(CarConstants.CAR_BASIC_OUTSIDE_TEMP.value))
        updates["inside_temp"] = formatTemp(sm.getData(CarConstants.CAR_BASIC_INSIDE_TEMP.value))

        // Revision info
        val enableOdometerAndRevision =
                preferences.getBoolean(
                        SharedPreferencesKeys.ENABLE_INSTRUMENT_ODOMETER_AND_REVISION.key,
                        true
                )
        val nextRevisionKm = preferences.getInt(SharedPreferencesKeys.INSTRUMENT_REVISION_KM.key, 0)
        updates["enableOdometer"] = enableOdometerAndRevision.toString()
        updates["enableRevisionWarning"] =
                (enableOdometerAndRevision && nextRevisionKm > 0).toString()

        val odometer = sm.getData(CarConstants.CAR_BASIC_TOTAL_ODOMETER.value) ?: "0"
        updates["odometer"] = odometer

        updates["nextRevisionKm"] = nextRevisionKm.toString()
        updates["nextRevisionDate"] =
                preferences
                        .getLong(SharedPreferencesKeys.INSTRUMENT_REVISION_NEXT_DATE.key, 0L)
                        .toString()

        // Fuel and Battery Percentages/Range
        updates["fuelPercent"] =
                sm.getData(CarConstants.CAR_BASIC_REMAIN_FUEL_PERCENTAGE.value) ?: "0"
        updates["batteryPercent"] =
                sm.getData(CarConstants.CAR_EV_INFO_CUR_BATTERY_POWER_PERCENTAGE.value) ?: "0"
        updates["fuelRange"] =
                sm.getData(CarConstants.CAR_EV_INFO_FUEL_MODE_REMAIN_ODOMETER.value) ?: "0"
        updates["batteryRange"] =
                sm.getData(CarConstants.CAR_EV_INFO_ELECTRIC_MODE_REMAIN_ODOMETER.value) ?: "0"
        updates["fuelDisplayUnit"] = getClusterFuelDisplayUnit()
        val tripAnalysisActive =
                preferences.getBoolean(
                        SharedPreferencesKeys.TRIP_CONSISTENCY_CLUSTER_ACTIVE.key,
                        false
                )
        val tripAnalysisScore =
                preferences.getInt(SharedPreferencesKeys.TRIP_CONSISTENCY_CLUSTER_SCORE.key, -1)
        updates["tripAnalysisActive"] = tripAnalysisActive.toString()
        updates["tripAnalysisScore"] = if (tripAnalysisScore >= 0) tripAnalysisScore.toString() else "null"

        // Speed and Engine
        val speedStr = getAdjustedSpeed(sm.getData(CarConstants.CAR_BASIC_VEHICLE_SPEED.value))
        updates["carSpeed"] = speedStr
        updates["engineRPM"] = sm.getData(CarConstants.CAR_BASIC_ENGINE_SPEED.value) ?: "0"

        // Power and Regen Graph
        val outputPower =
                sm.getData(CarConstants.CAR_EV_INFO_ENERGY_OUTPUT_PERCENTAGE.value)?.toFloatOrNull()
                        ?: 0.0f
        val regenValue = kotlin.math.max(0.0f, -1 * outputPower)
        updates["evPowerFactor"] = outputPower.toString()
        updates["evPowerRegen"] = regenValue.toString()

        // Battery KW Calculation
        batteryVoltage =
                sm.getData(CarConstants.CAR_EV_INFO_POWER_BATTERY_VOLTAGE.value)?.toFloatOrNull()
                        ?: 0f
        batteryCurrent =
                sm.getData(CarConstants.CAR_EV_INFO_CUR_CHARGE_CURRENT.value)?.toFloatOrNull() ?: 0f
        val kw = batteryVoltage * batteryCurrent / 1000f
        updates["evPowerKw"] = kw.toString()

        // Consumption initial values
        updateGasConsumption(
                sm.getData(CarConstants.CAR_BASIC_INSTANT_FUEL_CONSUMPTION.value),
                updates
        )
        updates["instantEVConsumption"] =
                sm.getData(CarConstants.CAR_EV_INFO_INSTANT_ENERGY_CONSUMPTION.value) ?: "0"

        // Bootstrap: clear the dedup cache so the next telemetry burst is
        // pushed through even if values match. The batchEvaluateJs below
        // re-establishes the WebView's full state.
        lastSentValues.clear()

        batchEvaluateJs(webView, updates)

        // The active card, on the canonical channel. Must come after the batch above so
        // projection state is already in place — otherwise a card such as AC can paint an
        // opaque menu over a native CarPlay surface for a frame. pushActiveCardToTheme
        // reports "missing" if the theme has not defined onCardChanged yet, which makes an
        // ordering problem on this path visible instead of silent.
        pushActiveCardToTheme(currentCard)
    }

    private fun updateCardEntryValuesWebView(cardId: Int) {
        val sm = ServiceManager.getInstance()
        val updates = mutableMapOf<String, String>()

        when (cardId) {
            ClusterCardIds.AIRCON_CARD -> {
                updates["fan"] = sm.getData(CarConstants.CAR_HVAC_FAN_SPEED.value) ?: "0"
                updates["temp"] =
                        sm.getData(CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.value) ?: "22"
                updates["power"] = sm.getData(CarConstants.CAR_HVAC_POWER_MODE.value) ?: "0"
                updates["recycle"] = sm.getData(CarConstants.CAR_HVAC_CYCLE_MODE.value) ?: "0"
                updates["auto"] = sm.getData(CarConstants.CAR_HVAC_AUTO_ENABLE.value) ?: "0"
                updates["aion"] = sm.getData(CarConstants.CAR_HVAC_ANION_ENABLE.value) ?: "0"
                val tempUnit = sm.getData(CarConstants.CAR_CONFIGURE_DEFAULT_TEMP_UNIT.value)
                updates["tempUnit"] = if (tempUnit == "1") "°F" else "°C"
                updates["outside_temp"] =
                        formatTemp(sm.getData(CarConstants.CAR_BASIC_OUTSIDE_TEMP.value))
                updates["inside_temp"] =
                        formatTemp(sm.getData(CarConstants.CAR_BASIC_INSIDE_TEMP.value))
            }
            ClusterCardIds.MAIN_MENU_CARD -> {
                updates["evMode"] = sm.getData(CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.value) ?: ""
                // 'drivingMode' is a translated (friendly) key: the WebView's window.control()
                // maps its raw CAN value through the label table and derives evModeLabel from
                // it (see state.js). Do NOT also push a raw "evModeLabel" here — that key isn't
                // in FRIENDLY_KEY_TO_CAN_KEY, so it lands untranslated and clobbers the label
                // the drivingMode push just correctly derived (e.g. showing "0" instead of "NORMAL").
                updates["drivingMode"] = sm.getData(CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.value) ?: ""
                updates["steerMode"] = sm.getData(CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE.value) ?: ""
                updates["espStatus"] = sm.getData(CarConstants.CAR_DRIVE_SETTING_ESP_ENABLE.value) ?: ""
                updates["regenMode"] = sm.getData(CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.value) ?: ""
            }
        }

        batchEvaluateJs(webView, updates)
    }

    /**
     * Prime a freshly-loaded theme (init / page-finished / theme switch).
     *
     * This is the case the whole dismissal mechanism exists for: a theme starts with an
     * empty warning state, so without the backend replaying what it knows, a warning the
     * driver already dismissed would pop straight back up. [recomputeWarningState] decides
     * the badge from the backend's own record, and a key still sitting at the value it was
     * acknowledged at is withheld below so the theme never even hears about it.
     */
    private fun syncInitialWarnings() {
        val webView = this.webView ?: return

        for (key in monitoredWarningKeys) {
            // Momentary alerts reach the theme from live telemetry only — never replayed
            // from ServiceManager's cache, which still holds whatever the last event left
            // behind. See ClusterWarningPolicy.transientWarningKeys.
            if (key in ClusterWarningPolicy.transientWarningKeys) continue
            // On a theme switch the projector has been running and already has a live view
            // that is fresher than the cache; on a genuine first load this falls through to
            // the cache. Either way it is the same value the badge is computed from.
            val value = currentWarningValue(key) ?: "0"
            trackWarningOnset(key, value)
            if (dismissedWarnings[key] == value) continue
            evaluateJsIfReady(webView, "updateWarning('$key', '$value')")
        }

        recomputeWarningState("THEME_LOAD")
    }

    /**
     * Single source of truth for the warning badge.
     *
     * The badge used to be computed inside each theme and pushed *back* here via
     * `Android.setWarningActive()`, which left a downloadable theme deciding a value the
     * backend uses for cluster visibility and app widths — and left two "which keys count"
     * lists to drift apart. The backend owns it now; themes render what they are told.
     */
    private fun recomputeWarningState(reason: String, immediate: Boolean = false) {
        val active = unacknowledgedBadgeWarnings().isNotEmpty()

        if (active) {
            cancelPendingWarningClear()
            applyWarningState(true, reason)
            return
        }

        // Raising is instant; clearing is not. When the last warning goes inactive the car's
        // own popup lingers for a moment before it closes, and dropping the layout out from
        // under it looks like the same mismatch as leaving it up too long — just in the other
        // direction. A BACK press is exempt: the popup closes with it, so the layout should
        // follow immediately.
        if (isWarningActive && !immediate) {
            scheduleWarningClear(reason)
            return
        }

        cancelPendingWarningClear()
        applyWarningState(false, reason)
    }

    private fun scheduleWarningClear(reason: String) {
        if (warningClearRunnable != null) return // a hold-off is already running
        val runnable = Runnable {
            warningClearRunnable = null
            // Re-check rather than trusting the decision made when this was queued: a warning
            // may have arrived, or been acknowledged, during the hold-off.
            if (unacknowledgedBadgeWarnings().isEmpty()) {
                applyWarningState(false, "$reason+holdoff")
            }
        }
        warningClearRunnable = runnable
        handler.postDelayed(runnable, WARNING_CLEAR_HOLDOFF_MS)
        Log.d(TAG, "Warning clear held off ${WARNING_CLEAR_HOLDOFF_MS}ms ($reason)")
    }

    private fun cancelPendingWarningClear() {
        warningClearRunnable?.let { handler.removeCallbacks(it) }
        warningClearRunnable = null
    }

    private fun applyWarningState(active: Boolean, reason: String) {
        val dismissed = !active && dismissedWarnings.isNotEmpty()
        val changed = active != isWarningActive || dismissed != isWarningDismissed

        if (active && !isWarningActive) {
            lastWarningActiveTime = System.currentTimeMillis()
            Log.w(TAG, "Warning onset ($reason). currentCard=$currentCard")
        }

        isWarningActive = active
        isWarningDismissed = dismissed

        if (changed) {
            lastAppliedConfigs.clear() // Invalidate cache on warning toggle to force re-sync
            logClusterPerfEvent(
                    "warning_state_changed",
                    mapOf("active" to active, "dismissed" to dismissed, "reason" to reason)
            )
            updateVirtualClusterVisibility(reason = "WARNING_STATE_CHANGED")
            syncSecondaryDisplayApps(3)
        }

        pushWarningStateToTheme()
    }

    private fun pushWarningStateToTheme() {
        // control() delivers real booleans. pushOnDataChanged() would deliver the string
        // "false", which is truthy in JS and would pin the badge on — see the note beside
        // keysToSubscribe in the themes' main.js.
        evaluateJsIfReady(
                webView,
                "control('warningActive', $isWarningActive); control('warningDismissed', $isWarningDismissed);"
        )
    }

    private fun pushBsdIndicatorsToTheme() {
        // Same live-view rule as the badge: this runs from the telemetry listener, where the
        // cache may not yet reflect the event that triggered it — reading it here would latch
        // an arrow on after the alert had already passed.
        val left = ClusterWarningPolicy.isWarningValueActive(
                currentWarningValue(CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQLEFT.value))
        val right = ClusterWarningPolicy.isWarningValueActive(
                currentWarningValue(CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQRIGHT.value))
        evaluateJsIfReady(
                webView,
                "control('bsdLeft', $left); control('bsdRight', $right);"
        )
    }

    /**
     * Per-key onset bookkeeping, feeding both the dismissal lockout and the "newest
     * warning wins" pick in [dismissWarnings]. Must see every telemetry event, including
     * a warning re-arriving at a value the driver already acknowledged.
     */
    private fun trackWarningOnset(key: String, value: String): Boolean {
        val previous = lastWarningValues.put(key, value)
        if (!ClusterWarningPolicy.isWarningValueActive(value)) {
            warningOnsetTimes.remove(key)
            return false
        }
        // A repeat of the same value is the same warning still standing, not a new one.
        // The car re-emits these while they are active, so re-arming the lockout on every
        // emission would keep pushing it into the future and make BACK permanently inert.
        if (previous != value || !warningOnsetTimes.containsKey(key)) {
            warningOnsetTimes[key] = System.currentTimeMillis()
            return true
        }
        return false
    }

    /**
     * The value this projector believes a warning key currently holds.
     *
     * Deliberately NOT ServiceManager.getData(). The telemetry listener hands us the new
     * value directly, and ServiceManager's cache is not guaranteed to have caught up by the
     * time the listener runs — so a warning that had just cleared could still read as active
     * here, leaving the badge latched on with no further event coming to clear it. It also
     * kept dismissal fragile: an acknowledgement recorded from the getData form of a value
     * would stop matching the listener form of the same value, and the warning would come
     * back on its next re-emission.
     *
     * [lastWarningValues] is fed from the live event before the state is recomputed, and
     * seeded from the cache at theme load, so it is both the freshest and the only view the
     * comparisons here are made against.
     */
    private fun currentWarningValue(key: String): String? =
            lastWarningValues[key] ?: ServiceManager.getInstance().getData(key)

    /** Warnings the driver can currently see and has not acknowledged, newest first. */
    private fun unacknowledgedBadgeWarnings(): List<Pair<String, String>> {
        return monitoredWarningKeys
                .mapNotNull { key -> currentWarningValue(key)?.let { key to it } }
                .filter { (key, value) ->
                    ClusterWarningPolicy.raisesWarningBadge(key, value) &&
                            dismissedWarnings[key] != value
                }
                .sortedByDescending { (key, _) -> warningOnsetTimes[key] ?: 0L }
    }

    private fun hasCriticalTelemetryWarning(): Boolean {
        for (key in monitoredWarningKeys) {
            val value = currentWarningValue(key)
            if (ClusterWarningPolicy.shouldTriggerCriticalWarningFlow(key, value)) {
                Log.w(TAG, "Critical telemetry warning active: key=$key value=$value")
                return true
            }
        }
        return false
    }

    private fun getClusterFuelDisplayUnit(): String {
        val unit =
                preferences.getString(
                        SharedPreferencesKeys.CLUSTER_FUEL_DISPLAY_UNIT.key,
                        "liters"
                ) ?: "liters"
        return if (unit == "percent") "percent" else "liters"
    }

    private fun batchEvaluateJs(view: WebView?, updates: Map<String, String>) {
        if (view == null || updates.isEmpty()) return
        val jsBuilder = StringBuilder("(function(){")
        updates.forEach { (key, value) ->
            val formattedValue =
                    if (value == "true" ||
                                    value == "false" ||
                                    value == "null" ||
                                    value.toDoubleOrNull() != null
                    )
                            value
                    else "'$value'"
            jsBuilder.append("control('$key', $formattedValue);")
        }
        jsBuilder.append("})()")
        evaluateJsIfReady(view, jsBuilder.toString())
    }

    private fun updateVirtualClusterVisibility(
            carPlayInDash: Boolean = isCarPlayInDash(),
            projectionMirrorInDash: Boolean = isProjectionMirrorInDash(),
            reason: String = "UPDATE_VIRTUAL_CLUSTER_VISIBILITY",
            projectionPreparingD3: Boolean = isProjectionPreparingD3()
    ) {
        val clusterEnabled =
                preferences.getBoolean(SharedPreferencesKeys.ENABLE_VIRTUAL_CLUSTER.key, true)
        val projectorVisible =
                shouldShowProjector() && ServiceManager.getInstance().isMainScreenOn
        var isLeftCovered = false
        var isRightCovered = false

        // NOTE ON ORDERING: everything down to the appInDash push below is cheap
        // (prefs + one cached `am stack list`). The Android Auto probe further
        // down is not — it fans out into several stack queries and only feeds the
        // overlay-bypass decision, never appInDash. This whole method runs on the
        // UI thread via ensureUi{}, which is the same thread that paints the theme
        // WebView, so doing the expensive probe first made the theme wait seconds
        // to learn that an app had left the cluster. Compute coverage and tell the
        // theme first; do the projection/overlay bookkeeping afterwards.
        if (projectionMirrorInDash || projectionPreparingD3) {
            isLeftCovered = true
            isRightCovered = true
        }

        val displayIdsToInspect =
                buildSet {
                    if (isAnyAppOnDisplay1) add(1)
                    if (isAnyAppOnDisplay3 || projectionMirrorInDash || projectionPreparingD3) add(3)
                }
        val configs = getManagedSecondaryDisplayConfigs(displayIdsToInspect)
        val tasksByPackage =
                if (configs.isEmpty()) {
                    emptyMap()
                } else {
                    br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                            .findFirstTasksForPackages(configs.map { it.packageName })
                }

        for (displayId in listOf(1, 3)) {
            val res =
                    br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                            .getDisplayResolution(displayId)
            val fullWidth = res.first
            if (fullWidth <= 0) continue

            val appsOnDisplay =
                    configs.filter { config ->
                        val task = tasksByPackage[config.packageName]
                        task != null && task.displayId == displayId
                    }

            for (app in appsOnDisplay) {
                val bounds =
                        br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                                .getEffectiveBounds(app)
                val baseX = bounds[0]
                val baseWidth = bounds[2] - bounds[0]

                if (baseX <= (fullWidth * 0.1f).toInt()) {
                    isLeftCovered = true
                }

                val actualWidth =
                        if (displayId == 3 &&
                                        (!isWarningDismissed &&
                                                (currentCard == ClusterCardIds.NATIVE_CARD ||
                                                        isWarningActive))
                        ) {
                            (fullWidth * 0.7f).toInt() - baseX
                        } else {
                            baseWidth
                        }

                if (baseX + actualWidth >= (fullWidth * 0.7f).toInt()) {
                    isRightCovered = true
                }
            }
        }

        val appInDashValue =
                when {
                    isLeftCovered && isRightCovered -> "true"
                    isLeftCovered -> "'left'"
                    isRightCovered -> "'right'"
                    else -> "false"
                }

        val clusterBackground = isClusterBackgroundApplied()

        evaluateJsIfReady(
                webView,
                "(function(){control('clusterEnabled', $clusterEnabled);control('clusterBackground', $clusterBackground);control('appInDash', $appInDashValue);})()"
        )

        // Deferred until after the theme has been told: the AA probe is the
        // expensive part of this method and nothing above depends on it.
        val androidAutoInDash =
                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                        .isAndroidAutoOnDisplay(3)
        val overlayBypassActive =
                isProjectionOverlayBypassActive(carPlayInDash, androidAutoInDash)

        logProjectionVisibility(
                reason,
                carPlayInDash,
                projectionMirrorInDash,
                projectionPreparingD3,
                clusterEnabled,
                projectorVisible,
                overlayBypassActive
        )

        applyProjectionOverlayBypass(overlayBypassActive)
        applyProjectorViewVisibility(
                projectorVisible,
                overlayBypassActive,
                carPlayInDash || projectionMirrorInDash || projectionPreparingD3
        )

        pushProjectionStateToWebView(carPlayInDash, projectionMirrorInDash, projectionPreparingD3)
    }

    private fun logProjectionVisibility(
            reason: String,
            carPlayInDash: Boolean,
            projectionMirrorInDash: Boolean,
            projectionPreparingD3: Boolean,
            clusterEnabled: Boolean,
            projectorVisible: Boolean,
            overlayBypassActive: Boolean
    ) {
        val snapshot =
                "carPlayInDash=$carPlayInDash projectionMirrorInDash=$projectionMirrorInDash projectionPreparingD3=$projectionPreparingD3 " +
                        "cardId=$currentCard clusterEnabled=$clusterEnabled projectorVisible=$projectorVisible " +
                        "overlayBypass=$overlayBypassActive anyD3=$isAnyAppOnDisplay3 anyD1=$isAnyAppOnDisplay1"
        if (snapshot != lastProjectionVisibilityLog ||
                        reason == "ON_CREATE" ||
                        reason == "WEBVIEW_PAGE_FINISHED" ||
                        reason == "CLUSTER_CARD_CHANGED"
        ) {
            Log.w(TAG, "[$reason] Projection visibility: $snapshot")
            lastProjectionVisibilityLog = snapshot
        }
    }

    private fun scheduleProjectionDomDiagnostic(reason: String) {
        val view = webView ?: return
        if (!webViewsLoaded.getOrDefault(view, false)) return

        val now = SystemClock.uptimeMillis()
        if (now - lastProjectionDomDiagnosticAt < 1_500L) return
        lastProjectionDomDiagnosticAt = now

        handler.postDelayed(
                {
                    val js =
                            """
                            (function(){
                              try {
                                if (window.__havalProjectionDebug) {
                                  return JSON.stringify(window.__havalProjectionDebug());
                                }
                                var app = document.getElementById('app');
                                var menu = document.querySelector('.dashboard-menu-container');
                                return JSON.stringify({
                                  debugHook: false,
                                  appClass: app ? app.className : null,
                                  menuDisplay: menu ? getComputedStyle(menu).display : null,
                                  menuVisibility: menu ? getComputedStyle(menu).visibility : null,
                                  menuOpacity: menu ? getComputedStyle(menu).opacity : null
                                });
                              } catch (e) {
                                return 'error:' + e.message;
                              }
                            })()
                            """.trimIndent()
                    view.evaluateJavascript(js) { result ->
                        Log.w(TAG, "[$reason] Projection DOM: $result")
                    }
                },
                250L
        )
    }

    private fun syncSecondaryDisplayApps(displayId: Int) {
        if (!hasManagedSecondaryDisplayWork(displayId)) return

        val res =
                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.getDisplayResolution(
                        displayId
                )
        val fullWidth = res.first
        if (fullWidth <= 0) return

        val configs = getManagedSecondaryDisplayConfigs(setOf(displayId))
        val tasksByPackage =
                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                        .findFirstTasksForPackages(configs.map { it.packageName })
        val appsOnDisplay =
                configs.filter { config -> tasksByPackage[config.packageName]?.displayId == displayId }

        for (app in appsOnDisplay) {
            val bounds =
                    br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.getEffectiveBounds(
                            app
                    )
            val baseX = bounds[0]
            val baseY = bounds[1]
            val baseWidth = bounds[2] - bounds[0]
            val baseHeight = bounds[3] - bounds[1]

            val targetWidth =
                    if (displayId == 3 &&
                                    (!isWarningDismissed &&
                                            (currentCard == ClusterCardIds.NATIVE_CARD ||
                                                    isWarningActive))
                    ) {
                        val calculated = (fullWidth * 0.7f).toInt() - baseX
                        kotlin.math.max(100, kotlin.math.min(baseWidth, calculated))
                    } else {
                        baseWidth
                    }

            val targetConfig =
                    app.copy(
                            x = baseX,
                            y = baseY,
                            width = targetWidth,
                            height = baseHeight,
                            displayId = displayId
                    )
            val lastConfig = lastAppliedConfigs[app.packageName]

            if (lastConfig == null ||
                            lastConfig.x != targetConfig.x ||
                            lastConfig.y != targetConfig.y ||
                            lastConfig.width != targetConfig.width ||
                            lastConfig.height != targetConfig.height ||
                            lastConfig.displayId != targetConfig.displayId
            ) {

                lastAppliedConfigs[app.packageName] = targetConfig
                Log.d(
                        TAG,
                        "Syncing app ${app.packageName} (Display $displayId): card=$currentCard warn=$isWarningActive -> width=$targetWidth"
                )
                scope.launch {
                    br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.resizeApp(
                            targetConfig
                    )
                }
            }
        }
    }

    /**
     * Deliver the active card to the theme and report what actually happened.
     *
     * The card is the one piece of state with no periodic re-push: telemetry is resent
     * constantly via onDataChanged, but a dropped card leaves the theme rendering a
     * different card from the one the cluster is on, with nothing to correct it. The
     * result callback distinguishes "delivered", "theme has no handler" and "the handler
     * threw" — which is otherwise invisible from the Kotlin side.
     */
    private fun pushActiveCardToTheme(cardId: Int) {
        val wv = webView ?: return
        val js =
                "(function(){ try {" +
                        " if (!window.onCardChanged) return 'missing';" +
                        " window.onCardChanged($cardId); return 'ok';" +
                        " } catch (e) { return 'threw:' + e; } })()"
        if (webViewsLoaded.getOrDefault(wv, false)) {
            wv.evaluateJavascript(js) { result ->
                if (result == null || !result.contains("ok")) {
                    Log.e(TAG, "[CARD_PUSH] card=$cardId not delivered to theme: $result")
                    ClusterPersistentEventLogger.log(
                            "card_push_failed",
                            mapOf("card" to cardId, "result" to (result ?: "null"))
                    )
                } else {
                    Log.w(TAG, "[CARD_PUSH] card=$cardId delivered")
                }
            }
        } else {
            Log.w(TAG, "[CARD_PUSH] card=$cardId queued (webview not loaded)")
            ClusterPersistentEventLogger.log("card_push_queued", mapOf("card" to cardId))
            evaluateJsIfReady(wv, js)
        }
    }

    private fun evaluateJsIfReady(webView: WebView?, js: String) {
        if (webView == null) return
        if (webViewsLoaded.getOrDefault(webView, false)) {
            if (!hasAutoLaunched) {
                hasAutoLaunched = true
                triggerAutoLaunch()
            }
            webView.evaluateJavascript(js, null)
        } else {
            pendingJsQueues.getOrPut(webView) { mutableListOf() }.add(js)
        }
    }

    private fun markWebViewLoading(webView: WebView, reason: String, url: String? = null) {
        webViewsLoaded[webView] = false
        pendingJsQueues.remove(webView)
        lastHeartbeatTime = System.currentTimeMillis()
        logClusterPerfEvent(
                "webview_loading",
                mapOf(
                        "reason" to reason,
                        "url" to (url ?: "")
                )
        )
    }

    private fun getThemeBaseUrl(): String {
        val customThemeName = getActiveCustomThemeName()
        if (customThemeName.isNotEmpty()) {
            val themeDir = File(File(outerContext.filesDir, "themes"), customThemeName)
            if (themeDir.exists()) {
                return "file://${themeDir.absolutePath}/"
            }
        }
        return "file:///android_asset/"
    }

    private fun readAppContent(context: Context): String {
        if (isDebuggableApp()) {
            tryLoadExternalDebugHtml()?.let { externalHtml ->
                return externalHtml
            }
        }

        val customThemeName = getActiveCustomThemeName()
        if (customThemeName.isNotEmpty()) {
            try {
                val themeManager =
                        br.com.redesurftank.havalshisuku.managers.ThemeManager.getInstance(
                                outerContext
                        )
                val metadata = themeManager.getThemeMetadata(customThemeName)
                if (metadata != null && !themeManager.isContractCompatible(metadata.contractVersion)) {
                    Log.w(TAG, "Theme '$customThemeName' contract version '${metadata.contractVersion}' is incompatible. Falling back to default raw asset.")
                    activeThemeMetadata = null
                } else {
                    activeThemeMetadata = metadata
                    val decentralized = metadata?.decentralized ?: false
                    Log.d(TAG, "Theme $customThemeName isThemeDecentralized = $decentralized")
                }

                val mainFile = metadata?.mainFile ?: "index.html"

                val themeFile = themeManager.getThemeFile(customThemeName, mainFile)
                if (themeFile != null && themeFile.exists()) {
                    Log.d(
                            TAG,
                            "Loading custom HTML from: ${themeFile.absolutePath} (mainFile: $mainFile)"
                    )
                    return themeFile.readText()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading custom theme file, falling back to raw asset", e)
            }
        } else {
            activeThemeMetadata = null

        }

        Log.d(TAG, "Loading base HTML from resource: app.html")
        return context.resources.openRawResource(R.raw.app).bufferedReader().use { it.readText() }
    }

    private fun getActiveCustomThemeName(): String {
        val themeName =
                preferences.getString(SharedPreferencesKeys.ACTIVE_CUSTOM_THEME.key, "") ?: ""
        return if (themeName.equals("Default", ignoreCase = true)) "" else themeName
    }

    private fun tryLoadExternalDebugHtml(): String? {
        val externalFile = File(DEBUG_EXTERNAL_APP_HTML)
        if (!externalFile.exists() || !externalFile.isFile || !externalFile.canRead()) {
            Log.d(TAG, "[HavalDev] External debug HTML not available at $DEBUG_EXTERNAL_APP_HTML")
            return null
        }

        val html = externalFile.readText()
        val normalized = html.lowercase(Locale.ROOT)
        val isValidHtml =
                html.isNotBlank() &&
                        (normalized.contains("<html") || normalized.contains("<!doctype html"))

        if (!isValidHtml) {
            Log.w(
                    TAG,
                    "[HavalDev] External debug HTML exists but is invalid. Falling back to packaged app.html"
            )
            return null
        }

        Log.i(TAG, "[HavalDev] Loading external debug HTML from $DEBUG_EXTERNAL_APP_HTML")
        return html
    }

    private fun isDebuggableApp(): Boolean {
        return (outerContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    override fun carMainScreenOff() {
        ensureUi { root.visibility = View.INVISIBLE }
    }

    override fun carMainScreenOn() {
        ensureUi {
            applyProjectorViewVisibility(
                    shouldShowProjector() && ServiceManager.getInstance().isMainScreenOn,
                    projectionOverlayBypassActive == true
            )
        }
    }

    fun getGearLabel(gear: String?): String {
        val gearLabel =
                when (gear.toString().toIntOrNull()) {
                    2 -> "D"
                    3 -> "P"
                    4 -> "R"
                    else -> "N"
                }
        return gearLabel
    }

    private fun formatTemp(value: String?): String {
        if (value == null || value == "--" || value == "-1" || value == "255") return "null"
        return try {
            val floatVal = value.toFloat()
            // Format to 1 decimal place. Use dot as decimal separator for JS.
            String.format(java.util.Locale.US, "%.1f", floatVal)
        } catch (e: Exception) {
            "null"
        }
    }

    private fun updateGasConsumption(value: Any?, updates: MutableMap<String, String>? = null) {
        val view = webView
        val stringValue = value.toString()
        var metricValue = 0.0f
        var consumptionValue = 0.0f
        var adjustedValue = 0.0f
        var adjustedValueIdle = 0.0f

        if (stringValue.startsWith("{") && stringValue.endsWith("}") && stringValue.contains(",")) {
            try {
                val cleanedString = stringValue.substring(1, stringValue.length - 1)
                val parts = cleanedString.split(',')
                if (parts.size >= 2) {
                    metricValue = parts[0].trim().toFloat()
                    consumptionValue = parts[1].trim().toFloat()
                }
            } catch (e: Exception) {
                metricValue = 0.0f
                consumptionValue = 0.0f
            }
        } else {
            consumptionValue = stringValue.toFloatOrNull() ?: 0.0f
            metricValue = 1.0f
        }

        var mode = "Running"
        if (metricValue == 4.0f) {
            if (consumptionValue > 0.0f) {
                adjustedValueIdle = kotlin.math.truncate(consumptionValue * 10) / 10
                adjustedValue = 0.0f
                mode = "Idle"
            }
        } else if (metricValue == 1.0f) {
            if (consumptionValue > 0.0f) {
                adjustedValue = kotlin.math.truncate(10 * 100 / consumptionValue) / 10
                adjustedValueIdle = 0.0f
                mode = "Running"
            }
        }

        if (updates != null) {
            updates["gasConsumptionMode"] = mode
            updates["gasConsumptionIdle"] = adjustedValueIdle.toString()
            updates["gasConsumption"] = adjustedValue.toString()
        } else {
            evaluateJsIfReady(
                    view,
                    "control('gasConsumptionMode', '$mode')"
            )
            evaluateJsIfReady(
                    view,
                    "control('gasConsumptionIdle', $adjustedValueIdle)"
            )
            evaluateJsIfReady(
                    view,
                    "control('gasConsumption', $adjustedValue)"
            )
        }
    }

    private fun getAdjustedSpeed(value: Any?): String {
        val speedValue = value?.toString()?.toDoubleOrNull() ?: 0.0
        val enableAdjustment =
                preferences.getBoolean(SharedPreferencesKeys.ENABLE_SPEED_ADJUSTMENT.key, false)
        val offset = preferences.getFloat(SharedPreferencesKeys.SPEED_ADJUSTMENT_OFFSET.key, 0f)

        // Formula to match the original instrument cluster
        val adjustedSpeed = speedValue * 1.07 - speedValue / 180 * 0.02
        val finalSpeed =
                if (enableAdjustment) {
                    adjustedSpeed * (1.0 + (offset / 100.0))
                } else {
                    adjustedSpeed
                }

        return finalSpeed.toInt().toString()
    }

    private fun getSavedClusterDisplay(): String {
        if (testDefaultDisplayOverrideActive) {
            return MAP_DISPLAY_TEST_VALUE
        }

        val savedDisplay =
                preferences.getString(
                        SharedPreferencesKeys.CURRENT_CLUSTER_DISPLAY.key,
                        "Normal"
                ) ?: "Normal"

        return normalizeClusterDisplay(savedDisplay)
    }

    private fun normalizeClusterDisplay(display: String): String {
        return when (display) {
            "Normal", "Esportivo", "Reduzido", "Clean", "Mapa" -> display
            else -> "Normal"
        }
    }

    override fun saveClusterDisplay(value: String) {
        testDefaultDisplayOverrideActive = false
        val normalizedDisplay = normalizeClusterDisplay(value)
        preferences.edit()
                .putString(SharedPreferencesKeys.CURRENT_CLUSTER_DISPLAY.key, normalizedDisplay)
                .apply()
        Log.d(TAG, "Cluster display saved: $normalizedDisplay")
    }

    /**
     * Legacy bridge entry point: themes built before the backend owned the badge compute
     * `warningActive` themselves and push it here. Advisory only now — acting on it would
     * let a downloadable theme override cluster visibility and app widths, and would fight
     * [recomputeWarningState]. Older themes are unaffected: they still receive the
     * authoritative value through `control('warningActive', …)` and render that.
     *
     * Deliberately does no work at all. This used to fire every ~580ms while a warning was
     * standing, and each call invalidated caches, recomputed visibility and called
     * syncSecondaryDisplayApps(), which pegged Impulse's main thread at ~87% CPU.
     */
    override fun updateWarningUI(isActive: Boolean) {
        if (isActive != isWarningActive) {
            Log.d(TAG, "updateWarningUI($isActive) ignored; backend computed $isWarningActive")
        }
    }

    /**
     * BACK closes the car's warning popup, which acknowledges everything listed in it.
     *
     * Acknowledging only the newest was tried and is wrong: the car's popup shows every
     * active warning at once, so one BACK dismisses the lot from the driver's point of view.
     * Holding back the others left our layout collapsed under a popup that was still up, and
     * meant one physical condition reported over several CAN keys (tyres are four, oil is
     * three) needed a BACK press each.
     *
     * The lockout is still measured per-warning, against the most recent onset, so a warning
     * that has only just appeared cannot be swallowed by a BACK press already in flight —
     * that part of the per-warning work was worth keeping.
     */
    override fun dismissWarnings() {
        ensureUi {
            val showing = unacknowledgedBadgeWarnings()
            if (showing.isEmpty()) {
                Log.d(TAG, "dismissWarnings: nothing un-acknowledged to dismiss")
                return@ensureUi
            }

            // Sorted newest-first, so the head is the warning that most recently appeared.
            val newestKey = showing.first().first
            val onset = warningOnsetTimes[newestKey] ?: lastWarningActiveTime
            val timeSinceWarning = System.currentTimeMillis() - onset
            Log.d(TAG, "dismissWarnings called. newest=$newestKey timeSinceWarning=${timeSinceWarning}ms (onset=$onset)")
            logClusterPerfEvent(
                    "dismiss_warning",
                    mapOf("newest" to newestKey, "count" to showing.size, "timeSinceWarningMs" to timeSinceWarning)
            )
            if (timeSinceWarning < WARNING_DISMISS_LOCKOUT_MS) {
                Log.w(TAG, "DISMISS_WARNING ignored: newest=$newestKey timeSinceWarning=${timeSinceWarning}ms < ${WARNING_DISMISS_LOCKOUT_MS}ms lockout")
                return@ensureUi
            }

            for ((key, value) in showing) {
                dismissedWarnings[key] = value
            }
            Log.d(TAG, "dismissWarnings: acknowledged ${showing.size} warning(s): ${showing.map { it.first }}")
            // No hold-off here: the car's popup closes with the same BACK press.
            recomputeWarningState("DISMISS", immediate = true)
        }
    }

    override fun runOnUiThread(action: Runnable) {
        ensureUi { action.run() }
    }

    // setCardId is intentionally gone. The theme used to echo the card back here, which
    // made currentCard writable from JS and let a theme's stale view overwrite the card
    // the car had reported. Card state flows one way: car -> backend -> theme.

    override fun updateHeartbeat() {
        lastHeartbeatTime = System.currentTimeMillis()
    }

    override fun refreshDisplayBounds() {
        Log.d(TAG, "refreshDisplayBounds: Triggering display app sync from frontend")
        ensureUi {
            if (hasManagedSecondaryDisplayWork(3)) {
                lastAppliedConfigs.clear()
                syncSecondaryDisplayApps(3)
            }
        }
    }

    private fun pushVirtualDisplayState(displayId: Int) {
        val themeBridge = this.themeBridge ?: return
        val contextApp = outerContext.applicationContext
        
        val activeAppKey = "app.display.$displayId.active_app"
        val activeAppLabelKey = "app.display.$displayId.active_app_label"
        val activeAppIconKey = "app.display.$displayId.active_app_icon"
        
        if (subscribedKeys.contains(activeAppKey)) {
            val value = br.com.redesurftank.havalshisuku.bridge.VirtualTelemetryManager.getVirtualValue(contextApp, activeAppKey)
            themeBridge.pushOnDataChanged(activeAppKey, value)
        }
        if (subscribedKeys.contains(activeAppLabelKey)) {
            val value = br.com.redesurftank.havalshisuku.bridge.VirtualTelemetryManager.getVirtualValue(contextApp, activeAppLabelKey)
            themeBridge.pushOnDataChanged(activeAppLabelKey, value)
        }
        if (subscribedKeys.contains(activeAppIconKey)) {
            val value = br.com.redesurftank.havalshisuku.bridge.VirtualTelemetryManager.getVirtualValue(contextApp, activeAppIconKey)
            themeBridge.pushOnDataChanged(activeAppIconKey, value)
        }
    }

    private fun initNativeMaskViews(parent: FrameLayout) {
        if (nativeMaskContainer != null) return
        val container = FrameLayout(outerContext).apply {
            layoutParams = FrameLayout.LayoutParams(1920, 720)
            isClickable = false
        }

        fuelMaskView = android.widget.ImageView(outerContext).apply {
            id = View.generateViewId()
            scaleType = android.widget.ImageView.ScaleType.FIT_XY
            visibility = View.GONE
        }
        batteryMaskView = android.widget.ImageView(outerContext).apply {
            id = View.generateViewId()
            scaleType = android.widget.ImageView.ScaleType.FIT_XY
            visibility = View.GONE
        }
        speedMaskView = android.widget.ImageView(outerContext).apply {
            id = View.generateViewId()
            scaleType = android.widget.ImageView.ScaleType.FIT_XY
            visibility = View.GONE
        }
        infoMaskView = android.widget.ImageView(outerContext).apply {
            id = View.generateViewId()
            scaleType = android.widget.ImageView.ScaleType.FIT_XY
            visibility = View.GONE
        }
        topCenterMaskView = android.widget.ImageView(outerContext).apply {
            id = View.generateViewId()
            scaleType = android.widget.ImageView.ScaleType.FIT_XY
            visibility = View.GONE
        }
        globalMaskView = android.widget.ImageView(outerContext).apply {
            id = View.generateViewId()
            scaleType = android.widget.ImageView.ScaleType.FIT_XY
            layoutParams = FrameLayout.LayoutParams(1920, 720)
            visibility = View.GONE
        }

        container.addView(globalMaskView)
        container.addView(fuelMaskView)
        container.addView(batteryMaskView)
        container.addView(speedMaskView)
        container.addView(infoMaskView)
        container.addView(topCenterMaskView)

        nativeMaskContainer = container
        parent.addView(container, 0)
    }

    fun updateNativeMaskViews() {
        val customThemeName = getActiveCustomThemeName()
        val themeMgr = br.com.redesurftank.havalshisuku.managers.ThemeManager.getInstance(outerContext)
        val metadata = activeThemeMetadata ?: if (customThemeName.isNotEmpty()) {
            themeMgr.getThemeMetadata(customThemeName)
        } else {
            themeMgr.getEmbeddedDefaultTheme()
        }
        val config = metadata?.nativeMasks
        Log.d(TAG, "updateNativeMaskViews: customThemeName='$customThemeName' metadata='${metadata?.name}' hasConfig=${config != null && config.hasAnyActiveMask()}")
        if (config == null || !config.hasAnyActiveMask()) {
            nativeMaskContainer?.isVisible = false
            return
        }

        nativeMaskContainer?.isVisible = true
        val folderName = metadata?.folderName ?: customThemeName
        val isCard0 = (currentCard == 0)

        val assetMaskBitmap: android.graphics.Bitmap? = try {
            outerContext.assets.open("d3_mask.png").use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }

        val globalMaskFile = themeMgr.getThemeFile(folderName, "d3_mask.png")
        val maskBitmap = assetMaskBitmap ?: if (globalMaskFile != null && globalMaskFile.exists()) {
            android.graphics.BitmapFactory.decodeFile(globalMaskFile.absolutePath)
        } else null

        if (maskBitmap != null) {
            try {
                val bgBitmap = getBackgroundBitmap(folderName)
                if (bgBitmap != null) {
                    val scaledBg = getCoverScaledBitmap(bgBitmap, 1920, 720)
                    val scaledMask = getCoverScaledBitmap(maskBitmap, 1920, 720)

                    val alphaMask = convertLuminanceToAlpha(scaledMask)
                    
                    val result = android.graphics.Bitmap.createBitmap(1920, 720, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(result)
                    canvas.drawBitmap(scaledBg, 0f, 0f, null)
                    
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
                    }
                    canvas.drawBitmap(alphaMask, 0f, 0f, paint)
                    
                    globalMaskView?.setImageBitmap(result)
                    globalMaskView?.isVisible = true
                    
                    fuelMaskView?.isVisible = false
                    batteryMaskView?.isVisible = false
                    speedMaskView?.isVisible = false
                    infoMaskView?.isVisible = false
                    topCenterMaskView?.isVisible = false
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load global d3_mask.png", e)
            }
        }
        globalMaskView?.isVisible = false

        fun bindMask(
            view: android.widget.ImageView?,
            spec: br.com.redesurftank.havalshisuku.models.NativeMaskSpec,
            name: String,
            forceHide: Boolean = false
        ) {
            if (view == null) return
            val overrideVisible = maskVisibilityOverrides[name] ?: true
            val shouldShow = spec.enabled && overrideVisible && !forceHide

            if (!shouldShow) {
                view.isVisible = false
                return
            }

            val params = (view.layoutParams as? FrameLayout.LayoutParams) ?: FrameLayout.LayoutParams(spec.width, spec.height)
            params.width = spec.width
            params.height = spec.height
            params.leftMargin = spec.x
            params.topMargin = spec.y
            view.layoutParams = params
            view.alpha = spec.opacity

            if (spec.image.isNotBlank()) {
                val themeFile = br.com.redesurftank.havalshisuku.managers.ThemeManager.getInstance(outerContext).getThemeFile(folderName, spec.image)
                if (themeFile != null && themeFile.exists()) {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(themeFile.absolutePath)
                    view.setImageBitmap(bitmap)
                } else {
                    view.setImageDrawable(getThemeMaskFallbackDrawable(folderName, spec))
                }
            } else {
                view.setImageDrawable(getThemeMaskFallbackDrawable(folderName, spec))
            }

            view.isVisible = true
        }

        bindMask(fuelMaskView, config.fuelMask, "fuel")
        bindMask(batteryMaskView, config.batteryMask, "battery")
        bindMask(speedMaskView, config.speedMask, "speed")
        // Mask 2.2 Info Mask: ALWAYS hidden by backend when card = 0
        bindMask(infoMaskView, config.infoMask, "info", forceHide = isCard0)
        bindMask(topCenterMaskView, config.topCenterMask, "topcenter")
    }

    private fun getBackgroundBitmap(folderName: String): android.graphics.Bitmap? {
        val isEnabled = preferences.getBoolean(SharedPreferencesKeys.ENABLE_CUSTOM_BACKGROUND_D1.key, true)
        if (!isEnabled) return null

        val type = preferences.getString(SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key, "THEME") ?: "THEME"
        val value = preferences.getString(SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key, "") ?: ""
        val themeMgr = br.com.redesurftank.havalshisuku.managers.ThemeManager.getInstance(outerContext)

        try {
            when (type) {
                "THEME" -> {
                    val bgFile = themeMgr.getActiveThemeBackgroundFile(value.ifBlank { null })
                        ?: themeMgr.getThemeFile(folderName, "car-bg.png")
                    if (bgFile != null && bgFile.exists()) {
                        return android.graphics.BitmapFactory.decodeFile(bgFile.absolutePath)
                    }
                }
                "FILE" -> {
                    val file = File(value)
                    if (file.exists()) {
                        return android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                    }
                }
                "PRESET" -> {
                    val inputStream = outerContext.assets.open("backgrounds/$value")
                    return android.graphics.BitmapFactory.decodeStream(inputStream)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load custom background for mask fallback", e)
        }
        return null
    }

    private fun getCoverScaledBitmap(bitmap: android.graphics.Bitmap, targetW: Int, targetH: Int): android.graphics.Bitmap {
        if (bitmap.width == targetW && bitmap.height == targetH) return bitmap
        
        val scale = Math.max(targetW.toFloat() / bitmap.width, targetH.toFloat() / bitmap.height)
        val scaledW = Math.round(bitmap.width * scale)
        val scaledH = Math.round(bitmap.height * scale)
        
        val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
        
        val x = ((scaledW - targetW) / 2).coerceIn(0, Math.max(0, scaledW - targetW))
        val y = ((scaledH - targetH) / 2).coerceIn(0, Math.max(0, scaledH - targetH))
        
        return android.graphics.Bitmap.createBitmap(scaledBitmap, x, y, targetW, targetH)
    }

    private fun getThemeMaskFallbackDrawable(folderName: String, spec: br.com.redesurftank.havalshisuku.models.NativeMaskSpec): android.graphics.drawable.Drawable {
        val fullBitmap = getBackgroundBitmap(folderName)

        if (fullBitmap != null) {
            try {
                val scaledBitmap = getCoverScaledBitmap(fullBitmap, 1920, 720)
                
                val cropX = spec.x.coerceIn(0, scaledBitmap.width - 1)
                val cropY = spec.y.coerceIn(0, scaledBitmap.height - 1)
                val cropW = spec.width.coerceAtMost(scaledBitmap.width - cropX)
                val cropH = spec.height.coerceAtMost(scaledBitmap.height - cropY)
                if (cropW > 0 && cropH > 0) {
                    val cropped = android.graphics.Bitmap.createBitmap(scaledBitmap, cropX, cropY, cropW, cropH)
                    return android.graphics.drawable.BitmapDrawable(outerContext.resources, cropped)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to crop theme background for mask fallback", e)
            }
        }

        // Return TRANSPARENT for large side mask fallbacks to avoid black side bars
        return if (spec.width >= 500 && spec.height >= 500) {
            android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        } else {
            android.graphics.drawable.ColorDrawable(Color.BLACK)
        }
    }

    private fun convertLuminanceToAlpha(mask: android.graphics.Bitmap): android.graphics.Bitmap {
        val width = mask.width
        val height = mask.height
        val result = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        mask.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
            val newAlpha = (lum * (a / 255f)).toInt().coerceIn(0, 255)
            pixels[i] = (newAlpha shl 24) or 0x00FFFFFF
        }
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    override fun setNativeMaskState(maskName: String, visible: Boolean) {
        maskVisibilityOverrides[maskName.lowercase(Locale.ROOT)] = visible
        ensureUi { updateNativeMaskViews() }
    }

    override fun setNativeMasksConfig(jsonConfig: String) {
        ensureUi { updateNativeMaskViews() }
    }
}
